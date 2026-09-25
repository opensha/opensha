package org.opensha.sha.calc;

import java.math.BigDecimal;
import java.util.Objects;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.TimeSpan.DurationUnits;

import com.google.common.base.Preconditions;

/**
 * A hazard exceedance target, expressed as a probability over a reference duration. The equivalent probability for
 * another curve duration is computed with a Poisson conversion.
 */
public final class ReturnPeriod {

	public static final ReturnPeriod TWO_IN_50 = new ReturnPeriod(0.02, 50d, "2% in 50 years", "TWO_IN_50");
	public static final ReturnPeriod TEN_IN_50 = new ReturnPeriod(0.10, 50d, "10% in 50 years", "TEN_IN_50");
	public static final ReturnPeriod FORTY_IN_50 = new ReturnPeriod(0.40, 50d, "40% in 50 years", "FORTY_IN_50");

	private static final ReturnPeriod[] DEFAULTS = { TWO_IN_50, TEN_IN_50 };
	private static final ReturnPeriod[] STANDARD_VALUES = { TWO_IN_50, TEN_IN_50, FORTY_IN_50 };

	private final double probability;
	private final double durationYears;
	private final String label;
	private final String name;

	private ReturnPeriod(double probability, double durationYears, String label, String name) {
		Preconditions.checkArgument(probability > 0d && probability < 1d,
				"Probability must be in (0,1): %s", probability);
		Preconditions.checkArgument(durationYears > 0d && Double.isFinite(durationYears),
				"Duration must be positive and finite: %s", durationYears);
		this.probability = probability;
		this.durationYears = durationYears;
		this.label = Preconditions.checkNotNull(label);
		this.name = Preconditions.checkNotNull(name);
	}

	/** Creates an exceedance target such as 2% in 30 years. */
	public static ReturnPeriod forExceedanceProbability(double probability, double durationYears) {
		String percent = format(100d*probability);
		String duration = format(durationYears);
		String label = percent+"% in "+duration+" year"+(durationYears == 1d ? "" : "s");
		String percentName;
		if (probability == 0.02)
			percentName = "TWO";
		else if (probability == 0.10)
			percentName = "TEN";
		else if (probability == 0.40)
			percentName = "FORTY";
		else
			percentName = token(percent);
		String name = percentName+"_IN_"+token(duration);
		return new ReturnPeriod(probability, durationYears, label, name);
	}

	private static String format(double value) {
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	/** Creates an exceedance target with duration taken from a {@link TimeSpan}. */
	public static ReturnPeriod forExceedanceProbability(double probability, TimeSpan timeSpan) {
		return forExceedanceProbability(probability, durationYears(timeSpan));
	}

	/**
	 * Returns conventional 2%- and 10%-in-50 targets for one- and 50-year curves. For other durations, returns 2% and
	 * 10% in that duration.
	 */
	public static ReturnPeriod[] defaultsForCurveDuration(TimeSpan timeSpan) {
		double durationYears = durationYears(timeSpan);
		if ((float)durationYears == 1f || (float)durationYears == 50f)
			return DEFAULTS.clone();
		return new ReturnPeriod[] { forExceedanceProbability(0.02, durationYears),
				forExceedanceProbability(0.10, durationYears) };
	}

	/** Returns the conventional 2%- and 10%-in-50 targets. */
	public static ReturnPeriod[] defaults() {
		return DEFAULTS.clone();
	}

	/** Returns all predefined standard targets. */
	public static ReturnPeriod[] standardValues() {
		return STANDARD_VALUES.clone();
	}

	private static String token(String value) {
		return value.replace('-', 'M').replace('.', 'P');
	}

	private static double durationYears(TimeSpan timeSpan) {
		return Preconditions.checkNotNull(timeSpan, "TimeSpan cannot be null").getDuration(DurationUnits.YEARS);
	}

	public double getReferenceProbability() {
		return probability;
	}

	public double getReferenceDurationYears() {
		return durationYears;
	}

	public double getProbability(double curveDurationYears) {
		return ReturnPeriodUtils.calcExceedanceProb(probability, durationYears, curveDurationYears);
	}

	public double getProbability(TimeSpan curveTimeSpan) {
		return getProbability(durationYears(curveTimeSpan));
	}

	public double getReturnPeriodYears() {
		return ReturnPeriodUtils.calcReturnPeriod(probability, durationYears);
	}

	public String getLabel() {
		return label;
	}

	/** Stable uppercase token suitable for filenames. */
	public String name() {
		return name;
	}

	@Override
	public int hashCode() {
		return Objects.hash(probability, durationYears);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof ReturnPeriod))
			return false;
		ReturnPeriod other = (ReturnPeriod)obj;
		return Double.doubleToLongBits(probability) == Double.doubleToLongBits(other.probability)
				&& Double.doubleToLongBits(durationYears) == Double.doubleToLongBits(other.durationYears);
	}

	@Override
	public String toString() {
		return label;
	}
}
