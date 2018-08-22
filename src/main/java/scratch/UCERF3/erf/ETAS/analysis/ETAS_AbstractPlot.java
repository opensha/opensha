package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotPreferences;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_SimAnalysisTools;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

public abstract class ETAS_AbstractPlot {
	
	private ETAS_Config config;
	private ETAS_Launcher launcher;

	protected ETAS_AbstractPlot(ETAS_Config config, ETAS_Launcher launcher) {
		this.config = config;
		this.launcher = launcher;
	}
	
	public abstract boolean isFilterSpontaneous();
	
	public void processCatalog(List<ETAS_EqkRupture> catalog, FaultSystemSolution fss) {
		List<ETAS_EqkRupture> triggeredOnlyCatalog = null;
		if (isFilterSpontaneous())
			triggeredOnlyCatalog = ETAS_Launcher.getFilteredNoSpontaneous(config, catalog);
		doProcessCatalog(catalog, triggeredOnlyCatalog, fss);
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
	
	protected static HeadlessGraphPanel buildGraphPanel() {
		PlotPreferences plotPrefs = PlotPreferences.getDefault();
		plotPrefs.setTickLabelFontSize(20);
		plotPrefs.setAxisLabelFontSize(22);
		plotPrefs.setPlotLabelFontSize(24);
		plotPrefs.setLegendFontSize(20);
		plotPrefs.setBackgroundColor(Color.WHITE);
		return new HeadlessGraphPanel(plotPrefs);
	}
	
	protected static final DecimalFormat optionalDigitDF = new DecimalFormat("0.##");
	
	protected static String getTimeLabel(double years, boolean plural) {
		if (years < 1d) {
			int days = (int) (years * 365.25 + 0.5);
			if (days == 30) {
				return "1 Month";
			} else if (days == 7) {
				return "1 Week";
			} else if (days == 14) {
				if (plural)
					return "2 Weeks";
				return "2 Week";
			} else if (years < 1d / 365.25) {
				int hours = (int) (years * 365.25 * 24 + 0.5);
				if (hours > 1 && plural)
					return hours + " Hours";
				return hours + " Hour";
			} else {
				if (days > 1 && plural)
					return days + " Days";
				return days + " Day";
			}
		} else {
			if (plural && (int)years > 1)
				return (int) years + " Years";
			return (int) years + " Year";
		}
	}
	
	public static String getTimeShortLabel(double years) {
		String label = getTimeLabel(years, false);
		label = label.replaceAll("Days", "d");
		label = label.replaceAll("Day", "d");
		label = label.replaceAll("Months", "mo");
		label = label.replaceAll("Month", "mo");
		label = label.replaceAll("Weeks", "wk");
		label = label.replaceAll("Week", "wk");
		label = label.replaceAll("Hours", "hr");
		label = label.replaceAll("Hour", "hr");
		label = label.replaceAll("Years", "yr");
		label = label.replaceAll("Year", "yr");
		return label;
	}
	
	private static DecimalFormat normProbDF = new DecimalFormat("0.000");
	private static DecimalFormat expProbDF = new DecimalFormat("0.00E0");
	
	protected static String getProbStr(double prob) {
		if (prob < 0.01)
			return expProbDF.format(prob);
		return normProbDF.format(prob);
	}

}
