package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.utils.SectionMFD_constraint;

/**
 * MFDs spatially smooth along adjacent subsections on a parent section (Laplacian smoothing).
 * 
 * This can be applied globally to all sections, or only to those which have paleoseismic constraints.
 * In UCERF3, we used it only on paleo-constrained sections.
 * 
 * @author Morgan Page & Kevin Milner
 *
 */
public class MFDLaplacianSmoothingInversionConstraint extends InversionConstraint {
	
	public static final String NAME = "MFD Laplacian Smoothing";
	public static final String SHORT_NAME = "LaplaceSmooth";
	
	private transient FaultSystemRupSet rupSet;
	private HashSet<Integer> parentIDs;
	private List<SectionMFD_constraint> constraints;

	public MFDLaplacianSmoothingInversionConstraint(FaultSystemRupSet rupSet,
			double weight, List<SectionMFD_constraint> constraints) {
		this(rupSet, weight, null, constraints);
	}

	public MFDLaplacianSmoothingInversionConstraint(FaultSystemRupSet rupSet,
			double weight, HashSet<Integer> parentIDs,
			List<SectionMFD_constraint> constraints) {
		super(NAME, SHORT_NAME, weight, false);
		this.rupSet = rupSet;
		this.parentIDs = parentIDs;
		this.constraints = constraints;
	}

	@Override
	public int getNumRows() {
		// Get list of parent sections
		HashSet<Integer> parentIDs = new HashSet<Integer>();
		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
			int parentID = sect.getParentSectionId();
			parentIDs.add(parentID);
		}
		int totalNumMFDSmoothnessConstraints = 0;
		for (int parentID: parentIDs) {
			if (this.parentIDs != null && !this.parentIDs.contains(parentID))
				continue;
			// Get list of subsections for parent 
			ArrayList<Integer> sectsForParent = new ArrayList<Integer>();
			for (FaultSection sect : rupSet.getFaultSectionDataList()) {
				int sectParentID = sect.getParentSectionId();
				if (sectParentID == parentID)
					sectsForParent.add(sect.getSectionId());
			}
			// For each beginning section of subsection-pair, there will be numMagBins # of constraints
			for (int j=1; j<sectsForParent.size()-2; j++) {
				int sect2 = sectsForParent.get(j);
				SectionMFD_constraint sectMFDConstraint = constraints.get(sect2);
				if (sectMFDConstraint == null)
					continue; // Parent sections with Mmax<6 have no MFD constraint; skip these
				int numMagBins = sectMFDConstraint.getNumMags();
				totalNumMFDSmoothnessConstraints+=numMagBins;
			}
		}
		return totalNumMFDSmoothnessConstraints;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		int rowIndex = startRow;
		
		// Get list of parent IDs
		Map<Integer, List<FaultSection>> parentSectsMap = Maps.newHashMap();
		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
			Integer parentID = sect.getParentSectionId();
			List<FaultSection> parentSects = parentSectsMap.get(parentID);
			if (parentSects == null) {
				parentSects = Lists.newArrayList();
				parentSectsMap.put(parentID, parentSects);
			}
			parentSects.add(sect);
		}
		
		List<HashSet<Integer>> sectRupsHashes = Lists.newArrayList();
		for (int s=0; s<rupSet.getNumSections(); s++)
			sectRupsHashes.add(new HashSet<Integer>(rupSet.getRupturesForSection(s)));

		for (List<FaultSection> sectsForParent : parentSectsMap.values()) {		
			
			// Does this parent sect have a paleo constraint?
			int parentID = rupSet.getFaultSectionDataList().get(sectsForParent.get(0).getSectionId()).getParentSectionId();
			
			if (this.parentIDs != null && !this.parentIDs.contains(parentID))
				continue;
			
			// Laplacian smoothing of event rates: r[i+1]-2*r[i]+r[i-1]=0 (minimize curvature of event rates)
			// Don't need to worry about smoothing for subsection pairs at edges b/c they always share the same ruptures (no ruptures have only 1 subsection in a given parent section)
			for (int j=1; j<sectsForParent.size()-2; j++) {
				int sect1 = sectsForParent.get(j-1).getSectionId(); 
				HashSet<Integer> sect1Hash = sectRupsHashes.get(sect1);
				int sect2 = sectsForParent.get(j).getSectionId(); 
				HashSet<Integer> sect2Hash = sectRupsHashes.get(sect2);
				int sect3 = sectsForParent.get(j+1).getSectionId(); 
				HashSet<Integer> sect3Hash = sectRupsHashes.get(sect3);
				
				List<Integer> sect1Rups = Lists.newArrayList();  
				List<Integer> sect2Rups = Lists.newArrayList();
				List<Integer> sect3Rups = Lists.newArrayList();
				
				// only rups that involve sect 1 but not in sect 2
				for (Integer sect1Rup : sect1Hash)
					if (!sect2Hash.contains(sect1Rup))
						sect1Rups.add(sect1Rup);
				// only rups that involve sect 2 but not sect 1, then add in rups that involve sect 2 but not sect 3
				// Apparent double counting is OK, that is the factor of 2 in the center of the Laplacian
				// Think of as: (r[i+1]-*r[i]) + (r[i-1]-r[i])=0 
				for (Integer sect2Rup : sect2Hash)
					if (!sect1Hash.contains(sect2Rup))
						sect2Rups.add(sect2Rup); 
				for (Integer sect2Rup : sect2Hash)
					if (!sect3Hash.contains(sect2Rup))
						sect2Rups.add(sect2Rup); 
				// only rups that involve sect 3 but sect 2
				for (Integer sect3Rup : sect3Hash) {
					if (!sect2Hash.contains(sect3Rup))
						sect3Rups.add(sect3Rup);
				}
				
				// Get section MFD constraint -- we will use the irregular mag binning for the constraint (but not the rates)
				SectionMFD_constraint sectMFDConstraint = constraints.get(sect2);
				if (sectMFDConstraint == null)
					continue; // Parent sections with Mmax<6 have no MFD constraint; skip these
				int numMagBins = sectMFDConstraint.getNumMags();
				// Loop over MFD constraints for this subsection
				for (int magBin = 0; magBin<numMagBins; magBin++) {
				
					// Determine which ruptures are in this magBin
					List<Integer> sect1RupsForMagBin = new ArrayList<Integer>();
					for (int i=0; i<sect1Rups.size(); i++) {
						double mag = rupSet.getMagForRup(sect1Rups.get(i));
						if (sectMFDConstraint.isMagInBin(mag, magBin))
							sect1RupsForMagBin.add(sect1Rups.get(i));
					}
					List<Integer> sect2RupsForMagBin = new ArrayList<Integer>();
					for (int i=0; i<sect2Rups.size(); i++) {
						double mag = rupSet.getMagForRup(sect2Rups.get(i));
						if (sectMFDConstraint.isMagInBin(mag, magBin))
							sect2RupsForMagBin.add(sect2Rups.get(i));
					}
					List<Integer> sect3RupsForMagBin = new ArrayList<Integer>();
					for (int i=0; i<sect3Rups.size(); i++) {
						double mag = rupSet.getMagForRup(sect3Rups.get(i));
						if (sectMFDConstraint.isMagInBin(mag, magBin))
							sect3RupsForMagBin.add(sect3Rups.get(i));
					}
					
					// Loop over ruptures in this subsection-MFD bin
					for (int rup: sect1RupsForMagBin) { 
						setA(A, rowIndex, rup, weight);
						numNonZeroElements++;
					}
					for (int rup: sect2RupsForMagBin) {
						setA(A, rowIndex, rup, -weight);
						numNonZeroElements++;
					}
					for (int rup: sect3RupsForMagBin) {
						setA(A, rowIndex, rup, weight);
						numNonZeroElements++;
					}
					d[rowIndex]=0;
					rowIndex++;
				}
			}
		}
		return numNonZeroElements;
	}

	@Override
	public void setRuptureSet(FaultSystemRupSet rupSet) {
		this.rupSet = rupSet;
	}

}
