package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.data.uncertainty.UncertainArbDiscFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedDiscretizedFunc;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_SiteLogicTreeHazardCurveCalc;

import com.google.common.base.Preconditions;

public class SiteLogicTreeHazardPageGen {

	@SuppressWarnings("unused")
	public static void main(String[] args) throws ZipException, IOException {
		if (args.length < 2 || args.length > 3) {
			System.err.println("USAGE: <zip-file> <output-dir> [<comp-zip-file>]");
			System.exit(1);
		}
		File zipFile = new File(args[0]);
		File outputDir = new File(args[1]);
		File compZipFile = null;
		if (args.length > 2)
			compZipFile = new File(args[2]);
		
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		ZipFile zip = new ZipFile(zipFile);
		ZipFile compZip = compZipFile == null ? null : new ZipFile(compZipFile);
		
		CSVFile<String> sitesCSV = CSVFile.readStream(zip.getInputStream(
				zip.getEntry(MPJ_SiteLogicTreeHazardCurveCalc.SITES_CSV_FILE_NAME)), true);
		
		LogicTree<?> tree = LogicTree.read(new InputStreamReader(zip.getInputStream(zip.getEntry("logic_tree.json"))));
		LogicTree<?> compTree = null;
		if (compZip != null)
			compTree = LogicTree.read(new InputStreamReader(compZip.getInputStream(compZip.getEntry("logic_tree.json"))));
		
		List<Site> sites = MPJ_SiteLogicTreeHazardCurveCalc.parseSitesCSV(sitesCSV, null);
		
		Color primaryColor = Color.RED;
		Color compColor = Color.BLUE;
		
		List<String> lines = new ArrayList<>();
		
		lines.add("# Logic Tree Site Hazard Curves");
		lines.add("");
		int tocIndex = lines.size();
		String topLink = "_[(top)](#table-of-contents)_";
		
		for (Site site : sites) {
			lines.add("## "+site.getName());
			lines.add(topLink); lines.add("");
			
			String sitePrefix = site.getName().replaceAll("\\W+", "_");
			
			Map<Double, ZipEntry> perEntries = loadSiteCSVs(sitePrefix, zip);
			Preconditions.checkState(!perEntries.isEmpty());
			
			List<Double> periods = new ArrayList<>(perEntries.keySet());
			Collections.sort(periods);
			
			Map<Double, ZipEntry> compPerEntries = null;
			if (compZip != null) {
				compPerEntries = loadSiteCSVs(sitePrefix, compZip);
				if (compPerEntries.isEmpty())
					compPerEntries = null;
			}
			
			TableBuilder table = MarkdownUtils.tableBuilder();
			
			for (double period : periods) {
				String perLabel, perPrefix, perUnits;
				if (period == -1d) {
					perLabel = "PGV";
					perPrefix = "pgv";
					perUnits = "cm/s";
				} else if (period == 0d) {
					perLabel = "PGA";
					perPrefix = "pgA";
					perUnits = "g";
				} else {
					perLabel = (float)period+"s SA";
					perPrefix = "sa_"+(float)period;
					perUnits = "g";
				}
				
				CSVFile<String> curvesCSV = CSVFile.readStream(zip.getInputStream(perEntries.get(period)), true);
				List<DiscretizedFunc> curves = new ArrayList<>();
				List<Double> weights = new ArrayList<>();
				ArbDiscrEmpiricalDistFunc[] dists = loadCurves(curvesCSV, tree, curves, weights);
				
				ZipEntry compEntry = null;
				if (compPerEntries != null)
					compEntry = compPerEntries.get(period);
				
				List<DiscretizedFunc> compCurves = null;
				List<Double> compWeights = null;
				ArbDiscrEmpiricalDistFunc[] compDists = null;
				DiscretizedFunc compMeanCurve = null;
				if (compEntry != null) {
					CSVFile<String> compCurvesCSV = CSVFile.readStream(compZip.getInputStream(compPerEntries.get(period)), true);
					compCurves = new ArrayList<>();
					compWeights = new ArrayList<>();
					compDists = loadCurves(compCurvesCSV, compTree, compCurves, compWeights);
					compMeanCurve = calcMeanCurve(compDists, xVals(compCurves.get(0)));
				}
				
				if (compMeanCurve == null)
					table.addLine(MarkdownUtils.boldCentered(perLabel));
				else
					table.addLine(MarkdownUtils.boldCentered("Primary "+perLabel), MarkdownUtils.boldCentered("Comparison "+perLabel));
				
				String prefix = sitePrefix+"_"+perPrefix;
				
				table.initNewLine();
				File plot = curveDistPlot(resourcesDir, prefix+"_curve_dists", site.getName(), perLabel, perUnits, dists,
						xVals(curves.get(0)), primaryColor, compMeanCurve, compColor);
				table.addColumn("![Curve Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
				if (compDists != null) {
					plot = curveDistPlot(resourcesDir, prefix+"_comp_curve_dists", site.getName(), perLabel, perUnits, compDists,
							xVals(compCurves.get(0)), compColor, calcMeanCurve(dists, xVals(curves.get(0))), primaryColor);
					table.addColumn("![Curve Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
				}
				table.finalizeLine();
			}
			lines.addAll(table.build());
			lines.add("");
		}
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 4));
		lines.add(tocIndex, "## Table Of Contents");

		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}
	
	private static Map<Double, ZipEntry> loadSiteCSVs(String sitePrefix, ZipFile zip) {
		Enumeration<? extends ZipEntry> entries = zip.entries();
		Map<Double, ZipEntry> perEntires = new HashMap<>();
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			String name = entry.getName();
			if (name.startsWith(sitePrefix)) {
				String nameLeft = name.substring(sitePrefix.length());
				double period;
				if (nameLeft.equals("_pgv.csv")) {
					period = -1;
				} else if (nameLeft.equals("_pga.csv")) {
					period = 0d;
				} else if (nameLeft.startsWith("_sa_") && nameLeft.endsWith(".csv")) {
					String perStr = nameLeft.substring(4, nameLeft.length()-4);
					period = Double.parseDouble(perStr);
				} else {
					System.err.println("Skipping unexpected file that we couldn't parse for a period: "+name);
					continue;
				}
				perEntires.put(period, entry);
			}
		}
		return perEntires;
	}
	
	private static ArbDiscrEmpiricalDistFunc[] loadCurves(CSVFile<String> curvesCSV, LogicTree<?> tree,
			List<DiscretizedFunc> curves, List<Double> weights) {
		int startCol = 3+tree.getLevels().size();
		double[] xVals = new double[curvesCSV.getNumCols()-startCol];
		ArbDiscrEmpiricalDistFunc[] dists = new ArbDiscrEmpiricalDistFunc[xVals.length];
		for (int i=0; i<xVals.length; i++) {
			xVals[i] = curvesCSV.getDouble(0, startCol+i);
			dists[i] = new ArbDiscrEmpiricalDistFunc();
		}
		
		for (int row=1; row<curvesCSV.getNumRows(); row++) {
			double weight = curvesCSV.getDouble(row, 2);
			double[] yVals = new double[xVals.length];
			for (int i=0; i<yVals.length; i++) {
				yVals[i] = curvesCSV.getDouble(row, startCol+i);
				dists[i].set(yVals[i], weight);
			}
			LightFixedXFunc curve = new LightFixedXFunc(xVals, yVals);
			curves.add(curve);
			weights.add(weight);
		}
		
		return dists;
	}
	
	private static File curveDistPlot(File resourcesDir, String prefix, String siteName, String perLabel, String units,
			ArbDiscrEmpiricalDistFunc[] curveDists, double[] xVals, Color color,
			DiscretizedFunc compMeanCurve, Color compColor) throws IOException {
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		DiscretizedFunc meanCurve = calcMeanCurve(curveDists, xVals);
		DiscretizedFunc medianCurve = calcFractileCurve(curveDists, xVals, 0.5d);
		
		UncertainBoundedDiscretizedFunc minMax = new UncertainArbDiscFunc(medianCurve,
				calcFractileCurve(curveDists, xVals, 0d), calcFractileCurve(curveDists, xVals, 1d));
		UncertainBoundedDiscretizedFunc bounds95 = new UncertainArbDiscFunc(medianCurve,
				calcFractileCurve(curveDists, xVals, 0.025d), calcFractileCurve(curveDists, xVals, 0.975d));
		UncertainBoundedDiscretizedFunc bounds68 = new UncertainArbDiscFunc(medianCurve,
				calcFractileCurve(curveDists, xVals, 0.16d), calcFractileCurve(curveDists, xVals, 0.84d));
		
		Color transColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 60);
		
		meanCurve.setName("Mean");
		funcs.add(meanCurve);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, color));
		
		if (compMeanCurve != null) {
			compMeanCurve.setName("Comparison Mean");
			funcs.add(compMeanCurve);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, compColor));
		}
		
		medianCurve.setName("Median");
		funcs.add(medianCurve);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 3f, color));
		
		minMax.setName("p[0,2.5,16,84,97.5,100]");
		funcs.add(minMax);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, transColor));
		funcs.add(bounds95);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, transColor));
		funcs.add(bounds68);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, transColor));
		
		Range yRange = new Range(1e-6, 1e0);
		double minX = Double.POSITIVE_INFINITY;
		double maxX = 0d;
		for (DiscretizedFunc func : funcs) {
			DiscretizedFunc[] iterFuncs;
			if (func instanceof UncertainBoundedDiscretizedFunc)
				iterFuncs = new DiscretizedFunc[] { func, ((UncertainBoundedDiscretizedFunc)func).getLower(),
						((UncertainBoundedDiscretizedFunc)func).getUpper()};
			else
				iterFuncs = new DiscretizedFunc[] { func };
			for (DiscretizedFunc iterFunc : iterFuncs) {
				for (Point2D pt : iterFunc) {
					if (pt.getX() > 1e-2 && (float)pt.getY() < (float)yRange.getUpperBound()
							&& (float)pt.getY() > (float)yRange.getLowerBound()) {
						minX = Math.min(minX, pt.getX());
						maxX = Math.max(maxX, pt.getX());
					}
				}
			}
		}
		minX = Math.pow(10, Math.floor(Math.log10(minX)));
		maxX = Math.pow(10, Math.ceil(Math.log10(maxX)));
		Range xRange = new Range(minX, maxX);
		
		PlotSpec spec = new PlotSpec(funcs, chars, siteName, perLabel+" ("+units+")", "Annual Probability of Exceedance");
		spec.setLegendInset(true);
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		gp.drawGraphPanel(spec, true, true, xRange, yRange);
		
		PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, 800, true, true, false);
		
		return new File(resourcesDir, prefix+".png");
	}
	
	private static double[] xVals(DiscretizedFunc curve) {
		double[] ret = new double[curve.size()];
		for (int i=0; i<ret.length; i++)
			ret[i] = curve.getX(i);
		return ret;
	}
	
	private static DiscretizedFunc calcMeanCurve(ArbDiscrEmpiricalDistFunc[] dists, double[] xVals) {
		Preconditions.checkState(dists.length == xVals.length);
		double[] yVals = new double[xVals.length];
		for (int i=0; i<dists.length; i++)
			yVals[i] = dists[i].getMean();
		return new LightFixedXFunc(xVals, yVals);
	}
	
	private static DiscretizedFunc calcFractileCurve(ArbDiscrEmpiricalDistFunc[] dists, double[] xVals, double fractile) {
		Preconditions.checkState(dists.length == xVals.length);
		double[] yVals = new double[xVals.length];
		for (int i=0; i<dists.length; i++)
			yVals[i] = dists[i].getInterpolatedFractile(fractile);
		return new LightFixedXFunc(xVals, yVals);
	}

}
