package scratch.UCERF3.erf.ETAS.launcher;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileNameComparator;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_SimAnalysisTools;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.BinaryFilteredOutputConfig;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_CatalogIteration;

class ETAS_BinaryWriter {
	
	private List<InProgressWriter> writers;
	
	public ETAS_BinaryWriter(File outputDir, ETAS_Config config) throws IOException {
		int numCatalogs = config.getNumSimulations();
		
		writers = new ArrayList<>();
		
		for (BinaryFilteredOutputConfig binaryConfig : config.getBinaryOutputFilters())
			writers.add(new InProgressWriter(outputDir, numCatalogs, config, binaryConfig));
	}
	
	public void processCatalog(File catalogDir) throws IOException {
		File catalogFile;
		
		// check ASCII first
		File asciiFile = new File(catalogDir, "simulatedEvents.txt");
		File binaryFile = new File(catalogDir, "simulatedEvents.bin");
		File binaryGZipFile = new File(catalogDir, "simulatedEvents.bin.gz");
		if (asciiFile.exists())
			catalogFile = asciiFile;
		else if (binaryFile.exists())
			catalogFile = binaryFile;
		else if (binaryGZipFile.exists())
			catalogFile = binaryGZipFile;
		else
			throw new IllegalStateException("No ETAS catalogs found in "+catalogDir.getAbsolutePath());
		
		List<ETAS_EqkRupture> catalog;
		try {
			catalog = ETAS_CatalogIO.loadCatalog(catalogFile);
		} catch (IllegalStateException e) {
			throw new IllegalStateException("Exception processing catalog "+catalogFile.getAbsolutePath());
		}
		processCatalog(catalog);
	}
	
	public void processCatalog(List<ETAS_EqkRupture> catalog) throws IOException {
		for (InProgressWriter writer : writers)
			writer.processCatalog(catalog);
	}
	
	public void finalize() throws IOException {
		for (InProgressWriter writer : writers)
			writer.finalize();
	}
	
	private class InProgressWriter {
		
		private File inProgressFile;
		private File destFile;
		private DataOutputStream dOut;

		private int numCatalogs;
		private BinaryFilteredOutputConfig binaryConf;
		private ETAS_Config config;

		public InProgressWriter(File outputDir, int numCatalogs, ETAS_Config config, BinaryFilteredOutputConfig binaryConf) throws IOException {
			this.numCatalogs = numCatalogs;
			this.binaryConf = binaryConf;
			this.config = config;
			
			String prefix = binaryConf.getPrefix();
			inProgressFile = new File(outputDir, prefix+"_partial.bin");
			destFile = new File(outputDir, prefix+".bin");
		}
		
		public synchronized void processCatalog(List<ETAS_EqkRupture> catalog) throws IOException {
			if (dOut == null)
				dOut = ETAS_CatalogIO.initCatalogsBinary(inProgressFile, numCatalogs);
			if (binaryConf.isDescendantsOnly() && !catalog.isEmpty())
				catalog = ETAS_Launcher.getFilteredNoSpontaneous(config, catalog);
			if (binaryConf.getMinMag() != null && binaryConf.getMinMag() > 0 && !catalog.isEmpty()) {
				if (binaryConf.isPreserveChainBelowMag()) {
					catalog = ETAS_SimAnalysisTools.getAboveMagPreservingChain(catalog, binaryConf.getMinMag());
				} else {
					List<ETAS_EqkRupture> filteredCatalog = new ArrayList<>();
					for (ETAS_EqkRupture rup : catalog)
						if (rup.getMag() >= binaryConf.getMinMag())
							filteredCatalog.add(rup);
					catalog = filteredCatalog;
				}
			}
			ETAS_CatalogIO.writeCatalogBinary(dOut, catalog);
			dOut.flush();
		}
		
		public void finalize() throws IOException {
			dOut.close();
			Files.move(inProgressFile, destFile);
		}
	}
	
	public static void main(String[] args) throws IOException {
		if (args.length < 1 || args.length > 2) {
			System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(ETAS_BinaryWriter.class)+" <etas-config.json> [<results-dir/catalogs.bin>]");
			System.exit(2);
		}
		
		File confFile = new File(args[0]);
		ETAS_Config config = ETAS_Config.readJSON(confFile);
		
		File outputDir = config.getOutputDir();
		Preconditions.checkState(outputDir.exists(), "ETAS output dir doesn't exist: "+outputDir.getAbsolutePath());
		
		File resultsDir;
		if (args.length == 2)
			resultsDir = new File(args[1]);
		else
			resultsDir = ETAS_Launcher.getResultsDir(outputDir);
		
		Preconditions.checkState(resultsDir.exists(), "ETAS results dir doesn't exist: "+resultsDir.getAbsolutePath());
		
		ETAS_BinaryWriter writer = new ETAS_BinaryWriter(config.getOutputDir(), config);
		
		int numProcessed = ETAS_CatalogIteration.processCatalogs(resultsDir, new ETAS_CatalogIteration.Callback() {
			
			@Override
			public void processCatalog(List<ETAS_EqkRupture> catalog, int index) {
				try {
					writer.processCatalog(catalog);
				} catch (IOException e) {
					ExceptionUtils.throwAsRuntimeException(e);
				}
			}
		});
		
		writer.finalize();
		System.out.println("Finished binary consolidation of "+numProcessed+" catalogs");
	}

}
