package org.opensha.sha.earthquake.faultSysSolution.inversion;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.LaplacianSmoothingInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.RelativeBValueConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.RupRateMinimizationConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateSegmentationConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateSegmentationConstraint.RateCombiner;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateSegmentationConstraint.Shaw07JumpDistSegModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.TotalRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ThreadedSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.ProgressTrackingCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.modules.InitialSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfits;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.WaterLevelRates;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;

public class Inversions {

	public static double[] getDefaultVariablePerturbationBasis(FaultSystemRupSet rupSet) {
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
		return basis;
	}
	
	private static double getMinMagForMFD(FaultSystemRupSet rupSet) {
		if (rupSet.hasModule(ModSectMinMags.class))
			return StatUtils.min(rupSet.requireModule(ModSectMinMags.class).getMinMagForSections());
		return rupSet.getMinMag();
	}

	public static GutenbergRichterMagFreqDist inferTargetGRFromSlipRates(FaultSystemRupSet rupSet, double bValue) {
		double totMomentRate = rupSet.requireModule(SectSlipRates.class).calcTotalMomentRate();
		System.out.println("Inferring target G-R");
		HistogramFunction tempHist = HistogramFunction.getEncompassingHistogram(
				getMinMagForMFD(rupSet), rupSet.getMaxMag(), 0.1d);
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(
				tempHist.getMinX(), tempHist.size(), tempHist.getDelta(), totMomentRate, bValue);
		return gr;
	}
	
	public static IncrementalMagFreqDist restrictMFDRange(IncrementalMagFreqDist orig, double minMag, double maxMag) {
		int minIndex = -1;
		int maxIndex = 0;
		for (int i=0; i<orig.size(); i++) {
			double mag = orig.getX(i);
			if (minIndex < 0 && (float)mag >= (float)minMag)
				minIndex = i;
			if ((float)mag < (float)maxMag)
				maxIndex = i;
		}
		Preconditions.checkState(minIndex >= 0 && minIndex <= maxIndex,
				"Could not restrict MFD to range [%s, %s]", minMag, maxMag);
		IncrementalMagFreqDist trimmed = new IncrementalMagFreqDist(
				orig.getX(minIndex), orig.getX(maxIndex), 1+maxIndex-minIndex);
		for (int i=0; i<trimmed.size(); i++) {
			int refIndex = i+minIndex;
			Preconditions.checkState((float)trimmed.getX(i) == (float)orig.getX(refIndex));
			trimmed.set(i, orig.getY(refIndex));
		}
		trimmed.setRegion(orig.getRegion());
		return trimmed;
	}
	
	private static Options createOptions() {
		Options ops = InversionConfiguration.createSAOptions();

		Option rupSetOption = new Option("rs", "rupture-set", true,
				"Path to Rupture Set zip file.");
		rupSetOption.setRequired(true);
		ops.addOption(rupSetOption);

		Option outputOption = new Option("o", "output-file", true,
				"Path where output Solution zip file will be written.");
		outputOption.setRequired(true);
		ops.addOption(outputOption);

		Option writeConfig = new Option("wcj", "write-config-json", true,
				"Path to write inversion configuration JSON");
		writeConfig.setRequired(false);
		ops.addOption(writeConfig);
		
		/*
		 * Constraints
		 */
		// slip rate constraint

		Option slipConstraint = new Option("sl", "slip-constraint", false, "Enables the slip-rate constraint.");
		slipConstraint.setRequired(false);
		ops.addOption(slipConstraint);

		Option slipWeight = new Option("sw", "slip-weight", true, "Sets weight for the regular (un-normalized) slip-rate constraint.");
		slipWeight.setRequired(false);
		ops.addOption(slipWeight);

		Option normSlipWeight = new Option("nsw", "norm-slip-weight", true, "Sets weight for the normalized slip-rate constraint.");
		normSlipWeight.setRequired(false);
		ops.addOption(normSlipWeight);

		// TODO: add to docs
		Option uncertSlipWeight = new Option("usw", "uncertain-slip-weight", true,
				"Sets weight for the uncertaintly-normalized slip-rate constraint.");
		uncertSlipWeight.setRequired(false);
		ops.addOption(uncertSlipWeight);

		// MFD constraint

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

		// TODO add to docs
		Option mfdIneq = new Option("min", "mfd-ineq", false, "Flag to configure MFD constraints as inequality rather "
				+ "than equality constraints. Used in conjunction with --mfd-constraint. Use --mfd-transition-mag "
				+ "instead if you want to transition from equality to inequality constraints.");
		mfdIneq.setRequired(false);
		ops.addOption(mfdIneq);

		// TODO add to docs
		Option mfdTransMag = new Option("mtm", "mfd-transition-mag", true, "Magnitude at and above which the mfd "
				+ "constraint should be applied as a inequality, allowing a natural taper (default is equality only).");
		mfdTransMag.setRequired(false);
		ops.addOption(mfdTransMag);

		// TODO: add to docs
		Option relGRConstraint = new Option("rgr", "rel-gr-constraint", false, "Enables the relative Gutenberg-Richter "
				+ "constraint, which constraints the overal MFD to be G-R withought constraining the total event rate. "
				+ "The b-value will default to 1, override with --b-value <vlalue>. Set constraint weight with "
				+ "--mfd-weight <weight>, or configure as an inequality with --mfd-ineq.");
		relGRConstraint.setRequired(false);
		ops.addOption(relGRConstraint);

		// event rate constraint

		Option eventRateConstraint = new Option("er", "event-rate-constraint", true, "Enables the total event-rate constraint"
				+ " with the supplied total event rate");
		eventRateConstraint.setRequired(false);
		ops.addOption(eventRateConstraint);

		Option erWeight = new Option("erw", "event-rate-weight", true, "Sets weight for the event-rate constraint.");
		erWeight.setRequired(false);
		ops.addOption(erWeight);

		// segmentation constraint

		Option slipSegConstraint = new Option("seg", "slip-seg-constraint", false,
				"Enables the slip-rate segmentation constraint.");
		slipSegConstraint.setRequired(false);
		ops.addOption(slipSegConstraint);

		Option normSlipSegConstraint = new Option("nseg", "norm-slip-seg-constraint", false,
				"Enables the normalized slip-rate segmentation constraint.");
		normSlipSegConstraint.setRequired(false);
		ops.addOption(normSlipSegConstraint);

		Option netSlipSegConstraint = new Option("ntseg", "net-slip-seg-constraint", false,
				"Enables the net (distance-binned) slip-rate segmentation constraint.");
		netSlipSegConstraint.setRequired(false);
		ops.addOption(netSlipSegConstraint);

		Option slipSegIneq = new Option("segi", "slip-seg-ineq", false,
				"Flag to make segmentation constraints an inequality constraint (only applies if segmentation rate is exceeded).");
		slipSegIneq.setRequired(false);
		ops.addOption(slipSegIneq);

		Option segR0 = new Option("r0", "shaw-r0", true,
				"Sets R0 in the Shaw (2007) jump-distance probability model in km"
						+ " (used for segmentation constraint). Default: "+(float)Shaw07JumpDistProb.R0_DEFAULT);
		segR0.setRequired(false);
		ops.addOption(segR0);

		Option slipSegWeight = new Option("segw", "slip-seg-weight", true,
				"Sets weight for the slip-rate segmentation constraint.");
		slipSegWeight.setRequired(false);
		ops.addOption(slipSegWeight);

		// minimization constraint

		// TODO add to docs
		Option minimizeBelowMin = new Option("mbs", "minimize-below-sect-min", false,
				"Flag to enable the minimzation constraint for rupture sets that have modified section minimum magnitudes."
						+ " If enabled, rates for all ruptures below those minimum magnitudes will be minimized.");
		minimizeBelowMin.setRequired(false);
		ops.addOption(minimizeBelowMin);

		// TODO add to docs
		Option mwWeight = new Option("mw", "minimize-weight", true, "Sets weight for the minimization constraint.");
		mwWeight.setRequired(false);
		ops.addOption(mwWeight);

		// smooth constraint

		// TODO add to docs
		Option smooth = new Option("sm", "smooth", false,
				"Flag to enable the Laplacian smoothness constraint that smooths supra-seismogenic participation rates "
				+ "along adjacent subsections on a parent section.");
		smooth.setRequired(false);
		ops.addOption(smooth);

		// TODO add to docs
		Option smoothWeight = new Option("smw", "smooth-weight", true, "Sets weight for the smoothness constraint.");
		smoothWeight.setRequired(false);
		ops.addOption(smoothWeight);
		
		// external configuration
		
		// TODO add to docs
		Option configOp = new Option("cfg", "config-json", true,
				"Path to a JSON file containing a full inversion configuration, as an alternative to using "
				+ "command line options.");
		configOp.setRequired(false);
		ops.addOption(configOp);
		
		// TODO add to docs
		Option constraintsOp = new Option("cnstr", "constraints-json", true,
				"Path to a JSON file containing inversion constraints that should be included.");
		constraintsOp.setRequired(false);
		ops.addOption(constraintsOp);
		
		return ops;
	}
	
	public static List<InversionConstraint> parseConstraints(FaultSystemRupSet rupSet, CommandLine cmd) throws IOException {
		List<InversionConstraint> constraints = new ArrayList<>();
		
		if (cmd.hasOption("slip-constraint")) {
			double regWeight = 0d;
			double normWeight = 0d;
			double uncertWeight = 0d;
			
			if (cmd.hasOption("norm-slip-weight"))
				normWeight = Double.parseDouble(cmd.getOptionValue("norm-slip-weight"));
			if (cmd.hasOption("slip-weight"))
				regWeight = Double.parseDouble(cmd.getOptionValue("slip-weight"));
			if (cmd.hasOption("uncertain-slip-weight"))
				uncertWeight = Double.parseDouble(cmd.getOptionValue("uncertain-slip-weight"));
			
			if (normWeight == 0d && regWeight == 0d && uncertWeight == 0d)
				// default to just regular (unnormalized) slip rates
				regWeight = 1d;
			
			if (regWeight > 0d)
				constraints.add(new SlipRateInversionConstraint(regWeight,
						ConstraintWeightingType.UNNORMALIZED, rupSet));
			if (normWeight > 0d)
				constraints.add(new SlipRateInversionConstraint(normWeight,
						ConstraintWeightingType.NORMALIZED, rupSet));
			if (uncertWeight > 0d)
				constraints.add(new SlipRateInversionConstraint(uncertWeight,
						ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, rupSet));
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
				
				List<IncrementalMagFreqDist> mfdConstraints = List.of(targetMFD);
				
				targetMFDs = new InversionTargetMFDs.Precomputed(rupSet, null, targetMFD, null, null, mfdConstraints, null);
			} else if (cmd.hasOption("mfd-total-rate")) {
				double minX = 0.1*Math.floor(getMinMagForMFD(rupSet)*10d);
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
				
				List<IncrementalMagFreqDist> mfdConstraints = List.of(targetGR);
				
				targetMFDs = new InversionTargetMFDs.Precomputed(rupSet, null, targetGR, null, null, mfdConstraints, null);
			} else {
				Preconditions.checkState(rupSet.hasModule(InversionTargetMFDs.class),
						"MFD Constraint enabled, but no target MFD specified. Rupture Set must either already have "
						+ "target MFDs attached, or MFD should be specified via --infer-target-gr or --mfd-total-rate <rate>.");
				targetMFDs = rupSet.requireModule(InversionTargetMFDs.class);
			}
			
			rupSet.addModule(targetMFDs);
			
			List<? extends IncrementalMagFreqDist> mfdConstraints = targetMFDs.getMFD_Constraints();
			
			for (IncrementalMagFreqDist constr : mfdConstraints)
				System.out.println("MFD Constraint for region "
						+(constr.getRegion() == null ? "null" : constr.getRegion().getName())
						+":\n"+constr);
			
			if (cmd.hasOption("mfd-ineq")) {
				Preconditions.checkArgument(!cmd.hasOption("mfd-transition-mag"),
						"Can't specify both --mfd-transition-mag and --mfd-ineq");
				constraints.add(new MFDInversionConstraint(rupSet, weight, true, mfdConstraints));
			} else if (cmd.hasOption("mfd-transition-mag")) {
				double transMag = Double.parseDouble(cmd.getOptionValue("mfd-transition-mag"));
				List<IncrementalMagFreqDist> eqConstrs = new ArrayList<>();
				List<IncrementalMagFreqDist> ieqConstrs = new ArrayList<>();
				for (IncrementalMagFreqDist constr : mfdConstraints) {
					eqConstrs.add(restrictMFDRange(constr, Double.NEGATIVE_INFINITY, transMag));
					ieqConstrs.add(restrictMFDRange(constr, transMag, Double.POSITIVE_INFINITY));
				}
				constraints.add(new MFDInversionConstraint(rupSet, weight, false, eqConstrs));
				constraints.add(new MFDInversionConstraint(rupSet, weight, true, ieqConstrs));
			} else {
				constraints.add(new MFDInversionConstraint(rupSet, weight, false, mfdConstraints));
			}
		}
		
		if (cmd.hasOption("rel-gr-constraint")) {
			double weight = 1d;

			if (cmd.hasOption("mfd-weight"))
				weight = Double.parseDouble(cmd.getOptionValue("mfd-weight"));
			
			double bValue = 1d;
			if (cmd.hasOption("b-value"))
				bValue = Double.parseDouble(cmd.getOptionValue("b-value"));
			
			boolean ineq = cmd.hasOption("mfd-ineq");
			
			constraints.add(new RelativeBValueConstraint(rupSet, bValue, weight,
					ConstraintWeightingType.NORMALIZED, null, ineq));
		}
		
		// doesn't work well, and slip rate constraint handles moment anyway
//		if (cmd.hasOption("moment-rate-constraint")) {
//			double targetMomentRate;
//			if (cmd.hasOption("target-moment-rate"))
//				targetMomentRate = Double.parseDouble(cmd.getOptionValue("target-moment-rate"));
//			else
//				targetMomentRate = rupSet.requireModule(SectSlipRates.class).calcTotalMomentRate();
//			System.out.println("Target moment rate: "+targetMomentRate+" N-m/yr");
//			
//			double weight;
//			if (cmd.hasOption("moment-rate-weight"))
//				weight = Double.parseDouble(cmd.getOptionValue("moment-rate-weight"));
//			else
//				weight = 1e-5;
//			constraints.add(new TotalMomentInversionConstraint(rupSet, weight, targetMomentRate));
//		}
		
		if (cmd.hasOption("event-rate-constraint")) {
			double targetEventRate = Double.parseDouble(cmd.getOptionValue("event-rate-constraint"));
			System.out.println("Target event rate: "+targetEventRate+" /yr");
			
			double weight = 1d;
			if (cmd.hasOption("event-rate-weight"))
				weight = Double.parseDouble(cmd.getOptionValue("event-rate-weight"));
			constraints.add(new TotalRateInversionConstraint(weight, targetEventRate));
		}
		
		if (cmd.hasOption("slip-seg-constraint") || cmd.hasOption("norm-slip-seg-constraint")
				|| cmd.hasOption("net-slip-seg-constraint")) {
			System.out.println("Adding slip rate segmentation constraints");
			double weight = 1d;
			if (cmd.hasOption("slip-seg-weight"))
				weight = Double.parseDouble(cmd.getOptionValue("slip-seg-weight"));
			
			double r0 = Shaw07JumpDistProb.R0_DEFAULT;
			if (cmd.hasOption("shaw-r0"))
				r0 = Double.parseDouble(cmd.getOptionValue("shaw-r0"));
			
			double a = 1d;
			Shaw07JumpDistSegModel segModel = new Shaw07JumpDistSegModel(a, r0);
			
			RateCombiner combiner = RateCombiner.MIN; // TODO: make selectable?
			
			boolean inequality = cmd.hasOption("slip-seg-ineq");
			
			boolean doNormalized = cmd.hasOption("norm-slip-seg-constraint");
			boolean doRegular = cmd.hasOption("slip-seg-constraint");
			boolean doNet = cmd.hasOption("net-slip-seg-constraint");
			
			if (doNet)
				constraints.add(new SlipRateSegmentationConstraint(
						rupSet, segModel, combiner, weight, true, inequality, true, true));
			if (doNormalized)
				constraints.add(new SlipRateSegmentationConstraint(
						rupSet, segModel, combiner, weight, true, inequality));
			if (doRegular)
				constraints.add(new SlipRateSegmentationConstraint(
						rupSet, segModel, combiner, weight, false, inequality));
		}
		
		if (cmd.hasOption("minimize-below-sect-min")) {
			Preconditions.checkState(rupSet.hasModule(ModSectMinMags.class),
					"Rupture set must have the ModSectMinMags module attached to enable the minimzation constraint.");
			
			double weight = 1d;
			if (cmd.hasOption("minimize-weight"))
				weight = Double.parseDouble(cmd.getOptionValue("minimize-weight"));
			
			ModSectMinMags modMinMags = rupSet.requireModule(ModSectMinMags.class);
			List<Integer> belowMinIndexes = new ArrayList<>();
			for (int r=0; r<rupSet.getNumRuptures(); r++)
				if (FaultSystemRupSetCalc.isRuptureBelowSectMinMag(rupSet, r, modMinMags))
					belowMinIndexes.add(r);
			System.out.println("Minimizing rates of "+belowMinIndexes.size()
				+" ruptures below the modified section minimum magnitudes");
			constraints.add(new RupRateMinimizationConstraint(weight, belowMinIndexes));
		}
		
		if (cmd.hasOption("smooth")) {
			double weight = 1d;
			if (cmd.hasOption("smooth-weight"))
				weight = Double.parseDouble(cmd.getOptionValue("smooth-weight"));
			
			System.out.println("Enabling Laplacian smoothness constraint");
			constraints.add(new LaplacianSmoothingInversionConstraint(rupSet, weight));
		}
		
		if (cmd.hasOption("constraints-json")) {
			File constraintsFile = new File(cmd.getOptionValue("constraints-json"));
			Preconditions.checkArgument(constraintsFile.exists(), "File doesn't exist: %s", constraintsFile.getAbsolutePath());
			constraints.addAll(InversionConstraint.loadConstraintsJSON(constraintsFile, rupSet));
		}
		
		return constraints;
	}

	public static void main(String[] args) {
		try {
			run(args);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public static FaultSystemSolution run(String[] args) throws IOException {
		return run(args, null);
	}
	
	public static FaultSystemSolution run(String[] args, List<InversionConstraint> constraints) throws IOException {
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, Inversions.class);
		
		File rupSetFile = new File(cmd.getOptionValue("rupture-set"));
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(rupSetFile);
		
		if (constraints == null)
			constraints = new ArrayList<>();
		else if (!constraints.isEmpty())
			constraints = new ArrayList<>(constraints);
		
		int numExtra = constraints.size();
		
		File outputFile = new File(cmd.getOptionValue("output-file"));
		
		constraints.addAll(parseConstraints(rupSet, cmd));
		
		InversionConfiguration config;
		
		if (cmd.hasOption("config-json")) {
			File configFile = new File(cmd.getOptionValue("config-json"));
			Preconditions.checkArgument(configFile.exists(), "File doesn't exist: %s", configFile.getAbsolutePath());
			config = InversionConfiguration.readJSON(configFile, rupSet);
			config = InversionConfiguration.builder(config).forCommandLine(cmd).build();
		} else {
			config = InversionConfiguration.builder(constraints, cmd).build();
		}
		Preconditions.checkState(!config.getConstraints().isEmpty(), "No constraints specified.");
		
		if (cmd.hasOption("write-config-json")) {
			File configFile = new File(cmd.getOptionValue("write-config-json"));
			
			BufferedWriter writer = new BufferedWriter(new FileWriter(configFile));
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(config, InversionConfiguration.class, writer);
			writer.flush();
			writer.close();
		}
		
		String info = "Fault System Solution generated with OpenSHA Fault System Tools ("
				+ "https://github.com/opensha/opensha-fault-sys-tools), using the following command:"
				+ "\n\nfst_inversion_runner.sh "+Joiner.on(" ").join(args);
		if (numExtra > 0)
			info += "\n\nNOTE: "+numExtra+" constraints were passed in directly, bypassing the command line interface "
					+ "and are not reflected in the command above.";
		
		FaultSystemSolution sol = run(rupSet, config, info);
		
		// write solution
		sol.write(outputFile);
		
		return sol;
	}
	
	public static FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfiguration config) {
		return run(rupSet, config, null);
	}
	
	public static FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfiguration config, String info) {
		CompletionCriteria completion = config.getCompletionCriteria();
		if (completion == null)
			throw new IllegalArgumentException("Must supply total inversion time or iteration count");
		
		InversionInputGenerator inputs = new InversionInputGenerator(rupSet, config);
		inputs.generateInputs(true);
		inputs.columnCompress();
		
		ProgressTrackingCompletionCriteria progress = new ProgressTrackingCompletionCriteria(completion);
		
		SimulatedAnnealing sa = config.buildSA(inputs);
		
		System.out.println("SA Parameters:");
		System.out.println("\tImplementation: "+sa.getClass().getName());
		System.out.println("\tCompletion Criteria: "+completion);
		System.out.println("\tPerturbation Function: "+sa.getPerturbationFunc());
		System.out.println("\tNon-Negativity Constraint: "+sa.getNonnegativeityConstraintAlgorithm());
		System.out.println("\tCooling Schedule: "+sa.getCoolingFunc());
		if (sa instanceof ThreadedSimulatedAnnealing) {
			ThreadedSimulatedAnnealing tsa = (ThreadedSimulatedAnnealing)sa;
			System.out.println("\tTop-Level Threads: "+tsa.getNumThreads());
			System.out.println("\tSub-Completion Criteria: "+tsa.getSubCompetionCriteria());
			System.out.println("\tAveraging? "+tsa.isAverage());
		}
		
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
		sol.addModule(config);
		InversionMisfits misfits = new InversionMisfits(sa);
		sol.addModule(misfits);
		sol.addModule(misfits.getMisfitStats());
		if (info != null)
			sol.setInfoString(info);
		
		return sol;
	}

}
