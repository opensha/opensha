package org.opensha.sha.earthquake.rupForecastImpl.nshm23;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;

import org.opensha.commons.data.IntegerSampler;
import org.opensha.commons.data.IntegerSampler.ExclusionIntegerSampler;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.RupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.LaplacianSmoothingInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.ParkfieldInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationsPerVariableCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.SolutionProcessor;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.MaxJumpDistModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.RupturePlausibilityModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSectConstraintModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSeisMoRateReductions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs.SubSeisMoRateReduction;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.GRParticRateEstimator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;

public class NSHM23_InvConfigFactory implements InversionConfigurationFactory {

	private transient Table<RupSetFaultModel, RupturePlausibilityModels, FaultSystemRupSet> rupSetCache = HashBasedTable.create();
	private transient File cacheDir;
	private boolean autoCache = true;
	
	private boolean adjustTargetsForSegmentation = true;
	private boolean adjustForActualRupSlips = SupraSeisBValInversionTargetMFDs.ADJ_FOR_ACTUAL_RUP_SLIPS_DEFAULT;
	private boolean adjustForSlipAlong = SupraSeisBValInversionTargetMFDs.ADJ_FOR_SLIP_ALONG_DEFAULT;
	
	protected synchronized FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) {
		RupSetFaultModel fm = branch.requireValue(RupSetFaultModel.class);
		RupturePlausibilityModels model = branch.getValue(RupturePlausibilityModels.class);
		if (model == null) {
			if (fm instanceof FaultModels) // UCERF3 FM
				model = RupturePlausibilityModels.UCERF3; // for now
			else
				model = RupturePlausibilityModels.COULOMB;
		}
		
		// check cache
		FaultSystemRupSet rupSet = rupSetCache.get(fm, model);
		if (rupSet != null)
			return rupSet;
		
		RupSetScalingRelationship scale = branch.requireValue(RupSetScalingRelationship.class);
		
		RupSetDeformationModel dm = fm.getDefaultDeformationModel();
		List<? extends FaultSection> subSects;
		try {
			subSects = dm.build(fm);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		RupSetConfig config = model.getConfig(subSects, scale);
		
		if (cacheDir != null) {
			File subDir = new File(cacheDir, "rup_sets_"+fm.getFilePrefix()+"_"+dm.getFilePrefix());
			if (!subDir.exists())
				subDir.mkdir();
			config.setCacheDir(subDir);
		}
		config.setAutoCache(autoCache);
		
		rupSet = config.build(threads);
		rupSetCache.put(fm, model, rupSet);
		
		return rupSet;
	}
	
	@Override
	public void setCacheDir(File cacheDir) {
		this.cacheDir = cacheDir;
	}
	
	@Override
	public void setAutoCache(boolean autoCache) {
		this.autoCache = autoCache;
	}

	@Override
	public void writeCache() {
		if (cacheDir != null) {
			
		}
	}

	public void setAdjustTargetsForSegmentation(boolean adjustTargetsForSegmentation) {
		this.adjustTargetsForSegmentation = adjustTargetsForSegmentation;
	}
	
	public void adjustForActualRupSlips(boolean adjustForActualRupSlips, boolean adjustForSlipAlong) {
		this.adjustForActualRupSlips = adjustForActualRupSlips;
		this.adjustForSlipAlong = adjustForSlipAlong;
	}

	@Override
	public FaultSystemRupSet buildRuptureSet(LogicTreeBranch<?> branch, int threads) throws IOException {
		// build empty-ish rup set without modules attached
		return updateRuptureSetForBranch(buildGenericRupSet(branch, threads), branch);
	}

	@Override
	public FaultSystemRupSet updateRuptureSetForBranch(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch)
			throws IOException {
		// we don't trust any modules attached to this rupture set as it could have been used for another calculation
		// that could have attached anything. Instead, lets only keep the ruptures themselves
		
		RupSetFaultModel fm = branch.requireValue(RupSetFaultModel.class);
		RupSetDeformationModel dm = branch.requireValue(RupSetDeformationModel.class);
		Preconditions.checkState(dm.isApplicableTo(fm),
				"Fault and deformation models are compatible: %s, %s", fm.getName(), dm.getName());
		// override slip rates for the given deformation model
		List<? extends FaultSection> subSects;
		try {
			subSects = dm.build(fm);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		Preconditions.checkState(subSects.size() == rupSet.getNumSections());
		
		ClusterRuptures cRups = rupSet.getModule(ClusterRuptures.class);
		
		PlausibilityConfiguration plausibility = rupSet.getModule(PlausibilityConfiguration.class);
		RupSetScalingRelationship scale = branch.requireValue(RupSetScalingRelationship.class);
		
		if (cRups == null) {
			rupSet = FaultSystemRupSet.builder(subSects, rupSet.getSectionIndicesForAllRups())
					.forScalingRelationship(scale).build();
			if (plausibility != null)
				rupSet.addModule(plausibility);
			rupSet.addModule(ClusterRuptures.singleStranged(rupSet));
		} else {
			rupSet = ClusterRuptureBuilder.buildClusterRupSet(scale, subSects, plausibility, cRups.getAll());
		}
		
		SlipAlongRuptureModels slipAlong = branch.requireValue(SlipAlongRuptureModels.class);
		rupSet.addModule(slipAlong.getModel());
		
		// add other modules
		return getSolutionLogicTreeProcessor().processRupSet(rupSet, branch);
	}

	@Override
	public SolutionProcessor getSolutionLogicTreeProcessor() {
		return new NSHM23SolProcessor();
	}
	
	public static class NSHM23SolProcessor implements SolutionProcessor {
		
		private Table<RupSetFaultModel, RupturePlausibilityModels, PlausibilityConfiguration> configCache = HashBasedTable.create();

		@Override
		public synchronized FaultSystemRupSet processRupSet(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			// here we do trust the incoming rupture set to not be wholly incompatible (e.g., average slips)
			// so we generally offer modules instead of forcing them
			
			rupSet.addModule(branch);
			
			// offer average slips, don't force replacement
			if (branch.hasValue(RupSetScalingRelationship.class)) {
				rupSet.offerAvailableModule(new Callable<AveSlipModule>() {

					@Override
					public AveSlipModule call() throws Exception {
						return AveSlipModule.forModel(rupSet, branch.requireValue(RupSetScalingRelationship.class));
					}
				}, AveSlipModule.class);
			}
			
			// slip along rupture model
			if (branch.hasValue(SlipAlongRuptureModels.class)) {
				// force replacement as rupture sets can have a default slip along rupture model attached, which we
				// need to make sure to override
				SlipAlongRuptureModel model = branch.getValue(SlipAlongRuptureModels.class).getModel();
				if (rupSet.getModule(SlipAlongRuptureModel.class) != model)
					rupSet.addModule(model);
			}
			
			RupSetFaultModel fm = branch.getValue(RupSetFaultModel.class);
			
			// named faults, regions of interest
			if (fm != null)
				fm.attachDefaultModules(rupSet);
				
			if (fm == FaultModels.FM2_1 || fm == FaultModels.FM3_1 || fm == FaultModels.FM3_2) {
				// include the UERF3 parkfield hack for modified section min mags
				// TODO will need a similar parkfield hack for NSHM23 fault models?
				rupSet.offerAvailableModule(new Callable<ModSectMinMags>() {

					@Override
					public ModSectMinMags call() throws Exception {
						return ModSectMinMags.instance(rupSet, FaultSystemRupSetCalc.computeMinSeismoMagForSections(
								rupSet, InversionFaultSystemRupSet.MIN_MAG_FOR_SEISMOGENIC_RUPS));
					}
				}, ModSectMinMags.class);
				rupSet.offerAvailableModule(new Callable<PolygonFaultGridAssociations>() {

					@Override
					public PolygonFaultGridAssociations call() throws Exception {
						if (fm == FaultModels.FM3_1 || fm == FaultModels.FM3_2) {
							try {
								return FaultPolyMgr.loadSerializedUCERF3((FaultModels)fm);
							} catch (IOException e) {
								throw ExceptionUtils.asRuntimeException(e);
							}
						}
						return FaultPolyMgr.create(rupSet.getFaultSectionDataList(), U3InversionTargetMFDs.FAULT_BUFFER);
					}
				}, PolygonFaultGridAssociations.class);
				// add paleoseismic data TODO: U3 for now
				rupSet.offerAvailableModule(new Callable<PaleoseismicConstraintData>() {

					@Override
					public PaleoseismicConstraintData call() throws Exception {
						return PaleoseismicConstraintData.loadUCERF3(rupSet);
					}
				}, PaleoseismicConstraintData.class);
			} else {
				// regular system-wide minimum magnitudes
				rupSet.offerAvailableModule(new Callable<ModSectMinMags>() {

					@Override
					public ModSectMinMags call() throws Exception {
						// TODO revisit
						return ModSectMinMags.above(rupSet, InversionFaultSystemRupSet.MIN_MAG_FOR_SEISMOGENIC_RUPS, true);
					}
				}, ModSectMinMags.class);
			}
			// add inversion target MFDs
			rupSet.offerAvailableModule(new Callable<SupraSeisBValInversionTargetMFDs>() {

				@Override
				public SupraSeisBValInversionTargetMFDs call() throws Exception {
					return getConstraintBuilder(rupSet, branch).getTargetMFDs();
				}
			}, SupraSeisBValInversionTargetMFDs.class);
			// add target slip rates (modified for sub-seismogenic ruptures)
			// don't offer as a default implementation could have been attached
			rupSet.addAvailableModule(new Callable<SectSlipRates>() {

				@Override
				public SectSlipRates call() throws Exception {
					SupraSeisBValInversionTargetMFDs targetMFDs = rupSet.getModule(SupraSeisBValInversionTargetMFDs.class, false);
					if (targetMFDs != null)
						// we already have target MFDs loaded, get it from there
						return targetMFDs.getSectSlipRates();
					// build them
					double bVal = branch.requireValue(SupraSeisBValues.class).bValue;
					SubSeisMoRateReduction moRateRed = branch.hasValue(SubSeisMoRateReductions.class) ?
							branch.getValue(SubSeisMoRateReductions.class).getChoice() :
								SupraSeisBValInversionTargetMFDs.SUB_SEIS_MO_RATE_REDUCTION_DEFAULT;
					return new SupraSeisBValInversionTargetMFDs.Builder(rupSet, bVal)
							.subSeisMoRateReduction(moRateRed).buildSlipRatesOnly();
				}
			}, SectSlipRates.class);
			
			// don't override existing plausibility configuration, offer it instead
			// mostly for branch averaging
			List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
			rupSet.offerAvailableModule(new Callable<PlausibilityConfiguration>() {

				@Override
				public PlausibilityConfiguration call() throws Exception {
					RupturePlausibilityModels model = branch.requireValue(RupturePlausibilityModels.class);
					RupSetFaultModel fm = branch.requireValue(RupSetFaultModel.class); // for now
					PlausibilityConfiguration config;
					synchronized (configCache) {
						config = configCache.get(fm, model);
						if (config == null) {
							config = model.getConfig(subSects,
									branch.requireValue(RupSetScalingRelationship.class)).getPlausibilityConfig();
							configCache.put(fm, model, config);
						}
					}
					return config;
				}
			}, PlausibilityConfiguration.class);
			
			// offer cluster ruptures
			// should always be single stranded
			rupSet.offerAvailableModule(new Callable<ClusterRuptures>() {

				@Override
				public ClusterRuptures call() throws Exception {
					return ClusterRuptures.singleStranged(rupSet);
				}
			}, ClusterRuptures.class);
			return rupSet;
		}

		@Override
		public FaultSystemSolution processSolution(FaultSystemSolution sol, LogicTreeBranch<?> branch) {
			FaultSystemRupSet rupSet = sol.getRupSet();
			if (!sol.hasAvailableModule(SolutionSlipRates.class) && rupSet.hasAvailableModule(AveSlipModule.class)
					&& rupSet.hasAvailableModule(SlipAlongRuptureModel.class)) {
				sol.addAvailableModule(new Callable<SolutionSlipRates>() {

					@Override
					public SolutionSlipRates call() throws Exception {
						return SolutionSlipRates.calc(sol, rupSet.requireModule(AveSlipModule.class),
								rupSet.requireModule(SlipAlongRuptureModel.class));
					}
				}, SolutionSlipRates.class);
			}
			
			return sol;
		}
		
	}
	
	private static NSHM23_ConstraintBuilder getConstraintBuilder(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		double bVal = branch.requireValue(SupraSeisBValues.class).bValue;
		NSHM23_ConstraintBuilder constrBuilder = new NSHM23_ConstraintBuilder(rupSet, bVal,
				true, false, true);
		
		SubSeisMoRateReduction reduction = SupraSeisBValInversionTargetMFDs.SUB_SEIS_MO_RATE_REDUCTION_DEFAULT;
		if (branch.hasValue(SubSeisMoRateReductions.class))
			reduction = branch.getValue(SubSeisMoRateReductions.class).getChoice();
		
		constrBuilder.subSeisMoRateReduction(reduction);
		
		constrBuilder.magDepRelStdDev(M->0.1*Math.pow(10, bVal*0.5*(M-6)));
		
		return constrBuilder;
	}

	@Override
	public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
			int threads) {
		NSHM23_ConstraintBuilder constrBuilder = getConstraintBuilder(rupSet, branch);
		constrBuilder.adjustForActualRupSlips(adjustForActualRupSlips, adjustForSlipAlong);
		
		SubSectConstraintModels constrModel = branch.requireValue(SubSectConstraintModels.class);
		RupSetFaultModel fm = branch.requireValue(RupSetFaultModel.class);
		
		double slipWeight = 1d;
		double paleoWeight = fm instanceof FaultModels ? 5 : 0; // TODO
		double parkWeight = fm instanceof FaultModels ? 10 : 0; // TODO
		double mfdWeight = constrModel == SubSectConstraintModels.NUCL_MFD ? 1 : 10;
		double nuclWeight = constrModel == SubSectConstraintModels.TOT_NUCL_RATE ? 0.5 : 0d;
		double nuclMFDWeight = constrModel == SubSectConstraintModels.NUCL_MFD ? 0.5 : 0d;
		double paleoSmoothWeight = paleoWeight > 0 ? 10000 : 0;

		SegmentationModels segModel = branch.getValue(SegmentationModels.class);
		System.out.println("Segmentation model: "+segModel);
		MaxJumpDistModels distModel = branch.getValue(MaxJumpDistModels.class);
		System.out.println("Max distance model: "+distModel);
		if (adjustTargetsForSegmentation) {
			JumpProbabilityCalc targetSegModel = segModel == null ? null : segModel.getModel(rupSet);
			if (distModel != null) {
				if (targetSegModel == null)
					targetSegModel = distModel.getModel(rupSet);
				else
					targetSegModel = new JumpProbabilityCalc.MultiProduct(targetSegModel, distModel.getModel(rupSet));
			}
			constrBuilder.adjustForSegmentationModel(targetSegModel);
		}
		
		if (slipWeight > 0d)
			constrBuilder.slipRates().weight(slipWeight);
		
		if (paleoWeight > 0d) {
			constrBuilder.paleoRates().weight(paleoWeight);
			constrBuilder.paleoSlips().weight(paleoWeight);
		}
		
		if (parkWeight > 0d)
			constrBuilder.parkfield().weight(parkWeight);
		
		if (mfdWeight > 0d)
			constrBuilder.supraBValMFDs().weight(mfdWeight);
		
		if (nuclWeight > 0d)
			constrBuilder.sectSupraRates().weight(nuclWeight);
		
		if (nuclMFDWeight > 0d)
			constrBuilder.sectSupraNuclMFDs().weight(nuclMFDWeight);
		
		if (paleoSmoothWeight > 0d)
			constrBuilder.supraPaleoSmooth().weight(paleoSmoothWeight);
		
		ExclusionIntegerSampler sampler = constrBuilder.getSkipBelowMinSampler();
		
		List<InversionConstraint> constraints = constrBuilder.build();
		
		double bVal = branch.requireValue(SupraSeisBValues.class).bValue;
		
		GRParticRateEstimator rateEst = new GRParticRateEstimator(rupSet, bVal);
		
		if (segModel != null && segModel != SegmentationModels.NONE) {
			constraints = new ArrayList<>(constraints);
			
//			InitialModelParticipationRateEstimator rateEst = new InitialModelParticipationRateEstimator(
//					rupSet, Inversions.getDefaultVariablePerturbationBasis(rupSet));

//			double weight = 0.5d;
//			boolean ineq = false;
			double weight = 10d;
			boolean ineq = true;
			
			constraints.add(new JumpProbabilityConstraint.RelativeRate(
					weight, ineq, rupSet, segModel.getModel(rupSet), rateEst));
		}
		
		if (distModel != null) {
			JumpProbabilityCalc model = distModel.getModel(rupSet);
			ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
			System.out.println("Zeroing out sampler probabilities for "+model);
			HashSet<Integer> distSkips = new HashSet<>();
			for (int r=0; r<cRups.size(); r++) {
//				if (r % 1000 == 0)
//					System.out.println("Prob for r="+r+": "+model.calcRuptureProb(cRups.get(r), false));
				if ((float)model.calcRuptureProb(cRups.get(r), false) == 0f)
					distSkips.add(r);
			}
			System.out.println("\tSkipped "+distSkips.size()+" ruptures");
			if (!distSkips.isEmpty()) {
				ExclusionIntegerSampler distSkipSampler = new ExclusionIntegerSampler(0, rupSet.getNumRuptures(), distSkips);
				if (sampler == null)
					sampler = distSkipSampler;
				else
					sampler = sampler.getCombinedWith(distSkipSampler);
			}
		}
		
		int avgThreads = threads / 4;
		
		CompletionCriteria completion = new IterationsPerVariableCompletionCriteria(5000d);
		
		InversionConfiguration.Builder builder = InversionConfiguration.builder(constraints, completion)
				.threads(threads)
				.subCompletion(new IterationsPerVariableCompletionCriteria(1d))
//				.avgThreads(avgThreads, new IterationsPerVariableCompletionCriteria(100d))
				.avgThreads(avgThreads, new IterationsPerVariableCompletionCriteria(50d))
				.perturbation(GenerationFunctionType.VARIABLE_EXPONENTIAL_SCALE)
				.nonNegativity(NonnegativityConstraintType.TRY_ZERO_RATES_OFTEN)
				.sampler(sampler)
				.reweight()
				.variablePertubationBasis(rateEst.estimateRuptureRates());
		
		if (parkWeight > 0d)
			builder.initialSolution(constrBuilder.getParkfieldInitial(true));
		
		return builder.build();
	}
	
	public static class NoPaleoParkfield extends NSHM23_InvConfigFactory {

		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			return InversionConfiguration.builder(config).except(PaleoRateInversionConstraint.class)
				.except(PaleoSlipInversionConstraint.class).except(ParkfieldInversionConstraint.class)
				.except(LaplacianSmoothingInversionConstraint.class).build();
		}
		
	}
	
	public static class NoSegAdjust extends NSHM23_InvConfigFactory {
		
		public NoSegAdjust() {
			this.setAdjustTargetsForSegmentation(false);
		}
		
	}
	
	public static class NoMFDScaleAdjust extends NSHM23_InvConfigFactory {

		public NoMFDScaleAdjust() {
			this.adjustForActualRupSlips(false, false);
		}
		
	}
	
	public static class MFDSlipAlongAdjust extends NSHM23_InvConfigFactory {

		public MFDSlipAlongAdjust() {
			this.adjustForActualRupSlips(true, true);
		}
		
	}
	
	public static void main(String[] args) throws IOException {
//		File dir = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
//				+ "2021_11_24-nshm23_draft_branches-FM3_1/");
////				+ "2021_11_30-nshm23_draft_branches-FM3_1-FaultSpec");
//		File ltFile = new File(dir, "results.zip");
//		SolutionLogicTree tree = SolutionLogicTree.load(ltFile);
//		
//		FaultSystemSolution ba = tree.calcBranchAveraged();
//		
//		ba.write(new File(dir, "branch_averaged.zip"));
		
//		LogicTree<?> tree = LogicTree.read(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
//				+ "2021_12_16-nshm23_draft_branches-max_dist-FM3_1-CoulombRupSet-ZENGBB-Shaw09Mod-DsrUni-TotNuclRate-SubB1/"
//				+ "logic_tree.json"));
//		LogicTreeBranch<LogicTreeNode> branch0 = (LogicTreeBranch<LogicTreeNode>)tree.getBranch(0);
//		branch0.setValue(RupturePlausibilityModels.UCERF3);
//		branch0.setValue(MaxJumpDistModels.TWO);
//		
//		System.out.println(branch0);
		
		LogicTreeBranch<?> branch = NSHM23_LogicTreeBranch.DEFAULT;
		
		NSHM23_InvConfigFactory factory = new NSHM23_InvConfigFactory();
		
		FaultSystemRupSet rupSet = factory.buildRuptureSet(branch, 32);
		
		factory.buildInversionConfig(rupSet, branch, 32);
	}

}
