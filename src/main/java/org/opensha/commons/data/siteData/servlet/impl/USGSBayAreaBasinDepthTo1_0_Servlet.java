package org.opensha.commons.data.siteData.servlet.impl;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.impl.USGSBayAreaBasinDepth;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.util.ServerPrefUtils;

public class USGSBayAreaBasinDepthTo1_0_Servlet extends
		AbstractSiteDataServlet<Double> {
	
	private static final String FILE = ServerPrefUtils.SERVER_PREFS.getTomcatProjectDir().getAbsolutePath()
										+File.separator+USGSBayAreaBasinDepth.DEPTH_1_0_FILE;
	
	public USGSBayAreaBasinDepthTo1_0_Servlet() throws IOException {
		super(new USGSBayAreaBasinDepth(SiteData.TYPE_DEPTH_TO_1_0, FILE, false));
	}
}
