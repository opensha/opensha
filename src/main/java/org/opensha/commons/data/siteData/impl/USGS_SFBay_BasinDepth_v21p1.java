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
import org.opensha.commons.data.siteData.SiteDataToXYZ;
import org.opensha.commons.data.siteData.servlet.SiteDataServletAccessor;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.binFile.BinaryMesh2DCalculator;
import org.opensha.commons.util.binFile.GeolocatedRectangularBinaryMesh2DCalculator;
import org.opensha.commons.util.binFile.BinaryMesh2DCalculator.DataType;

public class USGS_SFBay_BasinDepth_v21p1 extends AbstractSiteData<Double> {
	
	public static final String NAME = "USGS SF Bay Area Velocity Model Release 21.1";
	public static final String SHORT_NAME = "USGSSFBayAreaBasin21.1";
	
	public static final double minLat = 36.9;
	public static final double minLon = -123.4;
	
	private static final int nx = 421;
	private static final int ny = 401;
	
	private static final long MAX_FILE_POS = (nx-1) * (ny-1) * 4;
	
	public static final double gridSpacing = 0.005;
	
	public static final String DEPTH_2_5_FILE = "src/main/resources/data/site/USGS_SF_21.1/sfcvm_z2.5.firstOrSecond";
	public static final String DEPTH_1_0_FILE = "src/main/resources/data/site/USGS_SF_21.1/sfcvm_z1.0.firstOrSecond";
	
	public static final String SERVLET_2_5_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "SiteData/USGS_SF_21p1_2_5";
	public static final String SERVLET_1_0_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "SiteData/USGS_SF_21p1_1_0";
	
	private RandomAccessFile file = null;
	private String fileName = null;
	
	private GeolocatedRectangularBinaryMesh2DCalculator calc = null;
	
	private byte[] recordBuffer = null;
	private FloatBuffer floatBuff = null;
	
	private boolean useServlet;
	
	private String type;
	
	private SiteDataServletAccessor<Double> servlet = null;
	
	public USGS_SFBay_BasinDepth_v21p1(String type) throws IOException {
		this(type, null, true);
	}
	
	public USGS_SFBay_BasinDepth_v21p1(String type, String dataFile) throws IOException {
		this(type, dataFile, false);
	}

	public USGS_SFBay_BasinDepth_v21p1(String type, boolean useServlet) throws IOException {
		this(type, null, useServlet);
	}
	
	public USGS_SFBay_BasinDepth_v21p1(String type, String dataFile, boolean useServlet) throws IOException {
		super();
		this.useServlet = useServlet;
		this.fileName = dataFile;
		this.type = type;
		
		calc = new GeolocatedRectangularBinaryMesh2DCalculator(
				DataType.FLOAT, nx, ny, minLat, minLon, gridSpacing);
		
		if (useServlet) {
			if (type.equals(TYPE_DEPTH_TO_1_0))
				servlet = new SiteDataServletAccessor<Double>(this, SERVLET_1_0_URL);
			else
				servlet = new SiteDataServletAccessor<Double>(this, SERVLET_2_5_URL);
		} else {
			if (dataFile == null) {
				if (type.equals(TYPE_DEPTH_TO_1_0))
					dataFile = DEPTH_1_0_FILE;
				else
					dataFile = DEPTH_2_5_FILE;
			}
			
			file = new RandomAccessFile(new File(dataFile), "r");
			
			calc.setStartBottom(true);
			calc.setStartLeft(true);
			
			recordBuffer = new byte[4];
			ByteBuffer record = ByteBuffer.wrap(recordBuffer);
			record.order(ByteOrder.LITTLE_ENDIAN);
			
			floatBuff = record.asFloatBuffer();
		}
		initDefaultBasinParams();
		this.paramList.addParameter(minBasinDoubleParam);
		this.paramList.addParameter(maxBasinDoubleParam);
	}

	public Region getApplicableRegion() {
		return calc.getApplicableRegion();
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
		return type + ", Extracted by Mei-Hui Su on 3/22/24 from UCVM (model name 'sfcvm'), representing version 21.1 "
				+ "of the USGS SF Bay Area Velocity Model from "
				+ "https://www.sciencebase.gov/catalog/item/61817394d34e9f2789e3c36c."
				+ "\n\nAccording to Scott Callaghan, it is modified \"to include corrections to the gabbro regions.\"\n\n" +
				"It has a grid spacing of " + gridSpacing + " degrees";
	}

	public double getResolution() {
		return gridSpacing;
	}

	public String getDataType() {
		return type;
	}
	
	// TODO: what should we set this to?
	public String getDataMeasurementType() {
		return TYPE_FLAG_INFERRED;
	}

	public Double getValue(Location loc) throws IOException {
		if (useServlet) {
			return certifyMinMaxBasinDepth(servlet.getValue(loc));
		} else {
			long pos = calc.calcClosestLocationFileIndex(loc);
			
			if (pos > MAX_FILE_POS || pos < 0)
				return Double.NaN;
			
			file.seek(pos);
			file.read(recordBuffer);
			
			// this is in meters
			double val = floatBuff.get(0);
			
			if (val >= 100000000.0) {
//				System.out.println("Found a too big...");
				return Double.NaN;
			}
			
			// convert to KM
			Double dobVal = (double)val / 1000d;
			return certifyMinMaxBasinDepth(dobVal);
		}
	}

	public ArrayList<Double> getValues(LocationList locs) throws IOException {
		if (useServlet) {
			ArrayList<Double> vals = servlet.getValues(locs);
			for (int i=0; i<vals.size(); i++) {
				vals.set(i, certifyMinMaxBasinDepth(vals.get(i)));
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
		paramsEl.addAttribute("type", this.type);
		return super.addXMLParameters(paramsEl);
	}
	
	public static USGS_SFBay_BasinDepth_v21p1 fromXMLParams(org.dom4j.Element paramsElem) throws IOException {
		boolean useServlet = Boolean.parseBoolean(paramsElem.attributeValue("useServlet"));
		String fileName = paramsElem.attributeValue("fileName");
		String type = paramsElem.attributeValue("type");
		
		return new USGS_SFBay_BasinDepth_v21p1(type, fileName, useServlet);
	}
	
	public static void main(String args[]) {
		try {
//			USGS_SFBay_BasinDepth_v21p1 cvm = new USGS_SFBay_BasinDepth_v21p1(TYPE_DEPTH_TO_2_5, DEPTH_2_5_FILE, false);
			USGS_SFBay_BasinDepth_v21p1 cvm = new USGS_SFBay_BasinDepth_v21p1(TYPE_DEPTH_TO_1_0, DEPTH_1_0_FILE, false);
//			EvenlyGriddedRectangularGeographicRegion region = new EvenlyGriddedRectangularGeographicRegion(37, 38.5, -122.75, -121.5, 0.01);
//			SiteDataToXYZ.writeXYZ(cvm, region, "/tmp/sfbasin.txt");
			SiteDataToXYZ.writeXYZ(cvm, 0.05, "/tmp/sfbasin.txt");
			
			System.out.println(cvm.getValue(new Location(37.743302641211756, -122.45461093949983)));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
