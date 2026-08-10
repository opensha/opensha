package org.opensha.sha.earthquake.rupForecastImpl.nshm23.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeLevel.RandomLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.RandomlyGeneratedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.SamplingMethod;
import org.opensha.sha.earthquake.faultSysSolution.modules.PosteriorSectionBValueDistributions;
import org.opensha.sha.earthquake.faultSysSolution.mpj.HPCConfig;
import org.opensha.sha.earthquake.faultSysSolution.mpj.HazardConfig;
import org.opensha.sha.earthquake.faultSysSolution.mpj.InversionConfig;
import org.opensha.sha.earthquake.faultSysSolution.mpj.LogicTreeConfig;
import org.opensha.sha.earthquake.faultSysSolution.mpj.MPJ_LogicTreeInversionScriptWriter;
import org.opensha.sha.earthquake.faultSysSolution.mpj.PostProcessConfig;
import org.opensha.sha.earthquake.faultSysSolution.mpj.RunConfig;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_PaleoUncertainties;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SectionSupraSeisBValues;
import org.opensha.sha.util.NEHRP_TestCity;

public class NSHM23_InversionScriptWriter {

	private static final double HAZARD_GRID_SPACING = 0.1;

	public static void main(String[] args) throws IOException {
		HPCConfig.HPCSite hpcSite = HPCConfig.HPCSite.USC_CARC_FMPJ;

		File localMainDir = new File("/home/kevin/OpenSHA/fss_inversions");
		File remoteMainDir = new File("/project2/scec_608/kmilner/fss_inversions");
		
		List<String> nameAdds = new ArrayList<>();
		
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = NSHM23_LogicTreeBranch.levelsOnFault;
		List<RandomLevel<?,?>> randomLevels = new ArrayList<>();
		int samplingBranchCountMultiplier = 1;
		
		levels = new ArrayList<>(levels);
		for (int l=levels.size(); --l>=0;) {
			LogicTreeLevel<? extends LogicTreeNode> level = levels.get(l);
			if (SectionSupraSeisBValues.class.isAssignableFrom(level.getType())
					|| NSHM23_PaleoUncertainties.class.isAssignableFrom(level.getType()))
				levels.remove(l);
		}
//		nameAdds.add("bPosterior10x");
//		randomLevels.add(new PosteriorSectionBValueDistributions.UniformSamplingLevel(
//				"Section posterior b-value samples", "Posterior-b samples"));
//		samplingBranchCountMultiplier = 10;
		nameAdds.add("bUniform10x");
		randomLevels.add(new SectionSupraSeisBValues.DistributionSamplingLevel(
				"Section b-value samples", "b-value samples", NSHM23_InvConfigFactory.SUPRA_B_PRIOR_DIST));
		samplingBranchCountMultiplier = 10;

		GriddedRegion hazardRegion = new GriddedRegion(
				NSHM23_RegionLoader.loadFullConterminousWUS(), HAZARD_GRID_SPACING, GriddedRegion.ANCHOR_0_0);

		RunConfig run = RunConfig.builder()
				.baseName("nshm23_branches")
				.addNameToken(NSHM23_FaultModels.WUS_FM_v3.name())
//				.addNameToken("gridded_rebuild")
				.addNameTokens(nameAdds)
				.build();

		HPCConfig hpc = HPCConfig.builder(hpcSite)
				.localMainDir(localMainDir)
				.remoteMainDir(remoteMainDir)
				.build();

		LogicTreeConfig logicTreeConfig = LogicTreeConfig.builder()
				.forLogicTreeLevels(levels)
				.addRandomLevels(randomLevels)
				.samplingMethod(SamplingMethod.LATIN_HYPERCUBE)
				.samplingBranchCountMultiplier(samplingBranchCountMultiplier)
				.requiredNodes(NSHM23_FaultModels.WUS_FM_v3)
				.forceRequiredNonZeroWeight(true)
				.sortBy(NSHM23_SegmentationModels.class)
				.build();

		InversionConfig inversion = InversionConfig.builder()
				.factoryClass(NSHM23_InvConfigFactory.class)
				.estimateWallTimeMinutes(600000d, 2000, 200000d)
				.parallelBranchAverage(true)
				.build();

		HazardConfig hazard = HazardConfig.builder()
				.backgroundOption(IncludeBackgroundOption.EXCLUDE)
				.region(hazardRegion)
				.sites(loadWUSHazardSites())
				.build();

		PostProcessConfig postProcess = PostProcessConfig.builder()
				.writeTrueMean(true)
				.writeNodeBranchAverages(true)
				.nodeBAAsyncThreads(2)
				.nodeBASkipSectBySect(true)
				.gridSourcePostProcess(PostProcessConfig.GridSourceConfig.builder().build())
				.build();

		MPJ_LogicTreeInversionScriptWriter.Request request = MPJ_LogicTreeInversionScriptWriter.Request.builder()
				.run(run)
				.hpc(hpc)
				.logicTree(logicTreeConfig)
				.inversion(inversion)
				.hazard(hazard)
				.postProcess(postProcess)
				.build();

		new MPJ_LogicTreeInversionScriptWriter().writeScripts(request);
	}

	private static List<Site> loadWUSHazardSites() throws IOException {
		Region region = NSHM23_RegionLoader.loadFullConterminousWUS();
		List<Site> sites = new ArrayList<>();
		for (NEHRP_TestCity city : NEHRP_TestCity.values()) {
			if (region.contains(city.location()))
				sites.add(new Site(city.location(), city.toString()));
		}
		return sites;
	}
}
