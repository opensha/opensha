package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.collect.Lists;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoProbabilityModel;

/**
 * This constrains paleoseismically-visible event rates along parent sections to be smooth.
 * 
 * It was not ultimately used in UCERF3, and upon further review and implementation in the new
 * framework by Kevin, it had a problem at that time where A values were overridden (see note in
 * the encode method below). That problem has now been corrected, so it won't match the prior
 * (unused) UCERF3 implementation.
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class PaleoVisibleEventRateSmoothnessInversionConstraint extends InversionConstraint {
	
	private FaultSystemRupSet rupSet;
	private double weight;
	private PaleoProbabilityModel paleoProbabilityModel;

	public PaleoVisibleEventRateSmoothnessInversionConstraint(FaultSystemRupSet rupSet, double weight,
			PaleoProbabilityModel paleoProbabilityModel) {
		this.rupSet = rupSet;
		this.weight = weight;
		this.paleoProbabilityModel = paleoProbabilityModel;
	}

	@Override
	public String getShortName() {
		return "PaleoRateSmooth";
	}

	@Override
	public String getName() {
		return "Paleo-Visible Event Rate Smoothness";
	}

	@Override
	public int getNumRows() {
		HashSet<Integer> parentIDs = new HashSet<Integer>();
		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
			int parentID = sect.getParentSectionId();
			parentIDs.add(parentID);
		}
		int numParentSections=parentIDs.size();
		// one constraint for each section, except minus 1 section on each parent
		return rupSet.getNumSections()-numParentSections;
	}

	@Override
	public boolean isInequality() {
		return false;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		
		// Get list of parent IDs
		HashSet<Integer> prevParents = new HashSet<>();
		List<Integer> parentIDs = new ArrayList<Integer>();
		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
			int parentID = sect.getParentSectionId();
			if (!prevParents.contains(parentID)) {
				parentIDs.add(parentID);
				prevParents.add(parentID);
			}
		}
		
		int rowIndex = startRow;

		for (int parentID: parentIDs) {
			// Find subsection IDs for given parent section
			List<Integer> sectsForParent = new ArrayList<Integer>();
			for (FaultSection sect : rupSet.getFaultSectionDataList()) {
				int sectParentID = sect.getParentSectionId();
				if (sectParentID == parentID)
					sectsForParent.add(sect.getSectionId());
			}
			
			// Constrain the event rate of each neighboring subsection pair (with same parent section) to be approximately equal
			for (int j=0; j<sectsForParent.size()-1; j++) {
				int sect1 = sectsForParent.get(j);
				int sect2 = sectsForParent.get(j+1);
				List<Integer> sect1Rups = rupSet.getRupturesForSection(sect1);
				List<Integer> sect2Rups = rupSet.getRupturesForSection(sect2);
				
				for (int rup: sect1Rups) { 
					double probPaleoVisible = paleoProbabilityModel.getProbPaleoVisible(rupSet, rup, sect1);
					if (probPaleoVisible > 0d) {
						setA(A, rowIndex, rup, probPaleoVisible*weight);
						numNonZeroElements++;
					}
				}
				for (int rup: sect2Rups) {
					double probPaleoVisible = paleoProbabilityModel.getProbPaleoVisible(rupSet, rup, sect2);
					if (probPaleoVisible > 0d) {
						// NOTE: when we tested this constraint with UCERF3, we would just set the value to
						// -probPaleoVisibile*weight which would clobber the prior value if this rupture also
						// includes sect1. This is now corrected to subtract probPaleoVisible*weight.
						double prevVal = getA(A, rowIndex, rup);
						double newVal = prevVal - probPaleoVisible*weight;
						if (newVal == 0d)
							numNonZeroElements--;
						else if (prevVal == 0d)
							numNonZeroElements++;
						setA(A, rowIndex, rup, newVal);
					}
				}
				d[rowIndex] = 0;
				rowIndex++;
			}
		}
		return numNonZeroElements;
	}

}
