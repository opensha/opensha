package org.opensha.sha.earthquake.faultSysSolution.inversion;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.LaplacianSmoothingInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModels;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.RelativeBValueConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SectionTotalRateConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SubSectMFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.TotalRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint.InitialModelParticipationRateEstimator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint.SectParticipationRateEstimator;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.BinaryJumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_ConstraintBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs.SubSeisMoRateReduction;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.GRParticRateEstimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.PaleoSectNuclEstimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.ScalingRelSlipRateMFD_Estimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.SectNucleationMFD_Estimator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import scratch.UCERF3.inversion.UCERF3InversionConfiguration;

public class Inversions {
	
	public static SectParticipationRateEstimator getDefaultSectParticEstimator(FaultSystemRupSet rupSet) {
		InversionTargetMFDs targetMFDs = rupSet.getModule(InversionTargetMFDs.class);
		
		if (targetMFDs == null) {
			// assume target MFDs
			targetMFDs = new SupraSeisBValInversionTargetMFDs.Builder(rupSet, 1d)
					.subSeisMoRateReduction(SubSeisMoRateReduction.FROM_INPUT_SLIP_RATES)
					.applyDefModelUncertainties(false).build();
		}
		List<? extends IncrementalMagFreqDist> sectNuclRates = targetMFDs.getOnFaultSupraSeisNucleationMFDs();
		if (sectNuclRates != null) {
			// we have section nucleation rates, use them
			return new GRParticRateEstimator(rupSet, targetMFDs);
		} else {
			// we don't have section nucleation rates, use the UCERF3 approach
			double[] basis = UCERF3InversionConfiguration.getSmoothStartingSolution(rupSet, targetMFDs.getTotalOnFaultSupraSeisMFD());
			return new InitialModelParticipationRateEstimator(rupSet, basis);
		}
	}

	public static double[] getDefaultVariablePerturbationBasis(FaultSystemRupSet rupSet) {
		// compute variable basis
		return getDefaultSectParticEstimator(rupSet).estimateRuptureRates();
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
		if (orig instanceof UncertainIncrMagFreqDist) {
			// also trim uncertainties
			EvenlyDiscretizedFunc origStdDevs = ((UncertainIncrMagFreqDist)orig).getStdDevs();
			EvenlyDiscretizedFunc trimmedStdDevs = new EvenlyDiscretizedFunc(trimmed.getMinX(), trimmed.getMaxX(), trimmed.size());
			for (int i=0; i<trimmedStdDevs.size(); i++) {
				int refIndex = i+minIndex;
				trimmedStdDevs.set(i, origStdDevs.getY(refIndex));
			}
			return new UncertainIncrMagFreqDist(trimmed, trimmedStdDevs);
		}
		return trimmed;
	}
	
	/**
	 * Creates command line options, but without any of the constraints
	 * @return
	 */
	static Options createOptionsNoConstraints(boolean requireRupSet, boolean requireOutputFile) {
		Options ops = InversionConfiguration.createSAOptions();

		ops.addOption(FaultSysTools.helpOption());

		if (requireRupSet)
			ops.addRequiredOption("rs", "rupture-set", true, "Path to Rupture Set zip file.");
		else
			ops.addOption("rs", "rupture-set", true, "Path to Rupture Set zip file.");

		if (requireOutputFile)
			ops.addRequiredOption("o", "output-file", true,
					"Path where output Solution zip file will be written.");
		else
			ops.addOption("o", "output-file", true,
					"Path where output Solution zip file will be written.");

		ops.addOption("wcj", "write-config-json", true,
				"Path to write inversion configuration JSON");
		
		return ops;
	}
	
	private static Options createOptions() {
		Options ops = createOptionsNoConstraints(true, true);
		
		/*
		 * Constraints
		 */
		// slip rate constraint

		ops.addOption("sl", "slip-constraint", false, "Enables the slip-rate constraint.");

		ops.addOption("sw", "slip-weight", true, "Sets weight for the regular (un-normalized) slip-rate constraint.");

		ops.addOption(null, "norm-slip-weight", true, "Sets weight for the normalized slip-rate constraint.");
		
		ops.addOption(null, "uncertain-slip-weight", true,
				"Sets weight for the uncertaintly-normalized slip-rate constraint.");

		// MFD constraint
		
		// Common options
		
		ops.addOption("mfd", "mfd-constraint", false, "Enables the MFD constraint. "
				+ "Must supply either --infer-target-gr or --mfd-total-rate, or Rupture Set must have "
				+ "InversionTargetMFDs module already attached.");

		ops.addOption("mw", "mfd-weight", true, "Sets weight for the MFD constraint.");

		ops.addOption("b", "b-value", true, "Gutenberg-Richter b-value.");

		ops.addOption(null, "mfd-ineq", false, "Flag to configure MFD constraints as inequality rather "
				+ "than equality constraints. Used in conjunction with --mfd-constraint. Use --mfd-transition-mag "
				+ "instead if you want to transition from equality to inequality constraints.");

		ops.addOption(null, "mfd-transition-mag", true, "Magnitude at and above which the mfd "
				+ "constraint should be applied as a inequality, allowing a natural taper (default is equality only).");

		ops.addOption(null, "b-dependent-mfd-uncert", false, "Flag to enable NSHM23 b-value and magnitude-dependent "
				+ "uncertainty model: 0.1 * max[1, 10^(b*0.5*(M-6))]. This switches the constraint type to be "
				+ "uncertainty-weighted (otherwise it is an equality constraint).");
		
		// Simple G-R options

		ops.addOption(null, "infer-target-gr", false,
				"Flag to infer target MFD as a G-R from total deformation model moment rate.");
		
		ops.addOption(null, "mfd-total-rate", true, "Total (cumulative) rate for the MFD constraint. "
				+ "By default, this will apply to the minimum magnitude from the rupture set, but another magnitude can "
				+ "be supplied with --mfd-min-mag");

		ops.addOption(null, "mfd-min-mag", true, "Minimum magnitude for the MFD constraint "
				+ "(default is minimum magnitude of the rupture set), used with --mfd-total-rate.");

		ops.addOption(null, "rel-gr-constraint", false, "Enables the relative Gutenberg-Richter "
				+ "constraint, which constraints the overal MFD to be G-R withought constraining the total event rate. "
				+ "The b-value will default to 1, override with --b-value <vlalue>. Set constraint weight with "
				+ "--mfd-weight <weight>, or configure as an inequality with --mfd-ineq.");
		
		// Supra-Seis b-value MFD options
		
		ops.addOption(null, "supra-b-mfds", false, "Flag to enable the supra-seismogenic b-value mode. Used in "
				+ "conjunction with `--mfd-constraint`, `--sect-rate-constraint`, and/or `--sect-mfd-constraint`.");
		
		ops.addOption(null, "adj-mfds-for-seg", false, "If supplied, MFDs will be adjusted for compatibility with the "
				+ "chosen segmentation model using the NSHM23 adjustment approach (Milner and Field, 2023). See "
				+ "segmentation model arguments for how to specify the segmentation model.");
		
		ops.addOption(null, "adj-mfds-for-slips", false, "If supplied, MFDs will be adjusted for compatibility with the "
				+ "scaling relationship. This can be needed if the chosen scaling relationship does not determine "
				+ "average slip directly from moment (e.g., in length-based relationships).");
		
		ops.addOption(null, "sub-seis-mo-red", true, "If supplied, slip rates will be adjusted to account for "
				+ "sub-seismogenic ruptures. The default implementation uses input slip rates attached to the rupture "
				+ "set; by default, this applies any coupling coefficient to reduct the slip rate but makes no "
				+ "adjustment for sub-seismogenic ruptures. Options: "+FaultSysTools.enumOptions(SubSeisMoRateReduction.class));
		
		ops.addOption(null, "adj-mfd-ucert-slip", false, "If supplied, slip rate uncertainties are propagated to "
				+ "section and regional MFD constraints.");
		
		ops.addOption(null, "adj-mfd-ucert-paleo", false, "If supplied, section MFD uncertainties are expanded to "
				+ "account for any incompatibilities found with supplied paleoseismic constraints.");
		
		// Supra-Seis b-value section constraint options
		
		ops.addOption(null, "sect-rate-constraint", false, "Enables section nucleation rate constraints that match the "
				+ "total rate implied by their assumed MFDs.");
		
		ops.addOption(null, "sect-rate-weight", true, "Sets the weight for the section rate constraint.");
		
		ops.addOption(null, "sect-rate-uncert", false, "Flag to enable uncertainty-weighting of section rate constraints. "
				+ "Usually used in conjunction with `--adj-mfd-ucert-slip` (assuming that slip rate uncertainties are "
				+ "present). Otherwise, the constraint will be normalized.");
		
		ops.addOption(null, "sect-mfd-constraint", false, "Enables section nucleation MFD constraints.");
		
		ops.addOption(null, "sect-mfd-weight", true, "Sets the weight for the section MFD constraint.");
		
		ops.addOption(null, "sect-mfd-uncert", false, "Flag to enable uncertainty-weighting of section MFD constraints. "
				+ "Usually used in conjunction with `--adj-mfd-ucert-slip` (assuming that slip rate uncertainties are "
				+ "present) and `--b-dependent-mfd-uncert`. Otherwise, the constraint will be normalized.");
		
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
		
		ops.addOption(null, "event-rate-ineq", true, "Flag to constraint total event rates as an inequality constraint "
				+ "(i.e., to no exceed the specified rate).");

		// segmentation constraint
		
		ops.addOption(null, "seg-constraint", false,
				"Enables the default segmentation constraint implementation where passthrough rates are constrained not "
				+ "to exceed the prescribed rate. Must supply a constraint model, e.g. via --seg-dist-d0 or --seg-rates.");
		
		ops.addOption(null, "slip-seg-constraint", false,
				"Enables the alternative segmentation constraint implementation where constraitns are applied as "
				+ "proxy-slip constraints. In this implementation, larger magnitude ruptures contribute more to the "
				+ "segmentation budget (because they consume more slip).");

		ops.addOption(null, "seg-constraint-eq", false,
				"Flag to make segmentation constraints an equality constraint. This is generally not recommended unless "
				+ "all faults only have a single connection. When Y's exist, it might be impossible to match imposed "
				+ "equality segmentation constraints.");

		ops.addOption(null, "seg-rates", true,
				"Specify custom segmentation passthrough rates via a CSV file. The format consists of a header line: "
				+ "Section ID1,Section ID2,Fractional Passthrough Rate, and a line for each constrained jump. Section "
				+ "IDs are subsection must be on different parent faults (unless the --seg-parent-rates flag is present), "
				+ "and passthrough rates should be in the range [0,1]. Passthrough rates are assumed to be 1 for all "
				+ "jumps not included in the file.");

		ops.addOption(null, "seg-dist-d0", true,
				"Use Shaw and Dieterich (2007) distance-dependent segmentation rate model. Sets D0 (referred to as R0 "
				+ "in their model) in km, where D0=3 is the preferred value. Also see --seg-dist-delta.");

		ops.addOption(null, "seg-dist-delta", true,
				"Sets the fault uncertainty parameter, &delta;, in the NSHM23 implementation of the Shaw and Dieterich "
				+ "(2007) distance-dependent segmentation rate model. With uits in km, this shifts the exponential "
				+ "distribution to the right by &delta;; values less than this value are unconstrained. Used in "
				+ "conjunction with --seg-dist-d0.");

		ops.addOption(null, "seg-nshm23", true,
				"Use an NSHM23 segmentation constraint. This includes distance-dependent segmentation, along with "
				+ "special constraints on the SAF creeping section and Wasatch faults (if found by name). Note that "
				+ "this won't include all features of the NSHM23 segmentation constraint, such as rupture length limits "
				+ "or detection of special fault exceptions to the Classic model. Options: LOW,MID,HIGH,CLASSIC");
		
		ops.addOption(null, "seg-weight", true,
				"Sets weight for the segmentation constraint.");

//		// minimization constraint
//		// TODO re-enable and add to docs? or add skip below min option? needed?
//
//		ops.addOption(null, "minimize-below-sect-min", false,
//				"Flag to enable the minimzation constraint for rupture sets that have modified section minimum magnitudes."
//						+ " If enabled, rates for all ruptures below those minimum magnitudes will be minimized.");
//
//		ops.addOption(null, "minimize-weight", true, "Sets weight for the minimization constraint.");

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
		
		/*
		 * Slip rate constraints
		 */
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
		
		/*
		 * MFD constraints
		 */
		// might be generated here and reused later
		JumpProbabilityCalc segModel = null;
		InversionTargetMFDs targetMFDs = null;
		PaleoseismicConstraintData paleoData = null;
		
		double bValue = cmd.hasOption("b-value") ? Double.parseDouble(cmd.getOptionValue("b-value")) : 1d;
		
		DoubleUnaryOperator mfdMagDepUncertModel = null;
		if (cmd.hasOption("b-dependent-mfd-uncert"))
			mfdMagDepUncertModel = M->0.1*Math.max(1, Math.pow(10, bValue*0.5*(M-6)));
		
		LogicTreeBranch<?> branch = rupSet.getModule(LogicTreeBranch.class);
		
		// first check for supra-seis b-value MFDs, which can be used even if the regional MFD constraint is disabled
		if (cmd.hasOption("supra-b-mfds")) {
			Preconditions.checkArgument(!cmd.hasOption("infer-target-gr") && !cmd.hasOption("mfd-total-rate"),
					"Can't supply --supra-b-mfds with either --infer-target-gr or --mfd-total-rate");
			SupraSeisBValInversionTargetMFDs.Builder builder = new SupraSeisBValInversionTargetMFDs.Builder(rupSet, bValue);
			
			if (branch != null && branch.hasValue(SupraSeisBValues.class)) {
				// we have a logic tree branch, update the b-value to match
				boolean found = false;
				for (SupraSeisBValues node : SupraSeisBValues.values()) {
					if (node.bValue == bValue) {
						found = true;
						branch.setValueUnchecked(node);
					}
				}
				if (!found)
					// clear it
					branch.clearValue(SupraSeisBValues.class);
			}
			
			if (cmd.hasOption("adj-mfds-for-seg")) {
				segModel = getSegModel(rupSet, cmd);
				Preconditions.checkNotNull(segModel, "--adj-mfds-for-seg supplied but no segmentation model specified");
				
				if (segModel instanceof BinaryJumpProbabilityCalc)
					builder.forBinaryRupProbModel((BinaryJumpProbabilityCalc)segModel);
				else
					builder.adjustTargetsForData(NSHM23_ConstraintBuilder.SEG_ADJ_METHOD_DEFAULT.getAdjustment(segModel));
			}
			
			if (cmd.hasOption("adj-mfds-for-slips"))
				builder.adjustTargetsForData(new ScalingRelSlipRateMFD_Estimator(false));
			
			if (cmd.hasOption("sub-seis-mo-red"))
				builder.subSeisMoRateReduction(SubSeisMoRateReduction.valueOf(cmd.getOptionValue("sub-seis-mo-red")));
			else
				builder.subSeisMoRateReduction(SubSeisMoRateReduction.FROM_INPUT_SLIP_RATES);
			
			builder.applyDefModelUncertainties(cmd.hasOption("adj-mfd-ucert-slip"));
			
			if (cmd.hasOption("adj-mfd-ucert-paleo")) {
				paleoData = getPaleoData(rupSet, cmd);
				Preconditions.checkNotNull(paleoData, "--adj-mfd-ucert-paleo supplied but no paleoseismic data specified");
				
				List<SectNucleationMFD_Estimator> dataConstraints = new ArrayList<>();
				if (paleoData.getPaleoSlipConstraints() != null) {
					// use updated slip rate
					rupSet.addModule(builder.buildSlipRatesOnly());
				}
				dataConstraints.addAll(PaleoSectNuclEstimator.buildPaleoEstimates(rupSet, true, null));
				builder.expandUncertaintiesForData(dataConstraints, UncertaintyBoundType.ONE_SIGMA);
			}
			
			if (mfdMagDepUncertModel != null)
				builder.magDepDefaultRelStdDev(mfdMagDepUncertModel);
			
			targetMFDs = builder.build();
			rupSet.addModule(targetMFDs);
		}
		
		if (cmd.hasOption("mfd-constraint")) {
			double weight = 1d;

			if (cmd.hasOption("mfd-weight"))
				weight = Double.parseDouble(cmd.getOptionValue("mfd-weight"));
			
			List<? extends IncrementalMagFreqDist> mfdConstraints;
			if (targetMFDs == null) {
				if (cmd.hasOption("infer-target-gr")) {
					IncrementalMagFreqDist targetMFD = inferTargetGRFromSlipRates(rupSet, bValue);
					
					if (mfdMagDepUncertModel != null)
						targetMFD = UncertainIncrMagFreqDist.relStdDev(targetMFD, mfdMagDepUncertModel);
					
					mfdConstraints = List.of(targetMFD);
					
					targetMFDs = new InversionTargetMFDs.Precomputed(rupSet, null, targetMFD, null, null, mfdConstraints, null, null);
					rupSet.addModule(targetMFDs);
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
					
					IncrementalMagFreqDist targetMFD = targetGR;
					if (mfdMagDepUncertModel != null)
						targetMFD = UncertainIncrMagFreqDist.relStdDev(targetMFD, mfdMagDepUncertModel);
					
					mfdConstraints = List.of(targetMFD);
					
					targetMFDs = new InversionTargetMFDs.Precomputed(rupSet, null, targetMFD, null, null, mfdConstraints, null, null);
					rupSet.addModule(targetMFDs);
				} else {
					Preconditions.checkState(rupSet.hasModule(InversionTargetMFDs.class),
							"MFD Constraint enabled, but no target MFD specified. Rupture Set must either already have "
							+ "target MFDs attached, or MFD should be specified via --infer-target-gr or --mfd-total-rate <rate>.");
					targetMFDs = rupSet.requireModule(InversionTargetMFDs.class);
					
					if (mfdMagDepUncertModel != null) {
						List<IncrementalMagFreqDist> newConstraints = new ArrayList<>();
						for (IncrementalMagFreqDist constraint : targetMFDs.getMFD_Constraints()) {
							if (!(constraint instanceof UncertainIncrMagFreqDist))
								constraint = UncertainIncrMagFreqDist.relStdDev(constraint, mfdMagDepUncertModel);
							newConstraints.add(constraint);
						}
						mfdConstraints = newConstraints;
					} else {
						mfdConstraints = targetMFDs.getMFD_Constraints();
					}
				}
			} else {
				// will already be set up for uncertainties
				mfdConstraints = targetMFDs.getMFD_Constraints();
			}
			
			ConstraintWeightingType mfdType = mfdMagDepUncertModel == null ?
					ConstraintWeightingType.NORMALIZED : ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY;
			
			for (IncrementalMagFreqDist constr : mfdConstraints)
				System.out.println("MFD Constraint for region "
						+(constr.getRegion() == null ? "null" : constr.getRegion().getName())
						+":\n"+constr);
			
			if (cmd.hasOption("mfd-ineq")) {
				Preconditions.checkArgument(!cmd.hasOption("mfd-transition-mag"),
						"Can't specify both --mfd-transition-mag and --mfd-ineq");
				constraints.add(new MFDInversionConstraint(rupSet, weight, true, mfdType, mfdConstraints));
			} else if (cmd.hasOption("mfd-transition-mag")) {
				double transMag = Double.parseDouble(cmd.getOptionValue("mfd-transition-mag"));
				List<IncrementalMagFreqDist> eqConstrs = new ArrayList<>();
				List<IncrementalMagFreqDist> ieqConstrs = new ArrayList<>();
				for (IncrementalMagFreqDist constr : mfdConstraints) {
					eqConstrs.add(restrictMFDRange(constr, Double.NEGATIVE_INFINITY, transMag));
					ieqConstrs.add(restrictMFDRange(constr, transMag, Double.POSITIVE_INFINITY));
				}
				constraints.add(new MFDInversionConstraint(rupSet, weight, false, mfdType, eqConstrs));
				constraints.add(new MFDInversionConstraint(rupSet, weight, true, mfdType, ieqConstrs));
			} else {
				constraints.add(new MFDInversionConstraint(rupSet, weight, false, mfdType, mfdConstraints));
			}
		}
		
		if (cmd.hasOption("rel-gr-constraint")) {
			double weight = 1d;

			if (cmd.hasOption("mfd-weight"))
				weight = Double.parseDouble(cmd.getOptionValue("mfd-weight"));
			
			boolean ineq = cmd.hasOption("mfd-ineq");
			
			constraints.add(new RelativeBValueConstraint(rupSet, bValue, weight,
					ConstraintWeightingType.NORMALIZED, null, ineq));
		}
		
		// section rate constraints
		if (cmd.hasOption("sect-rate-constraint")) {
			double weight = 1d;
			if (cmd.hasOption("sect-rate-weight"))
				weight = Double.parseDouble(cmd.getOptionValue("sect-rate-weight"));
			
			ConstraintWeightingType type = cmd.hasOption("sect-rate-uncert") ?
					ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY : ConstraintWeightingType.NORMALIZED;
			
			Preconditions.checkNotNull(targetMFDs, "Section rate constraint enabled but no target MFDs");
			List<? extends IncrementalMagFreqDist> sectSupraMFDs = targetMFDs.getOnFaultSupraSeisNucleationMFDs();
			Preconditions.checkNotNull(sectSupraMFDs, "Section rate constraint enabled but target MFDs doesn't supply section MFDs");
			
			double[] targetRates = new double[rupSet.getNumSections()];
			double[] targetRateStdDevs = type == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY ?
					new double[rupSet.getNumSections()] : null;
			
			for (int s=0; s<targetRates.length; s++) {
				IncrementalMagFreqDist sectSupraMFD = sectSupraMFDs.get(s);
				targetRates[s] = sectSupraMFD.calcSumOfY_Vals();
				if (targetRateStdDevs != null) {
					Preconditions.checkState(sectSupraMFD instanceof UncertainIncrMagFreqDist,
							"--sect-rate-uncert supplied but section MFDs have no uncertainty model");
					UncertainBoundedIncrMagFreqDist oneSigmaBoundedMFD = 
							((UncertainIncrMagFreqDist)sectSupraMFD).estimateBounds(UncertaintyBoundType.ONE_SIGMA);
					double upperVal = oneSigmaBoundedMFD.getUpper().calcSumOfY_Vals();
					double lowerVal = oneSigmaBoundedMFD.getLower().calcSumOfY_Vals();
					targetRateStdDevs[s] = UncertaintyBoundType.ONE_SIGMA.estimateStdDev(targetRates[s], lowerVal, upperVal);
				}
			}
			
			constraints.add(new SectionTotalRateConstraint(rupSet, weight, type, targetRates, targetRateStdDevs, true));
		}
		
		// section MFD constraints
		if (cmd.hasOption("sect-mfd-constraint")) {
			double weight = 1d;
			if (cmd.hasOption("sect-mfd-weight"))
				weight = Double.parseDouble(cmd.getOptionValue("sect-mfd-weight"));
			
			ConstraintWeightingType type = cmd.hasOption("sect-mfd-uncert") ?
					ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY : ConstraintWeightingType.NORMALIZED;
			
			Preconditions.checkNotNull(targetMFDs, "Section MFD constraint enabled but no target MFDs");
			List<? extends IncrementalMagFreqDist> sectSupraMFDs = targetMFDs.getOnFaultSupraSeisNucleationMFDs();
			Preconditions.checkNotNull(sectSupraMFDs, "Section MFD constraint enabled but target MFDs doesn't supply section MFDs");
			
			constraints.add(new SubSectMFDInversionConstraint(rupSet, weight, type, sectSupraMFDs, true));
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
			
			constraints.add(new TotalRateInversionConstraint(weight, targetEventRate,
					rupSet, 0d, ConstraintWeightingType.UNNORMALIZED, Double.NaN, cmd.hasOption("event-rate-ineq")));
		}
		
		if (cmd.hasOption("paleo-constraint")) {
			double weight = 1d;
			
			if (cmd.hasOption("paleo-weight"))
				weight = Double.parseDouble(cmd.getOptionValue("paleo-weight"));
			
			if (paleoData == null)
				paleoData = getPaleoData(rupSet, cmd);
			
			constraints.add(new PaleoRateInversionConstraint(rupSet, weight,
					paleoData.getPaleoRateConstraints(), paleoData.getPaleoProbModel()));
			if (paleoData.getPaleoSlipConstraints() != null && !paleoData.getPaleoSlipConstraints().isEmpty())
				System.err.println("WARNING: rupture set has paleo slip constraints, which are not supported via the "
						+ "command line inversion configuration, skipping");
		}
		
		if (cmd.hasOption("seg-constraint") || cmd.hasOption("slip-seg-constraint")) {
			System.out.println("Adding segmentation constraints");
			double weight = 1d;
			if (cmd.hasOption("seg-weight"))
				weight = Double.parseDouble(cmd.getOptionValue("seg-weight"));
			
			if (segModel == null)
				segModel = getSegModel(rupSet, cmd);
			if (segModel == null)
				throw new IllegalArgumentException("Segmentation constraint enabled, but no segmentation rates specified.");
			
			boolean inequality = !cmd.hasOption("seg-constraint-eq");
			
			if (cmd.hasOption("seg-constraint")) {
				// rate constraint
				Preconditions.checkState(!cmd.hasOption("slip-seg-constraint"),
						"Can't enable both rate and slip segmentation constraints");
				SectParticipationRateEstimator rateEst = getDefaultSectParticEstimator(rupSet);
				constraints.add(new JumpProbabilityConstraint.RelativeRate(weight, inequality, rupSet, segModel, rateEst));
			} else {
				// proxy slip constraint
				constraints.add(new JumpProbabilityConstraint.ProxySlip(weight, inequality, rupSet, segModel));
			}
		}
		
		if (cmd.hasOption("smooth") || cmd.hasOption("paleo-smooth")) {
			double weight = 1d;
			if (cmd.hasOption("smooth-weight"))
				weight = Double.parseDouble(cmd.getOptionValue("smooth-weight"));
			
			if (cmd.hasOption("paleo-smooth")) {
				// only at paleo sites
				if (paleoData == null)
					paleoData = getPaleoData(rupSet, cmd);
				Preconditions.checkNotNull(paleoData, "Can't smooth at paleo sites if no paleo data attached");
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
	
	private static JumpProbabilityCalc getSegModel(FaultSystemRupSet rupSet, CommandLine cmd) throws IOException {
		LogicTreeBranch<?> branch = rupSet.getModule(LogicTreeBranch.class);
		if (cmd.hasOption("seg-dist-d0")) {
			// shaw and dieterich (2007)
			Preconditions.checkState(!cmd.hasOption("seg-rates") && !cmd.hasOption("seg-nshm23"),
					"Cannot supply multiple segmentation constraints");
			
			double d0 = Double.parseDouble(cmd.getOptionValue("seg-dist-d0"));
			
			if (cmd.hasOption("seg-dist-delta"))
				return Shaw07JumpDistProb.forHorzOffset(1d, d0, Double.parseDouble(cmd.getOptionValue("seg-dist-delta")));
			else
				return new Shaw07JumpDistProb(1d, d0);
		} else if (cmd.hasOption("seg-rates")) {
			Preconditions.checkState(!cmd.hasOption("seg-dist-d0") && !cmd.hasOption("seg-nshm23"),
					"Cannot supply multiple segmentation constraints");
			File csvFile = new File(cmd.getOptionValue("seg-rates"));
			Preconditions.checkState(csvFile.exists(), "Segmentation rate CSV file doesn't exist: %s", csvFile.getAbsolutePath());
			System.out.println("Loading segmentation rates from "+csvFile.getAbsolutePath());
			CSVFile<String> csv = CSVFile.readFile(csvFile, true);
			Preconditions.checkState(csv.getNumCols() == 3, "Segmentation CSV file should be exactly 3 columns: "
					+ "Section ID1,Section ID2,Fractional Passthrough Rate");
			Preconditions.checkState(csv.getNumRows() > 1, "Segmentation CSV file doesn't contain any rates");
			boolean parents = cmd.hasOption("--seg-parent-rates");
			HashSet<Integer> parentsSet = null;
			if (parents) {
				parentsSet = new HashSet<>();
				for (FaultSection sect : rupSet.getFaultSectionDataList())
					if (sect.getParentSectionId() >= 0)
						parentsSet.add(sect.getParentSectionId());
				Preconditions.checkState(!parentsSet.isEmpty(),
						"Parents flag given, but fault section data have no valid parent section IDs");
			}
			Map<IDPairing, Double> rates = new HashMap<>();
			for (int row=1; row<csv.getNumRows(); row++) {
				int sect1 = csv.getInt(row, 0);
				int sect2 = csv.getInt(row, 1);
				Preconditions.checkState(sect1 != sect2, "Both section IDs the same? %s == %s", sect1, sect2);
				if (parents) {
					Preconditions.checkState(parentsSet.contains(sect1), "Bad parent section ID: %s", sect1);
					Preconditions.checkState(parentsSet.contains(sect2), "Bad parent section ID: %s", sect2);
				} else {
					Preconditions.checkState(sect1 >= 0 && sect1 < rupSet.getNumSections(), "Bad section ID: %s", sect1);
					Preconditions.checkState(sect2 >= 0 && sect2 < rupSet.getNumSections(), "Bad section ID: %s", sect2);
					int parent1 = rupSet.getFaultSectionData(sect1).getParentSectionId();
					int parent2 = rupSet.getFaultSectionData(sect2).getParentSectionId();
					if (parent1 >= 0 || parent2 >= 0)
						Preconditions.checkState(parent1 != parent2, "Cannot constraint segmentation on the same parent section.");
				}
				double rate = csv.getDouble(row, 2);
				Preconditions.checkState(rate >= 0 && rate <= 1, "Passthrough rates must be in the range [0,1]: %s", rate);
				rates.put(new IDPairing(sect1, sect2), rate);
			}
			System.out.println("Loaded "+rates.size()+" segmentation rates");
			return new JumpProbabilityCalc.HardcodedJumpProb(csvFile.getName(), rates, parents);
		} else if (cmd.hasOption("seg-nshm23")) {
			Preconditions.checkState(!cmd.hasOption("seg-rates") && !cmd.hasOption("seg-dist-d0"),
					"Cannot supply multiple segmentation constraints");
			NSHM23_SegmentationModels model = NSHM23_SegmentationModels.valueOf(cmd.getOptionValue("seg-nshm23"));
			if (branch != null && branch.hasValue(NSHM23_SegmentationModels.class))
				branch.setValueUnchecked(model);
			return model.getModel(rupSet, null);
		} else {
			return null;
		}
	}
	
	private static PaleoseismicConstraintData getPaleoData(FaultSystemRupSet rupSet, CommandLine cmd) throws IOException {
		if (cmd.hasOption("paleo-data")) {
			File csvFile = new File(cmd.getOptionValue("paleo-data"));
			CSVFile<String> csv = CSVFile.readFile(csvFile, true);
			
			PaleoProbabilityModels modelChoice = PaleoProbabilityModels.DEFAULT;
			if (cmd.hasOption("paleo-prob-model"))
				modelChoice = PaleoProbabilityModels.valueOf(cmd.getOptionValue("paleo-prob-model"));
			PaleoseismicConstraintData paleoData = PaleoseismicConstraintData.fromSimpleCSV(rupSet, csv, modelChoice.get());
			rupSet.addModule(paleoData);
			return paleoData;
		} else {
			Preconditions.checkArgument(rupSet.hasModule(PaleoseismicConstraintData.class),
					"Must supply --paleo-data if PaleoseismicConstraintData module not attached to rupture set and "
					+ "--paleo-constraint enabled.");
			Preconditions.checkArgument(!cmd.hasOption("paleo-prob-model"), "Cannot supply paleoseismic probably "
					+ "model when using already attached paleo data.");
			return rupSet.requireModule(PaleoseismicConstraintData.class);
		}
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
		FaultSysTools.checkPrintHelp(null, cmd, Inversions.class);
		
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
