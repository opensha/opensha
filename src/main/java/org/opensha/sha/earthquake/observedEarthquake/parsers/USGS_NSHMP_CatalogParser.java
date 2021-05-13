package org.opensha.sha.earthquake.observedEarthquake.parsers;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.jfree.data.Range;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.magdist.IncrementalMagFreqDist;


/**
 * This reads a USGS NSHMP catalog (at least as they were defined in 2020).
 * 
 * This defines event IDs as the nth event in the catalog (starting at 0)
 * @author field
 *
 */
public class USGS_NSHMP_CatalogParser {
	
	public static ObsEqkRupList loadCatalog(File file) throws IOException {
		ObsEqkRupList rups = new ObsEqkRupList();
		
		BufferedReader in = new BufferedReader(new FileReader(file));
		
		// used if 10 column input without IDs
		int startID = 1;
		
		String line;
		String[] split;
		int eventID = -1;
		
		while (in.ready()) {
			line = in.readLine();
			line = line.trim();
			line = line.replaceAll("\t", " ");
			while (line.contains("  "))
				line = line.replaceAll("  ", " ");
			split = line.split(" ");
			// FORMAT:
			// 0-mag 1-lon 2-lat 3-depth 4-year 5-month 6-day 7-hr 8-min (not sure what the rest is)
			double mag			= Double.parseDouble(split[0]);
			double longitude	= Double.parseDouble(split[1]);
			double latitude		= Double.parseDouble(split[2]);
			double depth		= Double.parseDouble(split[3]);

			int year			= Integer.parseInt(split[4]);
			int month			= Integer.parseInt(split[5]);
			int date			= Integer.parseInt(split[6]);
			int hourOfDay		= Integer.parseInt(split[7]);
			int minute			= Integer.parseInt(split[8]);
			double second			= Double.parseDouble(split[9]);
			double mag_expected	= Double.parseDouble(split[11]);
			double n_star	= Double.parseDouble(split[12]);

			if (Double.isNaN(depth) || depth < 0)
				depth = 0;
			
			eventID += 1;
						
			GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT-0:00"));
			cal.clear();
			cal.set(year, month-1, date, hourOfDay, minute, (int)second);
			
			Location hypoLoc = new Location(latitude, longitude, depth);
						
			rups.add(new ObsEqkRupture(eventID+"", cal.getTimeInMillis(), hypoLoc, mag));
			
			
		}
		
		in.close();
				
		return rups;
	}
	

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		File file = new File("/Users/field/MiscDocs/MuellerGK_FortranCode/wmm.c2");
		ObsEqkRupList rupList = loadCatalog(file);
		for (ObsEqkRupture rup : rupList) {
//			System.out.println(rup);
		}
		System.out.println("numEvents = "+rupList.size());
	}
	
	

}
