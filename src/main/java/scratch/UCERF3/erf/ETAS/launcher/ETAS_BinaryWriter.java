package scratch.UCERF3.erf.ETAS.launcher;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.BinarayCatalogsMetadataIterator;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_SimAnalysisTools;
import scratch.UCERF3.erf.ETAS.ETAS_SimulationMetadata;
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
	
	public HashSet<Integer> getDoneIndexes() {
		HashSet<Integer> doneSet = null;
		for (InProgressWriter writer : writers) {
			if (doneSet == null)
				doneSet = writer.doneSet;
			else
				doneSet.retainAll(writer.doneSet);
		}
		return doneSet;
	}
	
	public static File locateCatalogFile(File catalogDir) {
		// check ASCII first
		File asciiFile = new File(catalogDir, "simulatedEvents.txt");
		File binaryFile = new File(catalogDir, "simulatedEvents.bin");
		File binaryGZipFile = new File(catalogDir, "simulatedEvents.bin.gz");
		if (asciiFile.exists())
			return asciiFile;
		else if (binaryFile.exists())
			return binaryFile;
		else if (binaryGZipFile.exists())
			return binaryGZipFile;
		else
			throw new IllegalStateException("No ETAS catalogs found in "+catalogDir.getAbsolutePath());
	}
	
	public void processCatalog(int index, File catalogDir) throws IOException {
		// see if everything is prestaged and we can skip loading...
		boolean preStaged = true;
		for (InProgressWriter writer : writers) {
			File stagedFile = writer.binaryConf.getPreStagedCatalogFile(catalogDir);
			if (!stagedFile.exists()) {
				preStaged = false;
				break;
			}
		}
		if (preStaged) {
			for (InProgressWriter writer : writers)
				writer.processPreStaged(index, catalogDir);
			return;
		}
		
		File catalogFile = locateCatalogFile(catalogDir);
		
//		System.out.println("PROCESSING CATALOG FROM: "+catalogFile.getAbsolutePath());
		ETAS_Catalog catalog;
		try {
			catalog = ETAS_CatalogIO.loadCatalog(catalogFile);
		} catch (IllegalStateException e) {
			throw new IllegalStateException("Exception processing catalog "+catalogFile.getAbsolutePath());
		}
		ETAS_SimulationMetadata meta = catalog.getSimulationMetadata();
		if (meta != null && meta.catalogIndex < 0)
			// set the catalog index
			catalog.setSimulationMetadata(meta.getModCatalogIndex(index));
//		System.out.println("PROCESSING WRITERS FOR: "+catalogFile.getAbsolutePath());
		processCatalog(catalog);
//		System.out.println("DONE PROCESSING WRITERS FOR: "+catalogFile.getAbsolutePath());
	}
	
	public void processCatalog(ETAS_Catalog catalog) throws IOException {
		for (InProgressWriter writer : writers) {
//			System.out.println("\tPROCESSING WRITER: "+writer.inProgressFile.getName());
			writer.processCatalog(catalog);
		}
	}
	
	public void flushWriters() throws IOException {
		for (InProgressWriter writer : writers)
			if (writer.dOut != null)
				writer.dOut.flush();
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
		
		private HashSet<Integer> doneSet;
		private boolean warnedNoMeta = false;

		public InProgressWriter(File outputDir, int numCatalogs, ETAS_Config config, BinaryFilteredOutputConfig binaryConf) throws IOException {
			this.numCatalogs = numCatalogs;
			this.binaryConf = binaryConf;
			this.config = config;
			this.doneSet = new HashSet<>();
			
			if (outputDir != null) {
				String prefix = binaryConf.getPrefix();
				inProgressFile = new File(outputDir, prefix+"_partial.bin");
				if (inProgressFile.exists() && inProgressFile.length() > 100l && !config.isForceRecalc()) {
					System.out.println("Attempting to retstart, reading old "+inProgressFile.getName());
					BinarayCatalogsMetadataIterator metadataIt = ETAS_CatalogIO.getBinaryCatalogsMetadataIterator(inProgressFile);
					
					long writePos = -1;
					while (metadataIt.hasNext()) {
						if (!metadataIt.isNextFullyWritten())
							// partial, stop here
							break;
						long endIndex = metadataIt.getNextEndPos();
						ETAS_SimulationMetadata meta = metadataIt.next();
						if (meta != null && meta.catalogIndex >= 0) {
							Preconditions.checkState(!doneSet.contains(meta.catalogIndex),
									"Duplicate catalog index encountered: %s", meta.catalogIndex);
							doneSet.add(meta.catalogIndex);
							writePos = endIndex;
						} else {
							System.out.println("Old catalog format encountered without metadata, can't restart this (or subsequent) catalogs");
							break;
						}
					}
					metadataIt.close();
					if (writePos > 0l) {
						System.out.println("Will resume "+inProgressFile.getName()+" after "+doneSet.size()
							+" catalogs (at pos="+writePos+")");
						
						@SuppressWarnings("resource") // will be closed by dOut.close()
						RandomAccessFile raFile = new RandomAccessFile(inProgressFile, "rw");
						long len = raFile.length();
						Preconditions.checkState(writePos <= len, "bad writePos=%s with len=%s", writePos, len);
						if (writePos < len) {
							long overwrite = len - writePos;
							System.out.println("Write position is before end, will overwrite "+overwrite+" bytes (current lengh="+len+")");
						}
						raFile.seek(writePos);
						FileOutputStream fout = new FileOutputStream(raFile.getFD());
						dOut = new DataOutputStream(new BufferedOutputStream(fout, ETAS_CatalogIO.buffer_len));
					}
				}
				destFile = new File(outputDir, prefix+".bin");
			}
		}
		
		public synchronized void processCatalog(ETAS_Catalog catalog) throws IOException {
			if (dOut == null)
				dOut = ETAS_CatalogIO.initCatalogsBinary(inProgressFile, numCatalogs);
			ETAS_SimulationMetadata meta = catalog.getSimulationMetadata();
			if (meta == null) {
				if (!warnedNoMeta) {
					System.err.println("WARNING: catalog doesn't have metadata attached, old file version? future warnings supressed");
					warnedNoMeta = true;
				}
			} else {
				Preconditions.checkState(meta.catalogIndex >= 0, "Bad catalog index: %s", meta.catalogIndex);
				if (doneSet.contains(meta.catalogIndex)) {
					System.err.println("WARNING: already processed index "+meta.catalogIndex+", skipping");
					return;
				}
			}
			catalog = binaryConf.filter(config, catalog);
			ETAS_CatalogIO.writeCatalogBinary(dOut, catalog);
			if (meta != null)
				doneSet.add(meta.catalogIndex);
//			dOut.flush();
		}
		
		public synchronized void processPreStaged(int index, File catalogDir) throws IOException {
			if (dOut == null)
				dOut = ETAS_CatalogIO.initCatalogsBinary(inProgressFile, numCatalogs);
			File stagedFile = binaryConf.getPreStagedCatalogFile(catalogDir);
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(stagedFile));
			ByteStreams.copy(in, dOut);
			in.close();
			doneSet.add(index);
//			dOut.flush();
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
			public void processCatalog(ETAS_Catalog catalog, int index) {
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
