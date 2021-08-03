package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;

public class RateVsRateScatter extends AbstractSolutionPlot {

	@Override
	public String getName() {
		return "Rupture Rate Comparison";
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		if (meta.comparison == null || meta.comparison.sol == null || !meta.primary.rupSet.isEquivalentTo(meta.comparison.rupSet))
			return null;
		List<String> lines = new ArrayList<>();
		
		DefaultXY_DataSet scatter = new DefaultXY_DataSet();
		int numRups = meta.primary.numRuptures;
		
		MinMaxAveTracker rateTrack = new MinMaxAveTracker();
		for (int r=0; r<numRups; r++) {
			double x = Math.max(1e-16, meta.primary.sol.getRateForRup(r));
			double y = Math.max(1e-16, meta.comparison.sol.getRateForRup(r));
			scatter.set(x, y);
			rateTrack.addValue(x);
			rateTrack.addValue(y);
		}
		Range range = new Range(Math.max(1e-16, Math.pow(10, Math.floor(Math.log10(rateTrack.getMin())))),
				Math.pow(10, Math.ceil(Math.log10(rateTrack.getMax()))));
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		DefaultXY_DataSet oneToOne = new DefaultXY_DataSet();
		oneToOne.set(range.getLowerBound(), range.getLowerBound());
		oneToOne.set(range.getUpperBound(), range.getUpperBound());
		
		funcs.add(scatter);
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK));
		
		funcs.add(oneToOne);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.GRAY));
		
		PlotSpec spec = new PlotSpec(funcs, chars, "Rupture Rate Comparison",
				getTruncatedTitle(meta.primary.name)+" Rate", getTruncatedTitle(meta.comparison.name)+" Rate");
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		gp.drawGraphPanel(spec, true, true, range, range);
		
		String prefix = "rate_comparison";
		PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, false, true, false, false);
		
		lines.add("![rate comparison]("+relPathToResources+"/"+prefix+".png)");
		return lines;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return null;
	}

}
