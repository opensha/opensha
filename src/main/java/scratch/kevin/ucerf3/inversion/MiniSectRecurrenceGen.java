package scratch.kevin.ucerf3.inversion;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.dom4j.DocumentException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.utils.DeformationModelFileParser;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.DeformationModelFileParser.DeformationSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class MiniSectRecurrenceGen {

	/**
	 * @param args
	 * @throws DocumentException 
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException, DocumentException {
		File file = new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/" +
				"2013_01_14-stampede_3p2_production_runs_combined_FM3_1_MEAN_BRANCH_AVG_SOL.zip");
		
		FaultSystemSolution sol = FaultSystemIO.loadSol(file);
		Map<Integer, DeformationSection> origDM =
				DeformationModelFileParser.load(DeformationModels.GEOLOGIC.getDataFileURL(FaultModels.FM3_1));
		
		
		
		
		writeRates(new File("/tmp/mini_sect_branch_avg.csv"), origDM, calcMinisectionParticRates(origDM, sol, 6.7d, true));
		
		
	}
	
	public static Map<Integer, List<Double>> calcMinisectionParticRates(
			Map<Integer, DeformationSection> dm, FaultSystemSolution sol, double minMag, boolean ri) {
		Map<Integer, List<List<Integer>>> mappings =
				buildSubSectMappings(dm, sol.getRupSet().getFaultSectionDataList());
		return calcMinisectionParticRates(sol, mappings, minMag, ri);
	}
	
	public static Map<Integer, List<Double>> calcMinisectionParticRates(
			FaultSystemSolution sol, Map<Integer, List<List<Integer>>> mappings, double minMag, boolean ri) {
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		Map<Integer, List<Double>> ratesMap = Maps.newHashMap();
		
		for (Integer parentID : mappings.keySet()) {
			List<Double> rates = Lists.newArrayList();
			
			for (List<Integer> subSects : mappings.get(parentID)) {
				// this avoids duplicates
				HashSet<Integer> rupsSet = new HashSet<Integer>();
				
				for (int subSect : subSects)
					for (int rupID : rupSet.getRupturesForSection(subSect))
						if (rupSet.getMagForRup(rupID) >= minMag)
							rupsSet.add(rupID);
				
//				Preconditions.checkState(!rupsSet.isEmpty(), "No rups for minisection: "
//						+DeformationModelFileParser.getMinisectionString(parentID, rates.size()+1));
				
				double rate = 0d;
				for (int rupID : rupsSet)
					rate += sol.getRateForRup(rupID);
				if (ri)
					rate = 1d / rate;
				rates.add(rate);
			}
			ratesMap.put(parentID, rates);
		}
		
		return ratesMap;
	}
	
	public static void writeRates(File file, Map<Integer, DeformationSection> dm,
			Map<Integer, List<Double>> rates) throws IOException {
		
		Map<Integer, List<Double>> rakesBackup = replaceRakes(dm, rates);
		DeformationModelFileParser.write(dm, file);
		replaceRakes(dm, rakesBackup);
	}
	
	private static Map<Integer, List<Double>> replaceRakes(Map<Integer, DeformationSection> dm,
			Map<Integer, List<Double>> replacement) {
		Map<Integer, List<Double>> backupMap = Maps.newHashMap();
		for (Integer parentID : dm.keySet()) {
			List<Double> backupList = Lists.newArrayList();
			backupMap.put(parentID, backupList);
			List<Double> ratesList = replacement.get(parentID);
			Preconditions.checkNotNull(ratesList);
			
			DeformationSection sect = dm.get(parentID);
			Preconditions.checkNotNull(sect);
			
			for (int mini=0; mini<sect.getRakes().size(); mini++) {
				backupList.add(sect.getRakes().get(mini));
				sect.getRakes().set(mini, ratesList.get(mini));
			}
		}
		return backupMap;
	}
	
	public static Map<Integer, List<List<Integer>>> buildSubSectMappings(
			Map<Integer, DeformationSection> origDM,
			List<FaultSectionPrefData> subSectsList) {
		Map<Integer, List<FaultSectionPrefData>> sectsMap = Maps.newHashMap();
		for (FaultSectionPrefData sect : subSectsList) {
			Integer parentID = sect.getParentSectionId();
			List<FaultSectionPrefData> sects = sectsMap.get(parentID);
			if (sects == null) {
				sects = Lists.newArrayList();
				sectsMap.put(parentID, sects);
			}
			sects.add(sect);
		}
		
		// make sure parents are consistent
		subSectsList = Lists.newArrayList(subSectsList);
		for (Integer parentID : sectsMap.keySet()) {
			if (!origDM.containsKey(parentID)) {
				for (FaultSectionPrefData sect : sectsMap.get(parentID))
					subSectsList.remove(sect);
			}
		}
		
		Map<Integer, List<List<Integer>>> mappings = Maps.newHashMap();
		
		HashSet<Integer> mappedSectsSet = new HashSet<Integer>();
		
		for (Integer parentID : origDM.keySet()) {
			List<List<Integer>> mappingsLists = Lists.newArrayList();
			mappings.put(parentID, mappingsLists);
			
			List<FaultSectionPrefData> sects = sectsMap.get(parentID);
			
//			FaultTrace trace = sect.getFaultTrace();
			
			DeformationSection dmSect = origDM.get(parentID);
			LocationList trace = dmSect.getLocsAsTrace();
			
			for (int mini=0; mini<dmSect.getSlips().size(); mini++) {
				List<Integer> miniMappings = Lists.newArrayList();
				mappingsLists.add(miniMappings);
				
				Location dmStart = dmSect.getLocs1().get(mini);
				Location dmEnd = dmSect.getLocs2().get(mini);
				
//				List<Double> lengths = Lists.newArrayList();
//				List<Double> rates = Lists.newArrayList();
				
				for (FaultSectionPrefData sect : sects) {
					Location sectStart = sect.getFaultTrace().get(0);
					Location sectEnd = sect.getFaultTrace().get(sect.getFaultTrace().size()-1);
					
					boolean startBefore = isBefore(dmStart, dmEnd, sectStart);
					boolean startAfter = isAfter(dmStart, dmEnd, sectStart);
//					boolean startBetween = !startBefore && !startAfter;
					
					boolean endBefore = isBefore(dmStart, dmEnd, sectEnd);
					boolean endAfter = isAfter(dmStart, dmEnd, sectEnd);
//					boolean endBetween = !endBefore && !endAfter;
					
					if (startAfter)
						continue;
					if (endBefore)
						continue;
					
					miniMappings.add(sect.getSectionId());
					mappedSectsSet.add(sect.getSectionId());
					
//					double lenContained;
//					
//					if (startBetween && endBetween) {
//						// sect is completely contained in mini
//						lenContained = sect.getFaultTrace().getTraceLength();
//					} else if (startBefore && endAfter) {
//						// mini is completely contained in sect
//						lenContained = 0;
//						for (int i=1; i<trace.size(); i++)
//							lenContained += LocationUtils.horzDistance(trace.get(i-1), trace.get(i));
//					} else if (startBefore && endBetween) {
//						int firstBetweenIndex = -1;
//						for (int i=0; i<sect.getFaultTrace().size(); i++) {
//							Location loc = sect.getFaultTrace().get(i);
//							if (isBetween(dmStart, dmEnd, loc)) {
//								firstBetweenIndex = i;
//								break;
//							}
//						}
//						Preconditions.checkState(firstBetweenIndex > 0);
//						lenContained = 0;
//						for (int i=firstBetweenIndex; i<sect.getFaultTrace().size(); i++) {
//							Location prevLoc;
//							if (i == firstBetweenIndex)
//								prevLoc = dmStart;
//							else
//								prevLoc = sect.getFaultTrace().get(i-1);
//							Location loc = sect.getFaultTrace().get(i);
//							lenContained += LocationUtils.horzDistance(prevLoc, loc);
//						}
//					} else if (startBetween && endAfter) {
//						int firstAfterIndex = -1;
//						for (int i=0; i<sect.getFaultTrace().size(); i++) {
//							Location loc = sect.getFaultTrace().get(i);
//							if (isAfter(dmStart, dmEnd, loc)) {
//								firstAfterIndex = i;
//								break;
//							}
//						}
//						Preconditions.checkState(firstAfterIndex > 0);
//						lenContained = 0;
//						for (int i=0; i<firstAfterIndex; i++) {
//							Location loc = sect.getFaultTrace().get(i);
//							Location afterLoc;
//							if (i+1 == firstAfterIndex)
//								afterLoc = dmEnd;
//							else
//								afterLoc = sect.getFaultTrace().get(i+1);
//							lenContained += LocationUtils.horzDistance(loc, afterLoc);
//						}
//					} else {
//						throw new IllegalStateException("Shouldn't get here...");
//					}
//					
//					lengths.add(lenContained);
////					double particRate = sol.calcParticRateForSect(sect.getSectionId(), 6.7d, 10d);
//					Preconditions.checkState(lenContained > 0, "Invalid length contained: "+lenContained);
//					Preconditions.checkState(particRate >= 0, "Invalid partic rate: "+particRate);
//					rates.add(particRate);
				}
				
				Preconditions.checkState(!miniMappings.isEmpty(), "No mappings found!!!");
				
//				double avgRate = DeformationModelFetcher.calcLengthBasedAverage(lengths, rates);
//				double avgRP = 1d/avgRate;
//				
//				dmSect.getRakes().set(mini, avgRP);
			}
		}
		
		Preconditions.checkState(mappedSectsSet.size() == subSectsList.size(),
				"Not all sub sects mapped! Total: "+subSectsList.size()
				+", Mapped: "+mappedSectsSet.size());
		
		return mappings;
	}
	
	/**
	 * Determines if the given point, pt, is before or equal to the start point. This is
	 * done by determining that pt is closer to start than end, and is further from end
	 * than start is.
	 * 
	 * @param start
	 * @param end
	 * @param pt
	 * @return
	 */
	private static boolean isBefore(Location start, Location end, Location pt) {
		if (start.equals(pt) || LocationUtils.areSimilar(start, pt))
			return true;
		double pt_start_dist = LocationUtils.linearDistanceFast(pt, start);
		if (pt_start_dist == 0)
			return true;
		double pt_end_dist = LocationUtils.linearDistanceFast(pt, end);
		double start_end_dist = LocationUtils.linearDistanceFast(start, end);

		return pt_start_dist < pt_end_dist && pt_end_dist > start_end_dist;
	}

	/**
	 * Determines if the given point, pt, is after or equal to the end point. This is
	 * done by determining that pt is closer to end than start, and is further from start
	 * than end is.
	 * 
	 * @param start
	 * @param end
	 * @param pt
	 * @return
	 */
	private static boolean isAfter(Location start, Location end, Location pt) {
		if (end.equals(pt) || LocationUtils.areSimilar(end, pt))
			return true;
		double pt_end_dist = LocationUtils.linearDistanceFast(pt, end);
		if (pt_end_dist == 0)
			return true;
		double pt_start_dist = LocationUtils.linearDistanceFast(pt, start);
		double start_end_dist = LocationUtils.linearDistanceFast(start, end);

		return pt_end_dist < pt_start_dist && pt_start_dist > start_end_dist;
	}
	
	private static boolean isBetween(Location start, Location end, Location pt) {
		return !isBefore(start, end, pt) && !isAfter(start, end, pt);
	}

}
