package scratch.UCERF3.griddedSeismicity;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.DataUtils;

import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.utils.UCERF3_DataUtils;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.io.Files;
import com.google.common.io.Resources;

/**
 * Class provides one method, {@code getValue(Location)} that returns the value
 * of Karen Felzer's UCERF3 smoothed seismicity spatial PDF at the supplied
 * location. Class assumes X and Y are lat lon discretized on 0.1 and uses ints
 * as row and column keys to access a sparse {@code HashBasedTable}.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class GridReader {
	protected static final CaliforniaRegions.RELM_TESTING_GRIDDED region = 
			new CaliforniaRegions.RELM_TESTING_GRIDDED();
	
	protected static final String DATA_DIR = "seismicityGrids";

	protected static final Splitter SPLIT;

	protected static final Function<String, Double> FN_STR_TO_DBL;
	protected static final Function<Double, Integer> FN_DBL_TO_KEY;
	protected static final Function<String, Integer> FN_STR_TO_KEY;

	protected Table<Integer, Integer, Double> table;
	protected String filename;

	/**
	 * Constructs a new UCERF3 grid reader for the supplied filename.
	 * @param filename
	 */
	public GridReader(String filename) {
		this.filename = filename;
		table = initTable();
	}

	static {
		SPLIT = Splitter.on(CharMatcher.whitespace()).omitEmptyStrings();
		FN_STR_TO_DBL = new FnStrToDbl();
		FN_DBL_TO_KEY = new FnDblToKey();
		FN_STR_TO_KEY = Functions.compose(FN_DBL_TO_KEY, FN_STR_TO_DBL);
	}

	/**
	 * Returns the spatial PDF value at the point closest to the supplied
	 * {@code Location}
	 * @param loc {@code Location} of interest
	 * @return a PDF value or @code null} if supplied {@coed Location} is more
	 *         than 0.05&deg; outside the available data domain
	 */
	public Double getValue(Location loc) {
		return table.get(FN_DBL_TO_KEY.apply(loc.getLatitude()),
			FN_DBL_TO_KEY.apply(loc.getLongitude()));

	}
	
	/**
	 * Returns all values in order corresponding to the node indices in the
	 * supplied GriddedRegion.
	 * @param region of interest
	 * @return all required values
	 */
	public double[] getValues() {
		
		double[] values = new double[region.getNodeCount()];
		int i = 0;
		for (Location loc : region) {
			Double value = getValue(loc);
			values[i++] = (value == null) ? Double.NaN : value;
		}
		return values;
	}

	protected Table<Integer, Integer, Double> initTable() {
		Table<Integer, Integer, Double> table = HashBasedTable.create();
		try {
			BufferedReader br = new BufferedReader(UCERF3_DataUtils.getReader(DATA_DIR,
				filename));
			Iterator<String> dat;
			String line = br.readLine();
			while (line != null) {
				dat = SPLIT.split(line).iterator();				
				table.put(FN_STR_TO_KEY.apply(dat.next()),
					FN_STR_TO_KEY.apply(dat.next()),
					FN_STR_TO_DBL.apply(dat.next()));
				line = br.readLine();
			}
		} catch (IOException ioe) {
			throw Throwables.propagate(ioe);
		}
		return table;
	}

	// //////// Conversion Functions //////////
	// / TODO these would make good utilities ///

	private static class FnStrToInt implements Function<String, Integer> {
		@Override
		public Integer apply(String s) {
			return Integer.valueOf(s);
		}
	}

	private static class FnStrToDbl implements Function<String, Double> {
		@Override
		public Double apply(String s) {
			return Double.valueOf(s);
		}
	}

	private static class FnDblToKey implements Function<Double, Integer> {
		@Override
		public Integer apply(Double d) {
			return (int) Math.round(d * 10);
		}
	}

	public static void main(String[] args) {
		double[] pdf = SpatialSeisPDF.UCERF3.getPDF();
		System.out.println("U3 " + DataUtils.sum(pdf));
		
		pdf = SpatialSeisPDF.UCERF2.getPDF();
		System.out.println("U2 " + DataUtils.sum(pdf));
		
		pdf = SpatialSeisPDF.UNSMOOTHED_GRIDDED.getPDF();
		System.out.println("UNSMOOTHED_GRIDDED " + DataUtils.sum(pdf));
		
		pdf = SpatialSeisPDF.AVG_DEF_MODEL_ALL.getPDF();
		System.out.println("AVG_DEF_MODEL_ALL " + DataUtils.sum(pdf));

		pdf = SpatialSeisPDF.AVG_DEF_MODEL_OFF.getPDF();
		System.out.println("AVG_DEF_MODEL_OFF " + DataUtils.sum(pdf));

		
		double fir = SpatialSeisPDF.UCERF3.getFractionInRegion(region);
		System.out.println("U3 FractionInRegion" + fir);
		
//		String fName =  "SmoothSeis_KF_5-5-2012.txt";
//		File f = getSourceFile(fName);
//		System.out.println(f.exists());
//		GridReader gr = new GridReader();
//		
//		 double sum = 0;
//		 for (Table.Cell<Integer, Integer, Double> cell : table.cellSet()) {
//			 sum += cell.getValue();
//		 }
//		 System.out.println(sum);
//		 System.out.println(GridReader.getScale(new Location(39.65,  -120.1)));
//		 System.out.println(GridReader.getScale(new Location(20, -20)));
	}

}
