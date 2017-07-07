package org.opensha.sha.calc.mcer;

import java.util.Collection;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public abstract class AbstractMCErDeterministicCalc {
	
	public static final double percentile = 84;
	
	public abstract Map<Double, DeterministicResult> calc(Site site, Collection<Double> periods);
	
	public DeterministicResult calc(Site site, double period) {
		Map<Double, DeterministicResult> result = calc(site, Lists.newArrayList(period));
		Preconditions.checkState(result.size() == 1);
		return result.get(period);
	}
	
	public static DiscretizedFunc toSpectrumFunc(Map<Double, DeterministicResult> results) {
		ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
		
		for (Double period : results.keySet())
			func.set(period, results.get(period).getVal());
		
		return func;
	}

}
