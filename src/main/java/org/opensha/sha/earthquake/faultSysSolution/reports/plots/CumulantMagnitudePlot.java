package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.SolidFillPlot;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

public class CumulantMagnitudePlot extends AbstractSolutionPlot implements SolidFillPlot {

	boolean fillSurfaces = false;

	@Override
	public void setFillSurfaces(boolean fillSurfaces){
		this.fillSurfaces = fillSurfaces;
	}

	@Override
	public String getName() {
		return "Fault Cumulant Magnitudes";
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		List<String> header = new ArrayList<>();
		header.add("Section Index");
		header.add("Section Name");
		header.add(meta.primary.name+" Median");
		header.add(meta.primary.name+" IQR");
		
		boolean comparison = meta.hasComparisonSol() && meta.comparisonHasSameSects;
		if (comparison) {
			header.add(meta.comparison.name+" Median");
			header.add(meta.comparison.name+" IQR");
		}
		csv.addLine(header);
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		FaultSystemSolution compSol = comparison ? meta.comparison.sol : null;
		
		List<Double> medians = new ArrayList<>();
		List<Double> compMedians = comparison ? new ArrayList<>() : null;
		
		List<Double> iqrs = new ArrayList<>();
		List<Double> compIQRs = comparison ? new ArrayList<>() : null;
		
		DefaultXY_DataSet medianScatter = comparison ? new DefaultXY_DataSet() : null;
		DefaultXY_DataSet iqrScatter = comparison ? new DefaultXY_DataSet() : null;
		
		for (int s=0; s<rupSet.getNumSections(); s++) {
			List<String> line = new ArrayList<>();
			line.add(s+"");
			line.add(rupSet.getFaultSectionData(s).getName());
			EvenlyDiscretizedFunc func = calcCumulantMagFunc(sol, s);
			double median, iqr;
			if (func == null) {
				median = Double.NaN;
				iqr = Double.NaN;
			} else {
				median = func.getFirstInterpolatedX(0.5);
				iqr = func.getFirstInterpolatedX(0.75) - func.getFirstInterpolatedX(0.25);
			}
			medians.add(median);
			iqrs.add(iqr);
			line.add(median+"");
			line.add(iqr+"");
			if (comparison) {
				EvenlyDiscretizedFunc compFunc = calcCumulantMagFunc(compSol, s);
				double compMedian, compIQR;
				if (compFunc == null) {
					compMedian = Double.NaN;
					compIQR = Double.NaN;
				} else {
					compMedian = compFunc.getFirstInterpolatedX(0.5);
					compIQR = compFunc.getFirstInterpolatedX(0.75) - compFunc.getFirstInterpolatedX(0.25);
					if (!Double.isNaN(median)) {
						medianScatter.set(compMedian, median);
						iqrScatter.set(compIQR, iqr);
					}
				}
				compMedians.add(compMedian);
				compIQRs.add(compIQR);
				line.add(compMedian+"");
				line.add(compIQR+"");
			}
			csv.addLine(line);
		}
		
		String medianPrefix = "mag_cumulant_medians";
		String iqrPrefix = "mag_cumulant_iqrs";
		
		csv.writeToFile(new File(resourcesDir, medianPrefix+".csv"));
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(rupSet, meta.region);
		
		CPT cpt = GMT_CPT_Files.SEQUENTIAL_BATLOW_UNIFORM.instance().rescale(6d,  8.5d);
		mapMaker.plotSectScalars(medians, cpt, meta.primary.name+" Mag Cumulant Median");
		mapMaker.plot(resourcesDir, medianPrefix, " ");
		
		if (comparison) {
			mapMaker.plotSectScalars(compMedians, cpt, meta.comparison.name+" Mag Cumulant Median");
			mapMaker.plot(resourcesDir, medianPrefix+"_comp", " ");
		}
		
		CPT diffCPT = GMT_CPT_Files.DIVERGING_VIK_UNIFORM.instance().rescale(-1, 1);
//		CPT diffCPT = GMT_CPT_Files.GMT_POLAR.instance().rescale(-1d, 1d);
//		CPT diffCPT = new CPT(-1, 1d,
//				new Color(0, 0, 140), new Color(0, 60, 200 ), new Color(0, 120, 255),
//				Color.WHITE,
//				new Color(255, 120, 0), new Color(200, 60, 0), new Color(140, 0, 0));
		diffCPT.setBelowMinColor(diffCPT.getMinColor());
		diffCPT.setAboveMaxColor(diffCPT.getMaxColor());
		
		if (comparison) {
			double[] diffVals = new double[rupSet.getNumSections()];
			
			for (int i=0; i<diffVals.length; i++)
				diffVals[i] = medians.get(i) - compMedians.get(i);
			
			mapMaker.plotSectScalars(diffVals, diffCPT, "Difference, Mag Cumulant Median");
			mapMaker.plot(resourcesDir, medianPrefix+"_diff", " ");
		}
		
		// IQRs
//		CPT iqrCPT = new CPT(0d, 1d, new Color(40, 0, 0), new Color(80, 0, 0), 	new Color(140, 0, 0),
//				new Color(200, 60, 0), new Color(255, 120, 0), new Color(255, 200, 0),
//				new Color(255, 225, 0), Color.WHITE);
		CPT iqrCPT = GMT_CPT_Files.SEQUENTIAL_LAJOLLA_UNIFORM.instance().reverse().rescale(0d, 1d);
		iqrCPT.setNanColor(Color.GRAY);
		
		mapMaker.plotSectScalars(iqrs, iqrCPT, meta.primary.name+" Mag Cumulant IQR");
		mapMaker.plot(resourcesDir, iqrPrefix, " ");
		
		if (comparison) {
			mapMaker.plotSectScalars(compIQRs, iqrCPT, meta.comparison.name+" Mag Cumulant IQR");
			mapMaker.plot(resourcesDir, iqrPrefix+"_comp", " ");
			
			diffCPT = diffCPT.rescale(-0.5, 0.5);
			double[] diffVals = new double[rupSet.getNumSections()];
			
			for (int i=0; i<diffVals.length; i++)
				diffVals[i] = iqrs.get(i) - compIQRs.get(i);
			
			mapMaker.plotSectScalars(diffVals, diffCPT, "Difference, Mag Cumulant IQR");
			mapMaker.plot(resourcesDir, iqrPrefix+"_diff", " ");
			
			// scatters
			List<XY_DataSet> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			double minMedian = 0.5*Math.floor(2d*Math.min(medianScatter.getMinX(), medianScatter.getMinY()));
			double maxMedian = 0.5*Math.ceil(2d*Math.max(medianScatter.getMaxX(), medianScatter.getMaxY()));
			DefaultXY_DataSet oneToOne = new DefaultXY_DataSet();
			oneToOne.set(minMedian, minMedian);
			oneToOne.set(maxMedian, maxMedian);
			
			funcs.add(oneToOne);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
			
			funcs.add(medianScatter);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 2f, Color.BLACK));
			
			PlotSpec spec = new PlotSpec(funcs, chars, "Median Cumulant Mag Scatter", meta.comparison.name, meta.primary.name);
			
			List<XYTextAnnotation> anns = new ArrayList<>();
			DecimalFormat magDF = new DecimalFormat("0.0");
			Font annFont = new Font(Font.SANS_SERIF, Font.BOLD, 22);
			double[] medianArray = getNoNans(medians);
			double meanMedian = StatUtils.mean(medianArray);
			double medianMedian = DataUtils.median(medianArray);
			XYTextAnnotation ann = new XYTextAnnotation(
					"  "+meta.primary.name+": mean="+magDF.format(meanMedian)+", mdn.="+magDF.format(medianMedian),
					minMedian, minMedian + 0.95*(maxMedian-minMedian));
			ann.setTextAnchor(TextAnchor.TOP_LEFT);
			ann.setFont(annFont);
			anns.add(ann);
			double[] compMedianArray = getNoNans(compMedians);
			double compMeanMedian = StatUtils.mean(compMedianArray);
			double compMedianMedian = DataUtils.median(compMedianArray);
			ann = new XYTextAnnotation(
					"  "+meta.primary.name+": mean="+magDF.format(compMeanMedian)+", mdn.="+magDF.format(compMedianMedian),
					minMedian, minMedian + 0.9*(maxMedian-minMedian));
			ann.setTextAnchor(TextAnchor.TOP_LEFT);
			ann.setFont(annFont);
			anns.add(ann);
			spec.setPlotAnnotations(anns);
			
			HeadlessGraphPanel gp = PlotUtils.initHeadless();
			
			gp.drawGraphPanel(spec, false, false, new Range(minMedian, maxMedian), new Range(minMedian, maxMedian));
			PlotUtils.writePlots(resourcesDir, medianPrefix+"_scatter", gp, 800, 800, true, false, false);
			
			funcs = new ArrayList<>();
			chars = new ArrayList<>();

			double maxIQR = Math.ceil(2*Math.max(iqrScatter.getMaxX(), iqrScatter.getMaxY()))*0.5d;
			oneToOne = new DefaultXY_DataSet();
			oneToOne.set(0d, 0d);
			oneToOne.set(maxIQR, maxIQR);
			
			funcs.add(oneToOne);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
			
			funcs.add(iqrScatter);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 2f, Color.BLACK));
			
			spec = new PlotSpec(funcs, chars, "Cumulant Mag IQR Scatter", meta.primary.name, meta.comparison.name);
			
			anns = new ArrayList<>();
			DecimalFormat iqrDF = new DecimalFormat("0.00");
			double[] iqrArray = getNoNans(iqrs);
			double meanIQR = StatUtils.mean(iqrArray);
			double medianIQR = DataUtils.median(iqrArray);
			ann = new XYTextAnnotation(
					"  "+meta.primary.name+": mean="+iqrDF.format(meanIQR)+", mdn.="+iqrDF.format(medianIQR),
					0d, 0.95*maxIQR);
			ann.setTextAnchor(TextAnchor.TOP_LEFT);
			ann.setFont(annFont);
			anns.add(ann);
			double[] compIQRArray = getNoNans(compIQRs);
			double compMeanIQR = StatUtils.mean(compIQRArray);
			double compMedianIQR = DataUtils.median(compIQRArray);
			ann = new XYTextAnnotation(
					"  "+meta.comparison.name+": mean="+iqrDF.format(compMeanIQR)+", mdn.="+iqrDF.format(compMedianIQR),
					0d, 0.9*maxIQR);
			ann.setTextAnchor(TextAnchor.TOP_LEFT);
			ann.setFont(annFont);
			anns.add(ann);
			spec.setPlotAnnotations(anns);
			
			gp.drawGraphPanel(spec, false, false, new Range(0, maxIQR), new Range(0, maxIQR));
			PlotUtils.writePlots(resourcesDir, iqrPrefix+"_scatter", gp, 800, 800, true, false, false);
		}
		
		List<String> lines = new ArrayList<>();
		lines.add("These plots show the magnitude above which half of all moment is released (median cumulant magnitude), "
				+ "as well as the interquartile range of the cumulant magnitude distribution.");
		lines.add("");
		
		if (comparison) {
			TableBuilder table = MarkdownUtils.tableBuilder();
			table.addLine(meta.primary.name, meta.comparison.name, "Difference");
			File plot = new File(resourcesDir, medianPrefix+".png");
			Preconditions.checkState(plot.exists());
			File compPlot = new File(resourcesDir, medianPrefix+"_comp.png");
			Preconditions.checkState(compPlot.exists());
			File diffPlot = new File(resourcesDir, medianPrefix+"_diff.png");
			Preconditions.checkState(diffPlot.exists());
			table.addLine("!["+meta.primary.name+"]("+resourcesDir.getName()+"/"+plot.getName()+")",
					"!["+meta.comparison.name+"]("+resourcesDir.getName()+"/"+compPlot.getName()+")",
					"![Difference]("+resourcesDir.getName()+"/"+diffPlot.getName()+")");
			plot = new File(resourcesDir, iqrPrefix+".png");
			Preconditions.checkState(plot.exists());
			compPlot = new File(resourcesDir, iqrPrefix+"_comp.png");
			Preconditions.checkState(compPlot.exists());
			diffPlot = new File(resourcesDir, iqrPrefix+"_diff.png");
			Preconditions.checkState(diffPlot.exists());
			table.addLine("!["+meta.primary.name+"]("+resourcesDir.getName()+"/"+plot.getName()+")",
					"!["+meta.comparison.name+"]("+resourcesDir.getName()+"/"+compPlot.getName()+")",
					"![Difference]("+resourcesDir.getName()+"/"+diffPlot.getName()+")");
			lines.addAll(table.build());
			lines.add("");
			table = MarkdownUtils.tableBuilder();
			table.addLine("![Median Scatter]("+resourcesDir.getName()+"/"+medianPrefix+"_scatter.png)",
					"![IQR Scatter]("+resourcesDir.getName()+"/"+iqrPrefix+"_scatter.png)");
			lines.addAll(table.build());
		} else {
			TableBuilder table = MarkdownUtils.tableBuilder();
			table.addLine("Median Cumulant Magnitude", "Interquartile Range");
			table.addLine("![Median]("+resourcesDir.getName()+"/"+medianPrefix+".png)",
					"![IQR]("+resourcesDir.getName()+"/"+iqrPrefix+".png)");
			lines.addAll(table.build());
		}
		
		return lines;
	}
	
	private static EvenlyDiscretizedFunc calcCumulantMagFunc(FaultSystemSolution sol, int s) {
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(5d, 9d, (int)((9d-5d)/0.01) + 1);
		for (int r : sol.getRupSet().getRupturesForSection(s)) {
			double mag = sol.getRupSet().getMagForRup(r);
			int i = func.getClosestXIndex(mag);
			for (int x=i; x<func.size(); x++)
				func.add(x, MagUtils.magToMoment(mag));
		}
		if (func.calcSumOfY_Vals() == 0)
			return null;
		func.scale(1d/func.getMaxY());
		return func;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return null;
	}
	
	private static double[] getNoNans(List<Double> vals) {
		List<Double> ret = new ArrayList<>();
		for (double val : vals)
			if (Double.isFinite(val))
				ret.add(val);
		return Doubles.toArray(ret);
	}

}
