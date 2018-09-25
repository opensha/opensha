package scratch.UCERF3.inversion.laughTest;

import java.util.List;
import java.util.Map;

import org.opensha.commons.util.IDPairing;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

/**
 * This filter keeps track of the cumulative rake changes along a rupture and stops the
 * rupture at the given threshold.
 * 
 * @author kevin
 *
 */
public class CumulativeRakeChangeFilter extends AbstractLaughTest {
	
	private Map<Integer, Double> rakesMap;
	private double maxCmlRakeChange;
	
	public CumulativeRakeChangeFilter(Map<Integer, Double> rakesMap, double maxCmlRakeChange) {
		this.rakesMap = rakesMap;
		this.maxCmlRakeChange = maxCmlRakeChange;
	}

	@Override
	public boolean doesLastSectionPass(List<FaultSectionPrefData> rupture,
			List<IDPairing> pairings, List<Integer> junctionIndexes) {
		double cmlRakeChange = 0;
//		for (int junctionIndex : junctionIndexes) {
//			// index+1 here because pairing list starts with the second section
//			double rake1 = getRake(rupture.get(junctionIndex-1));
//			double rake2 = getRake(rupture.get(junctionIndex));
		for (int i=1; i<rupture.size(); i++) {
			double rake1 = getRake(rupture.get(i-1));
			double rake2 = getRake(rupture.get(i));
			double rakeDiff = Math.abs(rake1 - rake2);
			if (rakeDiff > 180)
				rakeDiff = 360-rakeDiff; // Deal with branch cut (180deg = -180deg)
			cmlRakeChange += rakeDiff;
		}
		return cmlRakeChange <= maxCmlRakeChange;
	}
	
	private double getRake(FaultSectionPrefData sect) {
		if (rakesMap == null)
			return sect.getAveRake();
		return rakesMap.get(sect.getSectionId());
	}

	@Override
	public boolean isContinueOnFaulure() {
		return false;
	}

	@Override
	public boolean isApplyJunctionsOnly() {
		return true;
	}
	
	@Override
	public String getName() {
		return "Cumulative Rake Change Filter";
	}
	
	@Override
	public String getShortName() {
		return "CumRake";
	}

}
