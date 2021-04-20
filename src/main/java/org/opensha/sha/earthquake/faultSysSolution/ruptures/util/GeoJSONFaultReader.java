package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import scratch.UCERF3.enumTreeBranches.FaultModels;

public class GeoJSONFaultReader {
	
	/*
	 * Fault sections GeoJSON
	 */
	
	public static List<FaultSection> readFaultSections(File file, String state, boolean includeSecondary) throws IOException {
		return readFaultSections(file, includeSecondary).get(state);
	}
	
	public static List<FaultSection> readFaultSections(JsonReader reader, String state, boolean includeSecondary) throws IOException {
		return readFaultSections(reader, includeSecondary).get(state);
	}
	
	/**
	 * Loads all fault sections from the given GeoJSON. Results are grouped by state. If includceSecondary is true, then
	 * faults will also be added to the lists for their secondary states if supplied
	 *  
	 * @param reader
	 * @param includeSecondary
	 * @return
	 * @throws IOException
	 */
	public static Map<String, List<FaultSection>> readFaultSections(File file, boolean includeSecondary) throws IOException {
		return readFaultSections(reader(file), includeSecondary);
	}
	
	private static JsonReader reader(File file) throws IOException {
		return new JsonReader(new BufferedReader(new FileReader(file)));
	}
	
	/**
	 * Loads all fault sections from the given GeoJSON. Results are grouped by state. If includceSecondary is true, then
	 * faults will also be added to the lists for their secondary states if supplied
	 *  
	 * @param reader
	 * @param includeSecondary
	 * @return
	 * @throws IOException
	 */
	public static Map<String, List<FaultSection>> readFaultSections(JsonReader reader, boolean includeSecondary) throws IOException {
		Map<String, List<FaultSection>> ret = new HashMap<>();
		
		HashSet<Integer> prevIDs = new HashSet<>();
		
		reader.beginObject();
		while (reader.hasNext()) {
			String nextName = reader.nextName();
			switch (nextName) {
			case "features":
				Preconditions.checkState(reader.peek() == JsonToken.BEGIN_ARRAY);
				reader.beginArray();
				while (reader.hasNext()) {
					Integer id = null;
					String name = null;
					String primState = null;
					String secState = null;
					Double dipDeg = null;
					Double rake = null;
					Double upDepth = null;
					Double lowDepth = null;
					Boolean proxy = false;
					
					FaultTrace trace = null;
					
					reader.beginObject();
					
					while (reader.hasNext()) {
						switch (reader.nextName()) {
						case "type":
							String type = reader.nextString();
							Preconditions.checkState(type.equals("Feature"), "Expected 'Feature' but was '%s'", type);
							break;
						case "properties":
							reader.beginObject();
							while (reader.hasNext()) {
								switch (reader.nextName()) {
								case "FaultID":
									id = reader.nextInt();
									break;
								case "FaultName":
									name = reader.nextString();
									break;
								case "PrimState":
									primState = reader.nextString();
									break;
								case "SecState":
									if (reader.peek() == JsonToken.NULL)
										reader.nextNull();
									else
										secState = reader.nextString();
									break;
								case "DipDeg":
									dipDeg = reader.nextDouble();
									break;
								case "Rake":
									rake = reader.nextDouble();
									break;
								case "LowDepth":
									lowDepth = reader.nextDouble();
									break;
								case "UpDepth":
									upDepth = reader.nextDouble();
									break;
								case "Proxy":
									if (reader.peek() == JsonToken.NULL)
										reader.nextNull();
									else
										proxy = reader.nextString().equals("yes");
									break;
									

								default:
									reader.skipValue();
									break;
								}
							}
							reader.endObject();
							break;
							
						case "geometry":
							reader.beginObject();
							
							while (reader.hasNext()) {
								switch (reader.nextName()) {
								case "type":
									String gType = reader.nextString();
									Preconditions.checkState(gType.equals("MultiLineString"), "Only MultiLineString supported, given: %s", gType);
									break;
								case "coordinates":
									reader.beginArray();
//									System.out.println("Loading trace for "+name);
									reader.beginArray();
									trace = new FaultTrace(name);
									while (reader.hasNext()) {
										reader.beginArray();
										double lon = reader.nextDouble();
										double lat = reader.nextDouble();
										trace.add(new Location(lat, lon));
										reader.endArray();
									}
									Preconditions.checkState(!trace.isEmpty(), "Trace is empty");
									reader.endArray();
									if (reader.peek() != JsonToken.END_ARRAY) {
//										System.err.println("WARNING: skipping mult-trace for "+name+" ("+id+")");
//										while (reader.peek() != JsonToken.END_ARRAY) {
////											System.out.println("Skipping "+reader.peek());
//											reader.skipValue();
//										}
										System.err.println("WARNING: concatenating multi-trace for "+name+" ("+id+")");
										int extraLocs = 0;
										List<FaultTrace> extraTraces = new ArrayList<>();
										while (reader.hasNext()) {
											reader.beginArray();
											FaultTrace extraTrace = new FaultTrace(null);
											while (reader.hasNext()) {
												reader.beginArray();
												double lon = reader.nextDouble();
												double lat = reader.nextDouble();
												Location newLoc = new Location(lat, lon);
												extraLocs++;
												extraTrace.add(newLoc);
												reader.endArray();
											}
											extraTraces.add(extraTrace);
											reader.endArray();
										}
										// figure out where they go
										while (!extraTraces.isEmpty()) {
											int bestIndex = -1;
											boolean bestAtEnd = false;
											double bestDistance = Double.POSITIVE_INFINITY;
											for (int i=0; i<extraTraces.size(); i++) {
												FaultTrace extraTrace = extraTraces.get(i);
												double beforeDist = LocationUtils.horzDistanceFast(extraTrace.last(), trace.first());
												double afterDist = LocationUtils.horzDistanceFast(extraTrace.first(), trace.last());
												if (beforeDist < afterDist && beforeDist < bestDistance) {
													bestIndex = i;
													bestAtEnd = false;
													bestDistance = beforeDist;
												} else if (afterDist <= beforeDist && afterDist < bestDistance) {
													bestIndex = i;
													bestAtEnd = true;
													bestDistance = afterDist;
												}
											}
											FaultTrace addTrace = extraTraces.remove(bestIndex);
//											System.err.println("\tadding extra trace with inOrder="+bestAtEnd+", dist="+bestDistance);
											if (bestAtEnd) {
												System.err.println("\tadding extra trace to end:\n\t\tprevLoc="+trace.last()+"\n\t\tnextLoc="
														+addTrace.first()+"\t(dist="+bestDistance+")");
												trace.addAll(addTrace);
											} else {
												System.err.println("\tadding extra trace to start (before previous):\n\t\tprevLoc="
													+trace.last()+"\n\t\tnextLoc="+addTrace.first()+"\t(dist="+bestDistance+")");
												trace.addAll(0, addTrace);
											}
											if (bestIndex != 0)
												System.out.println("\t\tWARNING: didn't add new traces in order");
										}
//										System.err.println("\tAdded "+extraTraces+" traces w/ "+extraLocs+" locs. "
//												+ "Max dist from prevLast to new trace: "+(float)maxDistFromPrev);
									}
									reader.endArray();
									break;

								default:
									break;
								}
							}
							
							reader.endObject();
							break;
							
						default:
							reader.skipValue();
							break;
						}
					}
					
					reader.endObject();
					
					Preconditions.checkNotNull(name);
					Preconditions.checkNotNull(id);
					Preconditions.checkNotNull(primState);
					Preconditions.checkNotNull(dipDeg);
					Preconditions.checkNotNull(rake);
					Preconditions.checkNotNull(lowDepth);
					Preconditions.checkNotNull(upDepth);
					Preconditions.checkNotNull(trace);
					FaultSectionPrefData sect = new FaultSectionPrefData();
					sect.setSectionId(id);
					sect.setSectionName(name);
					sect.setAveDip(dipDeg);
					sect.setAveRake(rake);
					sect.setAveLowerDepth(lowDepth);
					sect.setAveUpperDepth(upDepth);
					sect.setFaultTrace(trace);
					sect.setDipDirection((float)(trace.getAveStrike()+90d));
					List<FaultSection> stateList = ret.get(primState);
					if (stateList == null) {
						stateList = new ArrayList<>();
						ret.put(primState, stateList); 
					}
					stateList.add(sect);
					if (includeSecondary && secState != null) {
						stateList = ret.get(secState);
						if (stateList == null) {
							stateList = new ArrayList<>();
							ret.put(secState, stateList);
						}
						stateList.add(sect);
					}
					Preconditions.checkState(!prevIDs.contains(id));
					prevIDs.add(id);
				}
				reader.endArray();
				break;
				
			default:
//				System.out.println("Skipping "+nextName+" ("+reader.peek()+") at "+reader.getPath());
				
				reader.skipValue();
				break;
			}
//			System.out.println("Ending feature");
		}
//		System.out.println("Ending object");
		reader.endObject();
		reader.close();
		return ret;
	}
	
	/*
	 * Geologic Database GeoJSON
	 */
	
	public static class GeoSlipRateRecord {
		public final String slipRateID;
		public final int faultID;
		public final String faultName;
		public final String state;
		public final String siteName;
		public final String dataType;
		public final String observation;
		public final Double prefRate;
		public final Double lowRate;
		public final Double highRate;
		public final Double rate2014;
		public final Location location;
		
		public GeoSlipRateRecord(String slipRateID, int faultID, String faultName, String state, String siteName,
				String dataType, String observation, Double prefRate, Double lowRate, Double highRate, Double rate2014,
				Location location) {
			super();
			this.slipRateID = slipRateID;
			this.faultID = faultID;
			this.faultName = faultName;
			this.state = state;
			this.siteName = siteName;
			this.dataType = dataType;
			this.observation = observation;
			this.prefRate = prefRate;
			this.lowRate = lowRate;
			this.highRate = highRate;
			this.rate2014 = rate2014;
			this.location = location;
		}
	}
	
	/**
	 * Loads all fault geologic slip rate records from the given GeoJSON reader. Results are mapped from fault ID to 
	 * records for that fault.
	 * 
	 * @param file
	 * @return map of fault ID to geologic records for that fault
	 * @throws IOException
	 */
	public static Map<Integer, List<GeoSlipRateRecord>> readGeoDB(File file) throws IOException {
		return readGeoDB(reader(file));
	}
	
	/**
	 * Loads all fault geologic slip rate records from the given GeoJSON reader. Results are mapped from fault ID to 
	 * records for that fault.
	 * 
	 * @param reader
	 * @return map of fault ID to geologic records for that fault
	 * @throws IOException
	 */
	public static Map<Integer, List<GeoSlipRateRecord>> readGeoDB(JsonReader reader) throws IOException {
		Map<Integer, List<GeoSlipRateRecord>> ret = new HashMap<>();
		
		reader.beginObject();
		while (reader.hasNext()) {
			switch (reader.nextName()) {
			case "features":
				Preconditions.checkState(reader.peek() == JsonToken.BEGIN_ARRAY);
				reader.beginArray();
				while (reader.hasNext()) {
					String slipRateID = null;
					Integer faultID = null;
					String faultName = null;
					String state = null;
					String siteName = null;
					String dataType = null;
					String observation = null;
					Double prefRate = null;
					Double lowRate = null;
					Double highRate = null;
					Double rate2014 = null;
					Location location = null;
					
					reader.beginObject();
					
					while (reader.hasNext()) {
						switch (reader.nextName()) {
						case "type":
							String type = reader.nextString();
							Preconditions.checkState(type.equals("Feature"), "Expected 'Feature' but was '%s'", type);
							break;
						case "properties":
							reader.beginObject();
							while (reader.hasNext()) {
								String nextName = reader.nextName();
								switch (nextName) {
								case "SlipRateID":
									slipRateID = reader.nextString();
									break;
								case "FaultID":
									faultID = reader.nextInt();
									break;
								case "FaultName":
									faultName = reader.nextString();
									break;
								case "State":
									state = reader.nextString();
									break;
								case "DataType":
									dataType = reader.nextString();
									break;
								case "Observn":
									if (reader.peek() == JsonToken.NULL)
										reader.skipValue();
									else
										observation = reader.nextString();
									break;
								case "PrefRate":
									prefRate = tryReadSlipRateDouble(reader);
									break;
								case "LowRate":
									lowRate = tryReadSlipRateDouble(reader);
									break;
								case "HighRate":
									highRate = tryReadSlipRateDouble(reader);
									break;
								case "2014rate":
									rate2014 = tryReadSlipRateDouble(reader);
									break;
									

								default:
									reader.skipValue();
									break;
								}
							}
							reader.endObject();
							break;
							
						case "geometry":
							reader.beginObject();
							
							while (reader.hasNext()) {
								switch (reader.nextName()) {
								case "type":
									String gType = reader.nextString();
									Preconditions.checkState(gType.equals("Point"), "Only Point supported, given: %s", gType);
									break;
								case "coordinates":
									reader.beginArray();
									double lon = reader.nextDouble();
									double lat = reader.nextDouble();
									location = new Location(lat, lon);
									reader.endArray();
									break;

								default:
									break;
								}
							}
							
							reader.endObject();
							break;
							
						default:
							reader.skipValue();
							break;
						}
					}
					
					reader.endObject();

					Preconditions.checkNotNull(slipRateID);
					Preconditions.checkNotNull(faultID);
					Preconditions.checkNotNull(faultName);
					Preconditions.checkNotNull(state);
					GeoSlipRateRecord record = new GeoSlipRateRecord(slipRateID, faultID, faultName, state, siteName,
							dataType, observation, prefRate, lowRate, highRate, rate2014, location);
					List<GeoSlipRateRecord> faultRecords = ret.get(faultID);
					if (faultRecords == null) {
						faultRecords = new ArrayList<>();
						ret.put(faultID, faultRecords);
					}
					faultRecords.add(record);
				}
				reader.endArray();
				break;
				
			default:
				reader.skipValue();
				break;
			}
		}
		reader.endObject();
		reader.close();
		return ret;
	}
	
	private static Double tryReadSlipRateDouble(JsonReader reader) throws IOException {
		JsonToken peek = reader.peek();
		if (peek == JsonToken.NULL) {
			reader.skipValue();
			return null;
		}
		if (peek == JsonToken.STRING) {
			// could be a number, as a string...arg
			String str = reader.nextString();
			try {
				return Double.parseDouble(str);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		Preconditions.checkState(peek == JsonToken.NUMBER,
				"Expected STRING, NULL, or NUMBER for slip rate. Have %s at %s", peek, reader.getPath());
		return reader.nextDouble();
	}
	
	public static void testMapSlipRates(Collection<? extends FaultSection> sects, Map<Integer, List<GeoSlipRateRecord>> recsMap,
			Double defaultSlipRate, List<FaultSection> fallbacks) {
		if (defaultSlipRate == null)
			defaultSlipRate = Double.NaN;
		for (FaultSection sect : sects) {
			List<GeoSlipRateRecord> records = recsMap.get(sect.getSectionId());
			if (records == null) {
				// look for a fallback
				String testName = sect.getSectionName().toLowerCase();
				if (testName.contains("["))
					testName = testName.substring(0, testName.indexOf("["));
				testName = testName.replaceAll("\\W+", "").trim();
				List<String> matchNames = new ArrayList<>();
				List<FaultSection> matches = new ArrayList<>();
				if (fallbacks != null) {
					for (FaultSection fallback : fallbacks) {
						String fallbackName = fallback.getName().toLowerCase();
						if (fallbackName.contains(" alt"))
							fallbackName = fallbackName.substring(0, fallbackName.indexOf(" alt"));
						fallbackName = fallbackName.replaceAll("\\W+", "").trim();
						if (fallbackName.contains(testName)) {
							matches.add(fallback);
							matchNames.add(fallbackName);
						}
					}
				}
				if (matchNames.isEmpty()) {
//					System.out.println("TestName="+testName);
					System.err.println("WARNING: no slip rate for "+sect.getSectionId()+". "
							+sect.getSectionName()+". Setting to default="+defaultSlipRate.floatValue());
					sect.setAveSlipRate(defaultSlipRate);
				} else {
					FaultSection match = null;
//					FuzzyScore fs = new FuzzyScore(Locale.getDefault());
					LevenshteinDistance ld = new LevenshteinDistance();
					// find best match
					int numAway = Integer.MAX_VALUE;
//					System.out.println("dist scores for "+sect.getSectionName()+" -> "+testName);
					for (int i=0; i<matches.size(); i++) {
//						int myAway = fs.fuzzyScore(testName, matchNames.get(i));
						int myAway = ld.apply(testName, matchNames.get(i));
//						System.out.println("\t"+myAway+":\t"+matches.get(i).getName()+" -> "+matchNames.get(i)+"");
						if (myAway < numAway) {
							numAway = myAway;
							match = matches.get(i);
						}
					}
					System.err.println("WARNING: no slip rate for "+sect.getSectionId()+". "
							+sect.getSectionName()+". Matched to old section: "+match.getName()
							+". Using prev value="+match.getOrigAveSlipRate());
					sect.setAveSlipRate(match.getOrigAveSlipRate());
				}
				continue;
			}
			Double aveSlipRate = null;
			int numAvg = 0;
			for (GeoSlipRateRecord record : records) {
				Double guess = bestGuessSlipRate(record);
				if (guess != null) {
					if (aveSlipRate == null)
						aveSlipRate = guess;
					else
						aveSlipRate += guess;
					numAvg++;
				}
			}
			if (aveSlipRate == null) {
				System.err.println("WARNING: no useable slip rate estimates found for "+sect.getSectionId()+". "
						+sect.getSectionName()+". Setting to default="+defaultSlipRate.floatValue());
				aveSlipRate = defaultSlipRate;
			} else if (numAvg > 1) {
				aveSlipRate /= numAvg;
			}
			sect.setAveSlipRate(aveSlipRate);
		}
	}
	
	private static Double bestGuessSlipRate(GeoSlipRateRecord record) {
		if (record.prefRate != null)
			return record.prefRate;
		if (record.rate2014 != null)
			return record.rate2014;
		// try bounds
		if (record.lowRate != null && record.highRate != null)
			return 0.5*(record.lowRate + record.highRate);
		System.err.println("WARNING: Couldn't guess slip rate for "+record.faultID+". "+record.faultName+": "+record.slipRateID);
		return null;
	}
	
	public static List<FaultSection> buildSubSects(List<FaultSection> sects) {
		Collections.sort(sects, new Comparator<FaultSection>() {

			@Override
			public int compare(FaultSection o1, FaultSection o2) {
				return o1.getSectionName().compareTo(o2.getSectionName());
			}
		});
		List<FaultSection> subSects = new ArrayList<>();
		for (FaultSection sect : sects)
			subSects.addAll(sect.getSubSectionsList(0.5*sect.getOrigDownDipWidth(), subSects.size(), 2));
		System.out.println("Built "+subSects.size()+" subsections");
		return subSects;
	}
	
	public static List<FaultSection> buildSubSects(File sectsFile, File geoDBFile, String state) throws IOException {
		List<FaultSection> sects;
		if (state == null) {
			sects = new ArrayList<>();
			for (List<FaultSection> stateFaults : GeoJSONFaultReader.readFaultSections(sectsFile, false).values())
				sects.addAll(stateFaults);
		} else {
			sects = GeoJSONFaultReader.readFaultSections(sectsFile, true).get(state);
		}
		System.out.println("Loaded "+sects.size()+" sections");
		Collections.sort(sects, new Comparator<FaultSection>() {

			@Override
			public int compare(FaultSection o1, FaultSection o2) {
				return o1.getSectionName().compareTo(o2.getSectionName());
			}
		});
		// add slip rates
		Map<Integer, List<GeoSlipRateRecord>> slipRates = GeoJSONFaultReader.readGeoDB(geoDBFile);
		List<FaultSection> fallbacks = new ArrayList<>();
		fallbacks.addAll(FaultModels.FM3_1.fetchFaultSections());
		fallbacks.addAll(FaultModels.FM3_2.fetchFaultSections());
		GeoJSONFaultReader.testMapSlipRates(sects, slipRates, 1d, fallbacks);
		return buildSubSects(sects);
	}

	public static void main(String[] args) throws IOException {
		File baseDir = new File("/home/kevin/OpenSHA/UCERF4/fault_models/NSHM2023_FaultSectionsEQGeoDB_v1p2_29March2021");
		File sectFile = new File(baseDir, "NSHM2023_FaultSections_v1p2.geojson");
		Map<String, List<FaultSection>> sectsMap = readFaultSections(sectFile, false);
		for (String state : sectsMap.keySet()) {
			System.out.println(state+":");
			for (FaultSection sect : sectsMap.get(state)) {
				System.out.println("\t"+sect.getSectionId()+". "+sect.getSectionName());
			}
		}

		File geoFile = new File(baseDir, "NSHM2023_EQGeoDB_v1p2.geojson");
		Map<Integer, List<GeoSlipRateRecord>> slipRecords = readGeoDB(geoFile);
		int numRecs = 0;
		for (List<GeoSlipRateRecord> recs : slipRecords.values())
			numRecs += recs.size();
		System.out.println("Loaded "+numRecs+" slip records for "+slipRecords.size()+" faults");
		
		List<FaultSection> fallbacks = new ArrayList<>();
		fallbacks.addAll(FaultModels.FM3_1.fetchFaultSections());
		fallbacks.addAll(FaultModels.FM3_2.fetchFaultSections());
		for (String state : sectsMap.keySet()) {
			System.out.println("Testing "+state);
			int largestNum = 0;
			FaultSection largest = null;
			List<FaultSection> testSects = sectsMap.get(state);
			System.out.println("\t"+testSects.size()+" faults");
			testMapSlipRates(testSects, slipRecords, 1d, fallbacks);
			for (FaultSection sect : testSects) {
				try {
					RuptureSurface surf = sect.getFaultSurface(1d, false, false);
					int num = surf.getEvenlyDiscretizedNumLocs();
					if (num > largestNum) {
						largestNum = num;
						largest = sect;
					}
				} catch (Exception e) {
					System.out.println("Exception building surface for: "+sect);
					System.out.println(e.getMessage());
				}
			}
			System.out.println("\tLargest has "+largestNum+" points: "+largest.getName());
		}
	}

}
