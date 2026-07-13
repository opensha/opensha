package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StackedXYBarRenderer;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotPreferences;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.jfreechart.DiscretizedFunctionXYDataSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

/**
 * Stacked bar chart of the cumulative number of rupture occurrences over time,
 * aggregated across every matching catalog and measured as time after the
 * simulation start in years.
 *
 * <p>The horizontal axis spans {@code [0, config.getDuration()]} and is binned
 * uniformly. Each bar's height is the running total of ruptures that occurred
 * from time 0 through that bin: other M &ge; 2.5 ruptures (gray, bottom) stacked
 * beneath the scenario rupture (red, top). The scenario rupture is identified by
 * its FSS index.</p>
 *
 * <p>Times are referenced to {@link scratch.UCERF3.erf.ETAS.launcher.ETAS_Config#getSimulationStartTimeMillis()}
 * rather than the catalog metadata's simulation start (which records wall-clock
 * execution time), so the timeline matches the &Delta;t column of the Catalog
 * Matches table.</p>
 */
public class ETAS_RuptureOccurrenceTimeline extends ETAS_AbstractPlot {

	/** Smallest magnitude to consider; ruptures with M &lt; this are ignored. */
	private static final double MIN_MAG = 2.5;
	private static final String PREFIX = "rupture_occurrence_timeline";

	/** Number of time bins across the simulation duration. */
	private static final int NUM_BINS = 100;

	private final int fssIndex;
	private final double targetMag;

	/** {@code (origin − simStart) / msPerYear} for every M &ge; MIN_MAG rupture outside the scenario FSS index. */
	private final List<Double> otherTimes = new ArrayList<>();
	/** Same, but for ruptures whose FSS index matches the scenario. */
	private final List<Double> targetTimes = new ArrayList<>();

	/**
	 * @param config    ETAS config supplying the simulation duration
	 * @param launcher  ETAS launcher
	 * @param fssIndex  FSS index of the scenario rupture to highlight
	 * @param targetMag magnitude of the scenario rupture (used in the plot title)
	 */
	public ETAS_RuptureOccurrenceTimeline(ETAS_Config config, ETAS_Launcher launcher,
			int fssIndex, double targetMag) {
		super(config, launcher);
		this.fssIndex = fssIndex;
		this.targetMag = targetMag;
	}

	@Override
	public int getVersion() {
		return 3;
	}

	@Override
	public boolean isFilterSpontaneous() {
		return false;
	}

	@Override
	protected void doProcessCatalog(ETAS_Catalog completeCatalog,
			ETAS_Catalog triggeredOnlyCatalog, FaultSystemSolution fss) {
		// Reference the config simulation start (the same reference used by the
		// Catalog Matches table) so the timeline agrees with that table. The
		// catalog metadata's simulationStartTime is wall-clock execution time,
		// not the configured forecast start.
		long simStartMs = getConfig().getSimulationStartTimeMillis();
		for (ETAS_EqkRupture rup : completeCatalog) {
			double yrsAfterStart = (rup.getOriginTime() - simStartMs) / ProbabilityModelsCalc.MILLISEC_PER_YEAR;
			if (rup.getFSSIndex() == fssIndex)
				targetTimes.add(yrsAfterStart);
			else if (rup.getMag() >= MIN_MAG)
				otherTimes.add(yrsAfterStart);
		}
	}

	@Override
	protected List<? extends Runnable> doFinalize(File outputDir,
			FaultSystemSolution fss, ExecutorService exec) throws IOException {
		double duration = getConfig().getDuration();
		double delta = duration / NUM_BINS;

		// Bin occurrence times, then accumulate each bin into a running total so
		// bar height = cumulative count of ruptures through that bin.
		int[] otherHist = new int[NUM_BINS];
		int[] targetHist = new int[NUM_BINS];
		for (double t : otherTimes)
			otherHist[binIndex(t, delta)]++;
		for (double t : targetTimes)
			targetHist[binIndex(t, delta)]++;

		// Bars are centered within each bin so they stay inside [0, duration].
		EvenlyDiscretizedFunc otherCum = new EvenlyDiscretizedFunc(0.5 * delta, duration - 0.5 * delta, NUM_BINS);
		otherCum.setName("Other ruptures (M≥" + MIN_MAG + ")");
		EvenlyDiscretizedFunc targetCum = new EvenlyDiscretizedFunc(0.5 * delta, duration - 0.5 * delta, NUM_BINS);
		targetCum.setName("Scenario rupture (FSS=" + fssIndex + ")");
		int otherRunning = 0;
		int targetRunning = 0;
		for (int i = 0; i < NUM_BINS; i++) {
			otherRunning += otherHist[i];
			targetRunning += targetHist[i];
			otherCum.set(i, otherRunning);
			targetCum.set(i, targetRunning);
		}

		// Build the stacked bar chart directly: the standard PlotSpec path bundles
		// curves that share plot characteristics, which forces both series to a
		// single color. To stack with distinct colors we wrap both functions in one
		// TableXYDataset and attach a StackedXYBarRenderer with per-series paints.
		XY_DataSetList funcs = new XY_DataSetList();
		funcs.add(otherCum);
		funcs.add(targetCum);
		DiscretizedFunctionXYDataSet dataset = new DiscretizedFunctionXYDataSet();
		dataset.setFunctions(funcs);

		StackedXYBarRenderer renderer = new StackedXYBarRenderer();
		renderer.setShadowVisible(false);
		renderer.setBarPainter(new StandardXYBarPainter());
		renderer.setDrawBarOutline(false);
		renderer.setSeriesPaint(0, Color.GRAY);
		renderer.setSeriesPaint(1, new Color(200, 30, 30));

		HeadlessGraphPanel gp = ETAS_AbstractPlot.buildGraphPanel();
		PlotPreferences prefs = gp.getPlotPrefs();
		Font base = new Font(Font.DIALOG, Font.PLAIN, 1);

		NumberAxis xAxis = new NumberAxis("Time After Simulation Start (yr)");
		xAxis.setLabelFont(base.deriveFont((float) prefs.getAxisLabelFontSize()));
		xAxis.setTickLabelFont(base.deriveFont((float) prefs.getTickLabelFontSize()));
		xAxis.setRange(0d, duration);

		double yMax = (double) otherRunning + targetRunning;
		if (yMax <= 0d)
			yMax = 1d; // avoid a degenerate axis when nothing occurred
		NumberAxis yAxis = new NumberAxis("Cumulative Rupture Occurrences");
		yAxis.setLabelFont(base.deriveFont((float) prefs.getAxisLabelFontSize()));
		yAxis.setTickLabelFont(base.deriveFont((float) prefs.getTickLabelFontSize()));
		yAxis.setRange(0d, yMax);

		XYPlot plot = new XYPlot(dataset, xAxis, yAxis, renderer);
		plot.setBackgroundPaint(Color.WHITE);
		plot.setDomainGridlinePaint(new Color(225, 225, 225));
		plot.setRangeGridlinePaint(new Color(225, 225, 225));

		JFreeChart chart = new JFreeChart(
				"Cumulative Rupture Occurrence (FSS=" + fssIndex + ", M" + targetMag + ")",
				base.deriveFont(Font.BOLD, (float) prefs.getPlotLabelFontSize()), plot, true);
		chart.setBackgroundPaint(Color.WHITE);
		if (chart.getLegend() != null)
			chart.getLegend().setItemFont(base.deriveFont((float) prefs.getLegendFontSize()));

		// drawGraphPanel initializes the panel's ChartPanel (needed by saveAsPNG/PDF);
		// the chart it builds is replaced with the stacked chart above.
		List<XY_DataSet> initFuncs = new ArrayList<>();
		initFuncs.add(otherCum);
		initFuncs.add(targetCum);
		List<PlotCurveCharacterstics> initChars = new ArrayList<>();
		initChars.add(new PlotCurveCharacterstics(PlotLineType.STACKED_BAR, 1.0f, Color.GRAY));
		initChars.add(new PlotCurveCharacterstics(PlotLineType.STACKED_BAR, 1.0f, new Color(200, 30, 30)));
		PlotSpec initSpec = new PlotSpec(initFuncs, initChars, chart.getTitle().getText(),
				xAxis.getLabel(), yAxis.getLabel());
		initSpec.setLegendVisible(true);
		gp.setUserBounds(0d, duration, 0d, yMax);
		gp.drawGraphPanel(initSpec, false, false);

		gp.getChartPanel().setChart(chart);
		gp.getChartPanel().setSize(1200, 400);

		String baseName = new File(outputDir, PREFIX).getAbsolutePath();
		gp.saveAsPNG(baseName + ".png");
		gp.saveAsPDF(baseName + ".pdf");
		return null;
	}

	/**
	 * Maps a rupture time (years after the simulation start) to its time-bin index,
	 * clamping to {@code [0, NUM_BINS - 1]} so a time exactly at the duration
	 * boundary lands in the final bin.
	 */
	private static int binIndex(double t, double delta) {
		int b = (int) Math.floor(t / delta);
		if (b < 0)
			b = 0;
		else if (b >= NUM_BINS)
			b = NUM_BINS - 1;
		return b;
	}

	@Override
	public List<String> generateMarkdown(String relativePathToOutputDir,
			String topLevelHeading, String topLink) throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add(topLevelHeading);
		lines.add(topLink);
		lines.add("");
		lines.add("Cumulative rupture occurrences over time across all catalogs, binned by time "
				+ "after the simulation start. Each bar's height is the running total of ruptures through "
				+ "that bin: other M≥2.5 ruptures (gray) stacked beneath the scenario rupture (red).");
		lines.add("");
		String imgPath = relativePathToOutputDir.isEmpty() ? PREFIX : relativePathToOutputDir + "/" + PREFIX;
		lines.add("[![Rupture Occurrence Timeline](" + imgPath + ".png)](" + imgPath + ".png)");
		lines.add("");
		return lines;
	}
}