package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.coulomb.CoulombRatesRecord;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class U3CoulombJunctionFilter implements PlausibilityFilter {
	
	private CoulombRatesTester tester;
	private CoulombRates coulombRates;

	public U3CoulombJunctionFilter(CoulombRatesTester tester, CoulombRates coulombRates) {
		this.tester = tester;
		this.coulombRates = coulombRates;
	}
	
	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumJumps() == 0)
			return PlausibilityResult.PASS;
		
		List<List<IDPairing>> paths = new ArrayList<>();
		findPaths(rupture.getTreeNavigator(), paths, new ArrayList<>(), rupture.clusters[0].startSect);
		
		return testPaths(paths, verbose);
	}
	
	private void findPaths(RuptureTreeNavigator navigator,
			List<List<IDPairing>> fullPaths, List<IDPairing> curPath, FaultSection curSect) {
		Collection<FaultSection> descendants = navigator.getDescendants(curSect);
		
		while (descendants.size() == 1) {
			FaultSection destSect = descendants.iterator().next();
			if (curSect.getParentSectionId() != destSect.getParentSectionId())
				// it's a jump
				curPath.add(new IDPairing(curSect.getSectionId(), destSect.getSectionId()));
			
			curSect = destSect;
			descendants = navigator.getDescendants(curSect);
		}
		
		if (descendants.isEmpty()) {
			// we're at the end of a chain
			fullPaths.add(curPath);
		} else {
			// we're at a branching point
			for (FaultSection destSect : descendants) {
				List<IDPairing> branchPath = new ArrayList<>(curPath);
				if (curSect.getParentSectionId() != destSect.getParentSectionId())
					// it's a jump
					branchPath.add(new IDPairing(curSect.getSectionId(), destSect.getSectionId()));
				
				findPaths(navigator, fullPaths, branchPath, destSect);
			}
		}
	}
	
	private PlausibilityResult testPaths(List<List<IDPairing>> paths, boolean verbose) {
		if (verbose)
			System.out.println(getShortName()+": found "+paths.size()+" paths");
		Preconditions.checkState(paths.size() >= 0);
		for (List<IDPairing> path : paths) {
			if (verbose)
				System.out.println(getShortName()+": testing a path with "+path.size()+" jumps");
			if (path.isEmpty())
				continue;
			List<CoulombRatesRecord> forwardRates = new ArrayList<>();
			List<CoulombRatesRecord> backwardRates = new ArrayList<>();
			
			for (IDPairing pair : path) {
				CoulombRatesRecord forwardRate = coulombRates.get(pair);
				Preconditions.checkNotNull(forwardRate, "No coulomb rates for %s", pair);
				CoulombRatesRecord backwardRate = coulombRates.get(pair.getReversed());
				Preconditions.checkNotNull(backwardRate, "No coulomb rates for reversed %s", pair);
				if (verbose) {
					System.out.println(getShortName()+": "+pair.getID1()+" => "+pair.getID2());
					System.out.println("\tForward rate: "+forwardRate);
					System.out.println("\tBackward rate: "+backwardRate);
				}
				forwardRates.add(forwardRate);
				backwardRates.add(0, backwardRate);
			}
			
			boolean passes = tester.doesRupturePass(forwardRates, backwardRates);
			if (verbose)
				System.out.println(getShortName()+": test with "+forwardRates.size()+" jumps. passes ? "+passes);
			if (!passes)
				return PlausibilityResult.FAIL_HARD_STOP;
		}
		return PlausibilityResult.PASS;
	}

	@Override
	public String getShortName() {
		return "Coulomb";
	}

	@Override
	public String getName() {
		return "Coulomb Jump Filter";
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		// only directional if splayed (different inversions could take different jumping points)
		return splayed;
	}

}
