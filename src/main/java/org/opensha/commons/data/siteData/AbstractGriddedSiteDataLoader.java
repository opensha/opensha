package org.opensha.commons.data.siteData;

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

    private String type; // Z1.0, Z2.5, Zsed
    private final Path siteDataPath;

    private double minLat;
    private double maxLat;
    private double minLon;
    private double maxLon;

    /**
     * Constructor for the AbstractGriddedSiteDataLoader.
     * Invokes the downlaoder to retrieve site data if not already downloaded.
     * Defines the region where this site data is applicable
     * @param type See `org.opensha.commons.data.siteData` for list of types
     * @param downloader Instance of downloader for site data from GitLab
     */
    public AbstractGriddedSiteDataLoader(String type,
                                         AbstractGitLabDownloader downloader,
                                         double minLat, double maxLat,
                                         double minLon, double maxLon) {
        super();

        this.type = type;
        this.siteDataPath = downloader.downloadSiteData();
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.minLon = minLon;
        this.maxLon = maxLon;
        // TODO: We need to hardcode the min/max lat/lon for each known NSHM version
        //       inside our concrete impls
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
        // TODO: How do I validate gridding matches?
        return GriddedRegion.fromFeature(feature);
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
       // 1. First checks if the file is in the same directory as the CSV file
       String gjFileName = FileNameUtils.getBaseName(csvFile)+".geojson";
       Path geojson = csvFile.getParent().resolve(gjFileName);
       if (Files.exists(geojson))
           return geojson;

       // 2. If not found in the same directory, search recursively from the `siteDataPath`
        // TODO: Test this behavior
        try (Stream<Path> paths = Files.walk(siteDataPath)) {
            Optional<Path> foundPath = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(gjFileName))
                    .findFirst();

            if (foundPath.isPresent())
                return foundPath.get();
        }

       // 3. If the corresponding GeoJSON file for the CSV is not found, then simply return null.
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
        // TODO
        return 0;
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
     * Get the location of the closest data point
     *
     * @param loc
     * @return
     */
    @Override
    public Location getClosestDataLocation(Location loc) throws IOException {
        // Collect closest locations in each region
        LocationList closestLocs = new LocationList();

        // Recursively find all CSV files for locations
        try (Stream<Path> csvStream = Files.walk(siteDataPath)) {
            csvStream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                    .forEach(csv -> {
                        try {
                            // If there is no GeoJson for the CSV, it's locs are skipped.
                            Path gj = getGeoJsonFromCsv(csv);
                            GriddedGeoDataSet gds = new GriddedGeoDataSet(loadRegion(gj));
                            // Within a region, we can use the more efficient distance formula
                            Location regionalClosest = getClosestInList(loc,
                                    gds.getLocationList(), LocationUtils::horzDistanceFast);
                            closestLocs.add(regionalClosest);
                        } catch (IOException e) {
                            log.warn("Error locating GeoJSON for CSV " + csv.getFileName() +
                                    ": " + e.getMessage());
                        }
                    });
        }
        // Location distances may be greater across regions, so use Haversine formula
        return getClosestInList(loc, closestLocs, LocationUtils::horzDistance);
    }

    /**
     * Finds the closest location in a list of locations.
     * @param loc the location to find the closest point for.
     * @param locs list of locations to consider
     * @param distCalc function that takes two locations and returns the distance
     * @return
     */
    private Location getClosestInList(Location loc, LocationList locs,
                                      BiFunction<Location, Location, Double> distCalc) {
        // Calculate distances between loc and each location of locs
        double[] distances = new double[locs.size()];
        Arrays.setAll(distances, i -> distCalc.apply(loc, locs.get(i)));
        // Find the shortest distance
        double min = Double.MAX_VALUE;
        int idx = -1;
        for (int i = 0; i < distances.length; i++) {
           double d = distances[i];
           if (d < min) {
               idx = i;
               min = d;
           }
        }
        // Return the corresponding location
        if (idx == -1) return null;
        return locs.get(idx);
    }

    /**
     * Get the value at the closest location
     *
     * @param loc
     * @return
     */
    @Override
    public Double getValue(Location loc) throws IOException {
        // Find the closest location in the dataset
        Location closestLoc = getClosestDataLocation(loc);

        // Map the selected data type to the representation in the CSV headers.
        // CSV must have a header to be readable.
        // Comments before the header denoted with `#` will be ignored.
        // NOTE: If you attempt to load a SiteData type without declaring here what
        //       column the data is stored under, the values can't be retrieved.
        Map<String, String> dataTypesMap = Map.of(
                SiteData.TYPE_DEPTH_TO_1_0, "z1p0",
                SiteData.TYPE_DEPTH_TO_2_5, "z2p5",
                SiteData.TYPE_SEDIMENT_THICKNESS, "zsed"
        );

        List<Double> matches = new ArrayList<>();
        // Search all CSV files until the location is found
        try (Stream<Path> csvStream = Files.walk(siteDataPath)) {
            csvStream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                    .forEach(csv -> {
                        try {
                            // If there is no GeoJson for the CSV, it's locs are skipped.
                            Path gj = getGeoJsonFromCsv(csv);
                            GriddedGeoDataSet gds = new GriddedGeoDataSet(loadRegion(gj));
                            if (gds.contains(closestLoc)) {
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
                                    assert (latCol != -1);
                                    assert (lonCol != -1);

                                    if (datCol != -1) {
                                        for (CSVReader.Row row : reader) {
                                            if (Double.compare(row.getDouble(lonCol), closestLoc.getLongitude()) == 0
                                                    && Double.compare(row.getDouble(latCol), closestLoc.getLatitude()) == 0) {
                                                matches.add(row.getDouble(datCol));
                                            }
                                        }
                                    } else {
                                        log.warn("Location was found, but requested data type is unavailable");
                                    }
                                }
                            }
                        } catch (IOException e) {
                            log.warn("Error locating GeoJSON for CSV " + csv.getFileName() +
                                    ": " + e.getMessage());
                        }
                    });
            if (matches.isEmpty()) return NaN; // Should never happen
            return matches.get(0);
        }
    }

    /**
     * Returns true if the value is valid, and not NaN, N/A, or equivalent for the data type
     *
     * @param el
     * @return
     */
    @Override
    public boolean isValueValid(Double el) {
        if (type.equals(SiteData.TYPE_DEPTH_TO_1_0)
            || type.equals(SiteData.TYPE_DEPTH_TO_2_5)
            || type.equals(SiteData.TYPE_SEDIMENT_THICKNESS)) {
           return !el.isNaN() && !el.isInfinite() && el >= 0;
        }
        return false;
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
