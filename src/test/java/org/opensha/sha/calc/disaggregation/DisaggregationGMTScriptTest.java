package org.opensha.sha.calc.disaggregation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

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

	private static DisaggregationPlotData validData() {
		return new DisaggregationPlotData(new double[] { 6 }, new double[] { 5, 7 },
				new double[] { 20 }, new double[] { 10, 30 }, 100, 2, new double[][][] { { { 60, 40 } } });
	}
}
