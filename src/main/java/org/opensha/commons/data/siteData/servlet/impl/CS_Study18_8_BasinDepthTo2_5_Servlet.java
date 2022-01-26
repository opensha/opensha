package org.opensha.commons.data.siteData.servlet.impl;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.impl.CS_Study18_8_BasinDepth;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.util.ServerPrefUtils;

public class CS_Study18_8_BasinDepthTo2_5_Servlet extends
		AbstractSiteDataServlet<Double> {
	
	private static final File FILE = new File(ServerPrefUtils.SERVER_PREFS.getTomcatProjectDir(),
			CS_Study18_8_BasinDepth.DEPTH_2_5_FILE);
	
	public CS_Study18_8_BasinDepthTo2_5_Servlet() throws IOException {
		super(new CS_Study18_8_BasinDepth(SiteData.TYPE_DEPTH_TO_2_5, FILE, false));
	}
}
