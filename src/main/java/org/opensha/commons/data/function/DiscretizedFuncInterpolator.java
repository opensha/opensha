package org.opensha.commons.data.function;

import java.text.DecimalFormat;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.exceptions.InvalidRangeException;

import com.google.common.base.Stopwatch;

/**
 * Efficient interpolators for {@link DiscretizedFunc}s that take advantage of constant or precomputed slope data.
 * <p>
 * Note that they reflect the function as of the moment they were constructed, and do not update if values change.
 */
public interface DiscretizedFuncInterpolator {
	
	public static DiscretizedFuncInterpolator getOptimized(DiscretizedFunc f) {
		return getOptimized(f, false, false);
	}
	
	public static DiscretizedFuncInterpolator getOptimized(DiscretizedFunc f, boolean logX, boolean logY) {
		if (!logX && f instanceof EvenlyDiscretizedFunc)
			return new PrecomputedEvenlyDiscretized((EvenlyDiscretizedFunc)f, logY);
		return new PrecomputedArbitrarilyDiscretized(f, logX, logY);
	}
	
	public static DiscretizedFuncInterpolator getRepeatOptimized(DiscretizedFunc f, int numBeforeOptimized) {
		return getRepeatOptimized(f, false, false, numBeforeOptimized);
	}
	
	public static DiscretizedFuncInterpolator getRepeatOptimized(DiscretizedFunc f, boolean logX, boolean logY, int numBeforeOptimized) {
		return new RepeatOptimizedInterpolator(f, logX, logY, numBeforeOptimized);
	}
	
	public abstract boolean isLogX();

	public abstract boolean isLogY();
	
	public abstract double findY(double x);
	
	/**
	 * Basic interpolator that defers to built-in function implementations
	 */
	public static class Basic implements DiscretizedFuncInterpolator {
		
		private final DiscretizedFunc f;
		private final boolean logX;
		private final boolean logY;

		public Basic(DiscretizedFunc f, boolean logX, boolean logY) {
			this.f = f;
			this.logX = logX;
			this.logY = logY;
		}

		@Override
		public boolean isLogX() {
			return logX;
		}

		@Override
		public boolean isLogY() {
			return logY;
		}

		@Override
		public double findY(double x) {
			return f.getInterpolatedY(x, logX, logY);
		}
	}
	
	/**
	 * Hybrid interpolator that starts with the {@link Basic} implementation, but switches over to the
	 * an optimized precomputed instance after a given number of calls. This is useful if you don't know how often
	 * a function will be interpolated and don't want to waste the precomputing overhead if the ultimate interpolation
	 * count is low.
	 */
	public static class RepeatOptimizedInterpolator implements DiscretizedFuncInterpolator {
		
		private final DiscretizedFunc f;
		private final boolean logX;
		private final boolean logY;
		private final int numBeforeOptimized;
		
		private DiscretizedFuncInterpolator interp;
		private boolean isBasic;
		private int basicCount = 0;
		
		public RepeatOptimizedInterpolator(DiscretizedFunc f, boolean logX, boolean logY, int numBeforeOptimized) {
			this.f = f;
			this.logX = logX;
			this.logY = logY;
			this.numBeforeOptimized = numBeforeOptimized;
			isBasic = true;
			interp = new Basic(f, logX, logY);
		}

		@Override
		public boolean isLogX() {
			return logX;
		}

		@Override
		public boolean isLogY() {
			return logY;
		}

		@Override
		public double findY(double x) {
			if (isBasic) {
				if (basicCount >= numBeforeOptimized) {
					isBasic = false;
					interp = getOptimized(f, logX, logY);
				} else {
					basicCount++;
				}
			}
			
			return interp.findY(x);
		}
	}
	
	static abstract class AbstractPrecomputed implements DiscretizedFuncInterpolator {
		protected final DiscretizedFunc f;

		protected final boolean logX;
		protected final boolean logY;

		protected final int n;
		protected final double minX;
		protected final double maxX;
		protected final double tol;

		// y-domain: either y or ln(y)
		protected final double[] yDom; // length n

		protected AbstractPrecomputed(DiscretizedFunc f, boolean logX, boolean logY) {
			if (f == null)
				throw new NullPointerException("f is null");

			this.f = f;
			this.logX = logX;
			this.logY = logY;

			this.n = f.size();
			if (n < 2)
				throw new IllegalArgumentException("Need at least 2 points (size=" + n + ")");

			this.minX = f.getX(0);
			this.maxX = f.getX(n - 1);
			this.tol = f.getTolerance();

			this.yDom = new double[n];
			for (int i = 0; i < n; i++) {
				double y = f.getY(i);
				yDom[i] = logY ? Math.log(y) : y;
			}
		}

		@Override
		public final boolean isLogX() {
			return logX;
		}

		@Override
		public final boolean isLogY() {
			return logY;
		}

		/**
		 * Returns:
		 *	-2 if x is <= minX within tolerance (caller should return y(0))
		 *	-1 if x is >= maxX within tolerance (caller should return y(n-1))
		 *	>=0 if x is strictly inside domain (caller should proceed)
		 */
		protected final int validateOrSnap(double x) {
			if (x > maxX + tol || x < minX - tol)
				throw new InvalidRangeException("x Value (" + x + ") must be within the range: "
						+ f.getX(0) + " and " + f.getX(n - 1));
			if (x >= maxX)
				return -1;
			if (x <= minX)
				return -2;
			return 0;
		}

		protected final double outputFromDom(double yDomVal) {
			return logY ? Math.exp(yDomVal) : yDomVal;
		}
	}
		
	/**
	 * Precomputed interpolator that is faster when a function will be reused for many interpolations but has some
	 * additional overhead
	 */
	public static final class PrecomputedArbitrarilyDiscretized extends AbstractPrecomputed {
		private final double[] xLeftDom;	// length n-1; x[i] or ln(x[i])
		private final double[] slopeDom;	// length n-1; dyDom/dxDom

		PrecomputedArbitrarilyDiscretized(DiscretizedFunc f, boolean logX, boolean logY) {
			super(f, logX, logY);

			this.xLeftDom = new double[n - 1];
			this.slopeDom = new double[n - 1];

			if (logX) {
				double[] lnX = new double[n];
				for (int i = 0; i < n; i++)
					lnX[i] = Math.log(f.getX(i));

				for (int i = 0; i < n - 1; i++) {
					double xL = lnX[i];
					double xR = lnX[i + 1];
					xLeftDom[i] = xL;
					slopeDom[i] = (yDom[i + 1] - yDom[i]) / (xR - xL);
				}
			} else {
				for (int i = 0; i < n - 1; i++) {
					double xL = f.getX(i);
					double xR = f.getX(i + 1);
					xLeftDom[i] = xL;
					slopeDom[i] = (yDom[i + 1] - yDom[i]) / (xR - xL);
				}
			}
		}

		@Override
		public double findY(double x) {
			int snap = validateOrSnap(x);
			if (snap == -1) return f.getY(n - 1);
			if (snap == -2) return f.getY(0);

			int i = f.getXIndexBefore(x); // expected [0, n-2]
			double xDom = logX ? Math.log(x) : x;

			double yInterpDom = yDom[i] + slopeDom[i] * (xDom - xLeftDom[i]);
			return outputFromDom(yInterpDom);
		}
	}
	
	/**
	 * Precomputed interpolator that is faster when a function will be reused for many interpolations but has some
	 * additional overhead. this version is specific to EvenlyDiscretizedFunc and skips the deltaX calculations.
	 *
	 * This implementation does NOT support logX.
	 */
	static final class PrecomputedEvenlyDiscretized extends AbstractPrecomputed {
		private final EvenlyDiscretizedFunc ef;

		private final double x0;
		private final double dx;
		private final double invDx;

		private final double[] slopeDom; // length n-1 (dyDom/dx)

		PrecomputedEvenlyDiscretized(EvenlyDiscretizedFunc f, boolean logY) {
			super(f, false, logY);
			this.ef = f;

			this.x0 = minX;
			this.dx = ef.getDelta();
			this.invDx = 1d / dx;

			this.slopeDom = new double[n - 1];
			for (int i = 0; i < n - 1; i++)
				slopeDom[i] = (yDom[i + 1] - yDom[i]) * invDx;
		}

		@Override
		public double findY(double x) {
			int snap = validateOrSnap(x);
			if (snap == -1) return ef.getY(n - 1);
			if (snap == -2) return ef.getY(0);

			// i = floor((x - x0)/dx)
			int i = (int)((x - x0) * invDx);
			if (i < 0) i = 0;
			else if (i > n - 2) i = n - 2;

			double xLeft = x0 + i * dx;
			double yInterpDom = yDom[i] + slopeDom[i] * (x - xLeft);

			return outputFromDom(yInterpDom);
		}
	}

}
