package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * This allows you to strongly minimize the rate of certain ruptures. We used this
 * in UCERF3 to zero out rates for ruptures which were below the section minimum magnitude
 * 
 * TODO: this could easily be a single row constraint, should we change it to be?
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class RupRateMinimizationConstraint extends InversionConstraint {
	
	public static final String NAME = "Rupture Rate Minimization";
	public static final String SHORT_NAME = "RateMinimize";
	
	private List<Integer> rupIndexes;

	public RupRateMinimizationConstraint(double weight, List<Integer> rupIndexes) {
		super(NAME, SHORT_NAME, weight, false);
		this.rupIndexes = rupIndexes;
	}

	@Override
	public int getNumRows() {
		return rupIndexes.size();
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
