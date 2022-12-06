package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.uncertainty.BoundedUncertainty;
import org.opensha.commons.data.uncertainty.Uncertainty;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;

import com.google.common.base.Preconditions;

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
	
	public void setInequality(boolean inequality) {
		super.inequality = inequality;
	}

	@Override
	public int getNumRows() {
		return aveSlipConstraints.size();
	}
	
	protected List<SectMappedUncertainDataConstraint> inferRateConstraints(FaultSystemRupSet rupSet,
			List<? extends SectMappedUncertainDataConstraint> aveSlipConstraints, boolean applySlipRateUncertainty) {
		return PaleoseismicConstraintData.inferRatesFromSlipConstraints(
				rupSet, aveSlipConstraints, applySlipRateUncertainty);
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		
		// these are constraints on average slip, but we need to convert them to constraints on rates
		List<SectMappedUncertainDataConstraint> rateConstraints = inferRateConstraints(
				rupSet, aveSlipConstraints, applySlipRateUncertainty);
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
	}
	
	/**
	 * Special version that uses the UCERF3 uncertainty propagation, which works pretty well but doesn't support
	 * adding slip rate uncertainties, and could blow up if the lower bound is near (or below) zero
	 * @author kevin
	 *
	 */
	public static class UCERF3 extends PaleoSlipInversionConstraint {

		public UCERF3(FaultSystemRupSet rupSet, double weight,
				List<? extends SectMappedUncertainDataConstraint> aveSlipConstraints,
				PaleoSlipProbabilityModel slipObsProbModel) {
			super(rupSet, weight, aveSlipConstraints, slipObsProbModel, false);
		}

		@Override
		protected List<SectMappedUncertainDataConstraint> inferRateConstraints(FaultSystemRupSet rupSet,
				List<? extends SectMappedUncertainDataConstraint> aveSlipConstraints, boolean applySlipRateUncertainty) {
			SectSlipRates targetSlipRates = rupSet.requireModule(SectSlipRates.class);
			
			Preconditions.checkState(!applySlipRateUncertainty);
			
			List<SectMappedUncertainDataConstraint> inferred = new ArrayList<>();
			
			for (SectMappedUncertainDataConstraint constraint : aveSlipConstraints) {
				// this is a constraint on average slip, but we need to convert it to a constraint on rates
				
				// slip rate, in m/yr
				double targetSlipRate = targetSlipRates.getSlipRate(constraint.sectionIndex);
				
				// average slip, in m
				double aveSlip = constraint.bestEstimate;
				// average slip uncertainties
				Preconditions.checkState(constraint.uncertainties[0] instanceof BoundedUncertainty,
						"UCERF3 paleo slip uncertainty estimation requires bounded uncertainties");
				BoundedUncertainty slipUncert = (BoundedUncertainty)constraint.uncertainties[0];
				
				Preconditions.checkState(slipUncert.lowerBound > 0d,
						"UCERF3 paleo slip uncertainty estimation can't handle lower bounds <= 0");
				
				System.out.println("Inferring rate constraint from paleo slip constraint on "+constraint.sectionName);
				System.out.println("\tslip="+(float)aveSlip+"\tuncert="+slipUncert);
				System.out.println("\tslip rate="+(float)targetSlipRate);
				
				// rate estimate: r = s / d
				double meanRate = targetSlipRate / aveSlip;
				double rateSD = slipUncert.type.estimateStdDev(meanRate,
						targetSlipRate / slipUncert.upperBound,
						targetSlipRate / slipUncert.lowerBound);
				
				System.out.println("\trate="+(float)meanRate+" +/- "+(float)rateSD);
				
				inferred.add(new SectMappedUncertainDataConstraint(constraint.name, constraint.sectionIndex,
						constraint.sectionName, constraint.dataLocation, meanRate, new Uncertainty(rateSD)));
			}
			
			return inferred;
		}
		
	}

}
