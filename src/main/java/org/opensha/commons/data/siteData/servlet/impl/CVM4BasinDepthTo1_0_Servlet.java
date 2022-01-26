package org.opensha.commons.data.siteData.servlet.impl;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.impl.CVM4BasinDepth;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.util.ServerPrefUtils;

public class CVM4BasinDepthTo1_0_Servlet extends
		AbstractSiteDataServlet<Double> {
	
	private static final File FILE = new File(ServerPrefUtils.SERVER_PREFS.getTomcatProjectDir(),
										CVM4BasinDepth.DEPTH_1_0_FILE);
	
	public CVM4BasinDepthTo1_0_Servlet() throws IOException {
		super(new CVM4BasinDepth(SiteData.TYPE_DEPTH_TO_1_0, FILE, false));
	}
}
