package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.io.IOException;
import java.util.ArrayList;

import org.opensha.commons.data.Named;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter.AzimuthCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class CumulativePenaltyFilter implements ScalarValuePlausibiltyFilter<Float> {
	
	private float threshold;
	// if true, then avoid any 'double counting' for a single jump by taking the maximum penalty
	// for each individual jump, rather than summing all penalties for that jump
	private boolean noDoubleCount = false;
	private Penalty[] penalties;

	public static interface Penalty extends Named {
		
		public double calcPenalty(ClusterRupture fullRup, Jump jump);
		
	}
	
	public static class JumpPenalty implements Penalty {
		
		private float minDistance;
		private double penalty;
		private boolean isDistMultiplier;

		public JumpPenalty(float minDistance, double penalty, boolean isDistMultiplier) {
			this.minDistance = minDistance;
			this.penalty = penalty;
			this.isDistMultiplier = isDistMultiplier;
			
		}

		@Override
		public double calcPenalty(ClusterRupture fullRup, Jump jump) {
			if ((float)jump.distance <= minDistance)
				return 0;
			if (isDistMultiplier)
				return penalty*jump.distance;
			return penalty;
		}

		@Override
		public String getName() {
			return "Jump Penalty > "+minDistance+" km: "+penalty+(isDistMultiplier ? " (multiplier)" : "");
		}
		
	}
	
	public static class RakeChangePenalty implements Penalty {
		
		private float minDifference;
		private double penalty;
		private boolean isDiffMultiplier;

		public RakeChangePenalty(float minDifference, double penalty, boolean isDiffMultiplier) {
			this.minDifference = minDifference;
			this.penalty = penalty;
			this.isDiffMultiplier = isDiffMultiplier;
		}

		@Override
		public double calcPenalty(ClusterRupture fullRup, Jump jump) {
			double diff = CumulativeRakeChangeFilter.rakeDiff(
					jump.fromSection.getAveRake(), jump.toSection.getAveRake());
			if ((float)diff <= minDifference)
				return 0;
			if (isDiffMultiplier)
				return diff*penalty;
			return penalty;
		}

		@Override
		public String getName() {
			return "Rake Penalty > "+minDifference+" deg: "+penalty+(isDiffMultiplier ? " (multiplier)" : "");
		}
		
	}
	
	public static class DipChangePenalty implements Penalty {
		
		private float minDifference;
		private double penalty;
		private boolean isDiffMultiplier;

		public DipChangePenalty(float minDifference, double penalty, boolean isDiffMultiplier) {
			this.minDifference = minDifference;
			this.penalty = penalty;
			this.isDiffMultiplier = isDiffMultiplier;
		}

		@Override
		public double calcPenalty(ClusterRupture fullRup, Jump jump) {
			double diff = Math.abs(jump.fromSection.getAveDip() - jump.toSection.getAveDip());
			if ((float)diff <= minDifference)
				return 0;
			if (isDiffMultiplier)
				return diff*penalty;
			return penalty;
		}

		@Override
		public String getName() {
			return "Dip Change Penalty > "+minDifference+" deg: "+penalty+(isDiffMultiplier ? " (multiplier)" : "");
		}
		
	}
	
	public static class AzimuthChangePenalty implements Penalty {
		
		private float minDifference;
		private double penalty;
		private boolean isDiffMultiplier;
		private AzimuthCalc calc;

		public AzimuthChangePenalty(float minDifference, double penalty, boolean isDiffMultiplier,
				AzimuthCalc calc) {
			this.minDifference = minDifference;
			this.penalty = penalty;
			this.isDiffMultiplier = isDiffMultiplier;
			this.calc = calc;
		}

		@Override
		public double calcPenalty(ClusterRupture fullRup, Jump jump) {
			RuptureTreeNavigator nav = fullRup.getTreeNavigator();
			FaultSection before2 = jump.fromSection;
			FaultSection before1 = nav.getPredecessor(before2);
			if (before1 == null)
				return 0d;
			FaultSection after1 = jump.toSection;
			FaultSection after2s[];
			if (jump.toCluster.subSects.size() > 1) {
				after2s = new FaultSection[] { jump.toCluster.subSects.get(1) };
			} else if (fullRup.contains(after1)) {
				// this is an existing jump
				after2s = nav.getDescendants(after1).toArray(new FaultSection[0]);
			} else {
				// new jump to a single section, bail
				return 0d;
			}
			double beforeAz = calc.calcAzimuth(before1, before2);
			double max = 0d;
			for (FaultSection after2 : after2s) {
				double afterAz = calc.calcAzimuth(after1, after2);
				double diff = JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz);
				if ((float)diff > minDifference) {
					if (isDiffMultiplier)
						max = Math.max(max, diff*penalty);
					else
						max = Math.max(max, penalty);
				}
			}
			return max;
		}

		@Override
		public String getName() {
			return "Azimuth Penalty > "+minDifference+" deg: "+penalty;
		}
		
	}
	
	public CumulativePenaltyFilter(float threshold, boolean noDoubleCount, Penalty... penalties) {
		Preconditions.checkArgument(threshold > 0f, "Penalty threshold must be positive");
		this.threshold = threshold;
		Preconditions.checkArgument(penalties.length > 0, "Must supply at least 1 penalty");
		this.penalties = penalties;
		this.noDoubleCount = noDoubleCount;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		double sum = calcTotalPenalty(rupture, verbose);
		if ((float)sum > threshold)
			return PlausibilityResult.FAIL_HARD_STOP;
		return PlausibilityResult.PASS;
	}

	@Override
	public String getShortName() {
		return "CumPenalty";
	}

	@Override
	public String getName() {
		return "Cumulative Penalty Filter";
	}
	
	public double calcTotalPenalty(ClusterRupture rupture, boolean verbose) {
		double sum = 0d;
		for (Jump jump : rupture.getJumpsIterable())
			sum += calcJumpPenalty(rupture, jump, verbose);
		if (verbose)
			System.out.println(getShortName()+": total penalty: "+sum);
		return sum;
	}
	
	public double calcJumpPenalty(ClusterRupture rupture, Jump jump, boolean verbose) {
		double ret = 0d;
		if (verbose)
			System.out.println(getShortName()+": Jump="+jump);
		for (Penalty penalty : penalties) {
			double val = penalty.calcPenalty(rupture, jump);
			Preconditions.checkState(val >= 0d, "Penalty must be postitive. Jump=%s, %s=%s",
					jump, penalty.getName(), val);
			if (verbose)
				System.out.println("\t"+penalty.getName()+" = "+val);
			if (noDoubleCount)
				ret = Math.max(ret, val);
			else
				ret += val;
		}
		if (verbose)
			System.out.println("\taggregated jump penalty: "+ret);
		return ret;
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		return (float)calcTotalPenalty(rupture, false);
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return Range.closed(0f, threshold);
	}

	@Override
	public String getScalarName() {
		return "Penalty";
	}

	@Override
	public String getScalarUnits() {
		return null;
	}

	@Override
	public TypeAdapter<PlausibilityFilter> getTypeAdapter() {
		return new Adapter();
	}
	
	public static class Adapter extends PlausibilityFilterTypeAdapter {

		private Gson gson;

		@Override
		public void init(ClusterConnectionStrategy connStrategy, SectionDistanceAzimuthCalculator distAzCalc,
				Gson gson) {
			this.gson = gson;
		}

		@Override
		public void write(JsonWriter out, PlausibilityFilter value) throws IOException {
			Preconditions.checkState(value instanceof CumulativePenaltyFilter);
			CumulativePenaltyFilter filter = (CumulativePenaltyFilter)value;
			out.beginObject();
			
			out.name("threshold").value(filter.threshold);
			out.name("noDoubleCount").value(filter.noDoubleCount);
			out.name("penalties").beginArray();
			for (Penalty penalty : filter.penalties) {
				out.beginObject();
				out.name("class").value(penalty.getClass().getName());
				out.name("value");
				gson.toJson(penalty, penalty.getClass(), out);
				out.endObject();
			}
			out.endArray();
			
			out.endObject();
		}

		@Override
		public PlausibilityFilter read(JsonReader in) throws IOException {
			in.beginObject();
			
			Float threshold = null;
			boolean noDoubleCount = false;
			Penalty[] penalties = null;
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "threshold":
					threshold = (float)in.nextDouble();
					break;
				case "noDoubleCount":
					noDoubleCount = in.nextBoolean();
					break;
				case "penalties":
					ArrayList<Penalty> list = new ArrayList<>();
					in.beginArray();
					while (in.hasNext()) {
						in.beginObject();
						
						Class<Penalty> type = null;
						Penalty penalty = null;
						
						while (in.hasNext()) {
							switch (in.nextName()) {
							case "class":
								type = PlausibilityConfiguration.getDeclaredTypeClass(in.nextString());
								break;
							case "value":
								Preconditions.checkNotNull(type, "Class must preceed value in Penalty JSON");
								penalty = gson.fromJson(in, type);
								break;

							default:
								throw new IllegalStateException("Unexpected JSON field");
							}
						}
						Preconditions.checkNotNull(penalty, "Penalty is null?");
						list.add(penalty);
						
						in.endObject();
					}
					in.endArray();
					Preconditions.checkState(!list.isEmpty(), "No penalties?");
					penalties = list.toArray(new Penalty[0]);
					break;

				default:
					throw new IllegalStateException("Unexpected JSON field");
				}
			}
			in.endObject();
			
			Preconditions.checkNotNull(threshold, "threshold not supplied");
			Preconditions.checkNotNull(penalties, "penalties not supplied");
			return new CumulativePenaltyFilter(threshold, noDoubleCount, penalties);
		}
		
	}

}
