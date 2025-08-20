package org.opensha.commons.util.interp;

import java.text.DecimalFormat;
import java.util.function.IntToDoubleFunction;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.util.ClassUtils;

import com.google.common.base.Preconditions;

/**
 * Utility class for interpolating values that are computed at distances. Distance bins are established that are linearly
 * spaced for very small values (up to {@link #LINEAR_LOG_TRANSITION} km), and log (base 10) spaced beyond. The maximum distance
 * considered is {@link #OVERALL_MAX_DIST}; queries above that will return the value at that distance.
 */
public class DistanceInterpolator {
	
	/**
	 * Discretization in the linear domain, i.e., for distances below {@link #LINEAR_LOG_TRANSITION}
	 */
	public static final double LINEAR_SPACING = 0.05;
	/**
	 * Maximum distance for linear discretization; everything above is discretized in log space.
	 */
	public static final double LINEAR_LOG_TRANSITION = 1d;
	/**
	 * Discretization in the log (base 10) domain, i.e., for distances above {@link #LINEAR_LOG_TRANSITION}, in log units
	 */
	public static final double LOG_SPACING = 0.025d;
	/**
	 * Maximum overall distance; interpolations above this will print a warning and return the value at this distance
	 */
	public static final double OVERALL_MAX_DIST = 10000;
	/**
	 * Tolerance for considering a distance to be exactly at a bin edge (in linear units) 
	 */
	public static final double DIST_TOL = 1e-5;
	
	private static volatile DistanceInterpolator instance;
	
	/**
	 * @return the {@link DistanceInterpolator} instance
	 */
	public static DistanceInterpolator get() {
		if (instance == null) {
			synchronized (DistanceInterpolator.class) {
				if (instance == null)
					instance = new DistanceInterpolator();
			}
		}
		return instance;
	}
	
	private final int numLinear;
	private final int numLog;
	
	private final EvenlyDiscretizedFunc linearDistFunc;
	private final EvenlyDiscretizedFunc logDistFunc;
	
	private final int size;
	private final int lastIndex;
	private final double[] linearDists;
	private final double[] logDists;
	
	private DistanceInterpolator() {
		numLinear = (int)Math.ceil(1/LINEAR_SPACING);
		linearDistFunc = new EvenlyDiscretizedFunc(0d, numLinear, LINEAR_SPACING);
		double logMaxDist = Math.log10(OVERALL_MAX_DIST);
		double logMinDist = Math.log10(LINEAR_LOG_TRANSITION);
		numLog = (int)Math.ceil((logMaxDist-logMinDist)/LOG_SPACING) + 1; // +1 because we want the end point here
		logDistFunc = new EvenlyDiscretizedFunc(logMinDist, numLog, LOG_SPACING);
		Preconditions.checkState(linearDistFunc.getMaxX() < Math.pow(10, logDistFunc.getMinX()));
		
		size = numLinear + numLog;
		lastIndex = size-1;
		linearDists = new double[size];
		logDists = new double[size];
		for (int i=0; i<linearDists.length; i++) {
			if (i < numLinear) {
				linearDists[i] = linearDistFunc.getX(i);
				logDists[i] = Math.log10(linearDists[i]);
			} else {
				logDists[i] = logDistFunc.getX(i-numLinear);
				linearDists[i] = Math.pow(10, logDists[i]);
			}
		}
	}
	
	/**
	 * 
	 * @return the number of discretized distances
	 */
	public int size() {
		return size;
	}
	
	/**
	 * 
	 * @param index
	 * @return distance at the given index
	 */
	public double getDistance(int index) {
		Preconditions.checkState(index >= 0 && index < size, "Bad index=%s with size=%s", index, size);
		return linearDists[index];
	}
	
	/**
	 * 
	 * @param index
	 * @return distance in log (base 10) units at the given index
	 */
	public double getLogDistance(int index) {
		Preconditions.checkState(index >= 0 && index < size, "Bad index=%s with size=%s", index, size);
		return logDists[index];
	}
	
	int getIndexAtOrBefore(double dist) {
		Preconditions.checkState(dist >= 0d, "Bad dist=%s", dist);
		if (dist >= OVERALL_MAX_DIST)
			return lastIndex;
		int index;
		if (dist < LINEAR_LOG_TRANSITION) {
			// linear domain
			index = linearDistFunc.getXIndexBefore(dist);
		} else {
			// log domain
			double logDist = Math.log10(dist);
			index = numLinear + logDistFunc.getXIndexBefore(logDist);
		}
		if (index < lastIndex) {
			// make sure we didn't get rounded to the bin before due to floating point precision
			if (equalsDiscreteDistance(index+1, dist))
				index++;
		}
		return index;
	}
	
	/**
	 * 
	 * @param index
	 * @param dist
	 * @return true if the given distance is equal to the distance at the given index (within {@link #DIST_TOL} tolerance)
	 */
	public boolean equalsDiscreteDistance(int index, double dist) {
		return Precision.equals(dist, linearDists[index], DIST_TOL);
	}
	
	/**
	 * 
	 * @param dist the distance at which we are interpolating values
	 * @param interpLogX if true, interpolations will be done in the log-distance domain
	 * @return interpolator instance for the given distance
	 */
	public QuickInterpolator getQuickInterpolator(double dist, boolean interpLogX) {
		int indexBefore = getIndexAtOrBefore(dist);
		
		if (equalsDiscreteDistance(indexBefore, dist)) {
			// exactly at an edge
			return new PerfectEdgeInterpolator(indexBefore, linearDists[indexBefore]);
		} else if (indexBefore == lastIndex) {
			System.err.println("WARNING: trying to interpolate for dist="+(float)dist
					+", which is "+(float)(dist-OVERALL_MAX_DIST)+" km beyond "
					+ClassUtils.getClassNameWithoutPackage(DistanceInterpolator.class)+"."+OVERALL_MAX_DIST+"="+(float)OVERALL_MAX_DIST
					+". Will return values that use that maximum distance.");
			return new PerfectEdgeInterpolator(indexBefore, linearDists[indexBefore]);
		}
		if (interpLogX && indexBefore == 0)
			// can't interpolate in logX for the bin that includes 0
			interpLogX = false;
		double binDelta;
		if (interpLogX)
			binDelta = calcBinDelta(Math.log10(dist), logDists[indexBefore], logDists[indexBefore+1]);
		else
			binDelta = calcBinDelta(dist, linearDists[indexBefore], linearDists[indexBefore+1]);
		
		return new BinDeltaInterpolator(indexBefore, indexBefore+1, dist, binDelta);
	}
	
	/**
	 * Interface for quick interpolations for values at a fixed distance between known distance bins
	 */
	public interface QuickInterpolator {
		
		/**
		 * 
		 * @return the distance at which we're interpolating
		 */
		public double getTargetDistance();
		
		/**
		 * 
		 * @return the index at or directly below {@link #getTargetDistance()}
		 */
		public int getIndex1();
		
		/**
		 * 
		 * @return the index at or directly above {@link #getTargetDistance()}
		 */
		public int getIndex2();
		
		/**
		 * 
		 * @return the distance at index {@link #getIndex1()}
		 */
		public double getDistance1();
		
		/**
		 * 
		 * @return the distance at index {@link #getIndex2()}
		 */
		public double getDistance2();
		
		/**
		 * @return true if {@link #getTargetDistance()} lies exactly at a discrete distance (i.e., that at 
		 * {@link #getIndex1()}), meaning that no interpolation is required.
		 */
		public boolean isDiscrete();
		
		/**
		 * @param data {@link IntToDoubleFunction} that returns a data value for a given distance index
		 * @return interpolated value at {@link #getTargetDistance()} using the given {@link IntToDoubleFunction} to fetch
		 * values at indexes 
		 */
		public double interpolate(IntToDoubleFunction data);
		
		/**
		 * @param y1 value at {@link #getIndex1()}
		 * @param y2 value at {@link #getIndex1()}+1
		 * @return interpolated value
		 */
		public double interpolate(double y1, double y2);
	}
	
	private class PerfectEdgeInterpolator implements QuickInterpolator {
		
		private final int index;
		private final double distance;

		private PerfectEdgeInterpolator(int index, double distance) {
			this.index = index;
			this.distance = distance;
		}

		@Override
		public double interpolate(IntToDoubleFunction data) {
			return data.applyAsDouble(index);
		}

		@Override
		public double getTargetDistance() {
			return distance;
		}

		@Override
		public int getIndex1() {
			return index;
		}

		@Override
		public int getIndex2() {
			return index;
		}

		@Override
		public double getDistance1() {
			return linearDists[index];
		}

		@Override
		public double getDistance2() {
			return linearDists[index];
		}

		@Override
		public boolean isDiscrete() {
			return true;
		}

		@Override
		public double interpolate(double y1, double y2) {
			return y1;
		}
		
	}
	
	private class BinDeltaInterpolator implements QuickInterpolator {
		private final int indexBefore;
		private final int indexAfter;
		private final double distance;
		private final double binDelta;

		private BinDeltaInterpolator(int indexBefore, int indexAfter, double distance, double binDelta) {
			this.indexBefore = indexBefore;
			this.indexAfter = indexAfter;
			this.distance = distance;
			this.binDelta = binDelta;
		}

		@Override
		public double interpolate(IntToDoubleFunction data) {
			return binDeltaInterp(binDelta, data.applyAsDouble(indexBefore), data.applyAsDouble(indexAfter));
		}

		@Override
		public double getTargetDistance() {
			return distance;
		}

		@Override
		public int getIndex1() {
			return indexBefore;
		}

		@Override
		public int getIndex2() {
			return indexAfter;
		}

		@Override
		public double getDistance1() {
			return linearDists[indexBefore];
		}

		@Override
		public double getDistance2() {
			return linearDists[indexAfter];
		}

		@Override
		public boolean isDiscrete() {
			return false;
		}

		@Override
		public double interpolate(double y1, double y2) {
			return binDeltaInterp(binDelta, y1, y2);
		}
	}
	
	static double calcBinDelta(double x, double x1, double x2) {
		Preconditions.checkState((float)x >= (float)x1);
		Preconditions.checkState((float)x <= (float)x2);
		if ((float)x <= (float)x1)
			return 0d;
		if ((float)x >= (float)x2)
			return 1d;
		return (x - x1) / (x2 - x1);
	}
	
	/**
	 * efficient interpolation that uses this equivalence:
	 * y = y1 + (x - x1) * (y2 - y1) / (x2 - x1)
	 * let binDelta = (x - x1) / (x2 - x1)
	 * y = y1 + binDelta * (y2-y1)
	 * 
	 * @param binDelta
	 * @param y1
	 * @param y2
	 * @return
	 */
	static double binDeltaInterp(double binDelta, double y1, double y2) {
		if (y1 == 0d && y2 == 0d)
			return 0d;
		double ret = y1 + binDelta * (y2-y1);
		
		// test for sign errors, which can happen very close to zero
		if (ret < 0.1 && ret > -0.1) {
			boolean sign1, sign2;
			if (y1 != 0) {
				sign1 = y1 >= 0;
				sign2 = y2 == 0 ? sign1 : y2 >= 0;
			} else {
				// y2 != 0
				sign2 = y2 >= 0;
				sign1 = y1 == 0  ? sign2 : y1 >= 0;
			}
			boolean signRet = ret >= 0;
			if (sign1 == sign2 && sign1 != signRet) {
				// this interpolation method can flip signs around zero by a tiny tiny amount
				// both input were the same sign, we're not; make sure we're effectively zero
				Preconditions.checkState(ret < 1e-10 && ret > -1e-10,
						"interp=%s sign flip for y1=%s, y2=%s, delta=%s", ret, y1, y2, binDelta);
				// force to zero
				ret = 0;
			}
		}
		return ret;
	}
	
	public static void main(String[] args) {
		System.out.println("Linear spacing="+(float)LINEAR_SPACING+" up to "+(float)LINEAR_LOG_TRANSITION);
		System.out.println("Log spacing="+(float)LOG_SPACING+" up to "+(float)OVERALL_MAX_DIST);
		DistanceInterpolator interp = new DistanceInterpolator();
		
		DecimalFormat df = new DecimalFormat("0.000");
		for (int i=0; i<interp.size; i++) {
			System.out.println(i+".\t"+df.format(interp.linearDists[i])
					+(i>0 ? "\t(+"+df.format((interp.linearDists[i]-interp.linearDists[i-1]))+")": "")
					+"\tlog10:\t"+df.format(interp.logDists[i])
					+(i>0 ? "\t(+"+df.format((interp.logDists[i]-interp.logDists[i-1]))+")": ""));
			int testIndexBefore = interp.getIndexAtOrBefore(interp.linearDists[i]);
			Preconditions.checkState(testIndexBefore == i, "Bad getIndexAtOrBefore=%s for %s. linear=%s, log=%s",
					testIndexBefore, i, interp.linearDists[i], interp.logDists[i]);
			testIndexBefore = interp.getIndexAtOrBefore(interp.linearDists[i]+1e-8);
			Preconditions.checkState(testIndexBefore == i, "Bad getIndexAtOrBefore=%s with small add for %s. linear=%s, log=%s",
					testIndexBefore, i, interp.linearDists[i], interp.logDists[i]);
			int index = i;
			double interpEdgeTest = interp.getQuickInterpolator(interp.linearDists[i], false).interpolate(val->(double)val);
			Preconditions.checkState(Precision.equals(interpEdgeTest, (double)i), "Bad interp for edge: %s != %s", (Double)interpEdgeTest, (Integer)i);
			if (i < interp.size-1) {
				double halfway = 0.5*(interp.linearDists[i]+interp.linearDists[i+1]);
				testIndexBefore = interp.getIndexAtOrBefore(halfway);
				Preconditions.checkState(testIndexBefore == i, "Bad getIndexAtOrBefore=%s with half add for %s. linear=%s, log=%s",
						testIndexBefore, i, interp.linearDists[i], interp.logDists[i]);
				double interpMiddleTest = interp.getQuickInterpolator(halfway, false).interpolate(val->(double)val);
				Preconditions.checkState(Precision.equals(interpMiddleTest, i+0.5), "Bad interp for middle: %s != %s", (Double)interpMiddleTest, i+0.5);
			}
		}
	}

}
