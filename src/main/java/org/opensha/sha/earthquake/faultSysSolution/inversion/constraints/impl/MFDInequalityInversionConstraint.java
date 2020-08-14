package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.utils.MFD_InversionConstraint;

/**
 * Constraints the solution to not exceed the given MFD constraints, which can be region specific.
 * 
 * In UCERF3, we used an equality constraint up to large magnitudes, where we transitioned to
 * an inequality constraint, and used separate constraints for northern and southern CA.
 * 
 * @author Morgan Page & Kevin Milner
 * 
 */
public class MFDInequalityInversionConstraint extends InversionConstraint {
	
	private FaultSystemRupSet rupSet;
	private double weight;
	private List<MFD_InversionConstraint> mfdInequalityConstraints;

	public MFDInequalityInversionConstraint(FaultSystemRupSet rupSet, double weight,
			List<MFD_InversionConstraint> mfdInequalityConstraints) {
		this.rupSet = rupSet;
		this.weight = weight;
		this.mfdInequalityConstraints = mfdInequalityConstraints;
	}

	@Override
	public String getShortName() {
		return "MFDInquality";
	}

	@Override
	public String getName() {
		return "MFD Inequality";
	}

	@Override
	public int getNumRows() {
		int numMFDRows = 0;
		for (MFD_InversionConstraint constr : mfdInequalityConstraints) {
			IncrementalMagFreqDist targetMagFreqDist = constr.getMagFreqDist();
			// Add number of rows used for magnitude distribution constraint - only include mag bins between minimum and maximum magnitudes in rupture set				
			numMFDRows += targetMagFreqDist.getClosestXIndex(rupSet.getMaxMag())
					- targetMagFreqDist.getClosestXIndex(rupSet.getMinMag()) + 1;
		}
		return numMFDRows;
	}

	@Override
	public boolean isInequality() {
		return true;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		int numRuptures = rupSet.getNumRuptures();
		// Loop over all MFD constraints in different regions
		for (int i=0; i < mfdInequalityConstraints.size(); i++) {
			double[] fractRupsInside = rupSet.getFractRupsInsideRegion(mfdInequalityConstraints.get(i).getRegion(), false);
			IncrementalMagFreqDist targetMagFreqDist = mfdInequalityConstraints.get(i).getMagFreqDist();
			double minMag = targetMagFreqDist.getMinX()-targetMagFreqDist.getDelta()/2.0;
			double maxMag = targetMagFreqDist.getMaxX()+targetMagFreqDist.getDelta()/2.0;
			int minMagIndex = targetMagFreqDist.getClosestXIndex(rupSet.getMinMag());
			int maxMagIndex = targetMagFreqDist.getClosestXIndex(rupSet.getMaxMag());
			for(int rup=0; rup<numRuptures; rup++) {
				double mag = rupSet.getMagForRup(rup);
				double fractRupInside = fractRupsInside[rup];
				if (fractRupInside > 0 && mag>=minMag && mag<=maxMag) {
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
