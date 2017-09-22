package scratch.UCERF3.inversion.laughTest;

import java.util.List;
import java.util.Map;

import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import com.google.common.base.Preconditions;

import scratch.UCERF3.utils.IDPairing;

/**
 * This is a squirreliness filter which keeps track of the cumulative azimuthal change along
 * a rupture and stops the rupture at the given threshold.
 * 
 * UCERF3.2 or before implementations contained a bug which didn't handle the 0/360 degree
 * boundary correctly.
 * @author kevin
 *
 */
public class CumulativeAzimuthChangeFilter extends AbstractLaughTest {
	
	private boolean USE_BUGGY_AZ_CHANGE = false;
	
	private Map<IDPairing, Double> azimuths;
	private double maxCmlAzimuthChange;
	
	public CumulativeAzimuthChangeFilter(Map<IDPairing, Double> azimuths, double maxCmlAzimuthChange) {
		this.azimuths = azimuths;
		this.maxCmlAzimuthChange = maxCmlAzimuthChange;
	}
	
	public void setBuggyAzChange(boolean buggyAzChange) {
		this.USE_BUGGY_AZ_CHANGE = buggyAzChange;
		if (USE_BUGGY_AZ_CHANGE)
			System.err.println("WARNING: CumulativeAzimuthChangeFilter has buggy implementation with respect " +
				"to the 0/360 boundary. Bug left in for compatibility but should be removed for next runs");
	}

	@Override
	public boolean doesLastSectionPass(List<FaultSectionPrefData> rupture,
			List<IDPairing> pairings, List<Integer> junctionIndexes) {
		double cmlAzimuthChange = 0;
		for (int i=1; i<pairings.size(); i++) {
			double prevAzimuth = azimuths.get(pairings.get(i-1));
			double newAzimuth = azimuths.get(pairings.get(i));
			if (USE_BUGGY_AZ_CHANGE)
				cmlAzimuthChange += Math.abs(newAzimuth - prevAzimuth);
			else
				cmlAzimuthChange += Math.abs(AzimuthChangeFilter.getAzimuthDifference(
						newAzimuth, prevAzimuth));
		}
		return cmlAzimuthChange <= maxCmlAzimuthChange;
	}

	@Override
	public boolean isContinueOnFaulure() {
		return false;
	}

	@Override
	public boolean isApplyJunctionsOnly() {
		return false;
	}
	
	@Override
	public String getName() {
		return "Cumulative Azimuth Filter";
	}
	
	@Override
	public String getShortName() {
		return "CumAzimuth";
	}

}
