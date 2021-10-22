package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.Uncertainty;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.utils.aveSlip.U3AveSlipConstraint;

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

	public PaleoSlipInversionConstraint(FaultSystemRupSet rupSet, double weight,
			List<? extends SectMappedUncertainDataConstraint> aveSlipConstraints) {
		super(NAME, SHORT_NAME, weight, false);
		setRuptureSet(rupSet);
		this.aveSlipConstraints = aveSlipConstraints;
	}

	@Override
	public int getNumRows() {
		return aveSlipConstraints.size();
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		for (int i=0; i<aveSlipConstraints.size(); i++) {
			// this is a constraint on average slip, but we need to convert it to a constraint on rates
			SectMappedUncertainDataConstraint constraint = aveSlipConstraints.get(i);
			
			double targetSlipRate = targetSlipRates.getSlipRate(constraint.sectionIndex);
			
			double meanRate = targetSlipRate / constraint.bestEstimate;
			
			// TODO: this is how I think we should do uncertainties, needs to be verified
//			Uncertainty origUncertainty = constraint.uncertainties[0];
//			
//			Uncertainty scaledUncertainty = new Uncertainty(origUncertainty.type,
//					targetSlipRate / origUncertainty.upperBound,
//					targetSlipRate / origUncertainty.lowerBound);
//			double stdDev = scaledUncertainty.stdDev;
			
			// this is how we did uncertainties in UCERF3
			Uncertainty origUncertainty = constraint.uncertainties[0];
			double lowRateBound = targetSlipRate / origUncertainty.upperBound;
			double highRateBound = targetSlipRate / origUncertainty.lowerBound;
			double stdDev = highRateBound - lowRateBound;
			
			int row = startRow+i;
			
			d[row] = weight * meanRate / stdDev;
			List<Integer> rupsForSect = rupSet.getRupturesForSection(constraint.sectionIndex);
			for (int rupIndex=0; rupIndex<rupsForSect.size(); rupIndex++) {
				int rup = rupsForSect.get(rupIndex);
				int sectIndexInRup = rupSet.getSectionsIndicesForRup(rup).indexOf(constraint.sectionIndex);
				double slipOnSect = slipAlongModule.calcSlipOnSectionsForRup(rupSet, aveSlipModule, rup)[sectIndexInRup]; 
				double probVisible = U3AveSlipConstraint.getProbabilityOfObservedSlip(slipOnSect);
				setA(A, row, rup, weight * probVisible / stdDev);
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
