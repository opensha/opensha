package org.opensha.sha.earthquake.rupForecastImpl.nshm23.data;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.BinaryJumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.HardcodedBinaryJumpProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.HardcodedJumpProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveBilateralRuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveBilateralRuptureGrowingStrategy.SecondaryVariations;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.PlausibleClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.ConnectivityCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public class NSHM23_WasatchSegmentationData {
	
	// maximum distance that the closest trace must be to the segmentation location
	private static final double MAX_MAP_DIST_1 = 5d;
	// maximum distance that the 2nd closest trace must be to the segmentation location
	private static final double MAX_MAP_DIST_2 = 10d;
	// section names must contain this in order to be mapped (must be lower case)
	private static final String MAP_SECT_NAME_REQUIREMENT = "wasatch";
	
	// if true, segmentation constraint applied to all jumps to/from the given sections, not just between the pair.
	// this will disallow ruptures to bypass it by taking another (non-wasatch) path, like the West Valley fault
	public static boolean APPLY_TO_ALL_JUMPS_FROM_LOC = true;
	
	private static class WasatchSegLocation {
		private final int id;
		private final String name;
		private final String notes;
		private final Location loc;
		
		public WasatchSegLocation(int id, String name, String notes, Location loc) {
			super();
			this.id = id;
			this.name = name;
			this.notes = notes;
			this.loc = loc;
		}
	}
	
	private static final String DATA_PATH = "/data/erf/nshm23/constraints/segmentation/wasatch/NSHM_WFZ_segbdry_Jun3_2022.csv";
	
	private static List<WasatchSegLocation> load() throws IOException {
		CSVFile<String> csv = CSVFile.readStream(WasatchSegLocation.class.getResourceAsStream(DATA_PATH), true);
		
		List<WasatchSegLocation> ret = new ArrayList<>();
		for (int row=1; row<csv.getNumRows(); row++) {
			int id = csv.getInt(row, 0);
			String name = csv.get(row, 1);
			String notes = csv.get(row, 2);
			double lat = csv.getDouble(row, 3);
			double lon = csv.getDouble(row, 4);
			Location loc = new Location(lat, lon);
			ret.add(new WasatchSegLocation(id, name, notes, loc));
		}
		
		return ret;
	}
	
	private static boolean isWasatchSection(FaultSection subSect) {
		Preconditions.checkState(subSect.getParentSectionId() >= 0, "Must call this with subsections");
		return subSect.getParentSectionName().toLowerCase().contains(MAP_SECT_NAME_REQUIREMENT);
	}
	
	private static Map<WasatchSegLocation, Set<IDPairing>> mapToParentSects(List<WasatchSegLocation> datas,
			FaultSystemRupSet rupSet, boolean verbose) {
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		List<FaultSection> wasatchSects = new ArrayList<>();
		for (FaultSection subSect : subSects)
			if (isWasatchSection(subSect))
				wasatchSects.add(subSect);
		if (verbose) System.out.println("Found "+wasatchSects.size()+" candidate Wasatch sections");
		if (wasatchSects.isEmpty())
			return null;
		
		Map<WasatchSegLocation, Set<IDPairing>> ret = new HashMap<>();
		
		Map<Integer, String> parentNames = new HashMap<>();
		List<LocationList> discrTraces = new ArrayList<>();
		for (FaultSection sect : wasatchSects) {
			discrTraces.add(sect.getFaultSurface(1d).getEvenlyDiscritizedUpperEdge());
			parentNames.put(sect.getParentSectionId(), sect.getParentSectionName());
		}
		
		ClusterRuptures cRups = null;
		if (APPLY_TO_ALL_JUMPS_FROM_LOC)
			cRups = rupSet.requireModule(ClusterRuptures.class);
		
		if (verbose) System.out.println("Mapping "+datas.size()+" Wasatch segmentation locations");
		for (WasatchSegLocation data : datas) {
			if (verbose) System.out.println("Mapping "+data.id+". "+data.name);
			Map<Integer, Double> parentDists = new HashMap<>();
			Map<Integer, Integer> parentClosestSects = new HashMap<>();
			for (int i=0; i<wasatchSects.size(); i++) {
				FaultSection subSect = wasatchSects.get(i);
				LocationList trace = discrTraces.get(i);
				double minDist = Double.POSITIVE_INFINITY;
				for (Location loc : trace)
					minDist = Math.min(minDist, LocationUtils.horzDistanceFast(loc, data.loc));
				if ((float)minDist > (float)MAX_MAP_DIST_1 && (float)minDist > (float)MAX_MAP_DIST_2)
					continue;
				Integer parentID = subSect.getParentSectionId();
				// true if this is the closest section yet found on this parent
				boolean closest = !parentDists.containsKey(parentID) || minDist < parentDists.get(parentID);
				if (closest) {
					parentDists.put(parentID, minDist);
					parentClosestSects.put(parentID, subSect.getSectionId());
				}
			}
			if (verbose) System.out.println("\tFound "+parentDists.size()+" candidate sections");
			if (parentDists.size() < 2)
				// need 2 sections
				continue;
			List<Integer> sortedParentIDs = ComparablePairing.getSortedData(parentDists);
			int parentID1 = sortedParentIDs.get(0);
			int sectID1 = parentClosestSects.get(parentID1);
			double dist1 = parentDists.get(parentID1);
			String name1 = parentNames.get(parentID1);
			int parentID2 = sortedParentIDs.get(1);
			int sectID2 = parentClosestSects.get(parentID2);
			double dist2 = parentDists.get(parentID2);
			String name2 = parentNames.get(parentID2);
			if (dist1 < MAX_MAP_DIST_1 && dist2 < MAX_MAP_DIST_2) {
				// it's a match
				if (verbose) System.out.println("\tMatch found between: "
						+parentID1+". "+name1+" ("+(float)dist1+" km) and "+parentID2+". "+name2+" ("+(float)dist2+" km)");
				
				HashSet<IDPairing> pairings = new HashSet<>();
				addBetween(pairings, parentID1, parentID2);
				
				if (APPLY_TO_ALL_JUMPS_FROM_LOC) {
					// see if there are any other jumps we should also constrain to/from these sections
					
					// first look for direct jumps to/from the sections involved
					for (int segID : new int[] {sectID1, sectID2}) {
						FaultSection sect = rupSet.getFaultSectionData(segID);
						for (int rupIndex : rupSet.getRupturesForSection(segID)) {
							ClusterRupture cRup = cRups.get(rupIndex);
							RuptureTreeNavigator nav = cRup.getTreeNavigator();
							
							FaultSection predecessor = nav.getPredecessor(sect);
							if (predecessor != null && predecessor.getParentSectionId() != sect.getParentSectionId()) {
								boolean isNew = addBetween(pairings, sect.getParentSectionId(), predecessor.getParentSectionId());
								if (verbose && isNew)
									System.out.println("Also applying between "+sect.getParentSectionName()
										+" and "+predecessor.getParentSectionName());
							}
							for (FaultSection descendant : nav.getDescendants(sect)) {
								if (descendant.getParentSectionId() != sect.getParentSectionId()) {
									boolean isNew = addBetween(pairings, sect.getParentSectionId(), descendant.getParentSectionId());
									if (verbose && isNew) System.out.println("Also applying between "+sect.getParentSectionName()
										+" and "+descendant.getParentSectionName());
								}
							}
						}
					}
					
					// now see if there are any jumps used to connect these points indirectly
					for (int rupIndex : rupSet.getRupturesForParentSection(parentID1)) {
						ClusterRupture cRup = cRups.get(rupIndex);
						FaultSubsectionCluster cluster1 = null;
						FaultSubsectionCluster cluster2 = null;
						for (FaultSubsectionCluster cluster : cRup.getClustersIterable()) {
							if (cluster.parentSectionID == parentID1)
								cluster1 = cluster;
							if (cluster.parentSectionID == parentID2)
								cluster2 = cluster;
						}
						Preconditions.checkNotNull(cluster1);
						if (cluster2 != null) {
							// this is a rupture that contains both of them, find out if it used any intermediate jumps
							RuptureTreeNavigator nav = cRup.getTreeNavigator();
							
							if (!nav.hasJump(cluster1, cluster2)) {
								// no direct jump, need to find the indirect path and restrict it
//								System.out.println(cRup);
								List<FaultSubsectionCluster> linkingPath = getPathLinking(cluster1, cluster2, nav);
								Preconditions.checkState(linkingPath.size() >= 3);
								
								FaultSubsectionCluster link1 = linkingPath.get(1);
								FaultSubsectionCluster link2 = linkingPath.get(linkingPath.size()-2);
								
								// see if it's already prohibited by prior direct jumps
								if (pairings.contains(new IDPairing(cluster1.parentSectionID, link1.parentSectionID))
										|| pairings.contains(new IDPairing(cluster1.parentSectionID, link2.parentSectionID))) {
									// path is already blocked, we can skip
									continue;
								}
								
								boolean newBlock = false;
								if (link1 == link2) {
									// simple case, only 1 link to block
									// block it on both ends
									if (addBetween(pairings, cluster1.parentSectionID, link1.parentSectionID))
										newBlock = true;
									if (addBetween(pairings, cluster2.parentSectionID, link1.parentSectionID))
										newBlock = true;
								} else {
									// block off the end that is closer to the actual segmentation point
									double link1Dist = Double.POSITIVE_INFINITY;
									for (FaultSection sect : link1.subSects)
										for (Location loc : sect.getFaultTrace())
											link1Dist = Math.min(link1Dist, LocationUtils.horzDistanceFast(loc, data.loc));
									double link2Dist = Double.POSITIVE_INFINITY;
									for (FaultSection sect : link2.subSects)
										for (Location loc : sect.getFaultTrace())
											link2Dist = Math.min(link2Dist, LocationUtils.horzDistanceFast(loc, data.loc));
									if (link1Dist < link2Dist) {
										// block cluster1 -> link1
										if (addBetween(pairings, cluster1.parentSectionID, link1.parentSectionID))
											newBlock = true;
									} else {
										// block cluster2 -> link2
										if (addBetween(pairings, cluster2.parentSectionID, link2.parentSectionID))
											newBlock = true;
									}
								}
								
								if (verbose && newBlock) {
									String linkStr = null;
									for (FaultSubsectionCluster cluster : linkingPath) {
										if (linkStr == null)
											linkStr = "";
										else
											linkStr += " -> ";
										linkStr += cluster.parentSectionName;
									}
									System.out.println("Also applying to indirect connection: "+
											linkStr);
								}
							}
						}
					}
				}
				
				ret.put(data, pairings);
			} else if (verbose) {
				if (verbose) System.out.println("\tNo match found. Closest 2 were: "
						+parentID1+". "+name1+" ("+(float)dist1+" km) and "+parentID2+". "+name2+" ("+(float)dist2+" km)");
			}
		}
		
		return ret;
	}
	
	private static boolean addBetween(HashSet<IDPairing> pairings, int parent1, int parent2) {
		IDPairing test12 = new IDPairing(parent1, parent2);
		IDPairing test21 = new IDPairing(parent2, parent1);
		if (pairings.contains(test12) || pairings.contains(test21))
			return false;
		pairings.add(test12);
		pairings.add(test21);
		return true;
	}
	
	/**
	 * Finds the path between cluster1 and cluster2
	 * @param cluster1
	 * @param cluster2
	 * @param nav
	 * @return
	 */
	private static List<FaultSubsectionCluster> getPathLinking(FaultSubsectionCluster cluster1, FaultSubsectionCluster cluster2,
			RuptureTreeNavigator nav) {
		List<FaultSubsectionCluster> curPath = List.of(cluster1);
		List<FaultSubsectionCluster> linkingPath = linkingPathSearch(curPath, cluster2, nav, true);
		if (linkingPath == null)
			// try the other direction;
			linkingPath = linkingPathSearch(curPath, cluster2, nav, false);
		Preconditions.checkNotNull(linkingPath, "No path found linking %s with %s", cluster1, cluster2);
		Preconditions.checkState(linkingPath.get(0) == cluster1);
		Preconditions.checkState(linkingPath.get(linkingPath.size()-1) == cluster2);
		return linkingPath;
	}
	
	private static List<FaultSubsectionCluster> linkingPathSearch(List<FaultSubsectionCluster> curPath,
			FaultSubsectionCluster dest, RuptureTreeNavigator nav, boolean direction) {
		Preconditions.checkState(!curPath.isEmpty());
		FaultSubsectionCluster curLast = curPath.get(curPath.size()-1);
		if (curLast == dest)
			return curPath;
		
		if (direction) {
			// forwards
			Collection<FaultSubsectionCluster> descendants = nav.getDescendants(curLast);
			if (descendants.isEmpty())
				return null;
			for (FaultSubsectionCluster descendant : descendants) {
				List<FaultSubsectionCluster> newPath = new ArrayList<>(curPath.size()+1);
				newPath.addAll(curPath);
				newPath.add(descendant);
				List<FaultSubsectionCluster> finalPath = linkingPathSearch(newPath, dest, nav, direction);
				if (finalPath != null)
					return finalPath;
			}
			// if we got here, no paths led to it
			return null;
		} else {
			// backwards
			FaultSubsectionCluster predecessor = nav.getPredecessor(curLast);
			if (predecessor == null)
				return null;
			List<FaultSubsectionCluster> newPath = new ArrayList<>(curPath.size()+1);
			newPath.addAll(curPath);
			newPath.add(predecessor);
			return linkingPathSearch(newPath, dest, nav, direction);
		}
	}
	
	private static Set<IDPairing> allPairings(Map<WasatchSegLocation, Set<IDPairing>> mappings) {
		HashSet<IDPairing> pairings = new HashSet<>();
		for (Set<IDPairing> subPairings : mappings.values())
			pairings.addAll(subPairings);
		return pairings;
	}
	
	public static HardcodedJumpProb build(FaultSystemRupSet rupSet, double passthroughRate) {
		return build(rupSet, passthroughRate, null);
	}
	
	public static HardcodedJumpProb build(FaultSystemRupSet rupSet, double passthroughRate,
			JumpProbabilityCalc fallback) {
		List<WasatchSegLocation> datas;
		try {
			datas = load();
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		Map<WasatchSegLocation, Set<IDPairing>> mappings = mapToParentSects(datas, rupSet, false);
		if (mappings == null)
			return null;
		
		Map<IDPairing, Double> idsToProbs = new HashMap<>();
		for (IDPairing pair : allPairings(mappings))
			idsToProbs.put(pair, passthroughRate);
		
		String name = "Wasatch, P="+(float)passthroughRate;
		if (fallback != null)
			name += ", "+fallback.getName();
		
		return new HardcodedJumpProb(name, idsToProbs, true, fallback);
	}
	
	public static BinaryJumpProbabilityCalc buildFullExclusion(FaultSystemRupSet rupSet,
			BinaryJumpProbabilityCalc fallback) {
		List<WasatchSegLocation> datas;
		try {
			datas = load();
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		Map<WasatchSegLocation, Set<IDPairing>> mappings = mapToParentSects(datas, rupSet, false);
		if (mappings == null || mappings.isEmpty())
			return null;
		
		Set<IDPairing> pairings = allPairings(mappings);
		
		String name = "Wastach Exclude";
		if (fallback != null)
			name += ", "+fallback.getName();
		
		return new HardcodedBinaryJumpProb(name, true, pairings, true);
	}
	
	private static void printSegPaths(FaultSystemRupSet rupSet, List<JumpProbabilityCalc> segModels, int fromSectID, int toSectID) {
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		
		FaultSection sect1 = rupSet.getFaultSectionData(fromSectID);
		FaultSection sect2 = rupSet.getFaultSectionData(toSectID);
		
		HashSet<Integer> rupIndexes1 = new HashSet<>(rupSet.getRupturesForSection(fromSectID));
		HashSet<Integer> rupIndexes2 = new HashSet<>(rupSet.getRupturesForSection(toSectID));
		HashSet<Integer> bothRupIndexes = new HashSet<>();
		for (int id1 : rupIndexes1)
			if (rupIndexes2.contains(id1))
				bothRupIndexes.add(id1);
		
		Map<String, List<ClusterRupture>> uniquePaths = new HashMap<>();
		
		for (int rupIndex : bothRupIndexes) {
			ClusterRupture rup = cRups.get(rupIndex);
			
			RuptureTreeNavigator nav = rup.getTreeNavigator();
			
			FaultSubsectionCluster cluster1 = nav.locateCluster(sect1);
			FaultSubsectionCluster cluster2 = nav.locateCluster(sect2);
			
			List<FaultSubsectionCluster> path = getPathLinking(cluster1, cluster2, nav);
			
			String pathStr = null;
			for (FaultSubsectionCluster cluster : path) {
				if (pathStr == null)
					pathStr = "";
				else
					pathStr += " -> ";
				pathStr += cluster.parentSectionName;
			}
			
			List<ClusterRupture> pathRups = uniquePaths.get(pathStr);
			if (pathRups == null) {
				pathRups = new ArrayList<>();
				uniquePaths.put(pathStr, pathRups);
			}
			pathRups.add(rup);
		}
		
		System.out.println("Found "+uniquePaths.size()+" unique paths");
		
		for (String path : uniquePaths.keySet()) {
			List<ClusterRupture> rups = uniquePaths.get(path);
			System.out.println(path+"\t("+rups.size()+" ruptures)");
			for (JumpProbabilityCalc segModel : segModels) {
				double maxRupProb = 0d;
				double maxWorstJumpProb = 0d;
				for (ClusterRupture rup : rups) {
					maxRupProb = Math.max(maxRupProb, segModel.calcRuptureProb(rup, false));
					double worstJumpProb = 1d;
					for (Jump jump : rup.getJumpsIterable())
						worstJumpProb = Math.min(worstJumpProb, segModel.calcJumpProbability(rup, jump, false));
					maxWorstJumpProb = Math.max(maxWorstJumpProb, worstJumpProb);
				}
				System.out.println("\t"+segModel.getName()+":\trup="+(float)maxRupProb+"\tjump="+(float)maxWorstJumpProb);
			}
		}
		
//		linkingPathSearch(null, null, null, APPLY_TO_ALL_JUMPS_FROM_LOC)
	}
	
	public static void main(String[] args) throws IOException {
		List<WasatchSegLocation> datas = load();
		
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		List<Location> locs = new ArrayList<>();
		for (WasatchSegLocation data : datas) {
			latTrack.addValue(data.loc.getLatitude());
			lonTrack.addValue(data.loc.getLongitude());
			locs.add(data.loc);
		}
		
		Region region = new Region(new Location(latTrack.getMin()-4d, lonTrack.getMin()-4d),
				new Location(latTrack.getMax()+4d, lonTrack.getMax()+4d));
		
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2022_09_28-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR/"
				+ "results_NSHM23_v2_CoulombRupSet_branch_averaged_gridded.zip"));
		
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		Map<WasatchSegLocation, Set<IDPairing>> mappings = mapToParentSects(datas, rupSet, true);
		
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		
		List<PlausibilityFilter> filters = new ArrayList<>();
		filters.add(new PlausibilityFilter() {
			
			@Override
			public String getName() {
				return "Wasatch Only";
			}
			
			@Override
			public String getShortName() {
				return getName();
			}
			
			@Override
			public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
				for (FaultSubsectionCluster cluster : rupture.getClustersIterable())
					for (FaultSection sect : cluster.subSects)
						if (!isWasatchSection(sect))
							return PlausibilityResult.FAIL_HARD_STOP;
				return PlausibilityResult.PASS;
			}
		});
		BinaryJumpProbabilityCalc fullExclusion = buildFullExclusion(rupSet, null);
		filters.add(new PlausibilityFilter() {
			
			@Override
			public String getName() {
				return "Wasatch Seg";
			}
			
			@Override
			public String getShortName() {
				return getName();
			}
			
			@Override
			public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
				if (fullExclusion.isRupAllowed(rupture, verbose))
					return PlausibilityResult.PASS;
				return PlausibilityResult.FAIL_HARD_STOP;
			}
		});

//		ClusterConnectionStrategy connStrat = new DistCutoffClosestSectClusterConnectionStrategy(
//				subSects,distAzCalc, 5d);
		ClusterConnectionStrategy connStrat = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, 5d,
				PlausibleClusterConnectionStrategy.JUMP_SELECTOR_DEFAULT, List.of(filters.get(0)));
		
		int threads = FaultSysTools.defaultNumThreads();
		System.out.println("Caching connections");
		connStrat.checkBuildThreaded(threads);
		PlausibilityConfiguration plausConfig = new PlausibilityConfiguration(
				filters, 0, connStrat, distAzCalc);
		
		System.out.println("Building ruptures");
		ClusterRuptureBuilder builder = new ClusterRuptureBuilder(plausConfig);
		List<ClusterRupture> rups = builder.build(
				new ExhaustiveBilateralRuptureGrowingStrategy(SecondaryVariations.ALL, false), threads);
		Map<FaultSection, ClusterRupture> rupLargestSect = new HashMap<>();
		for (ClusterRupture rup : rups) {
			for (FaultSubsectionCluster cluster : rup.getClustersIterable()) {
				for (FaultSection sect : cluster.subSects) {
					ClusterRupture largest = rupLargestSect.get(sect);
					if (largest == null || largest.getTotalNumSects() < rup.getTotalNumSects())
						rupLargestSect.put(sect, rup);
				}
			}
		}
		
		// pass through a hashset to remove duplicates
		List<ClusterRupture> largestRups = new ArrayList<>(new HashSet<>(rupLargestSect.values()));
		largestRups.sort(new Comparator<ClusterRupture>() {

			@Override
			public int compare(ClusterRupture rup1, ClusterRupture rup2) {
				return Double.compare(aveLat(rup1), aveLat(rup2));
			}
			
			private double aveLat(ClusterRupture rup) {
				double avg = 0d;
				int num = 0;
				for (FaultSubsectionCluster cluster : rup.clusters) {
					for (FaultSection sect : cluster.subSects) {
						num++;
						avg += 0.5*(sect.getFaultTrace().first().getLatitude()
								+ sect.getFaultTrace().last().getLatitude());
					}
				}
				return avg/(double)num;
			}
		});
		for (ClusterRupture rup : largestRups)
			System.out.println(rup);
//		FaultSystemRupSet rupSet = ClusterRuptureBuilder.buildClusterRupSet(
//				NSHM23_ScalingRelationships.AVERAGE, subSects, plausConfig, largestRups);
//		rupSet.write(new File("/tmp/wasatch_seg_rup_set.zip"));
		
		List<ConnectivityCluster> clusters = ConnectivityCluster.build(ClusterRuptureBuilder.buildClusterRupSet(
				NSHM23_ScalingRelationships.AVERAGE, subSects, plausConfig, rups));
		System.out.println("Found "+clusters.size()+" clusters");
		
		clusters.sort(new Comparator<ConnectivityCluster>() {

			@Override
			public int compare(ConnectivityCluster arg0, ConnectivityCluster arg1) {
				return Double.compare(aveLat(arg0), aveLat(arg1));
			}
			
			private double aveLat(ConnectivityCluster cluster) {
				double avg = 0d;
				int num = 0;
				
				for (int s : cluster.getSectIDs()) {
					FaultSection sect = subSects.get(s);
					num++;
					avg += 0.5*(sect.getFaultTrace().first().getLatitude()
							+ sect.getFaultTrace().last().getLatitude());
				}
				return avg/(double)num;
			}
			
		});
		
		System.out.println("Found "+largestRups.size()+" largest segmented rups");
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(subSects, region);
		mapMaker.setWriteGeoJSON(true);
		
		mapMaker.plotScatters(locs, Color.GRAY);
		List<Color> sectColors = new ArrayList<>();
		for (int s=0; s<subSects.size(); s++) {
			sectColors.add(Color.LIGHT_GRAY);
//			FaultSection subSect = subSects.get(s);
//			if (isWasatchSection(subSect)) {
//				Color color = null;
//				int parent = subSect.getParentSectionId();
//				for (WasatchSegLocation loc : subSectMappings.keySet()) {
//					IDPairing pair = subSectMappings.get(loc);
//					if (pair.getID1() == s) {
//						color = Color.RED.darker();
//						break;
//					} else if (pair.getID2() == s) {
//						color = Color.GREEN.darker();
//						break;
//					}
//				}
//				if (color == null)
//					color = Color.BLACK;
//				sectColors.add(color);
//			} else {
//				sectColors.add(null);
//			}
		}
		mapMaker.plotSectColors(sectColors);
		int maxColors = 5;
		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, Double.min(maxColors, clusters.size()-1));
		for (int i=0; i<clusters.size(); i++) {
			int colorIndex = i % maxColors;
			Color color = cpt.getColor((float)colorIndex);
			for (int sectID : clusters.get(i).getSectIDs())
				sectColors.set(sectID, color);
		}
		
		// also plot prohibited jumps
		HashSet<Integer> wasatchRupIndexes = new HashSet<>();
		HashSet<Integer> wasatchSectIndexes = new HashSet<>();
		for (FaultSection sect : subSects) {
			if (isWasatchSection(sect)) {
				wasatchSectIndexes.add(sect.getSectionId());
				wasatchRupIndexes.addAll(rupSet.getRupturesForSection(sect.getSectionId()));
			}
		}
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		
		HashSet<Jump> allowedJumps = new HashSet<>();
		HashSet<Jump> disallowedJumps = new HashSet<>();
		HashSet<Jump> otherJumps = new HashSet<>();
		Set<IDPairing> allMappings = allPairings(mappings);
		
		for (int rupIndex : wasatchRupIndexes) {
			ClusterRupture cRup = cRups.get(rupIndex);
			
			for (Jump jump : cRup.getJumpsIterable()) {
				if (wasatchSectIndexes.contains(jump.fromSection.getSectionId()) || wasatchSectIndexes.contains(jump.toSection.getSectionId())) {
					// it's a jump to/from a wasatch section
					IDPairing parents = new IDPairing(jump.fromCluster.parentSectionID, jump.toCluster.parentSectionID);
					boolean disallowed = allMappings.contains(parents) || allMappings.contains(parents.getReversed());
					if (disallowed)
						disallowedJumps.add(jump);
					else
						allowedJumps.add(jump);
				} else {
					otherJumps.add(jump);
				}
			}
		}
		mapMaker.plotJumps(removeDuplicates(disallowedJumps), alpha(Color.RED.darker(), 127), "Affected Jumps");
		mapMaker.plotJumps(removeDuplicates(allowedJumps), alpha(Color.GREEN.darker(), 127), "Unaffected Jumps");
		mapMaker.plotJumps(removeDuplicates(otherJumps), alpha(Color.GRAY, 127), "Other Jumps");
		
//		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, Double.min(maxColors, largestRups.size()-1));
//		for (int i=0; i<largestRups.size(); i++) {
//			int colorIndex = i % maxColors;
//			Color color = cpt.getColor((float)colorIndex);
//			for (FaultSubsectionCluster cluster : largestRups.get(i).clusters) {
//				for (FaultSection sect : cluster.subSects) {
//					if (sectColors.get(sect.getSectionId()) != null)
//						System.err.println("WARNING: multiple colors for "+sect.getSectionName());
//					sectColors.set(sect.getSectionId(), color);
//				}
//			}
//		}
		for (int s=0; s<subSects.size(); s++) {
			if (sectColors.get(s) == null && isWasatchSection(subSects.get(s))) {
				System.err.println("WARNING: missed subsection: "+subSects.get(s).getName());
				sectColors.set(s, Color.BLACK);
			}
		}
		
		mapMaker.plot(new File("/tmp"), "wasatch_seg_data", "Wasatch Segmentation Locations");
		
//		Map<Integer, List<FaultSection>> idToSects = subSects.stream().collect(Collectors.groupingBy(S -> S.getParentSectionId()));
////		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
//		List<DistDependentJumpProbabilityCalc> compSegModels = new ArrayList<>();
//		compSegModels.add(Shaw07JumpDistProb.forHorzOffset(1d, 2d, 1d));
//		compSegModels.add(Shaw07JumpDistProb.forHorzOffset(1d, 2d, 2d));
//		compSegModels.add(Shaw07JumpDistProb.forHorzOffset(1d, 3d, 2d));
//		compSegModels.add(Shaw07JumpDistProb.forHorzOffset(1d, 4d, 2d));
//		compSegModels.add(Shaw07JumpDistProb.forHorzOffset(1d, 4d, 3d));
//		for (WasatchSegLocation data : mappings.keySet()) {
//			IDPairing pair = mappings.get(data);
//			double minDist = Double.POSITIVE_INFINITY;
//			for (FaultSection sect1 : idToSects.get(pair.getID1()))
//				for (FaultSection sect2 : idToSects.get(pair.getID2()))
//					minDist = Math.min(minDist, distAzCalc.getDistance(sect1, sect2));
//			System.out.println(data.name+": "+minDist+" km");
//			for (DistDependentJumpProbabilityCalc segModel : compSegModels)
//				System.out.println("\t"+segModel.getName()+":\tP="+(float)segModel.calcJumpProbability(minDist));
//		}
		
		// print paths from provo to weber
		List<JumpProbabilityCalc> segModels = new ArrayList<>();
		List<JumpProbabilityCalc> distModels = new ArrayList<>();
		for (NSHM23_SegmentationModels segModel : NSHM23_SegmentationModels.values()) {
			if (segModel.getNodeWeight(null) == 0d && segModel != NSHM23_SegmentationModels.AVERAGE)
				continue;
			final JumpProbabilityCalc model = segModel.getModel(rupSet, null);
			JumpProbabilityCalc namedModel = new JumpProbabilityCalc() {
				
				@Override
				public String getName() {
					return segModel.getShortName();
				}
				
				@Override
				public boolean isDirectional(boolean splayed) {
					return model == null ? false : model.isDirectional(splayed);
				}
				
				@Override
				public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
					if (model == null)
						return 1d;
					return model.calcJumpProbability(fullRupture, jump, verbose);
				}
			};
			segModels.add(namedModel);
			if (segModel.getShawR0() > 0d) {
				double shawR0 = segModel.getShawR0();
				double shawShift = segModel.getShawShift();
				JumpProbabilityCalc shawModel = shawShift > 0d ?
						Shaw07JumpDistProb.forHorzOffset(1d, shawR0, shawShift) : new Shaw07JumpDistProb(1d, shawR0);
				boolean duplicate = false;
				for (JumpProbabilityCalc o : distModels)
					if (o.getName().equals(shawModel.getName()))
						duplicate = true;
				if (!duplicate)
					distModels.add(shawModel);
			}
		}
		segModels.addAll(distModels);
		printSegPaths(rupSet, segModels, 5295, 5316);
	}
	
	private static Color alpha(Color c, int alpha) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}
	
	private static HashSet<Jump> removeDuplicates(HashSet<Jump> jumps) {
		HashSet<Jump> ret = new HashSet<>();
		for (Jump jump : jumps)
			if (!ret.contains(jump) && !ret.contains(jump.reverse()))
				ret.add(jump);
		return ret;
	}

}
