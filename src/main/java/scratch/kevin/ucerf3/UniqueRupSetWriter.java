package scratch.kevin.ucerf3;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.DocumentException;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.utils.FaultSystemIO;

public class UniqueRupSetWriter {

	public static void main(String[] args) throws IOException, DocumentException {
		File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
		File solFile = new File(rupSetsDir, "rsqsim_4983_stitched_m6.5_skip65000_sectArea0.5.zip");
		File outFile = new File(rupSetsDir, "rsqsim_4983_stitched_m6.5_skip65000_sectArea0.5_unique.zip");
		
		FaultSystemSolution sol = FaultSystemIO.loadSol(solFile);
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		List<UniqueRupture> uniques = new ArrayList<>();
		Map<UniqueRupture, Integer> uniqueIndexes = new HashMap<>();
		List<List<Integer>> uniqueMappings = new ArrayList<>();
		
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			UniqueRupture unique = UniqueRupture.forIDs(rupSet.getSectionsIndicesForRup(r));
			uniques.add(unique);
			
			Integer uniqueIndex = uniqueIndexes.get(unique);
			if (uniqueIndex == null) {
				uniqueIndex = uniqueIndexes.size();
				uniqueIndexes.put(unique, uniqueIndex);
				uniqueMappings.add(new ArrayList<>());
			}
			uniqueMappings.get(uniqueIndex).add(r);
		}
		
		System.out.println("Found "+uniqueIndexes.size()+" unique ruptures");
		
		double[] mags = new double[uniqueMappings.size()];
		double[] rakes = new double[uniqueMappings.size()];
		double[] rupAreas = new double[uniqueMappings.size()];
//		double[] rupLengths = new double[uniqueMappings.size()];
		double[] rupLengths = null;
		double[] rates = new double[uniqueMappings.size()];
		
		List<List<Integer>> sectsList = new ArrayList<>();
		
		for (int r=0; r<mags.length; r++) {
			List<Integer> myIndexes = uniqueMappings.get(r);
			
			List<Double> weights = new ArrayList<>();
			List<Double> myMags = new ArrayList<>();
			List<Double> myRakes = new ArrayList<>();
			List<Double> myAreas = new ArrayList<>();
//			List<Double> myLengths = new ArrayList<>();
			double sumRate = 0d;
			
			Preconditions.checkState(myIndexes.size() > 0);
			
			for (int index : myIndexes) {
				double rate = sol.getRateForRup(index);
				weights.add(rate);
				myMags.add(rupSet.getMagForRup(index));
				myRakes.add(rupSet.getAveRakeForRup(index));
				myAreas.add(rupSet.getAreaForRup(index));
//				myLengths.add(rupSet.getLengthForRup(index));
				sumRate += rate;
			}
			
			mags[r] = weightedAvg(myMags, weights);
			rupAreas[r] = weightedAvg(myAreas, weights);
//			rupLengths[r] = weightedAvg(myLengths, weights);
			rakes[r] = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(weights, myRakes));
			rates[r] = sumRate;
			sectsList.add(rupSet.getSectionsIndicesForRup(myIndexes.get(0)));
		}
		
		
		FaultSystemRupSet modRupSet = new FaultSystemRupSet(rupSet.getFaultSectionDataList(),
				rupSet.getSlipRateForAllSections(), null, rupSet.getAreaForAllSections(),
				sectsList, mags, rakes, rupAreas, rupLengths, rupSet.getInfoString());
		FaultSystemSolution modSol = new FaultSystemSolution(modRupSet, rates);
		FaultSystemIO.writeSol(modSol, outFile);
	}
	
	private static double weightedAvg(List<Double> values, List<Double> weights) {
		double ret = 0d;
		double sumWeight = 0d;
		for (int i=0; i<values.size(); i++) {
			ret += values.get(i)*weights.get(i);
			sumWeight += weights.get(i);
		}
		return ret/sumWeight;
	}

}
