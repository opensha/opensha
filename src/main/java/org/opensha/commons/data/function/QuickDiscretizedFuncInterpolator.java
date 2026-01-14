package org.opensha.commons.data.function;

import org.opensha.commons.exceptions.InvalidRangeException;

/**
 * Efficient interpolators for {@link DiscretizedFunc}s that take advantage of constant or precomputed slope data.
 * <p>
 * Note that they reflect the function as of the moment they were constructed, and do not update if values change.
 */
public abstract class QuickDiscretizedFuncInterpolator {
	
	protected final DiscretizedFunc f;

	protected final boolean logX;
	protected final boolean logY;

	protected final int n;
	protected final double minX;
	protected final double maxX;
	protected final double tol;

	// y-domain: either y or ln(y)
	protected final double[] yDom; // length n
	
	public static QuickDiscretizedFuncInterpolator get(DiscretizedFunc f) {
		return get(f, false, false);
	}
	
	public static QuickDiscretizedFuncInterpolator get(DiscretizedFunc f, boolean logX, boolean logY) {
		if (!logX && f instanceof EvenlyDiscretizedFunc)
			return new EvenlyDiscretized((EvenlyDiscretizedFunc)f, logY);
		return new ArbitrarilyDiscretized(f, logX, logY);
	}

	protected QuickDiscretizedFuncInterpolator(DiscretizedFunc f, boolean logX, boolean logY) {
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

	public final boolean isLogX() {
		return logX;
	}

	public final boolean isLogY() {
		return logY;
	}
	
	public abstract double findY(double x);

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
	
	/**
	 * General implementation for any DiscretizedFunc.
	 * Uses getXIndexBefore(x) per query and precomputed per-segment slopes.
	 */
	static final class ArbitrarilyDiscretized extends QuickDiscretizedFuncInterpolator {
		private final double[] xLeftDom;	// length n-1; x[i] or ln(x[i])
		private final double[] slopeDom;	// length n-1; dyDom/dxDom

		ArbitrarilyDiscretized(DiscretizedFunc f, boolean logX, boolean logY) {
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
	 * Optimized implementation for EvenlyDiscretizedFunc (linear X only).
	 *
	 * Fast path:
	 * - O(1) direct index computation (no getXIndexBefore call)
	 * - constant dx precomputed slopes (dyDom/dx)
	 *
	 * This implementation does NOT support logX.
	 */
	static final class EvenlyDiscretized extends QuickDiscretizedFuncInterpolator {
		private final EvenlyDiscretizedFunc ef;

		private final double x0;
		private final double dx;
		private final double invDx;

		private final double[] slopeDom; // length n-1 (dyDom/dx)

		EvenlyDiscretized(EvenlyDiscretizedFunc f, boolean logY) {
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
