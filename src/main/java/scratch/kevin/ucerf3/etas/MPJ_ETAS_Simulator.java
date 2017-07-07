package scratch.kevin.ucerf3.etas;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.hpc.mpj.taskDispatch.MPJTaskCalculator;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
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
import org.opensha.sha.faultSurface.PointSurface;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_Simulator;
import scratch.UCERF3.erf.ETAS.FaultSystemSolutionERF_ETAS;
import scratch.UCERF3.erf.ETAS.ETAS_Params.ETAS_ApplyLongTermRatesInSamplingParam;
import scratch.UCERF3.erf.ETAS.ETAS_Params.ETAS_ParameterList;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ApplySubSeisRatesForSupraNucleationRatesParam;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_MaxCharFactorParam;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelParam;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_TotalRateScaleFactorParam;
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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.primitives.Ints;

public class MPJ_ETAS_Simulator extends MPJTaskCalculator {
	
	private int numSims;
	private FaultSystemSolution[] sols;
//	private FaultSystemSolutionERF_ETAS erf;
	private Map<Integer, List<LastEventData>> lastEventData;
	private File inputDir;
	private File outputDir;
	private boolean binaryOutput = false;
	
	private double duration;
	private Long ot;
	
	private int fssScenarioRupID;
	
	private List<float[]> fractionSrcAtPointList;
	private List<int[]> srcAtPointList;
	private int[] isCubeInsideFaultPolygon;
	
	private double[] gridSeisCorrections;
	
	private String simulationName;
	
	private boolean includeSpontEvents = true;
	private boolean includeIndirectTriggering = true;
	private double gridSeisDiscr = 0.1;
//	private boolean applyLongTermRates = ETAS_ApplyLongTermRatesInSamplingParam.DEFAULT;
	private boolean applySubSeisForSupraNucl = U3ETAS_ApplySubSeisRatesForSupraNucleationRatesParam.DEFAULT;
	private double totRateScaleFactor = U3ETAS_TotalRateScaleFactorParam.DEFAULT_VALUE;
	
	private boolean timeIndep = false;
	
	private GriddedRegion griddedRegion = RELM_RegionUtils.getGriddedRegionInstance();
	
	private List<ETAS_EqkRupture> histQkList;
	private ETAS_EqkRupture triggerRup;
	
	private U3ETAS_ProbabilityModelOptions probModel = U3ETAS_ProbabilityModelParam.DEFAULT;
	private boolean imposeGR = false;
	
	private static final int START_YEAR_DEFAULT = 2014;
	
	private boolean metadataOnly = false;
	
	private boolean griddedOnly = false;

	public MPJ_ETAS_Simulator(CommandLine cmd, File inputDir, File outputDir) throws IOException, DocumentException {
		super(cmd);
		
		this.inputDir = inputDir;
		this.outputDir = outputDir;
		
		// nasty kludgy setup
		ETAS_Simulator.D = false;
		AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF = 2.55;
		
		lastEventData = LastEventData.load();
		
		numSims = Integer.parseInt(cmd.getOptionValue("num"));
		
		if (cmd.hasOption("duration"))
			this.duration = Double.parseDouble(cmd.getOptionValue("duration"));
		else
			this.duration = 1d;
		
		includeSpontEvents = !cmd.hasOption("no-spontaneous");
		
		timeIndep = cmd.hasOption("indep");
		
		imposeGR = cmd.hasOption("impose-gr");
		
		if (cmd.hasOption("prob-model"))
			probModel = U3ETAS_ProbabilityModelOptions.valueOf(cmd.getOptionValue("prob-model"));
		
//		if (cmd.hasOption("apply-long-term-rates"))
//			applyLongTermRates = Boolean.parseBoolean(cmd.getOptionValue("apply-long-term-rates"));
		
		if (cmd.hasOption("apply-sub-seis-for-supra-nucl"))
			applySubSeisForSupraNucl = Boolean.parseBoolean(cmd.getOptionValue("apply-sub-seis-for-supra-nucl"));
		
		if (cmd.hasOption("tot-rate-scale-factor"))
			totRateScaleFactor = Double.parseDouble(cmd.getOptionValue("tot-rate-scale-factor"));
		
		binaryOutput = cmd.hasOption("binary");
		
		File solFile = new File(cmd.getOptionValue("sol-file"));
		Preconditions.checkArgument(solFile.exists(), "Solution file doesn't exist: "+solFile.getAbsolutePath());
		sols = new FaultSystemSolution[getNumThreads()];
		// must load in solution ofr each thread as last even data will be overridden/updated
		for (int i=0; i<sols.length; i++)
			sols[i] = FaultSystemIO.loadSol(solFile);
		
		if (cmd.hasOption("reset-sections")) {
			// reset individual sections
			// Format: epochMillis:s1,s2,...,sn[;epochMillis1:s1,s2]
			String resetArg = cmd.getOptionValue("reset-sections").trim();
			Preconditions.checkArgument(resetArg.contains(":"),
					"Reset sections format: epochMillis:s1,s2,...,sn[;epochMillis1:s1,s2]");
			List<String> args = Lists.newArrayList();
			while (resetArg.contains(";")) {
				int index = resetArg.indexOf(";");
				String subArg = resetArg.substring(0, index);
				Preconditions.checkState(!subArg.isEmpty());
				args.add(subArg);
				resetArg = resetArg.substring(index+1);
			}
			if (!resetArg.isEmpty())
				args.add(resetArg);
			Map<Long, int[]> resetMap = Maps.newHashMap();
			for (String arg : args) {
				if (rank == 0)
					debug("Processing subsection reset arg: "+arg);
				int index = arg.indexOf(":");
				Preconditions.checkState(index > 0);
				String timeStr = arg.substring(0, index);
				long time = Long.parseLong(timeStr);
				String sectStr = arg.substring(index+1);
				String[] sectSplit = sectStr.split(",");
				int[] sects = new int[sectSplit.length];
				for (int i=0; i<sectSplit.length; i++) {
					String sect = sectSplit[i];
					int s = Integer.parseInt(sect);
					sects[i] = s;
				}
				resetMap.put(time, sects);
			}
			
			for (Long time : resetMap.keySet()) {
				int[] sects = resetMap.get(time);
				if (rank == 0)
					debug("Resetting "+sects.length+" sects to "+time);
				for (FaultSystemSolution sol : sols) {
					FaultSystemRupSet rupSet = sol.getRupSet();
					for (int s : sects)
						rupSet.getFaultSectionData(s).setDateOfLastEvent(time);
				}
			}
		}
		
		griddedOnly = cmd.hasOption("gridded-only");
		if (griddedOnly) {
			ETAS_Simulator_NoFaults.D = false;
		}
		
		// if we have a triggered event
		if (cmd.hasOption("millis")) {
			ot = Long.parseLong(cmd.getOptionValue("millis"));
		} else {
			int startYear = START_YEAR_DEFAULT;
			if (cmd.hasOption("start-year"))
				startYear = Integer.parseInt(cmd.getOptionValue("start-year"));
			if (rank == 0)
				debug("Start year: "+startYear);
			ot = Math.round((startYear-1970.0)*ProbabilityModelsCalc.MILLISEC_PER_YEAR);
		}
		
		fssScenarioRupID = -1;
		
		FaultSystemRupSet rupSet = sols[0].getRupSet();
		
		histQkList = Lists.newArrayList();
		if (cmd.hasOption("trigger-catalog")) {
			// load in historical catalog
			File catFile = new File(cmd.getOptionValue("trigger-catalog"));
			Preconditions.checkArgument(catFile.exists(), "Catalog file doesn't exist: "+catFile.getAbsolutePath());
			ObsEqkRupList loadedRups = UCERF3_CatalogParser.loadCatalog(catFile);
			if (cmd.hasOption("rupture-surfaces")) {
				// add rupture surfaces
				FaultModels fm = getFaultModel(sols[0]);
				File surfsFile = new File(cmd.getOptionValue("rupture-surfaces"));
				Preconditions.checkArgument(surfsFile.exists(), "Rupture surfaces file doesn't exist: "+surfsFile.getAbsolutePath());
				FiniteFaultMappingData.loadRuptureSurfaces(surfsFile, loadedRups, fm, rupSet);
			}
			// filter for historical completeness
			loadedRups = U3_EqkCatalogStatewideCompleteness.load().getFilteredCatalog(loadedRups);
			if (rank == 0)
				debug("Loaded "+loadedRups.size()+" rups from catalog");
			int numWithSurfaces = 0;
			double maxTriggerMag = 0d;
			for (ObsEqkRupture rup : loadedRups) {
				if (rup.getOriginTime() > ot) {
					// skip all ruptures that occur after simulation start
					System.out.println("Skipping a M"+rup.getMag()+" after sim start ("
							+rup.getOriginTime()+" > "+ot+"): "+rup);
					continue;
				}
				ETAS_EqkRupture etasRup = new ETAS_EqkRupture(rup);
				etasRup.setID(Integer.parseInt(rup.getEventId()));
				histQkList.add(etasRup);
				if (rup.getRuptureSurface() != null && !(rup.getRuptureSurface() instanceof PointSurface))
					numWithSurfaces++;
				maxTriggerMag = Math.max(maxTriggerMag, rup.getMag());
			}
			if (rank == 0)
				debug("Seeding sim with "+histQkList.size()+" catalog ruptures ("+numWithSurfaces+" with surfaces)");
			if (rank == 0)
				debug("Max trigger mag in input catalog: "+maxTriggerMag);
		}
		
		Location triggerHypo = null;
		if (cmd.hasOption("trigger-loc")) {
			String locStr = cmd.getOptionValue("trigger-loc");
			String[] locSplit = locStr.split(",");
			Preconditions.checkState(locSplit.length == 3, "Location must be in format lat,lon,depth");
			triggerHypo = new Location(Double.parseDouble(locSplit[0]),
					Double.parseDouble(locSplit[1]), Double.parseDouble(locSplit[2]));
		}
		
		if (cmd.hasOption("trigger-rupture-id")) {
			// FSS rupture
			ETAS_EqkRupture mainshockRup = new ETAS_EqkRupture();
			mainshockRup.setOriginTime(ot);
			
			// Mojave M 7.05 rupture
//			int fssRupID=197792;
			fssScenarioRupID = Integer.parseInt(cmd.getOptionValue("trigger-rupture-id"));
//			int srcID = erf.getSrcIndexForFltSysRup(fssRupID);

//			ProbEqkRupture rupFromERF = erf.getSource(srcID).getRupture(0);
//			mainshockRup.setAveRake(rupFromERF.getAveRake());
//			mainshockRup.setMag(rupFromERF.getMag());
//			mainshockRup.setRuptureSurface(rupFromERF.getRuptureSurface());
			mainshockRup.setAveRake(rupSet.getAveRakeForRup(fssScenarioRupID));
			mainshockRup.setMag(rupSet.getMagForRup(fssScenarioRupID));
			mainshockRup.setRuptureSurface(rupSet.getSurfaceForRupupture(fssScenarioRupID, 1d, false));
			mainshockRup.setID(0);
			mainshockRup.setFSSIndex(fssScenarioRupID);
//			debug("test Mainshock: "+erf.getSource(srcID).getName());
			
			if (cmd.hasOption("trigger-mag"))
				mainshockRup.setMag(Double.parseDouble(cmd.getOptionValue("trigger-mag")));
			
			if (triggerHypo != null)
				mainshockRup.setHypocenterLocation(triggerHypo);
			
			// date of last event will be updated for this rupture in the calculateBatch method below
			
			simulationName = "FSS simulation. M="+mainshockRup.getMag()+", fss ID="+fssScenarioRupID;
			
			triggerRup = mainshockRup;
		} else if (cmd.hasOption("trigger-loc")) {
			ETAS_EqkRupture mainshockRup = new ETAS_EqkRupture();
			mainshockRup.setOriginTime(ot);	

			// 3-29-14 M 5.1 La Habra Earthquake
//			Location ptSurf = new Location(33.932,-117.917,4.8);	//
//			double mag = 6.2;	// test bigger magnitude
			
			Preconditions.checkArgument(cmd.hasOption("trigger-mag"), "trigger magnitude not supplied");
			double mag = Double.parseDouble(cmd.getOptionValue("trigger-mag"));
			
			if (cmd.hasOption("trigger-rake"))
				mainshockRup.setAveRake(Double.parseDouble(cmd.getOptionValue("trigger-rake")));
			else
				mainshockRup.setAveRake(0.0);
			mainshockRup.setMag(mag);
			mainshockRup.setPointSurface(triggerHypo);
			mainshockRup.setID(0);
			mainshockRup.setHypocenterLocation(triggerHypo);
			
			simulationName = "Pt Source. M="+mag+", "+triggerHypo;
			
			triggerRup = mainshockRup;
		} else {
			// only spontaneous
			
			// make sure no mag
			Preconditions.checkArgument(!cmd.hasOption("trigger-mag"), "trigger location not supplied");
			
			simulationName = "Spontaneous events";
		}
		
		metadataOnly = cmd.hasOption("metadata-only");
		
		if (cmd.hasOption("grid-seis-correction")) {
			File cacheFile = new File(inputDir, "griddedSeisCorrectionCache");
			if (rank == 0)
				debug("Loading gridded seismicity correction cache file from "+cacheFile.getAbsolutePath());
			gridSeisCorrections = MatrixIO.doubleArrayFromFile(cacheFile);
			
			for (int i=0; i<sols.length; i++)
				ETAS_Simulator.correctGriddedSeismicityRatesInERF(sols[i], false, gridSeisCorrections);
		}
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

	@Override
	protected int getNumTasks() {
		return numSims;
	}
	
	public String getMemoryDebug() {
		Runtime rt = Runtime.getRuntime();
		long totalMB = rt.totalMemory() / 1024 / 1024;
		long freeMB = rt.freeMemory() / 1024 / 1024;
		long usedMB = totalMB - freeMB;
		return "mem t/u/f: "+totalMB+"/"+usedMB+"/"+freeMB;
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		if (fractionSrcAtPointList == null) {
			File fractionSrcAtPointListFile = new File(inputDir, "sectDistForCubeCache");
			File srcAtPointListFile = new File(inputDir, "sectInCubeCache");
			File isCubeInsideFaultPolygonFile = new File(inputDir, "cubeInsidePolyCache");
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
		
		ArrayDeque<Integer> queue = new ArrayDeque<Integer>(Ints.asList(batch));
		
		Map<Integer, Integer> restartsMap = Maps.newHashMap();
		
		while (!queue.isEmpty()) {
			calcForDeque(queue, restartsMap);
		}
		
//		while (!queue.isEmpty()) {
//			int index = queue.poll();
//			System.gc();
//			
//			File outputDir = new File(this.outputDir, "results");
//			if (!outputDir.exists())
//				outputDir.mkdir();
//			
//			String runName = ""+index;
//			int desiredLen = ((getNumTasks()-1)+"").length();
//			while (runName.length() < desiredLen)
//				runName = "0"+runName;
//			runName = "sim_"+runName;
//			File resultsDir = new File(outputDir, runName);
//			
//			if (isAlreadyDone(resultsDir)) {
//				debug(index+" is already done: "+resultsDir.getName());
//				continue;
//			}
//			debug("calculating "+index);
//			
//			// reset date of last event
//			if (timeIndep) {
//				for (FaultSectionPrefData sect : sol.getRupSet().getFaultSectionDataList())
//					sect.setDateOfLastEvent(Long.MIN_VALUE);
//			} else {
//				LastEventData.populateSubSects(sol.getRupSet().getFaultSectionDataList(), lastEventData);
//			}
//			
//			debug("Instantiationg ERF");
//			FaultSystemSolutionERF_ETAS erf = buildERF(sol, timeIndep, duration);
//			
//			if (fssScenarioRupID >= 0) {
//				// This sets the rupture as having occurred in the ERF (to apply elastic rebound)
//				erf.setFltSystemSourceOccurranceTimeForFSSIndex(fssScenarioRupID, ot);
//			}
//			
//			erf.updateForecast();
//			debug("Done instantiating ERF");
//			
//			// redo with the previous random seed to avoid bias towards shorter running jobs in cases
//			// with many restarts (as shorter jobs more likely to finish before wall time is up).
//			long randSeed = getPrevRandSeed(resultsDir);
//			if (randSeed > 0 && restartsMap.get(index) == null)
//				debug("Resuming old rand seed of "+randSeed+" for "+runName);
//			else
//				randSeed = System.currentTimeMillis();
//			
//			
////			List<ETAS_EqkRupture> obsEqkRuptureList = Lists.newArrayList(this.obsEqkRuptureList);
//			try {
//				ETAS_Simulator.testETAS_Simulation(resultsDir, erf, griddedRegion, triggerRup, histQkList, includeSpontEvents,
//						includeIndirectTriggering, includeEqkRates, gridSeisDiscr, simulationName, randSeed,
//						fractionSrcAtPointList, srcAtPointList, isCubeInsideFaultPolygon, new ETAS_ParameterList());
//			} catch (Throwable t) {
//				debug("Calc failed with seed "+randSeed+". Exception: "+t);
//				t.printStackTrace();
//				if (t.getCause() != null) {
//					System.out.println("cause exceptoin:");
//					t.getCause().printStackTrace();
//				}
//				System.err.flush();
//				Integer prevFails = restartsMap.get(index);
//				if (prevFails == null)
//					prevFails = 0;
//				if (prevFails == 2) {
//					debug("Index "+index+" failed 3 times, bailing");
//					ExceptionUtils.throwAsRuntimeException(t);
//				}
//				restartsMap.put(index, prevFails+1);
//				debug("retrying "+index+" later");
//				queue.add(index);
//			}
//		}
	}
	
	private void calcForDeque(Deque<Integer> queue, Map<Integer, Integer> restartsMap) throws InterruptedException {
		CalcThread[] threads = new CalcThread[getNumThreads()];
		for (int i=0; i<threads.length; i++)
			threads[i] = new CalcThread(outputDir, sols[i], queue, restartsMap);
		
		for (CalcThread thread : threads)
			thread.start();
		
		for (CalcThread thread : threads)
			thread.join();
	}
	
	private File getResultsDir(int index) {
		File outputDir = new File(this.outputDir, "results");
		if (!outputDir.exists())
			outputDir.mkdir();
		
		String runName = ""+index;
		int desiredLen = ((getNumTasks()-1)+"").length();
		while (runName.length() < desiredLen)
			runName = "0"+runName;
		runName = "sim_"+runName;
		return new File(outputDir, runName);
	}
	
	private class CalcThread extends Thread {
		
		private File outputDir;
		private FaultSystemSolution sol;
		private Deque<Integer> queue;
		private Map<Integer, Integer> restartsMap;
		
		private CalcThread(File outputDir, FaultSystemSolution sol,
				Deque<Integer> queue, Map<Integer, Integer> restartsMap) {
			this.outputDir = outputDir;
			this.sol = sol;
			this.queue = queue;
			this.restartsMap = restartsMap;
		}

		@Override
		public void run() {
			if (metadataOnly && rank > 0) {
				queue.clear();
				return;
			}
			while (!queue.isEmpty()) {
				Integer index;
				synchronized (queue) {
					index = queue.poll();
				}
				if (index == null)
					// possible since multiple threads
					break;
				System.gc();

				File resultsDir = getResultsDir(index);

				try {
					if (!metadataOnly && isAlreadyDone(resultsDir)) {
						debug(index+" is already done: "+resultsDir.getName());
						continue;
					}
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				debug("calculating "+index);

				// reset date of last event
				if (timeIndep) {
					for (FaultSectionPrefData sect : sol.getRupSet().getFaultSectionDataList())
						sect.setDateOfLastEvent(Long.MIN_VALUE);
				} else {
					LastEventData.populateSubSects(sol.getRupSet().getFaultSectionDataList(), lastEventData);
				}

				debug("Instantiationg ERF");
				AbstractERF erf;
				if (griddedOnly) {
					erf = new UCERF3_GriddedSeisOnlyERF_ETAS();
					
//					// set parameters
					erf.setParameter(BackgroundRupParam.NAME, BackgroundRupType.POINT);
					erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, false);
					erf.setParameter(MaximumMagnitudeParam.NAME, 8.3);
					erf.setParameter("Total Regional Rate",TotalMag5Rate.RATE_7p9);
					erf.setParameter("Spatial Seis PDF",SpatialSeisPDF.UCERF3);

					erf.getTimeSpan().setStartTimeInMillis(ot);
					erf.getTimeSpan().setDuration(duration);
					
					erf.updateForecast();
				} else {
					erf = buildERF_millis(sol, timeIndep, duration, ot);
					
					if (fssScenarioRupID >= 0) {
						// This sets the rupture as having occurred in the ERF (to apply elastic rebound)
						((FaultSystemSolutionERF_ETAS)erf).setFltSystemSourceOccurranceTimeForFSSIndex(fssScenarioRupID, ot);
					}

					erf.updateForecast();
				}

				debug("Done instantiating ERF");

				// redo with the previous random seed to avoid bias towards shorter running jobs in cases
				// with many restarts (as shorter jobs more likely to finish before wall time is up).
				long randSeed;
				try {
					randSeed = getPrevRandSeed(resultsDir);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				if (randSeed > 0 && restartsMap.get(index) == null)
					debug("Resuming old rand seed of "+randSeed+" for "+resultsDir.getName());
				else
					randSeed = System.currentTimeMillis();

				ETAS_ParameterList params = new ETAS_ParameterList();
				params.setImposeGR(imposeGR);
				params.setU3ETAS_ProbModel(probModel);
				// already applied if applicable, setting here for metadata
				params.setApplyGridSeisCorr(gridSeisCorrections != null);
				params.setApplySubSeisForSupraNucl(applySubSeisForSupraNucl);
				params.setTotalRateScaleFactor(totRateScaleFactor);
				
				if (rank == 0) {
					synchronized (MPJ_ETAS_Simulator.class) {
						File metadataFile = new File(outputDir, "metadata.xml");
						if (!metadataFile.exists()) {
							try {
								writeMetadata(metadataFile, erf, params);
							} catch (IOException e) {
								System.err.println("WARNING: Failed to write metadata to "+metadataFile.getAbsolutePath());
								e.printStackTrace();
							}
						}
					}
					if (metadataOnly) {
						queue.clear();
						return;
					}
				}

				//				List<ETAS_EqkRupture> obsEqkRuptureList = Lists.newArrayList(this.obsEqkRuptureList);
				try {
					if (griddedOnly) {
						double gridSeisDiscr = 0.1;
						ETAS_Simulator_NoFaults.testETAS_Simulation(resultsDir, (UCERF3_GriddedSeisOnlyERF_ETAS)erf, griddedRegion,
								triggerRup, histQkList, includeSpontEvents, includeIndirectTriggering, gridSeisDiscr, simulationName,
								randSeed, params);
					} else {
						ETAS_Simulator.testETAS_Simulation(resultsDir, (FaultSystemSolutionERF_ETAS)erf, griddedRegion, triggerRup,
								histQkList, includeSpontEvents, includeIndirectTriggering, gridSeisDiscr, simulationName, randSeed,
								fractionSrcAtPointList, srcAtPointList, isCubeInsideFaultPolygon, params);
					}
					
					debug("completed "+index);
					if (binaryOutput) {
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
				} catch (Throwable t) {
					debug("Calc failed with seed "+randSeed+". Exception: "+t);
					t.printStackTrace();
					if (t.getCause() != null) {
						System.out.println("cause exceptoin:");
						t.getCause().printStackTrace();
					}
					System.err.flush();
					synchronized (queue) {
						Integer prevFails = restartsMap.get(index);
						if (prevFails == null)
							prevFails = 0;
						if (prevFails == 2) {
							debug("Index "+index+" failed 3 times, bailing");
							ExceptionUtils.throwAsRuntimeException(t);
						}
						restartsMap.put(index, prevFails+1);
						debug("retrying "+index+" later");
						queue.add(index);
					}
				}
			}
		}
	}
	
	private void consolidateBinary() throws IOException {
		// write out single binary catalog
		File outputFile = new File(this.outputDir, "results.bin");
		File resultsDir = new File(this.outputDir, "results");
		
		ETAS_CatalogIO.consolidateResultsDirBinary(resultsDir, outputFile, -10d);
	}
	
	public static final String TRIGGER_RUP_EL_NAME = "TriggerRup";
	public static final String HIST_CAT_EL_NAME = "HistCatalog";
	public static final String OTHER_PARAMS_EL_NAME = "MiscParams";
	
	private void writeMetadata(File metadataFile, AbstractERF erf, ETAS_ParameterList params)
			throws IOException {
		Document doc = XMLUtils.createDocumentWithRoot();
		Element root = doc.getRootElement();
		
//		ETAS_Simulator.testETAS_Simulation(resultsDir, erf, griddedRegion, triggerRup, histQkList, includeSpontEvents,
//		includeIndirectTriggering, gridSeisDiscr, simulationName, randSeed,
//		fractionSrcAtPointList, srcAtPointList, isCubeInsideFaultPolygon, params);

		Element otherParamsEl = root.addElement(OTHER_PARAMS_EL_NAME);
		otherParamsEl.addAttribute("includeSpontEvents", includeSpontEvents+"");
		otherParamsEl.addAttribute("includeIndirectTriggering", includeIndirectTriggering+"");
		otherParamsEl.addAttribute("gridSeisDiscr", gridSeisDiscr+"");
		otherParamsEl.addAttribute("numSimulations", numSims+"");
		otherParamsEl.addAttribute("duration", duration+"");
		otherParamsEl.addAttribute("ot", ot+"");
		otherParamsEl.addAttribute("binaryOutput", binaryOutput+"");
		
		// write out ERF
		erf.toXMLMetadata(root);
		
		// write out ETAS params
		params.toXMLMetadata(root);
		
		// write region
		griddedRegion.toXMLMetadata(root);
		
		// write trigger rupture
		if (triggerRup != null) {
			Element triggerEl = root.addElement(TRIGGER_RUP_EL_NAME);
			if (triggerRup.getHypocenterLocation() == null)
				triggerRup.setHypocenterLocation(triggerRup.getRuptureSurface().getFirstLocOnUpperEdge());
			triggerEl.addCDATA(ETAS_CatalogIO.getEventFileLine(triggerRup));
		}
		
		if (histQkList != null && !histQkList.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (ETAS_EqkRupture rup : histQkList)
				sb.append(ETAS_CatalogIO.getEventFileLine(rup)).append("\n");
			Element catEl = root.addElement(HIST_CAT_EL_NAME);
			catEl.addCDATA(sb.toString());
		}
		
		debug("Writing metadata to "+metadataFile.getAbsolutePath());
		XMLUtils.writeDocumentToFile(metadataFile, doc);
	}
	
	/**
	 * Creates ERF for use with ETAS simulations. Will not be updated
	 * @param sol
	 * @param timeIndep
	 * @param duration
	 * @return
	 */
	public static FaultSystemSolutionERF_ETAS buildERF(FaultSystemSolution sol, boolean timeIndep, double duration) {
		return buildERF(sol, timeIndep, duration, START_YEAR_DEFAULT);
	}
	
	public static FaultSystemSolutionERF_ETAS buildERF(FaultSystemSolution sol, boolean timeIndep, double duration,
			int startYear) {
		long ot = Math.round((startYear-1970.0)*ProbabilityModelsCalc.MILLISEC_PER_YEAR);
		return buildERF_millis(sol, timeIndep, duration, ot);
	}
	
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
	
	public static boolean isAlreadyDone(File resultsDir) throws IOException {
		File infoFile = new File(resultsDir, "infoString.txt");
		File eventsFile = new File(resultsDir, "simulatedEvents.txt");
		if (!eventsFile.exists())
			eventsFile = new File(resultsDir, "simulatedEvents.bin");
		if (!eventsFile.exists())
			eventsFile = new File(resultsDir, "simulatedEvents.bin.gz");
		if (!infoFile.exists() || !eventsFile.exists() || eventsFile.length() == 0l)
			return false;
		for (String line : Files.readLines(infoFile, Charset.defaultCharset())) {
			if (line.contains("Total num ruptures: "))
				return true;
		}
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

	@Override
	protected void doFinalAssembly() throws Exception {
		// do nothing
	}
	
	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		Option triggerRupID = new Option("r", "trigger-rupture-id", true, "Trigger FSS rupture ID");
		triggerRupID.setRequired(false);
		ops.addOption(triggerRupID);
		
		Option triggerLoc = new Option("l", "trigger-loc", true, "Trigger location in format lat,lon,depth");
		triggerLoc.setRequired(false);
		ops.addOption(triggerLoc);
		
		Option triggerMag = new Option("m", "trigger-mag", true, "Trigger magnitude for point source (used with --trigger-loc)");
		triggerMag.setRequired(false);
		ops.addOption(triggerMag);
		
		Option triggerRake = new Option("tr", "trigger-rake", true, "Trigger rake for point source (used with --trigger-loc, default=0)");
		triggerRake.setRequired(false);
		ops.addOption(triggerRake);
		
		Option triggerCat = new Option("tc", "trigger-catalog", true, "Trigger catalog in UCERF3 format (no fault sources)");
		triggerCat.setRequired(false);
		ops.addOption(triggerCat);
		
		Option rupSurfaces = new Option("rs", "rupture-surfaces", true, "Trigger catalog rupture surfaces");
		triggerCat.setRequired(false);
		ops.addOption(rupSurfaces);
		
		Option startYear = new Option("y", "start-year", true, "Start year for simulation (Default: "+START_YEAR_DEFAULT+")");
		startYear.setRequired(false);
		ops.addOption(startYear);
		
		Option millis = new Option("millis", "millis", true, "Start time for simulation in epoch milliseconds (Default uses --start-year)");
		millis.setRequired(false);
		ops.addOption(millis);
		
		Option numSims = new Option("n", "num", true, "Number of simulations");
		numSims.setRequired(true);
		ops.addOption(numSims);
		
		Option solFile = new Option("s", "sol-file", true, "Solution File");
		solFile.setRequired(true);
		ops.addOption(solFile);
		
		Option duration = new Option("d", "duration", true, "Simulation duration (years), default=1yr");
		duration.setRequired(false);
		ops.addOption(duration);
		
		Option noSpont = new Option("ns", "no-spontaneous", false, "Flag to disable spontaneous ruptures");
		noSpont.setRequired(false);
		ops.addOption(noSpont);
		
		Option indep = new Option("i", "indep", false, "Time independent probabilities. Elastic rebound will "
				+ "still be applied for fault initiating event and any triggered events.");
		indep.setRequired(false);
		ops.addOption(indep);
		
		Option probModel = new Option("p", "prob-model", true, "U3-ETAS probabilidy model. Options: "
				+Joiner.on(",").join(Lists.newArrayList(U3ETAS_ProbabilityModelOptions.values()))
				+". Default: "+U3ETAS_ProbabilityModelParam.DEFAULT.name());
		probModel.setRequired(false);
		ops.addOption(probModel);
		
		Option imposeGROption = new Option("gr", "impose-gr", false, "Impose G-R.");
		imposeGROption.setRequired(false);
		ops.addOption(imposeGROption);
		
//		Option maxCharOption = new Option("mc", "max-char-factor", true, "Max characteristic factor allowed"
//				+ " on a fault section. Default: "+U3ETAS_MaxCharFactorParam.DEFAULT_VALUE);
//		maxCharOption.setRequired(false);
//		ops.addOption(maxCharOption);
		
//		Option applyLongTermOption = new Option("ltr", "apply-long-term-rates", true, "Flag to apply/disable long"
//				+ " term rates (true/false). Default: "+ETAS_ApplyLongTermRatesInSamplingParam.DEFAULT);
//		applyLongTermOption.setRequired(false);
//		ops.addOption(applyLongTermOption);
		
		Option applyLongTermOption = new Option("subseissupra", "apply-sub-seis-for-supra-nucl", true, "Flag to apply/disable"
				+ " whether to correct gridded seismicity rates soas not to be less than the expected rate of aftershocks from "
				+ "supraseismogenic events. Default: "+U3ETAS_ApplySubSeisRatesForSupraNucleationRatesParam.DEFAULT);
		applyLongTermOption.setRequired(false);
		ops.addOption(applyLongTermOption);
		
		Option binaryOption = new Option("b", "binary", false, "Enables binary output. Default is ASCII.");
		binaryOption.setRequired(false);
		ops.addOption(binaryOption);
		
		Option metadataOnly = new Option("md", "metadata-only", false, "Write XML metadata file and exit.");
		metadataOnly.setRequired(false);
		ops.addOption(metadataOnly);
		
		Option gridSeisCorrectRates = new Option("gscorr", "grid-seis-correction", false, "Apply gridded seismicity correction"
				+ " using file in cache directory");
		gridSeisCorrectRates.setRequired(false);
		ops.addOption(gridSeisCorrectRates);
		
		Option totRateScaleFactor = new Option("scale", "tot-rate-scale-factor", true,
				"Total rate scale factor. Default: "+U3ETAS_TotalRateScaleFactorParam.DEFAULT_VALUE);
		totRateScaleFactor.setRequired(false);
		ops.addOption(totRateScaleFactor);
		
		Option griddedOnly = new Option("grid", "gridded-only", false, "Flag for the gridded seismicity only model");
		griddedOnly.setRequired(false);
		ops.addOption(griddedOnly);
		
		Option resetSections = new Option("reset", "reset-sections", true,
				"Reset date of last event of the given sub sections. "
				+ "Format: epochMillis:s1,s2,...,sn[;epochMillis1:s1,s2]");
		resetSections.setRequired(false);
		ops.addOption(resetSections);
		
		return ops;
	}
	
	public static void main(String[] args) {
		args = MPJTaskCalculator.initMPJ(args);
		
		try {
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args, MPJ_ETAS_Simulator.class);
			
			args = cmd.getArgs();
			
			if (args.length != 2) {
				System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(MPJ_ETAS_Simulator.class)
						+" [options] <input-dir> <output-dir>");
				abortAndExit(2);
			}
			
			File inputDir = new File(args[0]);
			Preconditions.checkArgument(inputDir.exists(),
					"input directory doesn't exist: "+inputDir.getAbsolutePath());
			File outputDir = new File(args[1]);
			Preconditions.checkArgument(outputDir.exists() || outputDir.mkdir(),
					"output directory doesn't exist: "+outputDir.getAbsolutePath());
			
			MPJ_ETAS_Simulator driver = new MPJ_ETAS_Simulator(cmd, inputDir, outputDir);
			driver.run();
			
			if (driver.rank == 0 && driver.binaryOutput)
				driver.consolidateBinary();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
