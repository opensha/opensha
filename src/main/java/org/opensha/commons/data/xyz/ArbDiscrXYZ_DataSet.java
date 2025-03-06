package org.opensha.commons.data.xyz;

import java.awt.geom.Point2D;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import org.opensha.commons.util.FileUtils;

/**
 * <p>Title: ArbDiscrXYZ_DataSet</p>
 * <p>Description: This class creates a vector for the XYZ dataset. Points can be arbitrarily
 * distributed. This class is backed by the Point2D class and a HashMap.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author : Ned Field, Nitin Gupta and Vipin Gupta
 * @version 1.0
 */

public class ArbDiscrXYZ_DataSet extends AbstractXYZ_DataSet {

	/**
	 * default serial version UID
	 */
	private static final long serialVersionUID = 1l;
	
	// we need a separate list of points (vs just using map for everything) to ensure order
	private ArrayList<Point2D> points;
	// mapping of poitns to values
	private HashMap<Point2D, Double> map;
	
	public ArbDiscrXYZ_DataSet() {
		points = new ArrayList<Point2D>();
		map = new HashMap<Point2D, Double>();
	}
	
	@Override
	public void set(Point2D point, double z) {
		if (point ==  null)
			throw new NullPointerException("Point cannot be null");
		if (!contains(point))
			points.add(point);
		map.put(point, z);
	}

	@Override
	public void set(double x, double y, double z) {
		set(new Point2D.Double(x, y), z);
	}

	@Override
	public void set(int index, double z) {
		set(getPoint(index), z);
	}
	
	@Override
	public void add(Point2D point, double z) {
		if (point ==  null)
			throw new NullPointerException("Point cannot be null");
		if (!contains(point))
			points.add(point);
		double prev = map.containsKey(point) ? map.get(point) : 0d;
		map.put(point, z+prev);
	}

	@Override
	public void add(double x, double y, double z) {
		add(new Point2D.Double(x, y), z);
	}

	@Override
	public void add(int index, double z) {
		add(getPoint(index), z);
	}

	@Override
	public double get(Point2D point) {
		return map.get(point);
	}

	@Override
	public double get(double x, double y) {
		return get(new Point2D.Double(x, y));
	}

	@Override
	public double get(int index) {
		return get(getPoint(index));
	}

	@Override
	public Point2D getPoint(int index) {
		return points.get(index);
	}

	@Override
	public int indexOf(Point2D point) {
		return points.indexOf(point);
	}

	@Override
	public boolean contains(Point2D point) {
		return map.containsKey(point);
	}

	@Override
	public boolean contains(double x, double y) {
		return contains(new Point2D.Double(x, y));
	}

	@Override
	public int size() {
		return points.size();
	}

	@Override
	public Object clone() {
		return copy();
	}
	
	@Override
	public ArbDiscrXYZ_DataSet copy() {
		ArbDiscrXYZ_DataSet xyz = new ArbDiscrXYZ_DataSet();
		for (int i=0; i<size(); i++) {
			xyz.set(getPoint(i), get(i));
		}
		return xyz;
	}

	@Override
	public int indexOf(double x, double y) {
		return indexOf(new Point2D.Double(x, y));
	}

	@Override
	public List<Point2D> getPointList() {
		return points;
	}
	
	public static ArbDiscrXYZ_DataSet loadXYZFile(String fileName) throws FileNotFoundException, IOException {
		ArrayList<String> lines = FileUtils.loadFile(fileName);
		
		ArbDiscrXYZ_DataSet xyz = new ArbDiscrXYZ_DataSet();
		
		for (String line : lines) {
			if (line.startsWith("#"))
				continue;
			if (line.length() < 2)
				continue;
			StringTokenizer tok = new StringTokenizer(line);
			if (tok.countTokens() < 3)
				continue;
			
			double x = Double.parseDouble(tok.nextToken());
			double y = Double.parseDouble(tok.nextToken());
			double z = Double.parseDouble(tok.nextToken());
			
			xyz.set(x, y, z);
		}
		
		return xyz;
	}
	
}
