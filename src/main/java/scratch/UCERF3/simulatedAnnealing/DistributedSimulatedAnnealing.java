package scratch.UCERF3.simulatedAnnealing;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.time.StopWatch;

import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.IterationCompletionCriteria;
import scratch.UCERF3.utils.MatrixIO;
import mpi.MPI;

public class DistributedSimulatedAnnealing {
	
	private static final boolean D = true;
	private static final boolean DD = false;
	private static final SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");

	private int rank;
	private int size;

	private ThreadedSimulatedAnnealing annealer;

	private CompletionCriteria criteria;
	
	private CompletionCriteria subCompletion;
	
	private boolean startSubIterationsAtZero;

	private static final int TAG_BEST_RESULT = 6;

	private static final long WORK_DONE = -1;

	private double[] single_double_buf = new double[1];
	private double[] quad_double_buf = new double[4];
	private long[] single_long_buf = new long[1];
	private int[] single_int_buf = new int[1];
	
	private StopWatch workWatch;
	private StopWatch totWatch;
	private StopWatch commWatch;

	public DistributedSimulatedAnnealing(CompletionCriteria criteria, CompletionCriteria subCompletion,
			boolean startSubIterationsAtZero,
			ThreadedSimulatedAnnealing annealer) {
		rank = MPI.COMM_WORLD.Rank();
		size = MPI.COMM_WORLD.Size();
		
		ddebug("constructor start");
		
		this.annealer = annealer;
		this.criteria = criteria;
		
		this.subCompletion = subCompletion;
		this.startSubIterationsAtZero = startSubIterationsAtZero;
		
		ddebug("constructor end");
	}
	
	private void ddebug(String str) {
		if (DD) debug(str);
	}
	
	private void debug(String str) {
		if (!D)
			return;
		
		String print = "["+df.format(new Date());
		if (rank == 0)
			print += " MASTER]: "+str;
		else
			print += " WORKER "+rank+"]: "+str;
		System.out.println(print);
	}
	
	public boolean isMaster() {
		return rank == 0;
	}

	public void run() {
		debug("running");
		totWatch = new StopWatch();
		totWatch.start();
		if (isMaster())
			runMaster();
		else
			runWorker();
		totWatch.stop();
		debug("done running");
		if (D) {
			long totTime = totWatch.getTime();
			long workTime = workWatch.getTime();
			long commTime = commWatch.getTime();
			long otherTime = totTime - workTime - commTime;
			
			double totMins = totTime / 1000d / 60d;
			double workMins = workTime / 1000d / 60d;
			double commMins = commTime / 1000d / 60d;
			double otherMins = otherTime / 1000d / 60d;
			
			double workPercent = workMins / totMins * 100d;
			double commPercent = commMins / totMins * 100d;
			double otherPercent = otherMins / totMins * 100d;
			
			debug("Total run time: "+(float)totMins+" mins");
			debug("Work time: "+(float)workMins+" mins");
			debug("Work percentage: "+(float)workPercent+" %");
			debug("Communication time: "+(float)commMins+" mins");
			debug("Communication percentage: "+(float)commPercent+" %");
			debug("Other time: "+(float)otherMins+" mins");
			debug("Other percentage: "+(float)otherPercent+" %");
			
		}
		
		// make sure everyone is done before exiting.
		MPI.COMM_WORLD.Barrier();
	}
	
	private void bcastSingleLong(long val) {
		single_long_buf[0] = val;
		MPI.COMM_WORLD.Bcast(single_long_buf, 0, 1, MPI.LONG, 0);
	}

	private void runMaster() {
		ddebug("starting watch");
		StopWatch watch = new StopWatch();
		watch.start();
		
		double[] pool_double_buf = new double[size];
		long[] pool_long_buf = new long[size];
		
		double[] Ebest = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
				Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
		
		int cnt = 0;
		long iter = 0;
		while (!criteria.isSatisfied(watch, iter, Ebest, 0l)) { // TODO add perturbation tracking
			boolean dd = cnt % 1000 == 0;
			if (dd)
				ddebug("starting loop "+cnt+", iter: "+iter);
			else
				debug("starting loop "+cnt+", iter: "+iter);
			
			long startIter;
			if (startSubIterationsAtZero)
				startIter = 0;
			else
				startIter = iter;
			ddebug("sending start iteration");
			if (D) commWatch = new StopWatch();
			if (D) commWatch.start();
			bcastSingleLong(startIter);
			if (D) commWatch.suspend();
			
			// do work yourself
			iter = doWork(startIter, subCompletion);
			
			single_double_buf[0] = annealer.getBestEnergy()[0];
			ddebug("my best energy: "+single_double_buf[0]);
			ddebug("gathering best energy");
			if (D) commWatch.resume();
			MPI.COMM_WORLD.Gather(single_double_buf, 0, 1, MPI.DOUBLE, pool_double_buf, 0, 1, MPI.DOUBLE, 0);
			if (D) commWatch.suspend();
			
			ddebug("gathering iterations");
			single_long_buf[0] = iter;
			if (D) commWatch.resume();
			MPI.COMM_WORLD.Gather(single_long_buf, 0, 1, MPI.LONG, pool_long_buf, 0, 1, MPI.LONG, 0);
			if (D) commWatch.suspend();
			
			int bestRank = 0;
			
			double bestEnergy = Double.POSITIVE_INFINITY;
			
			for (int i=0; i<size; i++) {
				double energy = pool_double_buf[i];
				
				if (energy < bestEnergy) {
					bestEnergy = energy;
					bestRank = i;
				}
				if (pool_long_buf[i] > iter)
					iter = pool_long_buf[i];
			}
			
			if (dd)
				ddebug("Process "+bestRank+" has best solution with energy: "+Ebest);
			else
				debug("Process "+bestRank+" has best solution with energy: "+Ebest);
			
			ddebug("broadcasting best solution rank");
			single_int_buf[0] = bestRank;
			if (D) commWatch.resume();
			MPI.COMM_WORLD.Bcast(single_int_buf, 0, 1, MPI.INT, 0);
			if (D) commWatch.suspend();
			
			fetchSolution(bestRank);
			
			cnt++;
			// this is now done above
//			iter += subIterations;
		}
		
		if (D) commWatch.resume();
		bcastSingleLong(WORK_DONE);
		if (D) commWatch.suspend();
		
		watch.stop();
		
		ddebug("DONE");
	}
	
	private long doWork(long startIter, CompletionCriteria criteria) {
		criteria = ThreadedSimulatedAnnealing.getForStartIter(startIter, criteria);
		ddebug("starting my annealing. start Ebest: "+annealer.getBestEnergy()+
				", startIter: "+startIter+", criteria: "+criteria);
		if (workWatch == null) {
			workWatch = new StopWatch();
			workWatch.start();
		} else {
			workWatch.resume();
		}
		long iter = annealer.iterate(startIter, 0l, criteria)[0];
		workWatch.suspend();
		ddebug("done with my annealing. Ebest: "+annealer.getBestEnergy());
		return iter;
	}

	private long getBcastLong() {
		// buf, offset, count, type, source, tag
//		MPI.COMM_WORLD.Recv(single_long_buf, 0, 1, MPI.LONG, from, tag);
		MPI.COMM_WORLD.Bcast(single_long_buf, 0, 1, MPI.LONG, 0);
		return single_long_buf[0];
	}

	private void runWorker() {
		ddebug("getting start iteration");
		if (D) commWatch = new StopWatch();
		if (D) commWatch.start();
		long startIter = getBcastLong();
		if (D) commWatch.suspend();
		while (startIter != WORK_DONE) {
			long iter = doWork(startIter, subCompletion);

			int bestRank = reportResults(iter);
			
			fetchSolution(bestRank);

			ddebug("getting num iterations");
			startIter = getBcastLong();
		}
	}

	private int reportResults(long iter) {
		// send energy
		single_double_buf[0] = annealer.getBestEnergy()[0];
		ddebug("sending my best energy ("+single_double_buf[0]+")");
		if (D) commWatch.resume();
		MPI.COMM_WORLD.Gather(single_double_buf, 0, 1, MPI.DOUBLE, null, 0, 1, MPI.DOUBLE, 0);
		if (D) commWatch.suspend();
//		MPI.COMM_WORLD.Send(single_double_buf, 0, 1, MPI.DOUBLE, 0, TAG_ENGERGY);
		
		// send max iteration
		single_long_buf[0] = iter;
		ddebug("sending my best energy ("+single_double_buf[0]+")");
		if (D) commWatch.resume();
		MPI.COMM_WORLD.Gather(single_long_buf, 0, 1, MPI.LONG, null, 0, 1, MPI.LONG, 0);
		if (D) commWatch.suspend();

		// find out if we should send result
		ddebug("checking if i should send my result");
		if (D) commWatch.resume();
		MPI.COMM_WORLD.Bcast(single_int_buf, 0, 1, MPI.INT, 0);
		if (D) commWatch.suspend();
//		MPI.COMM_WORLD.Recv(single_boolean_buf, 0, 1, MPI.BOOLEAN, 0,
//				TAG_SHOULD_SEND_RESULT);
		return single_int_buf[0];
//		if (single_int_buf[0] == rank) {
//			// this means the master wants our result, lets report it
//			double[] sol = annealer.getBestSolution();
//			ddebug("sending my best solution");
//			if (D) commWatch.resume();
//			MPI.COMM_WORLD.Send(sol, 0, sol.length, MPI.DOUBLE, 0, TAG_BEST_RESULT);
//			if (D) commWatch.suspend();
//			
//			// distribute best solution
//			ddebug("distributing best solution");
//			if (D) commWatch.resume();
//			MPI.COMM_WORLD.Bcast(sol, 0, sol.length, MPI.DOUBLE, 0);
//			if (D) commWatch.suspend();
//		}
	}
	
	private void fetchSolution(int bestRank) {
		String actionWord;
		if (bestRank == rank)
			actionWord = "sending";
		else
			actionWord = "receiving";
		// receive energy
		ddebug(actionWord+" best energy");
//		MPI.COMM_WORLD.Recv(single_double_buf, 0, 1, MPI.DOUBLE, 0, TAG_BEST_ENGERGY);
		quad_double_buf = annealer.getBestEnergy();
		if (D) commWatch.resume();
		MPI.COMM_WORLD.Bcast(quad_double_buf, 0, quad_double_buf.length, MPI.DOUBLE, bestRank);
		if (D) commWatch.suspend();
		double[] Ebest = Arrays.copyOf(quad_double_buf, quad_double_buf.length);
		
		double[] sol = annealer.getBestSolution();
		ddebug(actionWord+" best solution");
//		MPI.COMM_WORLD.Recv(sol, 0, sol.length, MPI.DOUBLE, 0, TAG_BEST_RESULT);
		if (D) commWatch.resume();
		MPI.COMM_WORLD.Bcast(sol, 0, sol.length, MPI.DOUBLE, bestRank);
		if (D) commWatch.suspend();
		
		if (DD) {
			int numZero = 0;
			for (int i=0; i<sol.length; i++)
				if (sol[i] == 0)
					numZero++;
			ddebug("num zero: "+numZero);
		}
		
		ddebug(actionWord+" misfits");
		double[] misfit = annealer.getBestMisfit();
		if (D) commWatch.resume();
		MPI.COMM_WORLD.Bcast(misfit, 0, misfit.length, MPI.DOUBLE, bestRank);
		if (D) commWatch.suspend();
		
		double[] misfit_ineq = annealer.getBestInequalityMisfit();
		if (misfit_ineq != null) {
			ddebug(actionWord+" inequality misfits");
			if (D) commWatch.resume();
			MPI.COMM_WORLD.Bcast(misfit_ineq, 0, misfit_ineq.length, MPI.DOUBLE, bestRank);
			if (D) commWatch.suspend();
		}
		
		ddebug("setting my own results");
		annealer.setResults(Ebest, sol, misfit, misfit_ineq);
	}
	
	public static Options createOptions() {
		Options options = ThreadedSimulatedAnnealing.createOptions();
		
		Option dsubIterOption = new Option("ds", "dist-sub-completion", true,
				"number of distributed sub iterations (optional...defaults to subIterations)");
		dsubIterOption.setRequired(false);
		options.addOption(dsubIterOption);
		
		return options;
	}
	
	public static DistributedSimulatedAnnealing parseOptions(CommandLine cmd) throws IOException {
		ThreadedSimulatedAnnealing annealer = ThreadedSimulatedAnnealing.parseOptions(cmd);
		CompletionCriteria criteria = ThreadedSimulatedAnnealing.parseCompletionCriteria(cmd);
		
		CompletionCriteria subCompletion;
		if (cmd.hasOption("dist-sub-completion"))
			subCompletion = ThreadedSimulatedAnnealing.parseSubCompletionCriteria(
					cmd.getOptionValue("dist-sub-completion"));
		else
			subCompletion = annealer.getSubCompetionCriteria();
		
		return new DistributedSimulatedAnnealing(criteria, subCompletion,
				annealer.isStartSubIterationsAtZero(), annealer);
	}
	
	private void writeMetadata(File file, String[] args) throws IOException {
		FileWriter fw = new FileWriter(file);
		
		fw.write("Distributed Simulated Annealing run completed on "+new SimpleDateFormat().format(new Date())+"\n");
		fw.write(""+"\n");
		String argsStr = "";
		for (String arg : args)
			argsStr += " "+arg;
		fw.write("Arguments:"+argsStr+"\n");
		fw.write("Completion Criteria: "+criteria+"\n");
		fw.write("Number of nodes: "+size+"\n");
		fw.write("Threads per node: "+annealer.getNumThreads()+"\n");
		fw.write(""+"\n");
		fw.write("Solution size: "+annealer.getBestSolution().length+"\n");
		fw.write("Best energy: "+annealer.getBestEnergy()+"\n");
		long totTime = totWatch.getTime();
		double totMins = totTime / 1000d / 60d;
		fw.write("Total time: "+totMins+" mins\n");
		
		fw.close();
	}
	
	public static void main(String[] args) {
		args = MPI.Init(args);
		
		Options options = createOptions();
		
		CommandLineParser parser = new GnuParser();
		
		try {
			CommandLine cmd = parser.parse(options, args);
			
			DistributedSimulatedAnnealing dsa = parseOptions(cmd);
			
			File outputFile = new File(cmd.getOptionValue("solution-file"));
			
			dsa.run();
			
			if (dsa.isMaster()) {
				dsa.annealer.writeBestSolution(outputFile);
				
				File metadataFile = new File(outputFile.getAbsolutePath()+"_metadata.txt");
				dsa.writeMetadata(metadataFile, args);
			}
			
			System.out.println("DONE...exiting.");
			MPI.Finalize();
			System.exit(0);
		} catch (MissingOptionException e) {
			System.err.println(e.getMessage());
			ThreadedSimulatedAnnealing.printHelp(options);
		} catch (ParseException e) {
			System.err.println("Error parsing command line arguments:");
			e.printStackTrace();
			ThreadedSimulatedAnnealing.printHelp(options);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
