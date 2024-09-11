package org.opensha.sha.earthquake.rupForecastImpl.prvi25;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.opensha.commons.data.IntegerSampler.ExclusionIntegerSampler;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.CoulombRupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.RupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.inversion.ClusterSpecificInversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.ClusterSpecificInversionSolver;
import org.opensha.sha.earthquake.faultSysSolution.inversion.GridSourceProviderFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionSolver;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.SolutionProcessor;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.BinaryJumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SlipAlongRuptureModelBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_ConstraintBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SlipAlongRuptureModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.RupturePlausibilityModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SectionSupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationMFD_Adjustment;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationModelBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSectConstraintModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSeisMoRateReductions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.random.BranchSamplingManager;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.random.RandomBValSampler;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs.SubSeisMoRateReduction;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs.SupraBAverager;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.GRParticRateEstimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.AnalyticalSingleFaultInversionSolver;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.ClassicModelInversionSolver;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded.PRVI25_GridSourceBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalFaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_RegionalSeismicity;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionBValues;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionFaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader.PRVI25_SeismicityRegions;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class PRVI25_InvConfigFactory implements ClusterSpecificInversionConfigurationFactory, GridSourceProviderFactory {
	
	protected transient Table<RupSetFaultModel, RupturePlausibilityModels, FaultSystemRupSet> rupSetCache = HashBasedTable.create();
	protected transient Map<RupSetFaultModel, SectionDistanceAzimuthCalculator> distAzCache = new HashMap<>();
	protected transient File cacheDir;
	private boolean autoCache = true;
	
	private boolean adjustForActualRupSlips = NSHM23_ConstraintBuilder.ADJ_FOR_ACTUAL_RUP_SLIPS_DEFAULT;
	private boolean adjustForSlipAlong = NSHM23_ConstraintBuilder.ADJ_FOR_SLIP_ALONG_DEFAULT;
	
	public static final long NUM_ITERS_PER_RUP_DEFAULT = 2000l;
	protected long numItersPerRup;
	
	public static final boolean SOLVE_CLUSTERS_INDIVIDUALLY_DEFAULT = true;
	protected boolean solveClustersIndividually;
	
	// minimum MFD uncertainty
	public static double MFD_MIN_FRACT_UNCERT = 0.1;
	
	public static double SUB_SECT_DDW_FRACT = Double.NaN; // use default
	
	public static SubSectConstraintModels SUB_SECT_CONSTR_DEFAULT = SubSectConstraintModels.TOT_NUCL_RATE;
	
	public static SlipAlongRuptureModelBranchNode SLIP_ALONG_DEFAULT = NSHM23_SlipAlongRuptureModels.UNIFORM;
	
	public static boolean ALLOW_CONNECTED_PROXY_FAULTS_DEFAULT = false;
	private static boolean allowConnectedProxyFaults = ALLOW_CONNECTED_PROXY_FAULTS_DEFAULT;
	private static final double MAX_PROXY_FAULT_RUP_LEN_DEFAULT = 75d;
	private static double maxProxyFaultRupLen = MAX_PROXY_FAULT_RUP_LEN_DEFAULT;
	
	public PRVI25_InvConfigFactory() {
		numItersPerRup = NUM_ITERS_PER_RUP_DEFAULT;
		solveClustersIndividually = SOLVE_CLUSTERS_INDIVIDUALLY_DEFAULT;
	}
	
	public void setNumItersPerRup(long numItersPerRup) {
		Preconditions.checkState(numItersPerRup > 0l, "numItersPerRup must be >0: %s", numItersPerRup);
		this.numItersPerRup = numItersPerRup;
	}
	
	@Override
	public boolean isSolveClustersIndividually() {
		return solveClustersIndividually;
	}

	public void setSolveClustersIndividually(boolean solveClustersIndividually) {
		this.solveClustersIndividually = solveClustersIndividually;
	}

	protected synchronized FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) {
		RupSetFaultModel fm = branch.requireValue(RupSetFaultModel.class);
		RupturePlausibilityModels model = branch.getValue(RupturePlausibilityModels.class);
		if (model == null) {
			if (fm instanceof PRVI25_SubductionFaultModels) // Subduction
				model = RupturePlausibilityModels.SIMPLE_SUBDUCTION; // for now
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
			if (Double.isFinite(SUB_SECT_DDW_FRACT))
				subSects = dm.build(fm, 2, SUB_SECT_DDW_FRACT, Double.NaN);
			else
				subSects = dm.build(fm);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		RupSetConfig config = model.getConfig(subSects, scale);
		if (config instanceof CoulombRupSetConfig)
			((CoulombRupSetConfig)config).setConnectProxyFaults(allowConnectedProxyFaults);
		
		File cachedRupSetFile = null;
		if (cacheDir != null) {
			File subDir = new File(cacheDir, "rup_sets_"+fm.getFilePrefix()+"_"+dm.getFilePrefix());
			if (!subDir.exists())
				subDir.mkdir();

			double dmMoment = 0d;
			for (FaultSection sect : subSects)
				dmMoment += sect.calcMomentRate(false);
			String momentStr = ((float)dmMoment+"").replace('.', 'p');
			String rupSetFileName = "rup_set_"+model.getFilePrefix()+"_"
				+SectionDistanceAzimuthCalculator.getUniqueSectCacheFileStr(subSects)+"_"+momentStr+"_moment.zip";
			
			cachedRupSetFile = new File(subDir, rupSetFileName);
			config.setCacheDir(subDir);
		}
		config.setAutoCache(autoCache);
		
		if (cachedRupSetFile != null && cachedRupSetFile.exists()) {
			try {
				rupSet = FaultSystemRupSet.load(cachedRupSetFile);
				if (!rupSet.areSectionsEquivalentTo(subSects)) {
					rupSet = null;
				} else {
					// see if we have section distances/azimuths already cached to copy over
					SectionDistanceAzimuthCalculator distAzCalc = distAzCache.get(fm);
					if (distAzCalc != null && rupSet.areSectionsEquivalentTo(distAzCalc.getSubSections())) {
						rupSet.addModule(distAzCalc);
					} else {
						// see if we have it in a cache file
						distAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
						String name = distAzCalc.getDefaultCacheFileName();
						File distAzCacheFile = new File(cacheDir, name);
						if (distAzCacheFile.exists()) {
							try {
								distAzCalc.loadCacheFile(distAzCacheFile);
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						rupSet.addModule(distAzCalc);
						distAzCache.put(fm, distAzCalc);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				rupSet = null;
			}
		} else {
			if (cachedRupSetFile != null)
				System.out.println("Rup set cache miss, doesn't exist: "+cachedRupSetFile.getAbsolutePath());
			else
				System.out.println("No cache directory supplied, will build rupture set from scratch. Consider "
						+ "settting a cache directory to speed up rupture set building in the future.");
		}
		
		if (rupSet == null)
			rupSet = config.build(threads);
		rupSetCache.put(fm, model, rupSet);
		
		if (cachedRupSetFile != null && !cachedRupSetFile.exists()) {
			// see if we should write it
			
			boolean write = true;
			try {
				int mpiRank = mpi.MPI.COMM_WORLD.Rank();
				// if we made it this far, this is an MPI job. make sure we're rank 0
				write = mpiRank == 0;
			} catch (Throwable e) {
				// will throw if MPI not on classpath, ignore
			}
			
			if (write && !cachedRupSetFile.exists()) {
				// still doesn't exist, write it
				try {
					rupSet.write(cachedRupSetFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
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
	
	public void adjustForActualRupSlips(boolean adjustForActualRupSlips, boolean adjustForSlipAlong) {
		this.adjustForActualRupSlips = adjustForActualRupSlips;
		this.adjustForSlipAlong = adjustForSlipAlong;
	}

	@Override
	public FaultSystemRupSet buildRuptureSet(LogicTreeBranch<?> branch, int threads) throws IOException {
		// build empty-ish rup set without modules attached
		return updateRuptureSetForBranch(buildGenericRupSet(branch, threads), branch);
	}
	
	protected List<? extends FaultSection> buildSubSectsForBranch(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) throws IOException {
		RupSetFaultModel fm = branch.requireValue(RupSetFaultModel.class);
		RupSetDeformationModel dm = branch.requireValue(RupSetDeformationModel.class);
		Preconditions.checkState(dm.isApplicableTo(fm),
				"Fault and deformation models are not compatible: %s, %s", fm.getName(), dm.getName());
		if (Double.isFinite(SUB_SECT_DDW_FRACT))
			return dm.build(fm, 2, SUB_SECT_DDW_FRACT, Double.NaN);
		return dm.build(fm);
	}

	@Override
	public FaultSystemRupSet updateRuptureSetForBranch(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch)
			throws IOException {
		// we don't trust any modules attached to this rupture set as it could have been used for another calculation
		// that could have attached anything. Instead, lets only keep the ruptures themselves
		
		
		// override slip rates for the given deformation model
		List<? extends FaultSection> subSects;
		try {
			subSects = buildSubSectsForBranch(rupSet, branch);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		RuptureSubSetMappings subsetMappings = rupSet.getModule(RuptureSubSetMappings.class);
		if (subsetMappings != null) {
			// state specific, remap the DM-specific sections to this subset
			List<FaultSection> subsetSects = new ArrayList<>();
			for (int s=0; s<rupSet.getNumSections(); s++) {
				FaultSection sect = subSects.get(subsetMappings.getOrigSectID(s)).clone();
				sect.setSectionId(s);
				subsetSects.add(sect);
			}
			subSects = subsetSects;
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
		
		if (subsetMappings != null)
			rupSet.addModule(subsetMappings);
		
		SlipAlongRuptureModelBranchNode slipAlong = branch.hasValue(SlipAlongRuptureModelBranchNode.class) ?
				branch.requireValue(SlipAlongRuptureModelBranchNode.class) : SLIP_ALONG_DEFAULT;
		rupSet.addModule(slipAlong.getModel());
		
		// add other modules
		return getSolutionLogicTreeProcessor().processRupSet(rupSet, branch);
	}

	@Override
	public SolutionProcessor getSolutionLogicTreeProcessor() {
		return new PRVI25SolProcessor();
	}
	
	public static class PRVI25SolProcessor implements SolutionProcessor {
		
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
			if (branch.hasValue(SlipAlongRuptureModelBranchNode.class)) {
				// force replacement as rupture sets can have a default slip along rupture model attached, which we
				// need to make sure to override
				SlipAlongRuptureModel model = branch.getValue(SlipAlongRuptureModelBranchNode.class).getModel();
				if (rupSet.getModule(SlipAlongRuptureModel.class) != model)
					rupSet.addModule(model);
			}
			
			RupSetFaultModel fm = branch.getValue(RupSetFaultModel.class);
			
			// named faults, regions of interest
			if (fm != null)
				fm.attachDefaultModules(rupSet);
			
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
					SubSeisMoRateReduction moRateRed = branch.hasValue(SubSeisMoRateReductions.class) ?
							branch.getValue(SubSeisMoRateReductions.class).getChoice() :
								SupraSeisBValInversionTargetMFDs.SUB_SEIS_MO_RATE_REDUCTION_DEFAULT;
					SupraSeisBValInversionTargetMFDs.Builder builder;
					RandomBValSampler.Node bValNode = branch.getValue(RandomBValSampler.Node.class);
					if (bValNode != null) {
						RandomBValSampler sampler = rupSet.requireModule(BranchSamplingManager.class).getSampler(bValNode);
						builder = new SupraSeisBValInversionTargetMFDs.Builder(rupSet, sampler.getBValues());
					} else {
						builder = new SupraSeisBValInversionTargetMFDs.Builder(rupSet,  branch.requireValue(SectionSupraSeisBValues.class));
					}
					return builder.subSeisMoRateReduction(moRateRed).buildSlipRatesOnly();
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
			
			if (BranchSamplingManager.hasSamplingNodes(branch)) {
				rupSet.offerAvailableModule(new Callable<BranchSamplingManager>() {

					@Override
					public BranchSamplingManager call() throws Exception {
						return new BranchSamplingManager(rupSet, branch);
					}
				}, BranchSamplingManager.class);
			}
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
			
			if (!sol.hasModule(LogicTreeBranch.class) || !branch.equals(sol.getModule(LogicTreeBranch.class)))
				sol.addModule(branch);
			
			return sol;
		}
		
	}
	
	private static NSHM23_ConstraintBuilder getAveragedConstraintBuilder(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		// TODO maybe add sampling support?
		SectionSupraSeisBValues[] bVals;
		if (branch.hasValue(SupraSeisBValues.AVERAGE))
			bVals = SupraSeisBValues.values();
		else if (branch.hasValue(PRVI25_SubductionBValues.AVERAGE))
			bVals = PRVI25_SubductionBValues.values();
		else
			bVals = new SectionSupraSeisBValues[] { branch.requireValue(SectionSupraSeisBValues.class) };
		
		NSHM23_SegmentationModels[] segModels;
		if (branch.hasValue(NSHM23_SegmentationModels.AVERAGE))
			segModels = NSHM23_SegmentationModels.values();
		else
			segModels = new NSHM23_SegmentationModels[] { branch.requireValue(NSHM23_SegmentationModels.class) };

		List<LogicTreeBranch<?>> avgBranches = new ArrayList<>();
		List<Double> avgWeights = new ArrayList<>();
		
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
		for (int i=0; i<branch.size(); i++)
			levels.add(branch.getLevel(i));
		LogicTreeBranch<LogicTreeNode> branchCopy = new LogicTreeBranch<>(levels);
		for (LogicTreeNode node : branch)
			branchCopy.setValue(node);
		for (SectionSupraSeisBValues bVal : bVals) {
			for (NSHM23_SegmentationModels segModel : segModels) {
				// create copy of the branch
				LogicTreeBranch<LogicTreeNode> subBranch = branchCopy.copy();
				// now override our values
				subBranch.setValue(bVal);
				subBranch.setValue(segModel);
				
				double subBranchWeight = 1d;
				if (bVals.length > 1)
					subBranchWeight *= bVal.getNodeWeight(subBranch);
				if (segModels.length > 1)
					subBranchWeight *= segModel.getNodeWeight(subBranch);
				if (subBranchWeight > 0d) {
					avgBranches.add(subBranch);
					avgWeights.add(subBranchWeight);
				}
			}
		}
		
		System.out.println("Building average SupraSeisBValTargetMFDs across "+avgBranches.size()+" sub-branches");
		Preconditions.checkState(avgBranches.size() > 1, "Expected multiple branches to average");
		SupraBAverager averager = new SupraBAverager();
		
		for (int i=0; i<avgBranches.size(); i++) {
			LogicTreeBranch<?> avgBranch = avgBranches.get(i);
			double weight = avgWeights.get(i);
			
			String branchStr = null;
			if (bVals.length > 1)
				branchStr = avgBranch.requireValue(SectionSupraSeisBValues.class).getShortName();
			if (segModels.length > 1) {
				if (branchStr == null)
					branchStr = "";
				else
					branchStr += ", ";
				branchStr += "SegModel="+avgBranch.requireValue(NSHM23_SegmentationModels.class).name();
			}
			
			System.out.println("Building target MFDs for branch "+i+"/"+avgBranches.size()+": "+branchStr);
			
			NSHM23_ConstraintBuilder builder = doGetConstraintBuilder(rupSet, avgBranch);
			averager.process(builder.getTargetMFDs(), weight);
		}
		
		SupraSeisBValInversionTargetMFDs avgTargets = averager.getSupraSeisAverageInstance();
		NSHM23_ConstraintBuilder ret = doGetConstraintBuilder(rupSet, branch);
		ret.setExternalTargetMFDs(avgTargets);
		rupSet.addModule(avgTargets);
		return ret;
	}
	
	private static NSHM23_ConstraintBuilder getConstraintBuilder(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		if (branch.hasValue(NSHM23_SegmentationModels.AVERAGE) || branch.hasValue(SupraSeisBValues.AVERAGE) || branch.hasValue(PRVI25_SubductionBValues.AVERAGE))
			// return averaged instance, looping over b-values and/or segmentation branches
			return getAveragedConstraintBuilder(rupSet, branch);
		return doGetConstraintBuilder(rupSet, branch);
	}
	
	private static NSHM23_ConstraintBuilder doGetConstraintBuilder(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		RandomBValSampler.Node bValNode = branch.getValue(RandomBValSampler.Node.class);
		double bVal;
		double[] sectSpecificBValues = null;
		if (bValNode != null) {
			RandomBValSampler sampler = rupSet.requireModule(BranchSamplingManager.class).getSampler(bValNode);
			sectSpecificBValues = sampler.getBValues();
			Preconditions.checkState(sectSpecificBValues.length == rupSet.getNumSections(),
					"Have %s sections but %s section b-values", rupSet.getNumSections(), sectSpecificBValues.length);
			bVal = NSHM23_ConstraintBuilder.momentWeightedAverage(rupSet, sectSpecificBValues);
		} else {
			SectionSupraSeisBValues bValues = branch.requireValue(SectionSupraSeisBValues.class);
			sectSpecificBValues = bValues.getSectBValues(rupSet);
			if (Double.isFinite(bValues.getB()))
				bVal = bValues.getB();
			else
				bVal = SectionSupraSeisBValues.momentWeightedAverage(rupSet, sectSpecificBValues);
		}
		NSHM23_ConstraintBuilder constrBuilder = new NSHM23_ConstraintBuilder(rupSet, bVal, sectSpecificBValues);
		
		SubSeisMoRateReduction reduction = SupraSeisBValInversionTargetMFDs.SUB_SEIS_MO_RATE_REDUCTION_DEFAULT;
		if (branch.hasValue(SubSeisMoRateReductions.class))
			reduction = branch.getValue(SubSeisMoRateReductions.class).getChoice();
		
		constrBuilder.subSeisMoRateReduction(reduction);
		
		constrBuilder.magDepRelStdDev(M->MFD_MIN_FRACT_UNCERT*Math.max(1, Math.pow(10, bVal*0.5*(M-6))));
		
		constrBuilder.adjustForActualRupSlips(NSHM23_ConstraintBuilder.ADJ_FOR_ACTUAL_RUP_SLIPS_DEFAULT,
				NSHM23_ConstraintBuilder.ADJ_FOR_SLIP_ALONG_DEFAULT);
		
		// apply any segmentation adjustments
		if (hasJumps(rupSet)) {
			// this handles creeping section, binary segmentation, and max dist models
			BinaryRuptureProbabilityCalc rupExclusionModel = getExclusionModel(
					rupSet, branch, rupSet.requireModule(ClusterRuptures.class));
			
			if (rupExclusionModel != null)
				constrBuilder.excludeRuptures(rupExclusionModel);
			
			JumpProbabilityCalc targetSegModel = buildSegModel(rupSet, branch);
			
			if (targetSegModel != null) {
				if (targetSegModel instanceof BinaryJumpProbabilityCalc) {
					// already taken care of above
					Preconditions.checkNotNull(rupExclusionModel);
				} else {
					SegmentationMFD_Adjustment segAdj = branch.getValue(SegmentationMFD_Adjustment.class);
					if (segAdj == null)
						// use default adjustment
						constrBuilder.adjustForSegmentationModel(targetSegModel);
					else
						constrBuilder.adjustForSegmentationModel(targetSegModel, segAdj);
				}
			}
		}
		
		return constrBuilder;
	}
	
	public static boolean hasJumps(FaultSystemRupSet rupSet) {
		for (ClusterRupture cRup : rupSet.requireModule(ClusterRuptures.class))
			if (cRup.getTotalNumJumps() > 0)
				return true;
		return false;
	}
	
	public static boolean hasPaleoData(FaultSystemRupSet rupSet) {
		PaleoseismicConstraintData data = rupSet.getModule(PaleoseismicConstraintData.class);
		return data != null && (data.hasPaleoRateConstraints() || data.hasPaleoSlipConstraints());
	}
	
	/**
	 * @param rupSet
	 * @param segModel
	 * @return true if we have any ruptures with jumps with P>0 and P<1
	 */
	private static boolean hasConstrainableJumps(FaultSystemRupSet rupSet, JumpProbabilityCalc segModel) {
		if (segModel instanceof BinaryJumpProbabilityCalc)
			return false;
		for (ClusterRupture cRup : rupSet.requireModule(ClusterRuptures.class)) {
			for (Jump jump : cRup.getJumpsIterable()) {
				double prob = segModel.calcJumpProbability(cRup, jump, false);
				if (prob > 0 && prob < 1)
					return true;
			}
		}
		return false;
	}
	
	private static JumpProbabilityCalc buildSegModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		SegmentationModelBranchNode segModel = branch.getValue(SegmentationModelBranchNode.class);
		JumpProbabilityCalc jumpProb = segModel == null ? null : segModel.getModel(rupSet, branch);
		return jumpProb;
	}

	@Override
	public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
			int threads) {
		NSHM23_ConstraintBuilder constrBuilder = getConstraintBuilder(rupSet, branch);
		
		if (rupSet.hasModule(ModSectMinMags.class) && rupSet.hasModule(RuptureSubSetMappings.class)) {
			// this is a cluster-specific inversion, make sure that we actually have ruptures above the minimum magnitude
			int numBelowMinMag = constrBuilder.getRupIndexesBelowMinMag().size();
			Preconditions.checkState(numBelowMinMag >= 0 && numBelowMinMag <= rupSet.getNumRuptures());
			if (numBelowMinMag == rupSet.getNumRuptures()) {
				System.out.println("Warning: skipping cluster specific inversion for cluster with "
						+rupSet.getNumSections()+" sections and "+rupSet.getNumRuptures()
						+" ruptures, all below minimum mag");
				return null;
			}
		}
		
		// make sure that we have at least 1 nonzero slip rate (most likely to happen with cluster-specific inversions)
		boolean hasNonZeroSlip = false;
		for (double slipRate : rupSet.requireModule(SectSlipRates.class).getSlipRates()) {
			if (slipRate > 0d) {
				hasNonZeroSlip = true;
				break;
			}
		}
		if (!hasNonZeroSlip) {
			System.out.println("Warning: skipping inversion for rupture set with "+rupSet.getNumSections()
					+" sections and "+rupSet.getNumRuptures()+" ruptures, no positive slip rates");
			return null;
		}
		
		constrBuilder.adjustForActualRupSlips(adjustForActualRupSlips, adjustForSlipAlong);
		
		SubSectConstraintModels constrModel = branch.hasValue(SubSectConstraintModels.class) ?
				branch.getValue(SubSectConstraintModels.class) : SUB_SECT_CONSTR_DEFAULT;
		
		double slipWeight = 1d;
		double mfdWeight = constrModel == SubSectConstraintModels.NUCL_MFD ? 1 : 10;
		double nuclWeight = constrModel == SubSectConstraintModels.TOT_NUCL_RATE ? 0.5 : 0d;
		double nuclMFDWeight = constrModel == SubSectConstraintModels.NUCL_MFD ? 0.5 : 0d;
		
		if (slipWeight > 0d)
			constrBuilder.slipRates().weight(slipWeight);
		
		if (mfdWeight > 0d)
			constrBuilder.supraBValMFDs().weight(mfdWeight);
		
		if (nuclWeight > 0d)
			constrBuilder.sectSupraRates().weight(nuclWeight);
		
		if (nuclMFDWeight > 0d)
			constrBuilder.sectSupraNuclMFDs().weight(nuclMFDWeight);
		
		// this will skip any ruptures below the minimim magnitude
		ExclusionIntegerSampler sampler = rupSet.hasModule(ModSectMinMags.class) ? constrBuilder.getSkipBelowMinSampler() : null;
		
		// this will exclude ruptures through creeping section, if applicable 
		BinaryRuptureProbabilityCalc rupExcludeModel = constrBuilder.getRupExclusionModel();
		if (rupExcludeModel != null)
			sampler = getExcludeSampler(rupSet.requireModule(ClusterRuptures.class), sampler, rupExcludeModel);
		
		List<InversionConstraint> constraints = constrBuilder.build();
		
		SupraSeisBValInversionTargetMFDs targetMFDs = rupSet.requireModule(SupraSeisBValInversionTargetMFDs.class);
		
		GRParticRateEstimator rateEst = new GRParticRateEstimator(rupSet, targetMFDs);
		
		JumpProbabilityCalc segModel = buildSegModel(rupSet, branch);
		if (segModel != null && hasConstrainableJumps(rupSet, segModel)) {
			constraints = new ArrayList<>(constraints);
			
//			InitialModelParticipationRateEstimator rateEst = new InitialModelParticipationRateEstimator(
//					rupSet, Inversions.getDefaultVariablePerturbationBasis(rupSet));

//			double weight = 0.5d;
//			boolean ineq = false;
			double weight = 100000d;
			boolean ineq = true;
			
			constraints.add(new JumpProbabilityConstraint.RelativeRate(
					weight, ineq, rupSet, buildSegModel(rupSet, branch), rateEst));
		}
		
		int avgThreads = Integer.max(1, threads / 4);
		
//		CompletionCriteria completion = new IterationsPerVariableCompletionCriteria(5000d);
		// the greater of 2,000 iterations per rupture, but floor the rupture count to be at least 100 times the number
		// of sections, which comes out to a minimum of 200,000 iterations per section
		int numRups = rupSet.getNumRuptures();
		if (sampler != null)
			// only count ruptures we can actually sample
			numRups = sampler.size();
		long equivNumVars = Long.max(numRups, rupSet.getNumSections()*100l);
		CompletionCriteria completion = new IterationCompletionCriteria(equivNumVars*numItersPerRup);
		CompletionCriteria subCompletion = new IterationCompletionCriteria(equivNumVars);
		CompletionCriteria avgCompletion = new IterationCompletionCriteria(equivNumVars*50l);
		
		InversionConfiguration.Builder builder = InversionConfiguration.builder(constraints, completion)
				.threads(threads)
				.subCompletion(subCompletion)
				.avgThreads(avgThreads, avgCompletion)
				.perturbation(GenerationFunctionType.VARIABLE_EXPONENTIAL_SCALE)
				.nonNegativity(NonnegativityConstraintType.TRY_ZERO_RATES_OFTEN)
				.sampler(sampler)
				.reweight()
				.variablePertubationBasis(rateEst.estimateRuptureRates());
		
		return builder.build();
	}
	
	public static BinaryRuptureProbabilityCalc getExclusionModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
			ClusterRuptures cRups) {
		// segmentation model
		List<BinaryRuptureProbabilityCalc> exclusionModels = new ArrayList<>();
		SegmentationModelBranchNode segChoice = branch.getValue(SegmentationModelBranchNode.class);
		if (segChoice != null) {
			BinaryRuptureProbabilityCalc exclusionModel = segChoice.getExclusionModel(rupSet, branch);
			if (exclusionModel != null)
				exclusionModels.add(exclusionModel);
		}
		
		if (!allowConnectedProxyFaults) {
			System.out.println("Excluding jumps to/from proxy faults");
			exclusionModels.add(new ProxyConnectionExclusionModel());
		}
		
		if (maxProxyFaultRupLen > 0d) {
			System.out.println("Excluding proxy fault ruptures longer than "+(float)maxProxyFaultRupLen+" km");
			exclusionModels.add(new ProxyMaxLenExclusionModel(maxProxyFaultRupLen));
		}
		
		if (exclusionModels.isEmpty())
			return null;
		if (exclusionModels.size() == 1)
			return exclusionModels.get(0);
		
		System.out.println("Combining "+exclusionModels.size()+" exclusion models");
		
		return new RuptureProbabilityCalc.LogicalAnd(exclusionModels.toArray(new BinaryRuptureProbabilityCalc[0]));
	}
	
	private static class ProxyConnectionExclusionModel implements BinaryJumpProbabilityCalc {

		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}

		@Override
		public String getName() {
			return "Proxy Fault Connections Excluded";
		}

		@Override
		public boolean isJumpAllowed(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			return !jump.fromSection.isProxyFault() && !jump.toSection.isProxyFault();
		}
		
	}
	
	private static class ProxyMaxLenExclusionModel implements BinaryRuptureProbabilityCalc {
		
		private double maxLen;

		public ProxyMaxLenExclusionModel(double maxLen) {
			this.maxLen = maxLen;
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}

		@Override
		public String getName() {
			return "Proxy Fault Connections Excluded";
		}

		@Override
		public boolean isRupAllowed(ClusterRupture fullRupture, boolean verbose) {
			double proxyLen = 0d;
			for (FaultSubsectionCluster cluster : fullRupture.getClustersIterable()) {
				for (FaultSection sect : cluster.subSects) {
					if (sect.isProxyFault()) {
						proxyLen += sect.getTraceLength();
						if (proxyLen > maxLen)
							return false;
					}
				}
			}
			return true;
		}
		
	}
	
	@Override
	public InversionSolver getSolver(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		NSHM23_SegmentationModels segModel = branch.getValue(NSHM23_SegmentationModels.class);
		if (segModel == NSHM23_SegmentationModels.CLASSIC || segModel == NSHM23_SegmentationModels.CLASSIC_FULL) {
			// it's a classic model
			if (isSolveClustersIndividually()) {
				// solve clusters individually, can handle mixed clusters and single-fault analytical
				System.out.println("Returning classic model solver");
				ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
				return new ClassicModelInversionSolver(rupSet, branch, getExclusionModel(rupSet, branch, cRups));
			} else if (!hasPaleoData(rupSet)) {
				// see if we can solve the whole thing analytically (can do if all multifault rups are excluded)
				// but only if we don't have paleo/parkfield constraints
				ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
				BinaryRuptureProbabilityCalc exclusionModel = getExclusionModel(rupSet, branch, cRups);
				boolean hasIncludedJump = false;
				for (ClusterRupture cRup : cRups) {
					int numJumps = cRup.getTotalNumJumps();
					if (numJumps > 0 && (exclusionModel == null || exclusionModel.isRupAllowed(cRup, false))) {
						hasIncludedJump = true;
						break;
					}
				}
				if (!hasIncludedJump) {
					// can solve analytically
					return new AnalyticalSingleFaultInversionSolver(exclusionModel);
				}
			}
			System.err.println("WARNING: solving classic model via full system inversion");
			// have to do a full system inversion
			return new InversionSolver.Default();
		} else if (isSolveClustersIndividually()) {
			return new ExclusionAwareClusterSpecificInversionSolver();
		} else {
			return new InversionSolver.Default();
		}
	}
	
	private static class ExclusionAwareClusterSpecificInversionSolver extends ClusterSpecificInversionSolver {

		@Override
		protected BinaryRuptureProbabilityCalc getRuptureExclusionModel(FaultSystemRupSet rupSet,
				LogicTreeBranch<?> branch) {
			return getExclusionModel(rupSet, branch, rupSet.requireModule(ClusterRuptures.class));
		}
		
	}

	private static ExclusionIntegerSampler getExcludeSampler(ClusterRuptures cRups,
			ExclusionIntegerSampler currentSampler, BinaryRuptureProbabilityCalc excludeCalc) {
		HashSet<Integer> skips = new HashSet<>();
		for (int r=0; r<cRups.size(); r++) {
//			if (r % 1000 == 0)
//				System.out.println("Prob for r="+r+": "+model.calcRuptureProb(cRups.get(r), false));
			if (!excludeCalc.isRupAllowed(cRups.get(r), false))
				skips.add(r);
		}
		System.out.println("\tSkipped "+skips.size()+" ruptures");
		if (skips.isEmpty())
			return currentSampler;
		ExclusionIntegerSampler skipSampler = new ExclusionIntegerSampler(0, cRups.size(), skips);
		if (currentSampler == null)
			return skipSampler;
		else
			return currentSampler.getCombinedWith(skipSampler);
	}

	@Override
	public LogicTree<?> getGridSourceTree(LogicTree<?> faultTree) {
		if (faultTree.getBranch(0).hasValue(PRVI25_CrustalFaultModels.class))
			return LogicTree.buildExhaustive(PRVI25_LogicTreeBranch.levelsCrustalOffFault, true);
		if (faultTree.getBranch(0).hasValue(PRVI25_SubductionFaultModels.class))
			return LogicTree.buildExhaustive(PRVI25_LogicTreeBranch.levelsSubductionGridded, true);
		return null;
	}

	@Override
	public GridSourceProvider buildGridSourceProvider(FaultSystemSolution sol, LogicTreeBranch<?> fullBranch)
			throws IOException {
		if (fullBranch.hasValue(PRVI25_CrustalFaultModels.class))
			return PRVI25_GridSourceBuilder.buildCrustalGridSourceProv(sol, fullBranch);
		if (fullBranch.hasValue(PRVI25_SubductionFaultModels.class))
			return PRVI25_GridSourceBuilder.buildCombinedSubductionGridSourceList(sol, fullBranch);
		throw new IllegalStateException("Unexpected logic tree branch: "+fullBranch);
	}
	
	@Override
	public void preGridBuildHook(FaultSystemSolution sol, LogicTreeBranch<?> faultBranch) throws IOException {
		PRVI25_GridSourceBuilder.doPreGridBuildHook(sol, faultBranch);
	}
	
	public static class LimitCrustalBelowObserved extends PRVI25_InvConfigFactory {
		
		static double LIMIT_FRACT = 0.9;
		static double WEIGHT = 1000d;

		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			
			EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(rupSet.getMaxMag());
			IncrementalMagFreqDist obsMFD = PRVI25_RegionalSeismicity.PREFFERRED.build(PRVI25_SeismicityRegions.CRUSTAL, refMFD,
					refMFD.getX(refMFD.getClosestXIndex(rupSet.getMaxMag())));
			RuptureSubSetMappings subsetMappings = rupSet.getModule(RuptureSubSetMappings.class);
			if (LIMIT_FRACT != 1d)
				obsMFD.scale(LIMIT_FRACT);
			if (subsetMappings != null) {
				// we're inverting a subset, need to reduce
				IncrementalMagFreqDist subsetTargets = rupSet.requireModule(InversionTargetMFDs.class).getTotalOnFaultSupraSeisMFD();
				IncrementalMagFreqDist origTargets = subsetMappings.getOrigRupSet().requireModule(InversionTargetMFDs.class).getTotalOnFaultSupraSeisMFD();
				Preconditions.checkState(subsetTargets.getMinX() == origTargets.getMinX());
				Preconditions.checkState(subsetTargets.getMinX() == obsMFD.getMinX());
				for (int i=0; i<obsMFD.size()&&i<subsetTargets.size(); i++) {
					double subsetRate = subsetTargets.getY(i);
					if (subsetRate > 0d) {
						double origRate = origTargets.getY(i);
						double obsRate = obsMFD.getY(i);
//						if (origRate > obsRate) {
							// need to reduce
							obsMFD.set(i, obsRate * subsetRate/origRate);
//						}
					}
				}
			}
			
			MFDInversionConstraint constraint = new MFDInversionConstraint(
					rupSet, WEIGHT, true, ConstraintWeightingType.NORMALIZED, List.of(obsMFD));
			
			config = InversionConfiguration.builder(config).add(constraint).build();
			
			return config;
		}
		
	}

}
