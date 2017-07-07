package org.opensha.sha.calc.mcer;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.Site;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Averages MCEr deterministic calculations by just averaging the spectrum curves. Metadata is removed from
 * deterministic results unless the same source was used for each.
 * @author kevin
 *
 */
public class WeightedAverageMCErDeterministicCalc extends
		AbstractMCErDeterministicCalc {
	
	private WeightProvider weightProv;
	private List<? extends AbstractMCErDeterministicCalc> calcs;
	
	public WeightedAverageMCErDeterministicCalc(WeightProvider weightProv,
			AbstractMCErDeterministicCalc... calcs) {
		this(weightProv, Lists.newArrayList(calcs));
	}
	
	public WeightedAverageMCErDeterministicCalc(WeightProvider weightProv,
			List<? extends AbstractMCErDeterministicCalc> calcs) {
		this.weightProv = weightProv;
		this.calcs = calcs;
		
		Preconditions.checkState(!calcs.isEmpty());
	}

	@Override
	public Map<Double, DeterministicResult> calc(Site site,
			Collection<Double> periods) {
		Map<Double, DeterministicResult> avgResult = Maps.newHashMap();
		
//		List<Map<Double, DeterministicResult>> subResults = Lists.newArrayList();
//		for (AbstractMCErDeterministicCalc calc : calcs)
//			subResults.add(calc.calc(site, periods));
		
		for (Double period : periods) {
			DeterministicResult avg = null;
			
			double totWeight = 0d;
			
			for (int i=0; i<calcs.size(); i++) {
				double weight = weightProv.getDetWeight(calcs.get(i), period);
				if (weight == 0d)
					continue;
				
				totWeight += weight;
				
//				DeterministicResult subResult = subResults.get(i).get(period);
				DeterministicResult subResult = calcs.get(i).calc(site, period);
				
				if (avg == null) {
					avg = (DeterministicResult)subResult.clone();
					avg.setVal(avg.getVal()*weight);
				} else {
					avg.setVal(avg.getVal() + subResult.getVal()*weight);
					if (subResult.getSourceID() != avg.getSourceID() || subResult.getRupID() != avg.getRupID()) {
						// clear out metadata as not relevant
						if (avg.getSourceID() >= 0) // don't bother if we've already cleared it
							avg = new DeterministicResult(-1, -1, Double.NaN, "(average of multiple)", avg.getVal());
					}
				}
			}
			
			if (totWeight != 1d)
				avg.setVal(avg.getVal()/totWeight);
			
			avgResult.put(period, avg);
		}
		
		return avgResult;
	}

}
