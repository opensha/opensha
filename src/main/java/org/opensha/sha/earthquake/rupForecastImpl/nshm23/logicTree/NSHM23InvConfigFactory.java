package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.Inversions;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint.InitialModelParticipationRateEstimator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.LaplacianSmoothingInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.ParkfieldInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.TimeCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.SolutionProcessor;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs.SubSeisMoRateReduction;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.DraftModelConstraintBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.GRParticRateEstimator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.logicTree.U3LogicTreeBranchNode;

public class NSHM23InvConfigFactory implements InversionConfigurationFactory {
	
	private Table<FaultModels, RupturePlausibilityModels, FaultSystemRupSet> rupSetCache = HashBasedTable.create();
	
	private static RupturePlausibilityModels RUP_SET_MODEL_DEFAULT = RupturePlausibilityModels.UCERF3; // for now
	
	protected synchronized FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) {
		RupturePlausibilityModels model = branch.getValue(RupturePlausibilityModels.class);
		if (model == null)
			model = RUP_SET_MODEL_DEFAULT; // for now
		FaultModels fm = branch.requireValue(FaultModels.class); // for now
		
		// check cache
		FaultSystemRupSet rupSet = rupSetCache.get(fm, model);
		if (rupSet != null)
			return rupSet;
		
		ScalingRelationships scale = branch.requireValue(ScalingRelationships.class);
		
		List<? extends FaultSection> subSects = RuptureSets.getU3SubSects(fm);
		rupSet = model.getConfig(subSects, scale).build(threads);
		rupSetCache.put(fm, model, rupSet);
		
		return rupSet;
	}

	@Override
	public FaultSystemRupSet buildRuptureSet(LogicTreeBranch<?> branch, int threads) {
		// build empty-ish rup set without modules attached
		FaultSystemRupSet rupSet = buildGenericRupSet(branch, threads);
		
		// this will replace magnitudes, slips, & slip rates, etc
		return getSolutionLogicTreeProcessor().processRupSet(rupSet, branch);
	}
	
	private static U3LogicTreeBranch equivU3(LogicTreeBranch<?> branch) {
		U3LogicTreeBranch u3Branch = U3LogicTreeBranch.DEFAULT.copy();
		for (LogicTreeNode node : branch)
			if (node instanceof U3LogicTreeBranchNode<?>)
				u3Branch.setValue((U3LogicTreeBranchNode<?>)node);
		return u3Branch;
	}

	@Override
	public SolutionProcessor getSolutionLogicTreeProcessor() {
		return new NSHM23SolProcessor();
	}
	
	private static class NSHM23SolProcessor implements SolutionProcessor {
		
		private Table<FaultModels, RupturePlausibilityModels, PlausibilityConfiguration> configCache = HashBasedTable.create();

		@Override
		public synchronized FaultSystemRupSet processRupSet(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			// create equivalent U3 branch (for now)
			U3LogicTreeBranch u3Branch = equivU3(branch);
			
			System.out.println("Equivalent U3 branch: "+u3Branch);
			// attach U3 modules
			rupSet = FaultSystemRupSet.buildFromExisting(rupSet).forU3Branch(u3Branch).build();
			rupSet.addModule(branch);
			
			if (!rupSet.hasModule(PlausibilityConfiguration.class)) {
				// mostly for branch averaging
				List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
				rupSet.addAvailableModule(new Callable<PlausibilityConfiguration>() {

					@Override
					public PlausibilityConfiguration call() throws Exception {
						RupturePlausibilityModels model = branch.requireValue(RupturePlausibilityModels.class);
						if (model == null)
							model = RUP_SET_MODEL_DEFAULT; // for now
						FaultModels fm = branch.requireValue(FaultModels.class); // for now
						PlausibilityConfiguration config;
						synchronized (configCache) {
							config = configCache.get(fm, model);
							if (config == null) {
								config = model.getConfig(subSects,
										branch.requireValue(ScalingRelationships.class)).getPlausibilityConfig();
								configCache.put(fm, model, config);
							}
						}
						return config;
					}
				}, PlausibilityConfiguration.class);
			}
			return rupSet;
		}

		@Override
		public FaultSystemSolution processSolution(FaultSystemSolution sol, LogicTreeBranch<?> branch) {
			return sol;
		}
		
	}

	@Override
	public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
			int threads) {
		double bVal = branch.requireValue(SupraSeisBValues.class).bValue;
		DraftModelConstraintBuilder constrBuilder = new DraftModelConstraintBuilder(rupSet, bVal,
				true, false, true);
		
		SubSeisMoRateReduction reduction = SupraSeisBValInversionTargetMFDs.SUB_SEIS_MO_RATE_REDUCTION_DEFAULT;
		if (branch.hasValue(SubSeisMoRateReductions.class))
			reduction = branch.getValue(SubSeisMoRateReductions.class).getChoice();
		
		constrBuilder.subSeisMoRateReduction(reduction);
		
		SubSectConstraintModels constrModel = branch.requireValue(SubSectConstraintModels.class);
		
		double slipWeight = 1d;
		double paleoWeight = 5;
		double parkWeight = 100;
		double mfdWeight = 10;
		double nuclWeight = constrModel == SubSectConstraintModels.TOT_NUCL_RATE ? 0.5 : 0d;
		double nuclMFDWeight = constrModel == SubSectConstraintModels.NUCL_MFD ? 0.1 : 0d;
		double paleoSmoothWeight = paleoWeight > 0 ? 10000 : 0;
		
		constrBuilder.magDepRelStdDev(M->0.1*Math.pow(10, bVal*0.5*(M-6)));
		
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
		
		IntegerPDF_FunctionSampler sampler = constrBuilder.getSkipBelowMinSampler();
		
		List<InversionConstraint> constraints = constrBuilder.build();
		
		SegmentationModels segModel = branch.getValue(SegmentationModels.class);
		System.out.println("Segmentation model: "+segModel);
		if (segModel != null && segModel != SegmentationModels.NONE) {
			constraints = new ArrayList<>(constraints);
			
			InitialModelParticipationRateEstimator rateEst = new InitialModelParticipationRateEstimator(
					rupSet, Inversions.getDefaultVariablePerturbationBasis(rupSet));

//			double weight = 0.5d;
//			boolean ineq = false;
			double weight = 1d;
			boolean ineq = true;
			
			constraints.add(new JumpProbabilityConstraint.RelativeRate(
					weight, ineq, rupSet, segModel.getModel(rupSet), rateEst));
		}
		
		MaxJumpDistModels distModel = branch.getValue(MaxJumpDistModels.class);
		System.out.println("Max distance model: "+distModel);
		if (distModel != null) {
			JumpProbabilityCalc model = distModel.getModel(rupSet);
			int numSkipped = 0;
			ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
			System.out.println("Zeroing out sampler probabilities for "+model);
			for (int r=0; r<cRups.size(); r++) {
//				if (r % 1000 == 0)
//					System.out.println("Prob for r="+r+": "+model.calcRuptureProb(cRups.get(r), false));
				if ((float)model.calcRuptureProb(cRups.get(r), false) == 0f) {
					sampler.set(r, 0d);
					numSkipped++;
				}
			}
			System.out.println("\tSkipped "+numSkipped+" ruptures");
		}
		
		int avgThreads = threads / 4;
		
		CompletionCriteria completion;
		if (constrModel == SubSectConstraintModels.NUCL_MFD)
			completion = TimeCompletionCriteria.getInHours(5l);
		else
			completion = TimeCompletionCriteria.getInHours(2l);
		
		InversionConfiguration.Builder builder = InversionConfiguration.builder(constraints, completion)
				.threads(threads)
				.avgThreads(avgThreads, TimeCompletionCriteria.getInMinutes(5l))
				.perturbation(GenerationFunctionType.VARIABLE_EXPONENTIAL_SCALE)
				.nonNegativity(NonnegativityConstraintType.TRY_ZERO_RATES_OFTEN)
				.sampler(sampler)
				.variablePertubationBasis(new GRParticRateEstimator(rupSet, bVal).estimateRuptureRates());
		
		return builder.build();
	}
	
	public static class NoPaleoParkfield extends NSHM23InvConfigFactory {

		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			return InversionConfiguration.builder(config).except(PaleoRateInversionConstraint.class)
				.except(PaleoSlipInversionConstraint.class).except(ParkfieldInversionConstraint.class)
				.except(LaplacianSmoothingInversionConstraint.class).build();
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
		
		LogicTree<?> tree = LogicTree.read(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2021_12_16-nshm23_draft_branches-max_dist-FM3_1-CoulombRupSet-ZENGBB-Shaw09Mod-DsrUni-TotNuclRate-SubB1/"
				+ "logic_tree.json"));
		LogicTreeBranch<LogicTreeNode> branch0 = (LogicTreeBranch<LogicTreeNode>)tree.getBranch(0);
		branch0.setValue(RupturePlausibilityModels.UCERF3);
		branch0.setValue(MaxJumpDistModels.TWO);
		
		System.out.println(branch0);
		
		NSHM23InvConfigFactory factory = new NSHM23InvConfigFactory();
		
		FaultSystemRupSet rupSet = factory.buildRuptureSet(branch0, 32);
		
		factory.buildInversionConfig(rupSet, branch0, 32);
	}

}
