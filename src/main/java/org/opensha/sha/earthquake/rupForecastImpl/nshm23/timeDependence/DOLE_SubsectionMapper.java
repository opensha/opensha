package org.opensha.sha.earthquake.rupForecastImpl.nshm23.timeDependence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.GeoJSON_Type;
import org.opensha.commons.geo.json.Geometry.LineString;
import org.opensha.commons.geo.json.Geometry.MultiLineString;
import org.opensha.commons.geo.json.Geometry.Point;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public class DOLE_SubsectionMapper {
	
	public static final String REL_PATH = "/data/erf/nshm23/date_of_last_event/2024_11_04_v1";
	public static final String PALEO_DOLE_PATH = REL_PATH+"/PaleoDOLE_v1.geojson";
	public static final String HIST_PATH = REL_PATH+"/HistDOLE_v1.geojson";
	
	private static final String YEAR_PROP_NAME = "CalYear";
	
	private static final FeatureCollection loadGeoJSON(String path) throws IOException {
		BufferedReader bRead = new BufferedReader(
				new InputStreamReader(DOLE_SubsectionMapper.class.getResourceAsStream(path)));
		return FeatureCollection.read(bRead);
	}
	
	public enum MappingType {
		HISTORICAL,
		DOLE_DIRECT,
		DOLE_INDIRECT
	}
	
	public static abstract class AbstractDOLE_Data {
		public final int year;
		public final long epochMillis;
		public final int faultID;
		public final String faultName;
		public final Feature feature;
		private List<FaultSection> mappedSubSects;
		private List<MappingType> mappedTypes;
		
		public AbstractDOLE_Data(Feature feature) {
			this.feature = feature;
			year = feature.properties.getInt(YEAR_PROP_NAME, Integer.MIN_VALUE);
			Preconditions.checkState(year > Integer.MIN_VALUE,
					"%s property is missing or malformatted: %s", YEAR_PROP_NAME, feature.properties.get(YEAR_PROP_NAME));
			epochMillis = new GregorianCalendar(year, 0, 1).getTimeInMillis();
			faultID = feature.properties.getInt("FaultID", -1);
			Preconditions.checkState(faultID >= 0,
					"FaultID is missing or malformatted: %s", feature.properties.get("FaultID"));
			faultName = feature.properties.getString("FaultName");
			mappedSubSects = new ArrayList<>();
			mappedTypes = new ArrayList<>();
		}
		
		public void addMapedSection(FaultSection sect, MappingType type) {
			mappedSubSects.add(sect);
			mappedTypes.add(type);
		}
		
		public List<FaultSection> getMappedSubSects() {
			return mappedSubSects;
		}
		
		public List<MappingType> getMappedTypes() {
			return mappedTypes;
		}
	}
	
	public static class PaleoDOLE_Data extends AbstractDOLE_Data {
		public final Location location;
		public final String siteName;
		public final String reference;
		
		public PaleoDOLE_Data(Feature feature) {
			super(feature);
			Preconditions.checkNotNull(feature, "Feature is null?");
			Preconditions.checkNotNull(feature.geometry, "Feature geometry is null?");
			Preconditions.checkState(feature.geometry.type == GeoJSON_Type.Point,
					"Geometry is of unexpected type: %s", feature.geometry.type);
			location = ((Point)feature.geometry).point;
			
			
			// optional fields
			siteName = feature.properties.getString("SiteName");
			reference = feature.properties.getString("Reference");
		}
	}
	
	public static List<PaleoDOLE_Data> loadPaleoDOLE() throws IOException {
		FeatureCollection features = loadGeoJSON(PALEO_DOLE_PATH);
		
		List<PaleoDOLE_Data> ret = new ArrayList<>(features.features.size());
		for (Feature feature : features) {
			if (feature.geometry == null) {
				System.err.println("WARNING: skipping feature with null geometry! "+feature.toCompactJSON());
				continue;
			}
			ret.add(new PaleoDOLE_Data(feature));
		}
		return ret;
	}
	
	public enum DOLE_MappingAlgorithm {
		FULL_PARENT("Full Parent DOLE Mapping"),
		CLOSEST_SECT("Closest Section DOLE Mapping"),
		NEIGHBORING_SECTS("Neighboring Section DOLE Mapping");
		
		private String name;

		private DOLE_MappingAlgorithm(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}
	
	private static final Comparator<PaleoDOLE_Data> DOLE_DATE_COMPARATOR = new Comparator<DOLE_SubsectionMapper.PaleoDOLE_Data>() {
		
		@Override
		public int compare(PaleoDOLE_Data o1, PaleoDOLE_Data o2) {
			return Integer.compare(o1.year, o2.year);
		}
	};
	
	private static final double MAX_DIST_TOL = 20d;
	
	private static final Comparator<HistoricalRupture> HIST_RUP_DATE_COMPARATOR = new Comparator<DOLE_SubsectionMapper.HistoricalRupture>() {
		
		@Override
		public int compare(HistoricalRupture o1, HistoricalRupture o2) {
			return Integer.compare(o1.year, o2.year);
		}
	};
	
	public static List<HistoricalRupture> loadHistRups() throws IOException {
		FeatureCollection features = loadGeoJSON(HIST_PATH);
		
		List<HistoricalRupture> ret = new ArrayList<>(features.features.size());
		for (Feature feature : features) {
			if (feature.geometry == null) {
				System.err.println("WARNING: skipping feature with null geometry! "+feature.toCompactJSON());
				continue;
			}
			ret.add(new HistoricalRupture(feature));
		}
		return ret;
	}
	
	/**
	 * This loads DOLE data and maps to the given subsection list, according to the given {@link DOLE_MappingAlgorithm}.
	 * <br>
	 * The date of last event will be overridden in all mapped fault sections, and the mappedSubSects field will be
	 * polulated in the {@link PaleoDOLE_Data} object.
	 * 
	 * @param subSects
	 * @param doleData
	 * @param mappingType
	 * @throws IOException 
	 */
	public static void mapDOLE(List<? extends FaultSection> subSects, DOLE_MappingAlgorithm mappingType, boolean verbose) throws IOException {
		mapDOLE(subSects, loadHistRups(), loadPaleoDOLE(), mappingType, verbose);
	}
	
	/**
	 * This maps the given DOLE data to the given subsection list, according to the given {@link DOLE_MappingAlgorithm}.
	 * <br>
	 * The date of last event will be overridden in all mapped fault sections, and the mappedSubSects field will be
	 * polulated in the {@link PaleoDOLE_Data} object.
	 * 
	 * @param subSects
	 * @param doleData
	 * @param mappingType
	 */
	public static void mapDOLE(List<? extends FaultSection> subSects, List<HistoricalRupture> histRups,
			List<PaleoDOLE_Data> doleData, DOLE_MappingAlgorithm mappingType, boolean verbose) {
		// group subsects by parent
		Map<Integer, List<FaultSection>> parentSectsMap = subSects.stream().collect(
				Collectors.groupingBy(S -> S.getParentSectionId()));
		
		// group Hist Rups data by parent
		Map<Integer, List<HistoricalRupture>> rupParentsMap = histRups.stream().collect(
				Collectors.groupingBy(D -> D.faultID));
		
		// group DOLE data by parent
		Map<Integer, List<PaleoDOLE_Data>> doleParentsMap = doleData.stream().collect(
				Collectors.groupingBy(D -> D.faultID));
		
		HashSet<Integer> allParents = new HashSet<>();
		allParents.addAll(rupParentsMap.keySet());
		allParents.addAll(doleParentsMap.keySet());
		
		List<AbstractDOLE_Data> allData = new ArrayList<>();
		allData.addAll(doleData);
		allData.addAll(histRups);
		Map<Integer, String> parentNames = new HashMap<>();
		for (AbstractDOLE_Data data : allData)
			if (!parentNames.containsKey(data.faultID))
				parentNames.put(data.faultID, data.faultName);
		
		for (int parentID : allParents) {
			List<FaultSection> parentSects = parentSectsMap.get(parentID);
			if (parentSects == null) {
				if (verbose) System.out.flush();
				System.err.println("WARNING: no section found with faultID="+parentID+" and name='"+parentNames.get(parentID)+"', skipping DOLE mapping");
				if (verbose) {
					System.err.println();
					System.err.flush();
				}
				continue;
			}
			
			if (verbose) System.out.println("--- "+parentID+". "+parentSects.get(0).getParentSectionName()+" ---");
			
			List<PaleoDOLE_Data> parentDOLE = doleParentsMap.get(parentID);
			List<HistoricalRupture> parentRups = rupParentsMap.get(parentID);
			
			// trace locs for distance calculations
			// add all actual trace locs, plus an evenly spaced version
			List<LocationList> sectTraceLocs = new ArrayList<>();
			for (FaultSection sect : parentSects) {
				LocationList locs = new LocationList();
				locs.addAll(sect.getFaultTrace());
				locs.addAll(FaultUtils.resampleTrace(sect.getFaultTrace(), 20));
				sectTraceLocs.add(locs);
			}
			
			AbstractDOLE_Data[] mappings = new AbstractDOLE_Data[parentSects.size()];
			MappingType[] mappingTypes = new MappingType[parentSects.size()];
			double[] mappedDists = new double[parentSects.size()];
			
			if (parentRups != null) {
				// do historical rups first
				if (parentRups.size() > 1)
					// sort by year (increasing) so that newer dates override earlier ones
					Collections.sort(parentRups, HIST_RUP_DATE_COMPARATOR);
				if (verbose) System.out.println("Mapping "+parentRups.size()+" HISTORICAL RUPTURE"
						+(parentRups.size() == 1 ? "" : "s"));
				for (int i=0; i<parentRups.size(); i++) {
					HistoricalRupture rup = parentRups.get(i);
					if (verbose) System.out.println("\t"+rup.year+", "+rup.eventID);
					double[] startSectDists = new double[parentSects.size()];
					int startSect = getClosestSect(parentSects, sectTraceLocs, rup.trace.first(), startSectDists, rup.year, rup.eventID);
					double[] endSectDists = new double[parentSects.size()];
					int endSect = getClosestSect(parentSects, sectTraceLocs, rup.trace.last(), endSectDists, rup.year, rup.eventID);
					if (startSect > endSect) {
						// reverse
						int tmp = startSect;
						startSect = endSect;
						endSect = tmp;
						double[] tmpDists = startSectDists;
						startSectDists = endSectDists;
						endSectDists = tmpDists;
					}
					if (startSect < parentSects.size()-1 && (float)startSectDists[startSect] == (float)startSectDists[startSect+1])
						// the starting point is exactly between two sections, start it in the middle (i.e., include the 2nd but not the 1st)
						startSect++;
					if (endSect > 0 && (float)endSectDists[endSect] == (float)endSectDists[endSect-1])
						// the end point is exactly between two sections, start it in the middle (i.e., include the 1st but not the 2nd)
						endSect--;
					
					if (verbose)
						System.out.println("\t\tFrom sect "+startSect+" ("+(float)startSectDists[startSect]+" km) to "
								+endSect+" ("+(float)endSectDists[endSect]+" km)");
					
					for (int s=startSect; s<=endSect; s++) {
						mappings[s] = rup;
						mappingTypes[s] = MappingType.HISTORICAL;
					}
				}
				
			}
			
			if (parentDOLE != null) {
				if (parentDOLE.size() > 1)
					// sort by year (increasing) so that newer dates override earlier ones
					Collections.sort(parentDOLE, DOLE_DATE_COMPARATOR);
				
				if (verbose) System.out.println("Mapping "+parentDOLE.size()+" DOLE "
						+(parentDOLE.size() == 1 ? "datum" : "data"));
				
				int[] closestSects = new int[parentDOLE.size()];
				for (int i=0; i<parentDOLE.size(); i++) {
					PaleoDOLE_Data dole = parentDOLE.get(i);
					if (verbose) System.out.println("\t"+dole.year+", "+dole.siteName+", "+dole.reference);
					double[] sectDists = new double[parentSects.size()];
					closestSects[i] = getClosestSect(parentSects, sectTraceLocs, dole.location, sectDists, dole.year, dole.siteName);
					
					int directMap = closestSects[i];
					// see if it the closest point is shared with a neighbor, in which case it should directly map to both
					int directMap2 = -1;
					if (directMap > 0 && (float)sectDists[directMap] == (float)sectDists[directMap-1]) {
						// it straddles this section and the section before
						// always make directMap < directMap2
						directMap2 = directMap;
						directMap--;
					} else if (directMap < parentSects.size()-1 && (float)sectDists[directMap] == (float)sectDists[directMap+1]) {
						// it straddles this section and the section after
						directMap2 = directMap+1;
					}
					
					// see if we're already mapped to a historical rupture
					if (mappingTypes[directMap] == MappingType.HISTORICAL) {
						if (directMap2 >= 0 && mappingTypes[directMap2] != MappingType.HISTORICAL) {
							// there's a second mapping we can still use, just swap them
							directMap = directMap2;
							directMap2 = -1;
						} else {
							if (verbose) {
								HistoricalRupture rup = (HistoricalRupture)mappings[directMap];
								System.out.println("\t\tClosest subsection ("+directMap
										+") is already mapped to a historical rupture, skipping: "+rup.year+", "+rup.eventID);
							}
							continue;
						}
					} else if (directMap2 >= 0 && mappingTypes[directMap2] == MappingType.HISTORICAL) {
						// our second mapping overlaps a historical, remove it
						directMap2 = -1;
					}
					
					if (verbose)
						System.out.println("\t\tClosest subsection is "+directMap+": "+(float)sectDists[directMap]+" km");
					if (mappingType == DOLE_MappingAlgorithm.CLOSEST_SECT
							|| mappingType == DOLE_MappingAlgorithm.NEIGHBORING_SECTS || parentSects.size() == 1) {
						mappings[directMap] = dole;
						mappingTypes[directMap] = MappingType.DOLE_DIRECT;
						mappedDists[directMap] = sectDists[directMap];
						if (verbose)
							System.out.println("\t\tMapping closest subsection: "+directMap+": "+(float)sectDists[directMap]+" km");
						if (directMap2 >= 0) {
							mappings[directMap2] = dole;
							mappingTypes[directMap2] = MappingType.DOLE_DIRECT;
							mappedDists[directMap2] = sectDists[directMap];
							if (verbose)
								System.out.println("\t\tAlso mapping equally-close subsection: "+directMap2+": "+(float)sectDists[directMap2]+" km");
						}
						if (parentSects.size() > 1 && mappingType == DOLE_MappingAlgorithm.NEIGHBORING_SECTS && directMap2 < 0) {
							// map neighbors, but only if they were not previously directly mapped and not at the straddle point between
							// two sections (in which case there are already 2 sections mapped)
							int[] testMapIndexes = { directMap-1, directMap+1};
							for (int index : testMapIndexes) {
								if (index >= 0 && index < parentSects.size()) {
									if (mappings[index] == null || mappingTypes[index] == MappingType.DOLE_INDIRECT) {
										// not previously mapped, or previously mapped indirectly
										mappings[index] = dole;
										mappingTypes[index] = MappingType.DOLE_INDIRECT;
										mappedDists[index] = sectDists[index];
										if (verbose)
											System.out.println("\t\tMapping neighboring subsection: "+index+": "+(float)sectDists[index]+" km");
									} else if (verbose) {
										System.out.println("\t\tWon't map "+index+", already directly mapped: "+mappingTypes[index]);
									}
								}
							}
						}
					} else if (mappingType == DOLE_MappingAlgorithm.FULL_PARENT) {
						// don't cross a historical rupture
						int startIndex = directMap;
						for (int s=startIndex; --s>=0;) {
							if (mappingTypes[s] == MappingType.HISTORICAL)
								break;
							startIndex = s;
						}
						int endIndex = directMap2 >= 0 ? directMap2 : directMap;
						for (int s=endIndex+1; s<parentSects.size(); s++) {
							if (mappingTypes[s] == MappingType.HISTORICAL)
								break;
							endIndex = s;
						}
						if (i == 0) {
							// simple case
							if (verbose)
								System.out.println("\t\tMaping full parent");
							for (int s=startIndex; s<=endIndex; s++) {
								mappings[s] = dole;
								mappedDists[s] = sectDists[s];
								mappingTypes[s] = s == directMap || s == directMap2 ? MappingType.DOLE_DIRECT : MappingType.DOLE_INDIRECT;
							}
						} else {
							// first map the closest no matter what
							mappings[directMap] = dole;
							mappedDists[directMap] = sectDists[directMap];
							mappingTypes[directMap] = MappingType.DOLE_DIRECT;
							if (verbose)
								System.out.println("\t\tMapping closest subsection: "+directMap+": "+(float)sectDists[directMap]+" km");
							if (directMap2 >= 0) {
								mappings[directMap2] = dole;
								mappingTypes[directMap2] = MappingType.DOLE_DIRECT;
								mappedDists[directMap2] = sectDists[directMap];
								if (verbose)
									System.out.println("\t\tAlso mapping equally-close subsection: "+directMap2+": "+(float)sectDists[directMap2]+" km");
							}
							// split up the parent
							// assign everything that is closer to this location than any other
							for (int s=startIndex; s<=endIndex; s++) {
								double myDist = sectDists[s];
								if (s != directMap && s != directMap2 && myDist < mappedDists[s]) {
									if (verbose)
										System.out.println("\t\tAlso mapping "+s+" as it's closer to this ("+dole.year
												+", "+(float)myDist+" km) than the prior ("+mappings[s].year+", "+(float)mappedDists[s]+" km)");
									mappings[s] = dole;
									mappedDists[s] = sectDists[s];
									mappingTypes[s] = MappingType.DOLE_INDIRECT;
								}
							}
						}
					} else {
						throw new IllegalStateException("Unexpected mapping type: "+mappingType);
					}
				}
			}
			if (verbose) System.out.println("Final mappings:");
			for (int s=0; s<mappings.length; s++) {
				if (verbose) {
					if (mappings[s] == null)
						System.out.println("\t"+s+". (none)");
					else if (mappings[s] instanceof PaleoDOLE_Data)
						System.out.println("\t"+s+". "+mappings[s].year+", "+mappingTypes[s]+", "+((PaleoDOLE_Data)mappings[s]).siteName+", "+(float)mappedDists[s]+" km");
					else
						System.out.println("\t"+s+". "+mappings[s].year+", "+mappingTypes[s]+", "+((HistoricalRupture)mappings[s]).eventID);
				}
				if (mappings[s] != null) {
					FaultSection sect = parentSects.get(s);
					sect.setDateOfLastEvent(mappings[s].epochMillis);
					mappings[s].addMapedSection(sect, mappingTypes[s]);
				}
			}
			if (verbose) System.out.println("----------------------------------");
		}
	}
	
	private static int getClosestSect(List<FaultSection> sects, List<LocationList> sectTraces,
			Location loc, double[] distsToFillIn, int year, String id) {
		Preconditions.checkState(sects.size() == sectTraces.size());
		double minDist = Double.POSITIVE_INFINITY;
		int closestIndex = -1;
		for (int s=0; s<sects.size(); s++) {
			double sectDist = Double.POSITIVE_INFINITY;
			for (Location testLoc : sectTraces.get(s))
				sectDist = Math.min(sectDist, LocationUtils.horzDistanceFast(loc, testLoc));
			if (sectDist < minDist) {
				minDist = sectDist;
				closestIndex = s;
			}
			if (distsToFillIn != null)
				distsToFillIn[s] = sectDist;
		}
		Preconditions.checkState(minDist < MAX_DIST_TOL,
				"Closest section on %s. %s was %s km away from DOLE record: %s,  %s",
				sects.get(0).getParentSectionId(), sects.get(0).getParentSectionName(), (float)minDist, year, id);
		return closestIndex;
	}
	
	public static class HistoricalRupture extends AbstractDOLE_Data {
		public final LocationList trace;
		public final String eventID;
		
		public HistoricalRupture(Feature feature) {
			super(feature);
			Preconditions.checkNotNull(feature, "Feature is null?");
			Preconditions.checkNotNull(feature.geometry, "Feature geometry is null?");
			if (feature.geometry.type == GeoJSON_Type.LineString) {
				trace = ((LineString)feature.geometry).line;
			} else if (feature.geometry.type == GeoJSON_Type.MultiLineString) {
				MultiLineString mls = (MultiLineString)feature.geometry;
				Preconditions.checkState(mls.lines.size() == 1, "MultiLineString only supported if it contains a single LineString");
				trace = mls.lines.get(0);
			} else {
				throw new IllegalStateException("Geometry is of unexpected type: "+feature.geometry.type);
			}
			eventID = feature.properties.getString("ComCatID");
		}
	}

	public static void main(String[] args) throws IOException {
		// see if we have any duplicates
//		Map<Integer, List<DOLE_Data>> parents = doleData.stream().collect(
//				Collectors.groupingBy(D -> D.faultID));
//		for (Integer parentID : parents.keySet()) {
//			List<DOLE_Data> parentDOLE = parents.get(parentID);
//			if (parentDOLE.size() > 1) {
//				System.out.println(parentID+" has "+parentDOLE.size()+" DOLE mappings");
//				for (DOLE_Data dole : parentDOLE) {
//					System.out.println("\t"+dole.year+", "+dole.siteName+", "+dole.location);
//				}
//			}
//		}

		List<? extends FaultSection> subSects = NSHM23_DeformationModels.GEOLOGIC.build(NSHM23_FaultModels.WUS_FM_v3);
		
		System.out.println("Loading Paleo DOLE data");
		List<PaleoDOLE_Data> doleData = loadPaleoDOLE();
		System.out.println("Loading Historical Rupture data");
		List<HistoricalRupture> hisRupData = loadHistRups();
		System.out.println("Mapping DOLE data");
		mapDOLE(subSects, hisRupData, doleData, DOLE_MappingAlgorithm.FULL_PARENT, true);
	}
}
