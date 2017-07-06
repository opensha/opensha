/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

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

public class CVM_CCAi6BasinDepth extends AbstractBinarySiteDataLoader {
	
	public static final String NAME = "SCEC CCA, Iteration 6, Basin Depth";
	public static final String SHORT_NAME = "CCAi6";
	
	public static final double minLat = 33.35;
	public static final double minLon = -123;
	
	private static final int nx = 1551;
	private static final int ny = 1201;
	
	private static final long MAX_FILE_POS = (nx*ny) * 4;
	
	public static final double gridSpacing = 0.005;
	
	public static final String DEPTH_2_5_FILE = "src/resources/data/site/CCAi6/depth_2.5.bin";
	public static final String DEPTH_1_0_FILE = "src/resources/data/site/CCAi6/depth_1.0.bin";
	
	public static final String SERVLET_2_5_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "SiteData/CVM_CCAi6_2_5";
	public static final String SERVLET_1_0_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "SiteData/CVM_CCAi6_1_0";
	
	/**
	 * Constructor for creating a CVM accessor using servlets
	 * 
	 * @param type
	 * @throws IOException
	 */
	public CVM_CCAi6BasinDepth(String type) throws IOException {
		this(type, null, true);
	}
	
	/**
	 * Constructor for creating a CVM accessor using either servlets or default file names
	 * 
	 * @param type
	 * @throws IOException
	 */
	public CVM_CCAi6BasinDepth(String type, boolean useServlet) throws IOException {
		this(type, null, useServlet);
	}
	
	/**
	 * Constructor for creating a CVM accessor using the given file
	 * 
	 * @param type
	 * @throws IOException
	 */
	public CVM_CCAi6BasinDepth(String type, File dataFile) throws IOException {
		this(type, dataFile, false);
	}
	
	public CVM_CCAi6BasinDepth(String type, File dataFile, boolean useServlet) throws IOException {
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
	
	public static CVM_CCAi6BasinDepth fromXMLParams(org.dom4j.Element paramsElem) throws IOException {
		boolean useServlet = Boolean.parseBoolean(paramsElem.attributeValue("useServlet"));
		Attribute fileAtt = paramsElem.attribute("fileName");
		File file = null;
		if (fileAtt != null)
			file = new File(fileAtt.getStringValue());
		String type = paramsElem.attributeValue("type");
		
		return new CVM_CCAi6BasinDepth(type, file, useServlet);
	}
	
	public static void main(String[] args) throws IOException {
		CVM_CCAi6BasinDepth local = new CVM_CCAi6BasinDepth(SiteData.TYPE_DEPTH_TO_1_0, false);
		
		Location outside = new Location(35, -122.5);
		double outsideVal = local.getValue(outside);
		System.out.println("Val: "+outsideVal+", valid? "+local.isValueValid(outsideVal));
		
		System.exit(0);
		
		FileWriter fw = new FileWriter(new File("/tmp/cvm_grid_locs.txt"));
		System.out.println("Expected Lat Bounds: "+local.calc.getMinLat()+"=>"+local.calc.getMaxLat());
		System.out.println("Expected Lon Bounds: "+local.calc.getMinLon()+"=>"+local.calc.getMaxLon());
		int cnt = 0;
		for (long pos=0; pos<=MAX_FILE_POS; pos+=4) {
			Double val = local.getValue(pos);
//			if (val > DepthTo2pt5kmPerSecParam.MAX) {
				cnt++;
				long x = local.calc.calcFileX(pos);
				long y = local.calc.calcFileY(pos);
				Location loc = local.calc.getLocationForPoint(x, y);
//				System.out.println(loc.getLatitude() + ", " + loc.getLongitude() + ": " + val);
				fw.write((float)loc.getLatitude()+"\t"+(float)loc.getLongitude()+"\n");
//			}
		}
		fw.close();
		System.out.println("Num above: " + cnt);
		
		System.exit(0);
		
		CVM_CCAi6BasinDepth map = new CVM_CCAi6BasinDepth(TYPE_DEPTH_TO_1_0);
		
		Document doc = XMLUtils.createDocumentWithRoot();
		org.dom4j.Element root = doc.getRootElement();
		map.getAdjustableParameterList().getParameter(PARAM_MIN_BASIN_DEPTH_DOUBLE_NAME).setValue(new Double(1.0));
		org.dom4j.Element mapEl = map.toXMLMetadata(root).element(XML_METADATA_NAME);
		XMLUtils.writeDocumentToFile(new File("/tmp/cvm4.xml"), doc);
		
		map = (CVM_CCAi6BasinDepth)AbstractSiteData.fromXMLMetadata(mapEl);
		
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
