package scratch.UCERF3.inversion.ruptures.plausibility.impl;

import java.util.Collection;
import java.util.HashSet;

import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.ClusterRupture;
import scratch.UCERF3.inversion.ruptures.Jump;
import scratch.UCERF3.inversion.ruptures.plausibility.JumpPlausibilityFilter;
import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;

public class JumpAzimuthChangeFilter extends JumpPlausibilityFilter {
	
	private AzimuthCalc calc;
	private float threshold;

	public JumpAzimuthChangeFilter(AzimuthCalc calc, float threshold) {
		this.calc = calc;
		this.threshold = threshold;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump jump, boolean verbose) {
		FaultSection before1 = rupture.sectPredecessorsMap.get(jump.fromSection);
		if (before1 == null) {
			// fewer than 2 sections before the first jump, will never work
			if (verbose)
				System.out.println(getShortName()+": failing because fewer than 2 before 1st jump");
			return PlausibilityResult.FAIL_HARD_STOP;
		}
		FaultSection before2 = jump.fromSection;
		double beforeAz = calc.calcAzimuth(before1, before2);
		
		FaultSection after1 = jump.toSection;
		Collection<FaultSection> after2s;
		if (rupture.contains(after1)) {
			// this is a preexisting jump and can be a fork with multiple second sections after the jump
			// we will pass only if they all pass
			after2s = rupture.sectDescendantsMap.get(after1);
		} else {
			// we're testing a new possible jump
			if (jump.toCluster.subSects.size() < 2) {
				// it's a jump to a single-section cluster
				if (verbose)
					System.out.println(getShortName()+": jump to single-section cluster");
				return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
			}
			after2s = Lists.newArrayList(jump.toCluster.subSects.get(1));
		}
		if (after2s.isEmpty()) {
			if (verbose)
				System.out.println(getShortName()+": jump to single-section cluster & nothing downstream");
			return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
		}
		for (FaultSection after2 : after2s) {
			double afterAz = calc.calcAzimuth(after1, after2);
			double diff = getAzimuthDifference(beforeAz, afterAz);
			Preconditions.checkState(Double.isFinite(diff));
			if (verbose)
				System.out.println(getShortName()+": ["+before1.getSectionId()+","+before2.getSectionId()+"]="
						+beforeAz+" => ["+after1.getSectionId()+","+after2.getSectionId()+"]="+afterAz+" = "+diff);
			if ((float)Math.abs(diff) > threshold) {
//				System.out.println("AZ DEBUG: "+before1.getSectionId()+" "+before2.getSectionId()
//					+" => "+after1.getSectionId()+" and "+after2.getSectionId()+" after2: "+diff);
				if (verbose)
					System.out.println(getShortName()+": failing with diff="+diff);
				return PlausibilityResult.FAIL_HARD_STOP;
			}
		}
		return PlausibilityResult.PASS;
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
	
	public interface AzimuthCalc {
		public double calcAzimuth(FaultSection sect1, FaultSection sect2);
	}
	
	/**
	 * Azimuth calculation strategy which will reverse the direction of left lateral fault sections,
	 * as defined by section rakes within the given range
	 * @author kevin
	 *
	 */
	public static class LeftLateralFlipAzimuthCalc implements AzimuthCalc {

		private SectionDistanceAzimuthCalculator calc;
		private Range<Double> rakeRange;

		public LeftLateralFlipAzimuthCalc(SectionDistanceAzimuthCalculator calc, Range<Double> rakeRange) {
			this.calc = calc;
			this.rakeRange = rakeRange;
		}

		@Override
		public double calcAzimuth(FaultSection sect1, FaultSection sect2) {
			if (rakeRange.contains(sect1.getAveRake()) && rakeRange.contains(sect2.getAveRake()))
				return calc.getAzimuth(sect2, sect1);
			return calc.getAzimuth(sect1, sect2);
		}
		
	}
	
	/**
	 * Azimuth calculation strategy which will reverse the direction of the hard-coded set of left lateral
	 * fault sections from UCERF3
	 * @author kevin
	 *
	 */
	public static class UCERF3LeftLateralFlipAzimuthCalc implements AzimuthCalc {

		private SectionDistanceAzimuthCalculator calc;
		private HashSet<Integer> parentIDs;

		public UCERF3LeftLateralFlipAzimuthCalc(SectionDistanceAzimuthCalculator calc) {
			this.calc = calc;
			parentIDs = new HashSet<Integer>();
			parentIDs.add(48);
			parentIDs.add(49);
			parentIDs.add(93);
			parentIDs.add(341);
			parentIDs.add(47);
			parentIDs.add(169);
		}

		@Override
		public double calcAzimuth(FaultSection sect1, FaultSection sect2) {
			if (parentIDs.contains(sect1.getParentSectionId()) && parentIDs.contains(sect2.getParentSectionId()))
				return calc.getAzimuth(sect2, sect1);
			return calc.getAzimuth(sect1, sect2);
		}
		
	}
	
	/**
	 * Simple azimuth calculation with no special treatment for left-lateral faults
	 * @author kevin
	 *
	 */
	public static class SimpleAzimuthCalc implements AzimuthCalc {

		private SectionDistanceAzimuthCalculator calc;

		public SimpleAzimuthCalc(SectionDistanceAzimuthCalculator calc) {
			this.calc = calc;
		}

		@Override
		public double calcAzimuth(FaultSection sect1, FaultSection sect2) {
			return calc.getAzimuth(sect1, sect2);
		}
		
	}

	@Override
	public String getShortName() {
		return "JumpAz";
	}

	@Override
	public String getName() {
		return "Jump Azimuth Change Filter";
	}

}
