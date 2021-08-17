package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.EvenlyDiscrXYZ_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZGraphPanel;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;

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
		
		int numRups = meta.primary.numRuptures;
		FaultSystemRupSet rupSet = sol.getRupSet();
		double minMag = rupSet.getMinMag();
		double maxMag = rupSet.getMaxMag();
		List<Range> magRanges = new ArrayList<>();
		magRanges.add(null);
		int minBinSize = (int)(rupSet.getNumRuptures()*0.001);
		double binFloor = 0;
		double binCeil = Math.ceil(minMag);
//		System.out.println("Min bin size: "+minBinSize);
		while (binFloor < maxMag) {
			Range range = new Range(binFloor, binCeil);
			int numInRange = numInRange(range, rupSet.getMagForAllRups());
//			System.out.println("Potential range ["+(int)range.getLowerBound()+","+(int)range.getUpperBound()+"] has "+numInRange+" rups");
			if (binFloor == 0d) {
				// see if we should skip this
				if (numInRange < minBinSize) {
					binCeil += 1d;
//					System.out.println("\tskipping (too small)");
					continue;
				}
			} else if (binCeil > maxMag) {
				// see if we should combine this with the previous one
				if (numInRange < minBinSize && magRanges.size() > 1) {
//					System.out.println("\tskipping/ending (too small)");
					Range prev = magRanges.remove(magRanges.size()-1);
					magRanges.add(new Range(prev.getLowerBound(), binCeil));
					break;
				}
			}
			
			magRanges.add(range);
			
			binFloor = binCeil;
			binCeil += 1d;
		}
		
		List<XY_DataSet> scatters = new ArrayList<>();
		for (int i=0; i<magRanges.size(); i++)
			scatters.add(new DefaultXY_DataSet());
		
		MinMaxAveTracker rateTrack = new MinMaxAveTracker();
		for (int r=0; r<numRups; r++) {
			double x = Math.max(1e-16, meta.primary.sol.getRateForRup(r));
			double y = Math.max(1e-16, meta.comparison.sol.getRateForRup(r));
			for (int m=0; m<magRanges.size(); m++) {
				Range range = magRanges.get(m);
				if (range == null || range.contains(rupSet.getMagForRup(r)))
					scatters.get(m).set(x, y);
			}
			rateTrack.addValue(x);
			rateTrack.addValue(y);
		}
		Range range = new Range(Math.max(1e-16, Math.pow(10, Math.floor(Math.log10(rateTrack.getMin())))),
				Math.pow(10, Math.ceil(Math.log10(rateTrack.getMax()))));
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		for (int m=0; m<magRanges.size(); m++) {
			Range magRange = magRanges.get(m);
			
			String title;
			String prefix;
			if (magRange == null) {
				title = "Rupture Rate Comparison";
				prefix = "rate_comparison";
			} else if (magRange.getLowerBound() == 0d) {
				title = "M≤"+(int)magRange.getUpperBound()+" Rupture Rate Comparison";
				prefix = "rate_comparison_m_lt_"+(int)magRange.getUpperBound();
			} else if (magRange.getUpperBound() >= maxMag) {
				title = "M≥"+(int)magRange.getLowerBound()+" Rupture Rate Comparison";
				prefix = "rate_comparison_m_ge_"+(int)magRange.getLowerBound();
			} else {
				title = "M"+(int)magRange.getLowerBound()+"-"+(int)magRange.getUpperBound()+" Rupture Rate Comparison";
				prefix = "rate_comparison_m_"+(int)magRange.getLowerBound()+"_"+(int)magRange.getUpperBound();
			}
			
			List<XY_DataSet> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			DefaultXY_DataSet oneToOne = new DefaultXY_DataSet();
			oneToOne.set(range.getLowerBound(), range.getLowerBound());
			oneToOne.set(range.getUpperBound(), range.getUpperBound());

			XY_DataSet scatter = scatters.get(m);
			funcs.add(scatter);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK));
			
			funcs.add(oneToOne);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.GRAY));
			
			PlotSpec spec = new PlotSpec(funcs, chars, title,
					getTruncatedTitle(meta.primary.name)+" Rate", getTruncatedTitle(meta.comparison.name)+" Rate");
			HeadlessGraphPanel gp = PlotUtils.initHeadless();
			
			gp.drawGraphPanel(spec, true, true, range, range);
			
			PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, false, true, false, false);
			
			double logMin = Math.log10(range.getLowerBound());
			double logMax = Math.log10(range.getUpperBound());
			oneToOne = new DefaultXY_DataSet();
			oneToOne.set(logMin, logMin);
			oneToOne.set(logMax, logMax);
			funcs = new ArrayList<>();
			chars = new ArrayList<>();
			funcs.add(oneToOne);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.GRAY));
			HistogramFunction refFunc = HistogramFunction.getEncompassingHistogram(logMin+0.01, logMax-0.01, 0.2);
			EvenlyDiscrXYZ_DataSet xyz = new EvenlyDiscrXYZ_DataSet(refFunc.size(), refFunc.size(),
					refFunc.getMinX(), refFunc.getMinX(), refFunc.getDelta());
			
			for (Point2D pt : scatter) {
				double logX = Math.log10(pt.getX());
				double logY = Math.log10(pt.getY());
				int xInd = refFunc.getClosestXIndex(logX);
				int yInd = refFunc.getClosestXIndex(logY);
				xyz.set(xInd, yInd, xyz.get(xInd, yInd)+1);
			}
			
			// set all zero to NaN so that it will plot clear
			for (int i=0; i<xyz.size(); i++) {
				if (xyz.get(i) == 0)
					xyz.set(i, Double.NaN);
			}
			
			xyz.log10();
			double maxZ = xyz.getMaxZ();
			CPT cpt = GMT_CPT_Files.BLACK_RED_YELLOW_UNIFORM.instance().reverse().rescale(0d, Math.ceil(maxZ));
			cpt.setNanColor(new Color(255, 255, 255, 0));
			cpt.setBelowMinColor(cpt.getNaNColor());
			
			XYZPlotSpec xyzSpec = new XYZPlotSpec(xyz, cpt, title, "Log10 "+spec.getXAxisLabel(),
					"Log10 "+spec.getYAxisLabel(), "Log10 Count");
			xyzSpec.setCPTPosition(RectangleEdge.BOTTOM);
			xyzSpec.setXYElems(funcs);
			xyzSpec.setXYChars(chars);
			XYZGraphPanel xyzGP = new XYZGraphPanel(gp.getPlotPrefs());
			Range logRange = new Range(refFunc.getMinX()-0.5*refFunc.getDelta(), refFunc.getMaxX()+0.5*refFunc.getDelta());
			xyzGP.drawPlot(xyzSpec, false, false, logRange, logRange);
			
			xyzGP.getChartPanel().setSize(1000, 1000);
			xyzGP.saveAsPNG(new File(resourcesDir, prefix+"_hist2D.png").getAbsolutePath());
			
			table.initNewLine();
			table.addColumn("![rate comparison]("+relPathToResources+"/"+prefix+".png)");
			table.addColumn("![rate hist]("+relPathToResources+"/"+prefix+"_hist2D.png)");
			table.finalizeLine();
		}
		
		return table.build();
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return null;
	}
	
	private int numInRange(Range range, double[] mags) {
		int count = 0;
		for (double mag : mags)
			if (range.contains(mag))
				count++;
		return count;
	}
	
	public static void main(String[] args) throws IOException {
		File mainDir = new File("/home/kevin/markdown/inversions");
		FaultSystemSolution sol1 = FaultSystemSolution.load(new File(mainDir,
				"2021_08_05-coulomb-u3_ref-perturb_exp_scale_1e-2_to_1e-10-avg_anneal_20m-wlAsInitial-5hr-run0/solution.zip"));
		FaultSystemSolution sol2 = FaultSystemSolution.load(new File(mainDir,
				"2021_08_05-coulomb-u3_ref-perturb_exp_scale_1e-2_to_1e-10-avg_anneal_20m-wlAsInitial-5hr-run1/solution.zip"));
		RateVsRateScatter plot = new RateVsRateScatter();
		ReportMetadata meta = new ReportMetadata(new RupSetMetadata("sol1", sol1), new RupSetMetadata("sol2", sol2));
		plot.plot(sol1, meta, new File("/tmp"), "resources", "");
	}

}
