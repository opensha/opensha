package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.utils.MFD_InversionConstraint;

/**
 * Constraints the solution to match the given MFD constraints, which can be region specific.
 * 
 * In UCERF3, we used an equality constraint up to large magnitudes, where we transitioned to
 * an inequality constraint, and used separate constraints for northern and southern CA. If inequality
 * is set to true, then it will be constrained not to exceed the given MFD(s).
 * 
 * TODO: just use MFDs, not the useless wrapper class
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class MFDInversionConstraint extends InversionConstraint {
	
	private transient FaultSystemRupSet rupSet;
	private List<? extends MFD_InversionConstraint> mfds;
	private List<? extends EvenlyDiscretizedFunc> mfdStdDevs;
	private HashSet<Integer> excludeRupIndexes;
	
	/**
	 * This computes standard deviations for the given MFDs as a fraction of their rate, where that fraction is
	 * function of their magnitude, passed in through a functional operator.
	 * @param mfds
	 * @param relStdDevFunc
	 * @return absolute standard deviations scaled to MFD y values, set according to the given function)
	 */
	public static List<EvenlyDiscretizedFunc> calcStdDevsFromRelativeFunc(List<? extends MFD_InversionConstraint> mfds,
			DoubleUnaryOperator relStdDevFunc) {
		List<EvenlyDiscretizedFunc> stdDevs = new ArrayList<>();
		for (MFD_InversionConstraint constr : mfds) {
			IncrementalMagFreqDist mfd = constr.getMagFreqDist();
			EvenlyDiscretizedFunc stdDev = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
			// now relative values
			stdDev.setYofX(relStdDevFunc);
			for (int i=0; i<mfd.size(); i++) {
				// scale by the rate for the actual std dev
				double rate = mfd.getY(i);
				double relSD = stdDev.getY(i);
				double sd = relSD*rate;
				stdDev.set(i, sd);
				System.out.println("M="+(float)mfd.getX(i)+"\trate="+(float)rate+"\trelSD="+(float)relSD+"\tsd="+(float)sd);
			}
			stdDevs.add(stdDev);
		}
		return stdDevs;
	}
	
	/**
	 * This computes absolute standard deviations for the given MFDs as a function of their magnitude and rate, passed
	 * in through a functional operator.
	 * @param mfds
	 * @param relStdDevFunc
	 * @return absolute standard deviations, set according to the given function
	 */
	public static List<EvenlyDiscretizedFunc> calcStdDevsFromRateFunc(List<? extends MFD_InversionConstraint> mfds,
			DoubleBinaryOperator absStdDevFunc) {
		List<EvenlyDiscretizedFunc> stdDevs = new ArrayList<>();
		for (MFD_InversionConstraint constr : mfds) {
			IncrementalMagFreqDist mfd = constr.getMagFreqDist();
			EvenlyDiscretizedFunc stdDev = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
			// set std dev from given function of mag and/or rate
			stdDev.setYofX(absStdDevFunc);
			stdDevs.add(stdDev);
		}
		return stdDevs;
	}
	
	public MFDInversionConstraint(FaultSystemRupSet rupSet, double weight, boolean inequality,
			List<? extends MFD_InversionConstraint> mfds) {
		this(rupSet, weight, inequality, ConstraintWeightingType.NORMALIZED, mfds, null, null);
	}

	public MFDInversionConstraint(FaultSystemRupSet rupSet, double weight, boolean inequality,
			ConstraintWeightingType weightingType, List<? extends MFD_InversionConstraint> mfds,
			List<? extends EvenlyDiscretizedFunc> mfdStdDevs) {
		this(rupSet, weight, inequality, weightingType, mfds, mfdStdDevs, null);
	}

	public MFDInversionConstraint(FaultSystemRupSet rupSet, double weight, boolean inequality,
			ConstraintWeightingType weightingType, List<? extends MFD_InversionConstraint> mfds,
			List<? extends EvenlyDiscretizedFunc> mfdStdDevs, HashSet<Integer> excludeRupIndexes) {
		super(weightingType.applyNamePrefix("MFD "+(inequality ? "Inequality" : "Equality")),
				weightingType.applyShortNamePrefix("MFD"+(inequality ? "Inequality" : "Equality")),
				weight, inequality, weightingType);
		this.rupSet = rupSet;
		this.mfds = mfds;
		if (weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY)
			Preconditions.checkNotNull(mfdStdDevs,
					"MFD Standard Deviation functions must be supplied for uncertainty weighting");
		if (mfdStdDevs != null) {
			Preconditions.checkArgument(mfdStdDevs.size() == mfds.size(),
					"Supplied different count of MFDs and Standard Deviations");
			for (int i=0; i<mfds.size(); i++) {
				IncrementalMagFreqDist mfd = mfds.get(i).getMagFreqDist();
				Preconditions.checkState(mfd.size() == mfdStdDevs.get(i).size(),
						"MFDs and std. dev. size mismatch");
				Preconditions.checkState((float)mfd.getMinX() == (float)mfdStdDevs.get(i).getMinX(),
						"MFDs and std. dev. x mismatch");
				Preconditions.checkState((float)mfd.getDelta() == (float)mfdStdDevs.get(i).getDelta(),
						"MFDs and std. dev. x mismatch");
			}
		}
		this.mfdStdDevs = mfdStdDevs;
		this.excludeRupIndexes = excludeRupIndexes;
	}
	
	/**
	 * Calculates the number of rows needed for MFD constraints, ignoring any bins that are above or below the
	 * magnitude range of the rupture set.
	 * 
	 * @param constraints
	 * @param rupSet
	 * @return number of rows needed
	 */
	static int getNumRows(List<? extends MFD_InversionConstraint> mfds, FaultSystemRupSet rupSet) {
		int totalNumMagFreqConstraints = 0;
		for (MFD_InversionConstraint constr : mfds) {
			IncrementalMagFreqDist mfd = constr.getMagFreqDist();
			// Find number of rows used for MFD equality constraint
			// only include mag bins between minimum and maximum magnitudes in rupture set
			totalNumMagFreqConstraints += mfd.getClosestXIndex(rupSet.getMaxMag())
					- mfd.getClosestXIndex(rupSet.getMinMag()) + 1;
		}
		return totalNumMagFreqConstraints;
	}

	@Override
	public int getNumRows() {
		return getNumRows(mfds, rupSet);
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		// Loop over all MFD constraints in different regions
		int numRuptures = rupSet.getNumRuptures();
		for (int i=0; i < mfds.size(); i++) {
			MFD_InversionConstraint constr = mfds.get(i);
			Region region = constr.getRegion();
			IncrementalMagFreqDist mfd = constr.getMagFreqDist();
//			Region region = mfd.getRegion(); // TODO Switch to useing MFD region, abandon wrapper
			double[] fractRupsInside = rupSet.getFractRupsInsideRegion(region, false);
			int minMagIndex = mfd.getClosestXIndex(rupSet.getMinMag());
			int maxMagIndex = mfd.getClosestXIndex(rupSet.getMaxMag());
			
			double[] targets = new double[mfd.size()];
			double[] scales = new double[targets.length];
			
			for (int j=0; j<targets.length; j++) {
				double rate = mfd.getY(j);
				if (rate == 0d) {
					targets[j] = 0d;
					scales[j] = 0d;
				} else {
					double stdDev = mfdStdDevs == null ? 0d : mfdStdDevs.get(i).getY(j);
					scales[j] = weightingType.getA_Scalar(rate, stdDev);
					targets[j] = weightingType.getD(rate, stdDev);
				}
			}
			
			for(int rup=0; rup<numRuptures; rup++) {
				double mag = rupSet.getMagForRup(rup);
				double fractRupInside = fractRupsInside[rup];
				if (fractRupInside > 0 && mag>mfd.getMinX()-mfd.getDelta()/2.0 && mag<mfd.getMaxX()+mfd.getDelta()/2.0) {
					if (excludeRupIndexes != null && excludeRupIndexes.contains(rup))
						continue;
					int magIndex = mfd.getClosestXIndex(mag);
					Preconditions.checkState(magIndex >= minMagIndex && magIndex <= maxMagIndex);
					int rowIndex = startRow + magIndex - minMagIndex;
					
					if (targets[magIndex] == 0d) {
						setA(A, rowIndex, rup, 0d);
					} else {
						setA(A, rowIndex, rup, weight * fractRupInside * scales[magIndex]);
						numNonZeroElements++;
					}
				}
			}
			for (int magIndex=minMagIndex; magIndex<=maxMagIndex; magIndex++) {
				int rowIndex = startRow + magIndex - minMagIndex;
				d[rowIndex] = weight*targets[magIndex];
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
