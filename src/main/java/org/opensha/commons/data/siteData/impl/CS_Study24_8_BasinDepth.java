package org.opensha.commons.data.siteData.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.opensha.commons.data.siteData.AbstractBinarySiteDataLoader;
import org.opensha.commons.data.siteData.AbstractSiteData;
import org.opensha.commons.data.siteData.CachedSiteDataWrapper;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.XMLUtils;

public class CS_Study24_8_BasinDepth extends AbstractBinarySiteDataLoader {
	
	public static final String NAME = "SCEC CyberShake Study 24.8 Stitched Basin Depth";
	public static final String SHORT_NAME = "CS24_8";
	
	public static final double minLat = 36.9;
	public static final double minLon = -123.4;
	
	private static final int nx = 421;
	private static final int ny = 401;
	
	private static final long MAX_FILE_POS = (nx*ny) * 4;
	
	public static final double gridSpacing = 0.005;
	
	public static final String DEPTH_2_5_FILE = "src/main/resources/data/site/CS_24_8/sfcvm_z2.5.firstOrSecond";
	public static final String DEPTH_1_0_FILE = "src/main/resources/data/site/CS_24_8/sfcvm_z1.0.firstOrSecond";
	
	public static final String SERVLET_2_5_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "SiteData/CS24_8_2_5";
	public static final String SERVLET_1_0_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "SiteData/CS24_8_1_0";
	
	/**
	 * Constructor for creating a CVM accessor using servlets
	 * 
	 * @param type
	 * @throws IOException
	 */
	public CS_Study24_8_BasinDepth(String type) throws IOException {
		this(type, null, true);
	}
	
	/**
	 * Constructor for creating a CVM accessor using either servlets or default file names
	 * 
	 * @param type
	 * @throws IOException
	 */
	public CS_Study24_8_BasinDepth(String type, boolean useServlet) throws IOException {
		this(type, null, useServlet);
	}
	
	/**
	 * Constructor for creating a CVM accessor using the given file
	 * 
	 * @param type
	 * @throws IOException
	 */
	public CS_Study24_8_BasinDepth(String type, File dataFile) throws IOException {
		this(type, dataFile, false);
	}
	
	public CS_Study24_8_BasinDepth(String type, File dataFile, boolean useServlet) throws IOException {
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
		return getDataType() + ", extracted from version 4 of the SCEC Community Velocity Model iteration 26" +
				" (inversions by Po Chen and others). Extracted with UCVM 13.9.0 on November 26 2012 by David Gill";
	}
	
	// TODO: what should we set this to?
	public String getDataMeasurementType() {
		return TYPE_FLAG_INFERRED;
	}
	
	@Override
	protected Element addXMLParameters(Element paramsEl) {
		paramsEl.addAttribute("useServlet", this.useServlet + "");
		if (this.dataFile != null)
			paramsEl.addAttribute("fileName", this.dataFile.getPath());
		paramsEl.addAttribute("type", getDataType());
		return super.addXMLParameters(paramsEl);
	}
	
	public static CS_Study24_8_BasinDepth fromXMLParams(org.dom4j.Element paramsElem) throws IOException {
		boolean useServlet = Boolean.parseBoolean(paramsElem.attributeValue("useServlet"));
		Attribute fileAtt = paramsElem.attribute("fileName");
		File file = null;
		if (fileAtt != null)
			file = new File(fileAtt.getStringValue());
		String type = paramsElem.attributeValue("type");
		
		return new CS_Study24_8_BasinDepth(type, file, useServlet);
	}
	
	public static void main(String[] args) throws IOException {
		boolean servlet = true;
		CS_Study24_8_BasinDepth z1 = new CS_Study24_8_BasinDepth(SiteData.TYPE_DEPTH_TO_1_0, servlet);
		CS_Study24_8_BasinDepth z25 = new CS_Study24_8_BasinDepth(SiteData.TYPE_DEPTH_TO_2_5, servlet);
		
//		ArrayList<Location> testLocs = new ArrayList<>();
//		testLocs.add(new Location(35.8, -121.25));
//		testLocs.add(new Location(35.8, -120.4));
//		testLocs.add(new Location(35.8, -119.5));
//		testLocs.add(new Location(35.8, -118.6));
//		testLocs.add(new Location(35.5, -120));
//		testLocs.add(new Location(35, -119.5));
//		testLocs.add(new Location(36, -121.0));
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
