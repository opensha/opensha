package org.opensha.commons.data.siteData.servlet.impl;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.impl.USGS_SFBay_BasinDepth_v21p1;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.util.ServerPrefUtils;

public class USGS_SFBay_BasinDepth_v21p1To2_5_Servlet extends
		AbstractSiteDataServlet<Double> {
	
	private static final String FILE = ServerPrefUtils.SERVER_PREFS.getTomcatProjectDir().getAbsolutePath()
										+File.separator+USGS_SFBay_BasinDepth_v21p1.DEPTH_2_5_FILE;
	
	public USGS_SFBay_BasinDepth_v21p1To2_5_Servlet() throws IOException {
		super(new USGS_SFBay_BasinDepth_v21p1(SiteData.TYPE_DEPTH_TO_2_5, FILE, false));
	}
}
