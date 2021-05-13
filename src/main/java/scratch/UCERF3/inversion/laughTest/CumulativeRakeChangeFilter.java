package scratch.UCERF3.inversion.laughTest;

import java.util.List;
import java.util.Map;

import org.opensha.commons.util.IDPairing;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * This filter keeps track of the cumulative rake changes along a rupture and stops the
 * rupture at the given threshold.
 * 
 * NOTE: this has a bug which is preserved for compatibility with UCERF3. It applies the test
 * with double precision, and summation can cause tiny differences to accumulate. It should instead
 * be applied at 4-bi floating point (or less) precision.
 * 
 * @author kevin
 *
 */
public class CumulativeRakeChangeFilter extends AbstractPlausibilityFilter {
	
	private Map<Integer, Double> rakesMap;
	private double maxCmlRakeChange;
	
	private static final boolean D = false;
	
	public CumulativeRakeChangeFilter(Map<Integer, Double> rakesMap, double maxCmlRakeChange) {
		this.rakesMap = rakesMap;
		this.maxCmlRakeChange = maxCmlRakeChange;
	}

	@Override
	public PlausibilityResult applyLastSection(List<? extends FaultSection> rupture,
			List<IDPairing> pairings, List<Integer> junctionIndexes) {
		double cmlRakeChange = 0;
//		for (int junctionIndex : junctionIndexes) {
//			// index+1 here because pairing list starts with the second section
//			double rake1 = getRake(rupture.get(junctionIndex-1));
//			double rake2 = getRake(rupture.get(junctionIndex));
		for (int i=1; i<rupture.size(); i++) {
			FaultSection sect1 = rupture.get(i-1);
			FaultSection sect2 = rupture.get(i);
			double rake1 = getRake(sect1);
			double rake2 = getRake(sect2);
			double rakeDiff = Math.abs(rake1 - rake2);
			if (rakeDiff > 180)
				rakeDiff = 360-rakeDiff; // Deal with branch cut (180deg = -180deg)
			cmlRakeChange += rakeDiff;
			if (D && rakeDiff != 0d)
				System.out.println(getShortName()+": ["
						+sect1.getSectionId()+","+sect2.getSectionId()+"]="+rakeDiff+"\ttot="+cmlRakeChange);
		}
		if (D) System.out.println(getShortName()+": total="+cmlRakeChange);
		if (cmlRakeChange <= maxCmlRakeChange)
			return PlausibilityResult.PASS;
		return PlausibilityResult.FAIL_HARD_STOP;
	}
	
	private double getRake(FaultSection sect) {
		if (rakesMap == null)
			return sect.getAveRake();
		return rakesMap.get(sect.getSectionId());
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
