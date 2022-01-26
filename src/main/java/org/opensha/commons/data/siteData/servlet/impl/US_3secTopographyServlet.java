package org.opensha.commons.data.siteData.servlet.impl;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.data.siteData.impl.SRTM30PlusTopography;
import org.opensha.commons.data.siteData.impl.US_3secTopography;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;

public class US_3secTopographyServlet extends AbstractSiteDataServlet<Double> {
	
	public static final String FILE_NAME = "/home/scec-01/opensha/ned_usa/us_dem_3sec.flt";
	
	public US_3secTopographyServlet() throws IOException {
		super(new US_3secTopography(new File(FILE_NAME)));
	}
}
