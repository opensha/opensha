package gov.usgs.earthquake.nshmp.erf.nshm27;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.opensha.commons.data.IntegerSampler.ExclusionIntegerSampler;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.RupSetSubsectioningModel;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.RectangularDownDipSubductionRupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.RupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.inversion.ClusterSpecificInversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.GridSourceProviderFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
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
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.util.SlipAlongRuptureModelBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_ConstraintBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.ExclusionaryLogicTreeNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SlipAlongRuptureModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.RupturePlausibilityModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SectionSupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationMFD_Adjustment;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationModelBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSectConstraintModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSeisMoRateReductions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.random.BranchSamplingManager;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.random.RandomBValSampler;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs.SubSeisMoRateReduction;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.GRParticRateEstimator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Range;
import com.google.common.collect.Table;

import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM27_InterfaceFaultModels;
import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM27_InterfaceObsSeisDMAdjustment;
import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM27_ModelRegimeNode;
import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM27_SeisRateModel;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.PureGR;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.RateRecord;

public class NSHM27_InvConfigFactory implements ClusterSpecificInversionConfigurationFactory, GridSourceProviderFactory.Single {
	
	private static final File[] POSSIBLE_DATA_DIRS = {
			new File("/home/kevin/OpenSHA/nshm27/data/"),
			new File("/project2/scec_608/kmilner/nshms/nshm27/data")
	};
	public static File locateDataDirectory() {
		for (File dir : POSSIBLE_DATA_DIRS)
			if (dir.exists())
				return dir;
		throw new IllegalStateException("No data directory located");
	}
	
	protected transient RuptureSets.Cache rupSetCache = new RuptureSets.Cache();
	protected transient Map<RupSetFaultModel, SectionDistanceAzimuthCalculator> distAzCache = new HashMap<>();
	protected transient File cacheDir;
	private boolean autoCache = true;
	
	public static SlipAlongRuptureModelBranchNode SLIP_ALONG_DEFAULT = NSHM23_SlipAlongRuptureModels.UNIFORM;
	public static double MFD_MIN_FRACT_UNCERT = 0.1;
	
	private boolean adjustForActualRupSlips = NSHM23_ConstraintBuilder.ADJ_FOR_ACTUAL_RUP_SLIPS_DEFAULT;
	private boolean adjustForSlipAlong = NSHM23_ConstraintBuilder.ADJ_FOR_SLIP_ALONG_DEFAULT;
	
	public static final long NUM_ITERS_PER_RUP_DEFAULT = 2000l;
	protected long numItersPerRup = NUM_ITERS_PER_RUP_DEFAULT;
	
	public static final boolean SOLVE_CLUSTERS_INDIVIDUALLY_DEFAULT = true;
	protected boolean solveClustersIndividually = SOLVE_CLUSTERS_INDIVIDUALLY_DEFAULT;
	
	public static SubSectConstraintModels SUB_SECT_CONSTR_DEFAULT = SubSectConstraintModels.TOT_NUCL_RATE;
	
	// allow non-rectangular ruptures once they hit this depth range
	public static Range<Double> INTERFACE_MIN_SUPRA_SEIS_DEPTH_RANGE_DEFAULT = Range.closed(20d, 40d);
	private Range<Double> interfaceMinSupraSeisDepthRange = INTERFACE_MIN_SUPRA_SEIS_DEPTH_RANGE_DEFAULT;
	// ..but ensure their aspect ratio doesn't exceed this
	private static double MAX_PARTIAL_SEISMOGENIC_ASPECT_RATIO_DEFAULT = 4d;
	private double maxPartialSeismogenicAspectRatio = MAX_PARTIAL_SEISMOGENIC_ASPECT_RATIO_DEFAULT;

	@Override
	public FaultSystemRupSet buildRuptureSet(LogicTreeBranch<?> branch, int threads) throws IOException {
		// build empty-ish rup set without modules attached
		return updateRuptureSetForBranch(buildGenericRupSet(branch, threads), branch);
	}

	public FaultSystemRupSet buildGenericRupSet(LogicTreeBranch<?> branch, int threads) throws IOException {
		RupSetFaultModel fm = branch.requireValue(RupSetFaultModel.class);
		RupSetSubsectioningModel ssm = branch.requireValue(RupSetSubsectioningModel.class);
		RupturePlausibilityModels model = branch.getValue(RupturePlausibilityModels.class);
		if (model == null) {
			if (fm instanceof NSHM27_InterfaceFaultModels) // Subduction
				model = RupturePlausibilityModels.SUBDUCTION_DD_RECTANGULAR;
			else
				model = RupturePlausibilityModels.COULOMB;
		}
		
		// check cache
		FaultSystemRupSet rupSet = rupSetCache.get(fm, ssm, model);
		if (rupSet != null)
			return rupSet;
		
		RupSetScalingRelationship scale = branch.requireValue(RupSetScalingRelationship.class);
		
		RupSetDeformationModel dm = fm.getDefaultDeformationModel();
		List<? extends FaultSection> subSects;
		try {
			subSects = dm.build(fm, ssm, branch);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		RupSetConfig config = model.getConfig(subSects, scale);
		if (config instanceof RectangularDownDipSubductionRupSetConfig) {
			((RectangularDownDipSubductionRupSetConfig)config).setMinSeismogenicDepthRange(
					interfaceMinSupraSeisDepthRange, maxPartialSeismogenicAspectRatio);
		}
		
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
				+SectionDistanceAzimuthCalculator.getUniqueSectCacheFileStr(subSects)+"_"+momentStr+"_moment"
				+".zip";
			
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
		
		return rupSet;
	}

	@Override
	public FaultSystemRupSet updateRuptureSetForBranch(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch)
			throws IOException {
		// we don't trust any modules attached to this rupture set as it could have been used for another calculation
		// that could have attached anything. Instead, lets only keep the ruptures themselves
		
		RupSetFaultModel fm = branch.requireValue(RupSetFaultModel.class);
		RupSetSubsectioningModel ssm = branch.requireValue(RupSetSubsectioningModel.class);
		RupSetDeformationModel dm = branch.requireValue(RupSetDeformationModel.class);

		// override slip rates for the given deformation model
		List<? extends FaultSection> subSects;
		try {
			subSects = dm.build(fm, ssm, branch);
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
			rupSet.addModule(ClusterRuptures.singleStranded(rupSet));
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
	
	private static NSHM23_ConstraintBuilder getConstraintBuilder(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
//		if (branch.hasValue(NSHM23_SegmentationModels.AVERAGE) || branch.hasValue(SupraSeisBValues.AVERAGE)
//				|| branch.hasValue(PRVI25_CrustalBValues.AVERAGE) || branch.hasValue(PRVI25_SubductionBValues.AVERAGE))
//			// return averaged instance, looping over b-values and/or segmentation branches
//			return getAveragedConstraintBuilder(rupSet, branch);
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
		
		
		
		if (branch.hasValue(NSHM27_InterfaceObsSeisDMAdjustment.class)) {
			NSHM27_InterfaceObsSeisDMAdjustment adjustment = branch.requireValue(NSHM27_InterfaceObsSeisDMAdjustment.class);
			if (adjustment == NSHM27_InterfaceObsSeisDMAdjustment.NONE) {
				constrBuilder.subSeisMoRateReduction(SubSeisMoRateReduction.NONE);
			} else {
				constrBuilder.subSeisMoRateReduction(SubSeisMoRateReduction.FROM_INPUT_SLIP_RATES);
				try {
					adjustment.adjustSlipRates(rupSet, branch);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				NSHM27_InterfaceFaultModels fm = branch.getValue(NSHM27_InterfaceFaultModels.class);
				if (fm != null && branch.hasValue(NSHM27_SeisRateModel.class)) {
					NSHM27_SeisRateModel rateModel = branch.requireValue(NSHM27_SeisRateModel.class);
					RateRecord record = rateModel.getRateRecord(fm.getSeisReg(), TectonicRegionType.SUBDUCTION_INTERFACE);
					if (record instanceof PureGR)
						constrBuilder.subSeisBOverride(((PureGR)record).b);
					else
						constrBuilder.subSeisBOverride(Double.NaN);
				}
			}
		} else {
			constrBuilder.subSeisMoRateReduction(SubSeisMoRateReduction.NONE);
		}
		
		constrBuilder.magDepRelStdDev(M->MFD_MIN_FRACT_UNCERT*Math.max(1, Math.pow(10, bVal*0.5*(M-6))));
		
		constrBuilder.adjustForActualRupSlips(NSHM23_ConstraintBuilder.ADJ_FOR_ACTUAL_RUP_SLIPS_DEFAULT,
				NSHM23_ConstraintBuilder.ADJ_FOR_SLIP_ALONG_DEFAULT);
		
		BinaryRuptureProbabilityCalc rupExclusionModel = getExclusionModel(
				rupSet, branch, rupSet.requireModule(ClusterRuptures.class));
		
		if (rupExclusionModel != null)
			constrBuilder.excludeRuptures(rupExclusionModel);
		
		// apply any segmentation adjustments
		if (hasJumps(rupSet)) {
			// this handles creeping section, binary segmentation, and max dist models
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

	@Override
	public InversionConfiguration buildInversionConfig(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
			int threads) {
		NSHM23_ConstraintBuilder constrBuilder = getConstraintBuilder(rupSet, branch);
		
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
		
		
		// this will exclude ruptures through creeping section, if applicable 
		BinaryRuptureProbabilityCalc rupExcludeModel = constrBuilder.getRupExclusionModel();
		ExclusionIntegerSampler sampler = null;
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
	
	public static boolean hasJumps(FaultSystemRupSet rupSet) {
		for (ClusterRupture cRup : rupSet.requireModule(ClusterRuptures.class))
			if (cRup.getTotalNumJumps() > 0)
				return true;
		return false;
	}
	
	public static BinaryRuptureProbabilityCalc getExclusionModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch,
			ClusterRuptures cRups) {
		// segmentation model, Lmax
		List<BinaryRuptureProbabilityCalc> exclusionModels = new ArrayList<>();
		for (ExclusionaryLogicTreeNode exclusionNode : branch.getValues(ExclusionaryLogicTreeNode.class)) {
			BinaryRuptureProbabilityCalc exclusionModel = exclusionNode.getExclusionModel(rupSet, branch);
			if (exclusionModel != null) {
				System.out.println("Adding exclusion model: "+exclusionModel.getName()+" (from "+exclusionNode.getName()+")");
				exclusionModels.add(exclusionModel);
			}
		}
		
		if (exclusionModels.isEmpty())
			return null;
		if (exclusionModels.size() == 1)
			return exclusionModels.get(0);
		
		System.out.println("Combining "+exclusionModels.size()+" exclusion models");
		
		return new RuptureProbabilityCalc.LogicalAnd(exclusionModels.toArray(new BinaryRuptureProbabilityCalc[0]));
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
	public SolutionProcessor getSolutionLogicTreeProcessor() {
		return new NSHM27SolProcessor();
	}
	
	public static class NSHM27SolProcessor implements SolutionProcessor {
		
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
					return ClusterRuptures.singleStranded(rupSet);
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

	@Override
	public GridSourceProvider buildGridSourceProvider(FaultSystemSolution sol, LogicTreeBranch<?> fullBranch)
			throws IOException {
		NSHM27_ModelRegimeNode modelRegime = fullBranch.requireValue(NSHM27_ModelRegimeNode.class);
		NSHM27_SeismicityRegions seisReg = modelRegime.getRegion();
		TectonicRegionType trt = modelRegime.getTectonicRegime();
		Preconditions.checkNotNull(seisReg, "Model regime node must have seismicity region");
		Preconditions.checkNotNull(trt, "Model regime node must have tectonic regime");
		preGridBuildHook(sol, fullBranch);
		return switch (trt){
		case SUBDUCTION_INTERFACE:
			yield NSHM27_GridSourceBuilder.buildInterfaceGridSourceList(sol, fullBranch, seisReg);
		case SUBDUCTION_SLAB:
			yield NSHM27_GridSourceBuilder.buildIntraslabGridSourceList(fullBranch, seisReg);
		case ACTIVE_SHALLOW:
			yield NSHM27_GridSourceBuilder.buildCrustalGridSourceProv(sol, fullBranch, seisReg);
		default:
			throw new IllegalArgumentException("Unexpected TRT: "+trt);
		};
	}

	@Override
	public void preGridBuildHook(FaultSystemSolution sol, LogicTreeBranch<?> faultBranch) throws IOException {
		NSHM27_GridSourceBuilder.doPreGridBuildHook(sol, faultBranch);
	}

}
