package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import org.opensha.commons.eq.MagUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * Constraint solution total rate.
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class TotalRateInversionConstraint extends InversionConstraint {
	
	private FaultSystemRupSet rupSet;
	private double weight;
	private double totalRateTarget;

	public TotalRateInversionConstraint(FaultSystemRupSet rupSet, double weight,
			double totalRateTarget) {
		this.rupSet = rupSet;
		this.weight = weight;
		this.totalRateTarget = totalRateTarget;
	}

	@Override
	public String getShortName() {
		return "TotRate";
	}

	@Override
	public String getName() {
		return "Total Rate";
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
		int numRuptures = rupSet.getNumRuptures();
		for (int rup=0; rup<numRuptures; rup++)  {
			setA(A, startRow, rup, weight);
			numNonZeroElements++;
		}
		d[startRow] = weight * totalRateTarget;
		return numNonZeroElements;
	}

}
