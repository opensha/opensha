package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.JumpPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.BoundType;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class JumpAzimuthChangeFilter extends JumpPlausibilityFilter
implements ScalarValuePlausibiltyFilter<Float> {
	
	private AzimuthCalc azCalc;
	private float threshold;
	
	private transient boolean errOnCantEval = false;

	public JumpAzimuthChangeFilter(AzimuthCalc calc, float threshold) {
		this.azCalc = calc;
		this.threshold = threshold;
	}
	
	/**
	 * This filter defaults to failing if a jump cannot be evaluated due to only 1 section
	 * on either side of a jump. It can be useful to identify those as errors, however, when
	 * evaluating an external rupture set (we don't want exceptions while building). If this is
	 * set to true, then exceptions will be thrown on evaluation errors.
	 * 
	 * @param errOnCantEval
	 */
	public void setErrOnCantEvaluate(boolean errOnCantEval) {
		this.errOnCantEval = errOnCantEval;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (errOnCantEval) {
			// return a failure from jumps any before throwing an exception
			PlausibilityResult result = PlausibilityResult.PASS;
			RuntimeException error = null;
			for (Jump jump : rupture.getJumpsIterable()) {
				if (!result.canContinue())
					return result;
				try {
					result = result.logicalAnd(testJump(rupture, jump, verbose));
				} catch (RuntimeException e) {
					error = e;
				}
//				if (verbose)
//					System.out.println("\t"+getShortName()+" applied at jump: "+jump+", result="+result);
			}
			if (error != null) {
				// we had an error
				if (!result.isPass())
					// return the failure instead
					return result;
				// throw the error
				throw error;
			}
			return result;
		}
		return super.apply(rupture, verbose);
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump jump, boolean verbose) {
		RuptureTreeNavigator navigator = rupture.getTreeNavigator();
		FaultSection before1 = navigator.getPredecessor(jump.fromSection);
		if (before1 == null) {
			// fewer than 2 sections before the first jump, will never work
			if (errOnCantEval)
				throw new IllegalStateException(getShortName()+": erring because fewer than "
						+ "2 sects before a jump");
			if (verbose)
				System.out.println(getShortName()+": failing because fewer than 2 before 1st jump");
			return PlausibilityResult.FAIL_HARD_STOP;
		}
		Float value = calc(rupture, jump, verbose);
		if (value == null) {
			if (errOnCantEval)
				throw new IllegalStateException(getShortName()+": erring because fewer than "
						+ "2 sects after a jump");
			return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
		}
		if (value > threshold)
			return PlausibilityResult.FAIL_HARD_STOP;
		return PlausibilityResult.PASS;
	}
	
	private Float calc(ClusterRupture rupture, Jump jump, boolean verbose) {
		RuptureTreeNavigator navigator = rupture.getTreeNavigator();
		FaultSection before1 = navigator.getPredecessor(jump.fromSection);
		if (before1 == null) {
			// fewer than 2 sections before the first jump, will never work
			if (verbose)
				System.out.println(getShortName()+": failing because fewer than 2 before 1st jump");
			return null;
		}
		FaultSection before2 = jump.fromSection;
		double beforeAz = azCalc.calcAzimuth(before1, before2);
		
		FaultSection after1 = jump.toSection;
		Collection<FaultSection> after2s;
		if (rupture.contains(after1)) {
			// this is a preexisting jump and can be a fork with multiple second sections after the jump
			// we will pass only if they all pass
			after2s = navigator.getDescendants(after1);
		} else {
			// we're testing a new possible jump
			if (jump.toCluster.subSects.size() < 2) {
				// it's a jump to a single-section cluster
				if (verbose)
					System.out.println(getShortName()+": jump to single-section cluster");
				return null;
			}
			after2s = Lists.newArrayList(jump.toCluster.subSects.get(1));
		}
		if (after2s.isEmpty()) {
			if (verbose)
				System.out.println(getShortName()+": jump to single-section cluster & nothing downstream");
			return null;
		}
		float maxVal = 0f;
		for (FaultSection after2 : after2s) {
			double afterAz = azCalc.calcAzimuth(after1, after2);
			double diff = getAzimuthDifference(beforeAz, afterAz);
			Preconditions.checkState(Double.isFinite(diff));
			if (verbose)
				System.out.println(getShortName()+": ["+before1.getSectionId()+","+before2.getSectionId()+"]="
						+beforeAz+" => ["+after1.getSectionId()+","+after2.getSectionId()+"]="+afterAz+" = "+diff);
			maxVal = Float.max(maxVal, (float)Math.abs(diff));
//			if ((float)Math.abs(diff) > threshold) {
////				System.out.println("AZ DEBUG: "+before1.getSectionId()+" "+before2.getSectionId()
////					+" => "+after1.getSectionId()+" and "+after2.getSectionId()+" after2: "+diff);
//				if (verbose)
//					System.out.println(getShortName()+": failing with diff="+diff);
//				return PlausibilityResult.FAIL_HARD_STOP;
//			}
		}
		return maxVal;
	}
	
	/**
	 * This returns the change in strike direction in going from this azimuth1 to azimuth2,
	 * where these azimuths are assumed to be defined between -180 and 180 degrees.
	 * The output is between -180 and 180 degrees.
	 * @return
	 */
	public static double getAzimuthDifference(double azimuth1, double azimuth2) {
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
	 * fault sections
	 * @author kevin
	 *
	 */
	public static class HardCodedLeftLateralFlipAzimuthCalc implements AzimuthCalc {

		private SectionDistanceAzimuthCalculator calc;
		private HashSet<Integer> parentIDs;

		public HardCodedLeftLateralFlipAzimuthCalc(SectionDistanceAzimuthCalculator calc,
				HashSet<Integer> parentIDs) {
			this.calc = calc;
			this.parentIDs = parentIDs;
		}

		@Override
		public double calcAzimuth(FaultSection sect1, FaultSection sect2) {
			if (parentIDs.contains(sect1.getParentSectionId()) && parentIDs.contains(sect2.getParentSectionId()))
				return calc.getAzimuth(sect2, sect1);
			return calc.getAzimuth(sect1, sect2);
		}
		
	}
	
	private static HashSet<Integer> getU3LeftLateralParents() {
		HashSet<Integer> parentIDs = new HashSet<Integer>();
		parentIDs.add(48);
		parentIDs.add(49);
		parentIDs.add(93);
		parentIDs.add(341);
		parentIDs.add(47);
		parentIDs.add(169);
		return parentIDs;
	}
	
	/**
	 * Azimuth calculation strategy which will reverse the direction of the hard-coded set of left lateral
	 * fault sections from UCERF3
	 * @author kevin
	 *
	 */
	public static class UCERF3LeftLateralFlipAzimuthCalc extends HardCodedLeftLateralFlipAzimuthCalc {

		public UCERF3LeftLateralFlipAzimuthCalc(SectionDistanceAzimuthCalculator calc) {
			super(calc, getU3LeftLateralParents());
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
	
	public static class AzimuthCalcTypeAdapter extends TypeAdapter<AzimuthCalc> {
		
		private SectionDistanceAzimuthCalculator distAzCalc;

		public AzimuthCalcTypeAdapter(SectionDistanceAzimuthCalculator distAzCalc) {
			this.distAzCalc = distAzCalc;
		}

		@Override
		public void write(JsonWriter out, AzimuthCalc calc) throws IOException {
			out.beginObject();
			if (calc instanceof LeftLateralFlipAzimuthCalc) {
				Range<Double> range = ((LeftLateralFlipAzimuthCalc)calc).rakeRange;
				String rangeStr = "";
				if (range.lowerBoundType() == BoundType.CLOSED)
					rangeStr += "[";
				else
					rangeStr += "(";
				rangeStr += range.lowerEndpoint()+","+range.upperEndpoint();
				if (range.upperBoundType() == BoundType.CLOSED)
					rangeStr += "]";
				else
					rangeStr = ")";
				out.name("leftLateralRange").value(rangeStr);
			} else if (calc instanceof HardCodedLeftLateralFlipAzimuthCalc) {
				out.name("leftLateralParents").beginArray();
				for (Integer parent : ((HardCodedLeftLateralFlipAzimuthCalc)calc).parentIDs)
					out.value(parent);
				out.endArray();
			} else {
				Preconditions.checkState(calc instanceof SimpleAzimuthCalc,
						"Don't know how to serialize this azimuth calculator");
			}
			
			out.endObject();
		}

		@Override
		public AzimuthCalc read(JsonReader in) throws IOException {
			in.beginObject();
			
			AzimuthCalc calc = null;
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "leftLateralRange":
					String str = in.nextString().trim();
					boolean lowerClosed;
					if (str.startsWith("[")) {
						lowerClosed = true;
					} else {
						lowerClosed = false;
						Preconditions.checkState(str.startsWith("("));
					}
					str = str.substring(1);
					boolean upperClosed;
					if (str.endsWith("]")) {
						upperClosed = true;
					} else {
						upperClosed = false;
						Preconditions.checkState(str.endsWith(")"));
					}
					str = str.substring(0, str.length()-1);
					String[] split = str.split(",");
					Preconditions.checkState(split.length == 2);
					double lower = Double.parseDouble(split[0]);
					double upper = Double.parseDouble(split[1]);
					
					Range<Double> range;
					if (lowerClosed && upperClosed)
						range = Range.closed(lower, upper);
					else if (lowerClosed && !upperClosed)
						range = Range.closedOpen(lower, upper);
					else if (!lowerClosed && upperClosed)
						range = Range.openClosed(lower, upper);
					else
						range = Range.open(lower, upper);
					
					calc = new LeftLateralFlipAzimuthCalc(distAzCalc, range);
					
					break;
					
				case "leftLateralParents":
					HashSet<Integer> parents = new HashSet<>();
					in.beginArray();
					while (in.hasNext())
						parents.add(in.nextInt());
					in.endArray();
					
					calc = new HardCodedLeftLateralFlipAzimuthCalc(distAzCalc, parents);
					
					break;

				default:
					break;
				}
			}
			
			if (calc == null)
				calc = new SimpleAzimuthCalc(distAzCalc);
			
			in.endObject();
			return calc;
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

	@Override
	public Float getValue(ClusterRupture rupture) {
		float max = 0f;
		for (Jump jump : rupture.getJumpsIterable()) {
			Float val = calc(rupture, jump, false);
			if (val == null)
				return val;
			max = Float.max(val, max);
		}
		return max;
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return Range.atMost(threshold);
	}
	
	@Override
	public String getScalarName() {
		return "Max Jump Azimuth Change";
	}

	@Override
	public String getScalarUnits() {
		return "Degrees";
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		// only directional if splayed
		return splayed;
	}

}
