package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.U3CoulombJunctionFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpCumulativeRakeChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.MinSectsPerParentFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.TotalAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.U3CompatibleCumulativeRakeChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter.AzimuthCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.UCERF3SectionConnectionStrategy;
import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester.TestType;
import scratch.UCERF3.inversion.laughTest.AbstractPlausibilityFilter;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.laughTest.UCERF3PlausibilityConfig;
import scratch.UCERF3.utils.FaultSystemIO;

public class CompareClusterRuptureBuild {

	public static void main(String[] args) throws ZipException, IOException, DocumentException {
		File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
//		FaultSystemRupSet compRupSet = FaultSystemIO.loadRupSet(new File(
//				"/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/"
//				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		FaultSystemRupSet compRupSet = FaultSystemIO.loadRupSet(new File(rupSetsDir, "fm3_2_ucerf3.zip"));
		boolean isCompUCERF3 = true;
//		FaultSystemRupSet compRupSet = FaultSystemIO.loadRupSet(new File(
//				"/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_cmlAz_cmlRake_cffClusterPositive.zip"));
//		boolean isCompUCERF3 = false;
		
		System.out.println("compRupSet has "+compRupSet.getNumRuptures()+" ruptures");
		
		FaultModels fm = FaultModels.FM3_2;
		FaultSystemRupSet clusterRupSet = FaultSystemIO.loadRupSet(
//				new File("/tmp/test_rup_set.zip"));
				new File("/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_2_reproduce_ucerf3.zip"));
		System.out.println("clusterRupSet has "+clusterRupSet.getNumRuptures()+" ruptures");
		
		boolean debugExclusions = true;
		boolean debugNewInclusions = true;
		boolean stopAfterFound = true;
		
		List<? extends FaultSection> subSects = clusterRupSet.getFaultSectionDataList();
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		File cacheFile = new File(rupSetsDir, fm.encodeChoiceString().toLowerCase()
				+"_dist_az_cache.csv");
		if (cacheFile.exists()) {
			System.out.println("Loading dist/az cache from "+cacheFile.getAbsolutePath());
			distAzCalc.loadCacheFile(cacheFile);
		}
		ClusterConnectionStrategy connectionStrategy = new DistCutoffClosestSectClusterConnectionStrategy(
				subSects, distAzCalc, 5d);
		Preconditions.checkState(subSects.size() == clusterRupSet.getNumSections());
		Preconditions.checkState(subSects.size() == compRupSet.getNumSections());
		for (int i=0; i<subSects.size(); i++) {
			Preconditions.checkState(subSects.get(i).getSectionName().equals(
					clusterRupSet.getFaultSectionData(i).getSectionName()));
			Preconditions.checkState(subSects.get(i).getSectionName().equals(
					compRupSet.getFaultSectionData(i).getSectionName()));
		}
		System.out.println("passed all section consistency tests");
		
		System.out.println("Looking for duplicates");
		Map<HashSet<Integer>, Integer> prevRups = new HashMap<>();
		for (int i=0; i<clusterRupSet.getNumRuptures(); i++) {
			HashSet<Integer> idsSet = new HashSet<>(clusterRupSet.getSectionsIndicesForRup(i));
//			Preconditions.checkState(!prevRups.containsKey(idsSet));
			if (prevRups.containsKey(idsSet)) {
				int prevID = prevRups.get(idsSet);
				System.out.println("DUPLICATE FOUND");
				System.out.println("\t"+prevID+": "+Joiner.on(",").join(
						clusterRupSet.getSectionsIndicesForRup(prevID)));
				System.out.println("\t"+i+": "+Joiner.on(",").join(
						clusterRupSet.getSectionsIndicesForRup(i)));
			}
			prevRups.put(idsSet, i);
		}
		
		List<PlausibilityFilter> filters;
		List<AbstractPlausibilityFilter> u3Filters = null;
		if (isCompUCERF3) {
			filters = new ArrayList<>();
			AzimuthCalc u3AzCalc = new JumpAzimuthChangeFilter.UCERF3LeftLateralFlipAzimuthCalc(distAzCalc);
			filters.add(new JumpAzimuthChangeFilter(u3AzCalc, 60f));
			filters.add(new TotalAzimuthChangeFilter(u3AzCalc, 60f, true, true));
			filters.add(new CumulativeAzimuthChangeFilter(
					new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc), 560f));
//			filters.add(new CumulativeRakeChangeFilter(180f));
//			filters.add(new JumpCumulativeRakeChangeFilter(180f));
			filters.add(new U3CompatibleCumulativeRakeChangeFilter(180d));
			filters.add(new MinSectsPerParentFilter(2, true, true, connectionStrategy));
			CoulombRates coulombRates = CoulombRates.loadUCERF3CoulombRates(fm);
			CoulombRatesTester coulombTester = new CoulombRatesTester(
					TestType.COULOMB_STRESS, 0.04, 0.04, 1.25d, true, true);
			filters.add(new U3CoulombJunctionFilter(coulombTester, coulombRates));
			
			System.out.println("Building UCERF3 original plausibility filters...");
			UCERF3PlausibilityConfig u3Config = UCERF3PlausibilityConfig.getDefault();
//			Map<IDPairing, Double> distances = new HashMap<>(distAzCalc.getCachedDistances());
//			Map<IDPairing, Double> azimuths = new HashMap<>(distAzCalc.getCachedAzimuths());
			Map<IDPairing, Double> distances = new HashMap<>();
			Map<IDPairing, Double> azimuths = new HashMap<>();
			for (int id1=0; id1<subSects.size(); id1++) {
				for (int id2=0; id2<subSects.size(); id2++) {
					if (distAzCalc.isDistanceCached(id1, id2)) {
						IDPairing pair = new IDPairing(id1, id2);
						double dist = distAzCalc.getDistance(id1, id2);
						distances.put(pair, dist);
						distances.put(pair.getReversed(), dist);
						azimuths.put(pair, distAzCalc.getAzimuth(id1, id2));
						azimuths.put(pair.getReversed(), distAzCalc.getAzimuth(id2, id1));
					}
				}
			}
			for (int i=1; i<subSects.size(); i++) {
				azimuths.put(new IDPairing(i-1, i), distAzCalc.getAzimuth(i-1, i));
				azimuths.put(new IDPairing(i, i-1), distAzCalc.getAzimuth(i, i-1));
			}
			List<List<Integer>> sectionConnectionsListList = UCERF3SectionConnectionStrategy.computeCloseSubSectionsListList(
					subSects, distances, 5d, coulombRates);
			u3Config.setCoulombRates(coulombRates);
			u3Filters = u3Config.buildPlausibilityFilters(
					azimuths, distances, sectionConnectionsListList, subSects);
		} else {
			filters = clusterRupSet.getPlausibilityConfiguration().getFilters();
//			clusterRupSet.getPlausibilityConfiguration().getDistAzCalc().
			// TODO: copy cache?
		}
		
		
		HashSet<UniqueRupture> u3Uniques = new HashSet<>();
		for (int r=0; r<compRupSet.getNumRuptures(); r++)
			u3Uniques.add(UniqueRupture.forIDs(compRupSet.getSectionsIndicesForRup(r)));
		
		HashSet<UniqueRupture> clusterUniques = new HashSet<>();
		for (int r=0; r<clusterRupSet.getNumRuptures(); r++)
			clusterUniques.add(UniqueRupture.forIDs(clusterRupSet.getSectionsIndicesForRup(r)));
		
		if (debugExclusions) {
			System.out.println("searching for members of the reference rup set which are excluded in the cluster set");
			int numFound = 0;
			for (int o=0; o<compRupSet.getNumRuptures(); o++) {
				List<Integer> sectIndexes = compRupSet.getSectionsIndicesForRup(o);
				UniqueRupture oUnique = UniqueRupture.forIDs(sectIndexes);
				if (!clusterUniques.contains(oUnique)) {
					numFound++;
					List<FaultSection> sects = compRupSet.getFaultSectionDataForRupture(o);
					ClusterRupture clusterRup = ClusterRupture.forOrderedSingleStrandRupture(sects, distAzCalc);
					System.out.println("Found an excluded rupture set at index "+o);
					List<Integer> parents = new ArrayList<>();
					for (FaultSubsectionCluster cluster : clusterRup.clusters)
						parents.add(cluster.parentSectionID);
					System.out.println("\tParents: "+Joiner.on(",").join(parents));
					System.out.println(clusterRup);
					PlausibilityResult netResult = PlausibilityResult.PASS;
					for (PlausibilityFilter filter : filters) {
						PlausibilityResult result = filter.apply(clusterRup, true);
						System.out.println(filter.getShortName()+": "+result);
						netResult = netResult.logicalAnd(result);
					}
					System.out.println("Net result: "+netResult);
					
					if (isCompUCERF3) {
						System.out.println("Applying original filters:");
						for (AbstractPlausibilityFilter filter : u3Filters) {
							PlausibilityResult result = filter.apply(sects);
							System.out.println(filter.getShortName()+": "+result);
						}
					}
					// prune from the end first
					LinkedList<Integer> testIDs = new LinkedList<>(sectIndexes);
					testIDs.removeLast();
					while (!testIDs.isEmpty()) {
						UniqueRupture testUnique = UniqueRupture.forIDs(testIDs);
						if (clusterUniques.contains(testUnique)) {
							System.out.println("Found a subset which is included (pruned from end):");
							List<FaultSection> testSects = new ArrayList<>();
							for (int i=0; i<testIDs.size(); i++)
								testSects.add(subSects.get(testIDs.get(i)));
							System.out.println(ClusterRupture.forOrderedSingleStrandRupture(testSects, distAzCalc));
							break;
						}
						testIDs.removeLast();
					}
					// prune from the start
					testIDs = new LinkedList<>(sectIndexes);
					testIDs.removeFirst();
					while (!testIDs.isEmpty()) {
						UniqueRupture testUnique = UniqueRupture.forIDs(testIDs);
						if (clusterUniques.contains(testUnique)) {
							System.out.println("Found a subset which is included (pruned from start):");
							List<FaultSection> testSects = new ArrayList<>();
							for (int i=0; i<testIDs.size(); i++)
								testSects.add(subSects.get(testIDs.get(i)));
							System.out.println(ClusterRupture.forOrderedSingleStrandRupture(testSects, distAzCalc));
							break;
						}
						testIDs.removeFirst();
					}
					
					if (stopAfterFound)
						break;
				}
			}
			if (numFound == 0 || !stopAfterFound)
				System.out.println("Found "+numFound+" exlusions");
			else if (numFound > 0)
				System.out.println("Stopping after first exlusion match");
		}
		
		if (debugNewInclusions) {
			System.out.println("searching for cluster ruptures which are not members of the reference rup set");
			int numFound = 0;
			for (int c=0; c<clusterRupSet.getNumRuptures(); c++) {
				List<Integer> sectIndexes = clusterRupSet.getSectionsIndicesForRup(c);
				UniqueRupture oUnique = UniqueRupture.forIDs(sectIndexes);
				if (!u3Uniques.contains(oUnique)) {
					numFound++;
					List<FaultSection> sects = clusterRupSet.getFaultSectionDataForRupture(c);
					ClusterRupture clusterRup = ClusterRupture.forOrderedSingleStrandRupture(sects, distAzCalc);
					System.out.println("Found a newly included rupture at index "+c);
					List<Integer> parents = new ArrayList<>();
					for (FaultSubsectionCluster cluster : clusterRup.clusters)
						parents.add(cluster.parentSectionID);
					System.out.println("\tParents: "+Joiner.on(",").join(parents));
					System.out.println(clusterRup);
					System.out.println("Applying cluster filters:");
					for (PlausibilityFilter filter : filters) {
						PlausibilityResult result = filter.apply(clusterRup, true);
						System.out.println(filter.getShortName()+": "+result);
					}
					if (isCompUCERF3) {
						System.out.println("Applying original filters:");
						for (AbstractPlausibilityFilter filter : u3Filters) {
							PlausibilityResult result = filter.apply(sects);
							System.out.println(filter.getShortName()+": "+result);
						}
					}
					
					if (stopAfterFound)
						break;
				}
			}
			if (numFound == 0 || !stopAfterFound)
				System.out.println("Found "+numFound+" inclusions");
			else if (numFound > 0)
				System.out.println("Stopping after first inclusion match");
		}
	}

}
