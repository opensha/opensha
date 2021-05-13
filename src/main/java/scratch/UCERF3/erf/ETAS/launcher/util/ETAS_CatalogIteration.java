package scratch.UCERF3.erf.ETAS.launcher.util;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.BinarayCatalogsIterable;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileNameComparator;

import com.google.common.base.Stopwatch;

public class ETAS_CatalogIteration {
	
	public static interface Callback {
		public void processCatalog(ETAS_Catalog catalog, int index);
	}
	
	public static int processCatalogs(File catalogsFile, Callback callback) {
		return processCatalogs(catalogsFile, callback, -1, 0d);
	}
	
	public static int processCatalogs(File catalogsFile, Callback callback, int numToProcess, double minMag) {
		Iterator<ETAS_Catalog> catalogsIterator;
		int totalNum;
		if (catalogsFile.isDirectory()) {
			catalogsIterator = new ETAS_ResultsDirIterator(catalogsFile, minMag);
			totalNum = ((ETAS_ResultsDirIterator)catalogsIterator).files.size();
		} else {
			BinarayCatalogsIterable iterable = ETAS_CatalogIO.getBinaryCatalogsIterable(catalogsFile, minMag);
			totalNum = iterable.getNumCatalogs();
			catalogsIterator = iterable.iterator();
		}
		
		int numProcessed = 0;
		int modulus = 10;
		Stopwatch watch = Stopwatch.createStarted();
		DecimalFormat timeDF = new DecimalFormat("0.00");
		DecimalFormat percentDF = new DecimalFormat("0.0%");
		while (catalogsIterator.hasNext()) {
			if (numProcessed % modulus == 0) {
				double fractProcessed = (double)numProcessed/(double)totalNum;
				if (numProcessed > 0 && totalNum >= numProcessed
						&& (numProcessed >= 100 || fractProcessed >= 0.01)) {
					long elapsed = watch.elapsed(TimeUnit.MILLISECONDS);
					double secsElapsed = (double)elapsed/1000d;
					
					double catsPerSec = (double)numProcessed/secsElapsed;
					double seconds = (totalNum - numProcessed)/catsPerSec;
					double mins = seconds / 60d;
					double hours = mins / 60d;
					String timeStr;
					if (hours > 1)
						timeStr = timeDF.format(hours)+" h";
					else if (mins > 1)
						timeStr = timeDF.format(mins)+" m";
					else
						timeStr = timeDF.format(seconds)+" s";
					System.out.println("Processing catalog "+numProcessed+" ("
						+percentDF.format(fractProcessed)+" done, approx "+timeStr+" left)");
				} else {
					System.out.println("Processing catalog "+numProcessed);
				}
				if (numProcessed == modulus*10)
					modulus *= 10;
			}
			
			ETAS_Catalog catalog;
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
		watch.stop();
		
		return numProcessed;
	}
	
	static class ETAS_ResultsDirIterator implements Iterator<ETAS_Catalog> {
		
		private LinkedList<File> files;
		private double minMag;
		
		public ETAS_ResultsDirIterator(File dir, double minMag) {
			this.minMag = minMag;
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
		public ETAS_Catalog next() {
			File simFile = getSimFile(files.removeFirst());
			try {
				return ETAS_CatalogIO.loadCatalog(simFile, minMag);
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
