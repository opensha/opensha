package scratch.UCERF3.inversion.laughTest;

import java.util.HashSet;
import java.util.List;

import org.opensha.commons.util.IDPairing;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.coulomb.CoulombRatesRecord;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester;

/**
 * This is a Coulomb Filter which only applies Coulomb tests at each junction where a rupture
 * jumps to a new parent fault section.
 * 
 * @author kevin
 *
 */
public class CoulombFilter extends AbstractPlausibilityFilter {
	
	private CoulombRates rates;
	private CoulombRatesTester tester;
	private boolean missingAsFail = false;
	
	public CoulombFilter(CoulombRates rates, CoulombRatesTester tester) {
		this.rates = rates;
		this.tester = tester;
	}

	@Override
	public PlausibilityResult applyLastSection(List<? extends FaultSection> rupture,
			List<IDPairing> pairings, List<Integer> junctionIndexes) {
		if (rupture.size() < 2 || (isApplyJunctionsOnly() && junctionIndexes.isEmpty()))
			return PlausibilityResult.PASS;
		
		List<CoulombRatesRecord> forwardRates = Lists.newArrayList();
		List<CoulombRatesRecord> backwardRates = Lists.newArrayList();
		
//		if (true) {
//			// debug for just testing last
//			IDPairing lastPairing = pairings.get(pairings.size()-1);
//			forwardRates.add(rates.get(lastPairing));
//			backwardRates.add(rates.get(lastPairing.getReversed()));
//			return tester.doesRupturePass(forwardRates, backwardRates);
//		}
		
		if (isApplyJunctionsOnly()) {
			for (int junctionIndex : junctionIndexes) {
				// junctionIndex-1 here  because junctions point forwards, but pairings start one back
				IDPairing pair = pairings.get(junctionIndex-1);
				if (missingAsFail && rates.get(pair) == null)
					return PlausibilityResult.FAIL_HARD_STOP;
//				System.out.println(pair);
//				FaultSection sect1 = rupture.get(junctionIndex-1);
//				FaultSection sect2 = rupture.get(junctionIndex);
//				System.out.println("\t"+sect1.getSectionId()+": "+sect1.getSectionName());
//				System.out.println("\t"+sect2.getSectionId()+": "+sect2.getSectionName());
				
				forwardRates.add(rates.get(pair));
				Preconditions.checkNotNull(rates.get(pair),
						"No coulomb for: %s, have %s pairings in total", pair, rates.size());
				Preconditions.checkNotNull(rates.get(pair.getReversed()),
						"No coulomb for: %s, have %s pairings in total", pair.getReversed(), rates.size());
				backwardRates.add(0, rates.get(pair.getReversed()));
			}
		} else {
			for (IDPairing pair : pairings) {
				if (missingAsFail && rates.get(pair) == null)
					return PlausibilityResult.FAIL_HARD_STOP;
				forwardRates.add(rates.get(pair));
				backwardRates.add(0, rates.get(pair.getReversed()));
			}
		}
		if (tester.doesRupturePass(forwardRates, backwardRates))
			return PlausibilityResult.PASS;
		return PlausibilityResult.FAIL_HARD_STOP;
	}

	@Override
	public boolean isApplyJunctionsOnly() {
		return tester.isApplyBranchesOnly();
	}
	
	@Override
	public String getName() {
		return "Coulomb Filter";
	}
	
	@Override
	public String getShortName() {
		return "Coulomb";
	}
	
	public void setMissingAsFail(boolean missingAsFail) {
		this.missingAsFail = missingAsFail;
	}

}
