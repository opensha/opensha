package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

public class JumpCountsOverDistancePlot extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "Jump Counts Over Distance";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		float[] maxJumpDists = { 0.1f, 1f, 3f };
		
		double rsMinMag = rupSet.getMinMag();
		double rsMaxMag = rupSet.getMaxMag();
		
		List<Double> minMags = new ArrayList<>();
		minMags.add(0d);
		if (rsMinMag < 7d && rsMaxMag > 7d)
			minMags.add(7d);
		if (rsMinMag < 8d && rsMinMag > 7d && rsMaxMag > 8d)
			minMags.add(8d);
		
		FaultSystemRupSet compRupSet = null;
		FaultSystemSolution compSol = null;
		String compName = null;
		if (meta.comparison != null && meta.comparison.rupSet.hasModule(ClusterRuptures.class)) {
			compRupSet = meta.comparison.rupSet;
			compSol = meta.comparison.sol;
			compName = meta.comparison.name;
		}
		
		boolean hasSols = sol != null || compSol != null;
		
		List<String> lines = new ArrayList<>();
		
		for (double minMag : minMags) {
			TableBuilder table = MarkdownUtils.tableBuilder();
			if (hasSols)
				table.addLine("As Discretized", "Rate Weighted");
			
			if (minMag > 0d)
				lines.add(getSubHeading()+" M&ge;"+optionalDigitDF.format(minMag)+" Jump Counts");
			else
				lines.add(getSubHeading()+" Supra-seismogenic Jump Counts");
			lines.add(topLink); lines.add("");
			
			List<ClusterRupture> inputRups = rupSet.getModule(ClusterRuptures.class).getAll();
			List<ClusterRupture> compRups = compRupSet == null ? null : compRupSet.getModule(ClusterRuptures.class).getAll();
			for (float jumpDist : maxJumpDists) {
				System.out.println("Plotting num jumps");
				table.initNewLine();
				File plotFile = plotFixedJumpDist(rupSet, null, inputRups, getTruncatedTitle(meta.primary.name),
						compRupSet, null, compRups, getTruncatedTitle(compName), minMag, jumpDist, resourcesDir);
				table.addColumn("![Plausibility Filter]("+resourcesDir.getName()+"/"+plotFile.getName()+")");
				if (hasSols) {
					plotFile = plotFixedJumpDist(
							sol == null ? null : rupSet, sol, inputRups, getTruncatedTitle(meta.primary.name),
							compSol == null ? null : compRupSet, compSol, compRups, getTruncatedTitle(compName),
							minMag, jumpDist, resourcesDir);
					table.addColumn("![Plausibility Filter]("+resourcesDir.getName()+"/"+plotFile.getName()+")");
				}
				table.finalizeLine();
			}
			lines.addAll(table.build());
			lines.add("");
		}
		
		// now do cumulative plot
		if (sol != null) {
			lines.add(getSubHeading()+" Max Jump Distance Distribution");
			lines.add(topLink); lines.add("");
			
			File plot = plotJumpDistRates(rupSet, sol, meta.primary.name, compRupSet, compSol, compName, resourcesDir, false);
			
			lines.add("![Max Jump Dist Plot]("+relPathToResources+"/"+plot.getName()+")");
			
			lines.add(getSubHeading()+" Cumulative Jump Distance Distribution");
			lines.add(topLink); lines.add("");
			
			plot = plotJumpDistRates(rupSet, sol, meta.primary.name, compRupSet, compSol, compName, resourcesDir, true);
			
			lines.add("![Cumulative Jump Dist Plot]("+relPathToResources+"/"+plot.getName()+")");
		}
		
		return lines;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return List.of(ClusterRuptures.class, SectionDistanceAzimuthCalculator.class);
	}
	
	public static File plotFixedJumpDist(FaultSystemRupSet inputRupSet, FaultSystemSolution inputSol,
			List<ClusterRupture> inputClusterRups, String inputName, FaultSystemRupSet compRupSet,
			FaultSystemSolution compSol, List<ClusterRupture> compClusterRups, String compName,
			double minMag, float jumpDist, File outputDir) throws IOException {
		
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();

		if (inputRupSet != null) {
			DiscretizedFunc func = calcJumpDistFunc(inputRupSet, inputSol, inputClusterRups, minMag, jumpDist);
			func.scale(1d/func.calcSumOfY_Vals());
			funcs.add(func);
			
			func.setName(inputName);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, MAIN_COLOR));
		}
		
		if (compRupSet != null) {
			DiscretizedFunc compFunc = calcJumpDistFunc(compRupSet, compSol, compClusterRups, minMag, jumpDist);
			compFunc.scale(1d/compFunc.calcSumOfY_Vals());
			compFunc.setName(compName);
			funcs.add(compFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, COMP_COLOR));
		}
		
		String title;
		String xAxisLabel = "Num Jumps ≥"+(float)jumpDist+" km";
		String yAxisLabel;
		String prefixAdd;
		if (minMag > 0d) {
			title = "M≥"+(float)minMag+" "+(float)jumpDist+" km Jump Comparison";
			prefixAdd = "_m"+optionalDigitDF.format(minMag);
		} else {
			title = (float)jumpDist+" km Jump Comparison";
			prefixAdd = "";
		}
		Range yRange = null;
		if (inputSol != null || compSol != null) {
			yAxisLabel = "Fraction (Rate-Weighted)";
			yRange = new Range(0d, 1d);
			prefixAdd += "_rates";
		} else {
			yAxisLabel = "Count";
			prefixAdd += "_counts";
		}
		PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
//				"Num Jumps ≥"+(float)jumpDist+"km", "Fraction (Rate-Weighted)");
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		
		String prefix = new File(outputDir, "jumps_"+(float)jumpDist+"km"+prefixAdd).getAbsolutePath();
		
		gp.drawGraphPanel(spec, false, false, null, yRange);
		TickUnits tus = new TickUnits();
		TickUnit tu = new NumberTickUnit(1d);
		tus.add(tu);
		gp.getXAxis().setStandardTickUnits(tus);
		gp.getChartPanel().setSize(1000, 500);
		gp.saveAsPNG(prefix+".png");
		gp.saveAsPDF(prefix+".pdf");
		gp.saveAsTXT(prefix+".txt");
		return new File(prefix+".png");
	}
	
	private static DiscretizedFunc calcJumpDistFunc(FaultSystemRupSet rupSet, FaultSystemSolution sol,
			List<ClusterRupture> clusterRups, double minMag, float jumpDist) {
		EvenlyDiscretizedFunc solFunc = new EvenlyDiscretizedFunc(0d, 5, 1d);

		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			double mag = rupSet.getMagForRup(r);

			if (mag < minMag)
				continue;
			
			ClusterRupture rup = clusterRups.get(r);
			int jumpsOverDist = 0;
			for (Jump jump : rup.getJumpsIterable()) {
				if ((float)jump.distance > jumpDist)
					jumpsOverDist++;
			}

			double rate = sol == null ? 1d : sol.getRateForRup(r);
			
			// indexes are fine to use here since it starts at zero with a delta of one 
			if (jumpsOverDist < solFunc.size())
				solFunc.set(jumpsOverDist, solFunc.getY(jumpsOverDist) + rate);
		}
		
		return solFunc;
	}
	
	public static File plotJumpDistRates(FaultSystemRupSet inputRupSet, FaultSystemSolution inputSol,
			String inputName, FaultSystemRupSet compRupSet, FaultSystemSolution compSol, String compName,
			File outputDir, boolean cumulative) throws IOException {
		
		double maxDist = 0d;
		List<Double> inputDists = new ArrayList<>();
		for (ClusterRupture rup : inputRupSet.requireModule(ClusterRuptures.class).getAll()) {
			double dist = 0d;
			for (Jump jump : rup.getJumpsIterable()) {
				if (cumulative)
					dist += jump.distance;
				else
					dist = Math.max(dist, jump.distance);
			}
			maxDist = Math.max(maxDist, dist);
			inputDists.add(dist);
		}
		
		List<Double> compDists = null;
		if (compSol != null) {
			compDists = new ArrayList<>();
			for (ClusterRupture rup : compRupSet.requireModule(ClusterRuptures.class).getAll()) {
				double dist = 0d;
				for (Jump jump : rup.getJumpsIterable()) {
					if (cumulative)
						dist += jump.distance;
					else
						dist = Math.max(dist, jump.distance);
				}
				maxDist = Math.max(maxDist, dist);
				compDists.add(dist);
			}
		}
		
		EvenlyDiscretizedFunc refFunc = HistogramFunction.getEncompassingHistogram(0.01, Math.max(4.9d, maxDist), 1d);
		
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		DiscretizedFunc inputRateFunc = rupDistRateFunc(refFunc, inputDists, inputSol);
		funcs.add(inputRateFunc);
		
		inputRateFunc.setName(inputName);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, MAIN_COLOR));
		
		DiscretizedFunc inputDiscrFunc = rupDistRateFunc(refFunc, inputDists, null);
		inputDiscrFunc.scale(inputRateFunc.calcSumOfY_Vals()/inputDiscrFunc.calcSumOfY_Vals());
		inputDiscrFunc.setName("(as discretized)");
		
		funcs.add(inputDiscrFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, MAIN_COLOR));
		
		if (compSol != null) {
			DiscretizedFunc func = rupDistRateFunc(refFunc, compDists, compSol);
			funcs.add(func);
			
			func.setName(compName);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, COMP_COLOR));
		}
		
		double maxY = 0d;
		double minY = Double.POSITIVE_INFINITY;
		for (DiscretizedFunc func : funcs) {
			for (Point2D pt : func) {
				if (pt.getY() > 0) {
					maxY = Math.max(maxY, pt.getY());
					minY = Math.min(minY, pt.getY());
				}
			}
		}
		maxY = Math.pow(10, Math.ceil(Math.log10(maxY)));
		minY = Math.pow(10, Math.floor(Math.log10(minY)));
		if (maxY <= minY)
			maxY = minY + 10;
		
		String title = " ";
		String xAxisLabel, prefix;
		if (cumulative) {
			xAxisLabel = "Rupture Cumulative Jump Distance (km)";
			prefix = "jump_rate_cumulative";
		} else {
			xAxisLabel = "Rupture Maximum Jump Distance (km)";
			prefix = "jump_rate_max";
		}
		String yAxisLabel = "Incremental Rate (1/yr)";
		Range yRange = new Range(minY, maxY);
		PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
//				"Num Jumps ≥"+(float)jumpDist+"km", "Fraction (Rate-Weighted)");
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		
		prefix = new File(outputDir, prefix).getAbsolutePath();
		
		gp.drawGraphPanel(spec, false, true, null, yRange);
		gp.getChartPanel().setSize(1000, 850);
		gp.saveAsPNG(prefix+".png");
		gp.saveAsPDF(prefix+".pdf");
		gp.saveAsTXT(prefix+".txt");
		return new File(prefix+".png");
	}
	
	private static EvenlyDiscretizedFunc rupDistRateFunc(EvenlyDiscretizedFunc refVals, List<Double> dists, FaultSystemSolution sol) {
		EvenlyDiscretizedFunc ret = new EvenlyDiscretizedFunc(refVals.getMinX(), refVals.size(), refVals.getDelta());
		
		for (int r=0; r<dists.size(); r++) {
			int ind = ret.getClosestXIndex(dists.get(r));
			if (sol == null)
				ret.add(ind, 1d);
			else
				ret.add(ind, sol.getRateForRup(r));
		}
		
		return ret;
	}

}
