package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.uncertainty.BoundedUncertainty;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;

public class PaleoDataComparisonPlot extends AbstractSolutionPlot {

	@Override
	public String getName() {
		return "Paleoseismic Data Comparison";
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		PaleoseismicConstraintData data = sol.getRupSet().requireModule(PaleoseismicConstraintData.class);
		
		FaultSystemSolution compSol = meta.hasComparisonSol() ? meta.comparison.sol : null;
		if (compSol != null) {
			// make sure they use the same sections
			if (!sol.getRupSet().areSectionsEquivalentTo(compSol.getRupSet()))
				compSol = null;
		}
		
		boolean hasRateData = data.hasPaleoRateConstraints();
		boolean hasSlipData = data.hasPaleoSlipConstraints();
		
		List<String> lines = new ArrayList<>();
		
		Map<SectMappedUncertainDataConstraint, Double> paleoRates = null;
		Map<SectMappedUncertainDataConstraint, Double> compPaleoRates = null;
		if (hasRateData) {
			paleoRates = calcSolPaleoRates(data.getPaleoRateConstraints(), data.getPaleoProbModel(), sol);
			if (compSol != null)
				compPaleoRates = calcSolPaleoRates(data.getPaleoRateConstraints(), data.getPaleoProbModel(), compSol);
			
			if (hasSlipData) {
				lines.add(getSubHeading()+" Paleo-Rate Data Comparison");
				lines.add(topLink); lines.add("");
			}
			
			String title = "Paleo-Rate Data Fits";
			
			lines.addAll(paleoLines(resourcesDir, "paleo_rate", relPathToResources, title, meta, paleoRates, compPaleoRates));
			lines.add("");
		}
		
		Map<SectMappedUncertainDataConstraint, Double> paleoSlips = null;
		Map<SectMappedUncertainDataConstraint, Double> compPaleoSlips = null;
		if (hasSlipData) {
			paleoSlips = calcSolPaleoSlipRates(data.getPaleoSlipConstraints(), data.getPaleoSlipProbModel(), sol);
			if (compSol != null)
				compPaleoSlips = calcSolPaleoSlipRates(data.getPaleoSlipConstraints(), data.getPaleoSlipProbModel(), compSol);

			String title = "Paleo-Rate (Implied from Slip) Data Fits";
			if (hasRateData) {
				lines.add(getSubHeading()+" Paleo-Slip Data Comparison");
				lines.add(topLink); lines.add("");
			}
			
			lines.addAll(paleoLines(resourcesDir, "paleo_slip", relPathToResources, title, meta, paleoSlips, compPaleoSlips));
			lines.add("");
		}
		
		return lines;
	}
	
	private static List<String> paleoLines(File resourcesDir, String prefix, String relPathToResources, String title,
			ReportMetadata meta, Map<SectMappedUncertainDataConstraint, Double> data,
			Map<SectMappedUncertainDataConstraint, Double> compData) throws IOException {
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		if (compData == null) {
			table.addLine("![Paleo Data Comparison]("+relPathToResources+"/"+plotScatter(resourcesDir, prefix+"_scatter",
					data, title, getTruncatedTitle(meta.primary.name)+" Recurrence Rate (/yr)").getName()+")");
			table.addLine("![Paleo Data Fits]("+relPathToResources+"/"+plotFitHist(resourcesDir, prefix+"_hist",
					data, title, MAIN_COLOR).getName()+")");
		} else {
			table.addLine("Primary", "Comparison");
			table.addLine("![Paleo Data Comparison]("+relPathToResources+"/"+plotScatter(resourcesDir, prefix+"_scatter",
					data, title, getTruncatedTitle(meta.primary.name)+" Recurrence Rate (/yr)").getName()+")",
					"![Paleo Data Comparison]("+relPathToResources+"/"+plotScatter(resourcesDir, prefix+"_scatter_comp",
							compData, title, getTruncatedTitle(meta.comparison.name)+" Recurrence Rate (/yr)").getName()+")");
			table.addLine("![Paleo Data Fits]("+relPathToResources+"/"+plotFitHist(resourcesDir, prefix+"_hist",
					data, title, MAIN_COLOR).getName()+")",
					"![Paleo Data Fits]("+relPathToResources+"/"+plotFitHist(resourcesDir, prefix+"_hist_comp",
							compData, title, COMP_COLOR).getName()+")");
		}
		
		List<String> lines = new ArrayList<>();
		lines.addAll(table.build());
		lines.add("");
		
		table = MarkdownUtils.tableBuilder();;
		table.initNewLine();
		table.addColumns("Site Name", "Mapped Subsection ID", "Mapped Parent Section", "Constraint Mean Rate",
				"68% Bounds", "Constraint 95% Bounds",
				"Std. Dev", "Solution Rate", "z-score");
		if (compData != null)
			table.addColumns("Comparison Solution Rate", "Comparison z-score");
		table.finalizeLine();
		
		for (SectMappedUncertainDataConstraint val : data.keySet()) {
			table.initNewLine();
			table.addColumn(val.name);
			if (val.sectionIndex >= 0)
				table.addColumn(val.sectionIndex).addColumn(meta.primary.rupSet.getFaultSectionData(val.sectionIndex).getParentSectionName());
			else
				table.addColumn("_N/A_").addColumn("_N/A_");
			table.addColumn((float)val.bestEstimate);
			BoundedUncertainty bounds68 = val.estimateUncertaintyBounds(UncertaintyBoundType.CONF_68);
			BoundedUncertainty bounds95 = val.estimateUncertaintyBounds(UncertaintyBoundType.CONF_95);
			table.addColumn("["+(float)bounds68.lowerBound+", "+(float)bounds68.upperBound+"]");
			table.addColumn("["+(float)bounds95.lowerBound+", "+(float)bounds95.upperBound+"]");
			table.addColumn((float)val.getPreferredStdDev());
			Double solVal = data.get(val);
			if (solVal == null) {
				table.addColumn("_N/A_").addColumn("_N/A_");
			} else {
				if (bounds68.contains(solVal))
					table.addColumn("**"+solVal.floatValue()+"**");
				else if (bounds95.contains(solVal))
					table.addColumn("_"+solVal.floatValue()+"_");
				else
					table.addColumn(solVal.floatValue());
				double z = (solVal - val.bestEstimate)/val.getPreferredStdDev();
				table.addColumn((float)z);
			}
			if (compData != null) {
				Double compVal = compData.get(val);
				if (compVal == null) {
					// slip ones may be different, see if we have a match
					for (SectMappedUncertainDataConstraint oData : compData.keySet()) {
						if (oData.name.equals(val.name) && oData.sectionIndex == val.sectionIndex) {
							compVal = compData.get(oData);
							break;
						}
					}
				}
				if (compVal == null) {
					table.addColumn("_N/A_").addColumn("_N/A_");
				} else {
					if (bounds68.contains(compVal))
						table.addColumn("**"+compVal.floatValue()+"**");
					else if (bounds95.contains(compVal))
						table.addColumn("_"+compVal.floatValue()+"_");
					else
						table.addColumn(compVal.floatValue());
					double z = (compVal - val.bestEstimate)/val.getPreferredStdDev();
					table.addColumn((float)z);
				}
			}
			table.finalizeLine();
		}
		
		File csvFile = new File(resourcesDir, prefix+".csv");
		CSVFile<String> csv = table.toCSV(true);
		csv.writeToFile(csvFile);
		
		String line = "Paleo data comparison table. For the solution";
		if (compData == null)
			line += " column";
		else
			line += " and comparison columns";
		line += ", text is **bold if within 68% bounds**, _italicized if within 95% bounds_, and otherwise plain text.";
		line += " [Download CSV here]("+resourcesDir.getName()+"/"+csvFile.getName()+").";
		lines.add(line);
		lines.add("");
		lines.addAll(table.build());
		
		return lines;
	}
	
	private static Map<SectMappedUncertainDataConstraint, Double> calcSolPaleoRates(
			List<? extends SectMappedUncertainDataConstraint> paleoRateData, PaleoProbabilityModel paleoProbModel,
			FaultSystemSolution sol) {
		Map<SectMappedUncertainDataConstraint, Double> ret = new LinkedHashMap<>(); // use linked implementation to maintain iteration order
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		for (SectMappedUncertainDataConstraint constr : paleoRateData) {
			if (constr.sectionIndex < 0)
				continue;
			double rate = 0d;
			for (int rupIndex : rupSet.getRupturesForSection(constr.sectionIndex))
				rate += sol.getRateForRup(rupIndex) * paleoProbModel.getProbPaleoVisible(rupSet, rupIndex, constr.sectionIndex);
			ret.put(constr, rate);
		}
		
		return ret;
	}
	
	private static Map<SectMappedUncertainDataConstraint, Double> calcSolPaleoSlipRates(
			List<? extends SectMappedUncertainDataConstraint> paleoSlipData, PaleoSlipProbabilityModel paleoSlipProbModel,
			FaultSystemSolution sol) {
		
		Map<SectMappedUncertainDataConstraint, Double> ret = new LinkedHashMap<>(); // use linked implementation to maintain iteration order
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		List<SectMappedUncertainDataConstraint> rateData = PaleoseismicConstraintData.inferRatesFromSlipConstraints(
				rupSet.requireModule(SectSlipRates.class), paleoSlipData, true);
		
		AveSlipModule aveSlipModule = rupSet.requireModule(AveSlipModule.class);
		SlipAlongRuptureModel slipAloModel = rupSet.requireModule(SlipAlongRuptureModel.class);
		
		for (SectMappedUncertainDataConstraint constr : rateData) {
			if (constr.sectionIndex < 0)
				continue;
			double rate = 0d;
			for (int rupIndex : rupSet.getRupturesForSection(constr.sectionIndex)) {
				int sectIndexInRup = rupSet.getSectionsIndicesForRup(rupIndex).indexOf(constr.sectionIndex);
				double slipOnSect = slipAloModel.calcSlipOnSectionsForRup(rupSet, aveSlipModule, rupIndex)[sectIndexInRup]; 
				double probVisible = paleoSlipProbModel.getProbabilityOfObservedSlip(slipOnSect);
				rate += sol.getRateForRup(rupIndex) * probVisible;
			}
			ret.put(constr, rate);
		}
		
		return ret;
	}
	
	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return Collections.singleton(PaleoseismicConstraintData.class);
	}
	
	private static File plotScatter(File outputDir, String prefix, Map<SectMappedUncertainDataConstraint, Double> rateMap,
			String title, String yAxisLabel) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();

		DefaultXY_DataSet scatterInside68 = new DefaultXY_DataSet();
		DefaultXY_DataSet scatterInside95 = new DefaultXY_DataSet();
		DefaultXY_DataSet scatterOutside = new DefaultXY_DataSet();
		
		MinMaxAveTracker valTrack = new MinMaxAveTracker();
		for (SectMappedUncertainDataConstraint constr : rateMap.keySet()) {
			double rate = rateMap.get(constr);
			if (rate != 0d) {
				valTrack.addValue(rate);
				valTrack.addValue(constr.bestEstimate);
				// make sure at least part of the uncertainties are visible
				BoundedUncertainty halfSigaBounds = constr.estimateUncertaintyBounds(UncertaintyBoundType.HALF_SIGMA);
				valTrack.addValue(halfSigaBounds.lowerBound);
				valTrack.addValue(halfSigaBounds.upperBound);
			}
		}
		
		Range range = Double.isInfinite(valTrack.getMin()) ? new Range(1e-10, 1) :
			calcEncompassingLog10Range(valTrack.getMin(), valTrack.getMax());
		
		double logWhiskerDelta95 = 0.03;
		double logWhiskerDelta68 = 0.04;
		
		for (SectMappedUncertainDataConstraint constr : rateMap.keySet()) {
			double rate = rateMap.get(constr);
			if (rate == 0d)
				continue;
			double paleoRate = constr.bestEstimate;
			
			BoundedUncertainty conf68 = constr.estimateUncertaintyBounds(UncertaintyBoundType.CONF_68);
			BoundedUncertainty conf95 = constr.estimateUncertaintyBounds(UncertaintyBoundType.CONF_95);
			double lower68 = conf68.lowerBound;
			double upper68 = conf68.upperBound;
			double lower95 = conf95.lowerBound;
			double upper95 = conf95.upperBound;
			if (rate >= lower68 && rate <= upper68)
				scatterInside68.set(paleoRate, rate);
			else if (rate >= lower95 && rate <= upper95)
				scatterInside95.set(paleoRate, rate);
			else
				scatterOutside.set(paleoRate, rate);
			
			double whiskerAbove95 = Math.pow(10, Math.log10(rate)+logWhiskerDelta95);
			double whiskerBelow95 = Math.pow(10, Math.log10(rate)-logWhiskerDelta95);
			
			DefaultXY_DataSet confRange95 = new DefaultXY_DataSet();
			if (range.contains(lower95)) {
				confRange95.set(lower95, whiskerAbove95);
				confRange95.set(lower95, whiskerBelow95);
				confRange95.set(lower95, rate);
			} else {
				confRange95.set(range.getLowerBound(), rate);
			}
			if (range.contains(upper95)) {
				confRange95.set(upper95, rate);
				confRange95.set(upper95, whiskerAbove95);
				confRange95.set(upper95, whiskerBelow95);
			} else {
				confRange95.set(range.getUpperBound(), rate);
			}
			
			funcs.add(confRange95);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.LIGHT_GRAY));
			
			double whiskerAbove68 = Math.pow(10, Math.log10(rate)+logWhiskerDelta68);
			double whiskerBelow68 = Math.pow(10, Math.log10(rate)-logWhiskerDelta68);
			
			DefaultXY_DataSet confRange68 = new DefaultXY_DataSet();
			if (range.contains(lower68)) {
				confRange68.set(lower68, whiskerAbove68);
				confRange68.set(lower68, whiskerBelow68);
				confRange68.set(lower68, rate);
			} else {
				confRange68.set(range.getLowerBound(), rate);
			}
			if (range.contains(upper68)) {
				confRange68.set(upper68, rate);
				confRange68.set(upper68, whiskerAbove68);
				confRange68.set(upper68, whiskerBelow68);
			} else {
				confRange68.set(range.getUpperBound(), rate);
			}
			
			funcs.add(confRange68);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY));
		}
		
		if (scatterInside68.size() > 0) {
			scatterInside68.setName("Inside 68% Conf");
			funcs.add(scatterInside68);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 7f, Color.GREEN.darker()));
		}
		
		if (scatterInside95.size() > 0) {
			scatterInside95.setName("Inside 95% Conf");
			funcs.add(scatterInside95);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 7f, Color.BLUE.darker()));
		}

		if (scatterOutside.size() > 0) {
			scatterOutside.setName("Outside 95% Conf");
			funcs.add(scatterOutside);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 7f, Color.RED.darker()));
		}
		
		double minVal = Double.POSITIVE_INFINITY;
		double maxVal = Double.NEGATIVE_INFINITY;
		if (scatterInside68.size() > 0) {
			minVal = Math.min(minVal, scatterInside68.getMinX());
			minVal = Math.min(minVal, scatterInside68.getMinY());
			maxVal = Math.max(maxVal, scatterInside68.getMaxX());
			maxVal = Math.max(maxVal, scatterInside68.getMaxY());
		}
		if (scatterInside95.size() > 0) {
			minVal = Math.min(minVal, scatterInside95.getMinX());
			minVal = Math.min(minVal, scatterInside95.getMinY());
			maxVal = Math.max(maxVal, scatterInside95.getMaxX());
			maxVal = Math.max(maxVal, scatterInside95.getMaxY());
		}
		if (scatterOutside.size() > 0) {
			minVal = Math.min(minVal, scatterOutside.getMinX());
			minVal = Math.min(minVal, scatterOutside.getMinY());
			maxVal = Math.max(maxVal, scatterOutside.getMaxX());
			maxVal = Math.max(maxVal, scatterOutside.getMaxY());
		}
		
		DefaultXY_DataSet oneToOne = new DefaultXY_DataSet();
		oneToOne.set(range.getLowerBound(), range.getLowerBound());
		oneToOne.set(range.getUpperBound(), range.getUpperBound());
		
		if (funcs.isEmpty()) {
			funcs.add(oneToOne);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		} else {
			funcs.set(0, oneToOne);
			chars.set(0, new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		}
		
		PlotSpec plot = new PlotSpec(funcs, chars, title, "Paleoseismic Data Recurrence Rate (/yr)", yAxisLabel);
		plot.setLegendVisible(true);
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		gp.drawGraphPanel(plot, true, true, range, range);
		
		PlotUtils.writePlots(outputDir, prefix, gp, 800, false, true, false, false);
		
		return new File(outputDir, prefix+".png");
	}
	
	private static File plotFitHist(File outputDir, String prefix, Map<SectMappedUncertainDataConstraint, Double> rateMap,
			String title, Color color) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();

		Range xRange = new Range(-3d, 3d);
		HistogramFunction hist = HistogramFunction.getEncompassingHistogram(-2.99, 2.99, 0.1);
		
		for (SectMappedUncertainDataConstraint constr : rateMap.keySet()) {
			double rate = rateMap.get(constr);
			if (rate == 0d)
				continue;
			double paleoRate = constr.bestEstimate;
			double stdDev = constr.getPreferredStdDev();
			
			double z = (rate - paleoRate)/stdDev;
			hist.add(hist.getClosestXIndex(z), 1d);
		}
		
		funcs.add(hist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, color));
		
		Range yRange = new Range(0d, hist.getMaxY()+2);
		
		PlotSpec plot = new PlotSpec(funcs, chars, title, "z-score (standard deviations)", "Count");
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		gp.drawGraphPanel(plot, false, false, xRange, yRange);
		
		PlotUtils.setYTick(gp, 1d);
		
		PlotUtils.writePlots(outputDir, prefix, gp, 800, 650, true, false, false);
		
		return new File(outputDir, prefix+".png");
	}
	
	public static void main(String[] args) throws IOException {
//		File solFile = new File("/home/kevin/markdown/inversions/2021_10_25-u3rs-u3_std_dev_tests-10m/solution.zip");
		File solFile = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2021_11_03-reproduce-ucerf3-ref_branch-uniform-nshm23_draft-supra_b_0.8-2h/mean_solution.zip");
		FaultSystemSolution sol1 = FaultSystemSolution.load(solFile);
//		FaultSystemSolution sol2 = null;
//		FaultSystemSolution sol2 = sol1;
		FaultSystemSolution sol2 = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2021_11_03-reproduce-ucerf3-ref_branch-uniform-nshm23_draft-supra_b_0.8-no_sect_rate-2h/mean_solution.zip"));
		
//		File outputDir = new File(solFile.getParentFile(), "temp_report");
		File outputDir = new File("/tmp/temp_report");
		
		PaleoDataComparisonPlot plot = new PaleoDataComparisonPlot();

		List<AbstractRupSetPlot> plots = List.of(plot);
//		List<AbstractRupSetPlot> plots = List.of(plot, new ModulesPlot(), new NamedFaultPlot());
		
		ReportPageGen report = new ReportPageGen(sol1.getRupSet(), sol1, "Solution", outputDir, List.of(plot));
//		ReportMetadata meta = new ReportMetadata(new RupSetMetadata("Sol 1", sol1),
//				sol2 == null ? null : new RupSetMetadata("Sol 2", sol2));
//		ReportPageGen report = new ReportPageGen(meta, outputDir, plots);
		
		report.setReplot(true);
		report.generatePage();
	}

}
