package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.HashSet;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.utils.MFD_InversionConstraint;

/**
 * Constraints the solution to match the given MFD constraints, which can be region specific.
 * 
 * In UCERF3, we used an equality constraint up to large magnitudes, where we transitioned to
 * an inequality constraint, and used separate constraints for northern and southern CA.
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class MFDEqualityInversionConstraint extends InversionConstraint {
	
	public static final String NAME = "MFD Equality";
	public static final String SHORT_NAME = "MFDEquality";
	
	private FaultSystemRupSet rupSet;
	private double weight;
	private List<MFD_InversionConstraint> mfdEqualityConstraints;
	private HashSet<Integer> excludeRupIndexes;

	public MFDEqualityInversionConstraint(FaultSystemRupSet rupSet, double weight,
			List<MFD_InversionConstraint> mfdEqualityConstraints, HashSet<Integer> excludeRupIndexes) {
		this.rupSet = rupSet;
		this.weight = weight;
		this.mfdEqualityConstraints = mfdEqualityConstraints;
		this.excludeRupIndexes = excludeRupIndexes;
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
		int totalNumMagFreqConstraints = 0;
		for (MFD_InversionConstraint constr : mfdEqualityConstraints) {
			IncrementalMagFreqDist targetMagFreqDist = constr.getMagFreqDist();
			// Find number of rows used for MFD equality constraint
			// only include mag bins between minimum and maximum magnitudes in rupture set
			totalNumMagFreqConstraints += targetMagFreqDist.getClosestXIndex(rupSet.getMaxMag())
					- targetMagFreqDist.getClosestXIndex(rupSet.getMinMag()) + 1;
		}
		return totalNumMagFreqConstraints;
	}

	@Override
	public boolean isInequality() {
		return false;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		// Loop over all MFD constraints in different regions
		int numRuptures = rupSet.getNumRuptures();
		for (int i=0; i < mfdEqualityConstraints.size(); i++) {
			double[] fractRupsInside = rupSet.getFractRupsInsideRegion(mfdEqualityConstraints.get(i).getRegion(), false);
			IncrementalMagFreqDist targetMagFreqDist = mfdEqualityConstraints.get(i).getMagFreqDist();
			int minMagIndex = targetMagFreqDist.getClosestXIndex(rupSet.getMinMag());
			int maxMagIndex = targetMagFreqDist.getClosestXIndex(rupSet.getMaxMag());
			for(int rup=0; rup<numRuptures; rup++) {
				double mag = rupSet.getMagForRup(rup);
				double fractRupInside = fractRupsInside[rup];
				if (fractRupInside > 0 && mag>targetMagFreqDist.getMinX()-targetMagFreqDist.getDelta()/2.0 && mag<targetMagFreqDist.getMaxX()+targetMagFreqDist.getDelta()/2.0) {
					if (excludeRupIndexes != null && excludeRupIndexes.contains(rup))
						continue;
					int magIndex = targetMagFreqDist.getClosestXIndex(mag);
					Preconditions.checkState(magIndex >= minMagIndex && magIndex <= maxMagIndex);
					int rowIndex = startRow + magIndex - minMagIndex;
					if (targetMagFreqDist.getClosestYtoX(mag) == 0) {
						setA(A, rowIndex, rup, 0d);
					} else {
						setA(A, rowIndex, rup, weight * fractRupInside / targetMagFreqDist.getClosestYtoX(mag));
						numNonZeroElements++;
					}
				}
			}
			for (int magIndex=minMagIndex; magIndex<=maxMagIndex; magIndex++) {
				int rowIndex = startRow + magIndex - minMagIndex;
				if (targetMagFreqDist.getY(magIndex)==0)
					d[rowIndex]=0;
				else
					d[rowIndex] = weight;
			}
			// move startRow to point after this constraint
			startRow += (maxMagIndex - minMagIndex) + 1;
		}
		return numNonZeroElements;
	}

}
