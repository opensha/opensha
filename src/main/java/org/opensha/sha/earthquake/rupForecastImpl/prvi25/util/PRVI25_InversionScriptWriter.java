package org.opensha.sha.earthquake.rupForecastImpl.prvi25.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.PRVI25_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalDeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalRandomlySampledDeformationModelLevel;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_LogicTree;
import org.opensha.sha.imr.AttenRelRef;

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

public class PRVI25_InversionScriptWriter {

	public static void main(String[] args) throws IOException {
		HPCSite hpcSite = HPCSite.USC_CARC_FMPJ;

		File localMainDir = new File("/home/kevin/OpenSHA/fss_inversions");
		File remoteMainDir = new File("/project2/scec_608/kmilner/fss_inversions");

		boolean crustal = true;
		
		GriddedRegion hazardRegion = new GriddedRegion(
				PRVI25_RegionLoader.loadPRVI_MapExtents(), 0.025, GriddedRegion.ANCHOR_0_0);
		List<Site> hazardSites = PRVI25_RegionLoader.loadHazardSites();
		
		RunConfig run;
		LogicTreeConfig logicTreeConfig;
		HazardConfig hazard;
		if (crustal) {
			List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>(PRVI25_LogicTree.levelsOnFault);
			int origNumLevels = levels.size();
			for (int i=levels.size(); --i>=0;)
				if (levels.get(i).getNodes().get(0) instanceof PRVI25_CrustalDeformationModels)
					levels.remove(i);
			if (levels.size() != origNumLevels - 1)
				throw new IllegalStateException("Expected to replace one crustal deformation model level");
			
			int samplingBranchCountMultiplier = 10;
			long randomSeed = 12345678L * samplingBranchCountMultiplier;

			run = RunConfig.builder()
					.baseName("prvi25_crustal_branches")
					.addNameToken("dmSample")
					.addNameToken(samplingBranchCountMultiplier+"x")
					.build();
			
			logicTreeConfig = LogicTreeConfig.builder()
					.forLogicTreeLevels(levels)
					.addRandomLevel(new PRVI25_CrustalRandomlySampledDeformationModelLevel())
					.samplingBranchCountMultiplier(samplingBranchCountMultiplier)
					.randomSeed(randomSeed)
					.build();
			
			hazard = HazardConfig.builder()
					.backgroundOption(IncludeBackgroundOption.EXCLUDE)
					.region(hazardRegion)
					.sigmaTruncation(3d)
					.gmpe(AttenRelRef.USGS_PRVI_ACTIVE)
					.sites(hazardSites)
					.build();
		} else {
			run = RunConfig.builder()
					.baseName("prvi25_subduction_branches")
					.build();
			
			logicTreeConfig = LogicTreeConfig.builder()
					.forLogicTreeLevels(PRVI25_LogicTree.levelsSubduction)
					.build();
			
			hazard = HazardConfig.builder()
					.backgroundOption(IncludeBackgroundOption.EXCLUDE)
					.region(hazardRegion)
					.sigmaTruncation(3d)
					.gmpe(AttenRelRef.USGS_PRVI_INTERFACE)
					.gmpe(AttenRelRef.USGS_PRVI_SLAB)
					.sites(hazardSites)
					.build();
		}
		
		HPCConfig hpc = HPCConfig.builder(hpcSite)
				.localMainDir(localMainDir)
				.remoteMainDir(remoteMainDir)
				.build();

		InversionConfig inversion = InversionConfig.builder()
				.factoryClass(PRVI25_InvConfigFactory.class)
				.estimateWallTimeMinutes(crustal ? 50000d : 10000d, 2000, 200000d)
				.parallelBranchAverage(true)
				.build();

		PostProcessConfig postProcess = PostProcessConfig.builder()
				.writeTrueMean(true)
				.writeNodeBranchAverages(true)
				.nodeBAAsyncThreads(2)
				.nodeBASkipSectBySect(false)
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
}
