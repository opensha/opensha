package org.opensha.commons.data.xyz;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.util.DataUtils.MinMaxAveTracker;

public abstract class AbstractXYZ_DataSet implements XYZ_DataSet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private MinMaxAveTracker getXTracker() {
		MinMaxAveTracker tracker = new MinMaxAveTracker();
		for (Point2D pt : getPointList()) {
			tracker.addValue(pt.getX());
		}
		return tracker;
	}
	
	private MinMaxAveTracker getYTracker() {
		MinMaxAveTracker tracker = new MinMaxAveTracker();
		for (Point2D pt : getPointList()) {
			tracker.addValue(pt.getY());
		}
		return tracker;
	}
	
	private MinMaxAveTracker getZTracker() {
		MinMaxAveTracker tracker = new MinMaxAveTracker();
		for (double val : getValueList()) {
			tracker.addValue(val);
		}
		return tracker;
	}

	@Override
	public double getMinX() {
		return getXTracker().getMin();
	}

	@Override
	public double getMaxX() {
		return getXTracker().getMax();
	}

	@Override
	public double getMinY() {
		return getYTracker().getMin();
	}

	@Override
	public double getMaxY() {
		return getYTracker().getMax();
	}

	@Override
	public double getMinZ() {
		return getZTracker().getMin();
	}

	@Override
	public double getMaxZ() {
		return getZTracker().getMax();
	}
	
	@Override
	public double getSumZ() {
		double sum = 0d;
		for (int index=0; index<size(); index++)
			sum += get(index);
		return sum;
	}

	@Override
	public void setAll(XYZ_DataSet dataset) {
		for (int i=0; i<dataset.size(); i++) {
			set(dataset.getPoint(i), dataset.get(i));
		}
	}
	
	@Override
	public List<Point2D> getPointList() {
		ArrayList<Point2D> points = new ArrayList<Point2D>();
		for (int i=0; i<size(); i++)
			points.add(getPoint(i));
		return points;
	}

	@Override
	public List<Double> getValueList() {
		ArrayList<Double> vals = new ArrayList<Double>();
		for (int i=0; i<size(); i++)
			vals.add(get(i));
		return vals;
	}
	
	public static void writeXYZFile(XYZ_DataSet xyz, String fileName) throws IOException {
		writeXYZFile(xyz, new File(fileName));
	}
	
	public static void writeXYZFile(XYZ_DataSet xyz, File file) throws IOException {
		
		FileWriter fw = new FileWriter(file);
		for (int i=0; i<xyz.size(); i++) {
			Point2D point = xyz.getPoint(i);
			double z = xyz.get(i);
			
			fw.write(point.getX() + "\t" + point.getY() + "\t" + z + "\n");
		}
		fw.close();
	}
	
	@Override
	public void abs() {
		for (int i=0; i<size(); i++) {
			set(i, Math.abs(get(i)));
		}
	}
	
	@Override
	public void log() {
		for (int i=0; i<size(); i++) {
			set(i, Math.log(get(i)));
		}
	}

	@Override
	public void log10() {
		for (int i=0; i<size(); i++) {
			set(i, Math.log10(get(i)));
		}
	}

	@Override
	public void exp() {
		for (int i=0; i<size(); i++) {
			set(i, Math.exp(get(i)));
		}
	}

//	@Override
	public void exp(double base) {
		for (int i=0; i<size(); i++) {
			set(i, Math.pow(10d, get(i)));
		}
	}

	@Override
	public void pow(double pow) {
		for (int i=0; i<size(); i++) {
			set(i, Math.pow(get(i), pow));
		}
	}

	@Override
	public void scale(double scalar) {
		for (int i=0; i<size(); i++) {
			set(i, get(i) * scalar);
		}
	}

	@Override
	public void add(double value) {
		for (int i=0; i<size(); i++) {
			set(i, get(i) + value);
		}
	}

}
