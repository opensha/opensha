package scratch.UCERF3.utils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * This class contains year after which each magnitude is thought to be complete throughout the entire RELM region.
 * 
 * "Strict" values taken from Table L9 of Felzer (2013, UCERF3_TI Appendix L; http://pubs.usgs.gov/of/2013/1165/pdf/ofr2013-1165_appendixL.pdf).
 * 
 * On 10/29/18, Ned added a new "Relaxed" option chosen to have a less restrictive option (to include more historical data in ETAS simulations,
 * but at the cost of under-estimating spontaneous rates in some regions
 * 
 * @author kevin
 *
 */
public enum U3_EqkCatalogStatewideCompleteness {
	
	STRICT("U3_EqkCatalogStatewideCompleteness.csv"),
	RELAXED("U3_EqkCatalogStatewideCompletenessRelaxed.csv");
	
	private static final String DIR_NAME = "EarthquakeCatalog";
	
	private final String fileName;
	
	private List<Integer> startYears;
	private List<Long> startEpochs;
	private List<Double> mags;
	
	private U3_EqkCatalogStatewideCompleteness(String fileName) {
		this.fileName = fileName;
	}
	
	private synchronized void checkLoad() {
		if (startYears == null) {
			InputStream is = UCERF3_DataUtils.locateResourceAsStream(DIR_NAME, fileName);
			
			if (!(is instanceof BufferedInputStream))
				is = new BufferedInputStream(is);
			CSVFile<String> csv;
			try {
				csv = CSVFile.readStream(is, true);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
			startYears = new ArrayList<>();
			startEpochs = new ArrayList<>();
			mags = new ArrayList<>();
			
			for (int row=1; row<csv.getNumRows(); row++) {
				int startYear;
				String startYearStr = csv.get(row, 0);
				if (startYearStr.isEmpty())
					startYear = 0;
				else
					startYear = Integer.parseInt(startYearStr);
				String magStr = csv.get(row, 2);
				if (magStr.isEmpty())
					// done, metadata lines
					break;
				double mag = Double.parseDouble(magStr);
				
//				System.out.println("Adding: "+startYear+" => "+mag);
				add(startYear, mag);
			}
		}
	}
	
	private void add(int year, double mag) {
		if (!startYears.isEmpty()) {
			Preconditions.checkState(year > startYears.get(startYears.size()-1), "years must be monotonically increasing");
			Preconditions.checkState(mag < mags.get(mags.size()-1), "mag must be monotonically decreasing");
		}
		
		startYears.add(year);
		GregorianCalendar cal = new GregorianCalendar(year, 0, 0);
		startEpochs.add(cal.getTimeInMillis());
		mags.add(mag);
	}
	
	public double getMagCompleteForDate(Date date) {
		checkLoad();
		return getMagCompleteForDate(date.getTime());
	}
	
	public double getMagCompleteForDate(long epoch) {
		checkLoad();
		double mc = Double.NaN;
		for (int i=0; i<startEpochs.size(); i++) {
			if (epoch >= startEpochs.get(i))
				mc = mags.get(i);
			else
				break;
		}
		return mc;
	}
	
	public double getMagCompleteForYear(int year) {
		checkLoad();
		double mc = Double.NaN;
		for (int i=0; i<startYears.size(); i++) {
			if (year >= startYears.get(i))
				mc = mags.get(i);
			else
				break;
		}
		return mc;
	}
	
	public int getFirstYearForMagComplete(double mag) {
		checkLoad();
		int year = -1;
		for (int i=0; i<startYears.size(); i++) {
			if (mag >= mags.get(i)) {
				year = startYears.get(i);
				break;
			}
		}
		return year;
	}
	
	public ObsEqkRupList getFilteredCatalog(List<? extends ObsEqkRupture> catalog) {
		checkLoad();
		ObsEqkRupList filtered = new ObsEqkRupList();
		
		for (ObsEqkRupture rup : catalog)
			if (rup.getMag() >= getMagCompleteForDate(rup.getOriginTime()))
				filtered.add(rup);
		
		return filtered;
	}
	
	public EvenlyDiscretizedFunc getEvenlyDiscretizedMagYearFunc() {
		checkLoad();
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(2.55,8.45,60);
		for (int i=0; i<func.size(); i++)
			func.set(i, getFirstYearForMagComplete(func.getX(i)));
		return func;
	}
	
	public static void main(String[] args) throws IOException {
		U3_EqkCatalogStatewideCompleteness data = U3_EqkCatalogStatewideCompleteness.STRICT;
		
		GraphWindow gw = new GraphWindow(data.getEvenlyDiscretizedMagYearFunc(), "Historical Mc");
		gw.setX_AxisLabel("Magnitude");
		gw.setY_AxisLabel("Year");
		gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
	}

}
