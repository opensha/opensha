package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

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
import java.util.stream.Collectors;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM23_DeformationModels implements RupSetDeformationModel {
	GEOLOGIC("NSHM23 Geologic Deformation Model v1.4", "Geologic V1.4", 1d) {
		@Override
		public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
			return buildGeol(faultModel, "v1p4");
		}
	},
	EVANS("NSHM23 Evans Deformation Model", "Evans", 1d) {
		@Override
		public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
			return buildGeodetic(faultModel, "Evans_section_slip_rates-include_ghost_correction_suite.txt");
		}
	},
	POLLITZ("NSHM23 Pollitz Deformation Model", "Pollitz", 1d) {
		@Override
		public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
			return buildGeodetic(faultModel, "Pollitz_section_slip_rates-no_ghost_correction.txt");
		}
	},
	SHEN_BIRD("NSHM23 Shen-Bird Deformation Model", "Shen-Bird", 1d) {
		@Override
		public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
			return buildGeodetic(faultModel, "Shen-Bird_section_slip_rates-include_ghost_correction.txt");
		}
	},
	ZENG("NSHM23 Zeng Deformation Model", "Zeng", 1d) {
		@Override
		public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
			return buildGeodetic(faultModel, "Zeng_section_slip_rates-include_ghost_correction.txt");
		}
	};
	
	private static final String NSHM23_DM_PATH_PREFIX = "/data/erf/nshm23/def_models/";
	
	private static final String GEODETIC_DATE = "2022_06_27";

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

	public List<? extends FaultSection> buildGeolFullSects(RupSetFaultModel faultModel, String version) throws IOException {
		Preconditions.checkState(isApplicableTo(faultModel), "DM/FM mismatch");
		String dmPath = NSHM23_DM_PATH_PREFIX+"geologic/"+version+"/NSHM23_GeolDefMod_"+version+".geojson";
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
		
		return geoSects;
	}

	public List<? extends FaultSection> buildGeol(RupSetFaultModel faultModel, String version) throws IOException {
		List<? extends FaultSection> geoSects = buildGeolFullSects(faultModel, version);
		
		return GeoJSONFaultReader.buildSubSects(geoSects);
	}

//	private static final double GEODETIC_LOC_WARN_TOL = 0.1;
//	private static final double GEODETIC_LOC_ERR_TOL = 1;
	private static final double GEODETIC_LOC_WARN_TOL = 0.1;
	private static final double GEODETIC_LOC_ERR_TOL = 100;
	
	public List<? extends FaultSection> buildGeodetic(RupSetFaultModel faultModel, String resourceName) throws IOException {
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
		
		String dmPath = NSHM23_DM_PATH_PREFIX+"geodetic/"+GEODETIC_DATE+"/"+resourceName;
		Map<Integer, List<GeodeticSlipRecord>> dmRecords = loadGeodeticModel(dmPath);
		
		// will load geologic slip rates if needed
		List<? extends FaultSection> geoSubSects = null;
		
		HashSet<Integer> warnedParent = new HashSet<>();
		// replace slip rates and rakes from deformation model
		for (FaultSection subSect : subSects) {
			int parentID = subSect.getParentSectionId();
			FaultSection parentSect = sectsByID.get(parentID);
			String name = parentSect.getSectionName();
			List<GeodeticSlipRecord> records = dmRecords.get(parentID);
			if (records == null) {
				if (!warnedParent.contains(parentID)) {
					System.err.println("WARNING: "+name()+" does not contain data for fault "+parentID+". "
							+name+", setting slip rate to 0.");
					warnedParent.add(parentID);
				}
				subSect.setAveSlipRate(0d);
				subSect.setSlipRateStdDev(0d);
				continue;
			}
			
			// subsection tract
			FaultTrace subTrace = subSect.getFaultTrace();
			Preconditions.checkState(subTrace.size()>1, "sub section trace only has one point!!!!");
			Location subStart = subTrace.get(0);
			Location subEnd = subTrace.get(subTrace.size()-1);
			
			FaultTrace trace = parentSect.getFaultTrace();
			
			if (trace.size() != records.size()+1) {
				// TODO remove this temporary continue
				System.err.println("WARNING: Trace size/minisection count mismatch for "+name()+", fault "+parentID
						+". "+name+". Have "+trace.size()+" trace points, "+records.size()+" DM minisections");
				subSect.setAveSlipRate(0d);
				subSect.setSlipRateStdDev(0d);
				continue;
			}
			Preconditions.checkState(trace.size() == records.size()+1,
					"Trace size/minisection count mismatch for %s, %s. %s. Have %s trace points, %s DM records",
					name(), parentID, name, trace.size(), records.size());
			
			// this is the index of the trace point that is either before or equal to the start of the sub section
			int traceIndexBefore = -1;
			// this is the index of the trace point that is either after or exactly at the end of the sub section
			int traceIndexAfter = -1;

			// now see if there are any trace points in between the start and end of the sub section
			for (int i=0; i<trace.size(); i++) {
				// loop over section trace. we leave out the first and last point because those are
				// by definition end points and are already equal to sub section start/end points
				Location tracePt = trace.get(i);

				if (isBefore(subStart, subEnd, tracePt)) {
//					if (DD) System.out.println("Trace "+i+" is BEFORE");
					traceIndexBefore = i;
				} else if (isAfter(subStart, subEnd, tracePt)) {
					// we want just the first index after, so we break
//					if (DD) System.out.println("Trace "+i+" is AFTER");
					traceIndexAfter = i;
					break;
				} else {
//					if (DD) System.out.println("Trace "+i+" must be BETWEEN");
				}
			}
			Preconditions.checkState(traceIndexBefore >= 0, "trace index before not found!");
			Preconditions.checkState(traceIndexAfter > traceIndexBefore, "trace index after not found!");

			// this is the list of locations on the sub section, including any trace points in between
			List<Location> subLocs = new ArrayList<Location>();
			// this is the slip of all spans of the locations above
			List<Double> subSlips = new ArrayList<Double>();
			// this is the slip rate standard deviations of all spans of the locations above
			List<Double> subSlipStdDevs = new ArrayList<Double>();
			// this is the rake of all spans of the locations above
			List<Double> subRakes = new ArrayList<Double>();

			subLocs.add(subStart);

			for (int i=traceIndexBefore; i<traceIndexAfter; i++) {
				GeodeticSlipRecord rec = records.get(i);
				Location traceLoc1 = trace.get(i);
				Location traceLoc2 = trace.get(i+1);
				if (!traceLoc1.equals(rec.startLoc) && !LocationUtils.areSimilar(traceLoc1, rec.startLoc)
						|| !traceLoc2.equals(rec.endLoc) && !LocationUtils.areSimilar(traceLoc2, rec.endLoc)) {
					double dist1 = LocationUtils.horzDistanceFast(traceLoc1, rec.startLoc);
					double dist2 = LocationUtils.horzDistanceFast(traceLoc2, rec.endLoc);
					if (dist1 > GEODETIC_LOC_WARN_TOL || dist2 > GEODETIC_LOC_WARN_TOL) {
						String str = "Trace/minisection location mismatch for "+name()+", fault "+parentID+". "+name
								+", minisection "+rec.minisectionID+":";
						str += "\n\tStart loc: ["+(float)traceLoc1.getLatitude()+", "+(float)traceLoc1.getLongitude()
							+"] vs ["+(float)rec.startLoc.getLatitude()+", "+(float)rec.startLoc.getLongitude()
							+"], dist="+(float)dist1+" km";
						str += "\n\tEnd loc: ["+(float)traceLoc2.getLatitude()+", "+(float)traceLoc2.getLongitude()
							+"] vs ["+(float)rec.endLoc.getLatitude()+", "+(float)rec.endLoc.getLongitude()
							+"], dist="+(float)dist2+" km";
						if (dist1 > GEODETIC_LOC_ERR_TOL || dist2 > GEODETIC_LOC_ERR_TOL)
							throw new IllegalStateException(str);
						else
							System.err.println("WARNING: "+str);
					}
				}
				Preconditions.checkState(rec.minisectionID == i+1);
				subSlips.add(rec.slipRate);
				if (Double.isNaN(rec.slipRateStdDev))
					subSlipStdDevs = null;
				else 
					subSlipStdDevs.add(rec.slipRateStdDev);
				subRakes.add(rec.rake);
				if (i > traceIndexBefore && i < traceIndexAfter)
					subLocs.add(trace.get(i));
			}
			subLocs.add(subEnd);

			// these are length averaged
			double avgSlip = getLengthBasedAverage(subLocs, subSlips);
			Preconditions.checkState(Double.isFinite(avgSlip) && avgSlip >= 0d,
					"Bad slip rate for subSect=%, parentID=%: %s",
					subSect.getSectionId(), parentID, avgSlip);
			double avgSlipStdDev;
			if (subSlipStdDevs == null) {
				if (geoSubSects == null) {
					System.err.println("WARNING: "+name()+" doesn't have slip rate std. devs. for at least 1 fault, loading geologic...");
					geoSubSects = GEOLOGIC.build(faultModel);
					Preconditions.checkState(geoSubSects.size() == subSects.size());
					System.err.println("\tdone loading geologic.");
				}
				avgSlipStdDev = geoSubSects.get(subSect.getSectionId()).getOrigSlipRateStdDev();
			} else {
				avgSlipStdDev = getLengthBasedAverage(subLocs, subSlipStdDevs);
			}
			Preconditions.checkState(Double.isFinite(avgSlipStdDev),
					"Bad slip rate standard deviation for subSect=%, parentID=%: %s",
					subSect.getSectionId(), parentID, avgSlipStdDev);
			double avgRake = FaultUtils.getLengthBasedAngleAverage(subLocs, subRakes);
			if (avgRake > 180)
				avgRake -= 360;
			Preconditions.checkState(Double.isFinite(avgRake), "Bad rake for subSect=%, parentID=%: %s",
					subSect.getSectionId(), parentID, avgRake);
			
			if (avgSlipStdDev == 0d && avgSlip > 0d)
				System.err.println("WARNING: slipRateStdDev=0 for "+subSect.getSectionId()
					+". "+subSect.getSectionName()+", with slipRate="+avgSlip);

			subSect.setAveSlipRate(avgSlip);
			subSect.setSlipRateStdDev(avgSlipStdDev);
			subSect.setAveRake(avgRake);
		}
		
		return subSects;
	}
	
	private static Map<Integer, List<GeodeticSlipRecord>> loadGeodeticModel(String path) throws IOException {
		BufferedReader dmReader = new BufferedReader(new InputStreamReader(
				NSHM23_DeformationModels.class.getResourceAsStream(path)));
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
				Preconditions.checkState(minisectionID == prev.minisectionID+1,
						"Minisections are out of order for fault %s, %s is directly after %s",
						parentID, minisectionID, prev.minisectionID);
				Preconditions.checkState(startLoc.equals(prev.endLoc) || LocationUtils.areSimilar(startLoc, prev.endLoc),
						"Previons endLoc does not match startLoc for %s:\n\t%s\n\t%s",
						parentID, prev.endLoc, startLoc);
			}
			
			parentRecs.add(new GeodeticSlipRecord(
					parentID, minisectionID, startLoc, endLoc, rake, slipRate, slipRateStdDev));
		}
		
		return ret;
	}
	
	/**
	 * Determines if the given point, pt, is before or equal to the start point. This is
	 * done by determining that pt is closer to start than end, and is further from end
	 * than start is.
	 * 
	 * @param start
	 * @param end
	 * @param pt
	 * @return
	 */
	private static boolean isBefore(Location start, Location end, Location pt) {
		if (start.equals(pt) || LocationUtils.areSimilar(start, pt))
			return true;
		double pt_start_dist = LocationUtils.horzDistanceFast(pt, start);
		if (pt_start_dist == 0)
			return true;
		double pt_end_dist = LocationUtils.horzDistanceFast(pt, end);
		double start_end_dist = LocationUtils.horzDistanceFast(start, end);

		return pt_start_dist < pt_end_dist && pt_end_dist > start_end_dist;
	}

	/**
	 * Determines if the given point, pt, is after or equal to the end point. This is
	 * done by determining that pt is closer to end than start, and is further from start
	 * than end is.
	 * 
	 * @param start
	 * @param end
	 * @param pt
	 * @return
	 */
	private static boolean isAfter(Location start, Location end, Location pt) {
		if (end.equals(pt) || LocationUtils.areSimilar(end, pt))
			return true;
		double pt_end_dist = LocationUtils.horzDistanceFast(pt, end);
		if (pt_end_dist == 0)
			return true;
		double pt_start_dist = LocationUtils.horzDistanceFast(pt, start);
		double start_end_dist = LocationUtils.horzDistanceFast(start, end);

		return pt_end_dist < pt_start_dist && pt_start_dist > start_end_dist;
	}
	
	/**
	 * This averages multiple scalars based on the length of each corresponding span.
	 * 
	 * @param locs a list of locations
	 * @param scalars a list of scalar values to be averaged based on the distance between
	 * each location in the location list
	 */
	public static double getLengthBasedAverage(List<Location> locs, List<Double> scalars) {
		Preconditions.checkArgument(locs.size() == scalars.size()+1,
				"there must be exactly one less slip than location!");
		Preconditions.checkArgument(!scalars.isEmpty(), "there must be at least 2 locations and 1 slip rate");

		if (scalars.size() == 1)
			return scalars.get(0);
		if (Double.isNaN(scalars.get(0)))
			return Double.NaN;
		boolean equal = true;
		for (int i=1; i<scalars.size(); i++) {
			if (Double.isNaN(scalars.get(i)))
				return Double.NaN;
			if (scalars.get(i).floatValue() != scalars.get(0).floatValue()) {
				equal = false;
				break;
			}
		}
		if (equal)
			return scalars.get(0);

		List<Double> dists = new ArrayList<Double>();

		for (int i=1; i<locs.size(); i++)
			dists.add(LocationUtils.linearDistanceFast(locs.get(i-1), locs.get(i)));
		
		return calcLengthBasedAverage(dists, scalars);
	}
	
	public static double calcLengthBasedAverage(List<Double> lengths, List<Double> scalars) {
		double totDist = 0d;
		for (double len : lengths)
			totDist += len;
		
		double scaledAvg = 0;
		for (int i=0; i<lengths.size(); i++) {
			double relative = lengths.get(i) / totDist;
			scaledAvg += relative * scalars.get(i);
		}

		return scaledAvg;
	}
	
	private static class GeodeticSlipRecord {
		private final int parentID;
		private final int minisectionID;
		private final Location startLoc;
		private final Location endLoc;
		private final double rake;
		private final double slipRate; // mm/yr
		private final double slipRateStdDev; // mm/yr
		
		public GeodeticSlipRecord(int parentID, int minisectionID, Location startLoc, Location endLoc, double rake,
				double slipRate, double slipRateStdDev) {
			super();
			this.parentID = parentID;
			this.minisectionID = minisectionID;
			this.startLoc = startLoc;
			this.endLoc = endLoc;
			this.rake = rake;
			this.slipRate = slipRate;
			this.slipRateStdDev = slipRateStdDev;
		}
	}
	
	public static void main(String[] args) throws IOException {
		// write geo gson
		NSHM23_FaultModels fm = NSHM23_FaultModels.NSHM23_v1p4;
		
		List<? extends FaultSection> geoFull = NSHM23_DeformationModels.GEOLOGIC.buildGeolFullSects(fm, "v1p4");
		GeoJSONFaultReader.writeFaultSections(new File("/tmp/"+NSHM23_DeformationModels.GEOLOGIC.getFilePrefix()+"_sects.geojson"), geoFull);
		
		for (NSHM23_DeformationModels dm : values()) {
			if (dm.weight > 0d && dm.isApplicableTo(fm)) {
				System.out.println("Building "+dm.name);
				List<? extends FaultSection> subSects = dm.build(fm);
				GeoJSONFaultReader.writeFaultSections(new File("/tmp/"+dm.getFilePrefix()+"_sub_sects.geojson"), subSects);
			}
		}
	}

}
