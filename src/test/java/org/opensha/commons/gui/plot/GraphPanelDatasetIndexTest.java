package org.opensha.commons.gui.plot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;
import org.jfree.data.xy.XYDataset;
import org.junit.Test;
import org.opensha.commons.data.function.DefaultXY_DataSet;

public class GraphPanelDatasetIndexTest {

	private static class EqualityCountingDataSet extends DefaultXY_DataSet {

		private static final long serialVersionUID = 1L;

		private static int equalsCalls;

		@Override
		public boolean equals(Object obj) {
			equalsCalls++;
			return super.equals(obj);
		}
	}

	@Test
	public void testDatasetIndexesUseIdentity() {
		PlotSpec spec = buildSpec("Test", Color.RED, Color.BLUE, 1d);

		EqualityCountingDataSet.equalsCalls = 0;
		GraphPanel panel = new GraphPanel(PlotPreferences.getDefaultAppPrefs());
		panel.drawGraphPanel(spec, false, false);

		assertEquals("Building the dataset index must not compare function contents",
				0, EqualityCountingDataSet.equalsCalls);

		assertIndexesAndLegend(panel.getPlot(), Color.RED, Color.BLUE);
		assertEquals("Dataset index lookups must not compare function contents",
				0, EqualityCountingDataSet.equalsCalls);
	}

	@Test
	public void testCombinedDomainSubplotsUseLocalIndexes() {
		testCombinedSubplots(false);
	}

	@Test
	public void testCombinedRangeSubplotsUseLocalIndexes() {
		testCombinedSubplots(true);
	}

	private static void testCombinedSubplots(boolean combinedRange) {
		PlotSpec firstSpec = buildSpec("First", Color.RED, Color.BLUE, 1d);
		PlotSpec secondSpec = buildSpec("Second", Color.GREEN, Color.MAGENTA, 3d);

		EqualityCountingDataSet.equalsCalls = 0;
		GraphPanel panel = new GraphPanel(PlotPreferences.getDefaultAppPrefs());
		List<Range> yRanges = combinedRange ? Collections.singletonList(new Range(-1d, 6d)) : null;
		panel.drawGraphPanel(Arrays.asList(firstSpec, secondSpec), false, false, null, yRanges);

		if (combinedRange)
			assertTrue(panel.getPlot() instanceof CombinedRangeXYPlot);
		else
			assertTrue(panel.getPlot() instanceof CombinedDomainXYPlot);

		List<XYPlot> subPlots = PlotUtils.getSubPlots(panel);
		assertEquals(2, subPlots.size());
		assertIndexesAndLegend(subPlots.get(0), Color.RED, Color.BLUE);
		assertIndexesAndLegend(subPlots.get(1), Color.GREEN, Color.MAGENTA);
		assertEquals("Combined subplot indexing must not compare function contents",
				0, EqualityCountingDataSet.equalsCalls);
	}

	private static void assertIndexesAndLegend(XYPlot plot, Color firstColor, Color secondColor) {
		assertEquals(2, plot.getDatasetCount());
		for (int i = 0; i < plot.getDatasetCount(); i++) {
			XYDataset dataset = plot.getDataset(i);
			for (int repeat = 0; repeat < 10; repeat++) {
				assertSame(dataset, plot.getDataset(plot.indexOf(dataset)));
				assertEquals(i, plot.indexOf(dataset));
			}
		}

		LegendItemCollection legend = plot.getLegendItems();
		assertEquals(2, legend.getItemCount());
		assertEquals(firstColor, legend.get(0).getLinePaint());
		assertEquals(secondColor, legend.get(1).getLinePaint());
		assertEquals("Legend lookup must not compare function contents",
				0, EqualityCountingDataSet.equalsCalls);

		XYDataset firstDataset = plot.getDataset(0);
		plot.setDataset(0, null);
		assertEquals("Removed datasets must also be removed from the index", -1, plot.indexOf(firstDataset));
		plot.setDataset(0, firstDataset);
		assertEquals("Re-added datasets must be restored to the index", 0, plot.indexOf(firstDataset));
	}

	private static PlotSpec buildSpec(String title, Color firstColor, Color secondColor, double yOffset) {
		EqualityCountingDataSet first = buildDataSet(yOffset + 1d);
		EqualityCountingDataSet second = buildDataSet(yOffset + 2d);

		PlotCurveCharacterstics firstChars =
				new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, firstColor);
		PlotCurveCharacterstics secondChars =
				new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, secondColor);

		PlotSpec spec = new PlotSpec(Arrays.asList(first, second), Arrays.asList(firstChars, secondChars),
				title, "X", "Y");
		spec.setLegendVisible(true);
		return spec;
	}

	private static EqualityCountingDataSet buildDataSet(double finalY) {
		EqualityCountingDataSet dataset = new EqualityCountingDataSet();
		dataset.setName("same-name");
		dataset.setInfo("same-info");
		dataset.set(0d, 0d);
		dataset.set(1d, 1d);
		dataset.set(2d, finalY);
		return dataset;
	}
}
