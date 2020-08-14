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
public class ParkfieldInversionConstraint extends InversionConstraint {
	
	public static final String NAME = "Parkfield";
	public static final String SHORT_NAME = "Parkfield";
	
	private double weight;
	private double targetRate;
	private List<Integer> parkfieldRups;

	public ParkfieldInversionConstraint(double weight, double targetRate,
			List<Integer> parkfieldRups) {
		this.weight = weight;
		this.targetRate = targetRate;
		this.parkfieldRups = parkfieldRups;
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
		return 1;
	}

	@Override
	public boolean isInequality() {
		return false;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		for (int r=0; r<parkfieldRups.size(); r++)  {
			int rup = parkfieldRups.get(r);
			setA(A, startRow, rup, weight);
			numNonZeroElements++;
		}
		d[startRow] = weight * targetRate;
		return numNonZeroElements;
	}

}
