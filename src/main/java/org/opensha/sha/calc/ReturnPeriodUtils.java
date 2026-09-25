package org.opensha.sha.calc;

import com.google.common.base.Preconditions;

/** Utility methods for Poisson conversions between exceedance probabilities, durations, and return periods. */
public final class ReturnPeriodUtils {

	private ReturnPeriodUtils() {}

	/**
	 * Returns the exceedance probability for {@code calcDuration} equivalent to a reference probability and duration.
	 */
	public static double calcExceedanceProb(double referenceProb, double referenceDuration, double calcDuration) {
		Preconditions.checkArgument(referenceProb >= 0d && referenceProb <= 1d,
				"Probability must be in [0,1]: %s", referenceProb);
		Preconditions.checkArgument(referenceDuration > 0d && Double.isFinite(referenceDuration),
				"Reference duration must be positive and finite: %s", referenceDuration);
		Preconditions.checkArgument(calcDuration >= 0d && Double.isFinite(calcDuration),
				"Calculation duration must be non-negative and finite: %s", calcDuration);
		if (calcDuration == 0d)
			return 0d;
		return calcProbFromProbStar(calcProbStar(referenceProb)*calcDuration/referenceDuration);
	}

	private static double calcProbStar(double prob) {
		return -Math.log1p(-prob);
	}

	private static double calcProbFromProbStar(double probStar) {
		return -Math.expm1(-probStar);
	}

	/** Returns the duration having {@code exceedProb}, given a reference probability and duration. */
	public static double calcDurationWithExceedanceProb(double exceedProb, double referenceProb,
			double referenceDuration) {
		Preconditions.checkArgument(exceedProb >= 0d && exceedProb <= 1d,
				"Probability must be in [0,1]: %s", exceedProb);
		Preconditions.checkArgument(referenceProb > 0d && referenceProb <= 1d,
				"Reference probability must be in (0,1]: %s", referenceProb);
		Preconditions.checkArgument(referenceDuration > 0d && Double.isFinite(referenceDuration),
				"Reference duration must be positive and finite: %s", referenceDuration);
		return referenceDuration*calcProbStar(exceedProb)/calcProbStar(referenceProb);
	}

	/** Returns the Poisson return period associated with a probability over a duration. */
	public static double calcReturnPeriod(double exceedProb, double duration) {
		Preconditions.checkArgument(exceedProb > 0d && exceedProb <= 1d,
				"Probability must be in (0,1]: %s", exceedProb);
		Preconditions.checkArgument(duration > 0d && Double.isFinite(duration),
				"Duration must be positive and finite: %s", duration);
		return duration/calcProbStar(exceedProb);
	}

	/** Returns the exceedance probability for a return period and calculation duration. */
	public static double calcExceedanceProbForReturnPeriod(double returnPeriod, double calcDuration) {
		Preconditions.checkArgument(returnPeriod > 0d && Double.isFinite(returnPeriod),
				"Return period must be positive and finite: %s", returnPeriod);
		Preconditions.checkArgument(calcDuration >= 0d && Double.isFinite(calcDuration),
				"Calculation duration must be non-negative and finite: %s", calcDuration);
		return calcProbFromProbStar(calcDuration/returnPeriod);
	}
}
