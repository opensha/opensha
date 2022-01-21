package scratch.UCERF3.inversion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration.Builder;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDLaplacianSmoothingInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.ParkfieldInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.TimeCompletionCriteria;
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
import org.opensha.sha.faultSurface.FaultSection;
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
	
	protected FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) throws IOException {
		return new RuptureSets.U3RupSetConfig(branch.requireValue(FaultModels.class),
					branch.requireValue(ScalingRelationships.class)).build(threads);
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

		List<InversionConstraint> constraints =  new UCERF3InversionInputGenerator(rupSet, config, paleoRateConstraints,
				aveSlipConstraints, improbabilityConstraint, paleoProbabilityModel).getConstraints();
		
		int avgThreads = threads / 4;
		
		return InversionConfiguration.builder(constraints, TimeCompletionCriteria.getInHours(5l))
				.threads(threads)
				.avgThreads(avgThreads, TimeCompletionCriteria.getInMinutes(5l))
				.perturbation(GenerationFunctionType.VARIABLE_EXPONENTIAL_SCALE)
				.nonNegativity(NonnegativityConstraintType.TRY_ZERO_RATES_OFTEN)
				.build();
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
	
	public static class CoulombRupSet extends U3InversionConfigFactory {
		
		Map<FaultModels, FaultSystemRupSet> rupSetCache = new HashMap<>();

		@Override
		protected synchronized FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) throws IOException {
			FaultModels fm = branch.requireValue(FaultModels.class);
			// check cache
			FaultSystemRupSet rupSet = rupSetCache.get(fm);
			if (rupSet != null)
				return rupSet;
			// need to build one
			rupSet = new RuptureSets.CoulombRupSetConfig(fm,
					branch.requireValue(ScalingRelationships.class)).build(threads);
			// cache it
			rupSetCache.put(fm, rupSet);
			return rupSet;
		}

		@Override
		public SolutionProcessor getSolutionLogicTreeProcessor() {
			return new CoulombU3SolProcessor();
		}
		
	}
	
	private static class CoulombU3SolProcessor extends UCERF3_SolutionProcessor {

		@Override
		public FaultSystemRupSet processRupSet(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			rupSet = super.processRupSet(rupSet, branch);
			if (!rupSet.hasModule(PlausibilityConfiguration.class)) {
				// for branch averaging
				rupSet.addAvailableModule(new Callable<PlausibilityConfiguration>() {

					@Override
					public PlausibilityConfiguration call() throws Exception {
						FaultModels fm = branch.requireValue(FaultModels.class);
						return new RuptureSets.CoulombRupSetConfig(fm,
								branch.requireValue(ScalingRelationships.class)).getPlausibilityConfig();
					}
				}, PlausibilityConfiguration.class);
				
			}
			return rupSet;
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
