package scratch.UCERF3.inversion;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.math3.stat.StatUtils;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.data.Range;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.hpc.mpj.taskDispatch.MPJTaskCalculator;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.AverageFaultSystemSolution;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.SlipEnabledSolution;
import scratch.UCERF3.analysis.CompoundFSSPlots;
import scratch.UCERF3.analysis.FaultSpecificSegmentationPlotGen;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.inversion.laughTest.LaughTestFilter;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.ProgressTrackingCompletionCriteria;
import scratch.UCERF3.utils.IDPairing;
import scratch.UCERF3.utils.MatrixIO;
import scratch.UCERF3.utils.RELM_RegionUtils;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.UCERF2_MFD_ConstraintFetcher;
import scratch.UCERF3.utils.UCERF2_Section_MFDs.UCERF2_Section_MFDsCalc;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoFitPlotter;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoRateConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoSiteCorrelationData;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF2_PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF2_PaleoRateConstraintFetcher;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoRateConstraintFetcher;
import scratch.kevin.ucerf3.RupSetDownsampler;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.io.Files;

/**
 * This is the main class for running UCERF3 Inversions. It handes creation of the input matrices,
 * running the inversion, and generating standard outputs/plots.
 * <br><br>
 * All options are specified via command line arguments. First, see Simulated Annealing specific
 * arguments in the ThreadedSimulatedAnnealing class. Then it requires the working directory and
 * a branch prefix, which is text based representation of the LogicTreeBranch to be computed
 * (see LogicTreeBranch.buildFileName()). There are also a number of inversion options that can
 * be used to override default behaviours, such as equation set weights and starting solutions.
 * These are all outlined in the InversionOptions enum below. These can be used to test specific
 * aspects of the model, and were widely used when developing the equation set weights.
 * @author kevin
 *
 */
public class CommandLineInversionRunner {

	public enum InversionOptions {
		DEFAULT_ASEISMICITY("aseis", "default-aseis", "Aseis", true,
		"Default Aseismicity Value"),
		A_PRIORI_CONST_FOR_ZERO_RATES("apz", "a-priori-zero", "APrioriZero", false,
		"Flag to apply a priori constraint to zero rate ruptures"),
		A_PRIORI_CONST_WT("apwt", "a-priori-wt", "APrioriWt", true, "A priori constraint weight"),
		WATER_LEVEL_FRACT("wtlv", "waterlevel", "Waterlevel", true, "Waterlevel fraction"),
		PARKFIELD_WT("pkfld", "parkfield-wt", "Parkfield", true, "Parkfield constraint weight"),
		PALEO_WT("paleo", "paleo-wt", "Paleo", true, "Paleoconstraint weight"),
		AVE_SLIP_WT("aveslip", "ave-slip-wt", "AveSlip", true, "Ave slip weight"),
		//		NO_SUBSEIS_RED("nosub", "no-subseismo", "NoSubseismo", false,
		//				"Flag to turn off subseimogenic reductions"),
		MFD_WT("mfd", "mfd-wt", "MFDWt", true, "MFD constraint weight"),
		INITIAL_ZERO("zeros", "initial-zeros", "Zeros", false, "Force initial state to zeros"),
		INITIAL_GR("inigr", "initial-gr", "StartGR", false, "GR starting model"),
		INITIAL_RANDOM("random", "initial-random", "RandStart", false, "Force initial state to random distribution"),
		EVENT_SMOOTH_WT("eventsm", "event-smooth-wt", "EventSmoothWt", true, "Relative Event Rate Smoothness weight"),
		SECTION_NUCLEATION_MFD_WT("nuclwt", "sect-nucl-mfd-wt", "SectNuclMFDWt", true,
				"Relative section nucleation MFD constraint weight"),
		MFD_TRANSITION_MAG("mfdtrans", "mfd-trans-mag", "MFDTrans", true, "MFD transition magnitude"),
		MFD_SMOOTHNESS_WT("mfdsmooth", "mfd-smooth-wt", "Smooth", true, "MFD smoothness constraint weight"),
		PALEO_SECT_MFD_SMOOTH("paleomfdsmooth", "paleo-sect-mfd-smooth", "SmoothPaleoSect", true,
				"MFD smoothness constraint weight for peleo parent sects"),
		REMOVE_OUTLIER_FAULTS("removefaults", "remove-faults", "RemoveFaults", false, "Remove some outlier high slip faults."),
		SLIP_WT_NORM("slipwt", "slip-wt", "SlipWt", true, "Normalized slip rate constraint wt"),
		SLIP_WT_UNNORM("slipwtunnorm", "slip-wt-unnorm", "SlipWtUnNorm", true, "Unnormalized slip rate constraint wt"),
		SLIP_WT_TYPE("sliptype", "slip-type", "SlipType", true, "Slip wt type"),
		SERIAL("serial", "force-serial", "Serial", false, "Force serial annealing"),
		SYNTHETIC("syn", "synthetic", "Synthetic", false, "Synthetic data from solution rates named syn.bin."),
		COULOMB("coulomb", "coulomb-threshold", "Coulomb", true, "Set coulomb filter threshold"),
		COULOMB_EXCLUDE("coulombex", "coulomb-exclude-threshold", "CoulombExclusion", true,
				"Set coulomb filter exclusion DCFF threshold"),
		UCERF3p2("u3p2", "ucerf3p2", "U3p2", false, "Flag for reverting to UCERF3.2 rup set/data"),
		RUP_SMOOTH_WT("rupsm", "rup-rate-smoothing-wt", "RupSmth", true, "Rupture rate smoothing constraint weight"),
		U2_MAPPED_RUPS_ONLY("u2rups", "u2-rups-only", "U2Rups", false, "UCERF2 Mappable Ruptures Only"),
		RUP_FILTER_FILE("rupfilter", "rup-filter-file", "FilteredRups", true,
				"ASCII file listing rupture indexes, one per line, to include in output solution"),
		RUP_DOWNSAMPLE_DM("dwn", "rup-downsample-dm", "Downsample", true,
				"Enable rup set downsampling with the given delta magnitude");

		private String shortArg, argName, fileName, description;
		private boolean hasOption;

		private InversionOptions(String shortArg, String argName, String fileName, boolean hasOption,
				String description) {
			this.shortArg = shortArg;
			this.argName = argName;
			this.fileName = fileName;
			this.hasOption = hasOption;
			this.description = description;
		}

		public String getShortArg() {
			return shortArg;
		}

		public String getArgName() {
			return argName;
		}

		public String getCommandLineArgs() {
			return getCommandLineArgs(null);
		}

		public String getCommandLineArgs(double option) {
			return getCommandLineArgs((float)option+"");
		}

		public String getCommandLineArgs(String option) {
			String args = "--"+argName;
			if (hasOption) {
				Preconditions.checkArgument(option != null && !option.isEmpty());
				args += " "+option;
			}
			return args;
		}

		public String getFileName() {
			return getFileName(null);
		}

		public String getFileName(double option) {
			return getFileName((float)option+"");
		}

		public String getFileName(String option) {
			if (hasOption) {
				Preconditions.checkArgument(option != null && !option.isEmpty());
				if (option.contains("/")) {
					// it's a file, just use file name
					File file = new File(option);
					option = file.getName().replaceAll("_", "");
					if (option.contains("."))
						option = option.substring(0, option.indexOf("."));
				}
				return fileName+option;
			}
			return fileName;
		}

		public boolean hasOption() {
			return hasOption;
		}
	}

	protected static Options createOptions() {
		Options ops = ThreadedSimulatedAnnealing.createOptionsNoInputs();

		for (InversionOptions invOp : InversionOptions.values()) {
			Option op = new Option(invOp.shortArg, invOp.argName, invOp.hasOption, invOp.description);
			op.setRequired(false);
			ops.addOption(op);
		}

		Option rupSetOp = new Option("branch", "branch-prefix", true, "Prefix for file names." +
		"Should be able to parse logic tree branch from this");
		rupSetOp.setRequired(true);
		ops.addOption(rupSetOp);

		Option lightweightOp = new Option("light", "lightweight", false, "Only write out a bin file for the solution." +
		"Leave the rup set if the prefix indicates run 0");
		lightweightOp.setRequired(false);
		ops.addOption(lightweightOp);

		Option dirOp = new Option("dir", "directory", true, "Directory to store inputs");
		dirOp.setRequired(true);
		ops.addOption(dirOp);
		
		Option noPlotsOp = new Option("noplots", "no-plots", false,
				"Flag to disable any plots (but still write solution zip file)");
		noPlotsOp.setRequired(false);
		ops.addOption(noPlotsOp);

		return ops;
	}

	public static void printHelp(Options options, boolean mpj) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(
				ClassUtils.getClassNameWithoutPackage(CommandLineInversionRunner.class),
				options, true );
		if (mpj)
			MPJTaskCalculator.abortAndExit(2);
		else
			System.exit(2);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		run(args, false);
		System.out.println("DONE");
		System.exit(0);
	}
	
	public static void run(String[] args, boolean mpj) {
		Options options = createOptions();

		try {
			CommandLineParser parser = new GnuParser();
			CommandLine cmd = parser.parse(options, args);

			// if enabled, only the .bin files and rup set will be written out (no solution zip file)
			boolean lightweight = cmd.hasOption("lightweight");
			// solution zip file will still be written out, but no plots
			boolean noPlots = cmd.hasOption("no-plots");

			// get the directory/logic tree branch
			File dir = new File(cmd.getOptionValue("directory"));
			if (!dir.exists())
				dir.mkdir();
			String prefix = cmd.getOptionValue("branch-prefix");
			// parse logic tree branch from prefix
			LogicTreeBranch branch = LogicTreeBranch.fromFileName(prefix);
			// ensure that branch is fully specified (no nulls)
			Preconditions.checkState(branch.isFullySpecified(),
					"Branch is not fully fleshed out! Prefix: "+prefix+", branch: "+branch);

			// rup specific files are stored in a subdirectory using the prefix
			File subDir = new File(dir, prefix);
			if (!subDir.exists())
				subDir.mkdir();

			// Laugh Test Filter for rup set creation
			LaughTestFilter laughTest;
			if (cmd.hasOption(InversionOptions.UCERF3p2.argName))
				laughTest = LaughTestFilter.getUCERF3p2Filter();
			else
				laughTest = LaughTestFilter.getDefault();
			
			// Option for overriding default Coulomb PDCFF threshold
			if (cmd.hasOption(InversionOptions.COULOMB.argName)) {
				double val = Double.parseDouble(cmd.getOptionValue(InversionOptions.COULOMB.argName));
				laughTest.getCoulombFilter().setMinAverageProb(val);
				laughTest.getCoulombFilter().setMinIndividualProb(val);
			}
			// Option for overriding default Coulomb DCFF threshold
			if (cmd.hasOption(InversionOptions.COULOMB_EXCLUDE.argName)) {
				double val = Double.parseDouble(cmd.getOptionValue(InversionOptions.COULOMB_EXCLUDE.argName));
				laughTest.getCoulombFilter().setMinimumStressExclusionCeiling(val);
			}
			// default aseismicity value for faults without creep data
			String aseisArg = InversionOptions.DEFAULT_ASEISMICITY.argName;
			double defaultAseis = InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE;
			// option for overriding this default value
			if (cmd.hasOption(aseisArg)) {
				String aseisVal = cmd.getOptionValue(aseisArg);
				defaultAseis = Double.parseDouble(aseisVal);
			}
			
			// build the rupture set
			System.out.println("Building RupSet");
			if (cmd.hasOption("remove-faults")) {
				// this is an option for a special test where we ignore certain
				// troublesome faults
				HashSet<Integer> sectionsToIgnore = new HashSet<Integer>();
				sectionsToIgnore.add(13); // mendocino
				sectionsToIgnore.add(97); // imperial
				sectionsToIgnore.add(172); // cerro prieto
				sectionsToIgnore.add(104); // laguna salada
				laughTest.setParentSectsToIgnore(sectionsToIgnore);
			}
			InversionFaultSystemRupSet rupSet = InversionFaultSystemRupSetFactory.forBranch(
					laughTest, defaultAseis, branch);
			System.out.println("Num rups: "+rupSet.getNumRuptures());
			
			if (cmd.hasOption(InversionOptions.RUP_FILTER_FILE.argName)) {
				rupSet = getFilteredRupsOnly(rupSet, new File(cmd.getOptionValue(InversionOptions.RUP_FILTER_FILE.argName)));
				System.out.println("Num rups after filtering: "+rupSet.getNumRuptures());
			}
			
			if (cmd.hasOption(InversionOptions.RUP_DOWNSAMPLE_DM.argName)) {
				double dm = Double.parseDouble(cmd.getOptionValue(InversionOptions.RUP_DOWNSAMPLE_DM.argName));
				rupSet = getDownsampledRupSet(rupSet, dm);
				System.out.println("Num rups after filtering: "+rupSet.getNumRuptures());
			}
			
			if (cmd.hasOption(InversionOptions.U2_MAPPED_RUPS_ONLY.argName)) {
				rupSet = getUCERF2RupsOnly(rupSet);
				System.out.println("Num rups after UCERF2 mapping: "+rupSet.getNumRuptures());
			}

			// store distances for jump plot later
			Map<IDPairing, Double> distsMap = rupSet.getSubSectionDistances();

			// now build the inversion inputs

			// MFD constraint weights
			double mfdEqualityConstraintWt = InversionConfiguration.DEFAULT_MFD_EQUALITY_WT;
			double mfdInequalityConstraintWt = InversionConfiguration.DEFAULT_MFD_INEQUALITY_WT;

			// check if we're on a RelaxMFD branch and set weights accordingly
			if (branch.getValue(MomentRateFixes.class).isRelaxMFD()) {
				mfdEqualityConstraintWt = 1;
				mfdInequalityConstraintWt = 1;
			}

			System.out.println("Building Inversion Configuration");
			// this contains all inversion weights
			InversionConfiguration config = InversionConfiguration.forModel(branch.getValue(InversionModels.class),
					rupSet, mfdEqualityConstraintWt, mfdInequalityConstraintWt, cmd);

			// load paleo rate constraints
			ArrayList<PaleoRateConstraint> paleoRateConstraints = getPaleoConstraints(branch.getValue(FaultModels.class), rupSet);

			// load paleo probability of observance model
			PaleoProbabilityModel paleoProbabilityModel =
				InversionInputGenerator.loadDefaultPaleoProbabilityModel();

			// this class generates inversion inputs (A matrix and data vector)
			InversionInputGenerator gen = new InversionInputGenerator(rupSet, config,
					paleoRateConstraints, null, paleoProbabilityModel);

			// flag for enabling A Priori constraint (not used by default) on zero rate ruptures
			// which acts as a minimization constraint on those ruptures.
			if (cmd.hasOption(InversionOptions.A_PRIORI_CONST_FOR_ZERO_RATES.argName)) {
				System.out.println("Setting a prior constraint for zero rates");
				gen.setAPrioriConstraintForZeroRates(true);
			}

			// actually generate the inputs
			System.out.println("Building Inversion Inputs");
			gen.generateInputs();

			// write out the rup set to a file so that we can clear it from memory
			System.out.println("Writing RupSet");
			config.updateRupSetInfoString(rupSet);
			String info = rupSet.getInfoString();
			info += "\n\n"+getPreInversionInfo(rupSet);

			File rupSetFile = new File(subDir, prefix+"_rupSet.zip");
			FaultSystemIO.writeRupSet(rupSet, rupSetFile);
			// now clear it out of memory
			rupSet = null;
			gen.setRupSet(null);
			System.gc();

			// this makes the inversion much more efficient
			System.out.println("Column Compressing");
			gen.columnCompress();

			// fetch inversion inputs
			DoubleMatrix2D A = gen.getA();
			double[] d = gen.getD();
			double[] initialState = gen.getInitial();
			// options for overriding initial state
			if (cmd.hasOption(InversionOptions.INITIAL_ZERO.argName))
				initialState = new double[initialState.length];
			if (cmd.hasOption(InversionOptions.INITIAL_RANDOM.argName)) {
				initialState = new double[initialState.length];
				// random rate from to^-10 => 10^2
				double minExp = -6;
				double maxExp = -10;
				
				double deltaExp = maxExp - minExp;
				
				for (int r=0; r<initialState.length; r++)
					initialState[r] = Math.pow(10d, Math.random() * deltaExp + minExp);
			}
			// inputs for inequality constraints
			DoubleMatrix2D A_ineq = gen.getA_ineq();
			double[] d_ineq = gen.getD_ineq();
			// waterlevel rates (these have already been subtracted from all datas and the initial state)
			// can be null for no waterlevel
			double[] minimumRuptureRates = gen.getMinimumRuptureRates();
			// these list the row numbers and names for each constraint type for tracking individual energy levels
			List<Integer> rangeEndRows = gen.getRangeEndRows();
			List<String> rangeNames = gen.getRangeNames();
			
			if (cmd.hasOption(InversionOptions.SYNTHETIC.argName)) {
				// special synthetic inversion test
				double[] synrates = MatrixIO.doubleArrayFromFile(new File(dir, "syn.bin"));
				Preconditions.checkState(synrates.length == initialState.length,
						"synthetic starting solution has different num rups!");
				// subtract min rates
				synrates = gen.adjustSolutionForMinimumRates(synrates);
				
				DoubleMatrix1D synMatrix = new DenseDoubleMatrix1D(synrates);
				
				DenseDoubleMatrix1D syn = new DenseDoubleMatrix1D(A.rows());
				A.zMult(synMatrix, syn);
				
				double[] d_syn = syn.elements();
				
				Preconditions.checkState(d.length == d_syn.length,
						"D and D_syn lengths tdon't match!");
				
				List<int[]> rangesToCopy = Lists.newArrayList();
				
				for (int i=0; i<rangeNames.size(); i++) {
					String name = rangeNames.get(i);
					boolean keep = false;
					if (name.equals("Slip Rate"))
						keep = true;
					else if (name.equals("Paleo Event Rates"))
						keep = true;
					else if (name.equals("Paleo Slips"))
						keep = true;
					else if (name.equals("MFD Equality"))
						keep = true;
					else if (name.equals("MFD Nucleation"))
						keep = true;
					else if (name.equals("Parkfield"))
						keep = true;
					
					if (keep) {
						int prevRow;
						if (i == 0)
							prevRow = 0;
						else
							prevRow = rangeEndRows.get(i-1) + 1;
						int[] range = { prevRow, rangeEndRows.get(i) };
						
						rangesToCopy.add(range);
					}
				}
				
				// copy over "data" from synthetics
				for (int[] range : rangesToCopy) {
					System.out.println("Copying range "+range[0]+" => "+range[1]+" from syn to D");
					for (int i=range[0]; i<=range[1]; i++)
						d[i] = d_syn[i];
				}
			}

			for (int i=0; i<rangeEndRows.size(); i++) {
				System.out.println(i+". "+rangeNames.get(i)+": "+rangeEndRows.get(i));
			}

			// clear out generator
			gen = null;
			System.gc();

			System.out.println("Creating TSA");
			// set up multi thread SA
			ThreadedSimulatedAnnealing tsa = ThreadedSimulatedAnnealing.parseOptions(cmd, A, d,
					initialState, A_ineq, d_ineq, minimumRuptureRates, rangeEndRows, rangeNames);
			// store a copy of the initial state for later
			initialState = Arrays.copyOf(initialState, initialState.length);
			// setup completion criteria
			CompletionCriteria criteria = ThreadedSimulatedAnnealing.parseCompletionCriteria(cmd);
			if (!(criteria instanceof ProgressTrackingCompletionCriteria)) {
				File csvFile = new File(dir, prefix+".csv");
				criteria = new ProgressTrackingCompletionCriteria(criteria, csvFile);
			}
			if (cmd.hasOption(InversionOptions.SERIAL.argName)) {
				// this forces serial annealing by setting the sub completion criteria to the
				// general completion criteria
				((ProgressTrackingCompletionCriteria)criteria).setIterationModulus(10000l);
				tsa.setSubCompletionCriteria(criteria);
				tsa.setNumThreads(1);
			}
			// run the inversion
			System.out.println("Starting Annealing");
			tsa.iterate(criteria);
			System.out.println("Annealing DONE");
			
			// add SA metadata to solution info string
			info += "\n";
			info += "\n****** Simulated Annealing Metadata ******";
			info += "\n"+tsa.getMetadata(args, criteria);
			// add metadata on how many ruptures had their rates actually peturbed
			ProgressTrackingCompletionCriteria pComp = (ProgressTrackingCompletionCriteria)criteria;
			long numPerturbs = pComp.getPerturbs().get(pComp.getPerturbs().size()-1);
			int numRups = initialState.length;
			info += "\nAvg Perturbs Per Rup: "+numPerturbs+"/"+numRups+" = "
			+((double)numPerturbs/(double)numRups);
			int rupsPerturbed = 0;
			double[] solution_no_min_rates = tsa.getBestSolution();
			int numAboveWaterlevel =  0;
			for (int i=0; i<numRups; i++) {
				if ((float)solution_no_min_rates[i] != (float)initialState[i])
					rupsPerturbed++;
				if (solution_no_min_rates[i] > 0)
					numAboveWaterlevel++;
			}
			info += "\nNum rups actually perturbed: "+rupsPerturbed+"/"+numRups+" ("
			+(float)(100d*((double)rupsPerturbed/(double)numRups))+" %)";
			info += "\nAvg Perturbs Per Perturbed Rup: "+numPerturbs+"/"+rupsPerturbed+" = "
			+((double)numPerturbs/(double)rupsPerturbed);
			info += "\nNum rups above waterlevel: "+numAboveWaterlevel+"/"+numRups+" ("
			+(float)(100d*((double)numAboveWaterlevel/(double)numRups))+" %)";
			info += "\n******************************************";
			System.out.println("Writing solution bin files");
			// write out results
			tsa.writeBestSolution(new File(subDir, prefix+".bin"));

			// for lightweight we just write out the .bin file, no solution files
			if (!lightweight) {
				System.out.println("Loading RupSet");
				// load the RupSet back in for plotting and solution file creation
				InversionFaultSystemRupSet loadedRupSet = FaultSystemIO.loadInvRupSet(rupSetFile);
				loadedRupSet.setInfoString(info);
				double[] rupRateSolution = tsa.getBestSolution();
				// this adds back in the minimum rupture rates (waterlevel) if present
				rupRateSolution = InversionInputGenerator.adjustSolutionForMinimumRates(
						rupRateSolution, minimumRuptureRates);
				InversionFaultSystemSolution sol = new InversionFaultSystemSolution(
						loadedRupSet, rupRateSolution, config, tsa.getEnergies());

				File solutionFile = new File(subDir, prefix+"_sol.zip");

				// add moments to info string
				info += "\n\n****** Moment and Rupture Rate Metatdata ******";
				info += "\nOriginal File Name: "+solutionFile.getName()
				+"\nNum Ruptures: "+loadedRupSet.getNumRuptures();
				int numNonZeros = 0;
				for (double rate : sol.getRateForAllRups())
					if (rate != 0)
						numNonZeros++;
				float percent = (float)numNonZeros / loadedRupSet.getNumRuptures() * 100f;
				info += "\nNum Non-Zero Rups: "+numNonZeros+"/"+loadedRupSet.getNumRuptures()+" ("+percent+" %)";
				info += "\nOrig (creep reduced) Fault Moment Rate: "+loadedRupSet.getTotalOrigMomentRate();
				double momRed = loadedRupSet.getTotalMomentRateReduction();
				info += "\nMoment Reduction (for subseismogenic ruptures only): "+momRed;
				info += "\nSubseismo Moment Reduction Fraction (relative to creep reduced): "+loadedRupSet.getTotalMomentRateReductionFraction();
				info += "\nFault Target Supra Seis Moment Rate (subseismo and creep reduced): "
					+loadedRupSet.getTotalReducedMomentRate();
				double totalSolutionMoment = sol.getTotalFaultSolutionMomentRate();
				info += "\nFault Solution Supra Seis Moment Rate: "+totalSolutionMoment;
				info += "\nFault Target Sub Seis Moment Rate: "
						+loadedRupSet.getInversionTargetMFDs().getTotalSubSeismoOnFaultMFD().getTotalMomentRate();
				info += "\nFault Solution Sub Seis Moment Rate: "
						+sol.getFinalTotalSubSeismoOnFaultMFD().getTotalMomentRate();
				info += "\nTruly Off Fault Target Moment Rate: "
						+loadedRupSet.getInversionTargetMFDs().getTrulyOffFaultMFD().getTotalMomentRate();
				info += "\nTruly Off Fault Solution Moment Rate: "
						+sol.getFinalTrulyOffFaultMFD().getTotalMomentRate();

				try {
					//					double totalOffFaultMomentRate = invSol.getTotalOffFaultSeisMomentRate(); // TODO replace - what is off fault moment rate now?
					//					info += "\nTotal Off Fault Seis Moment Rate (excluding subseismogenic): "
					//							+(totalOffFaultMomentRate-momRed);
					//					info += "\nTotal Off Fault Seis Moment Rate (inluding subseismogenic): "
					//							+totalOffFaultMomentRate;
					info += "\nTotal Moment Rate From Off Fault MFD: "+sol.getFinalTotalGriddedSeisMFD().getTotalMomentRate();
					//					info += "\nTotal Model Seis Moment Rate: "
					//							+(totalOffFaultMomentRate+totalSolutionMoment);
				} catch (Exception e1) {
					e1.printStackTrace();
					System.out.println("WARNING: InversionFaultSystemSolution could not be instantiated!");
				}

				double totalMultiplyNamedM7Rate = FaultSystemRupSetCalc.calcTotRateMultiplyNamedFaults(sol, 7d, null);
				double totalMultiplyNamedPaleoVisibleRate = FaultSystemRupSetCalc.calcTotRateMultiplyNamedFaults(sol, 0d, paleoProbabilityModel);

				double totalM7Rate = FaultSystemRupSetCalc.calcTotRateAboveMag(sol, 7d, null);
				double totalPaleoVisibleRate = FaultSystemRupSetCalc.calcTotRateAboveMag(sol, 0d, paleoProbabilityModel);

				info += "\n\nTotal rupture rate (M7+): "+totalM7Rate;
				info += "\nTotal multiply named rupture rate (M7+): "+totalMultiplyNamedM7Rate;
				info += "\n% of M7+ rate that are multiply named: "
					+(100d * totalMultiplyNamedM7Rate / totalM7Rate)+" %";
				info += "\nTotal paleo visible rupture rate: "+totalPaleoVisibleRate;
				info += "\nTotal multiply named paleo visible rupture rate: "+totalMultiplyNamedPaleoVisibleRate;
				info += "\n% of paleo visible rate that are multiply named: "
					+(100d * totalMultiplyNamedPaleoVisibleRate / totalPaleoVisibleRate)+" %";
				info += "\n***********************************************";

				// parent fault moment rates
				ArrayList<ParentMomentRecord> parentMoRates = getSectionMoments(sol);
				info += "\n\n****** Larges Moment Rate Discrepancies ******";
				for (int i=0; i<10 && i<parentMoRates.size(); i++) {
					ParentMomentRecord p = parentMoRates.get(i);
					info += "\n"+p.parentID+". "+p.name+"\ttarget: "+p.targetMoment
					+"\tsolution: "+p.solutionMoment+"\tdiff: "+p.getDiff();
				}
				info += "\n**********************************************";
				
				if (!noPlots) {
					// MFD plots - do this now so that we can add the M5 rates to the metadata files
					try {
						List<? extends DiscretizedFunc> funcs = writeMFDPlots(sol, subDir, prefix);
						
						if (!funcs.isEmpty()) {
							info += "\n\n****** MFD Cumulative M5 Rates ******";
							for (DiscretizedFunc func : funcs) {
								double cumulative = 0d;
								for (int i=func.size(); --i>=0;)
									if (func.getX(i) >= 5d)
										cumulative += func.getY(i);
									else
										break;
								info += "\n"+func.getName()+":\t"+cumulative;
							}
							info += "\n**********************************************";
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				sol.setInfoString(info);

				// actually write the solution
				System.out.println("Writing solution");
				FaultSystemIO.writeSol(sol, solutionFile);
				
				if (!noPlots) {
					// now write out plots
					
					// this writes out target and solution moment rates for each parent fault sections
					// as a CSV file
					CSVFile<String> moRateCSV = new CSVFile<String>(true);
					moRateCSV.addLine(Lists.newArrayList("ID", "Name", "Target", "Solution", "Diff"));
					for (ParentMomentRecord p : parentMoRates)
						moRateCSV.addLine(Lists.newArrayList(p.parentID+"", p.name, p.targetMoment+"",
								p.solutionMoment+"", p.getDiff()+""));
					moRateCSV.writeToFile(new File(subDir, prefix+"_sect_mo_rates.csv"));
					
					System.out.println("Writing Plots");
					
					// write simulated annealing related plots
					tsa.writePlots(criteria, new File(subDir, prefix));

					// 1 km jump plot
					try {
						writeJumpPlots(sol, distsMap, subDir, prefix);
					} catch (Exception e) {
						e.printStackTrace();
					}

					// combined paleo plots, not really used anymore in favor of paleo fault based plots
					List<AveSlipConstraint> aveSlipConstraints = null;
					try {
						if (config.getPaleoSlipConstraintWt() > 0d)
							aveSlipConstraints = AveSlipConstraint.load(sol.getRupSet().getFaultSectionDataList());
						else
							aveSlipConstraints = null;
						writePaleoPlots(paleoRateConstraints, aveSlipConstraints, sol, subDir, prefix);
					} catch (Exception e) {
						e.printStackTrace();
					}

					// SAF segmentation plots
					try {
						writeSAFSegPlots(sol, subDir, prefix);
					} catch (Exception e) {
						e.printStackTrace();
					}
					
					// write MFD plots for each parent fault section
					try {
						writeParentSectionMFDPlots(sol, new File(subDir, PARENT_SECT_MFD_DIR_NAME));
					} catch (Exception e) {
						e.printStackTrace();
					}
					
					// these are for correlation between paleoseismic sites
					try {
						writePaleoCorrelationPlots(
								sol, new File(subDir, PALEO_CORRELATION_DIR_NAME), paleoProbabilityModel);
					} catch (Exception e) {
						e.printStackTrace();
					}
					
					// Paleo fault based plots
					try {
						Map<String, List<Integer>> namedFaultsMap = rupSet.getFaultModel().getNamedFaultsMapAlt();
						writePaleoFaultPlots(
								paleoRateConstraints, aveSlipConstraints, namedFaultsMap, sol, new File(subDir,
										PALEO_FAULT_BASED_DIR_NAME));
					} catch (Exception e) {
						e.printStackTrace();
					}
					
					try {
						writeRupPairingSmoothnessPlot(sol, prefix, subDir);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			FileWriter fw = new FileWriter(new File(subDir, prefix+"_metadata.txt"));
			fw.write(info);
			fw.close();

			System.out.println("Deleting RupSet (no longer needed)");
			rupSetFile.delete();
		} catch (MissingOptionException e) {
			System.err.println(e.getMessage());
			printHelp(options, mpj);
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			printHelp(options, mpj);
		} catch (Exception e) {
			if (mpj) {
				MPJTaskCalculator.abortAndExit(e);
			} else {
				e.printStackTrace();
				System.exit(1);
			}
		}
	}
	
	private static String getPreInversionInfo(InversionFaultSystemRupSet rupSet) {
		// 2 lines, tab delimeted
		String data = rupSet.getPreInversionAnalysisData(true);
		String[] dataLines = data.split("\n");
		String header = dataLines[0];
		data = dataLines[1];
		String[] headerVals = header.trim().split("\t");
		String[] dataVals = data.trim().split("\t");
		Preconditions.checkState(headerVals.length == dataVals.length);
		
		String info = "****** Pre Inversion Analysis ******";
		for (int i=0; i<headerVals.length; i++)
			info += "\n"+headerVals[i]+": "+dataVals[i];
		info += "\n***********************************************";
		
		return info;
	}
	
	public static final String PALEO_FAULT_BASED_DIR_NAME = "paleo_fault_based";
	public static final String PALEO_CORRELATION_DIR_NAME = "paleo_correlation";
	public static final String PARENT_SECT_MFD_DIR_NAME = "parent_sect_mfds";

	/**
	 * This writes plots of rupture rate vs the number of 1km (or greater) jumps. 
	 * @param sol
	 * @param distsMap
	 * @param dir
	 * @param prefix
	 * @throws IOException
	 */
	public static void writeJumpPlots(FaultSystemSolution sol, Map<IDPairing, Double> distsMap, File dir, String prefix) throws IOException {
		// use UCERF2 here because it doesn't depend on distance along
		PaleoProbabilityModel paleoProbModel = new UCERF2_PaleoProbabilityModel();
		writeJumpPlot(sol, distsMap, dir, prefix, 1d, 7d, null);
		writeJumpPlot(sol, distsMap, dir, prefix, 1d, 0d, paleoProbModel);
	}
	
	/**
	 * Bin rupture rates into histograms of the number of 1 km or greater jumps
	 * 
	 * @param sol
	 * @param distsMap subsection distances that were calculated earlier
	 * @param jumpDist jump distance for calculation
	 * @param minMag minimum magnitude of ruptures to consider
	 * @param paleoProbModel if non null thn rates will be multiplied by their probability of observance
	 * @return
	 */
	public static EvenlyDiscretizedFunc[] getJumpFuncs(FaultSystemSolution sol,
			Map<IDPairing, Double> distsMap, double jumpDist, double minMag,
			PaleoProbabilityModel paleoProbModel) {
		EvenlyDiscretizedFunc solFunc = new EvenlyDiscretizedFunc(0d, 4, 1d);
		EvenlyDiscretizedFunc rupSetFunc = new EvenlyDiscretizedFunc(0d, 4, 1d);
		int maxX = solFunc.size()-1;
		
		FaultSystemRupSet rupSet = sol.getRupSet();

		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			double mag = rupSet.getMagForRup(r);

			if (mag < minMag)
				continue;

			List<Integer> sects = rupSet.getSectionsIndicesForRup(r);
			
			int jumpsOverDist = 0;
			for (int i=1; i<sects.size(); i++) {
				int sect1 = sects.get(i-1);
				int sect2 = sects.get(i);

				int parent1 = rupSet.getFaultSectionData(sect1).getParentSectionId();
				int parent2 = rupSet.getFaultSectionData(sect2).getParentSectionId();

				if (parent1 != parent2) {
					double dist = distsMap.get(new IDPairing(sect1, sect2));
					if (dist > jumpDist)
						jumpsOverDist++;
				}
			}

			double rate = sol.getRateForRup(r);

			if (paleoProbModel != null)
				rate *= paleoProbModel.getProbPaleoVisible(mag, 0.5); // TODO 0.5?

						// indexes are fine to use here since it starts at zero with a delta of one 
			if (jumpsOverDist <= maxX) {
				solFunc.set(jumpsOverDist, solFunc.getY(jumpsOverDist) + rate);
				rupSetFunc.set(jumpsOverDist, rupSetFunc.getY(jumpsOverDist) + 1d);
			}
		}

		// now normalize rupSetFunc so that the sum of it's y values equals the sum of solFunc's y values
		double totY = solFunc.calcSumOfY_Vals();
		double origRupSetTotY = rupSetFunc.calcSumOfY_Vals();
		for (int i=0; i<rupSetFunc.size(); i++) {
			double y = rupSetFunc.getY(i);
			double fract = y / origRupSetTotY;
			double newY = totY * fract;
			rupSetFunc.set(i, newY);
		}
		
		EvenlyDiscretizedFunc[] ret = { solFunc, rupSetFunc };
		return ret;
	}

	public static void writeJumpPlot(FaultSystemSolution sol, Map<IDPairing, Double> distsMap, File dir, String prefix,
			double jumpDist, double minMag, PaleoProbabilityModel paleoProbModel) throws IOException {
		EvenlyDiscretizedFunc[] funcsArray = getJumpFuncs(sol, distsMap, jumpDist, minMag, paleoProbModel);
		writeJumpPlot(dir, prefix, funcsArray, jumpDist, minMag, paleoProbModel != null);
	}
	
	public static void writeJumpPlot(File dir, String prefix,
			DiscretizedFunc[] funcsArray, double jumpDist, double minMag, boolean paleoProb) throws IOException {
		DiscretizedFunc[] solFuncs = { funcsArray[0] };
		DiscretizedFunc[] rupSetFuncs = { funcsArray[1] };
		
		writeJumpPlot(dir, prefix, solFuncs, rupSetFuncs, jumpDist, minMag, paleoProb);
	}
	
	/**
	 * Write the given jump plot to a pdf/png/txt file
	 * @param dir
	 * @param prefix
	 * @param solFuncs
	 * @param rupSetFuncs
	 * @param jumpDist
	 * @param minMag
	 * @param paleoProb
	 * @throws IOException
	 */
	public static void writeJumpPlot(File dir, String prefix,
			DiscretizedFunc[] solFuncs, DiscretizedFunc[] rupSetFuncs, double jumpDist, double minMag, boolean paleoProb) throws IOException {
		ArrayList<DiscretizedFunc> funcs = Lists.newArrayList();
		ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();
		
		funcs.add(solFuncs[0]);
		funcs.add(rupSetFuncs[0]);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, PlotSymbol.CIRCLE, 5f, Color.BLACK));
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, PlotSymbol.CIRCLE, 3f, Color.RED));
		
		for (int i=1; i<solFuncs.length; i++) {
			funcs.add(solFuncs[i]);
			funcs.add(rupSetFuncs[i]);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, PlotSymbol.CIRCLE, 5f, Color.BLACK));
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, PlotSymbol.CIRCLE, 3f, Color.RED));
		}

		String title = "Inversion Fault Jumps";

		prefix = getJumpFilePrefix(prefix, minMag, paleoProb);

		if (minMag > 0)
			title += " Mag "+(float)minMag+"+";

		if (paleoProb)
			title += " (Convolved w/ ProbPaleoVisible)";


		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		setFontSizes(gp);
		gp.drawGraphPanel("Number of Jumps > "+(float)jumpDist+" km", "Rate", funcs, chars, title);

		File file = new File(dir, prefix);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.saveAsTXT(file.getAbsolutePath()+".txt");
	}

	private static String getJumpFilePrefix(String prefix, double minMag, boolean probPaleoVisible) {
		prefix += "_jumps";
		if (minMag > 0)
			prefix += "_m"+(float)minMag+"+";
		if (probPaleoVisible)
			prefix += "_prob_paleo";
		return prefix;
	}

	public static boolean doJumpPlotsExist(File dir, String prefix) {
		return doesJumpPlotExist(dir, prefix, 0d, true);
	}

	private static boolean doesJumpPlotExist(File dir, String prefix,
			double minMag, boolean probPaleoVisible) {
		return new File(dir, getJumpFilePrefix(prefix, minMag, probPaleoVisible)+".png").exists();
	}

	/**
	 * Writes Statewide, Northern and Southern CA  Nucleation MFD plots for the given InversionFaultSystemSolution
	 * @param invSol solution
	 * @param dir directory in which to write the MFD plots
	 * @param prefix plot file name prefix
	 * @return statewide MFDs
	 * @throws IOException
	 */
	public static List<? extends DiscretizedFunc> writeMFDPlots(InversionFaultSystemSolution invSol, File dir, String prefix) throws IOException {
		UCERF2_MFD_ConstraintFetcher ucerf2Fetch = new UCERF2_MFD_ConstraintFetcher();

		// TODO switch to derrived value
		// no cal
		writeMFDPlot(invSol, dir, prefix,invSol.getRupSet().getInversionTargetMFDs().getTotalTargetGR_NoCal(), invSol.getRupSet().getInversionTargetMFDs().noCalTargetSupraMFD,
				RELM_RegionUtils.getNoCalGriddedRegionInstance(), ucerf2Fetch);

		// so cal
		writeMFDPlot(invSol, dir, prefix,invSol.getRupSet().getInversionTargetMFDs().getTotalTargetGR_SoCal(), invSol.getRupSet().getInversionTargetMFDs().soCalTargetSupraMFD,
				RELM_RegionUtils.getSoCalGriddedRegionInstance(), ucerf2Fetch);
		
		// statewide
		return writeMFDPlot(invSol, dir, prefix, invSol.getRupSet().getInversionTargetMFDs().getTotalTargetGR(), invSol.getRupSet().getInversionTargetMFDs().getOnFaultSupraSeisMFD(),
						RELM_RegionUtils.getGriddedRegionInstance(), ucerf2Fetch);
	}

	public static List<? extends DiscretizedFunc> writeMFDPlot(InversionFaultSystemSolution invSol, File dir, String prefix, IncrementalMagFreqDist totalMFD,
			IncrementalMagFreqDist targetMFD, Region region, UCERF2_MFD_ConstraintFetcher ucerf2Fetch) throws IOException {
		PlotSpec spec = invSol.getMFDPlots(totalMFD, targetMFD, region, ucerf2Fetch);
		HeadlessGraphPanel gp = invSol.getHeadlessMFDPlot(spec, totalMFD);
		File file = new File(dir, getMFDPrefix(prefix, region));
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.saveAsTXT(file.getAbsolutePath()+".txt");
		
		return (List<? extends DiscretizedFunc>) spec.getPlotElems();
	}

	private static String getMFDPrefix(String prefix, Region region) {
		String regName = region.getName();
		if (regName == null || regName.isEmpty())
			regName = "Uknown";
		regName = regName.replaceAll(" ", "_");
		return prefix+"_MFD_"+regName;
	}

	public static boolean doMFDPlotsExist(File dir, String prefix) {
		return new File(dir, getMFDPrefix(prefix, RELM_RegionUtils.getGriddedRegionInstance())+".png").exists();
	}

	public static ArrayList<PaleoRateConstraint> getPaleoConstraints(FaultModels fm, FaultSystemRupSet rupSet) throws IOException {
		if (fm == FaultModels.FM2_1)
			return UCERF2_PaleoRateConstraintFetcher.getConstraints(rupSet.getFaultSectionDataList());
		return UCERF3_PaleoRateConstraintFetcher.getConstraints(rupSet.getFaultSectionDataList());
	}

	public static void writePaleoPlots(ArrayList<PaleoRateConstraint> paleoRateConstraints,
			List<AveSlipConstraint> aveSlipConstraints, InversionFaultSystemSolution sol,
			File dir, String prefix)
	throws IOException {
		HeadlessGraphPanel gp = PaleoFitPlotter.getHeadlessSegRateComparison(
				paleoRateConstraints, aveSlipConstraints, sol, true);

		File file = new File(dir, prefix+"_paleo_fit");
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.saveAsTXT(file.getAbsolutePath()+".txt");
	}

	public static boolean doPaleoPlotsExist(File dir, String prefix) {
		return new File(dir, prefix+"_paleo_fit.png").exists();
	}

	/**
	 * Writes segmentation plots for the San Andreas Fault
	 * 
	 * @param sol
	 * @param dir
	 * @param prefix
	 * @throws IOException
	 */
	public static void writeSAFSegPlots(InversionFaultSystemSolution sol, File dir, String prefix) throws IOException {
		writeSAFSegPlots(sol, sol.getRupSet().getFaultModel(), dir, prefix);
	}
	
	public static void writeSAFSegPlots(FaultSystemSolution sol, FaultModels fm, File dir, String prefix) throws IOException {
		List<Integer> parentSects = FaultSpecificSegmentationPlotGen.getSAFParents(fm);

		writeSAFSegPlot(sol, dir, prefix, parentSects, 0, false);
		writeSAFSegPlot(sol, dir, prefix, parentSects, 7, false);
		writeSAFSegPlot(sol, dir, prefix, parentSects, 7.5, false);

	}

	public static void writeSAFSegPlot(FaultSystemSolution sol, File dir, String prefix,
			List<Integer> parentSects, double minMag, boolean endsOnly) throws IOException {
		HeadlessGraphPanel gp = FaultSpecificSegmentationPlotGen.getSegmentationHeadlessGP(parentSects, sol, minMag, endsOnly);
		
		prefix = getSAFSegPrefix(prefix, minMag, endsOnly);

		File file = new File(dir, prefix);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.saveAsTXT(file.getAbsolutePath()+".txt");
	}

	private static String getSAFSegPrefix(String prefix, double minMag, boolean endsOnly) {
		prefix += "_saf_seg";

		if (minMag > 5)
			prefix += (float)minMag+"+";

		return prefix;
	}

	public static boolean doSAFSegPlotsExist(File dir, String prefix) {
		return new File(dir, getSAFSegPrefix(prefix, 7.5, false)+".png").exists();
	}

	private static ArrayList<ParentMomentRecord> getSectionMoments(InversionFaultSystemSolution sol) {
		HashMap<Integer, ParentMomentRecord> map = Maps.newHashMap();
		
		InversionFaultSystemRupSet rupSet = sol.getRupSet();

		for (int sectIndex=0; sectIndex<rupSet.getNumSections(); sectIndex++) {
			FaultSectionPrefData sect = rupSet.getFaultSectionData(sectIndex);
			int parent = sect.getParentSectionId();
			if (!map.containsKey(parent)) {
				String name = sect.getName();
				if (name.contains(", Subsection"))
					name = name.substring(0, name.indexOf(", Subsection"));
				map.put(parent, new ParentMomentRecord(parent, name, 0, 0));
			}
			ParentMomentRecord rec = map.get(parent);
			double targetMo = rupSet.getReducedMomentRate(sectIndex);
			double solSlip = sol.calcSlipRateForSect(sectIndex);
			double solMo = FaultMomentCalc.getMoment(rupSet.getAreaForSection(sectIndex), solSlip);
			if (!Double.isNaN(targetMo))
				rec.targetMoment += targetMo;
			if (!Double.isNaN(solMo))
				rec.solutionMoment += solMo;
		}

		ArrayList<ParentMomentRecord> recs =
			new ArrayList<CommandLineInversionRunner.ParentMomentRecord>(map.values());
		Collections.sort(recs);
		Collections.reverse(recs);
		return recs;
	}

	private static class ParentMomentRecord implements Comparable<ParentMomentRecord> {
		int parentID;
		String name;
		double targetMoment;
		double solutionMoment;
		public ParentMomentRecord(int parentID, String name,
				double targetMoment, double solutionMoment) {
			super();
			this.parentID = parentID;
			this.name = name;
			this.targetMoment = targetMoment;
			this.solutionMoment = solutionMoment;
		}
		public double getDiff() {
			return targetMoment - solutionMoment;
		}
		@Override
		public int compareTo(ParentMomentRecord o) {
			return Double.compare(Math.abs(getDiff()), Math.abs(o.getDiff()));
		}
	}

	/**
	 * Writes incremental and cumulative participation and nucleation MFDs for each parent fault section.
	 * @param sol
	 * @param dir
	 * @throws IOException
	 */
	public static void writeParentSectionMFDPlots(FaultSystemSolution sol, File dir) throws IOException {
		Map<Integer, String> parentSects = Maps.newHashMap();
		
		if (!dir.exists())
			dir.mkdir();
		
		File particIncrSubDir = new File(dir, "participation_incremental");
		if (!particIncrSubDir.exists())
			particIncrSubDir.mkdir();
		File particCmlSubDir = new File(dir, "participation_cumulative");
		if (!particCmlSubDir.exists())
			particCmlSubDir.mkdir();
		File nuclIncrSubDir = new File(dir, "nucleation_incremental");
		if (!nuclIncrSubDir.exists())
			nuclIncrSubDir.mkdir();
		File nuclCmlSubDir = new File(dir, "nucleation_cumulative");
		if (!nuclCmlSubDir.exists())
			nuclCmlSubDir.mkdir();

		for (FaultSectionPrefData sect : sol.getRupSet().getFaultSectionDataList())
			if (!parentSects.containsKey(sect.getParentSectionId()))
				parentSects.put(sect.getParentSectionId(), sect.getParentSectionName());

		// MFD extents
		double minMag = 5.05;
		double maxMag = 9.05;
		int numMag = (int)((maxMag - minMag) / 0.1d) + 1;
		
		CSVFile<String> sdomOverMeanIncrParticCSV = null;
		CSVFile<String> stdDevOverMeanCmlParticCSV = null;
		CSVFile<String> meanIncrParticCSV = null;
		CSVFile<String> meanCmlParticCSV = null;
		
		boolean isAVG = sol instanceof AverageFaultSystemSolution;

		for (int parentSectionID : parentSects.keySet()) {
			String parentSectName = parentSects.get(parentSectionID);

			List<EvenlyDiscretizedFunc> nuclMFDs = Lists.newArrayList();
			List<EvenlyDiscretizedFunc> partMFDs = Lists.newArrayList();
			
			// get incremental MFDs
			SummedMagFreqDist nuclMFD = sol.calcNucleationMFD_forParentSect(parentSectionID, minMag, maxMag, numMag);
			nuclMFDs.add(nuclMFD);
			IncrementalMagFreqDist partMFD = sol.calcParticipationMFD_forParentSect(parentSectionID, minMag, maxMag, numMag);
			partMFDs.add(partMFD);
			
			// make cumulative MFDs with offsets
			List<EvenlyDiscretizedFunc> nuclCmlMFDs = Lists.newArrayList();
			nuclCmlMFDs.add(nuclMFD.getCumRateDistWithOffset());
			List<EvenlyDiscretizedFunc> partCmlMFDs = Lists.newArrayList();
			EvenlyDiscretizedFunc partCmlMFD = partMFD.getCumRateDistWithOffset();
			partCmlMFDs.add(partCmlMFD);
			
			if (isAVG) {
				// average fault system solution stuff (Std Dev, SDOM, Min/Max)
				AverageFaultSystemSolution avgSol = (AverageFaultSystemSolution)sol;
				double[] sdom_over_means = calcAveSolMFDs(avgSol, true, false, true, partMFDs, parentSectionID, minMag, maxMag, numMag);
				calcAveSolMFDs(avgSol, false, false, false, nuclMFDs, parentSectionID, minMag, maxMag, numMag);
				double[] std_dev_over_means = calcAveSolMFDs(avgSol, true, true, false, partCmlMFDs, parentSectionID, minMag, maxMag, numMag);
				calcAveSolMFDs(avgSol, false, true, false, nuclCmlMFDs, parentSectionID, minMag, maxMag, numMag);
				
				if (sdomOverMeanIncrParticCSV == null) {
					sdomOverMeanIncrParticCSV = new CSVFile<String>(true);
					meanIncrParticCSV = new CSVFile<String>(true);
					
					List<String> header = Lists.newArrayList("Parent ID", "Parent Name");
					for (int i=0; i<numMag; i++)
						header.add((float)nuclMFD.getX(i)+"");
					sdomOverMeanIncrParticCSV.addLine(header);
					meanIncrParticCSV.addLine(header);
					
					stdDevOverMeanCmlParticCSV = new CSVFile<String>(true);
					meanCmlParticCSV = new CSVFile<String>(true);
					
					header = Lists.newArrayList("Parent ID", "Parent Name");
					for (int i=0; i<numMag; i++)
						header.add((float)partCmlMFD.getX(i)+"");
					stdDevOverMeanCmlParticCSV.addLine(header);
					meanCmlParticCSV.addLine(header);
				}
				
				List<String> line = Lists.newArrayList();
				line.add(parentSectionID+"");
				line.add(parentSectName);
				
				for (int i=0; i<numMag; i++) {
					line.add(sdom_over_means[i]+"");
				}
				sdomOverMeanIncrParticCSV.addLine(line);
				
				line = Lists.newArrayList();
				line.add(parentSectionID+"");
				line.add(parentSectName);
				
				for (int i=0; i<numMag; i++) {
					line.add(partMFD.getY(i)+"");
				}
				meanIncrParticCSV.addLine(line);
				
				line = Lists.newArrayList();
				line.add(parentSectionID+"");
				line.add(parentSectName);
				
				for (int i=0; i<numMag; i++) {
					line.add(std_dev_over_means[i]+"");
				}
				stdDevOverMeanCmlParticCSV.addLine(line);
				
				line = Lists.newArrayList();
				line.add(parentSectionID+"");
				line.add(parentSectName);
				
				for (int i=0; i<numMag; i++) {
					line.add(partCmlMFD.getY(i)+"");
				}
				meanCmlParticCSV.addLine(line);
			}
			
			// these are UCERF2 MFDs for comparison
			ArrayList<IncrementalMagFreqDist> ucerf2NuclMFDs =
					UCERF2_Section_MFDsCalc.getMeanMinAndMaxMFD(parentSectionID, false, false);
			ArrayList<IncrementalMagFreqDist> ucerf2NuclCmlMFDs =
					UCERF2_Section_MFDsCalc.getMeanMinAndMaxMFD(parentSectionID, false, true);
			ArrayList<IncrementalMagFreqDist> ucerf2PartMFDs =
					UCERF2_Section_MFDsCalc.getMeanMinAndMaxMFD(parentSectionID, true, false);
			ArrayList<IncrementalMagFreqDist> ucerf2PartCmlMFDs =
					UCERF2_Section_MFDsCalc.getMeanMinAndMaxMFD(parentSectionID, true, true);
			
			// if it's an IFSS, we can add sub seis MFDs
			List<EvenlyDiscretizedFunc> subSeismoMFDs;
			List<EvenlyDiscretizedFunc> subSeismoCmlMFDs;
			List<EvenlyDiscretizedFunc> subPlusSupraSeismoNuclMFDs;
			List<EvenlyDiscretizedFunc> subPlusSupraSeismoNuclCmlMFDs;
			List<EvenlyDiscretizedFunc> subPlusSupraSeismoParticMFDs;
			List<EvenlyDiscretizedFunc> subPlusSupraSeismoParticCmlMFDs;
			if (sol instanceof InversionFaultSystemSolution) {
				InversionFaultSystemSolution invSol = (InversionFaultSystemSolution)sol;
				subSeismoMFDs = Lists.newArrayList();
				subSeismoCmlMFDs = Lists.newArrayList();
				SummedMagFreqDist subSeismoMFD = invSol.getFinalSubSeismoOnFaultMFDForParent(parentSectionID);
				subSeismoMFDs.add(subSeismoMFD);
				subSeismoCmlMFDs.add(subSeismoMFD.getCumRateDistWithOffset());
				
				// nucleation sum
				SummedMagFreqDist subPlusSupraSeismoNuclMFD = new SummedMagFreqDist(
						subSeismoMFD.getMinX(), subSeismoMFD.size(), subSeismoMFD.getDelta());
				subPlusSupraSeismoNuclMFD.addIncrementalMagFreqDist(subSeismoMFD);
				subPlusSupraSeismoNuclMFD.addIncrementalMagFreqDist(resizeToDimensions(
						nuclMFD, subSeismoMFD.getMinX(), subSeismoMFD.size(), subSeismoMFD.getDelta()));
				EvenlyDiscretizedFunc subPlusSupraSeismoNuclCmlMFD = subPlusSupraSeismoNuclMFD.getCumRateDistWithOffset();
				subPlusSupraSeismoNuclMFDs = Lists.newArrayList();
				subPlusSupraSeismoNuclCmlMFDs = Lists.newArrayList();
				subPlusSupraSeismoNuclMFDs.add(subPlusSupraSeismoNuclMFD);
				subPlusSupraSeismoNuclCmlMFDs.add(subPlusSupraSeismoNuclCmlMFD);
				
				// participation sum
				SummedMagFreqDist subPlusSupraSeismoParticMFD = new SummedMagFreqDist(
						subSeismoMFD.getMinX(), subSeismoMFD.size(), subSeismoMFD.getDelta());
				subPlusSupraSeismoParticMFD.addIncrementalMagFreqDist(subSeismoMFD);
				subPlusSupraSeismoParticMFD.addIncrementalMagFreqDist(resizeToDimensions(
						partMFD, subSeismoMFD.getMinX(), subSeismoMFD.size(), subSeismoMFD.getDelta()));
				EvenlyDiscretizedFunc subPlusSupraSeismoParticCmlMFD = subPlusSupraSeismoParticMFD.getCumRateDistWithOffset();
				subPlusSupraSeismoParticMFDs = Lists.newArrayList();
				subPlusSupraSeismoParticCmlMFDs = Lists.newArrayList();
				subPlusSupraSeismoParticMFDs.add(subPlusSupraSeismoParticMFD);
				subPlusSupraSeismoParticCmlMFDs.add(subPlusSupraSeismoParticCmlMFD);
			} else {
				subSeismoMFDs = null;
				subSeismoCmlMFDs = null;
				subPlusSupraSeismoNuclMFDs = null;
				subPlusSupraSeismoNuclCmlMFDs = null;
				subPlusSupraSeismoParticMFDs = null;
				subPlusSupraSeismoParticCmlMFDs = null;
			}
			
//			public static void writeParentSectMFDPlot(File dir,
//					List<EvenlyDiscretizedFunc> supraSeismoMFDs,
//					List<EvenlyDiscretizedFunc> subSeismoMFDs,
//					List<EvenlyDiscretizedFunc> subPlusSupraSeismoMFDs,
//					List<EvenlyDiscretizedFunc> ucerf2MFDs,
//					boolean avgColoring, int id, String name, boolean nucleation) throws IOException {

			// write out all of the plots
			
			// nucleation
			// incremental
			writeParentSectMFDPlot(nuclIncrSubDir, nuclMFDs, subSeismoMFDs, subPlusSupraSeismoNuclMFDs, ucerf2NuclMFDs,
					isAVG, parentSectionID, parentSectName, true, false);
			// cumulative
			writeParentSectMFDPlot(nuclCmlSubDir, nuclCmlMFDs, subSeismoCmlMFDs, subPlusSupraSeismoNuclCmlMFDs, ucerf2NuclCmlMFDs,
					isAVG, parentSectionID, parentSectName, true, true);
			// participation
			// incremental
			writeParentSectMFDPlot(particIncrSubDir, partMFDs, subSeismoMFDs, subPlusSupraSeismoParticMFDs, ucerf2PartMFDs,
					isAVG, parentSectionID, parentSectName, false, false);
			// cumulative
			writeParentSectMFDPlot(particCmlSubDir, partCmlMFDs, subSeismoCmlMFDs, subPlusSupraSeismoParticCmlMFDs, ucerf2PartCmlMFDs,
					isAVG, parentSectionID, parentSectName, false, true);
		}
		
		Comparator<String> csvCompare = new Comparator<String>() {
			
			@Override
			public int compare(String o1, String o2) {
				return o1.compareTo(o2);
			}
		};
		
		if (sdomOverMeanIncrParticCSV != null) {
			sdomOverMeanIncrParticCSV.sort(1, 1, csvCompare);
			
			sdomOverMeanIncrParticCSV.writeToFile(new File(dir, "participation_sdom_over_means.csv"));
			
			meanIncrParticCSV.sort(1, 1, csvCompare);
			
			meanIncrParticCSV.writeToFile(new File(dir, "participation_means.csv"));
		}
		
		if (stdDevOverMeanCmlParticCSV != null) {
			stdDevOverMeanCmlParticCSV.sort(1, 1, csvCompare);
			
			stdDevOverMeanCmlParticCSV.writeToFile(new File(dir, "participation_cumulative_std_dev_over_means.csv"));
			
			meanCmlParticCSV.sort(1, 1, csvCompare);
			
			meanCmlParticCSV.writeToFile(new File(dir, "participation_cumulative_means.csv"));
		}
	}
	
	private static IncrementalMagFreqDist resizeToDimensions(
			IncrementalMagFreqDist mfd, double min, int num, double delta) {
		if (mfd.getMinX() == min && mfd.size() == num && mfd.getDelta() == delta)
			return mfd;
		IncrementalMagFreqDist resized = new IncrementalMagFreqDist(min, num, delta);
		
		for (int i=0; i<mfd.size(); i++)
			if (mfd.getY(i) > 0)
				resized.set(mfd.get(i));
		
		return resized;
	}
	
	/**
	 * Calculates MFDs for average solutions, adding them to the MFD list. Also returns a list of SDOM/mean values
	 * for each mag bin.
	 * 
	 * @param avgSol
	 * @param participation
	 * @param mfds
	 * @param parentSectionID
	 * @param minMag
	 * @param maxMag
	 * @param numMag
	 * @returnist of SDOM/mean values for each mag bin.
	 */
	private static double[] calcAveSolMFDs(AverageFaultSystemSolution avgSol, boolean participation,
			boolean cumulative, boolean ret_sdom,
			List<EvenlyDiscretizedFunc> mfds, int parentSectionID, double minMag, double maxMag, int numMag) {
		EvenlyDiscretizedFunc meanMFD = mfds.get(0);
		double[] means = new double[numMag];
		for (int i=0; i<numMag; i++)
			means[i] = meanMFD.getY(i);
		
		double[] sdom_over_means = new double[numMag];
		
		int numSols = avgSol.getNumSolutions();
		double mfdVals[][] = new double[numMag][numSols];
		int cnt = 0;
		for (FaultSystemSolution sol : avgSol) {
			IncrementalMagFreqDist mfd;
			if (participation)
				mfd = sol.calcParticipationMFD_forParentSect(parentSectionID, minMag, maxMag, numMag);
			else
				mfd = sol.calcNucleationMFD_forParentSect(parentSectionID, minMag, maxMag, numMag);
			EvenlyDiscretizedFunc theMFD;
			if (cumulative)
				theMFD = mfd.getCumRateDistWithOffset();
			else
				theMFD = mfd;
			for (int i=0; i<numMag; i++) {
				mfdVals[i][cnt] = theMFD.getY(i);
			}
			cnt++;
		}
		
		minMag = meanMFD.getMinX();
		maxMag = meanMFD.getMaxX();
		
		IncrementalMagFreqDist meanPlusSDOM = new IncrementalMagFreqDist(minMag, maxMag, numMag);
		IncrementalMagFreqDist meanMinusSDOM = new IncrementalMagFreqDist(minMag, maxMag, numMag);
		IncrementalMagFreqDist meanPlusStdDev = new IncrementalMagFreqDist(minMag, maxMag, numMag);
		IncrementalMagFreqDist meanMinusStdDev = new IncrementalMagFreqDist(minMag, maxMag, numMag);
		IncrementalMagFreqDist minFunc = new IncrementalMagFreqDist(minMag, maxMag, numMag);
		IncrementalMagFreqDist maxFunc = new IncrementalMagFreqDist(minMag, maxMag, numMag);
		for (int i=0; i<numMag; i++) {
			double mean = means[i];
			if (mean == 0)
				continue;
			double stdDev = Math.sqrt(StatUtils.variance(mfdVals[i], mean));
			double sdom = stdDev / Math.sqrt(numSols);
			double min = StatUtils.min(mfdVals[i]);
			double max = StatUtils.max(mfdVals[i]);
			
			meanPlusSDOM.set(i, mean + sdom);
			meanMinusSDOM.set(i, mean - sdom);
			meanPlusStdDev.set(i, mean + stdDev);
			meanMinusStdDev.set(i, mean - stdDev);
			minFunc.set(i, min);
			maxFunc.set(i, max);
			
			if (ret_sdom)
				sdom_over_means[i] = sdom / mean;
			else
				sdom_over_means[i] = stdDev / mean;
		}
		
		mfds.add(meanPlusSDOM);
		mfds.add(meanMinusSDOM);
		mfds.add(meanPlusStdDev);
		mfds.add(meanMinusStdDev);
		mfds.add(minFunc);
		mfds.add(maxFunc);
		
		return sdom_over_means;
	}
	
	private static DiscretizedFunc getRIFunc(EvenlyDiscretizedFunc cmlFunc, String name) {
		ArbitrarilyDiscretizedFunc riCmlFunc = new ArbitrarilyDiscretizedFunc();
		riCmlFunc.setName(name);
		String info = cmlFunc.getInfo();
		String newInfo = " ";
		if (info != null && info.length()>1) {
			newInfo = null;
			for (String line : Splitter.on("\n").split(info)) {
				if (line.contains("RI")) {
					if (newInfo == null)
						newInfo = "";
					else
						newInfo += "\n";
					newInfo += line;
				}
			}
			if (newInfo == null)
				newInfo = " ";
		}
		riCmlFunc.setInfo(newInfo);
		for (int i=0; i<cmlFunc.size(); i++) {
			double y = cmlFunc.getY(i);
			if (y > 0)
				riCmlFunc.set(cmlFunc.getX(i), 1d/y);
		}
		if (riCmlFunc.size() == 0) {
			for (int i=0; i<cmlFunc.size(); i++) {
				riCmlFunc.set(cmlFunc.getX(i), 0d);
			}
		}
		return riCmlFunc;
	}

	public static void writeParentSectMFDPlot(File dir,
			List<? extends EvenlyDiscretizedFunc> supraSeismoMFDs,
			List<? extends EvenlyDiscretizedFunc> subSeismoMFDs,
			List<? extends EvenlyDiscretizedFunc> subPlusSupraSeismoMFDs,
			List<? extends EvenlyDiscretizedFunc> ucerf2MFDs,
			boolean avgColoring, int id, String name, boolean nucleation, boolean cumulative) throws IOException {
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		setFontSizes(gp);
		gp.setYLog(true);
		gp.setRenderingOrder(DatasetRenderingOrder.FORWARD);

		ArrayList<DiscretizedFunc> funcs = Lists.newArrayList();
		ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();
		
		EvenlyDiscretizedFunc mfd;
		if (supraSeismoMFDs.size() ==  1 || avgColoring) {
			mfd = supraSeismoMFDs.get(0);
			mfd.setName("Incremental MFD");
			funcs.add(mfd);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
			if (subSeismoMFDs != null) {
				funcs.add(subSeismoMFDs.get(0));
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.GRAY));
				funcs.add(subPlusSupraSeismoMFDs.get(0));
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));
			}
			
			if (avgColoring) {
				// this is an average fault system solution
				
				// mean +/- SDOM
				funcs.add(supraSeismoMFDs.get(1));
				funcs.add(supraSeismoMFDs.get(2));
				PlotCurveCharacterstics pchar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE);
				chars.add(pchar);
				chars.add(pchar);
				
				// mean +/- Std Dev
				funcs.add(supraSeismoMFDs.get(3));
				funcs.add(supraSeismoMFDs.get(4));
				pchar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GREEN);
				chars.add(pchar);
				chars.add(pchar);
				
				// min/max
				funcs.add(supraSeismoMFDs.get(5));
				funcs.add(supraSeismoMFDs.get(6));
				pchar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY);
				chars.add(pchar);
				chars.add(pchar);
			}
		} else {
			int numFractiles = supraSeismoMFDs.size()-3;
			mfd = supraSeismoMFDs.get(supraSeismoMFDs.size()-3);
			funcs.addAll(supraSeismoMFDs);
			// used to be: new Color(0, 126, 255)
			chars.addAll(CompoundFSSPlots.getFractileChars(Color.BLUE, Color.MAGENTA, numFractiles));
			numFractiles = subSeismoMFDs.size()-3;
			funcs.addAll(subSeismoMFDs);
			chars.addAll(CompoundFSSPlots.getFractileChars(Color.CYAN, Color.MAGENTA, numFractiles));
			funcs.addAll(subPlusSupraSeismoMFDs);
			chars.addAll(CompoundFSSPlots.getFractileChars(Color.BLACK, Color.MAGENTA, numFractiles));
		}
		
		if (ucerf2MFDs != null) {
//			Color lightRed = new Color (255, 128, 128);
			
			for (EvenlyDiscretizedFunc ucerf2MFD : ucerf2MFDs)
				ucerf2MFD.setName("UCERF2 "+ucerf2MFD.getName());
			EvenlyDiscretizedFunc meanMFD = ucerf2MFDs.get(0);
			funcs.add(meanMFD);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.RED));
			EvenlyDiscretizedFunc minMFD = ucerf2MFDs.get(1);
			funcs.add(minMFD);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
			EvenlyDiscretizedFunc maxMFD = ucerf2MFDs.get(2);
			funcs.add(maxMFD);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));			
		}

		double minX = mfd.getMinX();
		if (minX < 5)
			minX = 5;
		gp.setUserBounds(minX, mfd.getMaxX(),
				1e-10, 1e-1);
		String yAxisLabel;
		
		String fname = name.replaceAll("\\W+", "_");
		
		if (cumulative)
			fname += "_cumulative";
		
		if (nucleation) {
			yAxisLabel = "Nucleation Rate";
			fname += "_nucleation";
		} else {
			yAxisLabel = "Participation Rate";
			fname += "_participation";
		}
		String title = name;
		yAxisLabel += " (per yr)";
		
		gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
		gp.drawGraphPanel("Magnitude", yAxisLabel, funcs, chars, title);
		
		File file = new File(dir, fname);
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.saveAsTXT(file.getAbsolutePath()+".txt");
		File smallDir = new File(dir.getParentFile(), "small_MFD_plots");
		if (!smallDir.exists())
			smallDir.mkdir();
		file = new File(smallDir, fname+"_small");
		gp.getChartPanel().setSize(500, 400);
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.getChartPanel().setSize(1000, 800);
	}

	public static void writePaleoCorrelationPlots(
			InversionFaultSystemSolution sol, File dir, PaleoProbabilityModel paleoProb) throws IOException {
		Map<String, Table<String, String, PaleoSiteCorrelationData>> tables =
				PaleoSiteCorrelationData.loadPaleoCorrelationData(sol);
		
		Map<String, PlotSpec> specMap = Maps.newHashMap();
		
		for (String faultName : tables.keySet())
			specMap.put(faultName, PaleoSiteCorrelationData.getCorrelationPlotSpec(
					faultName, tables.get(faultName), sol, paleoProb));
		
		writePaleoCorrelationPlots(dir, specMap);
	}
	
	public static void writePaleoCorrelationPlots(
			File dir, Map<String, PlotSpec> specMap) throws IOException {
		
		
		if (!dir.exists())
			dir.mkdir();
		
		for (String faultName : specMap.keySet()) {
			String fname = faultName.replaceAll("\\W+", "_");
			
			PlotSpec spec = specMap.get(faultName);
			
			double maxX = 0;
			for (DiscretizedFunc func : spec.getPlotFunctionsOnly()) {
				double myMaxX = func.getMaxX();
				if (myMaxX > maxX)
					maxX = myMaxX;
			}
			
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			setFontSizes(gp);
			gp.setUserBounds(0d, maxX, -0.05d, 1.05d);
			
			gp.drawGraphPanel(spec.getXAxisLabel(), spec.getYAxisLabel(),
					spec.getPlotElems(), spec.getChars(), spec.getTitle());
			
			File file = new File(dir, fname);
			gp.getChartPanel().setSize(1000, 800);
			gp.saveAsPDF(file.getAbsolutePath()+".pdf");
			gp.saveAsPNG(file.getAbsolutePath()+".png");
			gp.saveAsTXT(file.getAbsolutePath()+".txt");
		}
	}

	public static void writePaleoFaultPlots(
			List<PaleoRateConstraint> paleoRateConstraints,
			List<AveSlipConstraint> aveSlipConstraints,
			Map<String, List<Integer>> namedFaultsMap, SlipEnabledSolution sol, File dir)
					throws IOException {
		Map<String, PlotSpec[]> specs = PaleoFitPlotter.getFaultSpecificPaleoPlotSpec(
				paleoRateConstraints, aveSlipConstraints, namedFaultsMap, sol);
		
		writePaleoFaultPlots(specs, null, dir);
	}
	
	public static void writePaleoFaultPlots(
			Map<String, PlotSpec[]> specs, String prefix, File dir)
					throws IOException {
		
		String[] fname_adds = { "paleo_rate", "slip_rate", "ave_event_slip" };
		
		if (!dir.exists())
			dir.mkdir();
		
		for (String faultName : specs.keySet()) {
			String fname = faultName.replaceAll("\\W+", "_");
			
			if (prefix != null && !prefix.isEmpty())
				fname = prefix+"_"+fname;
			
			PlotSpec[] specArray = specs.get(faultName);
			
			double xMin = Double.POSITIVE_INFINITY;
			double xMax = Double.NEGATIVE_INFINITY;
			for (DiscretizedFunc func : specArray[2].getPlotFunctionsOnly()) {
				double myXMin = func.getMinX();
				double myXMax = func.getMaxX();
				if (myXMin < xMin)
					xMin = myXMin;
				if (myXMax > xMax)
					xMax = myXMax;
			}
			
			Range xRange = null;
			Range slipYRange = null;
			Range paleoYRange = null;
			
			for (int i=0; i<specArray.length; i++) {
				String fname_add = fname_adds[i];
				PlotSpec spec = specArray[i];
				HeadlessGraphPanel gp = new HeadlessGraphPanel();
				setFontSizes(gp);
				gp.setBackgroundColor(Color.WHITE);
				if (i != 2)
					gp.setYLog(true);
				if (xMax > 0)
					// only when latitudeX, this is a kludgy way of detecting this for CA
					gp.setxAxisInverted(true);
				// now determine y scale
				List<Double> yValsList = Lists.newArrayList();
				List<Double> confYValsList = Lists.newArrayList();
				for (DiscretizedFunc func : spec.getPlotFunctionsOnly()) {
					if (func.getName().contains("separator"))
						continue;
					if (func.getName().contains("Confidence")) {
						for (Point2D pt : func) {
							confYValsList.add(pt.getY());
						}
					}
					for (Point2D pt : func) {
						yValsList.add(pt.getY());
					}
				}
				// now choose x/y such that most is inside
				Collections.sort(yValsList);
				Collections.sort(confYValsList);
				double yMax = yValsList.get(yValsList.size()-1);
				double yMin = yValsList.get((int)((double)yValsList.size()*0.005));
				// make sure confidence vals are shown completely
				// use index 1 (2nd one) to avoid a single outlier
				if (confYValsList.size() > 0 && yMin > confYValsList.get(1))
					yMin = confYValsList.get(1);
				double origYMax = yMax;
				double origYMin = yMin;
				if (!gp.getYLog()) {
					// buffer by just a bit in linear space
					yMax = yMax + yMax*0.1;
					yMin = yMin - yMin*0.1;
				} else {
					// buffer by just a bit in log space
					yMax = Math.log10(yMax);
					yMin = Math.log10(yMin);
					yMax += Math.abs(yMax * 0.1);
					yMin -= Math.abs(yMin * 0.1);
					yMax = Math.pow(10, yMax);
					yMin = Math.pow(10, yMin);
				}
				System.out.println(faultName);
				System.out.println("Total Y Range: "+yValsList.get(0)+"=>"+yValsList.get(yValsList.size()-1));
				System.out.println("Orig Y Range: "+origYMin+"=>"+origYMax);
				System.out.println("X Range: "+xMin+"=>"+xMax+", Y Range: "+yMin+"=>"+yMax);
				Preconditions.checkState(yMax >= origYMax);
				Preconditions.checkState(yMin <= origYMin);
//				if (i == 1)
//					// just slip
//					gp.setUserBounds(xMin, xMax, 1e-1, 5e1);
//				else if (i == 3)
//					// just ave slip data
//					gp.setUserBounds(xMin, xMax, 0, 10d);
//				else
//					// combined or just paleo
//					gp.setUserBounds(xMin, xMax, 1e-5, 1e0);
				gp.setUserBounds(xMin, xMax, yMin, yMax);
				
				// set y for text annotations
				if (spec.getPlotAnnotations() != null) {
					List<XYAnnotation> newAnnotations = Lists.newArrayList();
					for (XYAnnotation a : spec.getPlotAnnotations()) {
						if (a instanceof XYTextAnnotation) {
							try {
								XYTextAnnotation t = (XYTextAnnotation)((XYTextAnnotation)a).clone();
								t.setY(yMax);
								newAnnotations.add(t);
							} catch (CloneNotSupportedException e) {
								ExceptionUtils.throwAsRuntimeException(e);
							}
						} else {
							newAnnotations.add(a);
						}
						spec.setPlotAnnotations(newAnnotations);
					}
				}
				
				gp.drawGraphPanel(spec);
				
				File file = new File(dir, fname+"_"+fname_add);
				gp.getChartPanel().setSize(1000, 500);
				gp.saveAsPDF(file.getAbsolutePath()+".pdf");
				gp.saveAsPNG(file.getAbsolutePath()+".png");
				gp.saveAsTXT(file.getAbsolutePath()+".txt");
				
				if (i == 0) {
					xRange = new Range(xMin, xMax);
					paleoYRange = new Range(yMin, yMax);
				} else if (i == 1) {
					slipYRange = new Range(yMin, yMax);
				}
			}
			// now make combined plot
			PlotSpec slipSpec = specArray[1];
			PlotSpec paleoSpec = specArray[0];
			paleoSpec.setPlotAnnotations(null);
			List<PlotSpec> combinedSpecs = Lists.newArrayList(slipSpec, paleoSpec);
			List<Range> xRanges = Lists.newArrayList(xRange);
			List<Range> yRanges = Lists.newArrayList(slipYRange, paleoYRange);
			
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			setFontSizes(gp);
			gp.setBackgroundColor(Color.WHITE);
			if (xMax > 0)
				// only when latitudeX, this is a kludgy way of detecting this for CA
				gp.setxAxisInverted(true);
			gp.drawGraphPanel(combinedSpecs, false, true, xRanges, yRanges);
			
			File file = new File(dir, fname+"_combined");
			gp.getChartPanel().setSize(1000, 800);
			gp.saveAsPDF(file.getAbsolutePath()+".pdf");
			gp.saveAsPNG(file.getAbsolutePath()+".png");
			gp.saveAsTXT(file.getAbsolutePath()+".txt");
		}
	}
	
	public static void writeRupPairingSmoothnessPlot(FaultSystemSolution sol, String prefix, File dir)
			throws IOException {
		List<IDPairing> pairings = InversionInputGenerator.getRupSmoothingPairings(sol.getRupSet());
		
		double[] diffs = new double[pairings.size()];
		double[] fracts = new double[pairings.size()];
		
		for (int i=0; i<diffs.length; i++) {
			IDPairing pair = pairings.get(i);
			double r1 = sol.getRateForRup(pair.getID1());
			double r2 = sol.getRateForRup(pair.getID2());
			double diff = Math.abs(r1 - r2);
			double meanRate = 0.5*(r1+r2);
			diffs[i] = diff;
			if (meanRate > 0)
				fracts[i] = diff / meanRate;
		}
		
		// now sorted ascending
		Arrays.sort(diffs);
		Arrays.sort(fracts);
		
		EvenlyDiscretizedFunc diffVsRankFunc = new EvenlyDiscretizedFunc(0d, pairings.size(), 1d);
		EvenlyDiscretizedFunc fractVsRankFunc = new EvenlyDiscretizedFunc(0d, pairings.size(), 1d);
		
		// we want to plot descending
		int index = 0;
		for (int i=diffs.length; --i>=0;) {
			diffVsRankFunc.set(index, diffs[i]);
			fractVsRankFunc.set(index++, fracts[i]);
		}
		
		ArrayList<DiscretizedFunc> funcs = Lists.newArrayList();
		funcs.add(diffVsRankFunc);
		ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		setFontSizes(gp);
		gp.setBackgroundColor(Color.WHITE);
		gp.setYLog(true);
		gp.drawGraphPanel("Rank", "abs(rate1 - rate2)", funcs, chars,
				"Rupture Pairing Smoothness");
		File file = new File(dir, prefix+"_rup_smooth_pairings");
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		
		funcs.clear();
		funcs.add(fractVsRankFunc);
		
		gp = new HeadlessGraphPanel();
		setFontSizes(gp);
		gp.setBackgroundColor(Color.WHITE);
		gp.setYLog(true);
		gp.drawGraphPanel("Rank", "abs(rate1 - rate2)/(mean rate)", funcs, chars,
				"Rupture Pairing Smoothness Fractions");
		file = new File(dir, prefix+"_rup_smooth_pairings_fract");
		gp.getChartPanel().setSize(1000, 800);
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
	}
	
	public static InversionFaultSystemRupSet getUCERF2RupsOnly(InversionFaultSystemRupSet rupSet) {
		List<double[]> ucerf2_magsAndRates = InversionConfiguration.getUCERF2MagsAndrates(rupSet);
		
		int newNumRups = 0;
		for (double[] u2Vals : ucerf2_magsAndRates)
			if (u2Vals != null)
				newNumRups++;
		
		List<List<Integer>> sectionForRups = Lists.newArrayList();
		double[] mags = new double[newNumRups];
		double[] rakes = new double[newNumRups];
		double[] rupAreas = new double[newNumRups];
		double[] rupLengths = new double[newNumRups];
		double[] rupAveSlips = new double[newNumRups];
		
		int cnt = 0;
		for (int r=0; r<ucerf2_magsAndRates.size(); r++) {
			if (ucerf2_magsAndRates.get(r) == null)
				continue;
			sectionForRups.add(rupSet.getSectionsIndicesForRup(r));
			mags[cnt] = rupSet.getMagForRup(r);
			rakes[cnt] = rupSet.getAveRakeForRup(r);
			rupAreas[cnt] = rupSet.getAreaForRup(r);
			rupLengths[cnt] = rupSet.getLengthForRup(r);
			rupAveSlips[cnt] = rupSet.getAveSlipForRup(r);
			cnt++;
		}
		
		FaultSystemRupSet subset = new FaultSystemRupSet(rupSet.getFaultSectionDataList(), rupSet.getSlipRateForAllSections(),
				rupSet.getSlipRateStdDevForAllSections(), rupSet.getAreaForAllSections(),
				sectionForRups, mags, rakes, rupAreas, rupLengths, rupSet.getInfoString());
		
		return new InversionFaultSystemRupSet(subset, rupSet.getLogicTreeBranch(), rupSet.getLaughTestFilter(), rupAveSlips,
				null, null, null);
	}
	
	public static InversionFaultSystemRupSet getFilteredRupsOnly(InversionFaultSystemRupSet rupSet, File file) throws IOException {
		Preconditions.checkArgument(file.exists());
		
		List<Integer> rupIndexes = Lists.newArrayList();
		
		FileWriter mappingFW = new FileWriter(new File(file.getAbsolutePath()+".mappings"));
		mappingFW.write("# OrigID\tMappedID\n");
		
		for (String line : Files.readLines(file, Charset.defaultCharset())) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#"))
				continue;
			int rupIndex = Integer.parseInt(line);
			mappingFW.write(rupIndex+"\t"+rupIndexes.size()+"\n");
			rupIndexes.add(rupIndex);
		}
		mappingFW.close();
		
		List<List<Integer>> sectionForRups = Lists.newArrayList();
		double[] mags = new double[rupIndexes.size()];
		double[] rakes = new double[rupIndexes.size()];
		double[] rupAreas = new double[rupIndexes.size()];
		double[] rupLengths = new double[rupIndexes.size()];
		double[] rupAveSlips = new double[rupIndexes.size()];
		
		for (int i=0; i<rupIndexes.size(); i++) {
			int rupIndex = rupIndexes.get(i);
			sectionForRups.add(rupSet.getSectionsIndicesForRup(rupIndex));
			mags[i] = rupSet.getMagForRup(rupIndex);
			rakes[i] = rupSet.getAveRakeForRup(rupIndex);
			rupAreas[i] = rupSet.getAreaForRup(rupIndex);
			rupLengths[i] = rupSet.getLengthForRup(rupIndex);
			rupAveSlips[i] = rupSet.getAveSlipForRup(rupIndex);
		}
		
		FaultSystemRupSet subset = new FaultSystemRupSet(rupSet.getFaultSectionDataList(), rupSet.getSlipRateForAllSections(),
				rupSet.getSlipRateStdDevForAllSections(), rupSet.getAreaForAllSections(),
				sectionForRups, mags, rakes, rupAreas, rupLengths, rupSet.getInfoString());
		
		return new InversionFaultSystemRupSet(subset, rupSet.getLogicTreeBranch(), rupSet.getLaughTestFilter(), rupAveSlips,
				null, null, null);
	}
	
	public static InversionFaultSystemRupSet getDownsampledRupSet(InversionFaultSystemRupSet rupSet, double dm) {
		List<Integer> rupIndexes = new RupSetDownsampler(rupSet, dm).getRuptures();
		
		List<List<Integer>> sectionForRups = Lists.newArrayList();
		double[] mags = new double[rupIndexes.size()];
		double[] rakes = new double[rupIndexes.size()];
		double[] rupAreas = new double[rupIndexes.size()];
		double[] rupLengths = new double[rupIndexes.size()];
		double[] rupAveSlips = new double[rupIndexes.size()];
		
		for (int i=0; i<rupIndexes.size(); i++) {
			int rupIndex = rupIndexes.get(i);
			sectionForRups.add(rupSet.getSectionsIndicesForRup(rupIndex));
			mags[i] = rupSet.getMagForRup(rupIndex);
			rakes[i] = rupSet.getAveRakeForRup(rupIndex);
			rupAreas[i] = rupSet.getAreaForRup(rupIndex);
			rupLengths[i] = rupSet.getLengthForRup(rupIndex);
			rupAveSlips[i] = rupSet.getAveSlipForRup(rupIndex);
		}
		
		FaultSystemRupSet subset = new FaultSystemRupSet(rupSet.getFaultSectionDataList(), rupSet.getSlipRateForAllSections(),
				rupSet.getSlipRateStdDevForAllSections(), rupSet.getAreaForAllSections(),
				sectionForRups, mags, rakes, rupAreas, rupLengths, rupSet.getInfoString());
		
		return new InversionFaultSystemRupSet(subset, rupSet.getLogicTreeBranch(), rupSet.getLaughTestFilter(), rupAveSlips,
				null, null, null);
	}
	
	public static boolean doRupPairingSmoothnessPlotsExist(File dir, String prefix) {
		return new File(dir, prefix+"_rup_smooth_pairings.png").exists();
	}
	
	public static void setFontSizes(HeadlessGraphPanel gp) {
//		gp.setTickLabelFontSize(16);
//		gp.setAxisLabelFontSize(18);
//		gp.setPlotLabelFontSize(20);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setBackgroundColor(Color.WHITE);
	}
}
