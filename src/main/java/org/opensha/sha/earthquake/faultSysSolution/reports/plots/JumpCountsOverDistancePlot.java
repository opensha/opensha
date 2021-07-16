package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
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
		
		FaultSystemRupSet compRupSet = null;
		FaultSystemSolution compSol = null;
		String compName = null;
		if (meta.comparison != null && meta.comparison.rupSet.hasModule(ClusterRuptures.class)) {
			compRupSet = meta.comparison.rupSet;
			compSol = meta.comparison.sol;
			compName = meta.comparison.name;
		}
		
		SectionDistanceAzimuthCalculator distAzCalc = rupSet.getModule(SectionDistanceAzimuthCalculator.class);
		
		boolean hasSols = sol != null || compSol != null;
		
		List<String> lines = new ArrayList<>();
		TableBuilder table = MarkdownUtils.tableBuilder();
		if (hasSols)
			table.addLine("As Discretized", "Rate Weighted");
		List<ClusterRupture> inputRups = rupSet.getModule(ClusterRuptures.class).getAll();
		List<ClusterRupture> compRups = compRupSet == null ? null : compRupSet.getModule(ClusterRuptures.class).getAll();
		for (float jumpDist : maxJumpDists) {
			lines.add("");
			System.out.println("Plotting num jumps");
			table.initNewLine();
			File plotFile = plotFixedJumpDist(rupSet, null, inputRups, getTruncatedTitle(meta.primary.name),
					compRupSet, null, compRups, getTruncatedTitle(compName), distAzCalc, 0d, jumpDist, resourcesDir);
			table.addColumn("![Plausibility Filter]("+resourcesDir.getName()+"/"+plotFile.getName()+")");
			if (hasSols) {
				plotFile = plotFixedJumpDist(
						sol == null ? null : rupSet, sol, inputRups, getTruncatedTitle(meta.primary.name),
						compSol == null ? null : compRupSet, compSol, compRups, getTruncatedTitle(compName),
						distAzCalc, 0d, jumpDist, resourcesDir);
				table.addColumn("![Plausibility Filter]("+resourcesDir.getName()+"/"+plotFile.getName()+")");
			}
		}
		lines.addAll(table.build());
		lines.add("");
		return lines;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return List.of(ClusterRuptures.class, SectionDistanceAzimuthCalculator.class);
	}
	
	public static File plotFixedJumpDist(FaultSystemRupSet inputRupSet, FaultSystemSolution inputSol,
			List<ClusterRupture> inputClusterRups, String inputName, FaultSystemRupSet compRupSet,
			FaultSystemSolution compSol, List<ClusterRupture> compClusterRups, String compName,
			SectionDistanceAzimuthCalculator distAzCalc, double minMag, float jumpDist, File outputDir)
					throws IOException {
		
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
		if (minMag > 0d) {
			title = "M≥"+(float)minMag+" "+(float)jumpDist+" km Jump Comparison";
		} else {
			title = (float)jumpDist+" km Jump Comparison";
		}
		Range yRange = null;
		String prefixAdd;
		if (inputSol != null || compSol != null) {
			yAxisLabel = "Fraction (Rate-Weighted)";
			yRange = new Range(0d, 1d);
			prefixAdd = "_rates";
		} else {
			yAxisLabel = "Count";
			prefixAdd = "_counts";
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

}
