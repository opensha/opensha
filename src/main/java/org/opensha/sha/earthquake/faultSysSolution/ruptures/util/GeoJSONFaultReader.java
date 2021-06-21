package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
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
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.utils.DeformationModelFetcher;

public class GeoJSONFaultReader {
	
	/*
	 * Fault sections GeoJSON
	 */
	
	/**
	 * Loads all fault sections from the given GeoJSON.
	 *  
	 * @param reader
	 * @param includeSecondary
	 * @return
	 * @throws IOException
	 */
	public static List<GeoJSONFaultSection> readFaultSections(File file) throws IOException {
		return readFaultSections(reader(file));
	}
	
	private static JsonReader reader(File file) throws IOException {
		return new JsonReader(new BufferedReader(new FileReader(file)));
	}
	
	/**
	 * Loads all fault sections from the given GeoJSON.
	 *  
	 * @param reader
	 * @param includeSecondary
	 * @return
	 * @throws IOException
	 */
	public static List<GeoJSONFaultSection> readFaultSections(JsonReader reader) throws IOException {
		List<GeoJSONFaultSection> ret = new ArrayList<>();
		
		HashSet<Integer> prevIDs = new HashSet<>();
		
		TypeAdapter<GeoJSONFaultSection> adapter = new GeoJSONFaultSection.Adapter();
		
		reader.beginObject();
		while (reader.hasNext()) {
			String nextName = reader.nextName();
			switch (nextName) {
			case "type":
				Preconditions.checkState(reader.nextString().equals("FeatureCollection"), "Top level type must be a FeatureCollection");
				break;
			case "features":
				Preconditions.checkState(reader.peek() == JsonToken.BEGIN_ARRAY);
				reader.beginArray();
				while (reader.hasNext()) {
					GeoJSONFaultSection sect = adapter.read(reader);
					int id = sect.getSectionId();
					Preconditions.checkState(!prevIDs.contains(id), "Duplicate fault ID detected: %s", id);
					ret.add(sect);
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
	
	public static void writeFaultSections(File file, List<? extends FaultSection> sects) throws IOException {
		JsonWriter writer = writer(file);
		writeFaultSections(writer, sects);
		writer.close();
	}
	
	private static JsonWriter writer(File file) throws IOException {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		JsonWriter jout = gson.newJsonWriter(new BufferedWriter(new FileWriter(file)));
		return jout;
	}
	
	public static void writeFaultSections(JsonWriter writer, List<? extends FaultSection> sects) throws IOException {
		writer.beginObject();
		
		writer.name("type").value("FeatureCollection");
		
		// don't write CRS, it's unused as of RFC 7946
//		writer.name("crs").beginObject();
//		writer.name("type").value("name");
//		writer.name("properties").beginObject().name("name").value("urn:ogc:def:crs:OGC:1.3:CRS84").endObject();
//		writer.endObject(); // end crs
		
		writer.name("features").beginArray();
		
		TypeAdapter<GeoJSONFaultSection> adapter = new GeoJSONFaultSection.Adapter();
		
		for (FaultSection sect : sects) {
			GeoJSONFaultSection geoSect;
			if (sect instanceof GeoJSONFaultSection)
				geoSect = (GeoJSONFaultSection)sect;
			else
				geoSect = new GeoJSONFaultSection(sect);
			adapter.write(writer, geoSect);
		}
		
		writer.endArray(); // end features
		
		writer.endObject();
		
		writer.flush();
	}
	
	/**
	 * Filters the given list of GeoJSON fault sections by state. Sections must have the 'PrimState' property.
	 * 
	 * @param sects
	 * @param state state abbreviation, e.g., 'CA' (not case sensitive)
	 * @param includeSecondary if true, also include any sections where the 'SecState' property matches the supplied state
	 * @return
	 */
	public static List<GeoJSONFaultSection> filterByState(List<GeoJSONFaultSection> sects, String state, boolean includeSecondary) {
		List<GeoJSONFaultSection> ret = new ArrayList<>();
		for (GeoJSONFaultSection sect : sects) {
			String primState = sect.getProperty("PrimState", null);
			Preconditions.checkNotNull(primState, "Fault does not contain 'PrimState' property: %s", sect.getSectionId());
			String secState = sect.getProperty("SecState", null);
			if (state.equalsIgnoreCase(primState) || (includeSecondary && state.equalsIgnoreCase(secState)))
				ret.add(sect);
		}
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
	
	public static List<FaultSection> buildSubSects(List<? extends FaultSection> sects) {
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
		List<GeoJSONFaultSection> sects = readFaultSections(sectsFile);
		if (state != null)
			sects = filterByState(sects, state, true);
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
		List<GeoJSONFaultSection> sects = readFaultSections(sectFile);
		for (FaultSection sect : sects)
			System.out.println("\t"+sect.getSectionId()+". "+sect.getSectionName());
		
		writeFaultSections(new File("/tmp/sects.json"), sects);

		File geoFile = new File(baseDir, "NSHM2023_EQGeoDB_v1p2.geojson");
		Map<Integer, List<GeoSlipRateRecord>> slipRecords = readGeoDB(geoFile);
		int numRecs = 0;
		for (List<GeoSlipRateRecord> recs : slipRecords.values())
			numRecs += recs.size();
		System.out.println("Loaded "+numRecs+" slip records for "+slipRecords.size()+" faults");
		
		List<FaultSection> fallbacks = new ArrayList<>();
		fallbacks.addAll(FaultModels.FM3_1.fetchFaultSections());
		fallbacks.addAll(FaultModels.FM3_2.fetchFaultSections());
		int largestNum = 0;
		FaultSection largest = null;
		System.out.println("\t"+sects.size()+" faults");
		testMapSlipRates(sects, slipRecords, 1d, fallbacks);
		for (FaultSection sect : sects) {
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
		
		DeformationModelFetcher dmFetch = new DeformationModelFetcher(FaultModels.FM3_1, DeformationModels.GEOLOGIC, null, 0.1);
		List<? extends FaultSection> subSects = dmFetch.getSubSectionList();
		writeFaultSections(new File("/tmp/fm_3_1.json"), subSects);
		
		GeoJSONFaultSection sect0 = new GeoJSONFaultSection(subSects.get(0));
//		sect0.setZonePolygon(null);
		writeFaultSections(new File("/tmp/fm_3_1_single.json"), List.of(sect0));
	}

}
