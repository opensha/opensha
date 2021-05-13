package org.opensha.commons.data.function;

import java.awt.geom.Point2D;
import java.text.NumberFormat;
import java.util.Iterator;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.dom4j.Element;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

public class PrimitiveArrayXY_Dataset extends AbstractXY_DataSet {
	
	private double[] xs;
	private double[] ys;
	
	private int size;
	
	private SummaryStatistics xStats;
	private SummaryStatistics yStats;
	
	private boolean fixedX = false;;
	
	/**
	 * Creates a new dataset whose contents have been copied from the given dataset. Initial capaicty is that of the input dataset.
	 * Changes to this dataset do not affect the input dataset.
	 * @param xy
	 */
	public PrimitiveArrayXY_Dataset(XY_DataSet xy) {
		this(xy.size());
		
		for (Point2D pt : xy)
			set(pt);
	}
	
	/**
	 * Creates an empty dataset with the given initial capacity.
	 * @param initialCapacity
	 */
	public PrimitiveArrayXY_Dataset(int initialCapacity) {
		xs = new double[initialCapacity];
		ys = new double[initialCapacity];
		size = 0;
		xStats = new SummaryStatistics();
		yStats = new SummaryStatistics();
	}
	
	/**
	 * Creates a dataset with the supplied fixed x values. Capaicity is fixed to the size of the given x values,
	 * must be filled in order by calling set(x,y)
	 * @param xs
	 */
	public PrimitiveArrayXY_Dataset(double[] xs) {
		this.xs = xs;
		fixedX = true;
		this.ys = new double[xs.length];
		size = 0;
		xStats = new SummaryStatistics();
		yStats = new SummaryStatistics();
	}
	
	public double[] getXArray() {
		return xs;
	}
	
	public double[] getYArray() {
		return ys;
	}
	
	private void ensureCapacity(int capacity) {
		if (capacity > xs.length) {
			int padding = xs.length/2;
			if (padding < 10)
				padding = 10;
			xs = Doubles.ensureCapacity(xs, capacity, padding);
			ys = Doubles.ensureCapacity(ys, capacity, padding);
		}
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public double getMaxX() {
		return xStats.getMax();
	}

	@Override
	public double getMaxY() {
		return yStats.getMax();
	}

	@Override
	public double getMinX() {
		return xStats.getMin();
	}

	@Override
	public double getMinY() {
		return yStats.getMin();
	}

	@Override
	public Point2D get(int index) {
		return new Point2D.Double(xs[index], ys[index]);
	}

	@Override
	public double getX(int index) throws IndexOutOfBoundsException {
		return xs[index];
	}

	@Override
	public double getY(int index) throws IndexOutOfBoundsException {
		return ys[index];
	}

	@Override
	public void set(Point2D point) {
		set(point.getX(), point.getY());
	}

	@Override
	public synchronized void set(double x, double y) {
		// add a new value
		if (fixedX) {
			Preconditions.checkState(size < xs.length);
			Preconditions.checkState(xs[size] == x);
		} else {
			ensureCapacity(size+1);
			xs[size] = x;
		}
		ys[size] = y;
		xStats.addValue(x);
		yStats.addValue(y);
		size++;
	}

	@Override
	public synchronized void set(int index, double y) throws IndexOutOfBoundsException {
		ys[index] = y;
		yStats.clear();
		for (int i=0; i<size; i++)
			yStats.addValue(ys[i]);
	}

	@Override
	public boolean hasX(double x) {
		// slow, but not sorted so not much we can do
		for (int i=0; i<size; i++)
			if (xs[i] == x)
				return true;
		return false;
	}

	@Override
	public String getMetadataString() {
		StringBuffer b = new StringBuffer();
		Iterator<Point2D> it2 = this.iterator();

		while(it2.hasNext()){

			Point2D point = (Point2D)it2.next();
			double x = point.getX();
			double y = point.getY();
			b.append((float) x + "\t  " + (float) y + '\n');
		}
		return b.toString();
	}

	@Override
	public XY_DataSet deepClone() {
		return new PrimitiveArrayXY_Dataset(this);
	}

	@Override
	public Element toXMLMetadata(Element root, String elName) {
		throw new RuntimeException("not supported");
	}

	@Override
	public Element toXMLMetadata(Element root, String elName, NumberFormat format) {
		throw new RuntimeException("not supported");
	}

	@Override
	public Element toXMLMetadata(Element root) {
		throw new RuntimeException("not supported");
	}
	
	public static void main(String[] args) {
		PrimitiveArrayXY_Dataset xy = new PrimitiveArrayXY_Dataset(3);
		
		for (int i=0; i<100; i++) {
			double x = i + Math.random();
			double y = i + Math.random();
			
			xy.set(x, y);
			
			System.out.println(i+". x="+x+", y="+y);
			System.out.println("\tCurrent capacity: "+xy.xs.length);
			System.out.println("\tVal: "+xy.get(i));
			System.out.println("\tMax: "+xy.getMaxX()+" "+xy.getMaxY());
		}
	}

}
