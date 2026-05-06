package org.opensha.commons.data.siteData;

import org.apache.commons.compress.utils.Lists;
import org.opensha.commons.data.CSVReader;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import org.apache.commons.compress.utils.FileNameUtils;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.GeoJSON_Type;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.Double.NaN;

/**
 * Abstract base class for loading site data for GeoJSON regions.
 * <p>
 * Unlike `AbstractBinarySiteDataLoader`, there is no binary file.
 * There is just the geojson file specifying regions.
 * Locations and site data values are provided via CSV files and a corresponding
 * region is mapped or NaN if none are applicable.
 * </p>
 */
public abstract class AbstractGriddedSiteDataLoader extends AbstractSiteData<Double> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final String type;
    private final List<GriddedGeoDataSet> dataSets;
    private final Map<String, String> dataTypesMap;
    private double resolution; // smallest spacing encountered

    private final double minLat;
    private final double maxLat;
    private final double minLon;
    private final double maxLon;

    /**
     * Constructor for the AbstractGriddedSiteDataLoader.
     * Invokes the downloader to retrieve site data if not already downloaded.
     * Defines the region where this site data is applicable.
     * Loads site data into memory.
     * @param type See `org.opensha.commons.data.siteData` for list of types
     * @param downloader Instance of downloader for site data from GitLab
     */
    public AbstractGriddedSiteDataLoader(String type,
                                         AbstractGitLabDownloader downloader,
                                         double minLat, double maxLat,
                                         double minLon, double maxLon) {
        super();

        this.type = type;
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.minLon = minLon;
        this.maxLon = maxLon;
        this.resolution = Double.MAX_VALUE;
        this.dataSets = new ArrayList<>();

        // Map the selected data type to the representation in the CSV headers.
        // CSV must have a header to be readable.
        // Comments before the header denoted with `#` will be ignored.
        // NOTE: If you attempt to load a SiteData type without declaring here what
        //       column the data is stored under, the values can't be retrieved.
        this.dataTypesMap = Map.of(
                SiteData.TYPE_DEPTH_TO_1_0, "z1p0",
                SiteData.TYPE_DEPTH_TO_2_5, "z2p5",
                SiteData.TYPE_SEDIMENT_THICKNESS, "zsed"
        );

        // Download the site data if it hasn't been downloaded already
        Path siteDataPath = downloader.downloadSiteData();
        // Load geodata into memory
        try (Stream<Path> csvStream = Files.walk(siteDataPath)) {
            csvStream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                    .forEach(csv -> {
                        try {
                            // If there is no GeoJson for the CSV, it's locs are skipped.
                            Path gj = getGeoJsonFromCsv(csv);
                            GriddedGeoDataSet gds = new GriddedGeoDataSet(loadRegion(gj));
                            // Read data from CSV into memory
                            populateValues(gds, csv);
                            this.dataSets.add(gds);
                        } catch (IOException e) {
                            log.warn("Error locating GeoJSON for CSV " + csv.getFileName() +
                                    ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {

            throw new RuntimeException(e);
        }
    }

    /**
     * Populates data grid with values of `type`.
     * If there is no data to load, the values remain as `NaN`
     * @param gds data structure to populate with site data
     * @param csv Path to CSV file containing site data to load from
     */
    private void populateValues(GriddedGeoDataSet gds, Path csv) throws IOException {
        gds.scale(Double.NaN); // Clear all values
        try (InputStream in = Files.newInputStream(csv)) {
            CSVReader reader = new CSVReader(in);
            int latCol = -1;
            int lonCol = -1;
            int datCol = -1;
            // Ignore comments at start of CSV and get header
            CSVReader.Row header;
            do {
                header = reader.read();
            } while (header.toString().startsWith("#"));
            for (int i = 0; i < header.columns(); i++) {
                if (header.get(i).equals("lat")) {
                    latCol = i;
                } else if (header.get(i).equals("lon")) {
                    lonCol = i;
                } else if (header.get(i).equals(dataTypesMap.get(type))) {
                    datCol = i;
                }
            }
            Preconditions.checkState(latCol != -1,
                    "CSV \"" + csv.getFileName() + "\"" + " is missing latitude column");
            Preconditions.checkState(lonCol != -1,
                    "CSV \"" + csv.getFileName() + "\"" + " is missing longitude column");

            if (datCol != -1) {
                for (CSVReader.Row row : reader) {
                    double dat = row.getDouble(datCol);
                    if (type.equals(SiteData.TYPE_SEDIMENT_THICKNESS)) {
                       dat /= 1000; // Convert m to km
                    }
                    double lat = row.getDouble(latCol);
                    double lon = row.getDouble(lonCol);

                    Location loc = new Location(lat, lon);
                    int index = gds.indexOf(loc);
                    Preconditions.checkState(index >= 0,
                            "CSV location not found in GDS: %s", loc);

                    Location gridLoc = gds.getLocation(index);
                    Preconditions.checkState(LocationUtils.areSimilar(loc, gridLoc),
                            "CSV and GDS locs differ: %s != %s", loc, gridLoc);

                    double prevVal = gds.get(index);
                    Preconditions.checkState(Double.isNaN(prevVal),
                            "Value at index %s for location %s was previously set: %s",
                            index, gds.getLocation(index), dat);

                    gds.set(index, dat);
                }
            }
        }
    }

        /**
     * This gives the applicable region for this data set.
     *
     * @return Region
     */
    @Override
    public Region getApplicableRegion() {
        return new Region(
                new Location(minLat,minLon),
                new Location(maxLat,maxLon));
    }

    /**
     * Loads a Region from a GeoJSON file
     * @param geojson Path to a GeoJSON file with the Region data to load
     * @return
     * @throws IOException Failure reading the GeoJSON file
     * @throws FileNotFoundException if the GeoJSON file doesn't exist
     * @throws IllegalStateException if the GeoJSON file contains more than 1 feature
     */
    private GriddedRegion loadRegion(Path geojson) throws IOException, FileNotFoundException {
        if (!Files.exists(geojson))
            throw new FileNotFoundException();
        // could be either a Feature or FeatureCollection (with 1 feature)
        Feature feature;
        try (BufferedReader reader = Files.newBufferedReader(geojson)) {
            GeoJSON_Type type = GeoJSON_Type.detect(reader);
            if (type == GeoJSON_Type.Feature) {
                feature = Feature.read(reader);
            } else if (type == GeoJSON_Type.FeatureCollection) {
                FeatureCollection features = FeatureCollection.read(reader);
                Preconditions.checkState(features.features.size() == 1, "Expected 1 feature in collection");
                feature = features.features.get(0);
            } else {
                throw new IllegalStateException("Expected Feature or FeatureCollection, have "+type);
            }
        }

        // TODO: Test to confirm spacing is retrieved correctly
        double spacing = feature.properties.getDouble("spacing", 0);
        if (spacing < resolution) {
            resolution = spacing;
        }
        Region region = Region.fromFeature(feature);
        // If anchor at (0,0) is invalid, we could use the first location in region
        return new GriddedRegion(region, spacing, GriddedRegion.ANCHOR_0_0);
    }

    /**
     * Finds the corresponding GeoJSON file for a given CSV File.
     * <p>
     * Each CSV file of locations and site data should have a corresponding geojson
     * file that defines the region. The file names (excluding extension) are the same.
     * </p>
     * @param csvFile
     * @return
     * @throws IOException
     * @throws FileNotFoundException
     */
    private Path getGeoJsonFromCsv(Path csvFile) throws IOException, FileNotFoundException {
       // Checks in same directory for a .geojson file of the same name as the .csv file
       String gjFileName = FileNameUtils.getBaseName(csvFile)+".geojson";
       Path geojson = csvFile.getParent().resolve(gjFileName);
       if (Files.exists(geojson))
           return geojson;

        return null;
    }

    /**
     * This gives the resolution of the dataset in degrees, or 0 for infinite resolution.
     * <p>
     * We could possibly add a 'units' field to allow for resolution in KM
     *
     * @return
     */
    @Override
    public double getResolution() {
        return resolution;
    }

//    /**
//     * Get the name of this dataset
//     *
//     * @return
//     */
//    @Override
//    public String getName() {
//        // TODO
//        return "";
//    }

//    /**
//     * Get the short name of this dataset
//     *
//     * @return
//     */
//    @Override
//    public String getShortName() {
//        // TODO
//        return "";
//    }

    /**
     * Get the data type of this dataset
     *
     * @return String representing the data type of the data set for loading
     */
    @Override
    public String getDataType() {
        return type;
    }

    /**
     * Get the measurement type for this data, such as "Measured" or "Inferred"
     *
     * @return
     */
    @Override
    public String getDataMeasurementType() {
        return TYPE_FLAG_INFERRED;
    }

    /**
     * Get the location of the closest data point.
     * This assumes the location does exist in the data set.
     * Enables a fuzzy search accounting for grid spacing and precision.
     *
     * @param loc
     * @return
     */
    @Override
    public Location getClosestDataLocation(Location loc) {
       for (GriddedGeoDataSet grid : dataSets) {
           if (!grid.contains(loc)) continue;
           int index = grid.indexOf(loc);
           return grid.getLocation(index);
       }
       log.warn("Failed to find location {}", loc);
       return null;
    }

    /**
     * Get the value at the closest location
     *
     * @param loc
     * @return
     */
    @Override
    public Double getValue(Location loc) throws IOException {
        for (GriddedGeoDataSet grid : dataSets) {
            if (!grid.contains(loc)) continue;
            int index = grid.indexOf(loc);
            Location gridLoc = grid.getLocation(index);
            return grid.get(gridLoc);
        }
        log.warn("Failed to find a value for location {}", loc);
        return Double.NaN;
    }

    /**
     * Returns true if the value is valid, and not NaN, N/A, or equivalent for the data type
     *
     * @param el
     * @return
     */
    @Override
    public boolean isValueValid(Double el) {
       return !el.isNaN() && !el.isInfinite() && el >= 0;
    }

//    /**
//     * Returns the metadata for this dataset.
//     *
//     * @return
//     */
//    @Override
//    public String getMetadata() {
//        // TODO
//        return "";
//    }
}
