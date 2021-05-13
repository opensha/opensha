package org.opensha.nshmp2.util;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.io.LittleEndianDataInputStream;

/**
 * NSHMP uility methods. Some will eventually be relocated to commons. Others
 * should be in a package private class.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class NSHMP_Utils {

	public static final String WARN_INDENT = "          ";

	/** Path to NSHMP config file directory. */
	public static final String CONF_DIR = "/resources/data/nshmp/sources/";

	/**
	 * Reads the specified number of lines into a new line list.
	 * @param it line iterator
	 * @param n number of lines to read
	 * @return a list of lines
	 */
	public static List<String> readLines(Iterator<String> it, int n) {
		List<String> lineSet = new ArrayList<String>();
		for (int i = 0; i < n; i++) {
			lineSet.add(it.next());
		}
		return lineSet;
	}

	/**
	 * Splits line on spaces and reads the value at <code>pos</code> as an
	 * <code>int</code>.
	 * @param line to read from
	 * @param pos position to read
	 * @return the <code>int</code> value at <code>pos</code>
	 */
	public static int readInt(String line, int pos) {
		return readInt(StringUtils.split(line), pos);
	}

	/**
	 * Reads the value at <code>pos</code> in array as an <code>int</code>.
	 * @param vals the <code>String</code> array to read from
	 * @param pos position to read
	 * @return the <code>int</code> value at <code>pos</code>
	 */
	public static int readInt(String[] vals, int pos) {
		return Integer.parseInt(vals[pos]);
	}

	/**
	 * Reads the specified number of values on a line as doubles.
	 * @param line to read from
	 * @param n number of values to read
	 * @return an array of <code>double</code>s
	 */
	public static int[] readInts(String line, int n) {
		String[] dat = StringUtils.split(line);
		int[] vals = new int[n];
		for (int i = 0; i < n; i++) {
			vals[i] = Integer.parseInt(dat[i]);
		}
		return vals;
	}

	/**
	 * Splits line on spaces and reads the value at <code>pos</code> as a
	 * <code>double</code>.
	 * @param line to read from
	 * @param pos position to read
	 * @return the <code>double</code> value at <code>pos</code>
	 */
	public static double readDouble(String line, int pos) {
		return readDouble(StringUtils.split(line), pos);
	}

	/**
	 * Reads the value at <code>pos</code> in array as a <code>double</code>.
	 * @param vals the <code>String</code> array to read from
	 * @param pos position to read
	 * @return the <code>double</code> value at <code>pos</code>
	 */
	public static double readDouble(String[] vals, int pos) {
		return Double.parseDouble(vals[pos]);
	}

	/**
	 * Reads the specified number of values on a line as doubles.
	 * @param line to read from
	 * @param n number of values to read
	 * @return an array of <code>double</code>s
	 */
	public static double[] readDoubles(String line, int n) {
		String[] dat = StringUtils.split(line);
		double[] vals = new double[n];
		for (int i = 0; i < n; i++) {
			vals[i] = Double.parseDouble(dat[i]);
		}
		return vals;
	}

	/**
	 * Computes total moment rate as done by NSHMP code from supplied magnitude
	 * info and the Gutenberg-Richter a- and b-values. <b>Note:</b> the a- and
	 * b-values assume an incremental distribution.
	 * 
	 * @param mMin minimum magnitude (after adding <code>dMag</code>/2)
	 * @param nMag number of magnitudes
	 * @param dMag magnitude bin width
	 * @param a value (incremental and defined wrt <code>dMag</code> for M0)
	 * @param b value
	 * @return the total moment rate
	 */
	public static double totalMoRate(double mMin, int nMag, double dMag,
			double a, double b) {
		double moRate = 1e-10; // start with small, non-zero rate
		double M;
		for (int i = 0; i < nMag; i++) {
			M = mMin + i * dMag;
			moRate += MagUtils.gr_rate(a, b, M) * MagUtils.magToMoment(M);
		}
		return moRate;
	}

	/**
	 * Computes the Gutenberg-Richter incremental rate at the supplied
	 * magnitude. Convenience method for <code>N(M) = a*(10^-bm)</code>.
	 * @param a value (incremental and defined wrt <code>dMag</code> for M0)
	 * @param b value
	 * @param mMin minimum magnitude of distribution
	 * @return the rate at the supplied magnitude
	 */
	public static double incrRate(double a, double b, double mMin) {
		return a * Math.pow(10, -b * mMin);
	}

	/**
	 * Reads lines from a file, skipping any that start with '#'.
	 * @param f file to read from
	 * @param log to log to in the event of an error; may be <code>null</code>
	 * @return a <code>List</code> of <code>String</code>s or <code>null</code>
	 *         if an error occurs
	 */
//	public static List<String> readLines(File f, Logger log) {
//		try {
//			List<String> list = new ArrayList<String>();
//			LineIterator it = FileUtils.lineIterator(f);
//			String line;
//			while (it.hasNext()) {
//				if ((line = it.next()).startsWith("#")) continue;
//				list.add(line);
//			}
//			return list;
//		} catch (IOException ioe) {
//			if (log != null) {
//				log.log(Level.SEVERE,
//					"Error reading lines from file: " + f.getPath(), ioe);
//			}
//			return null;
//		}
//	}

	private static Logger log;

	public static Logger logger() {
		if (log != null) return log;
		log = Logger.getLogger("org.opensha.sha.nshmp");
		log.setLevel(Level.WARNING);
		log.setUseParentHandlers(false);

		Formatter cf = new Formatter() {
			@Override
			public String format(LogRecord lr) {
				// @formatter:off
				StringBuilder b = new StringBuilder();
				Level l = lr.getLevel();
				b.append("[").append(l).append("]");
				if (l == Level.SEVERE || l == Level.WARNING) {
					
					b.append(" ").append(lr.getMessage())
					.append(IOUtils.LINE_SEPARATOR)
					.append(WARN_INDENT)
					.append(lr.getSourceClassName())
					.append(": ")
					.append(lr.getSourceMethodName())
					.append("()")
					.append(IOUtils.LINE_SEPARATOR)
					;
					if (lr.getThrown() != null) {
						b.append(lr.getThrown());
					}
				} else {
					b.append(" ").append(lr.getMessage());
				}
				b.append(IOUtils.LINE_SEPARATOR);
				return b.toString();
				// @formatter:on
			}
		};
		Handler ch = new ConsoleHandler();
		ch.setFormatter(cf);
		ch.setLevel(log.getLevel());
		log.addHandler(ch);
		return log;
	}

	/**
	 * Method reads a binary file of data into an array. This method is tailored
	 * to the NSHMP grid files that are stored from top left to bottom right,
	 * reading across. The nodes in OpenSHA <code>GriddedRegion</code>s are
	 * stored from bottom left to top right, also reading across. This method
	 * places values at their proper index.
	 * 
	 * <p><b>Note</b>: NSHMP binary grid files are all currently little-endian.
	 * The grid files in some other parts of the USGS seismic hazard world are
	 * big-endian. Beware.</p>
	 * 
	 * @param url to read
	 * @param nRows
	 * @param nCols
	 * @return a 1D array of appropriately ordered values
	 */
	public static double[] readGrid(URL url, int nRows, int nCols) {
		int count = nRows * nCols;
		double[] data = new double[count];
		try {
			LittleEndianDataInputStream in = new LittleEndianDataInputStream(
				url.openStream());
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

	/**
	 * Custom method to read NSHMP CEUS craton and margin files. These are
	 * fortran output logical files and have 4 f-specific bytes on each end of
	 * the file. Each logical fills 4bytes and although the files contain
	 * 4*128000 bytes, the CEUS mMax files are used in cra.f when generating so
	 * only 4*127755 are filled. The reamining slots are false and not
	 * considered.
	 * 
	 * @param url to read
	 * @param nRows
	 * @param nCols
	 * @return a 1D array of appropriately ordered values
	 */
	public static boolean[] readBoolGrid(URL url, int nRows, int nCols) {
		int count = nRows * nCols;
		boolean[] data = new boolean[count];
		try {
			DataInputStream in = new DataInputStream(url.openStream());
			// skip first four bytes
			in.skipBytes(4);
			for (int i = 0; i < count; i++) {
				int iCor = NSHMP_Utils.calcIndex(i, nRows, nCols);
				// read first byte of each set of four
				data[iCor] = (in.readByte() == 0) ? false : true;
				in.skipBytes(3);
			}
			in.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		return data;
	}

	/*
	 * This method converts an NSHMP index to the correct GriddedRegion index
	 */
	private static int calcIndex(int idx, int nRows, int nCols) {
		return (nRows - (idx / nCols) - 1) * nCols + (idx % nCols);
		// compact form of:
		// int col = idx % nCols;
		// int row = idx / nCols;
		// int targetRow = nRows - row - 1;
		// return targetRow * nCols + col;
	}

	/**
	 * Given an observed annual rate of occurrence of some event (in num/yr),
	 * method returns the Poisson probability of occurence over the specified
	 * time period.
	 * @param rate (annual) of occurence of some event
	 * @param time period of interest
	 * @return the Poisson probability of occurrence in the specified
	 *         <code>time</code>
	 */
	public static double rateToProb(double rate, double time) {
		return 1 - Math.exp(-rate * time);
	}

	/**
	 * Given the Poisson probability of the occurence of some event over a
	 * specified time period, method returns the annual rate of occurrence of
	 * that event.
	 * @param P the Poisson probability of an event's occurrence
	 * @param time period of interest
	 * @return the annnual rate of occurrence of the event
	 */
	public static double probToRate(double P, double time) {
		return -Math.log(1 - P) / time;
	}
	
	public static void main(String[] args) {
		generateUCERF2pdf();
	}
	
	private static void generateUCERF2pdf() {
		double minLat = 24.6;
		double maxLat = 50.0;
		double dLat  = 0.1;
		double minLon = -125.0;
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

		List<String> gridNames = Lists.newArrayList();
		gridNames.add("CA/gridded/GR_DOS/agrd_brawly.out");
		gridNames.add("CA/gridded/GR_DOS/agrd_mendos.out");
		gridNames.add("CA/gridded/GR_DOS/agrd_creeps.out");
		gridNames.add("CA/gridded/GR_DOS/agrd_deeps.out");
		gridNames.add("CA/gridded/GR_DOS/agrd_impext.out");
		gridNames.add("CA/gridded/GR_DOS/agrd_cstcal.out");
		gridNames.add("WUS/gridded/GR_DOS/agrd_wuscmp.out");
		gridNames.add("WUS/gridded/GR_DOS/agrd_wusext.out");
		
		double[] gridSum = null;
		
		for (String gridName : gridNames) {
			URL gridURL = Utils.getResource("/sources/" + gridName);
			double[] aDat = readGrid(gridURL, nRows, nCols);
			if (gridSum == null) {
				gridSum = aDat;
				continue;
			}
			addArray(gridSum, aDat);
		}
		
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
		
		
//		System.out.println(gridSum.length);
//		System.out.println(region.getNodeCount());
		
		
		
	}
	
	private static void addArray(double[] a1, double[] a2) {
		for (int i=0; i<a1.length; i++) {
			a1[i] += a2[i];
		}
	}
	
	

	/**
	 * Returns a trapezoidal region that encloses the supplied location list
	 * with the supplied buffer. 
	 * 
	 * @param locs to create bounds for
	 * @param buffer for bounds
	 * @return a bounding region
	 * @see NSHMP_Utils#creatBounds(double, double, double, double, double)
	 */
	public static Region createBounds(LocationList locs, double buffer) {
		double minLat = LocationUtils.calcMinLat(locs);
		double maxLat = LocationUtils.calcMaxLat(locs);
		double minLon = LocationUtils.calcMinLon(locs);
		double maxLon = LocationUtils.calcMaxLon(locs);
		return creatBounds(minLat, maxLat, minLon, maxLon, buffer);
	}
	
	/**
	 * Returns a trapezoidal region that encloses the supplied lat-lon limits
	 * with the supplied buffer. Method builds region out laterally first to set
	 * edges that will not be parallel to geographic parallels, and then
	 * determines top and bottom edges. Such a regions could be used as a coarse
	 * filter for determining if an ERF should be included in a hazard
	 * calculation.
	 * 
	 * @param minLat 
	 * @param maxLat 
	 * @param minLon 
	 * @param maxLon 
	 * @param buffer for bounds
	 * @return a bounding region
	 */
	public static Region creatBounds(double minLat, double maxLat,
			double minLon, double maxLon, double buffer) {
		
		// inner bounds corners
		Location isw = new Location(minLat, minLon);
		Location ise = new Location(minLat, maxLon);
		Location inw = new Location(maxLat, minLon);
		Location ine = new Location(maxLat, maxLon);
		
		// build 2 points on east edge
		LocationVector v = new LocationVector(90, buffer, 0);
		Location seEdge = LocationUtils.location(ise, v);
		Location neEdge = LocationUtils.location(ine, v);
		
		// build 2 points on west edge
		v.reverse();
		Location swEdge = LocationUtils.location(isw, v);
		Location nwEdge = LocationUtils.location(inw, v);
		
		// outer bounds corners
		v.setAzimuth(LocationUtils.azimuth(seEdge, neEdge));
		v.reverse();
		Location ose = LocationUtils.location(seEdge, v);
		v.setAzimuth(LocationUtils.azimuth(neEdge, seEdge));
		v.reverse();
		Location one = LocationUtils.location(neEdge, v);
		v.setAzimuth(LocationUtils.azimuth(swEdge, nwEdge));
		v.reverse();
		Location osw = LocationUtils.location(swEdge, v);
		v.setAzimuth(LocationUtils.azimuth(nwEdge, swEdge));
		v.reverse();
		Location onw = LocationUtils.location(nwEdge, v);
		
		LocationList corners = new LocationList();
		corners.addAll(Lists.newArrayList(onw, one, ose, osw));
		return new Region(corners, BorderType.MERCATOR_LINEAR);
	}
	
	
	
}
