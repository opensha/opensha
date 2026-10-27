package org.opensha.sha.calc.disaggregation.chart3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.jfree.chart3d.Chart3D;
import org.jfree.chart3d.plot.CategoryPlot3D;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.sha.calc.disaggregation.DisaggregationCalculator.EpsilonCategories;
import org.opensha.sha.calc.disaggregation.DisaggregationPlotData;

/**
 * Tests {@link PureJavaDisaggPlotter} on synthetic data, headless: the value (Z) axis range, including honoring a requested
 * maximum, and writing PNG/PDF files.
 */
public class PureJavaDisaggPlotterTest {

	/** Tallest bin's total contribution; the automatic Z axis maximum should be this rounded up to 40. */
	private static final double TALLEST_BIN_TOTAL = 37d;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@BeforeClass
	public static void forceHeadless() {
		System.setProperty("java.awt.headless", "true");
	}

	/**
	 * Builds a 3 distance x 2 magnitude x 8 epsilon grid whose tallest bin (distance 0, magnitude 1) totals
	 * {@link #TALLEST_BIN_TOTAL} percent, with the contribution split evenly across the epsilon bins.
	 */
	private static DisaggregationPlotData buildData(double requestedMaxZ) {
		int numE = EpsilonCategories.values().length;
		double[][][] pdf3D = new double[3][2][numE];
		for (int k=0; k<numE; k++) {
			pdf3D[0][1][k] = TALLEST_BIN_TOTAL/numE;
			pdf3D[1][0][k] = 10d/numE;
			pdf3D[2][1][k] = 5d/numE;
		}
		return new DisaggregationPlotData(
				new double[] {5.25, 5.75}, new double[] {5d, 5.5, 6d},
				new double[] {5d, 15d, 25d}, new double[] {0d, 10d, 20d, 30d},
				requestedMaxZ, numE, pdf3D);
	}

	private static double zAxisMax(DisaggregationPlotData data) {
		Chart3D chart = PureJavaDisaggPlotter.buildChart(data);
		CategoryPlot3D plot = (CategoryPlot3D)chart.getPlot();
		return plot.getValueAxis().getRange().getMax();
	}

	/** With no explicit request the axis is automatically ranged to just fit the tallest bin. */
	@Test
	public void testAutomaticZAxis() {
		double auto = zAxisMax(buildData(0d));
		assertTrue("auto axis max "+auto+" should fit the data", auto >= TALLEST_BIN_TOTAL && auto <= 40d);
		// the calculator's "automatic" value is the data maximum itself, which must not change the result
		assertEquals(auto, zAxisMax(buildData(TALLEST_BIN_TOTAL)), 1e-10);
		assertEquals(auto, zAxisMax(buildData(Double.NaN)), 1e-10);
	}

	/** A larger requested maximum (DisaggregationCalculator.setMaxZAxisForPlot) is honored. */
	@Test
	public void testRequestedLargerZAxisIsHonored() {
		assertEquals(60d, zAxisMax(buildData(60d)), 1e-10);
	}

	/** A request just above the data (37), but below the data rounded up to the next 10 (40), is honored. */
	@Test
	public void testRequestedBetweenDataAndRoundedIsHonored() {
		assertEquals(38.5, zAxisMax(buildData(38.5)), 1e-10);
	}

	/** A requested maximum below the data is ignored rather than clipping bars. */
	@Test
	public void testRequestedSmallerZAxisIsIgnored() {
		assertEquals(zAxisMax(buildData(0d)), zAxisMax(buildData(20d)), 1e-10);
	}

	/** PNG and PDF output are written and non-empty. */
	@Test
	public void testWriteChartPlot() throws IOException {
		File dir = tmp.newFolder();
		PureJavaDisaggPlotter.writeChartPlot(dir, "disagg", buildData(0d), 400, 400, true, true);
		File png = new File(dir, "disagg.png");
		File pdf = new File(dir, "disagg.pdf");
		assertTrue("PNG missing or empty", png.isFile() && png.length() > 0);
		assertTrue("PDF missing or empty", pdf.isFile() && pdf.length() > 0);
	}
}
