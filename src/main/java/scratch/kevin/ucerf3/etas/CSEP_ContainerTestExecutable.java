package scratch.kevin.ucerf3.etas;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.dom4j.DocumentException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.AbstractERF;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_Simulator;
import scratch.UCERF3.erf.ETAS.FaultSystemSolutionERF_ETAS;
import scratch.UCERF3.erf.ETAS.ETAS_Params.ETAS_ParameterList;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_StatewideCatalogCompletenessParam;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.LastEventData;
import scratch.UCERF3.utils.MatrixIO;
import scratch.UCERF3.utils.RELM_RegionUtils;

public class CSEP_ContainerTestExecutable {
	
	private File solFile;
	private File outputDir;
	private int threads;
	private int numSims;
	
	private Map<Integer, List<LastEventData>> lastEventData;
	private double[] gridSeisCorrections;
	private double duration;
	private long ot;
	private List<ETAS_EqkRupture> histQkList;
	
	private ArrayDeque<FaultSystemSolution> solCache = new ArrayDeque<>();
	
	// input params
	boolean includeSpontEvents = true;
	U3ETAS_ProbabilityModelOptions probModel = U3ETAS_ProbabilityModelOptions.FULL_TD;
	boolean applySubSeisForSupraNucl = true;
	double totRateScaleFactor = 1.14;
	boolean gridSeisCorr = true;
	boolean griddedOnly = false;
	boolean imposeGR = false;
	boolean includeIndirectTriggering = true;
	double gridSeisDiscr = 0.1;
	GriddedRegion griddedRegion = RELM_RegionUtils.getGriddedRegionInstance();
	
	List<float[]> fractionSrcAtPointList;
	List<int[]> srcAtPointList;
	int[] isCubeInsideFaultPolygon;
	
	private Random random = new Random();
	
	public CSEP_ContainerTestExecutable(File catFile, File cacheDir, File solFile, File outputDir,
			int numSims, int threads, double duration) throws IOException, DocumentException {
		this.solFile = solFile;
		this.duration = duration;
		this.outputDir = outputDir;
		this.numSims = numSims;
		this.threads = threads;
		
		/*
		 * INPUTS
		 */
		AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF = 2.55;
		ETAS_Simulator.D = false;
		
		lastEventData = LastEventData.load();
		
		if (gridSeisCorr) {
			File cacheFile = new File(cacheDir, "griddedSeisCorrectionCache");
			System.out.println("Loading gridded seismicity correction cache file from "+cacheFile.getAbsolutePath());
			gridSeisCorrections = MatrixIO.doubleArrayFromFile(cacheFile);
		}
		
		System.out.println("Loading fault system solution");
		FaultSystemSolution sol = checkOutFSS();
		histQkList = ETAS_Launcher.loadHistoricalCatalog(catFile, null, sol, Long.MAX_VALUE, null,
				U3ETAS_StatewideCatalogCompletenessParam.DEFAULT_VALUE);
		ot = histQkList.get(histQkList.size()-1).getOriginTime()+1;
		
		// purge any last event data after OT
		LastEventData.filterDataAfterTime(lastEventData, ot);
		
		/*
		 * Caches
		 */
		System.out.println("Loading caches");
		File fractionSrcAtPointListFile = new File(cacheDir, "sectDistForCubeCache");
		File srcAtPointListFile = new File(cacheDir, "sectInCubeCache");
		File isCubeInsideFaultPolygonFile = new File(cacheDir, "cubeInsidePolyCache");
		Preconditions.checkState(fractionSrcAtPointListFile.exists(),
				"cache file not found: "+fractionSrcAtPointListFile.getAbsolutePath());
		Preconditions.checkState(srcAtPointListFile.exists(),
				"cache file not found: "+srcAtPointListFile.getAbsolutePath());
		Preconditions.checkState(isCubeInsideFaultPolygonFile.exists(),
				"cache file not found: "+isCubeInsideFaultPolygonFile.getAbsolutePath());
		fractionSrcAtPointList = MatrixIO.floatArraysListFromFile(fractionSrcAtPointListFile);
		srcAtPointList = MatrixIO.intArraysListFromFile(srcAtPointListFile);
		isCubeInsideFaultPolygon = MatrixIO.intArrayFromFile(isCubeInsideFaultPolygonFile);
		
		checkInFSS(sol);
	}
	
	public void calculate() {
		ExecutorService exec = Executors.newFixedThreadPool(threads);
		
		List<Future<?>> futures = new ArrayList<>();
		
		System.out.println("Starting "+numSims+" simulations across "+threads+" threads");
		for (int i=0; i<numSims; i++)
			futures.add(exec.submit(new SimulationTask(i)));
		
		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				System.out.println("Error with simulation, exiting");
				e.printStackTrace();
				System.exit(1);
			}
		}
		
		System.out.println("All simulations completed successfully!");
		
		exec.shutdown();
	}
	
	public synchronized FaultSystemSolution checkOutFSS() {
		FaultSystemSolution sol;
		if (solCache.isEmpty()) {
			try {
				sol = FaultSystemIO.loadSol(solFile);
			} catch (IOException | DocumentException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			if (gridSeisCorrections != null)
				ETAS_Simulator.correctGriddedSeismicityRatesInERF(sol, false, gridSeisCorrections);
		} else {
			sol = solCache.pop();
		}
		// reset/populate last event data
		LastEventData.populateSubSects(sol.getRupSet().getFaultSectionDataList(), lastEventData);
		return sol;
	}
	
	public synchronized void checkInFSS(FaultSystemSolution sol) {
		solCache.push(sol);
	}
	
	private class SimulationTask implements Runnable {
		
		private int index;
		public SimulationTask(int index) {
			this.index = index;
		}

		@Override
		public void run() {
			try {
				System.out.println("Preparing inputs for simulation "+index);
				/*
				 * Do the simulation
				 */
				FaultSystemSolution sol = checkOutFSS();
				LastEventData.populateSubSects(sol.getRupSet().getFaultSectionDataList(), lastEventData);
				AbstractERF erf = ETAS_Launcher.buildERF_millis(sol, false, duration, ot);

				erf.updateForecast();
				
				ETAS_ParameterList params = new ETAS_ParameterList();
				params.setImposeGR(imposeGR);
				params.setU3ETAS_ProbModel(probModel);
				// already applied if applicable, setting here for metadata
				params.setApplyGridSeisCorr(gridSeisCorrections != null);
				params.setApplySubSeisForSupraNucl(applySubSeisForSupraNucl);
				params.setTotalRateScaleFactor(totRateScaleFactor);
				
				File myOutputDir = new File(outputDir, "sim_"+index);
				Preconditions.checkState(myOutputDir.exists() || myOutputDir.mkdir());
				String simulationName = "Simulation "+index;
				long randSeed;
				synchronized (random) {
					randSeed = random.nextLong();
				}
				
				System.out.println("Running simulation "+index);
				ETAS_EqkRupture scenarioRup = null;
				ETAS_Simulator.runETAS_Simulation(myOutputDir, (FaultSystemSolutionERF_ETAS)erf, griddedRegion, scenarioRup,
						histQkList, includeSpontEvents, includeIndirectTriggering, gridSeisDiscr, simulationName, randSeed,
						fractionSrcAtPointList, srcAtPointList, isCubeInsideFaultPolygon, params, null);
				
				// convert to binary
				File asciiFile = new File(myOutputDir, "simulatedEvents.txt");
				List<ETAS_EqkRupture> catalog = ETAS_CatalogIO.loadCatalog(asciiFile);
				File binaryFile = new File(myOutputDir, "simulatedEvents.bin");
				ETAS_CatalogIO.writeCatalogBinary(binaryFile, catalog);
				// make sure that the binary file really succeeded before deleting ascii
				if (binaryFile.length() > 0l)
					asciiFile.delete();
				else
					binaryFile.delete();
				
				System.out.println("Done with simulation "+index);
				
				checkInFSS(sol);
			} catch (Exception e) {
				System.out.println("Simulation failed!");
				e.printStackTrace();
				System.out.println("Exiting...");
				System.exit(1);
			}
		}
		
	}

	public static void main(String[] args) throws IOException, DocumentException {
		if (args.length != 7) {
			System.out.println("USAGE: <catalog-input-file> <cache-dir>"
					+ " <u3-sol-file> <output-dir> <num-simulations> <threads> <catalog-duration>");
			System.exit(2);
		}
		
		File catFile = new File(args[0]);
		Preconditions.checkState(catFile.exists(), "Catalog file doesn't exist: %s", catFile.getAbsolutePath());
		
		File cacheDir = new File(args[1]);
		Preconditions.checkState(cacheDir.exists(), "Cache dir doesn't exist: %s", cacheDir.getAbsolutePath());
		
		File solFile = new File(args[2]);
		Preconditions.checkState(cacheDir.exists(), "UCERF3 FSS file doesn't exist: %s", solFile.getAbsolutePath());
		
		File outputDir = new File(args[3]);
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir(), "Output directory doesn't exist and could not be created: %s");
		
		int numSims = Integer.parseInt(args[4]);
		Preconditions.checkState(numSims > 0);
		
		int threads = Integer.parseInt(args[5]);
		Preconditions.checkState(threads > 0);
		
		double duration = Double.parseDouble(args[6]);
		
		CSEP_ContainerTestExecutable calc = new CSEP_ContainerTestExecutable(catFile, cacheDir, solFile, outputDir, numSims, threads, duration);
		calc.calculate();
	}

}
