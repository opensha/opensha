package org.opensha.sha.earthquake.rupForecastImpl.nshm23.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.util.NEHRP_TestCity;

import gov.usgs.earthquake.nshmp.erf.mpj.GridSourcePostProcessConfig;
import gov.usgs.earthquake.nshmp.erf.mpj.HPCConfig;
import gov.usgs.earthquake.nshmp.erf.mpj.HPCSite;
import gov.usgs.earthquake.nshmp.erf.mpj.HazardConfig;
import gov.usgs.earthquake.nshmp.erf.mpj.InversionConfig;
import gov.usgs.earthquake.nshmp.erf.mpj.InversionScriptWriteRequest;
import gov.usgs.earthquake.nshmp.erf.mpj.LogicTreeConfig;
import gov.usgs.earthquake.nshmp.erf.mpj.MPJ_LogicTreeInversionScriptWriter;
import gov.usgs.earthquake.nshmp.erf.mpj.PostProcessConfig;
import gov.usgs.earthquake.nshmp.erf.mpj.RunConfig;

public class NSHM23_InversionScriptWriter {

	private static final double HAZARD_GRID_SPACING = 0.1;

	public static void main(String[] args) throws IOException {
		HPCSite hpcSite = HPCSite.USC_CARC_FMPJ;

		File localMainDir = new File("/home/kevin/OpenSHA/fss_inversions");
		File remoteMainDir = new File("/project2/scec_608/kmilner/fss_inversions");

		GriddedRegion hazardRegion = new GriddedRegion(
				NSHM23_RegionLoader.loadFullConterminousWUS(), HAZARD_GRID_SPACING, GriddedRegion.ANCHOR_0_0);

		RunConfig run = RunConfig.builder()
				.baseName("nshm23_branches")
				.addNameToken(NSHM23_FaultModels.WUS_FM_v3.name())
//				.addNameToken("gridded_rebuild")
				.build();

		HPCConfig hpc = HPCConfig.builder(hpcSite)
				.localMainDir(localMainDir)
				.remoteMainDir(remoteMainDir)
				.build();

		LogicTreeConfig logicTreeConfig = LogicTreeConfig.builder()
				.forLogicTreeLevels(NSHM23_LogicTreeBranch.levelsOnFault)
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
				.gridSourcePostProcess(GridSourcePostProcessConfig.builder().build())
				.build();

		InversionScriptWriteRequest request = InversionScriptWriteRequest.builder()
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
