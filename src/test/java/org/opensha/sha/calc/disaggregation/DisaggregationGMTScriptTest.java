package org.opensha.sha.calc.disaggregation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.opensha.commons.util.ServletWorkLimits;

public class DisaggregationGMTScriptTest {

	@Test
	public void generatesFiveTickLevelsForOrdinaryBins() {
		List<String> script = DisaggregationCalculator.createGMTScriptForDisaggregationPlot(validData(), ".");
		int start = script.indexOf("${COMMAND_PATH}cat << END > temp_segments") + 1;
		int end = script.indexOf("END");
		assertTrue(start > 0 && end > start);
		// Each tick level adds two three-line segments. The two vertical segments follow them.
		assertEquals(5 * 6 + 2 * 3, end - start);
	}

	@Test
	public void rejectsInvalidAxisMaximum() {
		for (double maximum : new double[] { Double.NaN, Double.POSITIVE_INFINITY, 0d }) {
			DisaggregationPlotData data = validData();
			data.setMaxContrEpsilonForGMT_Plot(maximum);
			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
					() -> DisaggregationCalculator.createGMTScriptForDisaggregationPlot(data, "."));
			assertTrue(error.getMessage().contains("Z axis maximum"));
		}
	}

	@Test
	public void rejectsInconsistentBinsAndContributions() {
		DisaggregationPlotData wrongEdges = validData();
		wrongEdges.setDist_binEdges(new double[] { 10 });
		assertThrows(IllegalArgumentException.class,
				() -> DisaggregationCalculator.createGMTScriptForDisaggregationPlot(wrongEdges, "."));

		DisaggregationPlotData wrongContributions = validData();
		wrongContributions.setPdf3D(new double[][][] { { { 60 } } });
		assertThrows(IllegalArgumentException.class,
				() -> DisaggregationCalculator.createGMTScriptForDisaggregationPlot(wrongContributions, "."));
	}

	@Test
	public void boundsTotalContributionBins() {
		int side = (int) Math.sqrt(ServletWorkLimits.DISAGG_BINS / 2d) + 1;
		double[] centers = new double[side];
		double[] edges = new double[side + 1];
		for (int i = 0; i < side; i++) {
			edges[i] = i;
			centers[i] = i + 0.5;
		}
		edges[side] = side;
		DisaggregationPlotData data = new DisaggregationPlotData(centers, edges, centers, edges,
				100, 2, new double[side][side][2]);
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> DisaggregationCalculator.createGMTScriptForDisaggregationPlot(data, "."));
		assertTrue(error.getMessage().contains("contribution bins"));
	}

	private static DisaggregationPlotData validData() {
		return new DisaggregationPlotData(new double[] { 6 }, new double[] { 5, 7 },
				new double[] { 20 }, new double[] { 10, 30 }, 100, 2, new double[][][] { { { 60, 40 } } });
	}
}
