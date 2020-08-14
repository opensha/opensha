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
		List<Integer> parentIDs = new ArrayList<Integer>();
		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
			int parentID = sect.getParentSectionId();
			if (!parentIDs.contains(parentID))
				parentIDs.add(parentID);
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
				List<Integer> sect1Rups = Lists.newArrayList(rupSet.getRupturesForSection(sect1));  
				List<Integer> sect2Rups = Lists.newArrayList(rupSet.getRupturesForSection(sect2));
				
				HashSet<Integer> prevRups = new HashSet<>();
				
				for (int rup: sect1Rups) { 
					if (prevRups.contains(rup))
						continue;
					double probPaleoVisible = paleoProbabilityModel.getProbPaleoVisible(rupSet, rup, sect1);	
					setA(A, rowIndex, rup, probPaleoVisible*weight);
					if (probPaleoVisible > 0)
						numNonZeroElements++;
					prevRups.add(rup);
				}
				for (int rup: sect2Rups) {
					if (prevRups.contains(rup))
						continue;
					double probPaleoVisible = paleoProbabilityModel.getProbPaleoVisible(rupSet, rup, sect2);
					setA(A, rowIndex, rup, -probPaleoVisible*weight);
					if (probPaleoVisible > 0)
						numNonZeroElements++;
					prevRups.add(rup);
				}
				d[rowIndex] = 0;
				rowIndex++;
			}
		}
		return numNonZeroElements;
	}

}
