package scratch.UCERF3.inversion.ruptures.plausibility.impl;

import org.opensha.commons.util.IDPairing;

import com.google.common.collect.Lists;

import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.coulomb.CoulombRatesRecord;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.Jump;
import scratch.UCERF3.inversion.ruptures.plausibility.JumpPlausibilityFilter;

public class CoulombJunctionFilter extends JumpPlausibilityFilter {
	
	private CoulombRatesTester tester;
	private CoulombRates coulombRates;

	public CoulombJunctionFilter(CoulombRatesTester tester, CoulombRates coulombRates) {
		this.tester = tester;
		this.coulombRates = coulombRates;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		IDPairing forwardPair = new IDPairing(newJump.fromSection.getSectionId(),
				newJump.toSection.getSectionId());
		
		CoulombRatesRecord forwardRate = coulombRates.get(forwardPair);
		CoulombRatesRecord backwardRate = coulombRates.get(forwardPair.getReversed());
		
		if (tester.doesRupturePass(Lists.newArrayList(forwardRate), Lists.newArrayList(backwardRate)))
			return PlausibilityResult.PASS;
		return PlausibilityResult.FAIL_HARD_STOP;
	}

	@Override
	public String getShortName() {
		return "JumpCoulomb";
	}

	@Override
	public String getName() {
		return "Coulomb Jump Filter";
	}

}
