package org.opensha.commons.data.siteData.impl;

import org.opensha.commons.data.siteData.AbstractBinarySiteDataLoader;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ServerPrefUtils;

import java.io.File;
import java.io.IOException;

public class Muscal26_BasinDepth extends AbstractBinarySiteDataLoader {

    public static final String NAME = "SCEC Multi-Scale California (MUSCAL) 2026 Basin Depth";
    public static final String SHORT_NAME = "MUSCAL26";

    public static final double minLat = 32.2;
    public static final double minLon = -124.4;

    private static final int nx = 2061;
    private static final int ny = 1981;

    private static final long MAX_FILE_POS = (nx*ny) * 4;

    public static final double gridSpacing = 0.005;

    public static final String DEPTH_2_5_FILE = "src/main/resources/data/site/MUSCAL26/muscal_z2.5.firstOrSecond";
    public static final String DEPTH_1_0_FILE = "src/main/resources/data/site/MUSCAL26/muscal_z1.0.firstOrSecond";

    public static final String SERVLET_2_5_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "SiteData/Muscal26_2_5";
    public static final String SERVLET_1_0_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "SiteData/Muscal26_1_0";

    /**
     * Constructor for creating a CVM accessor using servlets
     *
     * @param type
     * @throws IOException
     */
    public Muscal26_BasinDepth(String type) throws IOException {
        this(type, null, true);
    }

    /**
     * Constructor for creating a CVM accessor using either servlets or default file names
     *
     * @param type
     * @throws IOException
     */
    public Muscal26_BasinDepth(String type, boolean useServlet) throws IOException {
        this(type, null, useServlet);
    }

    /**
     * Constructor for creating a CVM accessor using the given file
     *
     * @param type
     * @throws IOException
     */
    public Muscal26_BasinDepth(String type, File dataFile) throws IOException {
        this(type, dataFile, false);
    }

    public Muscal26_BasinDepth(String type, File dataFile, boolean useServlet) throws IOException {
        super(nx, ny, minLat, minLon, gridSpacing, true, true, type, dataFile, useServlet);
    }

    @Override
    protected File getDefaultFile(String type) {
        if (type.equals(TYPE_DEPTH_TO_1_0))
            return new File(DEPTH_1_0_FILE);
        return new File(DEPTH_2_5_FILE);
    }

    @Override
    protected String getServletURL(String type) {
        if (type.equals(TYPE_DEPTH_TO_1_0))
            return SERVLET_1_0_URL;
        return SERVLET_2_5_URL;
    }

    public String getName() {
        return NAME;
    }

    public String getShortName() {
        return SHORT_NAME;
    }

    public String getMetadata() {
        return getDataType() + ", extracted from 2026 version of the SCEC Multi-scale California 3D velocity model.";
    }

    // TODO: what should we set this to?
    public String getDataMeasurementType() {
        return TYPE_FLAG_INFERRED;
    }

//    @Override
//    protected Element addXMLParameters(Element paramsEl) {
//        paramsEl.addAttribute("useServlet", this.useServlet + "");
//        if (this.dataFile != null)
//            paramsEl.addAttribute("fileName", this.dataFile.getPath());
//        paramsEl.addAttribute("type", getDataType());
//        return super.addXMLParameters(paramsEl);
//    }
//
//    public static Muscal26_BasinDepth fromXMLParams(org.dom4j.Element paramsElem) throws IOException {
//        boolean useServlet = Boolean.parseBoolean(paramsElem.attributeValue("useServlet"));
//        Attribute fileAtt = paramsElem.attribute("fileName");
//        File file = null;
//        if (fileAtt != null)
//            file = new File(fileAtt.getStringValue());
//        String type = paramsElem.attributeValue("type");
//
//        return new Muscal26_BasinDepth(type, file, useServlet);
//    }

    public static void main(String[] args) throws IOException {
        boolean servlet = true;
        Muscal26_BasinDepth z1 = new Muscal26_BasinDepth(SiteData.TYPE_DEPTH_TO_1_0, servlet);
        Muscal26_BasinDepth z25 = new Muscal26_BasinDepth(SiteData.TYPE_DEPTH_TO_2_5, servlet);

        Region reg = z1.getApplicableRegion();
        LocationList testLocs = new GriddedRegion(reg, 0.25, GriddedRegion.ANCHOR_0_0).getNodeList();

        System.out.println(reg.getMinLat()+", "+reg.getMinLon());
        System.out.println(reg.getMaxLat()+", "+reg.getMaxLon());

        for (Location loc : testLocs)
            System.out.println((float)loc.getLongitude()+"\t"+(float)loc.getLatitude()
                    +"\t"+(z1.getValue(loc)*1000d)+"\t"+(z25.getValue(loc)*1000d));
        System.exit(0);
    }

}
