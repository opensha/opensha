package scratch.UCERF3.utils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * This class contains year after which each magnitude is thought to be complete throughout the entire RELM region, 
 * taken from Table L9 of Felzer (2013, UCERF3_TI Appendix L; http://pubs.usgs.gov/of/2013/1165/pdf/ofr2013-1165_appendixL.pdf)
 * 
 * @author kevin
 *
 */
public class U3_EqkCatalogStatewideCompleteness {
	
	private List<Integer> startYears;
	private List<Long> startEpochs;
	private List<Double> mags;
	
	private U3_EqkCatalogStatewideCompleteness() {
		startYears = Lists.newArrayList();
		startEpochs = Lists.newArrayList();
		mags = Lists.newArrayList();
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
		return getMagCompleteForDate(date.getTime());
	}
	
	public double getMagCompleteForDate(long epoch) {
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
		ObsEqkRupList filtered = new ObsEqkRupList();
		
		for (ObsEqkRupture rup : catalog)
			if (rup.getMag() >= getMagCompleteForDate(rup.getOriginTime()))
				filtered.add(rup);
		
		return filtered;
	}
	
	public EvenlyDiscretizedFunc getEvenlyDiscretizedMagYearFunc() {
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(2.55,8.45,60);
		for (int i=0; i<func.size(); i++)
			func.set(i, getFirstYearForMagComplete(func.getX(i)));
		return func;
	}
	
	public static U3_EqkCatalogStatewideCompleteness load() throws IOException {
		return load(UCERF3_DataUtils.locateResourceAsStream("EarthquakeCatalog", "U3_EqkCatalogStatewideCompleteness.csv"));
	}
	
	public static U3_EqkCatalogStatewideCompleteness load(InputStream is) throws IOException {
		if (!(is instanceof BufferedInputStream))
			is = new BufferedInputStream(is);
		CSVFile<String> csv = CSVFile.readStream(is, true);
		
		U3_EqkCatalogStatewideCompleteness data = new U3_EqkCatalogStatewideCompleteness();
		
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
			
//			System.out.println("Adding: "+startYear+" => "+mag);
			data.add(startYear, mag);
		}
		
		return data;
	}
	
	public static void main(String[] args) throws IOException {
		U3_EqkCatalogStatewideCompleteness data = load();
		
		GraphWindow gw = new GraphWindow(data.getEvenlyDiscretizedMagYearFunc(), "Historical Mc");
		gw.setX_AxisLabel("Magnitude");
		gw.setY_AxisLabel("Year");
		gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
	}

}
