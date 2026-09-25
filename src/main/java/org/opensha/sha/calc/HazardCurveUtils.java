package org.opensha.sha.calc;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.DiscretizedFunc;

import com.google.common.base.Preconditions;

/** Shared interpolation methods for hazard curves. */
public final class HazardCurveUtils {

	private HazardCurveUtils() {}

	/** Returns the IML at the supplied exceedance probability, saturating at the curve bounds. */
	public static double getIML(DiscretizedFunc curve, double exceedanceProbability) {
		Preconditions.checkNotNull(curve, "Curve cannot be null");
		Preconditions.checkArgument(exceedanceProbability >= 0d && exceedanceProbability <= 1d,
				"Probability must be in [0,1]: %s", exceedanceProbability);
		if (exceedanceProbability > curve.getMaxY())
			return 0d;
		if (exceedanceProbability < curve.getMinY())
			return curve.getMaxX();
		return curve.getFirstInterpolatedX_inLogXLogYDomain(exceedanceProbability);
	}

	/** Returns the IML at the probability represented by {@code returnPeriod} for {@code curveTimeSpan}. */
	public static double getIML(DiscretizedFunc curve, ReturnPeriod returnPeriod, TimeSpan curveTimeSpan) {
		return getIML(curve, returnPeriod.getProbability(curveTimeSpan));
	}

	/** Returns the exceedance probability at an IML, using log-log interpolation. */
	public static double getExceedanceProbability(DiscretizedFunc curve, double iml) {
		Preconditions.checkNotNull(curve, "Curve cannot be null");
		Preconditions.checkArgument(iml >= 0d && Double.isFinite(iml),
				"IML must be non-negative and finite: %s", iml);
		return curve.getInterpolatedY_inLogXLogYDomain(iml);
	}
}
