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

    public static final String VERSION_PARAM_NAME = "Version";
    private EnumParameter<CONUS_Versions> versionParam;
    public static final CONUS_Versions VERSION_DEFAULT = CONUS_Versions.NSHM23;

    public CONUS_SiteDataProvider(String type) throws IOException {
        super(type, new CONUS_Downloader(VERSION_DEFAULT), NSHM23_RegionLoader.loadFullConterminousUS());
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
        return "Conterminous U.S. (CONUS) Site Data from the National Seismic Hazard Model (NSHM)";
    }

    /**
     * Function that must be implemented by all Listeners for
     * ParameterChangeEvents.
     *
     * @param event The Event which triggered this function call
     */
    @Override
    public void parameterChange(ParameterChangeEvent event) {
        if (event.getParameter() == versionParam) {
            CONUS_Versions newVersion = versionParam.getValue();
            loadSiteData(new CONUS_Downloader(newVersion));
        }
    }
}
