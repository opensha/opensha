package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.SlipEnabledRupSet;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;

public class SlipRateInversionConstraint extends InversionConstraint {
	
	public static final String NAME = "Slip Rate";
	public static final String SHORT_NAME = "SlipRate";
	
	private double weightNormalized;
	private double weightUnnormalized;
	private SlipRateConstraintWeightingType weightingType;
	private SlipEnabledRupSet rupSet;
	private double[] targetSlipRates;

	public SlipRateInversionConstraint(double weightNormalized, double weightUnnormalized,
			SlipRateConstraintWeightingType weightingType, SlipEnabledRupSet rupSet,
			double[] targetSlipRates) {
		this.weightNormalized = weightNormalized;
		this.weightUnnormalized = weightUnnormalized;
		this.weightingType = weightingType;
		this.rupSet = rupSet;
		this.targetSlipRates = targetSlipRates;
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public int getNumRows() {
		if (weightingType == SlipRateConstraintWeightingType.BOTH)
			// one row for each section and for each weight type
			return 2*rupSet.getNumSections();
		// one row for each section
		return rupSet.getNumSections();
	}

	@Override
	public boolean isInequality() {
		return false;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		int numRuptures = rupSet.getNumRuptures();
		int numSections = rupSet.getNumSections();
		// A matrix component of slip-rate constraint 
		for (int rup=0; rup<numRuptures; rup++) {
			double[] slips = rupSet.getSlipOnSectionsForRup(rup);
			List<Integer> sects = rupSet.getSectionsIndicesForRup(rup);
			for (int i=0; i < slips.length; i++) {
				int row = sects.get(i);
				int col = rup;
				double val;
				if (weightingType == SlipRateConstraintWeightingType.UNNORMALIZED
						|| weightingType == SlipRateConstraintWeightingType.BOTH) {
					setA(A, startRow+row, col, weightUnnormalized*slips[i]);
					numNonZeroElements++;		
				}
				if (weightingType == SlipRateConstraintWeightingType.NORMALIZED_BY_SLIP_RATE
						|| weightingType == SlipRateConstraintWeightingType.BOTH) {  
					if (weightingType == SlipRateConstraintWeightingType.BOTH)
						row += numSections;
					// Note that constraints for sections w/ slip rate < 0.1 mm/yr is not normalized by slip rate
					// -- otherwise misfit will be huge (GEOBOUND model has 10e-13 slip rates that will dominate
					// misfit otherwise)
					if (targetSlipRates[sects.get(i)] < 1E-4 || Double.isNaN(targetSlipRates[sects.get(i)]))  
						val = slips[i]/0.0001;  
					else {
						val = slips[i]/targetSlipRates[sects.get(i)]; 
					}
					setA(A, startRow+row, col, weightNormalized*val);
					numNonZeroElements++;
				}
			}
		}  
		// d vector component of slip-rate constraint
		for (int sect=0; sect<numSections; sect++) {
			if (Double.isNaN(targetSlipRates[sect])) {
				// Treat NaN slip rates as 0 (minimize)
				d[startRow+sect] = 0;
				if (weightingType == SlipRateConstraintWeightingType.BOTH)
					d[startRow+numSections+sect] = 0;
			}
			if (weightingType == SlipRateConstraintWeightingType.UNNORMALIZED
					|| weightingType == SlipRateConstraintWeightingType.BOTH)
				d[startRow+sect] = weightUnnormalized * targetSlipRates[sect];
			if (weightingType == SlipRateConstraintWeightingType.NORMALIZED_BY_SLIP_RATE
					|| weightingType == SlipRateConstraintWeightingType.BOTH) {
				double val;
				if (targetSlipRates[sect]<1E-4)
					// For very small slip rates, do not normalize by slip rate
					//  (normalize by 0.0001 instead) so they don't dominate misfit
					val = weightNormalized * targetSlipRates[sect]/0.0001;
				else  // Normalize by slip rate
					val = weightNormalized;
				if (weightingType == SlipRateConstraintWeightingType.BOTH)
					d[startRow+numSections+sect] = val;
				else
					d[startRow+sect] = val;
			}
			if (Double.isNaN(d[sect]) || d[sect]<0)
				throw new IllegalStateException("d["+sect+"] is NaN or 0!  sectSlipRateReduced["
						+sect+"] = "+targetSlipRates[sect]);
		}
		return numNonZeroElements;
	}

}
