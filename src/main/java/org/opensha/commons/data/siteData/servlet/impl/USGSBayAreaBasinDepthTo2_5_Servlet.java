package org.opensha.commons.data.siteData.servlet.impl;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.impl.USGSBayAreaBasinDepth;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.util.ServerPrefUtils;

public class USGSBayAreaBasinDepthTo2_5_Servlet extends
		AbstractSiteDataServlet<Double> {
	
	private static final String FILE = ServerPrefUtils.SERVER_PREFS.getTomcatProjectDir().getAbsolutePath()
											+File.separator+USGSBayAreaBasinDepth.DEPTH_2_5_FILE;
	
	public USGSBayAreaBasinDepthTo2_5_Servlet() throws IOException {
		super(new USGSBayAreaBasinDepth(SiteData.TYPE_DEPTH_TO_2_5, FILE, false));
	}
}
