package org.opensha.sha.earthquake.rupForecastImpl.nshm23.prior2018;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import scratch.UCERF3.enumTreeBranches.FaultModels;

public class SpecialCases {
	
	static final String BASE_PATH = "/data/erf/nshm18/special_cases";
	static final String ACTIVITY_PROB_PATH = BASE_PATH+"/activity-probability.json";
	
	public static class ActivityProbRecord {
		int id;
		String name;
		String state;
		Double probability;
	}
	
	private static Map<Integer, ActivityProbRecord> activityProbabilities;
	
	public static synchronized Map<Integer, ActivityProbRecord> getActivityProbabilities() {
		if (activityProbabilities != null)
			return Collections.unmodifiableMap(activityProbabilities);
		Map<Integer, ActivityProbRecord> activityProbabilities = new HashMap<>();
		
		Reader reader = new BufferedReader(new InputStreamReader(
				NSHM18_DeformationModels.class.getResourceAsStream(ACTIVITY_PROB_PATH)));
		Preconditions.checkNotNull(reader, "File not found: %s", ACTIVITY_PROB_PATH);
		
		Gson gson = new GsonBuilder().create();
		
		List<ActivityProbRecord> recs = gson.fromJson(reader,
						TypeToken.getParameterized(List.class, ActivityProbRecord.class).getType());
		for (ActivityProbRecord rec : recs)
			activityProbabilities.put(rec.id, rec);
		
		SpecialCases.activityProbabilities = activityProbabilities;
		return Collections.unmodifiableMap(activityProbabilities);
	}
	
	static final String RECURRENCE_RATE_PATH = BASE_PATH+"/recurrence-rate.json";
	
	public static class RecurrenceRateRecord {
		int id;
		String name;
		String state;
		Double rate;
	}
	
	private static Map<Integer, RecurrenceRateRecord> recurrenceRates;
	
	public static synchronized Map<Integer, RecurrenceRateRecord> getRecurrenceRates() {
		if (recurrenceRates != null)
			return Collections.unmodifiableMap(recurrenceRates);
		Map<Integer, RecurrenceRateRecord> recurrenceRates = new HashMap<>();
		
		Reader reader = new BufferedReader(new InputStreamReader(
				NSHM18_DeformationModels.class.getResourceAsStream(RECURRENCE_RATE_PATH)));
		Preconditions.checkNotNull(reader, "File not found: %s", RECURRENCE_RATE_PATH);
		
		Gson gson = new GsonBuilder().create();
		
		List<RecurrenceRateRecord> recs = gson.fromJson(reader,
						TypeToken.getParameterized(List.class, RecurrenceRateRecord.class).getType());
		for (RecurrenceRateRecord rec : recs)
			recurrenceRates.put(rec.id, rec);
		
		SpecialCases.recurrenceRates = recurrenceRates;
		return Collections.unmodifiableMap(recurrenceRates);
	}
	
	static final String HISTORIC_MAX_MAG_PATH = BASE_PATH+"/historic-max-magnitude.json";
	static final String REGIONAL_MAX_MAG_PATH = BASE_PATH+"/regional-max-magnitude.json";
	
	public static class MaxMagRecord {
		int id;
		String name;
		String state;
		Double magnitude;
	}
	
	private static Map<Integer, MaxMagRecord> histMaxMags;
	private static Map<Integer, MaxMagRecord> regMaxMags;
	
	public static synchronized Map<Integer, MaxMagRecord> getHistoricMaxMags() {
		if (histMaxMags == null)
			histMaxMags = loadMaxMags(HISTORIC_MAX_MAG_PATH);
		
		return Collections.unmodifiableMap(histMaxMags);
	}
	
	public static synchronized Map<Integer, MaxMagRecord> getRegionalMaxMags() {
		if (regMaxMags == null)
			regMaxMags = loadMaxMags(REGIONAL_MAX_MAG_PATH);
		
		return Collections.unmodifiableMap(regMaxMags);
	}
	
	private static Map<Integer, MaxMagRecord> loadMaxMags(String path) {
		Map<Integer, MaxMagRecord> mags = new HashMap<>();
		
		Reader reader = new BufferedReader(new InputStreamReader(
				NSHM18_DeformationModels.class.getResourceAsStream(path)));
		Preconditions.checkNotNull(reader, "File not found: %s", path);
		
		Gson gson = new GsonBuilder().create();
		
		List<MaxMagRecord> recs = gson.fromJson(reader,
						TypeToken.getParameterized(List.class, MaxMagRecord.class).getType());
		for (MaxMagRecord rec : recs)
			mags.put(rec.id, rec);
		
		return mags;
	}
	
	static final String ZONE_PATH = BASE_PATH+"/zones";
	
	static final String[] ZONE_FILENAMES = {
		"charlevoix.geojson",
		"crittenden-co.geojson",
		"local.geojson",
		"narrow.geojson",
		"neokinema-wa-or.geojson",
		"river-picks.geojson",
		"shear-3.geojson",
		"wabash.geojson",
		"commerce-lineament.geojson",
		"eastern-rift-margin-n.geojson",
		"marianna.geojson",
		"neokinema-nv-ca.geojson",
		"regional.geojson",
		"shear-2.geojson",
		"shear-4.geojson"
	};
	
	private static List<Feature> zones;
	
	public static synchronized List<Feature> getZones() throws IOException {
		if (zones == null) {
			List<Feature> zones = new ArrayList<>();
			for (String filename : ZONE_FILENAMES)
				zones.add(Feature.read(new BufferedReader(new InputStreamReader(
						NSHM18_DeformationModels.class.getResourceAsStream(ZONE_PATH+"/"+filename)))));
			SpecialCases.zones = zones;
		}
		return Collections.unmodifiableList(zones);
	}
	
	public static void plotSpecialCases(File outputDir, String preifx, Region plotReg) throws IOException {
		List<? extends FaultSection> fullSects = NSHM18_FaultModels.NSHM18_WUS_PlusU3_FM_3p1.getFaultSections();
		
		Map<Integer, ActivityProbRecord> activityProbs = getActivityProbabilities();
		Map<Integer, RecurrenceRateRecord> recurrenceRates = getRecurrenceRates();
		Map<Integer, MaxMagRecord> histMaxMags = getHistoricMaxMags();
		Map<Integer, MaxMagRecord> regMaxMags = getRegionalMaxMags();
		List<Feature> zones = getZones();
		
		Color activityColor = Color.CYAN;
		Color recurrenceColor = Color.RED;
		Color histMagColor = Color.GREEN;
		Color regMagColor = Color.BLUE;
		
		List<FaultSection> modSects = new ArrayList<>();
		List<Color> colors = new ArrayList<>();
		
		HashSet<Integer> mapped = new HashSet<>();
		for (FaultSection sect : fullSects) {
			
			int id = sect.getSectionId();
			String name = sect.getSectionName();
			
			sect = sect.clone();
			sect.setSectionId(modSects.size());
			sect.setParentSectionId(id);
			
			modSects.add(sect);
			
			Color color = null;
			String type = null;
			if (activityProbs.containsKey(id)) {
				color = activityColor;
				type = "Activity";
			}
			
			if (histMaxMags.containsKey(id)) {
				if (type != null)
					System.err.println("WARNING: multiple mappings for "+id+". "+name+". Overriding "+type+" with historical max mag");
				type = "Historical max mag";
				color = histMagColor;
			}
			if (regMaxMags.containsKey(id)) {
				if (type != null)
					System.err.println("WARNING: multiple mappings for "+id+". "+name+". Overriding "+type+" with regional max mag");
				type = "Regional max mag";
				color = regMagColor;
			}
			if (recurrenceRates.containsKey(id)) {
				if (type != null)
					System.err.println("WARNING: multiple mappings for "+id+". "+name+". Overriding "+type+" with recurrence rate");
				type = "Recurrence rate";
				color = recurrenceColor;
			}
			
			if (color != null) {
				mapped.add(id);
//				System.out.println(id);
			}
			
			colors.add(color);
		}
		
		for (ActivityProbRecord rec : activityProbs.values())
			if (!mapped.contains(rec.id))
				System.err.println("WARNING: no activity probability mapping found for "+rec.id+". "+rec.name);
		for (RecurrenceRateRecord rec : recurrenceRates.values())
			if (!mapped.contains(rec.id))
				System.err.println("WARNING: no recurrence rate mapping found for "+rec.id+". "+rec.name);
		for (MaxMagRecord rec : histMaxMags.values())
			if (!mapped.contains(rec.id))
				System.err.println("WARNING: no hist max mag mapping found for "+rec.id+". "+rec.name);
		for (MaxMagRecord rec : regMaxMags.values())
			if (!mapped.contains(rec.id))
				System.err.println("WARNING: no regional max mag mapping found for "+rec.id+". "+rec.name);
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(modSects, plotReg);
		
		mapMaker.setSkipNaNs(true);
		
		mapMaker.plotSectColors(colors);
		
		List<Region> zoneRegions = new ArrayList<>();
		for (Feature feature : zones)
			zoneRegions.add(Region.fromFeature(feature));
		mapMaker.plotInsetRegions(zoneRegions, new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK), Color.GRAY, 0.1);
		
		mapMaker.plot(outputDir, preifx, "NSHM18 Special Cases");
		
		int numSpecial = 0;
		int numZoneOverlap = 0;
		int numOutsideCA = 0;
		int numEither = 0;
		
		Set<Integer> u3IDs = FaultModels.FM3_1.getFaultSectionIDMap().keySet();
		for (FaultSection sect : fullSects) {
			if (mapped.contains(sect.getSectionId()))
				numSpecial++;
			if (!u3IDs.contains(sect.getSectionId())) {
				numOutsideCA++;
				boolean contained = false;
				for (Region reg : zoneRegions) {
					for (Location loc : sect.getFaultTrace()) {
						if (reg.contains(loc))
							contained = true;
					}
				}
				if (contained)
					numZoneOverlap++;
				if (contained || mapped.contains(sect.getSectionId()))
					numEither++;
			}
		}
		
		DecimalFormat pDF = new DecimalFormat("0.0%");
		System.out.println(numSpecial+"/"+numOutsideCA+" ("+pDF.format((double)numSpecial/(double)numOutsideCA)
			+") non-U3 faults are special cases");
		System.out.println(numZoneOverlap+"/"+numOutsideCA+" ("+pDF.format((double)numZoneOverlap/(double)numOutsideCA)
			+") non-U3 faults lie in zone polygons");
		System.out.println(numEither+"/"+numOutsideCA+" ("+pDF.format((double)numEither/(double)numOutsideCA)
			+") non-U3 faults are either special cases or lie in zone polygons");
	}

	public static void main(String[] args) throws IOException {
		plotSpecialCases(new File("/tmp"), "nshm18_special_cases", NSHM23_RegionLoader.loadFullConterminousWUS());
	}

}
