package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

/**
 * Cumulative plot of when the scenario (primary) aftershock occurred across
 * every matching catalog, measured as time after the simulation start. The
 * horizontal axis spans {@code [0, config.getDuration()]} expressed in months;
 * each bar's height is the running total of matching catalogs whose primary
 * aftershock occurred by that time (a cumulative distribution).
 *
 * <p>Times are referenced to {@link ETAS_Config#getSimulationStartTimeMillis()}
 * so the plot and its summary match the &Delta;t column of the Catalog Matches
 * table exactly.</p>
 *
 * <p>The accompanying markdown summary reports cumulative occurrence counts
 * within several fixed time thresholds (1 hour, 1 day, 1 week, 1 month, 1
 * year). Any threshold that exceeds the simulation duration is omitted, since
 * no rupture can occur beyond the duration and the count would simply equal
 * the total.</p>
 */
public class ETAS_PrimaryAftershockTimingPlot extends ETAS_AbstractPlot {

	private static final String PREFIX = "primary_aftershock_timing";
	private static final int NUM_BINS = 50;
	private static final double MONTHS_PER_YEAR = 12;

	private static final double HOURS_PER_YEAR = 24 * 365.25;
	private static final double DAYS_PER_YEAR = 365.25;

	private final int fssIndex;
	private final double targetMag;

	/** {@code (origin − configStart) / msPerYear} of the primary aftershock in each matching catalog. */
	private final List<Double> timesYears = new ArrayList<>();

	/**
	 * @param config    ETAS config supplying the simulation start time and duration
	 * @param launcher  ETAS launcher
	 * @param fssIndex  FSS index of the primary aftershock
	 * @param targetMag magnitude of the primary aftershock (used in the plot title)
	 */
	public ETAS_PrimaryAftershockTimingPlot(ETAS_Config config, ETAS_Launcher launcher,
			int fssIndex, double targetMag) {
		super(config, launcher);
		this.fssIndex = fssIndex;
		this.targetMag = targetMag;
	}

	@Override
	public int getVersion() {
		return 2;
	}

	@Override
	public boolean isFilterSpontaneous() {
		return false;
	}

	@Override
	protected void doProcessCatalog(ETAS_Catalog completeCatalog,
			ETAS_Catalog triggeredOnlyCatalog, FaultSystemSolution fss) {
		// Reference the config simulation start (the same reference used by the
		// Catalog Matches table) so the plot and summary agree with that table.
		long simStartMs = getConfig().getSimulationStartTimeMillis();
		for (ETAS_EqkRupture rup : completeCatalog) {
			if (rup.getFSSIndex() == fssIndex) {
				timesYears.add((rup.getOriginTime() - simStartMs) / ProbabilityModelsCalc.MILLISEC_PER_YEAR);
				return; // exactly one primary aftershock per matching catalog
			}
		}
	}

	@Override
	protected List<? extends Runnable> doFinalize(File outputDir,
			FaultSystemSolution fss, ExecutorService exec) throws IOException {
		double durationYears = getConfig().getDuration();
		double durationMonths = durationYears * MONTHS_PER_YEAR;
		double deltaMonths = durationMonths / NUM_BINS;

		// Bin occurrence times (in months), then accumulate each bin into a running
		// total so bar height = cumulative count of catalogs through that bin.
		int[] hist = new int[NUM_BINS];
		for (double t : timesYears) {
			double tMonths = t * MONTHS_PER_YEAR;
			hist[binIndex(tMonths, deltaMonths)]++;
		}
		// Bars are centered within each bin so they stay inside [0, durationMonths].
		EvenlyDiscretizedFunc cum = new EvenlyDiscretizedFunc(
				0.5 * deltaMonths, durationMonths - 0.5 * deltaMonths, NUM_BINS);
		int running = 0;
		for (int i = 0; i < NUM_BINS; i++) {
			running += hist[i];
			cum.set(i, running);
		}
		cum.setName("Matching catalogs");

		double yMax = running;
		if (yMax <= 0d)
			yMax = 1d; // avoid a degenerate axis when nothing occurred

		List<EvenlyDiscretizedFunc> funcs = new ArrayList<>();
		funcs.add(cum);
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1.0f, new Color(21, 81, 182)));

		PlotSpec spec = new PlotSpec(funcs, chars,
				"Cumulative Primary Aftershock Occurrence\n(FSS=" + fssIndex + ", M" + targetMag + ")",
				"Time After Simulation Start (months)", "Cumulative Number of Catalogs");
		spec.setLegendVisible(false);

		HeadlessGraphPanel gp = ETAS_AbstractPlot.buildGraphPanel();
		gp.setUserBounds(0d, durationMonths, 0d, yMax);
		// Both axes linear; y is a count and the zero lower bound must be kept.
		gp.drawGraphPanel(spec, false, false);
		gp.getChartPanel().setSize(1000, 500);

		String base = new File(outputDir, PREFIX).getAbsolutePath();
		gp.saveAsPNG(base + ".png");
		gp.saveAsPDF(base + ".pdf");
		return null;
	}

	/**
	 * Maps an occurrence time (in months after the simulation start) to its
	 * time-bin index, clamping to {@code [0, NUM_BINS - 1]} so a time at the
	 * duration boundary lands in the final bin.
	 */
	private static int binIndex(double tMonths, double deltaMonths) {
		int b = (int) Math.floor(tMonths / deltaMonths);
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
		lines.add(topLevelHeading + " Primary Aftershock Timing");
		lines.add(topLink);
		lines.add("");

		int total = timesYears.size();
		lines.add("Cumulative count of matching catalogs that had produced the primary aftershock "
				+ "by each time after the simulation start, across the " + total + " matching catalog"
				+ (total == 1 ? "" : "s") + ". The final bar reaches " + total + " (every matching catalog "
				+ "contains the primary aftershock within the simulation duration).");
		lines.add("");

		String imgPath = relativePathToOutputDir.isEmpty() ? PREFIX : relativePathToOutputDir + "/" + PREFIX;
		lines.add("![Primary Aftershock Timing](" + imgPath + ".png)");
		lines.add("");

		// Cumulative occurrence counts within fixed thresholds. Thresholds that
		// exceed the simulation duration are omitted (no rupture can occur past
		// the duration, so the count would just equal the total).
		double durationYears = getConfig().getDuration();
		lines.add("Number of matching catalogs where the primary aftershock occurred within:");
		for (Threshold thr : cumulativeThresholds()) {
			if (thr.years > durationYears)
				continue;
			int count = 0;
			for (double t : timesYears)
				if (t <= thr.years)
					count++;
			double pct = total > 0 ? 100d * count / total : 0d;
			lines.add(String.format("- Under %s: %d (%.2f%%)", thr.label, count, pct));
		}
		lines.add("");
		return lines;
	}

	/** Fixed cumulative time thresholds, from shortest to longest. */
	private static List<Threshold> cumulativeThresholds() {
		List<Threshold> list = new ArrayList<>();
		list.add(new Threshold("1 hour", 1d / HOURS_PER_YEAR));
		list.add(new Threshold("1 day", 1d / DAYS_PER_YEAR));
		list.add(new Threshold("1 week", 7d / DAYS_PER_YEAR));
		list.add(new Threshold("1 month", 1d / MONTHS_PER_YEAR));
		list.add(new Threshold("1 year", 1d));
		return list;
	}

	/** A cumulative time threshold: a human-readable label and its length in years. */
	private static class Threshold {
		final String label;
		final double years;

		Threshold(String label, double years) {
			this.label = label;
			this.years = years;
		}
	}
}