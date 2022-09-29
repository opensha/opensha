package org.opensha.sha.earthquake.rupForecastImpl.nshm23.data;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.IDPairing;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ConnectionPointsRuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveBilateralRuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.PlausibleClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveBilateralRuptureGrowingStrategy.SecondaryVariations;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.ConnectivityCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public class NSHM23_WasatchSegmentationData {
	
	// maximum distance that the closest trace must be to the segmentation location
	private static final double MAX_MAP_DIST_1 = 5d;
	// maximum distance that the 2nd closest trace must be to the segmentation location
	private static final double MAX_MAP_DIST_2 = 10d;
	// section names must contain this in order to be mapped (must be lower case)
	private static final String MAP_SECT_NAME_REQUIREMENT = "wasatch";
	
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
	
	private static Map<WasatchSegLocation, IDPairing> mapToParentSects(List<WasatchSegLocation> datas,
			List<? extends FaultSection> subSects, boolean verbose) {
		List<FaultSection> wasatchSects = new ArrayList<>();
		for (FaultSection subSect : subSects)
			if (isWasatchSection(subSect))
				wasatchSects.add(subSect);
		if (verbose) System.out.println("Found "+wasatchSects.size()+" candidate Wasatch sections");
		if (wasatchSects.isEmpty())
			return null;
		
		Map<WasatchSegLocation, IDPairing> ret = new HashMap<>();
		
		Map<Integer, String> parentNames = new HashMap<>();
		List<LocationList> discrTraces = new ArrayList<>();
		for (FaultSection sect : wasatchSects) {
			discrTraces.add(sect.getFaultSurface(1d).getEvenlyDiscritizedUpperEdge());
			parentNames.put(sect.getParentSectionId(), sect.getParentSectionName());
		}
		
		if (verbose) System.out.println("Mapping "+datas.size()+" Wasatch segmentation locations");
		for (WasatchSegLocation data : datas) {
			if (verbose) System.out.println("Mapping "+data.id+". "+data.name);
			Map<Integer, Double> parentDists = new HashMap<>();
			for (int i=0; i<wasatchSects.size(); i++) {
				FaultSection subSect = wasatchSects.get(i);
				LocationList trace = discrTraces.get(i);
				double minDist = Double.POSITIVE_INFINITY;
				for (Location loc : trace)
					minDist = Math.min(minDist, LocationUtils.horzDistanceFast(loc, data.loc));
				if ((float)minDist > (float)MAX_MAP_DIST_1 && (float)minDist > (float)MAX_MAP_DIST_2)
					continue;
				Integer parentID = subSect.getParentSectionId();
				if (parentDists.containsKey(parentID))
					parentDists.put(parentID, Double.min(minDist, parentDists.get(parentID)));
				else
					parentDists.put(parentID, minDist);
			}
			if (verbose) System.out.println("\tFound "+parentDists.size()+" candidate sections");
			if (parentDists.size() < 2)
				// need 2 sections
				continue;
			List<Integer> sortedParentIDs = ComparablePairing.getSortedData(parentDists);
			int parentID1 = sortedParentIDs.get(0);
			double dist1 = parentDists.get(parentID1);
			String name1 = parentNames.get(parentID1);
			int parentID2 = sortedParentIDs.get(1);
			double dist2 = parentDists.get(parentID2);
			String name2 = parentNames.get(parentID2);
			if (dist1 < MAX_MAP_DIST_1 && dist2 < MAX_MAP_DIST_2) {
				// it's a match
				if (verbose) System.out.println("\tMatch found between: "
						+parentID1+". "+name1+" ("+(float)dist1+" km) and "+parentID2+". "+name2+" ("+(float)dist2+" km)");
				ret.put(data, new IDPairing(parentID1, parentID2));
			} else if (verbose) {
				if (verbose) System.out.println("\tNo match found. Closest 2 were: "
						+parentID1+". "+name1+" ("+(float)dist1+" km) and "+parentID2+". "+name2+" ("+(float)dist2+" km)");
			}
		}
		
		return ret;
	}
	
	public static HardcodedJumpProb build(List<? extends FaultSection> subSects, double passthroughRate) {
		return build(subSects, passthroughRate);
	}
	
	public static HardcodedJumpProb build(List<? extends FaultSection> subSects, double passthroughRate,
			JumpProbabilityCalc fallback) {
		List<WasatchSegLocation> datas;
		try {
			datas = load();
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		Map<WasatchSegLocation, IDPairing> mappings = mapToParentSects(datas, subSects, false);
		if (mappings == null)
			return null;
		
		Map<IDPairing, Double> idsToProbs = new HashMap<>();
		for (IDPairing pair : mappings.values())
			idsToProbs.put(pair, passthroughRate);
		
		String name = "Wasatch, P="+(float)passthroughRate;
		if (fallback != null)
			name += ", "+fallback.getName();
		
		return new HardcodedJumpProb(name, idsToProbs, true, fallback);
	}
	
	public static BinaryJumpProbabilityCalc buildFullExclusion(List<? extends FaultSection> subSects,
			BinaryJumpProbabilityCalc fallback) {
		List<WasatchSegLocation> datas;
		try {
			datas = load();
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		Map<WasatchSegLocation, IDPairing> mappings = mapToParentSects(datas, subSects, false);
		if (mappings == null || mappings.isEmpty())
			return null;
		
		String name = "Wastach Exclude";
		if (fallback != null)
			name += ", "+fallback.getName();
		
		return new HardcodedBinaryJumpProb(name, true, new HashSet<>(mappings.values()), true);
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
		
		List<? extends FaultSection> subSects = NSHM23_DeformationModels.GEOLOGIC.build(NSHM23_FaultModels.NSHM23_v2);
		
		Map<WasatchSegLocation, IDPairing> mappings = mapToParentSects(datas, subSects, true);
		
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
		BinaryJumpProbabilityCalc fullExclusion = buildFullExclusion(subSects, null);
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
			sectColors.add(null);
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
	}

}
