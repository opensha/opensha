package org.opensha.sha.calc.params.filters;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.sha.calc.params.MagDistCutoffParam;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.EqkSource;

/**
 * Magnitude-dependent distance cutoffs, applied at the rupture level ({@link #canSkipSource(EqkSource, Site, double)} always returns false).
 */
public class MagDependentDistCutoffFilter implements SourceFilter, ParameterChangeListener {
	
	private MagDistCutoffParam magDist;
	private ArbitrarilyDiscretizedFunc magDistFunc;
	private ParameterList params;
	
	public MagDependentDistCutoffFilter() {
		magDist = new MagDistCutoffParam();
		magDist.addParameterChangeListener(this);
		magDistFunc = magDist.getValue();
		
		params = new ParameterList();
		params.addParameter(magDist);
	}
	
	public void setMagDistFunc(ArbitrarilyDiscretizedFunc magDistFunc) {
		this.magDist.setValue(magDistFunc);
	}
	
	public ArbitrarilyDiscretizedFunc getMagDistFunc() {
		return magDistFunc;
	}

	@Override
	public boolean canSkipSource(EqkSource source, Site site, double sourceSiteDistance) {
		// never skip a source, this is done on a per-rupture basis
		return false;
	}

	@Override
	public boolean canSkipRupture(EqkRupture rup, Site site) {
		double dist = rup.getRuptureSurface().getQuickDistance(site.getLocation());
		if (dist < magDistFunc.getMinX())
			// closer than the smallest cutoff, don't skip
			return false;
		if (dist > magDistFunc.getMaxX())
			// further than the largest cutoff, always skip
			return true;
		double magThresh = magDistFunc.getInterpolatedY(dist);
		// skip if the magnitude is less than the distance-dependent threshold
		return rup.getMag() < magThresh;
	}

	@Override
	public ParameterList getAdjustableParams() {
		return params;
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		magDistFunc = magDist.getValue();
	}

}
