package org.opensha.commons.data.siteData;

import org.opensha.commons.data.CSVReader;
import org.opensha.commons.data.siteData.util.GriddedSiteDataUtil;
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
import java.util.stream.Stream;

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
    private final Map<String, String> dataTypesMap;
    private List<GriddedRegion> regions;
    private Map<Location, Double> siteData;
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

        loadSiteData(downloader);
    }

    /**
     * Downloads and loads site data.
     * Concrete classes may call this method to change which site data should be loaded.
     * This is called with a default downloader at construction.
     * @param downloader
     */
    protected void loadSiteData(AbstractGitLabDownloader downloader) {
        this.resolution = Double.MAX_VALUE;
        this.regions = new ArrayList<>();
        this.siteData = new HashMap<>();

        // Download the site data if it hasn't been downloaded already
        Path siteDataPath = downloader.downloadSiteData();
        // Load data into memory
        try (Stream<Path> csvStream = Files.walk(siteDataPath)) {
            csvStream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                    .forEach(csv -> {
                        try {
                            // If there is no GeoJson for the CSV, it's locs are skipped.
                            Path gj = getGeoJsonFromCsv(csv);
                            GriddedRegion region = loadRegion(gj, getAnchor(csv));
                            this.regions.add(region);
                            populateValues(csv, region.getSpacing());
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
     * Gets an anchor location from the CSV file.
     * This is required to align with the spacing of the data for loading Regions.
     * @param csv
     * @return
     */
    private Location getAnchor(Path csv) throws IOException {
        try (InputStream in = Files.newInputStream(csv)) {
            CSVReader reader = new CSVReader(in);
            int latCol = -1;
            int lonCol = -1;
            int datCol = -1;
            // Ignore comments at start of CSV and get header
            CSVReader.Row header;
            do {
                header = reader.read();
            } while (header.get(0).startsWith("#"));
            for (int i = 0; i < header.columns(); i++) {
                if (header.get(i).equals("lat")) {
                    latCol = i;
                } else if (header.get(i).equals("lon")) {
                    lonCol = i;
                }
            }
            CSVReader.Row firstLoc = reader.read();
            return new Location(firstLoc.getDouble(latCol), firstLoc.getDouble(lonCol));
        }
    }

    /**
     * Reads site data into memory from CSV file
     * If there is no data to load, the values remain as `NaN`
     * @param csv Path to CSV file containing site data to load from
     * @param spacing Grid node spacing for this region
     */
    private void populateValues(Path csv, double spacing) throws IOException {
        try (InputStream in = Files.newInputStream(csv)) {
            CSVReader reader = new CSVReader(in);
            int latCol = -1;
            int lonCol = -1;
            int datCol = -1;
            // Ignore comments at start of CSV and get header
            CSVReader.Row header;
            do {
                header = reader.read();
            } while (header.get(0).startsWith("#"));
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
                    Location snappedLoc = GriddedSiteDataUtil.snapToGrid(loc, spacing);

                    siteData.put(snappedLoc, dat);
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
     * @param anchor Location to anchor the Region on
     * @return
     * @throws IOException Failure reading the GeoJSON file
     * @throws FileNotFoundException if the GeoJSON file doesn't exist
     * @throws IllegalStateException if the GeoJSON file contains more than 1 feature
     */
    private GriddedRegion loadRegion(Path geojson, Location anchor) throws IOException, FileNotFoundException {
        if (!Files.exists(geojson))
            throw new FileNotFoundException();
        // could be either a Feature or FeatureCollection (with 1 feature)
        Feature feature;
        GeoJSON_Type type = GeoJSON_Type.detect(Files.newBufferedReader(geojson));
        try (BufferedReader reader = Files.newBufferedReader(geojson)) {
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

        double spacing = feature.properties.getDouble("spacing", 0);
        if (spacing < resolution) {
            resolution = spacing;
        }
        Region region = Region.fromFeature(feature);
        region.setName(feature.properties.getString("name", "Unnamed Region"));
        return new GriddedRegion(region, spacing, anchor);
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
     *
     * @param loc
     * @return
     */
    @Override
    public Location getClosestDataLocation(Location loc) {
        for (GriddedRegion region : regions) {
            Location closestLoc = GriddedSiteDataUtil.snapToGrid(loc, region.getSpacing());
            if (region.contains(closestLoc))
                return closestLoc;
        }
        return null;
    }

    /**
     * Get the value at the closest location if the coordinate exists on a gridded region
     *
     * @param loc
     * @return the site data value at the location, or NaN if not found
     */
    @Override
    public Double getValue(Location loc) throws IOException {
        return siteData.getOrDefault(getClosestDataLocation(loc), Double.NaN);
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
}
