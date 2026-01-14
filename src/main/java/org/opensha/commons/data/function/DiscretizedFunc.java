package org.opensha.commons.data.function;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.function.Consumer;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * <b>Title:</b> DiscretizedFuncAPI<p>
 *
 * <b>Description:</b> Interface that all Discretized Functions must implement. <P>
 *
 * A Discretized Function is a collection of x and y values grouped together as
 * the points that describe a function. A discretized form of a function is the
 * only ways computers can represent functions. Instead of having y=x^2, you
 * would have a sample of possible x and y values. <p>
 *
 * This functional framework is modeled after mathmatical functions
 * such as sin(x), etc. It assumes that there are no duplicate x values,
 * and that if two points have the same x value but different y values,
 * they are still considered the same point. The framework also sorts the
 * points along the x axis, so the first point contains the mimimum
 * x-value and the last point contains the maximum value.<p>
 *
 * Since this API represents the points in a list, alot of these API functions
 * are standard list access functions such as (paraphrasing) get(), set(),
 * delete(). numElements(), iterator(), etc.<p>
 *
 * There are three fields along with getXXX() and setXXX() matching the field
 * names. These javabean fields provide the basic information to describe
 * a function. All functions have a name, information string, and a
 * tolerance level that specifies how close two points have to be along
 * the x axis to be considered equal.<p>
 *
 * Point2D = (x,y)<p>
 *
 * Note: This interface defines a tolerance so that you can say two x-values
 * are the same within this tolerance limit. THERE IS NO TOLERANCE FOR THE
 * Y-AXIS VALUES. This may be useful to add in the future.<p>
 *
 * @author Steven W. Rock
 * @see DataPoint2D
 * @version 1.0
 */
@JsonAdapter(DiscretizedFunc.Adapter.class)
public interface DiscretizedFunc extends XY_DataSet {
	
	/** Sets the tolerance of this function. */
	public void setTolerance(double newTolerance);
	
	/** Returns the tolerance of this function.  */
	public double getTolerance();
	
	/** returns the y-value given an x-value - within tolerance */
	public double getY(double x);
	
	/**
	 * Returns the largest x index that is at or before the given X value.
	 * <p>
	 * Return value is undefined if x < minX or x > maxX, error checking should be done externally.
	 * @param x
	 * @return
	 */
	public int getXIndexBefore(double x);
	
	/**
	 * Returns the index of the closest point to this x value. If x < {@link #getMinX()} this will return 0, and if
	 * x > {@link #getMaxX()} this will return size-1.
	 * @param x
	 * @return
	 */
	public int getClosestXIndex( double x);

	/* ***************/
	/* INTERPOLATION */
	/* ***************/

	/**
     * Given the imput y value, finds the two sequential
     * x values with the closest y values, then calculates an
     * interpolated x value for this y value, fitted to the curve. <p>
     *
     * Since there may be multiple y values with the same value, this
     * function just matches the first found starting at the x-min point
     * along the x-axis.
     */
    public double getFirstInterpolatedX(double y);

    /**
     * Given the input x value, finds the two sequential
     * x values with the closest x values, then calculates an
     * interpolated y value for this x value, fitted to the curve.
     */
    public double getInterpolatedY(double x);
    
    /**
	 * This function interpolates the Y values in the log space between x and y values.
	 * The Y value returned is in the linear space but the interpolation is done in the log space.
	 * @param x : X value in the linear space corresponding to which we are required to find the interpolated
	 * y value in log space.
	 */
	public double getInterpolatedY_inLogXLogYDomain(double x);


	/**
	 * This function interpolates the Y values in the log-Y space.
	 * The Y value returned is in the linear space.
	 * @param x : X value in the linear space corresponding to which we are required to find the interpolated
	 * y value in log space.
	 */
	public double getInterpolatedY_inLogYDomain(double x);


	/**
	 * This function interpolates the Y values in the log-X space.
	 * The Y value returned is in the linear space.
	 * @param x : X value in the linear space corresponding to which we are required to find the interpolated
	 * y value in logX space.
	 */
	public double getInterpolatedY_inLogXDomain(double x);

    /**
     * Given the input x value, finds the two sequential
     * x values with the closest x values, then calculates an
     * interpolated y value for this x value, fitted to the curve.
     */
    public double getInterpolatedY(double x, boolean logX, boolean logY);

	/**
	 * Given the input y value, finds the two sequential
	 * x values with the closest y values, then calculates an
	 * interpolated x value for this y value, fitted to the curve.
	 * The interpolated Y value returned is in the linear space but
	 * the interpolation is done in the log space.
	 * Since there may be multiple y values with the same value, this
	 * function just matches the first found starting at the x-min point
	 * along the x-axis.
	 * @param y : Y value in the linear space corresponding to which we are 
	 * required to find the interpolated
	 * x value in the log space.
	 */
	public double getFirstInterpolatedX_inLogXLogYDomain(double y);
	
	/**
	 * Builds an optimized interpolator for repeated access taking advantage of precomputed slopes. This has memory and
	 * initialization overhead and should only be used if you need to interpolate many times.
	 * <p>
	 * Note that this will reflect the function at the time this method is called and its behavior is undefined if the
	 * underlying function is modified in any way.
	 * @return
	 */
	public default DiscretizedFuncInterpolator getOptimizedInterpolator() {
		return getOptimizedInterpolator(false, false);
	}
	
	/**
	 * Builds an optimizedinterpolator for repeated access taking advantage of precomputed slopes. This has memory and
	 * initialization overhead and should only be used if you need to interpolate many times.
	 * <p>
	 * Note that this will reflect the function at the time this method is called and its behavior is undefined if the
	 * underlying function is modified in any way.
	 * 
	 * @param logX if true, interpolation is done in the log-X domain
	 * @param logY if true, interpolation is done in the log-Y domain
	 * @return
	 */
	public default DiscretizedFuncInterpolator getOptimizedInterpolator(boolean logX, boolean logY) {
		return DiscretizedFuncInterpolator.getOptimized(this, logX, logY);
	}
	
	
	/* ***************************/
	/* Index Getters From Points */
	/* ***************************/

	/**
	 * Since the x-axis is sorted and points stored in a list,
	 * they can be accessed by index. This function returns the index
	 * of the specified x value if found within tolerance, else returns -1.
	 */
	public int getXIndex(double x);

	/**
	 * Since the x-axis is sorted and points stored in a list,
	 * they can be accessed by index. This function returns the index
	 * of the specified x value in the Point2D if found withing tolerance,
	 * else returns -1.
	 */
	public int getIndex(Point2D point);
	
	/**
	 * Scales (multiplies) the y-values of this function by the esupplied value.
	 * @param scale
	 */
	public void scale(double scale);
	
	/**
	 * Calculates the sum of all y values. If any such value is NaN, this will return NaN.
	 * @return
	 */
	public double calcSumOfY_Vals();
	
	@Override
	public DiscretizedFunc deepClone();
	
	public static class Adapter extends XY_DataSet.AbstractAdapter<DiscretizedFunc> {

		@Override
		protected DiscretizedFunc instance(Double minX, Double maxX, Integer size) {
			return new ArbitrarilyDiscretizedFunc();
		}

		@Override
		protected Class<DiscretizedFunc> getType() {
			return DiscretizedFunc.class;
		}
	}
	
	public static abstract class AbstractAdapter<E extends DiscretizedFunc> extends XY_DataSet.AbstractAdapter<E> {

		@Override
		protected void serializeExtras(JsonWriter out, E xy) throws IOException {
			double tol = xy.getTolerance();
			if (tol > 0)
				out.name("tolerance").value(tol);
			super.serializeExtras(out, xy);
		}

		@Override
		protected Consumer<E> deserializeExtra(JsonReader in, String name) throws IOException {
			if (name.equals("tolerance")) {
				double tol = in.nextDouble();
				return new Consumer<E>() {

					@Override
					public void accept(E t) {
						t.setTolerance(tol);
					}
				};
			}
			return super.deserializeExtra(in, name);
		}
		
	}
	
	/**
	 * Standard java function, usually used for debugging, prints out
	 * the state of the list, such as number of points, the value of each point, etc.
	 * @return
	 */
	public static String toString(DiscretizedFunc func) {
		StringBuffer b = new StringBuffer();

		b.append("Name: " + func.getName() + '\n');
		b.append("Num Points: " + func.size() + '\n');
		b.append("Info: " + func.getInfo() + "\n\n");
		b.append("X, Y Data:" + '\n');
		b.append(func.getMetadataString()+ '\n');
		return b.toString();
	}

}
