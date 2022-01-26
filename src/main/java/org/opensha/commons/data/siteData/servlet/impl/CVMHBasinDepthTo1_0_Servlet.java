package org.opensha.commons.data.siteData.servlet.impl;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.impl.CVMHBasinDepth;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.util.ServerPrefUtils;

public class CVMHBasinDepthTo1_0_Servlet extends
		AbstractSiteDataServlet<Double> {
	
	private static final File DIR = new File(ServerPrefUtils.SERVER_PREFS.getTomcatProjectDir(),
										CVMHBasinDepth.DEFAULT_DATA_DIR);
	
	public CVMHBasinDepthTo1_0_Servlet() throws IOException {
		super(new CVMHBasinDepth(SiteData.TYPE_DEPTH_TO_1_0, DIR, false));
	}
}
