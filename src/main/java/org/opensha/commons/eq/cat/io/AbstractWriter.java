package org.opensha.commons.eq.cat.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.apache.commons.io.IOUtils;
import org.opensha.commons.eq.cat.Catalog;

/**
 * Class provides basic functionality for catalog file writing. Subclass
 * constructors should initialize arrays (via <code>initWriter()</code>) as
 * necessary and must also implement <code>writeLine()</code>.
 *
 * @author Peter Powers
 * @version $Id: AbstractWriter.java 7478 2011-02-15 04:56:25Z pmpowers $
 *
 */
public abstract class AbstractWriter implements CatalogWriter {

	private String name;
	private String description;

	/** EventID data store. */
	protected int[] dat_eventIDs;
	/** Event type data store. */
	protected int[] dat_eventTypes;
	/** Event dates data store. */
	protected long[] dat_dates;
	/** Event longitudes data store. */
	protected double[] dat_longitudes;
	/** Event longitudes data store. */
	protected double[] dat_latitudes;
	/** Event depths data store. */
	protected double[] dat_depths;
	/** Event magnitudes data store. */
	protected double[] dat_magnitudes;
	/** Event magnitudeTypes data store. */
	protected int[] dat_magnitudeTypes;
	/** Event quality data store. */
	protected int[] dat_eventQuality;
	/** Event xy errors data store. */
	protected double[] dat_xyErrors;
	/** Event z zrrors data store. */
	protected double[] dat_zErrors;
	/** Event fault plane strikes data store. */
	protected int[] dat_fpStrikes;
	/** Event fault plane dips data store. */
	protected int[] dat_fpDips;
	/** Event fault plane rakes data store. */
	protected int[] dat_fpRakes;

	/**
	 * Utility calendar preset to UTC time for transforming date/time values.
	 */
	protected final GregorianCalendar cal = new GregorianCalendar(
		TimeZone.getTimeZone("UTC"));

	/** Catalog from which to retrieve data. */
	protected Catalog catalog;

	/**
	 * Constructs a new catalog file writer.
	 * 
	 * @param name of the reader
	 * @param description of the reader
	 */
	public AbstractWriter(String name, String description) {
		this.name = name;
		this.description = description;
	}

	/**
	 * Constructs a new catalog file writer.
	 */
	public AbstractWriter() {
		this("No name", "No description");
	}

	/**
	 * Initialize writer by instantiating necessary arrays.
	 *
	 * @param writer
	 * @throws NullPointerException if <code>writer</code> is <code>null</code>
	 */
	public abstract void initWriter(PrintWriter writer);

	/**
	 * Write a line of data.
	 *
	 * @param index of data to write
	 * @throws IndexOutOfBoundsException if <code>index</code> is not valid for
	 *         catalog
	 * @throws IOException if unable to write catalog entry
	 */
	public abstract void writeLine(int index) throws IOException;

	@Override
	public void process(Catalog catalog, File file) throws IOException {
		this.catalog = catalog;
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
			initWriter(pw);
			for (int i = 0; i < this.catalog.size(); i++) {
				writeLine(i);
			}
		} catch (IOException ioe) {
			throw new IOException("Error opening catalog file: "
				+ file.getName(), ioe);
		} finally {
			clearArrays();
			IOUtils.closeQuietly(pw);
		}
	}

	/**
	 * Resets all internal arrays to null, thereby releasing references to data.
	 */
	private void clearArrays() {
		dat_eventIDs = null;
		dat_eventTypes = null;
		dat_dates = null;
		dat_longitudes = null;
		dat_latitudes = null;
		dat_depths = null;
		dat_magnitudes = null;
		dat_magnitudeTypes = null;
		dat_eventQuality = null;
		dat_xyErrors = null;
		dat_zErrors = null;
		dat_fpStrikes = null;
		dat_fpDips = null;
		dat_fpRakes = null;
	}

	/**
	 * Overriden to return the name of this writer.
	 */
	@Override
	public String toString() {
		return name;
	}

	@Override
	public String description() {
		return description;
	}

}
