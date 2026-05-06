package org.opensha.commons.data.siteData.impl;

import org.opensha.commons.data.siteData.AbstractGriddedSiteDataLoader;
import org.opensha.commons.data.siteData.CONUS_Downloader;
import org.opensha.commons.data.siteData.CONUS_Versions;
import org.opensha.commons.geo.Region;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader;

import java.io.IOException;
import java.util.EnumSet;

public class CONUS_SiteDataProvider extends AbstractGriddedSiteDataLoader implements ParameterChangeListener {

    public static final String NAME = "USGS NSHM-CONUS Site Data Provider";
    public static final String SHORT_NAME = "NSHM-CONUS";

    private static final double MIN_LAT;
    private static final double MAX_LAT;
    private static final double MIN_LON;
    private static final double MAX_LON;

    public static final String VERSION_PARAM_NAME = "Version";
    private EnumParameter<CONUS_Versions> versionParam;
    public static final CONUS_Versions VERSION_DEFAULT = CONUS_Versions.NSHM23;

    static {
        try {
            Region region = NSHM23_RegionLoader.loadFullConterminousUS();
            MIN_LAT = region.getMinLat();
            MAX_LAT = region.getMaxLat();
            MIN_LON = region.getMinLon();
            MAX_LON = region.getMaxLon();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public CONUS_SiteDataProvider(String type) {
        super(type, new CONUS_Downloader(VERSION_DEFAULT),
                MIN_LAT, MAX_LAT, MIN_LON, MAX_LON);
        this.versionParam = new EnumParameter<CONUS_Versions>(VERSION_PARAM_NAME,
                EnumSet.allOf(CONUS_Versions.class), VERSION_DEFAULT, null);
        versionParam.addParameterChangeListener(this);
        paramList.addParameter(versionParam);
        serverParamsList.addParameter(versionParam);
    }

    /**
     * Get the name of this dataset
     *
     * @return
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Get the short name of this dataset
     *
     * @return
     */
    @Override
    public String getShortName() {
        return SHORT_NAME;
    }

    /**
     * Returns the metadata for this dataset.
     *
     * @return
     */
    @Override
    public String getMetadata() {
        return "TODO"; // TODO
    }

    /**
     * Function that must be implemented by all Listeners for
     * ParameterChangeEvents.
     *
     * @param event The Event which triggered this function call
     */
    @Override
    public void parameterChange(ParameterChangeEvent event) {
        // TODO: Adjustable param for version
        // TODO: On selection of a new version, we need to force retrieval again and reload site data
    }
}
