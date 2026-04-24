package scratch.kevin.nshm23;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.hpc.JavaShellScriptWriter;
import org.opensha.commons.hpc.mpj.FastMPJShellScriptWriter;
import org.opensha.commons.hpc.mpj.MPJExpressShellScriptWriter;
import org.opensha.commons.hpc.mpj.NoMPJSingleNodeShellScriptWriter;
import org.opensha.commons.hpc.pbs.BatchScriptWriter;
import org.opensha.commons.hpc.pbs.StampedeScriptWriter;
import org.opensha.commons.hpc.pbs.USC_CARC_ScriptWriter;
import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.RandomlyGeneratedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.SamplingMethod;
import org.opensha.commons.logicTree.LogicTreeNode.RandomlyGeneratedNode;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ClassUtils;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.hazard.FaultAndGriddedSeparateTreeHazardCombiner;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_LogicTreeHazardCalc;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_SiteLogicTreeHazardCurveCalc;
import org.opensha.sha.earthquake.faultSysSolution.inversion.ClusterSpecificInversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.GridSourceProviderFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.mpj.AbstractAsyncLogicTreeWriter;
import org.opensha.sha.earthquake.faultSysSolution.inversion.mpj.MPJ_GridSeisBranchBuilder;
import org.opensha.sha.earthquake.faultSysSolution.inversion.mpj.MPJ_LogicTreeBranchAverageBuilder;
import org.opensha.sha.earthquake.faultSysSolution.inversion.mpj.MPJ_LogicTreeInversionRunner;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.TimeCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.CoolingScheduleType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen.PlotLevel;
import org.opensha.sha.earthquake.faultSysSolution.util.TrueMeanSolutionCreator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.DistDependSegShift;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.MaxJumpDistModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_PaleoUncertainties;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SingleStates;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SlipAlongRuptureModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_U3_HybridLogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.RupsThroughCreepingSect;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.RupturePlausibilityModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationMFD_Adjustment;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationModelBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.ShawSegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSectConstraintModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSeisMoRateReductions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.U3_UncertAddDeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.random.RandomBValSampler;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.random.RandomSegModelSampler;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.prior2018.NSHM18_DeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.prior2018.NSHM18_FaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.prior2018.NSHM18_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_LogicTree;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionFaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.PRVI25_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalDeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalFaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalRandomlySampledDeformationModelLevel;
import org.opensha.sha.util.NEHRP_TestCity;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;
import gov.usgs.earthquake.nshmp.erf.nshm27.NSHM27_InvConfigFactory;
import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM27_LogicTree;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_MapRegions;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.inversion.U3InversionConfigFactory;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.logicTree.U3LogicTreeBranchNode;
import scratch.kevin.nshm23.devinSlipRateTests.DevinModDeformationModels;
import scratch.kevin.nshm23.devinSlipRateTests.TaperOverrideSlipAlongRuptureModels;
import scratch.kevin.nshm23.dmCovarianceTests.DefModSamplingEnabledInvConfig;
import scratch.kevin.nshm23.dmCovarianceTests.RandomDefModSampleLevel;

public class MPJ_LogicTreeInversionRunnerScriptWriter {
	
	public static void main(String[] args) throws IOException {
		File localMainDir = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions");
		
		List<String> extraArgs = new ArrayList<>();
		
		boolean strictSeg = false;
		double segTransMaxDist = 3d;
		boolean hazardGridded = false;
		boolean nodeBAskipSectBySect = true;
		boolean forceRequiredNonzeroWeight = false;
		Double forceHazardGridSpacing = null;
		GriddedRegion forceHazardReg = null;
		long randSeed = 12345678l;
		boolean parallelBA = false;
		
		Double vs30 = null;
		Double sigmaTrunc = null;
		boolean supersample = false;
		double[] periods = null;
		
		File remoteMainDir = new File("/project2/scec_608/kmilner/fss_inversions");
		int remoteTotalThreads = 20;
		int remoteInversionsPerBundle = 1;
		int remoteTotalMemGB = 50;
		String queue = "scec";
		int nodes = 36;
//		int nodes = 18;
		double itersPerSec = 200000;
		int runsPerBranch = 1;
		int nodeBAAsyncThreads = 2;
//		JavaShellScriptWriter mpjWrite = new MPJExpressShellScriptWriter(
//				USC_CARC_ScriptWriter.JAVA_BIN, remoteTotalMemGB*1024, null, USC_CARC_ScriptWriter.MPJ_HOME);
		JavaShellScriptWriter mpjWrite = new FastMPJShellScriptWriter(
				USC_CARC_ScriptWriter.JAVA_BIN, remoteTotalMemGB*1024, null, USC_CARC_ScriptWriter.FMPJ_HOME);
//		JavaShellScriptWriter mpjWrite = new NoMPJSingleNodeShellScriptWriter(USC_CARC_ScriptWriter.JAVA_BIN,
//				remoteTotalMemGB*1024, null); nodes = 1; remoteInversionsPerBundle = 2;
		BatchScriptWriter pbsWrite = new USC_CARC_ScriptWriter();
		
//		File remoteMainDir = new File("/work/00950/kevinm/stampede2/nshm23/batch_inversions");
//		int remoteTotalThreads = 48;
//		int remoteInversionsPerBundle = 3;
//		int remoteTotalMemGB = 100;
//		String queue = "skx-normal";
//		int nodes = 128;
//		double itersPerSec = 300000;
//		int runsPerBranch = 1;
//		int nodeBAAsyncThreads = 4;
////		String queue = "skx-dev";
////		int nodes = 4;
//		JavaShellScriptWriter mpjWrite = new FastMPJShellScriptWriter(
//				StampedeScriptWriter.JAVA_BIN, remoteTotalMemGB*1024, null, StampedeScriptWriter.FMPJ_HOME);
//		BatchScriptWriter pbsWrite = new StampedeScriptWriter();
		
		AttenRelRef[] gmpes = null;
		
		List<RandomlyGeneratedLevel<?>> individualRandomLevels = new ArrayList<>();
		int samplingBranchCountMultiplier = 1;
		LogicTree<LogicTreeNode> customTree = null;
		LogicTree<LogicTreeNode> analysisTree = null;

		String dirName = new SimpleDateFormat("yyyy_MM_dd").format(new Date());
//		String dirName = "2026_03_27";
		String dirSuffix = null;
		
		/*
		 * UCERF3 logic tree
		 */
//		List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>(U3LogicTreeBranch.getLogicTreeLevels());
//		
////		levels = new ArrayList<>(levels);
////		int origSize = levels.size();
////		for (int i=levels.size(); --i>=0;)
////			if (levels.get(i).getType().isAssignableFrom(ScalingRelationships.class))
////				levels.remove(i);
////		Preconditions.checkState(levels.size() < origSize);
////		levels.add(NSHM23_LogicTreeBranch.SCALE);
////		dirName += "-new_scale_rels";
//		
//		dirName += "-u3_branches";
//		
//		levels = new ArrayList<>(levels);
//		levels.add(NSHM23_LogicTreeBranch.SEG);
//		dirName += "-new_seg";
//		
//		Class<? extends InversionConfigurationFactory> factoryClass = U3InversionConfigFactory.class;
//		int avgNumRups = 250000;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = U3InversionConfigFactory.ScaleLowerDepth1p3.class;
////		int avgNumRups = 250000;
////		dirName += "-scaleLowerDepth1.3";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = U3InversionConfigFactory.ForceNewPaleo.class;
////		int avgNumRups = 250000;
////		dirName += "-new_paleo";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = U3InversionConfigFactory.OriginalCalcParamsNewAvgConverged.class;
////		dirName += "-orig_calc_params-new_avg-converged";
////		int avgNumRups = 250000;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = U3InversionConfigFactory.OriginalCalcParamsNewAvgNoWaterLevel.class;
////		dirName += "-orig_calc_params-new_avg-converged-noWL";
////		int avgNumRups = 250000;
//		
////		dirName += "-new_perturb";
////		extraArgs.add("--perturb "+GenerationFunctionType.VARIABLE_EXPONENTIAL_SCALE.name());
//		
////		dirName += "-try_zero_often";
////		extraArgs.add("--non-negativity "+NonnegativityConstraintType.TRY_ZERO_RATES_OFTEN.name());
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = U3InversionConfigFactory.ThinnedRupSet.class;
////		int avgNumRups = 150000;
////		dirName += "-thinned_0.1";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = U3InversionConfigFactory.OriginalCalcParams.class;
////		dirName += "-orig_calc_params";
////		int avgNumRups = 250000;
////		remoteInversionsPerBundle = 4;
////		runsPerBranch = 10;
////		String completionArg = null;
////		int invMins = 30;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = U3InversionConfigFactory.OriginalCalcParamsNewAvg.class;
////		dirName += "-orig_calc_params-new_avg";
////		int avgNumRups = 250000;
////		String completionArg = null;
////		int invMins = 30;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = U3InversionConfigFactory.NoPaleoParkfieldSingleReg.class;
////		dirName += "-no_paleo-no_parkfield-single_mfd_reg";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = U3InversionConfigFactory.CoulombRupSet.class;
////		dirName += "-coulomb";
////		int avgNumRups = 325000;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = U3InversionConfigFactory.CoulombBilateralRupSet.class;
////		dirName += "-coulomb-bilateral";
////		int avgNumRups = 500000;
//		
////		U3LogicTreeBranchNode<?>[] required = { FaultModels.FM3_1, DeformationModels.GEOLOGIC,
////				ScalingRelationships.SHAW_2009_MOD, TotalMag5Rate.RATE_7p9 };
////		U3LogicTreeBranchNode<?>[] required = { FaultModels.FM3_1, DeformationModels.ZENGBB,
////				ScalingRelationships.SHAW_2009_MOD };
//		U3LogicTreeBranchNode<?>[] required = { FaultModels.FM3_1 };
////		U3LogicTreeBranchNode<?>[] required = {  };
//		Class<? extends LogicTreeNode> sortBy = null;
		/*
		 * END UCERF3 logic tree
		 */

		/*
		 * NSHM23 logic tree
		 * TODO (this is a just a marker to find this part quickly, not an actual todo)
		 */
////		List<LogicTreeLevel<? extends LogicTreeNode>> levels = NSHM23_U3_HybridLogicTreeBranch.levels;
////		dirName += "-nshm23_u3_hybrid_branches";
////		double avgNumRups = 325000;
//		
//		List<LogicTreeLevel<? extends LogicTreeNode>> levels = NSHM23_LogicTreeBranch.levelsOnFault;
//		dirName += "-nshm23_branches";
//		double avgNumRups = 600000;
//		
//		dirSuffix = "-gridded_rebuild";
//		
////		List<LogicTreeLevel<? extends LogicTreeNode>> levels = NSHM18_LogicTreeBranch.levels;
////		dirName += "-nshm18_branches-wc_94";
////		double avgNumRups = 500000;
//		
////		List<LogicTreeLevel<? extends LogicTreeNode>> levels = NSHM18_LogicTreeBranch.levelsNewScale;
////		dirName += "-nshm18_branches-new_scale";
////		double avgNumRups = 500000;
//		
////		levels = new ArrayList<>(levels);
////		for (int i=levels.size(); --i>=0;)
////			if (levels.get(i).getType().isAssignableFrom(ShawSegmentationModels.class)
////					|| levels.get(i).getType().isAssignableFrom(NSHM23_SegmentationModels.class)
////					|| levels.get(i).getType().isAssignableFrom(SegmentationMFD_Adjustment.class)
////					|| levels.get(i).getType().isAssignableFrom(DistDependSegShift.class))
////				levels.remove(i);
////		dirName += "-no_seg";
//////		levels.add(NSHM23_LogicTreeBranch.RUPS_THROUGH_CREEPING);
//////		dirName += "-creep_branches";
//////		levels.add(NSHM23_LogicTreeBranch.MAX_DIST);
//////		dirName += "-strict_cutoff_seg"; strictSeg = true;
//		
//		
////		dirName += "-pre_zero_slip_parent_fix";
////		dirName += "-reweight_seg_2_3_4";
//		
////		levels = new ArrayList<>(levels);
////		int origSize = levels.size();
////		for (int i=levels.size(); --i>=0;)
////			if (levels.get(i).getType().isAssignableFrom(ScalingRelationships.class))
////				levels.remove(i);
////		Preconditions.checkState(levels.size() < origSize);
////		levels.add(NSHM23_LogicTreeBranch.SCALE);
////		dirName += "-new_scale_rels";
////		dirName += "-full_set";
//		
////		levels = new ArrayList<>(levels);
////		boolean dmReplaced = false;
////		for (int l=levels.size(); --l >= 0;) {
////			LogicTreeLevel<? extends LogicTreeNode> level = levels.get(l);
////			System.out.println("Level "+l+": name='"+level.getName()+"'; type='"+level.getType()+"'");
////			if (NSHM23_DeformationModels.class.isAssignableFrom(level.getType())) {
////				dmReplaced = true;
////				levels.set(l, LogicTreeLevel.forEnum(DevinModDeformationModels.class, "Custom Deformation Model", "CustomDM"));
////			} else if (SlipAlongRuptureModels.class.isAssignableFrom(level.getType())) {
////				levels.remove(l);
////			}
////		}
////		Preconditions.checkState(dmReplaced);
////		levels.add(LogicTreeLevel.forEnum(TaperOverrideSlipAlongRuptureModels.class, "Taper-Override Slip Along Rupture Models", "SlipAlong"));
////		dirName += "-devin_tapered_slip_tests";
//		
//		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.class;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.MFDUncert0p1.class;
////		dirName += "-mfd_uncert_0p1";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.ConstantSlipRateStdDev0p1.class;
////		dirName += "-const_slip_sd_0p1";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.ConstantSlipRateStdDev0p2.class;
////		dirName += "-const_slip_sd_0p2";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.FullSysInv.class;
////		dirName += "-full_sys_inv";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.ClusterSpecific.class;
////		dirName += "-cluster_specific_inversion";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.SegWeight100.class;
////		dirName += "-seg_weight_100";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.SegWeight1000.class;
////		dirName += "-seg_weight_1000";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.SegWeight10000.class;
////		dirName += "-seg_weight_10000";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.HardcodedPrevWeightAdjust.class;
////		dirName += "-no_reweight_use_prev";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.HardcodedPrevWeightAdjustFullSys.class;
////		dirName += "-full_sys_inv-no_reweight_use_prev";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.HardcodedOrigWeights.class;
////		dirName += "-no_reweight_use_orig";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.HardcodedOrigWeightsFullSys.class;
////		dirName += "-full_sys_inv-no_reweight_use_orig";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.HardcodedPrevAvgWeights.class;
////		dirName += "-no_reweight_use_prev_avg";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.HardcodedPrevAvgWeightsFullSys.class;
////		dirName += "-full_sys_inv-no_reweight_use_prev_avg";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.NoPaleoParkfield.class;
////		dirName += "-no_paleo_parkfield";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.NoMFDScaleAdjust.class;
////		dirName += "-no_scale_adj_mfds";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.NoIncompatibleDataAdjust.class;
////		dirName += "-no_mfd_sigma_data_adj";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.ScaleLowerDepth1p3.class;
////		dirName += "-scaleLowerDepth1.3";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.HardcodedPrevAsInitial.class;
////		dirName += "-prev_as_initial";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.NoAvg.class;
////		dirName += "-no_avg";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.ForceNewPaleo.class;
////		dirName += "-new_paleo";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.NewScaleUseOrigWidths.class;
////		dirName += "-use_orig_widths";
//		
//		// also set nonzero weights!
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.ForceWideSegBranches.class;
////		dirName += "-wide_seg_branches";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.ForceNoGhostTransient.class;
////		dirName += "-no_ghost_trans";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.ScaleSurfSlipUseActualWidths.class;
////		dirName += "-surf_slip_use_actual_w";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.RemoveIsolatedFaults.class;
////		dirName += "-remove_isolated_faults";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.RemoveProxyFaults.class;
////		dirName += "-remove_proxy_faults";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.NoPaleoSlip.class;
////		dirName += "-no_paleo_slip";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.PaleoSlipInequality.class;
////		dirName += "-paleo_slip_ineq";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.TenThousandItersPerRup.class;
////		dirName += "-10000ip";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.DM_OriginalWeights.class;
////		dirName += "-dm_orig_weights"; NSHM23_DeformationModels.ORIGINAL_WEIGHTS = true;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.DM_OutlierlMinimizationWeights.class;
////		dirName += "-dm_outlier_minimize_weights"; NSHM23_DeformationModels.ORIGINAL_WEIGHTS = false;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.DM_OutlierReplacementYc2p0.class;
////		dirName += "-dm_outlier_sub_yc_2"; NSHM23_DeformationModels.ORIGINAL_WEIGHTS = true;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.DM_OutlierReplacementYc3p5.class;
////		dirName += "-dm_outlier_sub_yc_3p5"; NSHM23_DeformationModels.ORIGINAL_WEIGHTS = true;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.DM_OutlierReplacementYc5p0.class;
////		dirName += "-dm_outlier_sub_yc_5"; NSHM23_DeformationModels.ORIGINAL_WEIGHTS = true;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.DM_OutlierLogReplacementYc2p0.class;
////		dirName += "-dm_outlier_log_sub_yc_2"; NSHM23_DeformationModels.ORIGINAL_WEIGHTS = true;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.DM_OutlierLogReplacementYc3p5.class;
////		dirName += "-dm_outlier_log_sub_yc_3p5"; NSHM23_DeformationModels.ORIGINAL_WEIGHTS = true;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.DM_OutlierLogReplacementYc5p0.class;
////		dirName += "-dm_outlier_log_sub_yc_5"; NSHM23_DeformationModels.ORIGINAL_WEIGHTS = true;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.SegModelLimitMaxLen.class;
////		dirName += "-seg_limit_max_length";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.SlipRateStdDevCeil0p1.class;
////		dirName += "-slip_rate_sd_ceil_0p1";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.SegModelMaxLen600.class;
////		dirName += "-seg_limit_max_length_600";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.SparseGRDontSpreadSingleToMulti.class;
////		dirName += "-sparse_gr_dont_spread_single_multi";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.ModDepthGV08.class;
////		dirName += "-gv_08_mod_depth";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.OrigDraftScaling.class;
////		dirName += "-orig_draft_scaling";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.ModScalingAdd4p3.class;
////		dirName += "-mod_scaling";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.NSHM18_UseU3Paleo.class;
////		dirName += "-u3_paleo";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = NSHM23_InvConfigFactory.ModPitasPointDDW.class;
////		dirName += "-mod_pitas_ddw";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = DefModSamplingEnabledInvConfig.ConnDistB0p5MidSegCorr.class;
////		dirName += "-dm_sampling";
////		individualRandomLevels.add(new RandomDefModSampleLevel());
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = DefModSamplingEnabledInvConfig.ConnDistB0p5MidSegCorrCapSigma.class;
////		dirName += "-dm_sampling_cap_sigma";
////		individualRandomLevels.add(new RandomDefModSampleLevel());
//		
////		levels = new ArrayList<>(levels);
////		boolean randB = true;
////		boolean randSeg = true;
////		int origSize = levels.size();
////		for (int i=levels.size(); --i>=0;) {
////			if (randB && SupraSeisBValues.class.isAssignableFrom(levels.get(i).getType()))
////				levels.remove(i);
////			if (randSeg && SegmentationModelBranchNode.class.isAssignableFrom(levels.get(i).getType()))
////				levels.remove(i);
////		}
////		Preconditions.checkState(levels.size() < origSize);
////		if (randB) {
////			samplingBranchCountMultiplier *= 5; // there were originally 5 each
////			dirName += "-randB";
////			individualRandomLevels.add(new RandomBValSampler.Level());
////		}
////		if (randSeg) {
////			samplingBranchCountMultiplier *= 5; // there were originally 5 each
////			dirName += "-randSeg";
////			individualRandomLevels.add(new RandomSegModelSampler.Level());
////		}
//		
////		dirName += "-mini_one_fifth";
////		samplingBranchCountMultiplier /= 5;
//		
////		dirName += "-u3_perturb";
////		extraArgs.add("--perturb "+GenerationFunctionType.UNIFORM_0p001.name());
////		dirName += "-exp_perturb";
////		extraArgs.add("--perturb "+GenerationFunctionType.EXPONENTIAL_SCALE.name());
////		dirName += "-limit_zeros";
////		extraArgs.add("--non-negativity "+NonnegativityConstraintType.LIMIT_ZERO_RATES.name());
////		dirName += "-classic_sa";
////		extraArgs.add("--cooling-schedule "+CoolingScheduleType.CLASSICAL_SA.name());
//		
////		levels = new ArrayList<>(levels);
////		levels.add(NSHM23_LogicTreeBranch.SINGLE_STATES);
////		dirName += "-single_state";
//		
////		dirName += "-mod_west_valley_ddw";
//		
////		dirName += "-mod_dm_weights";
//		
//		forceHazardGridSpacing = 0.1;
//		
//		forceRequiredNonzeroWeight = true;
//		LogicTreeNode[] required = {
//				// FAULT MODELS
////				FaultModels.FM3_1,
////				FaultModels.FM3_2,
////				NSHM18_FaultModels.NSHM18_WUS_NoCA,
////				NSHM18_FaultModels.NSHM18_WUS_PlusU3_FM_3p1,
////				NSHM23_FaultModels.FM_v1p4,
////				NSHM23_FaultModels.FM_v2,
//				NSHM23_FaultModels.WUS_FM_v3,
////				PRVI25_FaultModels.PRVI_FM_INITIAL,
//				
////				// SINGLE STATE
////				NSHM23_SingleStates.NM,
////				NSHM23_SingleStates.UT,
//
//				// RUPTURE SETS
////				RupturePlausibilityModels.COULOMB, // default
////				RupturePlausibilityModels.COULOMB_5km,
////				RupturePlausibilityModels.AZIMUTHAL,
////				RupturePlausibilityModels.SEGMENTED,
////				RupturePlausibilityModels.UCERF3,
////				RupturePlausibilityModels.UCERF3_REDUCED,
//				
//				// DEFORMATION MODELS
////				U3_UncertAddDeformationModels.U3_ZENG,
////				U3_UncertAddDeformationModels.U3_MEAN,
////				NSHM18_DeformationModels.BRANCH_AVERAGED,
////				NSHM23_DeformationModels.AVERAGE,
////				NSHM23_DeformationModels.GEOLOGIC,
////				NSHM23_DeformationModels.EVANS,
////				NSHM23_DeformationModels.MEDIAN,
////				DevinModDeformationModels.GEO_AVG_FROM_DEVIN,
////				DevinModDeformationModels.GEO_FROM_DEVIN,
//				
//				// SCALING RELATIONSHIPS
////				ScalingRelationships.SHAW_2009_MOD,
////				ScalingRelationships.MEAN_UCERF3,
////				NSHM23_ScalingRelationships.AVERAGE,
////				NSHM23_ScalingRelationships.LOGA_C4p2_SQRT_LEN,
////				NSHM23_ScalingRelationships.WIDTH_LIMITED_CSD,
//				
//				// SLIP ALONG RUPTURE
////				NSHM23_SlipAlongRuptureModels.UNIFORM,
////				NSHM23_SlipAlongRuptureModels.TAPERED,
////				SlipAlongRuptureModels.UNIFORM,
////				SlipAlongRuptureModels.TAPERED,
////				TaperOverrideSlipAlongRuptureModels.UNIFORM,
////				TaperOverrideSlipAlongRuptureModels.TAPER_OVERRIDE_COMBINED,
////				TaperOverrideSlipAlongRuptureModels.TAPER_OVERRIDE_INDIVIDUAL,
//				
//				// SUB-SECT CONSTRAINT
////				SubSectConstraintModels.TOT_NUCL_RATE, // default
////				SubSectConstraintModels.NUCL_MFD,
//				
//				// SUB-SEIS MO REDUCTION
////				SubSeisMoRateReductions.SUB_B_1,
////				SubSeisMoRateReductions.NONE, // default
////				SubSeisMoRateReductions.SYSTEM_AVG,
////				SubSeisMoRateReductions.SYSTEM_AVG_SUB_B_1,
//				
//				// SUPRA-SEIS-B
////				SupraSeisBValues.B_0p5,
////				SupraSeisBValues.AVERAGE,
//				
//				// PALEO UNCERT
////				NSHM23_PaleoUncertainties.EVEN_FIT,
//				
//				// SEGMENTATION
////				SegmentationModels.SHAW_R0_3,
////				NSHM23_SegmentationModels.AVERAGE,
////				NSHM23_SegmentationModels.MID,
////				NSHM23_SegmentationModels.CLASSIC,
////				NSHM23_SegmentationModels.CLASSIC_FULL,
//				
//				// SEG-SHIFT
////				DistDependSegShift.NONE,
////				DistDependSegShift.ONE_KM,
////				DistDependSegShift.TWO_KM,
////				DistDependSegShift.THREE_KM,
//				
//				// SEG ADJUSTMENT
////				SegmentationMFD_Adjustment.NONE,
////				SegmentationMFD_Adjustment.JUMP_PROB_THRESHOLD_AVG,
////				SegmentationMFD_Adjustment.REL_GR_THRESHOLD_AVG_SINGLE_ITER,
////				SegmentationMFD_Adjustment.REL_GR_THRESHOLD_AVG, // default
////				SegmentationMFD_Adjustment.CAPPED_REDIST,
////				SegmentationMFD_Adjustment.CAPPED_REDIST_SELF_CONTAINED,
////				SegmentationMFD_Adjustment.GREEDY,
////				SegmentationMFD_Adjustment.GREEDY_SELF_CONTAINED,
////				SegmentationMFD_Adjustment.JUMP_PROB_THRESHOLD_AVG_MATCH_STRICT,
//				
//				// CREEPING SECTION
////				RupsThroughCreepingSect.INCLUDE,
////				RupsThroughCreepingSect.EXCLUDE,
//				};
////		LogicTreeNode[] required = { FaultModels.FM3_1, SubSeisMoRateReductionNode.SYSTEM_AVG };
////		LogicTreeNode[] required = { FaultModels.FM3_1, SubSeisMoRateReductionNode.FAULT_SPECIFIC };
////		Class<? extends LogicTreeNode> sortBy = SubSectConstraintModels.class;
//		Class<? extends LogicTreeNode> sortBy = NSHM23_SegmentationModels.class;
		/*
		 * END NSHM23 logic tree
		 */
		
		/*
		 * PRVI25 logic tree
		 * TODO (this is a just a marker to find this part quickly, not an actual todo)
		 */
		
//		List<LogicTreeLevel<? extends LogicTreeNode>> levels = PRVI25_LogicTree.levelsOnFault;
//		dirName += "-prvi25_crustal_branches";
//		double avgNumRups = 50000;
//		gmpes = new AttenRelRef[] { AttenRelRef.USGS_PRVI_ACTIVE };
//		
//		// random DM sampling
//		levels = new ArrayList<>(levels);
//		int origNumLevels = levels.size();
//		for (int i=levels.size(); --i>=0;)
//			if (levels.get(i).getNodes().get(0) instanceof PRVI25_CrustalDeformationModels)
//				levels.remove(i);
//		Preconditions.checkState(levels.size() == origNumLevels -1);
//		individualRandomLevels.add(new PRVI25_CrustalRandomlySampledDeformationModelLevel());
////		samplingBranchCountMultiplier = 5; // 5 for each branch
//		samplingBranchCountMultiplier = 10; // 10 for each branch
////		samplingBranchCountMultiplier = 20; // 20 for each branch
////		samplingBranchCountMultiplier = 50; // 50 for each branch
//		randSeed *= samplingBranchCountMultiplier;
//		dirName += "-dmSample";
//		if (samplingBranchCountMultiplier > 1)
//			dirName += samplingBranchCountMultiplier+"x";
//		
////		List<LogicTreeLevel<? extends LogicTreeNode>> levels = PRVI25_LogicTree.levelsSubduction;
////		dirName += "-prvi25_subduction_branches";
////		double avgNumRups = 10000;
////		gmpes = new AttenRelRef[] { AttenRelRef.USGS_PRVI_INTERFACE, AttenRelRef.USGS_PRVI_SLAB };
//		
////		forceHazardReg = new GriddedRegion(PRVI25_RegionLoader.loadPRVI_Tight(), 0.05, GriddedRegion.ANCHOR_0_0);
////		forceHazardReg = new GriddedRegion(PRVI25_RegionLoader.loadPRVI_MapExtents(), 0.1, GriddedRegion.ANCHOR_0_0); // good for quicker tests
////		forceHazardReg = new GriddedRegion(PRVI25_RegionLoader.loadPRVI_MapExtents(), 0.05, GriddedRegion.ANCHOR_0_0);
//		forceHazardReg = new GriddedRegion(PRVI25_RegionLoader.loadPRVI_MapExtents(), 0.025, GriddedRegion.ANCHOR_0_0); // this is what I use for the paper
//		sigmaTrunc = 3d;
//		
////		levels = new ArrayList<>(levels);
////		levels.add(NSHM23_LogicTreeBranch.SUB_SECT_CONSTR);
//		
////		dirName += "-proxyGriddedTests";
//		
//		Class<? extends InversionConfigurationFactory> factoryClass = PRVI25_InvConfigFactory.class;
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = PRVI25_InvConfigFactory.MueAsCrustal.class;
////		dirName += "-mue_as_crustal";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = PRVI25_InvConfigFactory.GriddedUseM1Bounds.class;
////		dirName += "-grid_bounds_m1";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = PRVI25_InvConfigFactory.GriddedUseM1toMmaxBounds.class;
////		dirName += "-grid_bounds_m1_to_mmax";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = PRVI25_InvConfigFactory.GriddedForceCrustalRateBalancing.class;
////		dirName += "-grided_rate_balancing";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = PRVI25_InvConfigFactory.LimitCrustalBelowObserved_0p9.class;
////		dirName += "-limit_below_obs_constraint";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = PRVI25_InvConfigFactory.RateBalanceAndLimitCrustalBelowObserved_0p9.class;
////		dirName += "-limit_below_obs_constraint-grided_rate_balancing";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = PRVI25_InvConfigFactory.GriddedForceSlab2Depths.class;
////		dirName += "-gridded_use_slab2";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = PRVI25_InvConfigFactory.NoProxyLengthLimit.class;
////		dirName += "-no_proxy_len_limit";
//		
////		Class<? extends InversionConfigurationFactory> factoryClass = PRVI25_InvConfigFactory.Rates1973scaledTo1900.class;
////		dirName += "-1973_rates_scaled_to_1900";
//		
//		forceHazardGridSpacing = 0.1;
//		nodeBAskipSectBySect = false;
//		
//		forceRequiredNonzeroWeight = true;
//		LogicTreeNode[] required = {
//				// FAULT MODELS
////				PRVI25_CrustalFaultModels.PRVI_FM_INITIAL,
////				PRVI25_SubductionFaultModels.PRVI_SUB_FM_INITIAL,
//
//				// RUPTURE SETS
////				RupturePlausibilityModels.COULOMB, // default
////				RupturePlausibilityModels.COULOMB_5km,
//				
//				// DEFORMATION MODELS
////				PRVI25_CrustalDeformationModels.GEOLOGIC,
////				PRVI25_CrustalDeformationModels.GEOLOGIC_DIST_AVG,
//				
//				// SCALING RELATIONSHIPS
//				
//				// SUB-SECT CONSTRAINT
////				SubSectConstraintModels.TOT_NUCL_RATE, // default
////				SubSectConstraintModels.NUCL_MFD,
//				
//				// SUPRA-SEIS-B
////				SupraSeisBValues.B_0p5,
////				SupraSeisBValues.AVERAGE,
//				
//				// SEGMENTATION
////				NSHM23_SegmentationModels.AVERAGE,
////				NSHM23_SegmentationModels.MID,
////				NSHM23_SegmentationModels.CLASSIC,
//				};
////		LogicTreeNode[] required = { FaultModels.FM3_1, SubSeisMoRateReductionNode.SYSTEM_AVG };
////		LogicTreeNode[] required = { FaultModels.FM3_1, SubSeisMoRateReductionNode.FAULT_SPECIFIC };
////		Class<? extends LogicTreeNode> sortBy = SubSectConstraintModels.class;
//		Class<? extends LogicTreeNode> sortBy = NSHM23_SegmentationModels.class;
		/*
		 * END PRVI25 logic tree
		 */
		
		/*
		 * NSHM27 logic tree
		 * TODO (this is a just a marker to find this part quickly, not an actual todo)
		 */
		
		NSHM27_SeismicityRegions seisReg = NSHM27_SeismicityRegions.AMSAM;
//		NSHM27_SeismicityRegions seisReg = NSHM27_SeismicityRegions.GNMI;
//		int numBranchSamples = 100;
//		int numBranchSamples = 1000;
//		int numBranchSamples = 2000;
		int numBranchSamples = 5000;
//		int numBranchSamples = 10000;
//		int numBranchSamples = 20000;
//		int numBranchSamples = 100000;
		TectonicRegionType trt = null;
		
		parallelBA = true;
		boolean deterministicSeed = false;
		
		SamplingMethod samplingMethod = SamplingMethod.MONTE_CARLO;
//		SamplingMethod samplingMethod = SamplingMethod.LATIN_HYPERCUBE;
//		SamplingMethod samplingMethod = SamplingMethod.PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE;
		
		if (trt == null) {
			customTree = NSHM27_LogicTree.buildMultiRegimeTree(seisReg, numBranchSamples, deterministicSeed, samplingMethod);
			analysisTree = LogicTree.unrollTRTs(customTree);
			Preconditions.checkNotNull(analysisTree);
		} else {
			customTree = NSHM27_LogicTree.buildLogicTree(seisReg, trt, numBranchSamples, deterministicSeed, samplingMethod);
			analysisTree = customTree;
		}
		analysisTree = LogicTree.applyBinning(analysisTree);
		Preconditions.checkNotNull(analysisTree);
		
		hazardGridded = true;
		
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>(customTree.getLevels());
		dirName += "-nshm27-"+seisReg.name()+"-"+numBranchSamples+"samples";
		if (samplingMethod == SamplingMethod.MONTE_CARLO)
			dirName += "-mcs";
		else if (samplingMethod == SamplingMethod.LATIN_HYPERCUBE)
			dirName += "-lhs";
		else if (samplingMethod == SamplingMethod.PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE)
			dirName += "-lhs_pairwise";
		if (!deterministicSeed)
			dirName += "-unique_seed";
		if (trt != null)
			dirName += "-"+trt.name();
		double avgNumRups = 200000;
		// TODO
		System.err.println("WARNING: still using PRVI GMMs");
		gmpes = new AttenRelRef[] { AttenRelRef.USGS_PRVI_ACTIVE, AttenRelRef.USGS_PRVI_SLAB, AttenRelRef.USGS_PRVI_INTERFACE };
		
//		// full seis region
//		Region mapRegion = seisReg.load();
		// smaller map region
		Region mapRegion = NSHM27_MapRegions.valueOf(seisReg.name()).load();
		
		forceHazardReg = new GriddedRegion(mapRegion, 0.1, GriddedRegion.ANCHOR_0_0);
//		forceHazardReg = new GriddedRegion(mapRegion, 0.2, GriddedRegion.ANCHOR_0_0); dirName += "-haz0.2deg";
//		forceHazardReg = new GriddedRegion(mapRegion, 0.025, GriddedRegion.ANCHOR_0_0);
		sigmaTrunc = 3d;
		
		Class<? extends InversionConfigurationFactory> factoryClass = NSHM27_InvConfigFactory.class;
		
		forceHazardGridSpacing = 0.1;
		nodeBAskipSectBySect = false;
		
		forceRequiredNonzeroWeight = true;
		LogicTreeNode[] required = null;
		Class<? extends LogicTreeNode> sortBy = null;
		/*
		 * END NSHM26 logic tree
		 */
		
		// TODO this is the end of the configurable section
		
		System.out.println("Instantiating factory class: "+factoryClass.getName());
		InversionConfigurationFactory factory = null;
		try {
			factory = factoryClass.getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);;
		}
		
		LogicTree<LogicTreeNode> logicTree;
		if (customTree != null)
			logicTree = customTree;
		else if (forceRequiredNonzeroWeight)
			logicTree = LogicTree.buildExhaustive(levels, true, new BranchWeightProvider.NodeWeightOverrides(required, 1d), required);
		else
			logicTree = LogicTree.buildExhaustive(levels, true, required);
		
		int rounds = 2000;
		String completionArg = null;
		
////		int rounds = 5000;
//		int rounds = 10000;
//		String completionArg = rounds+"ip";
		
		double numIters = avgNumRups*rounds;
		double invSecs = numIters/itersPerSec;
		int invMins = (int)(invSecs/60d + 0.5);
		if (ClusterSpecificInversionConfigurationFactory.class.isAssignableFrom(factoryClass))
			invMins *= 2;
		System.out.println("Estimate "+invMins+" minutes per inversion");
		
//		String completionArg = "1m"; int invMins = 1;
//		String completionArg = "10m"; int invMins = 10;
//		String completionArg = "30m"; int invMins = 30;
//		String completionArg = "2h"; int invMins = 2*60;
//		String completionArg = "5h"; int invMins = 5*60;
//		String completionArg = null; int invMins = defaultInvMins;
		
//		int numSamples = nodes*5;
//		int numSamples = nodes*4;
//		long randSeed = System.currentTimeMillis();
		int numSamples = 0;
//		int numSamples = 450;
//		int numSamples = 36*10;
		
		if (required != null && required.length > 0) {
			for (LogicTreeNode node : required)
				dirName += "-"+node.getFilePrefix();
		}
		
		if (!individualRandomLevels.isEmpty()) {
			System.out.println("Adding "+individualRandomLevels.size()+" random levels, with "
					+ "samplingBranchCountMultiplier="+samplingBranchCountMultiplier);
			Preconditions.checkState(samplingBranchCountMultiplier >= 1);
			List<LogicTreeLevel<? extends LogicTreeNode>> modLevels = new ArrayList<>(levels.size()+individualRandomLevels.size());
			modLevels.addAll(levels);
			modLevels.addAll(individualRandomLevels);
			
			int numBranches = logicTree.size()*samplingBranchCountMultiplier;
			System.out.println("\tnumBranches = "+logicTree.size()+" x "+samplingBranchCountMultiplier+" = "+numBranches);
			
			Random rand = new Random(randSeed);
			
			List<List<? extends RandomlyGeneratedNode>> levelNodes = new ArrayList<>();
			for (RandomlyGeneratedLevel<?> level : individualRandomLevels) {
				level.build(rand.nextLong(), numBranches);
				levelNodes.add(level.getNodes());
			}
			
			List<LogicTreeBranch<LogicTreeNode>> modBranches = new ArrayList<>();
			for (int i=0; i<logicTree.size(); i++) {
				LogicTreeBranch<LogicTreeNode> branch = logicTree.getBranch(i);
				for (int n=0; n<samplingBranchCountMultiplier; n++) {
					List<LogicTreeNode> modValues = new ArrayList<>(modLevels.size());
					for (LogicTreeNode val : branch)
						modValues.add(val);
					int randIndex = modBranches.size();
					for (List<? extends RandomlyGeneratedNode> randNodes : levelNodes)
						modValues.add(randNodes.get(randIndex));
					LogicTreeBranch<LogicTreeNode> modBranch = new LogicTreeBranch<>(modLevels, modValues);
					modBranch.setOrigBranchWeight(branch.getOrigBranchWeight());
					modBranches.add(modBranch);
				}
			}
			
			levels = modLevels;
			logicTree = LogicTree.fromExisting(modLevels, modBranches);
			logicTree.setWeightProvider(new BranchWeightProvider.OriginalWeights());
		}
		
		LogicTree<?> origTree = logicTree;
		
		if (numSamples > 0) {
			if (numSamples < logicTree.size()) {
				System.out.println("Reducing tree of size "+logicTree.size()+" to "+numSamples+" samples");
				// write out the original tree
				dirName += "-"+numSamples+"_samples";
				if (!individualRandomLevels.isEmpty() && samplingBranchCountMultiplier > 1) {
					// see if we can just evenly downsample
					int sampleDiv = logicTree.size() / numSamples;
					if (logicTree.size() % numSamples == 0 && samplingBranchCountMultiplier % sampleDiv == 0) {
						System.out.println("Downsampling exactly by keepking every "
								+sampleDiv+"th random sampling branch instead of full random");
						
						double sampledWeight = 0d;
						double skippedWeight = 0d;
						List<LogicTreeBranch<LogicTreeNode>> modBranches = new ArrayList<>();
						
						for (int i=0; i<logicTree.size(); i++) {
							double weight = logicTree.getBranchWeight(i);
							if (i % sampleDiv == 0) {
								modBranches.add(logicTree.getBranch(i));
								sampledWeight += weight;
							} else {
								skippedWeight += weight;
							}
						}
						float sampledRatio = (float)(sampledWeight/(sampledWeight+skippedWeight));
						float testRatio = (float)(1d/(double)sampleDiv);
						Preconditions.checkState(sampledRatio == testRatio,
								"Samples don't have equal weight? sampledRatio=%s, testRatio=%s, sampleDiv=%s",
								sampledRatio, testRatio, sampleDiv);
						Preconditions.checkState(modBranches.size() == numSamples);
						
						dirName += "_mod"+sampleDiv;
						
						logicTree = LogicTree.fromExisting(levels, modBranches);
						logicTree.setWeightProvider(new BranchWeightProvider.OriginalWeights());
					} else {
						System.out.println("Still doing random downsampling");
						logicTree = logicTree.sample(numSamples, true, randSeed);
					}
				} else {
					logicTree = logicTree.sample(numSamples, true, randSeed);
				}
			} else {
				System.out.println("Won't sample logic tree, as tree has "+logicTree.size()+" values, which is fewer "
						+ "than the specified "+numSamples+" samples.");
			}
		}
		
		if (sortBy != null && remoteInversionsPerBundle > 1) {
			// only sort if we're bundling multiple inversions
			Comparator<LogicTreeBranch<?>> groupingComparator = new Comparator<LogicTreeBranch<?>>() {

				@SuppressWarnings("unchecked")
				@Override
				public int compare(LogicTreeBranch<?> o1, LogicTreeBranch<?> o2) {
					LogicTreeNode v1 = o1.getValue(sortBy);
					LogicTreeNode v2 = o2.getValue(sortBy);
					boolean fallback = false;;
					if (v1 == null || v2 == null) {
						if (v1 == null && v2 == null)
							fallback = true;
						else if (v1 == null)
							return -1;
						else if (v2 == null)
							return 1;
					} else if (v1.equals(v2)) {
						fallback = true;
					}
					if (fallback)
						return ((LogicTreeBranch<LogicTreeNode>)o1).compareTo((LogicTreeBranch<LogicTreeNode>)o2);
					
					return v1.getShortName().compareTo(v2.getShortName());
				}
			};
			logicTree = logicTree.sorted(groupingComparator);
		}
		
		System.out.println("Built "+logicTree.size()+" branches");
		Preconditions.checkState(logicTree.size() > 0, "No matching branches");
		
		int origNodes = nodes;
		nodes = Integer.min(nodes, logicTree.size());
		
		int numCalcs = logicTree.size()*runsPerBranch;
		int nodeRounds = (int)Math.ceil((double)numCalcs/(double)(nodes*remoteInversionsPerBundle));
		double calcNodes = Math.ceil((double)numCalcs/(double)(nodeRounds*remoteInversionsPerBundle));
		System.out.println("Implies "+(float)calcNodes+" nodes for "+nodeRounds+" rounds");
		nodes = Integer.min(nodes, (int)calcNodes);
		if (origNodes != nodes)
			System.out.println("Adjusted "+origNodes+" to "+nodes+" nodes to evenly divide "+numCalcs+" calcs");
		
		if (origNodes > 1 && nodes < 2) {
			System.out.println("Forcing 2 nodes");
			nodes = 2;
		}
		
		if (completionArg != null)
			dirName += "-"+completionArg;
		
		if (hazardGridded)
			dirName += "-gridded";
		
		if (dirSuffix != null && !dirSuffix.isBlank())
			dirName += dirSuffix;
		
		System.out.println("Directory name: "+dirName);
		
		File localDir = new File(localMainDir, dirName);
		Preconditions.checkState(localDir.exists() || localDir.mkdir());
		
		mpjWrite.setEnvVar("MAIN_DIR", remoteMainDir.getAbsolutePath());
		String mainDirPath = "$MAIN_DIR";
		mpjWrite.setEnvVar("DIR", mainDirPath+"/"+dirName);
		String dirPath = "$DIR";
		
		List<File> classpath = new ArrayList<>();
		classpath.add(new File(dirPath+"/opensha-dev-all.jar"));
		if (mpjWrite instanceof NoMPJSingleNodeShellScriptWriter)
			classpath.add(new File("/project2/scec_608/kmilner/git/opensha/lib/mpj-0.38.jar"));
		
		File localLogicTree = new File(localDir, "logic_tree.json");
		logicTree.write(localLogicTree);
		String ltPath = dirPath+"/"+localLogicTree.getName();
		
		String ltAnalPath = null;
		if (analysisTree != null) {
			Preconditions.checkState(analysisTree.size() == logicTree.size());
			for (int i=0; i<analysisTree.size(); i++)
				Preconditions.checkState(analysisTree.getBranch(i).buildFileName().equals(logicTree.getBranch(i).buildFileName()));
			File localAnalysisLogicTree = new File(localDir, "logic_tree_analysis.json");
			analysisTree.write(localAnalysisLogicTree);
			ltAnalPath = dirPath+"/"+localAnalysisLogicTree.getName();
		}
		
		if (logicTree != origTree)
			origTree.write(new File(localDir, "logic_tree_original.json"));
		
		mpjWrite.setClasspath(classpath);
		if (mpjWrite instanceof MPJExpressShellScriptWriter)
			((MPJExpressShellScriptWriter)mpjWrite).setUseLaunchWrapper(true);
		else if (mpjWrite instanceof FastMPJShellScriptWriter)
			((FastMPJShellScriptWriter)mpjWrite).setUseLaunchWrapper(true);
		
		int annealingThreads = remoteTotalThreads/remoteInversionsPerBundle;
		
		String resultsPath = dirPath+"/results";
		
		String argz = "--logic-tree "+ltPath;
		argz += " --output-dir "+resultsPath;
		argz += " --inversion-factory '"+factoryClass.getName()+"'"; // surround in single quotes to escape $'s
		argz += " --annealing-threads "+annealingThreads;
		argz += " --cache-dir "+mainDirPath+"/cache";
		if (remoteInversionsPerBundle > 0)
			argz += " --runs-per-bundle "+remoteInversionsPerBundle;
		if (completionArg != null)
			argz += " --completion "+completionArg;
		if (runsPerBranch > 1)
			argz += " --runs-per-branch "+runsPerBranch;
		if (parallelBA)
			argz += " --parallel-ba";
		for (String arg : extraArgs)
			argz += " "+arg;
		argz += " "+MPJTaskCalculator.argumentBuilder().exactDispatch(remoteInversionsPerBundle).build();
		List<String> script = mpjWrite.buildScript(MPJ_LogicTreeInversionRunner.class.getName(), argz);
		
		int mins = (int)(nodeRounds*invMins);
		mins += Integer.max(60, invMins);
		mins += (nodeRounds-1)*10; // add a little extra for overhead associated with each round
		System.out.println("Total job time: "+mins+" mins = "+(float)((double)mins/60d)+" hours");
		// make sure to not exceed 1 week
		mins = Integer.min(mins, 60*24*7 - 1);
		pbsWrite.writeScript(new File(localDir, "batch_inversion.slurm"), script, mins, nodes, remoteTotalThreads, queue);
		
		Map<String, File> baFiles = AbstractAsyncLogicTreeWriter.getBranchAverageSolutionFileMap(new File("results"), logicTree);
		
		// now write hazard script
		argz = "--input-file "+resultsPath+".zip";
		if (ltAnalPath != null)
			argz += " --analysis-logic-tree "+ltAnalPath;
		argz += " --output-dir "+resultsPath;
		if (gmpes != null)
			for (AttenRelRef gmpe : gmpes)
				argz += " --gmpe "+gmpe.name();
		if (hazardGridded) {
			argz += " --gridded-seis INCLUDE";
//			argz += " --max-distance 200";
		}
		String hazardRegionArg;
		if (forceHazardReg != null) {
			System.out.println("Using custom gridded region for hazard, spacing="
					+(float)forceHazardReg.getSpacing()+", "+forceHazardReg.getNodeCount()+" sites");
			File regionFile = new File(localDir, "gridded_region.geojson");
			Feature.write(forceHazardReg.toFeature(), regionFile);
			hazardRegionArg = " --region "+dirPath+"/"+regionFile.getName();
		} else {
			// figure out if CA or full WUS
			// also use coarse if logic tree is enormous
			double gridSpacing = logicTree.size() > 1000 ? 0.2 : 0.1;
			for (LogicTreeLevel<? extends LogicTreeNode> level : levels) {
				if (NSHM23_FaultModels.class.isAssignableFrom(level.getType()))
					// WUS, use coarser spacing
					gridSpacing = 0.2;
				if (NSHM23_SingleStates.class.isAssignableFrom(level.getType()))
					// but it's actually a single state, nevermind
					gridSpacing = 0.1;
			}
			if (forceHazardGridSpacing != null && forceHazardGridSpacing != gridSpacing) {
				System.out.println("Using hardcoded grid spacing of "+forceHazardGridSpacing.floatValue()
					+" (would have otherwise used "+(float)gridSpacing+")");
				gridSpacing = forceHazardGridSpacing;
			}
			hazardRegionArg = " --grid-spacing "+(float)gridSpacing;
		}
		argz += hazardRegionArg;
		String extraHazardArgs = "";
		if (periods != null) {
			extraHazardArgs += " --periods ";
			for (int p=0; p<periods.length; p++) {
				if (p > 0)
					extraHazardArgs += ",";
				extraHazardArgs += (float)periods[p];
			}
		}
		if (vs30 != null)
			extraHazardArgs += " --vs30 "+vs30.floatValue();
		if (supersample)
			extraHazardArgs += " --supersample-quick";
		if (sigmaTrunc != null)
			extraHazardArgs += " --gmm-sigma-trunc-one-sided "+sigmaTrunc.floatValue();
		argz += extraHazardArgs;
		argz += " "+MPJTaskCalculator.argumentBuilder().exactDispatch(1).threads(remoteTotalThreads).build();
		script = mpjWrite.buildScript(MPJ_LogicTreeHazardCalc.class.getName(), argz);
		
		// greater of 10 hours, and 45 minutes per round
		mins = Integer.max(60*10, 45*nodeRounds);
		// make sure to not exceed 1 week
		mins = Integer.min(mins, 60*24*7 - 1);
		nodes = Integer.min(40, nodes);
		if (queue != null && queue.equals("scec"))
			// run hazard in the high priority queue
			queue = "scec_hiprio";
		pbsWrite.writeScript(new File(localDir, "batch_hazard.slurm"), script, mins, nodes, remoteTotalThreads, queue);
		
		JavaShellScriptWriter javaWrite = new JavaShellScriptWriter(
				mpjWrite.getJavaBin(), remoteTotalMemGB*1024, classpath);
		Map<String, String> envVars = mpjWrite.getEnvVars();
		for (String varName : envVars.keySet())
			javaWrite.setEnvVar(varName, envVars.get(varName));
		
		boolean griddedJob = GridSourceProviderFactory.class.isAssignableFrom(factoryClass)
				&& !(GridSourceProviderFactory.Single.class.isAssignableFrom(factoryClass));
		if (griddedJob) {
			LogicTree<?> gridTree = ((GridSourceProviderFactory)factory).getGridSourceTree(logicTree);
			System.out.println("Will do gridded seismicity jobs. Grid tree has "+gridTree.size()
					+" nodes, fault x grid tree has "+(gridTree.size()*logicTree.size()));
			boolean allLevelsAffected = true;
			for (LogicTreeLevel<?> level : levels) {
				if (!GridSourceProvider.affectedByLevel(level)) {
					System.out.println(level.getShortName()+" isn't affected by gridded seismicity");
					allLevelsAffected = false;
				}
			}
			
			numCalcs = gridTree.size()*logicTree.size();
			nodeRounds = (int)Math.ceil((double)numCalcs/(double)(nodes));
			// greater of 10 hours, and 45 minutes per round
			mins = Integer.max(60*10, 45*nodeRounds);
			// make sure to not exceed 1 week
			mins = Integer.min(mins, 60*24*7 - 1);
			
			System.out.println("Gridded mins: "+mins);
			
			argz = "--factory '"+factoryClass.getName()+"'"; // surround in single quotes to escape $'s
			argz += " --logic-tree "+ltPath;
			argz += " --sol-dir "+resultsPath;
			String fullLTPath = dirPath+"/logic_tree_full_gridded.json";
			String randLTPath = dirPath+"/logic_tree_full_gridded_sampled.json";
			argz += " --write-full-tree "+fullLTPath;
			argz += " --write-rand-tree "+randLTPath+" --num-samples-per-sol 5";
			argz += " --slt-min-mag 5";
			String onlyLTPath;
			if (allLevelsAffected) {
				onlyLTPath = fullLTPath;
			} else {
				onlyLTPath = dirPath+"/logic_tree_full_gridded_for_only_calc.json";
				argz += " --write-only-tree "+onlyLTPath;
			}
//			boolean averageOnly = logicTree.size() > 400;
//			if (averageOnly)
				argz += " --average-only";
			// these calculations can take a lot of memory
			int gridThreads = Integer.max(1, remoteTotalThreads/2);
			argz += " "+MPJTaskCalculator.argumentBuilder().exactDispatch(1).threads(gridThreads).build();
			script = mpjWrite.buildScript(MPJ_GridSeisBranchBuilder.class.getName(), argz);
			pbsWrite.writeScript(new File(localDir, "batch_grid_calc.slurm"), script, mins, nodes, remoteTotalThreads, queue);
			
			String griddedBAName = null;
			if (baFiles != null && baFiles.size() == 1)
				// just one BA solution file, use that for gridded
				griddedBAName = baFiles.values().iterator().next().getName().replace(".zip", "")+"_gridded.zip"; 
			
			// true mean job
			argz = resultsPath+".zip true_mean_solution.zip";
			if (griddedBAName != null)
				argz += " "+dirPath+"/"+griddedBAName;
			script = javaWrite.buildScript(TrueMeanSolutionCreator.class.getName(), argz);
			pbsWrite.writeScript(new File(localDir, "true_mean_builder.slurm"), script, mins, 1, remoteTotalThreads, queue);
			
			// now add hazard calc jobs with gridded
			for (int i=0; i<5; i++) {
				int myNodes = nodes;
//			for (boolean avgGridded : new boolean[] {true, false}) {
				File jobFile;
				if (i == 0) {
					if (griddedBAName != null) {
						// just one BA solution file, use that for gridded
						argz = "--input-file "+resultsPath+".zip";
						argz += " --external-grid-prov "+dirPath+"/"+griddedBAName;
					} else {
						argz = "--input-file "+resultsPath+"_avg_gridded.zip";
					}
					argz += " --output-file "+resultsPath+"_hazard_avg_gridded.zip";
					argz += " --output-dir "+resultsPath;
					argz += " --gridded-seis INCLUDE";
					jobFile = new File(localDir, "batch_hazard_avg_gridded.slurm");
				} else if (i == 1) {
					argz = "--input-file "+resultsPath;
					argz += " --logic-tree "+fullLTPath;
					argz += " --output-file "+resultsPath+"_hazard_full_gridded.zip";
					argz += " --output-dir "+resultsPath+"_full_gridded";
					argz += " --combine-with-dir "+resultsPath;
					argz += " --gridded-seis INCLUDE";
					if (logicTree.size() > 50)
						argz += " --quick-grid-calc";
					jobFile = new File(localDir, "batch_hazard_full_gridded.slurm");
					myNodes = origNodes;
				} else if (i == 2) {
					argz = "--input-file "+resultsPath;
					argz += " --logic-tree "+randLTPath;
					argz += " --output-file "+resultsPath+"_hazard_full_gridded_sampled.zip";
					argz += " --output-dir "+resultsPath+"_full_gridded";
					argz += " --combine-with-dir "+resultsPath;
					argz += " --gridded-seis INCLUDE";
					argz += " --quick-grid-calc";
					jobFile = new File(localDir, "batch_hazard_full_gridded_sampled.slurm");
					myNodes = origNodes;
				} else if (i == 3) {
					argz = "--input-file "+resultsPath;
					argz += " --logic-tree "+onlyLTPath;
					argz += " --output-file "+resultsPath+"_hazard_full_gridded_only.zip";
					argz += " --output-dir "+resultsPath+"_full_gridded";
					argz += " --combine-with-dir "+resultsPath;
					argz += " --gridded-seis ONLY";
					argz += " --quick-grid-calc";
					jobFile = new File(localDir, "batch_hazard_full_gridded_only.slurm");
					myNodes = origNodes;
				} else {
					argz = "--input-file "+resultsPath+"_gridded_branches.zip";
					argz += " --output-file "+resultsPath+"_hazard_gridded_only.zip";
					argz += " --output-dir "+resultsPath+"_gridded_only";
					argz += " --gridded-seis ONLY";
					jobFile = new File(localDir, "batch_hazard_gridded_only.slurm");
				}
				argz += hazardRegionArg;
				argz += extraHazardArgs;
//				argz += " --max-distance 200";
				if (gmpes != null)
					for (AttenRelRef gmpe : gmpes)
						argz += " --gmpe "+gmpe.name();
				if (forceHazardReg != null) {
				// 	use fault-only hazard as source for region
					argz += " --region "+resultsPath+"_hazard.zip";
				}
				if (logicTree.size() > 400 && i == 1)
					argz += " "+MPJTaskCalculator.argumentBuilder().maxDispatch(100).threads(remoteTotalThreads).build();
				else
					argz += " "+MPJTaskCalculator.argumentBuilder().exactDispatch(1).threads(remoteTotalThreads).build();
				script = mpjWrite.buildScript(MPJ_LogicTreeHazardCalc.class.getName(), argz);
				int myMins = mins;
				if (i == 1)
					 myMins = Integer.min(mins*5, 60*24*7 - 1);
				pbsWrite.writeScript(jobFile, script, myMins, myNodes, remoteTotalThreads, queue);
			}
			
			// write out gridded seismicity combiner script
			argz = resultsPath+".zip";
			argz += " "+resultsPath;
			argz += " "+resultsPath+"_gridded_branches.zip";
			argz += " "+resultsPath+"_gridded_only";
			argz += " "+resultsPath+"_comb_branches.zip";
			argz += " "+resultsPath+"_comb_hazard.zip";
			// this one is just for the gridded region
			argz += " "+resultsPath+"_hazard.zip";
			script = javaWrite.buildScript(FaultAndGriddedSeparateTreeHazardCombiner.class.getName(), argz);
			
			pbsWrite.writeScript(new File(localDir, "fault_grid_hazard_combine.slurm"), script, mins, 1, remoteTotalThreads, queue);
		} else {
			// true mean without gridded
			argz = resultsPath+".zip true_mean_solution.zip";
			script = javaWrite.buildScript(TrueMeanSolutionCreator.class.getName(), argz);
			pbsWrite.writeScript(new File(localDir, "true_mean_builder.slurm"), script, mins, 1, remoteTotalThreads, queue);
		}
		
		// site hazard job
		RupSetFaultModel fm = logicTree.getBranch(0).getValue(RupSetFaultModel.class);
		if (fm != null) {
			Collection<Site> sites = null;
			if (fm instanceof FaultModels) {
				// CA
				sites = new ArrayList<>();
				for (NEHRP_TestCity site : NEHRP_TestCity.getCA())
					sites.add(new Site(site.location(), site.toString()));
			} else if (fm instanceof NSHM23_FaultModels) {
				// filter out CEUS for now
				Region reg = NSHM23_RegionLoader.loadFullConterminousWUS();
				sites = new ArrayList<>();
				for (NEHRP_TestCity site : NEHRP_TestCity.values()) {
					if (reg.contains(site.location()))
						sites.add(new Site(site.location(), site.toString()));
				}
			} else if (fm instanceof PRVI25_CrustalFaultModels || fm instanceof PRVI25_SubductionFaultModels) {
				sites = PRVI25_RegionLoader.loadHazardSites();
			}
			if (sites != null && !sites.isEmpty()) {
				CSVFile<String> csv = new CSVFile<>(true);
				csv.addLine("Name", "Latitude", "Longitude");
				for (Site site : sites)
					csv.addLine(site.getName(), site.getLocation().lat+"", site.getLocation().lon+"");
				File localSitesFile = new File(localDir, "hazard_sites.csv");
				csv.writeToFile(localSitesFile);
				
				argz = "--input-file "+resultsPath+".zip";
				if (ltAnalPath != null)
					argz += " --analysis-logic-tree "+ltAnalPath;
				argz += " --output-dir "+resultsPath+"_hazard_sites";
				argz += " --sites-file "+dirPath+"/"+localSitesFile.getName();
				argz += " "+MPJTaskCalculator.argumentBuilder().exactDispatch(1).threads(remoteTotalThreads).build();
				if (hazardGridded)
					argz += " --gridded-seis INCLUDE";
				else
					argz += " --gridded-seis EXCLUDE";
				argz += extraHazardArgs;
				if (gmpes != null)
					for (AttenRelRef gmpe : gmpes)
						argz += " --gmpe "+gmpe.name();
				script = mpjWrite.buildScript(MPJ_SiteLogicTreeHazardCurveCalc.class.getName(), argz);
				pbsWrite.writeScript(new File(localDir, "batch_hazard_sites.slurm"), script, mins, nodes, remoteTotalThreads, queue);
				
				if (griddedJob) {
					Preconditions.checkState(!hazardGridded);
					argz = "--input-file "+resultsPath;
					if (ltAnalPath != null)
						argz += " --analysis-logic-tree "+ltAnalPath;
					argz += " --logic-tree "+dirPath+"/logic_tree_full_gridded.json";
					argz += " --output-dir "+resultsPath+"_hazard_sites_full_gridded";
					argz += " --sites-file "+dirPath+"/"+localSitesFile.getName();
					argz += " --gridded-seis INCLUDE";
					argz += extraHazardArgs;
					if (gmpes != null)
						for (AttenRelRef gmpe : gmpes)
							argz += " --gmpe "+gmpe.name();
					argz += " "+MPJTaskCalculator.argumentBuilder().minDispatch(2).maxDispatch(10).threads(remoteTotalThreads).build();
					script = mpjWrite.buildScript(MPJ_SiteLogicTreeHazardCurveCalc.class.getName(), argz);
					pbsWrite.writeScript(new File(localDir, "batch_hazard_sites_full_gridded.slurm"), script, mins, nodes, remoteTotalThreads, queue);
				}
			}
		}
		
		// write node branch averaged script
		Map<String, List<LogicTreeBranch<?>>> baPrefixes = AbstractAsyncLogicTreeWriter.getBranchAveragePrefixes(
				analysisTree == null ? logicTree : analysisTree);
		List<String> baLTPaths = new ArrayList<>();
		List<String> baJobSuffixes = new ArrayList<>();
		List<String> baOutDirs = new ArrayList<>();
		if (baPrefixes.size() > 1) {
			// need to write them out piecewise
			List<LogicTreeLevel<? extends LogicTreeNode>> baLevels = analysisTree == null ? levels : analysisTree.getLevels();
			for (String baPrefix : baPrefixes.keySet()) {
				List<LogicTreeBranch<LogicTreeNode>> plainBranches = new ArrayList<>();
				for (LogicTreeBranch<?> branch : baPrefixes.get(baPrefix)) {
					LogicTreeBranch<LogicTreeNode> plainBranch = new LogicTreeBranch<>(baLevels);
					for (int i=0; i<branch.size(); i++)
						plainBranch.setValue(i, branch.getValue(i));
					plainBranch.setOrigBranchWeight(branch.getOrigBranchWeight());
					plainBranches.add(plainBranch);
				}
				LogicTree<?> subLT = LogicTree.fromExisting(baLevels, plainBranches);
				File subLogicTreeFile = new File(localDir, "sub_logic_tree_"+baPrefix+".json");
				subLT.write(subLogicTreeFile);
				String subLTPath = dirPath+"/"+subLogicTreeFile.getName();
				baLTPaths.add(subLTPath);
				baOutDirs.add(dirPath+"/node_branch_averaged_"+baPrefix);
				baJobSuffixes.add("_"+baPrefix);
			}
		} else {
			// can do the full tree
			if (ltAnalPath != null)
				baLTPaths.add(ltAnalPath);
			else
				baLTPaths.add(ltPath);
			baOutDirs.add(dirPath+"/node_branch_averaged");
			baJobSuffixes.add("");
		}
		
		for (int n=0; n<baLTPaths.size(); n++) {
			File baFile;
			if (baFiles == null)
				baFile = null;
			else if (baFiles.size() == 1)
				baFile = baFiles.values().iterator().next();
			else
				baFile = baFiles.get(baJobSuffixes.get(n));
			argz = "--input-file "+resultsPath;
			if (analysisTree == null) {
				argz += " --logic-tree "+baLTPaths.get(n);
			} else {
				argz += " --logic-tree "+ltPath;
				argz += " --analysis-logic-tree "+baLTPaths.get(n);
			}
			argz += " --output-dir "+baOutDirs.get(n);
			argz += " --threads "+Integer.min(8, remoteTotalThreads);
			argz += " --async-threads "+nodeBAAsyncThreads;
			// see if we have a single BA file to use as a comparison
			if (baFile != null)
				argz += " --branch-averaged-file "+dirPath+"/"+baFile.getName();
			script = javaWrite.buildScript(LogicTreeBranchAverageWriter.class.getName(), argz);
			
			pbsWrite.writeScript(new File(localDir, "full_node_ba"+baJobSuffixes.get(n)+".slurm"), script, mins, 1, remoteTotalThreads, queue);
			
//			// write out individual node BA scripts (useful if the tree is enormous
//			File baIndvLocalDir = new File(localDir, "indv_node_ba_scripts");
//			Preconditions.checkArgument(baIndvLocalDir.exists() || baIndvLocalDir.mkdir());
//			for (int l=0; l<levels.size(); l++) {
//				LogicTreeNode first = null;
//				boolean same = true;
//				for (LogicTreeBranch<?> branch : logicTree) {
//					LogicTreeNode node = branch.getValue(l);
//					if (first == null) {
//						first = node;
//					} else {
//						if (!first.equals(node)) {
//							same = false;
//							break;
//						}
//					}
//				}
//				if (!same) {
//					// we have variations for this level
//					LogicTreeLevel<? extends LogicTreeNode> level = levels.get(l);
//					String levelArgs = argz+" --level-class "+level.getType().getName();
//					script = javaWrite.buildScript(LogicTreeBranchAverageWriter.class.getName(), levelArgs);
//					
//					String scriptName = "node_ba_"+ClassUtils.getClassNameWithoutPackage(level.getType()).replace("$", "_")+".slurm";
//					pbsWrite.writeScript(new File(baIndvLocalDir, scriptName), script, mins, 1, remoteTotalThreads, queue);
//				}
//			}
			
			if (logicTree.size() > 20) {
				// write out parallel version
				int totNum = MPJ_LogicTreeBranchAverageBuilder.buildCombinations(analysisTree == null ? logicTree : analysisTree, 1).size();
				if (totNum > 0) {
					int myNodes = Integer.min(nodes, totNum);
					
					argz = "--input-dir "+resultsPath;
					if (analysisTree == null) {
						argz += " --logic-tree "+baLTPaths.get(n);
					} else {
						argz += " --logic-tree "+ltPath;
						argz += " --analysis-logic-tree "+baLTPaths.get(n);
					}
					argz += " --output-dir "+baOutDirs.get(n);
					if (nodeBAskipSectBySect)
						argz += " --skip-sect-by-sect";
					argz += " --plot-level "+PlotLevel.REVIEW.name();
					argz += " --depth 1";
					if (baFile != null)
						argz += " --compare-to "+dirPath+"/"+baFile.getName();
					argz += " "+MPJTaskCalculator.argumentBuilder().exactDispatch(1).threads(remoteTotalThreads).build();
					script = mpjWrite.buildScript(MPJ_LogicTreeBranchAverageBuilder.class.getName(), argz);
					pbsWrite.writeScript(new File(localDir, "batch_node_ba"+baJobSuffixes.get(n)+".slurm"),
							script, mins, myNodes, remoteTotalThreads, queue);
				}
			}
		}
		
		if (strictSeg) {
			// write strict segmentation branch translation job
			String modDirName = localDir.getName()+"-branch-translated";
			if (segTransMaxDist > 0d)
				modDirName += "-min"+new DecimalFormat("0.#").format(segTransMaxDist)+"km";
			File modLocalDir = new File(localMainDir, modDirName);
			Preconditions.checkState(modLocalDir.exists() || modLocalDir.mkdir());
			
			mpjWrite.setEnvVar("SEG_DIR", mainDirPath+"/"+modDirName);
			String modDirPath = "$SEG_DIR";
			
			classpath = new ArrayList<>();
			classpath.add(new File(modDirPath+"/opensha-dev-all.jar"));
			mpjWrite.setClasspath(classpath);
			
			List<LogicTreeLevel<? extends LogicTreeNode>> modLevels = new ArrayList<>();
			for (LogicTreeLevel<? extends LogicTreeNode> level : levels)
				if (!level.matchesType(MaxJumpDistModels.class))
					modLevels.add(level);
			modLevels.add(NSHM23_LogicTreeBranch.SEG);
			
			List<LogicTreeBranch<LogicTreeNode>> modBranches = new ArrayList<>();
			
			HashSet<String> branchNameHash = new HashSet<>();
			for (LogicTreeBranch<?> branch : logicTree) {
				LogicTreeBranch<LogicTreeNode> modBranch = new LogicTreeBranch<LogicTreeNode>(modLevels);
				for (LogicTreeNode node : branch)
					if (!(node instanceof MaxJumpDistModels))
						modBranch.setValue(node);
				String baseName = modBranch.toString();
				if (branchNameHash.contains(baseName))
					continue;
				branchNameHash.add(baseName);
				for (NSHM23_SegmentationModels segModel : NSHM23_SegmentationModels.values()) {
					if (segModel.getNodeWeight(modBranch) > 0d) {
						LogicTreeBranch<LogicTreeNode> fullBranch = modBranch.copy();
						fullBranch.setValue(segModel);
						modBranches.add(fullBranch);
					}
				}
			}
			
			System.out.println("Translating "+logicTree.size()+" branches to "+modBranches.size());
			LogicTree<LogicTreeNode> modTree = LogicTree.fromExisting(modLevels, modBranches);
			File modLocalLogicTree = new File(modLocalDir, localLogicTree.getName());
			modTree.write(modLocalLogicTree);
//			File modRemoteLogicTree = new File(modRemoteDir, modLocalLogicTree.getName());
			String modLTPath = modDirPath+"/"+modLocalLogicTree.getName();
			
			String modResultsPath = modDirPath+"/results";
			argz = "--input-dir "+resultsPath;
			argz += " --input-logic-tree "+ltPath;
			argz += " --output-logic-tree "+modLTPath;
			argz += " --inversion-factory '"+factoryClass.getName()+"'"; // surround in single quotes to escape $'s
			argz += " --output-dir "+modResultsPath;
			if (segTransMaxDist > 0d)
				argz += " --min-distance "+segTransMaxDist;
			argz += " "+MPJTaskCalculator.argumentBuilder().threads(Integer.min(10, remoteTotalThreads)).build();
			script = mpjWrite.buildScript(MPJ_StrictSegLogicTreeTranslation.class.getName(), argz);
			
			int transNodes = Integer.min(16, nodes);
			pbsWrite.writeScript(new File(modLocalDir, "batch_strict_branch_translate.slurm"), script, mins, transNodes,
					remoteTotalThreads, queue);
			
			// now write hazard script
			argz = "--input-file "+modResultsPath+".zip";
			argz += " --output-dir "+modResultsPath;
			argz += " "+MPJTaskCalculator.argumentBuilder().exactDispatch(1).threads(remoteTotalThreads).build();
			script = mpjWrite.buildScript(MPJ_LogicTreeHazardCalc.class.getName(), argz);
			
			nodes = Integer.min(40, nodes);
			pbsWrite.writeScript(new File(modLocalDir, "batch_hazard.slurm"), script, mins, nodes, remoteTotalThreads, queue);
		}
	}
	
	

}
