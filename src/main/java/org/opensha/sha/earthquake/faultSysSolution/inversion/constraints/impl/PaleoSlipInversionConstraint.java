package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * Constraint to match paleoseismic event rates inferred from mean slip on
 * subsections.
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class PaleoSlipInversionConstraint extends InversionConstraint {
	
	public static final String NAME = "Paleoseismic Average Slip";
	public static final String SHORT_NAME = "PaleoSlip";
	
	private transient FaultSystemRupSet rupSet;
	private transient AveSlipModule aveSlipModule;
	private transient SlipAlongRuptureModel slipAlongModule;
	private transient SectSlipRates targetSlipRates;
	
	private List<? extends SectMappedUncertainDataConstraint> aveSlipConstraints;
	private PaleoSlipProbabilityModel slipObsProbModel;
	private boolean applySlipRateUncertainty;

	public PaleoSlipInversionConstraint(FaultSystemRupSet rupSet, double weight,
			List<? extends SectMappedUncertainDataConstraint> aveSlipConstraints,
			PaleoSlipProbabilityModel slipObsProbModel, boolean applySlipRateUncertainty) {
		this(rupSet, weight, aveSlipConstraints, slipObsProbModel, applySlipRateUncertainty,
				ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY);
	}

	public PaleoSlipInversionConstraint(FaultSystemRupSet rupSet, double weight,
			List<? extends SectMappedUncertainDataConstraint> aveSlipConstraints,
			PaleoSlipProbabilityModel slipObsProbModel, boolean applySlipRateUncertainty,
			ConstraintWeightingType weightingType) {
		super(NAME, SHORT_NAME, weight, false, weightingType);
		setRuptureSet(rupSet);
		this.aveSlipConstraints = aveSlipConstraints;
		this.slipObsProbModel = slipObsProbModel;
		this.applySlipRateUncertainty = applySlipRateUncertainty;
	}

	@Override
	public int getNumRows() {
		return aveSlipConstraints.size();
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		
		// these are constraints on average slip, but we need to convert them to constraints on rates
		List<SectMappedUncertainDataConstraint> rateConstraints =
				PaleoseismicConstraintData.inferRatesFromSlipConstraints(
						targetSlipRates, aveSlipConstraints, applySlipRateUncertainty);
		for (int i=0; i<rateConstraints.size(); i++) {
			SectMappedUncertainDataConstraint rateConstraint = rateConstraints.get(i);
			
			double stdDev = rateConstraint.getPreferredStdDev();
			double meanRate = rateConstraint.bestEstimate;
			
			int row = startRow+i;
			
			d[row] = weight * weightingType.getD(meanRate, stdDev);
			double scalar = weightingType.getA_Scalar(meanRate, stdDev);
			List<Integer> rupsForSect = rupSet.getRupturesForSection(rateConstraint.sectionIndex);
			for (int rupIndex=0; rupIndex<rupsForSect.size(); rupIndex++) {
				int rup = rupsForSect.get(rupIndex);
				int sectIndexInRup = rupSet.getSectionsIndicesForRup(rup).indexOf(rateConstraint.sectionIndex);
				double slipOnSect = slipAlongModule.calcSlipOnSectionsForRup(rupSet, aveSlipModule, rup)[sectIndexInRup]; 
				double probVisible = slipObsProbModel.getProbabilityOfObservedSlip(slipOnSect);
				setA(A, row, rup, weight * probVisible * scalar);
				numNonZeroElements++;
			}
		}
		return numNonZeroElements;
	}

	@Override
	public void setRuptureSet(FaultSystemRupSet rupSet) {
		this.rupSet = rupSet;
		this.aveSlipModule = rupSet.requireModule(AveSlipModule.class);
		this.slipAlongModule = rupSet.requireModule(SlipAlongRuptureModel.class);
		this.targetSlipRates = rupSet.requireModule(SectSlipRates.class);
	}

}
