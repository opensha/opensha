package org.opensha.sha.earthquake.rupForecastImpl.nshm23;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.IntegerSampler.ExclusionIntegerSampler;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.CubedGriddedRegion;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.RupSetSubsectioningModel;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.RupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.inversion.ClusterSpecificInversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.ClusterSpecificInversionSolver;
import org.opensha.sha.earthquake.faultSysSolution.inversion.GridSourceProviderFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionSolver;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.LaplacianSmoothingInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.ParkfieldInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SectionTotalRateConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultCubeAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultCubeAssociations.StitchedFaultCubeAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats.MisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.SolutionProcessor;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.BinaryJumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.ConnectivityCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionUtils;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.MaxMagOffFaultBranchNode;
import org.opensha.sha.earthquake.faultSysSolution.util.SlipAlongRuptureModelBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_ConstraintBuilder.ParkfieldSelectionCriteria;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.data.NSHM23_PaleoDataLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_AbstractGridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_CombinedRegionGridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_FaultCubeAssociations;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_GridFocalMechs;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_SingleRegionGridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeclusteringAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_PaleoUncertainties;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_RegionalSeismicity;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels.ExcludeRupsThroughCreepingSegmentationModel;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.random.BranchSamplingManager;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.random.RandomBValSampler;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SeisSmoothingAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SingleStates;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SlipAlongRuptureModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_U3_HybridLogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.RupsThroughCreepingSectBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.RupturePlausibilityModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SectionSupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationMFD_Adjustment;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationModelBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSectConstraintModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSeisMoRateReductions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.U3_UncertAddDeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.prior2018.NSHM18_FaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs.SubSeisMoRateReduction;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs.SupraBAverager;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.GRParticRateEstimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.AnalyticalSingleFaultInversionSolver;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.ClassicModelInversionSolver;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.SeismicityRegions;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SparseGutenbergRichterSolver;
import org.opensha.sha.magdist.SparseGutenbergRichterSolver.SpreadingMethod;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.erf.ETAS.SeisDepthDistribution;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.griddedSeismicity.GridReader;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;

public class NSHM23_InvConfigFactory implements ClusterSpecificInversionConfigurationFactory, GridSourceProviderFactory {

	protected transient RuptureSets.Cache rupSetCache = new RuptureSets.Cache();
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
	
//	public static double MIN_MAG_FOR_SEISMOGENIC_RUPS = 6d;
	// no longer apply min mag. this was necessary for U3 target MFDs, but not so this time around
	public static double MIN_MAG_FOR_SEISMOGENIC_RUPS = 0d;
	
	public static boolean PARKFIELD_INITIAL = true;
	
	public static SubSectConstraintModels SUB_SECT_CONSTR_DEFAULT = SubSectConstraintModels.TOT_NUCL_RATE;
	
	public static SlipAlongRuptureModelBranchNode SLIP_ALONG_DEFAULT = NSHM23_SlipAlongRuptureModels.UNIFORM;
	
	public NSHM23_InvConfigFactory() {
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
	
	private synchronized void checkAddDistAzCalc(FaultSystemRupSet rupSet, RupSetFaultModel fm) {
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

	protected synchronized FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) {
		RupSetFaultModel fm = branch.requireValue(RupSetFaultModel.class);
		RupSetSubsectioningModel ssm = branch.requireValue(RupSetSubsectioningModel.class);
		RupturePlausibilityModels model = branch.getValue(RupturePlausibilityModels.class);
		if (model == null) {
			if (fm instanceof FaultModels) // UCERF3 FM
				model = RupturePlausibilityModels.UCERF3; // for now
			else
				model = RupturePlausibilityModels.COULOMB;
		}
		
		NSHM23_SingleStates state = branch.getValue(NSHM23_SingleStates.class);
		
		// check cache
		FaultSystemRupSet rupSet = rupSetCache.get(fm, ssm, model);
		if (rupSet != null) {
			if (state != null)
				rupSet = state.getRuptureSubSet(rupSet);
			return rupSet;
		}
		
		RupSetScalingRelationship scale = branch.requireValue(RupSetScalingRelationship.class);
		
		RupSetDeformationModel dm = fm.getDefaultDeformationModel();
		List<? extends FaultSection> subSects;
		try {
			subSects = dm.build(fm, ssm, branch);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		RupSetConfig config = model.getConfig(subSects, scale);
		
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
					checkAddDistAzCalc(rupSet, fm);
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
		rupSetCache.put(rupSet, fm, ssm, model);
		
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
		if (state != null)
			rupSet = state.getRuptureSubSet(rupSet);
		
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
		RupSetDeformationModel dm = branch.requireValue(RupSetDeformationModel.class);
		return dm.build(branch);
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
		checkAddDistAzCalc(rupSet, branch.requireValue(RupSetFaultModel.class));
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
	
	public static ParkfieldSelectionCriteria getParkfieldSelectionCriteria(RupSetFaultModel fm) {
		if (fm instanceof FaultModels)
			// UCERF3
			return ParkfieldSelectionCriteria.SECT_COUNT;
		return NSHM23_ConstraintBuilder.PARKFIELD_SELECT_DEFAULT;
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
				
			if (fm == FaultModels.FM2_1 || fm == FaultModels.FM3_1 || fm == FaultModels.FM3_2) {
				// include the UERF3 parkfield hack for modified section min mags
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
				// add paleoseismic data
				rupSet.offerAvailableModule(new Callable<PaleoseismicConstraintData>() {

					@Override
					public PaleoseismicConstraintData call() throws Exception {
						PaleoseismicConstraintData data = PaleoseismicConstraintData.loadUCERF3(rupSet);
						
						if (!NSHM23_PaleoDataLoader.INCLUDE_U3_PALEO_SLIP)
							// remove paleo ave slip data
							data = new PaleoseismicConstraintData(rupSet,
									data.getPaleoRateConstraints(), data.getPaleoProbModel(), null, null);
						return data;
					}
				}, PaleoseismicConstraintData.class);
			} else {
				if (fm instanceof NSHM18_FaultModels) {
					// NSHM18
					// NSHM23 paleo data
					rupSet.offerAvailableModule(new Callable<PaleoseismicConstraintData>() {

						@Override
						public PaleoseismicConstraintData call() throws Exception {
							PaleoseismicConstraintData ret;
							if (NSHM18_FaultModels.USE_NEW_PALEO_DATA) {
								double prevDistOtherContained = NSHM23_PaleoDataLoader.LOC_MAX_DIST_OTHER_CONTAINED;
								// loosen things up a bit to get better mappings for the older fault model
								NSHM23_PaleoDataLoader.LOC_MAX_DIST_OTHER_CONTAINED = 5d;
								ret = NSHM23_PaleoDataLoader.load(rupSet);
								NSHM23_PaleoDataLoader.LOC_MAX_DIST_OTHER_CONTAINED = prevDistOtherContained;
							} else {
								// UCERF3 paleo data
								ret = PaleoseismicConstraintData.loadUCERF3(rupSet);
								if (!NSHM23_PaleoDataLoader.INCLUDE_U3_PALEO_SLIP) {
									// clear out paleo slip data
									ret = new PaleoseismicConstraintData(rupSet,
											ret.getPaleoRateConstraints(), ret.getPaleoProbModel(), null, null);
								}
							}
							return ret;
						}
					}, PaleoseismicConstraintData.class);
				} else if (fm instanceof NSHM23_FaultModels) {
					// NSHM23 paleo data
					rupSet.offerAvailableModule(new Callable<PaleoseismicConstraintData>() {

						@Override
						public PaleoseismicConstraintData call() throws Exception {
							return NSHM23_PaleoDataLoader.load(rupSet);
						}
					}, PaleoseismicConstraintData.class);
				}
				
				// regular system-wide minimum magnitudes
				if (MIN_MAG_FOR_SEISMOGENIC_RUPS > 0) {
					rupSet.offerAvailableModule(new Callable<ModSectMinMags>() {

						@Override
						public ModSectMinMags call() throws Exception {
							ModSectMinMags minMags = ModSectMinMags.above(rupSet, MIN_MAG_FOR_SEISMOGENIC_RUPS, true);
							// modify for parkfield if needed
							List<Integer> parkRups = NSHM23_ConstraintBuilder.findParkfieldRups(
									rupSet, getParkfieldSelectionCriteria(fm));
							if (parkRups != null && !parkRups.isEmpty()) {
								EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(rupSet);
								int minParkBin = -1;
								double minParkMag = Double.POSITIVE_INFINITY;
								for (int parkRup : parkRups) {
									if (minMags.isRupBelowSectMinMag(parkRup, refMFD)) {
										double mag = rupSet.getMagForRup(parkRup);
										int bin = refMFD.getClosestXIndex(mag);
										if (minParkBin < 0)
											minParkBin = bin;
										else
											minParkBin = Integer.min(minParkBin, bin);
										minParkMag = Math.min(minParkMag, mag);
									}
								}
								if (minParkBin >= 0) {
									// we have parkfield ruptures that would have been excluded
									double[] array = Arrays.copyOf(minMags.getMinMagForSections(), rupSet.getNumSections());
									int parkfieldID = FaultSectionUtils.findParentSectionID(
											rupSet.getFaultSectionDataList(), "San", "Andreas", "Parkfield");
									Preconditions.checkState(parkfieldID >= 0);
									// position the minimum magnitude at the start of the magnitude bin
									double parkMinMag = Math.min(minParkMag, refMFD.getX(minParkBin));
									int numModified = 0;
									for (int s=0; s<array.length; s++) {
										if (rupSet.getFaultSectionData(s).getParentSectionId() == parkfieldID) {
											array[s] = parkMinMag;
											numModified++;
										}
									}
									System.out.println("Modified SectMinMag for "+numModified+" Parkfield sections to "+(float)parkMinMag);
									Preconditions.checkState(numModified > 0, "Parkfield sections not found?");
									minMags = ModSectMinMags.instance(rupSet, array);
								}
							}
							return minMags;
						}
					}, ModSectMinMags.class);
				}
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
					SubSeisMoRateReduction moRateRed = branch.hasValue(SubSeisMoRateReductions.class) ?
							branch.getValue(SubSeisMoRateReductions.class).getChoice() :
								SupraSeisBValInversionTargetMFDs.SUB_SEIS_MO_RATE_REDUCTION_DEFAULT;
					SupraSeisBValInversionTargetMFDs.Builder builder;
					RandomBValSampler.Node bValNode = branch.getValue(RandomBValSampler.Node.class);
					if (bValNode != null) {
						RandomBValSampler sampler = rupSet.requireModule(BranchSamplingManager.class).getSampler(bValNode);
						builder = new SupraSeisBValInversionTargetMFDs.Builder(rupSet, sampler.getBValues());
					} else {
						builder = new SupraSeisBValInversionTargetMFDs.Builder(rupSet, branch.requireValue(SectionSupraSeisBValues.class));
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
			if (branch.hasValue(MaxMagOffFaultBranchNode.class)
					&& branch.hasValue(NSHM23_DeclusteringAlgorithms.class)
					&& branch.hasValue(NSHM23_SeisSmoothingAlgorithms.class)
					&& branch.hasValue(NSHM23_RegionalSeismicity.class)
					&& rupSet.hasAvailableModule(ModelRegion.class)) {
				// offer fault cube associations
				rupSet.offerAvailableModule(new Callable<FaultGridAssociations>() {

					@Override
					public FaultGridAssociations call() throws Exception {
						return buildFaultCubeAssociations(rupSet, branch, rupSet.requireModule(ModelRegion.class).getRegion());
					}
				}, FaultGridAssociations.class);
			} else if (branch.hasValue(MaxMagOffFaultBranchNode.class)
					&& branch.hasValue(SpatialSeisPDF.class)
					&& branch.hasValue(TotalMag5Rate.class)) {
				// offer U3 ingredients fault cube associations
				rupSet.offerAvailableModule(new Callable<FaultGridAssociations>() {

					@Override
					public FaultGridAssociations call() throws Exception {
						return buildU3IngredientsFaultCubeAssociations(rupSet);
					}
				}, FaultGridAssociations.class);
			}
			
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
			
			if (branch.hasValue(MaxMagOffFaultBranchNode.class)
					&& branch.hasValue(NSHM23_DeclusteringAlgorithms.class)
					&& branch.hasValue(NSHM23_SeisSmoothingAlgorithms.class)
					&& branch.hasValue(NSHM23_RegionalSeismicity.class)) {
				// can build grid source provider
				// offer as a GridSourceProvider so as not to replace a precomputed one
				// this will also add fault cube associations to the rupture set
				sol.offerAvailableModule(new Callable<GridSourceProvider>() {

					@Override
					public NSHM23_AbstractGridSourceProvider call() throws Exception {
						return buildGridSourceProv(sol, branch);
					}
				}, GridSourceProvider.class);
			} else if (branch.hasValue(MaxMagOffFaultBranchNode.class)
					&& branch.hasValue(SpatialSeisPDF.class)
					&& branch.hasValue(TotalMag5Rate.class)) {
				// can build grid source provider using UCERF3 ingredients
				// offer as a GridSourceProvider so as not to replace a precomputed one
				// this will also add fault cube associations to the rupture set
				sol.offerAvailableModule(new Callable<GridSourceProvider>() {

					@Override
					public NSHM23_AbstractGridSourceProvider call() throws Exception {
						double maxMagOff = branch.requireValue(MaxMagOffFaultBranchNode.class).getMaxMagOffFault();
						EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(
								Math.max(maxMagOff, sol.getRupSet().getMaxMag()));
						NSHM23_FaultCubeAssociations cubeAssociations = buildU3IngredientsFaultCubeAssociations(rupSet);
						rupSet.addModule(cubeAssociations);
						return buildU3IngredientsGridSourceProv(sol,
								branch.requireValue(TotalMag5Rate.class).getRateMag5(),
								branch.requireValue(SpatialSeisPDF.class),
								maxMagOff,
								refMFD,
								cubeAssociations);
					}
				}, GridSourceProvider.class);
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
		if (branch.hasValue(NSHM23_SegmentationModels.AVERAGE) || branch.hasValue(SupraSeisBValues.AVERAGE))
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
		
		RupSetFaultModel fm = branch.getValue(RupSetFaultModel.class);
		constrBuilder.parkfieldSelection(getParkfieldSelectionCriteria(fm));
		
		NSHM23_PaleoUncertainties paleoUncert = branch.getValue(NSHM23_PaleoUncertainties.class);
		constrBuilder.paleoUncerts(paleoUncert);
		
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
	
	public static boolean hasParkfield(FaultSystemRupSet rupSet) {
		return NSHM23_ConstraintBuilder.findParkfieldSection(rupSet) >= 0;
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
	public LogicTree<?> getGridSourceTree(LogicTree<?> faultTree) {
		if (isU3Branch(faultTree.getBranch(0)))
			return LogicTree.buildExhaustive(NSHM23_U3_HybridLogicTreeBranch.levelsOffFault, true);
		return LogicTree.buildExhaustive(NSHM23_LogicTreeBranch.levelsOffFault, true);
	}
	
	private static boolean isU3Branch(LogicTreeBranch<?> branch) {
		return branch.hasValue(FaultModels.class) || branch.hasValue(U3_UncertAddDeformationModels.class)
				|| branch.hasValue(SpatialSeisPDF.class);
	}
	
	public static List<SeismicityRegions> getSeismicityRegions(Region modelRegion) throws IOException {
		List<SeismicityRegions> seisRegions = new ArrayList<>();
		for (SeismicityRegions seisReg : SeismicityRegions.values()) {
			Region testReg = seisReg.load();
//			System.out.println("Comparing "+modelRegion.getName()+" to "+testReg.getName());
//			System.out.println("\tEquals? "+modelRegion.equals(testReg));
//			System.out.println("\tIntersects? "+modelRegion.intersects(testReg));
//			System.out.println("\tContains? "+modelRegion.contains(testReg));
			if (testReg.equalsRegion(modelRegion) || modelRegion.intersects(testReg) || modelRegion.contains(testReg))
				seisRegions.add(seisReg);
		}
		return seisRegions;
	}
	
	public static GriddedRegion getGriddedSeisRegion(SeismicityRegions seisRegion) throws IOException {
		return getGriddedSeisRegion(List.of(seisRegion));
	}
	
	public static GriddedRegion getGriddedSeisRegion(List<SeismicityRegions> seisRegions) throws IOException {
		Preconditions.checkState(!seisRegions.isEmpty());
		Region region;
		if (seisRegions.size() == 1) {
			// simple case
			region = seisRegions.get(0).load();
		} else {
			// union them
			List<Region> regions = new ArrayList<>();
			for (SeismicityRegions seisRegion : seisRegions)
				regions.add(seisRegion.load());
			if (regions.size() > 2) {
				// sort them to try to minimize chances of non-intersecting regions
				regions.sort(new Comparator<Region>() {

					@Override
					public int compare(Region o1, Region o2) {
						return Double.compare(avgLon(o1), avgLon(o2));
					}
					
					private double avgLon(Region reg) {
						int num = 0;
						double sum = 0d;
						for (Location loc : reg.getBorder()) {
							num++;
							sum += loc.getLongitude();
						}
						return sum/(double)num;
					}
				});
			}
			region = regions.get(0);
			for (int i=1; i<regions.size(); i++) {
				region = Region.union(region, regions.get(i));
				Preconditions.checkNotNull(region, "Seismicity regions don't overlap, can't union");
			}
		}
		return new GriddedRegion(region, 0.1, GriddedRegion.ANCHOR_0_0);
	}
	
	public static FaultCubeAssociations buildFaultCubeAssociations(FaultSystemRupSet rupSet,
			LogicTreeBranch<?> branch, Region region) throws IOException {
		List<SeismicityRegions> seisRegions = getSeismicityRegions(region);
		return buildFaultCubeAssociations(rupSet, seisRegions);
	}
	
	public static FaultCubeAssociations buildFaultCubeAssociations(FaultSystemRupSet rupSet,
			List<SeismicityRegions> seisRegions) throws IOException {
		Preconditions.checkState(seisRegions.size() >= 1);
		GriddedRegion modelGridReg = getGriddedSeisRegion(seisRegions);
		if (seisRegions.size() == 1) {
//			Region seisRegion = seisRegions.get(0).load();
//			if (seisRegion.equalsRegion(modelReg)) {
				// simple case, model and seismicity region are the same
				// now always the case
				return new NSHM23_FaultCubeAssociations(rupSet, new CubedGriddedRegion(modelGridReg),
						NSHM23_SingleRegionGridSourceProvider.DEFAULT_MAX_FAULT_NUCL_DIST);
//			} else {
//				// build it for the seismicity region, then nest within the model region
//				NSHM23_FaultCubeAssociations seisCubeAssociations = new NSHM23_FaultCubeAssociations(rupSet,
//						new CubedGriddedRegion(getGriddedSeisRegion(seisRegion)),
//						NSHM23_SingleRegionGridSourceProvider.DEFAULT_MAX_FAULT_NUCL_DIST);
//				return new NSHM23_FaultCubeAssociations(rupSet,
//						new CubedGriddedRegion(modelGridReg), List.of(seisCubeAssociations));
//			}
		} else {
			List<NSHM23_FaultCubeAssociations> regionalAssociations = new ArrayList<>();
			for (SeismicityRegions seisReg : seisRegions) {
				GriddedRegion subGridReg = getGriddedSeisRegion(seisReg);
				regionalAssociations.add(new NSHM23_FaultCubeAssociations(rupSet, new CubedGriddedRegion(subGridReg),
						NSHM23_SingleRegionGridSourceProvider.DEFAULT_MAX_FAULT_NUCL_DIST));
			}
			return FaultCubeAssociations.stitch(new CubedGriddedRegion(modelGridReg), regionalAssociations);
		}
	}
	
	public static GridSourceProvider buildBranchAveragedGridSourceProv(FaultSystemSolution sol, LogicTreeBranch<?> faultBranch) throws IOException {
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		if (!rupSet.hasModule(ModelRegion.class) && faultBranch.hasValue(NSHM23_FaultModels.class))
			rupSet.addModule(NSHM23_FaultModels.getDefaultRegion(faultBranch));
		Region modelReg = rupSet.requireModule(ModelRegion.class).getRegion();
		
		// figure out what region(s) we need
		List<SeismicityRegions> seisRegions = getSeismicityRegions(modelReg);
		
		Preconditions.checkState(!seisRegions.isEmpty(), "Found no seismicity regions for model region %s", modelReg.getName());
		// build cube associations
		FaultCubeAssociations cubeAssociations = buildFaultCubeAssociations(rupSet, seisRegions);
		// add those to the rupture set
		rupSet.addModule(cubeAssociations);
		
		// average across the seismicity branches and off fault MMax branches
		// use average seis pdf and declustering
		AveragingAccumulator<GridSourceProvider> avgBuilder = null;
		
		LogicTreeNode[] fixedBranches = { NSHM23_SeisSmoothingAlgorithms.AVERAGE, NSHM23_DeclusteringAlgorithms.AVERAGE };
		// force these to have a weight of 1
		BranchWeightProvider gridTreeWeightProv = new BranchWeightProvider.NodeWeightOverrides(fixedBranches, 1d);
		LogicTree<?> offFaultTree = LogicTree.buildExhaustive(NSHM23_LogicTreeBranch.levelsOffFault, true,
				gridTreeWeightProv, fixedBranches);
		System.out.println("Building branch averaged gridded seismicity model across "+offFaultTree.size()
			+" combinations of MMax & seis rate branches, and using the average PDF");
		for (int i=0; i<offFaultTree.size(); i++) {
			LogicTreeBranch<?> gridBranch = offFaultTree.getBranch(i);
			double weight = offFaultTree.getBranchWeight(gridBranch);
			System.out.println("Building for branch "+i+"/"+offFaultTree.size()+": "+gridBranch+" (weight="+(float)weight+")");
			Preconditions.checkState(weight > 0d, "Bad weight (%s) for gridded branch: %s", weight, gridBranch);
			GridSourceProvider gridProv = buildGridSourceProv(sol, gridBranch, seisRegions, cubeAssociations);
			if (avgBuilder == null)
				avgBuilder = gridProv.averagingAccumulator();
			avgBuilder.process(gridProv, weight);
		}
		
		return avgBuilder.getAverage();
	}
	
	@Override
	public GridSourceList buildGridSourceProvider(FaultSystemSolution sol, LogicTreeBranch<?> branch) throws IOException {
		NSHM23_AbstractGridSourceProvider prov = buildGridSourceProv(sol, branch);
		
		double minMag = 2.55d;
		if (prov instanceof NSHM23_SingleRegionGridSourceProvider) {
			return ((NSHM23_SingleRegionGridSourceProvider)prov).convertToGridSourceList(minMag);
		} else {
			Preconditions.checkState(prov instanceof NSHM23_CombinedRegionGridSourceProvider);
			NSHM23_CombinedRegionGridSourceProvider combProv = (NSHM23_CombinedRegionGridSourceProvider)prov;
			List<? extends GridSourceProvider> regionalProviders = combProv.getRegionalProviders();
			GridSourceList[] gridLists = new GridSourceList[regionalProviders.size()];
			for (int i=0; i<gridLists.length; i++) {
				GridSourceProvider regionalProv = regionalProviders.get(i);
				Preconditions.checkState(regionalProv instanceof NSHM23_SingleRegionGridSourceProvider);
				gridLists[i] = ((NSHM23_SingleRegionGridSourceProvider)regionalProv).convertToGridSourceList(minMag);
			}
			return GridSourceList.combine(combProv.getGriddedRegion(), gridLists);
		}
	}
	
	@Override
	public void preGridBuildHook(FaultSystemSolution sol, LogicTreeBranch<?> faultBranch) throws IOException {
		doPreGridBuildHook(sol, faultBranch);
	}
	
	private static class SeismicityRegionsListModule implements OpenSHA_Module {
		
		public final List<SeismicityRegions> seisRegions;

		public SeismicityRegionsListModule(List<SeismicityRegions> seisRegions) {
			super();
			this.seisRegions = seisRegions;
		}

		@Override
		public String getName() {
			return "Seismicity Regions";
		}
		
	}
	
	private static void doPreGridBuildHook(FaultSystemSolution sol, LogicTreeBranch<?> faultBranch) throws IOException {
		// add fault cube associations and seismicity regions
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		if (!rupSet.hasModule(ModelRegion.class) && faultBranch.hasValue(NSHM23_FaultModels.class))
			rupSet.addModule(NSHM23_FaultModels.getDefaultRegion(faultBranch));
		Region modelReg = rupSet.requireModule(ModelRegion.class).getRegion();
		
		// figure out what region(s) we need
		FaultCubeAssociations cubeAssociations = rupSet.getModule(FaultCubeAssociations.class);
		boolean addCubeAssoc = cubeAssociations == null;
		if (isU3Branch(faultBranch)) {
			// U3 branch, skip seismicity regions
			if (cubeAssociations == null)
				cubeAssociations = NSHM23_InvConfigFactory.buildU3IngredientsFaultCubeAssociations(rupSet);
		} else {
			List<SeismicityRegions> seisRegions;
			if (rupSet.hasModule(SeismicityRegionsListModule.class)) {
				seisRegions = rupSet.requireModule(SeismicityRegionsListModule.class).seisRegions;
			} else {
				seisRegions = getSeismicityRegions(modelReg);
				Preconditions.checkState(!seisRegions.isEmpty(), "Found no seismicity regions for model region %s", modelReg.getName());
				rupSet.addModule(new SeismicityRegionsListModule(seisRegions));
				addCubeAssoc = true;
				cubeAssociations = null; // force a rebuild
			}
			// build cube associations
			if (cubeAssociations == null)
				cubeAssociations = buildFaultCubeAssociations(rupSet, seisRegions);
		}
		Preconditions.checkNotNull(cubeAssociations, "Cube associations is null");
		// add to the rupture set
		if (addCubeAssoc)
			rupSet.addModule(cubeAssociations);
	}

	public static NSHM23_AbstractGridSourceProvider buildGridSourceProv(FaultSystemSolution sol, LogicTreeBranch<?> branch) throws IOException {
		doPreGridBuildHook(sol, branch);
		FaultSystemRupSet rupSet = sol.getRupSet();
		SeismicityRegionsListModule seisRegionsModule = rupSet.getModule(SeismicityRegionsListModule.class);
		List<SeismicityRegions> seisRegions = seisRegionsModule == null ? null : seisRegionsModule.seisRegions;
		FaultCubeAssociations cubeAssociations = rupSet.requireModule(FaultCubeAssociations.class);
		return buildGridSourceProv(sol, branch, seisRegions, cubeAssociations);
	}
	
	public static NSHM23_AbstractGridSourceProvider buildGridSourceProv(FaultSystemSolution sol, LogicTreeBranch<?> branch,
			List<SeismicityRegions> seisRegions, FaultCubeAssociations cubeAssociations)  throws IOException {
		GriddedRegion gridReg = cubeAssociations.getRegion();
		
		double maxMagOff = branch.requireValue(MaxMagOffFaultBranchNode.class).getMaxMagOffFault();
		
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(
				Math.max(maxMagOff, sol.getRupSet().getMaxMag()));
		
		if ((seisRegions == null || seisRegions.isEmpty()) && branch.hasValue(SpatialSeisPDF.class)) {
			// it's a UCERF3 ingredients run
			return buildU3IngredientsGridSourceProv(sol, branch.requireValue(TotalMag5Rate.class).getRateMag5(),
					branch.requireValue(SpatialSeisPDF.class), maxMagOff, refMFD, cubeAssociations);
		}
		
		NSHM23_RegionalSeismicity seisBranch = branch.requireValue(NSHM23_RegionalSeismicity.class);
		NSHM23_DeclusteringAlgorithms declusteringAlg = branch.requireValue(NSHM23_DeclusteringAlgorithms.class);
		NSHM23_SeisSmoothingAlgorithms seisSmooth = branch.requireValue(NSHM23_SeisSmoothingAlgorithms.class);
		
		Preconditions.checkState(!seisRegions.isEmpty(), "Found no seismicity regions for model region %s", gridReg.getName());
		
		List<? extends FaultCubeAssociations> regionalCubeAssoc = null;
		if (cubeAssociations instanceof StitchedFaultCubeAssociations)
			regionalCubeAssoc = ((StitchedFaultCubeAssociations)cubeAssociations).getRegionalAssociations();
		if (seisRegions.size() > 1 && regionalCubeAssoc != null) {
			Preconditions.checkState(regionalCubeAssoc.size() == seisRegions.size(),
					"Have %s regions but %s regional cube associations", seisRegions.size(), regionalCubeAssoc.size());
			for (int i=0; i<seisRegions.size(); i++)
				Preconditions.checkState(seisRegions.get(i).load().equalsRegion(regionalCubeAssoc.get(i).getRegion()),
						"Regional cube association %s doesn't match the seismicity region at that index", i);
		}
		
		NSHM23_AbstractGridSourceProvider ret;
		if (seisRegions.size() == 1) {
			// simple case
			ret = buildSingleGridSourceProv(sol, seisRegions.get(0), seisBranch, declusteringAlg, seisSmooth,
					maxMagOff, refMFD, cubeAssociations);
		} else {
			List<NSHM23_SingleRegionGridSourceProvider> regionalProvs = new ArrayList<>();
			for (int i=0; i<seisRegions.size(); i++) {
				SeismicityRegions seisRegion = seisRegions.get(i);
				FaultCubeAssociations subRegCubeAssoc = null;
				if (regionalCubeAssoc != null) {
					subRegCubeAssoc = regionalCubeAssoc.get(i);
				} else {
					// need to build it
					GriddedRegion subGridReg = getGriddedSeisRegion(seisRegion);
					subRegCubeAssoc = new NSHM23_FaultCubeAssociations(sol.getRupSet(),
							new CubedGriddedRegion(subGridReg), NSHM23_SingleRegionGridSourceProvider.DEFAULT_MAX_FAULT_NUCL_DIST);
				}
				regionalProvs.add(buildSingleGridSourceProv(sol, seisRegion, seisBranch, declusteringAlg, seisSmooth,
						maxMagOff, refMFD, subRegCubeAssoc));
			}
			
			ret = new NSHM23_CombinedRegionGridSourceProvider(sol, cubeAssociations, regionalProvs);
		}
		
		return ret;
	}
	
	private static EnumMap<TectonicRegionType, Region> trtRegions = null;
	
	public static synchronized EnumMap<TectonicRegionType, Region> getTRT_Regions() throws IOException {
		if (trtRegions == null) {
			trtRegions = new EnumMap<>(TectonicRegionType.class);
			trtRegions.put(TectonicRegionType.ACTIVE_SHALLOW, NSHM23_RegionLoader.GridSystemRegions.WUS_ACTIVE.load());
			trtRegions.put(TectonicRegionType.STABLE_SHALLOW, NSHM23_RegionLoader.GridSystemRegions.CEUS_STABLE.load());
		}
		return trtRegions;
	}
	
	private static NSHM23_SingleRegionGridSourceProvider buildSingleGridSourceProv(FaultSystemSolution sol,
			SeismicityRegions region, NSHM23_RegionalSeismicity seisBranch, NSHM23_DeclusteringAlgorithms declusteringAlg,
			NSHM23_SeisSmoothingAlgorithms seisSmooth, double maxMagOff, EvenlyDiscretizedFunc refMFD,
			FaultCubeAssociations cubeAssociations) throws IOException {
		// total G-R up to Mmax
		IncrementalMagFreqDist totalGR = seisBranch.build(region, refMFD, maxMagOff);
		
		// figure out what's left for gridded seismicity
		IncrementalMagFreqDist totalGridded = new IncrementalMagFreqDist(
				refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		
		GriddedRegion gridReg = cubeAssociations.getRegion();
		IncrementalMagFreqDist solNuclMFD = sol.calcNucleationMFD_forRegion(
				gridReg, refMFD.getMinX(), refMFD.getMaxX(), refMFD.size(), false);
		for (int i=0; i<totalGR.size(); i++) {
			double totalRate = totalGR.getY(i);
			if (totalRate > 0) {
				double solRate = solNuclMFD.getY(i);
				if (solRate > totalRate) {
					System.err.println("WARNING: MFD bulge at M="+(float)refMFD.getX(i)
						+"\tGR="+(float)totalRate+"\tsol="+(float)solRate);
				} else {
					totalGridded.set(i, totalRate - solRate);
				}
			}
		}
		
		// focal mechanisms
		double[] fractStrikeSlip = NSHM23_GridFocalMechs.getFractStrikeSlip(region, gridReg);
		double[] fractReverse = NSHM23_GridFocalMechs.getFractReverse(region, gridReg);
		double[] fractNormal = NSHM23_GridFocalMechs.getFractNormal(region, gridReg);
		
		// spatial seismicity PDF
		double[] pdf = seisSmooth.load(region, declusteringAlg);
		
		// seismicity depth distribution
		
		// TODO still using UCERF3
		SeisDepthDistribution seisDepthDistribution = new SeisDepthDistribution();
		double delta=2;
		HistogramFunction binnedDepthDistFunc = new HistogramFunction(1d, 12,delta);
		for(int i=0;i<binnedDepthDistFunc.size();i++) {
			double prob = seisDepthDistribution.getProbBetweenDepths(binnedDepthDistFunc.getX(i)-delta/2d,binnedDepthDistFunc.getX(i)+delta/2d);
			binnedDepthDistFunc.set(i,prob);
		}
//		EvenlyDiscretizedFunc depthNuclDistFunc = NSHM23_SeisDepthDistributions.load(region);
		
		return new NSHM23_SingleRegionGridSourceProvider(sol, cubeAssociations, pdf, totalGridded, binnedDepthDistFunc,
				fractStrikeSlip, fractNormal, fractReverse, getTRT_Regions());
	}
	
	public static NSHM23_FaultCubeAssociations buildU3IngredientsFaultCubeAssociations(FaultSystemRupSet rupSet) throws IOException {
		GriddedRegion modelGridReg = new CaliforniaRegions.RELM_TESTING_GRIDDED();
		return new NSHM23_FaultCubeAssociations(rupSet, new CubedGriddedRegion(modelGridReg),
				NSHM23_SingleRegionGridSourceProvider.DEFAULT_MAX_FAULT_NUCL_DIST);
	}
	
	public static NSHM23_SingleRegionGridSourceProvider buildU3IngredientsGridSourceProv(FaultSystemSolution sol,
			double totRateM5, SpatialSeisPDF spatSeisPDF, double maxMagOff, EvenlyDiscretizedFunc refMFD,
			FaultCubeAssociations cubeAssociations) throws IOException {
		// total G-R up to Mmax
		GutenbergRichterMagFreqDist totalGR = new GutenbergRichterMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		
		// this sets shape, min/max
		totalGR.setAllButTotCumRate(refMFD.getX(0), refMFD.getX(refMFD.getClosestXIndex(maxMagOff)), 1e16, 1d);
		// this scales it to match
		totalGR.scaleToCumRate(refMFD.getClosestXIndex(5.001), totRateM5);
		
		totalGR.setName("Total Observed [b=1, N5="+(float)totRateM5+"]");
		
		GriddedRegion gridReg = cubeAssociations.getRegion();

		// figure out what's left for gridded seismicity
		IncrementalMagFreqDist totalGridded = new IncrementalMagFreqDist(
				refMFD.getMinX(), refMFD.size(), refMFD.getDelta());

		IncrementalMagFreqDist solNuclMFD = sol.calcNucleationMFD_forRegion(
				gridReg, refMFD.getMinX(), refMFD.getMaxX(), refMFD.size(), false);
		for (int i=0; i<totalGR.size(); i++) {
			double totalRate = totalGR.getY(i);
			if (totalRate > 0) {
				double solRate = solNuclMFD.getY(i);
				if (solRate > totalRate) {
					System.err.println("WARNING: MFD bulge at M="+(float)refMFD.getX(i)
					+"\tGR="+(float)totalRate+"\tsol="+(float)solRate);
				} else {
					totalGridded.set(i, totalRate - solRate);
				}
			}
		}

		// focal mechanisms
		double[] fractStrikeSlip = new GridReader("StrikeSlipWts.txt").getValues();
		double[] fractReverse = new GridReader("ReverseWts.txt").getValues();
		double[] fractNormal = new GridReader("NormalWts.txt").getValues();

		// spatial seismicity PDF
		double[] pdf = spatSeisPDF.getPDF();
		
		Preconditions.checkState(pdf.length == gridReg.getNodeCount(),
				"PDF has %s nodes but grid reg has %s", pdf.length, gridReg.getNodeCount());
		Preconditions.checkState(pdf.length == fractStrikeSlip.length,
				"PDF has %s nodes but fract strike-slip has %s", pdf.length, fractStrikeSlip.length);
		Preconditions.checkState(pdf.length == fractReverse.length,
				"PDF has %s nodes but fract reverse has %s", pdf.length, fractReverse.length);
		Preconditions.checkState(pdf.length == fractNormal.length,
				"PDF has %s nodes but fract normal has %s", pdf.length, fractNormal.length);

		// seismicity depth distribution
		SeisDepthDistribution seisDepthDistribution = new SeisDepthDistribution();
		double delta=2;
		HistogramFunction binnedDepthDistFunc = new HistogramFunction(1d, 12,delta);
		for(int i=0;i<binnedDepthDistFunc.size();i++) {
			double prob = seisDepthDistribution.getProbBetweenDepths(binnedDepthDistFunc.getX(i)-delta/2d,binnedDepthDistFunc.getX(i)+delta/2d);
			binnedDepthDistFunc.set(i,prob);
		}

		return new NSHM23_SingleRegionGridSourceProvider(sol, cubeAssociations, pdf, totalGridded, binnedDepthDistFunc,
				fractStrikeSlip, fractNormal, fractReverse, null);
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
		boolean hasPaleoData = rupSet.hasModule(PaleoseismicConstraintData.class);
		if (hasPaleoData) {
			PaleoseismicConstraintData paleoData = rupSet.getModule(PaleoseismicConstraintData.class);
			hasPaleoData = paleoData.hasPaleoRateConstraints() || paleoData.hasPaleoSlipConstraints();
		}
		
		boolean hasParkfield = constrBuilder.rupSetHasParkfield();
		
		// make sure that we have at least 1 nonzero slip rate (most likely to happen with cluster-specific inversions)
		boolean hasNonZeroSlip = false;
		for (double slipRate : rupSet.requireModule(SectSlipRates.class).getSlipRates()) {
			if (slipRate > 0d) {
				hasNonZeroSlip = true;
				break;
			}
		}
		if (!hasNonZeroSlip && !hasPaleoData && !hasParkfield) {
			System.out.println("Warning: skipping inversion for rupture set with "+rupSet.getNumSections()
					+" sections and "+rupSet.getNumRuptures()+" ruptures, no positive slip rates/paleo data/parkfield");
			return null;
		}
		
		constrBuilder.adjustForActualRupSlips(adjustForActualRupSlips, adjustForSlipAlong);
		
		SubSectConstraintModels constrModel = branch.hasValue(SubSectConstraintModels.class) ?
				branch.getValue(SubSectConstraintModels.class) : SUB_SECT_CONSTR_DEFAULT;
		
		double slipWeight = 1d;
		double paleoWeight = hasPaleoData ? 5 : 0;
		double parkWeight = hasParkfield ? 10 : 0;
		double mfdWeight = constrModel == SubSectConstraintModels.NUCL_MFD ? 1 : 10;
		double nuclWeight = constrModel == SubSectConstraintModels.TOT_NUCL_RATE ? 0.5 : 0d;
		double nuclMFDWeight = constrModel == SubSectConstraintModels.NUCL_MFD ? 0.5 : 0d;
		double paleoSmoothWeight = paleoWeight > 0 ? 10000 : 0;
		
		if (slipWeight > 0d)
			constrBuilder.slipRates().weight(slipWeight);
		
		if (paleoWeight > 0d) {
			// need to explicitly specify class for weighting as the constraint could be skipped if no paleo (slip)
			// data exists
			constrBuilder.paleoRates().weight(PaleoRateInversionConstraint.class, paleoWeight);
			constrBuilder.paleoSlips().weight(PaleoSlipInversionConstraint.class, paleoWeight);
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
			// need to explicitly specify class for weighting as the constraint could be skipped
			constrBuilder.supraPaleoSmooth().weight(LaplacianSmoothingInversionConstraint.class, paleoSmoothWeight);
		
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
		
		if (parkWeight > 0d && PARKFIELD_INITIAL)
			builder.initialSolution(constrBuilder.getParkfieldInitial(rupSet.hasModule(ModSectMinMags.class), targetMFDs));
		
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
		
		// creeping section model, only include if not part of our segmentation models (will already be taken care of in that case)
		RupsThroughCreepingSectBranchNode creepingModel = branch.getValue(RupsThroughCreepingSectBranchNode.class);
		if (creepingModel != null && creepingModel.isExcludeRupturesThroughCreepingSect() && !(creepingModel instanceof NSHM23_SegmentationModels)) {
			int creepingSectID = NSHM23_ConstraintBuilder.findCreepingSection(rupSet);
			if (creepingSectID >= 0)
				exclusionModels.add(new ExcludeRupsThroughCreepingSegmentationModel(creepingSectID));
		}
		
		if (exclusionModels.isEmpty())
			return null;
		if (exclusionModels.size() == 1)
			return exclusionModels.get(0);
		
		System.out.println("Combining "+exclusionModels.size()+" exclusion models");
		
		return new RuptureProbabilityCalc.LogicalAnd(exclusionModels.toArray(new BinaryRuptureProbabilityCalc[0]));
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
			} else if (!hasPaleoData(rupSet) && !hasParkfield(rupSet)) {
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
	
	public static class ExclusionAwareClusterSpecificInversionSolver extends ClusterSpecificInversionSolver {

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
	
	public static class NoPaleoParkfield extends NSHM23_InvConfigFactory {

		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			return InversionConfiguration.builder(config).except(PaleoRateInversionConstraint.class, false)
				.except(PaleoSlipInversionConstraint.class, false).except(ParkfieldInversionConstraint.class, false)
				.except(LaplacianSmoothingInversionConstraint.class, false).build();
		}
		
	}
	
	public static class NoMFDScaleAdjust extends NSHM23_InvConfigFactory {

		public NoMFDScaleAdjust() {
			this.adjustForActualRupSlips(false, false);
		}
		
	}
	
	public static class NoIncompatibleDataAdjust extends NSHM23_InvConfigFactory {

		public NoIncompatibleDataAdjust() {
			NSHM23_ConstraintBuilder.ADJ_FOR_INCOMPATIBLE_DATA_DEFAULT = false;
		}
		
	}
	
	public static class MFDSlipAlongAdjust extends NSHM23_InvConfigFactory {

		public MFDSlipAlongAdjust() {
			this.adjustForActualRupSlips(true, true);
		}
		
	}
	
	public static class HardcodedPrevWeightAdjust extends NSHM23_InvConfigFactory {
		
		private ZipFile zip;
		
		public HardcodedPrevWeightAdjust() {
			this(new File("/project/scec_608/kmilner/nshm23/batch_inversions/"
						+ "2022_12_20-nshm23_u3_hybrid_branches-full_sys_inv-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-NoRed-ThreshAvgIterRelGR/results.zip"));
		}

		public HardcodedPrevWeightAdjust(File resultsFile) {
			try {
				zip = new ZipFile(resultsFile);
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}

		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			
			// disable reweighting
			config = InversionConfiguration.builder(config).reweight(null).build();
			
			// get previous weights
			String entryName = "solution_logic_tree/";
			for (int i=0; i<branch.size(); i++) {
				LogicTreeLevel<?> level = branch.getLevel(i);
				if (level.affects(InversionMisfitStats.MISFIT_STATS_FILE_NAME, true))
					entryName += branch.getValue(i).getFilePrefix()+"/";
			}
			entryName += InversionMisfitStats.MISFIT_STATS_FILE_NAME;
			System.out.println("Loading "+entryName);
			ZipEntry entry = zip.getEntry(entryName);
			Preconditions.checkNotNull(entry, "Entry not found: %s", entryName);
			
			CSVFile<String> csv;
			try {
				csv = CSVFile.readStream(zip.getInputStream(entry), true);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			InversionMisfitStats stats = new InversionMisfitStats(null);
			stats.initFromCSV(csv);
			
			for (InversionConstraint constraint : config.getConstraints()) {
				if (constraint.getWeightingType() == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
					// find prev final weight
					boolean found = false;
					for (MisfitStats misfits : stats.getStats()) {
						if (misfits.range.name.equals(constraint.getName())) {
							constraint.setWeight(misfits.range.weight);
							System.out.println(misfits.range.name+" prevWeight="+misfits.range.weight);
							found = true;
						}
					}
					Preconditions.checkState(found, "Previous weight not found for constraint %s, branch %s",
							constraint.getName(), branch);
				}
			}
			
			return config;
		}
		
	}
	
	public static class HardcodedPrevWeightAdjustFullSys extends HardcodedPrevWeightAdjust {
		
		public HardcodedPrevWeightAdjustFullSys() {
			super(new File("/project/scec_608/kmilner/nshm23/batch_inversions/"
					+ "2022_12_20-nshm23_u3_hybrid_branches-full_sys_inv-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-NoRed-ThreshAvgIterRelGR/results.zip"));
		}

		@Override
		public boolean isSolveClustersIndividually() {
			return false;
		}
	}
	
	public static class HardcodedOrigWeights extends NSHM23_InvConfigFactory {
		
		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			
			// disable reweighting
			config = InversionConfiguration.builder(config).reweight(null).build();
			
			return config;
		}
		
	}
	
	public static class HardcodedOrigWeightsFullSys extends HardcodedOrigWeights {

		@Override
		public boolean isSolveClustersIndividually() {
			return false;
		}
		
	}
	
	public static class HardcodedPrevAvgWeights extends NSHM23_InvConfigFactory {
		
		private Map<String, Double> nameWeightMap;
		
		public HardcodedPrevAvgWeights() {
			this(new File("/project/scec_608/kmilner/nshm23/batch_inversions/"
						+ "2022_06_10-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-Shift2km-ThreshAvgIterRelGR-IncludeThruCreep/results_FM3_1_CoulombRupSet_branch_averaged.zip"));
		}
		
		public HardcodedPrevAvgWeights(File baFile) {
			try {
				FaultSystemSolution prevBA = FaultSystemSolution.load(baFile);
				InversionMisfitStats avgStats = prevBA.requireModule(InversionMisfitStats.class);
				nameWeightMap = new HashMap<>();
				for (MisfitStats stats : avgStats.getStats()) {
					if (stats.range.weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY)
						nameWeightMap.put(stats.range.name, stats.range.weight);
				}
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			
			// disable reweighting
			config = InversionConfiguration.builder(config).reweight(null).build();
			
			for (InversionConstraint constraint : config.getConstraints()) {
				if (constraint.getWeightingType() == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
					// find prev final weight
					Double prevWeight = nameWeightMap.get(constraint.getName());
					Preconditions.checkNotNull(prevWeight, "Previous weight not found for constraint %s, branch %s",
							constraint.getName(), branch);
					constraint.setWeight(prevWeight);
				}
			}
			
			return config;
		}
		
	}
	
	public static class HardcodedPrevAvgWeightsFullSys extends HardcodedPrevAvgWeights {
		
		public HardcodedPrevAvgWeightsFullSys() {
			super(new File("/project/scec_608/kmilner/nshm23/batch_inversions/"
						+ "2022_12_09-nshm23_u3_hybrid_branches-full_sys_inv-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-NoRed-ThreshAvgIterRelGR/results_FM3_1_CoulombRupSet_branch_averaged.zip"));
		}

		@Override
		public boolean isSolveClustersIndividually() {
			return false;
		}
	}
	
	public static class HardcodedPrevAsInitial extends NSHM23_InvConfigFactory {
		
		private SolutionLogicTree slt;
		
		public HardcodedPrevAsInitial() {
			this(new File("/project/scec_608/kmilner/nshm23/batch_inversions/"
						+ "2022_06_10-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-Shift2km-ThreshAvgIterRelGR-IncludeThruCreep/results.zip"));
		}

		public HardcodedPrevAsInitial(File resultsFile) {
			try {
				slt = SolutionLogicTree.load(resultsFile);
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}

		@Override
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
			
			// load from previous
			try {
				return slt.forBranch(branch, false).getRupSet();
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}

		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			
			double[] prevRates;
			try {
				prevRates = slt.loadRatesForBranch(branch);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
			if (rupSet.hasModule(RuptureSubSetMappings.class)) {
				RuptureSubSetMappings mappings = rupSet.getModule(RuptureSubSetMappings.class);
				double[] modPrevRates = new double[mappings.getNumRetainedRuptures()];
				for (int r=0; r<modPrevRates.length; r++)
					modPrevRates[r] = prevRates[mappings.getOrigRupID(r)];
				prevRates = modPrevRates;
			}
			
			config = InversionConfiguration.builder(config).initialSolution(prevRates).build();
			
			return config;
		}
		
	}
	
//	public static class ClusterSpecific extends NSHM23_InvConfigFactory implements ClusterSpecificInversionConfigurationFactory {
//		
//	}
	
	public static class SegWeight100 extends NSHM23_InvConfigFactory {
		
		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			
			if (config == null)
				return null;
			
			for (InversionConstraint constraint : config.getConstraints())
				if (constraint instanceof JumpProbabilityConstraint)
					constraint.setWeight(100);
			
			return config;
		}
		
	}
	
	public static class SegWeight1000 extends NSHM23_InvConfigFactory {
		
		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			
			if (config == null)
				return null;
			
			for (InversionConstraint constraint : config.getConstraints())
				if (constraint instanceof JumpProbabilityConstraint)
					constraint.setWeight(1000);
			
			return config;
		}
		
	}
	
	public static class SegWeight10000 extends NSHM23_InvConfigFactory {
		
		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			
			if (config == null)
				return null;
			
			for (InversionConstraint constraint : config.getConstraints())
				if (constraint instanceof JumpProbabilityConstraint)
					constraint.setWeight(10000);
			
			return config;
		}
		
	}
	
	public static class SegWeight100000 extends NSHM23_InvConfigFactory {
		
		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			
			if (config == null)
				return null;
			
			for (InversionConstraint constraint : config.getConstraints())
				if (constraint instanceof JumpProbabilityConstraint)
					constraint.setWeight(100000);
			
			return config;
		}
		
	}
	
	public static class FullSysInv extends NSHM23_InvConfigFactory {
		
		public FullSysInv() {
			super();
			setSolveClustersIndividually(false);
		}
		
	}
	
	public static class MFDUncert0p1 extends NSHM23_InvConfigFactory {
		
		public MFDUncert0p1() {
			MFD_MIN_FRACT_UNCERT = 0.1;
		}
	}
	
	public static class ConstantSlipRateStdDev0p1 extends NSHM23_InvConfigFactory {
		
		public ConstantSlipRateStdDev0p1() {
			NSHM23_DeformationModels.HARDCODED_FRACTIONAL_STD_DEV = 0.1d;
		}
	}
	
	public static class ConstantSlipRateStdDev0p2 extends NSHM23_InvConfigFactory {
		
		public ConstantSlipRateStdDev0p2() {
			NSHM23_DeformationModels.HARDCODED_FRACTIONAL_STD_DEV = 0.2d;
		}
	}
	
	/**
	 * Extend down-dip width of all faults
	 * 
	 * @author kevin
	 *
	 */
	private static class AbstractScaleLowerDepth extends NSHM23_InvConfigFactory {
		
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
			
			// attach modules
			getSolutionLogicTreeProcessor().processRupSet(rupSet, branch);
			
			return rupSet;
		}
		
	}
	
	public static class ScaleLowerDepth1p3 extends AbstractScaleLowerDepth {
		
		public ScaleLowerDepth1p3() {
			super(1.3);
		}
		
	}
	
	public static class NoAvg extends NSHM23_InvConfigFactory {
		
		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			
			config = InversionConfiguration.builder(config).avgThreads(1, config.getAvgCompletionCriteria()).build();
			
			return config;
		}
		
	}
	
	public static class ForceNewPaleo extends NSHM23_InvConfigFactory {

		@Override
		public SolutionProcessor getSolutionLogicTreeProcessor() {
			return new NSHM23SolProcessor() {

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
	
	public static class ScaleSurfSlipUseActualWidths extends NSHM23_InvConfigFactory {

		public ScaleSurfSlipUseActualWidths() {
			NSHM23_ScalingRelationships.SURFACE_SLIP_HARDCODED_W = false;
		}
		
	}
	
	public static class ForceNoGhostTransient extends NSHM23_InvConfigFactory {

		public ForceNoGhostTransient() {
			NSHM23_DeformationModels.GEODETIC_INCLUDE_GHOST_TRANSIENT = false;
		}
		
	}
	
	public static class RemoveIsolatedFaults extends NSHM23_InvConfigFactory {

		@Override
		protected synchronized FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) {
			FaultSystemRupSet fullRupSet = super.buildGenericRupSet(branch, threads);
			
			List<ConnectivityCluster> clusters = ConnectivityCluster.build(fullRupSet);
			
			HashSet<Integer> retained = new HashSet<>();
			for (ConnectivityCluster cluster : clusters)
				if (cluster.getParentSectIDs().size() > 1)
					retained.addAll(cluster.getSectIDs());
			
			System.out.println("Retaining "+retained.size()+"/"+fullRupSet.getNumSections()+" subsections");
			
			FaultSystemRupSet ret = fullRupSet.getForSectionSubSet(retained);
			ret.addModule(fullRupSet.getModule(PlausibilityConfiguration.class));
			return ret;
		}
		
		@Override
		public FaultSystemRupSet updateRuptureSetForBranch(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch)
				throws IOException {
			// need to keep the subsections list from the previous
			List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
			
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
			
			SlipAlongRuptureModelBranchNode slipAlong = branch.requireValue(SlipAlongRuptureModelBranchNode.class);
			rupSet.addModule(slipAlong.getModel());
			
			// add other modules
			return getSolutionLogicTreeProcessor().processRupSet(rupSet, branch);
		}
		
	}
	
	public static class RemoveProxyFaults extends NSHM23_InvConfigFactory {

		@Override
		protected synchronized FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) {
			FaultSystemRupSet fullRupSet = super.buildGenericRupSet(branch, threads);
			
			HashSet<Integer> retained = new HashSet<>();
			List<FaultSection> excluded = new ArrayList<>();
			
			for (int s=0; s<fullRupSet.getNumSections(); s++) {
				GeoJSONFaultSection sect = (GeoJSONFaultSection)fullRupSet.getFaultSectionData(s);
				String proxy = sect.getProperty("Proxy", null);
				if (proxy != null && proxy.equals("yes")) {
					System.out.println("Skipping "+sect.getName()+" (proxy="+proxy+")");
					excluded.add(sect);
				} else {
					retained.add(s);
				}
			}
			
			System.out.println("Retaining "+retained.size()+"/"+fullRupSet.getNumSections()+" subsections");
			
			FaultSystemRupSet ret = fullRupSet.getForSectionSubSet(retained, new BinaryRuptureProbabilityCalc() {
				
				@Override
				public String getName() {
					return "No proxy faults";
				}
				
				@Override
				public boolean isDirectional(boolean splayed) {
					return false;
				}
				
				@Override
				public boolean isRupAllowed(ClusterRupture fullRupture, boolean verbose) {
					for (FaultSection sect : excluded)
						if (fullRupture.contains(sect))
							return false;
					return true;
				}
			});
			
			ret.addModule(fullRupSet.getModule(PlausibilityConfiguration.class));
			return ret;
		}
		
	}
	
	public static class NoPaleoSlip extends NSHM23_InvConfigFactory {
		
		public NoPaleoSlip() {
			NSHM23_PaleoDataLoader.INCLUDE_U3_PALEO_SLIP = false;
		}

		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			if (config == null)
				// can happen for an isolated fault with zero constraints (no slip rate)
				return null;
			boolean hasSlip = false;
			for (InversionConstraint constraint : config.getConstraints())
				hasSlip = hasSlip || constraint instanceof PaleoSlipInversionConstraint;
			if (hasSlip)
				config = InversionConfiguration.builder(config).except(PaleoSlipInversionConstraint.class, false).build();
			return config;
		}
		
	}
	
	public static class ForcePaleoSlip extends NSHM23_InvConfigFactory {
		
		public ForcePaleoSlip() {
			NSHM23_PaleoDataLoader.INCLUDE_U3_PALEO_SLIP = true;
		}
		
	}
	
	public static class PaleoSlipInequality extends NSHM23_InvConfigFactory {
		
		public PaleoSlipInequality() {
			NSHM23_PaleoDataLoader.INCLUDE_U3_PALEO_SLIP = true;
		}

		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			if (config == null)
				// can happen for an isolated fault with zero constraints (no slip rate)
				return null;
			for (InversionConstraint constraint : config.getConstraints())
				if (constraint instanceof PaleoSlipInversionConstraint)
					((PaleoSlipInversionConstraint)constraint).setInequality(true);
			return config;
		}
		
	}
	
	public static class TenThousandItersPerRup extends NSHM23_InvConfigFactory {
		
		public TenThousandItersPerRup() {
			super();
			numItersPerRup = 10000;
		}
		
	}
	
	public static class DM_OutlierReplacementYc2p0 extends NSHM23_InvConfigFactory {
		
		public DM_OutlierReplacementYc2p0() {
			NSHM23_DeformationModels.OUTLIER_SUB_YC = 2d;
			NSHM23_DeformationModels.OUTLIER_SUB_LOG = false;
			NSHM23_DeformationModels.OUTLIER_SUB_USE_BOUND = false;
			NSHM23_DeformationModels.ORIGINAL_WEIGHTS = true;
		}
		
	}
	
	public static class DM_OutlierReplacementYc3p5 extends NSHM23_InvConfigFactory {
		
		public DM_OutlierReplacementYc3p5() {
			NSHM23_DeformationModels.OUTLIER_SUB_YC = 3.5d;
			NSHM23_DeformationModels.OUTLIER_SUB_LOG = false;
			NSHM23_DeformationModels.OUTLIER_SUB_USE_BOUND = false;
			NSHM23_DeformationModels.ORIGINAL_WEIGHTS = true;
		}
		
	}
	
	public static class DM_OutlierReplacementYc5p0 extends NSHM23_InvConfigFactory {
		
		public DM_OutlierReplacementYc5p0() {
			NSHM23_DeformationModels.OUTLIER_SUB_YC = 5d;
			NSHM23_DeformationModels.OUTLIER_SUB_LOG = false;
			NSHM23_DeformationModels.OUTLIER_SUB_USE_BOUND = false;
			NSHM23_DeformationModels.ORIGINAL_WEIGHTS = true;
		}
		
	}
	
	public static class DM_OutlierLogReplacementYc2p0 extends NSHM23_InvConfigFactory {
		
		public DM_OutlierLogReplacementYc2p0() {
			NSHM23_DeformationModels.OUTLIER_SUB_YC = 2d;
			NSHM23_DeformationModels.OUTLIER_SUB_LOG = true;
			NSHM23_DeformationModels.OUTLIER_SUB_USE_BOUND = false;
			NSHM23_DeformationModels.ORIGINAL_WEIGHTS = true;
		}
		
	}
	
	public static class DM_OutlierLogReplacementYc3p5 extends NSHM23_InvConfigFactory {
		
		public DM_OutlierLogReplacementYc3p5() {
			NSHM23_DeformationModels.OUTLIER_SUB_YC = 3.5d;
			NSHM23_DeformationModels.OUTLIER_SUB_LOG = true;
			NSHM23_DeformationModels.OUTLIER_SUB_USE_BOUND = false;
			NSHM23_DeformationModels.ORIGINAL_WEIGHTS = true;
		}
		
	}
	
	public static class DM_OutlierLogReplacementYc5p0 extends NSHM23_InvConfigFactory {
		
		public DM_OutlierLogReplacementYc5p0() {
			NSHM23_DeformationModels.OUTLIER_SUB_YC = 5d;
			NSHM23_DeformationModels.OUTLIER_SUB_LOG = true;
			NSHM23_DeformationModels.OUTLIER_SUB_USE_BOUND = false;
			NSHM23_DeformationModels.ORIGINAL_WEIGHTS = true;
		}
		
	}
	
	public static class DM_OriginalWeights extends NSHM23_InvConfigFactory {
		
		public DM_OriginalWeights() {
			NSHM23_DeformationModels.ORIGINAL_WEIGHTS = true;
		}
		
	}
	
	public static class DM_OutlierlMinimizationWeights extends NSHM23_InvConfigFactory {
		
		public DM_OutlierlMinimizationWeights() {
			NSHM23_DeformationModels.ORIGINAL_WEIGHTS = false;
		}
		
	}
	
	public static class SegModelLimitMaxLen extends NSHM23_InvConfigFactory {
		
		public SegModelLimitMaxLen() {
			NSHM23_SegmentationModels.LIMIT_MAX_LENGTHS = true;
		}
		
	}
	
	public static class SegModelMaxLen600 extends NSHM23_InvConfigFactory {
		
		public SegModelMaxLen600() {
			NSHM23_SegmentationModels.LIMIT_MAX_LENGTHS = true;
			NSHM23_SegmentationModels.SINGLE_MAX_LENGTH_LIMIT = 600d;
		}
		
	}
	
	public static class SlipRateStdDevCeil0p1 extends NSHM23_InvConfigFactory {
		
		public SlipRateStdDevCeil0p1() {
			NSHM23_DeformationModels.HARDCODED_FRACTIONAL_STD_DEV = 0d;
			NSHM23_DeformationModels.HARDCODED_FRACTIONAL_STD_DEV_UPPER_BOUND = 0.1d;
		}
	}
	
	public static class SparseGRDontSpreadSingleToMulti extends NSHM23_InvConfigFactory {
		
		public SparseGRDontSpreadSingleToMulti() {
			SupraSeisBValInversionTargetMFDs.SPARSE_GR_DONT_SPREAD_SINGLE_TO_MULTI = true;
		}
	}
	
	public static class SparseGRNearest extends NSHM23_InvConfigFactory {
		
		public SparseGRNearest() {
			SparseGutenbergRichterSolver.METHOD_DEFAULT = SpreadingMethod.NEAREST;
		}
	}
	
	public static class ModDepthGV08 extends NSHM23_InvConfigFactory {
		
		protected synchronized FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) {
			RupSetFaultModel fm = branch.requireValue(RupSetFaultModel.class);
			RupSetSubsectioningModel ssm = branch.requireValue(RupSetSubsectioningModel.class);
			RupturePlausibilityModels model = branch.getValue(RupturePlausibilityModels.class);
			if (model == null) {
				if (fm instanceof FaultModels) // UCERF3 FM
					model = RupturePlausibilityModels.UCERF3; // for now
				else
					model = RupturePlausibilityModels.COULOMB;
			}
			
			// check cache
			FaultSystemRupSet rupSet = rupSetCache.get(fm, ssm, model);
			if (rupSet != null) {
				return rupSet;
			}
			
			RupSetScalingRelationship scale = branch.requireValue(RupSetScalingRelationship.class);
			
			RupSetDeformationModel dm = fm.getDefaultDeformationModel();
			List<? extends FaultSection> origSubSects;
			try {
				origSubSects = dm.build(fm, ssm, branch);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
			List<FaultSection> subSects = new ArrayList<>();
			
			// modify GV depths
			for (int s=0; s<origSubSects.size(); s++) {
				FaultSection sect = origSubSects.get(s);
				if (sect.getParentSectionId() == 106) {
					double origUpper = sect.getOrigAveUpperDepth();
					double origLower = sect.getAveLowerDepth();
					
					double newUpper = 7d;
					double newLower = 7d + (origLower - origUpper);
					
					GeoJSONFaultSection origSect = (sect instanceof GeoJSONFaultSection) ?
							(GeoJSONFaultSection)sect : new GeoJSONFaultSection(sect);
					Feature feature = origSect.toFeature();
					feature.properties.set(GeoJSONFaultSection.UPPER_DEPTH, newUpper);
					feature.properties.set(GeoJSONFaultSection.LOW_DEPTH, newLower);
					GeoJSONFaultSection newSect = GeoJSONFaultSection.fromFeature(feature);
					System.out.println("Moved DDW for "+sect.getSectionName()+" from "
							+origSect.getOrigAveUpperDepth()+" to "+newSect.getOrigAveUpperDepth());
					sect = newSect;
				}
				subSects.add(sect);
			}
			
			RupSetConfig config = model.getConfig(subSects, scale);
			
			if (rupSet == null)
				rupSet = config.build(threads);
			rupSetCache.put(rupSet, fm, ssm, model);
			
			return rupSet;
		}
	}
	
	public static class OrigDraftScaling extends NSHM23_InvConfigFactory {
		
		public OrigDraftScaling() {
			NSHM23_ScalingRelationships.ORIGINAL_DRAFT_RELS = true;
		}
	}
	
	public static class ModScalingAdd4p3 extends NSHM23_InvConfigFactory {
		
		public ModScalingAdd4p3() {
			NSHM23_ScalingRelationships.ORIGINAL_DRAFT_RELS = false;
		}
	}
	
	public static class NSHM18_UseU3Paleo extends NSHM23_InvConfigFactory {
		
		public NSHM18_UseU3Paleo() {
			NSHM18_FaultModels.USE_NEW_PALEO_DATA = false;
		}
	}
	
	public static class MatchFullBA extends NSHM23_InvConfigFactory {
		
		private double[] baNuclRates;
		
		public MatchFullBA() throws IOException {
			FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/nshm23/batch_inversions/"
					+ "2023_04_11-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR/"
					+ "results_NSHM23_v2_CoulombRupSet_branch_averaged_gridded.zip"));
			baNuclRates = sol.calcNucleationRateForAllSects(0d, Double.POSITIVE_INFINITY);
		}

		@Override
		public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
				int threads) {
			InversionConfiguration config = super.buildInversionConfig(rupSet, branch, threads);
			boolean found = false;
			for (InversionConstraint constr : config.getConstraints()) {
				if (constr instanceof SectionTotalRateConstraint) {
					found = true;
					SectionTotalRateConstraint sectConstr = (SectionTotalRateConstraint)constr;
					RuptureSubSetMappings mappings = rupSet.getModule(RuptureSubSetMappings.class);
					double[] totRates = baNuclRates;
					double[] totRateStdDevs = sectConstr.getSectRateStdDevs();
					if (mappings != null) {
						// need to map
						totRates = new double[mappings.getNumRetainedSects()];
						for (int i=0; i<totRates.length; i++)
							totRates[i] = baNuclRates[mappings.getOrigSectID(i)];
					}
					Preconditions.checkState(totRates.length == totRateStdDevs.length);
					sectConstr.setSectRates(totRates, totRateStdDevs);
				}
			}
			Preconditions.checkState(found);
			return config;
		}
		
	}
	
	public static class NSHM23_V2 extends NSHM23_InvConfigFactory {
		
		public NSHM23_V2() {
			NSHM23_ConstraintBuilder.MAX_NUM_ZERO_SLIP_SECTS_PER_RUP = 0;
			NSHM23_RegionalSeismicity.RATE_FILE_NAME = "rates_2023_03_30.csv";
			NSHM23_RegionalSeismicity.clearCache();
			NSHM23_SeisSmoothingAlgorithms.MODEL_DATE = "2023_03_30";
			NSHM23_SeisSmoothingAlgorithms.clearCache();
			NSHM23_RegionLoader.setSeismicityRegionVersion(2);
		}
		
	}
	
	public static class ModPitasPointDDW extends NSHM23_InvConfigFactory {
		
		private ModDepthFM modFM(RupSetFaultModel fm) {
			return new ModDepthFM(fm, 333, 15d);
		}
		
		protected synchronized FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) {
			RupSetFaultModel fm = branch.requireValue(RupSetFaultModel.class);
			RupSetSubsectioningModel ssm = branch.requireValue(RupSetSubsectioningModel.class);
			RupturePlausibilityModels model = branch.getValue(RupturePlausibilityModels.class);
			if (model == null) {
				if (fm instanceof FaultModels) // UCERF3 FM
					model = RupturePlausibilityModels.UCERF3; // for now
				else
					model = RupturePlausibilityModels.COULOMB;
			}
			
			// check cache
			FaultSystemRupSet rupSet = rupSetCache.get(fm, ssm, model);
			if (rupSet != null) {
				return rupSet;
			}
			
			RupSetScalingRelationship scale = branch.requireValue(RupSetScalingRelationship.class);
			
			RupSetDeformationModel dm = fm.getDefaultDeformationModel();
			List<? extends FaultSection> subSects;
			try {
				subSects = dm.build(modFM(fm), (RupSetSubsectioningModel)fm , branch);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
			RupSetConfig config = model.getConfig(subSects, scale);
			
			if (rupSet == null)
				rupSet = config.build(threads);
			rupSetCache.put(rupSet, fm, ssm, model);
			
			return rupSet;
		}
		
		@Override
		public FaultSystemRupSet updateRuptureSetForBranch(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch)
				throws IOException {
			// we don't trust any modules attached to this rupture set as it could have been used for another calculation
			// that could have attached anything. Instead, lets only keep the ruptures themselves
			
			RupSetFaultModel fm = branch.requireValue(RupSetFaultModel.class);
			RupSetDeformationModel dm = branch.requireValue(RupSetDeformationModel.class);
			Preconditions.checkState(dm.isApplicableTo(fm),
					"Fault and deformation models are not compatible: %s, %s", fm.getName(), dm.getName());
			// override slip rates for the given deformation model
			List<? extends FaultSection> subSects;
			try {
				subSects = dm.build(modFM(fm), (RupSetSubsectioningModel)fm, branch);
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
			
			SlipAlongRuptureModelBranchNode slipAlong = branch.requireValue(SlipAlongRuptureModelBranchNode.class);
			rupSet.addModule(slipAlong.getModel());
			
			// add other modules
			return getSolutionLogicTreeProcessor().processRupSet(rupSet, branch);
		}
		
		private class ModDepthFM implements RupSetFaultModel {
			
			private RupSetFaultModel fm;
			private int parentID;
			private double lowerDepth;

			public ModDepthFM(RupSetFaultModel fm, int parentID, double lowerDepth) {
				this.fm = fm;
				this.parentID = parentID;
				this.lowerDepth = lowerDepth;
			}

			@Override
			public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
				return fm.getNodeWeight(fullBranch);
			}

			@Override
			public String getFilePrefix() {
				return fm.getFilePrefix();
			}

			@Override
			public String getShortName() {
				return fm.getShortName();
			}

			@Override
			public String getName() {
				return fm.getName();
			}

			@Override
			public List<? extends FaultSection> getFaultSections() throws IOException {
				List<FaultSection> ret = new ArrayList<>();
				boolean found = false;
				for (FaultSection sect : fm.getFaultSections()) {
					if (sect.getSectionId() == parentID) {
						found = true;
						Preconditions.checkState(sect instanceof GeoJSONFaultSection);
						double origDepth = sect.getAveLowerDepth();
						Feature feature = ((GeoJSONFaultSection)sect).toFeature();
						feature.properties.set(GeoJSONFaultSection.LOW_DEPTH, lowerDepth);
						sect = GeoJSONFaultSection.fromFeature(feature);
						System.out.println("Updated lowDepth for "+sect.getSectionId()+". "+sect.getSectionName()
							+": "+(float)origDepth+" -> "+(float)sect.getAveLowerDepth()+" km");
					}
					ret.add(sect);
				}
				Preconditions.checkState(found);
				return ret;
			}

			@Override
			public RupSetDeformationModel getDefaultDeformationModel() {
				return fm.getDefaultDeformationModel();
			}
			
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
		
		LogicTreeBranch<?> branch = NSHM23_LogicTreeBranch.DEFAULT_ON_FAULT;
		
		NSHM23_InvConfigFactory factory = new NSHM23_InvConfigFactory();
		
		FaultSystemRupSet rupSet = factory.buildRuptureSet(branch, 32);
		
		factory.buildInversionConfig(rupSet, branch, 32);
	}

}
