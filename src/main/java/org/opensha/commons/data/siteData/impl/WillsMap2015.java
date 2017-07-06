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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import org.dom4j.Element;
import org.opensha.commons.data.siteData.AbstractSiteData;
import org.opensha.commons.data.siteData.servlet.SiteDataServletAccessor;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.binFile.BinaryMesh2DCalculator.DataType;
import org.opensha.commons.util.binFile.GeolocatedRectangularBinaryMesh2DCalculator;

public class WillsMap2015 extends AbstractSiteData<Double> {
	
	public static final int nx = 41104;	// NCOLS
	public static final int ny = 37900;	// NROWS
	
	public static final double spacing = 0.00025;
	
	public static final double startLon = -124.406528862058;	// ULXMAP
	public static final double startLat = 42.0090923024621;	// ULYMAP
	
	public static final boolean startBottom = false;
	public static final boolean startLeft = true;
	
	public static final String NAME = "CGS/Wills VS30 Map (2015)";
	public static final String SHORT_NAME = "Wills2015";
	
	public static final String SERVER_BIN_FILE = 
			ServerPrefUtils.SERVER_PREFS.getDataDir().getAbsolutePath()
				+File.separator+"siteData"+File.separator+"wills_2015"+File.separator+"raster_0.00025.flt";
	public static final String DEBUG_BIN_FILE = "/home/kevin/OpenSHA/siteClass/out.bin";
	
	public static final String SERVLET_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL()
			+"SiteData/Wills2015";
	
	private Region applicableRegion;
	
	private RandomAccessFile file = null;
	private String fileName = null;
	private byte[] recordBuffer = null;
	private FloatBuffer floatBuff = null;
	
	private GeolocatedRectangularBinaryMesh2DCalculator calc = null;
	
	private boolean useServlet;
	
	private SiteDataServletAccessor<Double> servlet = null;
	
	public WillsMap2015() throws IOException {
		this(true, null);
//		this(false, "/home/kevin/OpenSHA/wills_2015/raster_0.0005.flt");
	}
	
	public WillsMap2015(String dataFile) throws IOException {
		this(false, dataFile);
	}
	
	private WillsMap2015(boolean useServlet, String dataFile) throws IOException {
		super();
		this.useServlet = useServlet;
		this.fileName = dataFile;
		
		calc = new GeolocatedRectangularBinaryMesh2DCalculator(
				DataType.FLOAT, nx, ny, startLat, startLon, startBottom, startLeft, spacing);
		
		applicableRegion = calc.getApplicableRegion();
		
		if (useServlet) {
			servlet = new SiteDataServletAccessor<Double>(this, SERVLET_URL);
		} else {
			file = new RandomAccessFile(new File(dataFile), "r");
			
			recordBuffer = new byte[4];
			ByteBuffer record = ByteBuffer.wrap(recordBuffer);
			record.order(ByteOrder.LITTLE_ENDIAN);
			floatBuff = record.asFloatBuffer();
		}
		initDefaultVS30Params();
		this.paramList.addParameter(minVs30Param);
		this.paramList.addParameter(maxVs30Param);
	}

	public Region getApplicableRegion() {
		return applicableRegion;
	}

	public Location getClosestDataLocation(Location loc) {
		return calc.calcClosestLocation(loc);
	}

	public String getName() {
		return NAME;
	}
	
	public String getShortName() {
		return SHORT_NAME;
	}
	
	public String getMetadata() {
		return "Vs30 values as described in:\n\n" +
				"A Next Generation VS30 Map for California Based on Geology and Topography\n"+
				"by C. J. Wills, C. I. Gutierrez, F. G. Perez, and D. M. Branum\n"+
				"Bulletin of the Seismological Society of America; December 2015; v. 105; no. 6; p. 3083-3091\n\n"+
				"The dataset contains Vs obtained from the electronic supplements, specifically "+
				"'VsMapV3_20150714_shapefile.zip', fetched 2/23/17.\n\n"+
				"Data reprojected from EPSG:3310 to EPSG:4326 with QGIS, then rasterized with the "+
				"'gdal_rasterize' linux command, GDAL 1.10.0. It has a grid spacing of "+spacing+" degrees";
	}

	public double getResolution() {
		return spacing;
	}

	public String getDataType() {
		return TYPE_VS30;
	}
	
	public String getDataMeasurementType() {
		return TYPE_FLAG_INFERRED;
	}

	public Double getValue(Location loc) throws IOException {
		if (useServlet) {
			return certifyMinMaxVs30(servlet.getValue(loc));
		} else {
			long pos = calc.calcClosestLocationFileIndex(loc);
			
			if (pos < 0 || pos > calc.getMaxFilePos())
				return Double.NaN;
			
			file.seek(pos);
			file.read(recordBuffer);
			
			double val = floatBuff.get(0);
			
			if (val <= 0)
				return Double.NaN;
			
			// round to get rid of float nastiness
			val = Math.round(val*100d)/100d;
			
			return certifyMinMaxVs30((double)val);
		}
	}

	public ArrayList<Double> getValues(LocationList locs) throws IOException {
		if (useServlet) {
			ArrayList<Double> vals = servlet.getValues(locs);
			for (int i=0; i<vals.size(); i++) {
				vals.set(i, certifyMinMaxVs30(vals.get(i)));
			}
			return vals;
		} else {
			return super.getValues(locs);
		}
	}
	
	public boolean isValueValid(Double val) {
		return val != null && !Double.isNaN(val);
	}
	
	@Override
	protected Element addXMLParameters(Element paramsEl) {
		paramsEl.addAttribute("useServlet", this.useServlet + "");
		paramsEl.addAttribute("fileName", this.fileName);
		return super.addXMLParameters(paramsEl);
	}
	
	public static WillsMap2015 fromXMLParams(org.dom4j.Element paramsElem) throws IOException {
		boolean useServlet = Boolean.parseBoolean(paramsElem.attributeValue("useServlet"));
		String fileName = paramsElem.attributeValue("fileName");
		
		return new WillsMap2015(useServlet, fileName);
	}
	
	public static void main(String[] args) throws IOException {
		
		WillsMap2015 map = new WillsMap2015();
		
		GriddedRegion region = 
			new GriddedRegion(
					new Location(37, -122.75),
					new Location(38.5, -121.5),
					0.01, new Location(0,0));
		
//		SiteDataToXYZ.writeXYZ(map, region, "/tmp/wills.txt");
		
//		SiteDataServletAccessor<Double> serv = new SiteDataServletAccessor<Double>(SERVLET_URL);
//		
		LocationList locs = new LocationList();
		locs.add(new Location(34.01920, -118.28800));
		locs.add(new Location(34.91920, -118.3200));
		locs.add(new Location(34.781920, -118.88600));
		locs.add(new Location(34.21920, -118.38600));
		locs.add(new Location(34.61920, -118.18600));
		
		ArrayList<Double> vals = map.getValues(locs);
		for (double val : vals)
			System.out.println(val);
	}

}
