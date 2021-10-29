package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints;

import com.google.common.base.Preconditions;

/**
 * Some constraints support multiple weighting/normalization schemes. This enum describes the possible options.
 * 
 * @author kevin
 *
 */
public enum ConstraintWeightingType {
	/**
	 * Constraints are normalized by the target value, so that constraints for low values are fit equally well
	 * as constraints for high values. Values in the A matrix will be divided by the target rate, and the d vector
	 * will contain 1's (before accounting for global constraint weights).
	 */
	NORMALIZED("Normalized", "Norm") {
		@Override
		public double getA_Scalar(double targetRate, double targetStdDev) {
			return 1d/targetRate;
		}

		@Override
		public double getD(double targetRate, double targetStdDev) {
			return 1d;
		}
	},
	/**
	 * Constraints are not normalized, so constraints for low values will be fit more poorly than constraints for
	 * large values. The d vector will contain the target value (before accounting for global constraint weights),
	 * and misfits are in units of rate difference.
	 */
	UNNORMALIZED("Unnormlized", "Unnorm") {
		@Override
		public double getA_Scalar(double targetRate, double targetStdDev) {
			return 1d;
		}

		@Override
		public double getD(double targetRate, double targetStdDev) {
			return targetRate;
		}
	},
	/**
	 * Normalizes targets by their standard deviation. This weights all constraints equally relative to their
	 * uncertainties. Implementation is similar to the {@link ConstraintWeightingType.NORMALIZED} scheme, except
	 * the A matrix and d vector are normalized by standard deviations instead of target values. Misfits are in
	 * units of standard deviations, so a misfit of +1 means that the solution value is 1 standard deviation above
	 * the target value.
	 */
	NORMALIZED_BY_UNCERTAINTY("Uncertainty-Weighted", "UncertWt") {
		@Override
		public double getA_Scalar(double targetRate, double targetStdDev) {
			Preconditions.checkState(targetStdDev > 0d);
			return 1d/targetStdDev;
		}

		@Override
		public double getD(double targetRate, double targetStdDev) {
			Preconditions.checkState(targetStdDev > 0d);
			return targetRate/targetStdDev;
		}
	};
	
	private String namePrefix;
	private String shortNamePrefix;

	private ConstraintWeightingType(String namePrefix, String shortNamePrefix) {
		this.namePrefix = namePrefix;
		this.shortNamePrefix = shortNamePrefix;
	}
	
	public String applyNamePrefix(String name) {
		return namePrefix+" "+name;
	}
	
	public String applyShortNamePrefix(String shortName) {
		return shortNamePrefix+shortName;
	}
	
	public String getNamePrefix() {
		return namePrefix;
	}
	
	public String getShortNamePrefix() {
		return shortNamePrefix;
	}
	
	/**
	 * 
	 * @param targetRate
	 * @param targetStdDev
	 * @return multiplicative scalar for values in the A matrix for this weighting type
	 */
	public abstract double getA_Scalar(double targetRate, double targetStdDev);
	
	/**
	 * 
	 * @param targetRate
	 * @param targetStdDev
	 * @return target value for the d vector for this weighting type
	 */
	public abstract double getD(double targetRate, double targetStdDev);
}