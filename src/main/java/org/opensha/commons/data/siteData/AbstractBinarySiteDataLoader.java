package org.opensha.commons.data.siteData;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import org.opensha.commons.data.siteData.servlet.SiteDataServletAccessor;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.binFile.BinaryMesh2DCalculator.DataType;
import org.opensha.commons.util.binFile.GeolocatedRectangularBinaryMesh2DCalculator;

import com.google.common.base.Preconditions;

public abstract class AbstractBinarySiteDataLoader extends AbstractSiteData<Double> {
	
	protected static final String s = File.separator;
	
	private int nx;
	private int ny;
	private double minLat;
	private double minLon;
	private double gridSpacing;
	private boolean startBottom;
	private boolean startLeft;
	private String type;
	protected boolean useServlet;
	protected File dataFile; 
	
	protected GeolocatedRectangularBinaryMesh2DCalculator calc;
	private SiteDataServletAccessor<Double> servlet = null;
	private RandomAccessFile file;
	private byte[] recordBuffer;
	private FloatBuffer floatBuff;
	
	protected long maxFilePos;
	
	public AbstractBinarySiteDataLoader(int nx, int ny, double minLat, double minLon,
			double gridSpacing, boolean startBottom, boolean startLeft,
			String type, File dataFile, boolean useServlet) throws IOException {
		super();
		
		this.nx = nx;
		this.ny = ny;
		this.minLat = minLat;
		this.minLon = minLon;
		this.gridSpacing = gridSpacing;
		this.startBottom = startBottom;
		this.startLeft = startLeft;
		this.type = type;
		this.useServlet = useServlet;
		
		maxFilePos = (nx*ny - 1) * 4;
		
		calc = new GeolocatedRectangularBinaryMesh2DCalculator(
				DataType.FLOAT, nx, ny, minLat, minLon, gridSpacing);
		
		if (useServlet) {
			servlet = new SiteDataServletAccessor<Double>(this, getServletURL(type));
		} else {
			if (dataFile == null) {
				dataFile = getDefaultFile(type);
			}
			
			file = new RandomAccessFile(dataFile, "r");
			
			calc.setStartBottom(true);
			calc.setStartLeft(true);
			
			recordBuffer = new byte[4];
			ByteBuffer record = ByteBuffer.wrap(recordBuffer);
			record.order(ByteOrder.LITTLE_ENDIAN);
			
			floatBuff = record.asFloatBuffer();
		}
		if (isBasinDepth()) {
			initDefaultBasinParams();
			this.paramList.addParameter(minBasinDoubleParam);
			this.paramList.addParameter(maxBasinDoubleParam);
		}
		
		this.dataFile = dataFile;
	}
	
	private boolean isBasinDepth() {
		return getDataType().equals(TYPE_DEPTH_TO_1_0) || getDataType().equals(TYPE_DEPTH_TO_2_5);
	}
	
	protected abstract File getDefaultFile(String type);
	
	protected abstract String getServletURL(String type);
	
	/**
	 * This can be called to change the datafile in the case of a parameter change
	 * @param dataFile
	 * @throws FileNotFoundException
	 */
	protected void setDataFile(File dataFile) throws FileNotFoundException {
		Preconditions.checkNotNull(dataFile, "data file cannot be null.");
		Preconditions.checkArgument(dataFile.exists(), dataFile.getAbsolutePath()+" doesn't exist!");
//		System.out.println("Set data file to: "+dataFile.getAbsolutePath());
		this.dataFile = dataFile;
		file = new RandomAccessFile(dataFile, "r");
	}
	
	public final Region getApplicableRegion() {
		return calc.getApplicableRegion();
	}

	public final Location getClosestDataLocation(Location loc) {
		return calc.calcClosestLocation(loc);
	}
	
	public final double getResolution() {
		return gridSpacing;
	}

	public final String getDataType() {
		return type;
	}
	
	public Double getValue(Location loc) throws IOException {
		if (useServlet) {
			double val = servlet.getValue(loc);
			if (Double.isNaN(val))
				return val;
			if (isBasinDepth())
				return certifyMinMaxBasinDepth(val);
			return val;
		} else {
			long pos = calc.calcClosestLocationFileIndex(loc);
			
			return getValue(pos);
		}
	}
	
	protected Double getValue(long pos) throws IOException {
		if (pos > maxFilePos || pos < 0)
			return Double.NaN;
		
		file.seek(pos);
		file.read(recordBuffer);
		
		// this is in meters
		double val = floatBuff.get(0);
		
		if (isBasinDepth()) {
			if (val < 0)
				return Double.NaN;
			// convert to KM
			Double dobVal = (double)val / 1000d;
			return certifyMinMaxBasinDepth(dobVal);
		}
		return val;
	}
	
	public final ArrayList<Double> getValues(LocationList locs) throws IOException {
		if (useServlet) {
			ArrayList<Double> vals = servlet.getValues(locs);
			if (isBasinDepth()) {
				for (int i=0; i<vals.size(); i++) {
					vals.set(i, certifyMinMaxBasinDepth(vals.get(i)));
				}
			}
			return vals;
		} else {
			return super.getValues(locs);
		}
	}

	public final boolean isValueValid(Double val) {
		if (isBasinDepth() && val < 0)
			return false;
		return val != null && !Double.isNaN(val);
	}

}
