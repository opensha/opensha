package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.dom4j.DocumentException;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.data.Range;
import org.jfree.chart.ui.TextAnchor;
import org.opensha.commons.data.function.DefaultXY_DataSet;
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
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder.RupDebugCriteria;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.MinSectsPerParentFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.UCERF3ClusterPermuationStrategy;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

import com.google.common.base.Preconditions;

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
	
	private static XY_DataSet line(FaultSection from, FaultSection to, boolean arrow, double lenScale) {
		Location center1 = GriddedSurfaceUtils.getSurfaceMiddleLoc(from.getFaultSurface(1d, false, false));
		Location center2 = GriddedSurfaceUtils.getSurfaceMiddleLoc(to.getFaultSurface(1d, false, false));
		double arrowAz = LocationUtils.azimuth(center1, center2);
		if (lenScale != 1d) {
			double dist = LocationUtils.horzDistanceFast(center1, center2);
			center2 = LocationUtils.location(center1, Math.toRadians(arrowAz), dist*lenScale);
		}
		
		DefaultXY_DataSet xy = new DefaultXY_DataSet();
		
		xy.set(center1.getLongitude(), center1.getLatitude());
		xy.set(center2.getLongitude(), center2.getLatitude());
		
		if (arrow) {
			double arrowLen = 4d; // km
			double az1 = arrowAz + 135;
			double az2 = arrowAz - 135;
			
			Location arrow1 = LocationUtils.location(center2, Math.toRadians(az1), arrowLen);
			Location arrow2 = LocationUtils.location(center2, Math.toRadians(az2), arrowLen);

			xy.set(arrow1.getLongitude(), arrow1.getLatitude());
			xy.set(center2.getLongitude(), center2.getLatitude());
			xy.set(arrow2.getLongitude(), arrow2.getLatitude());
		}
		
		return xy;
	}
	
	public static PlotSpec buildRupturePlot(ClusterRupture rup, String title,
			boolean plotAzimuths, boolean axisLabels) {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		RupturePlotBuilder builder = new RupturePlotBuilder(plotAzimuths);
		
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
		
		double latSpan = maxLat - minLat;
		double lonSpan = maxLon - minLon;
		int width = 800;
		double plotWidth = width - 70;
		double plotHeight = plotWidth*latSpan/lonSpan;
		int height = 150 + (int)plotHeight;
		
		Range xRange = new Range(minLon, maxLon);
		Range yRange = new Range(minLat, maxLat);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(24);
		gp.setPlotLabelFontSize(24);
		gp.setLegendFontSize(20);
		gp.setBackgroundColor(Color.WHITE);
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		gp.getXAxis().setTickLabelsVisible(axisLabels);
		gp.getYAxis().setTickLabelsVisible(axisLabels);
		
		File file = new File(outputDir, prefix);
		gp.getChartPanel().setSize(width, height);
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		if (write_pdfs)
			gp.saveAsPDF(file.getAbsolutePath()+".pdf");
	}
	
	private static Color[] strand_colors =  { Color.BLACK, Color.MAGENTA.darker(), Color.ORANGE.darker() };

	private static PlotCurveCharacterstics reg_jump_char = new PlotCurveCharacterstics(
			PlotLineType.SOLID, 5f, Color.RED);
	private static PlotCurveCharacterstics splay_jump_char = new PlotCurveCharacterstics(
			PlotLineType.SOLID, 5f, Color.CYAN);
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
			for (Jump jump : rup.internalJumps) {
				XY_DataSet line = line(jump.fromSection, jump.toSection, true, 1d);
				if (firstStrandJump)
					line.setName("Regular Jump");
				firstStrandJump = false;
				
				arrowFuncs.add(line);
				arrowChars.add(reg_jump_char);
				
				if (plotAzimuths)
					addAzimuths(rup, jump);
			}
			for (Jump splayJump : rup.splays.keySet()) {
				ClusterRupture splay = rup.splays.get(splayJump);
				
				XY_DataSet line = line(splayJump.fromSection, splayJump.toSection, true, 1d);
				if (firstSplayJump)
					line.setName("Splay Jump");
				firstSplayJump = false;
				
				arrowFuncs.add(line);
				arrowChars.add(splay_jump_char);
				
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
			XY_DataSet xy = line(from, to, true, 2.5d);
			if (firstAz)
				xy.setName("Azimuth");
			firstAz = false;
			arrowFuncs.add(xy);
			arrowChars.add(az_arrow_char);
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
	
	private static class RuptureBuilder {
		
		private double fractDDW;
		private List<FaultSection> subSectsList;
//		private List<FaultSubsectionCluster> allClusters;
		
		private List<ClusterRupture> allRups;
		private ClusterRupture biggestRup;
		
		public RuptureBuilder(double fractDDW) {
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
	
	private static void animateRuptureBuilding(File outputDir, String prefix, RuptureBuilder rupBuild,
			List<PlausibilityFilter> filters, ClusterConnectionStrategy connStrat,
			int maxNumSplays, boolean includeDuplicates, boolean plotAzimuths,
			boolean axisLabels, double fps) throws IOException {
		TrackAllDebugCriteria tracker = new TrackAllDebugCriteria();
		
		ClusterRuptureBuilder builder = new ClusterRuptureBuilder(
				connStrat.getClusters(), filters, maxNumSplays);
		builder.setDebugCriteria(tracker, false);
		List<ClusterRupture> finalRups = builder.build(new UCERF3ClusterPermuationStrategy());
		
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
		
		double latSpan = maxLat - minLat;
		double lonSpan = maxLon - minLon;
		int width = 800;
		double plotWidth = width - 70;
		double plotHeight = plotWidth*latSpan/lonSpan;
		int height = 150 + (int)plotHeight;
		
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
			boolean duplicate = uniques.contains(possible.unique);;
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
			
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setTickLabelFontSize(18);
			gp.setAxisLabelFontSize(24);
			gp.setPlotLabelFontSize(24);
			gp.setLegendFontSize(20);
			gp.setBackgroundColor(Color.WHITE);
			
			gp.drawGraphPanel(spec, false, false, xRange, yRange);
			gp.getXAxis().setTickLabelsVisible(axisLabels);
			gp.getYAxis().setTickLabelsVisible(axisLabels);
			
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
		RuptureBuilder rupBuild = new RuptureBuilder(fractDDW);
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

	public static void main(String[] args) throws IOException, DocumentException {
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
		
		File outputDir = new File("/home/kevin/workspace/opensha-ucerf3/src/org/opensha/sha/"
				+ "earthquake/faultSysSolution/ruptures/doc");
		
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
		
		FaultSystemRupSet u3RupSet = FaultSystemIO.loadRupSet(new File("/home/kevin/workspace/"
				+ "opensha-ucerf3/src/scratch/UCERF3/data/scratch/InversionSolutions/"
				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
		SectionDistanceAzimuthCalculator u3DistCalc = new SectionDistanceAzimuthCalculator(
				u3RupSet.getFaultSectionDataList());
		ClusterRupture u3Complicated1 = ClusterRupture.forOrderedSingleStrandRupture(
				u3RupSet.getFaultSectionDataForRupture(212600), u3DistCalc);
		plotRupture(outputDir, "u3_complicated_1", u3Complicated1, "UCERF3 Rupture", false, false);
		plotRupture(outputDir, "u3_complicated_az_1", u3Complicated1, "UCERF3 Rupture", true, false);
		ClusterRupture u3Complicated2 = ClusterRupture.forOrderedSingleStrandRupture(
				u3RupSet.getFaultSectionDataForRupture(237540), u3DistCalc);
		plotRupture(outputDir, "u3_complicated_2", u3Complicated2, "UCERF3 Rupture", false, false);
		plotRupture(outputDir, "u3_complicated_az_2", u3Complicated2, "UCERF3 Rupture", true, false);
		ClusterRupture u3SAFPleito = ClusterRupture.forOrderedSingleStrandRupture(
				u3RupSet.getFaultSectionDataForRupture(194942), u3DistCalc);
		plotRupture(outputDir, "u3_saf_pleito", u3SAFPleito, "UCERF3 SAF & Pleito", false, false);
		// now same thing, but full sections
		Map<Integer, List<FaultSection>> parentSectsMap = new HashMap<>();
		for (FaultSection sect : u3RupSet.getFaultSectionDataList()) {
			List<FaultSection> sects = parentSectsMap.get(sect.getParentSectionId());
			if (sects == null) {
				sects = new ArrayList<>();
				parentSectsMap.put(sect.getParentSectionId(), sects);
			}
			sects.add(sect);
		}
		ClusterRupture u3SAFPlietoSplay = getFullSectionsRup(u3SAFPleito, parentSectsMap, u3DistCalc);
		plotRupture(outputDir, "u3_saf_pleito_splay", u3SAFPlietoSplay, "SAF & Pleito Splay", false, false);
		
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
		RuptureBuilder rupBuild = new RuptureBuilder(fractDDW);
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

}
