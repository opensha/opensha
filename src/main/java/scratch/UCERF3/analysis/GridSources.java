package scratch.UCERF3.analysis;

import static org.opensha.commons.eq.cat.util.DataType.*;
import static org.opensha.commons.eq.cat.util.MagnitudeType.*;
import static scratch.UCERF3.enumTreeBranches.FaultModels.*;

import java.awt.geom.Area;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.eq.cat.Catalog;
import org.opensha.commons.eq.cat.DefaultCatalog;
import org.opensha.commons.eq.cat.io.AbstractReader;
import org.opensha.commons.eq.cat.util.MagnitudeType;
import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.param.impl.CPTParameter;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;

/**
 * Use for the creation and presentation of various grid source diagnostics
 * including, but not limited to, smoothed seismicity MFDs, spatial pdfs, eq
 * catalogs, and fault zone polygons.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class GridSources {
	
	private static final CaliforniaRegions.RELM_TESTING_GRIDDED region = 
 new CaliforniaRegions.RELM_TESTING_GRIDDED();

	private static final String catPath = "/Users/pmpowers/Documents/UCERF3/seismicity/griddedSeis/data/cat/UCERF3_Catalog5.txt";

	public static void main(String[] args) {
		// plotNodePolyParticipCat(FM3_1, 6.5, 7d, true);

		InversionFaultSystemRupSet faultSysRupSet = InversionFaultSystemRupSetFactory
			.forBranch(FaultModels.FM3_1, DeformationModels.GEOLOGIC,
				ScalingRelationships.ELLSWORTH_B, SlipAlongRuptureModels.TAPERED, InversionModels.GR_CONSTRAINED);

		FaultSection f = faultSysRupSet.getFaultSectionData(603);
		System.out.println(f.getSectionId());
		System.out.println(f.getSectionName());		
		System.out.println(f.getZonePolygon().getBorder());
		
		int pID = f.getParentSectionId();
		List<? extends FaultSection> ps = faultSysRupSet.getFaultModel().fetchFaultSections();
		FaultSection p = null;
		for (FaultSection pps : ps) {
			if (pps.getSectionId() == pID) p = pps;
		}
		System.out.println(pID);
		System.out.println(f.getParentSectionName());
		System.out.println(p.getName());
		System.out.println(p.getZonePolygon().getBorder());
		
//		System.out.println("test def model frac: " +
//		calcNodePolyParticPDF(faultSysRupSet, 12.0, SpatialSeisPDF.UCERF3));

//		System.out.println("frac pdf UCERF3: " +
//			calcNodePolyParticPDF(FM3_1, 12.0, SpatialSeisPDF.UCERF3));
//		System.out.println("frac pdf UCERF2: " +
//			calcNodePolyParticPDF(FM3_1, 12.0, SpatialSeisPDF.UCERF2));

	}
	
	private static double calcNodePolyParticPDF(FaultModels model, Double buf, SpatialSeisPDF pdf) {
		FaultPolyMgr mgr = FaultPolyMgr.create(model, buf, 7.0);
		return pdfInPolys(mgr, pdf) * 100;
	}
	
//	private static double calcNodePolyParticPDF(List<FaultSectionPrefData> faults, Double buf, SpatialSeisPDF pdf) {
//		FaultPolyMgr mgr = FaultPolyMgr.create(faults, buf);
//		return pdfInPolys(mgr, pdf) * 100;
//	}

	private static void plotNodePolyParticCat(FaultModels model, Double buf, Double len, boolean computeCatFrac) {

//		 TODO this needs to be extended to work with UCERF3 inversion solutions
//		SimpleFaultSystemSolution tmp = null;
//		try {
//			File f = new File(filename);
//			tmp = SimpleFaultSystemSolution.fromFile(f);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		InversionFaultSystemSolution invFss = new InversionFaultSystemSolution(tmp);
//		UCERF3_GridSourceGenerator gridGen = new UCERF3_GridSourceGenerator(
//			invFss, null, SpatialSeisPDF.UCERF3, 8.54, SmallMagScaling.MO_REDUCTION);
//		System.out.println("init done");

		FaultPolyMgr mgr = FaultPolyMgr.create(model, buf, len);

		// true makes X latitude
		GriddedGeoDataSet polyDat = new GriddedGeoDataSet(region, true); 
		double regionFraction = 0.0;
		for (int i = 0; i < region.getNodeCount(); i++) {
			double frac = mgr.getNodeFraction(i);
			regionFraction += frac;
			polyDat.set(i, mgr.getNodeFraction(i));
		}
		regionFraction = 100.0 * regionFraction / region.getNodeCount();
		regionFraction = Precision.round(regionFraction, 1);
		
		StringBuffer sb = new StringBuffer("Fault buffer (km): ");
		sb.append(buf);
		sb.append("<br />Inside polygons...");
		sb.append("<br />% of region: ").append(regionFraction);
		
		if (computeCatFrac) {
			double clustPct = catInPolys(mgr, getCatalog(false));
			clustPct = Precision.round(clustPct, 1);
			sb.append("<br />% of whole catalog: ").append(clustPct);
			double declustPct = catInPolys(mgr, getCatalog(true));
			declustPct = Precision.round(declustPct, 1);
			sb.append("<br />% of declustered catalog: ").append(declustPct);
		}
		
		try {
			plotMap(polyDat, "% Grid node spanned by faults", sb.toString(),
				"UCERF3_NodePolyParticipation other info");
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	
	private static void plotMap(GeoDataSet geoDataSet, String scaleLabel,
			String metadata, String dirName) throws IOException {
		
		GMT_MapGenerator gmt_MapGenerator = GMT_CA_Maps.getDefaultGMT_MapGenerator();
		gmt_MapGenerator.setParameter(GMT_MapGenerator.LOG_PLOT_NAME, false);
			
		//override default scale
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MIN_PARAM_NAME, 0.0);
		gmt_MapGenerator.setParameter(GMT_MapGenerator.COLOR_SCALE_MAX_PARAM_NAME, 1.0);
		
		CPTParameter cptParam = (CPTParameter )gmt_MapGenerator.getAdjustableParamsList().getParameter(GMT_MapGenerator.CPT_PARAM_NAME);
		cptParam.setValue(GMT_CPT_Files.GMT_OCEAN2.getFileName());
		
		GMT_CA_Maps.makeMap(geoDataSet, scaleLabel, metadata, dirName, gmt_MapGenerator);
	}
	

	private static double pdfInPolys(FaultPolyMgr mgr, SpatialSeisPDF pdf) {
		double[] pdfVals = pdf.getPDF();
		double fraction = 0;
		Map<Integer, Double> nodeMap = mgr.getNodeExtents();
		for (int idx : nodeMap.keySet()) {
			fraction += nodeMap.get(idx) * pdfVals[idx];
		}
		return fraction;
	}
	
	
	private static double catInPolys(FaultPolyMgr mgr, Catalog cat) {
		int contains = 0;
		for (int i=0; i<cat.size(); i++) {
			double lat = cat.getValue(LATITUDE, i);
			double lon = cat.getValue(LONGITUDE, i);
			for (Area a : mgr) {
				if (a != null && a.contains(lon, lat)) {
					contains++;
					break;
				}
			}
		}
		return 100.0 * contains / cat.size();
	}
	
	
	private static Catalog getCatalog(boolean decluster) {
		try {
			return new DefaultCatalog(new File(catPath), new FelzerReader(61000, decluster));
		} catch (IOException ioe) {
			ioe.printStackTrace();
			return null;
		}
	}

	
	private static class FelzerReader extends AbstractReader {

		private static final String NAME = "Felzer UCERF Catalog Format";
		private static final String DESC = "Parses a UCERF3 catalog generated by K. Felzer.";
		private static final Splitter S = Splitter.on(CharMatcher.whitespace()).omitEmptyStrings();

		private int count;
		private boolean decluster;

		public FelzerReader(int size, boolean decluster) {
			super(NAME, DESC, size);
			this.decluster = decluster;
		}

		@Override
		public void initReader() {
			count = 0;
//			dat_eventIDs       = new ArrayList<Integer>(size);
			dat_dates          = new ArrayList<Long>(size);
			dat_longitudes     = new ArrayList<Double>(size);
			dat_latitudes      = new ArrayList<Double>(size);
			dat_depths         = new ArrayList<Double>(size);
			dat_magnitudes     = new ArrayList<Double>(size);
			dat_magnitudeTypes = new ArrayList<Integer>(size);
			// TODO 
			// dat_magnitudeUnc
			// dat_magnitudeRound
			// dat_network
		}

		@Override
		public void loadData() {
//			catalog.addData(EVENT_ID, Ints.toArray(dat_eventIDs));
			catalog.addData(TIME, Longs.toArray(dat_dates));
			catalog.addData(LONGITUDE, Doubles.toArray(dat_longitudes));
			catalog.addData(LATITUDE, Doubles.toArray(dat_latitudes));
			catalog.addData(DEPTH, Doubles.toArray(dat_depths));
			catalog.addData(MAGNITUDE, Doubles.toArray(dat_magnitudes));
			catalog.addData(MAGNITUDE_TYPE,  Ints.toArray(dat_magnitudeTypes));
		}

		@Override
		public void parseLine(String line) {

			/** Sample data
			0      1  2  3  4  5      6       7        8     9    10   11   12   13          14      15 16
	        #YYY  MM DD HH mm SS.ss   LAT     LON      DEP   MAG  TYPE NETW UNC  ROUND       ID       A F
	        2009   2 12 20 52 55.7500 40.8453 -123.288 30.91 2.54 4.00 2.00 0.17 0.01        40231748 0 0
	        2009   2 13  1 52 48.7000 35.4118 -117.795 7.78 2.80 1.00 2.00 0.03 0.01        14420704 1 0
	        2009   2 13  4 17 27.4600 38.8278 -122.792 2.12 2.54 4.00 2.00 0.18 0.01        40231766 1 0
	        2009   2 13  8 25 38.2600 35.4985 -120.771 3.21 2.63 4.00 2.00 0.12 0.01        40231773 0 0
	        2009   2 14 17  1 3.8000 35.9900 -117.299 3.55 2.58 1.00 2.00 0.03 0.01        14421304 1 1
	        2009   2 14 18 48 6.8100 32.6263 -115.610 17.69 2.59 1.00 2.00 0.06 0.01        14421360 1 0

	        0         1         2         3         4         5         6         7         8
	        012345678901234567890123456789012345678901234567890123456789012345678901234567890
			 */

			try {
				
				cal.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
				count++;

				List<String> dat = Lists.newArrayList(S.split(line));
				
				// foreshock aftershock tests
				boolean fs = Integer.parseInt(dat.get(16)) == 1;
				boolean as = Integer.parseInt(dat.get(15)) == 1;
				if (decluster && (fs || as)) return;
				
				// date
				int year = Integer.parseInt(dat.get(0));
				cal.set(Calendar.YEAR, year);
				
				int month = Integer.parseInt(dat.get(1));
				if (month != 99) cal.set(Calendar.MONTH, month - 1);
				
				int day = Integer.parseInt(dat.get(2));
				if (day != 99) cal.set(Calendar.DAY_OF_MONTH, day);

				int hour = Integer.parseInt(dat.get(3));
				if (hour != 99) cal.set(Calendar.HOUR_OF_DAY, hour);
				
				int minute = Integer.parseInt(dat.get(4));
				if (minute != 99) cal.set(Calendar.MINUTE, minute);

				String[] secDat = StringUtils.split(dat.get(5), '.');
				int second = Integer.parseInt(secDat[0]);
				if (second != 99) {
					cal.set(Calendar.SECOND, second);
					int ms = (int) Double.parseDouble(secDat[1]) / 10;
					cal.set(Calendar.MILLISECOND, ms);
				}
				
				dat_dates.add(cal.getTimeInMillis());

				// magnitude
				dat_magnitudes.add(Double.parseDouble(dat.get(9)));
				dat_magnitudeTypes.add(parseMagType(Double.parseDouble(dat.get(10))).id());

				// extents
				dat_latitudes.add(Double.parseDouble(dat.get(6)));
				dat_longitudes.add(Double.parseDouble(dat.get(7)));
				dat_depths.add(Double.parseDouble(dat.get(8)));
//				dat_eventQuality.add(EventQuality.parse(line.substring(58,59)).id());

				// id NOTE skipping event IDs for now as some are >
				// MAX_INTEGER and need longs or strings
//				dat_eventIDs.add(Integer.parseInt(dat.get(14)));

			} catch (Exception e) {
	        	// TODO stack trace to log
				e.printStackTrace();
				throw new IllegalArgumentException(
					"Error reading catalog file format at line: " + count);
			}
			
		}
		
		
		// 1 ML
		// 2 Mb
		// 3 Mc
		// 4 Md
		// 5 Mh
		// 6 MS
		// 7 MW
		// 8 MX
		// 9 Unk
		private static MagnitudeType parseMagType(double d) {
			int typeID = new Double(d).intValue();
			switch(typeID) {
				case 1: return LOCAL;
				case 2: return BODY;
				case 3: return CODA_AMPLITUDE;
				case 4: return CODA_DURATION;
				case 5: return HELICORDER;
				case 6: return SURFACE;
				case 7: return MOMENT;
				case 8: return MAX_AMPLITUDE;
				default: return NONE;
			}
		}
			
	}

}
