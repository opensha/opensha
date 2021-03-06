package org.opensha.sha.earthquake.rupForecastImpl.nshm23.prior2018;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.GeoJSON_Type;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.prior2018.NSHM18_DeformationModels.DefModelRecord;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM18_FaultModels implements LogicTreeNode, RupSetFaultModel {
	
	NSHM18_WUS_NoCA("NSHM18 WUS Except CA", "NSHM18-WUS-NoCA", NSHM18_DeformationModels.GEOL, 1d) {
		@Override
		public List<? extends FaultSection> getFaultSections() throws IOException {
			Reader sectsReader = new BufferedReader(new InputStreamReader(
					GeoJSONFaultReader.class.getResourceAsStream(NSHM18_SECTS_PATH)));
			Preconditions.checkNotNull(sectsReader, "Fault model file not found: %s", NSHM18_SECTS_PATH);
			return GeoJSONFaultReader.readFaultSections(sectsReader);
		}
	};
	
	static final Set<String> STATES = Set.of("WA", "OR", "ID", "MT", "WY", "NV", "UT", "CO", "AZ", "NM");
	
	public static final String NSHM18_SECTS_PATH = "/data/erf/nshm18/fault_models/geol_fm_2018.geojson";
	
	private String name;
	private String shortName;
	private RupSetDeformationModel defaultDM;
	private double weight;

	private NSHM18_FaultModels(String name, String shortName, RupSetDeformationModel defaultDM, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.defaultDM = defaultDM;
		this.weight = weight;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return name();
	}

	@Override
	public abstract List<? extends FaultSection> getFaultSections() throws IOException;

	@Override
	public RupSetDeformationModel getDefaultDeformationModel() {
		return defaultDM;
	}

	@Override
	public void attachDefaultModules(FaultSystemRupSet rupSet) {
		// TODO NamedFaults
		// TODO RegionsOfInterest
	}
	
	/**
	 * this converts the consolidated CONUS.geojson file, but has some mismatching sections with the dormation model file
	 * @param inputGeoJSON
	 * @param outputGeoJSON
	 * @throws IOException
	 */
	public static void convertConsolidatedGeoJSON(File inputGeoJSON, File outputGeoJSON) throws IOException {
		FeatureCollection inFeatures = FeatureCollection.read(inputGeoJSON);
		
		HashSet<Integer> ids = new HashSet<>();
		
		List<GeoJSONFaultSection> sects = new ArrayList<>();
		for (Feature feature : inFeatures) {
			FeatureProperties props = feature.properties;
			
			String name = props.get("name", null);
			int id = ((Number)feature.id).intValue();
			
			FeatureProperties cleanProps = new FeatureProperties();
			
			double dip = props.getDouble("dip", Double.NaN);
			Preconditions.checkState(Double.isFinite(dip), "Bad dip for %s: %s", name, dip);
			cleanProps.put(GeoJSONFaultSection.DIP, dip);
			
			double lowDepth = props.getDouble("lower-depth", Double.NaN);
			Preconditions.checkState(Double.isFinite(lowDepth), "Bad lower depth for %s: %s", name, lowDepth);
			cleanProps.put(GeoJSONFaultSection.LOW_DEPTH, lowDepth);
			
			double upDepth = props.getDouble("upper-depth", Double.NaN);
			Preconditions.checkState(Double.isFinite(upDepth), "Bad upper depth for %s: %s", name, upDepth);
			cleanProps.put(GeoJSONFaultSection.UPPER_DEPTH, upDepth);
			
			double rake = props.getDouble("rake", Double.NaN);
			Preconditions.checkState(Double.isFinite(rake), "Bad rake for %s: %s", name, rake);
			cleanProps.put(GeoJSONFaultSection.RAKE, rake);
			
			String state = props.get("state", null);
			if (state.equals("CA") || state.equals("AK"))
				continue;
			cleanProps.put("PrimState", state);
			
			Preconditions.checkState(id >= 0, "Bad ID for %s: %s", name, id);
			Preconditions.checkState(!ids.contains(id), "Duplicate ID=%s for %s", id, name);
			ids.add(id);
			cleanProps.set(GeoJSONFaultSection.FAULT_ID, id);
			
			cleanProps.set(GeoJSONFaultSection.FAULT_NAME, name);
			
			Feature modFeature = new Feature(id, feature.geometry, cleanProps);
			sects.add(GeoJSONFaultSection.fromFeature(modFeature));
		}
		
		GeoJSONFaultReader.writeFaultSections(outputGeoJSON, sects);
		System.out.println("Writing "+sects.size()+" sects to "+outputGeoJSON.getAbsolutePath());
	}
	
	/**
	 * this converts the consolidated CONUS.geojson file, but has some mismatching sections with the dormation model file
	 * @param inputGeoJSON
	 * @param outputGeoJSON
	 * @throws IOException
	 */
	public static void convertIndividualGeoJSON(File inputDir, File outputGeoJSON) throws IOException {
		
		HashSet<Integer> ids = new HashSet<>();
		
		List<GeoJSONFaultSection> sects = new ArrayList<>();
		for (File stateDir : inputDir.listFiles()) {
			if (!stateDir.isDirectory())
				continue;
			String state = stateDir.getName();
			if (!STATES.contains(state)) {
				System.out.println("Skipping state: "+state);
				continue;
			}
			
			System.out.println("Processing state: "+state);
			
//			for (File jsonFile : stateDir.listFiles()) {
//				String name = jsonFile.getName();
//				if (!name.endsWith(".geojson"))
//					continue;
//				name = name.replace(".geojson", "");
//				
//				System.out.println("\tReading "+name);
//				
//				Feature feature = Feature.read(jsonFile);
			File stateGeo = new File(stateDir, state+".geojson");
			FeatureCollection features = FeatureCollection.read(stateGeo);
			for (Feature feature : features) {
				FeatureProperties props = feature.properties;
				
				String name = props.get("name", null);
				System.out.println("\t"+name);
				
				int id = ((Number)feature.id).intValue();
				
				FeatureProperties cleanProps = new FeatureProperties();
				
				double dip = props.getDouble("dip", Double.NaN);
				Preconditions.checkState(Double.isFinite(dip), "Bad dip for %s: %s", name, dip);
				cleanProps.put(GeoJSONFaultSection.DIP, dip);
				
				double lowDepth = props.getDouble("lower-depth", Double.NaN);
				Preconditions.checkState(Double.isFinite(lowDepth), "Bad lower depth for %s: %s", name, lowDepth);
				cleanProps.put(GeoJSONFaultSection.LOW_DEPTH, lowDepth);
				
				double upDepth = props.getDouble("upper-depth", Double.NaN);
				Preconditions.checkState(Double.isFinite(upDepth), "Bad upper depth for %s: %s", name, upDepth);
				cleanProps.put(GeoJSONFaultSection.UPPER_DEPTH, upDepth);
				
				double rake = props.getDouble("rake", Double.NaN);
				Preconditions.checkState(Double.isFinite(rake), "Bad rake for %s: %s", name, rake);
				cleanProps.put(GeoJSONFaultSection.RAKE, rake);
				
				cleanProps.put("PrimState", state);
				
				Preconditions.checkState(id >= 0, "Bad ID for %s: %s", name, id);
				Preconditions.checkState(!ids.contains(id), "Duplicate ID=%s for %s", id, name);
				ids.add(id);
				cleanProps.set(GeoJSONFaultSection.FAULT_ID, id);
				
				cleanProps.set(GeoJSONFaultSection.FAULT_NAME, name);
				
				Feature modFeature = new Feature(id, feature.geometry, cleanProps);
				sects.add(GeoJSONFaultSection.fromFeature(modFeature));
			}
		}
		
		GeoJSONFaultReader.writeFaultSections(outputGeoJSON, sects);
		System.out.println("Writing "+sects.size()+" sects to "+outputGeoJSON.getAbsolutePath());
	}
	
	/**
	 * this converts the consolidated CONUS.geojson file, but has some mismatching sections with the dormation model file
	 * @param inputGeoJSON
	 * @param outputGeoJSON
	 * @throws IOException
	 */
	public static void consolidatedFaultSearchGeoJSON(File sectsDir, File conusDir, File outputGeoJSON) throws IOException {
		// first load in all sections from the fault-sections repository
		Map<Integer, GeoJSONFaultSection> allSects = new HashMap<>();
		
		boolean preferCONUS = true;
		
		for (File stateDir : sectsDir.listFiles()) {
			if (!stateDir.isDirectory())
				continue;
			String state = stateDir.getName();
			if (!STATES.contains(state)) {
				System.out.println("Skipping state: "+state);
				continue;
			}
			
			System.out.println("Processing state: "+state);
			
			for (File faultDir : stateDir.listFiles()) {
				File jsonFile = new File(faultDir, faultDir.getName()+".geojson");
				if (jsonFile.exists()) {
					String name = faultDir.getName();
					System.out.println("\tReading "+jsonFile.getAbsolutePath());
					
					Feature feature;
					try {
						feature = Feature.read(jsonFile);
					} catch (Exception e) {
						// try collection
						FeatureCollection collection = FeatureCollection.read(jsonFile);
						feature = null;
						for (Feature oFeature : collection) {
							if (oFeature.geometry.type == GeoJSON_Type.LineString) {
								Preconditions.checkState(feature == null);
								feature = oFeature;
							}
						}
					}
					
					FeatureProperties props = feature.properties;
					
					int id = ((Number)feature.id).intValue();
					
					FeatureProperties cleanProps = new FeatureProperties();
					
					double dip = props.getDouble("dip", Double.NaN);
					Preconditions.checkState(Double.isFinite(dip), "Bad dip for %s: %s", name, dip);
					cleanProps.put(GeoJSONFaultSection.DIP, dip);
					
					double lowDepth = props.getDouble("lower-depth", Double.NaN);
					Preconditions.checkState(Double.isFinite(lowDepth), "Bad lower depth for %s: %s", name, lowDepth);
					cleanProps.put(GeoJSONFaultSection.LOW_DEPTH, lowDepth);
					
					double upDepth = props.getDouble("upper-depth", Double.NaN);
					Preconditions.checkState(Double.isFinite(upDepth), "Bad upper depth for %s: %s", name, upDepth);
					cleanProps.put(GeoJSONFaultSection.UPPER_DEPTH, upDepth);
					
					double rake = props.getDouble("rake", Double.NaN);
					Preconditions.checkState(Double.isFinite(rake), "Bad rake for %s: %s", name, rake);
					cleanProps.put(GeoJSONFaultSection.RAKE, rake);
					
					cleanProps.put("PrimState", state);
					
					Preconditions.checkState(id >= 0, "Bad ID for %s: %s", name, id);
					Preconditions.checkState(!allSects.containsKey(id), "Duplicate ID=%s for %s", id, name);
					cleanProps.set(GeoJSONFaultSection.FAULT_ID, id);
					
					cleanProps.set(GeoJSONFaultSection.FAULT_NAME, name);
					
					Feature modFeature = new Feature(id, feature.geometry, cleanProps);
					allSects.put(id, GeoJSONFaultSection.fromFeature(modFeature));
				}
			}
			
////			for (File jsonFile : stateDir.listFiles()) {
////				String name = jsonFile.getName();
////				if (!name.endsWith(".geojson"))
////					continue;
////				name = name.replace(".geojson", "");
////				
////				System.out.println("\tReading "+name);
////				
////				Feature feature = Feature.read(jsonFile);
//			File stateGeo = new File(stateDir, state+".geojson");
//			FeatureCollection features = FeatureCollection.read(stateGeo);
//			for (Feature feature : features) {
//				FeatureProperties props = feature.properties;
//				
//				String name = props.get("name", null);
//				System.out.println("\t"+name);
//				
//				int id = ((Number)feature.id).intValue();
//				
//				FeatureProperties cleanProps = new FeatureProperties();
//				
//				double dip = props.getDouble("dip", Double.NaN);
//				Preconditions.checkState(Double.isFinite(dip), "Bad dip for %s: %s", name, dip);
//				cleanProps.put(GeoJSONFaultSection.DIP, dip);
//				
//				double lowDepth = props.getDouble("lower-depth", Double.NaN);
//				Preconditions.checkState(Double.isFinite(lowDepth), "Bad lower depth for %s: %s", name, lowDepth);
//				cleanProps.put(GeoJSONFaultSection.LOW_DEPTH, lowDepth);
//				
//				double upDepth = props.getDouble("upper-depth", Double.NaN);
//				Preconditions.checkState(Double.isFinite(upDepth), "Bad upper depth for %s: %s", name, upDepth);
//				cleanProps.put(GeoJSONFaultSection.UPPER_DEPTH, upDepth);
//				
//				double rake = props.getDouble("rake", Double.NaN);
//				Preconditions.checkState(Double.isFinite(rake), "Bad rake for %s: %s", name, rake);
//				cleanProps.put(GeoJSONFaultSection.RAKE, rake);
//				
//				cleanProps.put("PrimState", state);
//				
//				Preconditions.checkState(id >= 0, "Bad ID for %s: %s", name, id);
//				Preconditions.checkState(!allSects.containsKey(id), "Duplicate ID=%s for %s", id, name);
//				cleanProps.set(GeoJSONFaultSection.FAULT_ID, id);
//				
//				cleanProps.set(GeoJSONFaultSection.FAULT_NAME, name);
//				
//				Feature modFeature = new Feature(id, feature.geometry, cleanProps);
//				allSects.put(id, GeoJSONFaultSection.fromFeature(modFeature));
//			}
		}
		
		// now load the deformation model data to see what we're missing and what we want to retain
		Reader dmReader = new BufferedReader(new InputStreamReader(
				NSHM18_DeformationModels.class.getResourceAsStream(NSHM18_DeformationModels.NSHM18_DM_PATH)));
		Preconditions.checkNotNull(dmReader, "Deformation model file not found: %s", NSHM18_DeformationModels.NSHM18_DM_PATH);
		
		Gson gson = new GsonBuilder().create();
		
		List<DefModelRecord> records = gson.fromJson(dmReader,
				TypeToken.getParameterized(List.class, DefModelRecord.class).getType());
		
		List<GeoJSONFaultSection> sects = new ArrayList<>();
		
		System.out.println("Mapping sections from deformation model");
		
		int numCONUS = 0;
		int numFS = 0;
		int numSkipped = 0;
		
		for (DefModelRecord record : records) {
			if (!STATES.contains(record.state))
				continue;
			
			System.out.println("Mapping "+record.id+". "+record.name);
			
			GeoJSONFaultSection fmSect = allSects.get(record.id);
			GeoJSONFaultSection conusSect = null;
			// search for it in nshm-conus
			File jsonFile = findGeoJSON(conusDir, record.name);
			if (jsonFile != null) {
				Feature feature = Feature.read(jsonFile);
				
				FeatureProperties props = feature.properties;
				
				int id = ((Number)feature.id).intValue();
				String name = props.get("name", null);
				
				if (id == record.id) {
					FeatureProperties cleanProps = new FeatureProperties();
					
					double dip = props.getDouble("dip", Double.NaN);
					Preconditions.checkState(Double.isFinite(dip), "Bad dip for %s: %s", name, dip);
					cleanProps.put(GeoJSONFaultSection.DIP, dip);
					
					double lowDepth = props.getDouble("lower-depth", Double.NaN);
					Preconditions.checkState(Double.isFinite(lowDepth), "Bad lower depth for %s: %s", name, lowDepth);
					cleanProps.put(GeoJSONFaultSection.LOW_DEPTH, lowDepth);
					
					double upDepth = props.getDouble("upper-depth", Double.NaN);
					Preconditions.checkState(Double.isFinite(upDepth), "Bad upper depth for %s: %s", name, upDepth);
					cleanProps.put(GeoJSONFaultSection.UPPER_DEPTH, upDepth);
					
					double rake = props.getDouble("rake", Double.NaN);
					Preconditions.checkState(Double.isFinite(rake), "Bad rake for %s: %s", name, rake);
					cleanProps.put(GeoJSONFaultSection.RAKE, rake);
					
					cleanProps.put("PrimState", record.state);
					
					Preconditions.checkState(id >= 0, "Bad ID for %s: %s", name, id);
					cleanProps.set(GeoJSONFaultSection.FAULT_ID, id);
					
					// use DM name
					cleanProps.set(GeoJSONFaultSection.FAULT_NAME, record.name);
					
					Feature modFeature = new Feature(id, feature.geometry, cleanProps);
					conusSect = GeoJSONFaultSection.fromFeature(modFeature);
				} else {
					System.err.println("\tWARNING: CONUS record for "+record.name+" has a different id: "+id+" != "+record.id);
				}
			}
			
			GeoJSONFaultSection sect;
			if (preferCONUS && conusSect != null)
				sect = conusSect;
			else
				sect = fmSect;
			
			if (sect == null) {
				System.err.println("WARNING: couldn't locate fault "+record.name+", skipping");
				numSkipped++;
			} else {
				if (sect == conusSect) {
					System.out.println("\tUsing CONUS from "+jsonFile.getAbsolutePath());
					numCONUS++;
				} else {
					System.out.println("\tUsing fault-sections");
					numFS++;
				}
				if (!sect.getSectionName().equals(record.name)) {
					System.out.println("\tRenaming: "+sect.getSectionName()+" -> "+record.name);
					sect.setSectionName(record.name);
				}
				sects.add(sect);
			}
		}
		
		System.out.println("Used "+numCONUS+" from CONUS");
		System.out.println("Used "+numFS+" from fault-sections");
		System.out.println("Skipped "+numSkipped);
		
		GeoJSONFaultReader.writeFaultSections(outputGeoJSON, sects);
		System.out.println("Writing "+sects.size()+" sects to "+outputGeoJSON.getAbsolutePath());
	}
	
	private static File findGeoJSON(File dir, String name) {
		for (File file : dir.listFiles()) {
			if (file.isDirectory()) {
				File match = findGeoJSON(file, name);
				if (match != null)
					return match;
			} else if (file.getName().equals(name+".geojson")) {
				return file;
			}
		}
		return null;
	}
	
	public static void main(String[] args) throws IOException {
		File dataFile = new File("/home/kevin/workspace/opensha/src/main/resources"+NSHM18_SECTS_PATH);
//		convertConsolidatedGeoJSON(new File("/home/kevin/git/nshm-fault-sections/fault-sections/CONUS.geojson"), dataFile);
//		convertIndividualGeoJSON(new File("/home/kevin/git/nshm-fault-sections/fault-sections/"), dataFile);
		consolidatedFaultSearchGeoJSON(new File("/home/kevin/git/nshm-fault-sections-1.0/fault-sections/"),
				new File("/home/kevin/git/nshm-conus"), dataFile);
		// write NSHM18 fault/def model files
		
//		File baseDir = new File("/home/kevin/git/nshm-conus/active-crust/fault");
//		for (File stateDir : baseDir.listFiles()) {
//			if (!stateDir.isDirectory() || stateDir.getName().startsWith(".") || stateDir.getName().equals("CA"))
//				continue;
//			for (File jsonFile : stateDir.listFiles()) {
//				if (jsonFile.isDirectory()) {
//					// see if it's a fault with it's own logic tree
//					File testFile = new File(jsonFile, jsonFile.getName()+".geojson");
//					if (testFile.exists()) {
//						System.out.println(jsonFile.getName()+" has it's own dir, found "+testFile.getAbsolutePath());
//						jsonFile = testFile;
//					} else {
//						System.err.println("No GeoJSON found for "+jsonFile.getName());
//					}
//				}
//				if (jsonFile.isDirectory() || !jsonFile.getName().endsWith(".geojson"))
//					continue;
//			}
//		}
	}

}
