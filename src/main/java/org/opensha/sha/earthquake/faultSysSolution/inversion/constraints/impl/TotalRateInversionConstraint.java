package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * Constrain solution total rate.
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class TotalRateInversionConstraint extends InversionConstraint {
	
	public static final String NAME = "Total Rate";
	public static final String SHORT_NAME = "TotRate";
	
	private double totalRateTarget;

	public TotalRateInversionConstraint(double weight, double totalRateTarget) {
		super(NAME, SHORT_NAME, weight, false);
		this.totalRateTarget = totalRateTarget;
	}

	@Override
	public int getNumRows() {
		return 1;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		int numRuptures = A.columns();
		for (int rup=0; rup<numRuptures; rup++)  {
			setA(A, startRow, rup, weight);
			numNonZeroElements++;
		}
		d[startRow] = weight * totalRateTarget;
		return numNonZeroElements;
	}

}
