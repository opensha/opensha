package org.opensha.sha.calc.sourceFilters;

import org.opensha.commons.data.Site;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.sha.calc.sourceFilters.params.MinMagnitudeParam;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.EqkSource;

public class MinMagFilter implements SourceFilter, ParameterChangeListener {
	
	private MinMagnitudeParam param;
	private double minMag;
	private ParameterList params;
	
	public MinMagFilter() {
		param = new MinMagnitudeParam();
		minMag = param.getValue();
		param.addParameterChangeListener(this);
		
		params = new ParameterList();
		params.addParameter(param);
	}
	
	public void setMinMagnitude(double minMag) {
		param.setValue(minMag);
	}
	
	public double getMinMagnitude() {
		return minMag;
	}

	@Override
	public boolean canSkipSource(EqkSource source, Site site, double sourceSiteDistance) {
		// applied at the rupture level
		return false;
	}

	@Override
	public boolean canSkipRupture(EqkRupture rup, Site site) {
		return rup.getMag() < minMag;
	}

	@Override
	public ParameterList getAdjustableParams() {
		return params;
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		minMag = param.getValue();
	}
	
	@Override
	public String toString() {
		return "MinMag="+(float)minMag;
	}

}
