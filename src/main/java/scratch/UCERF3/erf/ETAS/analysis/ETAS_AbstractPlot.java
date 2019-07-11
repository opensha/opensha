package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotPreferences;

import com.google.common.base.Stopwatch;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_SimAnalysisTools;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

public abstract class ETAS_AbstractPlot {
	
	private ETAS_Config config;
	private ETAS_Launcher launcher;
	
	private Stopwatch processStopwatch;

	protected ETAS_AbstractPlot(ETAS_Config config, ETAS_Launcher launcher) {
		this.config = config;
		this.launcher = launcher;
		processStopwatch = Stopwatch.createUnstarted();
	}
	
	public abstract boolean isFilterSpontaneous();
	
	public void processCatalog(List<ETAS_EqkRupture> catalog, FaultSystemSolution fss) {
		List<ETAS_EqkRupture> triggeredOnlyCatalog = null;
		if (isFilterSpontaneous())
			triggeredOnlyCatalog = ETAS_Launcher.getFilteredNoSpontaneous(config, catalog);
		processCatalog(catalog, triggeredOnlyCatalog, fss);
	}
	
	public void processCatalog(List<ETAS_EqkRupture> completeCatalog,
			List<ETAS_EqkRupture> triggeredOnlyCatalog, FaultSystemSolution fss) {
		processStopwatch.start();
		doProcessCatalog(completeCatalog, triggeredOnlyCatalog, fss);
		processStopwatch.stop();
	}
	
	public long getProcessTimeMS() {
		return processStopwatch.elapsed(TimeUnit.MILLISECONDS);
	}
	
	protected abstract void doProcessCatalog(List<ETAS_EqkRupture> completeCatalog,
			List<ETAS_EqkRupture> triggeredOnlyCatalog, FaultSystemSolution fss);
	
	public ETAS_Config getConfig() {
		return config;
	}
	
	public ETAS_Launcher getLauncher() {
		return launcher;
	}
	
	public abstract void finalize(File outputDir, FaultSystemSolution fss) throws IOException;
	
	public abstract List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink) throws IOException;
	
	static HeadlessGraphPanel buildGraphPanel() {
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
	
	private static DecimalFormat normProbDF = new DecimalFormat("0.000");
	private static DecimalFormat expProbDF = new DecimalFormat("0.00E0");
	
	protected static String getProbStr(double prob) {
		if (prob < 0.01 && prob > 0)
			return expProbDF.format(prob);
		return normProbDF.format(prob);
	}
	
	public static void main(String[] args) {
		List<Double> times = new ArrayList<>(Doubles.asList(ETAS_HazardChangePlot.times));
		double month = 1d/12d;
		double day = 1/365.25;
		times.add(day / 24);
		times.add(1.5 * day / 24);
		times.add(2 * day / 24);
		times.add(day * 1);
		times.add(day * 2);
		times.add(day * 3);
		times.add(day * 4);
		times.add(day * 5);
		times.add(day * 6);
		times.add(day * 7);
		times.add(day * 14);
		times.add(day * 21);
		times.add(day * 28);
		times.add(day * 30);
		times.add(day * 31);
		times.add(day * 35);
		times.add(day * 60);
		times.add(day * 61);
		times.add(day * 62);
		times.add(day * 90);
		times.add(day * 91);
		times.add(day * 92);
		times.add(1.1);
		times.add(month*1);
		times.add(month*1.04);
		times.add(month*1.05);
		times.add(month*1.06);
		times.add(month*1.94);
		times.add(month*1.95);
		times.add(month*1.96);
		times.add(month*2);
		times.add(month*2.04);
		times.add(month*2.05);
		times.add(month*2.06);
		times.add(day*0.9);
		times.add(day*0.95);
		times.add(day*1.04);
		times.add(day*1.1);
		times.add(day*1.5);
		for (double time : times)
			System.out.println((float)time+" =>\t"+getTimeLabel(time, true)+"\t"+getTimeLabel(time, false)+"\t"+getTimeShortLabel(time));
	}

}

