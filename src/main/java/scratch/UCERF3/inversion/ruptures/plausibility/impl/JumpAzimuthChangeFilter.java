package scratch.UCERF3.inversion.ruptures.plausibility.impl;

import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.Jump;
import scratch.UCERF3.inversion.ruptures.plausibility.JunctionPlausibiltyFilter;
import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;

public class JumpAzimuthChangeFilter extends JunctionPlausibiltyFilter {
	
	private SectionDistanceAzimuthCalculator calc;
	private double threshold;
	private boolean flipLeftLateral;

	public JumpAzimuthChangeFilter(SectionDistanceAzimuthCalculator calc, double threshold, boolean flipLeftLateral) {
		this.calc = calc;
		this.threshold = threshold;
		this.flipLeftLateral = flipLeftLateral;
	}

	@Override
	public PlausibilityResult test(ClusterRupture rupture, Jump jump) {
		Preconditions.checkNotNull(jump.leadingSections, "Jump doesn't have leading sections populated");
		if (jump.leadingSections.size() < 2)
			// fewer than 2 sections before the first jump, will never work
			return PlausibilityResult.FAIL_HARD_STOP;
		if (jump.toCluster.subSects.size() < 2)
			// can't evaluate now, but could be a single section connection to another fault
			return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
		
		FaultSection before1 = jump.leadingSections.get(jump.leadingSections.size()-2);
		FaultSection before2 = jump.leadingSections.get(jump.leadingSections.size()-1);
		Preconditions.checkState(before2.equals(jump.fromSection));
		double beforeAz = calcAzimuth(calc, before1, before2, flipLeftLateral);
		
		FaultSection after1 = jump.toCluster.subSects.get(0);
		Preconditions.checkState(after1.equals(jump.toSection));
		FaultSection after2 = jump.toCluster.subSects.get(1);
		double afterAz = calcAzimuth(calc, after1, after2, flipLeftLateral);
		
		double diff = getAzimuthDifference(beforeAz, afterAz);
//		System.out.println(beforeAz+" => "+afterAz+" = "+diff);
		if (Math.abs(diff) <= threshold)
			return PlausibilityResult.PASS;
		
		return PlausibilityResult.FAIL_HARD_STOP;
	}
	
	static double calcAzimuth(SectionDistanceAzimuthCalculator calc,
			FaultSection sect1, FaultSection sect2, boolean flipLeftLateral) {
		if (flipLeftLateral && isLL(sect1) && isLL(sect2)) {
			FaultSection temp = sect1;
			sect1 = sect2;
			sect2 = temp;
		}
		return calc.getAzimuth(sect1, sect2);
	}
	
	static boolean isLL(FaultSection sect) {
		double rake = sect.getAveRake();
		return rake > -45d && rake < 45d;
	}
	
	/**
	 * This returns the change in strike direction in going from this azimuth1 to azimuth2,
	 * where these azimuths are assumed to be defined between -180 and 180 degrees.
	 * The output is between -180 and 180 degrees.
	 * @return
	 */
	static double getAzimuthDifference(double azimuth1, double azimuth2) {
		double diff = azimuth2 - azimuth1;
		if(diff>180)
			return diff-360;
		else if (diff<-180)
			return diff+360;
		else
			return diff;
	}

}
