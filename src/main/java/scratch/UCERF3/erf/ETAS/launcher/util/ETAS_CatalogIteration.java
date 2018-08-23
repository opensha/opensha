package scratch.UCERF3.erf.ETAS.launcher.util;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileNameComparator;

public class ETAS_CatalogIteration {
	
	public static interface Callback {
		public void processCatalog(List<ETAS_EqkRupture> catalog, int index);
	}
	
	public static int processCatalogs(File catalogsFile, Callback callback) {
		return processCatalogs(catalogsFile, callback, -1);
	}
	
	public static int processCatalogs(File catalogsFile, Callback callback, int numToProcess) {
		Iterator<List<ETAS_EqkRupture>> catalogsIterator;
		if (catalogsFile.isDirectory())
			catalogsIterator = new ETAS_ResultsDirIterator(catalogsFile);
		else
			catalogsIterator = ETAS_CatalogIO.getBinaryCatalogsIterable(catalogsFile, 0).iterator();
		
		int numProcessed = 0;
		int modulus = 10;
		while (catalogsIterator.hasNext()) {
			if (numProcessed % modulus == 0) {
				System.out.println("Processing catalog "+numProcessed);
				if (numProcessed == modulus*10)
					modulus *= 10;
			}
			
			List<ETAS_EqkRupture> catalog;
			try {
				catalog = catalogsIterator.next();
			} catch (Exception e) {
				e.printStackTrace();
				System.err.flush();
				System.out.println("Partial catalog detected or other error, stopping with "+numProcessed+" catalogs");
				break;
			}
			
			callback.processCatalog(catalog, numProcessed);
			
			numProcessed++;
			
			if (numProcessed == numToProcess)
				break;
		}
		
		return numProcessed;
	}
	
	static class ETAS_ResultsDirIterator implements Iterator<List<ETAS_EqkRupture>> {
		
		private LinkedList<File> files;
		
		public ETAS_ResultsDirIterator(File dir) {
			files = new LinkedList<>();
			File[] subDirs = dir.listFiles();
			Arrays.sort(subDirs, new FileNameComparator());
			for (File subDir : subDirs) {
				if (ETAS_Launcher.isAlreadyDone(subDir))
					files.add(subDir);
			}
		}

		@Override
		public boolean hasNext() {
			return !files.isEmpty();
		}

		@Override
		public List<ETAS_EqkRupture> next() {
			File simFile = getSimFile(files.removeFirst());
			try {
				return ETAS_CatalogIO.loadCatalog(simFile);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		private File getSimFile(File dir) {
			File eventsFile = new File(dir, "simulatedEvents.txt");
			if (!eventsFile.exists())
				eventsFile = new File(dir, "simulatedEvents.bin");
			if (!eventsFile.exists())
				eventsFile = new File(dir, "simulatedEvents.bin.gz");
			if (eventsFile.exists())
				return eventsFile;
			throw new IllegalStateException("No events files found in "+dir.getAbsolutePath());
		}
		
	}

}
