package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.HashSet;
import java.util.List;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.UCERF3.utils.MFD_WeightedInversionConstraint;

/**
 * Constrain the solution to match the given MFD Weighted constraints.
 * 
 * Kevin: Renamed to NZ_... to avoid confusion, as it's only sort of uncertainty weighted and should probably be removed
 * in favor of choosing standard deviations and then using the regular version.
 * 
 * @author chrisbc
 *
 */
@Deprecated
public class NZ_MFDUncertaintyWeightedInversionConstraint extends InversionConstraint {
	
	public static final String NAME = "Uncertainty-Weighted MFD";
	public static final String SHORT_NAME = "UncertMFD";
	
	private transient FaultSystemRupSet rupSet;
	private List<MFD_WeightedInversionConstraint> mfdWeightedConstraints;
	private HashSet<Integer> excludeRupIndexes;

	public NZ_MFDUncertaintyWeightedInversionConstraint(FaultSystemRupSet rupSet, double weight,
			List<MFD_WeightedInversionConstraint> mfdWeightedConstraints) {
		super(NAME, SHORT_NAME, weight, false, null); // somewhere inbetween normalized by slip rates and normalized by uncertainty
		this.rupSet = rupSet;
		this.mfdWeightedConstraints = mfdWeightedConstraints;
	}

	@Override
	public int getNumRows() {
		int totalNumMagFreqConstraints = 0;
		for (MFD_InversionConstraint constr : mfdWeightedConstraints) {
			IncrementalMagFreqDist mfd = constr.getMagFreqDist();
			// Find number of rows used for MFD equality constraint
			// only include mag bins between minimum and maximum magnitudes in rupture set
			totalNumMagFreqConstraints += mfd.getClosestXIndex(rupSet.getMaxMag())
					- mfd.getClosestXIndex(rupSet.getMinMag()) + 1;
		}
		return totalNumMagFreqConstraints;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		// Loop over all MFD constraints in different regions
		int numRuptures = rupSet.getNumRuptures();
		for (int i=0; i < mfdWeightedConstraints.size(); i++) {
			double[] fractRupsInside = rupSet.getFractRupsInsideRegion(mfdWeightedConstraints.get(i).getRegion(), false);
			IncrementalMagFreqDist targetMagFreqDist = mfdWeightedConstraints.get(i).getMagFreqDist();
			EvenlyDiscretizedFunc targetUncertaintyWeight = mfdWeightedConstraints.get(i).getWeights();
			
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
						// apply uncertainty adjusted weight to the A matrix
						setA(A, rowIndex, rup, weight * targetUncertaintyWeight.getClosestYtoX(mag) * fractRupInside / targetMagFreqDist.getClosestYtoX(mag));
						numNonZeroElements++;
					}
				}
			}
			for (int magIndex=minMagIndex; magIndex<=maxMagIndex; magIndex++) {
				int rowIndex = startRow + magIndex - minMagIndex;
				if (targetMagFreqDist.getY(magIndex)==0)
					d[rowIndex]=0;
				else
					// apply uncertainty adjusted weight to the d matrix	
					d[rowIndex] = weight * targetUncertaintyWeight.getY(magIndex); 
			}
			// move startRow to point after this constraint
			startRow += (maxMagIndex - minMagIndex) + 1;
		}
		return numNonZeroElements;
	}

	@Override
	public void setRuptureSet(FaultSystemRupSet rupSet) {
		this.rupSet = rupSet;
	}

}
