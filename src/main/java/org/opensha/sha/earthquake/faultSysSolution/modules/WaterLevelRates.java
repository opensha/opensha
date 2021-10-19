package org.opensha.sha.earthquake.faultSysSolution.modules;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;

import com.google.common.base.Preconditions;

public class WaterLevelRates implements CSV_BackedModule {
	
	private double[] waterlevelRates;

	@SuppressWarnings("unused") // used in deserialization
	private WaterLevelRates() {}
	
	public WaterLevelRates(double[] waterlevelRates) {
		this.waterlevelRates = waterlevelRates;
	}
	
	public double[] get() {
		return waterlevelRates;
	}
	
	public double[] subtractFrom(double[] rates) {
		Preconditions.checkState(rates.length == waterlevelRates.length);
		double[] ret = new double[rates.length];
		for (int i=0; i<rates.length; i++) {
			ret[i] = rates[i] - waterlevelRates[i];
			
			// deal with floating point precision issues
			if (ret[i] < 0) {
				// can happen if post-water-level rates are averaged
				Preconditions.checkState(ret[i] > -1e-20);
				ret[i] = 0d;
			} else if (ret[i] < 1e-20 || (float)rates[i] == (float)waterlevelRates[i]) {
				ret[i] = 0d;
			}
		}
		return ret;
	}

	@Override
	public String getFileName() {
		return "water_level_rates.csv";
	}

	@Override
	public String getName() {
		return "Water Level (pre-inversion minimum) Rates";
	}

	@Override
	public CSVFile<String> getCSV() {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("Rupture Index", "Water Level Rate");
		for (int r=0; r<waterlevelRates.length; r++)
			csv.addLine(r+"", waterlevelRates[r]+"");
		return csv;
	}

	@Override
	public void initFromCSV(CSVFile<String> csv) {
		double[] vals = new double[csv.getNumRows()-1];
		for (int row=1; row<csv.getNumRows(); row++) {
			int r = row-1;
			Preconditions.checkState(r == csv.getInt(row, 0),
					"Rupture indexes must be 0-based and in order. Expected %s at row %s", r, row);
			vals[r] = csv.getDouble(row, 1);
		}
		this.waterlevelRates = vals;
	}

}
