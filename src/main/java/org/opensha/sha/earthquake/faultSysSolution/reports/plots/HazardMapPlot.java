package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.GeoDataSetMath;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
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
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;

import com.google.common.base.Preconditions;

public class HazardMapPlot extends AbstractSolutionPlot {
	
	public static double SPACING_DEFAULT = 1d;
	
	static {
		String spacingEnv = System.getenv("FST_HAZARD_SPACING");
		if (spacingEnv != null && !spacingEnv.isBlank()) {
			try {
				SPACING_DEFAULT = Double.parseDouble(spacingEnv);
			} catch (NumberFormatException e) {
				System.err.println("Couldn't parse FST_HAZARD_SPACING environmental variable as a double: "+spacingEnv);
				e.printStackTrace();
			}
		}
	}
	
	private AttenRelRef gmpeRef;
	private double spacing;
	private double[] periods;
	
	public HazardMapPlot() {
		this(AttenRelRef.ASK_2014, SPACING_DEFAULT, 0d, 1d);
	}
	
	public HazardMapPlot(AttenRelRef gmpeRef, double spacing, double... periods) {
		this.gmpeRef = gmpeRef;
		this.spacing = spacing;
		this.periods = periods;
		
		Preconditions.checkState(periods.length > 0);
		for (double period : periods)
			Preconditions.checkState(period >= 0d, "Period must be 0 (PGA) or >0 for SA");
	}

	@Override
	public String getName() {
		return "Hazard Maps";
	}
	
	// keep track of the the most recent comparison, and reuse if possible (sometimes many plots generated with the
	// same comparison in succession)
	private static SolHazardMapCalc prevCompCalc = null;
	private static FaultSystemSolution prevSol = null;

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		GriddedRegion gridReg = new GriddedRegion(meta.region, spacing, GriddedRegion.ANCHOR_0_0);
		int numSites = gridReg.getNodeCount();
		System.out.println("Hazard gridded region with "+numSites+" sites, "+periods.length+" periods");
		
		int numThreads = getNumThreads();
		
//		// use longer maximum distance for comparisons to avoid high ratios in low rate regions at the cutoff boundary
//		double maxDist = meta.hasComparisonSol() ? 500d : 200d;
		
		SolHazardMapCalc calc = new SolHazardMapCalc(sol, gmpeRef, gridReg, periods);
//		calc.setMaxSourceSiteDist(maxDist);
		
		ArbitrarilyDiscretizedFunc xVals = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : IMT_Info.getUSGS_SA_Function())
			xVals.set(pt);
		// this function is coarse, which is fast, but lacks super low points. add some:
		xVals.set(xVals.getMinX()*0.1, 1d);
		xVals.set(xVals.getMinX()*0.1, 1d);
		calc.setXVals(xVals);
		
		calc.calcHazardCurves(numThreads);
		
		SolHazardMapCalc compCalc = null;
		if (meta.hasComparisonSol()) {
			System.out.println("Calculating comparison hazard map...");
			
			synchronized (HazardMapPlot.class) {
				if (prevCompCalc != null && prevSol == meta.comparison.sol)
					compCalc = prevCompCalc;
			}
			if (compCalc == null) {
				compCalc = new SolHazardMapCalc(meta.comparison.sol, gmpeRef, gridReg, periods);
//				compCalc.setMaxSourceSiteDist(maxDist);
				
				compCalc.setXVals(xVals);
				
				compCalc.calcHazardCurves(numThreads);
				
				synchronized (HazardMapPlot.class) {
					prevCompCalc = compCalc;
					prevSol = meta.comparison.sol;
				}
			} else {
				System.out.println("Reusing previous comparison calculator");
			}
		}
		
		return plot(resourcesDir, relPathToResources, topLink, gridReg, calc, compCalc);
	}

	public List<String> plot(File resourcesDir, String relPathToResources, String topLink,
			GriddedRegion gridReg, SolHazardMapCalc calc, SolHazardMapCalc compCalc) throws IOException {
		int numSites = gridReg.getNodeCount();
		
		System.out.println("Done calculating hazard maps!");
		
		List<String> lines = new ArrayList<>();
		
		lines.add("Hazard map comparisons with a resolution of "+optionalDigitDF.format(spacing)+" degrees ("
				+numSites+" sites). Hazard is computed with the "+gmpeRef.getShortName()+" GMPE, default site "
				+ "parameters, and supra-seismogenic fault sources only.");
		lines.add("");
		
		CPT logCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-3d, 1d);
		CPT logRatioCPT = GMT_CPT_Files.GMT_POLAR.instance().rescale(-1d, 1d);
		CPT pDiffCPT = GMT_CPT_Files.GMT_POLAR.instance().rescale(-100d, 100d);
		logRatioCPT.setNanColor(Color.GRAY);
		
		for (int p=0; p<periods.length; p++) {
			String perLabel, perUnits;
			if (periods[p] == -1d) {
				perLabel = "PGV";
				perUnits = "cm/s";
			} else if (periods[p] == 0d) {
				perLabel = "PGA";
				perUnits = "g";
			} else {
				Preconditions.checkState(periods[p] > 0);
				perLabel = optionalDigitDF.format(periods[p])+"s SA";
				perUnits = "g";
			}
			String perPrefix = perLabel.toLowerCase().replaceAll(" ", "_");
			
			String subHeading = getSubHeading();
			if (periods.length > 1) {
				lines.add(subHeading+" "+perLabel+" Hazard Maps");
				lines.add(topLink); lines.add("");
				subHeading += "#";
			}
			
			for (ReturnPeriods rp : SolHazardMapCalc.MAP_RPS) {
				lines.add(subHeading+" "+perLabel+", "+rp.label+" Hazard Maps");
				lines.add(topLink); lines.add("");
				
				String prefix = "hazard_map_"+perPrefix+"_"+rp.name().toLowerCase();
				
				GriddedGeoDataSet xyz = calc.buildMap(periods[p], rp);
				GriddedGeoDataSet logXYZ = xyz.copy();
				logXYZ.log10();
				
				String zLabel = "Log10 "+perLabel+" ("+perUnits+"), "+rp.label;
				File map = calc.plotMap(resourcesDir, prefix, logXYZ, logCPT, " ", zLabel);
				
				if (compCalc == null) {
					lines.add("![Hazard Map]("+relPathToResources+"/"+map.getName()+")");
				} else {
					GriddedGeoDataSet compXYZ = compCalc.buildMap(periods[p], rp);
					GriddedGeoDataSet compLogXYZ = compXYZ.copy();
					compLogXYZ.log10();
					
					File compMap = compCalc.plotMap(resourcesDir, prefix+"_comp", compLogXYZ, logCPT, " ", zLabel);
					
					TableBuilder table = MarkdownUtils.tableBuilder();
					table.addLine(MarkdownUtils.boldCentered("Primary"), MarkdownUtils.boldCentered("Comparison"));
					table.addLine("![Hazard Map]("+relPathToResources+"/"+map.getName()+")",
							"![Hazard Map]("+relPathToResources+"/"+compMap.getName()+")");
//					table.addLine(MarkdownUtils.boldCentered("Log10 Ratio (Primary/Comparison)"),
//							MarkdownUtils.boldCentered("Scatter"));
					table.addLine(MarkdownUtils.boldCentered("Log10 Ratio, Primary/Comparison"),
							MarkdownUtils.boldCentered("% Difference, 100*(Primary-Comparison)/Comparison"));
					
					GriddedGeoDataSet ratioXYZ = (GriddedGeoDataSet)GeoDataSetMath.divide(xyz, compXYZ);
					ratioXYZ.log10();
					for (int i=0; i<xyz.size(); i++) {
						if (!Double.isFinite(ratioXYZ.get(i))) {
							double z1 = xyz.get(i);
							double z2 = compXYZ.get(i);
							if ((float)z1 == 0f && (float)z2 == 0f)
								ratioXYZ.set(i, 0d);
							else if ((float)z1 == 0f && (float)z2 > 0f)
								ratioXYZ.set(i, Double.NEGATIVE_INFINITY);
							else if ((float)z2 == 0f && (float)z1 > 0f)
								ratioXYZ.set(i, Double.POSITIVE_INFINITY);
						}
					}
					
					String ratioLabel = zLabel;
					ratioLabel = ratioLabel.replace(" (g)", "");
					ratioLabel = ratioLabel.replace(" (cm/s)", "");
					ratioLabel = ratioLabel.replaceAll("Log10 ", "");
					File ratioMap = calc.plotMap(resourcesDir, prefix+"_ratio", ratioXYZ, logRatioCPT, " ",
							ratioLabel+", Log10 Comparison Ratio");
					table.initNewLine();
					table.addColumn("![Ratio Map]("+relPathToResources+"/"+ratioMap.getName()+")");
					
					GriddedGeoDataSet pDiffXYZ = new GriddedGeoDataSet(gridReg, false);
					for (int i=0; i<xyz.size(); i++) {
						double z1 = xyz.get(i);
						double z2 = compXYZ.get(i);
						double val;
						if (z1 == 0d && z2 == 0d)
							val = 0d;
						else if (z2 == 0d)
							val = Double.POSITIVE_INFINITY;
						else
							val = 100d*(z1-z2)/z2;
						pDiffXYZ.set(i, val);
					}
					
					String pDiffLabel = ratioLabel;
					File pDiffMap = calc.plotMap(resourcesDir, prefix+"_pDiff", pDiffXYZ, pDiffCPT, " ",
							pDiffLabel+", % Difference", true);
					table.addColumn("![Percent Difference Map]("+relPathToResources+"/"+pDiffMap.getName()+")");
					table.finalizeLine();
					
					// plot scatter
					table.initNewLine();
					File scatter = compScatterPlot(resourcesDir, prefix+"_scatter", xyz, compXYZ, zLabel);
					table.addColumn("![Scatter Plot]("+relPathToResources+"/"+scatter.getName()+")");
					File pDiffHist = compRatioHist(resourcesDir, prefix+"_pDiff_hist", xyz, compXYZ,
							pDiffLabel+", % Difference", HistType.PERCENT_DIFF);
					table.addColumn("![Hist Plot]("+relPathToResources+"/"+pDiffHist.getName()+")");
					table.finalizeLine();
					
//					File linearRatioHist = compRatioHist(resourcesDir, prefix+"_ratio_hist", xyz, compXYZ,
//							ratioLabel+", Comparison Ratio", false);
//					table.addColumn("![Hist Plot]("+relPathToResources+"/"+linearRatioHist.getName()+")");
//					File logRatioHist = compRatioHist(resourcesDir, prefix+"_ratio_hist_log", xyz, compXYZ,
//							ratioLabel+", Comparison Ratio", true);
//					table.addColumn("![Hist Plot]("+relPathToResources+"/"+logRatioHist.getName()+")");
					table.initNewLine();
					File logRatioHist = compRatioHist(resourcesDir, prefix+"_ratio_hist_log", xyz, compXYZ,
							ratioLabel+", Comparison Ratio", HistType.LOG_RATIO);
					table.addColumn("![Hist Plot]("+relPathToResources+"/"+logRatioHist.getName()+")");
					File linearRatioHist = compRatioHist(resourcesDir, prefix+"_ratio_hist", xyz, compXYZ,
							ratioLabel+", Comparison Ratio", HistType.RATIO);
					table.addColumn("![Hist Plot]("+relPathToResources+"/"+linearRatioHist.getName()+")");
					table.finalizeLine();
					
					lines.addAll(table.build());
				}
				lines.add("");
			}
		}
		
		return lines;
	}
	
	private static double withinRange(Range range, double val) {
		if (val < range.getLowerBound())
			return range.getLowerBound();
		if (val > range.getUpperBound())
			return range.getUpperBound();
		return val;
	}
	
	private static File compScatterPlot(File outputDir, String prefix, GriddedGeoDataSet xyz1, GriddedGeoDataSet xyz2,
			String label) throws IOException {
		XY_DataSet scatter = new DefaultXY_DataSet();
		
		Range range = new Range(1e-4, 1e1);
		
		for (int i=0; i<xyz1.size(); i++)
			scatter.set(withinRange(range, xyz1.get(i)), withinRange(range, xyz2.get(i)));
		
		double min = Math.min(scatter.getMinX(), scatter.getMinY());
		double max = Math.max(scatter.getMaxX(), scatter.getMaxY());
		if (min > 2e-2)
			range = new Range(1e-2, range.getUpperBound());
		else if (min > 2e-3)
			range = new Range(1e-3, range.getUpperBound());
		
		if (max < 7e-2)
			range = new Range(range.getLowerBound(), 1e-1);
		else if (max < 7e-1)
			range = new Range(range.getLowerBound(), 1e0);
		
		List<XY_DataSet> funcs = new ArrayList<>();
		funcs.add(scatter);
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLACK));
		
		DefaultXY_DataSet line = new DefaultXY_DataSet();
		line.set(range.getLowerBound(), range.getLowerBound());
		line.set(range.getUpperBound(), range.getUpperBound());
		funcs.add(line);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
		
		PlotSpec spec = new PlotSpec(funcs, chars, " ", "Primary "+label, "Comparison "+label);
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		gp.drawGraphPanel(spec, true, true, range, range);
		
		PlotUtils.writePlots(outputDir, prefix, gp, 800, -1, true, true, false);
		return new File(outputDir, prefix+".png");
	}
	
	private enum HistType {
		RATIO,
		LOG_RATIO,
		PERCENT_DIFF
	}
	
	private static File compRatioHist(File outputDir, String prefix, GriddedGeoDataSet xyz1, GriddedGeoDataSet xyz2,
			String label, HistType type) throws IOException {
		Range xRange;
		double delta;
		boolean xLog;
		switch (type) {
		case RATIO:
			xRange = new Range(0d, 2d);
			xLog = false;
			delta = 0.05;
			break;
		case LOG_RATIO:
			xRange = new Range(-1d, 1d);
			xLog = true;
			delta = 0.05;
			break;
		case PERCENT_DIFF:
			xRange = new Range(-100d, 100d);
			xLog = false;
			delta = 5;
			break;

		default:
			throw new IllegalStateException();
		}
		HistogramFunction hist = HistogramFunction.getEncompassingHistogram(
				xRange.getLowerBound()+0.1*delta, xRange.getUpperBound()-0.1*delta, delta);
		
		// re-center it so that we have a center bin
		hist = new HistogramFunction(hist.getMinX()-0.5*hist.getDelta(), hist.size()+1, hist.getDelta());
		
		double[] vals = new double[xyz1.size()];
		
		for (int i=0; i<xyz1.size(); i++) {
			double v1 = xyz1.get(i);
			double v2 = xyz2.get(i);
			double val;
			if (type == HistType.LOG_RATIO || type == HistType.RATIO) {
				if (v1 == 0d && v2 == 0d)
					val = 1d;
				else if (v2 == 0d)
					val = Double.POSITIVE_INFINITY;
				else
					val = v1/v2;
			} else {
				if (v1 == 0d && v2 == 0d)
					val = 0d;
				else if (v2 == 0d)
					val = Double.POSITIVE_INFINITY;
				else
					val = 100d*(v1-v2)/v2;
			}
			vals[i] = val;
			if (Double.isInfinite(val)) {
				if (val > 0)
					hist.add(hist.size()-1, 1d);
				else
					hist.add(0, 1d);
			} else {
				if (xLog)
					val = Math.log10(val);
				hist.add(hist.getClosestXIndex(val), 1d);
			}
		}
		hist.normalizeBySumOfY_Vals();
//		System.out.println("Ratio hist:\n"+hist);
		
		Range yRange = new Range(0d, hist.getMaxY() < 0.35 ? 0.5 : 1d);
		
		double annY1 = yRange.getUpperBound()*0.95;
		double annY2 = yRange.getUpperBound()*0.88;
		double annY3 = yRange.getUpperBound()*0.81;
		double annLeftX, annRightX;
		annLeftX = 0.05*xRange.getLength() + xRange.getLowerBound();
		annRightX = 0.95*xRange.getLength() + xRange.getLowerBound();
		if (xLog) {
			annLeftX = Math.pow(10, annLeftX);
			annRightX = Math.pow(10, annRightX);
		}
		
		Font annFont = new Font(Font.SANS_SERIF, Font.BOLD, 20);
		List<XYTextAnnotation> anns = new ArrayList<>();
		XYTextAnnotation ann = new XYTextAnnotation("Mean: "+twoDigits.format(StatUtils.mean(vals)), annLeftX, annY1);
		ann.setFont(annFont);
		ann.setTextAnchor(TextAnchor.TOP_LEFT);
		anns.add(ann);
		
		ann = new XYTextAnnotation("Median: "+twoDigits.format(DataUtils.median(vals)), annLeftX, annY2);
		ann.setFont(annFont);
		ann.setTextAnchor(TextAnchor.TOP_LEFT);
		anns.add(ann);
		
		ann = new XYTextAnnotation("Range: ["+twoDigits.format(StatUtils.min(vals))
			+","+twoDigits.format(StatUtils.max(vals))+"]", annLeftX, annY3);
		ann.setFont(annFont);
		ann.setTextAnchor(TextAnchor.TOP_LEFT);
		anns.add(ann);
		
		int numBelow = 0;
		int numAbove = 0;
		int numWithin = 0;
		
		String withinLabel, belowLabel, aboveLabel;
		if (type == HistType.LOG_RATIO || type == HistType.RATIO) {
			withinLabel = "Within [0.9,1.1]";
			belowLabel = "< 0.9";
			aboveLabel = "> 1.1";
			for (double val : vals) {
				if (val > 1.1d)
					numAbove++;
				else if (val < 0.9d)
					numBelow++;
				else
					numWithin++;
			}
		} else {
			withinLabel = "Within ±10%";
			belowLabel = "< 10%";
			aboveLabel = "> 10%";
			for (double val : vals) {
				if (val > 10d)
					numAbove++;
				else if (val < -10d)
					numBelow++;
				else
					numWithin++;
			}
		}
		
		ann = new XYTextAnnotation(withinLabel+": "+percentDF.format((double)numWithin/(double)vals.length),
				annRightX, annY1);
		ann.setFont(annFont);
		ann.setTextAnchor(TextAnchor.TOP_RIGHT);
		anns.add(ann);
		
		ann = new XYTextAnnotation(belowLabel+": "+percentDF.format((double)numBelow/(double)vals.length),
				annRightX, annY2);
		ann.setFont(annFont);
		ann.setTextAnchor(TextAnchor.TOP_RIGHT);
		anns.add(ann);
		
		ann = new XYTextAnnotation(aboveLabel+": "+percentDF.format((double)numAbove/(double)vals.length),
				annRightX, annY3);
		ann.setFont(annFont);
		ann.setTextAnchor(TextAnchor.TOP_RIGHT);
		anns.add(ann);
		
		List<XY_DataSet> funcs = new ArrayList<>();
		if (xLog) {
			ArbitrarilyDiscretizedFunc logHist = new ArbitrarilyDiscretizedFunc();
			for (Point2D pt : hist)
				logHist.set(Math.pow(10, pt.getX()), pt.getY());
			funcs.add(logHist);
			xRange = new Range(Math.pow(10, xRange.getLowerBound()), Math.pow(10, xRange.getUpperBound()));
		} else {
			funcs.add(hist);
		}
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));
		
		label = label.replace(" (g)", "");
		label = label.replace(" (cm/s)", "");
		
		PlotSpec spec = new PlotSpec(funcs, chars, " ", label, "Fraction");
		spec.setPlotAnnotations(anns);
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		gp.drawGraphPanel(spec, xLog, false, xRange, yRange);
		
		PlotUtils.writePlots(outputDir, prefix, gp, 800, 550, true, false, false);
		return new File(outputDir, prefix+".png");
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return null;
	}
	
	public static void main(String[] args) throws IOException {
		File solFile = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2021_10_19-coulomb-fm31-ref_branch-uniform-new_anneal-5x_avg-try_zero-var_perturb-noWL-5h/mean_solution.zip");
		File compSolFile = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2021_11_01-reproduce-ucerf3-ref_branch-uniform-new_anneal-5x_avg-try_zero-var_perturb-noWL-5h/mean_solution.zip");
		
		File outputDir = new File("/tmp/report");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		FaultSystemSolution sol = FaultSystemSolution.load(solFile);
		FaultSystemSolution compSol = FaultSystemSolution.load(compSolFile);
		
		ReportMetadata meta = new ReportMetadata(new RupSetMetadata("Primary", sol), new RupSetMetadata("Comparison", compSol));
		
		ReportPageGen gen = new ReportPageGen(meta, outputDir, List.of(new HazardMapPlot()));
		gen.setReplot(true);
		
		gen.generatePage();
	}

}
