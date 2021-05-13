package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.utils.SectionMFD_constraint;

/**
 * MFD Subsection nucleation MFD constraint - constraints MFDs to conform to
 * an a priori section MFD. In UCERF3, we weakly constrained section MFDs to match
 * UCERF2.
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class MFDSubSectNuclInversionConstraint extends InversionConstraint {
	
	public static final String NAME = "MFD Subsection Nucleation";
	public static final String SHORT_NAME = "MFDSubSectNucl";
	
	private FaultSystemRupSet rupSet;
	private double weight;
	private List<SectionMFD_constraint> constraints;

	public MFDSubSectNuclInversionConstraint(FaultSystemRupSet rupSet, double weight,
			List<SectionMFD_constraint> constraints) {
		this.rupSet = rupSet;
		this.weight = weight;
		this.constraints = constraints;
	}

	@Override
	public String getShortName() {
		return "MFDSubSectNucl";
	}

	@Override
	public String getName() {
		return "MFD Subsection Nucleation";
	}

	@Override
	public int getNumRows() {
		int numRows = 0;
		for (SectionMFD_constraint constraint : constraints)
			if (constraint != null)
				for (int i=0; i<constraint.getNumMags(); i++)
					if (constraint.getRate(i) > 0)
						numRows++;
		return numRows;
	}

	@Override
	public boolean isInequality() {
		return false;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		
		// Loop over all subsections
		int numSections = rupSet.getNumSections();
		int rowIndex = startRow;
		for (int sect=0; sect<numSections; sect++) {
			
			SectionMFD_constraint sectMFDConstraint = constraints.get(sect);
			if (sectMFDConstraint == null) continue; // Parent sections with Mmax<6 have no MFD constraint; skip these
			int numMagBins = sectMFDConstraint.getNumMags();
			List<Integer> rupturesForSect = rupSet.getRupturesForSection(sect);
			
			// Loop over MFD constraints for this subsection
			for (int magBin = 0; magBin<numMagBins; magBin++) {
				
				// Only include non-empty magBins in constraint
				if (sectMFDConstraint.getRate(magBin) > 0) {
					// Determine which ruptures are in this magBin
					List<Integer> rupturesForMagBin = new ArrayList<Integer>();
					for (int i=0; i<rupturesForSect.size(); i++) {
						double mag = rupSet.getMagForRup(rupturesForSect.get(i));
						if (sectMFDConstraint.isMagInBin(mag, magBin))
							rupturesForMagBin.add(rupturesForSect.get(i));
					}
					
					// Loop over ruptures in this subsection-MFD bin
					for (int i=0; i<rupturesForMagBin.size(); i++) {
						int rup  = rupturesForMagBin.get(i);
						double rupArea = rupSet.getAreaForRup(rup);
						double sectArea = rupSet.getAreaForSection(sect);
						setA(A, rowIndex, rup, weight * (sectArea / rupArea) / sectMFDConstraint.getRate(magBin));
						numNonZeroElements++;	
					}
					d[rowIndex] = weight;
					rowIndex++;
				}
			}
		}
		return numNonZeroElements;
	}

}
