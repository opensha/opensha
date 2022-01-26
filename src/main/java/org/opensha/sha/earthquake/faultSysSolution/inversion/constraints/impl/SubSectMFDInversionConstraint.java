package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * MFD Subsection nucleation constraint - constraints MFDs to conform to
 * an a priori section MFD. Similar to the UCERF3 implementation, but instead
 * takes in regular MFDs which should already be adjusted to deal with any empty bins.
 * 
 * @author Kevin Milner & Morgan Page
 *
 */
public class SubSectMFDInversionConstraint extends InversionConstraint {
	
	public static final String NAME = "Subsection Nucleation MFD";
	public static final String SHORT_NAME = "SectNuclMFD";
	
	private transient FaultSystemRupSet rupSet;
	private List<? extends IncrementalMagFreqDist> constraints;
	private boolean nucleation;

	public SubSectMFDInversionConstraint(FaultSystemRupSet rupSet, double weight,
			ConstraintWeightingType weightType, List<? extends IncrementalMagFreqDist> constraints, boolean nucleation) {
		super(weightType.applyNamePrefix("Subsection "+(nucleation?"Nucleation":"Participation")+" MFD"),
				weightType.applyShortNamePrefix("SubSect"+(nucleation?"Nucl":"Part")+"MFD"), weight, false, weightType);
		this.constraints = constraints;
		setRuptureSet(rupSet);
		this.nucleation = nucleation;
	}

	@Override
	public int getNumRows() {
		int numRows = 0;
		for (IncrementalMagFreqDist constraint : constraints)
			if (constraint != null) {
				EvenlyDiscretizedFunc stdDevs = weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY ?
						((UncertainIncrMagFreqDist)constraint).getStdDevs() : null;
				for (int i=0; i<constraint.size(); i++)
					if (constraint.getY(i) > 0 || (stdDevs != null && stdDevs.getY(i) > 0))
						numRows++;
			}
		return numRows;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		
		// Loop over all subsections
		int numSections = rupSet.getNumSections();
		int rowIndex = startRow;
		for (int sect=0; sect<numSections; sect++) {
			
			IncrementalMagFreqDist constraint = constraints.get(sect);
			if (constraint == null)
				continue;
			int numMagBins = constraint.size();
			List<Integer> rupturesForSect = rupSet.getRupturesForSection(sect);
			
			EvenlyDiscretizedFunc stdDevs = null;
			if (weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
				Preconditions.checkState(constraint instanceof UncertainIncrMagFreqDist,
						"Uncertainty-weighted subsection MFD constraint chosen, but sub section MFDs "
						+ "don't have uncertainties attached.");
				stdDevs = ((UncertainIncrMagFreqDist)constraint).getStdDevs();
			}
			
			// Loop over MFD constraints for this subsection
			for (int magBin=0; magBin<numMagBins; magBin++) {
				double rate = constraint.getY(magBin);
				double stdDev = stdDevs == null ? Double.NaN : stdDevs.getY(magBin);
				// Only include non-empty magBins in constraint
				if (rate > 0 || (stdDevs != null && stdDev > 0)) {
					double scalar = weightingType.getA_Scalar(rate, stdDev);
					
					// Determine which ruptures are in this magBin
					List<Integer> rupturesForMagBin = new ArrayList<Integer>();
					for (int i=0; i<rupturesForSect.size(); i++) {
						double mag = rupSet.getMagForRup(rupturesForSect.get(i));
						if (constraint.getClosestXIndex(mag) == magBin)
							rupturesForMagBin.add(rupturesForSect.get(i));
					}
					
					// Loop over ruptures in this subsection-MFD bin
					for (int i=0; i<rupturesForMagBin.size(); i++) {
						int rup  = rupturesForMagBin.get(i);
						double val = weight*scalar;
						if (nucleation) {
							double rupArea = rupSet.getAreaForRup(rup);
							double sectArea = rupSet.getAreaForSection(sect);
							val *= sectArea/rupArea;
						}
						setA(A, rowIndex, rup, val);
						numNonZeroElements++;	
					}
					d[rowIndex] = weight*weightingType.getD(rate, stdDev);
					rowIndex++;
				}
			}
		}
		return numNonZeroElements;
	}

	@Override
	public void setRuptureSet(FaultSystemRupSet rupSet) {
		Preconditions.checkState(rupSet.getNumSections() == constraints.size(),
				"Rupture set subsection count and constraint count are inconsistent");
		this.rupSet = rupSet;
	}

}
