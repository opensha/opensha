package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.FaultUtils.AngleAverager;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.MinisectionMappings;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.MinisectionMappings.MinisectionDataRecord;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;

/**
 * Deformation models for NSHM23
 * 
 * Weight revisions:
 * 
 * Original weights recommended by the deformation model review were:
 * 	* Geologic: 20%
 *  * Evans: 10%
 *  * Pollitz: 20%
 *  * Shen-Bird: 25%
 *  * Shen-Bird: 25%
 * 
 * We revised those weights on 12/7/2022 to the following to minimize the impact of outlier slip rates on the final
 * mean model, most notably from the Evans model but also from Pollitz:
 * 	* Geologic: 26%
 *  * Evans: 2%
 *  * Pollitz: 8%
 *  * Shen-Bird: 32%
 *  * Shen-Bird: 32%
 * 
 * @author kevin
 *
 */
@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM23_DeformationModels implements RupSetDeformationModel {
	GEOLOGIC("NSHM23 Geologic Deformation Model", "Geologic", 0.26d) {
		@Override
		public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
			return buildGeol(faultModel, GEOLOGIC_VERSION);
		}
	},
	EVANS("NSHM23 Evans Deformation Model", "Evans", 0.02d) {
		@Override
		public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
			return buildGeodetic(faultModel, GEODETIC_INCLUDE_GHOST_TRANSIENT);
		}
	},
	POLLITZ("NSHM23 Pollitz Deformation Model", "Pollitz", 0.08d) {
		@Override
		public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
			return buildGeodetic(faultModel, GEODETIC_INCLUDE_GHOST_TRANSIENT);
		}
	},
	SHEN_BIRD("NSHM23 Shen-Bird Deformation Model", "Shen-Bird", 0.32d) {
		@Override
		public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
			return buildGeodetic(faultModel, GEODETIC_INCLUDE_GHOST_TRANSIENT);
		}
	},
	ZENG("NSHM23 Zeng Deformation Model", "Zeng", 0.32d) {
		@Override
		public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
			return buildGeodetic(faultModel, GEODETIC_INCLUDE_GHOST_TRANSIENT);
		}
	},
	AVERAGE("NSHM23 Averaged Deformation Model", "AvgDM", 0d) {
		@Override
		public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
			double totWeight = 0d;
			List<GeoJSONFaultSection> ret = null;
			double[] slipRates = null;
			double[] slipRateStdDevs = null;
			double[] creepRates = null;
			boolean[] hasCreeps = null;
			AngleAverager[] rakes = null;
			for (NSHM23_DeformationModels dm : values()) {
				if (dm != this && dm.weight > 0d) {
					totWeight += dm.weight;
					List<? extends FaultSection> dmSects = dm.build(faultModel);
					if (ret == null) {
						ret = new ArrayList<>();
						for (FaultSection sect : dmSects) {
							GeoJSONFaultSection geoSect = (GeoJSONFaultSection)sect;
							ret.add(geoSect.clone());
						}
						slipRates = new double[ret.size()];
						slipRateStdDevs = new double[ret.size()];
						creepRates = new double[ret.size()];
						hasCreeps = new boolean[ret.size()];
						rakes = new AngleAverager[ret.size()];
						for (int s=0; s<rakes.length; s++)
							rakes[s] = new AngleAverager();
					}
					for (int s=0; s<dmSects.size(); s++) {
						GeoJSONFaultSection sect = (GeoJSONFaultSection)dmSects.get(s);
						slipRates[s] += dm.weight*sect.getOrigAveSlipRate();
						slipRateStdDevs[s] += dm.weight*sect.getOrigSlipRateStdDev();
						double creepRate = sect.getProperty(GeoJSONFaultSection.CREEP_RATE, Double.NaN);
						if (Double.isFinite(creepRate)) {
							creepRates[s] += dm.weight*creepRate;
							hasCreeps[s] = true;
						}
						rakes[s].add(sect.getAveRake(), dm.weight);
					}
				}
			}
			Preconditions.checkState(totWeight > 0d);
			for (int s=0; s<ret.size(); s++) {
				GeoJSONFaultSection subSect = ret.get(s);
				
				double slipRate = slipRates[s]/totWeight;
				double slipRateStdDev = slipRateStdDevs[s]/totWeight;
				double creepRate = hasCreeps[s] ? creepRates[s]/totWeight : Double.NaN;
				double rake = FaultUtils.getInRakeRange(rakes[s].getAverage());
				
				subSect.setAveSlipRate(slipRate);
				subSect.setSlipRateStdDev(slipRateStdDev);
				subSect.setAveRake(rake);
				if (Double.isFinite(creepRate))
					subSect.setProperty(GeoJSONFaultSection.CREEP_RATE, creepRate);
				else
					subSect.getProperties().remove(GeoJSONFaultSection.CREEP_RATE);
				
				applyCreepData(subSect, creepRate);
			}
			return ret;
		}
	},
	MEDIAN("NSHM23 Median Deformation Model (Unweighted, Geol. Rakes)", "MedDM", 0d) {
		@Override
		public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
			return buildGeodetic(faultModel, GEODETIC_INCLUDE_GHOST_TRANSIENT);
		}
	};
	
	private static final String NSHM23_DM_PATH_PREFIX = "/data/erf/nshm23/def_models/";

	private static final String GEOLOGIC_VERSION = "v1p0";
	private static final String CREEP_DATE = "2022_08_17";
	
	private static String getGeodeticDirForFM(RupSetFaultModel faultModel) {
		if (sameFaultModel(faultModel, NSHM23_FaultModels.NSHM23_v2))
			return "fm_v2";
		if (sameFaultModel(faultModel, NSHM23_FaultModels.NSHM23_v1p4))
			return "fm_v1p4";
		System.err.println("Warning, returning most recent geodetic date for unknown fault model: "
				+faultModel.getName()+" of type "+faultModel.getClass().getName());
		return "2022_08_05";
	}
	
	private static boolean sameFaultModel(RupSetFaultModel model1, RupSetFaultModel model2) {
		return model1 == model2 || model1.getName().equals(model2.getName());
	}
	
	public static boolean GEODETIC_INCLUDE_GHOST_TRANSIENT = true;
	
	/*
	 * Standard deviation parameters that affect how low, zero, or missing standard deviations are treated.
	 */
	
	/**
	 * if true, will use geologic slip rates if standard deviations are unspecified or zero
	 */
	private static final boolean DEFAULT_STD_DEV_USE_GEOLOGIC = true;
	/**
	 * if standard deviation is zero, default to this fraction of the slip rate. if DEFAULT_STD_DEV_USE_GEOLOGIC is
	 * true, then this will only be used if the geologic slip rate standard deviation is also zero 
	 */
	private static final double DEFAULT_FRACT_SLIP_STD_DEV = 0.1;
	/**
	 * minimum allowed slip rate standart deviation, in mm/yr
	 */
	public static final double STD_DEV_FLOOR = 1e-4;
	
	/**
	 * If nonzero, will use this fractional standard deviation everywhere rather than those from the deformation model
	 */
	public static double HARDCODED_FRACTIONAL_STD_DEV = 0.1d;
	
	/*
	 * Creep parameters
	 */
	
	/**
	 * For a given creep fractional moment reduction, creepRed:
	 * 
	 * if (creepRed <= ASEIS_CEILING) {
	 * 	aseis = creepRed
	 * 	coupling = 1
	 * } else {
	 * 	aseis = ASEIS_CEILING
	 * 	coupling = 1 - (1/(1-ASEIS_CEILING))*(creepRed - ASEIS_CEILING)
	 * }
	 * 
	 * In UCERF3, this was set to 0.9.
	 */
	public static double ASEIS_CEILING = 0.4;
	
	/**
	 * If no creep value is available, use the given default creep reduction value
	 */
	public static double CREEP_FRACT_DEFAULT = 0.1;
	
	/*
	 * Other
	 */
	
	private static final Map<RupSetFaultModel, Map<Integer, GeoJSONFaultSection>> geologicSectsCache = new HashMap<>();

	private String name;
	private String shortName;
	private double weight;

	private NSHM23_DeformationModels(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
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
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean isApplicableTo(RupSetFaultModel faultModel) {
		return faultModel instanceof NSHM23_FaultModels;
	}
	
	@Override
	public abstract List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException;
	
	/*
	 * Methods for loading the geologic model
	 */
	
	public static Map<Integer, GeoJSONFaultSection> getGeolFullSects(RupSetFaultModel faultModel)
			throws IOException {
		synchronized (geologicSectsCache) {
			if (!geologicSectsCache.containsKey(faultModel))
				GEOLOGIC.build(faultModel);
			return geologicSectsCache.get(faultModel);
		}
	}

	private static List<? extends FaultSection> buildGeolFullSects(RupSetFaultModel faultModel, String version) throws IOException {
		String dmPath = NSHM23_DM_PATH_PREFIX+"geologic/"+version+"/NSHM23_GeologicDeformationModel.geojson";
		Reader dmReader = new BufferedReader(new InputStreamReader(
				GeoJSONFaultReader.class.getResourceAsStream(dmPath)));
		Preconditions.checkNotNull(dmReader, "Deformation model file not found: %s", dmPath);
		FeatureCollection defModel = FeatureCollection.read(dmReader);
		
		List<GeoJSONFaultSection> geoSects = new ArrayList<>();
		for (FaultSection sect : faultModel.getFaultSections()) {
			if (sect instanceof GeoJSONFaultSection)
				geoSects.add((GeoJSONFaultSection)sect);
			else
				geoSects.add(new GeoJSONFaultSection(sect));
		}
		GeoJSONFaultReader.attachGeoDefModel(geoSects, defModel);
		
		// see if we need to add geologic sections to the cache
		synchronized (geologicSectsCache) {
			Map<Integer, GeoJSONFaultSection> geoCache = geologicSectsCache.get(faultModel);
			if (geoCache == null) {
				geoCache = new HashMap<>();
				
				for (GeoJSONFaultSection sect : geoSects)
					geoCache.put(sect.getSectionId(), sect.clone());
				
				geologicSectsCache.put(faultModel, geoCache);
			}
		}
		
		return geoSects;
	}

	public List<? extends FaultSection> buildGeol(RupSetFaultModel faultModel, String version) throws IOException {
		Preconditions.checkState(isApplicableTo(faultModel), "DM/FM mismatch");
		List<? extends FaultSection> geoSects = buildGeolFullSects(faultModel, version);
		
		List<FaultSection> subSects = GeoJSONFaultReader.buildSubSects(geoSects);
		
		MinisectionMappings mappings = new MinisectionMappings(geoSects, subSects);
		
		return applyCreepModel(mappings, applyStdDevDefaults(faultModel, subSects));
	}
	
	/*
	 * Methods for loading the geodetic models
	 */
	
	public List<? extends FaultSection> buildGeodetic(RupSetFaultModel faultModel, boolean includeGhostCorrection)
			throws IOException {
		// first load fault model
		List<GeoJSONFaultSection> geoSects = new ArrayList<>();
		Map<Integer, FaultSection> sectsByID = new HashMap<>();
		for (FaultSection sect : faultModel.getFaultSections()) {
			if (!(sect instanceof GeoJSONFaultSection))
				sect = new GeoJSONFaultSection(sect);
			geoSects.add((GeoJSONFaultSection)sect);
			sectsByID.put(sect.getSectionId(), sect);
		}
		
		// build subsections
		List<FaultSection> subSects = GeoJSONFaultReader.buildSubSects(geoSects);
		
		MinisectionMappings mappings = new MinisectionMappings(geoSects, subSects);
		
		String fmDirName = getGeodeticDirForFM(faultModel);
		Preconditions.checkNotNull(fmDirName, "No geodetic files found for fault model %s of type %s",
				faultModel, faultModel.getClass().getName());
		
		String dmPath = NSHM23_DM_PATH_PREFIX+"geodetic/"+fmDirName+"/"+name();
		if (includeGhostCorrection)
			dmPath += "-include_ghost_corr.txt";
		else
			dmPath += "-no_ghost_corr.txt";
		Map<Integer, List<GeodeticSlipRecord>> dmRecords = loadGeodeticModel(dmPath);

		for (Integer sectID : sectsByID.keySet())
			if (!dmRecords.containsKey(sectID))
				System.err.println("WARNING: "+name+" is missing data for fault "+sectID+". "+sectsByID.get(sectID).getSectionName());
		for (Integer parentID : new ArrayList<>(dmRecords.keySet())) {
			// validate records
			if (!mappings.areMinisectionDataForParentValid(parentID, dmRecords.get(parentID), true)) {
				FaultSection parentSect = sectsByID.get(parentID);
				if (parentSect == null)
					System.err.println("WARNING: "+name()+" removing data for unkown fault with ID="+parentID);
				else
					System.err.println("WARNING: "+name()+" does not contain valid data for fault "+parentID+". "
							+sectsByID.get(parentID).getSectionName()+", setting slip rate to 0.");
				// remove bad data
				dmRecords.remove(parentID);
			}
		}
		
		Map<Integer, GeoJSONFaultSection> geoDMSects = null; // may be needed if zeros are encountered
		int numRakesSkipped = 0;
		
		// replace slip rates and rakes from deformation model
		for (FaultSection subSect : subSects) {
			int subSectID = subSect.getSectionId();
			int parentID = subSect.getParentSectionId();
			List<GeodeticSlipRecord> records = dmRecords.get(parentID);
			if (records == null) {
				subSect.setAveSlipRate(0d);
				subSect.setSlipRateStdDev(0d);
				continue;
			}

			List<Double> recSlips = new ArrayList<>(records.size());
			List<Double> recSlipStdDevs = new ArrayList<>(records.size());
			List<Double> recRakes = new ArrayList<>(records.size());
			
			for (GeodeticSlipRecord record : records) {
				recSlips.add(record.slipRate);
				if (Double.isNaN(record.slipRateStdDev))
					recSlipStdDevs = null;
				else 
					recSlipStdDevs.add(record.slipRateStdDev);
				recRakes.add(record.rake);
			}

			// these are length averaged
			double avgSlip = mappings.getAssociationScaledAverage(subSectID, recSlips);
			Preconditions.checkState(Double.isFinite(avgSlip) && avgSlip >= 0d,
					"Bad slip rate for subSect=%, parentID=%: %s",
					subSect.getSectionId(), parentID, avgSlip);
			double avgSlipStdDev;
			if (recSlipStdDevs == null) {
				// will replace when applying defaults at the end
				avgSlipStdDev = Double.NaN;
			} else {
				avgSlipStdDev = mappings.getAssociationScaledAverage(subSectID, recSlipStdDevs);
				Preconditions.checkState(Double.isFinite(avgSlipStdDev),
						"Bad slip rate standard deviation for subSect=%, parentID=%: %s",
						subSect.getSectionId(), parentID, avgSlipStdDev);
				if (avgSlipStdDev == 0d && avgSlip > 0d)
					System.err.println("WARNING: slipRateStdDev=0 for "+subSect.getSectionId()
						+". "+subSect.getSectionName()+", with slipRate="+avgSlip);
			}
			double avgRake = FaultUtils.getInRakeRange(mappings.getAssociationScaledAngleAverage(subSectID, recRakes));
			Preconditions.checkState(Double.isFinite(avgRake), "Bad rake for subSect=%, parentID=%: %s",
					subSect.getSectionId(), parentID, avgRake);
			
			if ((float)avgSlip == 0f && (float)avgRake == 0f) {
				// this is likely a placeholder rake, skip
				if (geoDMSects == null)
					geoDMSects = getGeolFullSects(faultModel);
				avgRake = geoDMSects.get(parentID).getAveRake();
				if ((float)avgRake != 0f)
					// it wasn't supposed to be zero
					numRakesSkipped++;
			}
			subSect.setAveSlipRate(avgSlip);
			subSect.setSlipRateStdDev(avgSlipStdDev);
			subSect.setAveRake(avgRake);
		}
		
		if (numRakesSkipped > 0)
			System.err.println("WARNING: Ignored rakes set to zero with zero slip rates for "+numRakesSkipped
					+"/"+subSects.size()+" ("+pDF.format((double)numRakesSkipped/(double)subSects.size())
					+") subsections, keeping geologic rakes to avoid placeholder");
		
		return applyCreepModel(mappings, applyStdDevDefaults(faultModel, subSects));
	}
	
	private static Map<Integer, List<GeodeticSlipRecord>> loadGeodeticModel(String path) throws IOException {
		InputStream stream = NSHM23_DeformationModels.class.getResourceAsStream(path);
		Preconditions.checkNotNull(stream, "Deformation model file not found: %s", path);
		BufferedReader dmReader = new BufferedReader(new InputStreamReader(stream));
		Preconditions.checkNotNull(dmReader, "Deformation model file not found: %s", path);
		
		Map<Integer, List<GeodeticSlipRecord>> ret = new HashMap<>();
		
		String line = null;
		while ((line = dmReader.readLine()) != null) {
			line = line.trim();
			if (line.isBlank() || line.startsWith("#"))
				continue;
			line = line.replaceAll("\t", " ");
			while (line.contains("  "))
				line = line.replaceAll("  ", " ");
			String[] split = line.split(" ");
			Preconditions.checkState(split.length == 9 || split.length == 8, "Expected 8/9 columns, have %s. Line: %s", split.length, line);
			
			int index = 0;
			int parentID = Integer.parseInt(split[index++]);
			Preconditions.checkState(parentID >= 0, "Bad parentID=%s. Line: %s", parentID, line);
			int minisectionID = Integer.parseInt(split[index++]);
			Preconditions.checkState(minisectionID >= 1, "Bad minisectionID=%s. Line: %s", minisectionID, line);
			double startLat = Double.parseDouble(split[index++]);
			double startLon = Double.parseDouble(split[index++]);
			Location startLoc = new Location(startLat, startLon);
			double endLat = Double.parseDouble(split[index++]);
			double endLon = Double.parseDouble(split[index++]);
			Location endLoc = new Location(endLat, endLon);
			double rake = Double.parseDouble(split[index++]);
			Preconditions.checkState(Double.isFinite(rake) && (float)rake >= -180f && (float)rake <= 180f, 
					"Bad rake=%s. Line: %s", rake, line);
			double slipRate = Double.parseDouble(split[index++]);
			Preconditions.checkState(slipRate >= 0d && Double.isFinite(slipRate),
					"Bad slipRate=%s. Line: %s", slipRate, line);
			double slipRateStdDev;
			if (split.length > index) {
				slipRateStdDev = Double.parseDouble(split[index++]);
				Preconditions.checkState(slipRateStdDev >= 0d && Double.isFinite(slipRateStdDev),
						"Bad slipRateStdDev=%s. Line: %s", slipRateStdDev, line);
			} else {
				slipRateStdDev = Double.NaN;
			}
			
			List<GeodeticSlipRecord> parentRecs = ret.get(parentID);
			if (parentRecs == null) {
				parentRecs = new ArrayList<>();
				ret.put(parentID, parentRecs);
				Preconditions.checkState(minisectionID == 1,
						"First minisection encounterd for fault %s, but minisection ID is %s",
						parentID, minisectionID);
			} else {
				GeodeticSlipRecord prev = parentRecs.get(parentRecs.size()-1);
				Preconditions.checkState(minisectionID == prev.minisectionID+2, // +2 here as prev is 0-based
						"Minisections are out of order for fault %s, %s is directly after %s",
						parentID, minisectionID, prev.minisectionID);
				Preconditions.checkState(startLoc.equals(prev.endLoc) || LocationUtils.areSimilar(startLoc, prev.endLoc),
						"Previons endLoc does not match startLoc for %s %s:\n\t%s\n\t%s",
						parentID, minisectionID, prev.endLoc, startLoc);
			}
			
			// convert minisections to 0-based
			parentRecs.add(new GeodeticSlipRecord(
					parentID, minisectionID-1, startLoc, endLoc, rake, slipRate, slipRateStdDev));
		}
		
		return ret;
	}
	
	private static class GeodeticSlipRecord extends MinisectionDataRecord {
		public final double rake;
		public final double slipRate; // mm/yr
		public final double slipRateStdDev; // mm/yr
		
		public GeodeticSlipRecord(int parentID, int minisectionID, Location startLoc, Location endLoc, double rake,
				double slipRate, double slipRateStdDev) {
			super(parentID, minisectionID, startLoc, endLoc);
			this.rake = rake;
			this.slipRate = slipRate;
			this.slipRateStdDev = slipRateStdDev;
		}
	}
	
	/*
	 * Methods for processing loaded deformation models (standard deviations and creep)
	 */
	
	private static DecimalFormat pDF = new DecimalFormat("0.00%");
	
	/**
	 * Applies default/floor rules for slip rate standard deviations, updating their values in place and returning
	 * the supplied list
	 * 
	 * @param faultModel fault model, used to fetch geologic defaults if needed
	 * @param subSects subsection list
	 * @return subsection list that was supplied
	 * @throws IOException
	 */
	private List<? extends FaultSection> applyStdDevDefaults(RupSetFaultModel faultModel,
			List<? extends FaultSection> subSects) throws IOException {
		System.out.println("Checking slip rate standard deviations for "+name());
		
		Function<FaultSection, Double> fallbackStdDevFunc = null;
		if (!isHardcodedFractionalStdDev() && DEFAULT_STD_DEV_USE_GEOLOGIC) {
			fallbackStdDevFunc = new Function<FaultSection, Double>() {
				
				private Map<Integer, GeoJSONFaultSection> geoSects = null;
				
				@Override
				public synchronized Double apply(FaultSection sect) {
					// use geologic
					if (geoSects == null) {
						try {
							geoSects = getGeolFullSects(faultModel);
						} catch (IOException e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
					}
					FaultSection geoSect = geoSects.get(sect.getParentSectionId());
					Preconditions.checkNotNull(geoSect, "No geologic section found with parent=%s, name=%s",
							sect.getParentSectionId(), sect.getParentSectionName());
					return geoSect.getOrigSlipRateStdDev();
				}
			};
		}
		
		applyStdDevDefaults(subSects, fallbackStdDevFunc, "Geologic");
		
		return subSects;
	}
	
	public static boolean isHardcodedFractionalStdDev() {
		return HARDCODED_FRACTIONAL_STD_DEV > 0d;
	}
	
	public static void applyStdDevDefaults(List<? extends FaultSection> subSects) {
		applyStdDevDefaults(subSects, null, null);
	}
	
	public static final String ORIG_FRACT_STD_DEV_PROPERTY_NAME = "OrigFractStdDev";
	
	public static void applyStdDevDefaults(List<? extends FaultSection> subSects,
			Function<FaultSection, Double> fallbackStdDevFunc, String fallbackName) {
		if (isHardcodedFractionalStdDev())
			System.out.println("Overriding deformation model slip rates std devs and using hardcoded fractional value: "
					+HARDCODED_FRACTIONAL_STD_DEV);
		
		int numZeroSlips = 0;
		int numFallback = 0;
		int numFractDefault = 0;
		int numFloor = 0;
		
		for (FaultSection sect : subSects) {
			double slipRate = sect.getOrigAveSlipRate();
			Preconditions.checkState(Double.isFinite(slipRate) && slipRate >= 0d, "Bad slip rate for %s. %s: %s",
					sect.getSectionId(), sect.getSectionName(), slipRate);
			if ((float)slipRate == 0f)
				numZeroSlips++;
			
			if (sect instanceof GeoJSONFaultSection && slipRate > 0d) {
				double origStdDev = sect.getOrigSlipRateStdDev();
				((GeoJSONFaultSection)sect).setProperty(ORIG_FRACT_STD_DEV_PROPERTY_NAME, origStdDev/slipRate);
			}
			
			double stdDev;
			if (isHardcodedFractionalStdDev()) {
				stdDev = HARDCODED_FRACTIONAL_STD_DEV * slipRate;
			} else {
				// use value from the deformation model
				stdDev = sect.getOrigSlipRateStdDev();
				if (!Double.isFinite(stdDev))
					stdDev = 0d;
				
				if ((float)stdDev <= 0f) {
					// no slip rate std dev specified
					
					if (fallbackStdDevFunc != null) {
						// use fallback provider
						double fallback = fallbackStdDevFunc.apply(sect);
						if ((float)fallback > 0f) {
							stdDev = fallback;
							numFallback++;
						}
					}
					
					if ((float)stdDev <= 0f) {
						// didn't use fallback (DEFAULT_STD_DEV_USE_GEOLOGIC == false, or no geologic value)
						// set it to the given default fraction of the slip rate
						stdDev = slipRate*DEFAULT_FRACT_SLIP_STD_DEV;
						numFractDefault++;
					}
				}
			}
			
			// now apply any floor
			if ((float)stdDev < (float)STD_DEV_FLOOR) {
				stdDev = STD_DEV_FLOOR;
				numFloor++;
			}
			
			sect.setSlipRateStdDev(stdDev);
		}

		if (numZeroSlips > 0)
			System.err.println("WARNING: "+numZeroSlips+"/"+subSects.size()+" ("
					+pDF.format((double)numZeroSlips/(double)subSects.size())+") subsection slip rates are 0");
		if (numFallback > 0)
			System.err.println("WARNING: Set "+numFallback+"/"+subSects.size()+" ("
					+pDF.format((double)numFallback/(double)subSects.size())
					+") subsection slip rate standard deviations to fallback values ("+fallbackName+")");
		if (numFractDefault > 0)
			System.err.println("WARNING: Set "+numFractDefault+"/"+subSects.size()+" ("
					+pDF.format((double)numFractDefault/(double)subSects.size())
					+") subsection slip rate standard deviations to the default: "
					+(float)DEFAULT_FRACT_SLIP_STD_DEV+" x slipRate");
		if (numFloor > 0)
			System.err.println("WARNING: Set "+numFloor+"/"+subSects.size()+" ("
					+pDF.format((double)numFloor/(double)subSects.size())
					+") subsection slip rate standard deviations to the floor value of "+(float)STD_DEV_FLOOR+" (mm/yr)");
	}
	
	private List<? extends FaultSection> applyCreepModel(MinisectionMappings mappings,
			List<? extends FaultSection> subSects) throws IOException {
		String creepPath = NSHM23_DM_PATH_PREFIX+"creep/"+CREEP_DATE+"/"+name()+".csv";
		
		System.out.println("Applying creep model to "+name()+" from "+creepPath);
		
		Map<Integer, List<CreepRecord>> creepData = loadCreepData(creepPath, mappings);
		
		for (Integer parentID : new ArrayList<>(creepData.keySet())) {
			List<CreepRecord> records = creepData.get(parentID);
			
			// fill in any missing records with zeros
			int numMissing = 0;
			for (int i=0; i<records.size(); i++) {
				if (records.get(i) == null) {
					numMissing++;
					records.set(i, new CreepRecord(parentID, i, null, null, 0d));
				}
			}
			if (numMissing > 0)
				System.err.println("WARNING: "+name()+" is missing creep data for "+numMissing+"/"+records.size()
					+" minisections on "+parentID+", assuming creep rate is ["+(float)CREEP_FRACT_DEFAULT
					+" x slip_rate] on those minisections");
			
			// make sure it's valid
			if (!mappings.areMinisectionDataForParentValid(parentID, records, true)) {
				if (!mappings.hasParent(parentID))
					System.err.println("WARNING: "+name()+" removing creep data for unkown fault with ID="+parentID);
				else
					System.err.println("WARNING: "+name()+" does not contain valid creep data for fault "+parentID
							+", setting creep to default");
				// remove bad data
				creepData.remove(parentID);
			}
		}
		
		int numCreepDefault = 0;
		int numNonzeroData = 0;
		int numAboveCeiling = 0;
		MinMaxAveTracker creepFractTrack = new MinMaxAveTracker();
		MinMaxAveTracker aseisTrack = new MinMaxAveTracker();
		MinMaxAveTracker couplingTrack = new MinMaxAveTracker();
		
		for (int s=0; s<subSects.size(); s++) {
			FaultSection subSect = subSects.get(s);
			int parentID = subSect.getParentSectionId();
			
			double creepRate;
			if (creepData.containsKey(parentID)) {
				// we have creep data for this fault
				List<CreepRecord> records = creepData.get(parentID);
				List<Double> values = new ArrayList<>(records.size());
				boolean allDefault = true;
				for (CreepRecord record : records) {
					double recordVal;
					if (record == null) {
						// this fault has creep data, but this minisection does not. apply the default treatment
						recordVal = CREEP_FRACT_DEFAULT * subSect.getOrigAveSlipRate();
					} else {
						recordVal = record.creepRate;
						allDefault = false;
					}
					values.add(recordVal);
				}
				
				if (allDefault) {
					// this subsection only touches minisections without creep data
					creepRate = Double.NaN;
					numCreepDefault++;
				} else {
					creepRate = mappings.getAssociationScaledAverage(s, values);
					Preconditions.checkState(subSect instanceof GeoJSONFaultSection);
					((GeoJSONFaultSection)subSect).setProperty(GeoJSONFaultSection.CREEP_RATE, creepRate);
					if (creepRate > 0d)
						numNonzeroData++;
				}
			} else {
				creepRate = Double.NaN;
				numCreepDefault++;
			}
			
			applyCreepData(subSect, creepRate, creepFractTrack, aseisTrack, couplingTrack);
			
			if (subSect.getCouplingCoeff() < 1d)
				numAboveCeiling++;
		}
		
		System.out.println("Done applying creep data, stats:");
		System.out.println("\t"+numCreepDefault+"/"+subSects.size()
				+" ("+pDF.format((double)numCreepDefault/(double)subSects.size())
				+") subsections set to default creep reduction of "+(float)CREEP_FRACT_DEFAULT);
		System.out.println("\t"+numNonzeroData+"/"+subSects.size()
				+" ("+pDF.format((double)numNonzeroData/(double)subSects.size())
				+") subsections had supplied creep rates >0");
		System.out.println("\t"+numAboveCeiling+"/"+subSects.size()
				+" ("+pDF.format((double)numAboveCeiling/(double)subSects.size())
				+") subsections had creep rates above the aseismicity ceiling, >"+(float)ASEIS_CEILING);
		System.out.println("\tCreep reduction range: ["+(float)creepFractTrack.getMin()+","+(float)creepFractTrack.getMax()
			+"], avg="+(float)creepFractTrack.getAverage());
		System.out.println("\tAseismicity range: ["+(float)aseisTrack.getMin()+","+(float)aseisTrack.getMax()
			+"], avg="+(float)aseisTrack.getAverage());
		System.out.println("\tCoupling coefficient range: ["+(float)couplingTrack.getMin()+","+(float)couplingTrack.getMax()
			+"], avg="+(float)couplingTrack.getAverage());
		
		return subSects;
	}
	
	protected void applyCreepData(FaultSection subSect, double creepRate) {
		applyCreepData(subSect, creepRate, null, null, null);
	}
	
	private void applyCreepData(FaultSection subSect, double creepRate, MinMaxAveTracker creepFractTrack,
			MinMaxAveTracker aseisTrack, MinMaxAveTracker couplingTrack) {
		int s = subSect.getSectionId();
		String subSectName = subSect.getSectionName();
		
		double creepFract;
		if (Double.isFinite(creepRate)) {
			if (creepRate < 0d) {
				creepRate = 0d;
				System.err.println("WARNING: Setting negative creep rate ("+(float)creepRate+") to 0 for "
						+name()+", "+subSect.getSectionId()+". "+subSect.getName());
			}
			Preconditions.checkState(creepRate >= 0d, "Bad creep rate for %s. %s", s, subSectName);
			double slipRate = subSect.getOrigAveSlipRate();
			if (slipRate == 0d) {
				creepFract = 0d;
			} else {
				if (creepRate > slipRate) {
					System.err.println("WARNING: creep rate is greater than slip rate for section "
							+s+". "+subSectName+": "+(float)creepRate+" > "+(float)slipRate);
					creepRate = slipRate;
				}
				creepFract = creepRate/slipRate;
			}
		} else {
			// apply default creep
			creepFract = CREEP_FRACT_DEFAULT;
		}
		
		double aseis, coupling;
		if (creepFract < ASEIS_CEILING) {
			aseis = creepFract;
			coupling = 1d;
		} else {
			aseis = ASEIS_CEILING;
			coupling = 1 - (1/(1-ASEIS_CEILING))*(creepFract - ASEIS_CEILING);
		}
		
		Preconditions.checkState(aseis >= 0d && aseis <= ASEIS_CEILING,
				"Bad computed aseismicity value (%s) from creepFract=%s, aseisCeiling=%s",
				aseis, creepFract, ASEIS_CEILING);
		Preconditions.checkState(coupling >= 0d && coupling <= 1d,
				"Bad computed coupling coefficient (%s) from creepFract=%s, aseisCeiling=%s",
				coupling, creepFract, ASEIS_CEILING);
		
		if (creepFractTrack != null)
			creepFractTrack.addValue(creepFract);
		if (aseisTrack != null)
			aseisTrack.addValue(aseis);
		if (couplingTrack != null)
			couplingTrack.addValue(coupling);
		
		subSect.setAseismicSlipFactor(aseis);
		subSect.setCouplingCoeff(coupling);
	}
	
	private static Map<Integer, List<CreepRecord>> loadCreepData(String creepPath, MinisectionMappings mappings)
			throws IOException {
		CSVFile<String> csv;
		try {
			csv = CSVFile.readStream(NSHM23_DeformationModels.class.getResourceAsStream(creepPath), true);
		} catch (IOException e) {
			System.err.println("ERROR: couldn't load creep data from "+creepPath);
			throw e;
		}
		
		Map<Integer, List<CreepRecord>> ret = new HashMap<>();
		
		int numNegative = 0;
		
		for (int row=0; row<csv.getNumRows(); row++) {
			int parentID = csv.getInt(row, 0);
			int minisectionID = csv.getInt(row, 1);
			double creepRate = csv.getDouble(row, 2);
			Preconditions.checkState(Double.isFinite(creepRate), "Bad creepRate=%s for minisections=%s.%s",
					creepRate, parentID, minisectionID);
			if ((float)creepRate < 0f)
				numNegative++;
			
			// TODO get better data files with verification info
			Location startLoc = null;
			Location endLoc = null;

			List<CreepRecord> parentRecs = ret.get(parentID);
			int numMinisections = mappings.getNumMinisectionsForParent(parentID);
			if (parentRecs == null) {
				parentRecs = new ArrayList<>(numMinisections);
				for (int i=0; i<numMinisections; i++)
					parentRecs.add(null);
				ret.put(parentID, parentRecs);
			}
			Preconditions.checkState(minisectionID <= numMinisections,
					"Fault %s should have %s minisections, but encountered one with ID=%s",
					parentID, numMinisections, minisectionID);
			minisectionID--; // now 0-based
			parentRecs.set(minisectionID, new CreepRecord(parentID, minisectionID, startLoc, endLoc, creepRate));
		}
		if (numNegative > 0)
			System.err.println("WARNING: "+numNegative+" negative minisection creep values in "+creepPath);
		return ret;
	}
	
	private static class CreepRecord extends MinisectionDataRecord {
		public final double creepRate; // mm/yr
		
		public CreepRecord(int parentID, int minisectionID, Location startLoc, Location endLoc, double creepRate) {
			super(parentID, minisectionID, startLoc, endLoc);
			this.creepRate = creepRate;
		}
	}
	
	private static void checkForNegativeAndHighCreep(NSHM23_FaultModels fm) throws IOException {
		// check for creep data > slip rate or < 0
		Map<NSHM23_DeformationModels, Map<Integer, List<GeodeticSlipRecord>>> dmSlipRecs = new HashMap<>();
		Map<NSHM23_DeformationModels, Map<Integer, List<CreepRecord>>> dmCreepRecs = new HashMap<>();

		List<? extends FaultSection> geoSects = buildGeolFullSects(fm, GEOLOGIC_VERSION);
		List<FaultSection> subSects = GeoJSONFaultReader.buildSubSects(geoSects);
		MinisectionMappings mappings = new MinisectionMappings(geoSects, subSects);

		for (NSHM23_DeformationModels dm : values()) {
			if (dm.isApplicableTo(fm) && dm.weight > 0) {
				if (dm == GEOLOGIC) {
					Map<Integer, List<GeodeticSlipRecord>> fakeRecs = new HashMap<>();
					for (FaultSection sect : geoSects) {
						int parentID = sect.getSectionId();
						FaultTrace trace = sect.getFaultTrace();
						List<GeodeticSlipRecord> recs = new ArrayList<>(trace.size()-1);
						for (int i=1; i<trace.size(); i++) {
							Location loc1 = trace.get(i-1);
							Location loc2 = trace.get(i);
							recs.add(new GeodeticSlipRecord(parentID, i, loc1, loc2, sect.getAveRake(),
									sect.getOrigAveSlipRate(), sect.getOrigSlipRateStdDev()));
						}
						fakeRecs.put(parentID, recs);
					}
					dmSlipRecs.put(dm, fakeRecs);
				} else {
					String dmPath = NSHM23_DM_PATH_PREFIX+"geodetic/"+getGeodeticDirForFM(fm)+"/"+dm.name();
					if (GEODETIC_INCLUDE_GHOST_TRANSIENT)
						dmPath += "-include_ghost_corr.txt";
					else
						dmPath += "-no_ghost_corr.txt";
					dmSlipRecs.put(dm, loadGeodeticModel(dmPath));
				}
				String creepPath = NSHM23_DM_PATH_PREFIX+"creep/"+CREEP_DATE+"/"+dm.name()+".csv";
				dmCreepRecs.put(dm, loadCreepData(creepPath, mappings));
			}
		}

		Map<String, HashSet<NSHM23_DeformationModels>> creepBelowZeroes = new HashMap<>();
		Map<String, HashSet<NSHM23_DeformationModels>> creepAboveSlips = new HashMap<>();
		for (FaultSection sect : geoSects) {
			String name = sect.getSectionId()+". "+sect.getSectionName();

			for (NSHM23_DeformationModels dm : values()) {
				if (!dmSlipRecs.containsKey(dm))
					continue;
				List<CreepRecord> creepRecs = dmCreepRecs.get(dm).get(sect.getSectionId());
				if (creepRecs == null)
					continue;
				List<GeodeticSlipRecord> slipRecs = dmSlipRecs.get(dm).get(sect.getSectionId());
				Preconditions.checkState(creepRecs.size() == slipRecs.size());
				for (int i=0; i<slipRecs.size(); i++) {
					GeodeticSlipRecord slip = slipRecs.get(i);
					CreepRecord creep = creepRecs.get(i);
					if (creep == null)
						continue;
					if (creep.creepRate < 0d) {
						if (!creepBelowZeroes.containsKey(name))
							creepBelowZeroes.put(name, new HashSet<>());
						creepBelowZeroes.get(name).add(dm);
					}
					if ((float)creep.creepRate > (float)slip.slipRate) {
						if (!creepAboveSlips.containsKey(name))
							creepAboveSlips.put(name, new HashSet<>());
						creepAboveSlips.get(name).add(dm);
					}
				}
			}
		}
		if (!creepBelowZeroes.isEmpty()) {
			System.out.println("Faults with creep rates < 0:");
			for (String name : creepBelowZeroes.keySet()) {
				List<NSHM23_DeformationModels> dms = new ArrayList<>(creepBelowZeroes.get(name));
				Collections.sort(dms);
				System.out.print("\t"+name+":\t");
				for (int i=0; i<dms.size(); i++) {
					if (i > 0)
						System.out.print(", ");
					System.out.print(dms.get(i).name());
				}
				System.out.println();
			}
		}
		if (!creepAboveSlips.isEmpty()) {
			System.out.println("Faults with creep rates > slip rates:");
			for (String name : creepAboveSlips.keySet()) {
				List<NSHM23_DeformationModels> dms = new ArrayList<>(creepAboveSlips.get(name));
				Collections.sort(dms);
				System.out.print("\t"+name+":\t");
				for (int i=0; i<dms.size(); i++) {
					if (i > 0)
						System.out.print(", ");
					System.out.print(dms.get(i).name());
				}
				System.out.println();
			}
		}
	}
	
	private static void writeMedianDM(File outputDir) throws IOException {
		NSHM23_FaultModels fm = NSHM23_FaultModels.NSHM23_v2;
		
		// first load geologic
		List<? extends FaultSection> geoSects = buildGeolFullSects(fm, GEOLOGIC_VERSION);
		geoSects = new ArrayList<>(geoSects);
		geoSects.sort(new Comparator<FaultSection>() {

			@Override
			public int compare(FaultSection o1, FaultSection o2) {
				return Integer.compare(o1.getSectionId(), o2.getSectionId());
			}
		});
		
		Map<NSHM23_DeformationModels, Map<Integer, List<GeodeticSlipRecord>>> geodeticRecords = new HashMap<>();
		Map<NSHM23_DeformationModels, Map<Integer, List<CreepRecord>>> creepRecords = new HashMap<>();
		
		NSHM23_DeformationModels[] models = { GEOLOGIC, EVANS, POLLITZ, SHEN_BIRD, ZENG };
		
		MinisectionMappings mappings = new MinisectionMappings(geoSects, GEOLOGIC.build(fm));
		
		for (NSHM23_DeformationModels model : models) {
			if (model != GEOLOGIC) {
				String fmDirName = getGeodeticDirForFM(fm);
				String dmPath = NSHM23_DM_PATH_PREFIX+"geodetic/"+fmDirName+"/"+model.name()+"-include_ghost_corr.txt";
				geodeticRecords.put(model, loadGeodeticModel(dmPath));
			}
			String creepPath = NSHM23_DM_PATH_PREFIX+"creep/"+CREEP_DATE+"/"+model.name()+".csv";
			creepRecords.put(model, loadCreepData(creepPath, mappings));
		}
		
		FileWriter dmFW = new FileWriter(new File(outputDir, "MEDIAN-include_ghost_corr.txt"));
		dmFW.write("#FaultID	MinisectionID	StartLat	StartLon	EndLat	EndLon	Rake	SlipRate(mm/y)	StdDev\n");
		
		CSVFile<String> creepCSV = new CSVFile<>(true);
		for (int i=0; i<geoSects.size(); i++) {
			FaultSection sect = geoSects.get(i);
			int sectID = sect.getSectionId();
			
			double geoSlip = sect.getOrigAveSlipRate();
			double geoSD = sect.getOrigSlipRateStdDev();
			double geoRake = sect.getAveRake();
			
			FaultTrace trace = sect.getFaultTrace();
			
			for (int mini=0; mini<mappings.getNumMinisectionsForParent(sect.getSectionId()); mini++) {
				String line = sect.getSectionId()+"\t"+(mini+1);
				Location p1 = trace.get(mini);
				Location p2 = trace.get(mini+1);
				line += "\t"+(float)p1.getLatitude()+"\t"+(float)p1.getLongitude();
				line += "\t"+(float)p2.getLatitude()+"\t"+(float)p2.getLongitude();
				line += "\t"+(float)geoRake;
				
				// median slip rate
				double[] slips = new double[models.length];
				double[] creepFracts = new double[slips.length];
				boolean hasCreep = false;
				for (int m=0; m<models.length; m++) {
					if (models[m] == GEOLOGIC)
						slips[m] = geoSlip;
					else
						slips[m] = geodeticRecords.get(models[m]).get(sectID).get(mini).slipRate;
					List<CreepRecord> sectCreeps = creepRecords.get(models[m]).get(sectID);
					if (sectCreeps != null) {
						CreepRecord miniCreep = sectCreeps.get(mini);
						if (miniCreep != null) {
							hasCreep = true;
							creepFracts[m] = Math.min(1d, miniCreep.creepRate/slips[m]);
						}
					}
				}
				
				double medianSlip = DataUtils.median(slips);
				line += "\t"+(float)medianSlip;
				line += "\t"+(float)geoSD;
				
				if (hasCreep) {
					double medianCreepFract = DataUtils.median(creepFracts);
					creepCSV.addLine(sectID+"", (mini+1)+"", (float)(medianCreepFract*medianSlip)+"");
				}
				dmFW.write(line+"\n");
			}
		}
		
		dmFW.close();
		creepCSV.writeToFile(new File(outputDir, "MEDIAN.csv"));
	}
	
	private static void writeMinisectionCSV(File outputFile) throws IOException {
		NSHM23_FaultModels fm = NSHM23_FaultModels.NSHM23_v2;
		
		// first load geologic
		List<? extends FaultSection> geoSects = buildGeolFullSects(fm, GEOLOGIC_VERSION);
		geoSects = new ArrayList<>(geoSects);
		geoSects.sort(new Comparator<FaultSection>() {

			@Override
			public int compare(FaultSection o1, FaultSection o2) {
				return Integer.compare(o1.getSectionId(), o2.getSectionId());
			}
		});
		
		NSHM23_DeformationModels[] geodeticModels = {
				NSHM23_DeformationModels.EVANS,
				NSHM23_DeformationModels.POLLITZ,
				NSHM23_DeformationModels.SHEN_BIRD,
				NSHM23_DeformationModels.ZENG,
				NSHM23_DeformationModels.MEDIAN
		};
		
		CSVFile<String> csv = new CSVFile<>(true);
		
		List<String> header = new ArrayList<>(List.of("Section ID", "Section Name", "Minisection ID",
				"Start Lat", "Start Lon", "End Lat", "End Lon", "Geologic Slip Rate (mm/yr)",
				"Geologic Lower Bound (mm/yr)", "Geologic Upper Bound (mm/yr)"));
		for (NSHM23_DeformationModels dm : geodeticModels)
			header.add(dm.getShortName()+" (mm/yr)");
		csv.addLine(header);
		
		Map<NSHM23_DeformationModels, Map<Integer, List<GeodeticSlipRecord>>> geodeticRecords = new HashMap<>();
		
		MinisectionMappings mappings = new MinisectionMappings(geoSects, GEOLOGIC.build(fm));
		
		for (NSHM23_DeformationModels model : geodeticModels) {
			String fmDirName = getGeodeticDirForFM(fm);
			String dmPath = NSHM23_DM_PATH_PREFIX+"geodetic/"+fmDirName+"/"+model.name()+"-include_ghost_corr.txt";
			geodeticRecords.put(model, loadGeodeticModel(dmPath));
		}
		
		for (FaultSection sect : geoSects) {
			GeoJSONFaultSection geoSect = (GeoJSONFaultSection)sect;
			FaultTrace trace = sect.getFaultTrace();
			int numMinis = trace.size()-1;
			
			for (int i=0; i<numMinis; i++) {
				List<String> line = new ArrayList<>(header.size());
				
				line.add(sect.getSectionId()+"");
				line.add(sect.getSectionName());
				line.add((i+1)+"");
				Location l1 = trace.get(i);
				Location l2 = trace.get(i+1);
				line.add((float)l1.getLatitude()+"");
				line.add((float)l1.getLongitude()+"");
				line.add((float)l2.getLatitude()+"");
				line.add((float)l2.getLongitude()+"");
				line.add((float)sect.getOrigAveSlipRate()+"");
				line.add(geoSect.getProperty("LowRate", Double.NaN).floatValue()+"");
				line.add(geoSect.getProperty("HighRate", Double.NaN).floatValue()+"");
				for (NSHM23_DeformationModels dm : geodeticModels) {
					List<GeodeticSlipRecord> recs = geodeticRecords.get(dm).get(sect.getSectionId());
					line.add((float)recs.get(i).slipRate+"");
				}
				
				csv.addLine(line);
			}
		}
		
		csv.writeToFile(outputFile);
	}
	
	public static void main(String[] args) throws IOException {
		// write geo gson
////		NSHM23_FaultModels fm = NSHM23_FaultModels.NSHM23_v1p4;
//		NSHM23_FaultModels fm = NSHM23_FaultModels.NSHM23_v2;
//		
////		List<? extends FaultSection> geoFull = buildGeolFullSects(fm, GEOLOGIC_VERSION);
////		GeoJSONFaultReader.writeFaultSections(new File("/tmp/"+NSHM23_DeformationModels.GEOLOGIC.getFilePrefix()+"_sects.geojson"), geoFull);
//		
//		for (NSHM23_DeformationModels dm : values()) {
//			if (dm.isApplicableTo(fm)) {
//				System.out.println("************************");
//				System.out.println("Building "+dm.name);
//				List<? extends FaultSection> subSects = dm.build(fm);
//				GeoJSONFaultReader.writeFaultSections(new File("/tmp/"+dm.getFilePrefix()+"_sub_sects.geojson"), subSects);
//				System.err.flush();
//				System.out.flush();
//				System.out.println("************************");
//			}
//		}
//		
//		checkForNegativeAndHighCreep(fm);
		
//		// write median dm
//		writeMedianDM(new File("/tmp"));
		
		writeMinisectionCSV(new File("/tmp/nshm23_def_model_minisections.csv"));
	}

}
