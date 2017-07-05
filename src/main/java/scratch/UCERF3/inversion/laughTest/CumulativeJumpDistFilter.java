package scratch.UCERF3.inversion.laughTest;

import java.util.List;
import java.util.Map;

import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import scratch.UCERF3.utils.IDPairing;

/**
 * This filter keeps track of the cumulative jump distance along a rupture and stops the
 * rupture at the given threshold.
 * 
 * @author kevin
 *
 */
public class CumulativeJumpDistFilter extends AbstractLaughTest {
	
	private Map<IDPairing, Double> distances;
	private double maxCmlJumpDist;
	
	public CumulativeJumpDistFilter(Map<IDPairing, Double> distances, double maxCmlJumpDist) {
		this.distances = distances;
		this.maxCmlJumpDist = maxCmlJumpDist;
	}

	@Override
	public boolean doesLastSectionPass(List<FaultSectionPrefData> rupture,
			List<IDPairing> pairings, List<Integer> junctionIndexes) {
		double dist = 0;
		for (int junctionIndex : junctionIndexes) {
			// index+1 here because pairing list starts with the second section
			IDPairing pair = pairings.get(junctionIndex+1);
			dist += distances.get(pair);
		}
		return dist <= maxCmlJumpDist;
	}

	@Override
	public boolean isContinueOnFaulure() {
		return false;
	}

	@Override
	public boolean isApplyJunctionsOnly() {
		return true;
	}

}
