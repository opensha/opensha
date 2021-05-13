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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;

import org.apache.commons.io.IOUtils;
import org.opensha.commons.exceptions.InvalidRangeException;

import com.google.common.base.Preconditions;


/**
 * <b>Title:</b> EvenlyDiscretizedFunc<p>
 *
 * <b>Description:</b> Subclass of DiscretizedFunc and full implementation of
 * DiscretizedFuncAPI. Assumes even spacing between the x points represented by
 * the delta distance. Y Values are stored as doubles in an array of primitives. This
 * allows replacement of values at specified indexes.<p>
 *
 * Note that the basic unit for this function framework are Point2D which contain
 * x and y values. Since the x-values are evenly space there are no need to store
 * them. They can be calculated on the fly based on index. So the internal storage
 * saves space by only saving the y values, and reconstituting the Point2D values
 * as needed. <p>
 *
 * Since the x values are not stored, what is stored instead is the x-min value, x-max value,
 * and the delta spacing between x values. This is enough to calculate any x-value by
 * index.<p>
 *
 * This function can be used to generate histograms. To do that, tolerance should be set greater 
 * than delta.  Add methods should then be used to add to Y values for histograms. 
 * The x value is the mid-point of the histogram interval<p>
 * 
 * 
 * @author Steven W. Rock
 * @version 1.0
 */

public class EvenlyDiscretizedFunc extends AbstractDiscretizedFunc{

	private static final long serialVersionUID = 0xC4E0D3D;

	/** Class name used for debbuging */
	protected final static String C = "EvenlyDiscretizedFunc";

	/** if true print out debugging statements */
	protected final static boolean D = false;

	/** The internal storage collection of points, stored as a linked list */
	protected double points[];

	/** The minimum x-value in this series, pins the index values with delta */
	protected double minX=Double.NaN;

	/** The maximum x-value in this series */
	protected double maxX=Double.NaN;

	/** Distance between x points */
	protected double delta=Double.NaN;

	/** Number of points in this function */
	protected int num;

	/**
	 * Helper boolean that indicates no values have been put
	 * into this function yet. Used only internally.
	 */
	protected boolean first = true;


	/**
	 * This is one of two constructor options
	 * to fully quantify the domain of this list, i.e.
	 * the x-axis.
	 *
	 * @param min   - Starting x value
	 * @param num   - number of points in list
	 * @param delta - distance between x values
	 */
	public EvenlyDiscretizedFunc(double min, int num, double delta) {

		set(min, num, delta);
	}


	/**
	 * Fully quantify the domain of this list, i.e.
	 * the x-axis. This function clears the list of points
	 * previously in this function
	 *
	 * @param min   - Starting x value
	 * @param num   - number of points in list
	 * @param delta - distance between x values
	 */

	public void set(double min, int num, double delta) {
		set(min, min + (num-1)*delta, num);
	}


	/**
	 * The other three input options to fully quantify the domain
	 * of this list, i.e. the x-axis.
	 *
	 * @param min   - Starting x value
	 * @param num   - number of points in list
	 * @param max - Ending x value
	 */
	public EvenlyDiscretizedFunc(double min, double max, int num) {
		this.set(min, max, num);
	}

	/**
	 * Three input options to fully quantify the domain
	 * of this list, i.e. the x-axis. This function clears the list of points
	 * previously in this function
	 *
	 * @param min   - Starting x value
	 * @param num   - number of points in list
	 * @param max - Ending x value
	 */

	public void set(double min, double max, int num) {
		if (num <= 0)
			throw new IllegalArgumentException("num points must be > 0");

		if (num == 1 && min != max)
			throw new IllegalArgumentException("min must equal max if num points = 1");

		if (min > max)
			throw new IllegalArgumentException("min must be less than max");
		else if (min < max)
			delta = (max - min) / (num - 1);
		else { // max == min
			if (num == 1)
				delta = 0;
			else
				throw new IllegalArgumentException("num must = 1 if min = max");
		}

		this.minX = min;
		this.maxX = max;
		this.num = num;

		points = new double[num];
	}

	/** Sets all y values to NaN */
	public void clear(){ for( int i = 0; i < num; i++){ points[i] = Double.NaN; } }

	/**
	 * Returns true if two values are within tolerance to
	 * be considered equal. Used internally
	 */
	protected boolean withinTolerance(double x, double xx){
		if( Math.abs( x - xx)  <= this.tolerance) return true;
		else return false;
	}


	/** Returns the spacing between x-values */
	public double getDelta() { return delta; }

	/** Returns the number of points in this series */
	public int size(){ return num; }


	/**
	 * Returns the minimum x-value in this series. Since the value is
	 * stored, lookup is very quick
	 */
	public double getMinX(){ return minX; }

	/**
	 * Returns the maximum x-value in this series. Since the value is
	 * stored, lookup is very quick
	 */
	public double getMaxX(){ return maxX; }


	/**
	 * Returns the minimum y-value in this series. Since the value could
	 * appear aywhere along the x-axis, each point needs to be
	 * examined, lookup is slower the larger the dataset. <p>
	 *
	 * Note: An alternative would be to check for the min value every time a
	 * point is inserted and store the miny value. This would only slightly slow down
	 * the insert, but greatly speed up the lookup. <p>
	 */
	public double getMinY(){
		double minY = Double.POSITIVE_INFINITY;
		for(int i = 0; i<num; ++i)
			if(points[i] < minY) minY = points[i];
		return minY;
	}

	/**
	 * Returns the maximum y-value in this series. Since the value could
	 * appear aywhere along the x-axis, each point needs to be
	 * examined, lookup is slower the larger the dataset. <p>
	 *
	 * Note: An alternative would be to check for the min value every time a
	 * point is inserted and store the miny value. This would only slightly slow down
	 * the insert, but greatly speed up the lookup. <p>
	 */
	public double getMaxY(){
		double maxY = Double.NEGATIVE_INFINITY;
		for(int i = 0; i<num; ++i)
			if(points[i] > maxY) maxY = points[i];
		return maxY;
	}
	
	/**
	 * Returns the x index for the maximum y-value in this series. Since the value could
	 * appear aywhere along the x-axis, each point needs to be
	 * examined, lookup is slower the larger the dataset. <p>
	 *
	 */
	public int getXindexForMaxY(){
		double maxY = Double.NEGATIVE_INFINITY;
		int xIndex=-1;
		for(int i = 0; i<num; ++i)
			if(points[i] > maxY) {
				maxY = points[i];
				xIndex=i;
			}
		return xIndex;
	}



	/**
	 * Returns an x and y value in a Point2D based on index
	 * into the y-points array.  The index is based along the x-axis.
	 */
	public Point2D get(int index){
		if (index < 0 || index >= size())
			return null;
		return new Point2D.Double(getX(index), getY(index));
	}

	/**
	 * Returns the ith x element in this function. Returns null
	 * if index is negative or greater than number of points.
	 * The index is based along the x-axis.
	 */
	public double getX(int index){
		if( index < 0 || index > ( num -1 ) ) throw new IndexOutOfBoundsException("no point at index "+index);
		else return ( minX + delta * index );
	}

	/**
	 * Returns the ith y element in this function. Returns null
	 * if index is negative or greater than number of points.
	 * The index is based along the x-axis.
	 */
	public double getY(int index){
		if( index < 0 || index > ( num -1 ) ) throw new IndexOutOfBoundsException("no point at index "+index);
		return points[index];
	}

	/**
	 * Returns they-value associated with this x-value. First
	 * the index of the x-value is calculated, within tolerance.
	 * Then they value is obtained by it's index into the storage
	 * array. Returns null if x is not one of the x-axis points.
	 */
	public double getY(double x){ return getY( getXIndex( x) ); }

	/**
	 * Returns the index of the supplied value provided it's within the
	 * tolerance of one of the discretized values.
	 * @see #getClosestXIndex(double)
	 */
	public int getXIndex( double x) {
		int i = getClosestXIndex(x);
		double closestX = getX(i);
		return withinTolerance(x, closestX) ? i : -1;
	}
	
	
	private static final double PRECISION_SCALE = 1 + 1e-14;
	
	/**
	 * Returns the index of the supplied value (ignoring tolerance). It should
	 * be noted that this method uses a very small internal scale factor to
	 * provide accurate results. Double precision errors can result in x-values
	 * that fall on the boundary between adjacent function values. This may
	 * cause the value to be associated with the index below, when in fact
	 * boundary values should be associated with the index above. This is the
	 * convention followed in other data analysis software (e.g. Matlab). Values
	 * well outside the range spanned by the function are associated with index
	 * of the min or max function value as appropriate.
	 */
	public int getClosestXIndex( double x) {
		double iVal = PRECISION_SCALE * (x - minX) / delta;
		int i = (delta == 0) ? 0 : (int) Math.round(iVal);
		return (i<0) ? 0 : (i>=num) ? num-1 : i;
	}

	/**
	 * Calls set( x value, y value ). An IllegalArgumentException is thrown
	 * if the x value is not an x-axis point.
	 */
	public void set(Point2D point) {

		set( point.getX(), point.getY());
	}

	/**
	 * Sets the y-value at a specified index. The x-value index is first
	 * calculated, then the y-value is set in it's array. A
	 * DataPoint2DException is thrown if the x value is not an x-axis point.
	 */
	public void set(double x, double y) {
		int index = getXIndex( x );
		points[index] = y;
	}

	/**
	 * This method can be used for generating histograms if tolerance is set greater than delta.
	 * Adds to the y-value at a specified index. The x-value index is first
	 * calculated, then the y-value is added in it's array.  
	 * The specified x value is the mid-point of the histogram interval.
	 * 
	 * IllegalArgumentException is thrown if the x value is not an x-axis point.
	 */
	public void add(double x, double y) {
		int index = getXIndex( x );
		if (index < 0)
			throw new IllegalArgumentException("No point at x="+x);
		points[index] = y+points[index];
	}

	/**
	 * this function will throw an exception if the index is not
	 * within the range of 0 to num -1
	 */
	public void set(int index, double y) {
		if( index < 0 || index >= num ) {
			throw new IndexOutOfBoundsException(C + ": set(): The specified index ("+index+") doesn't match this function domain.");
		}
		points[index] = y;
	}

	/**
	 * This method can be used for generating histograms if tolerance is set greater than delta.
	 * Adds to the y-value at a specified index. The specified x value is the mid-point of the histogram interval.
	 * 
	 * this function will throw an exception if the index is not
	 * within the range of 0 to num -1
	 */
	public void add(int index, double y) {
		if( index < 0 || index > ( num -1 ) ) {
			throw new IndexOutOfBoundsException(C + ": set(): The specified index="+index+" doesn't match this function domain.");
		}
		points[index] = y+points[index];
	}


	/**
	 * This function may be slow if there are many points in the list. It has to
	 * reconstitute all the Point2D x-values by index, only y values are stored
	 * internally in this function type. A Point2D is built for each y value and
	 * added to a local ArrayList. Then the iterator of the local ArrayList is returned.
	 */
	public Iterator<Point2D> getPointsIterator(){
		ArrayList<Point2D> list = new ArrayList<Point2D>();
		for( int i = 0; i < num; i++){
			list.add( new Point2D.Double( getX(i), getY(i) ) );
		}
		return list.listIterator();
	}
	
	@Override
	protected int getXIndexBefore(double x) {
		return (int)Math.floor((x-minX)/delta);
	}

	@Override
	public double getClosestYtoX(double x) {
		// TODO unit test
		if (x >= maxX)
			return getY(size()-1);
		if (x <= minX)
			return getY(0);
		int ind = getXIndexBefore(x);
		double x1 = getX(ind);
		double x2 = getX(ind+1);
		double d1 = x-x1;
		double d2 = x2 - x;
		if (d1 < d2)
			return getY(ind);
		return getY(ind+1);
	}

	/** Returns a copy of this and all points in this DiscretizedFunction.
	 *  A copy, or clone has all values the same, but is a different java class
	 *  instance. That means you can change the copy without affecting the original
	 *  instance. <p>
	 *
	 * This is a deep clone so all fields and all data points are copies. <p>
	 */
	public EvenlyDiscretizedFunc deepClone(){

		EvenlyDiscretizedFunc f = new EvenlyDiscretizedFunc(
				minX, num, delta
		);

		f.info = info;
		f.minX = minX;
		f.maxX = maxX;
		f.name = name;
		f.xAxisName = xAxisName;
		f.yAxisName = yAxisName;
		f.tolerance = tolerance;
		f.setInfo(this.getInfo());
		f.setName(this.getName());
		for(int i = 0; i<num; i++)
			f.set(i, points[i]);

		return f;
	}


	/**
	 * Determines if two functions are the same by comparing
	 * that each point x value is the same, within tolerance
	 */
	public boolean equalXValues(DiscretizedFunc function){
		//String S = C + ": equalXValues():";

		if( !(function instanceof EvenlyDiscretizedFunc ) ) return false;
		if( num != function.size() ) return false;


		double min = minX;
		double min1 = ((EvenlyDiscretizedFunc)function).getMinX();
		if( !withinTolerance( min, min1 ) ) return false;

		double d = delta;
		double d1 = ((EvenlyDiscretizedFunc)function).getDelta();
		if( d != d1 ) return false;

		return true;

	}

	/**
	 * It finds out whether the X values are within tolerance of an integer value
	 * @param tol tolerance value to consider  rounding errors
	 *
	 * @return true if all X values are within the tolerance of an integer value
	 * else returns false
	 */
	public boolean areAllXValuesInteger(double tolerance) {

		double diff;
		// check that min X and delta are integer values
		diff = Math.abs(minX - Math.rint(minX));
		if (diff > tolerance) return false;
		diff = Math.abs(delta - Math.rint(delta));
		if (diff > tolerance) return false;
		return true;

	}



	/**
	 * Determines if two functions are the same by comparing
	 * that each point x value is the same, within tolerance,
	 * and that each y value is the same, including nulls.
	 */
	public boolean equalXAndYValues(DiscretizedFunc function){
		//String S = C + ": equalXAndYValues():";

		if( !equalXValues(function) ) return false;

		for( int i = 0; i < num; i++){

			double y1 = getY(i);
			double y2 = function.getY(i);

			if (Double.isNaN(y1) &&  !Double.isNaN(y2)) return false;
			else if (Double.isNaN(y2) &&  !Double.isNaN(y1)) return false;
			else if ( y1 != y2 ) return false;

		}

		return true;

	}

	/**
	 * Standard java function, usually used for debugging, prints out
	 * the state of the list, such as number of points, the value of each point, etc.
	 * @return
	 */
	public String toString(){
		// @formatter:off
		StringBuffer b = new StringBuffer()
			.append("     Name: ").append(getName())
			.append(IOUtils.LINE_SEPARATOR)
			.append("   Points: ").append(size())
			.append(IOUtils.LINE_SEPARATOR)
			.append("     Info: ").append(getInfo())
			.append(IOUtils.LINE_SEPARATOR)
			.append(IOUtils.LINE_SEPARATOR)
			.append("Data[x,y]:")
			.append(IOUtils.LINE_SEPARATOR)
			.append(getMetadataString())
			.append(IOUtils.LINE_SEPARATOR);
		return b.toString();
		// @formatter:on
	}

	/**
	 *
	 * @return value of each point in the function in String format
	 */
	public String getMetadataString(){
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

	// old implementations, replaced by hasX(double) in parent abstract class
//	/**
//	 * Returns true if the x value is withing tolerance of an x-value in this list,
//	 * and the y value is equal to y value in the list.
//	 */
//	public boolean hasPoint(Point2D point){
//		return point != null && hasPoint(point.getX(),point.getY());
//	}
//
//	/**
//	 * Returns true if the x value is withing tolerance of an x-value in this list,
//	 * and the y value is equal to y value in the list.
//	 */
//	public boolean hasPoint(double x, double y){
//		try {
//			int index = getXIndex( x );
//			if (index < 0)
//				return false;
//			double yVal = this.getY(index);
//			if(Double.isNaN(yVal)|| yVal!=y) return false;
//			return true;
//		} catch(Point2DException e) {
//			return false;
//		}
//	}

	/** Returns the index of this DataPoint based on it's x any y value
	 *  both the x-value and y-values in list should match with that of point
	 * returns -1 if there is no such value in the list
	 * */
	public int getIndex(Point2D point){
		int index= getXIndex( point.getX() );
		if (index < 0) return -1;
		double y = this.getY(index);
		if(y!=point.getY()) return -1;
		return index;
	}

//	public static void main(String args[]) {
//		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(4.324,9.55323, 90000);
//		for (int i=0; i<func.getNum(); i++)
//			func.set(i, Math.random());
//		for (double x=5; x<=9; x+=0.001) {
//			double ynew = func.getInterpolatedY(x);
//			double yold = func.getInterpolatedY_old(x);
//			Preconditions.checkState(ynew == yold);
//		}
//	}

}
