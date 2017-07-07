package org.opensha.sha.calc.mcer;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Averages MCEr probabilistic calculations by averaging hazard curves before RTGM calculation.
 * @author kevin
 *
 */
public class WeightedAverageMCErProbabilisticCalc extends
	CurveBasedMCErProbabilisitCalc {
	
	private WeightProvider weightProv;
	private List<? extends CurveBasedMCErProbabilisitCalc> calcs;
	
	public WeightedAverageMCErProbabilisticCalc(WeightProvider weightProv,
			CurveBasedMCErProbabilisitCalc... calcs) {
		this(weightProv, Lists.newArrayList(calcs));
	}
	
	public WeightedAverageMCErProbabilisticCalc(WeightProvider weightProv,
			List<? extends CurveBasedMCErProbabilisitCalc> calcs) {
		this.weightProv = weightProv;
		this.calcs = calcs;
		
		Preconditions.checkState(!calcs.isEmpty());
	}

	@Override
	protected Map<Double, DiscretizedFunc> calcHazardCurves(Site site,
			Collection<Double> periods) {
		Map<Double, DiscretizedFunc> avgCurves = Maps.newHashMap();
		
		for (double period : periods) {
			ArbitrarilyDiscretizedFunc avgCurve = null;
			
			double totWeight = 0d;
			
			for (int i=0; i<calcs.size(); i++) {
				CurveBasedMCErProbabilisitCalc calc = calcs.get(i);
				double weight = weightProv.getProbWeight(calc, period);
				
				if (weight == 0d)
					continue;
				
				totWeight += weight;
				
				if (calc instanceof GMPE_MCErProbabilisticCalc && avgCurve != null)
					// force it to use correct x values
					((GMPE_MCErProbabilisticCalc)calc).setXVals(avgCurve);
				DiscretizedFunc curve = calc.calcHazardCurves(site, Lists.newArrayList(period)).get(period);
				
				if (avgCurve == null) {
					avgCurve = new ArbitrarilyDiscretizedFunc();
					for (int j=0; j<curve.size(); j++)
						avgCurve.set(curve.getX(j), 0);
				} else {
					Preconditions.checkState(avgCurve.size() == curve.size(), "x values mismatch between calculators");
					for (int j=0; j<curve.size(); j++)
						Preconditions.checkState((float)avgCurve.getX(j) == (float)curve.getX(j),
						"x values mismatch between calculators for p=%ss at pt %s. %s != %s",
						period, j, avgCurve.getX(j), curve.getX(j));
				}
				
				for (int j=0; j<curve.size(); j++)
					avgCurve.set(j, avgCurve.getY(j) + weight*curve.getY(j));
			}
			
			if (totWeight != 1d)
				avgCurve.scale(1d/totWeight);
			
			avgCurves.put(period, avgCurve);
		}
		
		return avgCurves;
	}

	@Override
	public void setXVals(DiscretizedFunc xVals) {
		for (CurveBasedMCErProbabilisitCalc calc : calcs)
			calc.setXVals(xVals);
	}

}
