package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.List;

import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * Constraints section slip rates to match the given target rate. It can apply normalized or
 * unnormalized constraints, or both:
 * 
 * If normalized, slip rate misfit is % difference for each section (recommended since it helps
 * fit slow-moving faults). Note that constraints for sections w/ slip rate < 0.1 mm/yr is not
 * normalized by slip rate -- otherwise misfit will be huge (GEOBOUND model has 10e-13 slip rates
 * that will dominate misfit otherwise)
 * 
 * If unnormalized, misfit is absolute difference.
 * 
 * Set the weighting with the SlipRateConstraintWeightingType enum.
 * 
 * @author kevin
 *
 */
public class SlipRateInversionConstraint extends InversionConstraint {
	
	private transient FaultSystemRupSet rupSet;
	private transient AveSlipModule aveSlipModule;
	private transient SlipAlongRuptureModel slipAlongModule;
	private transient SectSlipRates targetSlipRates;
	
	public static final double DEFAULT_FRACT_STD_DEV = 0.5;

	public SlipRateInversionConstraint(double weight, ConstraintWeightingType weightingType,
			FaultSystemRupSet rupSet) {
		this(weight, weightingType, rupSet, rupSet.requireModule(AveSlipModule.class),
				rupSet.requireModule(SlipAlongRuptureModel.class), rupSet.requireModule(SectSlipRates.class));
	}

	public SlipRateInversionConstraint(double weight, ConstraintWeightingType weightingType,
			FaultSystemRupSet rupSet, AveSlipModule aveSlipModule, SlipAlongRuptureModel slipAlongModule,
			SectSlipRates targetSlipRates) {
		super(weightingType.applyNamePrefix("Slip Rate"), weightingType.applyShortNamePrefix("SlipRate"),
				weight, false, weightingType);
		this.weightingType = weightingType;
		setRuptureSet(rupSet);
	}

	@Override
	public int getNumRows() {
		// one row for each section
		return rupSet.getNumSections();
	}
	
	/**
	 * Calculates slip rate standard deviations. If zeros are encountered, the default fractional standard deviation
	 * will be applied, unless the fault is a GeoJSONFaultSection with a high and low rate attached, in which case
	 * the high and low rate are assumed to be +/- 2-sigma bounds
	 * 
	 * @param targetSlipRates
	 * @return
	 */
	public static double[] getSlipRateStdDevs(SectSlipRates targetSlipRates, double defaultFractStdDev) {
		int numDefaults = 0;
		int numInferred = 0;
		
		FaultSystemRupSet rupSet = targetSlipRates.getParent();
		int numSections = rupSet.getNumSections();
		
		double[] stdDevs = new double[numSections];
		for (int s=0; s<numSections; s++) {
			double stdDev = targetSlipRates.getSlipRateStdDev(s);
			if (stdDev == 0d || Double.isNaN(stdDev)) {
				FaultSection sect = rupSet.getFaultSectionData(s);
				FeatureProperties props = sect instanceof GeoJSONFaultSection ?
						((GeoJSONFaultSection)sect).getProperties() : null;
				double meanRate = targetSlipRates.getSlipRate(s);
				if (props != null && props.containsKey("HighRate") && props.containsKey("LowRate")) {
					// assume that we have +/- 2 sigma values (i.e., 95% confidence) to estimate a standard deviation
					double high = props.getDouble("HighRate", Double.NaN);
					double low = props.getDouble("LowRate", Double.NaN);
					// +/- 2 sigma means that there are 4 sigmas between low and high
					stdDev = UncertaintyBoundType.TWO_SIGMA.estimateStdDev(meanRate, low, high);
					numInferred++;
				} else {
					stdDev = meanRate*defaultFractStdDev;
					numDefaults++;
				}
			}
			stdDevs[s] = stdDev;
		}
		if (numDefaults > 0 || numInferred > 0) {
			System.err.println("WARNING: "+(numDefaults+numInferred)+"/"+numSections+" sections were missing slip rate standard "
					+ "deviations, and uncertainty weighting is selected.");
			if (numDefaults > 0)
				System.err.println("\tUsed default fractional standard deviation for "+numDefaults
						+" values: std = "+(float)defaultFractStdDev+" x slip_rate");
			if (numInferred > 0)
				System.err.println("\tInferred standard deviation for "+numDefaults
						+" values from upper and lower bounds, assuming +/- 2 sigma: std = (upper-lower)/4");	
		}
		return stdDevs;
	}
	
	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		int numRuptures = rupSet.getNumRuptures();
		int numSections = rupSet.getNumSections();
		
		double[] weights = new double[numSections];
		for (int s=0; s<numSections; s++)
			weights[s] = this.weight;
		if (weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
			double[] stdDevs = getSlipRateStdDevs(targetSlipRates, DEFAULT_FRACT_STD_DEV);
			for (int s=0; s<numSections; s++)
				if (stdDevs[s] != 0d)
					weights[s] /= stdDevs[s];
		} 
		
		// A matrix component of slip-rate constraint 
		for (int rup=0; rup<numRuptures; rup++) {
			double[] slips = slipAlongModule.calcSlipOnSectionsForRup(rupSet, aveSlipModule, rup);
			List<Integer> sects = rupSet.getSectionsIndicesForRup(rup);
			for (int i=0; i < slips.length; i++) {
				int sectIndex = sects.get(i);
				int row = startRow+sectIndex;
				int col = rup;
				double val = slips[i];
				if (weightingType == ConstraintWeightingType.NORMALIZED) {
					double target = targetSlipRates.getSlipRate(sectIndex);
					if (target != 0d) {
						// Note that constraints for sections w/ slip rate < 0.1 mm/yr is not normalized by slip rate
						// -- otherwise misfit will be huge (e.g., UCERF3 GEOBOUND model has 10e-13 slip rates that will
						// dominate misfit otherwise)
						if (target < 1e-4 || Double.isNaN(target))
							target = 1e-4;
						val = slips[i]/target;
					}
				}
				setA(A, row, col, val*weights[sectIndex]);
				numNonZeroElements++;
			}
		}  
		// d vector component of slip-rate constraint
		for (int sectIndex=0; sectIndex<numSections; sectIndex++) {
			double target = targetSlipRates.getSlipRate(sectIndex);
			double val = target;
			if (weightingType == ConstraintWeightingType.NORMALIZED) {
				if (target == 0d)
					// minimize
					val = 0d;
				else if (target < 1E-4 || Double.isNaN(target))
					// For very small slip rates, do not normalize by slip rate
					//  (normalize by 0.0001 instead) so they don't dominate misfit
					val = targetSlipRates.getSlipRate(sectIndex)/1e-4;
				else
					val = 1d;
			}
			int row = startRow+sectIndex;
			d[row] = val*weights[sectIndex];
			if (Double.isNaN(d[sectIndex]) || d[sectIndex]<0)
				throw new IllegalStateException("d["+sectIndex+"]="+d[sectIndex]+" is NaN or 0!  target="+target);
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
