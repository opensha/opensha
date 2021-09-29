package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.text.DecimalFormat;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * This constraint enforces a G-R MFD with the specified b-value, but without specifying an a-value. It does so by attempting
 * to force ruptures in each magnitude bin to be G-R relative to the ruptures in the previous bin
 * 
 * @author kevin
 *
 */
public class RelativeBValueConstraint extends InversionConstraint {
	
	private FaultSystemRupSet rupSet;
	private EvenlyDiscretizedFunc magFunc;
	private double weight;
	private double b;
	
	private static final DecimalFormat bDF = new DecimalFormat("0.#");
	
	public RelativeBValueConstraint(FaultSystemRupSet rupSet, double b) {
		this(rupSet, b, 1d);
	}
	
	public RelativeBValueConstraint(FaultSystemRupSet rupSet, double b, double weight) {
		this(rupSet, HistogramFunction.getEncompassingHistogram(rupSet.getMinMag(), rupSet.getMaxMag(), 0.1), b, weight);
	}
	
	public RelativeBValueConstraint(FaultSystemRupSet rupSet, EvenlyDiscretizedFunc magFunc, double b, double weight) {
		this.rupSet = rupSet;
		this.magFunc = magFunc;
		this.b = b;
		this.weight = weight;
	}

	@Override
	public String getShortName() {
		return "b="+bDF.format(b);
	}

	@Override
	public String getName() {
		return "B-value constrant (b="+bDF.format(b)+")";
	}

	@Override
	public int getNumRows() {
		return magFunc.size()-1;
	}

	@Override
	public boolean isInequality() {
		return false;
	}
	
	private static double gr(double a, double b, double M) {
		return Math.pow(10, a-b*M);
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long count = 0l;
		
		for (int m=1; m<magFunc.size(); m++) {
			int mBefore = m-1;
			
			System.out.println("B-Value constraint for M="+(float)magFunc.getX(m)
				+" relative to M="+(float)magFunc.getX(mBefore));
			
			// ruptures in this row should have a final rate equal to this times the rate of
			// ruptures in the prior bin (regardless of a-value)
			double relVal = gr(1, b, magFunc.getX(m))/gr(1, b, magFunc.getX(mBefore));
			System.out.println("\tTarget ratio: "+relVal);
			
			// determine a weight such that misfits from each bin should we weighted equally
			// (even though rates will be smaller for larger mags)
			double myWeight = gr(1, b, magFunc.getX(1))/gr(1, b, magFunc.getX(m));
			System.out.println("\tWeight: "+myWeight);
			myWeight *= this.weight;
			
			int row = startRow+m-1;
			
			double[] mags = rupSet.getMagForAllRups();
			for (int r=0; r<mags.length; r++) {
				double magIndex = magFunc.getClosestXIndex(mags[r]);
				if (magIndex == mBefore) {
					setA(A, row, r, -myWeight*relVal);
					count++;
				} else if (magIndex == m) {
					setA(A, row, r, myWeight);
					count++;
				}
			}
			d[row] = 0d;
		}
		
		return count;
	}

}
