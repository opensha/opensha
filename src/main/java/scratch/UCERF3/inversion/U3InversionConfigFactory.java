package scratch.UCERF3.inversion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.opensha.commons.data.IntegerSampler;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.RupSetSubsectioningModel;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration.Builder;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.Inversions;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint.InitialModelParticipationRateEstimator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDLaplacianSmoothingInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.ParkfieldInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationsPerVariableCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.TimeCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.CoolingScheduleType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.SolutionProcessor;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.data.NSHM23_PaleoDataLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_PaleoUncertainties;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationModelBranchNode;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.griddedSeismicity.UCERF3_GridSourceGenerator;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.aveSlip.U3AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.U3PaleoRateConstraint;

/**
 * Inversion configuration factory for UCERF3 in the new (2021) framework, and also using new annealing defaults
 * 
 * @author kevin
 *
 */
public class U3InversionConfigFactory implements InversionConfigurationFactory {
	
	transient Map<FaultModels, FaultSystemRupSet> rupSetCache = new HashMap<>();
	
	protected synchronized FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) throws IOException {
		FaultModels fm = branch.requireValue(FaultModels.class);
		// check cache
		FaultSystemRupSet rupSet = rupSetCache.get(fm);
		if (rupSet != null)
			return rupSet;
		
		rupSet = new RuptureSets.U3RupSetConfig(fm, branch.requireValue(RupSetScalingRelationship.class)).build(threads);

		rupSetCache.put(fm, rupSet);
		
		return rupSet;
	}

	@Override
	public FaultSystemRupSet buildRuptureSet(LogicTreeBranch<?> branch, int threads) throws IOException {
		FaultSystemRupSet rupSet = buildGenericRupSet(branch, threads);
		
		return updateRuptureSetForBranch(rupSet, branch);
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
			subSects = dm.build(fm, null, branch);
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
		} else {
			rupSet = ClusterRuptureBuilder.buildClusterRupSet(scale, subSects, plausibility, cRups.getAll());
		}
		
		// attach U3 modules
		getSolutionLogicTreeProcessor().processRupSet(rupSet, branch);
		
		return rupSet;
	}

	@Override
	public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
			int threads) {
		InversionTargetMFDs targetMFDs = rupSet.requireModule(InversionTargetMFDs.class);
		FaultModels fm = branch.getValue(FaultModels.class);
		UCERF3InversionConfiguration config = UCERF3InversionConfiguration.forModel(
				branch.getValue(InversionModels.class), rupSet, fm, targetMFDs);
		
		// get the improbability constraints
		double[] improbabilityConstraint = null; // not used
		// get the paleo rate constraints
		PaleoseismicConstraintData paleoData = rupSet.requireModule(PaleoseismicConstraintData.class);
		List<? extends SectMappedUncertainDataConstraint> paleoRateConstraints = paleoData.getPaleoRateConstraints();
		// paleo probability model
		PaleoProbabilityModel paleoProbabilityModel = paleoData.getPaleoProbModel();
		List<? extends SectMappedUncertainDataConstraint> aveSlipConstraints = paleoData.getPaleoSlipConstraints();

		NSHM23_PaleoUncertainties paleoUncert = branch.getValue(NSHM23_PaleoUncertainties.class);
		if (paleoUncert != null) {
			if (paleoRateConstraints != null)
				paleoRateConstraints = paleoUncert.getScaled(paleoRateConstraints);
			if (aveSlipConstraints != null)
				aveSlipConstraints = paleoUncert.getScaled(aveSlipConstraints);
		}

		UCERF3InversionInputGenerator inputGen = new UCERF3InversionInputGenerator(rupSet, config, paleoRateConstraints,
				aveSlipConstraints, improbabilityConstraint, paleoProbabilityModel);
		
		List<InversionConstraint> constraints = inputGen.getConstraints();
		
		SegmentationModelBranchNode segModelChoice = branch.getValue(SegmentationModelBranchNode.class);
		if (segModelChoice != null) {
			JumpProbabilityCalc segModel = segModelChoice.getModel(rupSet, branch);
			if (segModel != null) {
				constraints = new ArrayList<>(constraints);
				
				InitialModelParticipationRateEstimator rateEst = new InitialModelParticipationRateEstimator(
						rupSet, Inversions.getDefaultVariablePerturbationBasis(rupSet));

//				double weight = 0.5d;
//				boolean ineq = false;
				double weight = 100000d;
				boolean ineq = true;
				
				constraints.add(new JumpProbabilityConstraint.RelativeRate(
						weight, ineq, rupSet, segModel, rateEst));
			}
		}
		
		int avgThreads = threads / 4;
		
		CompletionCriteria completion = new IterationsPerVariableCompletionCriteria(2000d);
		
		InversionConfiguration.Builder builder = InversionConfiguration.builder(constraints, completion)
				.threads(threads)
				.subCompletion(new IterationsPerVariableCompletionCriteria(1d))
				.avgThreads(avgThreads, new IterationsPerVariableCompletionCriteria(50d))
				.perturbation(GenerationFunctionType.VARIABLE_EXPONENTIAL_SCALE)
				.nonNegativity(NonnegativityConstraintType.TRY_ZERO_RATES_OFTEN)
				.sampler(new IntegerSampler.ContiguousIntegerSampler(rupSet.getNumRuptures()))
				.variablePertubationBasis(config.getMinimumRuptureRateBasis());
		
		return builder.build();
	}

	@Override
	public SolutionProcessor getSolutionLogicTreeProcessor() {
		return new UCERF3_SolutionProcessor();
	}
	
	public static class UCERF3_SolutionProcessor implements SolutionProcessor {

		@Override
		public FaultSystemRupSet processRupSet(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			RupSetFaultModel fm = branch.getValue(RupSetFaultModel.class);
			
			// set logic tree branch
			rupSet.addModule(branch);
			
			// add slip along rupture model information
			if (branch.hasValue(SlipAlongRuptureModels.class))
				rupSet.addModule(branch.requireValue(SlipAlongRuptureModels.class).getModel());
			
			if (branch.hasValue(RupSetScalingRelationship.class)) {
				rupSet.offerAvailableModule(new Callable<AveSlipModule>() {

					@Override
					public AveSlipModule call() throws Exception {
						return AveSlipModule.forModel(rupSet, branch.requireValue(RupSetScalingRelationship.class));
					}
				}, AveSlipModule.class);
			}
			
			if (fm != null)
				// named faults, regions, polygons
				fm.attachDefaultModules(rupSet);
			
			// min mags are model specific, add them here
			if (fm == FaultModels.FM2_1 || fm == FaultModels.FM3_1 || fm == FaultModels.FM3_2) {
				// include the parkfield hack for modified section min mags
				rupSet.offerAvailableModule(new Callable<ModSectMinMags>() {

					@Override
					public ModSectMinMags call() throws Exception {
						return ModSectMinMags.instance(rupSet, FaultSystemRupSetCalc.computeMinSeismoMagForSections(
								rupSet, InversionFaultSystemRupSet.MIN_MAG_FOR_SEISMOGENIC_RUPS));
					}
				}, ModSectMinMags.class);
			} else {
				// regular system-wide minimum magnitudes
				rupSet.offerAvailableModule(new Callable<ModSectMinMags>() {

					@Override
					public ModSectMinMags call() {
						return ModSectMinMags.above(rupSet, InversionFaultSystemRupSet.MIN_MAG_FOR_SEISMOGENIC_RUPS, true);
					}
				}, ModSectMinMags.class);
			}
			
			// add inversion target MFDs
			rupSet.offerAvailableModule(new Callable<U3InversionTargetMFDs>() {

				@Override
				public U3InversionTargetMFDs call() throws Exception {
					return new U3InversionTargetMFDs(rupSet, branch, rupSet.requireModule(ModSectMinMags.class),
							rupSet.requireModule(PolygonFaultGridAssociations.class));
				}
			}, U3InversionTargetMFDs.class);
			
			// add target slip rates (modified for sub-seismogenic ruptures)
			// force replacement as there's a default implementation of this module
			rupSet.addAvailableModule(new Callable<SectSlipRates>() {

				@Override
				public SectSlipRates call() throws Exception {
					InversionTargetMFDs invMFDs = rupSet.requireModule(InversionTargetMFDs.class);
					return InversionFaultSystemRupSet.computeTargetSlipRates(rupSet,
							branch.getValue(InversionModels.class), branch.getValue(MomentRateFixes.class), invMFDs);
				}
			}, SectSlipRates.class);
			
			// add paleoseismic data
			rupSet.offerAvailableModule(new Callable<PaleoseismicConstraintData>() {

				@Override
				public PaleoseismicConstraintData call() throws Exception {
					try {
						return PaleoseismicConstraintData.loadUCERF3(rupSet);
					} catch (IOException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
			}, PaleoseismicConstraintData.class);
			
			return rupSet;
		}

		@Override
		public FaultSystemSolution processSolution(FaultSystemSolution sol, LogicTreeBranch<?> branch) {
			FaultSystemRupSet rupSet = sol.getRupSet();
			sol.offerAvailableModule(new Callable<SubSeismoOnFaultMFDs>() {

				@Override
				public SubSeismoOnFaultMFDs call() throws Exception {
					return new SubSeismoOnFaultMFDs(
							rupSet.requireModule(InversionTargetMFDs.class).getOnFaultSubSeisMFDs().getAll());
				}
			}, SubSeismoOnFaultMFDs.class);
			sol.offerAvailableModule(new Callable<GridSourceProvider>() {

				@Override
				public GridSourceProvider call() throws Exception {
					return new UCERF3_GridSourceGenerator(sol, branch.getValue(SpatialSeisPDF.class),
							branch.getValue(MomentRateFixes.class),
							rupSet.requireModule(InversionTargetMFDs.class),
							sol.requireModule(SubSeismoOnFaultMFDs.class),
							branch.getValue(MaxMagOffFault.class).getMaxMagOffFault(),
							rupSet.requireModule(FaultGridAssociations.class));
				}
			}, GridSourceProvider.class);
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
	
	public static class NoPaleoParkfieldSingleReg extends U3InversionConfigFactory {

		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			List<MFDInversionConstraint> singleRegionConstrs = new ArrayList<>();
			for (InversionConstraint constr : config.getConstraints()) {
				if (constr instanceof MFDInversionConstraint) {
					MFDInversionConstraint orig = (MFDInversionConstraint)constr;
					List<? extends IncrementalMagFreqDist> mfds = orig.getMFDs();
					Preconditions.checkState(mfds.size() == 2);
					SummedMagFreqDist sumMFD = null;
					for (IncrementalMagFreqDist mfd : mfds) {
						if (sumMFD == null)
							sumMFD = new SummedMagFreqDist(mfd.getMinX(), mfd.size(), mfd.getDelta());
						sumMFD.addIncrementalMagFreqDist(mfd);
					}
					sumMFD.setRegion(new CaliforniaRegions.RELM_TESTING());
					singleRegionConstrs.add(new MFDInversionConstraint(rupSet, orig.getWeight(), orig.isInequality(),
							orig.getWeightingType(), List.of(sumMFD), orig.getExcludeRupIndexes()));
				}
			}
			Preconditions.checkState(!singleRegionConstrs.isEmpty());
			Builder builder = InversionConfiguration.builder(config).except(PaleoRateInversionConstraint.class)
				.except(PaleoSlipInversionConstraint.class).except(ParkfieldInversionConstraint.class)
				.except(MFDInversionConstraint.class).except(MFDLaplacianSmoothingInversionConstraint.class);
			for (MFDInversionConstraint constr : singleRegionConstrs)
				builder.add(constr);
			return builder.build();
		}
		
	}
	
	public static class ThinnedRupSet extends U3InversionConfigFactory {

		@Override
		protected synchronized FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) throws IOException {
			FaultModels fm = branch.requireValue(FaultModels.class);
			// check cache
			FaultSystemRupSet rupSet = rupSetCache.get(fm);
			if (rupSet != null)
				return rupSet;
			
			RuptureSets.U3RupSetConfig config = new RuptureSets.U3RupSetConfig(fm, branch.requireValue(ScalingRelationships.class));
			config.setAdaptiveSectFract(0.1f);
			
			rupSet = config.build(threads);

			rupSetCache.put(fm, rupSet);
			
			return rupSet;
		}
		
	}
	
	/**
	 * As close as we can get to UCERF3 exactly as was
	 * 
	 * @author kevin
	 *
	 */
	public static class OriginalCalcParams extends U3InversionConfigFactory {

		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			threads = 5;
			
			InversionTargetMFDs targetMFDs = rupSet.requireModule(InversionTargetMFDs.class);
			FaultModels fm = branch.getValue(FaultModels.class);
			UCERF3InversionConfiguration config = UCERF3InversionConfiguration.forModel(
					branch.getValue(InversionModels.class), rupSet, fm, targetMFDs);
			
			// get the improbability constraints
			double[] improbabilityConstraint = null; // not used
			// get the paleo rate constraints
			List<U3PaleoRateConstraint> paleoRateConstraints;
			// paleo probability model
			PaleoProbabilityModel paleoProbabilityModel;
			List<U3AveSlipConstraint> aveSlipConstraints;
			try {
				paleoRateConstraints = CommandLineInversionRunner.getPaleoConstraints(
						fm, rupSet);

				paleoProbabilityModel = UCERF3InversionInputGenerator.loadDefaultPaleoProbabilityModel();

				aveSlipConstraints = U3AveSlipConstraint.load(rupSet.getFaultSectionDataList());
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
			UCERF3InversionInputGenerator u3Gen = new UCERF3InversionInputGenerator(rupSet, config, paleoRateConstraints,
					aveSlipConstraints, improbabilityConstraint, paleoProbabilityModel);
			
			long totalIters = 25000000l;
			double totSecs = 5*60*60;
			long itersPerSec = (long)((double)totalIters/totSecs + 0.5);
			
			InversionConfiguration.Builder builder = InversionConfiguration.builder(u3Gen.getConstraints(),
					new IterationCompletionCriteria(totalIters))
					.threads(threads)
					// include a fake averaging layer just to reduce STDOUT, will do nothing as only 1 thread
					.avgThreads(1, new IterationCompletionCriteria(100l*itersPerSec))
					.subCompletion(new IterationCompletionCriteria(itersPerSec))
					.threads(5)
					.cooling(CoolingScheduleType.FAST_SA)
					.initialSolution(null)
					.sampler(new IntegerSampler.ContiguousIntegerSampler(rupSet.getNumRuptures()))
					.nonNegativity(NonnegativityConstraintType.LIMIT_ZERO_RATES)
					.perturbation(GenerationFunctionType.UNIFORM_0p001)
					.reweight(null)
					.waterLevel(u3Gen.getWaterLevelRates());
			
			return builder.build();
		}
		
	}
	
	/**
	 * Same calculation params as UCERF3, but with the new threading/averaging scheme
	 * 
	 * @author kevin
	 *
	 */
	public static class OriginalCalcParamsNewAvg extends U3InversionConfigFactory {
		
		private OriginalCalcParams origFactory = new OriginalCalcParams();

		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = origFactory.buildInversionConfig(rupSet, branch, threads);
			
			InversionConfiguration.Builder builder = InversionConfiguration.builder(config);
			builder.threads(threads);
			int avgThreads = threads / 4;
			builder.avgThreads(avgThreads, new IterationsPerVariableCompletionCriteria(50d));
			
			return builder.build();
		}
		
	}
	
	/**
	 * Same calculation params as UCERF3, but with the new threading/averaging scheme & longer anneal time
	 * 
	 * @author kevin
	 *
	 */
	public static class OriginalCalcParamsNewAvgConverged extends U3InversionConfigFactory {
		
		private OriginalCalcParams origFactory = new OriginalCalcParams();

		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = origFactory.buildInversionConfig(rupSet, branch, threads);
			
			InversionConfiguration.Builder builder = InversionConfiguration.builder(config);
			
			builder.completion(new IterationsPerVariableCompletionCriteria(5000d));
			builder.threads(threads);
			int avgThreads = threads / 4;
			builder.subCompletion(new IterationsPerVariableCompletionCriteria(1d));
			builder.avgThreads(avgThreads, new IterationsPerVariableCompletionCriteria(50d));
			
			return builder.build();
		}
		
	}
	
	/**
	 * Same calculation params as UCERF3, but with the new threading/averaging scheme & longer anneal time and the water
	 * level removed
	 * 
	 * @author kevin
	 *
	 */
	public static class OriginalCalcParamsNewAvgNoWaterLevel extends U3InversionConfigFactory {
		
		private OriginalCalcParams origFactory = new OriginalCalcParams();

		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = origFactory.buildInversionConfig(rupSet, branch, threads);
			
			InversionConfiguration.Builder builder = InversionConfiguration.builder(config);
			
			builder.completion(new IterationsPerVariableCompletionCriteria(5000d));
			builder.threads(threads);
			int avgThreads = threads / 4;
			builder.subCompletion(new IterationsPerVariableCompletionCriteria(1d));
			builder.avgThreads(avgThreads, new IterationsPerVariableCompletionCriteria(50d));
			builder.waterLevel(null);
			
			return builder.build();
		}
		
	}
	
	public static class CoulombRupSet extends U3InversionConfigFactory {

		@Override
		protected synchronized FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) throws IOException {
			FaultModels fm = branch.requireValue(FaultModels.class);
			// check cache
			FaultSystemRupSet rupSet = rupSetCache.get(fm);
			if (rupSet != null)
				return rupSet;
			// need to build one
			RuptureSets.CoulombRupSetConfig rsConfig = new RuptureSets.CoulombRupSetConfig(fm,
					branch.requireValue(ScalingRelationships.class));
			rupSet = rsConfig.build(threads);
			// cache it
			rupSetCache.put(fm, rupSet);
			return rupSet;
		}

		@Override
		public SolutionProcessor getSolutionLogicTreeProcessor() {
			return new CoulombU3SolProcessor();
		}
		
	}
	
	public static class CoulombBilateralRupSet extends U3InversionConfigFactory {

		@Override
		protected synchronized FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) throws IOException {
			FaultModels fm = branch.requireValue(FaultModels.class);
			// check cache
			FaultSystemRupSet rupSet = rupSetCache.get(fm);
			if (rupSet != null)
				return rupSet;
			// need to build one
			RuptureSets.CoulombRupSetConfig rsConfig = new RuptureSets.CoulombRupSetConfig(fm,
					branch.requireValue(ScalingRelationships.class));
			rsConfig.setBilateral(true);
			rupSet = rsConfig.build(threads);
			// cache it
			rupSetCache.put(fm, rupSet);
			return rupSet;
		}

		@Override
		public SolutionProcessor getSolutionLogicTreeProcessor() {
			return new CoulombU3SolProcessor(true);
		}
		
	}
	
	private static class CoulombU3SolProcessor extends UCERF3_SolutionProcessor {
		
		private boolean bilateral;

		public CoulombU3SolProcessor() {
			this(false);
		}
		
		public CoulombU3SolProcessor(boolean bilateral) {
			this.bilateral = bilateral;
		}

		@Override
		public FaultSystemRupSet processRupSet(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			rupSet = super.processRupSet(rupSet, branch);
			if (!rupSet.hasModule(PlausibilityConfiguration.class)) {
				// for branch averaging
				rupSet.addAvailableModule(new Callable<PlausibilityConfiguration>() {

					@Override
					public PlausibilityConfiguration call() throws Exception {
						FaultModels fm = branch.requireValue(FaultModels.class);
						RuptureSets.CoulombRupSetConfig rsConfig = new RuptureSets.CoulombRupSetConfig(fm,
								branch.requireValue(ScalingRelationships.class));
						rsConfig.setBilateral(bilateral);
						return rsConfig.getPlausibilityConfig();
					}
				}, PlausibilityConfiguration.class);
				
			}
			return rupSet;
		}
		
	}
	
	/**
	 * Extend down-dip width of all faults
	 * 
	 * @author kevin
	 *
	 */
	private static class AbstractScaleLowerDepth extends U3InversionConfigFactory {
		
		private double scalar;

		private AbstractScaleLowerDepth(double scalar) {
			this.scalar = scalar;
		}

		@Override
		public FaultSystemRupSet buildRuptureSet(LogicTreeBranch<?> branch, int threads) throws IOException {
			FaultSystemRupSet rupSet = super.buildRuptureSet(branch, threads);
			
			List<FaultSection> modSects = new ArrayList<>();
			for (FaultSection sect : rupSet.getFaultSectionDataList()) {
				GeoJSONFaultSection geoSect;
				if (sect instanceof GeoJSONFaultSection)
					geoSect = (GeoJSONFaultSection)sect;
				else
					geoSect = new GeoJSONFaultSection(sect);
				Feature feature = geoSect.toFeature();
				FeatureProperties props = feature.properties;
				double curLowDepth = props.getDouble(GeoJSONFaultSection.LOW_DEPTH, Double.NaN);
				Preconditions.checkState(curLowDepth > 0);
				props.set(GeoJSONFaultSection.LOW_DEPTH, curLowDepth*scalar);
				FaultSection modSect = GeoJSONFaultSection.fromFeature(feature);
				modSects.add(modSect);
			}
			
			ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
			PlausibilityConfiguration plausibility = rupSet.getModule(PlausibilityConfiguration.class);
			RupSetScalingRelationship scale = branch.requireValue(RupSetScalingRelationship.class);

			rupSet = ClusterRuptureBuilder.buildClusterRupSet(scale, modSects, plausibility, cRups.getAll());
			
			// attach U3 modules
			getSolutionLogicTreeProcessor().processRupSet(rupSet, branch);
			
			return rupSet;
		}
		
	}
	
	public static class ScaleLowerDepth1p3 extends AbstractScaleLowerDepth {
		
		public ScaleLowerDepth1p3() {
			super(1.3);
		}
		
	}
	
	public static class ForceNewPaleo extends U3InversionConfigFactory {

		@Override
		public SolutionProcessor getSolutionLogicTreeProcessor() {
			return new UCERF3_SolutionProcessor() {

				@Override
				public synchronized FaultSystemRupSet processRupSet(FaultSystemRupSet rupSet,
						LogicTreeBranch<?> branch) {
					rupSet = super.processRupSet(rupSet, branch);
					rupSet.removeModuleInstances(PaleoseismicConstraintData.class);
					try {
						rupSet.addModule(NSHM23_PaleoDataLoader.load(rupSet));
					} catch (IOException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					return rupSet;
				}
				
			};
		}
		
	}
	
	public static void main(String[] args) throws IOException {
		InversionConfigurationFactory factory;
		try {
			@SuppressWarnings("unchecked")
			Class<? extends InversionConfigurationFactory> factoryClass = (Class<? extends InversionConfigurationFactory>)
					Class.forName("scratch.kevin.nshm23.U3InversionConfigFactory$NoPaleoParkfieldSingleReg");
			factory = factoryClass.getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		System.out.println("Factory type: "+factory.getClass().getName());
//		U3InversionConfigFactory factory = new U3InversionConfigFactory.NoPaleoParkfieldSingleReg();
		
		U3LogicTreeBranch branch = U3LogicTreeBranch.DEFAULT;
		FaultSystemRupSet rupSet = factory.buildRuptureSet(branch, 32);
		InversionConfiguration config = factory.buildInversionConfig(rupSet, branch, 32);
		for (InversionConstraint constraint : config.getConstraints())
			System.out.println(constraint.getName()+" has "+constraint.getNumRows()+" rows");
	}

}