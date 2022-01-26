package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import com.google.common.base.Preconditions;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

public class SectionTotalRateConstraint extends InversionConstraint {
	
	private transient FaultSystemRupSet rupSet;
	private double[] sectRates;
	private double[] sectRateStdDevs;
	private boolean nucleation;

	public SectionTotalRateConstraint(FaultSystemRupSet rupSet, double weight, double[] sectRates, boolean nucleation) {
		this(rupSet, weight, ConstraintWeightingType.NORMALIZED, sectRates, null, nucleation);
	}
	
	public SectionTotalRateConstraint(FaultSystemRupSet rupSet, double weight, ConstraintWeightingType weightType,
			double[] sectRates, double[] sectRateStdDevs, boolean nucleation) {
		super(weightType.applyNamePrefix("Subsection Total "+(nucleation?"Nucleation":"Participation")+" Rates"),
				weightType.applyShortNamePrefix("SubSect"+(nucleation?"Nucl":"Part")+"Rates"), weight, false, weightType);
		this.rupSet = rupSet;
		this.nucleation = nucleation;
		Preconditions.checkState(rupSet.getNumSections() == sectRates.length, "section rates array isn't the right length");
		this.sectRates = sectRates;
		if (weightType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
			Preconditions.checkState(sectRateStdDevs != null, "Must supply standard deviations to weight by uncertainty");
			Preconditions.checkState(sectRateStdDevs.length == sectRates.length, "Standard deviations array isn't the right length");
			this.sectRateStdDevs = sectRateStdDevs;
		}
	}

	public double[] getSectRates() {
		return sectRates;
	}

	public double[] getSectRateStdDevs() {
		return sectRateStdDevs;
	}

	public boolean isNucleation() {
		return nucleation;
	}

	@Override
	public int getNumRows() {
		return sectRates.length;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZero = 0l;
		
		for (int s=0; s<sectRates.length; s++) {
			if (Double.isNaN(sectRates[s]))
				continue;
			double stdDev = 0d;
			if (sectRateStdDevs != null) {
				stdDev = sectRateStdDevs[s];
				if (stdDev == 0d || !Double.isFinite(stdDev)) {
					Preconditions.checkState(sectRates[s] == 0d,
							"Zero standard deviations are only supported when the target rate is zero: "
							+ "sectRates[%s]=%s, sectRateStdDevs[%s]=%s",
							s, sectRates[s], s, sectRateStdDevs[s]);
					continue;
				}
			}
			
			double scale = weightingType.getA_Scalar(sectRates[s], stdDev);
			double target = weightingType.getD(sectRates[s], stdDev);
			
			int row = startRow+s;
			d[row] = target*weight;
			
			double sectArea = rupSet.getAreaForSection(s);
			
			for (int rup : rupSet.getRupturesForSection(s)) {
				double val = scale*weight;
				if (nucleation) {
					// these are nucleation rates
					double rupArea = rupSet.getAreaForRup(rup);
					val *= sectArea / rupArea;
				}
				setA(A, row, rup, val);
				numNonZero++;
			}
		}
		return numNonZero;
	}

	@Override
	public void setRuptureSet(FaultSystemRupSet rupSet) {
		Preconditions.checkState(rupSet.getNumSections() == sectRates.length);
		this.rupSet = rupSet;
	}

}
