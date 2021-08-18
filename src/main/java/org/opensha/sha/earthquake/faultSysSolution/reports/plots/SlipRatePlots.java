package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;

public class SlipRatePlots extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "Slip Rates";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		RupSetMapMaker mapMaker = new RupSetMapMaker(rupSet, meta.region);
		mapMaker.setWriteGeoJSON(true);
		
		CPT slipCPT = new CPT();
		
		slipCPT.setBelowMinColor(Color.GRAY);
		slipCPT.setNanColor(Color.GRAY);
		
//		slipCPT.add(new CPTVal(0f, Color.GRAY, 0f, Color.GRAY));
		slipCPT.add(new CPTVal(Float.MIN_VALUE, Color.BLUE, 10f, Color.MAGENTA));
		slipCPT.add(new CPTVal(10f, Color.MAGENTA, 20f, Color.RED));
		slipCPT.add(new CPTVal(20f, Color.RED, 30f, Color.ORANGE));
		slipCPT.add(new CPTVal(30f, Color.ORANGE, 40f, Color.YELLOW));
		
		slipCPT.setAboveMaxColor(Color.YELLOW);
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		String prefix = "slip_rates";
		
		table.initNewLine();
		double[] nonReduced = nonReduced(rupSet);
		double maxSlip = StatUtils.max(nonReduced);
		if (maxSlip < 10d)
			slipCPT = slipCPT.rescale(0d, 10d);
		else if (maxSlip < 20d)
			slipCPT = slipCPT.rescale(0d, 20d);
		else if (maxSlip > 60d)
			slipCPT = slipCPT.rescale(0d, 60d);
		double[] origReduced = origReduced(rupSet);
		mapMaker.plotSectScalars(nonReduced, slipCPT, "Original (non-reduced) Slip Rate (mm/yr)");
		mapMaker.plot(resourcesDir, prefix+"_orig", " ");
		table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_orig.png)");
		mapMaker.plotSectScalars(origReduced, slipCPT, "Reduced Slip Rate (mm/yr)");
		mapMaker.plot(resourcesDir, prefix+"_reduced", " ");
		table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_reduced.png)");
		table.finalizeLine();
		table.initNewLine();
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_orig.geojson")
				+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+"_orig.geojson)");
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_reduced.geojson")
				+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+"_reduced.geojson)");
		table.finalizeLine();
		
		if (rupSet.hasModule(SectSlipRates.class) || sol != null) {
			table.initNewLine();
			SectSlipRates slipRates = rupSet.getModule(SectSlipRates.class);
			double[] targets = null;
			if (slipRates != null) {
				targets = target(rupSet, slipRates);
				mapMaker.plotSectScalars(targets, slipCPT, "Target Slip Rate (mm/yr)");
				mapMaker.plot(resourcesDir, prefix+"_target", " ");
				table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_target.png)");
			} else {
				table.addColumn("_(no target slip rates found)_");
			}
			
			double[] solRates = null;
			if (sol != null) {
				solRates = solution(sol);
				mapMaker.plotSectScalars(solRates, slipCPT, "Solution Slip Rate (mm/yr)");
				mapMaker.plot(resourcesDir, prefix+"_sol", " ");
				table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_sol.png)");
			} else {
				table.addColumn("");
			}
			table.finalizeLine();
			table.initNewLine();
			if (targets == null)
				table.addColumn("");
			else
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_target.geojson")
						+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+"_target.geojson)");
			if (sol == null)
				table.addColumn("");
			else
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_sol.geojson")
						+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+"_sol.geojson)");
			table.finalizeLine();
			
			if (targets != null && solRates != null) {
				CPT diffCPT = new CPT(-10, 10,
						new Color(0, 0, 140), new Color(0, 60, 200 ), new Color(0, 120, 255),
						Color.WHITE,
						new Color(255, 120, 0), new Color(200, 60, 0), new Color(140, 0, 0));
				diffCPT.setNanColor(Color.GRAY);
				diffCPT.setBelowMinColor(diffCPT.getMinColor());
				diffCPT.setAboveMaxColor(diffCPT.getMaxColor());
				
				CPT belowCPT = new CPT(0.5d, 1d,
						new Color(0, 0, 140), new Color(0, 60, 200 ), new Color(0, 120, 255),
						Color.WHITE);
				CPT aboveCPT = new CPT(1d, 2d,
						Color.WHITE,
						new Color(255, 120, 0), new Color(200, 60, 0), new Color(140, 0, 0));
				CPT ratioCPT = new CPT();
				ratioCPT.addAll(belowCPT);
				ratioCPT.addAll(aboveCPT);
				ratioCPT.setNanColor(Color.GRAY);
				ratioCPT.setBelowMinColor(ratioCPT.getMinColor());
				ratioCPT.setAboveMaxColor(ratioCPT.getMaxColor());
				
				table.initNewLine();
				
				double[] diffs = diff(solRates, targets);
				double[] ratios = ratio(solRates, targets);
				
				mapMaker.plotSectScalars(diffs, diffCPT, "Solution - Target Slip Rate (mm/yr)");
				mapMaker.plot(resourcesDir, prefix+"_sol_diff", " ");
				table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_sol_diff.png)");
				mapMaker.plotSectScalars(ratios, ratioCPT, "Solution / Target Slip Rate");
				mapMaker.plot(resourcesDir, prefix+"_sol_ratio", " ");
				table.addColumn("![Map]("+relPathToResources+"/"+prefix+"_sol_ratio.png)");
				table.finalizeLine();
				table.initNewLine();
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_sol_diff.geojson")
						+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+"_sol_diff.geojson)");
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_sol_ratio.geojson")
						+" "+"[Download GeoJSON]("+relPathToResources+"/"+prefix+"_sol_ratio.geojson)");
				table.finalizeLine();
				
				// now scatters/histograms
				table.initNewLine();
				
				HistogramFunction diffHist = HistogramFunction.getEncompassingHistogram(-10d, 10d, 1d);
				for (double diff : diffs)
					diffHist.add(diffHist.getClosestXIndex(diff), 1d);
				
				List<XY_DataSet> funcs = new ArrayList<>();
				List<PlotCurveCharacterstics> chars = new ArrayList<>();
				
				funcs.add(diffHist);
				chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));
				
				HeadlessGraphPanel gp = PlotUtils.initHeadless();
				
				gp.drawGraphPanel(new PlotSpec(funcs, chars, "Slip Rate Differences", "Solution - Target Slip Rate (mm/yr)", "Count"));
				
				PlotUtils.writePlots(resourcesDir, prefix+"_sol_diff_hist", gp, 800, 650, true, true, false);
				table.addColumn("![Diff hist]("+relPathToResources+"/"+prefix+"_sol_diff_hist.png)");
				
				DefaultXY_DataSet scatter = new DefaultXY_DataSet(targets, solRates);
				Range scatterRange = new Range(0d, Math.max(scatter.getMaxX(), scatter.getMaxY()));
				
				funcs = new ArrayList<>();
				chars = new ArrayList<>();
				
				DefaultXY_DataSet oneToOne = new DefaultXY_DataSet();
				oneToOne.set(scatterRange.getLowerBound(), scatterRange.getLowerBound());
				oneToOne.set(scatterRange.getUpperBound(), scatterRange.getUpperBound());
				funcs.add(oneToOne);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.GRAY));
				
				funcs.add(scatter);
				chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 4f, Color.BLACK));

				gp.drawGraphPanel(new PlotSpec(funcs, chars,
						"Slip Rates Scatter", "Target Slip Rate (mm/yr)", "Solution Slip Rate (mm/yr)"),
						false, false, scatterRange, scatterRange);
				
				PlotUtils.writePlots(resourcesDir, prefix+"_sol_scatter", gp, 800, 650, true, true, false);
				table.addColumn("![Diff hist]("+relPathToResources+"/"+prefix+"_sol_scatter.png)");
				table.finalizeLine();
			}
		}
		
		return table.build();
	}
	
	private static double[] nonReduced(FaultSystemRupSet rupSet) {
		double[] slips = new double[rupSet.getNumSections()];
		for (int s=0; s<slips.length; s++)
			slips[s] = rupSet.getFaultSectionData(s).getOrigAveSlipRate();
		return slips;
	}
	
	private static double[] origReduced(FaultSystemRupSet rupSet) {
		double[] slips = new double[rupSet.getNumSections()];
		for (int s=0; s<slips.length; s++)
			slips[s] = rupSet.getFaultSectionData(s).getReducedAveSlipRate();
		return slips;
	}
	
	private static double[] target(FaultSystemRupSet rupSet, SectSlipRates slipRates) {
		double[] slips = new double[rupSet.getNumSections()];
		for (int s=0; s<slips.length; s++)
			slips[s] = slipRates.getSlipRate(s)*1e3;
		return slips;
	}
	
	private static double[] solution(FaultSystemSolution sol) {
		FaultSystemRupSet rupSet = sol.getRupSet();
		AveSlipModule aveSlips = rupSet.requireModule(AveSlipModule.class);
		SlipAlongRuptureModel slipAlongs = rupSet.requireModule(SlipAlongRuptureModel.class);
		double[] slips = slipAlongs.calcSlipRateForSects(sol, aveSlips);
		slips = Arrays.copyOf(slips, slips.length);
		for (int s=0; s<slips.length; s++)
			slips[s] *= 1e3; // to mm/yr
		return slips;
	}
	
	private static double[] diff(double[] v1, double[] v2) {
		double[] diff = new double[v1.length];
		for (int i=0; i<diff.length; i++)
			diff[i] = v1[i] - v2[i];
		return diff;
	}
	
	private static double[] ratio(double[] v1, double[] v2) {
		double[] ratio = new double[v1.length];
		for (int i=0; i<ratio.length; i++)
			ratio[i] = v1[i] / v2[i];
		return ratio;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return List.of(SlipAlongRuptureModel.class, AveSlipModule.class);
	}

}
