package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.faultSurface.FaultSection;

import cern.colt.matrix.tdouble.DoubleMatrix2D;

/**
 * Spatially smoothes supra-seismogenic participation rates along adjacent subsections on a parent section
 * (Laplacian smoothing).
 * <br>
 * UCERF3 used a more complex version ({@link MFDLaplacianSmoothingInversionConstraint}) that smoothed within MFD bins
 * rather than just total supra-seismogenic rates 
 * 
 * @author Kevin Milner and Morgan Page
 *
 */
public class LaplacianSmoothingInversionConstraint extends InversionConstraint {
	
	public static final String NAME = "Laplacian Smoothing";
	public static final String SHORT_NAME = "LaplaceSmooth";
	
	private transient FaultSystemRupSet rupSet;
	private HashSet<Integer> parentIDs;

	public LaplacianSmoothingInversionConstraint(FaultSystemRupSet rupSet, double weight) {
		this(rupSet, weight, null);
	}

	public LaplacianSmoothingInversionConstraint(FaultSystemRupSet rupSet, double weight, HashSet<Integer> parentIDs) {
		super(NAME+(parentIDs == null ? "" : " ("+parentIDs.size()+" parents)"), SHORT_NAME, weight, false);
		this.rupSet = rupSet;
		this.parentIDs = parentIDs;
	}

	@Override
	public int getNumRows() {
		// Get list of parent sections
		HashSet<Integer> parentIDs = new HashSet<Integer>();
		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
			int parentID = sect.getParentSectionId();
			if (this.parentIDs != null && !this.parentIDs.contains(parentID))
				continue;
			parentIDs.add(parentID);
		}
		int totalNumMFDSmoothnessConstraints = 0;
		for (int parentID: parentIDs) {
			// Get list of subsections for parent 
			ArrayList<Integer> sectsForParent = new ArrayList<Integer>();
			for (FaultSection sect : rupSet.getFaultSectionDataList()) {
				int sectParentID = sect.getParentSectionId();
				if (sectParentID == parentID)
					sectsForParent.add(sect.getSectionId());
			}
			// For each beginning section of subsection-pair, there will be 1 constraints
			if (sectsForParent.size() > 2)
				totalNumMFDSmoothnessConstraints += sectsForParent.size()-2;
		}
		return totalNumMFDSmoothnessConstraints;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		int rowIndex = startRow;
		
		// Get list of parent IDs
		Map<Integer, List<FaultSection>> parentSectsMap = new HashMap<>();
		
		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
			Integer parentID = sect.getParentSectionId();
			if (this.parentIDs != null && !this.parentIDs.contains(parentID))
				continue;
			List<FaultSection> parentSects = parentSectsMap.get(parentID);
			if (parentSects == null) {
				parentSects = new ArrayList<>();
				parentSectsMap.put(parentID, parentSects);
			}
			parentSects.add(sect);
		}
		
		List<Integer> sortedParentIDs = new ArrayList<>(parentSectsMap.keySet());
		Collections.sort(sortedParentIDs);
		
		for (int parentID : sortedParentIDs) {
			List<FaultSection> sectsForParent = parentSectsMap.get(parentID);
			
			List<HashSet<Integer>> sectRupLists = new ArrayList<>();
			for (FaultSection sect : sectsForParent)
				sectRupLists.add(new HashSet<>(rupSet.getRupturesForSection(sect.getSectionId())));
			
			// Laplacian smoothing of event rates: r[i+1]-2*r[i]+r[i-1]=0 (minimize curvature of event rates)
			// Don't need to worry about smoothing for subsection pairs at edges b/c they always share the same ruptures (no ruptures have only 1 subsection in a given parent section)
			for (int j=1; j<sectsForParent.size()-2; j++) {
				HashSet<Integer> sect1Hash = sectRupLists.get(j-1);
				HashSet<Integer> sect2Hash = sectRupLists.get(j);
				HashSet<Integer> sect3Hash = sectRupLists.get(j+1);
				
				List<Integer> sect1Rups = new ArrayList<>();  
				List<Integer> sect2Rups = new ArrayList<>();
				List<Integer> sect3Rups = new ArrayList<>();
				
				// only rups that involve sect 1 but not in sect 2
				for (Integer sect1Rup : sect1Hash)
					if (!sect2Hash.contains(sect1Rup))
						sect1Rups.add(sect1Rup);
				// only rups that involve sect 2 but not sect 1, then add in rups that involve sect 2 but not sect 3
				// Apparent double counting is OK, that is the factor of 2 in the center of the Laplacian
				// Think of as: (r[i+1]-*r[i]) + (r[i-1]-r[i])=0 
				for (Integer sect2Rup : sect2Hash)
					if (!sect1Hash.contains(sect2Rup) || !sect3Hash.contains(sect2Rup))
						sect2Rups.add(sect2Rup); 
				// only rups that involve sect 3 but sect 2
				for (Integer sect3Rup : sect3Hash) {
					if (!sect2Hash.contains(sect3Rup))
						sect3Rups.add(sect3Rup);
				}
				
				// Loop over ruptures in this subsection-MFD bin
				for (int rup: sect1Rups) { 
					setA(A, rowIndex, rup, weight);
					numNonZeroElements++;
				}
				for (int rup: sect2Rups) {
					setA(A, rowIndex, rup, -weight);
					numNonZeroElements++;
				}
				for (int rup: sect3Rups) {
					setA(A, rowIndex, rup, weight);
					numNonZeroElements++;
				}
				d[rowIndex]=0;
				rowIndex++;
			}
		}
		return numNonZeroElements;
	}

	@Override
	public void setRuptureSet(FaultSystemRupSet rupSet) {
		this.rupSet = rupSet;
	}

}
