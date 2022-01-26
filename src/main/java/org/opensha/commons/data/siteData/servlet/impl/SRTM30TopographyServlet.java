package org.opensha.commons.data.siteData.servlet.impl;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.data.siteData.impl.SRTM30Topography;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.util.ServerPrefUtils;

public class SRTM30TopographyServlet extends AbstractSiteDataServlet<Double> {
	
	public static final String FILE_NAME = ServerPrefUtils.SERVER_PREFS.getDataDir().getAbsolutePath()
			+File.separator+"siteData"+File.separator+"srtm30_v2.0";
	
	public SRTM30TopographyServlet() throws IOException {
		super(new SRTM30Topography(FILE_NAME));
	}
}
