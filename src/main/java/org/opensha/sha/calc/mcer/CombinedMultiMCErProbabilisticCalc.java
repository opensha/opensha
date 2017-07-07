package org.opensha.sha.calc.mcer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.sha.calc.hazardMap.HazardDataSetLoader;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Averages Probabilistic MCEr results from multiple calculators
 * @author kevin
 *
 */
public class CombinedMultiMCErProbabilisticCalc extends
		AbstractMCErProbabilisticCalc {
	
	private List<AbstractMCErProbabilisticCalc> calcs;
	private double weightEach;
	
	private boolean curveBased;
	
	public CombinedMultiMCErProbabilisticCalc(AbstractMCErProbabilisticCalc... calcs) {
		this(Lists.newArrayList(calcs));
	}
	
	public CombinedMultiMCErProbabilisticCalc(List<? extends AbstractMCErProbabilisticCalc> calcs) {
		Preconditions.checkArgument(!calcs.isEmpty());
		this.calcs = Collections.unmodifiableList(calcs);
		weightEach = 1d/(double)calcs.size();
		
		// see if they're all curve based
		curveBased = true;
		for (AbstractMCErProbabilisticCalc calc : calcs) {
			if (!(calc instanceof CurveBasedMCErProbabilisitCalc)) {
				curveBased = false;
				break;
			}
		}
	}

	@Override
	public DiscretizedFunc calc(Site site, Collection<Double> periods) {
		ArbitrarilyDiscretizedFunc result = new ArbitrarilyDiscretizedFunc();
		
		if (curveBased) {
			DiscretizedFunc xVals = null;
			for (double period : periods) {
				DiscretizedFunc avgCurve = null;
				for (AbstractMCErProbabilisticCalc calc : calcs) {
					CurveBasedMCErProbabilisitCalc curveCalc = (CurveBasedMCErProbabilisitCalc)calc;
					if (xVals != null && curveCalc instanceof GMPE_MCErProbabilisticCalc)
						((GMPE_MCErProbabilisticCalc)curveCalc).setXVals(xVals);
					DiscretizedFunc curve = curveCalc.calcHazardCurves(site, Lists.newArrayList(period)).get(period);
					if (xVals == null)
						xVals = curve.deepClone();
					if (avgCurve == null) {
						avgCurve = curve.deepClone();
						avgCurve.scale(weightEach);
					} else {
						Preconditions.checkState(avgCurve.size() == curve.size() && avgCurve.getX(0) == curve.getX(0));
						for (int i=0; i<avgCurve.size(); i++)
							avgCurve.set(i, avgCurve.getY(i) + weightEach*curve.getY(i));
					}
				}
				double rtgm;
				if (uhsVal > 0) {
					rtgm = HazardDataSetLoader.getCurveVal(avgCurve, false, uhsVal);
				} else {
					rtgm = calcRTGM(avgCurve);
				}
				Preconditions.checkState(rtgm > 0, "RTGM is not positive");
				
				result.set(period, rtgm);
			}
		} else {
			for (double period : periods) {
				double val = 0d;
				for (AbstractMCErProbabilisticCalc calc : calcs)
					val += weightEach*calc.calc(site, period);
				result.set(period, val);
			}
		}
		
		return result;
	}

}
