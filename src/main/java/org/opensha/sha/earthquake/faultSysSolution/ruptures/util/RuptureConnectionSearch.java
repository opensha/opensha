package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.data.Range;
import org.jfree.chart.ui.TextAnchor;
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
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import scratch.UCERF3.utils.U3FaultSystemIO;

public class RuptureConnectionSearch implements SubModule<FaultSystemRupSet> {
	
	private FaultSystemRupSet rupSet;
	private SectionDistanceAzimuthCalculator distCalc;
	
	public static final double MAX_POSSIBLE_JUMP_DEFAULT = 100d;
	private double maxJumpDist;
	
	public static final boolean CUMULATIVE_JUMPS_DEFAULT = false;
	// if true, find connections via the smallest cumulative jump distance
	// if false, find connections via the smallest individual jump (possibly across multiple clusters)
	private boolean cumulativeJumps;
	
	public RuptureConnectionSearch(FaultSystemRupSet rupSet, SectionDistanceAzimuthCalculator distCalc) {
		this(rupSet, distCalc, MAX_POSSIBLE_JUMP_DEFAULT, CUMULATIVE_JUMPS_DEFAULT);
	}
	
	public RuptureConnectionSearch(FaultSystemRupSet rupSet, SectionDistanceAzimuthCalculator distCalc,
			double maxJumpDist, boolean cumulativeJumps) {
		this.rupSet = rupSet;
		this.distCalc = distCalc;
		this.maxJumpDist = maxJumpDist; 
		this.cumulativeJumps = cumulativeJumps;
	}
	
	public SectionDistanceAzimuthCalculator getDistAzCalc() {
		return distCalc;
	}
	
	public List<FaultSubsectionCluster> calcClusters(List<? extends FaultSection> sects, final boolean debug) {
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
		
		ClusterConnectionStrategy connStrat = new DistCutoffClosestSectClusterConnectionStrategy(
				sects, clusters, distCalc, maxJumpDist);
		
		return connStrat.getClusters();
	}
	
	private static final Comparator<FaultSection> sectIDcomp = new Comparator<FaultSection>() {
		@Override
		public int compare(FaultSection o1, FaultSection o2) {
			return Integer.compare(o1.getSectionId(), o2.getSectionId());
		}
	};
	
	private static class SearchEdge {
		private final int toIndex;
		private final Jump jump;

		private SearchEdge(int toIndex, Jump jump) {
			this.toIndex = toIndex;
			this.jump = jump;
		}
	}

	private static class SearchGraph {
		private final FaultSubsectionCluster[] clusters;
		private final SearchEdge[][] edges;

		private SearchGraph(List<FaultSubsectionCluster> clusters) {
			this.clusters = clusters.toArray(new FaultSubsectionCluster[0]);
			this.edges = new SearchEdge[clusters.size()][];

			Map<FaultSubsectionCluster, Integer> indexMap = new IdentityHashMap<>();
			for (int i=0; i<clusters.size(); i++)
				Preconditions.checkState(indexMap.put(clusters.get(i), i) == null,
						"Duplicate cluster at index %s: %s", i, clusters.get(i));

			for (int i=0; i<clusters.size(); i++) {
				FaultSubsectionCluster from = clusters.get(i);
				List<SearchEdge> fromEdges = new ArrayList<>();
				// Build and sort this once rather than at every recursive search node.
				for (FaultSubsectionCluster to : from.getDistSortedConnectedClusters()) {
					Integer toIndex = indexMap.get(to);
					if (toIndex == null)
						// Public callers can supply clusters that also have connections outside this rupture.
						continue;
					Collection<Jump> jumpsTo = from.getConnectionsTo(to);
					Preconditions.checkState(jumpsTo.size() == 1, "%s paths between cluster pair?", jumpsTo.size());
					fromEdges.add(new SearchEdge(toIndex, jumpsTo.iterator().next()));
				}
				edges[i] = fromEdges.toArray(new SearchEdge[0]);
			}
		}
	}

	private class ClusterPath {
		private final FaultSubsectionCluster target;
		private final SearchGraph graph;
		private final int targetIndex;
		private final boolean[] available;
		private final int[] path;
		private final Jump[] jumps;
		private int pathSize;
		private double maxJumpDist;
		private double cmlJumpDist;

		public ClusterPath(SearchGraph graph, int startIndex, int targetIndex) {
			this.graph = graph;
			this.target = graph.clusters[targetIndex];
			this.targetIndex = targetIndex;
			this.available = new boolean[graph.clusters.length];
			Arrays.fill(available, true);
			available[startIndex] = false;
			this.path = new int[graph.clusters.length];
			this.path[0] = startIndex;
			this.jumps = new Jump[Math.max(0, graph.clusters.length-1)];
			this.pathSize = 1;
		}

		private void take(SearchEdge edge) {
			Preconditions.checkState(!isComplete());
			Preconditions.checkState(available[edge.toIndex]);
			available[edge.toIndex] = false;
			path[pathSize] = edge.toIndex;
			jumps[pathSize-1] = edge.jump;
			pathSize++;
			maxJumpDist = Math.max(maxJumpDist, edge.jump.distance);
			cmlJumpDist += edge.jump.distance;
		}

		private void backtrack(double previousMaxJumpDist, double previousCmlJumpDist) {
			Preconditions.checkState(pathSize > 1);
			int removedIndex = path[--pathSize];
			available[removedIndex] = true;
			jumps[pathSize-1] = null;
			maxJumpDist = previousMaxJumpDist;
			cmlJumpDist = previousCmlJumpDist;
		}
		
		public boolean isComplete() {
			return path[pathSize-1] == targetIndex;
		}

		private CompletedPath completedCopy() {
			Preconditions.checkState(isComplete());
			FaultSubsectionCluster[] completedPath = new FaultSubsectionCluster[pathSize];
			for (int i=0; i<pathSize; i++)
				completedPath[i] = graph.clusters[path[i]];
			return new CompletedPath(target, completedPath, Arrays.copyOf(jumps, pathSize-1),
					maxJumpDist, cmlJumpDist);
		}

		private int compareTo(CompletedPath o) {
			return comparePaths(jumps, pathSize-1, maxJumpDist, cmlJumpDist,
					o.jumps, o.jumps.length, o.maxJumpDist, o.cmlJumpDist);
		}

		private int currentIndex() {
			return path[pathSize-1];
		}
		
		public String toString() {
			FaultSubsectionCluster[] currentPath = new FaultSubsectionCluster[pathSize];
			for (int i=0; i<pathSize; i++)
				currentPath[i] = graph.clusters[path[i]];
			return pathToString(currentPath, jumps, target, maxJumpDist, cmlJumpDist, isComplete());
		}
	}

	private class CompletedPath implements Comparable<CompletedPath> {
		private final FaultSubsectionCluster target;
		private final FaultSubsectionCluster[] path;
		private final Jump[] jumps;
		private final double maxJumpDist;
		private final double cmlJumpDist;

		private CompletedPath(FaultSubsectionCluster target,
				FaultSubsectionCluster[] path, Jump[] jumps, double maxJumpDist, double cmlJumpDist) {
			this.target = target;
			this.path = path;
			this.jumps = jumps;
			this.maxJumpDist = maxJumpDist;
			this.cmlJumpDist = cmlJumpDist;
		}

		@Override
		public String toString() {
			return pathToString(path, jumps, target, maxJumpDist, cmlJumpDist, true);
		}

		@Override
		public int compareTo(CompletedPath o) {
			return comparePaths(jumps, jumps.length, maxJumpDist, cmlJumpDist,
					o.jumps, o.jumps.length, o.maxJumpDist, o.cmlJumpDist);
		}
	}

	private static String pathToString(FaultSubsectionCluster[] path, Jump[] jumps,
			FaultSubsectionCluster target, double maxJumpDist, double cmlJumpDist, boolean complete) {
			String str = "";
			for (int p=0; p<path.length; p++) {
				if (p == 0)
					str += "[";
				else {
					// close out previous
					Jump jump = jumps[p-1];
					int exitID = jump.fromSection.getSectionId();
					int entryID = jump.toSection.getSectionId();
					str += "; "+exitID+"] => ["+entryID+"; R="+distDF.format(jump.distance)+"; ";
				}
				str += path[p].parentSectionName;
			}
			str += "]";
			
			if (complete)
				str += " COMPLETE Rmax="+distDF.format(maxJumpDist)+", Rcml="+distDF.format(cmlJumpDist);
			else
				str += " INCOMPLETE (target: "+target.parentSectionName+") Rmax="
						+distDF.format(maxJumpDist)+", Rcml="+distDF.format(cmlJumpDist);
//			str += "\t"+maxJumpDist+"\t"+cmlJumpDist;
			return str;
	}

	private static double maxDistBefore(Jump[] jumps, int jumpCount, double maxDist) {
			double curMax = 0d;
			for (int i=0; i<jumpCount; i++) {
				Jump jump = jumps[i];
				if (jump.distance == maxDist)
					break;
				curMax = Math.max(curMax, jump.distance);
			}
			return curMax;
	}

	private int comparePaths(Jump[] myJumps, int myJumpCount, double myMaxJumpDist, double myCmlJumpDist,
			Jump[] oJumps, int oJumpCount, double oMaxJumpDist, double oCmlJumpDist) {
			if (cumulativeJumps) {
				return Double.compare(myCmlJumpDist, oCmlJumpDist);
			}
			int cmp = Double.compare(myMaxJumpDist, oMaxJumpDist);
			if (cmp != 0)
				return cmp;
			// we have the same max, but there could be multiple non-equal paths leading
			// to that maximum jump. choose the better one with the smallest max leading to
			// the max jump
			
			double myMaxDist = myMaxJumpDist;
			double oMaxDist = oMaxJumpDist;
			
//			System.out.println("max dists are equal ("+maxJumpDist+"), checking pre-max max dists");
			while (myMaxDist > 0 || oMaxDist > 0) {
				myMaxDist = maxDistBefore(myJumps, myJumpCount, myMaxDist);
				oMaxDist = maxDistBefore(oJumps, oJumpCount, oMaxDist);
				
//				System.out.println("myPrevMax="+myMaxDist+"\toPrevMax="+oMaxDist);
				cmp = Double.compare(myMaxDist, oMaxDist);
				if (cmp != 0) {
//					System.out.println("returning cmp="+cmp);
					return cmp;
				}
			}
			
			// if we're here then the previous max was equal between the two, so return the shortest path
			return Integer.compare(myJumpCount, oJumpCount);
	}
	
	private static final DecimalFormat distDF = new DecimalFormat("0.0");
	
	private class PathResult {
		private CompletedPath[] shortestPaths;
		private int completePathCount = 0;
		
		private void addPath(ClusterPath path, final boolean debug) {
			Preconditions.checkState(path.isComplete());
			if (shortestPaths == null) {
				CompletedPath completed = path.completedCopy();
				if (debug)
					System.out.println("\t\t\t\tNew shortest (first): "+completed);
				shortestPaths = new CompletedPath[] { completed };
			} else {
				// we already have one, compare them
				int cmp = path.compareTo(shortestPaths[0]);
//				System.out.println("Compared to shortest: "+cmp);
				if (cmp < 0) {
					// we're new shortest path
					CompletedPath completed = path.completedCopy();
					if (debug)
						System.out.println("\t\t\t\tNew shortest (better): "+completed);
					shortestPaths = new CompletedPath[] { completed };
				} else if (cmp == 0) {
					CompletedPath completed = path.completedCopy();
					if (debug)
						System.out.println("\t\t\t\tIt's a tie! Additional shortest: "+completed);
					shortestPaths = Arrays.copyOf(shortestPaths, shortestPaths.length+1);
					shortestPaths[shortestPaths.length-1] = completed;
				}
			}
			completePathCount++;
		}
	}
	
	private void pathSearch(final ClusterPath basePath, final PathResult result, final boolean debug) {
		Preconditions.checkState(!basePath.isComplete());
		
		for (SearchEdge edge : basePath.graph.edges[basePath.currentIndex()]) {
			FaultSubsectionCluster to = basePath.graph.clusters[edge.toIndex];
			if (!basePath.available[edge.toIndex]) {
				if (debug)
					System.out.println("\t\t\t\tCan't try because not available: ["
							+to.parentSectionName+"; "+to.parentSectionID+"]");
				continue;
			}
			double previousMaxJumpDist = basePath.maxJumpDist;
			double previousCmlJumpDist = basePath.cmlJumpDist;
			basePath.take(edge);
			if (debug)
				System.out.println("\t\t\t"+basePath);
//			if (debug) System.out.println("\t\t\t\tTaking "+sect.getSectionId()+" complete="+path.isComplete()
//							+", dist="+(float)path.cumulativeDistance);
			if (basePath.isComplete()) {
				result.addPath(basePath, debug);
			} else {
				if (result.shortestPaths != null && basePath.compareTo(result.shortestPaths[0]) > 0) {
					// already worse than our current best, stop here
					if (debug)
						System.out.println("\t\t\t\tStopping this search as we're worse than the shortest");
					basePath.backtrack(previousMaxJumpDist, previousCmlJumpDist);
					continue;
				}
				pathSearch(basePath, result, debug);
			}
			basePath.backtrack(previousMaxJumpDist, previousCmlJumpDist);
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
	
	/**
	 * Calculates all of the possible jumps between the given clusters. Jumps are not duplicated
	 * or included in both directions. Each jump will be ordered such that fromSection has a smaller
	 * subsection ID than toSection.
	 * @param rupClusters
	 * @param debug
	 * @return
	 */
	public List<Jump> calcRuptureJumps(List<FaultSubsectionCluster> rupClusters, final boolean debug) {
		List<Jump> jumps = new ArrayList<>();
		SearchGraph graph = new SearchGraph(rupClusters);
		
		if (debug) System.out.println("Searching for connections...");
		
		int numCompletePaths = 0;
		
		HashSet<IDPairing> uniques = new HashSet<>();
		
		for (int i=0; i<rupClusters.size(); i++) {
			FaultSubsectionCluster from = rupClusters.get(i);
			if (debug) {
				System.out.println("\tFrom cluster "+0+", "+from.parentSectionName);
				System.out.println("\t\tAvailable direct jumps:");
				for (SearchEdge edge : graph.edges[i]) {
					FaultSubsectionCluster to = graph.clusters[edge.toIndex];
					System.out.println("\t\t\t["+to.parentSectionID+"; "+to.parentSectionName
							+"] R="+distDF.format(edge.jump.distance));
				}
			}
			
			// TODO: add check for no connections possible?
			
			for (int j=i+1; j<rupClusters.size(); j++) {
				FaultSubsectionCluster target = rupClusters.get(j);
				ClusterPath basePath = new ClusterPath(graph, i, j);
				
				if (debug) System.out.println("\t\tSearching to cluster "+j+", "+target.parentSectionName);
				
				PathResult result = new PathResult();
				pathSearch(basePath, result, debug);
				
				CompletedPath[] shortestPaths = result.shortestPaths;
				if (shortestPaths != null) {
					// we have a valid path
					
					for (CompletedPath shortest : shortestPaths) {
						// only keep the first jump (as others are constrained and may not represent actual shortest path)
						Jump jump = shortest.jumps[0];
						IDPairing pair = jumpPair(jump);
						if (!uniques.contains(pair)) {
							// always add it with the lower section ID first
							if (jump.fromSection.getSectionId() < jump.toSection.getSectionId())
								jumps.add(jump);
							else
								jumps.add(jump.reverse());
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
		return buildClusterRupture(rupIndex, false, debug);
	}
	
	public ClusterRupture buildClusterRupture(int rupIndex, boolean maintainOrder, final boolean debug) {
		List<FaultSection> sects = rupSet.getFaultSectionDataForRupture(rupIndex);
		
		if (debug) System.out.println("Building clusters for "+rupIndex);
		
		// calculate clusters (between which there may be connections
		List<FaultSubsectionCluster> rupClusters = calcClusters(sects, debug);
		
		if (debug) {
			System.out.println("\tHave "+rupClusters.size()+" clusters:");
			for (FaultSubsectionCluster cluster : rupClusters)
				System.out.println("\t\t"+cluster+": "+rupSet.getFaultSectionData(cluster.startSect.getSectionId()).getParentSectionName());
		}
		
		List<Jump> jumps = calcRuptureJumps(rupClusters, debug);
		FaultSubsectionCluster startCluster = null;
		if (maintainOrder) {
			FaultSection firstSect = sects.get(0);
			for (FaultSubsectionCluster cluster : rupClusters) {
				if (cluster.contains(firstSect)) {
					startCluster = cluster;
					break;
				}
			}
		}
		return buildClusterRupture(rupClusters, jumps, debug, startCluster);
	}
	
	public ClusterRupture buildClusterRupture(List<FaultSubsectionCluster> rupClusters,
			List<Jump> jumps, final boolean debug) {
		return buildClusterRupture(rupClusters, jumps, debug, null);
	}
	
	public ClusterRupture buildClusterRupture(List<FaultSubsectionCluster> rupClusters,
			List<Jump> jumps, final boolean debug, FaultSubsectionCluster startCluster) {
		
		Multimap<FaultSubsectionCluster, Jump> jumpsFromMap = HashMultimap.create();
//		if (debug) {
		// now always do this; turns out it was affecting things?
			Collections.sort(jumps, Jump.id_comparator);
			rupClusters = new ArrayList<>(rupClusters);
			Collections.sort(rupClusters);
//		}
		for (Jump jump : jumps) {
			jumpsFromMap.put(jump.fromCluster, jump);
			Jump reversed = jump.reverse();
			jumpsFromMap.put(reversed.fromCluster, reversed);
			if (debug) System.out.println("Available jump: "+jump);
		}
		
		if (startCluster == null) {
			// calculate isolation score where each first level connection costs 1, 2nd level
			// costs 0.1, 3rd 0.01, etc...
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
					} else if (score == minClusterScore) {
						// go by number of isolated end sections
						int prevIsolatedSects = calcNumIsolatedEndSubsections(startCluster);
						int myIsolatedSects = calcNumIsolatedEndSubsections(cluster);
						if (debug) System.out.println("\t\tTie. I have "+myIsolatedSects
								+" isolated sects, prev has "+prevIsolatedSects);
						if (myIsolatedSects > prevIsolatedSects) {
							startCluster = cluster;
							minClusterScore = score;
						}
					}
				}
				
				if (debug)
					System.out.println("Most isolated cluster: "+startCluster+"\tscore="+minClusterScore);
			} else {
				startCluster = rupClusters.get(0);
			}
		}
		
		HashSet<FaultSubsectionCluster> availableClusters = new HashSet<>(rupClusters);
		
		if (!startCluster.startSect.equals(startCluster.subSects.get(0)))
			startCluster = changeStartSection(startCluster, startCluster.subSects.get(0), availableClusters, jumpsFromMap);
		
		if (rupClusters.size() > 1) {
			// see if we should reverse the starting cluster
			Preconditions.checkState(jumpsFromMap.containsKey(startCluster),
					"No jumps from starting cluster %s, but have %s clusters in total (%s jumpsFrom)",
					startCluster, rupClusters.size(), jumpsFromMap.size());
			int minJumpSectIndex = Integer.MAX_VALUE;
			int maxJumpSectIndex = 0;
			for (Jump jump : jumpsFromMap.get(startCluster)) {
				int ind = startCluster.subSects.indexOf(jump.fromSection);
				minJumpSectIndex = Integer.min(minJumpSectIndex, ind);
				maxJumpSectIndex = Integer.max(maxJumpSectIndex, ind);
			}
			if (minJumpSectIndex < (startCluster.subSects.size()-(maxJumpSectIndex+1))) {
				// jump is closer to the start than the end
				if (debug)
					System.out.println("Reversing startCluster as minJumpSectIndex="+minJumpSectIndex
							+", maxJumpSectIndex="+maxJumpSectIndex+", and size="+startCluster.subSects.size());
				startCluster = reverseCluster(startCluster, availableClusters, jumpsFromMap);
			}
		}
		
		Preconditions.checkState(availableClusters.remove(startCluster));
		
		ClusterRupture rupture = new ClusterRupture(startCluster);
		
		rupture = buildRupture(rupture, rupture, availableClusters, jumpsFromMap, debug);
		if (debug) {
			System.out.println("Final rupture:\n"+rupture);
			System.out.flush();
		}
		Preconditions.checkState(availableClusters.isEmpty(),
				"Didn't use all available clusters when building rupture, have %s left."
				+ "\n\tRupture: %s\n\tAvailable clusters: %s",
				availableClusters.size(), rupture, availableClusters);
		
		return rupture;
	}
	
	private static FaultSubsectionCluster reverseCluster(FaultSubsectionCluster cluster,
			HashSet<FaultSubsectionCluster> availableClusters,
			Multimap<FaultSubsectionCluster, Jump> jumpsFromMap) {
		FaultSubsectionCluster reversed = cluster.reversed();
		updateAvaialableClustersJumps(cluster, reversed, availableClusters, jumpsFromMap);
		return reversed;
	}
	
	private static FaultSubsectionCluster changeStartSection(FaultSubsectionCluster cluster, FaultSection startSect,
			HashSet<FaultSubsectionCluster> availableClusters,
			Multimap<FaultSubsectionCluster, Jump> jumpsFromMap) {
		FaultSubsectionCluster revised = new FaultSubsectionCluster(cluster.subSects, startSect, cluster.endSects);
		updateAvaialableClustersJumps(cluster, revised, availableClusters, jumpsFromMap);
		return revised;
	}
	
	private static void updateAvaialableClustersJumps(FaultSubsectionCluster origCluster, FaultSubsectionCluster revised,
			HashSet<FaultSubsectionCluster> availableClusters,
			Multimap<FaultSubsectionCluster, Jump> jumpsFromMap) {
		if (availableClusters.contains(origCluster)) {
			availableClusters.remove(origCluster);
			availableClusters.add(revised);
		}
		if (jumpsFromMap.containsKey(origCluster)) {
//			System.out.println("Size before original replace: "+jumpsFromMap.size());
			Collection<Jump> origJumps = new ArrayList<>(jumpsFromMap.get(origCluster));
			jumpsFromMap.removeAll(origCluster);
//			System.out.println("Size middle original replace: "+jumpsFromMap.size());
			jumpsFromMap.putAll(revised, origJumps);
//			System.out.println("Size after original replace: "+jumpsFromMap.size());
		}
		
		// now replace all jumps to point to the revised cluster
		Table<FaultSubsectionCluster, Jump, Jump> replaceTable = HashBasedTable.create();
		for (Entry<FaultSubsectionCluster, Jump> entry : jumpsFromMap.entries()) {
			Jump jump = entry.getValue();
//			System.out.println("Testing "+jump+" for replacement");
			if (jump.fromCluster == origCluster) {
				Jump newJump = new Jump(jump.fromSection, revised,
						jump.toSection, jump.toCluster, jump.distance);
				replaceTable.put(entry.getKey(), jump, newJump);
				revised.addConnection(newJump);
			} else if (jump.toCluster == origCluster) {
				Jump newJump = new Jump(jump.fromSection, jump.fromCluster,
						jump.toSection, revised, jump.distance);
				replaceTable.put(entry.getKey(), jump, newJump);
			}
		}
		for (Cell<FaultSubsectionCluster, Jump, Jump> cell : replaceTable.cellSet()) {
			FaultSubsectionCluster fromCluster = cell.getRowKey();
			Jump oldJump = cell.getColumnKey();
			Jump newJump = cell.getValue();
//			System.out.println("Replacing jump "+oldJump+" with "+newJump);
			Preconditions.checkNotNull(jumpsFromMap.remove(fromCluster, oldJump));
			jumpsFromMap.put(fromCluster, newJump);
		}
	}
	
	private ClusterRupture buildRupture(ClusterRupture rupture, ClusterRupture currentStrand,
			HashSet<FaultSubsectionCluster> availableClusters,
			Multimap<FaultSubsectionCluster, Jump> jumpsFromMap, final boolean debug) {
		// first build out this strand and all jumps from it
		if (debug) System.out.println("Building strand: "+currentStrand);
		while (true) {
			boolean extended = false;
			FaultSubsectionCluster lastCluster = currentStrand.clusters[currentStrand.clusters.length-1];
			if (debug)
				System.out.println("Looking for jumps off of end cluster, from "+lastCluster+" with available clusters: "+availableClusters);
			// wrap in an arraylist here to avoid concurrent modification exception if we reverse a toCluster
			// the update won't actually affect this list, as that toCluster will already have been processed
			// and can't occur again
			List<Jump> jumpsFromList = new ArrayList<>(jumpsFromMap.get(lastCluster));
			Collections.sort(jumpsFromList, Jump.dist_comparator);
			for (Jump jump : jumpsFromList) {
				if (!availableClusters.contains(jump.toCluster)) {
					// already taken this jump
					if (debug) System.out.println("Skipping jump "+jump+" as toCluster not in available set: "+jump.toCluster);
					continue;
				}
				// see if this is the shortest jump to this cluster or if there is a better alternative
				// from our current rupture
				FaultSubsectionCluster currentEnd = currentStrand.clusters[currentStrand.clusters.length-1];
				boolean deferToCurrentEnd = false;
				shorterJumpSearch:
				for (FaultSubsectionCluster cluster : rupture.clusters) {
					for (Jump oJump : jumpsFromMap.get(cluster)) {
						if (oJump.toCluster == jump.toCluster && oJump.distance < jump.distance) {
							if (oJump.fromCluster == currentEnd && currentEnd != lastCluster) {
								// jumpsFromList is a snapshot from lastCluster, but an earlier jump in that
								// list extended this strand. Defer so the next pass processes all jumps
								// from the new end rather than skipping its other branches.
								deferToCurrentEnd = true;
								break shorterJumpSearch;
							}
							if (debug)
								System.out.println("Was going to take "+jump
										+", but will instead take shorter "+oJump);
							jump = oJump;
						}
					}
				}
				if (deferToCurrentEnd) {
					if (debug)
						System.out.println("Deferring jump to "+jump.toCluster
								+" until processing new strand end "+currentEnd);
					continue;
				}
				if (debug)
					System.out.println("Evaluating jump "+jump+" with toCluster="+jump.toCluster);
				int sectIndex = jump.toCluster.subSects.indexOf(jump.toSection);
				if (sectIndex > (jump.toCluster.subSects.size() - (sectIndex + 1))) {
					if (debug) System.out.println("Reversing toCluster with sectIndex="+sectIndex
							+" and size="+jump.toCluster.subSects.size());
					FaultSubsectionCluster toCluster = reverseCluster(
							jump.toCluster, availableClusters, jumpsFromMap);
					jump = new Jump(jump.fromSection, jump.fromCluster, jump.toSection,
							toCluster, jump.distance);
					if (debug) System.out.println("\tReversed: "+toCluster);
				}
				if (!jump.toSection.equals(jump.toCluster.startSect)) {
					if (debug) System.out.println("Correcting toSection in toCluster");
					FaultSubsectionCluster toCluster = changeStartSection(
							jump.toCluster, jump.toSection, availableClusters, jumpsFromMap);
					jump = new Jump(jump.fromSection, jump.fromCluster, jump.toSection,
							toCluster, jump.distance);
					if (debug) System.out.println("\tCorrected: "+toCluster);
				}
				availableClusters.remove(jump.toCluster);
				if (debug) System.out.println("\tTaking jump: "+jump);
				rupture = rupture.take(correctJumpDist(jump));
				// current strand has now been replaced, find the new one
				currentStrand = splaySearchRecursive(rupture, currentStrand.clusters[0]);
				if (debug) System.out.println("Current strand after jump: "+currentStrand);
				Preconditions.checkNotNull(currentStrand, "current strand could not be found after taking jump");
				extended = true;
			}
			if (!extended)
				break;
		}
		// now build out each splay from this strand
		for (ClusterRupture splay : currentStrand.splays.values()) {
			rupture = buildRupture(rupture, splay, availableClusters, jumpsFromMap, debug);
		}
		return rupture;
	}
	
	private Jump correctJumpDist(Jump jump) {
		double minDist = Double.POSITIVE_INFINITY;
		for (FaultSection s1 : jump.fromCluster.subSects)
			for (FaultSection s2 : jump.toCluster.subSects)
				minDist = Double.min(minDist, distCalc.getDistance(s1, s2));
		return new Jump(jump.fromSection, jump.fromCluster, jump.toSection, jump.toCluster, minDist);
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
	
	private static int calcNumIsolatedEndSubsections(FaultSubsectionCluster cluster) {
		int max = 0;
		// search increasing index
		for (int i=0; i<cluster.subSects.size(); i++) {
			if (cluster.getConnections(cluster.subSects.get(i)).isEmpty())
				max = i;
			else
				break;
		}
		// search decreasing index
		for (int i=0; i<cluster.subSects.size(); i++) {
			int ind = (cluster.subSects.size()-1)-i;
			if (cluster.getConnections(cluster.subSects.get(ind)).isEmpty())
				max = i;
			else
				break;
		}
		return max;
	}
	
	public void plotConnections(File outputDir, String prefix, int rupIndex) throws IOException {
		plotConnections(outputDir, prefix, rupIndex, null, null, null);
	}
	
	public void plotConnections(File outputDir, String prefix, int rupIndex, ClusterRupture rup) throws IOException {
		plotConnections(outputDir, prefix, rupIndex, rup, null, null);
	}
	
	public void plotConnections(File outputDir, String prefix, int rupIndex,
			Set<Jump> highlightConn, String highlightName) throws IOException {
		plotConnections(outputDir, prefix, rupIndex, null, highlightConn, highlightName);
	}
	
	public void plotConnections(File outputDir, String prefix, int rupIndex, ClusterRupture rup,
			Set<Jump> highlightConn, String highlightName) throws IOException {
//		HashSet<IDPairing> connections = calcConnections(rupIndex, true);
		List<FaultSection> sects = rupSet.getFaultSectionDataForRupture(rupIndex);
		List<FaultSubsectionCluster> clusters = calcClusters(sects, false);
//		List<Jump> jumps = calcRuptureJumps(clusters, true);
		List<Jump> jumps = new ArrayList<>();
		if (rup == null)
			rup = buildClusterRupture(rupIndex, false);
		for (Jump jump : rup.getJumpsIterable())
			jumps.add(jump);

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
		HashSet<Jump> connections = new HashSet<>();
		for (Jump jump : jumps) {
			DefaultXY_DataSet xy = new DefaultXY_DataSet();
			maxDist = Math.max(maxDist, jump.distance);
			
			if (first) {
				xy.setName("Connections");
				first = false;
			}
			
			if (jump.fromSection.getSectionId() < jump.toSection.getSectionId())
				connections.add(jump);
			else
				connections.add(jump.reverse());
			Location loc1 = middles.get(jump.fromSection.getSectionId());
			Location loc2 = middles.get(jump.toSection.getSectionId());
			
			xy.set(loc1.getLongitude(), loc1.getLatitude());
			xy.set(loc2.getLongitude(), loc2.getLatitude());
			
			funcs.add(xy);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, connectedColor));
		}
		
		if (highlightConn != null) {
			boolean firstHighlight = true;
			for (Jump connection : connections) {
				if (!highlightConn.contains(connection))
					continue;
				DefaultXY_DataSet xy = new DefaultXY_DataSet();
				if (firstHighlight) {
					xy.setName(highlightName);
					firstHighlight = false;
				}
				
				Location loc1 = middles.get(connection.fromSection.getSectionId());
				Location loc2 = middles.get(connection.toSection.getSectionId());
				
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
//		int[] plotIndexes = { 302, 462, 97810, 132521 };
////		int[] plotIndexes = { 132521 };
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
		
		FaultSystemRupSet rupSet = U3FaultSystemIO.loadRupSet(fssFile);
		
		SectionDistanceAzimuthCalculator distCalc =
				new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
		RuptureConnectionSearch search = new RuptureConnectionSearch(rupSet, distCalc,
				maxPossibleJumpDist, CUMULATIVE_JUMPS_DEFAULT);
		
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
				ClusterRupture rup = null;
				try {
					rup = search.buildClusterRupture(r, false);
				} catch (Exception e) {
					System.out.println("detected an exception with "+r+", will redo with debugging enabled");
					System.out.println("Full rupture: "+Joiner.on(",").join(rupSet.getSectionsIndicesForRup(r)));
					rup = search.buildClusterRupture(r, true);
				}
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

	@Override
	public String getName() {
		return "Rupture Connection Search";
	}

	@Override
	public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
		if (this.rupSet != null)
			Preconditions.checkState(rupSet.isEquivalentTo(parent));
		this.rupSet = parent;
	}

	@Override
	public FaultSystemRupSet getParent() {
		return rupSet;
	}

	@Override
	public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
		if (this.rupSet != null)
			Preconditions.checkState(rupSet.isEquivalentTo(newParent));
		return new RuptureConnectionSearch(newParent, distCalc, maxJumpDist, cumulativeJumps);
	}

}
