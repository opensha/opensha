package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry.Point;
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
		return readFaultSections(new BufferedReader(new FileReader(file)));
	}
	
	/**
	 * Loads all fault sections from the given GeoJSON.
	 *  
	 * @param reader
	 * @param includeSecondary
	 * @return
	 * @throws IOException
	 */
	public static List<GeoJSONFaultSection> readFaultSections(Reader reader) throws IOException {
		if (!(reader instanceof BufferedReader))
			reader = new BufferedReader(reader);
		FeatureCollection features = FeatureCollection.read(reader);
		
		List<GeoJSONFaultSection> ret = new ArrayList<>();
		
		HashSet<Integer> prevIDs = new HashSet<>();
		
		HashMap<String, Integer> nameCounts = new HashMap<>();
		HashMap<String, GeoJSONFaultSection> nameSectMap = new HashMap<>();
		
		for (Feature feature : features.features) {
			GeoJSONFaultSection sect = GeoJSONFaultSection.fromFeature(feature);
			int id = sect.getSectionId();
			Preconditions.checkState(!prevIDs.contains(id), "Duplicate fault ID detected: %s", id);
			String sectName = sect.getSectionName();
			Integer nameCount = nameCounts.get(sectName);
			if (nameCount == null) {
				nameCount = 1;
			} else {
				// duplicate name!
				nameCount++;
				String state = sect.getProperty("PrimState", "");
				Preconditions.checkState(!state.isBlank(), "State cannot be blank in the case of duplicate fault names");
				String newSectName = sectName+" ("+state+")";
				System.err.println("WARNING: duplicate fault section name detected, changing '"+sectName+"' to '"+newSectName+"'");
				sect.setSectionName(newSectName);
				if (nameSectMap.containsKey(sectName)) {
					// rename the first one as well
					GeoJSONFaultSection prevSect = nameSectMap.remove(sectName);
					String prevState = prevSect.getProperty("PrimState", "");
					Preconditions.checkState(!prevState.isBlank(), "State cannot be blank in the case of duplicate fault names");
					String newPrevSectName = sectName+" ("+prevState+")";
					prevSect.setSectionName(newPrevSectName);
					nameSectMap.put(newPrevSectName, prevSect);
					System.err.println("\tretroactively changing previous '"+sectName+"' to '"+newPrevSectName+"'");
				}
			}
			nameCounts.put(sectName, nameCount); // this is the original section name
			
			sectName = sect.getSectionName();
			Preconditions.checkState(!nameSectMap.containsKey(sectName), "Duplicate name should have been caught: %s", sectName);
			nameSectMap.put(sectName, sect);
			
			ret.add(sect);
			prevIDs.add(id);
		}
		reader.close();
		return ret;
	}
	
	public static void writeFaultSections(File file, List<? extends FaultSection> sects) throws IOException {
		Writer writer = new BufferedWriter(new FileWriter(file));
		writeFaultSections(writer, sects);
		writer.close();
	}
	
	public static void writeFaultSections(Writer writer, List<? extends FaultSection> sects) throws IOException {
		List<Feature> features = new ArrayList<>(sects.size());
		for (FaultSection sect : sects)
			features.add(GeoJSONFaultSection.toFeature(sect));
		FeatureCollection collection = new FeatureCollection(features);
		FeatureCollection.write(collection, writer);
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
	
	private static <E extends FaultSection> Map<Integer, E> idMappedSects(List<E> sects) {
		return sects.stream().collect(Collectors.toMap(E::getSectionId, Function.identity()));
	}
	
	/*
	 * Geologic Deformation Model GeoJSON
	 */

	public static void attachGeoDefModel(List<GeoJSONFaultSection> sects, File dmGeoJSONFile) throws IOException {
		attachGeoDefModel(sects, FeatureCollection.read(dmGeoJSONFile));
	}
	
	public static void attachGeoDefModel(List<GeoJSONFaultSection> sects, FeatureCollection defModel) {
		Map<Integer, GeoJSONFaultSection> idMapped = idMappedSects(sects);
		Preconditions.checkState(sects.size() == idMapped.size(), "Duplicate fault ids in list?");
		HashSet<Integer> processed = new HashSet<>();
		
		int numInferred = 0;
		for (Feature dmSect : defModel.features) {
			FeatureProperties props = dmSect.properties;
			int id = props.getInt("FaultID", -1);
			Preconditions.checkState(id >= 0, "Feature is missing FaultID field");
			Preconditions.checkState(!processed.contains(id), "Duplicate id encountered: %s", id);
			if (!idMapped.containsKey(id))
				// no fault for this ID, might be filtered (e.g., by state)
				continue;
			double prefRate = props.getDouble("PrefRate", Double.NaN);
			double lowRate = props.getDouble("LowRate", Double.NaN);
			double highRate = props.getDouble("HighRate", Double.NaN);
			String treatment = props.get("Treat", null);
			String rateType = props.get("RateType", null);
			double stdDev = props.getDouble("StdMinus", Double.NaN);
			if (Double.isNaN(stdDev))
				stdDev = props.getDouble("Stdev", Double.NaN);
			
			processed.add(id);
			GeoJSONFaultSection sect = idMapped.get(id);
			String sectName = sect.getName();
			String dmFaultName = props.get("FaultName", "");
			if (!sectName.startsWith(dmFaultName))
				System.err.println("WARNING: name mismatch for "+id+": '"+sectName+"' != '"+dmFaultName+"'");
			// use startsWith as we might attach a suffix
			// also strip all whitespace
			
			String compSectName = sectName.replaceAll("\\W+", "");
			String compDMName = dmFaultName.replaceAll("\\W+", "");
			Preconditions.checkState(compSectName.startsWith(compDMName) || compDMName.startsWith(compSectName),
					"DM/FM name mismatch for %s: '%s' != '%s'", id, sect.getName(), dmFaultName);
			
			if (prefRate < 0d || !Double.isFinite(prefRate)) {
				System.err.println("No rate available (setting to zero) for "+id+". "+sect.getSectionName());
				sect.setAveSlipRate(0d);
				continue;
			}
			if ((float)prefRate >= 999f) {
				System.err.println("Slip rate magic number of "+(int)prefRate+" encountered, which makes Kevin very angry. "
						+ "Will treat as zero for now, but please fix ASAP. "+id+". "+sect.getSectionName());
				sect.setAveSlipRate(0d);
				continue;
			}
			
			Preconditions.checkState(Double.isFinite(highRate) && highRate >= 0d && highRate >= prefRate,
					"Bad HighRate for %s: %s", id, (Double)highRate);
			Preconditions.checkState(Double.isFinite(lowRate) && lowRate >= 0d && lowRate <= prefRate,
					"Bad LowRate for %s: %s", id, (Double)lowRate);
			
			sect.setAveSlipRate(prefRate);
			sect.setProperty("HighRate", highRate);
			sect.setProperty("LowRate", lowRate);
			if (treatment == null)
				sect.setProperty("Treatment", null);
			else
				sect.setProperty("Treatment", treatment);
			if (rateType == null)
				sect.setProperty("RateType", null);
			else
				sect.setProperty("RateType", rateType);
			
			if (Double.isFinite(stdDev)) {
				if (stdDev == 0d) {
					Preconditions.checkState(prefRate == 0d,
							"Slip rate is nonzero but standard deviation is zero for %s", sect.getName());
					System.err.println("WARNING: setting fake slip rate standard deviation of 0.01 mm/yr for "
							+sect.getName()+" that has a zero slip rate and zero slip rate standard deviation.");
					stdDev = 0.01d;
				}
			} else {
				numInferred++;
				// infer std dev from bounds
				// assume bounds are +/- 2 sigma (95% CI), and thus std dev is (high-low)/4
				stdDev = (highRate-lowRate)/4d;
			}
			
			sect.setSlipRateStdDev(stdDev);
		}
		System.out.println("Attached deformation model rates for "+processed.size()+" sections");
		if (numInferred > 0)
			System.err.println("WARNING: inferred "+numInferred+" slip rate values from bounds, assuming bounds are 2-sigma");
		if (sects.size() != processed.size()) {
			// find the missing section(s)
			String missingStr = null;
			for (GeoJSONFaultSection sect : sects) {
				if (!processed.contains(sect.getSectionId())) {
					if (missingStr == null)
						missingStr = "";
					else
						missingStr += "; ";
					missingStr += sect.getSectionId()+". "+sect.getSectionName();
				}
			}
			int num = sects.size()-processed.size();
			throw new IllegalStateException("No mappings found for "+num+" section(s): "+missingStr);
		}
	}
	
	/*
	 * Geologic Database GeoJSON
	 */
	
	public static class GeoDBSlipRateRecord {
		public final String slipRateID;
		public final int faultID;
		public final String faultName;
		public final String state;
		public final String dataType;
		public final String observation;
		public final double prefRate;
		public final double lowRate;
		public final double highRate;
		public final double rate2014;
		public final Location location;
		
		public GeoDBSlipRateRecord(Feature feature) {
			Preconditions.checkNotNull(feature);
			FeatureProperties properties = feature.properties;
			Preconditions.checkNotNull(properties);
			/*
			 * case "SlipRateID":
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
			 */
			// these are required
			slipRateID = properties.get("SlipRateID").toString();
			faultID = properties.getInt("FaultID", -1);
			Preconditions.checkState(faultID > 0);
			faultName = properties.get("FaultName").toString();
			state = properties.get("State").toString();
			// these are optional
			dataType = properties.get("DataType", null);
			observation = properties.get("Observn", null);
			prefRate = tryGetDouble(properties, "PrefRate");
			lowRate = tryGetDouble(properties, "LowRate");
			highRate = tryGetDouble(properties, "HighRate");
			rate2014 = tryGetDouble(properties, "2014rate");
			
			Preconditions.checkNotNull(feature.geometry);
			Preconditions.checkState(feature.geometry instanceof Point, "Only Point geometry supported");
			location = ((Point)feature.geometry).point;
		}
		
		private double tryGetDouble(FeatureProperties properties, String name) {
			Object value = properties.get(name);
			if (value == null) {
				return Double.NaN;
			} else if (value instanceof Number) {
				return ((Number)value).doubleValue();
			} else {
				String str = value.toString();
				try {
					return Double.parseDouble(str);
				} catch (NumberFormatException e) {
					return Double.NaN;
				}
			}
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
	public static Map<Integer, List<GeoDBSlipRateRecord>> readGeoDB(File file) throws IOException {
		return readGeoDB(new BufferedReader(new FileReader(file)));
	}
	
	/**
	 * Loads all fault geologic slip rate records from the given GeoJSON reader. Results are mapped from fault ID to 
	 * records for that fault.
	 * 
	 * @param reader
	 * @return map of fault ID to geologic records for that fault
	 * @throws IOException
	 */
	public static Map<Integer, List<GeoDBSlipRateRecord>> readGeoDB(Reader reader) throws IOException {
		if (!(reader instanceof BufferedReader))
			reader = new BufferedReader(reader);
		Map<Integer, List<GeoDBSlipRateRecord>> ret = new HashMap<>();
		
		FeatureCollection features = FeatureCollection.read(reader);
		
		for (Feature feature : features.features) {
			GeoDBSlipRateRecord record = new GeoDBSlipRateRecord(feature);
			List<GeoDBSlipRateRecord> faultRecords = ret.get(record.faultID);
			if (faultRecords == null) {
				faultRecords = new ArrayList<>();
				ret.put(record.faultID, faultRecords);
			}
			faultRecords.add(record);
		}
		reader.close();
		return ret;
	}
	
	public static void testMapSlipRates(Collection<? extends FaultSection> sects, Map<Integer, List<GeoDBSlipRateRecord>> recsMap,
			Double defaultSlipRate, List<FaultSection> fallbacks) {
		if (defaultSlipRate == null)
			defaultSlipRate = Double.NaN;
		for (FaultSection sect : sects) {
			List<GeoDBSlipRateRecord> records = recsMap.get(sect.getSectionId());
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
			for (GeoDBSlipRateRecord record : records) {
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
	
	private static Double bestGuessSlipRate(GeoDBSlipRateRecord record) {
		if (Double.isFinite(record.prefRate))
			return record.prefRate;
		if (Double.isFinite(record.rate2014))
			return record.rate2014;
		// try bounds
		if (Double.isFinite(record.lowRate) && Double.isFinite(record.highRate))
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
	
	public static final String NSHM23_SECTS_CUR_VERSION = "v1p4";
	private static final String NSHM23_SECTS_PATH_PREFIX = "/data/erf/nshm23/fault_models/";
	public static final String NSHM23_DM_CUR_VERSION = "v1p2";
	private static final String NSHM23_DM_PATH_PREFIX = "/data/erf/nshm23/def_models/";
	
	public static List<FaultSection> buildNSHM23SubSects() throws IOException {
		return buildNSHM23SubSects(null);
	}
	
	public static List<FaultSection> buildNSHM23SubSects(String state) throws IOException {
		String sectPath = NSHM23_SECTS_PATH_PREFIX+NSHM23_SECTS_CUR_VERSION
				+"/NSHM23_FaultSections_"+NSHM23_SECTS_CUR_VERSION+".geojson";
		Reader sectsReader = new BufferedReader(new InputStreamReader(
				GeoJSONFaultReader.class.getResourceAsStream(sectPath)));
		Preconditions.checkNotNull(sectsReader, "Fault model file not found: %s", sectPath);
		List<GeoJSONFaultSection> sects = readFaultSections(sectsReader);
		if (state != null)
			sects = filterByState(sects, state, true);
		// map deformation model
		String dmPath = NSHM23_DM_PATH_PREFIX+"geologic/"+NSHM23_DM_CUR_VERSION
				+"/NSHM23_GeolDefMod_"+NSHM23_DM_CUR_VERSION+".geojson";
		Reader dmReader = new BufferedReader(new InputStreamReader(
				GeoJSONFaultReader.class.getResourceAsStream(dmPath)));
		Preconditions.checkNotNull(dmReader, "Deformation model file not found: %s", dmPath);
		FeatureCollection defModel = FeatureCollection.read(dmReader);
		attachGeoDefModel(sects, defModel);
		return buildSubSects(sects);
	}
	
	public static List<FaultSection> buildSubSects(File sectsFile, File geoDBFile, String state) throws IOException {
		return buildSubSects(new BufferedReader(new FileReader(sectsFile)),
				new BufferedReader(new FileReader(geoDBFile)), state);
	}
	
	public static List<FaultSection> buildSubSects(Reader sectsReader, Reader geoDBReader, String state) throws IOException {
		List<GeoJSONFaultSection> sects = readFaultSections(sectsReader);
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
		Map<Integer, List<GeoDBSlipRateRecord>> slipRates = GeoJSONFaultReader.readGeoDB(geoDBReader);
		List<FaultSection> fallbacks = new ArrayList<>();
		fallbacks.addAll(FaultModels.FM3_1.getFaultSections());
		fallbacks.addAll(FaultModels.FM3_2.getFaultSections());
		GeoJSONFaultReader.testMapSlipRates(sects, slipRates, 1d, fallbacks);
		return buildSubSects(sects);
	}

	public static void main(String[] args) throws IOException {
		String sectPath = NSHM23_SECTS_PATH_PREFIX+NSHM23_SECTS_CUR_VERSION
				+"/NSHM23_FaultSections_"+NSHM23_SECTS_CUR_VERSION+".geojson";
		Reader sectsReader = new BufferedReader(new InputStreamReader(
				GeoJSONFaultReader.class.getResourceAsStream(sectPath)));
		Preconditions.checkNotNull(sectsReader, "Fault model file not found: %s", sectPath);
		List<GeoJSONFaultSection> sects = readFaultSections(sectsReader);
		// map deformation model
		String dmPath = NSHM23_DM_PATH_PREFIX+"geologic/"+NSHM23_DM_CUR_VERSION
				+"/NSHM23_GeolDefMod_"+NSHM23_DM_CUR_VERSION+".geojson";
		Reader dmReader = new BufferedReader(new InputStreamReader(
				GeoJSONFaultReader.class.getResourceAsStream(dmPath)));
		Preconditions.checkNotNull(dmReader, "Deformation model file not found: %s", dmPath);
		FeatureCollection defModel = FeatureCollection.read(dmReader);
		attachGeoDefModel(sects, defModel);
		
		for (GeoJSONFaultSection sect : sects) {
			FeatureProperties props = sect.getProperties();
			double high = props.getDouble("HighRate", Double.NaN);
			double low = props.getDouble("LowRate", Double.NaN);
			double stdDev = sect.getOrigSlipRateStdDev();
			String treat = props.get("Treatment", null);
			if (treat.equals("tribox")) {
				double calc = (high-low)/4d;
				System.out.println(sect.getSectionName()+" is "+treat+". high="+(float)high+"\tlow="+(float)low
						+"\tsd="+(float)stdDev+"\tcalc="+(float)calc);
			} else {
				double calc = (high-low)/2d;
				System.out.println(sect.getSectionName()+" is "+treat+". high="+(float)high+"\tlow="+(float)low
						+"\tsd="+(float)stdDev+"\tcalc="+(float)calc);
			}
//			if (treatment == null)
//				sect.setProperty("Treatment", null);
//			else
//				sect.setProperty("Treatment", treatment);
		}
		
//		File baseDir = new File("/home/kevin/OpenSHA/UCERF4/fault_models/");
//		
//		File fmFile = new File(baseDir, "NSHM23_FaultSections_v1p1/NSHM23_FaultSections_v1p1.geojson");
//		File dmFile = new File(baseDir, "NSHM23_GeolDefMod_v1/NSHM23_GeolDefMod_v1.geojson");
//		
//		List<GeoJSONFaultSection> sects = readFaultSections(fmFile);
//		System.out.println("Loaded "+sects.size()+" sections");
//		
//		attachGeoDefModel(sects, dmFile);
//		
//		List<GeoJSONFaultSection> sjcSects = new ArrayList<>();
//		for (GeoJSONFaultSection sect : sects) {
//			if (sect.getName().contains("Jacinto")) {
//				System.out.println(sect.getName());
//				sect.getProperties().remove("DipDir");
//				sect.getProperties().remove("PrimState");
//				sect.getProperties().remove("SecState");
//				sect.getProperties().remove("Proxy");
//				sect.getProperties().remove("HighRate");
//				sect.getProperties().remove("LowRate");
//				sect.getProperties().remove("Treatment");
//				sect.getProperties().remove("RateType");
//				sjcSects.add(sect);
//			}
//		}
//		writeFaultSections(new File("/home/kevin/git/opensha-fault-sys-tools/data/san_jacinto.geojson"), sjcSects);
		
//		buildNSHM23SubSects();
		
//		ArrayList<FaultSection> sects = FaultModels.FM3_1.fetchFaultSections();
//		for (FaultSection sect : sects)
//			sect.setZonePolygon(null);
//		writeFaultSections(new File("/tmp/fm3_1.geojson"), sects);
		
//		File baseDir = new File("/home/kevin/OpenSHA/UCERF4/fault_models/NSHM2023_FaultSectionsEQGeoDB_v1p2_29March2021");
//		File sectFile = new File(baseDir, "NSHM2023_FaultSections_v1p2.geojson");
//		List<GeoJSONFaultSection> sects = readFaultSections(sectFile);
//		for (FaultSection sect : sects)
//			System.out.println("\t"+sect.getSectionId()+". "+sect.getSectionName());
//		
//		writeFaultSections(new File("/tmp/sects.json"), sects);
//
//		File geoFile = new File(baseDir, "NSHM2023_EQGeoDB_v1p2.geojson");
//		Map<Integer, List<GeoDBSlipRateRecord>> slipRecords = readGeoDB(geoFile);
//		int numRecs = 0;
//		for (List<GeoDBSlipRateRecord> recs : slipRecords.values())
//			numRecs += recs.size();
//		System.out.println("Loaded "+numRecs+" slip records for "+slipRecords.size()+" faults");
//		
//		List<FaultSection> fallbacks = new ArrayList<>();
//		fallbacks.addAll(FaultModels.FM3_1.fetchFaultSections());
//		fallbacks.addAll(FaultModels.FM3_2.fetchFaultSections());
//		int largestNum = 0;
//		FaultSection largest = null;
//		System.out.println("\t"+sects.size()+" faults");
//		testMapSlipRates(sects, slipRecords, 1d, fallbacks);
//		for (FaultSection sect : sects) {
//			try {
//				RuptureSurface surf = sect.getFaultSurface(1d, false, false);
//				int num = surf.getEvenlyDiscretizedNumLocs();
//				if (num > largestNum) {
//					largestNum = num;
//					largest = sect;
//				}
//			} catch (Exception e) {
//				System.out.println("Exception building surface for: "+sect);
//				System.out.println(e.getMessage());
//			}
//		}
//		System.out.println("\tLargest has "+largestNum+" points: "+largest.getName());
//		
//		DeformationModelFetcher dmFetch = new DeformationModelFetcher(FaultModels.FM3_1, DeformationModels.GEOLOGIC, null, 0.1);
//		List<? extends FaultSection> subSects = dmFetch.getSubSectionList();
//		writeFaultSections(new File("/tmp/fm_3_1.json"), subSects);
//		
//		GeoJSONFaultSection sect0 = new GeoJSONFaultSection(subSects.get(0));
////		sect0.setZonePolygon(null);
//		writeFaultSections(new File("/tmp/fm_3_1_single.json"), List.of(sect0));
	}

}
