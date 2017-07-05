package scratch.UCERF3.inversion.laughTest;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.coulomb.CoulombRatesRecord;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester.TestType;
import scratch.UCERF3.inversion.laughTest.CoulombFilter;
import scratch.UCERF3.utils.IDPairing;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class TestCoulombFilter {
	
	private static FaultSectionPrefData buildFaultSection(int sectID, int parentID) {
		FaultSectionPrefData sect = new FaultSectionPrefData();
		
		sect.setSectionId(sectID);
		sect.setParentSectionId(parentID);
		
		return sect;
	}
	
	private static List<FaultSectionPrefData> buildTwoSubSectParentsRup(int numParents) {
		int numSects = numParents*2;
		List<FaultSectionPrefData> sectionDataList = Lists.newArrayList();
		for (int i=0; i<numSects; i++)
			sectionDataList.add(buildFaultSection(i, (i/2)));
		return sectionDataList;
	}
	
//	private static List<List<Integer>> buildConnections(List<FaultSectionPrefData> sectionDataList, boolean connectFirstToLast) {
//		List<List<Integer>> sectionConnectionsListList = Lists.newArrayList();
//		
//		for (FaultSectionPrefData sect : sectionDataList) {
//			System.out.println(sect.getSectionId()+" ("+sect.getParentSectionId()+")");
//			
//			List<Integer> connections = Lists.newArrayList();
//			int before = sect.getSectionId()-1;
//			int after = sect.getSectionId()+1;
//			if (connectFirstToLast || before >= 0) {
//				if (before < 0)
//					before = sectionDataList.size()-1;
//				connections.add(before);
//			}
//			
//			if (connectFirstToLast || after < sectionDataList.size()) {
//				if (after == sectionDataList.size())
//					after = 0;
//				connections.add(after);
//			}
//			
//			connections.add(before);
//			connections.add(after);
//			
//			sectionConnectionsListList.add(connections);
//		}
//		
//		return sectionConnectionsListList;
//	}
	
//	private static Map<IDPairing, Double> getMapForPairings(List<FaultSectionPrefData> datas) {
//		Map<IDPairing, Double> map = Maps.newHashMap();
//		for (int i=0; i<datas.size(); i++) {
//			int next = i+1;
//			if (next == datas.size())
//				next = 0;
//			IDPairing pairing = new IDPairing(i, next);
//			map.put(pairing, 0d);
//			map.put(pairing.getReversed(), 0d);
//		}
//		return map;
//	}
//	
//	private static CoulombRatesRecord getRecord(int id1, int id2, boolean allowedByStress, boolean allowedByRatio) {
//		IDPairing pairing = new IDPairing(id1, id2);
//		double dcff, pdcff;
//		if (allowedByStress)
//			dcff = 1.5;
//		else
//			dcff = 0;
//		if (allowedByRatio)
//			pdcff = 1;
//		else
//			pdcff = 0;
//		return new CoulombRatesRecord(pairing, 0, 0, dcff, pdcff);
//	}
	
	private enum RecType {
		FAIL(0, 0),
		PASS_DCFF(10, 0),
		PASS_PDCFF(0, 1),
		PASS_BOTH(10, 1);
		
		private double dcff, pdcff;
		private RecType(double dcff, double pdcff) {
			this.dcff = dcff;
			this.pdcff = pdcff;
		}
		
		public CoulombRatesRecord build(IDPairing pairing) {
			return new CoulombRatesRecord(pairing, 0, 0, dcff, pdcff);
		}
	}
	
	private static <E> E[] toArray(E... vals) {
		return vals;
	}
	
	private static CoulombRates buildRates(List<FaultSectionPrefData> rupture, RecType[]... recTypes) {
		Map<IDPairing, CoulombRatesRecord> ratesMap = Maps.newHashMap();
		
		int parentID = 0;
		for (int i=1; i<rupture.size(); i++) {
			if (rupture.get(i).getParentSectionId() != rupture.get(i-1).getParentSectionId()) {
				// we're at a junction
				IDPairing pairing = new IDPairing(i-1, i);
				ratesMap.put(pairing, recTypes[parentID][0].build(pairing));
				pairing = pairing.getReversed();
				ratesMap.put(pairing, recTypes[parentID][1].build(pairing));
				parentID++;
			}
		}
		
		return new CoulombRates(ratesMap);
	}
	
	private static CoulombRatesTester getDefaultTester() {
		return new CoulombRatesTester(TestType.COULOMB_STRESS, 0.05, 0.05, 1.25, true, true);
	}
	
	private static void doTest(List<FaultSectionPrefData> rupture, CoulombRates rates, CoulombRatesTester tester,
			boolean expected, String failMessage) {
		CoulombFilter filter = new CoulombFilter(rates, tester);
		
		if (expected)
			assertTrue(failMessage, filter.doesRupturePass(rupture));
		else
			assertFalse(failMessage, filter.doesRupturePass(rupture));
	}

	@Test
	public void test2ParentCases() {
		List<FaultSectionPrefData> rupture = buildTwoSubSectParentsRup(2);
		
		boolean[] anys = { false, true };
		
		for (boolean allowAnyDirection : anys) {
			CoulombRatesTester tester = getDefaultTester();
			tester.setAllowAnyWay(allowAnyDirection);
			
			String dirStr;
			if (allowAnyDirection)
				dirStr = "any direction 2 parent test ";
			else
				dirStr = "single direction 2 parent test ";
			
			// forward pass, backward fail
			CoulombRates rates = buildRates(rupture, toArray(RecType.PASS_DCFF, RecType.FAIL));
			doTest(rupture, rates, tester, true, dirStr+"should pass DCFF");
			rates = buildRates(rupture, toArray(RecType.PASS_PDCFF, RecType.FAIL));
			doTest(rupture, rates, tester, true, dirStr+"should pass PDCFF");
			rates = buildRates(rupture, toArray(RecType.PASS_BOTH, RecType.FAIL));
			doTest(rupture, rates, tester, true, dirStr+"should pass BOTH");
			
			// forward fail, backward pass
			rates = buildRates(rupture, toArray(RecType.FAIL, RecType.PASS_DCFF));
			doTest(rupture, rates, tester, true, dirStr+"should pass DCFF");
			rates = buildRates(rupture, toArray(RecType.FAIL, RecType.PASS_PDCFF));
			doTest(rupture, rates, tester, true, dirStr+"should pass PDCFF");
			rates = buildRates(rupture, toArray(RecType.FAIL, RecType.PASS_BOTH));
			doTest(rupture, rates, tester, true, dirStr+"should pass BOTH");
			
			// both fail
			rates = buildRates(rupture, toArray(RecType.FAIL, RecType.FAIL));
			doTest(rupture, rates, tester, false, "single direction 2 parent test should pass BOTH");
		}
	}

	@Test
	public void test3ParentCases() {
		List<FaultSectionPrefData> rupture = buildTwoSubSectParentsRup(3);
		
		boolean[] anys = { false, true };
		
		for (boolean allowAnyDirection : anys) {
			CoulombRatesTester tester = getDefaultTester();
			tester.setAllowAnyWay(allowAnyDirection);
			
			String dirStr;
			if (allowAnyDirection)
				dirStr = "any direction 3 parent test";
			else
				dirStr = "single direction 3 parent test";
			
			CoulombRates rates;
			
			// forward pass, backward fail, middle fail
			rates = buildRates(rupture, toArray(RecType.PASS_DCFF, RecType.FAIL), toArray(RecType.PASS_DCFF, RecType.FAIL));
			doTest(rupture, rates, tester, true, dirStr);
			// forward pass, backward fail, middle pass
			rates = buildRates(rupture, toArray(RecType.PASS_DCFF, RecType.PASS_DCFF), toArray(RecType.PASS_DCFF, RecType.FAIL));
			doTest(rupture, rates, tester, true, dirStr);
			
			// forward fail, backward pass, middle fail
			rates = buildRates(rupture, toArray(RecType.FAIL, RecType.PASS_DCFF), toArray(RecType.FAIL, RecType.PASS_DCFF));
			doTest(rupture, rates, tester, true, dirStr);
			// forward fail, backward pass, middle pass
			rates = buildRates(rupture, toArray(RecType.FAIL, RecType.PASS_DCFF), toArray(RecType.PASS_DCFF, RecType.PASS_DCFF));
			doTest(rupture, rates, tester, true, dirStr);
			
			// forward fail, backward fail, middle pass
			rates = buildRates(rupture, toArray(RecType.FAIL, RecType.PASS_DCFF), toArray(RecType.PASS_DCFF, RecType.FAIL));
			doTest(rupture, rates, tester, allowAnyDirection, dirStr);
			
			
			// forward fail, backward fail, middle fail
			rates = buildRates(rupture, toArray(RecType.PASS_DCFF, RecType.FAIL), toArray(RecType.FAIL, RecType.PASS_DCFF));
			doTest(rupture, rates, tester, false, dirStr);
			// forward fail, backward fail, middle pass
			rates = buildRates(rupture, toArray(RecType.FAIL, RecType.FAIL), toArray(RecType.FAIL, RecType.PASS_DCFF));
			doTest(rupture, rates, tester, false, dirStr);
			// forward fail, backward fail, middle pass
			rates = buildRates(rupture, toArray(RecType.FAIL, RecType.FAIL), toArray(RecType.PASS_DCFF, RecType.FAIL));
			doTest(rupture, rates, tester, false, dirStr);
			// forward fail, backward fail, middle pass
			rates = buildRates(rupture, toArray(RecType.FAIL, RecType.PASS_DCFF), toArray(RecType.FAIL, RecType.FAIL));
			doTest(rupture, rates, tester, false, dirStr);
			// forward fail, backward fail, middle pass
			rates = buildRates(rupture, toArray(RecType.PASS_DCFF, RecType.FAIL), toArray(RecType.FAIL, RecType.FAIL));
			doTest(rupture, rates, tester, false, dirStr);
		}
	}

	@Test
	public void test4ParentCases() {
		List<FaultSectionPrefData> rupture = buildTwoSubSectParentsRup(4);
		
		boolean[] anys = { false, true };
		
		for (boolean allowAnyDirection : anys) {
			CoulombRatesTester tester = getDefaultTester();
			tester.setAllowAnyWay(allowAnyDirection);
			
			String dirStr;
			if (allowAnyDirection)
				dirStr = "any direction 3 parent test";
			else
				dirStr = "single direction 3 parent test";
			
			CoulombRates rates;
			
			// forward pass, backward fail, middle pass
			rates = buildRates(rupture, toArray(RecType.FAIL, RecType.PASS_DCFF),
										toArray(RecType.PASS_DCFF, RecType.FAIL),
										toArray(RecType.PASS_DCFF, RecType.FAIL));
			doTest(rupture, rates, tester, allowAnyDirection, dirStr);
			
			// forward pass, backward fail, middle pass
			rates = buildRates(rupture, toArray(RecType.FAIL, RecType.PASS_DCFF),
										toArray(RecType.FAIL, RecType.PASS_DCFF),
										toArray(RecType.PASS_DCFF, RecType.FAIL));
			doTest(rupture, rates, tester, allowAnyDirection, dirStr);
			
			// forward pass, backward fail, middle fail
			rates = buildRates(rupture, toArray(RecType.PASS_DCFF, RecType.FAIL),
										toArray(RecType.FAIL, RecType.PASS_DCFF),
										toArray(RecType.FAIL, RecType.PASS_DCFF));
			doTest(rupture, rates, tester, false, dirStr);
			
			// forward pass, backward fail, middle fail
			rates = buildRates(rupture, toArray(RecType.PASS_DCFF, RecType.FAIL),
										toArray(RecType.PASS_DCFF, RecType.FAIL),
										toArray(RecType.FAIL, RecType.PASS_DCFF));
			doTest(rupture, rates, tester, false, dirStr);
			
			// forward pass, backward fail, middle fail
			rates = buildRates(rupture, toArray(RecType.PASS_DCFF, RecType.FAIL),
										toArray(RecType.FAIL, RecType.PASS_DCFF),
										toArray(RecType.PASS_DCFF, RecType.FAIL));
			doTest(rupture, rates, tester, false, dirStr);
			
			// forward pass, backward fail, middle fail
			rates = buildRates(rupture, toArray(RecType.FAIL, RecType.PASS_DCFF),
										toArray(RecType.PASS_DCFF, RecType.FAIL),
										toArray(RecType.FAIL, RecType.PASS_DCFF));
			doTest(rupture, rates, tester, false, dirStr);
		}
	}

}
