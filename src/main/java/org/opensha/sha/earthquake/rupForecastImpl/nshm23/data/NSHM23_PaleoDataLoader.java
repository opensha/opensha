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
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.utils.aveSlip.U3AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;

public class NSHM23_PaleoDataLoader {
	
	public static boolean INCLUDE_U3_PALEO_SLIP = false;
	
	public static PaleoseismicConstraintData load(FaultSystemRupSet rupSet) throws IOException {
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		// paleo event rate
		List<SectMappedUncertainDataConstraint> caRateConstraints = loadCAPaleoRateData(subSects);
		boolean hasCA = hasMappedConstraints(caRateConstraints);
		List<SectMappedUncertainDataConstraint> wasatchRateConstraints = loadWasatchPaleoRateData(subSects);
		boolean hasWasatch = hasMappedConstraints(wasatchRateConstraints);
		PaleoProbabilityModel paleoProbModel;
		List<SectMappedUncertainDataConstraint> paleoRateConstraints;
		if (hasCA && hasWasatch) {
			// both
			paleoRateConstraints = new ArrayList<>();
			paleoRateConstraints.addAll(caRateConstraints);
			paleoRateConstraints.addAll(wasatchRateConstraints);
			paleoProbModel = new NSHM23_PaleoProbabilityModel();
		} else if (hasCA) {
			// CA only
			paleoRateConstraints = caRateConstraints;
			paleoProbModel = UCERF3_PaleoProbabilityModel.load();
		} else  if (hasWasatch) {
			// Wasatch only
			paleoRateConstraints = wasatchRateConstraints;
			paleoProbModel = new NSHM23_PaleoProbabilityModel.WasatchPaleoProbabilityModel();
		} else {
			// neither
			paleoRateConstraints = null;
			paleoProbModel = null;
		}

		List<SectMappedUncertainDataConstraint> aveSlipConstraints = null;
		PaleoSlipProbabilityModel paleoSlipProbModel = null;
		if (INCLUDE_U3_PALEO_SLIP) {
			// reuse UCERF3 paleo slip
			aveSlipConstraints = loadU3PaleoSlipData(subSects);
			paleoSlipProbModel = U3AveSlipConstraint.slip_prob_model;
		}

		return new PaleoseismicConstraintData(rupSet, paleoRateConstraints, paleoProbModel,
				aveSlipConstraints, paleoSlipProbModel);
	}

	// ensure that mappings are within this distance in km
	private static final String NSHM23_PALEO_RI_PATH_PREFIX = "/data/erf/nshm23/constraints/paleo_ri/";
	private static final String CA_PALEO_PATH = NSHM23_PALEO_RI_PATH_PREFIX+"McPhillips_California_RIs_2022_07_13.csv";
	private static final String WASATCH_PALEO_PATH = NSHM23_PALEO_RI_PATH_PREFIX+"wasatch_paleo_data_2022_11_10.csv";
	
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
				mappedSect = PaleoseismicConstraintData.findMatchingSect(loc, subSects,
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
					mappedSect = PaleoseismicConstraintData.findMatchingSect(loc, subSects,
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
	
	public static List<SectMappedUncertainDataConstraint> loadWasatchPaleoRateData(List<? extends FaultSection> subSects)
			throws IOException {
		CSVFile<String> csv = CSVFile.readStream(NSHM23_PaleoDataLoader.class.getResourceAsStream(WASATCH_PALEO_PATH), false);
		
		List<SectMappedUncertainDataConstraint> ret = new ArrayList<>();
		
		final int siteNameCol = 0;
		final int parentNameCol = 1;
		final int parentIDCol = 2;
		final int latCol = 4;
		final int lonCol = latCol+1; 
		final int rateCol = 15;
		final int upperCol = rateCol+1; // listed as 2.5%, but it's from the 2.5% RI
		final int lowerCol = upperCol+1;
		final UncertaintyBoundType boundType = UncertaintyBoundType.CONF_95;
		
		// filter to just wasatch sections
		List<FaultSection> wasatchSubSects = new ArrayList<>();
		for (FaultSection sect : subSects)
			if (sect.getParentSectionName().toLowerCase().contains("wasatch"))
				wasatchSubSects.add(sect);
		
		for (int row=2; row<csv.getNumRows(); row++) {
			int cols = csv.getLine(row).size();
			if (cols <= upperCol+2 || csv.get(row, lowerCol).isBlank())
				continue;
			String name = csv.get(row, siteNameCol).replace("*", "");
			double lat = csv.getDouble(row, latCol);
			double lon = csv.getDouble(row, lonCol);
			double rate = csv.getDouble(row, rateCol);
			double lowerRate = csv.getDouble(row, lowerCol);
			double upperRate = csv.getDouble(row, upperCol);
			
			double stdDev = boundType.estimateStdDev(rate, lowerRate, upperRate);
			
			Location loc = new Location(lat, lon);
			// now find mapping
			
			FaultSection mappedSect = null;
			if (wasatchSubSects != null) {
				mappedSect = PaleoseismicConstraintData.findMatchingSect(loc, wasatchSubSects,
						LOC_MAX_DIST_NONE_CONTAINED, LOC_MAX_DIST_OTHER_CONTAINED, LOC_MAX_DIST_CONTAINED);
				if (mappedSect == null) {
					System.err.println("WARNING: no matching fault section found for paleo site "+name+" at "+lat+", "+lon);
					continue;
				} else {
					int parentID = csv.getInt(row, parentIDCol);
					if (parentID != mappedSect.getParentSectionId()) {
						System.err.println("WARNING: mapping mismatch for "+name+"? CSV parentID="+parentID
								+", csv parentName="+csv.get(row, parentNameCol)
								+", mapped parentID="+mappedSect.getParentSectionId()
								+", name="+mappedSect.getParentSectionName());
					}
				}
			}
			BoundedUncertainty uncert = new BoundedUncertainty(
					boundType, lowerRate, upperRate, stdDev);
			if (mappedSect == null)
				ret.add(new SectMappedUncertainDataConstraint(name, -1, null, loc, rate, uncert));
			else
				ret.add(new SectMappedUncertainDataConstraint(name, mappedSect.getSectionId(), mappedSect.getSectionName(),
						loc, rate, uncert));
		}
		
		return ret;
	}
	
	private static boolean hasMappedConstraints(List<? extends SectMappedUncertainDataConstraint> constraints) {
		for (SectMappedUncertainDataConstraint constraint : constraints)
			if (constraint.sectionIndex >= 0)
				return true;
		return false;
	}
	
	public static double LOC_MAX_DIST_NONE_CONTAINED = 40d; // large for offshore noyo site
	public static double LOC_MAX_DIST_OTHER_CONTAINED = 1d; // small, must be really close to override a fault that contains it
	public static final double LOC_MAX_DIST_CONTAINED = 20d; // allows comptom mapping

	public static void main(String[] args) throws IOException {
//		List<? extends FaultSection> subSects = NSHM23_DeformationModels.GEOLOGIC.build(NSHM23_FaultModels.NSHM23_v1p4);
		
//		String prefix = "nshm23_ca_paleo_mappings";
//		String title = "NSHM23 CA Paleo RI Mappings";
//		List<SectMappedUncertainDataConstraint> datas = loadCAPaleoRateData(subSects);
//		Region reg = new CaliforniaRegions.RELM_TESTING();
		
//		String prefix = "nshm23_ca_paleo_slip_mappings";
//		String title = "NSHM23 CA Paleo Slip Mappings";
//		List<SectMappedUncertainDataConstraint> datas = loadU3PaleoSlipData(subSects);
//		Region reg = new CaliforniaRegions.RELM_TESTING();
		
//		String prefix = "nshm23_wasatch_paleo_mappings";
//		String title = "NSHM23 Wasatch Paleo Mappings";
//		List<SectMappedUncertainDataConstraint> datas = loadWasatchPaleoRateData(subSects);
//		Region reg = new Region(new Location(42.5, -114.5), new Location(36.5, -108));
		
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File("/home/kevin/OpenSHA/nshm23/rup_sets/cache/"
				+ "rup_sets_NSHM18_WUS_PlusU3_FM_3p1_GEOL/rup_set_CoulombRupSet_4228_sects_13693_trace_locs_500367461464_area_2p7441094E19_moment.zip"));
		String prefix = "nshm18_paleo_mappings";
		String title = "NSHM23 Paleo Mappings to NSHM18 Sections";
		List<? extends SectMappedUncertainDataConstraint> datas = load(rupSet).getPaleoRateConstraints();
		Region reg = NSHM23_RegionLoader.loadFullConterminousWUS();
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		
		HashSet<FaultSection> mappedSects = new HashSet<>();
		List<Location> siteLocs = new ArrayList<>();
		
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
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(subSects, reg);
		mapMaker.highLightSections(mappedSects, new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));
		
		mapMaker.plotScatters(siteLocs, Color.BLUE);
		
		mapMaker.setWriteGeoJSON(true);
		
		mapMaker.plot(new File("/tmp"), prefix, title);
		
		String urlPrefix = "https://opensha.usc.edu/ftp/kmilner/nshm23/paleo_mappings/";
		System.out.println("GeoJSON.io URL: "+RupSetMapMaker.getGeoJSONViewerLink(urlPrefix+prefix+".geojson"));
	}

}
