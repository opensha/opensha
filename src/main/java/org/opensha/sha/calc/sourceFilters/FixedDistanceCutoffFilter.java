 package org.opensha.sha.calc.sourceFilters;

import org.opensha.commons.data.Site;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.sha.calc.sourceFilters.params.MaxDistanceParam;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.EqkSource;

/**
 * Fixed distance cutoff, applied at the source and rupture level
 */
public class FixedDistanceCutoffFilter implements SourceFilter, ParameterChangeListener {
	
	private MaxDistanceParam distParam;
	private double maxDist;
	private ParameterList params;
	
	public FixedDistanceCutoffFilter() {
		distParam = new MaxDistanceParam();
		maxDist = distParam.getValue();
		distParam.addParameterChangeListener(this);
		
		params = new ParameterList();
		params.addParameter(distParam);
	}
	
	public FixedDistanceCutoffFilter(double maxDistance) {
		this();
		distParam.setValue(maxDistance);
	}
	
	public void setMaxDistance(double maxDist) {
		distParam.setValue(maxDist);
	}
	
	public double getMaxDistance() {
		return maxDist;
	}
	
	public MaxDistanceParam getParam() {
		return distParam;
	}

	@Override
	public boolean canSkipSource(EqkSource source, Site site, double sourceSiteDistance) {
		return sourceSiteDistance > maxDist;
	}

	@Override
	public boolean canSkipRupture(EqkRupture rup, Site site) {
		return rup.getRuptureSurface().getQuickDistance(site.getLocation()) > maxDist;
	}

	@Override
	public ParameterList getAdjustableParams() {
		return params;
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		maxDist = distParam.getValue();
	}
	
	@Override
	public String toString() {
		return "MaxDist="+(float)maxDist;
	}

}
