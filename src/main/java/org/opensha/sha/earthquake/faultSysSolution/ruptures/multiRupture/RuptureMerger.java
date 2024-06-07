package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import com.google.common.base.Preconditions;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl.MultiRuptureFractCoulombPositiveFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupCartoonGenerator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCache;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class RuptureMerger {

	final boolean VERBOSE = true;
	List<MultiRuptureCompatibilityFilter> compatibilityFilters = new ArrayList<>();

	public void addFilter(MultiRuptureCompatibilityFilter filter) {
		compatibilityFilters.add(filter);
	}

	transient int mergeCounter = 0;

	/**
	 * Create new ruptures that combine the nucleation rupture and any of the target ruptures if they
	 * are compatible.
	 * It should be possible to use the resulting ruptures as new nucleation ruptures in order to
	 * create more complex ruptures. But we need to implement avoiding duplicate sections beforehand.
	 *
	 * @param nucleation A rupture that we want to combine with other ruptures
	 * @param targets    a list of ruptures that are potential jump targets from the nucleation rupture.
	 * @return a list of merged ruptures.
	 */
	public List<ClusterRupture> merge(ClusterRupture nucleation, List<ClusterRupture> targets) {
		List<ClusterRupture> result = new ArrayList<>();

		for (ClusterRupture target : targets) {
			MultiRuptureCompatibilityResult compatibility = MultiRuptureCompatibilityResult.PASS;
			for (MultiRuptureCompatibilityFilter filter : compatibilityFilters) {
				compatibility = compatibility.and(filter.apply(compatibility, nucleation, target, VERBOSE));
				if (!compatibility.canContinue()) {
					break;
				}
			}
			if (compatibility.isPass()) {
				Preconditions.checkState(compatibility.jump != null);
				result.add(MultiClusterRupture.takeSplayJump(compatibility.jump));
			}
		}

		int counter = mergeCounter++;
		if (counter % 10 == 0) {
			System.out.print('.');
		}
		return result;
	}

	public List<ClusterRupture> merge(List<ClusterRupture> nucleations, List<ClusterRupture> targets) {
		return nucleations
				.parallelStream()
				.map(nucleation -> merge(nucleation, targets))
				.flatMap(Collection::stream)
				.collect(Collectors.toList());
	}

	public static void main(String[] args) throws IOException {

		// load ruptures and split them up into crustal and subduction
		//        FaultSystemRupSet rupSet = FaultSystemRupSet.load(
		//                new File("C:\\Users\\user\\GNS\\nzshm-opensha\\TEST\\ruptures\\rupset-disjointed.zip"));
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File("/home/kevin/Downloads/catalogue.zip"));
		List<ClusterRupture> nucleationRuptures = new ArrayList<>();
		List<ClusterRupture> targetRuptures = new ArrayList<>();
		ClusterRuptures cRups = rupSet.getModule(ClusterRuptures.class);
		if (cRups == null)
			cRups = ClusterRuptures.singleStranged(rupSet);
		for (ClusterRupture rupture : cRups) {
			if (rupture.clusters[0].subSects.get(0).getSectionName().contains("row:")) {
				nucleationRuptures.add(rupture);
			} else {
				targetRuptures.add(rupture);
			}
		}

		System.out.println("Loaded " + nucleationRuptures.size() + " nucleation ruptures");
		System.out.println("Loaded " + targetRuptures.size() + " target ruptures");

		// set up RuptureMerger
		RuptureMerger merger = new RuptureMerger();
		SectionDistanceAzimuthCalculator distCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
		RuptureJumpDistFilter distFilter = new RuptureJumpDistFilter(10, distCalc);
		merger.addFilter(distFilter);

		// Coulomb filter
		// stiffness grid spacing, increase if it's taking too long
		double stiffGridSpacing = 1d;
		// stiffness calculation constants
		double lameLambda = 3e4;
		double lameMu = 3e4;
		double coeffOfFriction = 0.5;
		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
				rupSet.getFaultSectionDataList(), stiffGridSpacing, lameLambda, lameMu, coeffOfFriction, PatchAlignment.FILL_OVERLAP, 1d);
		AggregatedStiffnessCache stiffnessCache = stiffnessCalc.getAggregationCache(StiffnessType.CFF);

		File cacheDir = new File("/tmp");
		File stiffnessCacheFile = null;
		int stiffnessCacheSize = 0;
		if (cacheDir != null && cacheDir.exists()) {
			stiffnessCacheFile = new File(cacheDir, stiffnessCache.getCacheFileName());
			stiffnessCacheSize = 0;
			if (stiffnessCacheFile.exists()) {
				try {
					stiffnessCacheSize = stiffnessCache.loadCacheFile(stiffnessCacheFile);
				} catch (IOException e) {
					System.err.println("WARNING: exception loading previous cache");
					e.printStackTrace();
				}
			}
		}

		// what fraction of interactions should be positive? this number will take some tuning
		float threshold = 0.8f;
		MultiRuptureFractCoulombPositiveFilter filter = new MultiRuptureFractCoulombPositiveFilter(stiffnessCalc, threshold);
		merger.addFilter(filter);

		// run RuptureMerger for one nucleation rupture for now
//		ClusterRupture testNucleation = cRups.get(97653);
//		List<ClusterRupture> mergedRuptures = merger.merge(testNucleation, targetRuptures);

		// all nucleation
//		List<ClusterRupture> mergedRuptures = merger.merge(nucleationRuptures, targetRuptures);
		// custom nucleation
		List<ClusterRupture> testNucleationRuptures = List.of(
				new ClusterRupture(new FaultSubsectionCluster(
						List.of(rupSet.getFaultSectionData(2)))),
				new ClusterRupture(new FaultSubsectionCluster(
						List.of(rupSet.getFaultSectionData(2), rupSet.getFaultSectionData(3)))),
				new ClusterRupture(new FaultSubsectionCluster(
						List.of(rupSet.getFaultSectionData(2), rupSet.getFaultSectionData(3), rupSet.getFaultSectionData(4)))),
				new ClusterRupture(new FaultSubsectionCluster(
						List.of(rupSet.getFaultSectionData(3)))),
				new ClusterRupture(new FaultSubsectionCluster(
						List.of(rupSet.getFaultSectionData(3), rupSet.getFaultSectionData(4)))),
				new ClusterRupture(new FaultSubsectionCluster(
						List.of(rupSet.getFaultSectionData(4))))
				);
		List<ClusterRupture> mergedRuptures = merger.merge(testNucleationRuptures, targetRuptures);


		//        List<ClusterRupture> shortList = nucleationRuptures.stream()
		//                .filter(rupture -> rupture.clusters[0].subSects.get(0).getFaultTrace().first().lat > -42 &&
		//                                rupture.clusters[0].subSects.get(0).getFaultTrace().first().lat < -40.5 &&
		//                                rupture.clusters[0].subSects.get(0).getFaultTrace().last().lat > -42 &&
		//                                rupture.clusters[0].subSects.get(0).getFaultTrace().last().lat < -40.5)
		//                .filter(rupture -> rupture.buildOrderedSectionList().size() > 5)
		//                .collect(Collectors.toList())
		//                .subList(0, 50);
		//        List<ClusterRupture> mergedRuptures = merger.merge(shortList, targetRuptures);

		System.out.println("Generated " + mergedRuptures.size() + " ruptures.");// write out the cache to make future calculations faster

		if (stiffnessCacheFile != null
				&& stiffnessCacheSize < stiffnessCache.calcCacheSize()) {
			System.out.println("Writing stiffness cache to "+stiffnessCacheFile.getAbsolutePath());
			try {
				stiffnessCache.writeCacheFile(stiffnessCacheFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("DONE writing stiffness cache");
		}

		// write only the merged ruptures
		FaultSystemRupSet resultRupSet =
				FaultSystemRupSet.builderForClusterRups(
						rupSet.getFaultSectionDataList(),
						mergedRuptures)
				// magnitudes will be wrong, but this is required
				.forScalingRelationship(ScalingRelationships.MEAN_UCERF3)
				.build();
		resultRupSet.write(new File("/tmp/mergedRupset.zip"));

		// quick sanity check
		RupCartoonGenerator.plotRupture(new File("/tmp/"), "mergedRupture", mergedRuptures.get(0), "merged rupture", false, true);
	}
}
