package scratch.UCERF3.inversion.ruptures;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.opensha.commons.util.ClassUtils;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester.TestType;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRuptureBuilder.UniqueRupture;
import scratch.UCERF3.inversion.ruptures.plausibility.PlausibilityFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.CoulombJunctionFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.CumulativeAzimuthChangeFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.CumulativeRakeChangeFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.MinSectsPerParentFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.TotalAzimuthChangeFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.JumpAzimuthChangeFilter.AzimuthCalc;
import scratch.UCERF3.inversion.ruptures.strategies.ClusterConnectionStrategy;
import scratch.UCERF3.inversion.ruptures.strategies.DistCutoffSingleConnectionClusterConnectionStrategy;
import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;
import scratch.UCERF3.utils.FaultSystemIO;

public class DebugClusterRuptureBuild {

	public static void main(String[] args) throws ZipException, IOException, DocumentException {
		FaultSystemRupSet u3RupSet = FaultSystemIO.loadRupSet(new File(
				"/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/"
				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		FaultSystemRupSet clusterRupSet = FaultSystemIO.loadRupSet(new File("/tmp/test_rup_set.zip"));
		boolean debugNewInclusions = false;
		boolean debugExclusions = true;
		boolean stopAfterFound = true;
		
		FaultModels fm = FaultModels.FM3_1;
		List<FaultSection> parentSects = fm.fetchFaultSections();
		List<? extends FaultSection> subSects = clusterRupSet.getFaultSectionDataList();
		ClusterConnectionStrategy connectionStrategy = new DistCutoffSingleConnectionClusterConnectionStrategy(5d);
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		File cacheFile = new File("/tmp/dist_az_cache_"+fm.encodeChoiceString()+"_"+subSects.size()
			+"_sects_"+parentSects.size()+"_parents.csv");
		if (cacheFile.exists()) {
			System.out.println("Loading dist/az cache from "+cacheFile.getAbsolutePath());
			distAzCalc.loadCacheFile(cacheFile);
		}
		List<FaultSubsectionCluster> clusters = ClusterRuptureBuilder.buildClusters(subSects, connectionStrategy, distAzCalc);
		Preconditions.checkState(subSects.size() == clusterRupSet.getNumSections());
		Preconditions.checkState(subSects.size() == u3RupSet.getNumSections());
		for (int i=0; i<subSects.size(); i++) {
			Preconditions.checkState(subSects.get(i).getSectionName().equals(
					clusterRupSet.getFaultSectionData(i).getSectionName()));
			Preconditions.checkState(subSects.get(i).getSectionName().equals(
					u3RupSet.getFaultSectionData(i).getSectionName()));
		}
		System.out.println("passed all section consistency tests");
		
		List<PlausibilityFilter> filters = new ArrayList<>();
		AzimuthCalc u3AzCalc = new JumpAzimuthChangeFilter.UCERF3LeftLateralFlipAzimuthCalc(distAzCalc);
		filters.add(new JumpAzimuthChangeFilter(u3AzCalc, 60f));
		filters.add(new TotalAzimuthChangeFilter(u3AzCalc, 60f, true));
		filters.add(new CumulativeAzimuthChangeFilter(
				new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc), 560f));
		filters.add(new CumulativeRakeChangeFilter(180f));
		filters.add(new MinSectsPerParentFilter(2, true, clusters));
		CoulombRates coulombRates = CoulombRates.loadUCERF3CoulombRates(fm);
		CoulombRatesTester coulombTester = new CoulombRatesTester(
				TestType.COULOMB_STRESS, 0.04, 0.04, 1.25d, true, true);
		filters.add(new CoulombJunctionFilter(coulombTester, coulombRates));
		
		HashSet<UniqueRupture> u3Uniques = new HashSet<>();
		for (int r=0; r<u3RupSet.getNumRuptures(); r++)
			u3Uniques.add(new UniqueRupture(u3RupSet.getSectionsIndicesForRup(r)));
		
		HashSet<UniqueRupture> clusterUniques = new HashSet<>();
		for (int r=0; r<clusterRupSet.getNumRuptures(); r++)
			clusterUniques.add(new UniqueRupture(clusterRupSet.getSectionsIndicesForRup(r)));
		
		if (debugExclusions) {
			System.out.println("searching for members of the U3 rup set which are excluded in the cluster set");
			int numFound = 0;
			for (int o=0; o<u3RupSet.getNumRuptures(); o++) {
				List<Integer> sectIndexes = u3RupSet.getSectionsIndicesForRup(o);
				UniqueRupture oUnique = new UniqueRupture(sectIndexes);
				if (!clusterUniques.contains(oUnique)) {
					numFound++;
					List<FaultSection> sects = u3RupSet.getFaultSectionDataForRupture(o);
					ClusterRupture clusterRup = ClusterRupture.forOrderedSingleStrandRupture(sects, distAzCalc);
					System.out.println("Found an excluded rupture set at index "+o);
					List<Integer> parents = new ArrayList<>();
					for (FaultSubsectionCluster cluster : clusterRup.clusters)
						parents.add(cluster.parentSectionID);
					System.out.println("\tParents: "+Joiner.on(",").join(parents));
					System.out.println(clusterRup);
					for (PlausibilityFilter filter : filters) {
						PlausibilityResult result = filter.apply(clusterRup, true);
						System.out.println(ClassUtils.getClassNameWithoutPackage(filter.getClass())+": "+result);
					}
					// prune from the end first
					LinkedList<Integer> testIDs = new LinkedList<>(sectIndexes);
					testIDs.removeLast();
					while (!testIDs.isEmpty()) {
						UniqueRupture testUnique = new UniqueRupture(testIDs);
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
						UniqueRupture testUnique = new UniqueRupture(testIDs);
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
			System.out.println("Found "+numFound+" exclusions");
		}
		
//		if (debugNewInclusions) {
//			System.out.println("searching for members of the U3 rup set which are excluded in the cluster set");
//			int numFound = 0;
//			for (int o=0; o<u3RupSet.getNumRuptures(); o++) {
//				List<Integer> sectIndexes = u3RupSet.getSectionsIndicesForRup(o);
//				UniqueRupture oUnique = new UniqueRupture(sectIndexes);
//				if (!clusterUniques.contains(oUnique)) {
//					numFound++;
//					List<FaultSection> sects = u3RupSet.getFaultSectionDataForRupture(o);
//					ClusterRupture clusterRup = ClusterRupture.forOrderedSingleStrandRupture(sects, distAzCalc);
//					System.out.println("Found an excluded rupture set at index "+o);
//					List<Integer> parents = new ArrayList<>();
//					for (FaultSubsectionCluster cluster : clusterRup.clusters)
//						parents.add(cluster.parentSectionID);
//					System.out.println("\tParents: "+Joiner.on(",").join(parents));
//					System.out.println(clusterRup);
//					for (PlausibilityFilter filter : filters) {
//						PlausibilityResult result = filter.apply(clusterRup, true);
//						System.out.println(ClassUtils.getClassNameWithoutPackage(filter.getClass())+": "+result);
//					}
//					// prune from the end first
//					LinkedList<Integer> testIDs = new LinkedList<>(sectIndexes);
//					testIDs.removeLast();
//					while (!testIDs.isEmpty()) {
//						UniqueRupture testUnique = new UniqueRupture(testIDs);
//						if (clusterUniques.contains(testUnique)) {
//							System.out.println("Found a subset which is included (pruned from end):");
//							List<FaultSection> testSects = new ArrayList<>();
//							for (int i=0; i<testIDs.size(); i++)
//								testSects.add(subSects.get(testIDs.get(i)));
//							System.out.println(ClusterRupture.forOrderedSingleStrandRupture(testSects, distAzCalc));
//							break;
//						}
//						testIDs.removeLast();
//					}
//					// prune from the start
//					testIDs = new LinkedList<>(sectIndexes);
//					testIDs.removeFirst();
//					while (!testIDs.isEmpty()) {
//						UniqueRupture testUnique = new UniqueRupture(testIDs);
//						if (clusterUniques.contains(testUnique)) {
//							System.out.println("Found a subset which is included (pruned from start):");
//							List<FaultSection> testSects = new ArrayList<>();
//							for (int i=0; i<testIDs.size(); i++)
//								testSects.add(subSects.get(testIDs.get(i)));
//							System.out.println(ClusterRupture.forOrderedSingleStrandRupture(testSects, distAzCalc));
//							break;
//						}
//						testIDs.removeFirst();
//					}
//					
//					if (stopAfterFound)
//						break;
//				}
//			}
//			System.out.println("Found "+numFound+" exclusions");
//		}
	}

}
