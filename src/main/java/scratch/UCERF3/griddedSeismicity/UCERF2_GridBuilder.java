package scratch.UCERF3.griddedSeismicity;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.LittleEndianDataInputStream;

/*
 * Utility class to build files from UCERF2 and NSHMP08.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
class UCERF2_GridBuilder {

	
	public static void main(String[] args) {
//		generateUCERF2pdf();
		generateMechWeights();
	}
	
	private static List<String> gridNames;
	private static List<List<Double>> gridMechWts;
	static {
		gridNames = Lists.newArrayList();
		gridNames.add("CA/gridded/GR_DOS/agrd_brawly.out"); // 0.5 S 0.0 R 0.5 N
		gridNames.add("CA/gridded/GR_DOS/agrd_mendos.out"); // 0.5 S 0.5 R 0.0 N
		gridNames.add("CA/gridded/GR_DOS/agrd_creeps.out"); // 1.0 S 0.0 R 0.0 N
		gridNames.add("CA/gridded/GR_DOS/agrd_deeps.out");  // 1.0 S 0.0 R 0.0 N
		gridNames.add("CA/gridded/GR_DOS/agrd_impext.out"); // 0.5 S 0.0 R 0.5 N
		gridNames.add("CA/gridded/GR_DOS/agrd_cstcal.out"); // 0.5 S 0.5 R 0.0 N
		gridNames.add("WUS/gridded/GR_DOS/agrd_wuscmp.out");// 0.5 S 0.5 R 0.0 N
		gridNames.add("WUS/gridded/GR_DOS/agrd_wusext.out");// 0.5 S 0.0 R 0.5 N
		
		gridMechWts = Lists.newArrayList();
		gridMechWts.add(ImmutableList.of(0.5, 0.0, 0.5));
		gridMechWts.add(ImmutableList.of(0.5, 0.5, 0.0));
		gridMechWts.add(ImmutableList.of(1.0, 0.0, 0.0));
		gridMechWts.add(ImmutableList.of(1.0, 0.0, 0.0));
		gridMechWts.add(ImmutableList.of(0.5, 0.0, 0.5));
		gridMechWts.add(ImmutableList.of(0.5, 0.5, 0.0));
		gridMechWts.add(ImmutableList.of(0.5, 0.5, 0.0));
		gridMechWts.add(ImmutableList.of(0.5, 0.0, 0.5));
	}
	
	private static void generateMechWeights() {
		double minLat = 24.6;
		double maxLat = 51.0;
		double dLat  = 0.1;
		double minLon = -126.5;
		double maxLon = -100.0;
		double dLon = 0.1;
		
		GriddedRegion gridRegion = new GriddedRegion(
			new Location(minLat, minLon),
			new Location(maxLat, maxLon),
			dLat, GriddedRegion.ANCHOR_0_0);
		GriddedRegion ucerfRegion = 
				new CaliforniaRegions.RELM_TESTING_GRIDDED();
		int nRows = (int) Math.rint((maxLat - minLat) / dLat) + 1;
		int nCols = (int) Math.rint((maxLon - minLon) / dLon) + 1;
		
		List<double[]> aGrids = Lists.newArrayList();
		double[] aSum = null;
		
		// set up grid arrays and a-value totals
		for (String gridName : gridNames) {
			File gridFile = new File("src/resources/data/nshmp/sources/" + 
				gridName);
			double[] aDat = readGrid(gridFile, nRows, nCols);
			aGrids.add(aDat);
			if (aSum == null) {
				aSum = aDat.clone();
				continue;
			}
			addArray(aSum, aDat);
		}
		
//		int idx = 20057;
//		for (double[] aDat : aGrids) {
//			System.out.println(aDat[idx]);
//		}
//		System.out.println("----");
//		System.out.println(aSum[20057]);

		
		// normalize a-values
		for (double[] aDat : aGrids) {
			arrayDivide(aDat, aSum);
		}
		
//		double sum = 0.0;
//		for (double[] aDat : aGrids) {
//			System.out.println(aDat[idx]);
//			sum += aDat[idx];
//		}
//		System.out.println("----");
//		System.out.println(sum);

		// build focal mech arrays
		double[] s = new double[aSum.length];
		double[] r = new double[aSum.length];
		double[] n = new double[aSum.length];
		for (int i=0; i<aGrids.size(); i++) {
			List<Double> mechWts = gridMechWts.get(i);
			double[] aDat = aGrids.get(i);
			for (int j=0; j<aDat.length; j++) {
				s[j] = s[j] + mechWts.get(0) * aDat[j];
				r[j] = r[j] + mechWts.get(1) * aDat[j];
				n[j] = n[j] + mechWts.get(2) * aDat[j];
			}
		}
		
//		for (int i=0; i<100; i++) {
//			double sum = s[i] + r[i] + n[i];
//			System.out.println(sum);
//			System.out.println(aSum[i]);
//		}
		
		List<String> sRecords = Lists.newArrayList();
		List<String> rRecords = Lists.newArrayList();
		List<String> nRecords = Lists.newArrayList();
		for (Location loc : ucerfRegion) {
			int idx = gridRegion.indexForLocation(loc);
			double sVal = (idx == -1) ? Double.NaN : s[idx];
			double rVal = (idx == -1) ? Double.NaN : r[idx];
			double nVal = (idx == -1) ? Double.NaN : n[idx];
			double lat = loc.getLatitude();
			double lon = loc.getLongitude();
			sRecords.add(String.format("%.3f %.3f %.10f", lat, lon, sVal));
			rRecords.add(String.format("%.3f %.3f %.10f", lat, lon, rVal));
			nRecords.add(String.format("%.3f %.3f %.10f", lat, lon, nVal));
		}
		File dir = new File("tmp");
		File sOut = new File(dir, "StrikeSlipWts.txt");
		File rOut = new File(dir, "ReverseWts.txt");
		File nOut = new File(dir, "NormalWts.txt");
		try {
			FileUtils.writeLines(sOut, sRecords);
			FileUtils.writeLines(rOut, rRecords);
			FileUtils.writeLines(nOut, nRecords);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}		
	}
	
	
	/**
	 * 
	 * @param a1
	 * @param a2
	 */
	private static void arrayDivide(double[] a1, double[] a2) {
		checkNotNull(a1);
		checkNotNull(a2);
		checkArgument(a1.length == a2.length, "Arrays are different sizes");
		for (int i=0; i<a1.length; i++) {
			a1[i] /= a2[i];
		}
	}
	
	
	private static void generateUCERF2pdf() {
		double minLat = 24.6;
		double maxLat = 51.0;
		double dLat  = 0.1;
		double minLon = -126.5;
		double maxLon = -100.0;
		double dLon = 0.1;
		
		GriddedRegion gridRegion = new GriddedRegion(
			new Location(minLat, minLon),
			new Location(maxLat, maxLon),
			dLat, GriddedRegion.ANCHOR_0_0);
		GriddedRegion ucerfRegion = 
				new CaliforniaRegions.RELM_TESTING_GRIDDED();
		
		int nRows = (int) Math.rint((maxLat - minLat) / dLat) + 1;
		int nCols = (int) Math.rint((maxLon - minLon) / dLon) + 1;
		
		double[] gridSum = null;
		
		for (String gridName : gridNames) {
			File gridFile = new File("src/resources/data/nshmp/sources/" + 
				gridName);
			double[] aDat = readGrid(gridFile, nRows, nCols);
			if (gridSum == null) {
				gridSum = aDat.clone();
				continue;
			}
			addArray(gridSum, aDat);
		}
		
		// sum over all values included in ucerf region
		double regionSum = 0.0;
		for (Location loc : ucerfRegion) {
			int idx = gridRegion.indexForLocation(loc);
			regionSum += (idx == -1) ? 0.0 : gridSum[idx];
		}
		
		List<String> records = Lists.newArrayList();
		for (Location loc : ucerfRegion) {
			int idx = gridRegion.indexForLocation(loc);
			double value = (idx == -1) ? 0.0 : gridSum[idx] / regionSum;
			records.add(String.format(
				"%.3f %.3f %.10f", 
				loc.getLatitude(), 
				loc.getLongitude(),
				value));
		}
		File dir = new File("tmp");
		File out = new File(dir, "SmoothSeis_UCERF2.txt");
		try {
			FileUtils.writeLines(out, records);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}		
	}
	
	private static void addArray(double[] a1, double[] a2) {
		for (int i=0; i<a1.length; i++) {
			a1[i] += a2[i];
		}
	}

	/* Converts an NSHMP index to the correct GriddedRegion index */
	private static int calcIndex(int idx, int nRows, int nCols) {
		return (nRows - (idx / nCols) - 1) * nCols + (idx % nCols);
		// compact form of:
		// int col = idx % nCols;
		// int row = idx / nCols;
		// int targetRow = nRows - row - 1;
		// return targetRow * nCols + col;
	}

	/*
	 * Method reads a binary file of data into an array. This method is tailored
	 * to the NSHMP grid files that are stored from top left to bottom right,
	 * reading across. The nodes in OpenSHA <code>GriddedRegion</code>s are
	 * stored from bottom left to top right, also reading across. This method
	 * places values at their proper index. <i><b>NOTE</b></i>: NSHMP binary
	 * grid files are all currently little-endian. The grid files in some other
	 * parts of the USGS seismic hazard world are big-endian. Beware.
	 * @param file to read
	 * @param nRows
	 * @param nCols
	 * @return a 1D array of appropriately ordered values
	 */
	private static double[] readGrid(File file, int nRows, int nCols) {
		int count = nRows * nCols;
		double[] data = new double[count];
		try {
			LittleEndianDataInputStream in = new LittleEndianDataInputStream(
				FileUtils.openInputStream(file));
			for (int i = 0; i < count; i++) {
				double value = new Float(in.readFloat()).doubleValue();
				data[calcIndex(i, nRows, nCols)] = value;
			}
			in.close();
		} catch (IOException ioe) {
			System.out.println(ioe);
		}
		return data;
	}


}
