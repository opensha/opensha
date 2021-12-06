package org.opensha.sha.earthquake.faultSysSolution.modules;

import org.opensha.commons.util.modules.helpers.AbstractDoubleArrayCSV_BackedModule;

import com.google.common.base.Preconditions;

public class WaterLevelRates extends AbstractDoubleArrayCSV_BackedModule.Averageable<WaterLevelRates>
implements BranchAverageableModule<WaterLevelRates> {
	
	@SuppressWarnings("unused") // used in deserialization
	private WaterLevelRates() {
		super();
	}
	
	public WaterLevelRates(double[] waterlevelRates) {
		super(waterlevelRates);
	}
	
	public double[] subtractFrom(double[] rates) {
		Preconditions.checkState(rates.length == values.length);
		double[] ret = new double[rates.length];
		for (int i=0; i<rates.length; i++) {
			ret[i] = rates[i] - values[i];
			
			// deal with floating point precision issues
			if (ret[i] < 0) {
				// can happen if post-water-level rates are averaged
				Preconditions.checkState(ret[i] > -1e-20);
				ret[i] = 0d;
			} else if (ret[i] < 1e-20 || (float)rates[i] == (float)values[i]) {
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
	protected WaterLevelRates averageInstance(double[] avgValues) {
		return new WaterLevelRates(avgValues);
	}

	@Override
	protected String getIndexHeading() {
		return "Rupture Index";
	}

	@Override
	protected String getValueHeading() {
		return "Water Level Rate";
	}

}
