package org.opensha.commons.data.siteData.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.opensha.commons.data.siteData.AbstractBinarySiteDataLoader;
import org.opensha.commons.data.siteData.AbstractSiteData;
import org.opensha.commons.data.siteData.CachedSiteDataWrapper;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.XMLUtils;

public class CVM4i26_M01_TaperBasinDepth extends AbstractBinarySiteDataLoader {
	
	public static final String NAME = "SCEC Community Velocity Model Version 4, Iteration 26, M01 w/ Taper, Basin Depth";
	public static final String SHORT_NAME = "CVM4.26-M01-Taper";
	
	public static final double minLat = 31;
	public static final double minLon = -121;
	
	private static final int nx = 1701;
	private static final int ny = 1101;
	
	private static final long MAX_FILE_POS = (nx*ny) * 4;
	
	public static final double gridSpacing = 0.005;
	
	public static final String DEPTH_2_5_FILE = "src/main/resources/data/site/CVM4i26_M01_Taper/cvmsi_taper_z2.5.firstOrSecond";
	public static final String DEPTH_1_0_FILE = "src/main/resources/data/site/CVM4i26_M01_Taper/cvmsi_taper_z1.0.firstOrSecond";
	
	public static final String SERVLET_2_5_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "SiteData/CVM4i26_M01_TaperBasinDepth_2_5";
	public static final String SERVLET_1_0_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "SiteData/CVM4i26_M01_TaperBasinDepth_1_0";
	
	/**
	 * Constructor for creating a CVM accessor using servlets
	 * 
	 * @param type
	 * @throws IOException
	 */
	public CVM4i26_M01_TaperBasinDepth(String type) throws IOException {
		this(type, null, true);
	}
	
	/**
	 * Constructor for creating a CVM accessor using either servlets or default file names
	 * 
	 * @param type
	 * @throws IOException
	 */
	public CVM4i26_M01_TaperBasinDepth(String type, boolean useServlet) throws IOException {
		this(type, null, useServlet);
	}
	
	/**
	 * Constructor for creating a CVM accessor using the given file
	 * 
	 * @param type
	 * @throws IOException
	 */
	public CVM4i26_M01_TaperBasinDepth(String type, File dataFile) throws IOException {
		this(type, dataFile, false);
	}
	
	public CVM4i26_M01_TaperBasinDepth(String type, File dataFile, boolean useServlet) throws IOException {
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
		return getDataType() + ", extracted from version 4 of the SCEC Community Velocity Model iteration 26-M01" +
				" (inversions by Po Chen and others), with taper. Extracted with UCVM on March 7 2023 by Mei-Hui Su.";
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
	
	public static CVM4i26_M01_TaperBasinDepth fromXMLParams(org.dom4j.Element paramsElem) throws IOException {
		boolean useServlet = Boolean.parseBoolean(paramsElem.attributeValue("useServlet"));
		Attribute fileAtt = paramsElem.attribute("fileName");
		File file = null;
		if (fileAtt != null)
			file = new File(fileAtt.getStringValue());
		String type = paramsElem.attributeValue("type");
		
		return new CVM4i26_M01_TaperBasinDepth(type, file, useServlet);
	}
	
	public static void main(String[] args) throws IOException {
//		CVM4i26BasinDepth local = new CVM4i26BasinDepth(SiteData.TYPE_DEPTH_TO_2_5, false);
//		
//		FileWriter fw = new FileWriter(new File("/tmp/cvm_grid_locs.txt"));
//		System.out.println("Expected Lat Bounds: "+local.calc.getMinLat()+"=>"+local.calc.getMaxLat());
//		System.out.println("Expected Lon Bounds: "+local.calc.getMinLon()+"=>"+local.calc.getMaxLon());
//		int cnt = 0;
//		for (long pos=0; pos<=MAX_FILE_POS; pos+=4) {
//			Double val = local.getValue(pos);
////			if (val > DepthTo2pt5kmPerSecParam.MAX) {
//				cnt++;
//				long x = local.calc.calcFileX(pos);
//				long y = local.calc.calcFileY(pos);
//				Location loc = local.calc.getLocationForPoint(x, y);
////				System.out.println(loc.getLatitude() + ", " + loc.getLongitude() + ": " + val);
//				fw.write((float)loc.getLatitude()+"\t"+(float)loc.getLongitude()+"\n");
////			}
//		}
//		fw.close();
//		System.out.println("Num above: " + cnt);
//		
//		System.exit(0);
		
		CVM4i26_M01_TaperBasinDepth map = new CVM4i26_M01_TaperBasinDepth(TYPE_DEPTH_TO_1_0);
		
		Document doc = XMLUtils.createDocumentWithRoot();
		org.dom4j.Element root = doc.getRootElement();
		map.getAdjustableParameterList().getParameter(PARAM_MIN_BASIN_DEPTH_DOUBLE_NAME).setValue(Double.valueOf(1.0));
		org.dom4j.Element mapEl = map.toXMLMetadata(root).element(XML_METADATA_NAME);
		XMLUtils.writeDocumentToFile(new File("/tmp/cvm4.xml"), doc);
		
		map = (CVM4i26_M01_TaperBasinDepth)AbstractSiteData.fromXMLMetadata(mapEl);
		
		System.out.println("Min: " + map.getAdjustableParameterList().getParameter(PARAM_MIN_BASIN_DEPTH_DOUBLE_NAME).getValue());
		
		CachedSiteDataWrapper<Double> cache = new CachedSiteDataWrapper<Double>(map);
//		SiteDataToXYZ.writeXYZ(map, 0.02, "/tmp/basin.txt");
		LocationList locs = new LocationList();
		locs.add(new Location(34.01920, -118.28800));
		locs.add(new Location(34.91920, -118.3200));
		locs.add(new Location(34.781920, -118.88600));
		locs.add(new Location(34.21920, -118.38600));
		locs.add(new Location(34.781920, -118.88600));
		locs.add(new Location(34.21920, -118.38600));
		locs.add(new Location(34.781920, -118.88600));
		locs.add(new Location(34.21920, -118.38600));
		locs.add(new Location(34.7920, -118.800));
		locs.add(new Location(34.2920, -118.3860));
		locs.add(new Location(34.61920, -118.18600));
		locs.add(new Location(34.7920, -118.800));
		locs.add(new Location(34.2920, -118.3860));
		locs.add(new Location(34.7920, -118.800));
		locs.add(new Location(34.2920, -118.3860));
		locs.add(new Location(34.7920, -118.800));
		locs.add(new Location(34.2920, -118.3860));
		
		map.getValues(locs);
		
		long time = System.currentTimeMillis();
		for (Location loc : locs) {
			double val = map.getValue(loc);
		}
//		ArrayList<Double> vals = cache.getValues(locs);
		double secs = (double)(System.currentTimeMillis() - time) / 1000d;
		System.out.println("Raw time: " + secs + "s");
		
		time = System.currentTimeMillis();
		for (Location loc : locs) {
			double val = cache.getValue(loc);
		}
//		ArrayList<Double> vals2 = map.getValues(locs);
		secs = (double)(System.currentTimeMillis() - time) / 1000d;
		System.out.println("Cache time: " + secs + "s");
		
		time = System.currentTimeMillis();
		for (Location loc : locs) {
			double val = map.getValue(loc);
		}
//		ArrayList<Double> vals = cache.getValues(locs);
		secs = (double)(System.currentTimeMillis() - time) / 1000d;
		System.out.println("Raw time: " + secs + "s");
		
		time = System.currentTimeMillis();
		for (Location loc : locs) {
			double val = cache.getValue(loc);
		}
//		ArrayList<Double> vals2 = map.getValues(locs);
		secs = (double)(System.currentTimeMillis() - time) / 1000d;
		System.out.println("Cache time: " + secs + "s");
		
		
		
		
//		for (double val : vals)
//			System.out.println(val);
	}

}
