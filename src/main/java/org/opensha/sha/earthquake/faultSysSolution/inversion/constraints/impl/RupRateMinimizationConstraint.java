package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

public class RupRateMinimizationConstraint extends InversionConstraint {
	
	private double weight;
	private List<Integer> rupIndexes;

	public RupRateMinimizationConstraint(double weight, List<Integer> rupIndexes) {
		this.weight = weight;
		this.rupIndexes = rupIndexes;
	}

	@Override
	public String getShortName() {
		return "RateMinimize";
	}

	@Override
	public String getName() {
		return "Rupture Rate Minimization";
	}

	@Override
	public int getNumRows() {
		return rupIndexes.size();
	}

	@Override
	public boolean isInequality() {
		return false;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		int rowIndex = startRow;
		for (int rupIndex : rupIndexes) {
			setA(A, rowIndex, rupIndex, weight);
			d[rowIndex] = 0;
			numNonZeroElements++;
			rowIndex++;
		}
		return numNonZeroElements;
	}

}
