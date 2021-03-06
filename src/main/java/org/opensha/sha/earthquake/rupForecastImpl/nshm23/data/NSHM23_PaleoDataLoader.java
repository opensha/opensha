package org.opensha.sha.earthquake.rupForecastImpl.nshm23.data;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.uncertainty.BoundedUncertainty;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.utils.aveSlip.U3AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;

public class NSHM23_PaleoDataLoader {
	
	public static PaleoseismicConstraintData load(FaultSystemRupSet rupSet) throws IOException {
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		// paleo event rate
		List<SectMappedUncertainDataConstraint> paleoRateConstraints = loadCAPaleoRateData(subSects);
		// TODO add Wasatch
		UCERF3_PaleoProbabilityModel paleoProbModel = UCERF3_PaleoProbabilityModel.load();

		// paleo slip
		List<SectMappedUncertainDataConstraint> aveSlipConstraints = loadU3PaleoSlipData(subSects);
		PaleoSlipProbabilityModel paleoSlipProbModel = U3AveSlipConstraint.slip_prob_model;

		return new PaleoseismicConstraintData(rupSet, paleoRateConstraints, paleoProbModel,
				aveSlipConstraints, paleoSlipProbModel);
	}

	// ensure that mappings are within this distance in km
	private static final String NSHM23_PALEO_RI_PATH_PREFIX = "/data/erf/nshm23/constraints/paleo_ri/";
	private static final String CA_PALEO_PATH = NSHM23_PALEO_RI_PATH_PREFIX+"McPhillips_California_RIs_2022_07_13.csv";
	
	private static final String NSHM23_PALEO_SLIP_PATH_PREFIX = "/data/erf/nshm23/constraints/paleo_slip/";
	private static final String U3_PALEO_SLIP_PATH_1 = NSHM23_PALEO_SLIP_PATH_PREFIX+"Table_R5v4.csv";
	private static final String U3_PALEO_SLIP_PATH_2 = NSHM23_PALEO_SLIP_PATH_PREFIX+"Table_R6v5.csv";
	
	public static List<SectMappedUncertainDataConstraint> loadCAPaleoRateData(List<? extends FaultSection> subSects)
			throws IOException {
		CSVFile<String> csv = CSVFile.readStream(NSHM23_PaleoDataLoader.class.getResourceAsStream(CA_PALEO_PATH), false);
		
		List<SectMappedUncertainDataConstraint> ret = new ArrayList<>();
		
		for (int row=2; row<csv.getNumRows(); row++) {
			boolean isData = true;
			List<String> line = csv.getLine(row);
			if (line.size() < 9)
				continue;
			for (int col=0; col<9; col++) {
				if (line.get(col).isBlank()) {
					isData = false;
					break;
				}
			}
			if (!isData)
				continue;
			int col = 0;
			String name = csv.get(row, col++);
			double lat = csv.getDouble(row, col++);
			double lon = csv.getDouble(row, col++);
			col++; // Number of potential earthquakes
			col++; // conventional MRI
			col++; // conventional CI
			double ri = csv.getDouble(row, col++);
			String ciStr = csv.get(row, col++);
			
			double rate = 1d/ri;
			// split of csStr in the form lower-upper
			Preconditions.checkState(ciStr.contains("-"));
			String[] ciSplit = ciStr.split("-");
			Preconditions.checkState(ciSplit.length == 2);
			double lowerRI = Double.parseDouble(ciSplit[0]);
			double upperRI = Double.parseDouble(ciSplit[1]);
			// these are 95% RIs, convert to rates
			// upper RI is lower rate
			double lowerRate = 1d/upperRI;
			double upperRate = 1d/lowerRI;
			
			Location loc = new Location(lat, lon);
			// now find mapping
			
			FaultSection mappedSect = null;
			if (subSects != null) {
				mappedSect = findMatchingSect(loc, subSects,
						LOC_MAX_DIST_NONE_CONTAINED, LOC_MAX_DIST_OTHER_CONTAINED, LOC_MAX_DIST_CONTAINED);
				if (mappedSect == null) {
					System.err.println("WARNING: no matching fault section found for paleo site "+name+" at "+lat+", "+lon);
					continue;
				}
			}
			BoundedUncertainty uncert = BoundedUncertainty.fromMeanAndBounds(
					UncertaintyBoundType.CONF_95, rate, lowerRate, upperRate);
			if (mappedSect == null)
				ret.add(new SectMappedUncertainDataConstraint(name, -1, null, loc, rate, uncert));
			else
				ret.add(new SectMappedUncertainDataConstraint(name, mappedSect.getSectionId(), mappedSect.getSectionName(),
						loc, rate, uncert));
		}
		
		return ret;
	}
	
	public static List<SectMappedUncertainDataConstraint> loadU3PaleoSlipData(List<? extends FaultSection> subSects)
			throws IOException {
		List<CSVFile<String>> csvs = new ArrayList<>();
		csvs.add(CSVFile.readStream(NSHM23_PaleoDataLoader.class.getResourceAsStream(U3_PALEO_SLIP_PATH_1), false));
		csvs.add(CSVFile.readStream(NSHM23_PaleoDataLoader.class.getResourceAsStream(U3_PALEO_SLIP_PATH_2), false));
		
		List<SectMappedUncertainDataConstraint> ret = new ArrayList<>();
		
		for (CSVFile<String> csv : csvs) {
			for (int row=3; row<csv.getNumRows(); row++) {
				boolean isData = true;
				List<String> line = csv.getLine(row);
				if (line.size() < 24)
					continue;
				for (int col=0; col<3; col++) {
					if (line.get(col).isBlank()) {
						isData = false;
						break;
					}
				}
				if (line.get(21).isBlank() || line.get(21).isBlank() || line.get(21).isBlank())
					isData = false;
				if (!isData)
					continue;
				String name = csv.get(row, 0).trim();
				if (Character.isDigit(name.charAt(name.length()-1)) && name.length() > 2
						&& !Character.isDigit(name.charAt(name.length()-2)))
					// strip out trailing number (was a superscript annotation)
					name = name.substring(0, name.length()-1);
				double lat = csv.getDouble(row, 1);
				double lon = csv.getDouble(row, 2);
				double aveSlip = csv.getDouble(row, 21);
				double upper = aveSlip + csv.getDouble(row, 22);
				double lower = aveSlip - csv.getDouble(row, 23);
				
				Location loc = new Location(lat, lon);
				// now find mapping
				
				FaultSection mappedSect = null;
				if (subSects != null) {
					mappedSect = findMatchingSect(loc, subSects,
							LOC_MAX_DIST_NONE_CONTAINED, LOC_MAX_DIST_OTHER_CONTAINED, LOC_MAX_DIST_CONTAINED);
					if (mappedSect == null) {
						System.err.println("WARNING: no matching fault section found for paleo site "+name+" at "+lat+", "+lon);
						continue;
					}
				}
				BoundedUncertainty uncert = BoundedUncertainty.fromMeanAndBounds(
						UncertaintyBoundType.TWO_SIGMA, aveSlip, lower, upper);
				if (mappedSect == null)
					ret.add(new SectMappedUncertainDataConstraint(name, -1, null, loc, aveSlip, uncert));
				else
					ret.add(new SectMappedUncertainDataConstraint(name, mappedSect.getSectionId(), mappedSect.getSectionName(),
							loc, aveSlip, uncert));
			}
		}
		
		return ret;
	}
	
	// filter sections to actually check with cartesian distances in KM
	private static final double LOC_CHECK_DEGREE_TOLERANCE = 3d;
	private static final double LOC_CHECK_DEGREE_TOLERANCE_SQ = LOC_CHECK_DEGREE_TOLERANCE*LOC_CHECK_DEGREE_TOLERANCE;
	
	private static final double LOC_MAX_DIST_NONE_CONTAINED = 40d; // large for offshore noyo site
	private static final double LOC_MAX_DIST_OTHER_CONTAINED = 1d; // small, must be really close to override a fault that contains it
	private static final double LOC_MAX_DIST_CONTAINED = 20d; // allows comptom mapping
	
	/**
	 * 
	 * @param loc
	 * @param subSects
	 * @param maxDistNoneContained maximum distance to search if site not contained in the surface project of any fault
	 * @param maxDistOtherContained maximum distance to search if site is contained by a fault, but another trace is closer
	 * @param maxDistContained maximum distance to search to the trace of a fault whose surface projection contains this site
	 * @return
	 */
	private static FaultSection findMatchingSect(Location loc, List<? extends FaultSection> subSects,
			double maxDistNoneContained, double maxDistOtherContained, double maxDistContained) {
		List<FaultSection> candidates = new ArrayList<>();
		List<FaultSection> containsCandidates = new ArrayList<>();
		
		Map<FaultSection, Double> candidateDists = new HashMap<>();
		
		for (FaultSection sect : subSects) {
			// first check cartesian distance
			boolean candidate = false;
			for (Location traceLoc : sect.getFaultTrace()) {
				double latDiff = loc.getLatitude() - traceLoc.getLatitude();
				double lonDiff = loc.getLongitude() - traceLoc.getLongitude();
				if (latDiff*latDiff + lonDiff*lonDiff < LOC_CHECK_DEGREE_TOLERANCE_SQ) {
					candidate = true;
					break;
				}
			}
			if (candidate) {
				candidates.add(sect);
				double dist = sect.getFaultTrace().minDistToLine(loc);
				candidateDists.put(sect, dist);
				// see if this fault contains it
				if (sect.getAveDip() < 89d) {
					LocationList perim = new LocationList();
					perim.addAll(sect.getFaultSurface(1d).getPerimeter());
					if (!perim.last().equals(perim.first()))
						perim.add(perim.first());
					Region region = new Region(perim, BorderType.GREAT_CIRCLE);
					if (region.contains(loc))
						containsCandidates.add(sect);
				}
			}
		}
		
		// find the closest of any candidtae section
		FaultSection closest = null;
		double closestDist = Double.POSITIVE_INFINITY;
		for (FaultSection sect : candidates) {
			double dist = candidateDists.get(sect);
			if (dist < closestDist) {
				closestDist = dist;
				closest = sect;
			}
		}
		
		if (containsCandidates.isEmpty()) {
			// this site is not in the surface projection of any faults, return if less than the none-contained threshold
			// this allows the offshore noyo site to match
			if (closestDist < maxDistNoneContained)
				return closest;
			// no match
			return null;
		}
		
		// if we're here, then this site is contained in the surface projection of at least 1 fault
		
		// first see if it's within the inner threshold of any fault, regardless of if it is contained
		if (closestDist < maxDistOtherContained)
			return closest;
		
		// see if any of the faults containing it are close enough
		FaultSection closestContaining = null;
		double closestContainingDist = Double.POSITIVE_INFINITY;
		for (FaultSection sect : containsCandidates) {
			double dist = candidateDists.get(sect);
			if (dist < closestContainingDist) {
				closestContainingDist = dist;
				closestContaining = sect;
			}
		}
		
		if (closestContainingDist < maxDistContained)
			return closestContaining;
		// no match
		return null;
	}

	public static void main(String[] args) throws IOException {
		List<? extends FaultSection> subSects = NSHM23_DeformationModels.GEOLOGIC.build(NSHM23_FaultModels.NSHM23_v1p4);
		
		HashSet<FaultSection> mappedSects = new HashSet<>();
		List<Location> siteLocs = new ArrayList<>();
		
		String prefix = "nshm23_ca_paleo_mappings";
		String title = "NSHM23 CA Paleo RI Mappings";
		List<SectMappedUncertainDataConstraint> datas = loadCAPaleoRateData(subSects);
//		String prefix = "nshm23_ca_paleo_slip_mappings";
//		String title = "NSHM23 CA Paleo Slip Mappings";
//		List<SectMappedUncertainDataConstraint> datas = loadU3PaleoSlipData(subSects);
		
		System.out.println("Loaded "+datas.size()+" values");
		for (SectMappedUncertainDataConstraint constraint : datas) {
			System.out.println(constraint);
			if (constraint.sectionIndex >= 0) {
				FaultSection sect = subSects.get(constraint.sectionIndex);
				double dist = sect.getFaultTrace().minDistToLine(constraint.dataLocation);
				System.out.println("\tMapped section: "+sect.getSectionName()+"\tdistance: "+(float)dist+" km");
				mappedSects.add(sect);
				siteLocs.add(constraint.dataLocation);
			}
		}
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(subSects, new CaliforniaRegions.RELM_TESTING());
		mapMaker.highLightSections(mappedSects, new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));
		
		mapMaker.plotScatters(siteLocs, Color.BLUE);
		
		mapMaker.setWriteGeoJSON(true);
		
		mapMaker.plot(new File("/tmp"), prefix, title);
		
		String urlPrefix = "http://opensha.usc.edu/ftp/kmilner/nshm23/paleo_mappings/";
		System.out.println("GeoJSON.io URL: "+RupSetMapMaker.getGeoJSONViewerLink(urlPrefix+prefix+".geojson"));
	}

}
