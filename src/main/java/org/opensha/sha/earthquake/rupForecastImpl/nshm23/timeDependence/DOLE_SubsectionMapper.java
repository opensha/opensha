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
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.GeoJSON_Type;
import org.opensha.commons.geo.json.Geometry.LineString;
import org.opensha.commons.geo.json.Geometry.MultiLineString;
import org.opensha.commons.geo.json.Geometry.Point;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.AnalysisRegions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.SeismicityRegions;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

/*
 * This sets the date of last events in a given subSection list based on DOLE historical rupture
 * and Paleo site constraints.
 * 
 * DOLE data specify the faultID of the associated fault and this must correspond the the parentID
 * represented in the given subsection data, but note that some of the fault name strings differ due
 * to slight typos.
 * 
 * For Paleo site data, dates can be mapped to only closest section(PaleoMappingAlgorithm.PALEO_CLOSEST_SECT), 
 * the closest section plus the two adjacent (3 subsections, PaleoMappingAlgorithm.NEIGHBORING_SECTS), or to 
 * all subsections of the parent section (PaleoMappingAlgorithm.FULL_PARENT).
 * 
 * Data mapping rules/priorities:
 * 
 * Historical Ruptures override Paleo constraints, and more recent ruptures also take precedent
 * 
 * If endpoint of rupture overlaps any amount of a subsection (more than ~0.2 km), that subsection 
 * is included (resulting in a length bias described under known issues below)
 * 
 * If a Paleo site is at the boundary between two subsections (same small distance because they have 
 * identical endpoint locations), both subsections are assigned as the CLOSEST (rather than just one).
 * These remain a 2-section (not 3-section) rupture for the PaleoMappingAlgorithm.NEIGHBORING_SECTS case.
 * 
 * FULL_PARENT Paleo mappings will not override historical ruptures.  If there are multiple Paleo
 * site data on the parent section, the date in the nearest Paleo site gets applied to each subsection
 * (rather than applying the most recent date to all subsections; the idea is that sites with older dates
 * constitute direct evidence of nothing more recent has occurred at those locations)
 * 
 * 
 * Known Issues:
 * 
 * 1) The DOLE data only give the event year (not year, month, day, ...).  The epoch 
 * event time is set as the beginning of the year.
 * 
 * 2) It is not possible to algorithmically identify DOLE multi-fault ruptures (multiple parent
 * sections) unless the ComCatID is not null (most are null).  You can look for parent sections
 * that ruptured in the same year, but these could also be separate events.  Having year, month, 
 * day, etc. info would help such associations.
 * 
 * 3) Historical ruptures end up being mapped with an extra subsection length on average (half a 
 * subsection length at each end), so total length is slightly biased high.
 * 
 * 4) It's not clear whether the mapping algorithm has been tested for all the situations it
 * was written for.
 * 
 * 5) The historical ruptures represented in DOLE should be coordinated with those used as
 * inputs to models like UCERF3-ETAS.
 *    
 *  
 */
public class DOLE_SubsectionMapper {
	
//	public static final String REL_PATH = "/data/erf/nshm23/date_of_last_event/2024_11_04_v1";
//	public static final String PALEO_DOLE_PATH = REL_PATH+"/PaleoDOLE_v1.geojson";
//	public static final String HIST_PATH = REL_PATH+"/HistDOLE_v1.geojson";

	// TEST
//	public static final String REL_PATH = "/data/erf/nshm23/date_of_last_event/HatemCEUS_DOLE_Data060525";
//	public static final String PALEO_DOLE_PATH = REL_PATH+"/PaleoDOLE_CEUS_June5.geojson";
//	public static final String HIST_PATH = REL_PATH+"/HistDOLE_CEUS_June10.geojson";
	
	// PREVIOUS
//	public static final String REL_PATH = "/data/erf/nshm23/date_of_last_event/2025_06_12_v1.1";
//	public static final String PALEO_DOLE_PATH = REL_PATH+"/PaleoDOLE_v1.1.geojson";
//	public static final String HIST_PATH = REL_PATH+"/HistDOLE_v1.1.geojson";
	
	// UPDATE
	public static final String REL_PATH = "/data/erf/nshm23/date_of_last_event/2025_07_07";
	public static final String PALEO_DOLE_PATH = REL_PATH+"/PaleoDOLE_v1.2.geojson";
	public static final String HIST_PATH = REL_PATH+"/HistDOLE_v1.1.geojson";

	
	private static final String YEAR_PROP_NAME = "CalYear";
	
	private static final FeatureCollection loadGeoJSON(String path) throws IOException {
		BufferedReader bRead = new BufferedReader(
				new InputStreamReader(DOLE_SubsectionMapper.class.getResourceAsStream(path)));
		return FeatureCollection.read(bRead);
	}
	
	public enum MappingType {
		HISTORICAL_RUP,
		PALEO_CLOSEST_SECT,
		PALEO_NEIGHBORING_SECT
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
			int nshm_hazID = feature.properties.getInt("NSHMhazID",-1); // test to see if we need to override
			if(nshm_hazID>=0)
				faultID = nshm_hazID;
			else
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
//		System.out.println(PALEO_DOLE_PATH);
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
	
	public enum PaleoMappingAlgorithm {
		FULL_PARENT("Full Parent Paleo Mapping"),
		CLOSEST_SECT("Closest Section Paleo Mapping"),
		NEIGHBORING_SECTS("Neighboring Sections Paleo Mapping");
		
		private String name;

		private PaleoMappingAlgorithm(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}
	
	private static final Comparator<PaleoDOLE_Data> PALEO_DATE_COMPARATOR = new Comparator<DOLE_SubsectionMapper.PaleoDOLE_Data>() {
		
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
	 * This loads DOLE data and maps to the given subsection list, according to the given {@link PaleoMappingAlgorithm}.
	 * <br>
	 * The date of last event will be overridden in all mapped fault sections, and the mappedSubSects field will be
	 * polulated in the {@link PaleoDOLE_Data} object.
	 * 
	 * @param subSects
	 * @param doleData
	 * @param mappingType
	 * @throws IOException 
	 */
	public static String mapDOLE(List<? extends FaultSection> subSects, PaleoMappingAlgorithm mappingType, boolean verbose) throws IOException {
		return mapDOLE(subSects, loadHistRups(), loadPaleoDOLE(), mappingType, verbose);
	}
	
	/**
	 * This maps the given DOLE data to the given subsection list, according to the given {@link PaleoMappingAlgorithm}.
	 * <br>
	 * The date of last event will be overridden in all mapped fault sections, and the mappedSubSects field will be
	 * populated in the {@link PaleoDOLE_Data} object.
	 * 
	 * @param subSects
	 * @param histRups
	 * @param paleoData
	 * @param mappingType
	 * @param verbose
	 */
	public static String mapDOLE(List<? extends FaultSection> subSects, List<HistoricalRupture> histRups,
			List<PaleoDOLE_Data> paleoData, PaleoMappingAlgorithm mappingType, boolean verbose) {
		
		if(verbose) System.out.println("\nMapping DOLE data");
		
		// reset dates of last event in case reusing subSects
		for(FaultSection sect:subSects)
			sect.setDateOfLastEvent(Long.MIN_VALUE);  

		// group subsects by parent
		Map<Integer, List<FaultSection>> subsectListForParentID_Map = subSects.stream().collect(
				Collectors.groupingBy(S -> S.getParentSectionId()));
		
		// group Hist Rups data by parent
		Map<Integer, List<HistoricalRupture>> histRupListForParentID_Map = histRups.stream().collect(
				Collectors.groupingBy(D -> D.faultID));
		
		// group DOLE data by parent
		Map<Integer, List<PaleoDOLE_Data>> paleoDataListForParentID_Map = paleoData.stream().collect(
				Collectors.groupingBy(D -> D.faultID));
		
		HashSet<Integer> allDOLE_ParentIDs = new HashSet<>();
		allDOLE_ParentIDs.addAll(histRupListForParentID_Map.keySet());
		allDOLE_ParentIDs.addAll(paleoDataListForParentID_Map.keySet()); // this will filter duplicates
				
//		// this prints out 2 parents that have both paleo and hist data
//		System.out.println(histRupListForParentID_Map.size()+"\t"+paleoDataListForParentID_Map.size()+"\t"+allDOLE_ParentIDs.size());
//		for(Integer id:histRupListForParentID_Map.keySet()) {
//			if(paleoDataListForParentID_Map.keySet().contains(id)) {
//				System.out.println(id);
//				for(HistoricalRupture histRup:histRupListForParentID_Map.get(id))
//					System.out.println("\thist: "+histRup.faultName);
//					for(PaleoDOLE_Data paleo:paleoDataListForParentID_Map.get(id))
//						System.out.println("\tpaleo: "+paleo.faultName);
//			}
//		}
//		System.exit(-1);

		List<AbstractDOLE_Data> allDOLE_DataList = new ArrayList<>();
		allDOLE_DataList.addAll(paleoData);
		allDOLE_DataList.addAll(histRups);
		Map<Integer, String> doleParentNamesMap = new HashMap<>();
		for (AbstractDOLE_Data data : allDOLE_DataList)
			if (!doleParentNamesMap.containsKey(data.faultID))
				doleParentNamesMap.put(data.faultID, data.faultName);
		
		
		// this is the main loop
		String nameProblemString = "";
		for (int parentID : allDOLE_ParentIDs) {
			List<FaultSection> subsectListForParentList = subsectListForParentID_Map.get(parentID);

			// this checks whether subsection list exists for the DOLE fault/parent ID
			if (subsectListForParentList == null) {
				if (verbose) writeWarning("no parent section found with DOLE faultID="+parentID+" and name='"+doleParentNamesMap.get(parentID)+"', skipping DOLE mapping; perhaps a different branch?");
				continue;
			}
			
			String parSectName = subsectListForParentList.get(0).getParentSectionName();
			if (verbose) System.out.println("--- "+parentID+", "+parSectName+" ---");
			
			List<PaleoDOLE_Data> paleoDataList = paleoDataListForParentID_Map.get(parentID);
			List<HistoricalRupture> histRupList = histRupListForParentID_Map.get(parentID);
			
			// check that parent/fault names are the same & write warning if not
			if(paleoDataList != null)
				for(PaleoDOLE_Data data: paleoDataList) {
					if(!data.faultName.equals(parSectName)) {
						if(verbose) writeWarning(data.faultName+" != "+parSectName);
						nameProblemString += "\t"+data.faultName+"\tvs\t"+parSectName+"\n";
					}
				}
			if(histRupList != null)
				for(HistoricalRupture histRup: histRupList) {
					if(!histRup.faultName.equals(parSectName)) {
						if(verbose) writeWarning(histRup.faultName+" != "+parSectName);
						nameProblemString += "\t"+histRup.faultName+"\tvs\t"+parSectName+"\n";
					}
				}

			
			// trace locs for distance calculations
			// add all actual trace locs, plus an evenly spaced version
			List<LocationList> sectTraceLocs = new ArrayList<>();
			for (FaultSection sect : subsectListForParentList) {
				LocationList locs = new LocationList();
				locs.addAll(sect.getFaultTrace()); // this is also needed because the following can cut corners 
				locs.addAll(FaultUtils.resampleTrace(sect.getFaultTrace(), 20));
				sectTraceLocs.add(locs);
			}
			
			// mapping info for each parent subsection
			AbstractDOLE_Data[] mappings = new AbstractDOLE_Data[subsectListForParentList.size()];
			MappingType[] mappingTypes = new MappingType[subsectListForParentList.size()];
			double[] mappedDists = new double[subsectListForParentList.size()];
			
			if (histRupList != null) {
				// do historical rups first
				if (histRupList.size() > 1)
					// sort by year (increasing) so that newer dates override earlier ones
					Collections.sort(histRupList, HIST_RUP_DATE_COMPARATOR);
				if (verbose) System.out.println("Mapping "+histRupList.size()+" HISTORICAL RUPTURE"
						+(histRupList.size() == 1 ? "" : "s"));
				for (int i=0; i<histRupList.size(); i++) {
					HistoricalRupture rup = histRupList.get(i);
					if (verbose) System.out.println("\t"+rup.year+", "+rup.eventID);
					double[] startSectDists = new double[subsectListForParentList.size()];
					int startSect = getClosestSect(subsectListForParentList, sectTraceLocs, rup.trace.first(), startSectDists, rup.year, rup.eventID);
					double[] endSectDists = new double[subsectListForParentList.size()];
					int endSect = getClosestSect(subsectListForParentList, sectTraceLocs, rup.trace.last(), endSectDists, rup.year, rup.eventID);
					if (startSect > endSect) {
						// reverse
						int tmp = startSect;
						startSect = endSect;
						endSect = tmp;
						double[] tmpDists = startSectDists;
						startSectDists = endSectDists;
						endSectDists = tmpDists;
					}
					// Following implies mappings always imply increased rupture length (by 0.5 subsect length on each end on average)
					if (startSect < subsectListForParentList.size()-1 && (float)startSectDists[startSect] == (float)startSectDists[startSect+1])
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
						mappingTypes[s] = MappingType.HISTORICAL_RUP;
					}
				}
				
			}
			
			if (paleoDataList != null) {
				if (paleoDataList.size() > 1)
					// sort by year (increasing) so that newer dates override earlier ones
					Collections.sort(paleoDataList, PALEO_DATE_COMPARATOR);
				
				if (verbose) System.out.println("Mapping "+paleoDataList.size()+" PALEO "
						+(paleoDataList.size() == 1 ? "datum" : "data"));
				
				int[] closestSects = new int[paleoDataList.size()];
				for (int i=0; i<paleoDataList.size(); i++) {
					PaleoDOLE_Data paleo = paleoDataList.get(i);
					if (verbose) System.out.println("\t"+paleo.year+", "+paleo.siteName+", "+paleo.reference);
					double[] sectDists = new double[subsectListForParentList.size()];
					closestSects[i] = getClosestSect(subsectListForParentList, sectTraceLocs, paleo.location, sectDists, paleo.year, paleo.siteName);
					
					int closestSect = closestSects[i];
					// see if it the closest point is shared with a neighbor, in which case it should directly map to both
					int closestSect2 = -1;
					if (closestSect > 0 && (float)sectDists[closestSect] == (float)sectDists[closestSect-1]) {
						// it straddles this section and the section before
						// always make directMap < directMap2
						closestSect2 = closestSect;
						closestSect--;
					} else if (closestSect < subsectListForParentList.size()-1 && (float)sectDists[closestSect] == (float)sectDists[closestSect+1]) {
						// it straddles this section and the section after
						closestSect2 = closestSect+1;
					}
					
					// see if we're already mapped to a historical rupture
					if (mappingTypes[closestSect] == MappingType.HISTORICAL_RUP) {
						if (closestSect2 >= 0 && mappingTypes[closestSect2] != MappingType.HISTORICAL_RUP) {
							// there's a second mapping we can still use, just swap them
							closestSect = closestSect2;
							closestSect2 = -1;
						} else {
							if (verbose) {
								HistoricalRupture rup = (HistoricalRupture)mappings[closestSect];
								System.out.println("\t\tClosest subsection ("+closestSect
										+") is already mapped to a historical rupture: "+rup.year+", "+rup.eventID+"; skipping");
							}
							continue;
						}
					} else if (closestSect2 >= 0 && mappingTypes[closestSect2] == MappingType.HISTORICAL_RUP) {
						// our second mapping overlaps a historical, remove it
						closestSect2 = -1;
					}
					
					// this is redundant with next system write
//					if (verbose)
//						System.out.println("\t\tClosest subsection is "+closestSect+": "+(float)sectDists[closestSect]+" km");
					if (mappingType == PaleoMappingAlgorithm.CLOSEST_SECT
							|| mappingType == PaleoMappingAlgorithm.NEIGHBORING_SECTS || subsectListForParentList.size() == 1) {
						mappings[closestSect] = paleo;
						mappingTypes[closestSect] = MappingType.PALEO_CLOSEST_SECT;
						mappedDists[closestSect] = sectDists[closestSect];
						if (verbose)
							System.out.println("\t\tMapping closest subsection: "+closestSect+": "+(float)sectDists[closestSect]+" km");
						if (closestSect2 >= 0) {
							mappings[closestSect2] = paleo;
							mappingTypes[closestSect2] = MappingType.PALEO_CLOSEST_SECT;
							mappedDists[closestSect2] = sectDists[closestSect];
							if (verbose)
								System.out.println("\t\tAlso mapping equally-close subsection: "+closestSect2+": "+(float)sectDists[closestSect2]+" km");
						}
						if (subsectListForParentList.size() > 1 && mappingType == PaleoMappingAlgorithm.NEIGHBORING_SECTS && closestSect2 < 0) {
							// map neighbors, but only if they were not previously directly mapped and not at the straddle point between
							// two sections (in which case there are already 2 sections mapped)
							int[] testMapIndexes = { closestSect-1, closestSect+1};
							for (int index : testMapIndexes) {
								if (index >= 0 && index < subsectListForParentList.size()) {
									if (mappings[index] == null || mappingTypes[index] == MappingType.PALEO_NEIGHBORING_SECT) {  
										// not previously mapped as hist rup, or previously mapped directly
										mappings[index] = paleo;
										mappingTypes[index] = MappingType.PALEO_NEIGHBORING_SECT;
										mappedDists[index] = sectDists[index];
										if (verbose)
											System.out.println("\t\tMapping neighboring subsection: "+index+": "+(float)sectDists[index]+" km");
									} else if (verbose) {
										System.out.println("\t\tWon't map "+index+", already directly mapped: "+mappingTypes[index]);
									}
								}
							}
						}
					} else if (mappingType == PaleoMappingAlgorithm.FULL_PARENT) {
						// don't cross a historical rupture
						int startIndex = closestSect;
						for (int s=startIndex; --s>=0;) {
							if (mappingTypes[s] == MappingType.HISTORICAL_RUP)
								break;
							startIndex = s;
						}
						int endIndex = closestSect2 >= 0 ? closestSect2 : closestSect;
						for (int s=endIndex+1; s<subsectListForParentList.size(); s++) {
							if (mappingTypes[s] == MappingType.HISTORICAL_RUP)
								break;
							endIndex = s;
						}
						if (i == 0) {
							// simple case
							if (verbose)
								System.out.println("\t\tMaping full parent where no hist rup");
							for (int s=startIndex; s<=endIndex; s++) {
								mappings[s] = paleo;
								mappedDists[s] = sectDists[s];
								mappingTypes[s] = s == closestSect || s == closestSect2 ? MappingType.PALEO_CLOSEST_SECT : MappingType.PALEO_NEIGHBORING_SECT;
							}
						} else {
							// first map the closest no matter what
							mappings[closestSect] = paleo;
							mappedDists[closestSect] = sectDists[closestSect];
							mappingTypes[closestSect] = MappingType.PALEO_CLOSEST_SECT;
							if (verbose)
								System.out.println("\t\tMapping closest subsection: "+closestSect+": "+(float)sectDists[closestSect]+" km");
							if (closestSect2 >= 0) {
								mappings[closestSect2] = paleo;
								mappingTypes[closestSect2] = MappingType.PALEO_CLOSEST_SECT;
								mappedDists[closestSect2] = sectDists[closestSect];
								if (verbose)
									System.out.println("\t\tAlso mapping equally-close subsection: "+closestSect2+": "+(float)sectDists[closestSect2]+" km");
							}
							// split up the parent
							// assign everything that is closer to this location than any other
							for (int s=startIndex; s<=endIndex; s++) {
								double myDist = sectDists[s];
								if (s != closestSect && s != closestSect2 && myDist < mappedDists[s]) {
									if (verbose)
										System.out.println("\t\tAlso mapping "+s+" as it's closer to this ("+paleo.year
												+", "+(float)myDist+" km) than the prior ("+mappings[s].year+", "+(float)mappedDists[s]+" km)");
									mappings[s] = paleo;
									mappedDists[s] = sectDists[s];
									mappingTypes[s] = MappingType.PALEO_NEIGHBORING_SECT;
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
					FaultSection sect = subsectListForParentList.get(s);
					sect.setDateOfLastEvent(mappings[s].epochMillis);
					mappings[s].addMapedSection(sect, mappingTypes[s]);
				}
			}
			if (verbose) System.out.println("----------------------------------");
		}
		
		
		// Create string of DOLE data mapping status
		String mappingInfoString = "";
		ArrayList<Region> regionList = new ArrayList<Region>();
		try {
			regionList.add(SeismicityRegions.CONUS_WEST.load());
			regionList.add(SeismicityRegions.CONUS_EAST.load());
			regionList.add(AnalysisRegions.ALASKA.load());
		} catch (IOException e) {
			e.printStackTrace();
		}
		int numRegions = regionList.size();
		
//		regionList=null;
//		numRegions = 1;

		double minLon = 0;	// this is to avoid the problem in crossing at -180 longitude
		for(int regionIndex=0; regionIndex<numRegions; regionIndex++) {
			if(regionList != null)
				mappingInfoString += "\n***************  "+regionList.get(regionIndex).getName()+"  ***************\n";
			else
				mappingInfoString += "\n***************  ALL REGIONS  ***************\n";


			for (AbstractDOLE_Data data : allDOLE_DataList) {
				Location loc = null;
				String type = "";
				String doleName = "";
				int numSectsMapped=data.getMappedSubSects().size();
				if(data instanceof HistoricalRupture) {
					loc = ((HistoricalRupture)data).trace.get(0);
					type = "HistoricalRupture";
					doleName = ((HistoricalRupture) data).eventID;
					for(Location l:((HistoricalRupture)data).trace) {
						if(minLon>l.getLongitude()) minLon = l.getLongitude();
					}
				}
				else if (data instanceof  PaleoDOLE_Data) {
					loc = ((PaleoDOLE_Data) data).location;
					type = "PaleoDOLE_Data";
					doleName = ((PaleoDOLE_Data) data).siteName;
					if(minLon>loc.getLongitude()) minLon = loc.getLongitude();
				}
				String numSectMappedString = Integer.toString(numSectsMapped);
				if(numSectsMapped==0) {
					if(subsectListForParentID_Map.get(data.faultID)== null)
						numSectMappedString = "NA";
					else
						numSectMappedString = "X";
				}
				if(regionList==null || regionList.get(regionIndex).contains(loc)) {
					mappingInfoString += data.faultID+"\t"+numSectMappedString+"\t"+data.faultName+"\t"+type+"\t"+doleName+"\t"+data.year+"\t"+loc.toString()+"\n";
				}
			}
		}
		mappingInfoString += "\n* DOLE filter region mappings (ID, numSubsectsMapped, Flt Name, Type, Paleo Name, Year, Location):";
		mappingInfoString += "\n* (NA = no such parent section; X = Paleo data overidden; Location is first trace pt for hist rups)";

		mappingInfoString+= "\n\nName inconisistancies between DOLE and parent fault sections:\n";
		mappingInfoString += nameProblemString;
		if(minLon<-180)
			mappingInfoString+="\n\nWARNING: one or locations have lon<-180 so mapping may incorrect\n";
		if(verbose) {
			System.out.println(mappingInfoString);
//			System.out.println("min DOLE Lon: "+minLon);
		}
		return mappingInfoString;

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
	
	private static void writeWarning(String warning) {
		System.out.flush(); // this forces immediate writing
		System.err.println("WARNING: "+warning);
		System.err.println();
		System.err.flush();
	}

	public static void main(String[] args) throws IOException {

		// this only tests WUS
		List<? extends FaultSection> subSects = NSHM23_DeformationModels.GEOLOGIC.build(NSHM23_FaultModels.WUS_FM_v3);
		
		System.out.println("Loading Paleo DOLE data");
		List<PaleoDOLE_Data> paleoData = loadPaleoDOLE();
		System.out.println("Loading Historical Rupture data");
		List<HistoricalRupture> histRupData = loadHistRups();
		
//		// see if we have any duplicates
//		Map<Integer, List<PaleoDOLE_Data>> paleoForParentMap = paleoData.stream().collect(
//				Collectors.groupingBy(D -> D.faultID));
//		for (Integer parentID : paleoForParentMap.keySet()) {
//			List<PaleoDOLE_Data> parentDOLE = paleoForParentMap.get(parentID);
//			if (parentDOLE.size() > 1) {
//				System.out.println(parentID+" has "+parentDOLE.size()+" DOLE mappings");
//				for (PaleoDOLE_Data dole : parentDOLE) {
//					System.out.println("\t"+dole.year+", "+dole.siteName+", "+dole.location+", "+dole.faultName);
//				}
//			}
//		}

		
//		// see which parent sections have multiple historic ruptures
//		Map<Integer, List<HistoricalRupture>> histRupForParentMap = hisRupData.stream().collect(
//				Collectors.groupingBy(D -> D.faultID));
//		for (Integer parentID : histRupForParentMap.keySet()) {
//			List<HistoricalRupture> parentDOLE = histRupForParentMap.get(parentID);
//			if (parentDOLE.size() > 1) {
//				System.out.println(parentID+" has "+parentDOLE.size()+" DOLE mappings");
//				for (HistoricalRupture dole : parentDOLE) {
//					System.out.println("\t"+dole.year+", "+dole.faultName);
//				}
//			}
//		}

//		// show which historical ruptures have the same year/epoch
//		System.out.println("Historic Ruptures by Year:");
//		ArrayList<Integer> histRupYearList = new ArrayList<Integer>();
//		for(HistoricalRupture histRup: histRupData)
//			if(!histRupYearList.contains(histRup.year))
//				histRupYearList.add(histRup.year);
//		for(Integer year: histRupYearList) {
//			System.out.println("Year="+year+":");
//			boolean first = true;
//			long epochTest=-1;
//			for(HistoricalRupture histRup: histRupData) {
//				if(histRup.year == year) {
//					System.out.println("\t"+histRup.faultID+"\t"+"\t"+histRup.faultName);
//					if(first == true ) {
//						epochTest = histRup.epochMillis;
//						first = false;
//						continue;
//					}
//					if(epochTest != histRup.epochMillis)
//						throw new RuntimeException("\tEpochs are different");
//				}
//			}
//		}

		
		mapDOLE(subSects, histRupData, paleoData, PaleoMappingAlgorithm.CLOSEST_SECT, true);
	}
}
