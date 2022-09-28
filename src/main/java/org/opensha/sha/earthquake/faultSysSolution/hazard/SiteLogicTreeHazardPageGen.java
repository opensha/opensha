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
import org.opensha.commons.data.NamedComparator;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.uncertainty.UncertainArbDiscFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedDiscretizedFunc;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_SiteLogicTreeHazardCurveCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class SiteLogicTreeHazardPageGen {

	@SuppressWarnings("unused")
	public static void main(String[] args) throws ZipException, IOException {
		System.setProperty("java.awt.headless", "true");
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
		
		sites.sort(new NamedComparator());
		
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
			
			System.out.println("Site: "+site.getName());
			
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
			
			for (double period : periods) {
				String perLabel, perPrefix, perUnits;
				if (period == -1d) {
					perLabel = "PGV";
					perPrefix = "pgv";
					perUnits = "cm/s";
				} else if (period == 0d) {
					perLabel = "PGA";
					perPrefix = "pga";
					perUnits = "g";
				} else {
					perLabel = (float)period+"s SA";
					perPrefix = "sa_"+(float)period;
					perUnits = "g";
				}
				
				if (periods.size() > 1) {
					lines.add("### "+site.getName()+", "+perLabel);
					lines.add(topLink); lines.add("");
				}
				
				System.out.println("Period: "+perLabel);
				
				CSVFile<String> curvesCSV = CSVFile.readStream(zip.getInputStream(perEntries.get(period)), true);
				List<DiscretizedFunc> curves = new ArrayList<>();
				List<Double> weights = new ArrayList<>();
				List<LogicTreeBranch<?>> branches = new ArrayList<>();
				ArbDiscrEmpiricalDistFunc[] dists = loadCurves(curvesCSV, tree, curves, branches, weights);
				
				ZipEntry compEntry = null;
				if (compPerEntries != null)
					compEntry = compPerEntries.get(period);
				
				List<DiscretizedFunc> compCurves = null;
				List<Double> compWeights = null;
				List<LogicTreeBranch<?>> compBranches = new ArrayList<>();
				ArbDiscrEmpiricalDistFunc[] compDists = null;
				DiscretizedFunc compMeanCurve = null;
				if (compEntry != null) {
					CSVFile<String> compCurvesCSV = CSVFile.readStream(compZip.getInputStream(compPerEntries.get(period)), true);
					compCurves = new ArrayList<>();
					compWeights = new ArrayList<>();
					compDists = loadCurves(compCurvesCSV, compTree, compCurves, compBranches, compWeights);
					compMeanCurve = calcMeanCurve(compDists, xVals(compCurves.get(0)));
				}
				
				String prefix = sitePrefix+"_"+perPrefix;
				
				File plot = curveDistPlot(resourcesDir, prefix+"_curve_dists", site.getName(), perLabel, perUnits, dists,
						xVals(curves.get(0)), primaryColor, compMeanCurve, compColor);
				
				if (compDists == null) {
					lines.add("![Curve Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
				} else {
					TableBuilder table = MarkdownUtils.tableBuilder();
					
					table.addLine("Primary", "Comparison");
					
					table.initNewLine();
					table.addColumn("![Curve Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
					plot = curveDistPlot(resourcesDir, prefix+"_comp_curve_dists", site.getName(), perLabel, perUnits, compDists,
							xVals(compCurves.get(0)), compColor, calcMeanCurve(dists, xVals(curves.get(0))), primaryColor);
					table.addColumn("![Curve Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
					table.finalizeLine();
					
					lines.addAll(table.build());
				}
				lines.add("");
				
				// now add return periods
				for (ReturnPeriods rp : ReturnPeriods.values()) {
					if (periods.size() > 1)
						lines.add("#### "+site.getName()+", "+perLabel+", "+rp.label);
					else
						lines.add("### "+site.getName()+", "+perLabel+", "+rp.label);
					lines.add(topLink); lines.add("");
					
					List<Double> branchVals = new ArrayList<>();
					for (DiscretizedFunc curve : curves)
						branchVals.add(curveVal(curve, rp));
					
					List<Double> compBranchVals = null;
					HistogramFunction refHist;
					if (compCurves != null) {
						compBranchVals = new ArrayList<>();
						for (DiscretizedFunc curve : compCurves)
							compBranchVals.add(curveVal(curve, rp));
						List<Double> allVals = new ArrayList<>(curves.size()+compCurves.size());
						allVals.addAll(branchVals);
						allVals.addAll(compBranchVals);
						refHist = initHist(allVals);
					} else {
						refHist = initHist(branchVals);
					}
					
					HistogramFunction hist = buildHist(branches, weights, branchVals, null, refHist);
					double mean = mean(branches, weights, branchVals, null);
					
					String label = perLabel+", "+rp.label+" ("+perUnits+")";
					
					String valPrefix = prefix+"_"+rp.name();
					
					Double compMean = null;
					if (compDists == null) {
						plot = valDistPlot(resourcesDir, valPrefix, site.getName(), label, hist, mean, primaryColor, null, null);
						lines.add("![Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
					} else {
						TableBuilder table = MarkdownUtils.tableBuilder();
						
						table.addLine("Primary", "Comparison");
						
						table.initNewLine();
						compMean = mean(compBranches, compWeights, compBranchVals, null);
						plot = valDistPlot(resourcesDir, valPrefix, site.getName(), label, hist, mean, primaryColor, compMean, compColor);
						table.addColumn("![Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
						plot = valDistPlot(resourcesDir, valPrefix+"_comp", site.getName(), label, hist, compMean, compColor, mean, primaryColor);
						table.addColumn("![Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
						table.finalizeLine();
						
						lines.addAll(table.build());
					}
					lines.add("");
					
					// add logic tree comparisons
					List<String> ltNames = new ArrayList<>();
					List<File> ltPlots = new ArrayList<>();
					for (int l=0; l<tree.getLevels().size(); l++) {
						List<LogicTreeNode> nodes = new ArrayList<>();
						List<HistogramFunction> nodeHists = new ArrayList<>();
						List<Double> nodeMeans = new ArrayList<>();
						LogicTreeLevel<?> level = tree.getLevels().get(l);
						for (LogicTreeNode node : level.getNodes()) {
							HistogramFunction nodeHist = buildHist(branches, weights, branchVals, node, refHist);
							if (nodeHist != null) {
								nodes.add(node);
								nodeHists.add(nodeHist);
								nodeMeans.add(mean(branches, weights, branchVals, node));
							}
						}
						if (nodes.size() > 1) {
							// we have multiple at this level
							String ltPrefix = valPrefix+"_"+level.getShortName().replaceAll("\\W+", "_");
							ltNames.add(level.getName());
							ltPlots.add(valDistPlot(resourcesDir, ltPrefix, site.getName(), label, hist, mean,
									Color.BLACK, null, null, nodes, nodeHists, nodeMeans));
						}
					}
					
					TableBuilder table = MarkdownUtils.tableBuilder();
					table.initNewLine();
					for (String ltName : ltNames)
						table.addColumn(MarkdownUtils.boldCentered(ltName));
					table.finalizeLine();
					table.initNewLine();
					for (File ltPlot : ltPlots)
						table.addColumn("![Dist]("+resourcesDir.getName()+"/"+ltPlot.getName()+")");
					table.finalizeLine();
					table = table.wrap(2, 0);
					String ltLabel = site.getName()+", "+perLabel+", "+rp.label+" Logic Tree Histograms";
					if (periods.size() > 1)
						lines.add("##### "+ltLabel);
					else
						lines.add("#### "+ltLabel);
					lines.add(topLink); lines.add("");
					lines.addAll(table.build());
					lines.add("");
				}
			}
		}
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, sites.size() > 10 ? 2 : 3));
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
			List<DiscretizedFunc> curves, List<LogicTreeBranch<?>> branches, List<Double> weights) {
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
		for (LogicTreeLevel<?> level : tree.getLevels())
			levels.add(level);
		int startCol = 3+levels.size();
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
			if (branches != null) {
				List<LogicTreeNode> nodes = new ArrayList<>();
				for (int i=0; i<levels.size(); i++) {
					String nodeName = curvesCSV.get(row, i+3);
					LogicTreeLevel<?> level = levels.get(i);
					LogicTreeNode match = null;
					for (LogicTreeNode node : level.getNodes()) {
						if (nodeName.equals(node.getShortName())) {
							match = node;
							break;
						}
					}
					nodes.add(match);
				}
				branches.add(new LogicTreeBranch<>(levels, nodes));
			}
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
	
	private static HistogramFunction initHist(List<Double> branchVals) {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (double val : branchVals) {
			min = Math.min(val, min);
			max = Math.max(val, max);
		}
		if (min == max)
			max = min+0.1;
		double diff = max - min;
		double delta;
		if (diff > 100)
			delta = 10;
		else if (diff > 50)
			delta = 5d;
		else if (diff > 10)
			delta = 1;
		else if (diff > 5)
			delta = 0.5;
		else if (diff > 0.5)
			delta = 0.05;
		else if (diff > 0.1)
			delta = 0.01;
		else
			delta = 0.005;
		return HistogramFunction.getEncompassingHistogram(min, max, delta);
	}
	
	private static HistogramFunction buildHist(List<LogicTreeBranch<?>> branches, List<Double> weights,
			List<Double> branchVals, LogicTreeNode node, HistogramFunction refHist) {
		HistogramFunction hist = new HistogramFunction(refHist.getMinX(), refHist.getMaxX(), refHist.size());
		double totWeight = 0d;
		int count = 0;
		for (int i=0; i<branches.size(); i++) {
			LogicTreeBranch<?> branch = branches.get(i);
			double weight = weights.get(i);
			totWeight += weight;
			if (node == null || branch.hasValue(node)) {
				count++;
				hist.add(hist.getClosestXIndex(branchVals.get(i)), weight);
			}
		}
		if (count == 0)
			return null;
		hist.scale(1d/totWeight);
		return hist;
	}
	
	private static double mean(List<LogicTreeBranch<?>> branches, List<Double> weights,
			List<Double> branchVals, LogicTreeNode node) {
		double sumWeight = 0d;
		double ret = 0d;
		for (int i=0; i<branches.size(); i++) {
			LogicTreeBranch<?> branch = branches.get(i);
			double weight = weights.get(i);
			if (node == null || branch.hasValue(node)) {
				sumWeight += weight;
				ret += branchVals.get(i)*weight;
			}
		}
		return ret/sumWeight;
	}
	
	private static double curveVal(DiscretizedFunc curve, ReturnPeriods rp) {
		double curveLevel = rp.oneYearProb;
		// curveLevel is a probability, return the IML at that probability
		if (curveLevel > curve.getMaxY())
			return 0d;
		else if (curveLevel < curve.getMinY())
			// saturated
			return curve.getMaxX();
		else
			return curve.getFirstInterpolatedX_inLogXLogYDomain(curveLevel);
	}
	
	private static File valDistPlot(File resourcesDir, String prefix, String siteName, String label,
			HistogramFunction hist, double mean, Color color, Double compMean, Color compColor) throws IOException {
		return valDistPlot(resourcesDir, prefix, siteName, label, hist, mean, color, compMean, compColor, null, null, null);
	}
	
	private static File valDistPlot(File resourcesDir, String prefix, String siteName, String label,
			HistogramFunction hist, double mean, Color color, Double compMean, Color compColor,
			List<LogicTreeNode> nodes, List<HistogramFunction> nodeHists, List<Double> nodeMeans) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		funcs.add(hist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.GRAY));
		
		double maxY = hist.getMaxY()*1.2;
		if (nodeHists != null) {
			HistogramFunction sumHist = null;
			List<HistogramFunction> usedNodeHists = new ArrayList<>();
			List<Double> usedNodeMeans = new ArrayList<>();
			for (int i=0; i<nodes.size(); i++) {
				HistogramFunction nodeHist = nodeHists.get(i);
				if (nodeHist == null)
					continue;
				Preconditions.checkState(nodeHist.size() == hist.size());
				HistogramFunction nodeSumHist;
				if (sumHist == null) {
					nodeSumHist = nodeHist;
				} else {
					nodeSumHist = new HistogramFunction(hist.getMinX(), hist.getMaxX(), hist.size());
					for (int j=0; j<hist.size(); j++)
						nodeSumHist.set(j, sumHist.getY(j)+nodeHist.getY(j));
				}
				
				nodeSumHist.setName(nodes.get(i).getShortName());
				usedNodeHists.add(nodeSumHist);
				usedNodeMeans.add(nodeMeans.get(i));
				
				sumHist = nodeSumHist;
			}
			if (usedNodeHists.size() > 1) {
				maxY *= 1.1;
				CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, usedNodeHists.size()-1d);
				List<Color> colors = new ArrayList<>();
				for (int i=0; i<usedNodeHists.size(); i++)
					colors.add(cpt.getColor((float)i));
				// first forwards to get the legend in the right order
				for (int i=0; i<usedNodeHists.size(); i++) {
					funcs.add(usedNodeHists.get(i));
					chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, colors.get(i)));
				}
				// now add mean lines (these will end up behind and only show up on top)
				for (int i=0; i<usedNodeHists.size(); i++) {
					funcs.add(vertLine(usedNodeMeans.get(i), 0d, maxY, null));
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, colors.get(i)));
				}
				// now backwards so we can actually see them
				for (int i=usedNodeHists.size(); --i>=0;) {
					EvenlyDiscretizedFunc copy = usedNodeHists.get(i).deepClone();
					copy.setName(null);
					funcs.add(copy);
					chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, colors.get(i)));
				}
			}
		}
		
		if (compMean != null) {
			funcs.add(vertLine(compMean, 0d, maxY, "Comparison Mean"));
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, compColor));
		}
		
		funcs.add(vertLine(mean, 0d, maxY, "Mean"));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, color));
		
		Range xRange = new Range(hist.getMinX()-0.5*hist.getDelta(), hist.getMaxX()+0.5*hist.getDelta());
		Range yRange = new Range(0d, maxY);
		
		PlotSpec spec = new PlotSpec(funcs, chars, siteName, label, "Fraction");
		spec.setLegendInset(true);
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		
		PlotUtils.writePlots(resourcesDir, prefix, gp, 800, 700, true, true, false);
		
		return new File(resourcesDir, prefix+".png");
	}
	
	private static XY_DataSet vertLine(double x, double y0, double y1, String name) {
		DefaultXY_DataSet xy = new DefaultXY_DataSet();
		xy.set(x, y0);
		xy.set(x, y1);
		xy.setName(name);
		return xy;
	}

}
