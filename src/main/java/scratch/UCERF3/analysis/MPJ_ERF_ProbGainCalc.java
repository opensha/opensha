package scratch.UCERF3.analysis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.BPTAveragingTypeParam;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;

import scratch.UCERF3.CompoundFaultSystemSolution;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.utils.UCERF3_DataUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.primitives.Doubles;

import net.kevinmilner.mpj.taskDispatch.MPJTaskCalculator;

public class MPJ_ERF_ProbGainCalc extends MPJTaskCalculator {
	
	private CompoundFaultSystemSolution cfss;
	private List<LogicTreeBranch> branches;
	private FaultSystemSolutionERF[] erfs;
	
	private double duration;
	
	private File outputDir;
	
	private boolean mainFaults = false;
	private Map<String, List<Integer>> mainFaultsMap;
	private Map<FaultModels, Map<String, Collection<Integer>>> mainFaultsRupMappings;
	private List<String> mainFaultsSorted;

	public MPJ_ERF_ProbGainCalc(CommandLine cmd) throws ZipException, IOException {
		super(cmd);
		File compoundFile = new File(cmd.getOptionValue("cfss"));
		Preconditions.checkArgument(compoundFile.exists(),
				"Compound file doesn't exist: "+compoundFile.getAbsolutePath());
		cfss = CompoundFaultSystemSolution.fromZipFile(compoundFile);
		branches = Lists.newArrayList(cfss.getBranches());
		Collections.sort(branches);
		
		int numThreads = getNumThreads();
		
		erfs = new FaultSystemSolutionERF[numThreads];
		for (int i=0; i<numThreads; i++) {
			erfs[i] = new FaultSystemSolutionERF();
			FaultSystemSolutionERF erf = erfs[i];
			
			erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, false);
			
			erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
			
			if (cmd.hasOption("ave")) {
				BPTAveragingTypeOptions aveType = BPTAveragingTypeOptions.valueOf(cmd.getOptionValue("ave"));
				erf.setParameter(BPTAveragingTypeParam.NAME, aveType);
			}
			
			this.duration = Double.parseDouble(cmd.getOptionValue("duration"));
			erf.getTimeSpan().setDuration(duration);
			
			if (cmd.hasOption("cov")) {
				MagDependentAperiodicityOptions cov = MagDependentAperiodicityOptions.valueOf(cmd.getOptionValue("cov"));
				erf.setParameter(MagDependentAperiodicityParam.NAME, cov);
			}
			
			if (cmd.hasOption("hist")) {
				double histBasis = Double.parseDouble(cmd.getOptionValue("hist"));
				double calcOpen = (double)(FaultSystemSolutionERF.START_TIME_DEFAULT-histBasis);
				System.out.println("Setting historical open interval to "+calcOpen+" (basis="+histBasis+")");
				erf.setParameter(HistoricOpenIntervalParam.NAME, calcOpen);
			}
		}
		
		outputDir = new File(cmd.getOptionValue("dir"));
		if (rank == 0) {
			Preconditions.checkState(outputDir.exists() || outputDir.mkdirs());
//			debug("DURATION: "+erf.getTimeSpan().getDuration());
		}
		
		if (cmd.hasOption("faults")) {
			mainFaults = true;
			mainFaultsMap = FaultModels.parseNamedFaultsAltFile(UCERF3_DataUtils.getReader("FaultModels",
							"MainFaultsForTimeDepComparison.txt"));
			mainFaultsSorted = Lists.newArrayList(mainFaultsMap.keySet());
			Collections.sort(mainFaultsSorted);
			mainFaultsRupMappings = Maps.newHashMap();
		}
	}

	@Override
	protected int getNumTasks() {
		return branches.size();
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		ArrayDeque<Integer> stack = new ArrayDeque<Integer>();
		for (int index : batch)
			stack.add(index);
		
		ArrayList<Thread> threads = new ArrayList<Thread>();
		
		for (int i=0; i<getNumThreads(); i++) {
			threads.add(new Thread(new CalcRunnable(erfs[i], stack)));
		}
		
		// start the threads
		for (Thread t : threads) {
			t.start();
		}
		
		// join the threads
		for (Thread t : threads) {
			t.join();
		}
	}
	
	private class CalcRunnable implements Runnable {
		private FaultSystemSolutionERF erf;
		private Deque<Integer> stack;
		public CalcRunnable(FaultSystemSolutionERF erf, Deque<Integer> stack) {
			this.erf = erf;
			this.stack = stack;
		}

		@Override
		public void run() {
			while (true) {
				try {
					Integer index;
					synchronized (stack) {
						try {
							index = stack.pop();
						} catch (Exception e) {
							index = null;
						}
					}
					if (index == null)
						break;
					LogicTreeBranch branch = branches.get(index);
					String name = branch.buildFileName();
					
					File subOutputFile = new File(outputDir, name+"_subs.csv");
					File parentOutputFile = new File(outputDir, name+"_parents.csv");
					File mainOutputFile = new File(outputDir, name+"_main_faults.csv");
					
					if (mainFaults && mainOutputFile.exists() || !mainFaults && parentOutputFile.exists())
						continue;
					
					FaultSystemSolution sol = cfss.getSolution(branch);
					erf.setSolution(sol);
					erf.getTimeSpan().setDuration(duration);
					
//				erf.updateForecast();
//				System.out.println("Hist interval: "+erf.getParameter(HistoricOpenIntervalParam.NAME).getValue());
//				abortAndExit(0);
					
					if (!mainFaults) {
						FaultSysSolutionERF_Calc.writeSubSectionTimeDependenceCSV(erf, subOutputFile);
						FaultSysSolutionERF_Calc.writeParentSectionTimeDependenceCSV(erf, parentOutputFile);
					} else {
						double[] minMags = {0d, 6.7d, 7.2d, 7.7d, 8.2d};
						FaultModels fm = branch.getValue(FaultModels.class);
						FaultSystemRupSet rupSet = sol.getRupSet();
						Map<String, Collection<Integer>> mappings = mainFaultsRupMappings.get(fm);
						if (mappings == null) {
							mappings = Maps.newHashMap();
							for (String fault : mainFaultsSorted) {
								HashSet<Integer> rups = new HashSet<Integer>();
								for (Integer parentID : mainFaultsMap.get(fault)) {
									List<Integer> parentRups = rupSet.getRupturesForParentSection(parentID);
									if (parentRups != null)
										rups.addAll(parentRups);
								}
								mappings.put(fault, rups);
							}
							mainFaultsRupMappings.put(fm, mappings);
						}
						CSVFile<String> csv = new CSVFile<String>(true);
						List<String> header = Lists.newArrayList("Name");
						for (double minMag : minMags) {
							header.add("M>="+(float)minMag);
							header.add("U3 pBPT");
							header.add("U3 pPois");
						}
						csv.addLine(header);
						// time dependent
						erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_BPT);
						erf.getTimeSpan().setDuration(duration);
						erf.updateForecast();
						Table<String, Double, Double> bptTable = calcFaultProbs(erf, rupSet, minMags, mappings);
						erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
						erf.getTimeSpan().setDuration(duration);
						erf.updateForecast();
						Table<String, Double, Double> poisTable = calcFaultProbs(erf, rupSet, minMags, mappings);
						for (String fault : mainFaultsSorted) {
							List<String> line = Lists.newArrayList(fault);
							for (double minMag : minMags) {
								line.add("");
								line.add(""+bptTable.get(fault, minMag));
								line.add(""+poisTable.get(fault, minMag));
							}
							csv.addLine(line);
						}
						csv.writeToFile(mainOutputFile);
					}
				} catch (IOException e) {
					ExceptionUtils.throwAsRuntimeException(e);
				}
			}
		}
		
	}
	
	private Table<String, Double, Double> calcFaultProbs(FaultSystemSolutionERF erf, FaultSystemRupSet rupSet,
			double[] minMags, Map<String, Collection<Integer>> mappings) {
		Table<String, Double, Double> bptTable = ArrayTable.create(mainFaultsSorted, Doubles.asList(minMags));
		for (double minMag : minMags) {
			for (String fault : mainFaultsSorted) {
				Collection<Integer> rups = mappings.get(fault);
				double prob;
				if (rups.isEmpty())
					prob = Double.NaN;
				else
					prob = calcFaultProb(erf, rupSet, rups, minMag);
				bptTable.put(fault, minMag, prob);
			}
		}
		return bptTable;
	}
	
	public static double calcFaultProb(FaultSystemSolutionERF erf, FaultSystemRupSet rupSet,
			Collection<Integer> rups, double minMag) {
		List<Double> probs = Lists.newArrayList();
		for (int sourceID=0; sourceID<erf.getNumFaultSystemSources(); sourceID++) {
			int rupID = erf.getFltSysRupIndexForSource(sourceID);
			if (rups.contains(rupID) && rupSet.getMagForRup(rupID) >= minMag)
				probs.add(erf.getSource(sourceID).computeTotalProb());
		}
		
		return FaultSysSolutionERF_Calc.calcSummedProbs(probs);
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		// TODO Auto-generated method stub

	}
	
	protected static Options createOptions() {
		Options options = MPJTaskCalculator.createOptions();
		
		Option cfssOption = new Option("cfss", "compound-sol", true, "Compound Fault System Solution File");
		cfssOption.setRequired(true);
		options.addOption(cfssOption);
		
		Option dirOption = new Option("dir", "output-dir", true, "Output Directory");
		dirOption.setRequired(true);
		options.addOption(dirOption);
		
		Option durationOption = new Option("dur", "duration", true, "Forecast Duration");
		durationOption.setRequired(true);
		options.addOption(durationOption);
		
		Option aperiodOption = new Option("cov", "aperiodicity", true, "Aperiodicity enum name");
		aperiodOption.setRequired(false);
		options.addOption(aperiodOption);
		
		Option histOption = new Option("hist", "hist-open-interval-basis", true, "Historical Open Interval Basis (year, e.g. 1875)");
		histOption.setRequired(false);
		options.addOption(histOption);
		
		Option aveRIOption = new Option("ave", "ave-type", true, "Average Type");
		aveRIOption.setRequired(false);
		options.addOption(aveRIOption);
		
		Option mainFaultsOption = new Option("faults", "main-faults", false, "Flag for doing main faults calculation instead");
		mainFaultsOption.setRequired(false);
		options.addOption(mainFaultsOption);
		
		return options;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		args = MPJTaskCalculator.initMPJ(args);

		try {
			Options options = createOptions();

			CommandLine cmd = parse(options, args, MPJ_ERF_ProbGainCalc.class);
			
			MPJ_ERF_ProbGainCalc driver = new MPJ_ERF_ProbGainCalc(cmd);
			
			driver.run();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
