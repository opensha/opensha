package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * Constrains the summed rate across a set of ruptures to equal a given total rate.
 * We used this in UCERF3 to constrain Parkfield M6's to have a 25 year recurrence interval,
 * but it could be applied elsewhere as well.
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class ParkfieldUncertaintyWeightedInversionConstraint extends InversionConstraint {
	
	public static final String NAME = "Uncertainty-Weighted Parkfield";
	public static final String SHORT_NAME = "UncertParkfield";
	
	private double targetRate;
	private List<Integer> parkfieldRups;
	private double rateStdDev;

	public ParkfieldUncertaintyWeightedInversionConstraint(double weight, double targetRate, double rateStdDev,
			List<Integer> parkfieldRups) {
		super(NAME, SHORT_NAME, weight, false);
		this.targetRate = targetRate;
		this.rateStdDev = rateStdDev;
		this.parkfieldRups = parkfieldRups;
	}

	@Override
	public int getNumRows() {
		return 1;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		for (int r=0; r<parkfieldRups.size(); r++)  {
			int rup = parkfieldRups.get(r);
			setA(A, startRow, rup, weight / rateStdDev);
			numNonZeroElements++;
		}
		d[startRow] = weight * targetRate / rateStdDev;
		return numNonZeroElements;
	}

}
