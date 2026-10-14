package org.opensha.commons.gui.plot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.awt.Color;
import java.util.Arrays;

import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.plot.XYPlot;
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
		EqualityCountingDataSet first = buildDataSet(1d);
		EqualityCountingDataSet second = buildDataSet(2d);

		PlotCurveCharacterstics firstChars =
				new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED);
		PlotCurveCharacterstics secondChars =
				new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLUE);

		PlotSpec spec = new PlotSpec(Arrays.asList(first, second),
				Arrays.asList(firstChars, secondChars), "Test", "X", "Y");
		spec.setLegendVisible(true);

		EqualityCountingDataSet.equalsCalls = 0;
		GraphPanel panel = new GraphPanel(PlotPreferences.getDefaultAppPrefs());
		panel.drawGraphPanel(spec, false, false);

		assertEquals("Building the dataset index must not compare function contents",
				0, EqualityCountingDataSet.equalsCalls);

		XYPlot plot = panel.getPlot();
		assertEquals(2, plot.getDatasetCount());
		for (int i = 0; i < plot.getDatasetCount(); i++) {
			XYDataset dataset = plot.getDataset(i);
			assertSame(dataset, plot.getDataset(plot.indexOf(dataset)));
			assertEquals(i, plot.indexOf(dataset));
		}
		assertEquals("Dataset index lookups must not compare function contents",
				0, EqualityCountingDataSet.equalsCalls);

		LegendItemCollection legend = plot.getLegendItems();
		assertEquals(2, legend.getItemCount());
		assertEquals(Color.RED, legend.get(0).getLinePaint());
		assertEquals(Color.BLUE, legend.get(1).getLinePaint());
		assertEquals("Legend lookup must not compare function contents",
				0, EqualityCountingDataSet.equalsCalls);
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
