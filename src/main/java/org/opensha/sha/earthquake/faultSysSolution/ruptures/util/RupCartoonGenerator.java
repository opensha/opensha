package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.dom4j.DocumentException;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.data.Range;
import org.jfree.chart.ui.TextAnchor;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.gui.plot.AnimatedGIFRenderer;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder.RupDebugCriteria;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.DirectPathPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.MinSectsPerParentFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.ClusterCoulombCompatibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.ClusterCoulombPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.CumulativeProbPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathEvaluator.PathAddition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathEvaluator.PathNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathEvaluator.SectionPathNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.SectCoulombPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.SectCoulombPathEvaluator.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.AbstractRelativeProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CoulombSectRatioProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CoulombSectRatioProb.HighestNTracker;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CumulativeProbabilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RelativeCoulombProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RelativeSlipRateProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterPermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ConnectionPointsPermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.SectCountAdaptivePermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveClusterPermuationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.PlausibleClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.PlausibleClusterConnectionStrategy.*;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessDistribution;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.utils.FaultSystemIO;

public class RupCartoonGenerator {
	
	private static boolean write_pdfs = false;
	
	private static void plotSection(FaultSection sect, List<XY_DataSet> funcs,
			List<PlotCurveCharacterstics> chars, PlotCurveCharacterstics traceChar,
			PlotCurveCharacterstics outlineChar) {
		if (sect.getAveDip() < 90d) {
			RuptureSurface surf = sect.getFaultSurface(1d, false, false);
			LocationList perimeter = surf.getPerimeter();
			DefaultXY_DataSet xy = new DefaultXY_DataSet();
			for (Location loc : perimeter)
				xy.set(loc.getLongitude(), loc.getLatitude());
			xy.set(xy.get(0));
			funcs.add(xy);
			chars.add(outlineChar);
		}
		
		DefaultXY_DataSet xy = new DefaultXY_DataSet();
		for (Location loc : sect.getFaultTrace())
			xy.set(loc.getLongitude(), loc.getLatitude());
		funcs.add(xy);
		chars.add(traceChar);
	}
	
	private static List<XY_DataSet> line(FaultSection from, FaultSection to, boolean arrow, double lenScale) {
		return line(from, to, arrow, lenScale, null);
	}
	
	private static List<XY_DataSet> line(FaultSection from, FaultSection to, boolean arrow, double lenScale, Double arrowLen) {
		Location center1 = GriddedSurfaceUtils.getSurfaceMiddleLoc(from.getFaultSurface(1d, false, false));
		Location center2 = GriddedSurfaceUtils.getSurfaceMiddleLoc(to.getFaultSurface(1d, false, false));
		double arrowAz = LocationUtils.azimuth(center1, center2);
		double length = LocationUtils.horzDistanceFast(center1, center2);
		if (lenScale != 1d) {
			length *= lenScale;
			center2 = LocationUtils.location(center1, Math.toRadians(arrowAz), length);
		}
		
		List<XY_DataSet> ret = new ArrayList<>();
		ret.add(buildLine(center1, center2));
		
		if (arrow) {
			if (arrowLen == null)
				arrowLen = Math.max(1, Math.min(0.33*length, 4d)); // km
			double az1 = arrowAz + 135;
			double az2 = arrowAz - 135;
			
			Location arrow1 = LocationUtils.location(center2, Math.toRadians(az1), arrowLen);
			Location arrow2 = LocationUtils.location(center2, Math.toRadians(az2), arrowLen);

			ret.add(buildLine(center2, arrow1));
			ret.add(buildLine(center2, arrow2));
		}
		
		return ret;
	}
	
	private static XY_DataSet buildLine(Location from, Location to) {
		DefaultXY_DataSet xy = new DefaultXY_DataSet();
		
		xy.set(from.getLongitude(), from.getLatitude());
		xy.set(to.getLongitude(), to.getLatitude());
		
		return xy;
	}
	
	public static PlotSpec buildRupturePlot(ClusterRupture rup, String title,
			boolean plotAzimuths, boolean axisLabels) {
		return buildRupturePlot(rup, title, plotAzimuths, axisLabels, null, null, null);
	}
	
	public static PlotSpec buildRupturePlot(ClusterRupture rup, String title,
			boolean plotAzimuths, boolean axisLabels,
			Set<? extends FaultSection> highlightSects, Color highlightColor, String highlightName) {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		RupturePlotBuilder builder = new RupturePlotBuilder(plotAzimuths);
		
		if (highlightSects != null)
			builder.setHighlightSects(highlightSects, highlightColor, highlightName);
		
		builder.plotRecursive(rup);
		
		funcs.addAll(builder.sectFuncs);
		chars.addAll(builder.sectChars);
		funcs.addAll(builder.arrowFuncs);
		chars.addAll(builder.arrowChars);
		
		String xAxisLabel, yAxisLabel;
		if (axisLabels) {
			xAxisLabel = "Longitude";
			yAxisLabel = "Latitude";
		} else {
			xAxisLabel = " ";
			yAxisLabel = " ";
		}
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
		spec.setLegendVisible(true);
		
		return spec;
	}
	
	public static void plotRupture(File outputDir, String prefix, ClusterRupture rup, String title,
			boolean plotAzimuths, boolean axisLabels) throws IOException {
		PlotSpec spec = buildRupturePlot(rup, title, plotAzimuths, axisLabels);
		plotRupture(outputDir, prefix, spec, axisLabels);
	}
	
	public static void plotRupture(File outputDir, String prefix, PlotSpec spec, boolean axisLabels) throws IOException {
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		for (PlotElement xy : spec.getPlotElems()) {
			for (Point2D pt : (XY_DataSet)xy) {
				latTrack.addValue(pt.getY());
				lonTrack.addValue(pt.getX());
			}
		}
		
		double minLon = lonTrack.getMin();
		double maxLon = lonTrack.getMax();
		double minLat = latTrack.getMin();
		double maxLat = latTrack.getMax();
		maxLat += 0.05;
		minLat -= 0.05;
		maxLon += 0.05;
		minLon -= 0.05;
		int width = 800;
		
		Range xRange = new Range(minLon, maxLon);
		Range yRange = new Range(minLat, maxLat);
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		PlotUtils.setAxisVisible(gp, axisLabels, axisLabels);
		PlotUtils.setGridLinesVisible(gp, axisLabels, axisLabels);
		
		PlotUtils.writePlots(outputDir, prefix, gp, width, -1, true, write_pdfs, false);
	}
	
	private static PlotSpec plotConnStrat(File outputDir, String prefix, String title,
			ClusterConnectionStrategy connStrat, List<PlausibilityFilter> filters, boolean axisLabels,
			List<Jump> extraPassing, List<Jump> extraFailing) throws IOException {
		List<FaultSubsectionCluster> clusters = connStrat.getClusters();
		
		ConnectionPointsPermutationStrategy permStrat = new ConnectionPointsPermutationStrategy();
		
		ClusterRuptureBuilder build = new ClusterRuptureBuilder(clusters, filters, 0);
		System.out.println("Building ruptures with connStrat="+connStrat.getName());
		List<ClusterRupture> rups = build.build(permStrat);
		System.out.println("Built "+rups.size()+" ruptures");
		HashSet<FaultSection> coruptureSects =new HashSet<>();
		for (ClusterRupture rup : rups)
			if (rup.clusters.length > 1)
				for (FaultSubsectionCluster cluster : rup.clusters)
					coruptureSects.addAll(cluster.subSects);
		System.out.println("Found "+coruptureSects.size()+" co-rupture sects");
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		PlotCurveCharacterstics corupChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK);
		PlotCurveCharacterstics isolatedChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.LIGHT_GRAY);
		PlotCurveCharacterstics outlineChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK);
		
		boolean firstIsolated = true;
		for (FaultSubsectionCluster cluster : clusters) {
			for (FaultSection sect : cluster.subSects) {
				plotSection(sect, funcs, chars, isolatedChar, outlineChar);
				if (firstIsolated)
					funcs.get(funcs.size()-1).setName("Isolated Sections");
				firstIsolated = false;
			}
		}
		
		boolean firstCorup = true;
		for (FaultSubsectionCluster cluster : clusters) {
			for (FaultSection sect : cluster.subSects) {
				if (coruptureSects.contains(sect)) {
					plotSection(sect, funcs, chars, corupChar, outlineChar);
					if (firstCorup)
						funcs.get(funcs.size()-1).setName("Co-rupturing Sections");
					firstCorup = false;
				}
			}
		}
		
		boolean firstJump = true;
		for (Jump jump : clusters.get(0).getConnections()) {
			List<XY_DataSet> lines = line(jump.fromSection, jump.toSection, true, 1d);
			if (firstJump)
				lines.get(0).setName("Selected");
			firstJump = false;
			
			for (XY_DataSet line : lines) {
				funcs.add(line);
				chars.add(reg_jump_char);
			}
		}
		
		firstJump = true;
		for (Jump jump : extraPassing) {
			List<XY_DataSet> lines = line(jump.fromSection, jump.toSection, false, 1d);
			if (firstJump)
				lines.get(0).setName("Passes");
			firstJump = false;
			
			for (XY_DataSet line : lines) {
				funcs.add(line);
				chars.add(new PlotCurveCharacterstics(
						PlotLineType.DOTTED, 1f, new Color(255, 0, 0, 127)));
			}
		}
		
		firstJump = true;
		for (Jump jump : extraFailing) {
			List<XY_DataSet> lines = line(jump.fromSection, jump.toSection, false, 1d);
			if (firstJump)
				lines.get(0).setName("Fails");
			firstJump = false;
			
			for (XY_DataSet line : lines) {
				funcs.add(line);
				chars.add(new PlotCurveCharacterstics(
						PlotLineType.DASHED, 1f, new Color(0, 0, 0, 80)));
			}
		}
		
		String xAxisLabel, yAxisLabel;
		if (axisLabels) {
			xAxisLabel = "Longitude";
			yAxisLabel = "Latitude";
		} else {
			xAxisLabel = " ";
			yAxisLabel = " ";
		}
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
		spec.setLegendVisible(true);
		
		plotRupture(outputDir, prefix, spec, axisLabels);
		
		return spec;
	}
	
	private static Color[] strand_colors =  { Color.BLACK, Color.MAGENTA.darker(), Color.ORANGE.darker() };

	private static PlotCurveCharacterstics reg_jump_char = new PlotCurveCharacterstics(
			PlotLineType.SOLID, 3f, Color.RED);
	private static PlotCurveCharacterstics splay_jump_char = new PlotCurveCharacterstics(
			PlotLineType.SOLID, 3f, Color.CYAN);
	private static PlotCurveCharacterstics az_arrow_char = new PlotCurveCharacterstics(
			PlotLineType.SOLID, 2f, Color.GREEN);
	
	private static class RupturePlotBuilder {
		private boolean[] firstSects = null;
		private boolean firstStrandJump = true;
		private boolean firstSplayJump = true;
		private boolean firstAz = true;

		private List<XY_DataSet> sectFuncs;
		private List<PlotCurveCharacterstics> sectChars;
		private List<XY_DataSet> arrowFuncs;
		private List<PlotCurveCharacterstics> arrowChars;
		private boolean plotAzimuths;
		
		private Set<? extends FaultSection> highlightSects;
		private Color highlightColor;
		private String highlightName;
		private boolean firstHighlight = true;
		
		public RupturePlotBuilder(boolean plotAzimuths) {
			this.plotAzimuths = plotAzimuths;

			this.sectFuncs = new ArrayList<>();
			this.sectChars = new ArrayList<>();
			this.arrowFuncs = new ArrayList<>();
			this.arrowChars = new ArrayList<>();
			
			firstSects = new boolean[strand_colors.length];
			for (int i=0; i<firstSects.length; i++)
				firstSects[i] = true;
		}
		
		public void setHighlightSects(Set<? extends FaultSection> highlightSects, Color highlightColor, String highlightName) {
			this.highlightSects = highlightSects;
			this.highlightColor = highlightColor;
			this.highlightName = highlightName;
		}
		
		public void plotRecursive(ClusterRupture rup) {
			 plotRecursive(rup, 0);
		}
		
		public void plotRecursive(ClusterRupture rup, int strandIndex) {
			if (strandIndex >= strand_colors.length)
				strandIndex = strand_colors.length-1;
			Color strandColor = strand_colors[strandIndex];
			PlotCurveCharacterstics traceChar = new PlotCurveCharacterstics(
					PlotLineType.SOLID, 3f, strandColor);
			PlotCurveCharacterstics outlineChar = new PlotCurveCharacterstics(
					PlotLineType.SOLID, 1f, Color.GRAY);
			for (int i=0; i<rup.clusters.length; i++) {
				for (FaultSection sect : rup.clusters[i].subSects) {
					if (highlightSects != null && highlightSects.contains(sect)) {
						PlotCurveCharacterstics hTraceChar  = new PlotCurveCharacterstics(
								PlotLineType.SOLID, 3f, highlightColor);
						plotSection(sect, sectFuncs, sectChars, hTraceChar, outlineChar);
						if (firstHighlight) {
							firstHighlight = false;
							sectFuncs.get(sectFuncs.size()-1).setName(highlightName);
						}
					} else {
						plotSection(sect, sectFuncs, sectChars, traceChar, outlineChar);
						if (firstSects[strandIndex]) {
							firstSects[strandIndex] = false;
							String name;
							if (strandIndex == 0)
								name = "Primary Strand Sect";
							else {
								name = "Splay Strand (L"+strandIndex;
								if (strandIndex == strand_colors.length-1)
									name += "+";
								name += ")";
							}
							sectFuncs.get(sectFuncs.size()-1).setName(name);
						}
					}
				}
			}
			for (Jump jump : rup.internalJumps) {
				List<XY_DataSet> lines = line(jump.fromSection, jump.toSection, true, 1d);
				if (firstStrandJump)
					lines.get(0).setName("Regular Jump");
				firstStrandJump = false;
				
				for (XY_DataSet line : lines) {
					arrowFuncs.add(line);
					arrowChars.add(reg_jump_char);
				}
				
				if (plotAzimuths)
					addAzimuths(rup, jump);
			}
			for (Jump splayJump : rup.splays.keySet()) {
				ClusterRupture splay = rup.splays.get(splayJump);
				
				List<XY_DataSet> lines = line(splayJump.fromSection, splayJump.toSection, true, 1d);
				if (firstSplayJump)
					lines.get(0).setName("Splay Jump");
				firstSplayJump = false;
				
				for (XY_DataSet line : lines) {
					arrowFuncs.add(line);
					arrowChars.add(splay_jump_char);
				}
				
				if (plotAzimuths)
					addAzimuths(rup, splayJump);
				
				plotRecursive(splay, strandIndex+1);
			}
		}
		
		private void addAzimuths(ClusterRupture rup, Jump jump) {
			RuptureTreeNavigator navigator = rup.getTreeNavigator();
			FaultSection before = navigator.getPredecessor(jump.fromSection);
			if (before != null)
				addAzimuth(before, jump.fromSection);
			for (FaultSection after : navigator.getDescendants(jump.toSection))
				addAzimuth(jump.toSection, after);
		}
		
		private void addAzimuth(FaultSection from, FaultSection to) {
			List<XY_DataSet> xys = line(from, to, true, 2.5d);
			if (firstAz)
				xys.get(0).setName("Azimuth");
			firstAz = false;
			for (XY_DataSet xy : xys) {
				arrowFuncs.add(xy);
				arrowChars.add(az_arrow_char);
			}
		}
	}
	
	private static FaultSection buildSect(int parentID, double dip, double upperDepth, double lowerDepth,
			Location... traceLocs) {
		Preconditions.checkArgument(traceLocs.length > 1);
		FaultTrace trace = new FaultTrace("");
		for (Location loc : traceLocs)
			trace.add(loc);
		FaultSectionPrefData sect = new FaultSectionPrefData();
		sect.setFaultTrace(trace);
		sect.setAveDip(dip);
		sect.setDipDirection((float)(trace.getAveStrike() + 90d));
		sect.setAveLowerDepth(lowerDepth);
		sect.setAveUpperDepth(upperDepth);
		sect.setSectionId(parentID);
		
		return sect;
	}
	
	private static class SubSectBuilder {
		
		private double fractDDW;
		private List<FaultSection> subSectsList;
//		private List<FaultSubsectionCluster> allClusters;
		
//		private List<ClusterRupture> allRups;
//		private ClusterRupture biggestRup;
		
		public SubSectBuilder(double fractDDW) {
			this.fractDDW = fractDDW;
			this.subSectsList = new ArrayList<>();
//			allClusters = new ArrayList<>();
		}
		
		public void addFault(FaultSection parent) {
			// first build subsections
			double width = parent.getOrigDownDipWidth();
//			FaultSubsectionCluster cluster = new FaultSubsectionCluster(
//					parent.getSubSectionsList(fractDDW*width, subSectsList.size(), 2));
//			subSectsList.addAll(cluster.subSects);
			subSectsList.addAll(parent.getSubSectionsList(fractDDW*width, subSectsList.size(), 2));
//			allClusters.add(cluster);
		}
		
//		public void buildRuptures() {
//			DistCutoffClosestSectClusterConnectionStrategy connStrat =
//					new DistCutoffClosestSectClusterConnectionStrategy(subSectsList,
//							new SectionDistanceAzimuthCalculator(subSectsList), Double.POSITIVE_INFINITY);
//			connStrat.addConnections(allClusters);
//			
//			ClusterRuptureBuilder builder = new ClusterRuptureBuilder(
//					allClusters, new ArrayList<>(), Integer.MAX_VALUE);
//			allRups = builder.build(new UCERF3ClusterPermuationStrategy());
//			System.out.println("Built "+allRups.size()+" rups");
//			for (ClusterRupture rup : allRups) {
//				if (biggestRup == null)
//					biggestRup = rup;
//				else if (rup.unique.size() > biggestRup.unique.size())
//					biggestRup = rup;
//				else if (rup.unique.size() == biggestRup.unique.size()
//						&& rup.clusters.length > biggestRup.clusters.length)
//					biggestRup = rup;
//			}
//			System.out.println("Biggest has "+biggestRup.unique.size()+" sections");
//		}
		
	}
	
	private static double getAnnY(Range yRange, int index, double startYMult, double deltaYMult) {
		return yRange.getLowerBound() + (startYMult-index*deltaYMult)*
				(yRange.getUpperBound() - yRange.getLowerBound());
	}
	
	private static void animateRuptureBuilding(File outputDir, String prefix, SubSectBuilder rupBuild,
			List<PlausibilityFilter> filters, ClusterConnectionStrategy connStrat,
			int maxNumSplays, boolean includeDuplicates, boolean plotAzimuths,
			boolean axisLabels, double fps) throws IOException {
		animateRuptureBuilding(outputDir, prefix, rupBuild, filters, connStrat, new ExhaustiveClusterPermuationStrategy(),
				maxNumSplays, includeDuplicates, true, plotAzimuths, axisLabels, fps, null);
	}
	
	private static void animateRuptureBuilding(File outputDir, String prefix, SubSectBuilder rupBuild,
			List<PlausibilityFilter> filters, ClusterConnectionStrategy connStrat,
			ClusterPermutationStrategy permStrat, int maxNumSplays, boolean includeDuplicates, boolean includeFailures,
			boolean plotAzimuths, boolean axisLabels, double fps, Set<FaultSection> startSects) throws IOException {
		TrackAllDebugCriteria tracker = new TrackAllDebugCriteria();
		
		ClusterRuptureBuilder builder = new ClusterRuptureBuilder(
				connStrat.getClusters(), filters, maxNumSplays);
		builder.setDebugCriteria(tracker, false);
		List<ClusterRupture> finalRups = builder.build(permStrat);
		if (startSects != null) {
			for (int r=finalRups.size(); --r>=0;)
				if (!startSects.contains(finalRups.get(r).clusters[0].subSects.get(0)))
					finalRups.remove(r);
			for (int r=tracker.allRups.size(); --r>=0;)
				if (!startSects.contains(tracker.allRups.get(r).clusters[0].subSects.get(0)))
					tracker.allRups.remove(r);
		}
		
		System.out.println("Built "+finalRups.size()+" final rups");
		System.out.println("Tested "+tracker.allRups.size()+" rups");
		
		List<XY_DataSet> backgroundFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> backgroundChars = new ArrayList<>();
		PlotCurveCharacterstics bgTraceChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, 2f, Color.LIGHT_GRAY);
		PlotCurveCharacterstics bgOutlineChar = new PlotCurveCharacterstics(
				PlotLineType.DOTTED, 1f, Color.LIGHT_GRAY);
		for (FaultSection sect : rupBuild.subSectsList)
			plotSection(sect, backgroundFuncs, backgroundChars, bgTraceChar, bgOutlineChar);
		
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		for (PlotElement xy : backgroundFuncs) {
			for (Point2D pt : (XY_DataSet)xy) {
				latTrack.addValue(pt.getY());
				lonTrack.addValue(pt.getX());
			}
		}
		
		double minLon = lonTrack.getMin();
		double maxLon = lonTrack.getMax();
		double minLat = latTrack.getMin();
		double maxLat = latTrack.getMax();
		maxLat += 0.05;
		minLat -= 0.05;
		maxLon += 0.05;
		minLon -= 0.05;
		
		int width = 800;
		
		Range xRange = new Range(minLon, maxLon);
		Range yRange = new Range(minLat, maxLat);
		
		HashSet<UniqueRupture> uniques = new HashSet<>();
		
		Font annFont = new Font(Font.SANS_SERIF, Font.BOLD, 22);
		double startYMult = 0.98;
		double deltaYMult = 0.08;
		double rightAnnX = xRange.getLowerBound() + 0.98*(xRange.getUpperBound() - xRange.getLowerBound());
		double leftAnnX = xRange.getLowerBound() + 0.02*(xRange.getUpperBound() - xRange.getLowerBound());
		
		File outputFile = new File(outputDir, prefix+".gif");
		
		AnimatedGIFRenderer gifRender = new AnimatedGIFRenderer(outputFile, fps, true);
		
		int count = 0;
		
		for (ClusterRupture possible : tracker.allRups) {
			boolean duplicate = uniques.contains(possible.unique);
			if (duplicate && !includeDuplicates)
				continue;
			uniques.add(possible.unique);
			PlausibilityResult result = PlausibilityResult.PASS;
			List<PlausibilityResult> results = new ArrayList<>();
			for (PlausibilityFilter filter : filters) {
				PlausibilityResult subResult = filter.apply(possible, false);
				results.add(subResult);
				result = result.logicalAnd(subResult);
			}
			if (!includeFailures && !result.isPass())
				continue;
			
			PlotSpec spec = buildRupturePlot(possible, " ", false, false);
			List<PlotElement> newFuncs = new ArrayList<>(backgroundFuncs);
			List<PlotCurveCharacterstics> newChars = new ArrayList<>(backgroundChars);
			newFuncs.addAll(spec.getPlotElems());
			newChars.addAll(spec.getChars());
			spec.setPlotElems(newFuncs);
			spec.setChars(newChars);
			
			XYTextAnnotation resultAnn = new XYTextAnnotation(result.name(), rightAnnX,
					getAnnY(yRange, 0, startYMult, deltaYMult));
			resultAnn.setFont(annFont);
			resultAnn.setTextAnchor(TextAnchor.TOP_RIGHT);
			if (result.isPass())
				resultAnn.setPaint(Color.GREEN.darker());
			else if (result.canContinue())
				resultAnn.setPaint(Color.DARK_GRAY);
			else
				resultAnn.setPaint(Color.RED.darker());
			spec.addPlotAnnotation(resultAnn);
			if (duplicate && result.isPass()) {
				XYTextAnnotation dupAnn = new XYTextAnnotation("DUPLICATE", rightAnnX,
						getAnnY(yRange, 1, startYMult, deltaYMult));
				dupAnn.setFont(annFont);
				dupAnn.setTextAnchor(TextAnchor.TOP_RIGHT);
				spec.addPlotAnnotation(dupAnn);
			}
			for (int i=0; i<filters.size(); i++) {
				String text = filters.get(i).getShortName()+": ";
				PlausibilityResult subResult = results.get(i);
				if (subResult.isPass()) {
					text += "\u2714";
				} else {
					text += "X";
					if (subResult.canContinue())
						text += "*";
				}
				XYTextAnnotation resAnn = new XYTextAnnotation(text, leftAnnX,
						getAnnY(yRange, i, startYMult, deltaYMult));
				resAnn.setFont(annFont);
				resAnn.setTextAnchor(TextAnchor.TOP_LEFT);
				spec.addPlotAnnotation(resAnn);
			}
			
			System.out.println("Plotting frame "+(count++));
			
			HeadlessGraphPanel gp = PlotUtils.initHeadless();
			
			gp.drawGraphPanel(spec, false, false, xRange, yRange);
			PlotUtils.setAxisVisible(gp, axisLabels, axisLabels);
			PlotUtils.setGridLinesVisible(gp, axisLabels, axisLabels);
			
			int height = PlotUtils.calcHeight(gp, width);
			
			gp.getChartPanel().setSize(width, height);
			BufferedImage img = gp.getBufferedImage(width, height);
			
			gifRender.writeFrame(img);
		}
		
		gifRender.finalizeAnimation();
	}
	
	private static class TrackAllDebugCriteria implements RupDebugCriteria {
		
		private List<ClusterRupture> allRups = new ArrayList<>();

		@Override
		public boolean isMatch(ClusterRupture rup) {
			allRups.add(rup);
			return false;
		}

		@Override
		public boolean isMatch(ClusterRupture rup, Jump newJump) {
			allRups.add(rup.take(newJump));
			return false;
		}

		@Override
		public boolean appliesTo(PlausibilityResult result) {
			return true;
		}
		
	}
	
	private static FaultSubsectionCluster buildCluster(FaultSection parentSect, double fractDDW, int startIndex) {
		double width = parentSect.getOrigDownDipWidth();
		return new FaultSubsectionCluster(parentSect.getSubSectionsList(fractDDW*width, startIndex, 2));
	}
	
	private static void plot(File outputDir, String prefix, String title, double fractDDW,
			boolean plotAzimuths, FaultSection... parents) throws IOException {
		SubSectBuilder rupBuild = new SubSectBuilder(fractDDW);
		for (FaultSection parent : parents)
			rupBuild.addFault(parent);
		
		SectionDistanceAzimuthCalculator distCalc = new SectionDistanceAzimuthCalculator(rupBuild.subSectsList);
//		List<FaultSubsectionCluster> clusters = connStrat.getClusters();
		RuptureConnectionSearch search = new RuptureConnectionSearch(null, distCalc, Double.POSITIVE_INFINITY, false);
		List<FaultSubsectionCluster> clusters = search.calcClusters(rupBuild.subSectsList, false);
		
		List<Jump> rupJumps = search.calcRuptureJumps(clusters, true);
		ClusterRupture fullRup = search.buildClusterRupture(clusters, rupJumps, true, clusters.get(0));
		
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		plotRupture(outputDir, prefix, fullRup, title, plotAzimuths, false);
	}
	
	private static ClusterRupture getFullSectionsRup(ClusterRupture rup,
			Map<Integer, List<FaultSection>> parentSectsMap, SectionDistanceAzimuthCalculator distCalc) {
		List<FaultSubsectionCluster> fullClusters = new ArrayList<>();
		for (FaultSubsectionCluster cluster : rup.clusters) {
			List<FaultSection> fullSects = parentSectsMap.get(cluster.parentSectionID);
			if (cluster.subSects.size() == fullSects.size()) {
				fullClusters.add(cluster);
			} else {
				// grab the full one
				int fullStartID = fullSects.get(0).getSectionId();
				int fullEndID = fullSects.get(fullSects.size()-1).getSectionId();
				int clusterStartID = cluster.startSect.getSectionId();
				int clusterEndID = cluster.subSects.get(cluster.subSects.size()-1).getSectionId();
				if (fullStartID == clusterEndID || fullEndID == clusterStartID) {
					// reverse it
					fullSects = new ArrayList<>(fullSects);
					Collections.reverse(fullSects);
				}
				fullClusters.add(new FaultSubsectionCluster(fullSects));
			}
		}
		RuptureConnectionSearch search = new RuptureConnectionSearch(null, distCalc, Double.POSITIVE_INFINITY, false);
		
		List<Jump> rupJumps = search.calcRuptureJumps(fullClusters, true);
		return search.buildClusterRupture(fullClusters, rupJumps, true, fullClusters.get(0));
	}
	
	private static void buildStandardDemos(File outputDir) throws IOException {
		double upperDepth = 0d;
		double lowerDepth = 20d;
		double fractDDW = 0.5;
		
		int parentID = 1001;
		
		FaultSection firstHorz = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(0d, 0d), new Location(0d, 1d));
		FaultSection secondHorz = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(0d, 1.05d), new Location(0d, 2.05d));
		FaultSection jumpBelowFromEndSecond = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(-0.1d, 2d), new Location(-0.5, 2.5d));
		FaultSection jumpAboveFromEndSecond = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(0.1d, 2d), new Location(0.5, 2.5d));
		FaultSection jumpAboveFromMidSecond = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(0.2d, 1.5d), new Location(0.6, 2.1d));
		FaultSection tVert = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(-0.5d, 2.1), new Location(0.5, 2.1d));
		
		plot(outputDir, "simple_jump_1", "Single Strand, 2 Jumps", fractDDW, false,
				firstHorz, secondHorz, jumpBelowFromEndSecond);
		
		plot(outputDir, "splay_jump_1", "Multiple Strands, 2 Jumps", fractDDW, false,
				firstHorz, secondHorz, jumpAboveFromMidSecond);
				
		plot(outputDir, "splay_jump_2", "Multiple Strands, 3 Jumps", fractDDW, false,
				firstHorz, secondHorz, jumpBelowFromEndSecond, jumpAboveFromMidSecond);
		
		plot(outputDir, "y_jump_1", "Y Jump (Simple+Splay)", fractDDW, false,
				firstHorz, secondHorz, jumpBelowFromEndSecond, jumpAboveFromEndSecond);
		
		plot(outputDir, "t_jump_1", "T Jump", fractDDW, false,
				firstHorz, secondHorz, tVert);

		FaultSection almostParallelTowardEndFirst = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(0.3d, 0.6d), new Location(0.28d, 1d));
		FaultSection almostParallelTowardMiddleFirst = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(0.3d, 0.6d), new Location(0.32d, 1d));
		FaultSection shorterFirstHorz = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(0d, 0d), new Location(0d, 0.6d));
		
		plot(outputDir, "parallel_as_primary", "Parallel Single Strand", fractDDW, true,
				firstHorz, almostParallelTowardEndFirst);
		
		plot(outputDir, "parallel_as_splay", "Parallel Splay", fractDDW, true,
				firstHorz, almostParallelTowardMiddleFirst);
		
		plot(outputDir, "parallel_simple", "Parallel Simple", fractDDW, true,
				shorterFirstHorz, almostParallelTowardEndFirst);
		
//		FaultSystemRupSet u3RupSet = FaultSystemIO.loadRupSet(new File("/home/kevin/workspace/"
//				+ "opensha-ucerf3/src/scratch/UCERF3/data/scratch/InversionSolutions/"
//				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
//		SectionDistanceAzimuthCalculator u3DistCalc = new SectionDistanceAzimuthCalculator(
//				u3RupSet.getFaultSectionDataList());
//		ClusterRupture u3Complicated1 = ClusterRupture.forOrderedSingleStrandRupture(
//				u3RupSet.getFaultSectionDataForRupture(212600), u3DistCalc);
//		plotRupture(outputDir, "u3_complicated_1", u3Complicated1, "UCERF3 Rupture", false, false);
//		plotRupture(outputDir, "u3_complicated_az_1", u3Complicated1, "UCERF3 Rupture", true, false);
//		ClusterRupture u3Complicated2 = ClusterRupture.forOrderedSingleStrandRupture(
//				u3RupSet.getFaultSectionDataForRupture(237540), u3DistCalc);
//		plotRupture(outputDir, "u3_complicated_2", u3Complicated2, "UCERF3 Rupture", false, false);
//		plotRupture(outputDir, "u3_complicated_az_2", u3Complicated2, "UCERF3 Rupture", true, false);
//		ClusterRupture u3SAFPleito = ClusterRupture.forOrderedSingleStrandRupture(
//				u3RupSet.getFaultSectionDataForRupture(194942), u3DistCalc);
//		plotRupture(outputDir, "u3_saf_pleito", u3SAFPleito, "UCERF3 SAF & Pleito", false, false);
//		// now same thing, but full sections
//		Map<Integer, List<FaultSection>> parentSectsMap = new HashMap<>();
//		for (FaultSection sect : u3RupSet.getFaultSectionDataList()) {
//			List<FaultSection> sects = parentSectsMap.get(sect.getParentSectionId());
//			if (sects == null) {
//				sects = new ArrayList<>();
//				parentSectsMap.put(sect.getParentSectionId(), sects);
//			}
//			sects.add(sect);
//		}
//		ClusterRupture u3SAFPlietoSplay = getFullSectionsRup(u3SAFPleito, parentSectsMap, u3DistCalc);
//		plotRupture(outputDir, "u3_saf_pleito_splay", u3SAFPlietoSplay, "SAF & Pleito Splay", false, false);
		
		FaultSection azExampleAbove = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(-0.03d, 1.02d), new Location(0.15, 1.02d));
		
		plot(outputDir, "az_example_1", "Azimuth Example 1", fractDDW, true,
				firstHorz, azExampleAbove);
		
		FaultSection azExampleBelow = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(-0.15d, 1.02d), new Location(0.03, 1.02d));
		
		plot(outputDir, "az_example_2", "Azimuth Example 2", fractDDW, true,
				firstHorz, azExampleBelow);
		
		// now make a rupture building animation with a few small faults
		FaultSection smallHorz = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(0d, 0d), new Location(0d, 0.2d));
		FaultSection smallSE = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(-0.01d, 0.22d), new Location(-0.02, 0.28d));
		FaultSection smallNE = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(0.04d, 0.22d), new Location(0.1, 0.35d));
		FaultSection smallNConn = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(-0.038, 0.27d), new Location(0.09, 0.45d));
//		plot(outputDir, "small_example_full", "Small Example System", fractDDW, false,
//				smallHorz, smallSE, smallNE, smallNConn);
		SubSectBuilder rupBuild = new SubSectBuilder(fractDDW);
		rupBuild.addFault(smallHorz);
		rupBuild.addFault(smallSE);
		rupBuild.addFault(smallNE);
		rupBuild.addFault(smallNConn);
		List<PlausibilityFilter> filters = new ArrayList<>();
		SectionDistanceAzimuthCalculator animDistAzCalc = new SectionDistanceAzimuthCalculator(
				rupBuild.subSectsList);
		ClusterConnectionStrategy connStrat = new DistCutoffClosestSectClusterConnectionStrategy(
				rupBuild.subSectsList, animDistAzCalc, Double.POSITIVE_INFINITY);
		filters.add(new MinSectsPerParentFilter(2, false, false, connStrat));
		filters.add(new JumpAzimuthChangeFilter(
				new JumpAzimuthChangeFilter.SimpleAzimuthCalc(animDistAzCalc), 60f));
		animateRuptureBuilding(outputDir, "system_build_anim", rupBuild,
				filters, connStrat, 0, true, false, false, 1d);
	}
	
	private static void buildThinningAnimDemo(File outputDir) throws IOException {
		double upperDepth = 0d;
		double lowerDepth = 20d;
		double fractDDW = 0.5;
		
		int parentID = 1001;
		
		// now demo thinned permutation strategy
		FaultSection s1 = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(0d, 0d), new Location(0d, 0.95d));
		FaultSection s2 = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(0d, 1.05), new Location(0d, 1.95));
		FaultSection s3 = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(0d, 2.05), new Location(0d, 2.95));
		FaultSection s4 = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(0d, 3.05), new Location(0d, 3.95));
		FaultSection s5 = buildSect(parentID++, 85d, upperDepth, lowerDepth,
				new Location(0.05d, 3.65), new Location(0.55d, 3.9));

		SubSectBuilder rupBuild = new SubSectBuilder(fractDDW);
		rupBuild.addFault(s1);
		HashSet<FaultSection> s1Sects = new HashSet<>(rupBuild.subSectsList);
		rupBuild.addFault(s2);
		rupBuild.addFault(s3);
		rupBuild.addFault(s4);
		rupBuild.addFault(s5);
		SectionDistanceAzimuthCalculator animDistAzCalc = new SectionDistanceAzimuthCalculator(
				rupBuild.subSectsList);
		ClusterConnectionStrategy connStrat = new DistCutoffClosestSectClusterConnectionStrategy(
				rupBuild.subSectsList, animDistAzCalc, 20d);
		List<PlausibilityFilter> filters = new ArrayList<>();
		filters.add(new MinSectsPerParentFilter(2, false, false, connStrat));
		animateRuptureBuilding(outputDir, "system_build_anim_thin", rupBuild,
				filters, connStrat, new SectCountAdaptivePermutationStrategy(0.1f, true), 0, true, true, false, false, 2d,
				s1Sects);
		animateRuptureBuilding(outputDir, "system_build_anim_no_thin", rupBuild,
				filters, connStrat, new ExhaustiveClusterPermuationStrategy(), 0, true, true, false, false, 2d,
				s1Sects);
	}
	
	private static void buildPermStratDemos(File outputDir) throws IOException {
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		double upperDepth = 0d;
		double lowerDepth = 10d;
		double fractDDW = 0.5;
		int parentID = 1001;
		
		int numRupsToPlot = 20;
		
		double dip = 84d;
		
//		float fract = 0.05f;
//		double x1 = 100d;
//		double x2 = x1+5d;
//		double x3 = x2+60d;
//		double x4 = x2+100d;
		
//		float fract = 0.1f;
//		double x1 = 50d;
//		double x2 = x1+2d;
//		double x3 = x2+35d;
//		double x4 = x3+15d;
//		double x5 = x2+75d;
//		double y1 = 0d;
//		double y2 = 2d;
//		double y3 = -1d;
//		double y4 = 5d;
		
		float fract = 0.1f;
		double x1 = 50d;
		double x2 = x1+2d;
		double x3 = x2+35d;
		double x4 = x3+15d;
		double x5 = x2+75d;
		double y1 = 0d;
		double y2 = 0d;
		double y3 = -3d;
		double y4 = 2d;
		
		FaultSection s1 = buildSect(parentID++, dip, upperDepth, lowerDepth,
				loc(0d, y1), loc(x1, y1));
		s1.setAveRake(180d);
		FaultSection s2 = buildSect(parentID++, dip, upperDepth, lowerDepth,
				loc(x2, y1), loc(x5, y4));
		s2.setAveRake(180d);
		FaultSection s3 = buildSect(parentID++, dip, upperDepth, lowerDepth,
				loc(x3, y2), loc(x4, y3));
		s3.setAveRake(180d);

		SubSectBuilder rupBuild = new SubSectBuilder(fractDDW);
		rupBuild.addFault(s1);
		HashSet<FaultSection> s1Sects = new HashSet<>(rupBuild.subSectsList);
		System.out.println("S1 has "+s1Sects.size()+" sects");
		rupBuild.addFault(s2);
		rupBuild.addFault(s3);
		
		SectionDistanceAzimuthCalculator animDistAzCalc = new SectionDistanceAzimuthCalculator(
				rupBuild.subSectsList);
		ClusterConnectionStrategy connStrat = new DistCutoffClosestSectClusterConnectionStrategy(
				rupBuild.subSectsList, animDistAzCalc, 5d);
		List<PlausibilityFilter> filters = new ArrayList<>();
//		filters.add(new MinSectsPerParentFilter(2, false, false, connStrat));

		List<ClusterPermutationStrategy> permStrats = new ArrayList<>();
		List<String> prefixes = new ArrayList<>();
		
		permStrats.add(new SectCountAdaptivePermutationStrategy(fract, true));
		prefixes.add("perm_strat_adaptive");
		permStrats.add(new ExhaustiveClusterPermuationStrategy());
		prefixes.add("perm_strat_exhaustive");
		permStrats.add(new ConnectionPointsPermutationStrategy());
		prefixes.add("perm_strat_conn_points");
		
		PlotCurveCharacterstics rupChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK);
		PlotCurveCharacterstics noRupChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.LIGHT_GRAY);
		PlotCurveCharacterstics traceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY);
		PlotCurveCharacterstics whiteChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.WHITE);
		
		for (int p=0; p<permStrats.size(); p++) {
			ClusterPermutationStrategy permStrat = permStrats.get(p);
			
//			String title = "Permuation Strategy: "+permStrat.getName();
			String title = permStrat.getName();
			System.out.println(title);
			
			// build ruptures
			List<PlausibilityFilter> myFilters = new ArrayList<>(filters);
			if (permStrat instanceof SectCountAdaptivePermutationStrategy)
				myFilters.add(((SectCountAdaptivePermutationStrategy)permStrat).buildConnPointCleanupFilter(connStrat));
			ClusterRuptureBuilder builder = new ClusterRuptureBuilder(connStrat.getClusters(), myFilters, 0);
			
			List<ClusterRupture> rups = builder.build(permStrat);
			List<ClusterRupture> matchingRups = new ArrayList<>();
			for (ClusterRupture rup : rups) {
				if (rup.clusters[0].parentSectionID != s1.getSectionId() || rup.clusters[0].subSects.size() != s1Sects.size())
					continue;
				matchingRups.add(rup);
				if (matchingRups.size() == numRupsToPlot)
					break;
			}
			
			System.out.println("Plotting "+matchingRups.size()+" ruptures");
			
			List<PlotSpec> specs = new ArrayList<>();
			List<String> titles = new ArrayList<>();
			for (ClusterRupture rup : matchingRups) {
				List<XY_DataSet> funcs = new ArrayList<>();
				List<PlotCurveCharacterstics> chars = new ArrayList<>();
				
				titles.add("Rupture "+(titles.size()+1)+", "+rup.getTotalNumSects()+" Sections");
				
				boolean firstRup = true;
				boolean firstNoRup = true;
				for (FaultSection sect : rupBuild.subSectsList) {
					if (rup.contains(sect)) {
						plotSection(sect, funcs, chars, rupChar, traceChar);
						if (firstRup)
							funcs.get(funcs.size()-1).setName("Ruptured Sections");
						firstRup = false;
					} else {
						plotSection(sect, funcs, chars, noRupChar, traceChar);
						if (firstNoRup)
							funcs.get(funcs.size()-1).setName("Other Sections");
						firstNoRup = false;
					}
				}
				boolean firstJump = true;
				for (Jump jump : rup.getJumpsIterable()) {
					List<XY_DataSet> arrow = line(jump.fromSection, jump.toSection, true, 1d, 1d);
					if (firstJump)
						arrow.get(0).setName("Jump");
					for (XY_DataSet xy : arrow) {
						funcs.add(xy);
						chars.add(reg_jump_char);
					}
				}
				
				PlotSpec spec = new PlotSpec(funcs, chars, title, null, " ");
				spec.setLegendVisible(specs.isEmpty());
				specs.add(spec);
			}
			while (specs.size() < numRupsToPlot) {
				List<XY_DataSet> funcs = new ArrayList<>();
				List<PlotCurveCharacterstics> chars = new ArrayList<>();
				
				titles.add(" ");
				for (FaultSection sect : rupBuild.subSectsList) {
					plotSection(sect, funcs, chars, whiteChar, whiteChar);
				}
				
				PlotSpec spec = new PlotSpec(funcs, chars, title, null, " ");
				spec.setLegendVisible(false);
				specs.add(spec);
			}
			
			plotTileMulti(outputDir, prefixes.get(p), specs, title, titles, 0.01, 16, 0);
		}
	}
	
	private static XY_DataSet cloneOffset(XY_DataSet xy, double xOff, double yOff) {
		DefaultXY_DataSet ret = new DefaultXY_DataSet();
		ret.setName(xy.getName());
		for (Point2D pt : xy)
			ret.set(pt.getX() + xOff, pt.getY() + yOff);
		return ret;
	}
	
	private static void plotTileMulti(File outputDir, String prefix, List<PlotSpec> specs, String title, List<String> titles,
			double yDelta, int titleFontSize, int refRangeIndex) throws IOException {
		List<XY_DataSet> combFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> combChars = new ArrayList<>();
		
		double yOffset = 0d;
		
		List<XYTextAnnotation> anns = new ArrayList<>();
		
		Double prevBottom = null;
		
		MinMaxAveTracker refLatTrack = null, refLonTrack = null;
		if (refRangeIndex >= 0) {
			refLatTrack = new MinMaxAveTracker();
			refLonTrack = new MinMaxAveTracker();
			for (PlotElement xy : specs.get(refRangeIndex).getPlotElems()) {
				for (Point2D pt : (XY_DataSet)xy) {
					refLatTrack.addValue(pt.getY());
					refLonTrack.addValue(pt.getX());
				}
			}
		}
		
		for (int i=0; i < specs.size(); i++) {
			PlotSpec spec = specs.get(i);
			
			MinMaxAveTracker subLatTrack = refLatTrack, subLonTrack = refLonTrack;
			if (refRangeIndex < 0) {
				subLatTrack = new MinMaxAveTracker();
				subLonTrack = new MinMaxAveTracker();
				for (PlotElement xy : spec.getPlotElems()) {
					for (Point2D pt : (XY_DataSet)xy) {
						subLatTrack.addValue(pt.getY());
						subLonTrack.addValue(pt.getX());
					}
					if (refRangeIndex >= 0 && i == 0) {
						System.out.println("REFERENCE RANGES");
						System.out.println("\tX:");
					}
				}
			}
			
			
			for (XY_DataSet func : (List<? extends XY_DataSet>)spec.getPlotElems()) {
				XY_DataSet modFunc = cloneOffset(func, 0d, yOffset);
				if (!spec.isLegendVisible())
					modFunc.setName(null);
				combFuncs.add(modFunc);
			}
			combChars.addAll(spec.getChars());
			
			if (titles != null) {
				double titleX = subLonTrack.getMin() + 0.5*(subLonTrack.getMax() - subLonTrack.getMin());
//				double titleY = subLatTrack.getMax() + yOffset + 1.5*yDelta;
//				double titleY = yOffset + subLatTrack.getMax() + yDelta;
				
				double myTop = subLatTrack.getMax() + yOffset;
				if (prevBottom == null)
					prevBottom = myTop + yDelta;
				double titleY = 0.5*(myTop + prevBottom);
				
				XYTextAnnotation ann = new XYTextAnnotation(titles.get(i), titleX, titleY);
				ann.setTextAnchor(TextAnchor.CENTER);
//				ann.setTextAnchor(TextAnchor.TOP_CENTER);
				ann.setFont(new Font(Font.SANS_SERIF, Font.BOLD, titleFontSize));
				anns.add(ann);
			}
			
			List<XYAnnotation> specAnns = spec.getPlotAnnotations();
			if (specAnns != null) {
				for (XYAnnotation ann : specAnns) {
					if (ann instanceof XYTextAnnotation) {
						XYTextAnnotation tAnn = (XYTextAnnotation)ann;
						try {
							tAnn = (XYTextAnnotation)tAnn.clone();
						} catch (CloneNotSupportedException e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
						tAnn.setY(tAnn.getY() + yOffset);
						anns.add(tAnn);
					}
				}
			}
			
			prevBottom = subLatTrack.getMin() + yOffset;
			
			yOffset -= (subLatTrack.getMax() - subLatTrack.getMin());
			yOffset -= yDelta;
		}
		
		double minLon, maxLon, minLat, maxLat;
		
		if (refRangeIndex < 0) {
			MinMaxAveTracker latTrack = new MinMaxAveTracker();
			MinMaxAveTracker lonTrack = new MinMaxAveTracker();
			for (PlotElement xy : combFuncs) {
				for (Point2D pt : (XY_DataSet)xy) {
					latTrack.addValue(pt.getY());
					lonTrack.addValue(pt.getX());
				}
			}
			
			minLon = lonTrack.getMin();
			maxLon = lonTrack.getMax();
			minLat = latTrack.getMin();
			maxLat = latTrack.getMax();
		} else {
			minLon = refLonTrack.getMin();
			maxLon = refLonTrack.getMax();
			minLat = refLatTrack.getMin();
			maxLat = refLatTrack.getMax();
			
			minLat -= (specs.size()-1)*(yDelta+maxLat-minLat);
		}
		
		double maxDelta = Math.max(maxLat - minLat, maxLon - minLon);
		
		double buffer = Math.min(maxDelta*0.1, 0.05);
		
		maxLat += titles == null ? buffer : buffer*1.2;
		minLat -= buffer;
		maxLon += buffer;
		minLon -= buffer;
		
		double latSpan = maxLat - minLat;
		double lonSpan = maxLon - minLon;
		int width = 800;
		double plotWidth = width - 70;
		double plotHeight = plotWidth*latSpan/lonSpan;
		int height = 150 + (int)plotHeight;
		
		Range xRange = new Range(minLon, maxLon);
		Range yRange = new Range(minLat, maxLat);
		
		PlotSpec combSpec = new PlotSpec(combFuncs, combChars, title, null, " ");
		combSpec.setLegendVisible(true);
		combSpec.setPlotAnnotations(anns);
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		gp.drawGraphPanel(combSpec, false, false, xRange, yRange);
		PlotUtils.setAxisVisible(gp, false, false);
		PlotUtils.setGridLinesVisible(gp, false, false);
		
		PlotUtils.writePlots(outputDir, prefix, gp, width, -1, true, write_pdfs, false);
		
//		HeadlessGraphPanel gp = new HeadlessGraphPanel();
//		gp.setTickLabelFontSize(18);
//		gp.setAxisLabelFontSize(24);
//		gp.setPlotLabelFontSize(24);
//		gp.setLegendFontSize(20);
//		gp.setBackgroundColor(Color.WHITE);
//		
//		gp.drawGraphPanel(combSpec, false, false, xRange, yRange);
//		gp.getXAxis().setTickLabelsVisible(false);
//		gp.getYAxis().setTickLabelsVisible(false);
//		gp.getPlot().setDomainGridlinesVisible(false);
//		gp.getPlot().setRangeGridlinesVisible(false);
//		
//		File file = new File(outputDir, prefix);
//		gp.getChartPanel().setSize(width, height);
//		gp.saveAsPNG(file.getAbsolutePath()+".png");
//		if (write_pdfs)
//			gp.saveAsPDF(file.getAbsolutePath()+".pdf");
	}
	
	private static void buildConnStratDemo(File outputDir) throws IOException {
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		double upperDepth = 0d;
		double lowerDepth = 10d;
		double fractDDW = 0.5;
		int parentID = 1001;
		
		double dip = 88d;
		double maxDist = 10d;
		float r0 = 5f;
		
		FaultSection s1 = buildSect(parentID++, dip, upperDepth, lowerDepth,
//				new Location(0d, 0d), new Location(0d, 0.98d));
				loc(0d, 0d), loc(31d, 0d));
		s1.setAveRake(180d);
		FaultSection s2 = buildSect(parentID++, dip, upperDepth, lowerDepth,
//				new Location(-0.05d, 0.48d), new Location(-0.04999d, 0.75d), new Location(-0.05d, 1d), new Location(-0.3d, 1.20d));
//				loc(15d, -4d), loc(22.5d, -3.965d), loc(30d, -4d), loc(40d, -12d));
				loc(15d, -4d), loc(22.5d, -3.5d), loc(30d, -4d), loc(40d, -12d));
//				loc(10d, -4d), loc(20d, -3.96d), loc(25d, -4.2d), loc(30d, -4.8d), loc(45d, -15d));
		s2.setAveRake(180d);
		
		SubSectBuilder rupBuild = new SubSectBuilder(fractDDW);
//		rupBuild.addFault(s1);
		FaultSection[] startsWithSects = new FaultSection[2];
		rupBuild.addFault(s2);
		startsWithSects[0] = rupBuild.subSectsList.get(rupBuild.subSectsList.size()-1);
		int startNext = rupBuild.subSectsList.size();
		rupBuild.addFault(s1);
		startsWithSects[1] = rupBuild.subSectsList.get(startNext);
		
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(rupBuild.subSectsList);
		
		List<PlausibilityFilter> filters = new ArrayList<>();
		SubSectStiffnessCalculator stiffCalc = new SubSectStiffnessCalculator(rupBuild.subSectsList, 2d, 30000, 30000, 0.5d);
		AggregatedStiffnessCalculator aggCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffCalc, false,
				AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM);
		filters.add(new CumulativeProbabilityFilter(0.5f, new CoulombSectRatioProb(aggCalc, 2)));
//		filters.add(new CumulativeProbabilityFilter(0.5f, new CoulombSectRatioProb(aggCalc, 2, true, (float)maxDist, distAzCalc)));
//		filters.add(new PathPlausibilityFilter(new CumulativeProbPathEvaluator(
//				0.5f, PlausibilityResult.FAIL_HARD_STOP, new CoulombSectRatioProb(aggCalc, 2))));
//		filters.add(new ClusterCoulombCompatibilityFilter(aggCalc, 0f));
//		filters.add(new PathPlausibilityFilter(new ClusterCoulombPathEvaluator(aggCalc,
//				com.google.common.collect.Range.atLeast(0f), PlausibilityResult.FAIL_HARD_STOP)));
		filters.add(new StartsWithFitler(startsWithSects));
		
		List<ClusterConnectionStrategy> connStrats = new ArrayList<>();
		List<String> connPrefixes = new ArrayList<>();
		List<String> connTitles = new ArrayList<>();
		
		connStrats.add(new DistCutoffClosestSectClusterConnectionStrategy(rupBuild.subSectsList, distAzCalc, maxDist));
		connPrefixes.add("conn_min_dist");
		connTitles.add("Minimum Distance");
		
		JumpSelector singleSelect = new FallbackJumpSelector(true,
				new PassesMinimizeFailedSelector(), new BestScalarSelector(2d));
		JumpSelector multiSelect = new FallbackJumpSelector(false,
				new PassesMinimizeFailedSelector(),
				new AllowMultiEndsSelector(r0, new FallbackJumpSelector(true, new BestScalarSelector(2d))));
		JumpSelector allSelect = new AnyPassSelector();
		JumpSelector withinSelect = new WithinDistanceSelector((float)maxDist);
		
		connStrats.add(new PlausibleClusterConnectionStrategy(rupBuild.subSectsList, distAzCalc, maxDist, singleSelect, filters));
		connPrefixes.add("conn_plausible_single");
		connTitles.add("Path-Optimized, Plausible, Single Connection");
		
		connStrats.add(new PlausibleClusterConnectionStrategy(rupBuild.subSectsList, distAzCalc, maxDist, multiSelect, filters));
		connPrefixes.add("conn_plausible_multi");
		connTitles.add("Path-Optimized, Plausible, Multiple Connections");
		
		List<Jump> allPossibleJumps = new ArrayList<>();
		PlausibleClusterConnectionStrategy all = new PlausibleClusterConnectionStrategy(rupBuild.subSectsList, distAzCalc, maxDist, allSelect, filters);
		allPossibleJumps.addAll(all.getClusters().get(0).getConnections());
		System.out.println("Plotting "+allPossibleJumps.size()+" extra possible jumps");
		
		PlausibleClusterConnectionStrategy allDist = new PlausibleClusterConnectionStrategy(rupBuild.subSectsList, distAzCalc, maxDist, withinSelect, filters);
		List<Jump> failedJumps = new ArrayList<>();
		for (Jump jump : allDist.getClusters().get(0).getConnections())
			if (!allPossibleJumps.contains(jump))
				failedJumps.add(jump);
		System.out.println("Plotting "+failedJumps.size()+" extra failed jumps");
		
		List<PlotSpec> specs = new ArrayList<>();
		
		for (int i=0; i<connStrats.size(); i++)
			specs.add(plotConnStrat(outputDir, connPrefixes.get(i), connTitles.get(i), connStrats.get(i), filters, false, allPossibleJumps, failedJumps));
		
		for (int i=1; i<specs.size(); i++)
			specs.get(i).setLegendVisible(false);
		plotTileMulti(outputDir, "conn_combined", specs, "Connection Strategy Example", connTitles, 0.025, 22, 0);
	}
	
	private static void buildPathCoulombDemos(File outputDir) throws IOException {
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		double upperDepth = 0d;
		double lowerDepth = 10d;
		double fractDDW = 0.5;
		int parentID = 1001;
		
		double dip = 88d;
		
		// overlapping SS
		FaultSection s1 = buildSect(parentID++, dip, upperDepth, lowerDepth,
				loc(0d, 0d), loc(15d, 0d));
		s1.setAveRake(180d);
		FaultSection s2 = buildSect(parentID++, dip, upperDepth, lowerDepth,
//				loc(17, -1d), loc(32, -1d));
				loc(13, -1d), loc(33, -1d));
		s2.setAveRake(180d);
//		FaultSection s3 = buildSect(parentID++, dip, upperDepth, lowerDepth,
//				loc(x3, 2d), loc(x4, -1d));
//		s3.setAveRake(180d);
		
		SubSectBuilder rupBuild = new SubSectBuilder(fractDDW);
		rupBuild.addFault(s1);
		HashSet<FaultSection> s1Sects = new HashSet<>(rupBuild.subSectsList);
		System.out.println("S1 has "+s1Sects.size()+" sects");
		rupBuild.addFault(s2);
//		rupBuild.addFault(s3);
		
		buildPathCoulombDemo(outputDir, "cff_ratio_overlapping_ss", StiffnessType.CFF.getName()+" Ratio, Simple Path", false, 10d, rupBuild);
		buildPathCoulombDemo(outputDir, "cff_ratio_overlapping_ss_favjump", StiffnessType.CFF.getName()+" Ratio, Favorable Path", true, 10d, rupBuild);
	}
	
	private static void buildPathCoulombDemo(File outputDir, String prefix, String title, boolean jumpToMostFavorable,
			double maxJumpDist, SubSectBuilder rupBuild) throws IOException {
		int numDenominatorSubsects = 2;
		float threshold = 0.5f;
		
		CPT favCPT = new CPT();
		favCPT.add(new CPTVal(0f, Color.BLUE, 0.5f, Color.BLUE));
		favCPT.add(new CPTVal(0.5f, Color.RED, 1f, Color.RED.darker().darker()));
		
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(
				rupBuild.subSectsList);
		ClusterConnectionStrategy connStrat = new DistCutoffClosestSectClusterConnectionStrategy(
				rupBuild.subSectsList, distAzCalc, maxJumpDist);
		
		SubSectStiffnessCalculator stiffCalc = new SubSectStiffnessCalculator(rupBuild.subSectsList, 2d, 30000, 30000, 0.5d);
		AggregatedStiffnessCalculator aggCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffCalc, false,
				AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM);
		
		DecimalFormat df = new DecimalFormat("0.00");
		
		List<FaultSubsectionCluster> clusters = connStrat.getClusters();
		for (int n=0; n<clusters.size(); n++) {
			System.out.println("Nucleation cluster: "+clusters.get(n));
			ClusterRupture rup = new ClusterRupture(clusters.get(n));
			if (n == clusters.size()-1)
				rup = rup.reversed();
			for (int i=n+1; i<clusters.size(); i++)
				rup = rup.take(clusters.get(i-1).getConnectionsTo(clusters.get(i)).iterator().next());
			for (int i=n; --i>=0;) {
				FaultSubsectionCluster prev = clusters.get(i+1);
				FaultSubsectionCluster to = clusters.get(i);
				Jump jump = prev.getConnectionsTo(to).iterator().next();
				if (rup.clusters.length == 1) {
					// prev was reversed, reverse that and this
					jump = new Jump(jump.fromSection, jump.fromCluster.reversed(), jump.toSection, jump.toCluster.reversed(), jump.distance);
				} else {
					// reverse this
					jump = new Jump(jump.fromSection, jump.fromCluster, jump.toSection, jump.toCluster.reversed(), jump.distance);
				}
				rup = rup.take(jump);
			}
			System.out.println("Rupture: "+rup);
				
			SectionPathNavigator nav;
			if (jumpToMostFavorable)
				nav = new CoulombFavorableSectionPathNavigator(rup.clusters[0].subSects, rup.getTreeNavigator(),
						aggCalc, com.google.common.collect.Range.atLeast(0f), distAzCalc, (float)maxJumpDist);
			else
				nav = new SectionPathNavigator(rup.clusters[0].subSects, rup.getTreeNavigator());
			nav.setVerbose(true);

			double prob = 1d;

			List<FaultSection> currentSects = null;
			Set<PathAddition> nextAdds = null;

			PlotCurveCharacterstics rupChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK);
			PlotCurveCharacterstics futureChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.LIGHT_GRAY);
			PlotCurveCharacterstics traceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY);
			PlotCurveCharacterstics sourceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.GREEN.darker());
			double arrowLen = 0.4;
			PlotCurveCharacterstics jumpChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, new Color(210, 105, 30, 127));

			boolean firstRupSect = true;
			boolean firstSourceSect = true;
			boolean firstEffective = true;

			XY_DataSet lastRecieverFunc = null;
			XY_DataSet bestRecieverFunc = null;
			double bestReceiverScore = -1d;

			List<PlotSpec> specs = new ArrayList<>();
			List<String> titles = new ArrayList<>();

			while (true) {
				if (currentSects == null) {
					List<XY_DataSet> funcs = new ArrayList<>();
					List<PlotCurveCharacterstics> chars = new ArrayList<>();
					
					// first one, plot raw rupture
					for (FaultSubsectionCluster cluster : rup.clusters) {
						for (FaultSection sect : cluster.subSects) {
							plotSection(sect, funcs, chars, rupChar, traceChar);
							if (firstRupSect)
								funcs.get(funcs.size()-1).setName("Full Rupture");
							firstRupSect = false;
						}
					}
					titles.add("Full Rupture");
					
					for (Jump jump : rup.getJumpsIterable()) {
						for (XY_DataSet xy : line(jump.fromSection, jump.toSection, true, 1d, arrowLen)) {
							funcs.add(xy);
							chars.add(jumpChar);
						}
					}

					PlotSpec spec = new PlotSpec(funcs, chars, null, null, " ");
					spec.setLegendVisible(true);

					specs.add(spec);
				} else {
					for (PathAddition add : nextAdds) {
						List<XY_DataSet> funcs = new ArrayList<>();
						List<PlotCurveCharacterstics> chars = new ArrayList<>();
						
						// plot sources
						HashSet<FaultSection> processed = new HashSet<>();
						for (FaultSection source : currentSects) {
							plotSection(source, funcs, chars, sourceChar, traceChar);
							if (firstSourceSect)
								funcs.get(funcs.size()-1).setName("Sources");
							firstSourceSect = false;
							processed.add(source);
						}
						
						Preconditions.checkState(add.toSects.size() == 1);
						FaultSection receiver = add.toSects.iterator().next();
						HighestNTracker track = new HighestNTracker(numDenominatorSubsects);
						for (FaultSection source : currentSects)
							track.addValue(aggCalc.calc(source, receiver));
						double myProb = track.getSum()/Math.abs(track.getSumHighest());
						if (myProb < 0)
							myProb = 0;
						else if (myProb > 1)
							myProb = 1;
						System.out.println("Probability of adding "+receiver.getSectionId()+" with "
								+currentSects.size()+" sources: "+track.getSum()+"/|"+track.getSumHighest()+"| = "+myProb);
						prob *= myProb;

						Color color = favCPT.getColor((float)myProb);
						plotSection(receiver, funcs, chars, new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, color), traceChar);
						processed.add(receiver);
						if (myProb > bestReceiverScore) {
							bestReceiverScore = myProb;
							bestRecieverFunc = funcs.get(funcs.size()-1);
						}
						for (XY_DataSet xy : line(add.fromSect, receiver, true, 1d, arrowLen)) {
							funcs.add(xy);
							chars.add(jumpChar);
							if (firstEffective)
								xy.setName("Effective Jump");
							firstEffective = false;
						}
						
						String str = "R"+specs.size()+"="+df.format(track.getSum())+"/|"+df.format(track.getSumHighest())+"|="+df.format(myProb);
						str += "; Cumulative P="+df.format(prob);
						
						for (FaultSection sect : rupBuild.subSectsList) {
							if (!processed.contains(sect)) {
								plotSection(sect, funcs, chars, futureChar, traceChar);
								lastRecieverFunc = funcs.get(funcs.size()-1);
							}
						}
						
						PlotSpec spec = new PlotSpec(funcs, chars, null, null, " ");
						spec.setLegendVisible(true);

						specs.add(spec);
						String passStr = (float)prob >= threshold ? "PASS" : "FAIL";
						titles.add(str);
					}
				}

				currentSects = nav.getCurrentSects();
				nextAdds = nav.getNextAdditions();
				System.out.println("Have "+nextAdds.size()+" nextAdds");
				if (nextAdds.isEmpty())
					break;
			}
			int expectedNum = rupBuild.subSectsList.size()-clusters.get(n).subSects.size()+1;
			PlotCurveCharacterstics whiteChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.WHITE);
			
			bestRecieverFunc.setName("Receivers");
			lastRecieverFunc.setName("Future Sections");
			
			plotTileMulti(outputDir, prefix+"_"+n, specs, title, titles, 0.02, 20, 0);
		}
	}
	
	private static Location loc(double x, double y) {
		Location loc = new Location(0d, 0d);
		if (x != 0d)
			loc = LocationUtils.location(loc, 0.5*Math.PI, x);
		if (y != 0d)
			loc = LocationUtils.location(loc, 0d, y);
		return loc;
	}
	
	private static class RelativeProbWrapper extends AbstractRelativeProb {

		private AbstractRelativeProb relProb;
		
		private ClusterRupture curRup;
		
		private List<Collection<? extends FaultSection>> currentSectsList;
		private List<PathAddition> availableAdditionsList;
		private List<Double> valuesList;
		
		private List<PathAddition> actualAdditionsList;
		private List<Double> additionProbs;
		
		private boolean disableSkip = false;

		public RelativeProbWrapper(AbstractRelativeProb relProb) {
			super(relProb.getConnStrat(), relProb.isAllowNegative(), relProb.isRelativeToBest());
			this.relProb = relProb;
		}

		@Override
		public PathNavigator getPathNav(ClusterRupture rupture, FaultSubsectionCluster nucleationCluster) {
			return relProb.getPathNav(rupture, nucleationCluster);
		}

		@Override
		public HashSet<FaultSubsectionCluster> getSkipToClusters(ClusterRupture rupture) {
			if (disableSkip)
				return null;
			return relProb.getSkipToClusters(rupture);
		}

		@Override
		public PathAddition targetJumpToAddition(Collection<? extends FaultSection> curSects,
				PathAddition testAddition, Jump alternateJump) {
			return relProb.targetJumpToAddition(curSects, testAddition, alternateJump);
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return relProb.isDirectional(splayed);
		}

		@Override
		public String getName() {
			return relProb.getName();
		}
		
		private void reset() {
			currentSectsList = new ArrayList<>();
			availableAdditionsList = new ArrayList<>();
			valuesList = new ArrayList<>();
			
			actualAdditionsList = new ArrayList<>();
			additionProbs = new ArrayList<>();
		}

		@Override
		public double calcAdditionValue(ClusterRupture fullRupture, Collection<? extends FaultSection> currentSects,
				PathAddition addition) {
			if (fullRupture != curRup) {
				System.out.println("Tracking additions for a new rupture: "+fullRupture);
				curRup = fullRupture;
				
				reset();
			}
			double value = relProb.calcAdditionValue(fullRupture, currentSects, addition);
			
			currentSectsList.add(new ArrayList<>(currentSects));
			availableAdditionsList.add(addition);
			valuesList.add(value);
			
			return value;
		}

		@Override
		protected double calcAdditionProb(ClusterRupture rupture, List<FaultSection> curSects, PathAddition add,
				boolean verbose) {
			if (rupture != curRup) {
				System.out.println("Tracking additions for a new rupture: "+rupture);
				curRup = rupture;
				
				reset();
			}
			double prob = super.calcAdditionProb(rupture, curSects, add, verbose);
			System.out.println("**********ADD "+add+"+: "+prob);
			actualAdditionsList.add(add);
			additionProbs.add(prob);
			
			return prob;
		}

		@Override
		public boolean isAddFullClusters() {
			return relProb.isAddFullClusters();
		}
		
	}
	
	private static SubSectBuilder buildRelativeDemoSystem() {
		double upperDepth = 0d;
		double lowerDepth = 10d;
		double fractDDW = 0.5;
		int parentID = 1001;
		
		double ssDip = 85d;
		
		FaultSection s1 = buildSect(parentID++, ssDip, upperDepth, lowerDepth,
				loc(0d, 0d), loc(120d, 0d));
		s1.setAveRake(180d);
		s1.setAveSlipRate(20d);
		
		FaultSection s2 = buildSect(parentID++, ssDip, upperDepth, lowerDepth,
//				loc(33d, -1d), loc(75d, -15d));
				loc(33d, -1d), loc(80d, -17d));
//				loc(34d, -1d), loc(80d, -13d));
		s2.setAveRake(180d);
		s2.setAveSlipRate(10d);
		
//		FaultSection s3 = buildSect(parentID++, 60d, upperDepth, lowerDepth,
////				new Location(-0.05d, 0.48d), new Location(-0.04999d, 0.75d), new Location(-0.05d, 1d), new Location(-0.3d, 1.20d));
////				loc(15d, -4d), loc(22.5d, -3.965d), loc(30d, -4d), loc(40d, -12d));
//				loc(90d, -6d), loc(65, -11d));
////				loc(90d, -6d), loc(50, -18d));
////				loc(90d, -8d), loc(50, -12d));
////				loc(10d, -4d), loc(20d, -3.96d), loc(25d, -4.2d), loc(30d, -4.8d), loc(45d, -15d));
//		s3.setAveRake(-90d);
//		s3.setAveSlipRate(1d);
		
//		FaultSection s3 = buildSect(parentID++, ssDip, upperDepth, lowerDepth,
////				new Location(-0.05d, 0.48d), new Location(-0.04999d, 0.75d), new Location(-0.05d, 1d), new Location(-0.3d, 1.20d));
////				loc(15d, -4d), loc(22.5d, -3.965d), loc(30d, -4d), loc(40d, -12d));
//				loc(90d, -2d), loc(71, -10d));
////				loc(90d, -6d), loc(50, -18d));
////				loc(90d, -8d), loc(50, -12d));
////				loc(10d, -4d), loc(20d, -3.96d), loc(25d, -4.2d), loc(30d, -4.8d), loc(45d, -15d));
//		s3.setAveRake(0);
//		s3.setAveSlipRate(1d);
		
		FaultSection s3 = buildSect(parentID++, ssDip, upperDepth, lowerDepth,
//				new Location(-0.05d, 0.48d), new Location(-0.04999d, 0.75d), new Location(-0.05d, 1d), new Location(-0.3d, 1.20d));
//				loc(15d, -4d), loc(22.5d, -3.965d), loc(30d, -4d), loc(40d, -12d));
//				loc(90d, -2d), loc(71, -10d));
//				loc(90d, -6d), loc(50, -18d));
//				loc(90d, -8d), loc(50, -12d));
//				loc(10d, -4d), loc(20d, -3.96d), loc(25d, -4.2d), loc(30d, -4.8d), loc(45d, -15d));
				loc(66, -11.2d), loc(70, -10.5d), loc(73, -10d), loc(80, -3d), loc(90.3d, -1d));
		s3.setAveRake(180);
		s3.setAveSlipRate(1d);
		
//		FaultSection s3 = buildSect(parentID++, ssDip, upperDepth, lowerDepth,
////				new Location(0d, 0d), new Location(0d, 0.98d));
//				loc(0d, 0d), loc(50d, 0d));
//		s3.setAveRake(180d);
//		s3.setAveSlipRate(20d);
		
		SubSectBuilder rupBuild = new SubSectBuilder(fractDDW);
		rupBuild.addFault(s1);
		rupBuild.addFault(s2);
		rupBuild.addFault(s3);
		
		return rupBuild;
	}
	
	private static class StartsWithFitler implements PlausibilityFilter {
		
		private FaultSection[] firstSects;

		public StartsWithFitler(FaultSection... firstSects) {
			this.firstSects = firstSects;
		}

		@Override
		public String getShortName() {
			return "Starts With";
		}

		@Override
		public String getName() {
			return "Starts With";
		}

		@Override
		public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
			for (FaultSection firstSect : firstSects)
				if (rupture.clusters[0].startSect.equals(firstSect))
					return PlausibilityResult.PASS;
			return PlausibilityResult.FAIL_HARD_STOP;
		}
		
	}
	
	private static List<ClusterRupture> getRelProbExampleRups(List<FaultSubsectionCluster> clusters) {
		List<PlausibilityFilter> buildFilters = new ArrayList<>();
		buildFilters.add(new StartsWithFitler(clusters.get(0).startSect));
		List<ClusterRupture> allRups = new ClusterRuptureBuilder(clusters, buildFilters, 0).build(new ConnectionPointsPermutationStrategy());
		
		System.out.println("Building relative probability example ruptures");
		
		List<ClusterRupture> targetRups = new ArrayList<>();
		
		// largest without any jumps
		ClusterRupture largeNoJump = null;
		for (ClusterRupture rup : allRups) {
			if (rup.getTotalNumJumps() > 0)
				continue;
			if (largeNoJump == null)
				largeNoJump = rup;
			else if (rup.getTotalNumSects() > largeNoJump.getTotalNumSects())
				largeNoJump = rup;
		}
		System.out.println("Largest without a jump: "+largeNoJump);
		targetRups.add(largeNoJump);
		
		// full 2nd cluster, 1 jump
		ClusterRupture full2nd = null;
		for (ClusterRupture rup : allRups) {
			if (rup.clusters.length != 2)
				continue;
			if (rup.clusters[1].parentSectionID != clusters.get(1).parentSectionID)
				continue;
			if (rup.clusters[1].subSects.size() != clusters.get(1).subSects.size())
				continue;
			if (full2nd == null)
				full2nd = rup;
			else if (rup.getTotalNumSects() > full2nd.getTotalNumSects())
				full2nd = rup;
		}
		targetRups.add(full2nd);
		System.out.println("Largest full 2nd: "+full2nd);
		
		// largest with 1->2->3
		ClusterRupture full3rd = null;
		for (ClusterRupture rup : allRups) {
			if (rup.clusters.length != 3)
				continue;
			if (rup.clusters[1].parentSectionID != clusters.get(1).parentSectionID)
				continue;
			if (rup.clusters[2].parentSectionID != clusters.get(2).parentSectionID)
				continue;
			if (rup.clusters[2].subSects.size() != clusters.get(2).subSects.size())
				continue;
			if (full3rd == null)
				full3rd = rup;
			else if (rup.getTotalNumSects() > full3rd.getTotalNumSects())
				full3rd = rup;
		}
		targetRups.add(full3rd);
		System.out.println("Largest full 3rd: "+full3rd);
		
		// largest 3 jump
		ClusterRupture allJump = null;
		for (ClusterRupture rup : allRups) {
			if (rup.clusters.length != 4)
				continue;
			if (rup.clusters[1].parentSectionID != clusters.get(1).parentSectionID)
				continue;
			if (rup.clusters[2].parentSectionID != clusters.get(2).parentSectionID)
				continue;
			if (rup.clusters[3].parentSectionID != clusters.get(0).parentSectionID)
				continue;
			if (allJump == null)
				allJump = rup;
			else if (rup.getTotalNumSects() > allJump.getTotalNumSects())
				allJump = rup;
		}
		targetRups.add(allJump);
		System.out.println("Largest 3 jump: "+allJump);
		
		return targetRups;
	}
	
	private static void buildSlipProbDemo(File outputDir) throws IOException {
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		SubSectBuilder rupBuild = buildRelativeDemoSystem();
		
		SectionDistanceAzimuthCalculator distCalc = new SectionDistanceAzimuthCalculator(rupBuild.subSectsList);
		ClusterConnectionStrategy connStrat = new DistCutoffClosestSectClusterConnectionStrategy(rupBuild.subSectsList, distCalc, 10d);
		
		RelativeSlipRateProb relProb = new RelativeSlipRateProb(connStrat, true);
		
		List<ClusterRupture> targetRups = getRelProbExampleRups(connStrat.getClusters());
		
		List<PlotSpec> specs = new ArrayList<>();
		
		String title = "Relative Slip Rate Probability";
		for (int i=0; i<targetRups.size(); i++) {
			PlotSpec spec = buildRelativeProbDemo(relProb, targetRups.get(i),
					title, "mm/yr");
			spec.setLegendVisible(i == targetRups.size()-1);
			specs.add(spec);
		}
		
		plotTileMulti(outputDir, "slip_prob_demo", specs, title, null, 0.02, 22, specs.size()-1);
	}
	
	private static void buildCoulombProbDemo(File outputDir) throws IOException {
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		SubSectBuilder rupBuild = buildRelativeDemoSystem();
		
		SubSectStiffnessCalculator stiffCalc = new SubSectStiffnessCalculator(rupBuild.subSectsList, 2d, 30000, 30000, 0.5d);
		AggregatedStiffnessCalculator aggCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffCalc, false,
				AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM);
		float favDist = 0f;
		
		SectionDistanceAzimuthCalculator distCalc = new SectionDistanceAzimuthCalculator(rupBuild.subSectsList);
		ClusterConnectionStrategy connStrat = new DistCutoffClosestSectClusterConnectionStrategy(rupBuild.subSectsList, distCalc, 10d);
//		PlausibilityFilter[] filters = {
//				new PathPlausibilityFilter(new SectCoulombPathEvaluator(aggCalc, com.google.common.collect.Range.atLeast(0f),
//						PlausibilityResult.FAIL_FUTURE_POSSIBLE, favDist > 0f, favDist, distCalc))
//		};
//		ClusterConnectionStrategy connStrat = new PlausibleClusterConnectionStrategy(rupBuild.subSectsList, distCalc, 3d, filters);
		
//		RelativeSlipRateProb relProb = new RelativeSlipRateProb(connStrat, true);
		RelativeCoulombProb relProb = new RelativeCoulombProb(aggCalc, connStrat, false, true, favDist > 0f, favDist, distCalc);
		
//		ClusterRupture targetRup = null;
//		for (ClusterRupture rup : new ClusterRuptureBuilder(connStrat.getClusters(), new ArrayList<>(), 0).build(new ConnectionPointsPermutationStrategy())) {
//			if (rup.clusters[0].startSect != rupBuild.subSectsList.get(0))
//				// always start at the start
//				continue;
//			if (targetRup == null)
//				targetRup = rup;
//			else if (rup.getTotalNumJumps() > targetRup.getTotalNumJumps())
//				targetRup = rup;
//			else if (rup.getTotalNumSects() > targetRup.getTotalNumSects() && rup.getTotalNumJumps() == targetRup.getTotalNumJumps())
//				targetRup = rup;
//		}
		List<ClusterRupture> targetRups = getRelProbExampleRups(connStrat.getClusters());
		
		List<PlotSpec> specs = new ArrayList<>();
		
		String title = "Relative "+StiffnessType.CFF.getName()+" Probability";
		for (int i=0; i<targetRups.size(); i++) {
			PlotSpec spec = buildRelativeProbDemo(relProb, targetRups.get(i), 
					title, StiffnessType.CFF.getUnits());
			spec.setLegendVisible(i == targetRups.size()-1);
			specs.add(spec);
		}
		
		plotTileMulti(outputDir, "cff_prob_demo", specs, title, null, 0.02, 22, specs.size()-1);
	}
	
	private static PlotSpec buildRelativeProbDemo(AbstractRelativeProb relProb, ClusterRupture fullRup, String title,
			String units) throws IOException {
		RelativeProbWrapper wrapper = new RelativeProbWrapper(relProb);
		
		double fullProb = wrapper.calcRuptureProb(fullRup, true);
		
		HashSet<FaultSubsectionCluster> skipToClusters = wrapper.getSkipToClusters(fullRup);
		if (skipToClusters != null && !skipToClusters.isEmpty()) {
			wrapper.disableSkip = true;
			wrapper.reset();
			wrapper.calcRuptureProb(fullRup, false);
		} else {
			skipToClusters = new HashSet<>();
		}
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		PlotCurveCharacterstics rupChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK);
		PlotCurveCharacterstics optionChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.LIGHT_GRAY);
		PlotCurveCharacterstics traceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY);
		
		boolean firstRup = true;
		boolean firstOption = true;
		for (FaultSubsectionCluster cluster : relProb.getConnStrat().getClusters()) {
			for (FaultSection sect : cluster.subSects) {
				if (fullRup.contains(sect)) {
					plotSection(sect, funcs, chars, rupChar, traceChar);
					if (firstRup)
						funcs.get(funcs.size()-1).setName("Ruptured Section");
					firstRup = false;
				} else {
					plotSection(sect, funcs, chars, optionChar, traceChar);
					if (firstOption)
						funcs.get(funcs.size()-1).setName("Other Sections");
					firstOption = false;
				}
			}
		}
		
		boolean firstJump = true;
		for (Jump jump : fullRup.getJumpsIterable()) {
			for (XY_DataSet xy : line(jump.fromSection, jump.toSection, true, 1d, 1d)) {
				funcs.add(xy);
				chars.add(reg_jump_char);
				if (firstJump)
					xy.setName("Jump");
				firstJump = false;
			}
		}
		
		MinMaxAveTracker xTrack = new MinMaxAveTracker();
		MinMaxAveTracker yTrack = new MinMaxAveTracker();
		for (XY_DataSet xy : funcs) {
			xTrack.addValue(xy.getMaxX());
			xTrack.addValue(xy.getMinX());
			yTrack.addValue(xy.getMaxY());
			yTrack.addValue(xy.getMinY());
		}
		
		double probY = yTrack.getMax() + 0.065;
		// draw fake line
		XY_DataSet fakeLine = new DefaultXY_DataSet();
		fakeLine.set(xTrack.getMin(), probY);
		fakeLine.set(xTrack.getMax(), probY);
		funcs.add(fakeLine);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.WHITE));
		// bottom fake line
		fakeLine = new DefaultXY_DataSet();
		fakeLine.set(xTrack.getMin(), yTrack.getMin() - 0.05);
		fakeLine.set(xTrack.getMax(), yTrack.getMin() - 0.05);
		funcs.add(fakeLine);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.WHITE));
		
		DecimalFormat df = new DecimalFormat("0.00");

		Font probFont = new Font(Font.SANS_SERIF, Font.BOLD, 22);
		Font deferredProbFont = new Font(Font.SANS_SERIF, Font.ITALIC, 22);
		
		List<XYTextAnnotation> anns = new ArrayList<>();
		
		XYTextAnnotation startProbAnn = new XYTextAnnotation("P", xTrack.getMin(), probY);
		startProbAnn.setFont(probFont);
		startProbAnn.setTextAnchor(TextAnchor.CENTER_LEFT);
		anns.add(startProbAnn);
		
		HashSet<FaultSection> jumpFroms = new HashSet<>();
		
		for (int i=0; wrapper.actualAdditionsList != null && i<wrapper.actualAdditionsList.size(); i++) {
			PathAddition add = wrapper.actualAdditionsList.get(i);
			if (add.fromCluster.parentSectionID == add.toCluster.parentSectionID)
				continue;
			jumpFroms.add(add.fromSect);
			MinMaxAveTracker addXTrack = new MinMaxAveTracker();
			for (Location loc : add.fromSect.getFaultTrace())
				addXTrack.addValue(loc.getLongitude());
			for (Location loc : add.toSects.iterator().next().getFaultTrace())
				addXTrack.addValue(loc.getLongitude());
			double x = addXTrack.getMin() + 0.5*(addXTrack.getMax() - addXTrack.getMin());
			
			double prevX = anns.get(anns.size()-1).getX();
			XYTextAnnotation timesAnn = new XYTextAnnotation(i > 0 ? "x" : "=", 0.5*(prevX+x), probY);
			timesAnn.setFont(probFont);
			timesAnn.setTextAnchor(TextAnchor.CENTER);
			anns.add(timesAnn);
			
			String label = df.format(wrapper.additionProbs.get(i));
			if (skipToClusters.contains(add.toCluster))
				label = "["+label+"]";
			XYTextAnnotation pAnn = new XYTextAnnotation(label, x, probY);
			if (skipToClusters.contains(add.toCluster)) {
				pAnn.setFont(deferredProbFont);
				pAnn.setPaint(Color.GRAY);
			} else {
				pAnn.setFont(probFont);
			}
			pAnn.setTextAnchor(TextAnchor.CENTER);
			anns.add(pAnn);
		}
		
//		for (FaultSection from : jumpFroms)
//			System.out.println("From sect: "+from.getSectionId());
		
		// final prob
		double prevX = anns.get(anns.size()-1).getX();
		XYTextAnnotation equalsAnn = new XYTextAnnotation("=", 0.5*(prevX+xTrack.getMax()), probY);
		equalsAnn.setFont(probFont);
		equalsAnn.setTextAnchor(TextAnchor.CENTER);
		anns.add(equalsAnn);
		XYTextAnnotation endProbAnn = new XYTextAnnotation(df.format(fullProb), xTrack.getMax(), probY);
		endProbAnn.setFont(probFont);
		endProbAnn.setTextAnchor(TextAnchor.CENTER_RIGHT);
		anns.add(endProbAnn);
		
		// now add branches
		Font branchFont = new Font(Font.SANS_SERIF, Font.BOLD, 18);
		for (int i=0; wrapper.availableAdditionsList != null && i<wrapper.availableAdditionsList.size(); i++) {
			PathAddition add = wrapper.availableAdditionsList.get(i);
			if (!jumpFroms.contains(add.fromSect))
				continue;
			FaultSection toSect = add.toSects.iterator().next();
			
			String label = df.format(wrapper.valuesList.get(i))+" "+units;
			
			double minX = Double.POSITIVE_INFINITY;
			double yAtMin = 0d;
			double maxX = Double.NEGATIVE_INFINITY;
			double yAtMax = 0d;
			for (Location loc : toSect.getFaultTrace()) {
				if (loc.getLongitude() < minX) {
					minX = loc.getLongitude();
					yAtMin = loc.getLatitude();
				}
				if (loc.getLongitude() > maxX) {
					maxX = loc.getLongitude();
					yAtMax = loc.getLatitude();
				}
			}
			TextAnchor anchor;
			double yPin;
			if ((float)yAtMax >= (float)yAtMin) {
				anchor = TextAnchor.BOTTOM_LEFT;
				yPin = yAtMin + 0.005;
			} else {
				anchor = TextAnchor.TOP_LEFT;
				yPin = yAtMin - 0.005;
			}
			XYTextAnnotation ann = new XYTextAnnotation(label, minX, yPin);
			ann.setFont(branchFont);
			ann.setTextAnchor(anchor);
			if ((float)yAtMax != (float)yAtMin) {
//				System.out.println("xRange: "+(float)minX+" "+(float)maxX);
//				System.out.println("xRange: "+(float)yAtMin+" "+(float)yAtMax);
				double angle = Math.atan((yAtMin-yAtMax)/(maxX-minX));
//				System.out.println("angle: "+angle+" = "+Math.toDegrees(angle)+" deg");
				ann.setRotationAngle(angle);
				ann.setRotationAnchor(anchor);
			}
			ann.setBackgroundPaint(new Color(255, 255, 255, 160));
			anns.add(ann);
		}
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, null, " ");
		spec.setLegendVisible(true);
		
		spec.setPlotAnnotations(anns);
		return spec;
	}
	
	private static void buildNoIndirectExamples(File outputDir) throws IOException {
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		double upperDepth = 0d;
		double lowerDepth = 10d;
		double fractDDW = 0.5;
		int parentID = 1001;
		
		double dip = 88d;
		
		SubSectBuilder rupBuild = new SubSectBuilder(fractDDW);
		
		rupBuild.addFault(buildSect(parentID++, dip, upperDepth, lowerDepth,
				loc(0d, 0d), loc(20d, 0d)));
		rupBuild.addFault(buildSect(parentID++, dip, upperDepth, lowerDepth,
				loc(24d, 0d), loc(44d, 0d)));
		rupBuild.addFault(buildSect(parentID++, dip, upperDepth, lowerDepth,
				loc(19d, 0d), loc(30d, 4d)));
		
		List<? extends FaultSection> subSects = rupBuild.subSectsList;
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		
		DistCutoffClosestSectClusterConnectionStrategy connStrat =
				new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, 10d);
		
		List<FaultSubsectionCluster> clusters = connStrat.getClusters();
		
		List<PlausibilityFilter> buildFilters = new ArrayList<>();
		buildFilters.add(new StartsWithFitler(clusters.get(0).startSect));
		
		List<ClusterRupture> allRups = new ClusterRuptureBuilder(clusters, buildFilters, 0).build(new ConnectionPointsPermutationStrategy());
		
		List<ClusterRupture> plotRups = new ArrayList<>();
		
		List<int[]> parentSets = new ArrayList<>();
		parentSets.add(new int[] { clusters.get(0).parentSectionID, clusters.get(1).parentSectionID});
		parentSets.add(new int[] { clusters.get(0).parentSectionID, clusters.get(2).parentSectionID});
		parentSets.add(new int[] { clusters.get(0).parentSectionID, clusters.get(2).parentSectionID, clusters.get(1).parentSectionID});
		
		for (int[] parentSet : parentSets) {
			ClusterRupture match = null;
			for (ClusterRupture rup : allRups) {
				if (rup.clusters.length != parentSet.length)
					continue;
				boolean sameParents = true;
				for (int i=0; i<parentSet.length; i++) {
					if (rup.clusters[i].parentSectionID != parentSet[i])
						sameParents = false;
				}
				if (sameParents && (match == null || rup.getTotalNumSects() > match.getTotalNumSects()))
					match = rup;
			}
			Preconditions.checkNotNull(match, "No match found for parents: %s", Joiner.on(",").join(Ints.asList(parentSet)));
			plotRups.add(match);
		}
		
		List<PlotSpec> specs = new ArrayList<>();
		DirectPathPlausibilityFilter filter = new DirectPathPlausibilityFilter(connStrat);

		PlotCurveCharacterstics rupChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK);
		PlotCurveCharacterstics otherChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.LIGHT_GRAY);
		PlotCurveCharacterstics traceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY);
		
		String title = "No Indirect Connections";
		List<String> titles = new ArrayList<>();
		for (int i=0; i<plotRups.size(); i++) {
			ClusterRupture rup = plotRups.get(i);
			boolean passes = filter.apply(rup, true).isPass();
			String label = "Rupture "+(i+1)+": ";
			if (passes)
				label += "PASS";
			else
				label += "FAIL";
			
			List<XY_DataSet> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			boolean firstRup = true;
			for (FaultSubsectionCluster cluster : rup.getClustersIterable()) {
				for (FaultSection sect : cluster.subSects) {
					plotSection(sect, funcs, chars, rupChar, traceChar);
					if (firstRup)
						funcs.get(funcs.size()-1).setName("Ruptured Sections");
					firstRup = false;
				}
			}
			
			boolean firstJump = true;
			for (Jump jump : rup.getJumpsIterable()) {
				for (XY_DataSet xy : line(jump.fromSection, jump.toSection, true, 1d, 0.5)) {
					funcs.add(xy);
					chars.add(reg_jump_char);
					if (firstJump)
						xy.setName("Jump");
					firstJump = false;
				}
			}
			
			// rename
			for (XY_DataSet xy : funcs)
				if (xy.getName() != null && xy.getName().contains("Sect"))
					xy.setName("Ruptured Sections");
			
			// add any non-ruptured sects
			boolean firstOther = true;
			for (FaultSection sect : subSects) {
				if (!rup.contains(sect)) {
					plotSection(sect, funcs, chars, otherChar, traceChar);
					if (firstOther)
						funcs.get(funcs.size()-1).setName("Other Sections");
					firstOther = false;
				}
			}
			
			PlotSpec spec = new PlotSpec(funcs, chars, title, null, " ");
			
			spec.setLegendVisible(i == 0);
			specs.add(spec);
			titles.add(label);
		}
		
		plotTileMulti(outputDir, "indirect_demo", specs, title, titles, 0.02, 22, 0);
	}
	
	private static void buildCoulombInteractionDemo(File outputDir) throws IOException {
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		double upperDepth = 0d;
		double lowerDepth = 10d;
		double fractDDW = 0.5;
		int parentID = 1001;
		
		double dip = 88d;
		
		SubSectBuilder rupBuild = new SubSectBuilder(fractDDW);
		
		double x1 = 75;
		double x2 = x1+15;
		double y0 = 0;
		double y1 = -10;
		double y2 = -20;
		double y3 = y2;
		
		rupBuild.addFault(buildSect(parentID++, dip, upperDepth, lowerDepth,
				loc(0d, y0), loc(0.98*x1, 0d)));
		rupBuild.addFault(buildSect(parentID++, dip, upperDepth, lowerDepth,
				loc(x1, 0d), loc(x1+0.33*(x2-x1), 0.15*y1), loc(x1+0.67*(x2-x1), 0.5*y1), loc(0.95*x2, 0.9*y1)));
		rupBuild.addFault(buildSect(parentID++, dip, upperDepth, lowerDepth,
				loc(0.95*x2, 1.1*y1), loc(x1+0.67*(x2-x1), y1+0.5*(y2-y1)), loc(x1+0.33*(x2-x1), y1+0.85*(y2-y1)), loc(x1, y2)));
		rupBuild.addFault(buildSect(parentID++, dip, upperDepth, lowerDepth,
				loc(0.98*x1, y2), loc(0d, y3)));
		
		List<? extends FaultSection> subSects = rupBuild.subSectsList;
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		
		DistCutoffClosestSectClusterConnectionStrategy connStrat =
				new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, 10d);
		List<FaultSubsectionCluster> clusters = connStrat.getClusters();
		
		SubSectStiffnessCalculator stiffCalc = new SubSectStiffnessCalculator(rupBuild.subSectsList, 2d, 30000, 30000,
				0.5d, PatchAlignment.CENTER, 1d);
		AggregatedStiffnessCalculator aggCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffCalc, false,
				AggregationMethod.NUM_POSITIVE, AggregationMethod.FLATTEN, AggregationMethod.FLATTEN, AggregationMethod.NORM_BY_COUNT);
		
		List<PlausibilityFilter> buildFilters = new ArrayList<>();
		buildFilters.add(new StartsWithFitler(clusters.get(0).startSect));
		
		List<ClusterRupture> allRups = new ClusterRuptureBuilder(clusters, buildFilters, 0).build(new ConnectionPointsPermutationStrategy());
		
		List<ClusterRupture> plotRups = new ArrayList<>();
		// plot ruptures that add each cluster in order
		for (int i=1; i<clusters.size(); i++) {
			ClusterRupture match = null;
			for (ClusterRupture rup : allRups) {
				if (rup.clusters.length != i+1)
					continue;
				boolean inOrder = true;
				for (int c=0; c<=i; c++)
					if (rup.clusters[c].parentSectionID != clusters.get(c).parentSectionID)
						inOrder = false;
				if (inOrder && (match == null || rup.getTotalNumSects() > match.getTotalNumSects()))
					match = rup;
			}
			Preconditions.checkNotNull(match);
			plotRups.add(match);
		}
		
		List<PlotSpec> specs = new ArrayList<>();

		PlotCurveCharacterstics rupChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK);
		PlotCurveCharacterstics otherChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.LIGHT_GRAY);
		PlotCurveCharacterstics traceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY);
		
		PlotCurveCharacterstics posChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, new Color(255, 0, 0, 5));
		PlotCurveCharacterstics negChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, new Color(0, 0, 255, 5));
		
		String title = "Fraction of "+StiffnessType.CFF.getName()+" Interactions Positive";
		DecimalFormat df = new DecimalFormat("0.00");
		DecimalFormat ndf = new DecimalFormat("0");
		ndf.setGroupingSize(3);
		ndf.setGroupingUsed(true);
		
		List<String> titles = new ArrayList<>();
		for (int i=0; i<plotRups.size(); i++) {
			ClusterRupture rup = plotRups.get(i);
			List<FaultSection> rupSects = new ArrayList<>();
			for (FaultSubsectionCluster cluster : rup.getClustersIterable())
				rupSects.addAll(cluster.subSects);
			
			// add interactions first
			long numInts = 0;
			
			Map<PatchLocation, List<XY_DataSet>> negFuncs = new HashMap<>();
			Map<PatchLocation, List<XY_DataSet>> posFuncs = new HashMap<>();
			for (FaultSection source : rupSects) {
				for (FaultSection receiver : rupSects) {
					StiffnessDistribution dist = stiffCalc.calcStiffnessDistribution(source, receiver);
					List<PatchLocation> sPatches = dist.sourcePatches;
					List<PatchLocation> rPatches = dist.receiverPatches;
					double[][] values = dist.get(aggCalc.getType());
					for (int r=0; r<values.length; r++) {
						PatchLocation rPatch = rPatches.get(r);
						for (int s=0; s<values[r].length; s++) {
							if (source == receiver && s == r)
								continue;
							PatchLocation sPatch = sPatches.get(s);
							numInts++;
							
//							funcs.add(xy);
							if (values[r][s] >= 0)
								bundleAddInteraction(posFuncs, sPatch, rPatch);
							else
								bundleAddInteraction(negFuncs, sPatch, rPatch);
						}
					}
				}
			}
			System.out.println("have "+numInts+" interactions for "+rup);
			// now add the funcs in random order
			List<Object[]> funcCharPairs = new ArrayList<>();
			for (PatchLocation patch : posFuncs.keySet())
				for (XY_DataSet xy : posFuncs.get(patch))
					funcCharPairs.add(new Object[] { xy, posChar });
			for (PatchLocation patch : negFuncs.keySet())
				for (XY_DataSet xy : negFuncs.get(patch))
					funcCharPairs.add(new Object[] { xy, negChar });
			System.out.println("combined down to "+funcCharPairs.size()+" functions");
			Collections.shuffle(funcCharPairs, new Random(funcCharPairs.size()));
			
			double val = aggCalc.calc(rupSects, rupSects);
			long numPositive = (long)(val*numInts);
			String label = "Rupture "+(i+1)+": "+ndf.format(numPositive)+"/"+ndf.format(numInts)+" = "+df.format(val);
			
			List<XY_DataSet> funcs = new ArrayList<>(funcCharPairs.size());
			List<PlotCurveCharacterstics> chars = new ArrayList<>(funcCharPairs.size());
			
			for (Object[] pair : funcCharPairs) {
				funcs.add((XY_DataSet)pair[0]);
				chars.add((PlotCurveCharacterstics)pair[1]);
			}
			
			boolean firstRup = true;
			for (FaultSubsectionCluster cluster : rup.getClustersIterable()) {
				for (FaultSection sect : cluster.subSects) {
					plotSection(sect, funcs, chars, rupChar, traceChar);
					if (firstRup)
						funcs.get(funcs.size()-1).setName("Ruptured Sections");
					firstRup = false;
				}
			}
			
//			boolean firstJump = true;
//			for (Jump jump : rup.getJumpsIterable()) {
//				for (XY_DataSet xy : line(jump.fromSection, jump.toSection, true, 1d, 0.5)) {
//					funcs.add(xy);
//					chars.add(reg_jump_char);
//					if (firstJump)
//						xy.setName("Jump");
//					firstJump = false;
//				}
//			}
			
			// rename
			for (XY_DataSet xy : funcs)
				if (xy.getName() != null && xy.getName().contains("Sect"))
					xy.setName("Ruptured Sections");
			
			// add any non-ruptured sects
			boolean firstOther = true;
			for (FaultSection sect : subSects) {
				if (!rup.contains(sect)) {
					plotSection(sect, funcs, chars, otherChar, traceChar);
					if (firstOther)
						funcs.get(funcs.size()-1).setName("Other Sections");
					firstOther = false;
				}
			}
			
			PlotSpec spec = new PlotSpec(funcs, chars, title, null, " ");
			
			spec.setLegendVisible(i == 0);
			specs.add(spec);
			titles.add(label);
		}
		
		plotTileMulti(outputDir, "cff_interact_demo", specs, title, titles, 0.04, 22, 0);
	}
	
	private static void bundleAddInteraction(Map<PatchLocation, List<XY_DataSet>> prevXYs, PatchLocation p1, PatchLocation p2) {
		List<XY_DataSet> prevs = prevXYs.get(p1);
		if (prevs != null && !prevs.isEmpty()) {
			// can chain from p1
			XY_DataSet newXY = prevs.remove(prevs.size()-1);
			newXY.set(p2.center.getLongitude(), p2.center.getLatitude());
			List<XY_DataSet> news = prevXYs.get(p2);
			if (news == null) {
				news = new ArrayList<>();
				prevXYs.put(p2, news);
			}
			news.add(newXY);
			return;
		}
		prevs = prevXYs.get(p2);
		if (prevs != null && !prevs.isEmpty()) {
			// can chain from p2
			XY_DataSet newXY = prevs.remove(prevs.size()-1);
			newXY.set(p1.center.getLongitude(), p1.center.getLatitude());
			List<XY_DataSet> news = prevXYs.get(p1);
			if (news == null) {
				news = new ArrayList<>();
				prevXYs.put(p1, news);
			}
			news.add(newXY);
			return;
		}
		// brand new!
		DefaultXY_DataSet xy = new DefaultXY_DataSet();
		xy.set(p1.center.getLongitude(), p1.center.getLatitude());
		xy.set(p2.center.getLongitude(), p2.center.getLatitude());
		List<XY_DataSet> news = prevXYs.get(p2);
		if (news == null) {
			news = new ArrayList<>();
			prevXYs.put(p2, news);
		}
		news.add(xy);
	}

	public static void main(String[] args) throws IOException, DocumentException {
		File rupsDir = new File("/home/kevin/workspace/opensha-ucerf3/src/org/opensha/sha/"
				+ "earthquake/faultSysSolution/ruptures");
		File rupDocsDir = new File(rupsDir, "doc");
		File stratDocsDir = new File(rupsDir, "strategies/doc");
		File plausiblityDocsDir = new File(rupsDir, "plausibility/doc");
		
//		buildStandardDemos(rupDocsDir);
//		buildThinningDemo(stratDocsDir);
		buildConnStratDemo(stratDocsDir);
		buildPermStratDemos(stratDocsDir);
		
		buildPathCoulombDemos(plausiblityDocsDir);
		buildSlipProbDemo(plausiblityDocsDir);
		buildCoulombProbDemo(plausiblityDocsDir);
		buildNoIndirectExamples(plausiblityDocsDir);
		buildCoulombInteractionDemo(plausiblityDocsDir);
	}

}
