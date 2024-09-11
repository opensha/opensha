package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.awt.Color;
import java.awt.Font;
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
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.NamedComparator;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
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
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.ExecutorUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_SiteLogicTreeHazardCurveCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.earthquake.faultSysSolution.util.SolSiteHazardCalc;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

public class SiteLogicTreeHazardPageGen {

	@SuppressWarnings("unused")
	public static void main(String[] args) throws ZipException, IOException {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			args = new String[] {
					"/home/kevin/OpenSHA/UCERF4/batch_inversions/"
					+ "2022_09_16-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR/results_hazard_sites.zip",
					"/tmp/site_hazard"
			};
		}
		System.setProperty("java.awt.headless", "true");
		
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, SiteLogicTreeHazardPageGen.class);
		args = cmd.getArgs();
		
		if (args.length < 2 || args.length > 3) {
			System.err.println("USAGE: <zip-file> <output-dir> [<comp-zip-file>]");
			System.exit(1);
		}
		File zipFile = new File(args[0]);
		File outputDir = new File(args[1]);
		File compZipFile = null;
		if (args.length > 2)
			compZipFile = new File(args[2]);
		
		int threads = FaultSysTools.getNumThreads(cmd);
		ExecutorService exec = ExecutorUtils.newBlockingThreadPool(threads);
		
		int downsample = -1;
		if (cmd.hasOption("downsample")) {
			downsample = Integer.parseInt(cmd.getOptionValue("downsample"));
			System.out.println("Will downsample to at most "+downsample+" curves when plotting individual curves");
		}
		
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		ZipFile zip = new ZipFile(zipFile);
		ZipFile compZip = compZipFile == null ? null : new ZipFile(compZipFile);
		
		CSVFile<String> sitesCSV = CSVFile.readStream(zip.getInputStream(
				zip.getEntry(MPJ_SiteLogicTreeHazardCurveCalc.SITES_CSV_FILE_NAME)), true);
		
		LogicTree<?> tree = LogicTree.read(new InputStreamReader(zip.getInputStream(zip.getEntry("logic_tree.json"))));
		
		List<Site> sites = MPJ_SiteLogicTreeHazardCurveCalc.parseSitesCSV(sitesCSV, null);
		
		ReturnPeriods[] rps = SolHazardMapCalc.MAP_RPS; 
		
		sites.sort(new NamedComparator());
		
		Color primaryColor = Color.RED;
		Color compColor = Color.BLUE;
		
		// colors for comparison hists
		Color compHistColor = new Color(180, 180, 220); // light gray-blue
		Color primaryHistColorOnCompPlot = new Color(220, 180, 180); // light gray-red
		
		List<String> lines = new ArrayList<>();
		
		lines.add("# Logic Tree Site Hazard Curves");
		lines.add("");
		int tocIndex = lines.size();
		String topLink = "_[(top)](#table-of-contents)_";
		
		boolean levelNameOverlap = false;
		List<String> levelPrefixes = new ArrayList<>();
		for (LogicTreeLevel<?> level : tree.getLevels()) {
			String ltPrefix = level.getShortName().replaceAll("\\W+", "_");
			levelNameOverlap |= levelPrefixes.contains(ltPrefix);
			levelPrefixes.add(ltPrefix);
		}
		if (levelNameOverlap)
			for (int i=0; i<levelPrefixes.size(); i++)
				levelPrefixes.set(i, i+"_"+levelPrefixes.get(i));
		
		for (Site site : sites) {
			List<String> siteLines = new ArrayList<>();
			siteLines.add("# "+site.getName()+" Logic Tree Hazard Curves");
			siteLines.add(topLink); siteLines.add("");
			
			siteLines.add("[Return to Full Site List](README.md)");
			siteLines.add("");
			int siteTOCIndex = siteLines.size();
			
			lines.add("## "+site.getName());
			lines.add(topLink); lines.add("");
			
			System.out.println("Site: "+site.getName());
			
			String sitePrefix = site.getName().replaceAll("\\W+", "_");
			
			lines.add("Summary figures across all branches are shown here. _[Click here for detailed branch-specific hazard "
					+ "curves for "+site.getName()+"]("+sitePrefix+".md)_");
			lines.add("");
			
			Map<Double, ZipEntry> perEntries = locateSiteCurveCSVs(sitePrefix, zip);
			Preconditions.checkState(!perEntries.isEmpty());
			
			List<Double> periods = new ArrayList<>(perEntries.keySet());
			Collections.sort(periods);
			
			Map<Double, ZipEntry> compPerEntries = null;
			if (compZip != null) {
				compPerEntries = locateSiteCurveCSVs(sitePrefix, compZip);
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
					siteLines.add("## "+site.getName()+", "+perLabel);
					siteLines.add(topLink); siteLines.add("");
					lines.add("### "+site.getName()+", "+perLabel);
					lines.add(topLink); lines.add("");
				}
				lines.add("");
				siteLines.add("");
				
				System.out.println("Period: "+perLabel);
				
				List<Future<?>> plotFutures = new ArrayList<>();
				
				System.out.println("\tReading curves");
				CSVFile<String> curvesCSV = CSVFile.readStream(zip.getInputStream(perEntries.get(period)), true);
				List<DiscretizedFunc> curves = new ArrayList<>();
				List<Double> weights = new ArrayList<>();
				List<LogicTreeBranch<?>> branches = new ArrayList<>();
				System.out.println("\tBuilding curve dists");
				ArbDiscrEmpiricalDistFunc[] dists = loadCurvesAndDists(curvesCSV, tree.getLevels(), curves, branches, weights, true);
				
				DiscretizedFunc meanCurve = calcMeanCurve(dists, xVals(curves.get(0)));
				
				ZipEntry compEntry = null;
				if (compPerEntries != null)
					compEntry = compPerEntries.get(period);
				
				List<DiscretizedFunc> compCurves = null;
				List<Double> compWeights = null;
				ArbDiscrEmpiricalDistFunc[] compDists = null;
				DiscretizedFunc compMeanCurve = null;
				if (compEntry != null) {
					System.out.println("\tReading comparison curves");
					CSVFile<String> compCurvesCSV = CSVFile.readStream(compZip.getInputStream(compPerEntries.get(period)), true);
					compCurves = new ArrayList<>();
					compWeights = new ArrayList<>();
					System.out.println("\tBuilding comparison curve dists");
					compDists = loadCurvesAndDists(compCurvesCSV, null, compCurves, null, compWeights, true);
					compMeanCurve = compCurves.size() == 1 ? compCurves.get(0) : calcMeanCurve(compDists, xVals(compCurves.get(0)));
				}
				
				String prefix = sitePrefix+"_"+perPrefix;
				
				double[] xVals = xVals(curves.get(0));
				
				System.out.println("\tBuilding plots");
				File plot = curveDistPlot(resourcesDir, prefix+"_curve_dists", site.getName(), perLabel, perUnits, dists,
						xVals, primaryColor, rps, compMeanCurve, compColor, exec, plotFutures);
				
				if (compDists == null || compCurves.size() < 2) {
					lines.add("![Curve Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
					siteLines.add("![Curve Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
				} else {
					// we have a comparison distribution
					TableBuilder table = MarkdownUtils.tableBuilder();
					
					table.addLine("Primary", "Comparison");
					
					table.initNewLine();
					table.addColumn("![Curve Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
					plot = curveDistPlot(resourcesDir, prefix+"_comp_curve_dists", site.getName(), perLabel, perUnits, compDists,
							xVals(compCurves.get(0)), compColor, rps, calcMeanCurve(dists, xVals(compCurves.get(0))), primaryColor,
							exec, plotFutures);
					table.addColumn("![Curve Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
					table.finalizeLine();
					
					lines.addAll(table.build());
					siteLines.addAll(table.build());
				}
				lines.add("");
				siteLines.add("");
				
				// add return period dists
				TableBuilder table = MarkdownUtils.tableBuilder();
				table.initNewLine();
				for (ReturnPeriods rp : rps)
					table.addColumn(rp.label);
				table.finalizeLine().initNewLine();
				List<HistogramFunction> rpHists = new ArrayList<>();
				List<HistogramFunction> rpCompHists = new ArrayList<>();
				List<Double> rpMeans = new ArrayList<>();
				List<Double> rpCompMeans = compCurves == null ? null : new ArrayList<>();
				List<List<Double>> rpBranchValsList = new ArrayList<>();
				List<List<Double>> rpCompBranchValsList = compCurves == null ? null : new ArrayList<>();
				for (ReturnPeriods rp : rps) {
					List<Double> branchVals = new ArrayList<>();
					for (DiscretizedFunc curve : curves)
						branchVals.add(curveVal(curve, rp));
					rpBranchValsList.add(branchVals);
					
					List<Double> compBranchVals = null;
					HistogramFunction refHist;
					Double compMean = null;
					HistogramFunction compHist = null;
					if (compCurves != null) {
						compBranchVals = new ArrayList<>();
						for (DiscretizedFunc curve : compCurves)
							compBranchVals.add(curveVal(curve, rp));
						List<Double> allVals = new ArrayList<>(curves.size()+compCurves.size());
						allVals.addAll(branchVals);
						allVals.addAll(compBranchVals);
						refHist = initHist(allVals);
						rpCompBranchValsList.add(compBranchVals);
//						compMean = mean(compWeights, compBranchVals);
						compMean = curveVal(compMeanCurve, rp);
						rpCompMeans.add(compMean);
						compHist = buildHist(compWeights, compBranchVals, refHist);
						compHist.setName("Comparison Distribution");
						rpCompHists.add(compHist);
					} else {
						refHist = initHist(branchVals);
					}
					
					HistogramFunction hist = buildHist(branches, weights, branchVals, null, refHist);
					hist.setName("Distribution");
					rpHists.add(hist);
//					double mean = mean(branches, weights, branchVals, null);
					double mean = curveVal(meanCurve, rp);
					rpMeans.add(mean);
					
					String label = perLabel+", "+rp.label+" ("+perUnits+")";
					String valPrefix = prefix+"_"+rp.name();
					
					plot = valDistPlot(resourcesDir, valPrefix, site.getName(), label,
							hist, mean, primaryColor, null,
							compHist, compMean, compColor, compHistColor, "Comparison", exec, plotFutures);
					table.addColumn("![Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
				}
				table.finalizeLine();
				if (compCurves != null) {
					if (compCurves.size() > 1) {
						table.initNewLine();
						for (ReturnPeriods rp : rps)
							table.addColumn(MarkdownUtils.boldCentered("Comparison Distribution, "+rp.label));
						table.finalizeLine().initNewLine();
						for (int r=0; r<rps.length; r++) {
							double mean = rpMeans.get(r);
							
							double compMean = rpCompMeans.get(r);
							HistogramFunction compHist = rpCompHists.get(r);
							
							String label = perLabel+", "+rps[r].label+" ("+perUnits+")";
							String valPrefix = prefix+"_"+rps[r].name();
							
							HistogramFunction origHist = rpHists.get(r);
							origHist.setName("Primary Distribution");
							
							plot = valDistPlot(resourcesDir, valPrefix+"_comp", site.getName(), label,
									compHist, compMean, compColor, "Comparison",
									origHist, mean, primaryColor, primaryHistColorOnCompPlot, "Primary", exec, plotFutures);
							table.addColumn("![Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
							
						}
						table.finalizeLine();
					} else {
						for (int r=0; r<rps.length; r++) {
							List<Double> compBranchVals = rpCompBranchValsList.get(r);
							Preconditions.checkState(compBranchVals.size() == 1);
							rpCompMeans.add(compBranchVals.get(0));
						}
					}
				}
				if (periods.size() > 1) {
					lines.add("#### "+site.getName()+", "+perLabel+", Hazard Value Distributions");
					siteLines.add("### "+site.getName()+", "+perLabel+", Hazard Value Distributions");
				} else {
					lines.add("### "+site.getName()+", "+perLabel+", Hazard Value Distributions");
					siteLines.add("## "+site.getName()+", "+perLabel+", Hazard Value Distributions");
				}
				lines.add(topLink); lines.add("");
				lines.add("");
				lines.addAll(table.build());
				siteLines.add(topLink); siteLines.add("");
				siteLines.add("");
				siteLines.addAll(table.build());
				
				int siteLinesLevelTableIndex = siteLines.size();
				List<String> levelLinks = new ArrayList<>();
				List<String> levelAvgPlotLinks = new ArrayList<>();
				
				// value table
				table = MarkdownUtils.tableBuilder();
				
				table.initNewLine();
				if (compDists != null)
					table.addColumn("");
				for (ReturnPeriods rp : rps)
					table.addColumn(rp.label);
				table.finalizeLine();
				
				table.initNewLine();
				if (compDists != null)
					table.addColumn("__Primary__");
				for (double rpVal : rpMeans)
					table.addColumn((float)rpVal+" (g)");
				table.finalizeLine();
				
				if (rpCompMeans != null) {
					table.initNewLine();
					table.addColumn("__Comparison__");
					for (double rpVal : rpCompMeans)
						table.addColumn((float)rpVal+" (g)");
					table.finalizeLine();
				}
				lines.add("");
				lines.addAll(table.build());
				lines.add("");
				siteLines.add("");
				siteLines.addAll(table.build());
				siteLines.add("");
				
				// now logic tree (but only to the site page)
				for (int l=0; l<tree.getLevels().size(); l++) {
					List<LogicTreeNode> nodes = new ArrayList<>();
					List<DiscretizedFunc> nodeMeanCurves = new ArrayList<>();
					List<List<DiscretizedFunc>> nodeCurves = new ArrayList<>();
					
					List<List<HistogramFunction>> nodeHists = new ArrayList<>();
					List<List<Double>> nodeMeans = new ArrayList<>();
					for (int r=0; r<rps.length; r++) {
						nodeHists.add(new ArrayList<>());
						nodeMeans.add(new ArrayList<>());
					}
					
					LogicTreeLevel<?> level = tree.getLevels().get(l);
					for (LogicTreeNode node : level.getNodes()) {
						List<DiscretizedFunc> myNodeCurves = new ArrayList<>();
						DiscretizedFunc nodeMeanCurve = getNodeMeanCurve(branches, weights, curves, node, myNodeCurves);
						if (nodeMeanCurve != null) {
							nodes.add(node);
							nodeCurves.add(myNodeCurves);
							for (int r=0; r<rps.length; r++) {
								List<Double> branchVals = rpBranchValsList.get(r);
								nodeHists.get(r).add(buildHist(branches, weights, branchVals, node, rpHists.get(r)));
//								nodeMeans.get(r).add(mean(branches, weights, branchVals, node));
								nodeMeans.get(r).add(curveVal(nodeMeanCurve, rps[r]));
							}
							nodeMeanCurves.add(nodeMeanCurve);
						}
					}
					if (nodes.size() > 1 && !LogicTreeCurveAverager.shouldSkipLevel(level, nodes.size())) {
						// we have multiple at this level
						String ltPrefix = prefix+"_"+levelPrefixes.get(l);
						String heading = site.getName()+", "+perLabel+", "+level.getName()+" Hazard";
						if (periods.size() > 1)
							siteLines.add("### "+heading);
						else
							siteLines.add("## "+heading);
						siteLines.add(topLink); siteLines.add("");
						System.out.println("\t\tLogic tree level: "+level.getName());
						
						table = MarkdownUtils.tableBuilder();
						
						table.addLine(MarkdownUtils.boldCentered("Individual Curves"),
								MarkdownUtils.boldCentered("Choice Mean Curves"));
						
						table.initNewLine();
						plot = curveBranchPlot(resourcesDir, ltPrefix+"_indv", site.getName(), perLabel, perUnits,
								dists, xVals, Color.BLACK, rps, compDists, Color.GRAY, nodes, nodeCurves, downsample, null, exec, plotFutures);
						table.addColumn("!["+level.getShortName()+" Individual]("+resourcesDir.getName()+"/"+plot.getName()+")");
						plot = curveBranchPlot(resourcesDir, ltPrefix+"_means", site.getName(), perLabel, perUnits,
								dists, xVals, Color.BLACK, rps, compDists, Color.GRAY, nodes, null, downsample, nodeMeanCurves, exec, plotFutures);
						String plotEmbed = "!["+level.getShortName()+" Means]("+resourcesDir.getName()+"/"+plot.getName()+")";
						table.addColumn(plotEmbed);
						
						levelLinks.add("["+level.getName()+"](#"+MarkdownUtils.getAnchorName(heading)+")");
						levelAvgPlotLinks.add(plotEmbed);
						
						table.finalizeLine();
						if (rps.length != 2) {
							siteLines.addAll(table.build());
							siteLines.add("");
							table = MarkdownUtils.tableBuilder();
						}
						table.initNewLine();
						for (int r=0; r<rps.length; r++)
							table.addColumn(MarkdownUtils.boldCentered(rps[r].label));
						table.finalizeLine();
						table.initNewLine();
						for (int r=0; r<rps.length; r++) {
							String rpPrefix = ltPrefix+"_"+rps[r].name();
							String label = perLabel+", "+rps[r].label+" ("+perUnits+")";
							
							Double compVal = rpCompMeans == null ? null : rpCompMeans.get(r);
							
							HistogramFunction hist = rpHists.get(r);
							// clear the name
							hist.setName(null);
							
							plot = valDistPlot(resourcesDir, rpPrefix, site.getName(), label,
									hist, rpMeans.get(r), Color.BLACK, null,
									null, compVal, Color.GRAY, null, "Comparison",
									nodes, nodeHists.get(r), nodeMeans.get(r), exec, plotFutures);
							table.addColumn("![Dist]("+resourcesDir.getName()+"/"+plot.getName()+")");
						}
						table.finalizeLine();
						
						siteLines.addAll(table.build());
						siteLines.add("");
						
						// value table
						table = MarkdownUtils.tableBuilder();
						
						table.initNewLine();
						table.addColumn("");
						for (ReturnPeriods rp : rps)
							table.addColumn(rp.label);
						table.finalizeLine();
						
						table.initNewLine();
						table.addColumn("__Full Model__");
						for (double rpVal : rpMeans)
							table.addColumn((float)rpVal+" (g)");
						table.finalizeLine();
						
						for (int i=0; i<nodes.size(); i++) {
							table.initNewLine();
							table.addColumn("__"+nodes.get(i).getShortName()+"__");
//							for (double rpVal : nodeMeans.get(i))
							for (int r=0; r<rps.length; r++)
								table.addColumn(nodeMeans.get(r).get(i).floatValue()+" (g)");
							table.finalizeLine();
						}
						
						siteLines.add("");
						siteLines.addAll(table.build());
						siteLines.add("");
					}
				}
				
				System.out.println("\tFinishing up "+plotFutures.size()+" plot futures");
				for (Future<?> future : plotFutures) {
					try {
						future.get();
					} catch (InterruptedException | ExecutionException e) {
						exec.shutdown();
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
				
				if (!levelLinks.isEmpty()) {
					TableBuilder summaryTable = MarkdownUtils.tableBuilder();
					summaryTable.initNewLine();
					for (String link : levelLinks)
						summaryTable.addColumn("__"+link+"__");
					summaryTable.finalizeLine();
					summaryTable.initNewLine();
					for (String plotEmbed : levelAvgPlotLinks)
						summaryTable.addColumn(plotEmbed);
					summaryTable.finalizeLine();
					List<String> linesAdd = new ArrayList<>();
					linesAdd.add("");
					if (periods.size() > 1)
						linesAdd.add("### "+site.getName()+", "+perLabel+", Logic Tree Summary");
					else
						linesAdd.add("## "+site.getName()+", Logic Tree Summary");
					linesAdd.add(topLink); linesAdd.add("");
					linesAdd.addAll(summaryTable.wrap(3, 0).build());
					linesAdd.add("");
					siteLines.addAll(siteLinesLevelTableIndex, linesAdd);
				}
			}
			
			// add TOC
			siteLines.addAll(siteTOCIndex, MarkdownUtils.buildTOC(siteLines, 2, 3));
			siteLines.add(siteTOCIndex, "## Table Of Contents");

			// write markdown
			MarkdownUtils.writeReadmeAndHTML(siteLines, outputDir, sitePrefix);
		}
		
		exec.shutdown();
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, sites.size() > 10 ? 2 : 3));
		lines.add(tocIndex, "## Table Of Contents");

		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}
	
	public static Options createOptions() {
		Options ops = new Options();
		
		ops.addOption(null, "downsample", true,
				"Maximum number of individual curves to include in plots (will be randomly downsampled to match if more curves exist).");
		ops.addOption(FaultSysTools.threadsOption());
		
		return ops;
	}
	
	public static Map<Double, ZipEntry> locateSiteCurveCSVs(String sitePrefix, ZipFile zip) {
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
	
	public static List<DiscretizedFunc> loadCurves(CSVFile<String> curvesCSV, LogicTree<?> tree) {
		List<DiscretizedFunc> curves = new ArrayList<>(tree.size());
		List<LogicTreeBranch<?>> branches = new ArrayList<>(tree.size());
		List<Double> weights = new ArrayList<>(tree.size());
		loadCurvesAndDists(curvesCSV, tree.getLevels(), curves, branches, weights, false);
		Preconditions.checkState(curves.size() == branches.size());
		if (curves.size() != tree.size())
			System.out.println("WARNING: we have "+curves.size()+" curves but the passed in tree has "
					+tree.size()+" breanches. It's likely resampled and we will attempt to match.");
		List<DiscretizedFunc> reordered = null;
		Map<LogicTreeBranch<?>, Integer> treeBranchIndexes = null;
		for (int i=0; i<curves.size(); i++) {
			LogicTreeBranch<?> treeBranch = i < tree.size() ? tree.getBranch(i) : null;
			LogicTreeBranch<?> curveBranch = branches.get(i);
			if (reordered != null || treeBranch == null || !treeBranch.equals(curveBranch)) {
				// out of order
				if (reordered == null) {
					// first time
					treeBranchIndexes = new HashMap<>(tree.size());
					for (int index=0; index<tree.size(); index++)
						treeBranchIndexes.put(tree.getBranch(index), index);
					Preconditions.checkState(treeBranchIndexes.size() == tree.size());
					reordered = new ArrayList<>(tree.size());
					// fill in any that we already processed, and nulls after
					for (int index=0; index<tree.size(); index++) {
						if (index < i)
							reordered.add(curves.get(index));
						else
							reordered.add(null);
					}
					System.out.println("Had to reorder hazard curves to match passed in logic tree; first mismatch i="
							+i+" -> "+treeBranchIndexes.get(curveBranch)
							+"\n\ttreeBranch= "+treeBranch+"\n\tcurveBranch="+curveBranch);
				}
				Integer index = treeBranchIndexes.get(curveBranch);
				if (index == null)
					// this branch doesn't exist in the passed in tree
					continue;
				Preconditions.checkState(reordered.set(index, curves.get(i)) == null); // this makes sure it was null previously
			}
		}
		if (reordered != null) {
			// now see if there are any holes (will be the case if the passed in tree has duplicates)
			int holes = 00;
			for (int i=0; i<reordered.size(); i++) {
				if (reordered.get(i) == null) {
					holes++;
					LogicTreeBranch<?> treeBranch = tree.getBranch(i);
					// this branch should have been already filled in earlier
					Integer prevIndex = treeBranchIndexes.get(treeBranch);
					Preconditions.checkState(prevIndex != null && prevIndex < i, "No mappings found for branch %s", treeBranch);
					reordered.set(i, reordered.get(prevIndex));
				}
			}
			if (holes > 0)
				System.out.println("Filled in "+holes+" holes (i.e., input tree has duplicates)");
			return reordered;
		}
		return curves;
	}
	
	private static ArbDiscrEmpiricalDistFunc[] loadCurvesAndDists(CSVFile<String> curvesCSV,
			List<? extends LogicTreeLevel<? extends LogicTreeNode>> levels,
			List<DiscretizedFunc> curves, List<LogicTreeBranch<?>> branches, List<Double> weights, boolean buildDists) {
		List<LogicTreeLevel<? extends LogicTreeNode>> levelsCopy;
		int startCol;
		if (levels == null) {
			levelsCopy = null;
			List<String> header = curvesCSV.getLine(0);
			startCol = -1;
			for (int i=0; i<header.size(); i++) {
				try {
					Double.parseDouble(header.get(i));
					// valud number, this is it
					startCol = i;
					break;
				} catch (NumberFormatException e) {}
			}
			Preconditions.checkState(startCol > 2);
		} else {
			levelsCopy = new ArrayList<>(levels);
			startCol = 3+levels.size();
		}
		double[] xVals = new double[curvesCSV.getNumCols()-startCol];
//		ArbDiscrEmpiricalDistFunc[] dists = new ArbDiscrEmpiricalDistFunc[xVals.length];
		List<List<Point2D>> distPoints = null;
		if (buildDists) {
			distPoints = new ArrayList<>();
		}
		for (int i=0; i<xVals.length; i++) {
			xVals[i] = curvesCSV.getDouble(0, startCol+i);
//			dists[i] = new ArbDiscrEmpiricalDistFunc();
			if (buildDists)
				distPoints.add(new ArrayList<>(curvesCSV.getNumRows()-1));
		}
		
		for (int row=1; row<curvesCSV.getNumRows(); row++) {
			double weight = curvesCSV.getDouble(row, 2);
			double[] yVals = new double[xVals.length];
			for (int i=0; i<yVals.length; i++) {
				yVals[i] = curvesCSV.getDouble(row, startCol+i);
//				dists[i].set(yVals[i], weight);
				if (buildDists)
					distPoints.get(i).add(new Point2D.Double(yVals[i], weight));
			}
			LightFixedXFunc curve = new LightFixedXFunc(xVals, yVals);
			curves.add(curve);
			weights.add(weight);
			if (branches != null) {
				Preconditions.checkState(levelsCopy != null);
				List<LogicTreeNode> nodes = new ArrayList<>();
				for (int i=0; i<levelsCopy.size(); i++) {
					String nodeName = curvesCSV.get(row, i+3);
					LogicTreeLevel<?> level = levelsCopy.get(i);
					LogicTreeNode match = null;
					for (LogicTreeNode node : level.getNodes()) {
						if (nodeName.equals(node.getShortName())) {
							Preconditions.checkState(match == null, "Multiple nodes match name %s for level %s", nodeName, level.getName());
							match = node;
						}
					}
					Preconditions.checkNotNull(match, "No match found for %s in level %s", nodeName, level.getName());
					nodes.add(match);
				}
				branches.add(new LogicTreeBranch<>(levelsCopy, nodes));
			}
		}
		
		if (!buildDists)
			return null;
		ArbDiscrEmpiricalDistFunc[] dists = new ArbDiscrEmpiricalDistFunc[xVals.length];
		for (int i=0; i<dists.length; i++)
			// this will initialize them more efficiently
			dists[i] = new ArbDiscrEmpiricalDistFunc(distPoints.get(i));
		
		return dists;
	}
	
	private static File curveDistPlot(File resourcesDir, String prefix, String siteName, String perLabel, String units,
			ArbDiscrEmpiricalDistFunc[] curveDists, double[] xVals, Color color, ReturnPeriods[] rps,
			DiscretizedFunc compMeanCurve, Color compColor, ExecutorService exec, List<Future<?>> plotFutures)
					throws IOException {
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
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, color.darker()));
		
		if (compMeanCurve != null) {
			compMeanCurve.setName("Comparison Mean");
			funcs.add(compMeanCurve);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, compColor.darker()));
		}
		
		medianCurve.setName("Median");
		funcs.add(medianCurve);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 3f, color.darker()));
		
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
		
		List<XYAnnotation> anns = SolSiteHazardCalc.addRPAnnotations(funcs, chars, xRange, yRange, rps, true);
		
		PlotSpec spec = new PlotSpec(funcs, chars, siteName, perLabel+" ("+units+")", "Annual Probability of Exceedance");
		spec.setLegendInset(true);
		spec.setPlotAnnotations(anns);
		
		plotFutures.add(exec.submit(new Runnable() {
			
			@Override
			public void run() {
				HeadlessGraphPanel gp = PlotUtils.initHeadless();
				
				gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
				gp.drawGraphPanel(spec, true, true, xRange, yRange);
				
				try {
					Stopwatch watch = Stopwatch.createStarted();
					PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, 800, true, true, false);
					watch.stop();
					double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
					if (secs > 10)
						System.out.println("\t\tDONE "+prefix+".png in "+(float)secs+" s");
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		}));
		
		return new File(resourcesDir, prefix+".png");
	}
	
	private static File curveBranchPlot(File resourcesDir, String prefix, String siteName, String perLabel, String units,
			ArbDiscrEmpiricalDistFunc[] curveDists, double[] xVals, Color color, ReturnPeriods[] rps,
			ArbDiscrEmpiricalDistFunc[] compCurveDists, Color compColor, List<LogicTreeNode> nodes,
			List<List<DiscretizedFunc>> nodeIndvCurves, int downsample, List<DiscretizedFunc> nodeMeanCurves,
			ExecutorService exec, List<Future<?>> plotFutures) throws IOException {
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		DiscretizedFunc meanCurve = calcMeanCurve(curveDists, xVals);
		
		meanCurve.setName("Mean");
		funcs.add(meanCurve);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, color));
		
		if (compCurveDists != null) {
			DiscretizedFunc compMeanCurve = calcMeanCurve(compCurveDists, xVals);
			
			compMeanCurve.setName("Comparison Mean");
			funcs.add(compMeanCurve);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, compColor));
		}
		
		List<Color> nodeColors = getNodeColors(nodes.size());
		
		if (nodeMeanCurves != null) {
			for (int i=0; i<nodes.size(); i++) {
				Color nodeColor = nodeColors.get(i);
				DiscretizedFunc nodeCurve = nodeMeanCurves.get(i);
				nodeCurve.setName(nodes.get(i).getShortName());
				funcs.add(nodeCurve);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, nodeColor));
			}
		}
		
		if (nodeIndvCurves != null) {
			int totNumCurves = 0;
			for (List<DiscretizedFunc> curves : nodeIndvCurves)
				totNumCurves += curves.size();
			int plotNumCurves = totNumCurves;
			if (downsample > 0 && downsample < totNumCurves)
				plotNumCurves = downsample;
			int alpha;
			if (plotNumCurves < 50)
				alpha = 255;
			else if (plotNumCurves < 100)
				alpha = 180;
			else if (plotNumCurves < 500)
				alpha = 100;
			else if (plotNumCurves < 1000)
				alpha = 60;
			else if (plotNumCurves < 5000)
				alpha = 40;
			else if (plotNumCurves < 10000)
				alpha = 20;
			else
				alpha = 10;
			
			List<CurveChar> allCurveChars = new ArrayList<>();
			for (int i=0; i<nodes.size(); i++) {
				
				Color nodeColor = nodeColors.get(i);
				// add a fake one just for the legend, without alpha
				DiscretizedFunc fakeCurve = new ArbitrarilyDiscretizedFunc();
				fakeCurve.set(0d, 0d);
				fakeCurve.set(0d, 1d);
				fakeCurve.setName(nodes.get(i).getShortName());
				funcs.add(fakeCurve);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, nodeColor));
				
				if (alpha != 255)
					nodeColor = new Color(nodeColor.getRed(), nodeColor.getGreen(), nodeColor.getBlue(), alpha);
				PlotCurveCharacterstics pChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, nodeColor);
				// do a full shuffle here so that when we block suffle latter there are fewer near-identical overlaps
				List<DiscretizedFunc> shuffledNodeCurves = new ArrayList<>(nodeIndvCurves.get(i));
				Collections.shuffle(shuffledNodeCurves, new Random(shuffledNodeCurves.size()*1000l));
				
				if (downsample > 0 && downsample < totNumCurves) {
					// downsample
					double keepFract = (double)downsample/(double)totNumCurves;
					int myKept = (int)(keepFract*shuffledNodeCurves.size() + 0.5);
					if (myKept < shuffledNodeCurves.size())
						shuffledNodeCurves = shuffledNodeCurves.subList(0, myKept);
				}
				
				for (DiscretizedFunc nodeCurve : shuffledNodeCurves)
					allCurveChars.add(new CurveChar(nodeCurve, pChar));
			}
			// shuffle them so we're not biased by whichever one is plotted on top
			if (allCurveChars.size() > 2000) {
				// we want to shuffle them in blocks so that there are more datasets of the same color next to each
				// other, which get reused internally in GraphPanel to reduce plot time
				int blockSize = allCurveChars.size() / 1000;
				List<List<CurveChar>> blocks = new ArrayList<>();
				List<CurveChar> curBlock = new ArrayList<>(blockSize);
				for (int i=0; i<allCurveChars.size(); i++) {
					if (curBlock.size() == blockSize) {
						blocks.add(curBlock);
						curBlock = new ArrayList<>(blockSize);
					}
					curBlock.add(allCurveChars.get(i));
				}
				blocks.add(curBlock);
				Collections.shuffle(blocks, new Random(allCurveChars.size()*1000l*nodes.size()));
				for (List<CurveChar> block : blocks) {
					for (CurveChar curve : block) {
						funcs.add(curve.curve);
						chars.add(curve.pChar);
					}
				}
			} else {
				Collections.shuffle(allCurveChars, new Random(allCurveChars.size()*1000l*nodes.size()));
				for (CurveChar curve : allCurveChars) {
					funcs.add(curve.curve);
					chars.add(curve.pChar);
				}
			}
		}
		
		Range yRange = new Range(1e-6, 1e0);
		double minX = Double.POSITIVE_INFINITY;
		double maxX = 0d;
		for (DiscretizedFunc func : funcs) {
			for (Point2D pt : func) {
				if (pt.getX() > 1e-2 && (float)pt.getY() < (float)yRange.getUpperBound()
						&& (float)pt.getY() > (float)yRange.getLowerBound()) {
					minX = Math.min(minX, pt.getX());
					maxX = Math.max(maxX, pt.getX());
				}
			}
		}
		minX = Math.pow(10, Math.floor(Math.log10(minX)));
		maxX = Math.pow(10, Math.ceil(Math.log10(maxX)));
		Range xRange = new Range(minX, maxX);
		
		List<XYAnnotation> anns = SolSiteHazardCalc.addRPAnnotations(funcs, chars, xRange, yRange, rps, true);
		
		PlotSpec spec = new PlotSpec(funcs, chars, siteName, perLabel+" ("+units+")", "Annual Probability of Exceedance");
		spec.setLegendInset(true);
		spec.setPlotAnnotations(anns);
		
		plotFutures.add(exec.submit(new Runnable() {
			
			@Override
			public void run() {
				HeadlessGraphPanel gp = PlotUtils.initHeadless();
				
				gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
				gp.drawGraphPanel(spec, true, true, xRange, yRange);
				
				try {
					Stopwatch watch = Stopwatch.createStarted();
					PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, 800, true, nodeIndvCurves == null, false);
					watch.stop();
					double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
					if (secs > 10)
						System.out.println("\t\tDONE "+prefix+".png in "+(float)secs+" s");
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		}));
		
		return new File(resourcesDir, prefix+".png");
	}
	
	private static class CurveChar {
		final DiscretizedFunc curve;
		final PlotCurveCharacterstics pChar;
		public CurveChar(DiscretizedFunc curve, PlotCurveCharacterstics pChar) {
			super();
			this.curve = curve;
			this.pChar = pChar;
		}
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
	
	private static DiscretizedFunc getNodeMeanCurve(List<LogicTreeBranch<?>> branches, List<Double> weights,
			List<DiscretizedFunc> curves, LogicTreeNode node, List<DiscretizedFunc> nodeCurves) {
		LightFixedXFunc meanCurve = null;
		double nodeWeight = 0d;
		for (int i=0; i<branches.size(); i++) {
			LogicTreeBranch<?> branch = branches.get(i);
			double weight = weights.get(i);
			if (branch.hasValue(node)) {
				DiscretizedFunc curve = curves.get(i);
				if (meanCurve == null)
					meanCurve = new LightFixedXFunc(xVals(curve), new double[curve.size()]);
				else
					Preconditions.checkState(curve.size() == meanCurve.size());
				nodeCurves.add(curves.get(i));
				nodeWeight += weight;
				for (int j=0; j<curve.size(); j++)
					meanCurve.set(j, meanCurve.getY(j) + weight*curve.getY(j));
			}
		}
		if (nodeCurves.isEmpty())
			return null;
		meanCurve.scale(1d/nodeWeight);
		return meanCurve;
	}
	
	private static HistogramFunction buildHist(List<Double> weights, List<Double> branchVals, HistogramFunction refHist) {
		return buildHist(null, weights, branchVals, null, refHist);
	}
	
	private static HistogramFunction buildHist(List<LogicTreeBranch<?>> branches, List<Double> weights,
			List<Double> branchVals, LogicTreeNode node, HistogramFunction refHist) {
		HistogramFunction hist = new HistogramFunction(refHist.getMinX(), refHist.getMaxX(), refHist.size());
		double totWeight = 0d;
		int count = 0;
		for (int i=0; i<branchVals.size(); i++) {
			double weight = weights.get(i);
			totWeight += weight;
			if (node != null) {
				LogicTreeBranch<?> branch = branches.get(i);
				if (!branch.hasValue(node))
					continue;
			}
			count++;
			hist.add(hist.getClosestXIndex(branchVals.get(i)), weight);
		}
		if (count == 0)
			return null;
		hist.scale(1d/totWeight);
		return hist;
	}
	
	private static double mean(List<Double> weights, List<Double> vals) {
		return mean(null, weights, vals, null);
	}
	
	private static double mean(List<LogicTreeBranch<?>> branches, List<Double> weights,
			List<Double> branchVals, LogicTreeNode node) {
		double sumWeight = 0d;
		double ret = 0d;
		if (node != null)
			Preconditions.checkState(branches != null);
		for (int i=0; i<branchVals.size(); i++) {
			double weight = weights.get(i);
			if (node != null) {
				LogicTreeBranch<?> branch = branches.get(i);
				if (!branch.hasValue(node))
					continue;
			}
			sumWeight += weight;
			ret += branchVals.get(i)*weight;
		}
		return ret/sumWeight;
	}
	
	public static double curveVal(DiscretizedFunc curve, ReturnPeriods rp) {
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
			HistogramFunction hist, double mean, Color color, String primaryName,
			HistogramFunction compHist, Double compMean, Color compColor, Color compHistColor, String compName,
			ExecutorService exec, List<Future<?>> plotFutures) throws IOException {
		return valDistPlot(resourcesDir, prefix, siteName, label, hist, mean, color, primaryName,
				compHist, compMean, compColor, compHistColor, compName, null, null, null, exec, plotFutures);
	}
	
	private static File valDistPlot(File resourcesDir, String prefix, String siteName, String label,
			HistogramFunction hist, double mean, Color color, String primaryName,
			HistogramFunction compHist, Double compMean, Color compColor, Color compHistColor, String compName,
			List<LogicTreeNode> nodes, List<HistogramFunction> nodeHists, List<Double> nodeMeans,
			ExecutorService exec, List<Future<?>> plotFutures) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		double maxY = hist.getMaxY()*1.2;
		if (compHist != null) {
			funcs.add(compHist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, compHistColor));
			
			maxY = Math.max(maxY, compHist.getMaxY()*1.2);
			
			if (compMean != null) {
				funcs.add(vertLine(compMean, 0d, maxY, compName+" Mean"));
				chars.add(new PlotCurveCharacterstics(nodeHists == null ? PlotLineType.SOLID : PlotLineType.DASHED, 4f, compColor));
			}
		}
		
		funcs.add(hist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.GRAY));
		
		if (nodeHists != null) {
			HistogramFunction sumHist = null;
			List<HistogramFunction> usedNodeHists = new ArrayList<>();
			List<Double> usedNodeMeans = new ArrayList<>();
			for (int i=0; i<nodes.size(); i++) {
				HistogramFunction nodeHist = nodeHists.get(i);
				if (nodeHist == null)
					continue;
				Preconditions.checkState(nodeHist.size() == hist.size(), "%s != %s", nodeHist.size(), hist.size());
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
				List<Color> colors = getNodeColors(usedNodeHists.size());
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
			// if we have a comp hist, it's already in the legend earlier
			String lineLabel = compHist == null ? compName+" Mean" : null;
			funcs.add(vertLine(compMean, 0d, maxY, lineLabel));
			chars.add(new PlotCurveCharacterstics(nodeHists == null ? PlotLineType.SOLID : PlotLineType.DASHED, 4f, compColor));
		}
		
		funcs.add(vertLine(mean, 0d, maxY, (primaryName != null && !primaryName.isBlank() ? primaryName+" " : "")+"Mean"));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, color));
		
		Range xRange = new Range(hist.getMinX()-0.5*hist.getDelta(), hist.getMaxX()+0.5*hist.getDelta());
		Range yRange = new Range(0d, maxY);
		
		PlotSpec spec = new PlotSpec(funcs, chars, siteName, label, "Fraction");
		spec.setLegendInset(true);
		
		plotFutures.add(exec.submit(new Runnable() {
			
			@Override
			public void run() {
				HeadlessGraphPanel gp = PlotUtils.initHeadless();
				
				gp.drawGraphPanel(spec, false, false, xRange, yRange);
				
				try {
					Stopwatch watch = Stopwatch.createStarted();
					PlotUtils.writePlots(resourcesDir, prefix, gp, 800, 700, true, true, false);
					watch.stop();
					double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
					if (secs > 10)
						System.out.println("\t\tDONE "+prefix+".png in "+(float)secs+" s");
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		}));
		
		return new File(resourcesDir, prefix+".png");
	}
	
	public static List<Color> getNodeColors(int numNodes) throws IOException {
		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, Double.max(numNodes-1d, 1d));
		List<Color> colors = new ArrayList<>();
		for (int i=0; i<numNodes; i++)
			colors.add(cpt.getColor((float)i));
		return colors;
	}
	
	private static XY_DataSet vertLine(double x, double y0, double y1, String name) {
		DefaultXY_DataSet xy = new DefaultXY_DataSet();
		xy.set(x, y0);
		xy.set(x, y1);
		xy.setName(name);
		return xy;
	}

}
