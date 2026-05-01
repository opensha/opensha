package org.opensha.commons.data.siteData;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

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

    // TODO: Some of these overridden methods may be delegated to concrete impl

    private String type;
    private final Path siteDataPath;

    /**
     * Constructor for the AbstractGriddedSiteDataLoader.
     * @param type See `org.opensha.commons.data.siteData` for list of types
     * @param downloader Instance of downloader for site data from GitLab
     */
    public AbstractGriddedSiteDataLoader(String type, AbstractGitLabDownloader downloader) {
        super();

        this.type = type;
        this.siteDataPath = downloader.downloadSiteData();
    }

    /**
     * Downloads site data from GItLab (if not already downloaded) and returns
     * a path to where the CSV/GeoJSON files are stored.
     * <p>
     * Note that each CSV file maps locations to site data values and each CSV file
     * must have a corresponding GeoJSON file that defines their region.
     * You must validate that the `type` for retrieval is present in the CSV files.
     * </p>
     * @return
     */
    protected abstract Path getSiteData();

    /**
     * This gives the applicable region for this data set.
     *
     * @return Region
     */
    @Override
    public Region getApplicableRegion() {
        // TODO: We will iterate over all CSV files to find matching values
        //       If there is a match, we load the corresponding Region from the GeoJSON

        return null;
    }

    /**
     * Loads a Region from a GeoJSON file found in the site data path
     * @param regionName
     * @return
     * @throws IOException
     * @throws FileNotFoundException
     */
    private Region loadRegion(String regionName) throws IOException, FileNotFoundException {
      return null; // TODO
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
        // TODO
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
        // TODO
        return 0.0;
    }

    /**
     * Returns true if the value is valid, and not NaN, N/A, or equivelant for the data type
     *
     * @param el
     * @return
     */
    @Override
    public boolean isValueValid(Double el) {
        // TODO
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
