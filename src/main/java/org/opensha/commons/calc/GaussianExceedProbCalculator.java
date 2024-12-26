package org.opensha.commons.calc;

import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.util.Precision;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

/**
 * Interface for an Gaussian exceedance probability calculator. This class can be used to more efficiently compute
 * Gaussian exceedance probabilities than directly accessing {@link GaussianDistCalc#getExceedProb(double)}
 * and {@link GaussianDistCalc#getExceedProb(double, int, double)}.
 * 
 * 
 */
public abstract class GaussianExceedProbCalculator {
	
	public abstract double getExceedProb(double standRandVariable);
	
	/**
	 * Dynamic ExceedProbCalculator where each value is computed on the fly. Still faster than using the static methods
	 * if sigma truncation is enabled because it caches pUp and (pUp - pDown)
	 */
	public static class Dynamic extends GaussianExceedProbCalculator {
		
		private int truncType;
		private double truncLevel;
		
		private double pUp;
		private double pUpMinusDown;

		public Dynamic() {
			truncType = 0;
		}
		
		public Dynamic(int truncType, double truncLevel) {
			this.truncType = truncType;
			this.truncLevel = truncLevel;
			switch (truncType) {
			case 0:
				break;
			case 1:
				Preconditions.checkState(truncLevel > 0);
				pUp = GaussianDistCalc.getCDF( truncLevel );
				break;
			case 2:
				Preconditions.checkState(truncLevel > 0);
				pUp = GaussianDistCalc.getCDF( truncLevel );
				double pDown = GaussianDistCalc.getCDF(-truncLevel);
				pUpMinusDown = pUp - pDown;
				break;

			default:
				throw new IllegalStateException("Bad trunc type: "+truncType);
			}
		}

		@Override
		public double getExceedProb(double standRandVariable) {
			if (truncType == 0)
				return GaussianDistCalc.getExceedProb(standRandVariable);
			double cdf = GaussianDistCalc.getCDF(standRandVariable);
			if (  truncType == 1 ) {  // upper truncation
				if ( standRandVariable > truncLevel )
					return  0.0;
				else
					return  (1.0 - cdf/pUp) ;
			}
			// the two sided case
			if ( standRandVariable > truncLevel )
				return (0.0);
			else if ( standRandVariable < -truncLevel )
				return (1.0);
			else
				return ( (pUp-cdf)/pUpMinusDown );
		}
		
	}
	
	private static Precomputed DEFAULT_NO_TRUNC;
	private static Precomputed PREV_ONE_SIDED;
	private static double PREV_ONE_SIDED_LEVEL = Double.NaN;
	private static Precomputed PREV_TWO_SIDED;
	private static double PREV_TWO_SIDED_LEVEL = Double.NaN;
	public synchronized static GaussianExceedProbCalculator getPrecomputedExceedProbCalc(int truncType, double truncLevel) {
		if (truncType == 1) {
			if (PREV_ONE_SIDED == null || truncLevel != PREV_ONE_SIDED_LEVEL) {
				PREV_ONE_SIDED = new Precomputed(truncType, truncLevel);
				PREV_ONE_SIDED_LEVEL = truncLevel;
			}
			return PREV_ONE_SIDED;
		} else if (truncType == 2) {
			if (PREV_TWO_SIDED == null || truncLevel != PREV_TWO_SIDED_LEVEL) {
				PREV_TWO_SIDED = new Precomputed(truncType, truncLevel);
				PREV_TWO_SIDED_LEVEL = truncLevel;
			}
			return PREV_TWO_SIDED;
		} else {
			if (DEFAULT_NO_TRUNC == null)
				DEFAULT_NO_TRUNC = new Precomputed();
			return DEFAULT_NO_TRUNC;
		}
	}
	
	// random variables below -this or above this will always return 1 or 0 exactly to double precision
	static final double BOUND_TO_DOUBLE_PRECISION = 9;
	static final int NUM_DISCR = 100000;
	static final boolean INTERPOLATE = true;
	
	/**
	 * Precomputed ExceedProbCalculator where values within the given range (defaults to -3 to 3 sigma) are precomputed
	 * on a fine grid, and values outside of that are computed dynamically.
	 */
	public static class Precomputed extends GaussianExceedProbCalculator {
		private static final double PRECISION_SCALE = 1 + 1e-14;
		
		private final double[] probCache;
		private final double[] probSlopeCache; // y[i] = (y[i+1]-y[i])/delta
		private final double delta;
		private final double scaleOverDelta; // used for more reliable index finding, see note in EvenlyDiscretizedFunc
		
		private final double minForCache;
		private final double maxForCache;
		
		public Precomputed() {
			this(-BOUND_TO_DOUBLE_PRECISION, BOUND_TO_DOUBLE_PRECISION, NUM_DISCR, 0);
		}
		
		public Precomputed(int truncType, double truncLevel) {
			this(truncType < 2 ? -BOUND_TO_DOUBLE_PRECISION : -truncLevel,
					truncType > 0 ? truncLevel : BOUND_TO_DOUBLE_PRECISION,
							NUM_DISCR, truncType);
		}
		
		private Precomputed(double minForCache, double maxForCache, int discrCount, int truncType) {
			Preconditions.checkArgument(minForCache < maxForCache);
			Preconditions.checkState(discrCount > 1);
			Preconditions.checkState(truncType >= 0 && truncType < 3);
			this.minForCache = minForCache;
			this.maxForCache = maxForCache;
			delta = (maxForCache - minForCache) / (discrCount - 1);
			probCache = new double[discrCount];
			scaleOverDelta = PRECISION_SCALE / delta;
			double pUp, pDown;
			switch (truncType) {
			case 0:
				// no truncation
				for (int i=0; i<discrCount; i++)
					probCache[i] = GaussianDistCalc.getExceedProb(getX(i));
				Preconditions.checkState((float)probCache[0] == 1f);
				Preconditions.checkState((float)probCache[probCache.length-1] == 0f);
				break;
			case 1:
				// one sided truncation
				pUp = GaussianDistCalc.getCDF(maxForCache);
				for (int i=0; i<discrCount; i++) {
					double cdf = GaussianDistCalc.getCDF(getX(i));
					probCache[i] = 1.0 - cdf/pUp;
				}
				Preconditions.checkState((float)probCache[0] == 1f);
				break;
			case 2:
				// two sided
				pDown = GaussianDistCalc.getCDF(minForCache);
				pUp = GaussianDistCalc.getCDF(maxForCache);
				double upDown = pUp - pDown;
				for (int i=0; i<discrCount; i++) {
					double cdf = GaussianDistCalc.getCDF(getX(i));
					probCache[i] = (pUp - cdf) / upDown;
				}
				break;
				
			default:
				throw new IllegalStateException("Unexpected truncation type: "+truncType);
			}
			if (INTERPOLATE) {
				// precompute for faster interpolation
				probSlopeCache = new double[discrCount];
				double invDelta = 1d/delta;
				for (int i=0; i<discrCount-1; i++)
					probSlopeCache[i] = invDelta*(probCache[i+1] - probCache[i]);
			} else {
				probSlopeCache = null;
			}
		}
		
		private double getX(int index) {
			return minForCache + delta * index;
		}

		@Override
		public double getExceedProb(double standRandVariable) {
			// check cache bounds
			// cache bounds are always either the truncation level, or the value beyond which we're 0 or 1 to double precision
			// so we can just return 1 below minimum and 0 above maximum
			if (standRandVariable < minForCache)
				return 1d;
			else if (standRandVariable > maxForCache)
				return 0d;
			if (INTERPOLATE) {
				int xBefore = floorInt(scaleOverDelta*(standRandVariable - minForCache));
//				Preconditions.checkState(standRandVariable >= getX(xBefore) && standRandVariable <= getX(xBefore+1));
//				return Interpolate.findY(getX(xBefore), probCache[xBefore], getX(xBefore+1), probCache[xBefore+1], standRandVariable);
				return findY(getX(xBefore), probCache[xBefore], probSlopeCache[xBefore], standRandVariable);
			} else {
				// closest
				double iVal = scaleOverDelta * (standRandVariable - minForCache);
				return probCache[floorInt(iVal+0.5)];
			}
		}
		
		static int floorInt(double x) {
			return (int)Math.floor(x);
			// actually slower than regular Math
//			return (int)FastMath.floor(x);
		}
		
		double findY(double x1, double y1, double ySlope, double x) {
			/*
			 * original interpolation is:
			 * 
			 * y1 + (x - x1) * (y2 - y1) / (x2 - x1);
			 * 
			 * (x2 - x1) = delta, so:
			 * 
			 * y1 + (x - x1) * (y2 - y1) / delta;
			 * 
			 * we cache (y2 - y1) / delta in probSlopeCache, so:
			 * 
			 * y1 + (x - x1) * ySlope
			 */
//			return y1 + ySlope * (x - x1);
			return Math.fma(ySlope, x-x1, y1);
		}
		
	}
	
	private static void testCachedProbOptimization() {
		int num = 100000000;
		double[] rands = new double[num];
		for (int i=0; i<num; i++)
			rands[i] = Math.random()*BOUND_TO_DOUBLE_PRECISION*2 - BOUND_TO_DOUBLE_PRECISION;
		double[] valuesOrig = new double[num];
		double[] valuesOptimized = new double[num];
		GaussianExceedProbCalculator dynamicCalc = new Dynamic();
		Stopwatch cacheWatch = Stopwatch.createStarted();
		GaussianExceedProbCalculator cachedCalc = new Precomputed();
		cacheWatch.stop();
		double cacheSecs = cacheWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		System.out.println("Caching took "+(float)cacheSecs+" s");
		double tol = 1e-4;
		for (int j=0; j<5; j++) {
			Stopwatch origWatch = Stopwatch.createStarted();
			for (int i=0; i<num; i++) {
				valuesOrig[i] = dynamicCalc.getExceedProb(rands[i]);
			}
			origWatch.stop();
			Stopwatch optimizedWatch = Stopwatch.createStarted();
			for (int i=0; i<num; i++) {
				valuesOptimized[i] = cachedCalc.getExceedProb(rands[i]);
			}
			optimizedWatch.stop();
			double origSecs = origWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
			double optimizedSecs = optimizedWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
			System.out.println("Orig took "+(float)origSecs+" s ("+(float)(num/origSecs)+" /sec)");
			System.out.println("Optimized took "+(float)optimizedSecs+" s ("+(float)(num/optimizedSecs)+" /sec)");
			System.out.println("Speedup factor: "+(float)(origSecs/optimizedSecs));
			double maxDiff = 0d;
			double maxPDiff = 0d;
			double sumDiff = 0d;
			double sumPDiff = 0d;
			for (int i=0; i<num; i++) {
				double diff = valuesOptimized[i] - valuesOrig[i];
				double pDiff = 100d*diff/valuesOrig[i];
				maxDiff = Math.max(maxDiff, Math.abs(diff));
				maxPDiff = Math.max(maxPDiff, Math.abs(pDiff));
				sumDiff += Math.abs(diff);
				if (valuesOrig[i] > 0d)
					sumPDiff += Math.abs(pDiff);
				Preconditions.checkState(Precision.equals(valuesOptimized[i], valuesOrig[i], tol),
						"Mismatch for input=%s: %s != %s; diff=%s, pDiff=%s",
						rands[i], valuesOrig[i], valuesOptimized[i], diff, pDiff);
			}
			double avgDiff = sumDiff/(double)num;
			double avgPDiff = sumPDiff/(double)num;
			System.out.println("\tdiffs: avg="+(float)avgDiff+", max="+(float)maxDiff);
			System.out.println("\tpDiffs: avg="+(float)avgPDiff+", max="+(float)maxPDiff);
		}
	}
	public static void main(String args[]) {
		testCachedProbOptimization();
		
		System.out.println("pDown="+GaussianDistCalc.getExceedProb(-BOUND_TO_DOUBLE_PRECISION));
		System.out.println("pUp="+GaussianDistCalc.getExceedProb(BOUND_TO_DOUBLE_PRECISION));
	}
}