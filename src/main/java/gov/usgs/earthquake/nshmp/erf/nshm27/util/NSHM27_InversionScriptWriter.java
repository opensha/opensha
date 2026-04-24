package gov.usgs.earthquake.nshmp.erf.nshm27.util;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeLevel.SamplingMethod;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.imr.AttenRelRef;

import gov.usgs.earthquake.nshmp.erf.mpj.HPCConfig;
import gov.usgs.earthquake.nshmp.erf.mpj.HPCSite;
import gov.usgs.earthquake.nshmp.erf.mpj.HazardConfig;
import gov.usgs.earthquake.nshmp.erf.mpj.InversionConfig;
import gov.usgs.earthquake.nshmp.erf.mpj.InversionScriptWriteRequest;
import gov.usgs.earthquake.nshmp.erf.mpj.LogicTreeConfig;
import gov.usgs.earthquake.nshmp.erf.mpj.MPJ_LogicTreeInversionScriptWriter;
import gov.usgs.earthquake.nshmp.erf.mpj.PostProcessConfig;
import gov.usgs.earthquake.nshmp.erf.mpj.RunConfig;
import gov.usgs.earthquake.nshmp.erf.nshm27.NSHM27_InvConfigFactory;
import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM27_LogicTree;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_MapRegions;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;

public class NSHM27_InversionScriptWriter {
	
	private static SamplingMethod SAMPLING_METHOD_DEFAULT = SamplingMethod.MONTE_CARLO;
	private static double GRID_SPACING_DEFAULT = 0.1;
	
	private static Options buildOptions() {
		Options ops = new Options();
		
		ops.addOption(FaultSysTools.helpOption());
		HPCConfig.addOptions(ops);
		HazardConfig.addOptions(ops);
		
		ops.addRequiredOption(null, "region", true, "NSHM27 region, one of: "+FaultSysTools.enumOptions(NSHM27_SeismicityRegions.class));
		
		ops.addOption(null, "sampling-method", true, "Sampling method, one of: "
				+FaultSysTools.enumOptions(SamplingMethod.class)+"; Default: "+SAMPLING_METHOD_DEFAULT.name());
		ops.addRequiredOption(null, "samples", true, "Number of logic tree samples.");
		ops.addOption(null, "unique-seed", false, "Flag to use a unique seed from this calculation rather than the "
				+ "deterministic seed based on the region and sample count/method.");
		
		return ops;
	}

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			// for Kevin's convenience within eclipse
			System.err.println("No command line arguments, using hardcoded defaults; use --help to see available options instead.");
			
			args = new String[] {
					"--region", NSHM27_SeismicityRegions.AMSAM.name(),
					
					"--hpc-site", HPCSite.USC_CARC_FMPJ.name(),
					
					"--local-dir", "/home/kevin/OpenSHA/fss_inversions",
					"--remote-dir", "/project2/scec_608/kmilner/fss_inversions",
					
					"--sampling-method", SamplingMethod.MONTE_CARLO.name(),
					"--samples", "5000",
//					"--unique-seed",
			};
		}
		
		CommandLine cmd = FaultSysTools.parseOptions(buildOptions(), args, NSHM27_InversionScriptWriter.class);
		
		NSHM27_SeismicityRegions seisReg = NSHM27_SeismicityRegions.valueOf(cmd.getOptionValue("region"));
		int numBranchSamples = Integer.parseInt(cmd.getOptionValue("samples"));
		boolean deterministicSeed = !cmd.hasOption("unique-seed");
		SamplingMethod samplingMethod = cmd.hasOption("sampling-method") ?
				SamplingMethod.valueOf(cmd.getOptionValue("sampling-method")) : SAMPLING_METHOD_DEFAULT;
		double gridSpacing = cmd.hasOption("hazard-grid-spacing") ?
				Double.parseDouble(cmd.getOptionValue("hazard-grid-spacing")) : GRID_SPACING_DEFAULT;

		LogicTree<LogicTreeNode> logicTree = NSHM27_LogicTree.buildMultiRegimeTree(
				seisReg, numBranchSamples, deterministicSeed, samplingMethod);
		LogicTree<LogicTreeNode> analysisTree = LogicTree.applyBinning(LogicTree.unrollTRTs(logicTree));

		GriddedRegion hazardRegion = new GriddedRegion(
				NSHM27_MapRegions.valueOf(seisReg.name()).load(), gridSpacing, GriddedRegion.ANCHOR_0_0);

		RunConfig run = RunConfig.builder()
				.baseName("nshm27")
				.addNameToken(seisReg.name())
				.addNameToken(numBranchSamples+"samples")
				.addNameToken(samplingMethod.getFilePrefix())
				.addNameToken(deterministicSeed ? null : "unique_seed")
				.build();

		HPCConfig hpc = HPCConfig.builder(cmd)
				.build();

		LogicTreeConfig logicTreeConfig = LogicTreeConfig.builder()
				.forSuppliedLogicTree(logicTree, analysisTree)
				.build();

		InversionConfig inversion = InversionConfig.builder()
				.factoryClass(NSHM27_InvConfigFactory.class)
				.estimateWallTimeMinutes(200000d, 2000, 200000d)
				.parallelBranchAverage(true)
				.build();

		System.err.println("WARNING: still using PRVI25 GMMs until NSHM27-specific models are available");
		HazardConfig hazard = HazardConfig.builder()
				// set our defaults before cmd
				.backgroundOption(IncludeBackgroundOption.INCLUDE)
				.region(hazardRegion)
				.sigmaTruncation(3d)
				.gmpe(AttenRelRef.USGS_PRVI_ACTIVE)
				.gmpe(AttenRelRef.USGS_PRVI_SLAB)
				.gmpe(AttenRelRef.USGS_PRVI_INTERFACE)
				// now allow cmd overrides
				.forCMD(cmd)
				.build();

		PostProcessConfig postProcess = PostProcessConfig.builder()
				.writeTrueMean(true)
				.writeNodeBranchAverages(true)
				.nodeBAAsyncThreads(2)
				.nodeBASkipSectBySect(false)
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
