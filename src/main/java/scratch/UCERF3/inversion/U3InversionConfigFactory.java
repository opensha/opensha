package scratch.UCERF3.inversion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
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
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.SolutionProcessor;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.logicTree.U3LogicTreeBranchNode;
import scratch.UCERF3.utils.aveSlip.U3AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.U3PaleoRateConstraint;

/**
 * Inversion configuration factory for UCERF3 in the new (2021) framework, and also using new annealing defaults
 * 
 * @author kevin
 *
 */
public class U3InversionConfigFactory implements InversionConfigurationFactory {
	
	protected FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) {
		return new RuptureSets.U3RupSetConfig(branch.requireValue(FaultModels.class),
					branch.requireValue(ScalingRelationships.class)).build(threads);
	}

	@Override
	public FaultSystemRupSet buildRuptureSet(LogicTreeBranch<?> branch, int threads) {
		// build empty-ish rup set without modules attached
		FaultSystemRupSet rupSet = buildGenericRupSet(branch, threads);
		
		U3LogicTreeBranch u3Branch;
		if (branch instanceof U3LogicTreeBranch) {
			u3Branch = (U3LogicTreeBranch)branch;
		} else {
			List<U3LogicTreeBranchNode<?>> nodes = new ArrayList<>();
			for (LogicTreeNode node : branch) {
				Preconditions.checkState(node instanceof U3LogicTreeBranchNode<?>);
				nodes.add((U3LogicTreeBranchNode<?>) node);
			}
			u3Branch = U3LogicTreeBranch.fromValues(nodes);
		}
		
		return FaultSystemRupSet.buildFromExisting(rupSet).forU3Branch(u3Branch).build();
	}

	@Override
	public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
			int threads) {
		org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs targetMFDs =
				rupSet.requireModule(org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs.class);
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
		return new SolutionLogicTree.UCERF3_SolutionProcessor();
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
		protected synchronized FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) {
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
	
	private static class CoulombU3SolProcessor extends SolutionLogicTree.UCERF3_SolutionProcessor {

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
	
	public static void main(String[] args) {
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
