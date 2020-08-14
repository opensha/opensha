package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.faultSurface.FaultSection;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.FaultSystemRupSet;

public class RupRateSmoothingInversionConstraint extends InversionConstraint {
	
	private double weight;
	private List<IDPairing> smoothingConstraintRupPairings;

	public RupRateSmoothingInversionConstraint(double weight, FaultSystemRupSet rupSet) {
		this(weight, getRupSmoothingPairings(rupSet));
	}
	
	public RupRateSmoothingInversionConstraint(double weight, List<IDPairing> smoothingConstraintRupPairings) {
		this.weight = weight;
		this.smoothingConstraintRupPairings = smoothingConstraintRupPairings;
	}

	@Override
	public String getShortName() {
		return "RupRateSmooth";
	}

	@Override
	public String getName() {
		return "Rup Rate Smoothing";
	}

	@Override
	public int getNumRows() {
		return smoothingConstraintRupPairings.size();
	}

	@Override
	public boolean isInequality() {
		return false;
	}

	@Override
	public long encode(DoubleMatrix2D A, double[] d, int startRow) {
		long numNonZeroElements = 0;
		for (int i=0; i<smoothingConstraintRupPairings.size(); i++) {
			int rowIndex = startRow + i;
			IDPairing rupPairings = smoothingConstraintRupPairings.get(i);
			setA(A, rowIndex, rupPairings.getID1(), weight);
			setA(A, rowIndex, rupPairings.getID2(), -weight);
			d[rowIndex]=0;
			numNonZeroElements += 2;
		}
		return numNonZeroElements;
	}
	
	public static List<IDPairing> getRupSmoothingPairings(FaultSystemRupSet rupSet) {
		List<IDPairing> pairings = new ArrayList<>();
		int numRuptures = rupSet.getNumRuptures();
		// This is a list of each rupture as a HashSet for quick contains(...) operations
		List<HashSet<Integer>> rupHashList = new ArrayList<>();
		// This is a list of the parent sections for each rupture as a HashSet
		List<HashSet<Integer>> rupParentsHashList = new ArrayList<>();
		// This is a mapping from each unique set of parent sections, to the ruptures which involve all/only those parent sections
		Map<HashSet<Integer>, List<Integer>> parentsToRupsMap = new HashMap<>();
		for (int r=0; r<numRuptures; r++) {
			// create the hashSet for the rupture
			rupHashList.add(new HashSet<Integer>(rupSet.getSectionsIndicesForRup(r)));
			// build the hashSet of parents
			HashSet<Integer> parentIDs = new HashSet<Integer>();
			for (FaultSection sect : rupSet.getFaultSectionDataForRupture(r))
				parentIDs.add(sect.getParentSectionId());	// this won't have duplicates since it's a hash set
			rupParentsHashList.add(parentIDs);
			// now add this rupture to the list of ruptures that involve this set of parents
			List<Integer> rupsForParents = parentsToRupsMap.get(parentIDs);
			if (rupsForParents == null) {
				rupsForParents = new ArrayList<>();
				parentsToRupsMap.put(parentIDs, rupsForParents);
			}
			rupsForParents.add(r);
		}
//		if (D) System.out.println("Rupture rate smoothing constraint: "+parentsToRupsMap.size()+" unique parent set combinations");

		// Find set of rupture pairs for smoothing
		for(int rup1=0; rup1<numRuptures; rup1++) {
			List<Integer> sects = rupSet.getSectionsIndicesForRup(rup1);
			HashSet<Integer> rup1Parents = rupParentsHashList.get(rup1);
			ruptureLoop:
				for(Integer rup2 : parentsToRupsMap.get(rup1Parents)) { // We only loop over ruptures that involve the same set of parents
					// Only keep pair if rup1 < rup2 (don't need to include pair in each direction)
					if (rup2 <= rup1) // Only keep pair if rup1 < rup2 (don't need to include pair in each direction)
						continue;
					HashSet<Integer> sects2 = rupHashList.get(rup2);
					// Check that ruptures are same size
					if (sects.size() != sects2.size()) continue ruptureLoop;
					// Check that ruptures differ by at most one subsection
					int numSectsDifferent = 0;
					for(int i=0; i<sects.size(); i++) {
						if (!sects2.contains(sects.get(i))) numSectsDifferent++;
						if (numSectsDifferent > 1) continue ruptureLoop;
					}
					// The pair passes!
					pairings.add(new IDPairing(rup1, rup2));
				}
		}
		
		return pairings;
	}

}
