package scratch.UCERF3.erf.ETAS.launcher.util;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotPreferences;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.FileNameComparator;
import org.opensha.commons.util.cpt.CPT;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

import edu.usc.kmilner.mpj.taskDispatch.MPJTaskLogStatsGen;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

class LogFileBenchmark {
	
	private static final double[] fractiles = { 0d, 0.025, 0.25, 0.5, 0.75, 0.975, 1d };
	
	private DiscretizedFunc meanFunc;
	private DiscretizedFunc timeToSolFunc;
	
	private DiscretizedFunc[] fractileFuncs;

	private String title;
	private String xValName;
	private String yValName;
	
	public LogFileBenchmark(String title, String xValName, String yValName) {
		this.title = title;
		this.xValName = xValName;
		this.yValName = yValName;
		meanFunc = new ArbitrarilyDiscretizedFunc();
		timeToSolFunc = new ArbitrarilyDiscretizedFunc();
		
		fractileFuncs = new DiscretizedFunc[fractiles.length];
		for (int f=0; f<fractiles.length; f++)
			fractileFuncs[f] = new ArbitrarilyDiscretizedFunc();
	}
	
	public void addLog(double xVal, File logFile, boolean first) throws IOException {
		if (logFile.isDirectory()) {
			File[] files = logFile.listFiles();
			Arrays.sort(files, new FileNameComparator());
			for (File file : files) {
				if (file.getName().contains(".slurm.o")) {
					logFile = file;
					if (first)
						break;
				}
			}
			Preconditions.checkState(!logFile.isDirectory(), "No slurm output files found in "+logFile.getAbsolutePath());
		}
		
		System.out.println("Processing "+logFile.getAbsolutePath());
		Date firstStart = null;
		Date lastEnd = null;
		Date latestDate = null;
		Map<Integer, Date> startDates = new HashMap<>();
		Map<Integer, Date> endDates = new HashMap<>();
		
		BufferedReader read = new BufferedReader(new FileReader(logFile), 81920);
		
		for (String line : new MPJTaskLogStatsGen.LogFileIterable(read)) {
			if (!line.startsWith("[") || !line.contains("]:"))
				continue;
			Date date = MPJTaskLogStatsGen.parseDate(line, latestDate);
			if (date == null)
				continue;
			latestDate = date;
			
			line = line.trim();
			
			if (line.contains("calculating") && !line.contains("batch")) {
				String indexStr = line.substring(line.indexOf("calculating")+"calculating".length()).trim();
				Integer index = Integer.parseInt(indexStr);
				if (startDates.containsKey(index))
					System.out.println("WARNING: duplicate start found for index "+index);
				startDates.put(index, date);
				if (firstStart == null)
					firstStart = date;
			}
			if (line.contains("completed") && !line.contains("binary")) {
				String indexStr = line.substring(line.indexOf("completed")+"completed".length()).trim();
				Integer index = Integer.parseInt(indexStr);
				if (endDates.containsKey(index))
					System.out.println("WARNING: duplicate end found for index "+index);
				endDates.put(index, date);
				lastEnd = date;
			}
		}
		
		System.out.println("Found "+startDates.size()+" start times");
		System.out.println("Found "+endDates.size()+" end times");
		
		List<Double> durations = new ArrayList<>();
		
		int numMissing = 0;
		
		for (Integer index : endDates.keySet()) {
			Date start = startDates.get(index);
			if (start == null) {
				System.out.println("Start date not found for "+index);
				numMissing++;
				continue;
			}
			Date end = endDates.get(index);
			long deltaMillis = end.getTime() - start.getTime();
			Preconditions.checkState(deltaMillis >= 0);
			double durationSecs = (double)deltaMillis/1000d;
			durations.add(durationSecs);
		}
		
		if (numMissing > 0)
			System.out.println(numMissing+" were missing");
		
		long wallDurationMillis = lastEnd.getTime() - firstStart.getTime();
		double wallDuration = (double)wallDurationMillis/1000d;
		
		System.out.println("\tTotal duration: "+ETAS_AbstractPlot.getTimeShortLabel(wallDuration*seconds_to_years));
		
		double[] durationsArray = Doubles.toArray(durations);
		double mean = StatUtils.mean(durationsArray);
		double max = StatUtils.max(durationsArray);
		double min = StatUtils.min(durationsArray);
		double sum = StatUtils.sum(durationsArray);
		System.out.println("\tMean sim duration: "+ETAS_AbstractPlot.getTimeShortLabel(mean*seconds_to_years));
		System.out.println("\tMin sim duration: "+ETAS_AbstractPlot.getTimeShortLabel(min*seconds_to_years));
		System.out.println("\tMax sim duration: "+ETAS_AbstractPlot.getTimeShortLabel(max*seconds_to_years));
		System.out.println("\tTotal sim duration: "+ETAS_AbstractPlot.getTimeShortLabel(sum*seconds_to_years));
		
		timeToSolFunc.set(xVal, wallDuration/(60d*60d)); // hours
		meanFunc.set(xVal, mean/(60d)); // minutes
		
		for (int f=0; f<fractiles.length; f++) {
			double val;
			if (fractiles[f] == 0d)
				val = min;
			else if (fractiles[f] == 1d)
				val = max;
			else
				val = StatUtils.percentile(durationsArray, fractiles[f]*100d);
			System.out.println("\t\tFractile "+fractiles[f]+" => "+ETAS_AbstractPlot.getTimeShortLabel(val*seconds_to_years));
			fractileFuncs[f].set(xVal, val/(60d)); // minutes
		}
	}
	
	public void plot() throws IOException {
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		funcs.add(timeToSolFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, xValName, yValName);
		
		PlotPreferences plotPrefs = PlotPreferences.getDefault();
		plotPrefs.setTickLabelFontSize(18);
		plotPrefs.setAxisLabelFontSize(20);
		plotPrefs.setPlotLabelFontSize(21);
		plotPrefs.setBackgroundColor(Color.WHITE);

		HeadlessGraphPanel gp = new HeadlessGraphPanel(plotPrefs);
		
		gp.drawGraphPanel(spec);
		gp.getChartPanel().setSize(800, 600);
		gp.saveAsPNG("/tmp/scaling.png");
		gp.saveAsPDF("/tmp/scaling.pdf");
		
		GraphWindow gw = new GraphWindow(spec);
		gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
		
		funcs = new ArrayList<>();
		chars = new ArrayList<>();
		funcs.add(meanFunc);
		meanFunc.setName("Mean");
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
		
		CPT fractileCPT = new CPT(0d, 1d, Color.LIGHT_GRAY, Color.DARK_GRAY, Color.LIGHT_GRAY);
		
		for (int f=0; f<fractiles.length; f++) {
			double percentile = fractiles[f]*100d;
			fractileFuncs[f].setName((float)percentile+" %-ile");
			funcs.add(fractileFuncs[f]);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, fractileCPT.getColor((float)fractiles[f])));
		}
		
		spec = new PlotSpec(funcs, chars, "Time Per Sim", xValName, "Time (minutes)");
		spec.setLegendVisible(true);
		
		gw = new GraphWindow(spec);
		gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
	}
	
	private static double seconds_to_years = 1000d/ProbabilityModelsCalc.MILLISEC_PER_YEAR;
	
	public static void main(String[] args) throws IOException {
		File outputDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations");
		String prefix = "2018_08_31-Spontaneous-includeSpont-historicalCatalog-10yr-";
//		String prefix = "2018_08_31-MojaveM7-noSpont-10yr-";
//		String prefix = "2019_04_12-Spontaneous-includeSpont-historicalCatalog-1yr-5nodes_";

		int[] threads = { 1, 2, 3, 4, 5, 6, 8, 10, 12, 14, 16 };
//		int[] threads = { 5, 10, 15, 20, 25, 30, 35, 40, 45, 50 };
		boolean first = false;
		
		LogFileBenchmark benchmark = new LogFileBenchmark("OpenSHA UCERF3-ETAS Stampede2 SKX Scaling",
				"# Threads per SKX Node", "Time To Solution (hours)");
		
		for (int thread : threads) {
			File dir = new File(outputDir, prefix+thread+"threads");
			try {
				benchmark.addLog(thread, dir, first);
			} catch (Exception e) {
				System.out.println("Skipping "+dir.getName());
				e.printStackTrace();
			}
		}
		
		benchmark.plot();
		
//		LogFileBenchmark benchmark = new LogFileBenchmark("Code Version");
//		
//		benchmark.addLog(0d, new File("/tmp/etas_thread_test/output_regular.txt"));
//		benchmark.addLog(1d, new File("/tmp/etas_thread_test/output_nosync.txt"));
//		
//		benchmark.plot();
	}

}
