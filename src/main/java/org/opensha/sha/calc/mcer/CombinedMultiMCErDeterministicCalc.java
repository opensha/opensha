package org.opensha.sha.calc.mcer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.Site;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Combined deterministic calculation which returns the createst deterministic result of any sub results. Useful
 * when considering a range of GMPEs and you want the maximum value.
 * @author kevin
 *
 */
public class CombinedMultiMCErDeterministicCalc extends
		AbstractMCErDeterministicCalc {
	
	private List<AbstractMCErDeterministicCalc> calcs;
	
	public CombinedMultiMCErDeterministicCalc(AbstractMCErDeterministicCalc... calcs) {
		this(Lists.newArrayList(calcs));
	}
	
	public CombinedMultiMCErDeterministicCalc(List<? extends AbstractMCErDeterministicCalc> calcs) {
		Preconditions.checkArgument(!calcs.isEmpty());
		this.calcs = Collections.unmodifiableList(calcs);
	}

	@Override
	public Map<Double, DeterministicResult> calc(Site site,
			Collection<Double> periods) {
		Map<Double, DeterministicResult> ret = Maps.newHashMap();
		
		for (Double period : periods) {
			DeterministicResult maxResult = null;
			for (AbstractMCErDeterministicCalc calc : calcs) {
				DeterministicResult result = calc.calc(site, period);
				if (maxResult == null || result.getVal() > maxResult.getVal())
					maxResult = result;
			}
			Preconditions.checkNotNull(maxResult);
			ret.put(period, maxResult);
		}
		
		return ret;
	}

}
