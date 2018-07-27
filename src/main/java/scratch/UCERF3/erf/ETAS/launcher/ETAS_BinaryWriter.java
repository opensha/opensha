package scratch.UCERF3.erf.ETAS.launcher;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_SimAnalysisTools;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.BinaryFilteredOutputConfig;

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
		
		List<ETAS_EqkRupture> catalog = ETAS_CatalogIO.loadCatalog(catalogFile);
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
		private int maxParentID;

		public InProgressWriter(File outputDir, int numCatalogs, ETAS_Config config, BinaryFilteredOutputConfig binaryConf) throws IOException {
			this.numCatalogs = numCatalogs;
			this.binaryConf = binaryConf;
			
			if (binaryConf.isDescendantsOnly()) {
				List<TriggerRupture> triggerRups = config.getTriggerRuptures();
				Preconditions.checkState((triggerRups != null && !triggerRups.isEmpty()) || config.getTriggerCatalogFile() != null,
						"Cannot write descendants only files without either trigger ruptures or a trigger catalog");
				if (!binaryConf.isIncludeTriggerCatalogDescendants()) {
					Preconditions.checkState(triggerRups != null && !triggerRups.isEmpty(),
							"Can't write descendants only files without trigger ruptures if includeTriggerCatalogDescendants=false");
					maxParentID = triggerRups.size()-1;
				} else {
					maxParentID = -1;
				}
			}
			
			String prefix = binaryConf.getPrefix();
			inProgressFile = new File(outputDir, prefix+"_partial.bin");
			destFile = new File(outputDir, prefix+".bin");
		}
		
		public synchronized void processCatalog(List<ETAS_EqkRupture> catalog) throws IOException {
			if (dOut == null)
				dOut = ETAS_CatalogIO.initCatalogsBinary(inProgressFile, numCatalogs);
			if (binaryConf.isDescendantsOnly() && !catalog.isEmpty()) {
				int maxParentID = this.maxParentID;
				if (maxParentID < 0)
					// track descendants of ruptures before the start of this catalog
					// this includes both trigger ruptures and a trigger (historical) catalog
					maxParentID = catalog.get(0).getID()-1;
				int[] parentIDs = new int[maxParentID];
				for (int i=0; i<maxParentID; i++)
					parentIDs[i] = 0;
				catalog = ETAS_SimAnalysisTools.getChildrenFromCatalog(catalog, parentIDs);
			}
			if (binaryConf.getMinMag() != null && binaryConf.getMinMag() > 0 && !catalog.isEmpty())
				catalog = ETAS_SimAnalysisTools.getAboveMagPreservingChain(catalog, binaryConf.getMinMag());
			ETAS_CatalogIO.writeCatalogBinary(dOut, catalog);
		}
		
		public void finalize() throws IOException {
			dOut.close();
			Files.move(inProgressFile, destFile);
		}
	}

}
