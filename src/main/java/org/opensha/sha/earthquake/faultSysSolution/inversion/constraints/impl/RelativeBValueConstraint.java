package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;

import com.google.common.base.Preconditions;

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
	
	private double[] mags;
	private int[] magIndexes;
	private int[] magCounts;
	private double globalMin;
	private double globalMax;
	
	private static final DecimalFormat bDF = new DecimalFormat("0.#");
	
	public RelativeBValueConstraint(FaultSystemRupSet rupSet, double b) {
		this(rupSet, b, 1d);
	}
	
	public RelativeBValueConstraint(FaultSystemRupSet rupSet, double b, double weight) {
		this(rupSet, getMagFunc(rupSet), b, weight);
	}
	
	private static EvenlyDiscretizedFunc getMagFunc(FaultSystemRupSet rupSet) {
		double maxMag = rupSet.getMaxMag();
		double minMag;
		if (rupSet.hasModule(ModSectMinMags.class))
			minMag = StatUtils.min(rupSet.getModule(ModSectMinMags.class).getMinMagForSections());
		else
			minMag = rupSet.getMinMag();
		Preconditions.checkState(maxMag > minMag+0.2,
				"Minimum and maximum magnitude are too close for b-value constraint: %s %s", minMag, maxMag);
		minMag = Math.floor(minMag*10)/10d + 0.05;
		maxMag = Math.ceil(maxMag*10)/10d - 0.05;
		int num = (int)((maxMag-minMag)/0.1)+2;
		return new EvenlyDiscretizedFunc(minMag, maxMag, num);
	}
	
	public RelativeBValueConstraint(FaultSystemRupSet rupSet, EvenlyDiscretizedFunc magFunc, double b, double weight) {
		this.rupSet = rupSet;
		this.magFunc = magFunc;
		Preconditions.checkState(magFunc.size() > 1, "Must have at least 2 MFD bins to constraint relative b-value");
		this.b = b;
		this.weight = weight;
		
		mags = rupSet.getMagForAllRups();
		magIndexes = new int[mags.length];
		globalMin = magFunc.getMinX() - 0.5*magFunc.getDelta();
		globalMax = magFunc.getMaxX() + 0.5*magFunc.getDelta();
		magCounts = new int[magFunc.size()];
		for (int r=0; r<mags.length; r++) {
			if ((float)mags[r] < (float)globalMin  || (float)mags[r] > (float)globalMax) {
				magIndexes[r] = -1;
			} else {
				magIndexes[r] = magFunc.getClosestXIndex(mags[r]);
				magCounts[magIndexes[r]]++;
			}
		}
	}

	@Override
	public String getShortName() {
		return "G-R,b="+bDF.format(b);
	}

	@Override
	public String getName() {
		return "G-R b-value constrant (b="+bDF.format(b)+")";
	}

	@Override
	public int getNumRows() {
		int rows = 0;
		for (int m1=0; m1<magFunc.size(); m1++)
			for (int m2=m1+1; m2<magFunc.size(); m2++)
				if (magCounts[m1] > 0 && magCounts[m2] > 0)
					rows++;
		return rows;
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
		
		double refVal = gr(1, b, magFunc.getX(0));
		
		int row = startRow;
		
		// weigh it such that each bin has the given weight, not each bin-to-bin comparison row
		double weight = this.weight / magFunc.size();
		
		for (int m1=0; m1<magFunc.size(); m1++) {
			if (magCounts[m1] == 0) {
				System.out.println("Skipping M1="+(float)magFunc.getX(m1)+" (no ruptures)");
				continue;
			}
			double val1 = gr(1, b, magFunc.getX(m1));
			
			System.out.println("Building b-value constraints for ruptures with M1="+(float)magFunc.getX(m1));
			
			// determine a weight such that misfits from each bin should we weighted equally
			// (even though rates will be smaller for larger mags)
			double myWeight = refVal/val1;
			System.out.println("\tWeight: "+myWeight);
			myWeight *= weight;
			
			for (int m2=m1+1; m2<magFunc.size(); m2++) {
				if (magCounts[m2] == 0) {
					System.out.println("\tSkipping M2="+(float)magFunc.getX(m2)+" (no ruptures)");
					continue;
				}
				double val2 = gr(1, b, magFunc.getX(m2));
				
				// ruptures in this row should have a final rate equal to this times the rate of
				// ruptures in the comparison bin (regardless of a-value)
				double relVal = val1/val2;
				System.out.println("\tTarget ratio relative to M2="+(float)magFunc.getX(m2)+": "+relVal);
				
				for (int r=0; r<mags.length; r++) {
					int magIndex = magIndexes[r];
					if (magIndex < 0)
						continue;
					
					if (magIndex == m2) {
						setA(A, row, r, -myWeight*relVal);
						count++;
					} else if (magIndex == m1) {
						setA(A, row, r, myWeight);
						count++;
					}
				}
				d[row] = 0d;
				
				row++;
			}
		}
		
//		for (int m=1; m<magFunc.size(); m++) {
//			int mBefore = m-1;
//			
//			System.out.println("B-Value constraint for M="+(float)magFunc.getX(m)
//				+" relative to M="+(float)magFunc.getX(mBefore));
//			
//			// ruptures in this row should have a final rate equal to this times the rate of
//			// ruptures in the prior bin (regardless of a-value)
//			double relVal = gr(1, b, magFunc.getX(m))/gr(1, b, magFunc.getX(mBefore));
//			System.out.println("\tTarget ratio: "+relVal);
//			
//			// determine a weight such that misfits from each bin should we weighted equally
//			// (even though rates will be smaller for larger mags)
//			double myWeight = gr(1, b, magFunc.getX(1))/gr(1, b, magFunc.getX(m));
//			System.out.println("\tWeight: "+myWeight);
//			myWeight *= this.weight;
//			
//			int row = startRow+m-1;
//			
//			double[] mags = rupSet.getMagForAllRups();
//			for (int r=0; r<mags.length; r++) {
//				double magIndex = magFunc.getClosestXIndex(mags[r]);
//				if (magIndex == mBefore) {
//					setA(A, row, r, -myWeight*relVal);
//					count++;
//				} else if (magIndex == m) {
//					setA(A, row, r, myWeight);
//					count++;
//				}
//			}
//			d[row] = 0d;
//		}
		
		return count;
	}
	
	public static void main(String[] args) throws IOException {
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File("/data/kevin/markdown/inversions/u3_coulomb_rup_set.zip"));
		RelativeBValueConstraint constr = new RelativeBValueConstraint(rupSet, 1d);
		System.out.println(constr.magFunc);
		System.out.println("Func has "+constr.magFunc.size()+" bins");
		System.out.println("Rows: "+constr.getNumRows());
	}

}
