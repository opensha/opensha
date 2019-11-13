package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotPreferences;
import org.opensha.commons.util.ExceptionUtils;

import com.google.common.base.Stopwatch;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.erf.ETAS.analysis.SimulationMarkdownGenerator.PlotResult;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_CatalogIteration;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_CatalogIteration.Callback;

public abstract class ETAS_AbstractPlot {
	
	private ETAS_Config config;
	private ETAS_Launcher launcher;
	
	private Stopwatch processStopwatch;
	
	private AsyncManager asyncManager;
	
	private int numProcessed = 0;

	protected ETAS_AbstractPlot(ETAS_Config config, ETAS_Launcher launcher) {
		this.config = config;
		this.launcher = launcher;
		processStopwatch = Stopwatch.createUnstarted();
		
		if (isProcessAsync())
			asyncManager = new AsyncManager(config);
	}
	
	/**
	 * Gets the version number for this plot. This is used when regenerating plots for
	 * a simulation to determine if this one needs to be rebuilt. The default implementation of
	 * shouldReplot(Integer, Long) returns true if this version number is greater than the
	 * previous version (or the previous version is null), but can be overridden for advanced
	 * functionality.
	 * @return
	 */
	public abstract int getVersion();
	
	/**
	 * Checks if this plot should be regenerated, based on the previous version run and 
	 * previous plot time. Default implementation returns true if prevResult is null,
	 * or if prevResult.version is less than getVersion()
	 * @param prevResult previous plot result (can be null)
	 * @return true if plot should be regenerated, false otherwise
	 */
	public boolean shouldReplot(PlotResult prevResult) {
		return shouldReplot(prevResult, getVersion());
	}
	
	static boolean shouldReplot(PlotResult prevResult, int version) {
		return prevResult == null || prevResult.version < version;
	}
	
	/**
	 * If true, and spontaneous ruptures exist in this simulation, the triggeredOnlyCatalog
	 * will be populated
	 * @return
	 */
	public abstract boolean isFilterSpontaneous();
	
	/**
	 * @return true if this is an evaluation plot with real data. If true, then the plot generator code will
	 * always replot it (but the cron job will defer to shouldReplot(...)
	 */
	public boolean isEvaluationPlot() {
		return false;
	}
	
	protected void processCatalogsFile(File catalogsFile) {
		FaultSystemSolution fss = getLauncher().checkOutFSS();
		ETAS_CatalogIteration.processCatalogs(catalogsFile, new Callback() {
			
			@Override
			public void processCatalog(ETAS_Catalog catalog, int index) {
				ETAS_AbstractPlot.this.processCatalog(catalog, fss);
			}
		});
		getLauncher().checkInFSS(fss);
	}
	
	public final void processCatalog(ETAS_Catalog catalog, FaultSystemSolution fss) {
		ETAS_Catalog triggeredOnlyCatalog = null;
		if (isFilterSpontaneous())
			triggeredOnlyCatalog = ETAS_Launcher.getFilteredNoSpontaneous(config, catalog);
		processCatalog(catalog, triggeredOnlyCatalog, fss);
	}
	
	public final void processCatalog(ETAS_Catalog completeCatalog,
			ETAS_Catalog triggeredOnlyCatalog, FaultSystemSolution fss) {
		processStopwatch.start();
		try {
			if (asyncManager == null)
				doProcessCatalog(completeCatalog, triggeredOnlyCatalog, fss);
			else
				asyncManager.processAsync(completeCatalog, triggeredOnlyCatalog, fss);
		} catch (RuntimeException e) {
			throw e;
		} finally {
			processStopwatch.stop();
		}
		numProcessed++;
	}
	
	/**
	 * @return the number of catalogs processed, assuming that any asynchronous ones have already completed
	 */
	protected int getNumProcessed() {
		return numProcessed;
	}
	
	public long getProcessTimeMS() {
		return processStopwatch.elapsed(TimeUnit.MILLISECONDS);
	}
	
	protected abstract void doProcessCatalog(ETAS_Catalog completeCatalog,
			ETAS_Catalog triggeredOnlyCatalog, FaultSystemSolution fss);
	
	public ETAS_Config getConfig() {
		return config;
	}
	
	public ETAS_Launcher getLauncher() {
		return launcher;
	}
	
	/**
	 * If overridden to return true, doProcessCatalog will be called asynchronously
	 * @return
	 */
	protected boolean isProcessAsync() {
		return false;
	}
	
	/**
	 * Called to build all plots from data gathered from each catalog.
	 * @param outputDir
	 * @param fss
	 * @throws IOException
	 */
	public final void finalize(File outputDir, FaultSystemSolution fss) throws IOException {
		int threads = Integer.min(8, Runtime.getRuntime().availableProcessors());
		ExecutorService exec = Executors.newFixedThreadPool(threads);
		List<? extends Runnable> runnables = finalize(outputDir, fss, exec);
		if (runnables != null && !runnables.isEmpty()) {
			List<Future<?>> futures = new ArrayList<>();
			for (Runnable r : runnables)
				futures.add(exec.submit(r));
			for (Future<?> f : futures) {
				try {
					f.get();
				} catch (InterruptedException | ExecutionException e) {
					exec.shutdown();
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		}
		exec.shutdown();
	}
	
	/**
	 * Called to build all plots from data gathered from each catalog. Can return a list
	 * of Runnable instances to be executed in parallel before generateMarkdown is called.
	 * @param outputDir
	 * @param fss
	 * @param exec
	 * @return list of Runnable instances to be executed in parallel before generateMarkdown is called, or null
	 * @throws IOException
	 */
	public final List<? extends Runnable> finalize(File outputDir, FaultSystemSolution fss, ExecutorService exec)
			throws IOException {
		if (asyncManager != null)
			asyncManager.waitOnFutures();
		return doFinalize(outputDir, fss, exec);
	}
	
	protected abstract List<? extends Runnable> doFinalize(File outputDir, FaultSystemSolution fss, ExecutorService exec) throws IOException;
	
	public abstract List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink) throws IOException;
	
	public static HeadlessGraphPanel buildGraphPanel() {
		PlotPreferences plotPrefs = PlotPreferences.getDefault();
		plotPrefs.setTickLabelFontSize(20);
		plotPrefs.setAxisLabelFontSize(22);
		plotPrefs.setPlotLabelFontSize(24);
		plotPrefs.setLegendFontSize(20);
		plotPrefs.setBackgroundColor(Color.WHITE);
		return new HeadlessGraphPanel(plotPrefs);
	}
	
	protected static final DecimalFormat optionalDigitDF = new DecimalFormat("0.##");
	private static final DecimalFormat optionalSingleDigitDF = new DecimalFormat("0.#");
	
	protected static String getTimeLabel(double years, boolean plural) {
		double fractionalDays = years * 365.25;
		if (fractionalDays < 0.99) {
			double hours = fractionalDays * 24;
			double mins = hours*60d;
			double secs = mins*60d;
			if (hours > 0.99) {
				if (hours >= 1.05 && plural)
					return optionalSingleDigitDF.format(hours) + " Hours";
				return optionalSingleDigitDF.format(hours) + " Hour";
			} else if (mins > 0.99) {
				if (mins >= 1.05 && plural)
					return optionalSingleDigitDF.format(mins) + " Minutes";
				return optionalSingleDigitDF.format(mins) + " Minute";
			} else {
				if (secs >= 1.05 && plural)
					return optionalSingleDigitDF.format(secs) + " Seconds";
				return optionalSingleDigitDF.format(secs) + " Second";
			}
		} else if (years < 1d) {
			int days = (int) (fractionalDays + 0.5);
			
			double months = years *12d;
			boolean isRoundedMonth = Math.round(months) == Math.round(10d*months)/10d;
			
			if (days > 28 && isRoundedMonth) {
				if (months >= 1.05 && plural)
					return optionalSingleDigitDF.format(months)+" Months";
				else
					return optionalSingleDigitDF.format(months)+" Month";
			} else if (days >= 7 && days % 7 == 0) {
				int weeks = days / 7;
				if (weeks > 1 && plural)
					return weeks+" Weeks";
				else
					return weeks+" Week";
			} else {
				if (fractionalDays >= 1.05 && plural)
					return optionalSingleDigitDF.format(fractionalDays) + " Days";
				return optionalSingleDigitDF.format(fractionalDays) + " Day";
			}
		} else {
			if (plural && years >= 1.05)
				return optionalSingleDigitDF.format(years) + " Years";
			return optionalSingleDigitDF.format(years) + " Year";
		}
	}
	
	public static String getTimeShortLabel(double years) {
		String label = getTimeLabel(years, false);
		label = label.replaceAll("Seconds", "s");
		label = label.replaceAll("Second", "s");
		label = label.replaceAll("Minutes", "m");
		label = label.replaceAll("Minute", "m");
		label = label.replaceAll("Hours", "hr");
		label = label.replaceAll("Hour", "hr");
		label = label.replaceAll("Days", "d");
		label = label.replaceAll("Day", "d");
		label = label.replaceAll("Weeks", "wk");
		label = label.replaceAll("Week", "wk");
		label = label.replaceAll("Months", "mo");
		label = label.replaceAll("Month", "mo");
		label = label.replaceAll("Years", "yr");
		label = label.replaceAll("Year", "yr");
		return label;
	}
	
	protected static DecimalFormat normProbDF = new DecimalFormat("0.000");
	protected static DecimalFormat expProbDF = new DecimalFormat("0.00E0");
	protected static DecimalFormat percentProbDF = new DecimalFormat("0.00%");
	
	protected static String getProbStr(double prob) {
		return getProbStr(prob, false);
	}
	
	protected static String getProbStr(double prob, boolean includePercent) {
		return getProbStr(prob, includePercent, -1);
	}
	
	protected static String getProbStr(double prob, boolean includePercent, int numForConf) {
		String ret;
		if (prob < 0.01 && prob > 0)
			ret = expProbDF.format(prob);
		else
			ret = normProbDF.format(prob);
		if (includePercent)
			ret += " ("+percentProbDF.format(prob)+")";
		if (numForConf > 0)
			ret += ", <small>*CI<sup>95%</sup>="+getConfString(prob, numForConf, includePercent)+"*</small>";
		return ret;
	}
	
	protected static String getConfString(double prob, int num, boolean percent) {
		double[] conf = ETAS_Utils.getBinomialProportion95confidenceInterval(prob, num);
		if (percent)
			return "["+percentProbDF.format(conf[0])+" "+percentProbDF.format(conf[1])+"]";
		return "["+getProbStr(conf[0])+" "+getProbStr(conf[1])+"]";
	}
	
//	public static void main(String[] args) {
//		List<Double> times = new ArrayList<>(Doubles.asList(ETAS_HazardChangePlot.times));
//		double month = 1d/12d;
//		double day = 1/365.25;
//		times.add(day / 24);
//		times.add(1.5 * day / 24);
//		times.add(2 * day / 24);
//		times.add(day * 1);
//		times.add(day * 2);
//		times.add(day * 3);
//		times.add(day * 4);
//		times.add(day * 5);
//		times.add(day * 6);
//		times.add(day * 7);
//		times.add(day * 14);
//		times.add(day * 21);
//		times.add(day * 28);
//		times.add(day * 30);
//		times.add(day * 31);
//		times.add(day * 35);
//		times.add(day * 60);
//		times.add(day * 61);
//		times.add(day * 62);
//		times.add(day * 90);
//		times.add(day * 91);
//		times.add(day * 92);
//		times.add(1.1);
//		times.add(month*1);
//		times.add(month*1.04);
//		times.add(month*1.05);
//		times.add(month*1.06);
//		times.add(month*1.94);
//		times.add(month*1.95);
//		times.add(month*1.96);
//		times.add(month*2);
//		times.add(month*2.04);
//		times.add(month*2.05);
//		times.add(month*2.06);
//		times.add(day*0.9);
//		times.add(day*0.95);
//		times.add(day*1.04);
//		times.add(day*1.1);
//		times.add(day*1.5);
//		for (double time : times)
//			System.out.println((float)time+" =>\t"+getTimeLabel(time, true)+"\t"+getTimeLabel(time, false)+"\t"+getTimeShortLabel(time));
//	}
	
	private static final int GLOBAL_MAX_PRELOAD = 50;
	private static final double MAX_PRELOAD_YEARS = 1000;
	private class AsyncManager {
		
		private ExecutorService exec;
		private List<Future<?>> futures;
		
		public AsyncManager(ETAS_Config config) {
			int maxPreload = GLOBAL_MAX_PRELOAD;
			maxPreload = Integer.min(maxPreload, config.getNumSimulations()/100);
			maxPreload = Integer.min(maxPreload, (int)(MAX_PRELOAD_YEARS/config.getDuration()+0.5));
			maxPreload = Integer.max(2, maxPreload);
			
//			System.out.println("Max preload: "+maxPreload);
			
			exec = new ThreadPoolExecutor(1, 1,
					0L, TimeUnit.MILLISECONDS,
					new LimitedQueue<Runnable>(maxPreload));
			futures = new ArrayList<>(config.getNumSimulations());
		}
		
		public void processAsync(ETAS_Catalog completeCatalog,
				ETAS_Catalog triggeredOnlyCatalog, FaultSystemSolution fss) {
			futures.add(exec.submit(new ProcessRunnable(completeCatalog, triggeredOnlyCatalog, fss)));
		}
		
		public void waitOnFutures() {
			try {
				for (Future<?> future : futures)
					future.get();
			} catch (InterruptedException | ExecutionException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			exec.shutdown();
		}
	}
	
	private class ProcessRunnable implements Runnable {
		private final ETAS_Catalog completeCatalog;
		private final ETAS_Catalog triggeredOnlyCatalog;
		private final FaultSystemSolution fss;
		
		public ProcessRunnable(ETAS_Catalog completeCatalog, ETAS_Catalog triggeredOnlyCatalog,
				FaultSystemSolution fss) {
			this.completeCatalog = completeCatalog;
			this.triggeredOnlyCatalog = triggeredOnlyCatalog;
			this.fss = fss;
		}

		@Override
		public void run() {
			doProcessCatalog(completeCatalog, triggeredOnlyCatalog, fss);
		}
	}
	
	public static class LimitedQueue<E> extends LinkedBlockingQueue<E>  {
	    public LimitedQueue(int maxSize) {
	        super(maxSize);
	    }

	    @Override
	    public boolean offer(E e)  {
	        // turn offer() and add() into a blocking calls (unless interrupted)
	        try {
	            put(e);
	            return true;
	        } catch(InterruptedException ie) {
	            Thread.currentThread().interrupt();
	        }
	        return false;
	    }

	}

}

