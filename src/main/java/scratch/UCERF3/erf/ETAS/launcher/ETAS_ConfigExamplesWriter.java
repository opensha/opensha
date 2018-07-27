package scratch.UCERF3.erf.ETAS.launcher;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.geo.Location;

import com.google.common.base.Preconditions;

class ETAS_ConfigExamplesWriter {

	public static void main(String[] args) throws IOException {
		File examplesDir;
		if (args.length == 1)
			examplesDir = new File(args[0]);
		else
			examplesDir = new File("/home/kevin/git/ucerf3-etas-launcher/json_examples");
		Preconditions.checkState(examplesDir.exists() || examplesDir.mkdir());
		
//		File outputDir = new File("/path/to/output");
//		File cacheDir = new File("/path/to/cache_fm3p1_ba");
//		File fssFile = new File("/path/to/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip");
		File outputDir = new File("/tmp/etas_output");
		File cacheDir = new File("/home/kevin/git/ucerf3-etas-launcher/cache_fm3p1_ba");
		File fssFile = new File("/home/kevin/workspace/opensha-ucerf3/src/scratch/UCERF3/data/scratch/InversionSolutions/"
				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip");
		
		writeSpontOnlyExamples(examplesDir, outputDir, cacheDir, fssFile);
		writeInputCatalogWithSpontaneous(examplesDir, outputDir, cacheDir, fssFile);
		writeSpontWithHistoricalExamples(examplesDir, outputDir, cacheDir, fssFile);
		writeNorthridgePointSource(examplesDir, outputDir, cacheDir, fssFile);
		writeNorthridgeBySubsectsSource(examplesDir, outputDir, cacheDir, fssFile);
		writeNorthridgeByFSSIndex(examplesDir, outputDir, cacheDir, fssFile);
		writeMultiRuptureExample(examplesDir, outputDir, cacheDir, fssFile);
	}
	
	private static void writeSpontOnlyExamples(File examplesDir, File outputDir, File cacheDir, File fssFile) throws IOException {
		int numSimulations = 10000;
		double simDuration = 10d;
		int startYear = 2018;
		boolean includeSpontaneous = true;
		
		ETAS_Config config = new ETAS_Config(numSimulations, simDuration, includeSpontaneous, cacheDir, fssFile, outputDir);
		
		config.setStartYear(startYear);
		
		config.writeJSON(new File(examplesDir, "spontaneous_only_simulation.json"));
	}
	
	private static void writeSpontWithHistoricalExamples(File examplesDir, File outputDir, File cacheDir, File fssFile) throws IOException {
		int numSimulations = 10000;
		double simDuration = 10d;
		int startYear = 2012;
		boolean includeSpontaneous = true;
		
		ETAS_Config config = new ETAS_Config(numSimulations, simDuration, includeSpontaneous, cacheDir, fssFile, outputDir);
		
		config.setStartYear(startYear);
		config.setTriggerCatalog(new File("/path/to/u3_historical_catalog.txt"));
		config.setTriggerCatalogSurfaceMappings(new File("/path/to/u3_historical_catalog_finite_fault_mappings.xml"));
		config.buildDefaultBinaryOutputFilters();
		
		config.writeJSON(new File(examplesDir, "spontaneous_with_historical_simulation.json"));
	}
	
	private static void writeInputCatalogWithSpontaneous(File examplesDir, File outputDir, File cacheDir, File fssFile) throws IOException {
		int numSimulations = 10000;
		double simDuration = 10d;
		int startYear = 2018;
		boolean includeSpontaneous = false;
		
		ETAS_Config config = new ETAS_Config(numSimulations, simDuration, includeSpontaneous, cacheDir, fssFile, outputDir);
		
		config.setStartYear(startYear);
		config.setTriggerCatalog(new File("/path/to/trigger_catalog.txt"));
		config.buildDefaultBinaryOutputFilters();
		
		config.writeJSON(new File(examplesDir, "input_catalog_simulation.json"));
	}
	
	private static void writeNorthridgePointSource(File examplesDir, File outputDir, File cacheDir, File fssFile) throws IOException {
		int numSimulations = 10000;
		double simDuration = 10d;
		long ot = 758809855000l;
		boolean includeSpontaneous = false;
		
		ETAS_Config config = new ETAS_Config(numSimulations, simDuration, includeSpontaneous, cacheDir, fssFile, outputDir);
		
		config.setStartTimeMillis(ot);
		config.addTriggerRupture(new TriggerRupture.Point(new Location(34.213, -118.537, 18.20), null, 6.7));
		config.buildDefaultBinaryOutputFilters();
		
		config.writeJSON(new File(examplesDir, "northridge_point_source_simulation.json"));
	}
	
	private static void writeNorthridgeBySubsectsSource(File examplesDir, File outputDir, File cacheDir, File fssFile) throws IOException {
		int numSimulations = 10000;
		double simDuration = 10d;
		long ot = 758809855000l;
		boolean includeSpontaneous = false;
		
		ETAS_Config config = new ETAS_Config(numSimulations, simDuration, includeSpontaneous, cacheDir, fssFile, outputDir);
		
		config.setStartTimeMillis(ot);
		config.addTriggerRupture(new TriggerRupture.SectionBased(new int[] {1409, 1410, 1411, 1412, 1413}, null, 6.7));
		config.buildDefaultBinaryOutputFilters();
		
		config.writeJSON(new File(examplesDir, "northridge_sub_sect_simulation.json"));
	}
	
	private static void writeNorthridgeByFSSIndex(File examplesDir, File outputDir, File cacheDir, File fssFile) throws IOException {
		int numSimulations = 10000;
		double simDuration = 10d;
		long ot = 758809855000l;
		boolean includeSpontaneous = false;
		
		ETAS_Config config = new ETAS_Config(numSimulations, simDuration, includeSpontaneous, cacheDir, fssFile, outputDir);
		
		config.setStartTimeMillis(ot);
		config.addTriggerRupture(new TriggerRupture.FSS(187455, null, 6.7));
		config.buildDefaultBinaryOutputFilters();
		
		config.writeJSON(new File(examplesDir, "northridge_fss_index_simulation.json"));
	}
	
	private static void writeMultiRuptureExample(File examplesDir, File outputDir, File cacheDir, File fssFile) throws IOException {
		int numSimulations = 50;
		double simDuration = 10d;
		long ot = System.currentTimeMillis();
		boolean includeSpontaneous = false;
		
		ETAS_Config config = new ETAS_Config(numSimulations, simDuration, includeSpontaneous, cacheDir, fssFile, outputDir);
		
		config.setStartTimeMillis(ot);
		config.addTriggerRupture(new TriggerRupture.FSS(187455, ot - 86400000, 6.7)); // one days prior
		config.addTriggerRupture(new TriggerRupture.Point(new Location(34.213, -118.537, 18.20), ot-60000000, 5.3));
		config.addTriggerRupture(new TriggerRupture.Point(new Location(34.257, -118.51, 14), ot-35000000, 5.6));
		config.addTriggerRupture(new TriggerRupture.SectionBased(new int[] {1412}, ot, 6.2));
		config.buildDefaultBinaryOutputFilters();
		
		config.writeJSON(new File(examplesDir, "multiple_ruptures_example_simulation.json"));
		
	}

}
