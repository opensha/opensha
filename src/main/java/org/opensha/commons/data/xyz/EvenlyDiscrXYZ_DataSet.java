package org.opensha.commons.data.xyz;

import java.awt.geom.Point2D;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.util.FileUtils;

import com.google.common.base.Preconditions;

/**
 * This class represents an evenly discretized XYZ dataset. Data is stored as an array, and set/get
 * operations will use the closest point in the data if it's not exact.
 * 
 * @author kevin
 *
 */
public class EvenlyDiscrXYZ_DataSet extends AbstractXYZ_DataSet {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private double data[][];
	
	private int ny;
	private int nx;
	private double minX;
	private double maxX;
	private double minY;
	private double maxY;
	private double gridSpacingX;
	private double gridSpacingY;
	
	public EvenlyDiscrXYZ_DataSet(int nx, int ny, double minX, double minY, double gridSpacing) {
		this(nx, ny, minX, minY, gridSpacing, gridSpacing);
	}
	
	public EvenlyDiscrXYZ_DataSet(int nx, int ny, double minX, double minY, double gridSpacingX, double gridSpacingY) {
		this(new double[ny][nx], minX, minY, gridSpacingX, gridSpacingY);
	}
	
	public EvenlyDiscrXYZ_DataSet(double[][] data, double minX, double minY, double gridSpacing) {
		this(data, minX, minY, gridSpacing, gridSpacing);
	}
	
	public EvenlyDiscrXYZ_DataSet(double[][] data, double minX, double minY, double gridSpacingX, double gridSpacingY) {
		this.data = data;
		this.minX = minX;
		this.minY = minY;
		this.gridSpacingX = gridSpacingX;
		this.gridSpacingY = gridSpacingY;
		
		this.ny = data.length;
		this.nx = data[0].length;
		
		maxX = minX + gridSpacingX * (nx-1);
		maxY = minY + gridSpacingY * (ny-1);
		
//		System.out.println("EvenlyDiscretizedXYZ_DataSet: minX: " + minX + ", maxX: " + maxX
//				+ ", minY: " + minY + ", maxY: " + maxY);
	}

	public double getMaxX() {
		return maxX;
	}

	public double getMaxY() {
		return maxY;
	}

	public double getMinX() {
		return minX;
	}

	public double getMinY() {
		return minY;
	}
	
	/**
	 * Get the grid spacing of this evenly discretized dataset in the x dimension
	 * @return
	 */
	public double getGridSpacingX() {
		return gridSpacingX;
	}
	
	/**
	 * Get the grid spacing of this evenly discretized dataset in the y dimension
	 * @return
	 */
	public double getGridSpacingY() {
		return gridSpacingY;
	}
	
	public int getNumX() {
		return nx;
	}
	
	public int getNumY() {
		return ny;
	}
	
	public void writeXYZBinFile(String fileNamePrefix) throws IOException {
		FileWriter header = new FileWriter(fileNamePrefix + ".hdr");
		header.write("ncols" + "\t" + nx + "\n");
		header.write("nrows" + "\t" + ny + "\n");
		header.write("xllcorner" + "\t" + minX + "\n");
		header.write("yllcorner" + "\t" + minY + "\n");
		if (gridSpacingX != gridSpacingY) {
			header.write("cellsizeX" + "\t" + gridSpacingX + "\n");
			header.write("cellsizeY" + "\t" + gridSpacingY + "\n");
		} else {
			header.write("cellsize" + "\t" + gridSpacingX + "\n");
		}
		header.write("NODATA_value" + "\t" + "-9999" + "\n");
		header.write("byteorder" + "\t" + "LSBFIRST" + "\n");
		
		header.close();
		
		DataOutputStream out = new DataOutputStream(new FileOutputStream(fileNamePrefix + ".flt"));
		
		for (int row=0; row<ny; row++) {
			for (int col=0; col<nx; col++) {
				double val = get(col, row);
				out.writeFloat((float)val);
			}
		}
		
		out.close();
	}
	
	private static String getHeaderValue(ArrayList<String> lines, String key) {
		for (String line : lines) {
			if (line.startsWith(key)) {
				StringTokenizer tok = new StringTokenizer(line);
				tok.nextToken();
				return tok.nextToken();
			}
		}
		return null;
	}
	
	public static EvenlyDiscrXYZ_DataSet readXYZBinFile(String fileNamePrefix) throws IOException {
		ArrayList<String> lines = FileUtils.loadFile(fileNamePrefix + ".hdr");
		
		int ncols = Integer.parseInt(getHeaderValue(lines, "ncols"));
		int nrows = Integer.parseInt(getHeaderValue(lines, "nrows"));
		double minX = Double.parseDouble(getHeaderValue(lines, "xllcorner"));
		double minY = Double.parseDouble(getHeaderValue(lines, "yllcorner"));
		double gridSpacing = Double.parseDouble(getHeaderValue(lines, "cellsize"));
		
		DataInputStream reader = new DataInputStream(new FileInputStream(fileNamePrefix + ".flt"));
		
		EvenlyDiscrXYZ_DataSet data = new EvenlyDiscrXYZ_DataSet(ncols, nrows, minX, minY, gridSpacing);
		
		for (int row=0; row<nrows; row++) {
			for (int col=0; col<ncols; col++) {
				double val = (double)reader.readFloat();
				
				data.set(col, row, val);
			}
		}
		
		return data;
	}
	
	public double getX(int xIndex) {
		return minX + (double)xIndex * gridSpacingX;
	}
	
	public double getY(int yIndex) {
		return minY + (double)yIndex * gridSpacingY;
	}
	
	private int getIndex(double x, double y) {
		int yInd = getYIndex(y);
		Preconditions.checkState(yInd >= 0 && yInd < getNumY());
		int xInd = getXIndex(x);
		Preconditions.checkState(xInd >= 0 && xInd < getNumX());
		return xInd + nx*yInd;
	}
	
	private int getXIndex(int index) {
		return index % nx;
	}
	
	private int getYIndex(int index) {
		return index / nx;
	}
	
	public int getYIndex(double y) {
		return (int)((y - minY) / gridSpacingY + 0.5);
	}
	
	public int getXIndex(double x) {
		return (int)((x - minX) / gridSpacingX + 0.5);
	}
	
	@Override
	public void set(Point2D point, double z) {
		this.set(point.getX(), point.getY(), z);
	}
	
	@Override
	public void set(double x, double y, double z) {
		if (!contains(x, y))
			throw new InvalidRangeException("point ("+x+", "+y+") is out of range: ("+minX+"=>"+maxX+", "+minY+"=>"+maxY+")");
		this.set(getXIndex(x), getYIndex(y), z);
	}

	@Override
	public void set(int index, double z) {
//		System.out.println("nx: " + nx + ", ny: " + ny);
//		System.out.println("set: index="+index+", x="+getXIndex(index)+", y="+getYIndex(index));
		this.set(getXIndex(index), getYIndex(index), z);
	}

	@Override
	public double get(double x, double y) {
		return get(getXIndex(x), getYIndex(y));
	}
	
	/**
	 * Bilinear interpolation. Algorithm taken from:<br>
	 * http://docs.oracle.com/cd/E17802_01/products/products/java-media/jai/forDevelopers/jai-apidocs/javax/media/jai/InterpolationBilinear.html
	 * 
	 * @param x
	 * @param y
	 * @return
	 * @throws IllegalArgumentException if x or y is outside of the allowable range
	 */
	public double bilinearInterpolation(double x, double y) {
		Preconditions.checkArgument(x >= minX && x <= maxX, "x value of "+x+" outside valid range!");
		Preconditions.checkArgument(y >= minY && y <= maxY, "y value of "+y+" outside valid range!");
			
		int x0 = getIndexBefore(x, minX, gridSpacingX);
		int x1 = x0 + 1;
		// handle edges
		if (x1 >= getNumX())
			x1 = x0;
		int y0 = getIndexBefore(y, minY, gridSpacingY);
		int y1 = y0 + 1;
		// handle edges
		if (y1 >= getNumY())
			y1 = y0;
		
		// "central"
		double s00 = get(x0, y0);
		// to the right
		double s01 = get(x1, y0);
		// below
		double s10 = get(x0, y1);
		// below and to the right
		double s11 = get(x1, y1);
		
		double xfrac = (x - getX(x0))/gridSpacingX;
		double yfrac = (y - getY(y0))/gridSpacingY;
		
		return (1 - yfrac) * ((1 - xfrac)*s00 + xfrac*s01) + 
			    yfrac * ((1 - xfrac)*s10 + xfrac*s11);
	}
	
	private int getIndexBefore(double val, double min, double gridSpacing) {
		return (int)Math.floor((val-min)/gridSpacing);
	}

	@Override
	public double get(int index) {
		return get(getXIndex(index), getYIndex(index));
	}

	@Override
	public int size() {
		return nx * ny;
	}
	
	public void set(int xInd, int yInd, double z) {
		this.data[yInd][xInd] = z;
	}
	
	public double get(int xInd, int yInd) {
		return this.data[yInd][xInd];
	}

	@Override
	public double get(Point2D point) {
		return this.get(point.getX(), point.getY());
	}

	@Override
	public Point2D getPoint(int index) {
		return new Point2D.Double(getX(getXIndex(index)), getY(getYIndex(index)));
	}
	
	public int indexOf(Point2D point) {
		return indexOf(point.getX(), point.getY());
	}

	@Override
	public boolean contains(Point2D point) {
		return contains(point.getX(), point.getY());
	}
	
	public boolean contains(double x, double y) {
		return (float)x >= (float)minX && (float)x <= (float)maxX && (float)y >= (float)minY && (float)y <= (float)maxY;
	}

	@Override
	public Object clone() {
		return copy();
	}
		
	@Override
	public EvenlyDiscrXYZ_DataSet copy() {
		EvenlyDiscrXYZ_DataSet xyz = new EvenlyDiscrXYZ_DataSet(nx, ny, minX, minY, gridSpacingX, gridSpacingY);
		for (int x=0; x<nx; x++) {
			for (int y=0; y<ny; y++) {
				xyz.set(x, y, get(x, y));
			}
		}
		return xyz;
	}
	
	@Override
	public int indexOf(double x, double y) {
		return getIndex(x, y);
	}
	
	public EvenlyDiscretizedFunc calcMarginalXDist() {
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(minX, nx, gridSpacingX);
		
		for (int xInd=0; xInd<nx; xInd++) {
			double sum = 0d;
			for (int yInd=0; yInd<ny; yInd++) {
				double val = get(xInd, yInd);
				if (!Double.isNaN(val)	)
					sum += val;
			}
			func.set(xInd, sum);
		}
		
		return func;
	}
	
	public EvenlyDiscretizedFunc calcMarginalYDist() {
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(minY, ny, gridSpacingY);
		
		for (int yInd=0; yInd<ny; yInd++) {
			double sum = 0d;
			for (int xInd=0; xInd<nx; xInd++) {
				double val = get(xInd, yInd);
				if (!Double.isNaN(val)	)
					sum += val;
			}
			func.set(yInd, sum);
		}
		
		return func;
	}
	
	public EvenlyDiscretizedFunc getRow(int yInd) {
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(minX, nx, gridSpacingX);
		
		for (int xInd=0; xInd<nx; xInd++)
			func.set(xInd, get(xInd, yInd));
		
		return func;
	}
	
	public EvenlyDiscretizedFunc getCol(int xInd) {
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(minY, ny, gridSpacingY);
		
		for (int yInd=0; yInd<ny; yInd++)
			func.set(yInd, get(xInd, yInd));
		
		return func;
	}
	
	/**
	 * Returns the diagonal going in the positive x and y direction starting at the
	 * given point. X values in the returned function start at 0 and increase by the
	 * two dimensional distance: sqrt(spacingX^2 + spacingY^2)
	 * @param xInd
	 * @param yInd
	 * @return
	 */
	public EvenlyDiscretizedFunc getDiag(int xInd, int yInd) {
		int numX = getNumX() - xInd;
		int numY = getNumY() - yInd;
		int num;
		if (numX < numY)
			num = numX;
		else
			num = numY;
		double dist = Math.sqrt(gridSpacingX*gridSpacingX + gridSpacingY*gridSpacingY);
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(0, ny, dist);
		for (int index=0; index<num; index++) {
			func.set(index, get(xInd, yInd));
			xInd++;
			yInd++;
		}
		return func;
	}

}
