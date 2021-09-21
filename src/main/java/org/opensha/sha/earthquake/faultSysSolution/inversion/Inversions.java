package org.opensha.sha.earthquake.faultSysSolution.inversion;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDEqualityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.TotalMomentInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.TotalRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.InitialSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.WaterLevelRates;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
import scratch.UCERF3.inversion.UCERF3InversionInputGenerator;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.simulatedAnnealing.SerialSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.SimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.IterationCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.ProgressTrackingCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.TimeCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.params.GenerationFunctionType;
import scratch.UCERF3.simulatedAnnealing.params.NonnegativityConstraintType;
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoRateConstraint;

public class Inversions {
	
	public static class UCERF3 extends InversionConfiguration {
		
		private LogicTreeBranch<?> branch;

		public UCERF3() {
			this(null);
		}

		public UCERF3(LogicTreeBranch<?> branch) {
			if (branch != null)
				Preconditions.checkState(branch.isFullySpecified());
			this.branch = branch;
		}

		@Override
		public InversionInputGenerator build(FaultSystemRupSet rupSet) {
			if (branch == null)
				branch = rupSet.requireModule(LogicTreeBranch.class);
			if (!rupSet.hasModule(AveSlipModule.class))
				rupSet.addModule(AveSlipModule.forModel(rupSet, branch.getValue(ScalingRelationships.class)));
			rupSet.addModule(branch.getValue(SlipAlongRuptureModels.class).getModel());
			if (!rupSet.hasModule(ModSectMinMags.class))
				rupSet.addModule(ModSectMinMags.instance(rupSet, FaultSystemRupSetCalc.computeMinSeismoMagForSections(
							rupSet, InversionFaultSystemRupSet.MIN_MAG_FOR_SEISMOGENIC_RUPS)));
			FaultModels fm = branch.getValue(FaultModels.class);
			if (!rupSet.hasModule(PolygonFaultGridAssociations.class)) {
				PolygonFaultGridAssociations polys;
				if ((fm == FaultModels.FM3_1 && rupSet.getNumSections() == 2606)
						|| (fm == FaultModels.FM3_2 && rupSet.getNumSections() == 2664)) {
					try {
						polys = FaultPolyMgr.loadSerializedUCERF3(fm);
					} catch (IOException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
				} else {
					polys = FaultPolyMgr.create(rupSet.getFaultSectionDataList(), U3InversionTargetMFDs.FAULT_BUFFER);
				}
				rupSet.addModule(polys);
			}
			InversionTargetMFDs targetMFDs = rupSet.getModule(InversionTargetMFDs.class);
			if (targetMFDs == null) {
				targetMFDs = new U3InversionTargetMFDs(rupSet, branch, rupSet.requireModule(ModSectMinMags.class),
						rupSet.requireModule(PolygonFaultGridAssociations.class));
				rupSet.addModule(targetMFDs);
			}
			rupSet.addModule(InversionFaultSystemRupSet.computeTargetSlipRates(rupSet,
							branch.getValue(InversionModels.class), branch.getValue(MomentRateFixes.class), targetMFDs));
			UCERF3InversionConfiguration u3Config = UCERF3InversionConfiguration.forModel(
					InversionModels.CHAR_CONSTRAINED, rupSet, fm, targetMFDs);
			
			// get the paleo rate constraints
			try {
				List<PaleoRateConstraint> paleoRateConstraints = CommandLineInversionRunner.getPaleoConstraints(
						fm, rupSet);

				// get the improbability constraints
				double[] improbabilityConstraint = null; // none

				// paleo probability model
				PaleoProbabilityModel paleoProbabilityModel = UCERF3InversionInputGenerator.loadDefaultPaleoProbabilityModel();

				List<AveSlipConstraint> aveSlipConstraints = AveSlipConstraint.load(rupSet.getFaultSectionDataList());

				return new UCERF3InversionInputGenerator(rupSet, u3Config, paleoRateConstraints, aveSlipConstraints,
						improbabilityConstraint, paleoProbabilityModel);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
	}
	
	public static class SlipRates extends InversionConfiguration {
		
		private double regularSlipWeight = 100;
		private double normalizedSlipWeight = 1;

		@Override
		public InversionInputGenerator build(FaultSystemRupSet rupSet) {
			SlipRateInversionConstraint constraint = slipConstraint(rupSet, regularSlipWeight, normalizedSlipWeight);
			Preconditions.checkNotNull(constraint, "Must enable regular slip constraint and/or normalized");
			return new InversionInputGenerator(rupSet, List.of(constraint));
		}
		
	}
	
	private static SlipRateInversionConstraint slipConstraint(FaultSystemRupSet rupSet, double regularSlipWeight, double normalizedSlipWeight) {
		AveSlipModule aveSlipModule = rupSet.requireModule(AveSlipModule.class);
		SlipAlongRuptureModel slipAlong = rupSet.requireModule(SlipAlongRuptureModel.class);
		double[] targetSlipRates = rupSet.requireModule(SectSlipRates.class).getSlipRates();
		
		SlipRateConstraintWeightingType weightingType;
		if (regularSlipWeight > 0 && normalizedSlipWeight > 0)
			weightingType = SlipRateConstraintWeightingType.BOTH;
		else if (regularSlipWeight > 0)
			weightingType = SlipRateConstraintWeightingType.UNNORMALIZED;
		else if (normalizedSlipWeight > 0)
			weightingType = SlipRateConstraintWeightingType.NORMALIZED_BY_SLIP_RATE;
		else
			return null;
		return new SlipRateInversionConstraint(normalizedSlipWeight, regularSlipWeight, weightingType, rupSet,
				aveSlipModule, slipAlong, targetSlipRates);
	}
	
	public static abstract class InversionConfiguration {
		
		public abstract InversionInputGenerator build(FaultSystemRupSet rupSet);
	}
	
	private enum Presets {
		UCERF3 {
			@Override
			public InversionConfiguration build() {
				return new UCERF3(null);
			}
		},
		SLIP_RATES {

			@Override
			public InversionConfiguration build() {
				return new SlipRates();
			}
			
		};
		
		public abstract InversionConfiguration build();
	}
	
	private static final GenerationFunctionType PERTURB_DEFAULT = GenerationFunctionType.VARIABLE_EXPONENTIAL_SCALE;
	private static final NonnegativityConstraintType NON_NEG_DEFAULT = NonnegativityConstraintType.TRY_ZERO_RATES_OFTEN;
	
	private static CompletionCriteria getCompletion(String value) {
		value = value.trim().toLowerCase();
		if (value.endsWith("h"))
			return TimeCompletionCriteria.getInHours(Long.parseLong(value.substring(0, value.length()-1)));
		if (value.endsWith("m"))
			return TimeCompletionCriteria.getInMinutes(Long.parseLong(value.substring(0, value.length()-1)));
		if (value.endsWith("s"))
			return TimeCompletionCriteria.getInSeconds(Long.parseLong(value.substring(0, value.length()-1)));
		if (value.endsWith("i"))
			value = value.substring(0, value.length()-1);
		return new IterationCompletionCriteria(Long.parseLong(value));
	}
	
	private static SimulatedAnnealing configSA(CommandLine cmd, FaultSystemRupSet rupSet, InversionInputGenerator inputs) {
		inputs.columnCompress();
		
		int threads = FaultSysTools.getNumThreads(cmd);
		SimulatedAnnealing sa;
		if (threads > 1) {
			int avgThreads = 0;
			if (cmd.hasOption("average-threads"))
				avgThreads = Integer.parseInt(cmd.getOptionValue("average-threads"));
			CompletionCriteria avgCompletion = null;
			if (avgThreads > 0) {
				Preconditions.checkArgument(cmd.hasOption("average-completion"),
						"Averaging enabled but --average-completion <value> not specified");
				avgCompletion = getCompletion(cmd.getOptionValue("average-completion"));
				if (avgCompletion == null)
					throw new IllegalArgumentException("Must supply averaging sub-completion time");
			}
			CompletionCriteria subCompletion;
			if (cmd.hasOption("sub-completion"))
				subCompletion = getCompletion(cmd.getOptionValue("sub-completion"));
			else
				subCompletion = TimeCompletionCriteria.getInSeconds(1);
			
			if (avgCompletion != null) {
				int threadsPerAvg = (int)Math.ceil((double)threads/(double)avgThreads);
				Preconditions.checkState(threadsPerAvg < threads);
				Preconditions.checkState(threadsPerAvg > 0);
				
				int threadsLeft = threads;
				
				// arrange lower-level (actual worker) SAs
				List<SimulatedAnnealing> tsas = new ArrayList<>();
				while (threadsLeft > 0) {
					int myThreads = Integer.min(threadsLeft, threadsPerAvg);
					if (myThreads > 1)
						tsas.add(new ThreadedSimulatedAnnealing(inputs.getA(), inputs.getD(),
								inputs.getInitialSolution(), 0d, inputs.getA_ineq(), inputs.getD_ineq(), myThreads, subCompletion));
					else
						tsas.add(new SerialSimulatedAnnealing(inputs.getA(), inputs.getD(),
								inputs.getInitialSolution(), 0d, inputs.getA_ineq(), inputs.getD_ineq()));
					threadsLeft -= myThreads;
				}
				sa = new ThreadedSimulatedAnnealing(tsas, avgCompletion);
				((ThreadedSimulatedAnnealing)sa).setAverage(true);
			} else {
				sa = new ThreadedSimulatedAnnealing(inputs.getA(), inputs.getD(),
						inputs.getInitialSolution(), 0d, inputs.getA_ineq(), inputs.getD_ineq(), threads, subCompletion);
			}
		} else {
			sa = new SerialSimulatedAnnealing(inputs.getA(), inputs.getD(), inputs.getInitialSolution(), 0d,
					inputs.getA_ineq(), inputs.getD_ineq());
		}
		sa.setConstraintRanges(inputs.getConstraintRowRanges());
		
		GenerationFunctionType perturb = PERTURB_DEFAULT;
		if (cmd.hasOption("perturb"))
			perturb = GenerationFunctionType.valueOf(cmd.getOptionValue("perturb"));
		if (perturb == GenerationFunctionType.VARIABLE_EXPONENTIAL_SCALE || perturb == GenerationFunctionType.VARIABLE_NO_TEMP_DEPENDENCE) {
			// compute variable basis
			System.out.println("Computing variable perturbation basis:");
			IncrementalMagFreqDist targetMFD;
			if (rupSet.hasModule(InversionTargetMFDs.class)) {
				targetMFD = rupSet.getModule(InversionTargetMFDs.class).getTotalOnFaultSupraSeisMFD();
			} else {
				// infer target MFD from slip rates
				System.out.println("\tInferring target GR from slip rates");
				GutenbergRichterMagFreqDist gr = inferTargetGRFromSlipRates(rupSet, 1d);
				targetMFD = gr;
			}
			double[] basis = UCERF3InversionConfiguration.getSmoothStartingSolution(rupSet, targetMFD);
			System.out.println("Perturbation-basis range: ["+(float)StatUtils.min(basis)+", "+(float)StatUtils.max(basis)+"]");
			sa.setVariablePerturbationBasis(basis);
		}
		sa.setPerturbationFunc(perturb);
		
		NonnegativityConstraintType nonneg = NON_NEG_DEFAULT;
		if (cmd.hasOption("non-negativity"))
			nonneg = NonnegativityConstraintType.valueOf(cmd.getOptionValue("non-negativity"));
		sa.setNonnegativeityConstraintAlgorithm(nonneg);
		
		return sa;
	}

	public static GutenbergRichterMagFreqDist inferTargetGRFromSlipRates(FaultSystemRupSet rupSet, double bValue) {
		double totMomentRate = rupSet.requireModule(SectSlipRates.class).calcTotalMomentRate();
		System.out.println("Inferring target G-R");
		HistogramFunction tempHist = HistogramFunction.getEncompassingHistogram(rupSet.getMinMag(), rupSet.getMaxMag(), 0.1d);
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(
				tempHist.getMinX(), tempHist.size(), tempHist.getDelta(), totMomentRate, bValue);
		return gr;
	}
	
	private static Options createOptions() {
		Options ops = new Options();

		ops.addOption(FaultSysTools.threadsOption());

		Option rupSetOption = new Option("rs", "rupture-set", true,
				"Path to Rupture Set zip file.");
		rupSetOption.setRequired(true);
		ops.addOption(rupSetOption);

		Option outputOption = new Option("o", "output-file", true,
				"Path where output Solution zip file will be written.");
		outputOption.setRequired(true);
		ops.addOption(outputOption);
		
		// Inversion configuration
		
		Option slipConstraint = new Option("sl", "slip-constraint", false, "Enables the slip-rate constraint.");
		slipConstraint.setRequired(false);
		ops.addOption(slipConstraint);
		
		Option slipWeight = new Option("sw", "slip-weight", true, "Sets weight for the slip-rate constraint.");
		slipWeight.setRequired(false);
		ops.addOption(slipWeight);
		
		Option normSlipWeight = new Option("nsw", "norm-slip-weight", true, "Sets weight for the normalized slip-rate constraint.");
		normSlipWeight.setRequired(false);
		ops.addOption(normSlipWeight);
		
		Option mfdConstraint = new Option("mfd", "mfd-constraint", false, "Enables the MFD constraint. "
				+ "Must supply either --infer-target-gr or --mfd-total-rate, or Rupture Set must have "
				+ "InversionTargetMFDs module already attached.");
		mfdConstraint.setRequired(false);
		ops.addOption(mfdConstraint);
		
		Option mfdWeight = new Option("mw", "mfd-weight", true, "Sets weight for the MFD constraint.");
		mfdWeight.setRequired(false);
		ops.addOption(mfdWeight);
		
		Option mfdFromSlip = new Option("itgr", "infer-target-gr", false,
				"Flag to infer target MFD as a G-R from total deformation model moment rate.");
		mfdFromSlip.setRequired(false);
		ops.addOption(mfdFromSlip);
		
		Option grB = new Option("b", "b-value", true, "Gutenberg-Richter b-value.");
		grB.setRequired(false);
		ops.addOption(grB);
		
		Option mfdTotRate = new Option("mtr", "mfd-total-rate", true, "Total (cumulative) rate for the MFD constraint. "
				+ "By default, this will apply to the minimum magnitude from the rupture set, but another magnitude can "
				+ "be supplied with --mfd-min-mag");
		mfdTotRate.setRequired(false);
		ops.addOption(mfdTotRate);
		
		Option mfdMinMag = new Option("mmm", "mfd-min-mag", true, "Minimum magnitude for the MFD constraint "
				+ "(default is minimum magnitude of the rupture set), used with --mfd-total-rate.");
		mfdMinMag.setRequired(false);
		ops.addOption(mfdMinMag);
		
		// doesn't work well, and slip rate constraint handles moment anyway
//		Option momRateConstraint = new Option("mr", "moment-rate-constraint", false, "Enables the total moment-rate constraint. By default, "
//				+ "the slip-rate implied moment rate will be used, but you can supply your own target moment rate with --target-moment-rate.");
//		momRateConstraint.setRequired(false);
//		ops.addOption(momRateConstraint);
//		
//		Option mrWeight = new Option("mrw", "moment-rate-weight", true, "Sets weight for the moment-rate constraint.");
//		mrWeight.setRequired(false);
//		ops.addOption(mrWeight);
//		
//		Option momRate = new Option("tmr", "target-moment-rate", true, "Specifies a custom target moment-rate in N-m/yr"
//				+ " (must also supply --moment-rate-constraint option)");
//		momRate.setRequired(false);
//		ops.addOption(momRate);
		
		Option eventRateConstraint = new Option("er", "event-rate-constraint", true, "Enables the total event-rate constraint"
				+ " with the supplied total event rate");
		eventRateConstraint.setRequired(false);
		ops.addOption(eventRateConstraint);
		
		Option erWeight = new Option("erw", "event-rate-weight", true, "Sets weight for the event-rate constraint.");
		erWeight.setRequired(false);
		ops.addOption(erWeight);
		
		// Simulated Annealing parameters
		
		String complText = "If either no suffix or 'i' is appended, then it is assumed to be an iteration count. "
				+ "Specify times in hours, minutes, or seconds by appending 'h', 'm', or 's' respecively. Fractions are not allowed.";
		
		Option completionOption = new Option("c", "completion", true, "Total inversion completion criteria. "+complText);
		completionOption.setRequired(true);
		ops.addOption(completionOption);
		
		Option avgOption = new Option("at", "average-threads", true, "Enables a top layer of threads that average results "
				+ "of worker threads at fixed intervals. Supply the number of averaging threads, which must be < threads. "
				+ "Default is no averaging, if enabled you must also supply --average-completion <value>.");
		avgOption.setRequired(false);
		ops.addOption(avgOption);
		
		Option avgCompletionOption = new Option("ac", "average-completion", true,
				"Interval between across-thread averaging. "+complText);
		avgCompletionOption.setRequired(false);
		ops.addOption(avgCompletionOption);
		
		Option subCompletionOption = new Option("sc", "sub-completion", true,
				"Interval between across-thread synchronization. "+complText+" Default: 1s");
		subCompletionOption.setRequired(false);
		ops.addOption(subCompletionOption);
		
		Option perturbOption = new Option("pt", "perturb", true, "Perturbation function. One of "
				+FaultSysTools.enumOptions(GenerationFunctionType.class)+". Default: "+PERTURB_DEFAULT.name());
		perturbOption.setRequired(false);
		ops.addOption(perturbOption);
		
		Option nonNegOption = new Option("nn", "non-negativity", true, "Non-negativity constraint. One of "
				+FaultSysTools.enumOptions(NonnegativityConstraintType.class)+". Default: "+NON_NEG_DEFAULT.name());
		nonNegOption.setRequired(false);
		ops.addOption(nonNegOption);
		
		return ops;
	}

	public static void main(String[] args) {
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, Inversions.class);
		
		try {
			File rupSetFile = new File(cmd.getOptionValue("rupture-set"));
			FaultSystemRupSet rupSet = FaultSystemRupSet.load(rupSetFile);
			
			File outputFile = new File(cmd.getOptionValue("output-file"));
			
			List<InversionConstraint> constraints = new ArrayList<>();
			
			if (cmd.hasOption("slip-constraint")) {
				double weight = 1d;
				double normWeight = 0d;
				
				if (cmd.hasOption("norm-slip-weight"))
					normWeight = Double.parseDouble(cmd.getOptionValue("norm-slip-weight"));
				if (cmd.hasOption("slip-weight"))
					weight = Double.parseDouble(cmd.getOptionValue("slip-weight"));
				
				SlipRateInversionConstraint constr = slipConstraint(rupSet, weight, normWeight);
				Preconditions.checkArgument(constr != null, "Must supply a positive slip rate weight");
				
				constraints.add(constr);
			}
			
			if (cmd.hasOption("mfd-constraint")) {
				double weight = 1d;

				if (cmd.hasOption("mfd-weight"))
					weight = Double.parseDouble(cmd.getOptionValue("mfd-weight"));
				
				double bValue = 1d;
				if (cmd.hasOption("b-value"))
					bValue = Double.parseDouble(cmd.getOptionValue("b-value"));
				
				InversionTargetMFDs targetMFDs;
				
				if (cmd.hasOption("infer-target-gr")) {
					IncrementalMagFreqDist targetMFD = inferTargetGRFromSlipRates(rupSet, bValue);
					
					List<MFD_InversionConstraint> mfdConstraints = List.of(new MFD_InversionConstraint(targetMFD, null));
					
					targetMFDs = new InversionTargetMFDs.Precomputed(rupSet, null, targetMFD, null, null, mfdConstraints, null);
				} else if (cmd.hasOption("mfd-total-rate")) {
					double minX = 0.1*Math.floor(rupSet.getMinMag()*10d);
					double minTargetMag = minX;
					if (cmd.hasOption("mfd-min-mag")) {
						minTargetMag = Double.parseDouble(cmd.getOptionValue("mfd-min-mag"));
						minX = Math.min(minX, minTargetMag);
					}
					
					Preconditions.checkArgument(cmd.hasOption("mfd-total-rate"),
							"MFD constraint enabled, but no --mfd-total-rate <rate> or --infer-target-gr");
					double totRate = Double.parseDouble(cmd.getOptionValue("mfd-total-rate"));
					
					HistogramFunction tempHist = HistogramFunction.getEncompassingHistogram(minX, rupSet.getMaxMag(), 0.1d);
					GutenbergRichterMagFreqDist targetGR = new GutenbergRichterMagFreqDist(
							tempHist.getMinX(), tempHist.getMaxX(), tempHist.size());
					targetGR.scaleToCumRate(minTargetMag, totRate);
					
					List<MFD_InversionConstraint> mfdConstraints = List.of(new MFD_InversionConstraint(targetGR, null));
					
					targetMFDs = new InversionTargetMFDs.Precomputed(rupSet, null, targetGR, null, null, mfdConstraints, null);
				} else {
					Preconditions.checkState(rupSet.hasModule(InversionTargetMFDs.class),
							"MFD Constraint enabled, but no target MFD specified. Rupture Set must either already have "
							+ "target MFDs attached, or MFD should be specified via --infer-target-gr or --mfd-total-rate <rate>.");
					targetMFDs = rupSet.requireModule(InversionTargetMFDs.class);
				}
				
				if (!rupSet.hasModule(InversionTargetMFDs.class))
					rupSet.addModule(targetMFDs);
				
				List<? extends MFD_InversionConstraint> mfdConstraints = targetMFDs.getMFD_Constraints();
				
				for (MFD_InversionConstraint constr : mfdConstraints)
					System.out.println("MFD Constraint for region "
							+(constr.getRegion() == null ? "null" : constr.getRegion().getName())
							+":\n"+constr.getMagFreqDist());
				constraints.add(new MFDEqualityInversionConstraint(rupSet, weight, mfdConstraints, null));
			}
			
			// doesn't work well, and slip rate constraint handles moment anyway
//			if (cmd.hasOption("moment-rate-constraint")) {
//				double targetMomentRate;
//				if (cmd.hasOption("target-moment-rate"))
//					targetMomentRate = Double.parseDouble(cmd.getOptionValue("target-moment-rate"));
//				else
//					targetMomentRate = rupSet.requireModule(SectSlipRates.class).calcTotalMomentRate();
//				System.out.println("Target moment rate: "+targetMomentRate+" N-m/yr");
//				
//				double weight;
//				if (cmd.hasOption("moment-rate-weight"))
//					weight = Double.parseDouble(cmd.getOptionValue("moment-rate-weight"));
//				else
//					weight = 1e-5;
//				constraints.add(new TotalMomentInversionConstraint(rupSet, weight, targetMomentRate));
//			}
			
			if (cmd.hasOption("event-rate-constraint")) {
				double targetEventRate = Double.parseDouble(cmd.getOptionValue("event-rate-constraint"));
				System.out.println("Target event rate: "+targetEventRate+" /yr");
				
				double weight = 1d;
				if (cmd.hasOption("event-rate-weight"))
					weight = Double.parseDouble(cmd.getOptionValue("event-rate-weight"));
				constraints.add(new TotalRateInversionConstraint(rupSet, weight, targetEventRate));
			}
			
			Preconditions.checkState(!constraints.isEmpty(), "No constraints specified.");
			
			InversionInputGenerator inputs = new InversionInputGenerator(rupSet, constraints);
			inputs.generateInputs(true);
			
			CompletionCriteria completion = getCompletion(cmd.getOptionValue("completion"));
			if (completion == null)
				throw new IllegalArgumentException("Must supply total inversion time or iteration count");
			
			ProgressTrackingCompletionCriteria progress = new ProgressTrackingCompletionCriteria(completion);
			
			SimulatedAnnealing sa = configSA(cmd, rupSet, inputs);
			
			System.out.println("Annealing!");
			sa.iterate(progress);
			
			System.out.println("DONE. Building solution...");
			double[] rawSol = sa.getBestSolution();
			double[] rates = inputs.adjustSolutionForWaterLevel(rawSol);
			
			FaultSystemSolution sol = new FaultSystemSolution(rupSet, rates);
			// add inversion progress
			sol.addModule(progress.getProgress());
			// add water level rates
			if (inputs.getWaterLevelRates() != null)
				sol.addModule(new WaterLevelRates(inputs.getWaterLevelRates()));
			if (inputs.hasInitialSolution())
				sol.addModule(new InitialSolution(inputs.getInitialSolution()));
			
			// write solution
			sol.write(outputFile);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		
	}

}
