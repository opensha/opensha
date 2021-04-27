package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
//import org.opensha.sha.faultSurface.FaultSection;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.SlipEnabledRupSet;
//import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;
import org.opensha.commons.data.CSVFile;

/**
 * Constrain section slip rates to match the given target, with the weighting for
 * each section scaled by its CoefficientOfVariance (rate/stddev).
 * 
 * @author chrisbc
 *
 */
public class SlipRateUncertaintyInversionConstraint extends InversionConstraint {

	public static final String NAME = "Slip Rate with weighting adjusted for uncertainty";
	public static final String SHORT_NAME = "SlipRateUncertaintyAdjusted";

	private int weight;
	private SlipEnabledRupSet rupSet;
	private double[] targetSlipRates;

	// max and min coefficient of variance (unnormalised)
	private Double maxCOV = Double.MIN_VALUE;
	private Double minCOV = Double.MAX_VALUE;

	// weightNormalisation
	private int weightScalingOrderOfMagnitude;

	// list of the normalised weights, one for each subsection
	private double[] targetNormalisedWeights;

	private CSVFile<String> subSectionWeightingsCSV;

	/**
	 * The constructor 
	 * 
	 * @param weight sets the overall weighting for this constraint
	 * @param weightScalingOrderOfMagnitude sets the range of weight scaling by orders of magnitude (typically 2 tp 4) 
	 * @param rupSet
	 * @param targetSlipRates from deformation model
	 * @param targetSlipRateStdDevs from deformation model
	 */
	public SlipRateUncertaintyInversionConstraint(int weight, int weightScalingOrderOfMagnitude,
			SlipEnabledRupSet rupSet, double[] targetSlipRates, double[] targetSlipRateStdDevs) {
		this.weight = weight;
		this.rupSet = rupSet;
		this.targetSlipRates = targetSlipRates;
		this.targetNormalisedWeights = new double[targetSlipRates.length];
		this.weightScalingOrderOfMagnitude = weightScalingOrderOfMagnitude;

		subSectionWeightingsCSV = new CSVFile<String>(true);
		subSectionWeightingsCSV.addLine("section_idx", "subsection_name", "COV", "normalised_weight",
				"normalised_weight", "target_slip_rate");

		// get the min & max COVs for normalisation
		setMinMaxCOV(targetSlipRates, targetSlipRateStdDevs);

		// build the table of normalized weights.
		buildNormalisedWeightTable(targetSlipRates, targetSlipRateStdDevs);

	}

	/**
	 * Returns a CSVFile object containing the weightings created for the given rupture set/deformation model
	 * 
	 * @return
	 */
	public CSVFile<String> getSubSectionWeightingsCSV() {
		return subSectionWeightingsCSV;
	}

	/**
	 * The coefficient of variance (COV) for a value (rate) and a std deviation.
	 * 
	 * @param rate
	 * @param stdDev
	 * @return
	 */
	private Double coefficientOfVariance(double rate, double stdDev) {
		return new Double(rate / stdDev);
	}

	/**
	 * Find the min/max COV values across the deformation model. This must be done before the weights are
	 * calculated.  
	 * 
	 * @param targetSlipRates
	 * @param targetSlipRateStdDevs
	 */
	private void setMinMaxCOV(double[] targetSlipRates, double[] targetSlipRateStdDevs) {
		assert targetSlipRates.length == targetSlipRateStdDevs.length;
		Double cov;
		for (int i = 0; i < targetSlipRates.length; i++) {
			cov = coefficientOfVariance(targetSlipRates[i], targetSlipRateStdDevs[i]);
			if (cov.isNaN() || cov.isInfinite())
				continue;
			minCOV = Double.min(cov, minCOV);
			maxCOV = Double.max(cov, maxCOV);
		}
		assert !(minCOV == Double.MAX_VALUE);
		assert !(maxCOV == Double.MIN_VALUE);

	}

	/**
	 * Scales a value (between 0 and 1) to a compression curve with the given rate (5 to 100).
	 * 
	 * see https://www.desmos.com/calculator/eujq6xh1wq
	 * 
	 * @param compression_rate
	 * @param x
	 * @return scaled value
	 */
	@SuppressWarnings("unused")
	private Double compress(int compression_rate, Double x) {
		assert (5 <= compression_rate || compression_rate <= 100);
		assert (0 < x || x <= 1);	
		return 1 - Math.pow(Math.E, (-1 * compression_rate * x));
	}

	/**
	 * Build the normalised weight table and the CSVFile rows.  
	 * 
	 * @param targetSlipRates
	 * @param targetSlipRateStdDevs
	 */
	private void buildNormalisedWeightTable(double[] targetSlipRates, double[] targetSlipRateStdDevs) {
		assert targetSlipRates.length == targetSlipRateStdDevs.length;
		Double cov;
		Double normalised_weight;
		
		@SuppressWarnings("unused")
		Integer compression = new Integer(10); //may still be used or removed

		double minNormalisedWeight = 1;
		double maxNormalisedWeight = Math.pow(10, weightScalingOrderOfMagnitude);

		System.out.println("weightScalingOrderOfMagnitude " + weightScalingOrderOfMagnitude + " minCOV: " + minCOV
				+ "; maxCOV: " + maxCOV + "; maxNormalisedWeight: " + maxNormalisedWeight);

		for (int i = 0; i < targetSlipRates.length; i++) {
			cov = coefficientOfVariance(targetSlipRates[i], targetSlipRateStdDevs[i]);
			normalised_weight = translate(cov, minCOV, maxCOV, minNormalisedWeight, maxNormalisedWeight);

			if (normalised_weight.isNaN() || normalised_weight.isInfinite()) {
				targetNormalisedWeights[i] = 1;
			} else {
				targetNormalisedWeights[i] = normalised_weight;
			}

			
			/*
			 * Some test code for soft-knee compression
			 */
//			if (cov.isNaN() || cov.isInfinite()) {
//				targetNormalisedWeights[i] = 1; //weightScalingOrderOfMagnitude;
//			} else {
//				//targetNormalisedWeights[i] = translate(cov, minCOV, maxCOV, weightScalingOrderOfMagnitude, maxWeight);				
//				//targetNormalisedWeights[i] = cov/Double.max(cov, weightScalingOrderOfMagnitude); Kiran's hard knee
//				targetNormalisedWeights[i] = compress(compression, normalised_weight);				
//			}

			assert Double.isFinite(targetNormalisedWeights[i]);

			subSectionWeightingsCSV.addLine(new Integer(i).toString(), rupSet.getFaultSectionData(i).getName(),
					new Double(cov).toString(), normalised_weight.toString(),
					new Double(targetNormalisedWeights[i]).toString(), new Double(targetSlipRates[i]).toString());
		}
	}

	/**
	 * Translate value from one range of values (left) onto another (right)
	 * 
	 * see: https://stackoverflow.com/a/1969274
	 */
	private Double translate(double value, double leftMin, double leftMax, double rightMin, double rightMax) {
		// Figure out how 'wide' each range is
		double leftSpan = leftMax - leftMin;
		double rightSpan = rightMax - rightMin;
		// Convert the left range into a 0-1 range (float)
		double valueScaled = (value - leftMin) / leftSpan;
		// Convert the 0-1 range into a value in the right range.
		return new Double(rightMin + (valueScaled * rightSpan));
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

		// A matrix component of the constraint
		for (int rup = 0; rup < numRuptures; rup++) {
			double[] slips = rupSet.getSlipOnSectionsForRup(rup); // slip on rupture sections
			List<Integer> sects = rupSet.getSectionsIndicesForRup(rup); // subsection indices for the rupture
			for (int i = 0; i < slips.length; i++) {
				int row = sects.get(i);
				int col = rup;

				// TODO what should go in the A value ??
				double singleEventDisplacement = slips[i];
				double normalised_weight = targetNormalisedWeights[sects.get(i)];

				if (Double.isNaN(normalised_weight)) {
					setA(A, startRow + row, col, 0);
				} else {
					setA(A, startRow + row, col, weight * normalised_weight * singleEventDisplacement);
					numNonZeroElements++;
				}
			}
		}

		// d vector component of slip-rate constraint
		for (int sect = 0; sect < numSections; sect++) {
			if (Double.isNaN(targetSlipRates[sect]) || Double.isNaN(targetNormalisedWeights[sect])) {
				// Treat NaN slip rates as 0 (minimize)
				d[startRow + sect] = 0;
			} else {
				double normalised_weight = targetNormalisedWeights[sect];
				double val = weight * normalised_weight * targetSlipRates[sect];
				d[startRow + sect] = val;
			}

			if (Double.isNaN(d[sect]) || d[sect] < 0)
				throw new IllegalStateException(
						"d[" + sect + "] is NaN or 0!  sectSlipRateReduced[" + sect + "] = " + targetSlipRates[sect]);
		}
		return numNonZeroElements;
	}

}
