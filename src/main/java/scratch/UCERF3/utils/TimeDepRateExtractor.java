package scratch.UCERF3.utils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ClassUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.FaultSystemSolutionERF;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Command line utility to extract UCERF3 time dependent probabilities
 * @author kevin
 *
 */
public class TimeDepRateExtractor {
	
	private static final int START_YEAR_DEFAULT = 2014;
	private static final int HIST_OPEN_INTERVAL_BASIS_DEFAULT = 1875;
	
	private FaultSystemSolution sol;
	
	private double duration;
	private int startYear;
	private int histOpenBasis;
	private boolean filterAftershocks;
	private String[] probModels;
	
	private File outputFile;
	private boolean binary;
	
	private boolean ignoreNoDateLast;
	private int numSectsWithDateLast = 0;
	
	private TimeDepRateExtractor(CommandLine cmd) {
		File solFile = new File(cmd.getOptionValue("solution"));
		Preconditions.checkArgument(solFile.exists(), "Sol file doesn't exist: "+solFile.getAbsolutePath());
		
		try {
			sol = FaultSystemIO.loadSol(solFile);
		} catch (Exception e) {
			throw new IllegalStateException("Error loading solution", e);
		}
		
		duration = Double.parseDouble(cmd.getOptionValue("duration"));
		
		if (cmd.hasOption("start-year"))
			startYear = Integer.parseInt(cmd.getOptionValue("start-year"));
		else
			startYear = START_YEAR_DEFAULT;
		
		if (cmd.hasOption("hist-open-interval-basis"))
			histOpenBasis = Integer.parseInt(cmd.getOptionValue("hist-open-interval-basis"));
		else
			histOpenBasis = HIST_OPEN_INTERVAL_BASIS_DEFAULT;
		
		filterAftershocks = cmd.hasOption("filter-aftershocks");
		
		probModels = cmd.getOptionValue("prob-model").split(",");
		Preconditions.checkArgument(probModels.length >= 1);
		
		outputFile = new File(cmd.getOptionValue("output-file"));
		binary = cmd.hasOption("binary");
		ignoreNoDateLast = cmd.hasOption("ignore-no-date-last");
		
		for (FaultSectionPrefData sect : sol.getRupSet().getFaultSectionDataList())
			if (sect.getDateOfLastEvent() > Long.MIN_VALUE)
				numSectsWithDateLast++;
	}
	
	private void calc() throws IOException {
		if (numSectsWithDateLast == 0)
			System.err.println("*** WARNING *** NO FAULT SECTIONS HAVE DATE OF LAST EVENT DATA!");
		else
			System.out.println(numSectsWithDateLast+"/"+sol.getRupSet().getNumSections()
					+" sects have date of last event.");
		
		int numRups = sol.getRupSet().getNumRuptures();
		
		CSVFile<String> csv;
		if (binary) {
			csv = null;
		} else  {
			csv = new CSVFile<String>(true);
			List<String> header = Lists.newArrayList("FSS Index", "FSS Rate (1/yr)");
			String durStr;
			if ((float)(int)duration == (float)duration)
				durStr = (int)duration+"";
			else
				durStr = (float)duration+"";
			for (String probModel : probModels) {
				header.add(probModel+" "+durStr+"yr Prob");
				header.add(probModel+" Equiv Annual Rate (1/yr)");
			}
			csv.addLine(header);
			for (int i=0; i<numRups; i++) {
				List<String> line = Lists.newArrayList(i+"", sol.getRateForRup(i)+"");
				while (line.size() < header.size())
					line.add("");
				csv.addLine(line);
			}
		}
		
		for (int i=0; i<probModels.length; i++) {
			String probModel = probModels[i];
			System.out.println("Calculating for "+probModel);
			
			FaultSystemSolutionERF erf = buildERF(probModel);
			
			erf.updateForecast();
			
			double[] probs = new double[numRups];
			double[] equivRates = new double[numRups];
			
			int numSkipped = 0;
			
			for (int r=0; r<numRups; r++) {
				int sourceID = erf.getSrcIndexForFltSysRup(r);
				if (sourceID < 0) {
					numSkipped++;
					continue;
				}
				ProbEqkSource source = erf.getSource(sourceID);
				
				probs[r] = source.computeTotalProb();
				equivRates[r] = source.computeTotalEquivMeanAnnualRate(duration);
			}
			System.out.println("Skipped "+numSkipped+" rups (due to zero rate or below sect min mag)");
			
			if (binary) {
				File outputFile;
				if (probModels.length > 1) {
					// there are multiple prob models, add prob model to the file name as name_<model>.ext
					String name = this.outputFile.getName();
					if (name.contains(".")) {
						int ind = name.lastIndexOf(".");
						name = name.substring(0, ind)+"_"+probModel+name.substring(ind);
					} else {
						name = name+"_"+probModel;
					}
					outputFile = new File(this.outputFile.getParentFile(), name);
				} else {
					outputFile = this.outputFile;
				}
				System.out.println("Writing binary file to: "+outputFile.getAbsolutePath());
				MatrixIO.doubleArrayToFile(equivRates, outputFile);
			} else {
				// populate CSV file
				int col = 2+2*i;
				for (int r=0; r<numRups; r++) {
					int row = r+1;
					
					csv.set(row, col, probs[r]+"");
					csv.set(row, col+1, equivRates[r]+"");
				}
			}
		}
		
		if (!binary) {
			System.out.println("Writing CSV file to: "+outputFile.getAbsolutePath());
			csv.writeToFile(outputFile);
		}
	}
	
	private FaultSystemSolutionERF buildERF(String probModel) {
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(sol);
		
		if (probModel.equals(ProbabilityModelOptions.U3_PREF_BLEND.name())) {
			erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_PREF_BLEND);
		} else if (probModel.equals(ProbabilityModelOptions.POISSON.name())) {
			erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		} else {
			erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
			MagDependentAperiodicityOptions cov;
			if (probModel.equals("BPT_LOW")) 
				cov = MagDependentAperiodicityOptions.LOW_VALUES;
			else if (probModel.equals("BPT_MID")) 
				cov = MagDependentAperiodicityOptions.MID_VALUES;
			else if (probModel.equals("BPT_HIGH")) 
				cov = MagDependentAperiodicityOptions.HIGH_VALUES;
			else
				throw new IllegalArgumentException("Unknown prob model: "+probModel);
			erf.setParameter(MagDependentAperiodicityParam.NAME, cov);
		}
		
		boolean timeDep = !erf.getParameter(ProbabilityModelParam.NAME).getValue()
									.equals(ProbabilityModelOptions.POISSON);
		
		System.out.println("Time dep? "+timeDep);
		
		erf.getTimeSpan().setDuration(duration);
		if (timeDep)
			erf.getTimeSpan().setStartTime(startYear);
		
		// setting the above can be sensative to order of param setting so just double check
		Preconditions.checkState(erf.getTimeSpan().getDuration() == duration);
		if (timeDep)
			Preconditions.checkState(erf.getTimeSpan().getStartTimeYear() == startYear);
		
		if (timeDep)
			erf.setParameter(HistoricOpenIntervalParam.NAME, (double)(startYear - histOpenBasis));
		
		erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, filterAftershocks);
		
		erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
		
		System.out.println("Built ERF for "+probModel);
		System.out.println("******** ERF PARAMS ********");
		for (Parameter<?> param : erf.getAdjustableParameterList())
			System.out.println("\t"+param.getName()+": "+param.getValue());
		System.out.println("\tTime Span Duration: "+erf.getTimeSpan().getDuration());
		if (timeDep)
			System.out.println("\tTime Span Start Year: "+erf.getTimeSpan().getStartTimeYear());
		System.out.println("****************************");
		
		Preconditions.checkState(!timeDep || numSectsWithDateLast > 0 || ignoreNoDateLast,
				"You are attempting to calculate time dependent probabilities on a fault system solution where"
				+ " the date of last event is not set on any fault section. You can skip this check and only"
				+ " calculation probabilities using the historical open interval with the --ignore-no-date-last flag.");
		
		return erf;
	}
	
	public static Options createOptions() {
		Options ops = new Options();
		
		Option durationOp = new Option("d", "duration", true, "Forecast duration in years.");
		durationOp.setRequired(true);
		ops.addOption(durationOp);
		
		Option startYearOp = new Option("y", "start-year", true,
				"Forecast start year. Default: "+START_YEAR_DEFAULT);
		startYearOp.setRequired(false);
		ops.addOption(startYearOp);
		
		Option probModelOp = new Option("p", "prob-model", true,
				"Probability model. One or more of "+ProbabilityModelOptions.U3_PREF_BLEND.name()+","
						+ProbabilityModelOptions.POISSON.name()+","
						+"BPT_LOW,BPT_MID,BPT_HIGH. Multiple entries can be comma separated.");
		probModelOp.setRequired(true);
		ops.addOption(probModelOp);
		
		Option aftershockOp = new Option("a", "filter-aftershocks", false, "Apply aftershock filter");
		aftershockOp.setRequired(false);
		ops.addOption(aftershockOp);
		
		Option histOpenOp = new Option("h", "hist-open-interval-basis", true,
				"Year basis for historical open interval. Default: "+HIST_OPEN_INTERVAL_BASIS_DEFAULT);
		histOpenOp.setRequired(false);
		ops.addOption(histOpenOp);
		
		Option solOp = new Option("s", "solution", true, "Input Fault System Solution zip file");
		solOp.setRequired(true);
		ops.addOption(solOp);
		
		Option outputOp = new Option("o", "output-file", true, "Output file name");
		outputOp.setRequired(true);
		ops.addOption(outputOp);
		
		Option binaryOp = new Option("b", "binary", false,
				"Output equivalent annualized rates binary file in FSS rates.bin format. Otherwise CSV format.");
		binaryOp.setRequired(false);
		ops.addOption(binaryOp);
		
		Option ignoreOp = new Option("i", "ignore-no-date-last", false,
				"Skips check that ensures date of last event data is set on at least some fault sections. Can be used"
				+ " to calculate time dependent probabilities using only the historical open interval.");
		ignoreOp.setRequired(false);
		ops.addOption(ignoreOp);
		
		return ops;
	}
	
	private static CommandLine parse(Options options, String args[]) {
		try {
			CommandLineParser parser = new GnuParser();
			
			CommandLine cmd = parser.parse(options, args);
			return cmd;
		} catch (Exception e) {
			printHelpAndExit(e.getMessage(), options);
			return null; // not accessible
		}
	}
	
	private static void printHelpAndExit(String message, Options options) {
		System.out.println(message);
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(
				ClassUtils.getClassNameWithoutPackage(TimeDepRateExtractor.class), options, true );
		abortAndExit(2);
	}
	
	private static void abortAndExit(int ret) {
		abortAndExit(null, ret);
	}
	
	private static void abortAndExit(Throwable t) {
		abortAndExit(t, 1);
	}
	
	private static void abortAndExit(Throwable t, int ret) {
		if (t != null)
			t.printStackTrace();
		System.out.flush();
		System.err.flush();
		System.exit(ret);
	}
	
	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals("TEST_FROM_ECLIPSE")) {
			args = new String[] {"--duration", "5", "--solution",
					"/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/"
							+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip",
					"--prob-model", "U3_PREF_BLEND,POISSON,BPT_LOW,BPT_MID,BPT_HIGH",
//					"--output-file", "/tmp/td_output.csv"};
					"--binary", "--output-file", "/tmp/td_output.bin"};
		}
		try {
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args);
			
			args = cmd.getArgs();
			
			if (args.length != 0)
				printHelpAndExit("Unknown option(s): "+Joiner.on(" ").join(args), options);
			
			System.err.println("****************************************************");
			System.err.println("WARNING: This is provided as a service and is not exhaustively tested. "
					+ "No warranty is expressed or implied and by using this software you agree to the "
					+ "OpenSHA license/disclaimer available at http://opensha.org/license");
			System.err.println("****************************************************\n");
			
			TimeDepRateExtractor extract = new TimeDepRateExtractor(cmd);
			
			extract.calc();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
