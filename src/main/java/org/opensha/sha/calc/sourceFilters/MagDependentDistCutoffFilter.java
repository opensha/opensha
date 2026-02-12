package org.opensha.sha.calc.sourceFilters;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.sha.calc.sourceFilters.params.MagDistCutoffParam;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.EqkSource;

/**
 * Magnitude-dependent distance cutoffs, generally applied at the rupture level, but also checked at the source level
 * against the largest stated cutoff 
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
	
	public MagDistCutoffParam getParam() {
		return magDist;
	}

	@Override
	public boolean canSkipSource(EqkSource source, Site site, double sourceSiteDistance) {
		// skip if it's further than the largest allowed cutoff
		return sourceSiteDistance > magDistFunc.getMaxX();
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
	
	@Override
	public String toString() {
		String str = "MagDistFunc=[";
		for (int i=0; i<magDistFunc.size(); i++) {
			if (i > 0)
				str += ", ";
			str += (float)magDistFunc.getX(i)+":"+(float)magDistFunc.getY(i);
		}
		str += "]";
		return str;
	}

}
