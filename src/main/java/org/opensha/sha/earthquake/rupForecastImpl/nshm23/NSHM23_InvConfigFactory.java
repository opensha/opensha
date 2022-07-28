package org.opensha.sha.earthquake.rupForecastImpl.nshm23;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.RupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.inversion.ClusterSpecificInversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.LaplacianSmoothingInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.ParkfieldInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats.MisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
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
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionUtils;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.data.NSHM23_PaleoDataLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.MaxJumpDistModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.MaxJumpDistModels.HardDistCutoffJumpProbCalc;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.RupsThroughCreepingSect;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.RupturePlausibilityModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationMFD_Adjustment;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationModelBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSectConstraintModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSeisMoRateReductions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs.SubSeisMoRateReduction;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.GRParticRateEstimator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;

public class NSHM23_InvConfigFactory implements ClusterSpecificInversionConfigurationFactory {

	protected transient Table<RupSetFaultModel, RupturePlausibilityModels, FaultSystemRupSet> rupSetCache = HashBasedTable.create();
	private transient File cacheDir;
	private boolean autoCache = true;
	
	private boolean adjustForActualRupSlips = NSHM23_ConstraintBuilder.ADJ_FOR_ACTUAL_RUP_SLIPS_DEFAULT;
	private boolean adjustForSlipAlong = NSHM23_ConstraintBuilder.ADJ_FOR_SLIP_ALONG_DEFAULT;
	
	// minimum MFD uncertainty
	public static double MFD_MIN_FRACT_UNCERT = 0.05;
	
	public static double MIN_MAG_FOR_SEISMOGENIC_RUPS = 6d;
	
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
		
		File cachedRupSetFile = null;
		if (cacheDir != null) {
			File subDir = new File(cacheDir, "rup_sets_"+fm.getFilePrefix()+"_"+dm.getFilePrefix());
			if (!subDir.exists())
				subDir.mkdir();
			int numLocs = 0;
			for (FaultSection sect : subSects)
				numLocs += sect.getFaultTrace().size();
			String rupSetFileName = "rup_set_"+model.getFilePrefix()+"_"+subSects.size()+"_sects_"+numLocs+"_trace_locs.zip";
			cachedRupSetFile = new File(subDir, rupSetFileName);
			config.setCacheDir(subDir);
		}
		config.setAutoCache(autoCache);
		
		if (cachedRupSetFile != null && cachedRupSetFile.exists()) {
			try {
				rupSet = FaultSystemRupSet.load(cachedRupSetFile);
				if (!rupSet.areSectionsEquivalentTo(subSects))
					rupSet = null;
			} catch (Exception e) {
				e.printStackTrace();
				rupSet = null;
			}
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
						return PaleoseismicConstraintData.loadUCERF3(rupSet);
					}
				}, PaleoseismicConstraintData.class);
			} else {
				// NSHM23 paleo data
				rupSet.offerAvailableModule(new Callable<PaleoseismicConstraintData>() {

					@Override
					public PaleoseismicConstraintData call() throws Exception {
						return NSHM23_PaleoDataLoader.load(rupSet);
					}
				}, PaleoseismicConstraintData.class);
				
				// regular system-wide minimum magnitudes
				rupSet.offerAvailableModule(new Callable<ModSectMinMags>() {

					@Override
					public ModSectMinMags call() throws Exception {
						ModSectMinMags minMags = ModSectMinMags.above(rupSet, MIN_MAG_FOR_SEISMOGENIC_RUPS, true);
						// modify for parkfield if needed
						List<Integer> parkRups = NSHM23_ConstraintBuilder.findParkfieldRups(rupSet);
						if (parkRups != null && !parkRups.isEmpty()) {
							EvenlyDiscretizedFunc refMFD = SupraSeisBValInversionTargetMFDs.buildRefXValues(rupSet);
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
		NSHM23_ConstraintBuilder constrBuilder = new NSHM23_ConstraintBuilder(rupSet, bVal);
		
		SubSeisMoRateReduction reduction = SupraSeisBValInversionTargetMFDs.SUB_SEIS_MO_RATE_REDUCTION_DEFAULT;
		if (branch.hasValue(SubSeisMoRateReductions.class))
			reduction = branch.getValue(SubSeisMoRateReductions.class).getChoice();
		
		constrBuilder.subSeisMoRateReduction(reduction);
		
		constrBuilder.magDepRelStdDev(M->MFD_MIN_FRACT_UNCERT*Math.max(1, Math.pow(10, bVal*0.5*(M-6))));
		
		constrBuilder.adjustForActualRupSlips(NSHM23_ConstraintBuilder.ADJ_FOR_ACTUAL_RUP_SLIPS_DEFAULT,
				NSHM23_ConstraintBuilder.ADJ_FOR_SLIP_ALONG_DEFAULT);
		
		// apply any segmentation adjustments
		if (hasJumps(rupSet)) {
			JumpProbabilityCalc targetSegModel = buildSegModel(rupSet, branch);
			MaxJumpDistModels distModel = branch.getValue(MaxJumpDistModels.class);
			if (distModel != null) {
				if (targetSegModel == null)
					targetSegModel = distModel.getModel(rupSet);
				else
					targetSegModel = new JumpProbabilityCalc.MultiProduct(targetSegModel, distModel.getModel(rupSet));
			}
			
			if (targetSegModel != null) {
				SegmentationMFD_Adjustment segAdj = branch.getValue(SegmentationMFD_Adjustment.class);
				if (segAdj == null)
					// use default adjustment
					constrBuilder.adjustForSegmentationModel(targetSegModel);
				else
					constrBuilder.adjustForSegmentationModel(targetSegModel, segAdj);
			}
			
			RupsThroughCreepingSect rupsThroughCreep = branch.getValue(RupsThroughCreepingSect.class);
			if (rupsThroughCreep != null && rupsThroughCreep.isExclude() && constrBuilder.rupSetHasCreepingSection())
				// this sets the binary exclusion model, which will remove them from target MFD calculations
				constrBuilder.excludeRupturesThroughCreeping();
		}
		
		return constrBuilder;
	}
	
	private static boolean hasJumps(FaultSystemRupSet rupSet) {
		for (ClusterRupture cRup : rupSet.requireModule(ClusterRuptures.class))
			if (cRup.getTotalNumJumps() > 0)
				return true;
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
		
		SubSectConstraintModels constrModel = branch.requireValue(SubSectConstraintModels.class);
		
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
		
		BinaryRuptureProbabilityCalc rupExcludeModel = constrBuilder.getRupExclusionModel();
		if (rupExcludeModel != null)
			sampler = getExcludeSampler(rupSet.requireModule(ClusterRuptures.class), sampler, rupExcludeModel);
		
		List<InversionConstraint> constraints = constrBuilder.build();
		
		SupraSeisBValInversionTargetMFDs targetMFDs = rupSet.requireModule(SupraSeisBValInversionTargetMFDs.class);
		
		GRParticRateEstimator rateEst = new GRParticRateEstimator(rupSet, targetMFDs);
		
		if (hasJumps(rupSet)) {
			JumpProbabilityCalc segModel = buildSegModel(rupSet, branch);
			if (segModel != null) {
				constraints = new ArrayList<>(constraints);
				
//				InitialModelParticipationRateEstimator rateEst = new InitialModelParticipationRateEstimator(
//						rupSet, Inversions.getDefaultVariablePerturbationBasis(rupSet));

//				double weight = 0.5d;
//				boolean ineq = false;
				double weight = 100d;
				boolean ineq = true;
				
				constraints.add(new JumpProbabilityConstraint.RelativeRate(
						weight, ineq, rupSet, buildSegModel(rupSet, branch), rateEst));
			}
			
			MaxJumpDistModels distModel = branch.getValue(MaxJumpDistModels.class);
			System.out.println("Max distance model: "+distModel);
			if (distModel != null) {
				HardDistCutoffJumpProbCalc model = distModel.getModel(rupSet);
				ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
				System.out.println("Zeroing out sampler probabilities for "+model);
				sampler = getExcludeSampler(cRups, sampler, model);
			}
		}
		
		int avgThreads = threads / 4;
		
//		CompletionCriteria completion = new IterationsPerVariableCompletionCriteria(5000d);
		// the greater of 2,000 iterations per rupture, but floor the rupture count to be at least 100 times the number
		// of sections, which comes out to a minimum of 200,000 iterations per section
		int numRups = rupSet.getNumRuptures();
		if (sampler != null)
			// only count ruptures we can actually sample
			numRups = sampler.size();
		long equivNumVars = Long.max(numRups, rupSet.getNumSections()*100l);
		CompletionCriteria completion = new IterationCompletionCriteria(equivNumVars*2000l);
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
		
		if (parkWeight > 0d)
			builder.initialSolution(constrBuilder.getParkfieldInitial(true));
		
		return builder.build();
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
						+ "2022_06_10-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-Shift2km-ThreshAvgIterRelGR-IncludeThruCreep/results.zip"));
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
					+ "2022_06_10-nshm23_u3_hybrid_branches-full_sys_inv-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-Shift2km-ThreshAvgIterRelGR-IncludeThruCreep/results.zip"));
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
						+ "2022_06_10-nshm23_u3_hybrid_branches-full_sys_inv-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-Shift2km-ThreshAvgIterRelGR-IncludeThruCreep/results_FM3_1_CoulombRupSet_branch_averaged.zip"));
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

		@Override
		public boolean isSolveClustersIndividually() {
			return false;
		}
		
	}
	
	public static class MFDUncert0p1 extends NSHM23_InvConfigFactory {
		
		public MFDUncert0p1() {
			MFD_MIN_FRACT_UNCERT = 0.1;
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
	
	public static class NewScaleUseOrigWidths extends NSHM23_InvConfigFactory {

		public NewScaleUseOrigWidths() {
			NSHM23_ScalingRelationships.USE_ORIG_WIDTHS = true;
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
