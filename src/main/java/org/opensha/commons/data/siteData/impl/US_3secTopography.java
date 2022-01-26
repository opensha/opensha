package org.opensha.commons.data.siteData.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

import org.opensha.commons.data.siteData.AbstractSiteData;
import org.opensha.commons.data.siteData.servlet.SiteDataServletAccessor;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.topo.NED_Convert;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.binFile.BinaryMesh2DCalculator;
import org.opensha.commons.util.binFile.GeolocatedRectangularBinaryMesh2DCalculator;
import org.opensha.commons.util.binFile.BinaryMesh2DCalculator.DataType;

public class US_3secTopography extends AbstractSiteData<Double> {
	
	public static final String NAME = "US 3 Sec Topography from USGS NED";
	public static final String SHORT_NAME = "US3secNED";
	
	public static final double arcSecondSpacing = 3d;
	public static final double spacing = GeoTools.secondsToDeg(arcSecondSpacing);
	
//	public static final int nx = 15601;
//	public static final int ny = 13201;
	
//	public static final double minLon = -126;
//	public static final double minLat = 32;
	
	public static final int nx = 81601;
	public static final int ny = 38401;
	
	public static final double minLon = -128;
	public static final double minLat = 20;
	
	public static final String SERVLET_URL = ServerPrefUtils.SERVER_PREFS.getServletBaseURL() + "SiteData/"+SHORT_NAME;
	
	private boolean useServlet;
	
	private Region region;
	
	private RandomAccessFile file = null;
	private byte[] recordBuffer = null;
	private FloatBuffer floatBuff = null;
	
	private GeolocatedRectangularBinaryMesh2DCalculator calc = null;
	
	private SiteDataServletAccessor<Double> servlet = null;
	
	public US_3secTopography() throws IOException {
		this(null, true);
	}
	
	public US_3secTopography(File file) throws IOException {
		this(file, false);
	}
	
	private US_3secTopography(File file, boolean useServlet) throws IOException {
		this.useServlet = useServlet;
		if (useServlet) {
			servlet = new SiteDataServletAccessor<Double>(this, SERVLET_URL);
		} else {
			this.file = new RandomAccessFile(file, "r");
			
			recordBuffer = new byte[4];
			ByteBuffer record = ByteBuffer.wrap(recordBuffer);
			record.order(ByteOrder.BIG_ENDIAN);
			floatBuff = record.asFloatBuffer();
		}
		
		calc = new GeolocatedRectangularBinaryMesh2DCalculator(
				DataType.FLOAT, nx, ny, minLat, minLon, spacing);
//		System.out.println("Max pos: "+calc.getMaxFilePos()+" (spacing="+spacing+")");
		
		calc.setStartBottom(true);
		calc.setStartLeft(true);
		
		region = new Region(new Location(minLat, minLon), new Location(calc.getMaxLat(), calc.getMaxLon()));
	}

	public Region getApplicableRegion() {
		return region;
	}

	public Location getClosestDataLocation(Location loc) throws IOException {
		return calc.calcClosestLocation(loc);
	}

	public String getMetadata() {
		// TODO
		return "Topography from USGS National Elevelation Dataset (NED) 1 arcsecond dataset." +
				"\n\n" +
				"Downloaded from: http://ned.usgs.gov/\n\n (May, 2014)\n\n" +
				"Converted to 3 arcsecond and stiched to a single file by "+NED_Convert.class;
	}

	public String getName() {
		return NAME;
	}

	public double getResolution() {
		return spacing;
	}

	public String getShortName() {
		return SHORT_NAME;
	}

	public String getDataType() {
		return TYPE_ELEVATION;
	}

	public String getDataMeasurementType() {
		return TYPE_FLAG_MEASURED;
	}

	public Double getValue(Location loc) throws IOException {
		if (useServlet) {
			return servlet.getValue(loc);
		} else {
			long pos = calc.calcClosestLocationFileIndex(loc);
			
			if (pos < 0 || pos > calc.getMaxFilePos())
				return Double.NaN;
			
			file.seek(pos);
			file.read(recordBuffer);
			
			float val = floatBuff.get(0);
			
			return (double)val;
		}
	}
	
	public ArrayList<Double> getValues(LocationList locs) throws IOException {
		if (useServlet) {
			return servlet.getValues(locs);
		} else {
			return super.getValues(locs);
		}
	}

	public boolean isValueValid(Double val) {
		return val != null && !Double.isNaN(val);
	}
	
	public static void main(String args[]) throws IOException {
//		SRTM30Topography data = new SRTM30Topography("/home/kevin/data/topo30");
		US_3secTopography data = new US_3secTopography();
		
		System.out.println(data.getValue(new Location(34, -118)));
		// top of mammoth
		System.out.println(data.getValue(new Location(37.630173, -119.032681)));
		
//		EvenlyGriddedRectangularGeographicRegion region = new EvenlyGriddedRectangularGeographicRegion(32, 35, -121, -115, 0.02);
//		EvenlyGriddedRectangularGeographicRegion region = new EvenlyGriddedRectangularGeographicRegion(-60, 60, -180, 180, 1);
		
//		SiteDataToXYZ.writeXYZ(data, region, "/tmp/topo2.txt");
	}
}
