package org.opensha.sha.earthquake.faultSysSolution.erf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.TimeDepFaultSystemSolutionERF;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import scratch.UCERF3.erf.FaultSystemSolutionERF;

/**
 * Much of the content here come from scratch.UCERF3.analysis.FaultSysSolutionERF_Calc.
 * In fact, if you don't see what you need here, check to see if it can be ported
 * from that class before rewriting here.
 */
public class FaultSysSolERF_Calc {
	
	
	public static double calcParticipationProbForSect(BaseFaultSystemSolutionERF erf, double minMag, int sectionIndex) {
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		
		HashSet<Integer> rupIndexes = new HashSet<Integer>(rupSet.getRupturesForSection(sectionIndex));
		
		List<Double> probs = Lists.newArrayList();

		for(int s=0; s<erf.getNumFaultSystemSources();s++) {
			int fssRupIndex = erf.getFltSysRupIndexForSource(s);
			if (!rupIndexes.contains(fssRupIndex))
				continue;
			for (ProbEqkRupture rup : erf.getSource(s)) {
				if (rup.getMag() >= minMag)
					probs.add(rup.getProbability());
			}
		}
		return calcSummedProbs(probs);
	}
	
	
	/**
	 * The returns a HashMap of parent name for parent ID.
	 * If parent section name is null we assume it is an un-sectioned fault from NSHM and 
	 * assign the section name.
	 * @return HashMap<Integer,String>
	 */
	public static HashMap<Integer,String> getParentSectNameFromID_Map(BaseFaultSystemSolutionERF erf) {
		// get HashMap of parent name from parent ID
		HashMap<Integer,String> parentNameFromID_Map = new HashMap<Integer,String>();
		ArrayList<String> testList = new ArrayList<String>();
		for (FaultSection sect:erf.getSolution().getRupSet().getFaultSectionDataList()) {
			if(!parentNameFromID_Map.keySet().contains(sect.getParentSectionId())) {
				String name = sect.getParentSectionName(); // some are currently null (for Cascadia?)
				if(name==null) { // if parent name is null we can use section name assuming there was not subsection, which is tested for here
					if(!testList.contains(name)) {  // not already used
						name = sect.getSectionName();
						testList.add(name);
					}
					else
						throw new RuntimeException("Error: more than one section with null parent name has the same section name");
				}
				parentNameFromID_Map.put(sect.getParentSectionId(), name);
				//			System.out.println(sect.getParentSectionId()+"\t"+name);
			}
		}
		return parentNameFromID_Map;
	}

	
	public static Map<Integer, EvenlyDiscretizedFunc> calcParentSectSupraSeisPartCumMagProbDists(
			BaseFaultSystemSolutionERF erf, double minMag, int numMag, double deltaMag) {

		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		
		HashSet<Integer> parentIDs = new HashSet<Integer>();
		for (FaultSection sect : rupSet.getFaultSectionDataList())
			parentIDs.add(sect.getParentSectionId());
		
		// create a list of all rupture probs for each parent section
		Map<Integer, List<List<Double>>> sectProbLists = Maps.newHashMap();
		for (Integer parentID : parentIDs) {
			List<List<Double>> probLists = Lists.newArrayList();
			for (int m=0; m<numMag; m++)
				probLists.add(new ArrayList<Double>());
			sectProbLists.put(parentID, probLists);
		}
		
		EvenlyDiscretizedFunc xVals = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
		
		for (int sourceID=0; sourceID<erf.getNumFaultSystemSources(); sourceID++) {
			int invIndex = erf.getFltSysRupIndexForSource(sourceID);
			for (ProbEqkRupture rup : erf.getSource(sourceID)) {
				double mag = rup.getMag();
				double prob = rup.getProbability();
				for (int parentID : rupSet.getParentSectionsForRup(invIndex)) {
					populateProbListCum(mag, prob, sectProbLists.get(parentID), xVals);
				}
			}
		}
		
		Map<Integer, EvenlyDiscretizedFunc> results = Maps.newHashMap();
		for (int parentID : parentIDs) {
			EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			calcSummedProbs(sectProbLists.get(parentID), func);
			results.put(parentID, func);
		}
		return results;
	}
	
	
	public static Map<Integer, EvenlyDiscretizedFunc> calcParentSectSupraSeisPartIncrMagProbDists(
			BaseFaultSystemSolutionERF erf, double minMag, int numMag, double deltaMag) {

		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		
		HashSet<Integer> parentIDs = new HashSet<Integer>();
		for (FaultSection sect : rupSet.getFaultSectionDataList())
			parentIDs.add(sect.getParentSectionId());
		
		// create a list of all rupture probs for each parent section
		Map<Integer, List<List<Double>>> sectProbLists = Maps.newHashMap();
		for (Integer parentID : parentIDs) {
			List<List<Double>> probLists = Lists.newArrayList();
			for (int m=0; m<numMag; m++)
				probLists.add(new ArrayList<Double>());
			sectProbLists.put(parentID, probLists);
		}
		
		EvenlyDiscretizedFunc xVals = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
		
		for (int sourceID=0; sourceID<erf.getNumFaultSystemSources(); sourceID++) {
			int invIndex = erf.getFltSysRupIndexForSource(sourceID);
			for (ProbEqkRupture rup : erf.getSource(sourceID)) {
				double mag = rup.getMag();
				double prob = rup.getProbability();
				for (int parentID : rupSet.getParentSectionsForRup(invIndex)) {
					populateProbListIncr(mag, prob, sectProbLists.get(parentID), xVals);
				}
			}
		}
		
		Map<Integer, EvenlyDiscretizedFunc> results = Maps.newHashMap();
		for (int parentID : parentIDs) {
			EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			calcSummedProbs(sectProbLists.get(parentID), func);
			results.put(parentID, func);
		}
		return results;
	}
	
	/**
	 * This returns a Map with a list of section rates for each parent section
	 * (key is parent section ID).
	 * @param erf
	 * @return
	 */
	public static Map<Integer, double[]> getTotSectSupraSeisRateListForParentSectMap(
			BaseFaultSystemSolutionERF erf) {

		FaultSystemSolution sol = erf.getSolution();
		
		HashSet<Integer> parentIDs = new HashSet<Integer>(); 
		for (FaultSection sect : sol.getRupSet().getFaultSectionDataList())
			parentIDs.add(sect.getParentSectionId()); // this does not allow duplicates so no test needed
		
		// create a list of all rupture probs for each parent section
		Map<Integer, List<Double>> parRateListMap = Maps.newHashMap();
		for (Integer parentID : parentIDs) {
			List<Double> riList = Lists.newArrayList();
			parRateListMap.put(parentID, riList);
		}
		double[] sectRateArray = sol.calcParticRateForAllSects(0.0, 10.0);
		
		for(int s=0;s<sol.getRupSet().getNumSections();s++) {
			int parID = sol.getRupSet().getFaultSectionData(s).getParentSectionId();
			parRateListMap.get(parID).add(sectRateArray[s]);
		}
		
		// convert to primitive array
		Map<Integer, double[]> parRateArrayMap = Maps.newHashMap();
		for(int id:parRateListMap.keySet()) {
			double[] array = parRateListMap.get(id).stream()
                    .mapToDouble(Double::doubleValue).toArray();
			parRateArrayMap.put(id, array);
		}
		
		return parRateArrayMap;
	}



	
	private static void populateProbListCum(double mag, double prob, List<List<Double>> probsList, EvenlyDiscretizedFunc xVals) {
		// we want to find the smallest mag in the function where rupMag >= mag
		if (mag < xVals.getMinX())
			return;
		int magIndex = xVals.getClosestXIndex(mag);
		// closest could be above, check for that and correct
		if (mag < xVals.getX(magIndex))
			magIndex--;
		Preconditions.checkState(magIndex >= 0);
		for (int m=0; m<=magIndex && m<xVals.size(); m++)
			probsList.get(m).add(prob);
	}
	
	private static void populateProbListIncr(double mag, double prob, List<List<Double>> probsList, EvenlyDiscretizedFunc xVals) {
		int magIndex = xVals.getClosestXIndex(mag);
		Preconditions.checkState(magIndex >= 0);
		if(mag>xVals.getMinX()-xVals.getDelta()/2 && mag<xVals.getMaxX()+xVals.getDelta()/2) // make sure it's not outside first and last bin
		probsList.get(magIndex).add(prob);
	}

	
	private static void calcSummedProbs(List<List<Double>> probsList, EvenlyDiscretizedFunc result) {
		// now sum the probabilities as:
		// totProb = 1 - (1 - prob1)*(1 - prob2)*...*(1 - probN)
		for (int i=0; i<result.size(); i++) {
			List<Double> probs = probsList.get(i);
			double totProb = calcSummedProbs(probs);
			result.set(i, totProb);
//			System.out.println("\tM "+result.getX(i)+"+ Prob: "+(float)(totProb*100d)+" %");
		}
	}
	
	public static double calcSummedProbs(double... probs) {
		double totOneMinus = 1;
		for (double prob : probs) {
			totOneMinus *= (1-prob);
		}
		double totProb = 1 - totOneMinus;
		
		return totProb;
	}
	
	public static double calcSummedProbs(List<Double> probs) {
		double totOneMinus = 1;
		for (double prob : probs) {
			totOneMinus *= (1-prob);
		}
		double totProb = 1 - totOneMinus;
		
		return totProb;
	}

	


	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
