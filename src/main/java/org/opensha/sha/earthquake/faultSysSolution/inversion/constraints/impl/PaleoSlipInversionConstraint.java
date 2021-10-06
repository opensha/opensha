package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;

import com.google.common.base.Preconditions;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.SlipEnabledRupSet;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;

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
	
	private List<AveSlipConstraint> constraints;
	private double[] targetSlipRates;

	public PaleoSlipInversionConstraint(FaultSystemRupSet rupSet, double weight,
			List<AveSlipConstraint> constraints, double[] targetSlipRates) {
		super(NAME, SHORT_NAME, weight, false);
		setRuptureSet(rupSet);
		this.constraints = constraints;
		this.targetSlipRates = targetSlipRates;
	}

	@Override
	public int getNumRows() {
		return constraints.size();
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		for (int i=0; i<constraints.size(); i++) {
			AveSlipConstraint constraint = constraints.get(i);
			int subsectionIndex = constraint.getSubSectionIndex();
			double meanRate = targetSlipRates[subsectionIndex] / constraint.getWeightedMean();
			double lowRateBound = targetSlipRates[subsectionIndex] / constraint.getUpperUncertaintyBound();
			double highRateBound = targetSlipRates[subsectionIndex] / constraint.getLowerUncertaintyBound();
			double constraintError = highRateBound - lowRateBound;
			
			int row = startRow+i;
			
			d[row] = weight * meanRate / constraintError;
			List<Integer> rupsForSect = rupSet.getRupturesForSection(subsectionIndex);
			for (int rupIndex=0; rupIndex<rupsForSect.size(); rupIndex++) {
				int rup = rupsForSect.get(rupIndex);
				int sectIndexInRup = rupSet.getSectionsIndicesForRup(rup).indexOf(subsectionIndex);
				double slipOnSect = slipAlongModule.calcSlipOnSectionsForRup(rupSet, aveSlipModule, rup)[sectIndexInRup]; 
				double probVisible = AveSlipConstraint.getProbabilityOfObservedSlip(slipOnSect);
				setA(A, row, rup, weight * probVisible / constraintError);
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
	}

}
