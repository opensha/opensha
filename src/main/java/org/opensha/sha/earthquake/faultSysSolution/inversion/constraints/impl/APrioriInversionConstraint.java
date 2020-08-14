package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * This will constrain a the inversion to stay near to a set of a priori rupture rates. It can apply
 * different weights to ruptures with zero rates in the a priori model.
 * 
 * For UCERF3, this constraint allowed us to keep rates close to UCERF2, and we ultimately did not use it.
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class APrioriInversionConstraint extends InversionConstraint {
	
	private double weight;
	private double weightForZeroRates;
	private double[] aPrioriRates;

	public APrioriInversionConstraint(double weight, double weightForZeroRates,
			double[] aPrioriRates) {
		this.weight = weight;
		this.weightForZeroRates = weightForZeroRates;
		this.aPrioriRates = aPrioriRates;
	}

	@Override
	public String getShortName() {
		return "APriori";
	}

	@Override
	public String getName() {
		return "A Priori Rupture Rate";
	}

	@Override
	public int getNumRows() {
		int numNonZero = 0;
		for (double rate : aPrioriRates)
			if (rate > 0)
				numNonZero++;
		if (weightForZeroRates > 0)
			return numNonZero +1;
		return numNonZero;
	}

	@Override
	public boolean isInequality() {
		return false;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		int rowIndex = startRow;
		if (weight > 0d) {
			for(int rup=0; rup<aPrioriRates.length; rup++) {
				// only apply if rupture-rate is greater than 0, this will keep ruptures on
				// faults not in UCERF2 from being minimized
				if (aPrioriRates[rup]>0) {
					setA(A, rowIndex, rup, weight);
					d[rowIndex]=aPrioriRates[rup]*weight;
					numNonZeroElements++; rowIndex++;
				}
			}
		}
		if (weightForZeroRates > 0d) {
			// constrain sum of all these rupture rates to be zero (minimize - adding only one row to A matrix)
			for(int rup=0; rup<aPrioriRates.length; rup++) {
				if (aPrioriRates[rup]==0) { 
					setA(A, rowIndex, rup, weightForZeroRates);
					numNonZeroElements++; 
				}
			}	
			d[rowIndex]=0;
		}
		return numNonZeroElements;
	}

}
