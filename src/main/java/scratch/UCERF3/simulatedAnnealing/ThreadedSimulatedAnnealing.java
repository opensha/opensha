package scratch.UCERF3.simulatedAnnealing;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.time.StopWatch;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import scratch.UCERF3.simulatedAnnealing.completion.AnnealingProgress;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.CompoundCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.EnergyChangeCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.EnergyCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.IterationCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.ProgressTrackingCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.TimeCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.VariableSubTimeCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.params.CoolingScheduleType;
import scratch.UCERF3.simulatedAnnealing.params.GenerationFunctionType;
import scratch.UCERF3.simulatedAnnealing.params.NonnegativityConstraintType;
import scratch.UCERF3.utils.MatrixIO;

public class ThreadedSimulatedAnnealing implements SimulatedAnnealing {
	
	private static final boolean D = true;
	
	public static final String XML_METADATA_NAME= "ThreadedSimulatedAnnealing";
	
	private CompletionCriteria subCompletionCriteria;
	private boolean startSubIterationsAtZero;
	private TimeCompletionCriteria checkPointCriteria;
	private File checkPointFileBase;
	
	private int numThreads;
	private List<? extends SimulatedAnnealing> sas;
	
	private ExecutorService exec;
	
	private double[] Ebest =  { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
			Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
	private double[] xbest = null;
	private int numNonZero;
	private double[] misfit = null;
	private double[] misfit_ineq = null;
	
	private double[] initialState;
	
	private List<ConstraintRange> constraintRanges;
	
	private DoubleMatrix2D A;
	private DoubleMatrix2D A_ineq;
	
	private boolean average = false;
	
	private boolean verbose = D;

	private double relativeSmoothnessWt;
	
	public ThreadedSimulatedAnnealing(
			DoubleMatrix2D A, double[] d, double[] initialState,
			int numThreads, CompletionCriteria subCompetionCriteria) {
		this(A, d, initialState, 0d, null, null, numThreads, subCompetionCriteria);
	}
	
	public ThreadedSimulatedAnnealing(
			DoubleMatrix2D A, double[] d, double[] initialState, double relativeSmoothnessWt, 
			DoubleMatrix2D A_ineq,  double[] d_ineq, int numThreads, CompletionCriteria subCompetionCriteria) {
		this.relativeSmoothnessWt = relativeSmoothnessWt;
		Preconditions.checkState(numThreads > 0, "Must have at least 1 thread");
		
		// list of serial SA instances for each thread
		List<SerialSimulatedAnnealing> sas = new ArrayList<SerialSimulatedAnnealing>();
		for (int i=0; i<numThreads; i++)
			sas.add(new SerialSimulatedAnnealing(A, d, initialState, relativeSmoothnessWt, A_ineq, d_ineq));
		init(sas, subCompetionCriteria);
	}
	
	public ThreadedSimulatedAnnealing(
			List<? extends SimulatedAnnealing> sas, CompletionCriteria subCompetionCriteria) {
		init(sas, subCompetionCriteria);
	}
		
	private void init(List<? extends SimulatedAnnealing> sas, CompletionCriteria subCompetionCriteria) {
		// SA inputs are checked in each serial SA constructor, no need to duplicate checks
		Preconditions.checkState(!sas.isEmpty(), "Must have at least 1 thread");
		Preconditions.checkNotNull(subCompetionCriteria, "subCompetionCriteria cannot be null");
		
		this.numThreads = sas.size();
		this.subCompletionCriteria = subCompetionCriteria;
		this.sas = sas;
		for (SimulatedAnnealing sa : sas)
			// make any downstream TSA's non-verbose
			if (sa instanceof ThreadedSimulatedAnnealing)
				((ThreadedSimulatedAnnealing)sa).verbose = false;
		
		SimulatedAnnealing sa0 = sas.get(0);
		this.initialState = sa0.getInitialSolution();
		
		xbest = sa0.getBestSolution();
		numNonZero = sa0.getNumNonZero();
		
		this.A = sa0.getA();
		this.A_ineq = sa0.getA_ineq();
	}
	
	public void setSubCompletionCriteria(CompletionCriteria subCompletionCriteria) {
		this.subCompletionCriteria = subCompletionCriteria;
	}
	
	public CompletionCriteria getSubCompetionCriteria() {
		return subCompletionCriteria;
	}
	
	public boolean isStartSubIterationsAtZero() {
		return startSubIterationsAtZero;
	}
	
	public void setStartSubIterationsAtZero(boolean startSubIterationsAtZero) {
		this.startSubIterationsAtZero = startSubIterationsAtZero;
	}
	
	public void setCheckPointCriteria(TimeCompletionCriteria checkPointCriteria, File checkPointFilePrefix) {
		this.checkPointCriteria = checkPointCriteria;
		this.checkPointFileBase = checkPointFilePrefix;
	}
	
	/**
	 * If true, then after each threaded round the average of each thread's best solution is taken (rather than the best)
	 * 
	 * @param average
	 */
	public void setAverage(boolean average) {
		this.average = average;
	}
	
	protected static CompletionCriteria getForStartIter(long startIter, CompletionCriteria subComp) {
		if (subComp instanceof IterationCompletionCriteria) {
			long iters = ((IterationCompletionCriteria)subComp).getMinIterations();
			subComp = new IterationCompletionCriteria(startIter + iters);
		}
		return subComp;
	}
	
	private class SACall implements Callable<SACall> {
		private SimulatedAnnealing sa;
		private CompletionCriteria subComp;
		private long startIter;
		private long endIter;
		private long startPerturbs;
		private long endPerturbs;
		
		private boolean fatal = false;
		private Throwable t;
		
		public SACall(SimulatedAnnealing sa, long startIter, long startPerturbs, CompletionCriteria subComp) {
			this.sa = sa;
			this.subComp = subComp;
			this.startIter = startIter;
			this.startPerturbs = startPerturbs;
		}
		
		@Override
		public SACall call() {
			try {
				long[] ret = sa.iterate(startIter, startPerturbs, getForStartIter(startIter, subComp));
				endIter = ret[0];
				endPerturbs = ret[1];
			} catch (Throwable t) {
				System.err.println("FATAL ERROR in thread!");
				t.printStackTrace();
				fatal = true;
				this.t = t;
			}
			return this;
		}
	}

	@Override
	public void setCalculationParams(CoolingScheduleType coolingFunc,
			NonnegativityConstraintType nonnegativeityConstraintAlgorithm,
			GenerationFunctionType perturbationFunc) {
		for (SimulatedAnnealing sa : sas)
			sa.setCalculationParams(coolingFunc, nonnegativeityConstraintAlgorithm, perturbationFunc);
	}

	@Override
	public CoolingScheduleType getCoolingFunc() {
		return sas.get(0).getCoolingFunc();
	}

	@Override
	public void setCoolingFunc(CoolingScheduleType coolingFunc) {
		for (SimulatedAnnealing sa : sas)
			sa.setCoolingFunc(coolingFunc);
	}

	@Override
	public NonnegativityConstraintType getNonnegativeityConstraintAlgorithm() {
		return sas.get(0).getNonnegativeityConstraintAlgorithm();
	}

	@Override
	public void setNonnegativeityConstraintAlgorithm(
			NonnegativityConstraintType nonnegativeityConstraintAlgorithm) {
		for (SimulatedAnnealing sa : sas)
			sa.setNonnegativeityConstraintAlgorithm(nonnegativeityConstraintAlgorithm);
	}

	@Override
	public GenerationFunctionType getPerturbationFunc() {
		return sas.get(0).getPerturbationFunc();
	}

	@Override
	public void setPerturbationFunc(GenerationFunctionType perturbationFunc) {
		for (SimulatedAnnealing sa : sas)
			sa.setPerturbationFunc(perturbationFunc);
	}

	@Override
	public void setRuptureSampler(IntegerPDF_FunctionSampler rupSampler) {
		for (SimulatedAnnealing sa : sas)
			sa.setRuptureSampler(rupSampler);
	}
	
	@Override
	public void setVariablePerturbationBasis(double[] variablePerturbBasis) {
		for (SimulatedAnnealing sa : sas)
			sa.setVariablePerturbationBasis(variablePerturbBasis);
	}

	@Override
	public double[] getBestSolution() {
		return xbest;
	}

	@Override
	public double[] getBestEnergy() {
		return Ebest;
	}
	
	@Override
	public double[] getBestMisfit() {
		return misfit;
	}

	@Override
	public double[] getBestInequalityMisfit() {
		return misfit_ineq;
	}
	
	@Override
	public void setResults(double[] Ebest, double[] xbest) {
		int numNonZero = 0;
		for (double x : xbest)
			if (x > 0)
				numNonZero++;
		setResults(Ebest, xbest, null, null, numNonZero);
	}

	@Override
	public void setResults(double[] Ebest, double[] xbest, double[] misfit, double[] misfit_ineq, int numNonZero) {
		this.Ebest = Ebest;
		this.xbest = xbest;
		this.misfit = misfit;
		this.misfit_ineq = misfit_ineq;
		this.numNonZero = numNonZero;
		for (SimulatedAnnealing sa : sas)
			sa.setResults(Ebest, xbest, misfit, misfit_ineq, numNonZero);
	}

	@Override
	public long iterate(long numIterations) {
		return iterate(0l, 0l, new IterationCompletionCriteria(numIterations))[0];
	}

	@Override
	public long iterate(CompletionCriteria completion) {
		return iterate(0l, 0l, completion)[0];
	}

	@Override
	public long[] iterate(long startIter, long startPerturbs, CompletionCriteria criteria) {
		if (verbose) System.out.println("Threaded Simulated Annealing starting with "+numThreads
				+" threads, "+criteria+", SUB: "+subCompletionCriteria);
		
		boolean rangeTrack = constraintRanges != null && !constraintRanges.isEmpty();
		if (rangeTrack && criteria instanceof ProgressTrackingCompletionCriteria)
			((ProgressTrackingCompletionCriteria)criteria).setConstraintRanges(constraintRanges);
		
		StopWatch watch = new StopWatch();
		watch.start();
		StopWatch checkPointWatch = null;
		long numCheckPoints = 0;
		if (checkPointCriteria != null) {
			checkPointWatch = new StopWatch();
			checkPointWatch.start();
		}
		long perturbs = startPerturbs;
		
		if (subCompletionCriteria instanceof VariableSubTimeCompletionCriteria)
			((VariableSubTimeCompletionCriteria)subCompletionCriteria).setGlobalCriteria(criteria);
		
		// little fix for force serial option
		if (criteria == subCompletionCriteria && criteria instanceof ProgressTrackingCompletionCriteria) {
			criteria = ((ProgressTrackingCompletionCriteria)criteria).getCriteria();
		}
		
		if (exec == null)
			exec = Executors.newFixedThreadPool(numThreads, new DaemonThreadFactory(average));
		
		int rounds = 0;
		long iter = startIter;
		double[] prevBestE = null;
		while (!criteria.isSatisfied(watch, iter, Ebest, perturbs, numNonZero)) {
			if (subCompletionCriteria instanceof VariableSubTimeCompletionCriteria)
				((VariableSubTimeCompletionCriteria)subCompletionCriteria).setGlobalState(watch, iter, Ebest, perturbs);
			
			// write checkpoint information if applicable
			if (checkPointCriteria != null &&
					checkPointCriteria.isSatisfied(checkPointWatch, iter, Ebest, perturbs, 0)) {
				numCheckPoints++;
				System.out.println("Writing checkpoint after "+iter+" iterations. Ebest: "
						+Doubles.join(", ", Ebest));
				long millis = checkPointCriteria.getMillis();
				millis *= numCheckPoints;
				String name = checkPointFileBase.getName()+"_checkpoint_"
						+TimeCompletionCriteria.getTimeStr(millis);
				File checkPointFile = new File(checkPointFileBase.getParentFile(), name+".bin");
				try {
					writeBestSolution(checkPointFile, null);
					writeRateVsRankPlot(checkPointFile.getParentFile(), name+"_rate_dist", null);
				} catch (IOException e) {
					// don't fail on a checkpoint, just continue
					e.printStackTrace();
				}
				checkPointWatch.reset();
				checkPointWatch.start();
			}
			
			List<Future<SACall>> futures = new ArrayList<>();
			
			// create the threads
			for (int i=0; i<numThreads; i++) {
				long start;
				if (startSubIterationsAtZero)
					start = 0l;
				else
					start = iter;
				futures.add(exec.submit(new SACall(sas.get(i), start, perturbs, subCompletionCriteria)));
			}
			
			if (average) {
				// average best solution from each
				double rateMult = 1d/(double)numThreads;
				double[] newE = null;
				double[] newX = null;
				double[] newMisfit = null;
				double[] newMisfitIneq = null;
				
				long prevPerturbs = perturbs;
				for (int i=0; i<numThreads; i++) {
					SACall thread;
					try {
						thread = futures.get(i).get();
					} catch (Exception e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					if (thread.fatal)
						throw ExceptionUtils.asRuntimeException(thread.t);
					SimulatedAnnealing sa = sas.get(i);
					double[] E = sa.getBestEnergy();
					double[] xbest = sa.getBestSolution();
					double[] misfit = sa.getBestMisfit();
					double[] misfit_ineq = sa.getBestInequalityMisfit();
					
					if (newE == null) {
						newE = new double[4];
						newX = new double[xbest.length];
						if (misfit != null)
							newMisfit = new double[misfit.length];
						if (misfit_ineq != null)
							newMisfitIneq = new double[misfit_ineq.length];
					}
					addScaled(newE, E, rateMult);
					addScaled(newX, xbest, rateMult);
					if (newMisfit != null)
						addScaled(newMisfit, misfit, rateMult);
					if (newMisfitIneq != null)
						addScaled(newMisfitIneq, misfit_ineq, rateMult);
					
					perturbs += (thread.endPerturbs-prevPerturbs);
					
					// now set the current iteration count to the max iteration achieved
					iter = Long.max(thread.endIter, iter);
				}
				numNonZero = 0;
				for (double x : newX)
					if (x > 0)
						numNonZero++;
				Ebest = newE;
				xbest = newX;
				misfit = newMisfit;
				misfit_ineq = newMisfitIneq;
			} else {
				// find best solution and max iteration count
				for (int i=0; i<numThreads; i++) {
					SACall thread;
					try {
						thread = futures.get(i).get();
					} catch (Exception e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					if (thread.fatal)
						throw ExceptionUtils.asRuntimeException(thread.t);
					SimulatedAnnealing sa = sas.get(i);
					double[] E = sa.getBestEnergy();
					if (E[0] < Ebest[0]) {
						Ebest = E;
						xbest = sa.getBestSolution();
						misfit = sa.getBestMisfit();
						misfit_ineq = sa.getBestInequalityMisfit();
						numNonZero = sa.getNumNonZero();
						// set the number of perturbations to the perturbation count
						// of the solution we're actually keeping
						perturbs = thread.endPerturbs;
					}
					
					// now set the current iteration count to the max iteration achieved
					iter = Long.max(thread.endIter, iter);
				}
			}
			
			rounds++;
			// this is now done in the loop above
//			iter += numSubIterations;
			
			if (rangeTrack) {
				// recalculate with constraint ranges
				Ebest = sas.get(0).calculateEnergy(xbest, misfit, misfit_ineq, constraintRanges);
			}
			
			if (verbose) {
				double secs = watch.getTime() / 1000d;
				int ips = (int)((double)iter/secs + 0.5);
				System.out.println("Threaded total round "+rounds+" DONE after "+timeStr(watch.getTime())
						+", "+cDF.format(iter)+" total iterations ("+cDF.format(ips)+" /sec).\t"
						+cDF.format(numNonZero)+"/"+cDF.format(xbest.length)
						+" = "+pDF.format((double)numNonZero/(double)xbest.length)+" non-zero rates.");
				System.out.println("Best energy after "+cDF.format(perturbs)+" total perturbations:");
				printEnergies(Ebest, prevBestE, constraintRanges);
				prevBestE = Ebest;
//						+Doubles.join(", ", Ebest));
			}
			
			// set next state in all SAs
			for (SimulatedAnnealing sa : sas)
				sa.setResults(Ebest, xbest, misfit, misfit_ineq, numNonZero);
		}
		
		watch.stop();
		
		if(verbose) {
			System.out.println("Threaded annealing schedule completed.");
			System.out.println("Done with Inversion after "+timeStr(watch.getTime())+".");
			System.out.println("Rounds: "+rounds);
			System.out.println("Total Iterations: "+iter);
			System.out.println("Total Perturbations: "+perturbs);
			System.out.println("Best energy:");
			printEnergies(Ebest, null, constraintRanges);
		}
		
		long[] ret = { iter, perturbs };
		return ret;
	}
	
	private static class DaemonThreadFactory implements ThreadFactory {
		
		private static int poolCount = 0;
		private int myPool;
		private int threadCount = 0;
		private boolean average;

	    public DaemonThreadFactory(boolean average) {
	    	synchronized (DaemonThreadFactory.class) {
	    		poolCount++;
	    	}
	    	this.myPool = poolCount;
			this.average = average;
		}

		@Override
	    public synchronized Thread newThread(final Runnable r) {
	        Thread t = new Thread(r);
	        String name = "TSA-pool-"+myPool;
	        threadCount++;
	        if (average)
	        	name += "-avg-"+threadCount;
	        else
	        	name += "-worker-"+threadCount;
	        t.setName(name);
	        t.setDaemon(true);
	        return t;
	    }
	}

	/**
	 * Shuts down the thread pool.
	 */
	public void shutdown(){
		if (exec != null){
			exec.shutdown();
			exec = null;
		}
	}
	
	public static String timeStr(long millis) {
		double secs = millis/1000d;
		if (secs < 60d)
			return tDF.format(secs)+" secs";
		double mins = secs/60d;
		if (mins < 60d)
			return twoPartTimeStr((int)mins, "min", secs - ((int)mins)*60d, "secs");
		double hours = mins/60d;
		return twoPartTimeStr((int)hours, "hour", mins - ((int)hours)*60d, "mins");
	}
	
	static String twoPartTimeStr(int first, String firstUnits, double remainder, String remainderUnits) {
		String ret = first+" "+firstUnits;
		if (!firstUnits.endsWith("s") && first > 1)
			ret += "s";
		if (remainder < 0.01)
			return ret;
		return ret+" "+tDF.format(remainder)+" "+remainderUnits;
	}
	
	private void printEnergies(double[] Ebest, double[] prev, List<ConstraintRange> constraintRanges) {
		int numIneq = A_ineq == null ? 0 : A_ineq.rows();
		printEnergies(Ebest, prev, constraintRanges, relativeSmoothnessWt, numIneq);
	}
	
	private static void printEnergies(double[] Ebest, double[] prev, List<ConstraintRange> constraintRanges,
			double entropyWeight, int numIneqRows) {
		List<String> strs = new ArrayList<>();
		for (int i=0; i<Ebest.length; i++) {
			String str;
			switch (i) {
			case 0:
				str = "Total:\t";
				break;
			case 1:
				if (entropyWeight > 0 || numIneqRows > 0)
					str = "Equality:\t";
				else
					// only equality, don't bother duplicating information
					continue;
				break;
			case 2:
				if (entropyWeight > 0)
					str = "Entropy:\t";
				else
					// entropy is disabled, don't print
					continue;
				break;
			case 3:
				if (numIneqRows > 0)
					str = "Inequality:\t";
				else
					// inequality is disabled, don't print
					continue;
				break;

			default:
				int ind = i-4;
				if (constraintRanges == null || ind >= constraintRanges.size() || constraintRanges.get(ind) == null)
					str = "Constraint "+ind+":\t";
				else
					str = constraintRanges.get(ind).shortName+":\t";
				break;
			}
			str += (float)Ebest[i]+"";
			if (prev != null) {
				double diff = Ebest[i]-prev[i];
				str += " (";
				if (diff > 0)
					str += "+";
				str += pDF.format(diff/prev[i])+")";
			}
			strs.add(str);
		}
		int cols;
		if (strs.size() > 9)
			cols = 4;
		else if (strs.size() > 4)
			cols = 3;
		else
			cols = strs.size();
		int lines = strs.size()/cols;
		if (strs.size() % cols != 0)
			lines++;
		int ind = 0;
		for (int l=0; l<lines; l++) {
			StringBuilder str = new StringBuilder();
			for (int c=0; c<cols && ind<strs.size(); c++)
				str.append("\t").append(strs.get(ind++));
			System.out.println(str.toString());
		}
	}
	
	private static void addScaled(double[] dest, double[] source, double scale) {
		for (int i=0; i<dest.length; i++)
			dest[i] += source[i]*scale;
	}
	
	private static DecimalFormat tDF = new DecimalFormat("0.#");
	private static DecimalFormat cDF = new DecimalFormat("#");
	static {
		cDF.setGroupingUsed(true);
		cDF.setGroupingSize(3);
	}
	private static DecimalFormat pDF = new DecimalFormat("0.00%");
	
	/**
	 * Sets the random number generator used - helpful for reproducing results for testing purposes
	 * @param r
	 */
	public void setRandom(Random r) {
		if (sas.size() == 1) {
			sas.get(0).setRandom(r);
		} else {
			for (SimulatedAnnealing sa : sas)
				sa.setRandom(new Random(r.nextLong()));
		}
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
	
	public int getNumThreads() {
		return numThreads;
	}
	
	public void setNumThreads(int numThreads) {
		Preconditions.checkState(numThreads <= this.numThreads,
				"Can only decrease number of threads for now");
		this.numThreads = numThreads;
		while (sas.size() > numThreads)
			sas.remove(sas.size()-1);
	}
	
	public void setConstraintRanges(List<ConstraintRange> constraintRanges) {
		// don't set it in downstream ones, we'll calculate range-specific values if needed at the end of each sub-iteration
		this.constraintRanges = constraintRanges;
	}
	
	public static Options createOptionsNoInputs() {
		Options ops = SerialSimulatedAnnealing.createOptions();
		
		Option subOption = new Option("s", "sub-completion", true, "number of sub iterations. Optionally, append 's'" +
		" to specify in seconds or 'm' to specify in minutes instead of iterations. You can also specify a range of times in the" +
		" form 'time,time'.");
		subOption.setRequired(true);
		ops.addOption(subOption);
		
		Option numThreadsOption = new Option("t", "num-threads", true, "number of threads (percentage of available" +
				" can also be specified, for example, '50%')");
		numThreadsOption.setRequired(true);
		ops.addOption(numThreadsOption);
		
		Option solutionFileOption = new Option("sol", "solution-file", true, "file to store solution");
		solutionFileOption.setRequired(false);
		ops.addOption(solutionFileOption);
		
		// Completion Criteria
		Option timeOption = new Option("time", "completion-time", true, "time to anneal. append 's' for secionds," +
				" 'm' for minutes, 'h' for hours. default is millis.");
		timeOption.setRequired(false);
		ops.addOption(timeOption);
		
		Option iterOption = new Option("iter", "completion-iterations", true, "num iterations to anneal");
		iterOption.setRequired(false);
		ops.addOption(iterOption);
		
		Option energyOption = new Option("energy", "completion-energy", true, "energy maximum to anneal to");
		energyOption.setRequired(false);
		ops.addOption(energyOption);
		
		Option deltaEnergyOption = new Option("delenergy", "completion-delta-energy", true, "energy change completion" +
				" criteria. Format: <time>,<%>,<diff>. For example: 60,1.5,2 means a look back period of 60 minutes," +
				" a minimum percent improvement of 1.5%, and a minimum actual energy change of 2.");
		deltaEnergyOption.setRequired(false);
		ops.addOption(deltaEnergyOption);
		
		// constraint weights
		Option smoothnessWeightOption = new Option("smoothness", "smoothness-weight", true, "weight for the entropy constraint");
		smoothnessWeightOption.setRequired(false);
		ops.addOption(smoothnessWeightOption);
		
		// other
		Option initial = new Option("i", "initial-state-file", true, "initial state file" +
				" (optional...default is all zeros)");
		initial.setRequired(false);
		ops.addOption(initial);
		
		Option progressFileOption = new Option("p", "progress-file", true, "file to store progress results");
		progressFileOption.setRequired(false);
		ops.addOption(progressFileOption);
		
		Option subIterationsStartOption = new Option("zero", "start-sub-iters-zero", false,
				"flag to start all sub iterations at zero instead of the true iteration count");
		subIterationsStartOption.setRequired(false);
		ops.addOption(subIterationsStartOption);
		
		Option checkPointOption = new Option("chk", "checkpoint", true, "will write out solutions on the given time " +
				"interval. append 's' for secionds, 'm' for minutes, 'h' for hours. default is millis.");
		checkPointOption.setRequired(false);
		ops.addOption(checkPointOption);
		
		Option plotsOption = new Option("plot", "plots", false, "write a variety of plots to the filesystem" +
				" when annealing has completed");
		plotsOption.setRequired(false);
		ops.addOption(plotsOption);
		
		return ops;
	}
	
	public static Options createOptions() {
		Options ops = createOptionsNoInputs();
		
		// REQUIRED
		// inputs can now be supplied in a single zip file if needed, thus individual ones not required
		Option aMatrix = new Option("a", "a-matrix-file", true, "A matrix file");
		aMatrix.setRequired(false);
		ops.addOption(aMatrix);
		
		Option dMatrix = new Option("d", "d-matrix-file", true, "D matrix file");
		dMatrix.setRequired(false);
		ops.addOption(dMatrix);
		
		Option a_MFDMatrix = new Option("aineq", "a-ineq-matrix-file", true, "A inequality matrix file");
		a_MFDMatrix.setRequired(false);
		ops.addOption(a_MFDMatrix);
		
		Option d_MFDMatrix = new Option("dineq", "d-ineq-matrix-file", true, "D inequality matrix file");
		d_MFDMatrix.setRequired(false);
		ops.addOption(d_MFDMatrix);
		
		Option zipInputs = new Option("zip", "zip-file", true, "Zip file containing all inputs. " +
				"File names must be a.bin, d.bin, and optionally: initial.bin, a_ineq.bin, d_ineq.bin, minimumRuptureRates.bin, metadata.txt");
		zipInputs.setRequired(false);
		ops.addOption(zipInputs);
		
		return ops;
	}
	
	public static String subCompletionCriteriaToArgument(CompletionCriteria subCompletion) {
		return subCompletionCriteriaToArgument("sub-completion", subCompletion);
	}
	
	public static String subCompletionCriteriaToArgument(String argName, CompletionCriteria subCompletion) {
		return "--"+argName+" "+subCompletionArgVal(subCompletion);
	}
	
	public static String subCompletionArgVal(CompletionCriteria subCompletion) {
		if (subCompletion instanceof IterationCompletionCriteria)
			return ""+((IterationCompletionCriteria)subCompletion).getMinIterations();
		else if (subCompletion instanceof TimeCompletionCriteria)
			return ((TimeCompletionCriteria)subCompletion).getTimeStr();
		else if (subCompletion instanceof VariableSubTimeCompletionCriteria) {
			return ((VariableSubTimeCompletionCriteria)subCompletion).getTimeStr();
		}
		else
			throw new UnsupportedOperationException("Can't create command line argument for: "+subCompletion);
	}
	
	public static String completionCriteriaToArgument(CompletionCriteria criteria) {
		if (criteria instanceof EnergyCompletionCriteria) {
			return "--completion-energy "+((EnergyCompletionCriteria)criteria).getMaxEnergy();
		} else if (criteria instanceof IterationCompletionCriteria) {
			return "--completion-iterations "+((IterationCompletionCriteria)criteria).getMinIterations();
		} else if (criteria instanceof TimeCompletionCriteria) {
			return "--completion-time "+((TimeCompletionCriteria)criteria).getTimeStr();
		} else if (criteria instanceof CompoundCompletionCriteria) {
			String str = null;
			for (CompletionCriteria subCriteria : ((CompoundCompletionCriteria)criteria).getCriterias()) {
				if (str == null)
					str = "";
				else
					str += " ";
				str += completionCriteriaToArgument(subCriteria);
			}
			return str;
		} else if (criteria instanceof ProgressTrackingCompletionCriteria) {
			throw new IllegalArgumentException("ProgressTrackingCompletionCriteria not supported," +
					"use --progress-file instead");
		} else
			throw new UnsupportedOperationException("Can't create command line argument for: "+criteria);
	}
	
	public static CompletionCriteria parseCompletionCriteria(CommandLine cmd) {
		ArrayList<CompletionCriteria> criterias = new ArrayList<CompletionCriteria>();
		
		if (cmd.hasOption("time")) {
			String timeStr = cmd.getOptionValue("time");
			long time;
			if (timeStr.toLowerCase().endsWith("s"))
				time = (long)(Double.parseDouble(timeStr.substring(0, timeStr.length()-1)) * 1000);
			else if (timeStr.toLowerCase().endsWith("m"))
				time = (long)(Double.parseDouble(timeStr.substring(0, timeStr.length()-1)) * 1000 * 60);
			else if (timeStr.toLowerCase().endsWith("h"))
				time = (long)(Double.parseDouble(timeStr.substring(0, timeStr.length()-1)) * 1000 * 60 * 60);
			else
				time = Long.parseLong(timeStr);
			
			criterias.add(new TimeCompletionCriteria(time));
		}
		if (cmd.hasOption("iter"))
			criterias.add(new IterationCompletionCriteria(Long.parseLong(cmd.getOptionValue("iter"))));
		if (cmd.hasOption("energy"))
			criterias.add(new EnergyCompletionCriteria(Double.parseDouble(cmd.getOptionValue("energy"))));
		if (cmd.hasOption("delenergy"))
			criterias.add(EnergyChangeCompletionCriteria.fromCommandLineArgument(
					cmd.getOptionValue("delenergy")));
		
		CompletionCriteria criteria;
		if (criterias.size() == 0)
			throw new IllegalArgumentException("must specify at least one completion criteria!");
		else if (criterias.size() == 1)
			criteria = criterias.get(0);
		else
			criteria = new CompoundCompletionCriteria(criterias);
		
		if (cmd.hasOption("progress-file")) {
			File progressFile = new File(cmd.getOptionValue("progress-file"));
			criteria = new ProgressTrackingCompletionCriteria(criteria, progressFile);
		}
		
		return criteria;
	}
	
	public static CompletionCriteria parseSubCompletionCriteria(String optionVal) {
		if (optionVal.contains(",")) {
			String[] times = optionVal.split(",");
			Preconditions.checkArgument(times.length == 2, "must specify exactly 2 times if using multiple option: "+optionVal);
			long maxTime = TimeCompletionCriteria.parseTimeString(times[0]);
			long minTime = TimeCompletionCriteria.parseTimeString(times[1]);
			Preconditions.checkArgument(maxTime >= minTime, "max must be greater than min! ("+optionVal+")");
			
			return new VariableSubTimeCompletionCriteria(maxTime, minTime);
		}
		if (optionVal.endsWith("s") || optionVal.endsWith("m") || optionVal.endsWith("h") || optionVal.endsWith("mi")) {
			return TimeCompletionCriteria.fromTimeString(optionVal);
		}
		return new IterationCompletionCriteria(Long.parseLong(optionVal));
	}
	
	public static int parseNumThreads(String threadsVal) {
		if (threadsVal.endsWith("%")) {
			threadsVal = threadsVal.substring(0, threadsVal.length()-1);
			double threadPercent = Double.parseDouble(threadsVal);
			int avail = Runtime.getRuntime().availableProcessors();
			double threadDouble = avail * threadPercent * 0.01;
			int numThreads = (int)(threadDouble + 0.5);
			System.out.println("Percentage based threads..."+threadsVal+"% of "+avail+" = "
					+threadDouble+" = "+numThreads);
			
			return numThreads < 1 ? 1 : numThreads;
		}
		return Integer.parseInt(threadsVal);
	}
	
	public static ThreadedSimulatedAnnealing parseOptions(CommandLine cmd) throws IOException {
		DoubleMatrix2D A = null; // can't stay null
		double[] d = null; // can't stay null
		double[] initialState = null; // can be null, for now
		DoubleMatrix2D A_ineq = null; // can be null
		double[] d_ineq = null; // can be null
		
		List<ConstraintRange> constraintRanges = null;
		
		if (cmd.hasOption("zip")) {
			File zipFile = new File(cmd.getOptionValue("zip"));
			if (D) System.out.println("Opening zip file: "+zipFile.getAbsolutePath());
			ZipFile zip = new ZipFile(zipFile);
			
			ZipEntry a_entry = zip.getEntry("a.bin");
			A = MatrixIO.loadSparse(new BufferedInputStream(zip.getInputStream(a_entry)), SparseCCDoubleMatrix2D.class);
			ZipEntry d_entry = zip.getEntry("d.bin");
			d = MatrixIO.doubleArrayFromInputStream(new BufferedInputStream(zip.getInputStream(d_entry)), A.rows()*8);
			
			ZipEntry a_ineq_entry = zip.getEntry("a_ineq.bin");
			if (a_ineq_entry != null)
				A_ineq = MatrixIO.loadSparse(new BufferedInputStream(zip.getInputStream(a_ineq_entry)), SparseCCDoubleMatrix2D.class);
			ZipEntry d_ineq_entry = zip.getEntry("d_ineq.bin");
			if (d_ineq_entry != null && A_ineq != null)
				d_ineq = MatrixIO.doubleArrayFromInputStream(new BufferedInputStream(zip.getInputStream(d_ineq_entry)), A_ineq.rows()*8);
			
			ZipEntry initial_entry = zip.getEntry("initial.bin");
			if (initial_entry != null) {
				initialState = MatrixIO.doubleArrayFromInputStream(
						new BufferedInputStream(zip.getInputStream(initial_entry)), A.columns()*8);
			}
			
			ZipEntry rangeEntry = zip.getEntry("constraintRanges.csv");
			if (rangeEntry != null) {
				constraintRanges = new ArrayList<>();
				CSVFile<String> rangeCSV = CSVFile.readStream(zip.getInputStream(rangeEntry), true);
				for (int row=1; row<rangeCSV.getNumRows(); row++) {
					String name = rangeCSV.get(row, 0);
					String shortName = rangeCSV.get(row, 1);
					boolean inequality = rangeCSV.getBoolean(row, 2);
					int startRow = rangeCSV.getInt(row, 3);
					int endRow = rangeCSV.getInt(row, 4);
					constraintRanges.add(new ConstraintRange(name, shortName, startRow, endRow, inequality));
				}
			}
			
			zip.close();
		} else {
			File aFile = new File(cmd.getOptionValue("a"));
			if (D) System.out.println("Loading A matrix from: "+aFile.getAbsolutePath());
			A = MatrixIO.loadSparse(aFile, SparseCCDoubleMatrix2D.class);
			
			File dFile = new File(cmd.getOptionValue("d"));
			if (D) System.out.println("Loading d matrix from: "+dFile.getAbsolutePath());
			d = MatrixIO.doubleArrayFromFile(dFile);
			
			if (cmd.hasOption("aineq")) {
				File a_ineqFile = new File(cmd.getOptionValue("aineq"));
				if (D) System.out.println("Loading A_ineq matrix from: "+a_ineqFile.getAbsolutePath());
				A_ineq = MatrixIO.loadSparse(a_ineqFile, SparseCCDoubleMatrix2D.class);
			}
			
			if (cmd.hasOption("dineq")) {
				File d_ineqFile = new File(cmd.getOptionValue("dineq"));
				if (D) System.out.println("Loading d_ineq matrix from: "+d_ineqFile.getAbsolutePath());
				d_ineq = MatrixIO.doubleArrayFromFile(d_ineqFile);
			}
			
			if (cmd.hasOption("i")) {
				File initialFile = new File(cmd.getOptionValue("i"));
				if (D) System.out.println("Loading initialState from: "+initialFile.getAbsolutePath());
				initialState = MatrixIO.doubleArrayFromFile(initialFile);
			}
		}
		
		return parseOptions(cmd, A, d, initialState, A_ineq, d_ineq, constraintRanges);
	}
	
	public static ThreadedSimulatedAnnealing parseOptions(CommandLine cmd,
			DoubleMatrix2D A,
			double[] d,
			double[] initialState,
			DoubleMatrix2D A_ineq,
			double[] d_ineq,
			List<ConstraintRange> constraintRanges) throws IOException {
		
		// load other weights
		double relativeSmoothnessWt;
		if (cmd.hasOption("smoothness"))
			relativeSmoothnessWt = Double.parseDouble(cmd.getOptionValue("smoothness"));
		else
			relativeSmoothnessWt = 0;
		
		if (initialState ==  null)
			// if we still don't have an initial state, use all zeros
			initialState = new double[A.columns()];
		
		CompletionCriteria subCompletionCriteria = parseSubCompletionCriteria(cmd.getOptionValue("s"));
		
		int numThreads = parseNumThreads(cmd.getOptionValue("t"));
		
		ThreadedSimulatedAnnealing tsa =
			new ThreadedSimulatedAnnealing(A, d, initialState,
					relativeSmoothnessWt, A_ineq, d_ineq, numThreads, subCompletionCriteria);
		
		for (SimulatedAnnealing sa : tsa.sas)
			((SerialSimulatedAnnealing)sa).setCalculationParamsFromOptions(cmd);
		
		if (cmd.hasOption("zero"))
			tsa.setStartSubIterationsAtZero(true);
		
		if (cmd.hasOption("checkpoint")) {
			String time = cmd.getOptionValue("checkpoint");
			TimeCompletionCriteria checkPointCriteria = TimeCompletionCriteria.fromTimeString(time);
			File checkPointFilePrefix;
			if (cmd.hasOption("solution-file")) {
				String outputStr = cmd.getOptionValue("solution-file");
				checkPointFilePrefix = getFileWithoutBinSuffix(outputStr);
			} else {
				// assume this is being called from CommandLineInversionRunner
				File dir = new File(cmd.getOptionValue("directory"));
				String prefix = cmd.getOptionValue("branch-prefix");
				checkPointFilePrefix = new File(dir, prefix);
			}
			tsa.setCheckPointCriteria(checkPointCriteria, checkPointFilePrefix);
		}
		
		tsa.setConstraintRanges(constraintRanges);
		
		return tsa;
	}
	
	private static File getFileWithoutBinSuffix(String path) {
		if (path.endsWith(".bin"))
			path = path.substring(0, path.lastIndexOf(".bin"));
		return new File(path);
	}
	
	public static void printHelp(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(
				ClassUtils.getClassNameWithoutPackage(ThreadedSimulatedAnnealing.class),
				options, true );
		System.exit(2);
	}
	
	public void writeBestSolution(File outputFile, double[] minimumRuptureRates) throws IOException {
		double[] solution = getBestSolution();
		if (minimumRuptureRates != null) {
			String outputFilePath = outputFile.getAbsolutePath();
			if (outputFilePath.endsWith(".bin"))
				outputFilePath = outputFilePath.substring(0, outputFilePath.length()-4);
			File outputOrigFile = new File(outputFilePath+"_noMinRates.bin");
			System.out.println("Writing original solution to: "+outputOrigFile.getAbsolutePath());
			MatrixIO.doubleArrayToFile(solution, outputOrigFile);
			
			System.out.println("Applying minimum rupture rates");
			solution = InversionInputGenerator.adjustSolutionForWaterLevel(solution, minimumRuptureRates);
		}
		
		System.out.println("Writing solution to: "+outputFile.getAbsolutePath());
		MatrixIO.doubleArrayToFile(solution, outputFile);
	}
	
	public Map<ConstraintRange, Double> getEnergies() {
		if (constraintRanges == null)
			return null;
		Map<ConstraintRange, Double> energies = Maps.newHashMap();
		
		double[] e = getBestEnergy();
		for (int i=4; i<e.length && (i-4)<constraintRanges.size(); i++)
			energies.put(constraintRanges.get(i-4), e[i]);
		
		return energies;
	}
	
	public String getMetadata(String[] args, CompletionCriteria criteria) {
		StringBuilder builder = new StringBuilder();
		builder.append("Distributed Simulated Annealing run completed on "
				+new SimpleDateFormat().format(new Date())+"\n");
		builder.append(""+"\n");
		String argsStr = "";
		for (String arg : args)
			argsStr += " "+arg;
		builder.append("Arguments:"+argsStr+"\n");
		builder.append("Completion Criteria: "+criteria+"\n");
		builder.append("Threads per node: "+getNumThreads()+"\n");
		builder.append(""+"\n");
		builder.append("Solution size: "+getBestSolution().length+"\n");
		builder.append("A matrix size: "+A.rows()+"x"+A.columns()+"\n");
		if (A_ineq == null)
			builder.append("A_ineq matrix size: (null)\n");
		else
			builder.append("A_ineq matrix size: "+A_ineq.rows()+"x"+A_ineq.columns()+"\n");
		double[] e = getBestEnergy();
		builder.append("Best energy: "+Doubles.join(", ", e)+"\n");
		if (constraintRanges != null) {
			builder.append("Energy type breakdown\n");
			for (int i=4; i<e.length && (i-4)<constraintRanges.size(); i++) {
				builder.append("\t"+constraintRanges.get(i-4).shortName+"\tenergy: "+e[i]+"\n");
			}
		}
		if (criteria instanceof ProgressTrackingCompletionCriteria) {
			ProgressTrackingCompletionCriteria track = (ProgressTrackingCompletionCriteria)criteria;
			AnnealingProgress progress = track.getProgress();
			int numSteps = progress.size();
			long millis = progress.getTime(numSteps-1);
			double totMins = millis / 1000d / 60d;
			builder.append("Total time: "+totMins+" mins\n");
			long iters = progress.getIterations(numSteps-1);
			long perturbs = progress.getNumPerturbations(numSteps-1);
			builder.append("Total iterations: "+iters+"\n");
			float pertPercent = (float)(((double)perturbs / (double)iters) * 100d);
			builder.append("Total perturbations: "+perturbs+" ("+pertPercent+" %)\n");
		}
		
		return builder.toString();
	}
	
	public void writeMetadata(File file, String[] args, CompletionCriteria criteria) throws IOException {
		FileWriter fw = new FileWriter(file);
		
		fw.write(getMetadata(args, criteria).toString());
		
		fw.close();
	}
	
	public static void main(String[] args) {
		Options options = createOptions();
		
		CommandLineParser parser = new DefaultParser();
		
		try {
			CommandLine cmd = parser.parse(options, args);
			
			ThreadedSimulatedAnnealing tsa = parseOptions(cmd);
			
			File outputFile = new File(cmd.getOptionValue("solution-file"));
			
			CompletionCriteria criteria = parseCompletionCriteria(cmd);
			
			tsa.iterate(criteria);
			
			tsa.writeBestSolution(outputFile, null);
			File prefix = getFileWithoutBinSuffix(outputFile.getAbsolutePath());
			tsa.writeMetadata(new File(prefix.getParentFile(), prefix.getName()+"_metadata.txt"),
					args, criteria);
			tsa.writePlots(criteria, prefix.getParentFile(), prefix.getName(), null);
			
			System.out.println("DONE...exiting.");
			System.exit(0);
		} catch (MissingOptionException e) {
			System.err.println(e.getMessage());
			printHelp(options);
		} catch (ParseException e) {
			System.err.println("Error parsing command line arguments:");
			e.printStackTrace();
			printHelp(options);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	@Override
	public double[] getInitialSolution() {
		return initialState;
	}

	@Override
	public int getNumNonZero() {
		return numNonZero;
	}

	@Override
	public DoubleMatrix2D getA_ineq() {
		return A_ineq;
	}

	@Override
	public DoubleMatrix2D getA() {
		return A;
	}

	@Override
	public double[] getD() {
		return sas.get(0).getD();
	}

	@Override
	public double[] getD_ineq() {
		return sas.get(0).getD_ineq();
	}

	@Override
	public double[] calculateEnergy(double[] solution) {
		return sas.get(0).calculateEnergy(solution);
	}

	@Override
	public double[] calculateEnergy(double[] solution, double[] misfit, double[] misfit_ineq) {
		return sas.get(0).calculateEnergy(solution, misfit, misfit_ineq);
	}

	@Override
	public double[] calculateEnergy(double[] solution, double[] misfit, double[] misfit_ineq,
			List<ConstraintRange> constraintRanges) {
		return sas.get(0).calculateEnergy(solution, misfit, misfit_ineq, constraintRanges);
	}

}
