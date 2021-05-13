package org.opensha.sha.imr.attenRelImpl.ngaw2;

import static com.google.common.base.Charsets.US_ASCII;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.io.Resources;

/**
 * Skeletal class for loading and managing {@code ScalarGMPE} coefficients.
 * Class is declared as abstract because subclasses must add fields that match
 * those defined in the resource supplied to the sole constructor.
 * 
 * <p>This approach to loading coefficients is intended to be as hassle free as
 * possible by providing access to coefficients in an easily editable CSV file.
 * However, when such files are updated, it may be necessary to edit certain
 * {@code IMT} designations that are commonly coded as integers (e.g. -1 = PGV,
 * usually) or coefficient IDs that contain illegal characters (e.g those with
 * units labels in parentheses).</p>
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public abstract class Coefficients {

//	private static final String C_DIR = "/resources/data/nshmp/gmpe/";
	
	// If you move this, you must update the build.xml resources.imr target as well!
	private static final String C_DIR = "coeff/";
	private Table<IMT, String, Double> table;
	private IMT imt;

	/**
	 * Create a new coefficent wrapper from a comma-delimited coefficient
	 * resource for use by a GMPE.
	 * 
	 * @param resource coefficent csv text resource
	 * @throws RuntimeException if an error occurs reading coefficient resource.
	 */
	public Coefficients(String resource) {
		table = load(resource);
	}

	/**
	 * Sets the coefficient fields for the supplied {@code IMT}.
	 * @param imt to set coefficents for
	 * @throws RuntimeException if implementation fields do not match the
	 *         coefficient names defined in the source file/resource.
	 */
	public void set(IMT imt) {
		if (this.imt == imt) return;
		try {
			Preconditions.checkState(table.rowKeySet().contains(imt), "no coeff for %s", imt);
			for (String name : table.columnKeySet()) {
				double value = table.get(imt, name);
				getClass().getDeclaredField(name).set(this, value);
			}
			this.imt = imt;
		} catch (Exception e) {
			Throwables.propagate(e);
		}
	}
	
	/**
	 * Returns the current intensity measure type for this set of coefficients.
	 * @return the IMT
	 */
	public IMT imt() {
		return imt;
	}

	/**
	 * Returns the value of the coefficient for the supplied name and intensity
	 * measure type.
	 * @param imt intensity measure type
	 * @param name of the coe fficient to look up
	 * @return the coefficient value
	 */
	public double get(IMT imt, String name) {
		return table.get(imt, name);
	}
	
	public Collection<IMT> getSupportedIMTs() {
		return table.rowKeySet();
	}

	private Table<IMT, String, Double> load(String resource) {
		Splitter split = Splitter.on(',');
		List<String> lines = null;
		try {
			URL url = Resources.getResource(
				Coefficients.class, 
				C_DIR + resource);
			lines = Resources.readLines(url, US_ASCII);
		} catch (IOException ioe) {
			Throwables.propagate(ioe);
		}
		// build coeff name list
		Iterable<String> nameList = split.split(lines.get(0));
		Iterable<String> names = Iterables.skip(nameList, 1);
		// build IMT-value map
		Map<IMT, Double[]> valueMap = Maps.newHashMap();
		for (String line : Iterables.skip(lines, 1)) {
			Iterable<String> entries = split.split(line);
			String imtStr = Iterables.get(entries, 0);
			IMT imt = IMT.parseIMT(imtStr);
			checkNotNull(imt, "Unparseable IMT: " + imtStr);
			Iterable<String> valStrs = Iterables.skip(entries, 1);
			Iterable<Double> values = Iterables.transform(valStrs, STR_2_DBL);
			valueMap.put(imt, Iterables.toArray(values, Double.class));
		}
		// create and load table
		Table<IMT, String, Double> table = ArrayTable.create(valueMap.keySet(),
			names);
		for (IMT imt : valueMap.keySet()) {
			Double[] values = valueMap.get(imt);
			int i = 0;
			for (String name : names) {
				table.put(imt, name, values[i++]);
			}
		}
		return table;
	}
	
	/**
	 * Instance of a {@code Function} that parses a {@code String} to {@code Double}
	 * using {@link Double#valueOf(String)} throwing
	 * {@code NumberFormatException}s and {@code NullPointerException}s for
	 * invalid and {@code null} arguments.
	 */
	public static final Function<String, Double> STR_2_DBL = StringToDouble.INSTANCE;

	// enum singleton patterns
	// @formatter:off
	private enum StringToDouble implements Function<String, Double> {
		INSTANCE;
		@Override public Double apply(String s) { return Double.valueOf(s); }
	}

	
}
