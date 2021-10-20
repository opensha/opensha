package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.jfree.data.Range;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;

import com.google.common.base.Preconditions;

public class SectBValuePlot extends AbstractSolutionPlot {
	
	private static final EvenlyDiscretizedFunc refFunc = new EvenlyDiscretizedFunc(0.05, 110, 0.1);
	
	private static final double minB = -3;
	private static final double maxB = 3;

	@Override
	public String getName() {
		return "Section b-values";
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		FaultSystemSolution compSol = meta.hasComparisonSol() ? meta.comparison.sol : null;
		if (compSol != null && !compSol.getRupSet().hasModule(AveSlipModule.class))
			compSol = null;
		if (compSol != null && !sol.getRupSet().areSectionsEquivalentTo(compSol.getRupSet()))
			compSol = null;
		
		double[] rupMoRates = calcMomentRates(sol);
		double[] compRupMoRates = compSol == null ? null : calcMomentRates(compSol);
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(sol.getRupSet(), meta.region);
		
		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(minB, maxB);
		cpt.setNanColor(Color.GRAY);
		
		List<String> lines = new ArrayList<>();
		lines.add(getSubHeading()+" Subsection b-values");
		lines.add(topLink); lines.add("");
		
		lines.add("These plots estimate a Gutenberg-Richter b-value for each subsection and parent section participation"
				+ " MFD. This is a rough approximation, and is intended primarily for model comparisons.");
		lines.add("");
		
		double[] sectBVals = estSectBValues(sol, rupMoRates);
		double[] compSectBVals = compSol == null ? null : estSectBValues(compSol, compRupMoRates);
		
		double[] sectRates = sol.calcTotParticRateForAllSects();
		double[] compSectRates = compSol == null ? null : compSol.calcTotParticRateForAllSects();
		
		String prefix = "sect_b_values";
		
		// map view
		mapMaker.plotSectScalars(sectBVals, cpt, "Subsection b-values");
		mapMaker.plot(resourcesDir, prefix, getTruncatedTitle(meta.primary.name));
		
		if (compSol == null) {
			lines.add("![Section b-values Plot]("+relPathToResources+"/"+prefix+".png)");
		} else {
			TableBuilder table = MarkdownUtils.tableBuilder();
			
			mapMaker.plotSectScalars(compSectBVals, cpt, "Subsection b-values");
			mapMaker.plot(resourcesDir, prefix+"_comp", getTruncatedTitle(meta.comparison.name));
			
			double[] diffs = new double[sectBVals.length];
			for (int i=0; i<diffs.length; i++)
				diffs[i] = sectBVals[i] - compSectBVals[i];
			
			CPT diffCPT = GMT_CPT_Files.GMT_POLAR.instance().rescale(-2d, 2d);
			diffCPT.setNanColor(Color.GRAY);
			mapMaker.plotSectScalars(diffs, diffCPT, "Subsection b-values, Primary - Comparison");
			mapMaker.plot(resourcesDir, prefix+"_diff", "Difference");
			
			table.addLine("![Section b-values Plot]("+relPathToResources+"/"+prefix+".png)",
					"![Section b-values Plot]("+relPathToResources+"/"+prefix+"_comp.png)",
					"![Section b-values Plot]("+relPathToResources+"/"+prefix+"_diff.png)");
			lines.addAll(table.build());
		}
		lines.add("");
		
		// histograms
		
		lines.addAll(getHistLines(sectBVals, sectRates, compSectBVals, compSectRates,
				resourcesDir, relPathToResources, prefix));
		
		lines.add("");
		lines.add(getSubHeading()+" Parent Section b-values");
		lines.add(topLink); lines.add("");
		
		Map<Integer, Double> parentBValsMap = estParentSectBValues(sol, rupMoRates);
		double[] parentBVals = new double[parentBValsMap.size()];
		double[] parentRates = new double[parentBValsMap.size()];
		calcParentVals(sol, parentBVals, parentRates, parentBValsMap);
		
		double[] compParentBVals = null;
		double[] compParentRates = null;
		if (compSol != null) {
			Map<Integer, Double> compParentBValsMap = estParentSectBValues(compSol, compRupMoRates);
			compParentBVals = new double[compParentBValsMap.size()];
			compParentRates = new double[compParentBValsMap.size()];
			calcParentVals(sol, compParentBVals, compParentRates, compParentBValsMap);
		}
		
		prefix = "parent_sect_b_values";
		lines.addAll(getHistLines(parentBVals, parentRates, compParentBVals, compParentRates,
				resourcesDir, relPathToResources, prefix));
		
		return lines;
	}
	
	private static void calcParentVals(FaultSystemSolution sol, double[] parentBVals, double[] parentRates,
			Map<Integer, Double> parentBValsMap) {
		Preconditions.checkState(parentBVals.length == parentBValsMap.size());
		List<Integer> parentIDs = new ArrayList<>(parentBValsMap.keySet());
		Collections.sort(parentIDs);
		
		for (int i=0; i<parentIDs.size(); i++) {
			int parentID = parentIDs.get(i);
			
			parentBVals[i] = parentBValsMap.get(parentID);
			parentRates[i] = 0;
			for (int r : sol.getRupSet().getRupturesForParentSection(parentID))
				parentRates[i] += sol.getRateForRup(r);
		}
	}
	
	private static double[] calcMomentRates(FaultSystemSolution sol) {
		FaultSystemRupSet rupSet = sol.getRupSet();
		AveSlipModule aveSlips = rupSet.requireModule(AveSlipModule.class);
		
		double[] ret = new double[rupSet.getNumRuptures()];
		for (int r=0; r<ret.length; r++)
			ret[r] = sol.getRateForRup(r)*FaultMomentCalc.getMoment(rupSet.getAreaForRup(r), aveSlips.getAveSlip(r));
		return ret;
	}
	
	private static double[] estSectBValues(FaultSystemSolution sol, double[] rupMoRates) {
		FaultSystemRupSet rupSet = sol.getRupSet();
		double[] ret = new double[rupSet.getNumSections()];
		
		ModSectMinMags modMinMags = rupSet.getModule(ModSectMinMags.class);
		
		for (int s=0; s<ret.length; s++) {
			double minMag = modMinMags == null ? rupSet.getMinMagForSection(s) : modMinMags.getMinMagForSection(s);
			double maxMag = rupSet.getMaxMagForSection(s);
			
			ret[s] = estBValue(minMag, maxMag, sol, rupSet.getRupturesForSection(s), rupMoRates);
		}
		
		return ret;
	}
	
	private static Map<Integer, Double> estParentSectBValues(FaultSystemSolution sol, double[] rupMoRates) {
		FaultSystemRupSet rupSet = sol.getRupSet();
		HashMap<Integer, Double> ret = new HashMap<>();
		
		Map<Integer, List<FaultSection>> sectsByParent = rupSet.getFaultSectionDataList().stream().collect(
				Collectors.groupingBy(S -> S.getParentSectionId()));
		
		ModSectMinMags modMinMags = rupSet.getModule(ModSectMinMags.class);
		
		for (int p : sectsByParent.keySet()) {
			double minMag = Double.POSITIVE_INFINITY;
			double maxMag = Double.NEGATIVE_INFINITY;
			
			for (FaultSection sect : sectsByParent.get(p)) {
				int s = sect.getSectionId();
				minMag = Math.min(minMag, modMinMags == null ? rupSet.getMinMagForSection(s) : modMinMags.getMinMagForSection(s));
				maxMag = Math.max(maxMag, rupSet.getMaxMagForSection(s));
			}
			
			ret.put(p, estBValue(minMag, maxMag, sol, rupSet.getRupturesForParentSection(p), rupMoRates));
		}
		
		return ret;
	}
	
	private static double estBValue(double minMag, double maxMag, FaultSystemSolution sol,
			Collection<Integer> rupIndexes, double[] rupMoRates) {
		double supraRate = 0d;
		double moRate = 0d;
		FaultSystemRupSet rupSet = sol.getRupSet();
		for (int r : rupIndexes) {
			double mag = rupSet.getMagForRup(r);
			if (mag < minMag)
				continue;
			supraRate += sol.getRateForRup(r);
			moRate += rupMoRates[r];
		}
		return estBValue(minMag, maxMag, supraRate, moRate);
	}
	
	private static double estBValue(double minMag, double maxMag, double supraRate, double moRate) {
		Preconditions.checkState(minMag >= refFunc.getMinX()-0.5);
		Preconditions.checkState(maxMag <= refFunc.getMaxX()+0.5);
		
		int binnedMinIndex = refFunc.getClosestXIndex(minMag);
		int binnedMaxIndex = refFunc.getClosestXIndex(maxMag);
		if (binnedMinIndex == binnedMaxIndex)
			return 0d;
		Preconditions.checkState(binnedMaxIndex > binnedMinIndex);
		minMag = refFunc.getX(binnedMinIndex);
		maxMag = refFunc.getX(binnedMaxIndex);
		
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(minMag,
				1+binnedMaxIndex-binnedMinIndex, refFunc.getDelta());
		gr.setAllButBvalue(minMag, maxMag, moRate, supraRate);
		
		return gr.get_bValue();
	}
	
	private static List<String> getHistLines(double[] bValues, double[] rates, double[] compBValues, double[] compRates,
			File outputDir, String relPath, String prefix) throws IOException {
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		if (compBValues == null)
			table.addLine("b-Value Distribution", "b-Value Rate-Dependence");
		else
			table.addLine("Primary b-Value Distribution", "Primary b-Value Rate-Dependence");
		
		File histPlot = histPlot(outputDir, prefix+"_hist", bValues, "b-Value", MAIN_COLOR);
		File scatterPlot = rateScatterPlot(outputDir, prefix+"_rate_scatter", bValues, rates, MAIN_COLOR);
		
		table.addLine("![Histogram]("+relPath+"/"+histPlot.getName()+")",
				"![Scatter]("+relPath+"/"+scatterPlot.getName()+")");
		
		if (compBValues != null) {
			
			File cHistPlot = histPlot(outputDir, prefix+"_hist_comp", compBValues, "B-Value", COMP_COLOR);
			File cScatterPlot = rateScatterPlot(outputDir, prefix+"_rate_scatter_comp", compBValues, compRates, COMP_COLOR);

			table.addLine(MarkdownUtils.boldCentered("Comparison B-Value Distribution"),
					MarkdownUtils.boldCentered("Comparison b-Value Rate-Dependence"));
			table.addLine("![Histogram]("+relPath+"/"+cHistPlot.getName()+")",
					"![Scatter]("+relPath+"/"+cScatterPlot.getName()+")");
			
			double[] diffs = new double[bValues.length];
			for (int i=0; i<diffs.length; i++)
				diffs[i] = bValues[i] - compBValues[i];
			
			File histDiffPlot = histPlot(outputDir, prefix+"_hist_diff", diffs, "Primary - Comparaison b-Value", COMMON_COLOR);
			File compScatterPlot = compScatterPlot(outputDir, prefix+"_comp_scatter", bValues, compBValues, COMMON_COLOR);
			
			table.addLine(MarkdownUtils.boldCentered("b-Value Difference"), MarkdownUtils.boldCentered("b-Value Scatter"));
			table.addLine("![Histogram]("+relPath+"/"+histDiffPlot.getName()+")",
					"![Scatter]("+relPath+"/"+compScatterPlot.getName()+")");
		}
		
		return table.build();
	}
	
	private static File histPlot(File outputDir, String prefix, double[] values, String xAxisLabel,
			Color color) throws IOException {
		HistogramFunction hist = HistogramFunction.getEncompassingHistogram(minB+0.01, maxB-0.01, 0.05);
		
		for (double value : values)
			if (Double.isFinite(value))
				hist.add(hist.getClosestXIndex(value), 1d);
		
		List<HistogramFunction> funcs = new ArrayList<>();
		funcs.add(hist);
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, color));
		
		PlotSpec spec = new PlotSpec(funcs, chars, " ", xAxisLabel, "Count");
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		Range xRange = new Range(minB, maxB);
		gp.drawGraphPanel(spec, false, false, xRange, null);
		
		PlotUtils.writePlots(outputDir, prefix, gp, 800, 650, true, false, false);
		return new File(outputDir, prefix+".png");
	}
	
	private static double withinBRange(double val) {
		if (val < minB)
			return minB;
		if (val > maxB)
			return maxB;
		return val;
	}
	
	private static File rateScatterPlot(File outputDir, String prefix, double[] bValues, double[] rates,
			Color color) throws IOException {
		XY_DataSet scatter = new DefaultXY_DataSet();
		
		for (int i=0; i<bValues.length; i++)
			scatter.set(rates[i], withinBRange(bValues[i]));
		
		List<XY_DataSet> funcs = new ArrayList<>();
		funcs.add(scatter);
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, color));
		
		PlotSpec spec = new PlotSpec(funcs, chars, " ", "Supra-Seismogenic Rate", "B-Value");
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		Range xRange = new Range(1e-6, 1e0);
		Range yRange = new Range(minB, maxB);
		gp.drawGraphPanel(spec, true, false, xRange, yRange);
		
		PlotUtils.writePlots(outputDir, prefix, gp, 800, 650, true, false, false);
		return new File(outputDir, prefix+".png");
	}
	
	private static File compScatterPlot(File outputDir, String prefix, double[] bValues1, double[] bValues2,
			Color color) throws IOException {
		XY_DataSet scatter = new DefaultXY_DataSet();
		
		for (int i=0; i<bValues1.length; i++)
			scatter.set(withinBRange(bValues1[i]), withinBRange(bValues2[i]));
		
		List<XY_DataSet> funcs = new ArrayList<>();
		funcs.add(scatter);
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, color));
		
		DefaultXY_DataSet line = new DefaultXY_DataSet();
		line.set(minB, minB);
		line.set(maxB, maxB);
		funcs.add(line);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
		
		PlotSpec spec = new PlotSpec(funcs, chars, " ", "Primary B-Value", "Comparison B-Value");
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		Range range = new Range(minB, maxB);
		gp.drawGraphPanel(spec, false, false, range, range);
		
		PlotUtils.writePlots(outputDir, prefix, gp, 800, -1, true, false, false);
		return new File(outputDir, prefix+".png");
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return Collections.singleton(AveSlipModule.class);
	}
	
	public static void main(String[] args) throws IOException {
		File solFile = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2021_10_18-reproduce-ucerf3-ref_branch-uniform-u3Iters/mean_solution.zip");
		File compSolFile = new File("/home/kevin/OpenSHA/UCERF3/rup_sets/modular/"
				+ "FM3_1_ZENGBB_Shaw09Mod_DsrUni_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3.zip");
		
		File outputDir = new File("/tmp/report");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		FaultSystemSolution sol = FaultSystemSolution.load(solFile);
		FaultSystemSolution compSol = FaultSystemSolution.load(compSolFile);
		
		ReportMetadata meta = new ReportMetadata(new RupSetMetadata("Primary", sol), new RupSetMetadata("Comparison", compSol));
		
		ReportPageGen gen = new ReportPageGen(meta, outputDir, List.of(new SectBValuePlot(), new ParticipationRatePlot()));
		
		gen.generatePage();
	}

}
