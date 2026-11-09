package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FaultTraceSegmentationGeometry.Measurements;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FaultTraceSegmentationGeometry.TraceRelation;

import com.google.common.base.Preconditions;

/**
 * Preliminary normal-fault passage relationships supplied by Sophia Esquenet and
 * Alba Padilla, October 27, 2026. Distances passed to this API are in km; the fits
 * use log10 of distance in meters. These are exploratory fits, not NSHM23 defaults.
 */
public class NormalFaultGeometricSegmentationProb {

	public enum CoefficientSet {
		MANUAL_60K(10.446978, 3.405079, 21.715169, 5.611446, 1.876476, 0.0082811),
		AUTOMATED_5KM(12.859280, 3.978794, 5.886363, 1.845861, 2.856840, 0.034269);

		private final double stepoverIntercept;
		private final double stepoverSlope;
		private final double gapIntercept;
		private final double gapSlope;
		private final double bendIntercept;
		private final double bendSlope;

		CoefficientSet(double stepoverIntercept, double stepoverSlope, double gapIntercept,
				double gapSlope, double bendIntercept, double bendSlope) {
			this.stepoverIntercept = stepoverIntercept;
			this.stepoverSlope = stepoverSlope;
			this.gapIntercept = gapIntercept;
			this.gapSlope = gapSlope;
			this.bendIntercept = bendIntercept;
			this.bendSlope = bendSlope;
		}
	}

	private final CoefficientSet coefficients;

	public NormalFaultGeometricSegmentationProb(CoefficientSet coefficients) {
		this.coefficients = Preconditions.checkNotNull(coefficients);
	}

	public CoefficientSet getCoefficientSet() {
		return coefficients;
	}

	public double calcGapProbability(double distanceKm) {
		return distanceProbability(distanceKm, coefficients.gapIntercept, coefficients.gapSlope);
	}

	/** Also used for the across-strike facing-end offset in a non-overlapping pair. */
	public double calcStepoverProbability(double distanceKm) {
		return distanceProbability(distanceKm, coefficients.stepoverIntercept, coefficients.stepoverSlope);
	}

	/** Raw fit, including its value below one at zero angle. Thresholding is applied separately. */
	public double calcBendProbability(double angleDegrees) {
		Preconditions.checkArgument(Double.isFinite(angleDegrees) && angleDegrees >= 0d && angleDegrees <= 180d,
				"Invalid bend angle: %s", angleDegrees);
		return logistic(coefficients.bendIntercept-coefficients.bendSlope*angleDegrees);
	}

	/**
	 * Within a parent, only bends meeting the configured threshold contribute.
	 * Between parents, overlapping traces use the overlap-clipped stepover distance;
	 * separated/touching traces multiply gap and lateral-offset relationships using
	 * components of the same facing-end connector. Qualifying bends multiply these
	 * distance terms. Inactive features contribute exactly one.
	 *
	 * <p>Between-parent geometry with an ambiguous strike axis is rejected explicitly;
	 * it cannot silently become an unconstrained transition. An internal bend needs
	 * no gap/stepover axis and remains evaluable in that case.
	 */
	public Probabilities calcProbabilities(Measurements measurement) {
		boolean internal = measurement.first.parentID == measurement.second.parentID;
		boolean bendApplied = measurement.bendTransitionDefined;
		boolean gapApplied = false;
		boolean stepoverApplied = false;
		double gapLengthKm = Double.NaN;
		double stepoverDistanceKm = Double.NaN;
		if (!internal) {
			Preconditions.checkArgument(measurement.relation != TraceRelation.AMBIGUOUS_STRIKE_AXIS,
					"Ambiguous strike axis for between-parent transition %s -> %s",
					measurement.first.sectionID, measurement.second.sectionID);
			stepoverApplied = true;
			if (measurement.relation == TraceRelation.OVERLAPPING) {
				stepoverDistanceKm = measurement.stepoverConnector.lengthKm;
			} else {
				gapApplied = true;
				gapLengthKm = measurement.facingEndGapKm;
				stepoverDistanceKm = measurement.facingEndOffsetKm;
			}
		}
		double bendProbability = bendApplied ? calcBendProbability(measurement.bendAngleDegrees) : 1d;
		double gapProbability = gapApplied ? calcGapProbability(gapLengthKm) : 1d;
		double stepoverProbability = stepoverApplied ? calcStepoverProbability(stepoverDistanceKm) : 1d;
		return new Probabilities(bendApplied, gapApplied, stepoverApplied, gapLengthKm, stepoverDistanceKm,
				bendProbability, gapProbability, stepoverProbability);
	}

	public static class Probabilities {
		public final boolean bendApplied;
		public final boolean gapApplied;
		public final boolean stepoverApplied;
		/** NaN when the corresponding distance relationship is inactive. */
		public final double gapLengthKm;
		public final double stepoverDistanceKm;
		public final double bendProbability;
		public final double gapProbability;
		public final double stepoverProbability;
		public final double combinedProbability;

		private Probabilities(boolean bendApplied, boolean gapApplied, boolean stepoverApplied,
				double gapLengthKm, double stepoverDistanceKm, double bendProbability,
				double gapProbability, double stepoverProbability) {
			this.bendApplied = bendApplied;
			this.gapApplied = gapApplied;
			this.stepoverApplied = stepoverApplied;
			this.gapLengthKm = gapLengthKm;
			this.stepoverDistanceKm = stepoverDistanceKm;
			this.bendProbability = bendProbability;
			this.gapProbability = gapProbability;
			this.stepoverProbability = stepoverProbability;
			combinedProbability = bendProbability*gapProbability*stepoverProbability;
		}
	}

	private static double distanceProbability(double distanceKm, double intercept, double slope) {
		Preconditions.checkArgument(Double.isFinite(distanceKm) && distanceKm >= 0d,
				"Invalid feature distance in km: %s", distanceKm);
		if (distanceKm == 0d)
			return 1d;
		// Adding 3 converts km to meters in log space without overflowing distanceKm*1000.
		return logistic(intercept-slope*(Math.log10(distanceKm)+3d));
	}

	private static double logistic(double logOdds) {
		if (logOdds >= 0d)
			return 1d/(1d+Math.exp(-logOdds));
		double odds = Math.exp(logOdds);
		return odds/(1d+odds);
	}
}
