package scratch.UCERF3.inversion.ruptures.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.data.Range;
import org.jfree.ui.TextAnchor;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.FaultSubsectionCluster;
import scratch.UCERF3.inversion.ruptures.Jump;
import scratch.UCERF3.inversion.ruptures.strategies.ClusterConnectionStrategy;
import scratch.UCERF3.inversion.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import scratch.UCERF3.utils.FaultSystemIO;

public class RupSetConnectionSearch {
	
	private FaultSystemRupSet rupSet;
	private SectionDistanceAzimuthCalculator distCalc;
	
	public static final double MAX_POSSIBLE_JUMP_DEFAULT = 100d;
	private ClusterConnectionStrategy clusterConnStrategy;
	
	public static final boolean CUMULATIVE_JUMPS_DEFAULT = false;
	// if true, find connections via the smallest cumulative jump distance
	// if false, find connections via the smallest individual jump (possibly across multiple clusters)
	private boolean cumulativeJumps;

	public RupSetConnectionSearch(FaultSystemRupSet rupSet) {
		this(rupSet, new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList()));
	}
	
	public RupSetConnectionSearch(FaultSystemRupSet rupSet, SectionDistanceAzimuthCalculator distCalc) {
		this(rupSet, distCalc, new DistCutoffClosestSectClusterConnectionStrategy(MAX_POSSIBLE_JUMP_DEFAULT),
				CUMULATIVE_JUMPS_DEFAULT);
	}
	
	public RupSetConnectionSearch(FaultSystemRupSet rupSet, SectionDistanceAzimuthCalculator distCalc,
			ClusterConnectionStrategy clusterConnStrategy, boolean cumulativeJumps) {
		this.rupSet = rupSet;
		if (distCalc == null)
			distCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
		this.distCalc = distCalc;
		this.clusterConnStrategy = clusterConnStrategy; 
		this.cumulativeJumps = cumulativeJumps;
	}
	
	private List<FaultSubsectionCluster> calcClusters(List<FaultSection> sects, final boolean debug) {
		List<FaultSubsectionCluster> clusters = new ArrayList<>();
		
		Map<Integer, List<FaultSection>> parentsMap = new HashMap<>();
		
		for (FaultSection sect : sects) {
			Integer parentID = sect.getParentSectionId();
			List<FaultSection> parentSects = parentsMap.get(parentID);
			if (parentSects == null) {
				parentSects = new ArrayList<>();
				parentsMap.put(parentID, parentSects);
			}
			parentSects.add(sect);
		}
		
		for (List<FaultSection> parentSects : parentsMap.values()) {
			// TODO this implementation is specific to U3 subsectioning, might need to be generalized
			// for models that allow subsections down dip
			Collections.sort(parentSects, sectIDcomp);
			
			List<FaultSection> curSects = new ArrayList<>();
			int prevID = -2;
			for (int s=0; s<parentSects.size(); s++) {
				FaultSection sect = parentSects.get(s);
				int id = sect.getSectionId();
				if (!curSects.isEmpty() && id != prevID+1) {
					// new cluster within this section
					clusters.add(new FaultSubsectionCluster(curSects));
					curSects = new ArrayList<>();
				}
				curSects.add(sect);
				prevID = id;
			}
			clusters.add(new FaultSubsectionCluster(curSects));
		}
		
		// calculate connections
		clusterConnStrategy.addConnections(clusters, distCalc);
		
		return clusters;
	}
	
	private static final Comparator<FaultSection> sectIDcomp = new Comparator<FaultSection>() {
		@Override
		public int compare(FaultSection o1, FaultSection o2) {
			return Integer.compare(o1.getSectionId(), o2.getSectionId());
		}
	};
	
	private class ClusterPath {
		private final FaultSubsectionCluster start;
		private final FaultSubsectionCluster target;
		
		private final HashSet<FaultSubsectionCluster> availableClusters;
		
		private final FaultSubsectionCluster[] path;
		private final Jump[] jumps;
		
		private double dist;

		public ClusterPath(FaultSubsectionCluster start, FaultSubsectionCluster target,
				HashSet<FaultSubsectionCluster> availableClusters) {
			this(start, target, availableClusters,
					new FaultSubsectionCluster[] {start}, // path starts with just this cluster
					new Jump[0], // no jumps yet
					0d); // start at zero dist (no jumps yet)
		}
		
		public ClusterPath(FaultSubsectionCluster start, FaultSubsectionCluster target,
				HashSet<FaultSubsectionCluster> availableClusters,
				FaultSubsectionCluster[] path, Jump[] jumps, double dist) {
			this.start = start;
			this.target = target;
			this.availableClusters = new HashSet<>(availableClusters);
			
			this.path = path;
			Preconditions.checkState(jumps.length == path.length-1);
			this.jumps = jumps;
			
			this.dist = dist;
		}
		
		public ClusterPath take(FaultSubsectionCluster to) {
			Preconditions.checkState(!isComplete());
			
			HashSet<FaultSubsectionCluster> newAvailClusters = new HashSet<>(availableClusters);
			Preconditions.checkState(newAvailClusters.remove(to));
			
			FaultSubsectionCluster from = path[path.length-1];
			FaultSubsectionCluster[] newPath = Arrays.copyOf(path, path.length+1);
			newPath[path.length] = to;
			
			Collection<Jump> jumpsTo = from.getConnectionsTo(to);
			Preconditions.checkState(jumpsTo.size() == 1, "%s paths between cluster pair?", jumpsTo.size());
			Jump jump = jumpsTo.iterator().next();
			Preconditions.checkNotNull(jump);
			Jump[] newJumps = Arrays.copyOf(jumps, jumps.length+1);
			newJumps[jumps.length] = jump;
			
			double newDist;
			if (cumulativeJumps)
				newDist = dist + jump.distance;
			else
				newDist = Math.max(dist, jump.distance);
			
			return new ClusterPath(start, target, newAvailClusters, newPath, newJumps, newDist);
		}
		
		public boolean isComplete() {
			return path[path.length-1] == target;
		}
		
		public String toString() {
			String str = "";
			double myDist = 0;
			for (int p=0; p<path.length; p++) {
				if (p == 0)
					str += "[";
				else {
					// close out previous
					Jump jump = jumps[p-1];
					int exitID = jump.fromSection.getSectionId();
					int entryID = jump.toSection.getSectionId();
					if (cumulativeJumps)
						myDist += jump.distance;
					else
						myDist = jump.distance;
					str += "; "+exitID+"] => ["+entryID+"; R="+distDF.format(myDist)+"; ";
				}
				str += path[p].parentSectionName;
			}
			str += "]";
			
			if (isComplete())
				str += " COMPLETE R="+distDF.format(dist);
			else
				str += " INCOMPLETE (target: "+target.parentSectionName+") R="+distDF.format(dist);
			return str;
		}
	}
	
	private static final DecimalFormat distDF = new DecimalFormat("0.0");
	
	private class PathResult {
		private ClusterPath[] shortestPaths;
		private int completePathCount = 0;
		
		private void addPath(ClusterPath path, final boolean debug) {
			Preconditions.checkState(path.isComplete());
			if (shortestPaths == null || (float)shortestPaths[0].dist > (float)path.dist) {
				if (debug)
					System.out.println("\t\t\t\tNew shortest with R="
							+distDF.format(path.dist)+": "+path);
				shortestPaths = new ClusterPath[] { path };
			} else if ((float)shortestPaths[0].dist == (float)path.dist
					&& shortestPaths[0].path[1] != path.path[1]) {
				if (debug)
					System.out.println("\t\t\t\tIt's a tie! Additional shortest with R="
							+distDF.format(path.dist)+": "+path);
				shortestPaths = Arrays.copyOf(shortestPaths, shortestPaths.length+1);
				shortestPaths[shortestPaths.length-1] = path;
			}
			completePathCount++;
		}
	}
	
	private void pathSearch(final ClusterPath basePath, final PathResult result, final boolean debug) {
		Preconditions.checkState(!basePath.isComplete());
		
		FaultSubsectionCluster from = basePath.path[basePath.path.length-1];
		
		// search in order of increasing distance // TODO
//		for (FaultSubsectionCluster to : from.sortedConnections) {
//			if (!basePath.availableClusters.contains(to))
//				continue;
		for (FaultSubsectionCluster to : from.getConnectedClusters()) {
			if (!basePath.availableClusters.contains(to))
				continue;
			ClusterPath path = basePath.take(to);
			if (debug)
				System.out.println("\t\t\t"+path);
//			if (debug) System.out.println("\t\t\t\tTaking "+sect.getSectionId()+" complete="+path.isComplete()
//							+", dist="+(float)path.cumulativeDistance);
			if (path.isComplete()) {
				result.addPath(path, debug);
			} else {
				if (result.shortestPaths != null && path.dist >= result.shortestPaths[0].dist)
					// already worse than our current best, stop here
					continue;
				pathSearch(path, result, debug);
			}
		}
	}
	
	public List<Jump> calcRuptureJumps(int rupIndex) {
		return calcRuptureJumps(rupIndex, false);
	}
	
	public List<Jump> calcRuptureJumps(int rupIndex, final boolean debug) {
		List<FaultSection> sects = rupSet.getFaultSectionDataForRupture(rupIndex);
		
		if (debug) System.out.println("Building clusters for "+rupIndex);
		
		// calculate clusters (between which there may be connections
		List<FaultSubsectionCluster> clusters = calcClusters(sects, debug);
		
		return calcRuptureJumps(clusters, debug);
	}
	
	private static IDPairing jumpPair(Jump jump) {
		int id1 = jump.fromSection.getSectionId();
		int id2 = jump.toSection.getSectionId();
		if (id1 < id2)
			return new IDPairing(id1, id2);
		return new IDPairing(id2, id1);
	}
	
	public List<Jump> calcRuptureJumps(List<FaultSubsectionCluster> rupClusters, final boolean debug) {
		List<Jump> jumps = new ArrayList<>();
		
		if (debug) System.out.println("Searching for connections...");
		
		int numCompletePaths = 0;
		
		HashSet<IDPairing> uniques = new HashSet<>();
		
		for (int i=0; i<rupClusters.size(); i++) {
			FaultSubsectionCluster from = rupClusters.get(i);
			HashSet<FaultSubsectionCluster> availableClusters = new HashSet<>(rupClusters);
			availableClusters.remove(from);
			
			if (debug) System.out.println("\tFrom cluster "+0+", "+from.parentSectionName);
			
			// TODO: add check for no connections possible?
			
			for (int j=i+1; j<rupClusters.size(); j++) {
				FaultSubsectionCluster target = rupClusters.get(j);
				ClusterPath basePath = new ClusterPath(from, target, availableClusters);
				
				if (debug) System.out.println("\t\tSearching to cluster "+j+", "+target.parentSectionName);
				
				PathResult result = new PathResult();
				pathSearch(basePath, result, debug);
				
				ClusterPath[] shortestPaths = result.shortestPaths;
				if (shortestPaths != null) {
					// we have a valid path
					
					for (ClusterPath shortest : shortestPaths) {
						// only keep the first jump (as others are constrained and may not represent actual shortest path)
						Jump jump = shortest.jumps[0];
						IDPairing pair = jumpPair(jump);
						if (!uniques.contains(pair)) {
							jumps.add(jump);
							uniques.add(pair);
						}
						if (debug) System.out.println("\t\t\tShortest path: "+shortest+" (of "+result.completePathCount+")");
					}
				} else if (debug) {
					System.out.println("\t\t\tNo valid path found");
				}
				numCompletePaths += result.completePathCount;
			}
		}
		
		if (debug)
			System.out.println("Found "+jumps.size()+" connections (searched "+numCompletePaths+" full paths)");
		
		return jumps;
	}
	
	public ClusterRupture buildClusterRupture(int rupIndex) {
		return buildClusterRupture(rupIndex, false);
	}
	
	public ClusterRupture buildClusterRupture(int rupIndex, final boolean debug) {
		List<FaultSection> sects = rupSet.getFaultSectionDataForRupture(rupIndex);
		
		if (debug) System.out.println("Building clusters for "+rupIndex);
		
		// calculate clusters (between which there may be connections
		List<FaultSubsectionCluster> rupClusters = calcClusters(sects, debug);
		
		if (debug) System.out.println("\tHave "+rupClusters.size()+" clusters");
		
		List<Jump> jumps = calcRuptureJumps(rupClusters, debug);
		return buildClusterRupture(rupClusters, jumps, debug);
	}
	
	public ClusterRupture buildClusterRupture(List<FaultSubsectionCluster> rupClusters,
			List<Jump> jumps, final boolean debug) {
		
		Multimap<FaultSubsectionCluster, Jump> jumpsFromMap = HashMultimap.create();
		for (Jump jump : jumps) {
			jumpsFromMap.put(jump.fromCluster, jump);
			Jump reversed = jump.reverse();
			jumpsFromMap.put(reversed.fromCluster, reversed);
			if (debug) System.out.println("Available jump: "+jump);
		}
		
		// calculate isolation score where each first level connection costs 1, 2nd level
		// costs 0.1, 3rd 0.01, etc...
		FaultSubsectionCluster startCluster = null;
		if (rupClusters.size() > 1) {
			if (debug) System.out.println("Calculating cluster isolation scores...");
			double minClusterScore = Double.POSITIVE_INFINITY;
			for (FaultSubsectionCluster cluster : rupClusters) {
				Preconditions.checkState(!jumpsFromMap.get(cluster).isEmpty());
				HashSet<FaultSubsectionCluster> availableClusters = new HashSet<>(rupClusters);
				availableClusters.remove(cluster);
				double score = calcClusterIsolationScore(cluster,
						availableClusters, jumpsFromMap, 1d);
				if (debug) System.out.println("\tCluster "+cluster+"\tscore="+score);
				if (score < minClusterScore) {
					startCluster = cluster;
					minClusterScore = score;
				}
			}
			
			if (debug)
				System.out.println("Most isolated cluster: "+startCluster+"\tscore="+minClusterScore);
		} else {
			startCluster = rupClusters.get(0);
		}
		
		
		ClusterRupture rupture = new ClusterRupture(startCluster);
		HashSet<FaultSubsectionCluster> availableClusters = new HashSet<>(rupClusters);
		Preconditions.checkState(availableClusters.remove(startCluster));
		
		rupture = buildRupture(rupture, rupture, availableClusters, jumpsFromMap, debug);
		if (debug) System.out.println("Final rupture:\n"+rupture);
		Preconditions.checkState(availableClusters.isEmpty(),
				"Didn't use all available clusters when building rupture, have %s left."
				+ "\n\tRupture: %s\n\tAvailable clusters: %s",
				availableClusters.size(), rupture, availableClusters);
		
		return rupture;
	}
	
	private ClusterRupture buildRupture(ClusterRupture rupture, ClusterRupture currentStrand,
			HashSet<FaultSubsectionCluster> availableClusters,
			Multimap<FaultSubsectionCluster, Jump> jumpsFromMap, final boolean debug) {
		// first build out this strand and all jumps from it
		if (debug) System.out.println("Building strand: "+currentStrand);
		while (true) {
			boolean extended = false;
			FaultSubsectionCluster lastCluster = currentStrand.clusters[currentStrand.clusters.length-1];
			for (Jump jump : jumpsFromMap.get(lastCluster)) {
				if (!availableClusters.contains(jump.toCluster))
					// already taken this jump
					continue;
				if (debug) System.out.println("\tTaking jump: "+jump);
				rupture = rupture.take(jump);
				availableClusters.remove(jump.toCluster);
				// current strand has now been replaced, find the new one
				currentStrand = splaySearchRecursive(rupture, currentStrand.clusters[0]);
				Preconditions.checkNotNull(currentStrand, "current strand could not be found after taking jump");
				extended = true;
			}
			if (!extended)
				break;
		}
		// now build out each splay from this strand
		for (ClusterRupture splay : currentStrand.splays.values())
			rupture = buildRupture(rupture, splay, availableClusters, jumpsFromMap, debug);
		return rupture;
	}
	
	private ClusterRupture splaySearchRecursive(ClusterRupture rup, FaultSubsectionCluster firstCluster) {
		if (rup.clusters[0] == firstCluster)
			return rup;
		for (ClusterRupture splay : rup.splays.values()) {
			ClusterRupture match = splaySearchRecursive(splay, firstCluster);
			if (match != null)
				return match;
		}
		return null;
	}
	
	private double calcClusterIsolationScore(FaultSubsectionCluster cluster,
			HashSet<FaultSubsectionCluster> availableClusters,
			Multimap<FaultSubsectionCluster, Jump> jumpsFromMap, double penaltyEach) {
		double tot = 0d;
		for (Jump jump : jumpsFromMap.get(cluster)) {
			if (!availableClusters.contains(jump.toCluster))
				continue;
			tot += penaltyEach;
			HashSet<FaultSubsectionCluster> newAvailableClusters = new HashSet<>(availableClusters);
			newAvailableClusters.remove(jump.toCluster);
			tot += calcClusterIsolationScore(jump.toCluster, newAvailableClusters, jumpsFromMap, 0.1*penaltyEach);
		}
		return tot;
	}
	
	public void plotConnections(File outputDir, String prefix, int rupIndex) throws IOException {
		plotConnections(outputDir, prefix, rupIndex, null, null);
	}
	
	public void plotConnections(File outputDir, String prefix, int rupIndex,
			Set<IDPairing> highlightConn, String highlightName) throws IOException {
//		HashSet<IDPairing> connections = calcConnections(rupIndex, true);
		List<FaultSection> sects = rupSet.getFaultSectionDataForRupture(rupIndex);
		List<FaultSubsectionCluster> clusters = calcClusters(sects, true);
		List<Jump> jumps = calcRuptureJumps(clusters, true);

		HashSet<Integer> parentIDs = new HashSet<>();
		for (FaultSection sect : sects)
			parentIDs.add(sect.getParentSectionId());
		
		Color connectedColor = Color.GREEN.darker();
		Color highlightColor = Color.RED.darker();
		Color faultColor = Color.DARK_GRAY;
		Color faultOutlineColor = Color.LIGHT_GRAY;
		
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		Map<Integer, Location> middles = new HashMap<>();
		
		for (int s=0; s<sects.size(); s++) {
			FaultSection sect = sects.get(s);
			RuptureSurface surf = sect.getFaultSurface(1d);
			
			XY_DataSet trace = new DefaultXY_DataSet();
			for (Location loc : surf.getEvenlyDiscritizedUpperEdge())
				trace.set(loc.getLongitude(), loc.getLatitude());
			
			if (sect.getAveDip() != 90d) {
				XY_DataSet outline = new DefaultXY_DataSet();
				LocationList perimeter = surf.getPerimeter();
				for (Location loc : perimeter)
					outline.set(loc.getLongitude(), loc.getLatitude());
				Location first = perimeter.first();
				outline.set(first.getLongitude(), first.getLatitude());
				
				funcs.add(0, outline);
				chars.add(0, new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, faultOutlineColor));
			}
			
			middles.put(sect.getSectionId(), GriddedSurfaceUtils.getSurfaceMiddleLoc(surf));
			
			if (s == 0)
				trace.setName("Fault Sections");
			
			funcs.add(trace);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, faultColor));
		}
		
		boolean first = true;
		double maxDist = 0d;
		HashSet<IDPairing> connections = new HashSet<>();
		for (Jump jump : jumps) {
			DefaultXY_DataSet xy = new DefaultXY_DataSet();
			maxDist = Math.max(maxDist, jump.distance);
			
			if (first) {
				xy.setName("Connections");
				first = false;
			}
			
			connections.add(jumpPair(jump));
			Location loc1 = middles.get(jump.fromSection.getSectionId());
			Location loc2 = middles.get(jump.toSection.getSectionId());
			
			xy.set(loc1.getLongitude(), loc1.getLatitude());
			xy.set(loc2.getLongitude(), loc2.getLatitude());
			
			funcs.add(xy);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, connectedColor));
		}
		
		if (highlightConn != null) {
			boolean firstHighlight = true;
			for (IDPairing connection : connections) {
				if (!highlightConn.contains(connection))
					continue;
				DefaultXY_DataSet xy = new DefaultXY_DataSet();
				if (firstHighlight) {
					xy.setName(highlightName);
					firstHighlight = false;
				}
				
				Location loc1 = middles.get(connection.getID1());
				Location loc2 = middles.get(connection.getID2());
				
				xy.set(loc1.getLongitude(), loc1.getLatitude());
				xy.set(loc2.getLongitude(), loc2.getLatitude());
				
				funcs.add(xy);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, highlightColor));
			}
		}
		
		for (XY_DataSet xy : funcs) {
			for (Point2D pt : xy) {
				latTrack.addValue(pt.getY());
				lonTrack.addValue(pt.getX());
			}
		}
		
		XY_DataSet[] outlines = PoliticalBoundariesData.loadCAOutlines();
		PlotCurveCharacterstics outlineChar = new PlotCurveCharacterstics(PlotLineType.SOLID, (float)1d, Color.GRAY);
		
		for (XY_DataSet outline : outlines) {
			funcs.add(outline);
			chars.add(outlineChar);
		}
		
		PlotSpec spec = new PlotSpec(funcs, chars, "Rupture "+rupIndex+" Connections", "Longitude", "Latitude");
		spec.setLegendVisible(true);
		
		Range xRange = new Range(lonTrack.getMin()-0.5, lonTrack.getMax()+0.5);
		Range yRange = new Range(latTrack.getMin()-0.5, latTrack.getMax()+0.5);
		
		double annX = xRange.getLowerBound() + 0.975*xRange.getLength();
		double annYmult = 0.975d;
		double deltaAnnYmult = 0.05;
		Font annFont = new Font(Font.SANS_SERIF, Font.BOLD, 22);
		
		double annY = yRange.getLowerBound() + annYmult*yRange.getLength();
		XYTextAnnotation cAnn = new XYTextAnnotation(
				clusters.size()+" clusters on "+parentIDs.size()+" parent sects",
				annX, annY);
		cAnn.setFont(annFont);
		cAnn.setTextAnchor(TextAnchor.TOP_RIGHT);
		spec.addPlotAnnotation(cAnn);
		
		annYmult -= deltaAnnYmult;
		annY = yRange.getLowerBound() + annYmult*yRange.getLength();
		XYTextAnnotation jumpAnn = new XYTextAnnotation(
				connections.size()+" connections (max dist: "+distDF.format(maxDist)+")",
				annX, annY);
		jumpAnn.setFont(annFont);
		jumpAnn.setTextAnchor(TextAnchor.TOP_RIGHT);
		spec.addPlotAnnotation(jumpAnn);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(24);
		gp.setPlotLabelFontSize(24);
		gp.setBackgroundColor(Color.WHITE);
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		double len = Math.max(xRange.getLength(), yRange.getLength());
//		double tick = 2d;
		double tick;
		if (len > 6)
			tick = 2d;
		else if (len > 3)
			tick = 1d;
		else if (len > 1)
			tick = 0.5;
		else
			tick = 0.1;
		TickUnits tus = new TickUnits();
		TickUnit tu = new NumberTickUnit(tick);
		tus.add(tu);
		gp.getXAxis().setStandardTickUnits(tus);
		gp.getYAxis().setStandardTickUnits(tus);
		
		
		File file = new File(outputDir, prefix+".png");
		System.out.println("writing "+file.getAbsolutePath());
		double aspectRatio = yRange.getLength() / xRange.getLength();
		gp.getChartPanel().setSize(800, 200 + (int)(600d*aspectRatio));
		gp.saveAsPNG(file.getAbsolutePath());
	}

	public static void main(String[] args) throws ZipException, IOException, DocumentException {
		File fssFile = new File("/home/kevin/Simulators/catalogs/rundir4983_stitched/fss/"
				+ "rsqsim_sol_m6.5_skip5000_sectArea0.2.zip");
//		int[] plotIndexes = { 1001, 27845, 173243, 193669 };
//		double debugDist = Double.POSITIVE_INFINITY;
		int[] plotIndexes = {  };
		double debugDist = 30d;
//		double maxPossibleJumpDist = MAX_POSSIBLE_JUMP_DEFAULT;
		double maxPossibleJumpDist = 1000d;
		File outputDir = new File("/tmp/rup_conn_rsqsim");
		
//		File fssFile = new File("/home/kevin/workspace/opensha-ucerf3/src/scratch/UCERF3/data/scratch/InversionSolutions/"
//				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip");
//		int[] plotIndexes = { 25000, 50000, 75000, 100000, 125000, 150000, 175000, 200000, 225000, 238293, 250000 };
//		double debugDist = Double.POSITIVE_INFINITY;
////		int[] plotIndexes = {};
////		double debugDist = 5d;
//		double maxPossibleJumpDist = 15d;
//		File outputDir = new File("/tmp/rup_conn_u3");
		
		FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(fssFile);
		
		SectionDistanceAzimuthCalculator distCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
		
		ClusterConnectionStrategy connStrategy = new DistCutoffClosestSectClusterConnectionStrategy(maxPossibleJumpDist);
		RupSetConnectionSearch search = new RupSetConnectionSearch(rupSet, distCalc,
				connStrategy, CUMULATIVE_JUMPS_DEFAULT);
		
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		if (plotIndexes != null && plotIndexes.length > 0) {
			// just plots
			for (int r : plotIndexes)
				search.plotConnections(outputDir, "rup_"+r, r);
		} else {
			HashSet<IDPairing> allConnections = new HashSet<>();
			
			for (int r=0; r<rupSet.getNumRuptures(); r++) {
				if (r % 1000 == 0)
					System.out.println("Calculating for rupture "+r+"/"+rupSet.getNumRuptures()
						+" ("+allConnections.size()+" connections found so far)");
//				HashSet<IDPairing> rupConnections = search.calcConnections(r);
				ClusterRupture rup = search.buildClusterRupture(r, false);
				boolean debug = false;
				for (Jump jump : rup.getJumpsIterable()) {
					IDPairing pair = jumpPair(jump);
					if (!allConnections.contains(pair) && jump.distance > debugDist) {
						System.out.println("Jump "+jump+" has dist="+(float)jump.distance);
						debug = true;
					}
					allConnections.add(pair);
				}
				if (debug)
					search.plotConnections(outputDir, "rup_"+r, r);
			}
			
			System.out.println("Found "+allConnections.size()+" total connections");
		}
	}

}
