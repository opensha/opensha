package org.opensha.commons.data.siteData.servlet.impl;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.impl.MuscalBasinDepth;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.util.ServerPrefUtils;

import java.io.File;
import java.io.IOException;

public class MuscalBasinDepthTo1_0_Servlet extends
        AbstractSiteDataServlet<Double> {

    private static final File FILE = new File(ServerPrefUtils.SERVER_PREFS.getTomcatProjectDir(),
            MuscalBasinDepth.DEPTH_1_0_FILE);

    public MuscalBasinDepthTo1_0_Servlet() throws IOException {
        super(new MuscalBasinDepth(SiteData.TYPE_DEPTH_TO_1_0, FILE, false));
    }
}
