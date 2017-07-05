package scratch.UCERF3.analysis;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import mpi.MPI;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.opensha.commons.geo.Region;
import org.opensha.commons.hpc.mpj.taskDispatch.MPJTaskCalculator;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.threads.Task;
import org.opensha.commons.util.threads.ThreadedTaskComputer;

import scratch.UCERF3.CompoundFaultSystemSolution;
import scratch.UCERF3.FaultSystemSolutionFetcher;
import scratch.UCERF3.analysis.CompoundFSSPlots.AveSlipMapPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.BranchAvgFSSBuilder;
import scratch.UCERF3.analysis.CompoundFSSPlots.ERFBasedRegionalMFDPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.ERFBasedRegionalMagProbPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.ERFBasedSiteHazardHistPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.ERFProbModelCalc;
import scratch.UCERF3.analysis.CompoundFSSPlots.GriddedParticipationMapPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.MapBasedPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.MiniSectRIPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.MisfitTable;
import scratch.UCERF3.analysis.CompoundFSSPlots.MultiFaultParticPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.PaleoFaultPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.PaleoRatesTable;
import scratch.UCERF3.analysis.CompoundFSSPlots.PaleoSiteCorrelationPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.ParentSectMFDsPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.ParticipationMapPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.PlotSolComputeTask;
import scratch.UCERF3.analysis.CompoundFSSPlots.RegionalMFDPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.RupJumpPlot;
import scratch.UCERF3.analysis.CompoundFSSPlots.SlipRatePlots;
import scratch.UCERF3.analysis.CompoundFSSPlots.SubSectRITable;
import scratch.UCERF3.analysis.CompoundFSSPlots.TimeDepGriddedParticipationProbPlot;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.logicTree.APrioriBranchWeightProvider;
import scratch.UCERF3.logicTree.BranchWeightProvider;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.UCERF3p2BranchWeightProvider;
import scratch.UCERF3.logicTree.UniformBranchWeightProvider;
import scratch.UCERF3.utils.FaultSystemIO;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

public class MPJDistributedCompoundFSSPlots extends MPJTaskCalculator {
	
	private FaultSystemSolutionFetcher fetcher;
	private List<LogicTreeBranch> branches;
	private List<CompoundFSSPlots> plots;
	
	private int threads;
	
	private int myCalcs = 0;
	
	private MPJDistributedCompoundFSSPlots parentMFDCompare;

	public MPJDistributedCompoundFSSPlots(CommandLine cmd, FaultSystemSolutionFetcher fetcher,
			List<CompoundFSSPlots> plots) {
		super(cmd);
		Preconditions.checkState(!plots.isEmpty(), "No plots specified!");
		
		branches = Lists.newArrayList();
		branches.addAll(fetcher.getBranches());
		
		if (cmd.hasOption("name-grep")) {
			List<String> greps = Lists.newArrayList(Splitter.on(",").split(cmd.getOptionValue("name-grep")));
			List<LogicTreeBranch> filtered = Lists.newArrayList();
			
			branchLoop:
			for (LogicTreeBranch branch : branches) {
				String fname = branch.buildFileName();
				for (String grep : greps) {
					if (!fname.contains(grep))
						continue branchLoop;
				}
				filtered.add(branch);
			}
			
			System.out.println("Filtered branches size: "+filtered.size()+"/"+branches.size());
			branches = filtered;
		}
		
		if (cmd.hasOption("name-exclude")) {
			List<String> greps = Lists.newArrayList(Splitter.on(",").split(cmd.getOptionValue("name-exclude")));
			List<LogicTreeBranch> filtered = Lists.newArrayList();
			
			branchLoop:
			for (LogicTreeBranch branch : branches) {
				String fname = branch.buildFileName();
				for (String grep : greps) {
					if (fname.contains(grep))
						continue branchLoop;
				}
				filtered.add(branch);
			}
			
			System.out.println("Filtered branches size: "+filtered.size()+"/"+branches.size());
			branches = filtered;
		}
		
		this.fetcher = fetcher;
		this.plots = plots;
		this.threads = getNumThreads();
		
		if (cmd.hasOption("plot-parent-mfd-compare")) {
			// make sure we're not the subplot - subplot will have a plot list with the MFD plot and null
			if (plots.get(0) == null) {
				plots.remove(0);
			} else {
				File compFile = new File(cmd.getOptionValue("plot-parent-mfd-compare"));
				List<CompoundFSSPlots> subPlots = Lists.newArrayList();
				subPlots.add(null); // hack for identification
				// TODO don't hardcode?
				subPlots.add(new ParentSectMFDsPlot(new UCERF3p2BranchWeightProvider()));
				try {
					FaultSystemSolutionFetcher subFetch = CompoundFaultSystemSolution.fromZipFile(compFile);
					if (cmd.hasOption("rand")) {
						int num = Integer.parseInt(cmd.getOptionValue("rand"));
						subFetch = FaultSystemSolutionFetcher.getRandomSample(subFetch, num);
					}
					parentMFDCompare = new MPJDistributedCompoundFSSPlots(cmd, subFetch, subPlots);
				} catch (Exception e) {
					ExceptionUtils.throwAsRuntimeException(e);
				}
			}
		}
	}

	@Override
	protected int getNumTasks() {
		return branches.size();
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		List<Task> tasks = Lists.newArrayList();
		
		for (int index : batch) {
			LogicTreeBranch branch = branches.get(index);
			List<CompoundFSSPlots> myPlots = Lists.newArrayList(plots);
			Collections.shuffle(myPlots);
			tasks.add(new PlotSolComputeTask(myPlots, fetcher, branch, true, index));
		}
		
		debug("Making "+plots.size()+" plot(s) with "+tasks.size()+" branches");
		
		ThreadedTaskComputer comp = new ThreadedTaskComputer(tasks);
		try {
			comp.computeThreaded(threads);
		} catch (InterruptedException e) {
			debug("Exception computing threaded: "+e.getMessage());
			ExceptionUtils.throwAsRuntimeException(e);
		}
		myCalcs += batch.length;
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		for (CompoundFSSPlots plot : plots)
			plot.flushResults();
		System.out.println(rank+". My number of calcs: "+myCalcs);
		int numPlots = plots.size();
		
		CompoundFSSPlots[] sendbuf = new CompoundFSSPlots[numPlots];
		for (int i=0; i<numPlots; i++)
			if (myCalcs > 0)
				sendbuf[i] = plots.get(i);
			else
				sendbuf[i] = null;
		
//		for (CompoundFSSPlots obj : sendbuf) {
//			if (obj != null) {
//				File tempFile = File.createTempFile("openSHA", "serial");
//				System.out.println(rank+". Test serializing to file: "+tempFile.getAbsolutePath());
//				try {
//					FileUtils.saveObjectInFile(tempFile.getAbsolutePath(), obj);
//					FileUtils.loadObject(tempFile.getAbsolutePath());
//					tempFile.delete();
//				} catch (Exception e) {
//					System.out.println("Error serializing with rank "+rank);
//					e.printStackTrace();
//				}
//			}
//		}
		
//		String[] sendbuf = new String[numPlots];
//		for (int i=0; i<numPlots; i++)
//			sendbuf[i] = rank+","+i;
		
		int recvcount = numPlots * size;
		CompoundFSSPlots[] recvbuf;
		if (rank == 0)
			recvbuf = new CompoundFSSPlots[recvcount];
		else
			recvbuf = null;
		
//		String[] recvbuf;
//		if (rank == 0)
//			recvbuf = new String[recvcount];
//		else
//			recvbuf = null;
		
//		System.out.println("Sendbuf size: "+numPlots);
//		System.out.println("Reczbuf size: "+recvcount);
		
//		MPI.COMM_WORLD.Gather(sendbuf, 0, numPlots, MPI.OBJECT, recvbuf, 0, numPlots, MPI.OBJECT, 0);
		
		// see if we have comparison MFD plots
		if (parentMFDCompare != null) {
			debug("Running MFD compare!");
			parentMFDCompare.run();
		}
		
//		List<List<CompoundFSSPlots>> otherPlotsList = Lists.newArrayList();
//		for (int p=0; p<numPlots; p++)
//			otherPlotsList.add(new ArrayList<CompoundFSSPlots>());
		if (rank == 0) {
			for (int source=1; source<size; source++) {
				System.out.println("Receiving from "+source);
				MPI.COMM_WORLD.Recv(sendbuf, 0, sendbuf.length, MPI.OBJECT, source, 0);
				for (int p=0; p<numPlots; p++) {
					if (sendbuf[p] != null) {
						CompoundFSSPlots plot = plots.get(p);
						CompoundFSSPlots oPlot = sendbuf[p];
						plot.combineDistributedCalcs(Lists.newArrayList(oPlot));
						plot.addToComputeTimeCount(oPlot.getComputeTimeCount());
						sendbuf[p] = null;
					}
				}
				System.gc();
			}
//			for (int p=0; p<numPlots; p++) {
//				CompoundFSSPlots plot = plots.get(p);
//				List<CompoundFSSPlots> oPlots = otherPlotsList.get(p);
//				plot.combineDistributedCalcs(oPlots);
//				for (CompoundFSSPlots oPlot : oPlots)
//					plot.addToComputeTimeCount(oPlot.getComputeTimeCount());
//			}
			
			for (CompoundFSSPlots plot : plots) {
				plot.finalizePlot();
				if (parentMFDCompare != null && plot instanceof ParentSectMFDsPlot) {
					debug("Merging in MFD compare");
					ParentSectMFDsPlot mainPlot = (ParentSectMFDsPlot)plot;
					ParentSectMFDsPlot oPlot = (ParentSectMFDsPlot)parentMFDCompare.plots.get(0);
					mainPlot.addMeanFromExternalAsFractile(oPlot);
				}
			}
			
			CompoundFSSPlots.printComputeTimes(plots);
		} else {
			MPI.COMM_WORLD.Send(sendbuf, 0, sendbuf.length, MPI.OBJECT, 0, 0);
		}
	}
	
	protected static Options createOptions() {
		Options options = MPJTaskCalculator.createOptions();
		
		Option mfdOption = new Option("mfd", "plot-mfds", false, "Flag for plotting MFDs");
		mfdOption.setRequired(false);
		options.addOption(mfdOption);
		
		Option paleoFaultOption = new Option("paleofault", "plot-paleo-faults", false,
				"Flag for plotting paleo faults");
		paleoFaultOption.setRequired(false);
		options.addOption(paleoFaultOption);
		
		Option paleoCorrOption = new Option("paleocorr", "plot-paleo-corrs", false,
				"Flag for plotting paleo correlations");
		paleoCorrOption.setRequired(false);
		options.addOption(paleoCorrOption);
		
		Option parentMFDsOption = new Option("parentmfds", "plot-parent-mfds", false,
				"Flag for plotting parent section MFDs");
		parentMFDsOption.setRequired(false);
		options.addOption(parentMFDsOption);
		
		Option parentMFDCompareOption = new Option("parentmfdcompare", "plot-parent-mfd-compare", true,
				"Flag for plotting parent section MFDs");
		parentMFDCompareOption.setRequired(false);
		options.addOption(parentMFDCompareOption);
		
		Option jumpsOption = new Option("jumps", "plot-rup-jumps", false,
				"Flag for plotting rupture jumps");
		jumpsOption.setRequired(false);
		options.addOption(jumpsOption);
		
		Option slipMisfitsOption = new Option("slips", "plot-slip-rates", false,
				"Flag for plotting slip misfits");
		slipMisfitsOption.setRequired(false);
		options.addOption(slipMisfitsOption);
		
		Option particsOption = new Option("partics", "plot-participations", false,
				"Flag for plotting fault based section participations");
		particsOption.setRequired(false);
		options.addOption(particsOption);
		
		Option gridParticsOption = new Option("gridpartics", "plot-gridded-participations", false,
				"Flag for plotting gridded participations");
		gridParticsOption.setRequired(false);
		options.addOption(gridParticsOption);
		
		Option gridParticsProbsOption = new Option("gridparticprobs", "plot-gridded-partic-probs", false,
				"Flag for plotting gridded participation probabilities");
		gridParticsProbsOption.setRequired(false);
		options.addOption(gridParticsProbsOption);
		
		Option erfMFDsOption = new Option("erfmfds", "plot-erf-mfds", false,
				"Flag for plotting gridded participations");
		erfMFDsOption.setRequired(false);
		options.addOption(erfMFDsOption);
		
		Option erfProbDistsOption = new Option("erfmpds", "plot-erf-prob-dists", false,
				"Flag for erf prob dists");
		erfProbDistsOption.setRequired(false);
		options.addOption(erfProbDistsOption);
		
		Option erfHazOption = new Option("erfhaz", "plot-erf-hazard", false,
				"Flag for erf hazard histograms");
		erfHazOption.setRequired(false);
		options.addOption(erfHazOption);
		
		Option erfProbsOption = new Option("erfprob", "plot-erf-probs", false,
				"Flag for erf probability models calc");
		erfProbsOption.setRequired(false);
		options.addOption(erfProbsOption);
		
		Option subSectRIsOption = new Option("ssris", "plot-sub-sect-ris", false,
				"Flag for creating sub section RIs tables");
		subSectRIsOption.setRequired(false);
		options.addOption(subSectRIsOption);
		
		Option miniSectRIsOption = new Option("miniris", "plot-mini-sect-ris", false,
				"Flag for creating mini section RIs tables");
		miniSectRIsOption.setRequired(false);
		options.addOption(miniSectRIsOption);
		
		Option paleoTablesOption = new Option("paleotables", "plot-paleo-tables", false,
				"Flag for creating paleo rate tables");
		paleoTablesOption.setRequired(false);
		options.addOption(paleoTablesOption);
		
		Option aveSlipsOption = new Option("aveslips", "plot-ave-slips", false,
				"Flag for creating ave slip map based plots");
		aveSlipsOption.setRequired(false);
		options.addOption(aveSlipsOption);
		
		Option multiFaultOption = new Option("multi", "plot-multi-faults", false,
				"Flag for creating ave slip map based plots");
		multiFaultOption.setRequired(false);
		options.addOption(multiFaultOption);
		
		Option misfitOption = new Option("misfit", "plot-misfits", false,
				"Flag for creating misfits table");
		misfitOption.setRequired(false);
		options.addOption(misfitOption);
		
		Option meanOption = new Option("mean", "build-mean", false,
				"Flag for building mean FSS");
		meanOption.setRequired(false);
		options.addOption(meanOption);
		
		Option meanIndOption = new Option("meanind", "mean-sol-ind", true,
				"Index in average fault system solutions to use for mean (optional)");
		meanIndOption.setRequired(false);
		options.addOption(meanIndOption);
		
		Option plotAllOption = new Option("all", "plot-all", false, "Flag for making all plots");
		plotAllOption.setRequired(false);
		options.addOption(plotAllOption);
		
		Option noERFOption = new Option("noerf", "no-erf-plots", false, "Flag for disabling ERF " +
				"based plots (can be used with --plot-all");
		noERFOption.setRequired(false);
		options.addOption(noERFOption);
		
		Option onlyERFOption = new Option("onlyerf", "only-erf-plots", false, "Flag for only ERF " +
				"based plots (when used with --plot-all");
		onlyERFOption.setRequired(false);
		options.addOption(onlyERFOption);
		
		Option noMapOption = new Option("nomap", "no-map-plots", false, "Flag for disabling Map " +
				"based plots (can be used with --plot-all");
		noMapOption.setRequired(false);
		options.addOption(noMapOption);
		
		Option onlyMapOption = new Option("onlymap", "only-map-plots", false, "Flag for only Map " +
				"based plots (when used with --plot-all");
		onlyMapOption.setRequired(false);
		options.addOption(onlyMapOption);
		
		Option randomSampleOption = new Option("rand", "random-sample", true,
				"If supplied, a random sample of the given size will be used.");
		randomSampleOption.setRequired(false);
		options.addOption(randomSampleOption);
		
		Option meanSampleOption = new Option("mean", "mean-sol", true,
				"If supplied along with --random-sample, the given solution will be loaded and used for each sample");
		meanSampleOption.setRequired(false);
		options.addOption(meanSampleOption);
		
		Option nameGrepsOption = new Option("ng", "name-grep", true,
				"If supplied, logic tree branches will be only be included out based on grepping for these comma separated " +
				"strings.");
		nameGrepsOption.setRequired(false);
		options.addOption(nameGrepsOption);
		
		Option nameExcludeOption = new Option("ne", "name-exclude", true,
				"If supplied, logic tree branches will be filtered out based on grepping for these comma separated " +
				"strings.");
		nameExcludeOption.setRequired(false);
		options.addOption(nameExcludeOption);
		
		Option u3p2WeightsOption = new Option("u3p2weight", "ucerf3p2-weights", false,
				"If supplied, UCERF3.2 weights will be used.");
		u3p2WeightsOption.setRequired(false);
		options.addOption(u3p2WeightsOption);
		
		Option uniformWeightsOption = new Option("uniwt", "uniform-weights", false,
				"If supplied, equal weights for all branches will be used.");
		uniformWeightsOption.setRequired(false);
		options.addOption(uniformWeightsOption);
		
		Option durationsOption = new Option("dur", "duration", true,
				"Override the default set of durations for time dependent plots (comma separated)");
		durationsOption.setRequired(false);
		options.addOption(durationsOption);
		
		return options;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		args = MPJTaskCalculator.initMPJ(args);

		try {
			Options options = createOptions();

			CommandLine cmd = parse(options, args, MPJDistributedCompoundFSSPlots.class);

			args = cmd.getArgs();

			Preconditions.checkArgument(args.length == 2, "Must specify inputfile file/output dir!");

			File inputFile = new File(args[0]);
			File dir = new File(args[1]);
			
			if (!dir.exists())
				dir.mkdir();

			Preconditions.checkArgument(inputFile.exists(), "Input file doesn't exist!: "+inputFile);
			
			FaultSystemSolutionFetcher fetcher = CompoundFaultSystemSolution.fromZipFile(inputFile);
			if (cmd.hasOption("rand")) {
				int num = Integer.parseInt(cmd.getOptionValue("rand"));
				if (cmd.hasOption("mean")) {
					final InversionFaultSystemSolution meanSol =
							FaultSystemIO.loadInvSol(new File(cmd.getOptionValue("mean")));
					fetcher = FaultSystemSolutionFetcher.getRandomSample(fetcher, num,
							meanSol.getLogicTreeBranch().getValue(FaultModels.class));
					final Collection<LogicTreeBranch> branches = fetcher.getBranches();
					fetcher = new FaultSystemSolutionFetcher() {
						
						@Override
						public Collection<LogicTreeBranch> getBranches() {
							return branches;
						}
						
						@Override
						protected InversionFaultSystemSolution fetchSolution(LogicTreeBranch branch) {
							return meanSol;
						}
					};
				} else {
					fetcher = FaultSystemSolutionFetcher.getRandomSample(fetcher, num);
				}
			}
			
			BranchWeightProvider weightProvider = new APrioriBranchWeightProvider();
			if (cmd.hasOption("ucerf3p2-weights"))
				weightProvider = new UCERF3p2BranchWeightProvider();
			if (cmd.hasOption("uniform-weights"))
				weightProvider = new UniformBranchWeightProvider();
			
			List<CompoundFSSPlots> plots = Lists.newArrayList();
			
			boolean plotAll = cmd.hasOption("all");
			
			if (cmd.hasOption("duration")) {
				String durStr = cmd.getOptionValue("duration").trim();
				List<Double> vals = Lists.newArrayList();
				for (String val : durStr.split(",")) {
					val = val.trim();
					vals.add(Double.parseDouble(val));
				}
				Preconditions.checkArgument(!vals.isEmpty(), "Must supply at least one duration");
				CompoundFSSPlots.time_dep_durations = Doubles.toArray(vals);
			}
			
			if (plotAll || cmd.hasOption("mfd")) {
				List<Region> regions = RegionalMFDPlot.getDefaultRegions();
				RegionalMFDPlot mfd = new RegionalMFDPlot(weightProvider, regions);
				plots.add(mfd);
			}
			
			if (plotAll || cmd.hasOption("paleofault")) {
				PaleoFaultPlot paleo = new PaleoFaultPlot(weightProvider);
				plots.add(paleo);
			}
			
			if (plotAll || cmd.hasOption("paleocorr")) {
				PaleoSiteCorrelationPlot paleo = new PaleoSiteCorrelationPlot(weightProvider);
				plots.add(paleo);
			}
			
			if (plotAll || cmd.hasOption("parentmfds")) {
				ParentSectMFDsPlot plot = new ParentSectMFDsPlot(weightProvider);
				plots.add(plot);
			}
			
			if (plotAll || cmd.hasOption("jumps")) {
				RupJumpPlot plot = new RupJumpPlot(weightProvider);
				plots.add(plot);
			}
			
			if (plotAll || cmd.hasOption("slips")) {
				SlipRatePlots slips = new SlipRatePlots(weightProvider);
				plots.add(slips);
			}
			
			if (plotAll || cmd.hasOption("partics")) {
				ParticipationMapPlot partics = new ParticipationMapPlot(weightProvider);
				plots.add(partics);
			}
			
			if (plotAll || cmd.hasOption("gridpartics")) {
				GriddedParticipationMapPlot partics = new GriddedParticipationMapPlot(weightProvider);
				plots.add(partics);
			}
			
			if (plotAll || cmd.hasOption("gridparticprobs")) {
				TimeDepGriddedParticipationProbPlot partics = new TimeDepGriddedParticipationProbPlot(weightProvider);
				plots.add(partics);
			}
			
			if (plotAll || cmd.hasOption("erfmfds")) {
				ERFBasedRegionalMFDPlot erfMFDs = new ERFBasedRegionalMFDPlot(weightProvider);
				plots.add(erfMFDs);
			}
			
			if (plotAll || cmd.hasOption("plot-erf-prob-dists")) {
				ERFBasedRegionalMagProbPlot erfMFDs = new ERFBasedRegionalMagProbPlot(weightProvider);
				plots.add(erfMFDs);
			}
			
			if (plotAll || cmd.hasOption("plot-erf-probs")) {
				ERFProbModelCalc erfProbs = new ERFProbModelCalc();
				plots.add(erfProbs);
			}
			
			if (plotAll || cmd.hasOption("erfhaz")) {
				ERFBasedSiteHazardHistPlot erfHaz = new ERFBasedSiteHazardHistPlot(
						weightProvider, new File(dir, ERFBasedSiteHazardHistPlot.DEFAULT_CACHE_DIR_NAME), fetcher.getBranches().size());
				plots.add(erfHaz);
			}
			
			if (plotAll || cmd.hasOption("miniris")) {
				MiniSectRIPlot miniRIs = new MiniSectRIPlot(weightProvider);
				plots.add(miniRIs);
			}
			
			if (plotAll || cmd.hasOption("ssris")) {
				SubSectRITable miniRIs = new SubSectRITable(weightProvider);
				plots.add(miniRIs);
			}
			
			if (plotAll || cmd.hasOption("paleotables")) {
				PaleoRatesTable miniRIs = new PaleoRatesTable(weightProvider);
				plots.add(miniRIs);
			}
			
			if (plotAll || cmd.hasOption("aveslips")) {
				AveSlipMapPlot aveSlip = new AveSlipMapPlot(weightProvider);
				plots.add(aveSlip);
			}
			
			if (plotAll || cmd.hasOption("multi")) {
				MultiFaultParticPlot multi = new MultiFaultParticPlot(weightProvider);
				plots.add(multi);
			}
			
			if (plotAll || cmd.hasOption("misfit")) {
				MisfitTable misfit = new MisfitTable();
				plots.add(misfit);
			}
			
			if (plotAll || cmd.hasOption("mean")) {
				BranchAvgFSSBuilder builder = new BranchAvgFSSBuilder(weightProvider);
				if (cmd.hasOption("meanind")) {
					int meanInd = Integer.parseInt(cmd.getOptionValue("meanind"));
					builder.setSolIndex(meanInd);
				}
				plots.add(builder);
			}
			
			if (cmd.hasOption("noerf")) {
				for (int i=plots.size(); --i>=0;)
					if (plots.get(i).usesERFs())
						plots.remove(i);
			}
			
			if (cmd.hasOption("onlyerf")) {
				for (int i=plots.size(); --i>=0;)
					if (!plots.get(i).usesERFs())
						plots.remove(i);
			}
			
			if (cmd.hasOption("nomap")) {
				for (int i=plots.size(); --i>=0;)
					if (plots.get(i) instanceof MapBasedPlot)
						plots.remove(i);
			}
			
			if (cmd.hasOption("onlymap")) {
				for (int i=plots.size(); --i>=0;)
					if (!(plots.get(i) instanceof MapBasedPlot))
						plots.remove(i);
			}
			
			MPJDistributedCompoundFSSPlots driver = new MPJDistributedCompoundFSSPlots(cmd, fetcher, plots);
			
			driver.run();
			
			String prefix = inputFile.getName();
			if (prefix.endsWith(".zip"))
				prefix = prefix.substring(0, prefix.indexOf(".zip"));
			
			if (driver.rank == 0) {
				debug(driver.rank, null, "Writing all plots");
				CompoundFSSPlots.batchWritePlots(plots, dir, prefix, false);
				debug(driver.rank, null, "DONE writing all plots");
			}
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
