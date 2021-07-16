package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.BiasiWesnouskyJumpProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.BiasiWesnouskyJumpProb.BiasiWesnousky2016CombJumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.BiasiWesnouskyJumpProb.BiasiWesnousky2016SSJumpProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen.RakeType;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureConnectionSearch;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.collect.Lists;

public class BiasiWesnouskyPlots extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "Biasi & Wesnousky (2016,2017) Comparisons";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		List<String> lines = new ArrayList<>();
		
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		lines.add(getSubHeading()+" Jump Distance Comparisons");
		lines.add(topLink); lines.add("");
		String line = "These plots express the chances of taking an available jump of a given distance "
				+ "between two faults of the same type (SS, Normal, Reverse). Passing ratios give the "
				+ "ratio of times a jump was taken to the number of times a rupture ended without taking "
				+ "an available jump of that distance.";
		if (sol == null)
			line += " NOTE: Only as-discretized rates are included, as we don't have a fault system solution.";
		else
			line += " Both as-discretized and rate-weighted probabilities are plotted (in separate plots).";
		lines.add(line);
		lines.add("");
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		SectionDistanceAzimuthCalculator distAzCalc = rupSet.requireModule(SectionDistanceAzimuthCalculator.class);
		PlausibilityConfiguration inputConfig = rupSet.getModule(PlausibilityConfiguration.class);
		List<ClusterRupture> inputRups = rupSet.requireModule(ClusterRuptures.class).getAll();
		RuptureConnectionSearch inputSearch = rupSet.requireModule(RuptureConnectionSearch.class);
		
		ClusterConnectionStrategy connStrat;
		if (inputConfig == null)
			connStrat = new DistCutoffClosestSectClusterConnectionStrategy(
					subSects, distAzCalc, 10d);
		else
			connStrat = inputConfig.getConnectionStrategy();
		System.out.println("Plotting Biasi & Wesnousky (2016) jump distance comparisons");
		for (RakeType type : RakeType.values()) {
			table.addLine("**"+type.name+" Passing Ratio**", "**"+type.name+" Probability**");
			File[] plots = plotBiasiWesnouskyJumpDistComparison(resourcesDir,
					"bw_jump_dist_"+type.prefix+"_discr", type.name, inputRups, connStrat,
					inputSearch, null, type);
			table.addLine("![Passing Ratio]("+relPathToResources+"/"+plots[0].getName()+")",
					"![Probability]("+relPathToResources+"/"+plots[1].getName()+")");
			if (sol != null) {
				plots = plotBiasiWesnouskyJumpDistComparison(resourcesDir,
						"bw_jump_dist_"+type.prefix, type.name, inputRups, connStrat,
						inputSearch, sol, type);
				table.addLine("![Passing Ratio]("+relPathToResources+"/"+plots[0].getName()+")",
						"![Probability]("+relPathToResources+"/"+plots[1].getName()+")");
			}
		}
		lines.addAll(table.build());
		
		lines.add(getSubHeading()+" Jump Azimuth Change Comparisons");
		lines.add(topLink); lines.add("");
		line = "These plots express the chances of taking an available jump of a given azimuth change "
				+ "between two faults of the same type (SS, Normal, Reverse). Passing ratios give the "
				+ "ratio of times a jump was taken to the number of times a rupture ended without taking "
				+ "an available jump of that azimuth change.";
		if (sol == null)
			line += " NOTE: Only as-discretized rates are included, as we don't have a fault system solution.";
		else
			line += " Both as-discretized and rate-weighted probabilities are plotted (in separate plots).";
		lines.add(line);
		lines.add("");
		table = MarkdownUtils.tableBuilder();
		System.out.println("Plotting Biasi & Wesnousky (2017) azimuth change comparisons");
		for (RakeType type : RakeType.values()) {
			table.addLine("**"+type.name+" Passing Ratio**", "**"+type.name+" Probability**");
			File[] plots = plotBiasiWesnouskyJumpAzComparison(resourcesDir,
					"bw_jump_az_"+type.prefix+"_discr", type.name, inputRups, connStrat,
					inputSearch, null, type);
			table.addLine("![Passing Ratio]("+relPathToResources+"/"+plots[0].getName()+")",
					"![Probability]("+relPathToResources+"/"+plots[1].getName()+")");
			if (sol != null) {
				plots = plotBiasiWesnouskyJumpAzComparison(resourcesDir,
						"bw_jump_az_"+type.prefix, type.name, inputRups, connStrat,
						inputSearch, sol, type);
				table.addLine("![Passing Ratio]("+relPathToResources+"/"+plots[0].getName()+")",
						"![Probability]("+relPathToResources+"/"+plots[1].getName()+")");
			}
		}
		lines.addAll(table.build());
		
		lines.add(getSubHeading()+" Mechanism Change Comparisons");
		lines.add(topLink); lines.add("");
		line = "These plots express the probability of a rupture changing mechanism (e.g., strike-slip to "
				+ "reverse, or right-lateral to left-lateral) at least once, as a function of magnitude.";
		if (sol == null)
			line += " NOTE: Only as-discretized rates are included, as we don't have a fault system solution.";
		else
			line += " Both as-discretized and rate-weighted probabilities are plotted (in separate plots).";
		lines.add(line);
		lines.add("");
		table = MarkdownUtils.tableBuilder();
		if (sol != null)
			table.addLine("As Discretized", "Rate-Weighted");
		table.initNewLine();
		System.out.println("Plotting Biasi & Wesnousky (2017) mechanism change comparisons");
		File plot = plotBiasiWesnouskyMechChangeComparison(resourcesDir,
				"bw_mech_change_discr", "Mechanism Change Probability", inputRups, connStrat,
				inputSearch, rupSet, null);
		table.addColumn("![As Discretized]("+relPathToResources+"/"+plot.getName()+")");
		if (sol != null) {
			plot = plotBiasiWesnouskyMechChangeComparison(resourcesDir,
					"bw_mech_change", "Mechanism Change Probability", inputRups, connStrat,
					inputSearch, rupSet, sol);
			table.addColumn("![Rate-Weighted]("+relPathToResources+"/"+plot.getName()+")");
		}
		table.finalizeLine();
		lines.addAll(table.build());
		return lines;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return List.of(SectionDistanceAzimuthCalculator.class, ClusterRuptures.class, RuptureConnectionSearch.class);
	}
	
	private static File[] plotBiasiWesnouskyJumpDistComparison(File resourcesDir, String prefix,
			String title, List<ClusterRupture> rups, ClusterConnectionStrategy connStrat,
			RuptureConnectionSearch connSearch, FaultSystemSolution sol, RakeType mech) throws IOException {
		Range xRange = new Range(0d, 10d);
		EvenlyDiscretizedFunc jumpsTakenFunc = new EvenlyDiscretizedFunc(0.5, 10, 1d);
		EvenlyDiscretizedFunc jumpsNotTakenFunc = new EvenlyDiscretizedFunc(0.5, 10, 1d);
		for (int r=0; r<rups.size(); r++) {
			double rate = sol == null ? 1d : sol.getRateForRup(r);
			List<ClusterRupture> versions = Lists.newArrayList(rups.get(r));
			versions.addAll(rups.get(r).getPreferredAltRepresentations(connSearch));
			rate /= versions.size();
			for (ClusterRupture rup : versions) {
				for (Jump jump : rup.getJumpsIterable())
					if (mech.isMatch(jump.fromSection.getAveRake()) && mech.isMatch(jump.toSection.getAveRake()))
						jumpsTakenFunc.add(jumpsTakenFunc.getClosestXIndex(jump.distance), rate);
				HashSet<Integer> rupParents = new HashSet<>();
				for (FaultSubsectionCluster cluster : rup.getClustersIterable())
					rupParents.add(cluster.parentSectionID);
				for (ClusterRupture strand : rup.getStrandsIterable()) {
					List<FaultSection> strandSects = strand.clusters[strand.clusters.length-1].subSects;
					FaultSection lastSect = strandSects.get(strandSects.size()-1);
					if (!mech.isMatch(lastSect.getAveRake()))
						continue;
					double minCandidateDist = Double.POSITIVE_INFINITY;
					for (Jump jump : connStrat.getJumpsFrom(lastSect)) {
						if (!mech.isMatch(jump.toSection.getAveRake()))
							continue;
						if (!rupParents.contains(jump.toSection.getParentSectionId())) {
							// only count if we don't have this jump (or a jump to a the same section)
							minCandidateDist = Math.min(minCandidateDist, jump.distance);
						}
					}
					if (Double.isFinite(minCandidateDist))
						jumpsNotTakenFunc.add(jumpsNotTakenFunc.getClosestXIndex(minCandidateDist), rate);
				}
			}
		}
		EvenlyDiscretizedFunc passingRatio = new EvenlyDiscretizedFunc(jumpsTakenFunc.getMinX(),
				jumpsTakenFunc.getMaxX(), jumpsTakenFunc.size());
		EvenlyDiscretizedFunc prob = new EvenlyDiscretizedFunc(jumpsTakenFunc.getMinX(),
				jumpsTakenFunc.getMaxX(), jumpsTakenFunc.size());
		for (int i=0; i<passingRatio.size(); i++) {
			double pr = jumpsTakenFunc.getY(i)/jumpsNotTakenFunc.getY(i);
			if (Double.isInfinite(pr))
				pr = Double.NaN;
			passingRatio.set(i, pr);
			if (Double.isFinite(pr))
				prob.set(i, BiasiWesnouskyJumpProb.passingRatioToProb(pr));
			else
				prob.set(i, Double.NaN);
		}
		
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		passingRatio.setName("Rupture Set");
		funcs.add(passingRatio);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f,
				PlotSymbol.FILLED_CIRCLE, 6f, MAIN_COLOR));
		
		EvenlyDiscretizedFunc bwPR = null;
		if (mech == RakeType.LEFT_LATERAL || mech == RakeType.RIGHT_LATERAL) {
			BiasiWesnousky2016SSJumpProb ssDistProb = new BiasiWesnousky2016SSJumpProb();
			bwPR = new EvenlyDiscretizedFunc(passingRatio.getMinX(),
					passingRatio.getMaxX(), passingRatio.size());
			for (int i=0; i<bwPR.size(); i++)
				bwPR.set(i, ssDistProb.calcPassingRatio(bwPR.getX(i)));
			
			bwPR.setName("B&W (2016), SS");
			funcs.add(bwPR);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		}
		new BiasiWesnousky2016CombJumpDistProb();
		double bwDistIndepProb = new BiasiWesnousky2016CombJumpDistProb().getDistanceIndepentProb(mech);
		double bwDistIndepPR = BiasiWesnouskyJumpProb.probToPassingRatio(bwDistIndepProb);
		DiscretizedFunc bwDistIndepPRFunc = new ArbitrarilyDiscretizedFunc();
		bwDistIndepPRFunc.set(xRange.getLowerBound(), bwDistIndepPR);
		bwDistIndepPRFunc.set(xRange.getUpperBound(), bwDistIndepPR);
		
		bwDistIndepPRFunc.setName("B&W (2016) Dist-Indep. P(>1km)");
		funcs.add(bwDistIndepPRFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLACK));
		
		String yAxisLabel = "Passing Ratio";
		if (sol == null)
			yAxisLabel += " (as discretized)";
		else
			yAxisLabel += " (rate-weighted)";
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Jump Distance (km)", yAxisLabel);
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setLegendFontSize(22);
		
		Range yRange = new Range(0, 5d);
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		gp.getChartPanel().setSize(800, 550);
		File pngFile = new File(resourcesDir, prefix+"_ratio.png");
		File pdfFile = new File(resourcesDir, prefix+"_ratio.pdf");
		File txtFile = new File(resourcesDir, prefix+"_ratio.txt");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		gp.saveAsPDF(pdfFile.getAbsolutePath());
		gp.saveAsTXT(txtFile.getAbsolutePath());

		File[] ret = new File[2];
		ret[0] = pngFile;
		
		funcs = new ArrayList<>();
		chars = new ArrayList<>();
		
		prob.setName("Rupture Set");
		funcs.add(prob);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f,
				PlotSymbol.FILLED_CIRCLE, 6f, MAIN_COLOR));
		
		if (bwPR != null) {
			EvenlyDiscretizedFunc bwProb = new EvenlyDiscretizedFunc(
					bwPR.getMinX(), bwPR.getMaxX(), bwPR.size());
			for (int i=0; i<bwPR.size(); i++)
				bwProb.set(i, BiasiWesnouskyJumpProb.passingRatioToProb(bwPR.getY(i)));
			
			bwProb.setName("B&W (2016), SS");
			funcs.add(bwProb);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		}
		
		DiscretizedFunc bwDistIndepProbFunc = new ArbitrarilyDiscretizedFunc();
		bwDistIndepProbFunc.set(xRange.getLowerBound(), bwDistIndepProb);
		bwDistIndepProbFunc.set(xRange.getUpperBound(), bwDistIndepProb);
		
		bwDistIndepProbFunc.setName("B&W (2016) Dist-Indep. P(>1km)");
		funcs.add(bwDistIndepProbFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLACK));
		
		yAxisLabel = "Jump Probability";
		if (sol == null)
			yAxisLabel += " (as discretized)";
		else
			yAxisLabel += " (rate-weighted)";
		spec = new PlotSpec(funcs, chars, title, "Jump Distance (km)", yAxisLabel);
		spec.setLegendVisible(true);
		
		gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setLegendFontSize(22);
		
		yRange = new Range(0, 1d);
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		gp.getChartPanel().setSize(800, 550);
		pngFile = new File(resourcesDir, prefix+"_prob.png");
		pdfFile = new File(resourcesDir, prefix+"_prob.pdf");
		txtFile = new File(resourcesDir, prefix+"_prob.txt");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		gp.saveAsPDF(pdfFile.getAbsolutePath());
		gp.saveAsTXT(txtFile.getAbsolutePath());

		ret[1] = pngFile;
		
		return ret;
	}
	
	private static File[] plotBiasiWesnouskyJumpAzComparison(File resourcesDir, String prefix,
			String title, List<ClusterRupture> rups, ClusterConnectionStrategy connStrat,
			RuptureConnectionSearch connSearch, FaultSystemSolution sol, RakeType mech) throws IOException {
		Range xRange = new Range(0d, 180d);
		EvenlyDiscretizedFunc jumpsTakenFunc = new EvenlyDiscretizedFunc(5d, 18, 10d);
		EvenlyDiscretizedFunc jumpsNotTakenFunc = new EvenlyDiscretizedFunc(5d, 18, 10d);
		SectionDistanceAzimuthCalculator distAzCalc = connSearch.getDistAzCalc();
		for (int r=0; r<rups.size(); r++) {
			double rate = sol == null ? 1d : sol.getRateForRup(r);
			List<ClusterRupture> versions = Lists.newArrayList(rups.get(r));
			versions.addAll(rups.get(r).getPreferredAltRepresentations(connSearch));
			rate /= versions.size();
			for (ClusterRupture rup : versions) {
				RuptureTreeNavigator nav = rup.getTreeNavigator();
				for (Jump jump : rup.getJumpsIterable()) {
					if (mech.isMatch(jump.fromSection.getAveRake())
							&& mech.isMatch(jump.toSection.getAveRake())) {
						FaultSection before2 = jump.fromSection;
						FaultSection before1 = nav.getPredecessor(before2);
						if (before1 == null)
							continue;
						double beforeAz = distAzCalc.getAzimuth(before1, before2);
						FaultSection after1 = jump.toSection;
						double minAz = Double.POSITIVE_INFINITY;
						for (FaultSection after2 : nav.getDescendants(after1)) {
							double afterAz = distAzCalc.getAzimuth(after1, after2);
							double diff = Math.abs(
									JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz));
							minAz = Math.min(minAz, diff);
						}
						if (Double.isFinite(minAz))
							jumpsTakenFunc.add(jumpsTakenFunc.getClosestXIndex(minAz), rate);
					}
				}
				HashSet<Integer> rupParents = new HashSet<>();
				for (FaultSubsectionCluster cluster : rup.getClustersIterable())
					rupParents.add(cluster.parentSectionID);
				for (ClusterRupture strand : rup.getStrandsIterable()) {
					List<FaultSection> strandSects = strand.clusters[strand.clusters.length-1].subSects;
					FaultSection lastSect = strandSects.get(strandSects.size()-1);
					if (!mech.isMatch(lastSect.getAveRake()))
						continue;
					FaultSection before2 = lastSect;
					FaultSection before1 = nav.getPredecessor(before2);
					if (before1 == null)
						continue;
					double beforeAz = distAzCalc.getAzimuth(before1, before2);
					double minCandidateAz = Double.POSITIVE_INFINITY;
					for (Jump jump : connStrat.getJumpsFrom(lastSect)) {
						if (!mech.isMatch(jump.toSection.getAveRake()))
							continue;
						
						if (!rupParents.contains(jump.toSection.getParentSectionId())) {
							// only count if we don't have this jump (or a jump to a the same section)
							FaultSection after1 = jump.toSection;
							if (jump.toCluster.subSects.size() < 2)
								continue;
							FaultSection after2 = jump.toCluster.subSects.get(1);
							double afterAz = distAzCalc.getAzimuth(after1, after2);
							double diff = Math.abs(
									JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz));
							minCandidateAz = Math.min(minCandidateAz, diff);
						}
					}
					if (Double.isFinite(minCandidateAz))
						jumpsNotTakenFunc.add(jumpsNotTakenFunc.getClosestXIndex(minCandidateAz), rate);
				}
			}
		}
		EvenlyDiscretizedFunc passingRatio = new EvenlyDiscretizedFunc(jumpsTakenFunc.getMinX(),
				jumpsTakenFunc.getMaxX(), jumpsTakenFunc.size());
		EvenlyDiscretizedFunc prob = new EvenlyDiscretizedFunc(jumpsTakenFunc.getMinX(),
				jumpsTakenFunc.getMaxX(), jumpsTakenFunc.size());
		for (int i=0; i<passingRatio.size(); i++) {
			double pr = jumpsTakenFunc.getY(i)/jumpsNotTakenFunc.getY(i);
			if (Double.isInfinite(pr))
				pr = Double.NaN;
			passingRatio.set(i, pr);
			if (Double.isFinite(pr))
				prob.set(i, BiasiWesnouskyJumpProb.passingRatioToProb(pr));
			else
				prob.set(i, Double.NaN);
		}
		
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		passingRatio.setName("Rupture Set");
		funcs.add(passingRatio);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f,
				PlotSymbol.FILLED_CIRCLE, 6f, MAIN_COLOR));
		
		EvenlyDiscretizedFunc bwPR = null;
		if (mech == RakeType.LEFT_LATERAL || mech == RakeType.RIGHT_LATERAL) {
			bwPR = BiasiWesnouskyJumpProb.bw2017_ss_passRatio.deepClone();
			
			bwPR.setName("B&W (2017), SS");
			funcs.add(bwPR);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		}
		
		String yAxisLabel = "Passing Ratio";
		if (sol == null)
			yAxisLabel += " (as discretized)";
		else
			yAxisLabel += " (rate-weighted)";
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Jump Azimuth Change (degrees)", yAxisLabel);
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setLegendFontSize(22);
		
		Range yRange = new Range(0, 5d);
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		gp.getChartPanel().setSize(800, 550);
		File pngFile = new File(resourcesDir, prefix+"_ratio.png");
		File pdfFile = new File(resourcesDir, prefix+"_ratio.pdf");
		File txtFile = new File(resourcesDir, prefix+"_ratio.txt");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		gp.saveAsPDF(pdfFile.getAbsolutePath());
		gp.saveAsTXT(txtFile.getAbsolutePath());
		
		File[] ret = new File[2];
		ret[0] = pngFile;
		
		funcs = new ArrayList<>();
		chars = new ArrayList<>();
		
		prob.setName("Rupture Set");
		funcs.add(prob);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f,
				PlotSymbol.FILLED_CIRCLE, 6f, MAIN_COLOR));
		
		if (bwPR != null) {
			EvenlyDiscretizedFunc bwProb = new EvenlyDiscretizedFunc(
					bwPR.getMinX(), bwPR.getMaxX(), bwPR.size());
			for (int i=0; i<bwPR.size(); i++)
				bwProb.set(i, BiasiWesnouskyJumpProb.passingRatioToProb(bwPR.getY(i)));
			
			bwProb.setName("B&W (2017), SS");
			funcs.add(bwProb);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		}
		
		yAxisLabel = "Jump Probability";
		if (sol == null)
			yAxisLabel += " (as discretized)";
		else
			yAxisLabel += " (rate-weighted)";
		spec = new PlotSpec(funcs, chars, title, "Jump Azimuth Change (degrees)", yAxisLabel);
		spec.setLegendVisible(true);
		
		gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setLegendFontSize(22);
		
		yRange = new Range(0, 1d);
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		gp.getChartPanel().setSize(800, 550);
		pngFile = new File(resourcesDir, prefix+"_prob.png");
		pdfFile = new File(resourcesDir, prefix+"_prob.pdf");
		txtFile = new File(resourcesDir, prefix+"_prob.txt");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		gp.saveAsPDF(pdfFile.getAbsolutePath());
		gp.saveAsTXT(txtFile.getAbsolutePath());

		ret[1] = pngFile;
		
		return ret;
	}
	
	private static File plotBiasiWesnouskyMechChangeComparison(File resourcesDir, String prefix,
			String title, List<ClusterRupture> rups, ClusterConnectionStrategy connStrat,
			RuptureConnectionSearch connSearch, FaultSystemRupSet rupSet, FaultSystemSolution sol)
					throws IOException {
		Range xRange = new Range(6d, 8.5d);
		EvenlyDiscretizedFunc mechChangesFunc = new EvenlyDiscretizedFunc(6.25, 5, 0.5d);
		EvenlyDiscretizedFunc rupMagFunc = new EvenlyDiscretizedFunc(6.25, 5, 0.5d);
		for (int r=0; r<rups.size(); r++) {
			double rate = sol == null ? 1d : sol.getRateForRup(r);
			
			List<ClusterRupture> versions = Lists.newArrayList(rups.get(r));
			versions.addAll(rups.get(r).getPreferredAltRepresentations(connSearch));
			int magIndex = rupMagFunc.getClosestXIndex(rupSet.getMagForRup(r));
			rupMagFunc.add(magIndex, rate);
			rate /= versions.size();
			for (ClusterRupture rup : versions) {
				boolean found = false;
				for (Jump jump : rup.getJumpsIterable()) {
					if (RakeType.getType(jump.fromSection.getAveRake())
							!= RakeType.getType(jump.toSection.getAveRake())) {
						found = true;
						break;
					}
				}
				if (found)
					mechChangesFunc.add(magIndex, rate);
			}
		}
		EvenlyDiscretizedFunc prob = new EvenlyDiscretizedFunc(rupMagFunc.getMinX(),
				rupMagFunc.getMaxX(), rupMagFunc.size());
		for (int i=0; i<prob.size(); i++) {
			double magRate = rupMagFunc.getY(i);
			if (magRate == 0d) {
				prob.set(i, Double.NaN);
			} else {
				prob.set(i, mechChangesFunc.getY(i)/magRate);
			}
		}
		
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		prob.setName("Rupture Set");
		funcs.add(prob);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, MAIN_COLOR));
		
		DiscretizedFunc bwProb = new ArbitrarilyDiscretizedFunc();
		bwProb.set(xRange.getLowerBound(), BiasiWesnouskyJumpProb.bw2017_mech_change_prob);
		bwProb.set(xRange.getUpperBound(), BiasiWesnouskyJumpProb.bw2017_mech_change_prob);
		bwProb.setName("B&W (2017)");
		funcs.add(bwProb);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		
		String yAxisLabel = "Probability";
		if (sol == null)
			yAxisLabel += " (as discretized)";
		else
			yAxisLabel += " (rate-weighted)";
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Magnitude", yAxisLabel);
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setLegendFontSize(22);
		
		Range yRange = new Range(0, 1d);
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		gp.getChartPanel().setSize(800, 550);
		File pngFile = new File(resourcesDir, prefix+".png");
		File pdfFile = new File(resourcesDir, prefix+".pdf");
		File txtFile = new File(resourcesDir, prefix+".txt");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		gp.saveAsPDF(pdfFile.getAbsolutePath());
		gp.saveAsTXT(txtFile.getAbsolutePath());

		return pngFile;
	}

}
