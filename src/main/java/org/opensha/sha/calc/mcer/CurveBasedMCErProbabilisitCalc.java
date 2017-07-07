package org.opensha.sha.calc.mcer;

import java.util.Collection;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.sha.calc.hazardMap.HazardDataSetLoader;

import com.google.common.base.Preconditions;

public abstract class CurveBasedMCErProbabilisitCalc extends
		AbstractMCErProbabilisticCalc {

	@Override
	public DiscretizedFunc calc(Site site, Collection<Double> periods) {
		Map<Double, DiscretizedFunc> curves = calcHazardCurves(site, periods);
		
		Preconditions.checkArgument(!curves.isEmpty(), "curves map empty!");
		ArbitrarilyDiscretizedFunc spectrum = new ArbitrarilyDiscretizedFunc();
		
		for (Double period : curves.keySet()) {
			DiscretizedFunc curve = curves.get(period);
			
			double rtgm;
			if (uhsVal > 0) {
				rtgm = HazardDataSetLoader.getCurveVal(curve, false, uhsVal);
			} else {
				rtgm = calcRTGM(curve);
			}
			Preconditions.checkState(rtgm > 0, "RTGM is not positive");
			
			spectrum.set(period, rtgm);
		}
		
		return spectrum;
	}
	
	protected abstract Map<Double, DiscretizedFunc> calcHazardCurves(Site site, Collection<Double> periods);
	
	public abstract void setXVals(DiscretizedFunc xVals);

}
