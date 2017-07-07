package org.opensha.sra.calc.parallel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.hpc.mpj.taskDispatch.MPJTaskCalculator;
import org.opensha.commons.hpc.mpj.taskDispatch.PostBatchHook;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.AbstractEpistemicListERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.imr.AbstractIMR;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sra.gui.portfolioeal.Asset;
import org.opensha.sra.gui.portfolioeal.CalculationExceptionHandler;
import org.opensha.sra.gui.portfolioeal.Portfolio;
import org.opensha.sra.gui.portfolioeal.PortfolioEALCalculatorController;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import mpi.MPI;
import scratch.UCERF3.erf.FaultSystemSolutionERF;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;

public class MPJ_CondLossCalc extends MPJTaskCalculator implements CalculationExceptionHandler, PostBatchHook {
	
	public static final String BATCH_ELEMENT_NAME = "BatchCalculation";
	
	protected List<Asset> assets;
//	protected double maxSourceDistance = 200; // TODO set
	
	private double[][] my_results;
	private int numRups;
	
	private boolean keepTractResults = false;
	private File tractMainDir;
	private File tractNodeDir;
	private Deque<TractWriteable> tractWriteQueue;
//	private List<String> tractsSorted;
//	private Map<String, double[][]> tractResults;
	
	private ThreadedCondLossCalc calc;
	
	private File outputFile;
	
	private ERF refERF;
	
	private boolean gzip = false;
	
	private static final boolean FILE_DEBUG = false;
	
	public MPJ_CondLossCalc(CommandLine cmd, Portfolio portfolio, Element el) throws IOException, DocumentException, InvocationTargetException {
		this(cmd, portfolio, el, null);
	}
	
	public MPJ_CondLossCalc(CommandLine cmd, Portfolio portfolio, Element el, File outputFile) throws IOException, DocumentException, InvocationTargetException {
		super(cmd);
		
		assets = portfolio.getAssetList();
		System.gc();
		
		if (outputFile == null)
			outputFile = new File(el.attributeValue("outputFile"));
		this.outputFile = outputFile;
		
		int numThreads = getNumThreads();
		
		if (rank == 0) {
			debug("num threads: "+numThreads);
		}
		
		int numERFs;
		if (cmd.hasOption("mult-erfs"))
			numERFs = numThreads; // could set to 1 for single instance
		else
			numERFs = 1;
		
		gzip = cmd.hasOption("gzip");
		
		debug("updating ERFs");
		ERF[] erfs = new ERF[numERFs];
		for (int i=0; i<numERFs; i++) {
			erfs[i] = loadERF(el);
			erfs[i].updateForecast();
		}
		debug("done updating ERFs");
		
		refERF = erfs[0];
		
		my_results = new double[refERF.getNumSources()][];
		for (int sourceID=0; sourceID<refERF.getNumSources(); sourceID++) {
			my_results[sourceID] = new double[refERF.getNumRuptures(sourceID)];
			numRups += my_results[sourceID].length;
		}
		
//		ERF erf = loadERF(el);
//		erf.updateForecast();
		
		ScalarIMR[] imrs = new ScalarIMR[numThreads];
		for (int i=0; i<imrs.length; i++) {
			imrs[i] = (ScalarIMR)AbstractIMR.fromXMLMetadata(el.element(AbstractIMR.XML_METADATA_NAME), null);
		}
		
//		OpenSHA default
//		0, 5.25,  5.75, 6.25,  6.75, 7.25,  9
//		0, 25,    40,   60,    80,   100,   500
//		ArbitrarilyDiscretizedFunc magThreshFunc = new MagDistCutoffParam().getDefaultValue();
		
		// from Keith 1/9/14
//		5.25  60 km
//		7.25 200 km
//		9.00 500 km
		ArbitrarilyDiscretizedFunc magThreshFunc = new ArbitrarilyDiscretizedFunc();
		magThreshFunc.set(0d,	0.00);
		magThreshFunc.set(60d,	5.25);
		magThreshFunc.set(200d,	7.25);
		magThreshFunc.set(500d,	9.00);
		
		keepTractResults = cmd.hasOption("tract-results");
		if (keepTractResults) {
			shuffle = false; // will keep individual assets on fewer nodes
			if (rank == 0 && numThreads > 60)
				numThreads -= 2;
			this.postBatchHook = this;
			String prefix = this.outputFile.getName();
			if (prefix.toLowerCase().endsWith(".bin"))
				prefix = prefix.substring(0, prefix.lastIndexOf("."));
			tractMainDir = new File(outputFile.getParentFile(), prefix+"_tract_results");
			if (rank == 0) {
				if (tractMainDir.exists()) {
					// remove old results
					for (File file : tractMainDir.listFiles()) {
						if (file.getName().endsWith(".bin") || file.getName().endsWith(".bin.gz")) {
							debug("Deleting old stale tract file: "+file.getName());
							Preconditions.checkState(file.delete());
						}
					}
				}
				Preconditions.checkState(tractMainDir.exists() || tractMainDir.mkdir());
			}
			tractNodeDir = getTractNodeDir(rank);
			tractWriteQueue = new LinkedList<MPJ_CondLossCalc.TractWriteable>();
//			HashSet<String> tractNames = new HashSet<String>();
//			for (Asset asset : assets)
//				tractNames.add(getTractName(asset));
//			tractsSorted = Lists.newArrayList(tractNames);
//			Collections.sort(tractsSorted);
//			if (rank == 0)
//				debug("Storing results individually for "+tractsSorted.size()+" census tracts");
//			tractResults = Maps.newHashMap();
//			for (String tract : tractsSorted) {
//				double[][] tract_results = new double[refERF.getNumSources()][];
//				tractResults.put(tract, tract_results);
//			}
		}
		
		calc = new ThreadedCondLossCalc(erfs, imrs, magThreshFunc);
		
		if (cmd.hasOption("vuln-file")) {
			File vulnFile = new File(cmd.getOptionValue("vuln-file"));
			System.out.println("trying to load vulnerabilities from: "+vulnFile.getAbsolutePath());
			PortfolioEALCalculatorController.getVulnerabilities(vulnFile);
			System.out.println("DONE loading vulns.");
		}
	}
	
	private File getTractNodeDir(int rank) {
		return new File(tractMainDir, "process_"+rank);
	}
	
	private static String getTractName(Asset asset) {
		return asset.getParameterList().getParameter(String.class, "AssetName").getValue().trim().replaceAll("\\W+", "_");
	}
	
	private ERF loadERF(Element root) throws InvocationTargetException {
		Element epistemicEl = root.element(AbstractEpistemicListERF.XML_METADATA_NAME);
		if (epistemicEl != null)
			return (ERF) AbstractEpistemicListERF.fromXMLMetadata(epistemicEl);
		else
			return AbstractERF.fromXMLMetadata(root.element(AbstractERF.XML_METADATA_NAME));
	}

	@Override
	protected int getNumTasks() {
		return assets.size();
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		Deque<SiteResult> deque = new ArrayDeque<MPJ_CondLossCalc.SiteResult>();
		for (int index : batch)
			deque.add(new SiteResult(index, assets.get(index)));
		calc.calculateBatch(deque); // will call registerResult on each one
		
		if (keepTractResults) {
			while (!tractWriteQueue.isEmpty()) {
				TractWriteable writeable = tractWriteQueue.pop();
				File outFile = new File(tractNodeDir, writeable.name+".bin");
				Preconditions.checkState(!outFile.exists());
				debug("Writing tract info to "+outFile.getName());
				writeResults(outFile, writeable.values);
			}
		}
		
		System.gc();
		Runtime rt = Runtime.getRuntime();
		long totalMB = rt.totalMemory() / 1024 / 1024;
		long freeMB = rt.freeMemory() / 1024 / 1024;
		long usedMB = totalMB - freeMB;
		debug("post calc mem t/u/f: "+totalMB+"/"+usedMB+"/"+freeMB);
	}
	
	protected synchronized void registerResult(SiteResult result) {
		double[][] vals = result.results;
		Preconditions.checkState(vals.length == my_results.length,
				"Source count discrepancy. Expected "+my_results.length+", was "+vals.length);
		double[][] tract_vals = null;
		for (int sourceID=0; sourceID<vals.length; sourceID++) {
			if (vals[sourceID] != null) {
//				Preconditions.checkState(vals[sourceID].length == my_results[sourceID].length,
//						"Rup count discrepancy for source "+sourceID+". Expected "+my_results[sourceID].length
//						+", was "+vals[sourceID].length);
				if (tract_vals != null && tract_vals[sourceID] == null)
					tract_vals[sourceID] = new double[my_results[sourceID].length];
				for (int rupID=0; rupID<vals[sourceID].length; rupID++) {
					my_results[sourceID][rupID] += vals[sourceID][rupID];
					if (tract_vals != null)
						tract_vals[sourceID][rupID] += vals[sourceID][rupID];
				}
			}
		}
		if (keepTractResults) {
			TractWriteable writeable = new TractWriteable(getTractName(result.asset), vals);
			// sometimes the filesystem can be slow
			int numRetries = 0;
			while (!tractNodeDir.exists()) {
				if (tractNodeDir.mkdir())
					break;
				if (numRetries == 5)
					throw new IllegalStateException("Couldn't create dir: "+tractNodeDir.getAbsolutePath());
				debug("Could not create dir: "+tractNodeDir.getAbsolutePath()+", retrying in 5s");
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {}
				numRetries++;
			}
			Preconditions.checkState(tractNodeDir.exists() || tractNodeDir.mkdir());
			synchronized (tractWriteQueue) {
				// will block if currently writing one
				boolean duplicate = false;
				for (TractWriteable other : tractWriteQueue) {
					if (other.name.equals(writeable.name)) {
						// just add these results to the existing one
						other.addFrom(writeable);
						duplicate = true;
						break;
					}
				}
				if (!duplicate)
					tractWriteQueue.add(writeable);
			}
		}
	}
	
	private class TractWriteable {
		private String name;
		private double[][] values;
		
		public TractWriteable(String name, double[][] values) {
			this.name = name;
			this.values = values;
		}
		
		public void addFrom(TractWriteable o) {
			add(o.values);
		}
		
		public void add(double[][] otherValues) {
			addTo(values, otherValues);
		}
	}
	
	public static void addTo(double[][] values, double[][] toBeAdded) {
		for (int i=0; i<values.length; i++) {
			double[] oVals = toBeAdded[i];
			if (oVals == null)
				continue;
			double[] vals = values[i];
			if (vals == null) {
				values[i] = oVals;
			} else {
				for (int j=0; j<vals.length; j++)
					vals[j] += oVals[j];
			}
		}
	}
	
	private ExecutorService mergeExec;
	private Deque<MergeTask> mergeDeque;
	
	private class MergeTask {
		private File desination;
		private List<File> origins;
		
		public MergeTask(File destination, File origin) {
			this.desination = destination;
			origins = Lists.newArrayList(origin);
		}
	}
	
	private class MergeRunnable implements Runnable {
		
		private MergeTask task;
		
		public MergeRunnable(MergeTask task) {
			this.task = task;
		}

		@Override
		public void run() {
			try {
				// remove from stack
				synchronized (mergeDeque) {
					Preconditions.checkState(mergeDeque.remove(task), "MergeRunnable: task wasn't in deque");
				}
				debug("Merging "+task.origins.size()+" files to "+task.desination.getName()
					+" (exists? "+task.desination.exists()+"). Queued merges: "+mergeDeque.size());
				Preconditions.checkState(!task.origins.isEmpty());
				
				if (!task.desination.exists()) {
					// just do a move
					Files.move(task.origins.remove(0), task.desination);
					if (task.origins.isEmpty())
						return;
				}
				
				// process
				double[][] results = loadResults(task.desination);
				for (File origin : task.origins) {
					double[][] subResults = loadResults(origin);
					Preconditions.checkState(origin.delete());
					if (results == null)
						results = subResults;
					else
						addTo(results, subResults);
				}
				writeResults(task.desination, results);
			} catch (Exception e) {
				abortAndExit(e);
			}
		}
		
	}

	@Override
	public void batchProcessed(int[] batch, int processIndex) {
		// will be called on rank 0 whenever a batch has been processed, if keepTractResults=true
		// consolidate node results for each tract into the file for the given tract
		if (batch.length == 0)
			return;
		Preconditions.checkState(keepTractResults);
		HashSet<String> tracts = new HashSet<String>();
		for (int index : batch)
			tracts.add(getTractName(assets.get(index)));
		
		if (mergeDeque == null) {
			mergeDeque = new ArrayDeque<MPJ_CondLossCalc.MergeTask>();
			mergeExec = Executors.newSingleThreadExecutor();
		}
		
		long stamp = System.currentTimeMillis();
		
		debug("post-batch considation for "+tracts.size()+" census tracts ("+batch.length+" assets)");
		
		List<MergeTask> myTasks = Lists.newArrayList();
		
		for (String tract : tracts) {
			File procTractFile = new File(getTractNodeDir(processIndex), tract+".bin");
			Preconditions.checkState(procTractFile.exists());
			// move to a unique file for future (async) processing
			File procDestFile = new File(procTractFile.getAbsolutePath()+"_merge_"+stamp);
			try {
				Files.move(procTractFile, procDestFile);
			} catch (IOException e1) {
				abortAndExit(e1);
			}
			String outName;
			if (gzip)
				outName = tract+".bin.gz";
			else
				outName = tract+".bin";
			File globalTractFile = new File(tractMainDir, outName);
			myTasks.add(new MergeTask(globalTractFile, procDestFile));
//			try {
//				debug("post-batch consolidating "+tract);
//				double[][] procResults = loadResults(procTractFile);
//				double[][] mergedResults;
//				if (globalTractFile.exists()) {
//					mergedResults = loadResults(globalTractFile);
//					addTo(mergedResults, procResults);
//				} else {
//					mergedResults = procResults;
//				}
//				writeResults(globalTractFile, mergedResults);
//				Preconditions.checkState(procTractFile.delete());
//			} catch (IOException e) {
//				abortAndExit(e);
//			}
		}
		// add tasks
		synchronized (mergeDeque) {
			for (MergeTask task : myTasks) {
				boolean match = false;
				for (MergeTask existing : mergeDeque) {
					if (task.desination.equals(existing.desination)) {
						// add to existing task
						existing.origins.add(task.origins.get(0));
						match = true;
						break;
					}
				}
				if (!match) {
					// new task, submit
					mergeDeque.add(task);
					mergeExec.submit(new MergeRunnable(task));
				}
			}
		}
	}
	
	private double[] packResults(double[][] results) {
		double[] packed_results = new double[numRups];
		int cnt = 0;
		int numSources = refERF.getNumSources();
		for (int sourceID=0; sourceID<numSources; sourceID++) {
			int numRups = refERF.getNumRuptures(sourceID);
			double[] vals = results[sourceID];
			if (vals == null)
				vals = new double[numRups];
			for (int rupID=0; rupID<numRups; rupID++) {
				packed_results[cnt++] = vals[rupID];
			}
		}
		return packed_results;
	}
	
	private double[][] unpackResults(double[] results) {
		int numSources = refERF.getNumSources();
		double[][] unpacked_results = new double[numSources][];
		int cnt = 0;
		for (int sourceID=0; sourceID<numSources; sourceID++) {
			int numRups = refERF.getNumRuptures(sourceID);
			unpacked_results[sourceID] = new double[numRups];
			for (int rupID=0; rupID<numRups; rupID++)
				unpacked_results[sourceID][rupID] = results[cnt++];
		}
		return unpacked_results;
	}
	
	@Override
	protected void doFinalAssembly() throws Exception {
		// global (totals)
		
		File outputDir = this.outputFile.getParentFile();
		String prefix = this.outputFile.getName();
		if (prefix.toLowerCase().endsWith(".bin"))
			prefix = prefix.substring(0, prefix.lastIndexOf("."));
		
		double[][] results = fetchResults(my_results);
		if (rank == 0) {
			writeAll(outputDir, prefix, results);
			
			if (keepTractResults) {
				// now handled after each batch
//				HashSet<String> tracts = new HashSet<String>();
//				for (Asset asset : assets)
//					tracts.add(getTractName(asset));
//				for (String tract : tracts) {
//					TractWriteable tractResults = new TractWriteable(tract, null);
//					for (int i=0; i<size; i++) {
//						File nodeDir = getTractNodeDir(i);
//						File tractFile = new File(nodeDir, tract+".bin");
//						if (tractFile.exists()) {
//							double[][] subTractResults = loadResults(tractFile);
//							if (tractResults.values == null)
//								tractResults.values = subTractResults;
//							else
//								tractResults.add(subTractResults);
//						}
//					}
//					writeAll(tractMainDir, tract, tractResults.values);
//				}
				debug("Waiting on any outstanding merges: "+mergeDeque.size());
				while (!mergeDeque.isEmpty()) {
					debug("Merge count: "+mergeDeque.size());
					Thread.sleep(10000);
				}
				for (int i=0; i<rank; i++)
					getTractNodeDir(i).delete();
			}
		}
	}
	
	private double[][] fetchResults(double[][] localResults) {
		int TAG_GET_RESULTS = 1;
		
		// pack results into one dimensional array
		double[] packed_results = packResults(localResults);
		int rupCount = packed_results.length;
		
		if (rank == 0) {
			double[] global_results = new double[rupCount];
			
			for (int source=0; source<size; source++) {
				double[] srcResults;
				
				if (source == rank) {
					srcResults = packed_results;
				} else {
					// get results
					srcResults = new double[rupCount];
					MPI.COMM_WORLD.Recv(srcResults, 0, srcResults.length, MPI.DOUBLE, source, TAG_GET_RESULTS);
				}
				
				for (int i=0; i<rupCount; i++)
					global_results[i] += srcResults[i];
			}
			
			double[][] unpacked_results = unpackResults(global_results);
			return unpacked_results;
		} else {
			// send results
			MPI.COMM_WORLD.Send(packed_results, 0, packed_results.length, MPI.DOUBLE, 0, TAG_GET_RESULTS);
			return null; // not processing locally
		}
	}
	
	private void writeAll(File outputDir, String prefix, double[][] results) throws IOException {
		String suffix = ".bin";
		if (gzip)
			suffix += ".gz";
		File outputFile = new File(outputDir, prefix+suffix);
		
		writeResults(outputFile, results);
		
		if (refERF instanceof FaultSystemSolutionERF) {
			double[][] fssResults = mapResultsToFSS((FaultSystemSolutionERF)refERF, results);
			
			File fssOutputFile = new File(outputFile.getParentFile(), prefix+"_fss_index"+suffix);
			writeResults(fssOutputFile, fssResults);
			File fssGridOutputFile = new File(outputFile.getParentFile(), prefix+"_fss_gridded"+suffix);
			writeFSSGridSourcesFile((FaultSystemSolutionERF)refERF, results, fssGridOutputFile);
		}
	}
	
	/**
	 * This takes results in [sourceID][rupID] format and maps them into [FSS rup index][mag index] where mag index
	 * is the index in the rupture MFD.
	 * 
	 * @param erf
	 * @param origResults
	 * @return
	 * @throws IOException
	 */
	public static double[][] mapResultsToFSS(FaultSystemSolutionERF erf, double[][] origResults) throws IOException {
		// write it out by rupture index as well. we can use the same file format
		int numFSSRups = erf.getSolution().getRupSet().getNumRuptures();
		double[][] fssResults = new double[numFSSRups][];
		for (int r=0; r<numFSSRups; r++) {
			int sourceIndex = erf.getSrcIndexForFltSysRup(r);
			if (sourceIndex < 0)
				fssResults[r] = new double[0];
			else
				fssResults[r] = origResults[sourceIndex];
		}
		return fssResults;
	}
	
	public static final int buffer_len = 655360;
	
	private static OutputStream getOutputStream(File file) throws IOException {
		FileOutputStream fos = new FileOutputStream(file);
		if (isGZIP(file))
			return new GZIPOutputStream(fos, buffer_len);
		return new BufferedOutputStream(fos, buffer_len);
	}
	
	private static boolean isGZIP(File file) {
		return file.getName().toLowerCase().endsWith(".gz");
	}
	
	private static InputStream getInputStream(File file) throws IOException {
		FileInputStream fis = new FileInputStream(file);
		if (isGZIP(file))
			return new GZIPInputStream(fis, buffer_len);
		return new BufferedInputStream(fis, buffer_len);
	}
	
	/**
	 * This writes a file containing expected losses for each grid node/magnitude bin
	 * 
	 * @param erf
	 * @param origResults
	 * @param file
	 * @throws IOException
	 */
	public static void writeFSSGridSourcesFile(FaultSystemSolutionERF erf, double[][] origResults, File file)
			throws IOException {
		int fssSources = erf.getNumFaultSystemSources();
		if (erf.getParameter(IncludeBackgroundParam.NAME).getValue() == IncludeBackgroundOption.ONLY)
			fssSources = 0;
		int numSources = erf.getNumSources();
		int numGridded = numSources - fssSources;
//		System.out.println("fssSources="+fssSources);
//		System.out.println("numSources="+numSources);
//		System.out.println("numGridded="+numGridded);
//		System.exit(0);
		
		if (numGridded <= 0)
			return;
		
		BackgroundRupType bgType = (BackgroundRupType)erf.getParameter(BackgroundRupParam.NAME).getValue();
		
		GridSourceProvider prov = erf.getSolution().getGridSourceProvider();

		DataOutputStream out = new DataOutputStream(getOutputStream(file));
		out.writeInt(numGridded);
		
		for (int srcIndex=fssSources; srcIndex<erf.getNumSources(); srcIndex++) {
			// returned in nodeList order
			int nodeIndex = srcIndex - fssSources;
			Location loc = prov.getGriddedRegion().locationForIndex(nodeIndex);
			
			// write location to be safe in case gridding changes in the future
			out.writeDouble(loc.getLatitude());
			out.writeDouble(loc.getLongitude());
			
			ProbEqkSource source = erf.getSource(srcIndex);
			
			// now combine by mag
			ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
			
			double fractSS = prov.getFracStrikeSlip(nodeIndex);
			double fractReverse = prov.getFracReverse(nodeIndex);
			double fractNormal = prov.getFracNormal(nodeIndex);
			// this is for making sure we account for everything correctly
			ArbitrarilyDiscretizedFunc fractTrack = new ArbitrarilyDiscretizedFunc();
			
			for (int r=0; r<source.getNumRuptures(); r++) {
				ProbEqkRupture rup = source.getRupture(r);
				
				// need to scale loss by the fraction with that focal mech
				double fract = 0d;
				if ((float)rup.getAveRake() == -90f)
					fract = fractNormal;
				else if ((float)rup.getAveRake() == 90f)
					fract = fractReverse;
				else if ((float)rup.getAveRake() == 0f)
					fract = fractSS;
				else
					throw new IllegalStateException("Unkown rake: "+rup.getAveRake());
				if (bgType == BackgroundRupType.CROSSHAIR)
					// there are twice as many ruptures in the crosshair case
					fract *= 0.5;
				else if (bgType == BackgroundRupType.POINT && (float)rup.getAveRake() != 0f)
					// non SS rups have 2 for each mech type
					fract *= 0.5;
				
				double loss;
				if (origResults[srcIndex] == null)
					loss = 0d;
				else
					loss = fract*origResults[srcIndex][r];
				double mag = rup.getMag();
				int ind = func.getXIndex(mag);
				if (ind >= 0) {
					func.set(ind, func.getY(ind)+loss);
					fractTrack.set(ind, fractTrack.getY(ind)+fract);
				} else {
					func.set(rup.getMag(), loss);
					fractTrack.set(rup.getMag(), fract);
				}
			}
			// make sure we got all of the fractional losses for each mag bin
			for (int i=0; i<fractTrack.size(); i++)
				Preconditions.checkState((float)fractTrack.getY(i) == 1f,
						"Fract for mag "+fractTrack.getX(i)+" != 1: "+fractTrack.getY(i));
			out.writeInt(func.size());
			for (int i=0; i<func.size(); i++) {
				out.writeDouble(func.getX(i));
				// expected loss
				out.writeDouble(func.getY(i));
			}
		}
		
		out.close();
	}
	
	/**
	 * This loads a file containing expected losses for each grid node/magnitude bin. A gridded region
	 * can also be passed in to validate the locations
	 * 
	 * @param file
	 * @param region can be null, just used for verification (recommended!)
	 * @return
	 * @throws IOException 
	 */
	public static DiscretizedFunc[] loadGridSourcesFile(File file, GriddedRegion region) throws IOException {
		InputStream is = getInputStream(file);
		Preconditions.checkNotNull(is, "InputStream cannot be null!");

		DataInputStream in = new DataInputStream(is);

		int numGridded = in.readInt();

		Preconditions.checkState(numGridded > 0, "Size must be > 0!");
		Preconditions.checkState(region == null || region.getNodeCount() == numGridded,
				"Gridded location count doesn't match passed in region.");
		
		DiscretizedFunc[] results = new DiscretizedFunc[numGridded];
		
		for (int node=0; node<numGridded; node++) {
			double lat = in.readDouble();
			double lon = in.readDouble();
			if (region != null)
				Preconditions.checkState(region.locationForIndex(node).equals(new Location(lat, lon)));
			int numRups = in.readInt();
			if (numRups == 0)
				continue;
			double[] mags = new double[numRups];
			double[] losses = new double[numRups];
			for (int rupID=0; rupID<numRups; rupID++) {
				mags[rupID] = in.readDouble();
				losses[rupID] = in.readDouble();
			}
			results[node] = new LightFixedXFunc(mags, losses);
		}
		
		in.close();
		
		return results;
	}
	
	/**
	 * File format:<br>
	 * [num ERF sources]
	 * 		[num ruptures for source]
	 *      	[expected loss for rupture]
	 * @param results
	 * @param file
	 * @throws IOException 
	 */
	public static void writeResults(File file, double[][] results) throws IOException {
		DataOutputStream out = new DataOutputStream(getOutputStream(file));
		
		int numSources = results.length;
		
		out.writeInt(numSources);
		
		// get asset counts for each source/rup
		for (int sourceID=0; sourceID<numSources; sourceID++) {
			if (results[sourceID] == null) {
				out.writeInt(-1);
			} else {
				int numRups = results[sourceID].length;
				out.writeInt(numRups);
				
				for (int rupID=0; rupID<numRups; rupID++)
					out.writeDouble(results[sourceID][rupID]);
			}
		}
		
		out.close();
		
		if (FILE_DEBUG) {
			System.out.println("Auditing file IO");
			double[][] results2 = loadResults(file);
			int checks = 0;
			Preconditions.checkState(results.length == results2.length);
			checks++;
			for (int sourceID=0; sourceID<results.length; sourceID++) {
				int numRups = results[sourceID].length;
				Preconditions.checkState(numRups == results2[sourceID].length);
				checks++;
				for (int rupID=0; rupID<numRups; rupID++) {
					double v1 = results[sourceID][rupID];
					double v2 = results2[sourceID][rupID];
					Preconditions.checkState((float)v1 == (float)v2);
					checks++;
				}
			}
			System.out.println("Done auditing file IO ("+checks+" checks)");
		}
	}
	
	/**
	 * [num ERF sources]
	 * 		[num ruptures for source]
	 *      	[expected loss for rupture]
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static double[][] loadResults(File file) throws IOException {
		InputStream is = getInputStream(file);
		Preconditions.checkNotNull(is, "InputStream cannot be null!");

		DataInputStream in = new DataInputStream(is);

		int numSources = in.readInt();

		Preconditions.checkState(numSources > 0, "Size must be > 0!");
		
		double[][] results = new double[numSources][];
		
		for (int sourceID=0; sourceID<numSources; sourceID++) {
			int numRups = in.readInt();
			if (numRups == -1) {
				results[sourceID] = null;
			} else {
				results[sourceID] = new double[numRups];
				for (int rupID=0; rupID<numRups; rupID++) {
					results[sourceID][rupID] = in.readDouble();
				}
			}
		}
		
		in.close();
		
		return results;
	}
	
	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		Option vulnOp = new Option("v", "vuln-file", true, "VUL06 file");
		vulnOp.setRequired(false);
		ops.addOption(vulnOp);
		
		Option erfOp = new Option("e", "mult-erfs", false, "If set, a copy of the ERF will be instantiated for each thread.");
		erfOp.setRequired(false);
		ops.addOption(erfOp);
		
		Option tractOp = new Option("tract", "tract-results", false, "If set, results are stored for each census tract");
		tractOp.setRequired(false);
		ops.addOption(tractOp);
		
		Option gzipOp = new Option("gz", "gzip", false, "If set, results gzipped");
		gzipOp.setRequired(false);
		ops.addOption(gzipOp); 
		
		return ops;
	}
	
	public static void main(String[] args) {
		args = MPJTaskCalculator.initMPJ(args);
		
		try {
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args, MPJ_CondLossCalc.class);
			
			args = cmd.getArgs();
			
			if (args.length < 2 || args.length > 3) {
				System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(MPJ_CondLossCalc.class)
						+" [options] <portfolio_file> <calculation_params_file> [<output_file>]");
				abortAndExit(2);
			}

			Portfolio portfolio = Portfolio.createPortfolio(new File(args[0]));

			Document doc = XMLUtils.loadDocument(new File(args[1]));
			Element root = doc.getRootElement();
			
			if (args.length == 2) {
				// batch mode
				
				Iterator<Element> it = root.elementIterator(BATCH_ELEMENT_NAME);
				
				while (it.hasNext()) {
					MPJ_CondLossCalc driver = new MPJ_CondLossCalc(cmd, portfolio, it.next());
					
					driver.run();
				}
			} else {
				File outputFile = new File(args[2]);
				
				MPJ_CondLossCalc driver = new MPJ_CondLossCalc(cmd, portfolio, root, outputFile);
				
				driver.run();
			}
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

	@Override
	public void calculationException(String errorMessage) {
		abortAndExit(new RuntimeException(errorMessage));
	}
	
	public class SiteResult implements Serializable, Comparable<SiteResult> {
		
		private int index;
		private transient Asset asset;
		
		// expected loss results per rupture: [sourceID][rupID]
		private double[][] results;
		
		public SiteResult(int index, Asset asset) {
			super();
			this.index = index;
			this.asset = asset;
		}
		
		void calculate(ERF erf, ScalarIMR imr, Site initialSite, ArbitrarilyDiscretizedFunc magThreshFunc) {
			try {
				results = asset.calculateExpectedLossPerRup(imr, magThreshFunc, initialSite, erf, MPJ_CondLossCalc.this);
				registerResult(this);
			} catch (Exception e) {
				abortAndExit(e);
			}
		}

		@Override
		public int compareTo(SiteResult o) {
			return new Integer(index).compareTo(o.index);
		}
	}

}
