package scratch.UCERF3.erf.ETAS.launcher;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.util.FileNameComparator;
import org.opensha.commons.util.FileUtils;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_CubeDiscretizationParams;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.utils.RELM_RegionUtils;

public class ETAS_ReproducibilityTest {
	
	private static File launcherDir;
	private static File fssFile;
	private static File cacheDir;
	private static File triggerCatalog;
	private static File triggerCatalogSurfaceMappings;
	private static TriggerRupture triggerRup;
	
	private static ETAS_CubeDiscretizationParams cubeParams;
	
	private static double duration = 1d;
	
	@BeforeClass
	public static void setUpBeforeClass() {
		launcherDir = ETAS_Config.resolvePath("${ETAS_LAUNCHER}");
//		fssFile = new File(launcherDir, "inputs/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip");
//		cacheDir = new File(launcherDir, "inputs/cache_fm3p1_ba");
//		triggerCatalog = new File(launcherDir, "inputs/u3_historical_catalog.txt");
//		triggerCatalogSurfaceMappings = new File(launcherDir, "inputs/u3_historical_catalog_finite_fault_mappings.xml");
		
		fssFile = new File(launcherDir, "inputs/small_test_solution.zip");
		cacheDir = new File(launcherDir, "inputs/cache_small_test_solution");
		triggerCatalog = new File(launcherDir, "inputs/u3_historical_catalog.txt");
		triggerCatalogSurfaceMappings = null;
		
		triggerRup = new TriggerRupture.FSS(5);
		
		System.setProperty("java.awt.headless", "true");
		
		cubeParams = new ETAS_CubeDiscretizationParams(RELM_RegionUtils.getGriddedRegionInstance());
	}
	
	private static ETAS_Config buildConfig(int numSimulations, long randSeed, boolean spontaneous,
			boolean scenario, boolean historical, boolean reuseERFs) {
		File outputDir = Files.createTempDir();
		TriggerRupture[] triggerRups = scenario ? new TriggerRupture[] {triggerRup} : new TriggerRupture[0];
		ETAS_Config config = new ETAS_Config(numSimulations, duration, spontaneous, cacheDir, fssFile, outputDir,
				historical ? triggerCatalog : null, historical ? triggerCatalogSurfaceMappings : null, triggerRups);
		config.setReuseERFs(reuseERFs);
		config.setSimulationName("Test");
		config.setRandomSeed(randSeed);
		config.setStartYear(2012);
		config.setBinaryOutput(false);
		config.setBinaryOutputFilters(null);
		config.setGridSeisCorr(false);
		return config;
	}
	
	@Test
	public void testSerialSpontOnlyNoReuse() throws IOException {
		testSerialReproducibility(true, false, false, false);
	}
	
	@Test
	public void testSerialSpontOnlyReuse() throws IOException {
		testSerialReproducibility(true, false, false, true);
	}
	
	@Test
	public void testSerialHistoricalOnlyNoReuse() throws IOException {
		testSerialReproducibility(false, false, true, false);
	}
	
	@Test
	public void testSerialHistoricalOnlyReuse() throws IOException {
		testSerialReproducibility(false, false, true, true);
	}
	
	@Test
	public void testSerialScenarioOnlyNoReuse() throws IOException {
		testSerialReproducibility(false, true, false, false);
	}
	
	@Test
	public void testSerialScenarioOnlyReuse() throws IOException {
		testSerialReproducibility(false, true, false, true);
	}
	
	@Test
	public void testSerialEverythingNoReuse() throws IOException {
		testSerialReproducibility(true, true, true, false);
	}
	
	@Test
	public void testSerialEverythingReuse() throws IOException {
		testSerialReproducibility(true, true, true, true);
	}
	
	@Test
	public void testParallelSpontOnlyNoReuse() throws IOException {
		testParallelReproducibility(true, false, false, false);
	}
	
	@Test
	public void testParallelSpontOnlyReuse() throws IOException {
		testParallelReproducibility(true, false, false, true);
	}
	
	@Test
	public void testParallelHistoricalOnlyNoReuse() throws IOException {
		testParallelReproducibility(false, false, true, false);
	}
	
	@Test
	public void testParallelHistoricalOnlyReuse() throws IOException {
		testParallelReproducibility(false, false, true, true);
	}
	
	@Test
	public void testParallelScenarioOnlyNoReuse() throws IOException {
		testParallelReproducibility(false, true, false, false);
	}
	
	@Test
	public void testParallelScenarioOnlyReuse() throws IOException {
		testParallelReproducibility(false, true, false, true);
	}
	
	@Test
	public void testParallelEverythingNoReuse() throws IOException {
		testParallelReproducibility(true, true, true, false);
	}
	
	@Test
	public void testParallelEverythingReuse() throws IOException {
		testParallelReproducibility(true, true, true, true);
	}
	
	private static final int num_seeds_each = 10;
	private static final int num_sims_each_seed = 2;
	
	private static long[] buildSeeds(long randSeed) {
		Random r = new Random(randSeed);
		long[] seeds = new long[num_seeds_each*num_sims_each_seed];
		// <seed1> <seed2> <seed3> <seed1> <seed2> <seed3>
		for (int i=0; i<num_seeds_each; i++) {
			long seed = r.nextInt();
			for (int j=0; j<num_sims_each_seed; j++)
				seeds[i + j*num_seeds_each] = seed;
		}
		return seeds;
	}
	
	private void testSerialReproducibility(boolean spontaneous, boolean scenario, boolean historical, boolean reuseERFs) throws IOException {
		long randSeed = System.currentTimeMillis();
		long[] seeds = buildSeeds(randSeed);
		int numSimulations = seeds.length;
		ETAS_Config config = buildConfig(numSimulations, randSeed, spontaneous, scenario, historical, reuseERFs);
		List<List<ETAS_EqkRupture>> catalogs = calculate(config, 1, seeds);
		
		checkAll(config, catalogs);
	}
	
	private void testParallelReproducibility(boolean spontaneous, boolean scenario, boolean historical, boolean reuseERFs) throws IOException {
		int numThreads = Runtime.getRuntime().availableProcessors();
		if (numThreads > num_seeds_each)
			numThreads = num_seeds_each;
		if (numThreads < 3)
			numThreads = 3;
		long randSeed = System.currentTimeMillis();
		long[] seeds = buildSeeds(randSeed);
		int numSimulations = seeds.length;
		ETAS_Config config = buildConfig(numSimulations, randSeed, spontaneous, scenario, historical, reuseERFs);
		List<List<ETAS_EqkRupture>> catalogs = calculate(config, numThreads, seeds);
		
		checkAll(config, catalogs);
	}
	
	private void checkAll(ETAS_Config config, List<List<ETAS_EqkRupture>> catalogs) {
		assertEquals(num_seeds_each*num_sims_each_seed, catalogs.size());
		for (int i=0; i<num_seeds_each; i++) {
			List<ETAS_EqkRupture> firstCatalog = catalogs.get(i);
			for (int j=1; j<num_sims_each_seed; j++) {
				List<ETAS_EqkRupture> compCatalog = catalogs.get(i + j*num_seeds_each);
				checkEquals(config.getSimulationStartTimeMillis(), firstCatalog, compCatalog);
			}
		}
	}

	private List<List<ETAS_EqkRupture>> calculate(ETAS_Config config, int numThreads, long[] seeds) throws IOException {
		ETAS_Launcher launcher = new ETAS_Launcher(config, true, config.getRandomSeed(), cubeParams);
		if (seeds != null)
			launcher.setRandomSeeds(seeds);
		launcher.calculateAll(numThreads);
		File resultsDir = new File(config.getOutputDir(), "results");
		File[] simDirs = resultsDir.listFiles();
		Arrays.sort(simDirs, new FileNameComparator());
		List<List<ETAS_EqkRupture>> catalogs = new ArrayList<>();
		for (File simDir : simDirs) {
			if (!simDir.getName().startsWith("sim_"))
				continue;
			File catFile = new File(simDir, "simulatedEvents.bin.gz");
			if (!catFile.exists())
				catFile = new File(simDir, "simulatedEvents.bin");
			if (!catFile.exists())
				catFile = new File(simDir, "simulatedEvents.txt");
			Preconditions.checkState(catFile.exists(), "No catalog found in %s", simDir.getAbsolutePath());
			catalogs.add(ETAS_CatalogIO.loadCatalog(catFile));
		}
		FileUtils.deleteRecursive(config.getOutputDir());
		return catalogs;
	}
	
	private void checkEquals(long simOT, List<ETAS_EqkRupture> cat1, List<ETAS_EqkRupture> cat2) {
		int maxNum = Integer.max(cat1.size(), cat2.size());
		
		if (cat1.size() == 0 || cat2.size() == 0) {
			assertEquals("One catalalog is empty, but the other is not", cat1.size(), cat2.size());
			return;
		}
		
		String sizeMessage = "Catalog 1 has "+cat1.size()+" events in "+timeDiffYears(simOT, cat1.get(cat1.size()-1).getOriginTime())+" yrs, "
				+ "Catalog 2 has "+cat2.size()+" events in "+timeDiffYears(simOT, cat2.get(cat2.size()-1).getOriginTime())+" yrs";
		for (int i=0; i<maxNum; i++) {
			assertTrue(sizeMessage, i<cat1.size());
			assertTrue(sizeMessage, i<cat2.size());
			ETAS_EqkRupture rup1 = cat1.get(i);
			ETAS_EqkRupture rup2 = cat2.get(i);
			
			String message = "index="+i+", "+timeDiffYears(simOT, rup1.getOriginTime())+" yrs into simulation";
			message += "\n\tRup 1:\t"+ETAS_CatalogIO.getEventFileLine(rup1);
			message += "\n\tRup 2:\t"+ETAS_CatalogIO.getEventFileLine(rup2);
			message += "\n";
			
			assertEquals("ID mismatch at "+message, rup1.getID(), rup2.getID());
			assertEquals("Time mismatch at "+message, rup1.getOriginTime(), rup2.getOriginTime());
			assertEquals("Parent ID "+message, rup1.getParentID(), rup2.getParentID());
			assertEquals("FSS index mismatch at "+message, rup1.getFSSIndex(), rup2.getFSSIndex());
			assertEquals("Grid node mismatch at "+message, rup1.getGridNodeIndex(), rup2.getGridNodeIndex());
			assertEquals("Generation mismatch at "+message, rup1.getGeneration(), rup2.getGeneration());
			assertEquals("Mag mismatch at "+message, rup1.getMag(), rup2.getMag(), 0.0001);
			assertEquals("Distance to parent mismatch at "+message, rup1.getDistanceToParent(), rup2.getDistanceToParent(), 0.0001);
			assertEquals("Hypocenter location mismatch at "+message, rup1.getHypocenterLocation(), rup2.getHypocenterLocation());
		}
	}
	
	private static float timeDiffYears(long startOT, long time) {
		return (float)((time - startOT)/ProbabilityModelsCalc.MILLISEC_PER_YEAR);
	}

}
