package org.opensha.sha.earthquake.faultSysSolution.inversion;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.LaplacianSmoothingInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModels;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.RelativeBValueConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.RupRateMinimizationConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateSegmentationConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateSegmentationConstraint.RateCombiner;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateSegmentationConstraint.Shaw07JumpDistSegModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.TotalRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.faultSurface.FaultSection;
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

		ops.addRequiredOption("rs", "rupture-set", true,
				"Path to Rupture Set zip file.");

		ops.addRequiredOption("o", "output-file", true,
				"Path where output Solution zip file will be written.");

		ops.addOption("wcj", "write-config-json", true,
				"Path to write inversion configuration JSON");
		
		/*
		 * Constraints
		 */
		// slip rate constraint

		ops.addOption("sl", "slip-constraint", false, "Enables the slip-rate constraint.");

		ops.addOption("sw", "slip-weight", true, "Sets weight for the regular (un-normalized) slip-rate constraint.");

		ops.addOption(null, "norm-slip-weight", true, "Sets weight for the normalized slip-rate constraint.");
		
		ops.addOption(null, "uncertain-slip-weight", true,
				"Sets weight for the uncertaintly-normalized slip-rate constraint.");

		// Regular MFD constraint

		ops.addOption("mfd", "mfd-constraint", false, "Enables the MFD constraint. "
				+ "Must supply either --infer-target-gr or --mfd-total-rate, or Rupture Set must have "
				+ "InversionTargetMFDs module already attached.");

		ops.addOption("mw", "mfd-weight", true, "Sets weight for the MFD constraint.");

		ops.addOption(null, "infer-target-gr", false,
				"Flag to infer target MFD as a G-R from total deformation model moment rate.");

		ops.addOption("b", "b-value", true, "Gutenberg-Richter b-value.");

		ops.addOption(null, "mfd-total-rate", true, "Total (cumulative) rate for the MFD constraint. "
				+ "By default, this will apply to the minimum magnitude from the rupture set, but another magnitude can "
				+ "be supplied with --mfd-min-mag");

		ops.addOption(null, "mfd-min-mag", true, "Minimum magnitude for the MFD constraint "
				+ "(default is minimum magnitude of the rupture set), used with --mfd-total-rate.");

		ops.addOption(null, "mfd-ineq", false, "Flag to configure MFD constraints as inequality rather "
				+ "than equality constraints. Used in conjunction with --mfd-constraint. Use --mfd-transition-mag "
				+ "instead if you want to transition from equality to inequality constraints.");

		ops.addOption(null, "mfd-transition-mag", true, "Magnitude at and above which the mfd "
				+ "constraint should be applied as a inequality, allowing a natural taper (default is equality only).");

		ops.addOption(null, "rel-gr-constraint", false, "Enables the relative Gutenberg-Richter "
				+ "constraint, which constraints the overal MFD to be G-R withought constraining the total event rate. "
				+ "The b-value will default to 1, override with --b-value <vlalue>. Set constraint weight with "
				+ "--mfd-weight <weight>, or configure as an inequality with --mfd-ineq.");
		
		// Supra-Seis b-value MFDs
		
		// TODO
//		ops.addOption(null, "supra-reg-mfd-constraint", false, "Enables the MFD constraint. "
//				+ "Must supply either --infer-target-gr or --mfd-total-rate, or Rupture Set must have "
//				+ "InversionTargetMFDs module already attached.");
		
		// paleo data constraint
		
		ops.addOption("paleo", "paleo-constraint", false, "Enables the paleoseismic data constraint. Must supply "
				+ "--paleo-data, or rupture set must already have a PaleoseismicConstraintData module attached. See "
				+ "also --paleo-prob-model.");
		
		ops.addOption(null, "paleo-data", true, "Paleoseismic data CSV file. Format must be as follows (including a "
				+ "header row with column names, although those names are not tested to exactly match those that follow). "
				+ "If values are omitted (or are negative) for 'Subsection Index', the closest subsection to each site "
				+ "location will be mapped automatically. CSV File columns: "
				+ "Site Name, Subsection Index, Latitude, Longitude, Rate, Rate Std Dev");
		
		ops.addOption(null, "paleo-prob-model", false, "Paleoseismic probability of detection model, one of: "
				+FaultSysTools.enumOptions(PaleoProbabilityModels.class)+", default: "+PaleoProbabilityModels.DEFAULT.name());

		ops.addOption(null, "paleo-weight", true, "Sets weight for the paleoseismic datat constraint.");

		// event rate constraint

		ops.addOption(null, "event-rate-constraint", true, "Enables the total event-rate constraint"
				+ " with the supplied total event rate");

		ops.addOption(null, "event-rate-weight", true, "Sets weight for the event-rate constraint.");

		// segmentation constraint TODO redo

		ops.addOption(null, "slip-seg-constraint", false,
				"Enables the slip-rate segmentation constraint.");

		ops.addOption(null, "norm-slip-seg-constraint", false,
				"Enables the normalized slip-rate segmentation constraint.");
		
		ops.addOption(null, "net-slip-seg-constraint", false,
				"Enables the net (distance-binned) slip-rate segmentation constraint.");

		ops.addOption(null, "slip-seg-ineq", false,
				"Flag to make segmentation constraints an inequality constraint (only applies if segmentation rate is exceeded).");

		ops.addOption("r0", "shaw-r0", true,
				"Sets R0 in the Shaw (2007) jump-distance probability model in km"
						+ " (used for segmentation constraint). Default: "+(float)Shaw07JumpDistProb.R0_DEFAULT);
		
		ops.addOption(null, "slip-seg-weight", true,
				"Sets weight for the slip-rate segmentation constraint.");

		// minimization constraint

		// TODO add to docs
		ops.addOption(null, "minimize-below-sect-min", false,
				"Flag to enable the minimzation constraint for rupture sets that have modified section minimum magnitudes."
						+ " If enabled, rates for all ruptures below those minimum magnitudes will be minimized.");

		// TODO add to docs
		ops.addOption(null, "minimize-weight", true, "Sets weight for the minimization constraint.");

		// smoothness constraint

		ops.addOption(null, "smooth", false,
				"Flag to enable the Laplacian smoothness constraint that smooths supra-seismogenic participation rates "
				+ "along adjacent subsections on a parent section.");
		
		ops.addOption(null, "paleo-smooth", false, "Enables the Laplacian smoothness constraint (see --smooth), but only "
				+ "for faults with paleoseismic constraints.");

		ops.addOption(null, "smooth-weight", true, "Sets weight for the smoothness constraint.");
		
		// external configuration
		
		ops.addOption("cfg", "config-json", true,
				"Path to a JSON file containing a full inversion configuration, as an alternative to using "
				+ "command line options.");
		
		ops.addOption("cnstr", "constraints-json", true,
				"Path to a JSON file containing inversion constraints that should be included.");
		
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
				
				targetMFDs = new InversionTargetMFDs.Precomputed(rupSet, null, targetMFD, null, null, mfdConstraints, null, null);
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
				
				targetMFDs = new InversionTargetMFDs.Precomputed(rupSet, null, targetGR, null, null, mfdConstraints, null, null);
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
		
		if (cmd.hasOption("paleo-constraint")) {
			double weight = 1d;
			
			if (cmd.hasOption("paleo-weight"))
				weight = Double.parseDouble(cmd.getOptionValue("paleo-weight"));
			
			PaleoseismicConstraintData paleoData;
			
			if (cmd.hasOption("paleo-data")) {
				File csvFile = new File(cmd.getOptionValue("paleo-data"));
				CSVFile<String> csv = CSVFile.readFile(csvFile, true);
				
				PaleoProbabilityModels modelChoice = PaleoProbabilityModels.DEFAULT;
				if (cmd.hasOption("paleo-prob-model"))
					modelChoice = PaleoProbabilityModels.valueOf(cmd.getOptionValue("paleo-prob-model"));
				paleoData = PaleoseismicConstraintData.fromSimpleCSV(rupSet, csv, modelChoice.get());
				rupSet.addModule(paleoData);
			} else {
				Preconditions.checkArgument(rupSet.hasModule(PaleoseismicConstraintData.class),
						"Must supply --paleo-data if PaleoseismicConstraintData module not attached to rupture set and "
						+ "--paleo-constraint enabled.");
				Preconditions.checkArgument(!cmd.hasOption("paleo-prob-model"), "Cannot supply paleoseismic probably "
						+ "model when using already attached paleo data.");
				paleoData = rupSet.requireModule(PaleoseismicConstraintData.class);
			}
			
			constraints.add(new PaleoRateInversionConstraint(rupSet, weight,
					paleoData.getPaleoRateConstraints(), paleoData.getPaleoProbModel()));
			if (paleoData.getPaleoSlipConstraints() != null && !paleoData.getPaleoSlipConstraints().isEmpty())
				System.err.println("WARNING: rupture set has paleo slip constraints, which are not supported via the "
						+ "command line inversion configuration, skipping");
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
		
		if (cmd.hasOption("smooth") || cmd.hasOption("paleo-smooth")) {
			double weight = 1d;
			if (cmd.hasOption("smooth-weight"))
				weight = Double.parseDouble(cmd.getOptionValue("smooth-weight"));
			
			if (cmd.hasOption("paleo-smooth")) {
				// only at paleo sites
				Preconditions.checkArgument(rupSet.hasModule(PaleoseismicConstraintData.class),
						"Can't smooth at paleo sites if no paleo data attached");
				PaleoseismicConstraintData paleoData = rupSet.requireModule(PaleoseismicConstraintData.class);
				HashSet<Integer> parentIDs = new HashSet<>();
				for (SectMappedUncertainDataConstraint constr: paleoData.getPaleoRateConstraints())
					parentIDs.add(rupSet.getFaultSectionData(constr.sectionIndex).getParentSectionId());
				System.out.println("Enabling Laplacian smoothness constraint for "+parentIDs.size()+" parent fault sections");
				constraints.add(new LaplacianSmoothingInversionConstraint(rupSet, weight, parentIDs));
			} else {
				// everywhere
				System.out.println("Enabling Laplacian smoothness constraint");
				constraints.add(new LaplacianSmoothingInversionConstraint(rupSet, weight));
			}
		}
		
		if (cmd.hasOption("constraints-json")) {
			File constraintsFile = new File(cmd.getOptionValue("constraints-json"));
			Preconditions.checkArgument(constraintsFile.exists(), "File doesn't exist: %s", constraintsFile.getAbsolutePath());
			constraints.addAll(InversionConstraint.loadConstraintsJSON(constraintsFile, rupSet));
		}
		
		return constraints;
	}

	public static void main(String[] args) {
//		args = new String[0]; // to force it to print help
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
	
	public static FaultSystemSolution run(InversionConfigurationFactory factory, LogicTreeBranch<?> branch, int threads)
			throws IOException {
		return run(factory, branch, threads, null);
	}
	
	public static FaultSystemSolution run(InversionConfigurationFactory factory, LogicTreeBranch<?> branch, int threads,
			CommandLine cmd) throws IOException {
		FaultSystemRupSet rupSet = factory.buildRuptureSet(branch, threads);;
		return run(rupSet, factory, branch, threads, cmd);
	}
	
	public static FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfigurationFactory factory,
			LogicTreeBranch<?> branch, int threads, CommandLine cmd) throws IOException {
		return factory.getSolver(rupSet, branch).run(rupSet, factory, branch, threads, cmd);
	}
	
	public static FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfiguration config) {
		return new InversionSolver.Default().run(rupSet, config, null);
	}
	
	public static FaultSystemSolution run(FaultSystemRupSet rupSet, InversionConfiguration config, String info) {
		return new InversionSolver.Default().run(rupSet, config, info);
	}

}
