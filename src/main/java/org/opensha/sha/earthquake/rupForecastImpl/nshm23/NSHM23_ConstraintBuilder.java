package org.opensha.sha.earthquake.rupForecastImpl.nshm23;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.IntegerSampler;
import org.opensha.commons.data.IntegerSampler.ExclusionIntegerSampler;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.Uncertainty;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.LaplacianSmoothingInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.ParkfieldInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.RupRateMinimizationConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SectionTotalRateConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SubSectMFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SectBValuePlot;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationMFD_Adjustment;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSectConstraintModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs.Builder;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs.SubSeisMoRateReduction;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.APrioriSectNuclEstimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.SectNucleationMFD_Estimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.SegmentationImpliedSectNuclMFD_Estimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.SegmentationImpliedSectNuclMFD_Estimator.MultiBinDistributionMethod;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.PaleoSectNuclEstimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.ScalingRelSlipRateMFD_Estimator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.inversion.UCERF2_ComparisonSolutionFetcher;
import scratch.UCERF3.inversion.UCERF3InversionInputGenerator;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.U3SectionMFD_constraint;
import scratch.UCERF3.utils.UCERF2_A_FaultMapper;

public class NSHM23_ConstraintBuilder {
	
	private List<InversionConstraint> constraints;
	private FaultSystemRupSet rupSet;
	private double supraBVal;
	private boolean applyDefModelUncertaintiesToNucl;
	private boolean addSectCountUncertaintiesToMFD;
	private boolean adjustForIncompatibleData;
	
	public static boolean ADJ_FOR_ACTUAL_RUP_SLIPS_DEFAULT = true;
	public static boolean ADJ_FOR_SLIP_ALONG_DEFAULT = false;
	private boolean adjustForActualRupSlips = ADJ_FOR_ACTUAL_RUP_SLIPS_DEFAULT;
	private boolean adjustForSlipAlong = ADJ_FOR_SLIP_ALONG_DEFAULT;
	
	private SubSeisMoRateReduction subSeisMoRateReduction = SupraSeisBValInversionTargetMFDs.SUB_SEIS_MO_RATE_REDUCTION_DEFAULT;
	
	private static final double DEFAULT_REL_STD_DEV = 0.1;
	
	private DoubleUnaryOperator magDepRelStdDev = M->DEFAULT_REL_STD_DEV;
	
	private JumpProbabilityCalc segModel;
	private SegmentationMFD_Adjustment segAdjMethod = SegmentationMFD_Adjustment.JUMP_PROB_THRESHOLD_AVG;
	
	public NSHM23_ConstraintBuilder(FaultSystemRupSet rupSet, double supraSeisB) {
		this(rupSet, supraSeisB, SupraSeisBValInversionTargetMFDs.APPLY_DEF_MODEL_UNCERTAINTIES_DEFAULT,
				SupraSeisBValInversionTargetMFDs.ADD_SECT_COUNT_UNCERTAINTIES_DEFAULT, false);
	}
	
	public NSHM23_ConstraintBuilder(FaultSystemRupSet rupSet, double supraBVal,
			boolean applyDefModelUncertaintiesToNucl, boolean addSectCountUncertaintiesToMFD,
			boolean adjustForIncompatibleData) {
		this.rupSet = rupSet;
		this.supraBVal = supraBVal;
		this.applyDefModelUncertaintiesToNucl = applyDefModelUncertaintiesToNucl;
		this.addSectCountUncertaintiesToMFD = addSectCountUncertaintiesToMFD;
		this.adjustForIncompatibleData = adjustForIncompatibleData;
		this.constraints = new ArrayList<>();
	}
	
	public List<InversionConstraint> build() {
		return constraints;
	}
	
	public NSHM23_ConstraintBuilder defaultConstraints() {
		return defaultConstraints(SubSectConstraintModels.TOT_NUCL_RATE);
	}
	
	public NSHM23_ConstraintBuilder defaultConstraints(SubSectConstraintModels subSectConstrModel) {
		return defaultDataConstraints(subSectConstrModel).defaultMetaConstraints();
	}
	
	public NSHM23_ConstraintBuilder except(Class<? extends InversionConstraint> clazz) {
		for (int i=constraints.size(); --i>=0;)
			if (clazz.isAssignableFrom(constraints.get(i).getClass()))
				constraints.remove(i);
		return this;
	}
	
	public NSHM23_ConstraintBuilder add(InversionConstraint constraint) {
		constraints.add(constraint);
		return this;
	}
	
	public NSHM23_ConstraintBuilder defaultDataConstraints() {
		return defaultDataConstraints(SubSectConstraintModels.TOT_NUCL_RATE);
	}
	
	public NSHM23_ConstraintBuilder defaultDataConstraints(SubSectConstraintModels subSectConstrModel) {
		magDepRelStdDev(M->0.1*Math.pow(10, supraBVal*0.5*(M-6)))
				.slipRates().weight(1d)
				.paleoRates().weight(5d).paleoSlips().weight(5d)
				.parkfield().weight(10d);
		if (subSectConstrModel == SubSectConstraintModels.TOT_NUCL_RATE) {
			supraBValMFDs().weight(10).sectSupraRates().weight(0.5);
		} else if (subSectConstrModel == SubSectConstraintModels.NUCL_MFD) {
			supraBValMFDs().weight(1).sectSupraNuclMFDs().weight(0.5);
		} else if (subSectConstrModel == null || subSectConstrModel == SubSectConstraintModels.NONE) {
			supraBValMFDs().weight(1);
		}
		
		return this;
	}
	
	public NSHM23_ConstraintBuilder slipRates() {
		constraints.add(new SlipRateInversionConstraint(1d, ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, rupSet));
		return this;
	}
	
	public NSHM23_ConstraintBuilder paleo() {
		return paleoRates().paleoSlips();
	}
	
	public NSHM23_ConstraintBuilder paleoRates() {
		PaleoseismicConstraintData data = rupSet.requireModule(PaleoseismicConstraintData.class);
		constraints.add(new PaleoRateInversionConstraint(rupSet, 1d, data.getPaleoRateConstraints(), data.getPaleoProbModel()));
		return this;
	}
	
	public NSHM23_ConstraintBuilder paleoSlips() {
		PaleoseismicConstraintData data = rupSet.requireModule(PaleoseismicConstraintData.class);
		constraints.add(new PaleoSlipInversionConstraint(rupSet, 1d, data.getPaleoSlipConstraints(), data.getPaleoSlipProbModel(), true));
		return this;
	}
	
	public NSHM23_ConstraintBuilder subSeisMoRateReduction(SubSeisMoRateReduction subSeisMoRateReduction) {
		this.subSeisMoRateReduction = subSeisMoRateReduction;
		targetCache = null;
		return this;
	}
	
	public NSHM23_ConstraintBuilder magDepRelStdDev(DoubleUnaryOperator magDepRelStdDev) {
		this.magDepRelStdDev = magDepRelStdDev;
		targetCache = null;
		return this;
	}
	
	public NSHM23_ConstraintBuilder adjustForSegmentationModel(JumpProbabilityCalc segModel) {
		this.segModel = segModel;
		targetCache = null;
		return this;
	}
	
	public NSHM23_ConstraintBuilder adjustForSegmentationModel(JumpProbabilityCalc segModel,
			SegmentationMFD_Adjustment segAdjMethod) {
		this.segModel = segModel;
		this.segAdjMethod = segAdjMethod;
		targetCache = null;
		return this;
	}
	
	/**
	 * Some scaling relationships are not be consistent with the way we calculate the total moment required to
	 * satisfy the target slip rate. If enabled, the target MFDs will be modified to account for any discrepancy
	 * between calculated slip rates and the G-R targets 
	 * 
	 * @return
	 */
	public NSHM23_ConstraintBuilder adjustForActualRupSlips(boolean adjustForActualRupSlips, boolean adjustForSlipAlong) {
		this.adjustForActualRupSlips = adjustForActualRupSlips;
		this.adjustForSlipAlong = adjustForSlipAlong;
		return this;
	}
	
	// TODO detect parkfield rups for non-U3 implementations
	
	private static UncertainDataConstraint parkfieldRate = 
		new UncertainDataConstraint("Parkfield", 1d/25d, new Uncertainty(0.1d/25d));
	
	public SupraSeisBValInversionTargetMFDs getTargetMFDs() {
		return getTargetMFDs(supraBVal);
	}
	
	private SupraSeisBValInversionTargetMFDs targetCache;
	
	private SupraSeisBValInversionTargetMFDs getTargetMFDs(double supraBVal) {
		if (targetCache != null && targetCache.getSupraSeisBValue() == supraBVal)
			return targetCache;
		
		SupraSeisBValInversionTargetMFDs.Builder builder = new SupraSeisBValInversionTargetMFDs.Builder(
				rupSet, supraBVal);
		builder.applyDefModelUncertainties(applyDefModelUncertaintiesToNucl);
		builder.magDepDefaultRelStdDev(magDepRelStdDev);
		builder.addSectCountUncertainties(addSectCountUncertaintiesToMFD);
		builder.subSeisMoRateReduction(subSeisMoRateReduction);
		if (segModel != null) {
			if (segModel instanceof BinaryRuptureProbabilityCalc) {
				builder.forBinaryRupProbModel((BinaryRuptureProbabilityCalc)segModel);
			} else {
				SectNucleationMFD_Estimator adjustment = segAdjMethod.getAdjustment(segModel);
				if (adjustment != null)
					builder.adjustTargetsForData(adjustment);
			}
		}
		if (adjustForActualRupSlips)
			builder.adjustTargetsForData(new ScalingRelSlipRateMFD_Estimator(adjustForSlipAlong));
		if (adjustForIncompatibleData) {
			UncertaintyBoundType dataWithinType = UncertaintyBoundType.ONE_SIGMA;
			List<SectNucleationMFD_Estimator> dataConstraints = new ArrayList<>();
			dataConstraints.add(new APrioriSectNuclEstimator(rupSet,
					UCERF3InversionInputGenerator.findParkfieldRups(rupSet), parkfieldRate));
			if (rupSet.hasModule(PaleoseismicConstraintData.class)) {
				PaleoseismicConstraintData paleoData = rupSet.requireModule(PaleoseismicConstraintData.class);
				if (paleoData.getPaleoSlipConstraints() != null) {
					// use updated slip rate
					rupSet.addModule(builder.buildSlipRatesOnly());
				}
				dataConstraints.addAll(PaleoSectNuclEstimator.buildPaleoEstimates(rupSet, true));
			}
			builder.expandUncertaintiesForData(dataConstraints, dataWithinType);
		}
		SupraSeisBValInversionTargetMFDs target = builder.build();
		targetCache = target;
		rupSet.addModule(target);
		return target;
	}
	
	public NSHM23_ConstraintBuilder supraBValMFDs() {
		SupraSeisBValInversionTargetMFDs target = getTargetMFDs(supraBVal);
		
		List<? extends IncrementalMagFreqDist> origMFDs = target.getMFD_Constraints();
		List<UncertainIncrMagFreqDist> uncertainMFDs = new ArrayList<>();
		for (IncrementalMagFreqDist mfd : origMFDs) {
			if (mfd instanceof UncertainIncrMagFreqDist) {
				// already uncertain
				uncertainMFDs.add((UncertainIncrMagFreqDist)mfd);
			} else {
				System.err.println("WARNING: temporary relative standard deviation of "+(float)DEFAULT_REL_STD_DEV
						+" set for all MFD bins"); // TODO
				UncertainIncrMagFreqDist uMFD = UncertainIncrMagFreqDist.constantRelStdDev(mfd, DEFAULT_REL_STD_DEV);
//				for (int i=0; i<uMFD.size(); i++)
//					System.out.println(uMFD.getX(i)+"\t"+uMFD.getY(i)+"\t"+uMFD.getStdDev(i));
				uncertainMFDs.add(uMFD);
			}
		}
		constraints.add(new MFDInversionConstraint(rupSet, 1d, false,
				ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, uncertainMFDs));
		return this;
	}
	
	public NSHM23_ConstraintBuilder sectSupraRates() {
		SupraSeisBValInversionTargetMFDs target = getTargetMFDs(supraBVal);
		
		double[] targetRates = new double[rupSet.getNumSections()];
		double[] targetRateStdDevs = new double[rupSet.getNumSections()];
		
		List<UncertainIncrMagFreqDist> sectSupraMFDs = target.getOnFaultSupraSeisNucleationMFDs();
		for (int s=0; s<targetRates.length; s++) {
			UncertainIncrMagFreqDist sectSupraMFD = sectSupraMFDs.get(s);
			targetRates[s] = sectSupraMFD.calcSumOfY_Vals();
			UncertainBoundedIncrMagFreqDist oneSigmaBoundedMFD = sectSupraMFD.estimateBounds(UncertaintyBoundType.ONE_SIGMA);
			double upperVal = oneSigmaBoundedMFD.getUpper().calcSumOfY_Vals();
			double lowerVal = oneSigmaBoundedMFD.getLower().calcSumOfY_Vals();
			targetRateStdDevs[s] = UncertaintyBoundType.ONE_SIGMA.estimateStdDev(targetRates[s], lowerVal, upperVal);
//			System.out.println(rupSet.getFaultSectionData(s).getSectionName()+": totRate="+(float)targetRates[s]
//					+"\tstdDev="+(float)targetRateStdDevs[s]+"\trelStdDev="+(float)(targetRateStdDevs[s]/targetRates[s]));
		}
		
		constraints.add(new SectionTotalRateConstraint(rupSet, 1d,
				ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, targetRates, targetRateStdDevs, true));
		return this;
	}
	
	public NSHM23_ConstraintBuilder sectSupraNuclMFDs() {
		SupraSeisBValInversionTargetMFDs target = getTargetMFDs(supraBVal);
		
		List<UncertainIncrMagFreqDist> sectSupraMFDs = target.getOnFaultSupraSeisNucleationMFDs();
		
		constraints.add(new SubSectMFDInversionConstraint(rupSet, 1d,
				ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, sectSupraMFDs, true));
		return this;
	}
	
	public NSHM23_ConstraintBuilder parkfield() {
		double parkfieldMeanRate = 1.0/25.0; // Bakun et al. (2005)
		System.err.println("WARNING: temporary relative standard deviation of "
				+(float)DEFAULT_REL_STD_DEV+" set for parkfield constraint"); // TODO
		double parkfieldStdDev = DEFAULT_REL_STD_DEV*parkfieldMeanRate;
		
		// Find Parkfield M~6 ruptures
		List<Integer> parkfieldRups = UCERF3InversionInputGenerator.findParkfieldRups(rupSet);
		constraints.add(new ParkfieldInversionConstraint(1d, parkfieldMeanRate, parkfieldRups,
				ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, parkfieldStdDev));
		return this;
	}
	
	public NSHM23_ConstraintBuilder defaultMetaConstraints() {
		return supraPaleoSmooth().weight(10000);
	}
	
	public List<Integer> getRupIndexesBelowMinMag() {
		ModSectMinMags modMinMags = rupSet.requireModule(ModSectMinMags.class);
		Preconditions.checkNotNull(modMinMags, "Rupture set must supply ModSectMinMags if minimization constraint is enabled");
		
		// we want to only grab ruptures with magnitudes below the MFD bin in which the section minimium magnitude resides
		EvenlyDiscretizedFunc refMagFunc = SupraSeisBValInversionTargetMFDs.buildRefXValues(rupSet);
		
		List<Integer> belowMinIndexes = new ArrayList<>();
		float maxMin = (float)StatUtils.max(modMinMags.getMinMagForSections());
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			double mag = rupSet.getMagForRup(r);
			if ((float)mag >= maxMin)
				continue;
			if (modMinMags.isRupBelowSectMinMag(r, refMagFunc))
				belowMinIndexes.add(r);
		}
		System.out.println("Found "+belowMinIndexes.size()+" ruptures below sect min mags");
		return belowMinIndexes;
	}
	
	public ExclusionIntegerSampler getSkipBelowMinSampler() {
		return new ExclusionIntegerSampler(0, rupSet.getNumRuptures(), getRupIndexesBelowMinMag());
	}
	
	public NSHM23_ConstraintBuilder minimizeBelowSectMinMag() {
		List<Integer> belowMinIndexes = getRupIndexesBelowMinMag();
		constraints.add(new RupRateMinimizationConstraint(100000, belowMinIndexes));
		return this;
	}
	
	public NSHM23_ConstraintBuilder supraSmooth() {
		constraints.add(new LaplacianSmoothingInversionConstraint(rupSet, 1000));
		return this;
	}
	
	public NSHM23_ConstraintBuilder supraPaleoSmooth() {
		HashSet<Integer> paleoParentIDs = new HashSet<>();
		
		PaleoseismicConstraintData paleoData = rupSet.requireModule(PaleoseismicConstraintData.class);
		List<SectMappedUncertainDataConstraint> paleos = new ArrayList<>();
		if (paleoData.hasPaleoRateConstraints())
			paleos.addAll(paleoData.getPaleoRateConstraints());
		if (paleoData.hasPaleoSlipConstraints())
			paleos.addAll(paleoData.getPaleoSlipConstraints());
		Preconditions.checkState(!paleos.isEmpty());
		for (SectMappedUncertainDataConstraint paleo : paleos)
			paleoParentIDs.add(rupSet.getFaultSectionData(paleo.sectionIndex).getParentSectionId());
		constraints.add(new LaplacianSmoothingInversionConstraint(rupSet, 1000, paleoParentIDs));
		return this;
	}
	
	/**
	 * sets the weight of all constraints of the given type
	 * 
	 * @param weight
	 * @return
	 */
	public NSHM23_ConstraintBuilder weight(Class<? extends InversionConstraint> clazz, double weight) {
		for (int i=constraints.size(); --i>=0;) {
			InversionConstraint constraint = constraints.get(i);
			if (clazz.isAssignableFrom(constraint.getClass()))
				constraint.setWeight(weight);
		}
		return this;
	}
	
	/**
	 * sets the weight of the most recently added constraint
	 * 
	 * @param weight
	 * @return
	 */
	public NSHM23_ConstraintBuilder weight(double weight) {
		Preconditions.checkState(!constraints.isEmpty());
		constraints.get(constraints.size()-1).setWeight(weight);
		return this;
	}
	
	public NSHM23_ConstraintBuilder testFlipBVals(FaultSystemSolution prevSol, double targetBVal) {
		Preconditions.checkState(rupSet.isEquivalentTo(prevSol.getRupSet()));
		SupraSeisBValInversionTargetMFDs targetMFDs = getTargetMFDs(targetBVal);
		
		List<UncertainIncrMagFreqDist> origSupraNuclMFDs = targetMFDs.getOnFaultSupraSeisNucleationMFDs();
		
		double[] solRupMoRates = SectBValuePlot.calcRupMomentRates(prevSol);

		double[] targetNuclRates = new double[rupSet.getNumSections()];
		double[] targetNuclRateStdDevs = new double[rupSet.getNumSections()];
		
		MinMaxAveTracker solBVals = new MinMaxAveTracker();
		MinMaxAveTracker flippedBVals = new MinMaxAveTracker();
		for (int s=0; s<targetNuclRates.length; s++) {
			IncrementalMagFreqDist origMFD = origSupraNuclMFDs.get(s);
			
			double minMag = Double.POSITIVE_INFINITY;
			double maxMag = 0d;
			for (int i=0; i<origMFD.size(); i++) {
				if (origMFD.getY(i) > 0) {
					double x = origMFD.getX(i);
					minMag = Math.min(minMag, x);
					maxMag = Math.max(maxMag, x);
				}
			}
			
			double solSupraNuclRate = 0d;
			double solSupraMoRate = 0d;
			double sectArea = rupSet.getAreaForSection(s);
			for (int r : rupSet.getRupturesForSection(s)) {
				double rate = prevSol.getRateForRup(r);
				if (rate > 0) {
					double fract = sectArea/rupSet.getAreaForRup(r);
					solSupraNuclRate += rate*fract;
					solSupraMoRate += solRupMoRates[r]*fract;
				}
			}
			
			int numOrig = 1+origMFD.getClosestXIndex(maxMag)-origMFD.getClosestXIndex(minMag);
			GutenbergRichterMagFreqDist grWithSolRate = new GutenbergRichterMagFreqDist(
					minMag, numOrig, origMFD.getDelta());
			grWithSolRate.setAllButBvalue(minMag, maxMag, solSupraMoRate, solSupraNuclRate);
			
			double solBVal = grWithSolRate.get_bValue();
			solBVals.addValue(solBVal);
			
			double flippedBVal = targetBVal + (targetBVal-solBVal);
			flippedBVals.addValue(flippedBVal);
			
			GutenbergRichterMagFreqDist grWithFlippedB = new GutenbergRichterMagFreqDist(
					minMag, numOrig, origMFD.getDelta());
			grWithFlippedB.setAllButTotCumRate(minMag, maxMag, origMFD.getTotalMomentRate(), flippedBVal);
			
			double flippedNuclRate = grWithFlippedB.getTotalIncrRate();
			
			System.out.println(s+". targetB="+(float)targetBVal+"\ttargetRate="+(float)origMFD.getTotalIncrRate()
					+"\ttargetMoRate="+(float)origMFD.getTotalMomentRate()+"\tsolMoRate="+(float)solSupraMoRate
					+"\tsolRate="+(float)solSupraNuclRate+"\tsolBVal="+(float)solBVal
					+"\tflippedBVal="+(float)flippedBVal+"\tflippedRate="+(float)flippedNuclRate);
			targetNuclRates[s] = flippedNuclRate;
			targetNuclRateStdDevs[s] = DEFAULT_REL_STD_DEV*targetNuclRates[s];
		}
		System.out.println("Solution b-values: "+solBVals);
		System.out.println("Flipped b-values: "+flippedBVals);
		constraints.add(new SectionTotalRateConstraint(rupSet, 1d,
				ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, targetNuclRates, targetNuclRateStdDevs, true));
		return this;
	}
	
	public NSHM23_ConstraintBuilder testSameBVals(FaultSystemSolution prevSol) {
		Preconditions.checkState(rupSet.isEquivalentTo(prevSol.getRupSet()));
		double[] targetNuclRates = new double[rupSet.getNumSections()];
		double[] targetNuclRateStdDevs = new double[rupSet.getNumSections()];
		for (int s=0; s<targetNuclRates.length; s++) {
			double solSupraNuclRate = 0d;
			double sectArea = rupSet.getAreaForSection(s);
			for (int r : rupSet.getRupturesForSection(s))
				solSupraNuclRate += prevSol.getRateForRup(r)*sectArea/rupSet.getAreaForRup(r);
			
			targetNuclRates[s] = solSupraNuclRate;
			targetNuclRateStdDevs[s] = DEFAULT_REL_STD_DEV*targetNuclRates[s];
		}
		constraints.add(new SectionTotalRateConstraint(rupSet, 1d,
				ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, targetNuclRates, targetNuclRateStdDevs, true));
		return this;
	}
	
	public IntegerPDF_FunctionSampler testGetSampleAllNew(FaultSystemSolution prevSol, boolean skipBelow) {
		Preconditions.checkState(rupSet.isEquivalentTo(prevSol.getRupSet()));
		double[] weights = new double[rupSet.getNumRuptures()];
		Arrays.fill(weights, 1d);
		if (skipBelow)
			for (int r : getRupIndexesBelowMinMag())
				weights[r] = 0;
		for (int r=0; r<weights.length; r++)
			if (prevSol.getRateForRup(r) > 0d)
				weights[r] = 0;
		return new IntegerPDF_FunctionSampler(weights);
	}
	
	public NSHM23_ConstraintBuilder u2NuclBVals(boolean aFaults, boolean reproduce) {
		U3LogicTreeBranch branch = rupSet.requireModule(U3LogicTreeBranch.class);
		AveSlipModule aveSlipModule = rupSet.requireModule(AveSlipModule.class);
		ScalingRelationships scalingRel = branch.getValue(ScalingRelationships.class);
		double fractGR = 0.33333;
				
		FaultSystemSolution u2Sol = null;
		FaultSystemRupSet u2RupSet = null;
		if (aFaults && reproduce) {
			u2Sol = UCERF2_ComparisonSolutionFetcher.getUCERF2Solution(
					rupSet, branch.getValue(FaultModels.class), aveSlipModule);
			u2RupSet = u2Sol.getRupSet();
		}
		
		double[] targetNuclRates = new double[rupSet.getNumSections()];
		double[] targetNuclRateStdDevs = new double[rupSet.getNumSections()];
		
		// targets with b=1
		SectSlipRates slipRates = rupSet.getSectSlipRates();
		SupraSeisBValInversionTargetMFDs targetB1 = new SupraSeisBValInversionTargetMFDs.Builder(u2RupSet, 1d)
				.subSeisMoRateReduction(SubSeisMoRateReduction.SYSTEM_AVG_IMPLIED_FROM_SUPRA_B).build();
		// don't use slip rates with this target
		rupSet.addModule(slipRates);

		MinMaxAveTracker implBValU2Track = new MinMaxAveTracker();
		MinMaxAveTracker implBValU2AFaultTrack = new MinMaxAveTracker();

		MinMaxAveTracker implBValTrack = new MinMaxAveTracker();
		MinMaxAveTracker implBValAFaultTrack = new MinMaxAveTracker();
		
		Map<Integer, List<FaultSection>> sectsByParent = rupSet.getFaultSectionDataList().stream().collect(
				Collectors.groupingBy(S -> S.getParentSectionId()));
		
		List<U3SectionMFD_constraint> u2Constraints = null;
		if (!reproduce) {
			u2Constraints = FaultSystemRupSetCalc.getCharInversionSectMFD_Constraints(rupSet);
			aFaults = false; // always use old constraint
		}
		
		Map<Integer, Double> parentAreas = new HashMap<>();
		Map<Integer, Double> parentLengths = new HashMap<>();
		for (Integer parentID : sectsByParent.keySet()) {
			double area = 0d;
			double len = 0d;
			for (FaultSection sect : sectsByParent.get(parentID)) {
				area += rupSet.getAreaForSection(sect.getSectionId()); // m
				len += sect.getTraceLength()*1e-3; // km -> m
			}
			parentAreas.put(parentID, area);
			parentLengths.put(parentID, len);
		}
		
		for (int s=0;s <rupSet.getNumSections(); s++) {
			FaultSection data = rupSet.getFaultSectionData(s);
			IncrementalMagFreqDist sectNuclB1 = targetB1.getOnFaultSupraSeisNucleationMFDs().get(s);
			int minIndex = -1;
			int maxIndex = 0;
			for (int i=0; i<sectNuclB1.size(); i++) {
				if (sectNuclB1.getY(i) > 0d) {
					maxIndex = i;
					if (minIndex < 0)
						minIndex = i;
				}
			}
			double minMag = sectNuclB1.getX(minIndex);
			double maxMag = sectNuclB1.getX(maxIndex);
			double totSectMoment = sectNuclB1.getTotalMomentRate();
			
			boolean aFault = false;
			
			double maxCharMag = 0d;
			int maxCharMagIndex = -1;
			
			if (!reproduce) {
				// just use UCERF2
				U3SectionMFD_constraint u2Constr = u2Constraints.get(s);
				if (u2Constr == null) {
					targetNuclRates[s] = Double.NaN;
					targetNuclRateStdDevs[s] = Double.NaN;
				} else {
					targetNuclRates[s] = u2Constr.getCumMFD().getY(0);
					targetNuclRateStdDevs[s] = DEFAULT_REL_STD_DEV*targetNuclRates[s];
					maxCharMag = u2Constr.getMag(u2Constr.getNumMags()-1);
					maxCharMagIndex = sectNuclB1.getClosestXIndex(maxCharMag);
					if (maxCharMagIndex < minIndex) {
						System.err.println("WARNING: characteristic mag ("+(float)maxCharMag+") is below sect min mag ("
								+(float)minMag+"), setting to sect min: "+s+". "+data.getName());
						maxCharMagIndex = minIndex;
					}
					maxCharMag = sectNuclB1.getX(maxCharMagIndex);
					aFault = UCERF2_A_FaultMapper.wasUCERF2_TypeAFault(data.getParentSectionId());
				}
			}
			
			if (targetNuclRates[s] == 0d && aFaults && UCERF2_A_FaultMapper.wasUCERF2_TypeAFault(data.getParentSectionId())) {
				// type a fault, use UCERF2
				double solSupraNuclRate = 0d;
				double sectArea = rupSet.getAreaForSection(s);
				maxCharMag = 0d;
				for (int r : u2RupSet.getRupturesForSection(s)) {
					double rate = u2Sol.getRateForRup(r);
					solSupraNuclRate += rate*sectArea/u2RupSet.getAreaForRup(r);
					if (rate > 0d)
						maxCharMag = Math.max(maxCharMag, u2RupSet.getMagForRup(r));
				}
				maxCharMagIndex = sectNuclB1.getClosestXIndex(maxCharMag);
				if (maxCharMagIndex < minIndex) {
					System.err.println("WARNING: characteristic mag ("+(float)maxCharMag+") is below sect min mag ("
							+(float)minMag+"), setting to sect min: "+s+". "+data.getName());
					maxCharMagIndex = minIndex;
				}
				maxCharMag = sectNuclB1.getX(maxCharMagIndex);
				if (solSupraNuclRate == 0d) {
					System.err.println("WARNING: no a-fault nucl rate for "+s+". "+data.getName()+", reverting to b-fault formula");
				} else {
					Preconditions.checkState(solSupraNuclRate > 0d, "nucl rate is zero for a-fault: %s. %s", s, data.getName());
					targetNuclRates[s] = solSupraNuclRate;
					targetNuclRateStdDevs[s] = DEFAULT_REL_STD_DEV*targetNuclRates[s];
					aFault = true;
				}
			}
			
			if (targetNuclRates[s] == 0d) {
				// b fault, use functional form
				
				// compute max mag for rupture filling parent section area
				double area = parentAreas.get(data.getParentSectionId());	// m-sq
				double length = parentLengths.get(data.getParentSectionId()); // m
				double width = area/length;	// km
				maxCharMag = scalingRel.getMag(area, width, data.getAveRake());
				maxCharMagIndex = sectNuclB1.getClosestXIndex(maxCharMag);
				if (maxCharMagIndex < minIndex) {
					System.err.println("WARNING: characteristic mag ("+(float)maxCharMag+") is below sect min mag ("
							+(float)minMag+"), setting to sect min: "+s+". "+data.getName());
					maxCharMagIndex = minIndex;
				}
				Preconditions.checkState(maxCharMagIndex <= maxIndex,
						"charMag=%s, charMagIndex%s, supraMin=%s, supraMinIndex=%s, supraMax=%s, supraMaxIndex=%s",
						maxCharMag, maxCharMagIndex, minMag, minIndex, maxMag, maxIndex);
				maxCharMag = sectNuclB1.getX(maxCharMagIndex);
				
				// create characteristic MFD where all moment is put in a single magnitude bin related to the full
				// parent section characteristic rupture
				GutenbergRichterMagFreqDist charMFD = new GutenbergRichterMagFreqDist(
						sectNuclB1.getMinX(), sectNuclB1.size(), sectNuclB1.getDelta());
				double charMoment = (1d-fractGR)*totSectMoment;
				charMFD.setAllButTotCumRate(maxCharMag, maxCharMag, charMoment, 1d);
				
				// create G-R from min-supra to parent section characteristic max
				GutenbergRichterMagFreqDist grMFD = new GutenbergRichterMagFreqDist(
						sectNuclB1.getMinX(), sectNuclB1.size(), sectNuclB1.getDelta());
				double grMoment = fractGR*totSectMoment;
				grMFD.setAllButTotCumRate(minMag, maxCharMag, grMoment, 1d);
				
				// sum them to create U2-style target
				Preconditions.checkState((float)(grMFD.getTotalMomentRate()+charMFD.getTotalMomentRate()) == (float)totSectMoment);
				SummedMagFreqDist u2Target = new SummedMagFreqDist(charMFD.getMinX(), charMFD.size(), charMFD.getDelta());
				u2Target.addIncrementalMagFreqDist(grMFD);
				u2Target.addIncrementalMagFreqDist(charMFD);
				
				Preconditions.checkState((float)u2Target.getTotalMomentRate() == (float)totSectMoment);
				
//				if (s % 50 == 0)
//					System.out.println(u2Target);
				
				targetNuclRates[s] = u2Target.getTotalIncrRate();
				Preconditions.checkState(targetNuclRates[s] > 0d, "nucl rate is zero for b-fault: %s. %s", s, data.getName());
				targetNuclRateStdDevs[s] = DEFAULT_REL_STD_DEV*targetNuclRates[s];
			}
//			if (data.getName().contains("Mono Lake") || data.getName().contains("Robinson"))
			if (!Double.isFinite(targetNuclRates[s])) {
				System.out.println(s+".\tNONE");
			} else {
				GutenbergRichterMagFreqDist grWithSolRate = new GutenbergRichterMagFreqDist(
						sectNuclB1.getMinX(), sectNuclB1.size(), sectNuclB1.getDelta());
				grWithSolRate.setAllButBvalue(sectNuclB1.getX(minIndex), maxMag, totSectMoment, targetNuclRates[s]);
				
				double implB = grWithSolRate.get_bValue();
				
				// now same thing, but only up to characteristic max mag
				grWithSolRate = new GutenbergRichterMagFreqDist(
						sectNuclB1.getMinX(), sectNuclB1.size(), sectNuclB1.getDelta());
				grWithSolRate.setAllButBvalue(sectNuclB1.getX(minIndex), maxCharMag, totSectMoment, targetNuclRates[s]);
				
				double implU2B = grWithSolRate.get_bValue();
				
				System.out.println(s+".\trate="+(float)targetNuclRates[s]+"\tb="+(float)implB+"\tb-to-char-max="
						+(float)implU2B+"\taFault="+aFault+"\tsectMin="+(float)minMag+"\tcharMax="+(float)maxCharMag
						+"\tcharBins="+(1+maxCharMagIndex-minIndex));
				
				if (minIndex != maxIndex) {
					if (aFault) {
						implBValAFaultTrack.addValue(implB);
						if (minIndex != maxCharMagIndex)
							implBValU2AFaultTrack.addValue(implU2B);
					} else {
						implBValTrack.addValue(implB);
						if (minIndex != maxCharMagIndex)
							implBValU2Track.addValue(implU2B);
					}
				}
			}
		}
		if (implBValAFaultTrack.getNum() > 0) {
			System.out.println("UCERF2-style implied b-values:");
			System.out.println("\t"+implBValAFaultTrack.getNum()+" a-faults: "+implBValAFaultTrack);
			System.out.println("\t"+implBValAFaultTrack.getNum()+" a-faults, to char-max: "+implBValU2AFaultTrack);
			System.out.println("\t"+implBValTrack.getNum()+" b-faults: "+implBValTrack);
			System.out.println("\t"+implBValTrack.getNum()+" b-faults, to char-max: "+implBValU2Track);
		} else {
			System.out.println("UCERF2-style implied b-values: "+implBValTrack);
			System.out.println("UCERF2-style implied b-values, to char-max: "+implBValU2Track);
		}
		
		constraints.add(new SectionTotalRateConstraint(rupSet, 1d,
				ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, targetNuclRates, targetNuclRateStdDevs, true));
		return this;
	}
	
	public IntegerPDF_FunctionSampler testSampleFaultsEqually(boolean skipBelow, double cap) {
		Map<Integer, List<FaultSection>> sectsByParent = rupSet.getFaultSectionDataList().stream().collect(
				Collectors.groupingBy(S -> S.getParentSectionId()));
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		Map<Integer, Integer> parentRupCounts = new HashMap<>();
		int maxRups = 0;
		int minRups = Integer.MAX_VALUE;
		
		HashSet<Integer> skipRups = null;
		if (skipBelow)
			skipRups = new HashSet<>(getRupIndexesBelowMinMag());
		
		for (int parentID : sectsByParent.keySet()) {
			List<Integer> rups = rupSet.getRupturesForParentSection(parentID);
			int count = rups.size();
			if (skipBelow)
				for (Integer r : rups)
					if (skipRups.contains(r))
						count--;
			maxRups = Integer.max(maxRups, count);
			if (count > 0)
				minRups = Integer.min(minRups, count);
			parentRupCounts.put(parentID, count);
		}
		System.out.println("Max ruptures per section: "+maxRups);
		System.out.println("Min ruptures per section: "+minRups);
		
		double[] sampleRates = new double[rupSet.getNumRuptures()];
		
		MinMaxAveTracker scoreTrack = new MinMaxAveTracker();
		for (int r=0; r<sampleRates.length; r++) {
			if (skipBelow && skipRups.contains(r))
				continue;
			ClusterRupture rup = cRups.get(r);
			double avgScore = 0d;
			for (FaultSubsectionCluster cluster : rup.getClustersIterable())
				avgScore += (double)maxRups/parentRupCounts.get(cluster.parentSectionID).doubleValue();
			avgScore /= (double)rup.getTotalNumClusters();
			avgScore = Math.min(avgScore, cap);
			sampleRates[r] = avgScore;
			scoreTrack.addValue(avgScore);
		}
		System.out.println("Sample score range: "+scoreTrack);
		
		return new IntegerPDF_FunctionSampler(sampleRates);
	}
	
	public NSHM23_ConstraintBuilder parkfieldHackSectSupraRates(double parkfieldRelStdDev) {
		SupraSeisBValInversionTargetMFDs target = getTargetMFDs(supraBVal);
		
		double[] targetRates = new double[rupSet.getNumSections()];
		double[] targetRateStdDevs = new double[rupSet.getNumSections()];
		
		System.err.println("WARNING: temporary relative standard deviation of "+(float)DEFAULT_REL_STD_DEV
				+" set for all section target rates"); // TODO
		
		List<UncertainIncrMagFreqDist> sectSupraMFDs = target.getOnFaultSupraSeisNucleationMFDs();
		int numPark = 0;
		for (int s=0; s<targetRates.length; s++) {
			targetRates[s] = sectSupraMFDs.get(s).calcSumOfY_Vals();
			targetRateStdDevs[s] = DEFAULT_REL_STD_DEV*targetRates[s];
			if (rupSet.getFaultSectionData(s).getName().toLowerCase().contains("parkfield")) {
				targetRateStdDevs[s] = targetRates[s]*parkfieldRelStdDev;
				numPark++;
			}
//			System.out.println(rupSet.getFaultSectionData(s).getSectionName()+": total rate: "+targetRates[s]);
		}
		Preconditions.checkState(numPark > 0);
		
		constraints.add(new SectionTotalRateConstraint(rupSet, 1d,
				ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, targetRates, targetRateStdDevs, true));
		return this;
	}
	
	public IntegerPDF_FunctionSampler testGetJumpDistSampler(double maxJumpDist, boolean skipBelow) {
		double[] weights = new double[rupSet.getNumRuptures()];
		Arrays.fill(weights, 1d);
		if (skipBelow)
			for (int r : getRupIndexesBelowMinMag())
				weights[r] = 0;
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		for (int r=0; r<weights.length; r++) {
			for (Jump jump : cRups.get(r).getJumpsIterable()) {
				if ((float)jump.distance > (float)maxJumpDist) {
					weights[r] = 0d;
					break;
				}
			}
		}
		return new IntegerPDF_FunctionSampler(weights);
	}
	
	public double[] getParkfieldInitial(boolean ensureNotSkipped) {
		List<Integer> parkRups = new ArrayList<>(UCERF3InversionInputGenerator.findParkfieldRups(rupSet));
		double[] initial = new double[rupSet.getNumRuptures()];
		if (ensureNotSkipped) {
			HashSet<Integer> skips = new HashSet<Integer>(getRupIndexesBelowMinMag());
			for (int r=parkRups.size(); --r>=0;)
				if (skips.contains(parkRups.get(r)))
					parkRups.remove(r);
			Preconditions.checkState(!skips.isEmpty(), "All parkfield rups are skipped!");
		}
		double rateEach = parkfieldRate.bestEstimate/(double)parkRups.size();
		for (int rup : parkRups)
			initial[rup] = rateEach;
		return initial;
	}

}
