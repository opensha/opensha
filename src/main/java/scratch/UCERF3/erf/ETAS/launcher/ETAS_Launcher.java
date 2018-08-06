package scratch.UCERF3.erf.ETAS.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dom4j.DocumentException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.parsers.UCERF3_CatalogParser;
import org.opensha.sha.earthquake.param.AleatoryMagAreaStdDevParam;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.BPTAveragingTypeParam;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityParam;
import org.opensha.sha.earthquake.param.MaximumMagnitudeParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.primitives.Floats;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_SimAnalysisTools;
import scratch.UCERF3.erf.ETAS.ETAS_Simulator;
import scratch.UCERF3.erf.ETAS.FaultSystemSolutionERF_ETAS;
import scratch.UCERF3.erf.ETAS.ETAS_Params.ETAS_ParameterList;
import scratch.UCERF3.erf.ETAS.NoFaultsModel.ETAS_Simulator_NoFaults;
import scratch.UCERF3.erf.ETAS.NoFaultsModel.UCERF3_GriddedSeisOnlyERF_ETAS;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.LastEventData;
import scratch.UCERF3.utils.MatrixIO;
import scratch.UCERF3.utils.RELM_RegionUtils;
import scratch.UCERF3.utils.U3_EqkCatalogStatewideCompleteness;
import scratch.UCERF3.utils.finiteFaultMap.FiniteFaultMappingData;

public class ETAS_Launcher {
	
	public enum DebugLevel {
		ERROR(0),
		INFO(1),
		FINE(2),
		DEBUG(3);
		
		private int val;
		private DebugLevel(int val) {
			this.val = val;
		}
		
		boolean shouldPrint(DebugLevel o) {
			return o.val <= val;
		}
	}
	
	protected DebugLevel debugLevel = DebugLevel.FINE;
	
	private ETAS_Config config;
	
	private long simulationOT;
	private String simulationName;
	private List<ETAS_EqkRupture> triggerRuptures;
	private List<ETAS_EqkRupture> histQkList;
	
	// caches
	private double[] gridSeisCorrections;
	private List<float[]> fractionSrcAtPointList;
	private List<int[]> srcAtPointList;
	private int[] isCubeInsideFaultPolygon;
	
	private Deque<FaultSystemSolution> fssDeque = new ArrayDeque<>();
	
	// last event data
	private Map<Integer, List<LastEventData>> lastEventData;
	private Map<Long, List<Integer>> resetSubSectsMap;
	
	private GriddedRegion griddedRegion;
	
	private File resultsDir;
	
	private Random r;
	
	public ETAS_Launcher(ETAS_Config config) throws IOException {
		this(config, true);
	}
	
	public ETAS_Launcher(ETAS_Config config, boolean mkdirs) throws IOException {
		ETAS_Simulator.D = false;
		if (config.isGriddedOnly())
			ETAS_Simulator_NoFaults.D = false;
		AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF = 2.55;
		this.config = config;
		
		this.simulationOT = config.getSimulationStartTimeMillis();
		
		lastEventData = LastEventData.load();
		resetSubSectsMap = new HashMap<>();
		
		// purge any last event data after OT
		LastEventData.filterDataAfterTime(lastEventData, simulationOT);
		
		// load trigger ruptures
		List<TriggerRupture> triggerRuptureConfigs = config.getTriggerRuptures();
		if (triggerRuptureConfigs != null && !triggerRuptureConfigs.isEmpty()) {
			debug(DebugLevel.INFO, "Building "+triggerRuptureConfigs.size()+" trigger ruptures");
			FaultSystemSolution fss = checkOutFSS();
			FaultSystemRupSet rupSet = fss.getRupSet();
			
			triggerRuptures = new ArrayList<>();
			for (TriggerRupture triggerRup : triggerRuptureConfigs) {
				ETAS_EqkRupture rup = triggerRup.buildRupture(rupSet, simulationOT);
				triggerRuptures.add(rup);
				int[] rupturedSects = triggerRup.getSectionsRuptured(rupSet);
				if (rupturedSects != null && rupturedSects.length > 0) {
					long time = rup.getOriginTime();
					List<Integer> sects = resetSubSectsMap.get(time);
					if (sects == null) {
						sects = new ArrayList<>();
						resetSubSectsMap.put(time, sects);
					}
					for (int sect : rupturedSects)
						sects.add(sect);
				}
			}
			
			checkInFSS(fss);
			
			if (!resetSubSectsMap.isEmpty()) {
				debug(DebugLevel.FINE, "The following subsections' time of occurrence will be reset:");
				for (Long time : getSortedResetTimes())
					debug(DebugLevel.FINE, "\t"+time+": "+Joiner.on(",").join(resetSubSectsMap.get(time)));
			}
		}
		
		// now load a trigger catalog
		histQkList = new ArrayList<>();
		if (config.getTriggerCatalogFile() != null) {
			FaultSystemSolution fss = checkOutFSS();
			try {
				histQkList.addAll(loadHistoricalCatalog(config.getTriggerCatalogFile(),
						config.getTriggerCatalogSurfaceMappingsFile(), fss, simulationOT));
			} catch (DocumentException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			checkInFSS(fss);
		}
		
		Preconditions.checkState(config.isIncludeSpontaneous() || !triggerRuptureConfigs.isEmpty() || !histQkList.isEmpty(),
				"Empty simulation! Must include spontaneous, trigger ruptures, and/or a trigger catalog");
		
		simulationName = config.getSimulationName();
		if (simulationName == null || simulationName.isEmpty()) {
			if (triggerRuptures != null) {
				if (triggerRuptures.size() > 1) {
					float[] mags = new float[triggerRuptures.size()];
					float maxMag = Float.NEGATIVE_INFINITY;
					for (int i=0; i<mags.length; i++) {
						mags[i] = (float)triggerRuptures.get(i).getMag();
						if (mags[i] > maxMag)
							maxMag = mags[i];
					}
					if (mags.length < 5)
						simulationName = mags.length+" Scenarios (M="+Joiner.on(", M=").join(Floats.asList(mags))+")";
					else
						simulationName = mags.length+" Scenarios (Max="+maxMag+")";
				}
			}
			
			if (!histQkList.isEmpty()) {
				if (simulationName != null)
					simulationName += ", ";
				else
					simulationName = "";
				simulationName += histQkList.size()+" Hist EQs";
			}
			
			if (config.isIncludeSpontaneous()) {
				if (simulationName != null)
					simulationName += ", ";
				else
					simulationName = "";
				simulationName += "Spontaneous";
			}
		}
		
		debug(DebugLevel.FINE, "Simulation name: "+simulationName);
		
		File outputDir = config.getOutputDir();
		if (mkdirs)
			waitOnDirCreation(outputDir, 10, 2000);
		
		resultsDir = new File(outputDir, "results");
		if (mkdirs)
			waitOnDirCreation(resultsDir, 10, 2000);
		
		griddedRegion = RELM_RegionUtils.getGriddedRegionInstance();
		
		r = new Random(System.nanoTime());
	}
	
	protected void setRandom(Random r) {
		Preconditions.checkNotNull(r);
		this.r = r;
	}
	
	public void debug(String message) {
		debug(DebugLevel.INFO, message);
	}
	
	private static final SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");
	
	public void debug(DebugLevel level, String message) {
		if (this.debugLevel.shouldPrint(level))
			System.out.println("["+df.format(new Date())+" ("+Thread.currentThread().getName()+")]: "+message);
	}
	
	public void setDebugLevel(DebugLevel level) {
		this.debugLevel = level;
	}
	
	private List<Long> getSortedResetTimes() {
		List<Long> times = new ArrayList<>(resetSubSectsMap.keySet());
		Collections.sort(times);
		return times;
	}
	
	public List<ETAS_EqkRupture> getTriggerRuptures() {
		return triggerRuptures;
	}

	public List<ETAS_EqkRupture> getHistQkList() {
		return histQkList;
	}

	protected synchronized FaultSystemSolution checkOutFSS() {
		FaultSystemSolution fss;
		if (fssDeque.isEmpty()) {
			// load a new one
			try {
				debug(DebugLevel.FINE, "Loading a new Fault System Solution from "+config.getFSS_File().getAbsolutePath());
				fss = FaultSystemIO.loadSol(config.getFSS_File());
				
				if (config.isGridSeisCorr()) {
					if (gridSeisCorrections == null) {
						File cacheFile = new File(config.getCacheDir(), "griddedSeisCorrectionCache");
						debug(DebugLevel.FINE, "Loading gridded seismicity correction cache file from "+cacheFile.getAbsolutePath());
						gridSeisCorrections = MatrixIO.doubleArrayFromFile(cacheFile);
					}
					ETAS_Simulator.correctGriddedSeismicityRatesInERF(fss, false, gridSeisCorrections);
				}
			} catch (IOException | DocumentException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		} else {
			fss = fssDeque.pop();
		}
		// reset last event data
		FaultSystemRupSet rupSet = fss.getRupSet();
		if (config.isTimeIndependentERF()) {
			for (int s=0; s<rupSet.getNumSections(); s++)
				rupSet.getFaultSectionData(s).setDateOfLastEvent(Long.MIN_VALUE);
		} else {
			LastEventData.populateSubSects(rupSet.getFaultSectionDataList(), lastEventData);
			
			if (!resetSubSectsMap.isEmpty()) {
				// now apply for any trigger ruptures
				for (Long time : getSortedResetTimes()) {
					for (int s : resetSubSectsMap.get(time))
						rupSet.getFaultSectionData(s).setDateOfLastEvent(time);
				}
			}
		}
		return fss;
	}
	
	protected synchronized void checkInFSS(FaultSystemSolution fss) {
		fssDeque.push(fss);
	}
	
	public static List<ETAS_EqkRupture> loadHistoricalCatalog(File catFile, File surfsFile, FaultSystemSolution sol, long ot)
			throws IOException, DocumentException {
		List<ETAS_EqkRupture> histQkList = new ArrayList<>();
		// load in historical catalog
		
		Preconditions.checkArgument(catFile.exists(), "Catalog file doesn't exist: "+catFile.getAbsolutePath());
		ObsEqkRupList loadedRups = UCERF3_CatalogParser.loadCatalog(catFile);
		
		if (surfsFile != null) {
			// add rupture surfaces
			FaultModels fm = getFaultModel(sol);
			
			Preconditions.checkArgument(surfsFile.exists(), "Rupture surfaces file doesn't exist: "+surfsFile.getAbsolutePath());
			FiniteFaultMappingData.loadRuptureSurfaces(surfsFile, loadedRups, fm, sol.getRupSet());
		}
		
		// filter for historical completeness
		loadedRups = U3_EqkCatalogStatewideCompleteness.load().getFilteredCatalog(loadedRups);
		int numAfter = 0;
		for (ObsEqkRupture rup : loadedRups) {
			if (rup.getOriginTime() > ot) {
				// skip all ruptures that occur after simulation start
				if (numAfter < 10)
					System.out.println("Skipping a M"+rup.getMag()+" after sim start ("
							+rup.getOriginTime()+" > "+ot+"): "+rup);
				numAfter++;
				if (numAfter == 10)
					System.out.println("(supressing future output on skipped ruptures)");
				continue;
			}
			ETAS_EqkRupture etasRup = new ETAS_EqkRupture(rup);
			etasRup.setID(Integer.parseInt(rup.getEventId()));
			histQkList.add(etasRup);
		}
		System.out.println("Skipped "+numAfter+" ruptures after sim start");
		return histQkList;
	}
	
	/**
	 * A little kludgy, but determines FaultModel by rupture count
	 * @param sol
	 * @return
	 */
	private static FaultModels getFaultModel(FaultSystemSolution sol) {
		int numRups = sol.getRupSet().getNumRuptures();
		if (sol instanceof InversionFaultSystemSolution)
			return ((InversionFaultSystemSolution)sol).getLogicTreeBranch().getValue(FaultModels.class);
		else if (numRups == 253706)
			return FaultModels.FM3_1;
		else if (numRups == 305709)
			return FaultModels.FM3_2;
		else
			throw new IllegalStateException("Don't know Fault Model for solution with "+numRups+" ruptures");
	}
	
	private static void waitOnDirCreation(File dir, int maxRetries, long sleepMillis) {
		int retry = 0;
		while (!(dir.exists() || dir.mkdir())) {
			try {
				Thread.sleep(sleepMillis);
			} catch (InterruptedException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			if (retry++ > maxRetries)
				throw new IllegalStateException("Directory doesn't exist and couldn't be created after "
						+maxRetries+" retries: "+dir.getAbsolutePath());
		}
	}
	
	File getResultsDir(int index) {
		String runName = ""+index;
		int desiredLen = ((config.getNumSimulations()-1)+"").length();
		while (runName.length() < desiredLen)
			runName = "0"+runName;
		runName = "sim_"+runName;
		return new File(resultsDir, runName);
	}
	
	/**
	 * Creates ERF for use with ETAS simulations. Will not be updated
	 * @param sol
	 * @param timeIndep
	 * @param duration
	 * @return
	 */	
	public static FaultSystemSolutionERF_ETAS buildERF(FaultSystemSolution sol, boolean timeIndep, double duration,
			int startYear) {
		long ot = Math.round((startYear-1970.0)*ProbabilityModelsCalc.MILLISEC_PER_YEAR);
		return buildERF_millis(sol, timeIndep, duration, ot);
	}
	
	@SuppressWarnings("unchecked")
	public static FaultSystemSolutionERF_ETAS buildERF_millis(FaultSystemSolution sol, boolean timeIndep, double duration,
			long ot) {
		FaultSystemSolutionERF_ETAS erf = new FaultSystemSolutionERF_ETAS(sol);
		// set parameters
		erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
		erf.setParameter(BackgroundRupParam.NAME, BackgroundRupType.POINT);
		erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, false);
		erf.getParameter(ProbabilityModelParam.NAME).setValue(ProbabilityModelOptions.U3_BPT);
		erf.getParameter(MagDependentAperiodicityParam.NAME).setValue(MagDependentAperiodicityOptions.MID_VALUES);
		BPTAveragingTypeOptions aveType = BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE;
		erf.setParameter(BPTAveragingTypeParam.NAME, aveType);
		erf.setParameter(AleatoryMagAreaStdDevParam.NAME, 0.0);
		if (!timeIndep) {
			double startYear = 1970d + (double)ot/(double)ProbabilityModelsCalc.MILLISEC_PER_YEAR;
			erf.getParameter(HistoricOpenIntervalParam.NAME).setValue(startYear-1875d);
		}
		erf.getTimeSpan().setStartTimeInMillis(ot+1);
		erf.getTimeSpan().setDuration(duration);
		return erf;
	}
	
	public static UCERF3_GriddedSeisOnlyERF_ETAS buildGriddedERF(long ot, double duration) {
		UCERF3_GriddedSeisOnlyERF_ETAS erf = new UCERF3_GriddedSeisOnlyERF_ETAS();
		
		// set parameters
		erf.setParameter(BackgroundRupParam.NAME, BackgroundRupType.POINT);
		erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, false);
		erf.setParameter(MaximumMagnitudeParam.NAME, 8.3);
		erf.setParameter("Total Regional Rate",TotalMag5Rate.RATE_7p9);
		erf.setParameter("Spatial Seis PDF",SpatialSeisPDF.UCERF3);

		erf.getTimeSpan().setStartTimeInMillis(ot);
		erf.getTimeSpan().setDuration(duration);
		
		return erf;
	}
	
	public static boolean isAlreadyDone(File resultsDir) {
		File infoFile = new File(resultsDir, "infoString.txt");
		File eventsFile = new File(resultsDir, "simulatedEvents.txt");
		if (!eventsFile.exists())
			eventsFile = new File(resultsDir, "simulatedEvents.bin");
		if (!eventsFile.exists())
			eventsFile = new File(resultsDir, "simulatedEvents.bin.gz");
		if (!infoFile.exists() || !eventsFile.exists() || eventsFile.length() == 0l)
			return false;
		try {
			for (String line : Files.readLines(infoFile, Charset.defaultCharset())) {
				if (line.contains("Total num ruptures: "))
					return true;
			}
		} catch (IOException e) {}
		return false;
	}
	
	private static long getPrevRandSeed(File resultsDir) throws IOException {
		File infoFile = new File(resultsDir, "infoString.txt");
		if (!infoFile.exists())
			return -1;
		for (String line : Files.readLines(infoFile, Charset.defaultCharset())) {
			if (line.contains("randomSeed=")) {
				line = line.trim();
				line = line.substring(line.indexOf("=")+1);
				return Long.parseLong(line);
			}
		}
		return -1;
	}
	
	private synchronized void checkLoadCaches() throws IOException {
		if (fractionSrcAtPointList == null) {
			File cacheDir = config.getCacheDir();
			File fractionSrcAtPointListFile = new File(cacheDir, "sectDistForCubeCache");
			File srcAtPointListFile = new File(cacheDir, "sectInCubeCache");
			File isCubeInsideFaultPolygonFile = new File(cacheDir, "cubeInsidePolyCache");
			Preconditions.checkState(fractionSrcAtPointListFile.exists(),
					"cache file not found: "+fractionSrcAtPointListFile.getAbsolutePath());
			Preconditions.checkState(srcAtPointListFile.exists(),
					"cache file not found: "+srcAtPointListFile.getAbsolutePath());
			Preconditions.checkState(isCubeInsideFaultPolygonFile.exists(),
					"cache file not found: "+isCubeInsideFaultPolygonFile.getAbsolutePath());
			debug("loading cache from "+fractionSrcAtPointListFile.getAbsolutePath()+" ("+getMemoryDebug()+")");
			fractionSrcAtPointList = MatrixIO.floatArraysListFromFile(fractionSrcAtPointListFile);
			debug("loading cache from "+srcAtPointListFile.getAbsolutePath()+" ("+getMemoryDebug()+")");
			srcAtPointList = MatrixIO.intArraysListFromFile(srcAtPointListFile);
			debug("loading cache from "+srcAtPointListFile.getAbsolutePath()+" ("+getMemoryDebug()+")");
			isCubeInsideFaultPolygon = MatrixIO.intArrayFromFile(isCubeInsideFaultPolygonFile);
			debug("done loading caches ("+getMemoryDebug()+")");
		}
	}
	
	private String getMemoryDebug() {
		Runtime rt = Runtime.getRuntime();
		long totalMB = rt.totalMemory() / 1024 / 1024;
		long freeMB = rt.freeMemory() / 1024 / 1024;
		long usedMB = totalMB - freeMB;
		return "mem t/u/f: "+totalMB+"/"+usedMB+"/"+freeMB;
	}
	
	private class CalcRunnable implements Callable<Integer> {
		
		private int index;
		private Long randSeed;
		
		public CalcRunnable(int index) {
			this(index, null);
		}

		public CalcRunnable(int index, Long randSeed) {
			this.index = index;
			this.randSeed = randSeed;
		}

		@Override
		public Integer call() {
			System.gc();
			
			File resultsDir = getResultsDir(index);
			if (!config.isForceRecalc() && isAlreadyDone(resultsDir)) {
				debug(index+" is already done: "+resultsDir.getName());
				return index;
			}
			
			waitOnDirCreation(resultsDir, 5, 2000);
			
			debug("calculating "+index);

			debug("Instantiationg ERF");
			AbstractERF erf;
			FaultSystemSolution sol = null;
			if (config.isGriddedOnly()) {
				erf = buildGriddedERF(simulationOT, config.getDuration());
			} else {
				sol = checkOutFSS();
				erf = buildERF_millis(sol, config.isTimeIndependentERF(), config.getDuration(), simulationOT);
			}
			erf.updateForecast();

			debug("Done instantiating ERF");

			if (randSeed == null) {
				// redo with the previous random seed to avoid bias towards shorter running jobs in cases
				// with many restarts (as shorter jobs more likely to finish before wall time is up).
				try {
					randSeed = getPrevRandSeed(resultsDir);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				
				if (randSeed > 0)
					debug("Resuming old rand seed of "+randSeed+" for "+resultsDir.getName());
				else
					randSeed = r.nextLong();
			}

			ETAS_ParameterList params = new ETAS_ParameterList();
			params.setImposeGR(config.isImposeGR());
			params.setU3ETAS_ProbModel(config.getProbModel());
			// already applied if applicable, setting here for metadata
			params.setApplyGridSeisCorr(gridSeisCorrections != null);
			params.setApplySubSeisForSupraNucl(config.isApplySubSeisForSupraNucl());
			params.setTotalRateScaleFactor(config.getTotRateScaleFactor());
			
			boolean success = false;
			int attempts = 0;
			Throwable failureThrow = null;
			int retries = config.getNumRetries();
			if (retries < 1)
				retries = 1;
			
			while (!success && attempts < retries) {
				attempts++;
				try {
					if (config.isGriddedOnly()) {
						ETAS_Simulator_NoFaults.testMultiScenarioETAS_Simulation(resultsDir, (UCERF3_GriddedSeisOnlyERF_ETAS)erf, griddedRegion,
								triggerRuptures, histQkList, config.isIncludeSpontaneous(), config.isIncludeIndirectTriggering(),
								config.getGridSeisDiscr(), simulationName, randSeed, params);
					} else {
						checkLoadCaches();
						ETAS_Simulator.testMultiScenarioETAS_Simulation(resultsDir, (FaultSystemSolutionERF_ETAS)erf, griddedRegion,
								triggerRuptures, histQkList, config.isIncludeSpontaneous(), config.isIncludeIndirectTriggering(),
								config.getGridSeisDiscr(), simulationName, randSeed,
								fractionSrcAtPointList, srcAtPointList, isCubeInsideFaultPolygon, params);
					}
					
					debug("completed "+index);
					if (config.isBinaryOutput()) {
						// convert to binary
						File asciiFile = new File(resultsDir, "simulatedEvents.txt");
						List<ETAS_EqkRupture> catalog = ETAS_CatalogIO.loadCatalog(asciiFile);
						File binaryFile = new File(resultsDir, "simulatedEvents.bin");
						ETAS_CatalogIO.writeCatalogBinary(binaryFile, catalog);
						// make sure that the binary file really succeeded before deleting ascii
						if (binaryFile.length() > 0l)
							asciiFile.delete();
						else
							binaryFile.delete();
						debug("completed binary output "+index);
					}
					success = true;
				} catch (Throwable t) {
					failureThrow = t;
					System.out.println("FAIL!!!!!");
					debug(DebugLevel.ERROR, "Calc failed with seed "+randSeed+". Exception: "+t);
					t.printStackTrace();
					if (t.getCause() != null) {
						System.err.println("cause exception:");
						t.getCause().printStackTrace();
					}
					System.err.flush();
				}
			}
			
			if (sol != null)
				checkInFSS(sol);
			
			if (!success) {
				Preconditions.checkState(failureThrow == null);
				debug("Index "+index+" failed 3 times, bailing");
				ExceptionUtils.throwAsRuntimeException(failureThrow);
			}
			
			return index;
		}
		
	}
	
	private static long getMaxMemMB() {
		return Runtime.getRuntime().maxMemory() / 1024 / 1024;
	}
	
	private static int defaultNumThreads() {
		long maxMemMB = getMaxMemMB();
		int maxThreads = (int)(maxMemMB/7000);
		return Integer.max(1, Integer.min(maxThreads, Runtime.getRuntime().availableProcessors()));
	}
	
	public void calculateAll() {
		debug(DebugLevel.FINE, "max mem MB: "+getMaxMemMB());
		int threads = defaultNumThreads();
		debug(DebugLevel.FINE, "max threads calculated from max mem & available procs: "+threads);
		calculateAll(threads);
	}
	
	public void calculateAll(int numThreads) {
		ETAS_BinaryWriter binaryWriter = null;
		if (config.hasBinaryOutputFilters()) {
			debug(DebugLevel.FINE, "initializing binary filter writers");
			try {
				binaryWriter = new ETAS_BinaryWriter(config.getOutputDir(), config);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		calculate(numThreads, null, binaryWriter);
		if (binaryWriter != null) {
			try {
				debug(DebugLevel.FINE, "finalizing");
				binaryWriter.finalize();
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		shutdownExecutor();
	}
	
	public void calculateBatch(int numThreads, int[] batch) {
		calculate(numThreads, batch, null);
	}
	
	private ExecutorService exec;
	private int execThreads = 0;
	private ExecutorService getExecutor(int numThreads) {
		if (exec != null) {
			// previous executor
			if (numThreads > execThreads || exec.isShutdown()) {
				// need a bigger one or a new one
				debug(DebugLevel.DEBUG, "Shutting down previous executor with numThreads="+numThreads+", isShutdown="+exec.isShutdown());
				exec.shutdown();
				debug(DebugLevel.DEBUG, "building new executor for numThreads="+numThreads);
				exec = Executors.newFixedThreadPool(numThreads);
				execThreads = numThreads;
			}
		} else {
			debug(DebugLevel.DEBUG, "building new executor for numThreads="+numThreads);
			exec = Executors.newFixedThreadPool(numThreads);
			execThreads = numThreads;
		}
		return exec;
	}
	
	void shutdownExecutor() {
		if (exec != null) {
			debug(DebugLevel.DEBUG, "shutting down executor");
			exec.shutdown();
		}
	}
	
	void calculate(int numThreads, int[] batch, ETAS_BinaryWriter binaryWriter) {
		List<CalcRunnable> tasks = new ArrayList<>();
		
		Long randSeed = config.getRandomSeed();
		Preconditions.checkState(randSeed == null || config.getNumSimulations() == 1, "Can only specify a random seed with numSimulations=1");
		
		if (batch != null && batch.length > 0) {
			// we're doing a fixed index/batch
			for (int index : batch)
				tasks.add(new CalcRunnable(index, randSeed));
		} else {
			for (int index=0; index<config.getNumSimulations(); index++)
				tasks.add(new CalcRunnable(index, randSeed));
		}
		
		debug("starting "+tasks.size()+" simulations with "+numThreads+" threads");
		
		if (numThreads > 1) {
			ExecutorService exec = getExecutor(numThreads);
			
			List<Future<Integer>> futures = new ArrayList<>();
			
			for (CalcRunnable task : tasks)
				futures.add(exec.submit(task));
			
			for (Future<Integer> future : futures) {
				try {
					int index = future.get();
					if (binaryWriter != null) {
						debug(DebugLevel.FINE, "processing binary filter for "+index);
						File resultsDir = getResultsDir(index);
						binaryWriter.processCatalog(resultsDir);
					}
				} catch (InterruptedException | ExecutionException | IOException e) {
					exec.shutdownNow();
					if (e instanceof ExecutionException)
						throw ExceptionUtils.asRuntimeException(e.getCause());
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		} else {
			for (CalcRunnable task : tasks) {
				int index = task.call();
				if (binaryWriter != null) {
					File resultsDir = getResultsDir(index);
					try {
						binaryWriter.processCatalog(resultsDir);
					} catch (IOException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
			}
		}
		debug("done with "+tasks.size()+" simulations");
	}
	
	public static List<ETAS_EqkRupture> getFilteredNoSpontaneous(ETAS_Config config, List<ETAS_EqkRupture> catalog) {
		int numTriggerRuptures = config.getTriggerRuptures() == null ? 0 : config.getTriggerRuptures().size();
		if (numTriggerRuptures == 0 && (config.getTriggerCatalogFile() == null || config.isTreatTriggerCatalogAsSpontaneous()))
			// everything in this catalog is spontaneous, can't filter
			return null;
		if (catalog.isEmpty())
			return catalog;
		if (!config.isIncludeSpontaneous() && (config.getTriggerCatalogFile() == null || !config.isTreatTriggerCatalogAsSpontaneous()))
			// does not include any spontaneous ruptures
			return catalog;
		int maxParentID;
		if (config.getTriggerCatalogFile() != null && !config.isTreatTriggerCatalogAsSpontaneous())
			// we have a trigger catalog, and want to include descendants of that catalog
			maxParentID = catalog.get(0).getID()-1;
		else
			// only include descendants of the trigger ruptures
			maxParentID = numTriggerRuptures-1;
		int[] parentIDs = new int[maxParentID+1];
		for (int i=0; i<maxParentID; i++)
			parentIDs[i] = 0;
		return ETAS_SimAnalysisTools.getChildrenFromCatalog(catalog, parentIDs);
	}
	
	private static Options createOptions() {
		Options ops = new Options();

		Option threadsOption = new Option("t", "threads", true,
				"Number of calculation threads. Default is the calculated from max JVM memory (set via -Xmx)" +
						" and the number of available processors (in this case: "+defaultNumThreads()+")");
		threadsOption.setRequired(false);
		ops.addOption(threadsOption);
		
		return ops;
	}

	public static void main(String[] args) throws IOException {
		System.setProperty("java.awt.headless", "true");
		
		Options options = createOptions();
		
		CommandLineParser parser = new DefaultParser();
		
		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(ClassUtils.getClassNameWithoutPackage(ETAS_Launcher.class),
					options, true );
			System.exit(2);
			return;
		}
		
		args = cmd.getArgs();
		
		if (args.length != 1) {
			System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(ETAS_Launcher.class)
					+" [options] <conf-file.json>");
			System.exit(2);
		}
		
		File confFile = new File(args[0]);
		Preconditions.checkArgument(confFile.exists(),
				"configuration file doesn't exist: "+confFile.getAbsolutePath());
		ETAS_Config config = ETAS_Config.readJSON(confFile);
				
		ETAS_Launcher launcher = new ETAS_Launcher(config);
		
		if (cmd.hasOption("threads")) {
			int numThreads = Integer.parseInt(cmd.getOptionValue("threads"));
			launcher.calculateAll(numThreads);
		} else {
			launcher.calculateAll();
		}
	}

}
