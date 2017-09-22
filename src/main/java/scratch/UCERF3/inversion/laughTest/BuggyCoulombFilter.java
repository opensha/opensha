package scratch.UCERF3.inversion.laughTest;

import java.util.HashSet;
import java.util.List;

import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.coulomb.CoulombRatesRecord;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester;
import scratch.UCERF3.utils.IDPairing;

/**
 * This is the buggy coulomb filter used in UCERF3.2 and below, kept for compatibility
 * @author kevin
 *
 */
public class BuggyCoulombFilter extends AbstractLaughTest {
	
	private CoulombRates rates;
	private CoulombRatesTester tester;
	private boolean minEqualsAvg;
	private List<FaultSectionPrefData> sectionDataList;
	private List<List<Integer>> sectionConnectionsListList;
	
	public BuggyCoulombFilter(CoulombRates rates, CoulombRatesTester tester,
			List<FaultSectionPrefData> sectionDataList, List<List<Integer>> sectionConnectionsListList) {
		this.rates = rates;
		this.tester = tester;
		this.minEqualsAvg = tester.getMinAverageProb() <= tester.getMinIndividualProb();
		this.sectionDataList = sectionDataList;
		this.sectionConnectionsListList = sectionConnectionsListList;
	}

	@Override
	public boolean doesLastSectionPass(List<FaultSectionPrefData> rupture,
			List<IDPairing> pairings, List<Integer> junctionIndexes) {
		if (rupture.size() < 2 || junctionIndexes.isEmpty())
			return true;
		
		List<Integer> rupIndexes = Lists.newArrayList();
		for (FaultSectionPrefData sect : rupture)
			rupIndexes.add(sect.getSectionId());
		
		List<CoulombRatesRecord> forwardRates = Lists.newArrayList();
		List<CoulombRatesRecord> backwardRates = Lists.newArrayList();
//		boolean debugMatch =
//				rupIndexes.get(0) == (2251) && rupIndexes.contains(2285) && rupIndexes.contains(301);
		// go backwards for extra efficiency of min == avg
		for (int i=1; i<rupture.size(); i++) {
//			if (debugMatch && rupIndexes.get(i) == 302)
//				System.out.println("Adding at 302. newList: "+Joiner.on(";").join(newList));C
			// here is the bug we should be checking if the section before this is a branch point
			if (isBranchPoint(rupIndexes.get(i), rupIndexes.subList(0, i+1))) {
				IDPairing pair = new IDPairing(rupIndexes.get(i-1), rupIndexes.get(i));
				forwardRates.add(rates.get(pair));
				backwardRates.add(0, rates.get(pair.getReversed()));
				if (rates.get(pair) == null)
					System.out.println("Weird...missing: "+pair);
//				if (minEqualsAvg)
//					// we don't need to go any further
//					break;
			}
		}
//		if (debugMatch) {
//			System.out.println("Testing coulomb: "+tester.doesRupturePass(forwardRates, backwardRates));
//			System.out.println("Rup: "+Joiner.on(", ").join(rupIndexes));
//			printDebugRates(rupIndexes, forwardRates, backwardRates);
//		}
		return tester.doesRupturePass(forwardRates, backwardRates);
	}
	
	public static void printDebugRates(List<Integer> rupIndexes,
			List<CoulombRatesRecord> forwardRates, List<CoulombRatesRecord> backwardRates) {
		List<String> fwStrings = Lists.newArrayList();
		List<String> bwStrings = Lists.newArrayList();
		List<String> indexStrings = Lists.newArrayList();
		for (int index : rupIndexes) {
			String str = "";
			for (CoulombRatesRecord r : forwardRates)
				if (r.getPairing().getID1() == index)
					str = "[";
			str += index;
			for (CoulombRatesRecord r : forwardRates)
				if (r.getPairing().getID2() == index)
					str += "]";
			indexStrings.add(str);
		}
		System.out.println("Indexes: "+Joiner.on(";").join(indexStrings));
		for (int i=0; i<forwardRates.size(); i++) {
			IDPairing pair = forwardRates.get(i).getPairing();
			fwStrings.add(pair+": "+forwardRates.get(i).getCoulombStressProbability());
			bwStrings.add(pair+": "+backwardRates.get((backwardRates.size() - 1) - i).getCoulombStressProbability());
		}
		System.out.println("Forward: "+Joiner.on("; ").join(fwStrings));
		System.out.println("Backward: "+Joiner.on("; ").join(bwStrings));
	}

	@Override
	public boolean isContinueOnFaulure() {
		return false;
	}

	@Override
	public boolean isApplyJunctionsOnly() {
		return false;
	}
	
	/**
	 * Return true if there are any connections from this index that are not on
	 * the same parent fault and not already in the rupture
	 * 
	 * @param index
	 */
	private boolean isBranchPoint(int index, List<Integer> rupture) {
		List<Integer> branches = sectionConnectionsListList.get(index);
		int sectParent = sectionDataList.get(index).getParentSectionId();
		for (int branch : branches) {
			if (rupture.contains(branch))
				continue;
			int branchParent = sectionDataList.get(branch).getParentSectionId();
			if (branchParent != sectParent) {
//				System.out.println("It's a junction! Possibility: "+index+"=>"+branch);
				return true;
			}
		}
		return false;
	}
	
	@Override
	public String getName() {
		return "Buggy Coulomb Filter";
	}
	
	@Override
	public String getShortName() {
		return "CoulombOld";
	}

}
