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

package org.opensha.commons.data.function;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;

import org.opensha.commons.data.Point2DComparator;
import org.opensha.commons.data.Point2DToleranceComparator;
import org.opensha.commons.data.Point2DToleranceSortedArrayList;
import org.opensha.commons.data.Point2DToleranceSortedList;
import org.opensha.commons.exceptions.InvalidRangeException;

import com.google.common.base.Preconditions;
import com.google.gson.annotations.JsonAdapter;

/**
 * <b>Title:</b> ArbitrarilyDiscretizedFunc<p>
 *
 * <b>Description:</b> This class is a sublcass implementation
 * of a DiscretizedFunc that stores the data internaly as a
 * sorted TreeMap of DataPoint2D. This subclass distinguishes itself
 * by the fact that it assumes no spacing interval along the x-axis.
 * Consecutive points can be spread out or bunched up in no predicatable
 * order.  For at least the default comparator (DataPoint2DComparator),
 * the tolerance determines whether the set() methods add the point
 * (if x value is more than tolerance away from that of all existing points)
 * or whether they replace an existing point (if within tolerance).  A tolerance
 * of less than about 1e-16 is effectively about 1e-16 due to the numerical
 * precision of floating point arithmetic (1.0 - (1.0+1e-16) = 1.0). <p>
 *
 * @author Steven W. Rock, Gupta Brothers
 * @version 1.0
 */
@JsonAdapter(DiscretizedFunc.Adapter.class)
public class ArbitrarilyDiscretizedFunc extends AbstractDiscretizedFunc
implements Serializable {

	private static final long serialVersionUID = 0xF1E37DA;
	/* Class name Debbuging variables */
	protected final static String C = "ArbitrarilyDiscretizedFunc";

	/* Boolean debugging variable to switch on and off debug printouts */
	protected final static boolean D = true;

	/**
	 * The set of DataPoints2D that conprise the discretized function. These
	 * are stored in a Point2D TreeMap so they are sorted on the X-Values.<p>
	 *
	 * This TreeMap will not allow identical DataPoint2D. A comparator and equals()
	 * is used to determine equality. Since you can specify any comparator you
	 * want, this ArbitrarilyDiscretizedFunc can be adopted for most purposes.<p>
	 *
	 * Note: SWR: I had to make a special org.opensha. version of the Java TreeMap and
	 * subclass DataPoint2DTreeMap to access internal objects in the Java TreeMap.
	 * Java's Treemap had internal objects hidden as private, I exposed them
	 * to subclasses by making them protected in org.opensha.data.TreeMap. This
	 * was neccessary for index access to the points in the TreeMap. Seems like a poor
	 * oversight on the part of Java.<p>
	 */
//	protected DataPoint2DTreeMap points = null;
	
	protected Point2DToleranceSortedList points = null;

	private static String TAB = "\t";

	/**
	 * Creates an ArbitrarilyDiscretizedFunc from an DiscretizedFunc
	 * 
	 * @param func
	 */
	public ArbitrarilyDiscretizedFunc(AbstractDiscretizedFunc func) {
		this(func.getTolerance());
		Iterator<Point2D> it = func.iterator();
		while (it.hasNext())
			this.set(it.next());
		this.setInfo(func.getInfo());
		this.setName(func.getName());
		this.setXAxisName(func.getXAxisName());
		this.setYAxisName(func.getYAxisName());
	}

	/**
	 * Constructor that takes a Point2D Comparator. The comparator is used
	 * for sorting the DataPoint2D. Using the no-arg constructor instantiates
	 * the default Comparator that compares only x-values within tolerance to
	 * determine if two points are equal.<p>
	 *
	 * The passed in comparator must be an implementor of DataPoint2DComparatorAPI.
	 * These comparators know they are dealing with a Point2D and usually
	 * only compare the x-values for sorting. Special comparators may wish to
	 * sort on both the x and y values, i.e. the data points are geographical
	 * locations.
	 */
	public ArbitrarilyDiscretizedFunc(Point2DComparator comparator) {
//		this(new Point2DToleranceSortedTreeSet(comparator));
		this(new Point2DToleranceSortedArrayList(comparator));
	}

	/**
	 * No-Arg Constructor that uses the default DataPoint2DToleranceComparator comparator.
	 * The comparator is used for sorting the DataPoint2D. This default Comparator
	 * compares only x-values within tolerance to determine if two points are equal.<p>
	 * 
	 * made private pending ticket #341
	 */
	private ArbitrarilyDiscretizedFunc(double tolerance) {
		this(new Point2DToleranceComparator(tolerance));
	}

	/**
	 * No-Arg Constructor that uses the default DataPoint2DToleranceComparator comparator.
	 * The comparator is used for sorting the DataPoint2D. This default Comparator
	 * compares only x-values within tolerance to determine if two points are equal.<p>
	 *
	 * The default tolerance of 0 is used. This means that two x-values must be exactly
	 * equal doubles to be considered equal.
	 */
	public ArbitrarilyDiscretizedFunc() {
		this(new Point2DToleranceComparator());
	}

	/**
	 * Creates a default arbitrarily discretized function with the given name
	 * @param name
	 */
	public ArbitrarilyDiscretizedFunc(String name) {
		this();
		setName(name);
	}

	public ArbitrarilyDiscretizedFunc(Point2DToleranceSortedList points) {
		this.points = points;
	}

	/**
	 * Sets the tolerance of this function. Overides the default function in the
	 * abstract class in that it calls setTolerance in the tree map which
	 * updates the comparator in there.
	 *
	 * These field getters and setters provide the basic information to describe
	 * a function. All functions have a name, information string,
	 * and a tolerance level that specifies how close two points
	 * have to be along the x axis to be considered equal.  A tolerance
	 * of less than about 1e-16 is effectively about 1e-16 due to the numerical
	 * precision of floating point arithmetic (1.0 - (1.0+1e-16) = 1.0).
	 */

	public void setTolerance(double newTolerance) throws InvalidRangeException {
		if( newTolerance < 0 )
			throw new InvalidRangeException("Tolerance must be larger or equal to 0");
		points.setTolerance(newTolerance);
		tolerance = newTolerance;
	}

	/** returns the number of points in this function list */
	public int size(){ return points.size(); }

	/**
	 * return the minimum x value along the x-axis. Since the values
	 * are sorted this is a very quick lookup
	 */
	public double getMinX(){;
		return points.getMinX();
	}
	
	/**
	 * return the maximum x value along the x-axis. Since the values
	 * are sorted this is a very quick lookup
	 */
	public double getMaxX() {
		return points.getMaxX();
	}

	/**
	 * Return the minimum y value along the y-axis. This value is calculated
	 * every time a Point2D is added to the list and cached as a variable
	 * so this function returns very quickly. Slows down adding new points
	 * slightly.  I assume that most of the time these lists will be created
	 * once, then used for plotting and in other functions, in other words
	 * more lookups than inserts.
	 */
	public double getMinY() {
		return points.getMinY();
	}
	/**
	 * Return the maximum y value along the y-axis. This value is calculated
	 * every time a Point2D is added to the list and cached as a variable
	 * so this function returns very quickly. Slows down adding new points
	 * slightly.  I assume that most of the time these lists will be created
	 * once, then used for plotting and in other functions, in other words
	 * more lookups than inserts.
	 */
	public double getMaxY() {
		return points.getMaxY();
	}


	/**
	 * Returns the nth (x,y) point in the Function, else null
	 * if this index point doesn't exist */
	public Point2D get(int index){ return points.get(index); }


	/** Returns the x value of a point given the index */
	public double getX(int index){
		Point2D pt = get(index);
		if (pt == null)
			throw new IndexOutOfBoundsException("no point at index "+index);
		return pt.getX();
	}

	/** Returns the y value of a point given the index */
	public double getY(int index){
		Point2D pt = get(index);
		if (pt == null)
			throw new IndexOutOfBoundsException("no point at index "+index);
		return pt.getY();
	}

	/** returns the Y value given an x value - within tolerance, returns null if not found */
	public double getY(double x){ return points.get( x ).getY(); }


	/** returns the Y value given an x value - within tolerance, returns null if not found */
	public int getIndex(Point2D point){ return points.indexOf( point ); }

	/** Returns the x value of a point given the index or -1 if not found */
	public int getXIndex(double x){ return points.indexOf( new Point2D.Double(x, 0.0 ) ); }


	/** Either adds a new DataPoint, or replaces an existing one, within tolerance */
	public void set(Point2D point) {
		if (!points.add(point))
			throw new IllegalStateException("set called but nothing changed!");
	}

	/**
	 * Either adds a new DataPoint, or replaces an existing one, within tolerance,
	 * created from the input x and y values.
	 */
	public void set(double x, double y) {
		set(new Point2D.Double(x,y));
	}


	/**
	 * Replaces a y value for an existing point, accessed by index. If no DataPoint exists
	 * nothing is done.
	 */
	public void set(int index, double y) {
		Point2D point = get(index);
		if( point != null ) {
			point.setLocation(point.getX(), y);
			set(point);
		} else
			throw new IndexOutOfBoundsException();
	}

	/**
	 * Returns an iterator over all datapoints in the list. Results returned
	 * in sorted order. Returns null if no points present.
	 * @return
	 */
	public Iterator<Point2D> getPointsIterator(){
		return points.iterator();
	}
	
	public Iterator<Point2D> iterator() {
		return points.iterator();
	}

	@Override
	int getXIndexBefore(double x) {
		int ind = points.binarySearch(new Point2D.Double(x, 0));
		if (ind < 0)
			return -ind-2;
		return ind-1;
	}

	private double extrapolate(double x1, double x2, double y1, double y2,
			double x) {
		// Create the linear regression function (slope and intercept)
		//System.out.printf("\textrapolating(%f, %f, %f, %f, %f)\n",
		//		x1, x2, y1, y2, x);
		double slope = (y2 - y1) / (x2 - x1);
		double intercept = y1 - (slope * x1);
		//System.out.printf("\tSlope is: %f\tIntercept is: %f\n",
		//		slope, intercept);
		return (slope * x) + intercept;
	}

	public double getInterpExterpY_inLogYDomain(double x) {
		try {
			double v =  getInterpolatedY_inLogYDomain(x);
			//System.err.println("interpolating(" + x + ")...");
			return v;
		} catch (InvalidRangeException irx) {
			//System.err.println("extrapolating(" + x + ")...");
			// We gotta extrapolate...
			if(x < getX(0)) {
				return Math.exp(extrapolate(getX(0), getX(1), Math.log(getY(0)),
						Math.log(getY(1)), x));
			} else {
				int max = points.size();
				return Math.exp(extrapolate(getX(max-2), getX(max-1),
						Math.log(getY(max-2)), Math.log(getY(max-1)), x));
			}
		}
	}

	/**
	 * This function returns a new copy of this list, including copies
	 * of all the points. A shallow clone would only create a new DiscretizedFunc
	 * instance, but would maintain a reference to the original points. <p>
	 *
	 * Since this is a clone, you can modify it without changing the original.
	 * @return
	 */
	public ArbitrarilyDiscretizedFunc deepClone(){

		ArbitrarilyDiscretizedFunc function = new ArbitrarilyDiscretizedFunc(  );
		function.setName(getName());
		function.setTolerance( getTolerance() );
		function.setInfo(getInfo());
		function.setXAxisName(this.getXAxisName());
		function.setYAxisName(this.getYAxisName());
		Iterator it = this.iterator();
		if( it != null ) {
			while(it.hasNext()) {
				function.set( (Point2D)((Point2D)it.next()).clone() );
			}
		}

		return function;

	}

	// not used, can resurrect using hasX if needed
//	/**
//	 * Determines if two functions are the same by comparing
//	 * that each point x value is the same. This requires
//	 * the two lists to have the same number of points.
//	 */
//	public boolean equalXValues(DiscretizedFunc function){
//		// String S = C + ": equalXValues():";
//		if( this.size() != function.size() ) return false;
//		Iterator it = this.iterator();
//		while(it.hasNext()) {
//			Point2D point = (Point2D)it.next();
//			if( !function.hasPoint( point ) ) return false;
//		}
//		return true;
//
//	}



	/**
	 * Standard java function, usually used for debugging, prints out
	 * the state of the list, such as number of points, the value of each point, etc.
	 * @return
	 */
	public String toString(){
		StringBuffer b = new StringBuffer();

		b.append("Name: " + getName() + '\n');
		b.append("Num Points: " + size() + '\n');
		b.append("Info: " + getInfo() + "\n\n");
		b.append("X, Y Data:" + '\n');
		b.append(getMetadataString()+ '\n');
		return b.toString();
	}

	/**
	 *
	 * @return value of each point in the function in String format
	 */
	public String getMetadataString(){
		StringBuffer b = new StringBuffer();
		Iterator it2 = this.iterator();

		while(it2.hasNext()){

			Point2D point = (Point2D)it2.next();
			double x = point.getX();
			double y = point.getY();
			b.append((float) x + TAB + (float) y + '\n');
		}
		return b.toString();
	}

	/**
	 * Almost the same as toString() but used
	 * specifically in a debugging context. Formatted slightly different
	 * @return
	 */
	public String toDebugString(){

		StringBuffer b = new StringBuffer();
		b.append(C + ": Log values:\n");
		Iterator it = this.iterator();
		while(it.hasNext()) {

			Point2D point = (Point2D)it.next();
			b.append( point.toString() + '\n');

		}


		return b.toString();
	}

	/**
	 * This method creates serialized Outputstream for the DataPoint2D
	 * @param s
	 */
	private void writeObject(ObjectOutputStream s){
		Iterator<Point2D> it =iterator();
		try{
			s.writeObject(points.getComparator());
			s.writeObject(new Integer(size()));
			while(it.hasNext()){
				Point2D data = (Point2D)it.next();
				//System.out.println("Data: "+data.toString());
				s.writeObject(data);
			}
		}catch(IOException e){
			e.printStackTrace();
		}
	}

	/**
	 * This method deserialises InputStream for the DataPoint2D
	 * @param s
	 */
	private void readObject(ObjectInputStream s){
		try{
			Point2DComparator comp = (Point2DComparator)s.readObject();
			points = new Point2DToleranceSortedArrayList(comp);
			int num = ((Integer)s.readObject()).intValue();
			for(int i=0;i<num;++i){
				Point2D data = (Point2D)s.readObject();
				set(data);
			}
			//System.out.println("Data Object read: "+data.toString());
		}catch(ClassNotFoundException e){
			System.out.println("Class not found");
			e.printStackTrace();
		}catch(IOException e){
			System.out.println("IO Exception ");
			e.printStackTrace();
		}
	}


	/**
	 * This function creates a new ArbitrarilyDiscretizedFunc whose X values are the
	 * Y values of the calling function and Y values are the Y values of the function
	 * passed as argument.
	 * @param function DiscretizedFuncAPI function whose Y values will the Y values
	 * of the new ArbitrarilyDiscretizedFunc.
	 * @return ArbitrarilyDiscretizedFunc new ArbitrarilyDiscretizedFunc
	 */
	public ArbitrarilyDiscretizedFunc getYY_Function(DiscretizedFunc function){

		if(size() !=function.size())
			throw new InvalidRangeException("This operation cannot be performed on functions "+
			"with different size");

		ArbitrarilyDiscretizedFunc newFunction = new ArbitrarilyDiscretizedFunc();
		int numPoints = function.size();
		for(int j=0;j<numPoints;++j)
			newFunction.set(getY(j),function.getY(j));

		return newFunction;
	}

	/**
	 * Clear all the X and Y values from this function
	 */
	public void clear() {
		points.clear();
	}

	public double[] getXVals() {
		double[] d = new double[points.size()];
		for (int i = 0; i < points.size(); ++i) {
			d[i] = getX(i);
		}
		return d;
	}

	public double[] getYVals() {
		double[] d = new double[points.size()];
		for (int i = 0; i < points.size(); ++i) {
			d[i] = getY(i);
		}
		return d;
	}
	
	public static void main(String[] args) {
		ArbitrarilyDiscretizedFunc f = new ArbitrarilyDiscretizedFunc();
		f.set(100d, 0.013609);
		f.set(250d, 0.033695);
		f.set(500d, 0.059583);
		f.set(1000d, 0.093446);
		f.set(1500d, 0.119977);
		f.set(2500d, 0.163888);
		f.set(3000d, 0.177374);
		f.set(5000d, 0.228356);
		f.set(7000d, 0.265878);
		f.set(10000d, 0.314945);
		
//		double[] lookups = {10,20,30,40,50,60,70,80,100,150,200,250,333,475,700,800,1000,1200,1300,1500,1800,2000,2475,10000};
		double[] lookups = {100,150,200,250,333,475,700,800,1000,1200,1300,1500,1800,2000,2475,10000};

		for (double v : lookups) {
			int iBefore = f.getXIndexBefore(v);
			System.out.println("Trying v="+v+". iBefore="+iBefore);
			System.out.flush();
			double yInterp = f.getInterpolatedY_inLogXLogYDomain(v);
			
			System.out.println("lookup: " + v);
			System.out.println("  iBefore: " + iBefore);
			System.out.println("  yInterp: " + yInterp);
		}
	}
	/*  temp main method to investige numerical precision issues
public static void main( String[] args ) {

  ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
//  func.setTolerance(Double.MIN_VALUE);
  func.set(1.0,0);
  func.set(Double.MIN_VALUE,0);
  func.set(1+1e-16,1);
  func.set(1+2e-16,2);
  func.set(1+3e-16,3);
  func.set(1+4e-16,4);
  func.set(1+5e-16,5);
  func.set(1+6e-16,6);
  func.set(1+7e-16,7);
  func.set(1+8e-16,8);
  func.set(1+9e-16,9);
  func.set(1+10e-16,10);
  Iterator it = func.iterator();
  Point2D point;
  while( it.hasNext()) {
    point = (Point2D) it.next();
    System.out.println(point.getX()+"  "+point.getY());
  }
}

	 */

	/*
    public void rebuild(){

        // make temporary storage
        ArrayList points = new ArrayList();

        // get all points
        Iterator it = getPointsIterator();
        if( it != null ) while(it.hasNext()) { points.add( (Point2D)it.next() ); }

        // get all non-log points if any
        it = getNonLogPointsIterator();
        if( it != null ) while(it.hasNext()) { points.add( (Point2D)it.next() ); }

        // clear permanent storage
        points.clear();
        nonPositivepoints.clear();

        // rebuild permanent storage
        it = points.listIterator();
        if( it != null ) while(it.hasNext()) { set( (Point2D)it.next() ); }

        if( D ) System.out.println("rebuild: " + toDebugString());
        points = null;
    }

    public boolean isYLog() { return yLog; }
    public void setYLog(boolean yLog) {

        if( yLog != this.yLog ) {
            this.yLog = yLog;
            rebuild();
        }
    }

    public boolean isXLog() { return xLog; }
    public void setXLog(boolean xLog) {
        if( xLog != this.xLog ) {
            this.xLog = xLog;
            rebuild();
        }
    }

	 */


}
