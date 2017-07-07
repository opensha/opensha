package org.opensha.sha.gcim.calc;

import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;

public class FakeFrankel96 extends Frankel96_AdjustableEqkRupForecast {

	@Override
	public int getNumSources() {
		return 1;
	}
}
