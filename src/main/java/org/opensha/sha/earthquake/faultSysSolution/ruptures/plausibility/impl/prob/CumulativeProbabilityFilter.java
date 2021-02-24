package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;

import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * Plausibility filter that evaluates the probability of a given rupture against a threshold. Probabilities are defined
 * by RuptureProbabilityCalc instances.
 * 
 * @author kevin
 *
 */
public class CumulativeProbabilityFilter implements ScalarValuePlausibiltyFilter<Float> {
	
	static final DecimalFormat optionalDigitDF = new DecimalFormat("0.#");
	
	private float minProbability;
	private RuptureProbabilityCalc[] calcs;
	
	public CumulativeProbabilityFilter(float minProbability, RuptureProbabilityCalc... calcs) {
		Preconditions.checkArgument(minProbability > 0d && minProbability <= 1d,
				"Minimum probability (%s) not in the range (0,1]", minProbability);
		this.minProbability = minProbability;
		Preconditions.checkArgument(calcs.length > 0);
		this.calcs = calcs;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		float prob = getValue(rupture, verbose);
		if (verbose)
			System.out.println(getShortName()+": final prob="+prob+", pass="+(prob >= minProbability));
		if ((float)prob >= minProbability)
			return PlausibilityResult.PASS;
		return PlausibilityResult.FAIL_HARD_STOP;
	}

	@Override
	public String getShortName() {
		if (calcs.length > 1)
			return "CumProb≥"+minProbability;
		return "P("+calcs[0].getName().replaceAll(" ", "")+")≥"+minProbability;
	}

	@Override
	public String getName() {
		if (calcs.length > 1)
			return "Cumulative Probability Filter ≥"+minProbability;
		return calcs[0].getName()+" ≥"+minProbability;
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		return getValue(rupture, false);
	}

	public Float getValue(ClusterRupture rupture, boolean verbose) {
		double prob = 1d;
		for (RuptureProbabilityCalc calc : calcs) {
			double indvProb = calc.calcRuptureProb(rupture, verbose);
			if (verbose)
				System.out.println("\t"+calc.getName()+": P="+indvProb);
			Preconditions.checkState(indvProb >= 0d && indvProb <= 1d,
					"Bad probability for %s: %s\n\tRupture: %s", indvProb, calc.getName(), rupture);
			prob *= indvProb;
		}
		return (float)prob;
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return Range.closed(minProbability, 1f);
	}

	@Override
	public String getScalarName() {
		return "Conditional Probability";
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
		private ClusterConnectionStrategy connStrategy;
		private SectionDistanceAzimuthCalculator distAzCalc;

		@Override
		public void init(ClusterConnectionStrategy connStrategy, SectionDistanceAzimuthCalculator distAzCalc,
				Gson gson) {
			this.connStrategy = connStrategy;
			this.distAzCalc = distAzCalc;
			this.gson = gson;
		}

		@Override
		public void write(JsonWriter out, PlausibilityFilter value) throws IOException {
			Preconditions.checkState(value instanceof CumulativeProbabilityFilter);
			CumulativeProbabilityFilter filter = (CumulativeProbabilityFilter)value;
			out.beginObject();
			
			out.name("minProbability").value(filter.minProbability);
			out.name("calcs").beginArray();
			for (RuptureProbabilityCalc calc : filter.calcs) {
				out.beginObject();
				out.name("class").value(calc.getClass().getName());
				out.name("value");
				gson.toJson(calc, calc.getClass(), out);
				out.endObject();
			}
			out.endArray();
			
			out.endObject();
		}

		@Override
		public PlausibilityFilter read(JsonReader in) throws IOException {
			in.beginObject();
			
			Float minProbability = null;
			RuptureProbabilityCalc[] calcs = null;
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "minProbability":
					minProbability = (float)in.nextDouble();
					break;
				case "calcs":
					ArrayList<RuptureProbabilityCalc> list = new ArrayList<>();
					in.beginArray();
					while (in.hasNext()) {
						in.beginObject();
						
						Class<RuptureProbabilityCalc> type = null;
						RuptureProbabilityCalc calc = null;
						
						while (in.hasNext()) {
							switch (in.nextName()) {
							case "class":
								try {
									type = PlausibilityConfiguration.getDeclaredTypeClass(in.nextString());
								} catch (ClassNotFoundException e) {
									throw ExceptionUtils.asRuntimeException(e);
								}
								break;
							case "value":
								Preconditions.checkNotNull(type, "Class must preceed value in ProbCalc JSON");
								calc = gson.fromJson(in, type);
								break;

							default:
								throw new IllegalStateException("Unexpected JSON field");
							}
						}
						Preconditions.checkNotNull(calc, "Penalty is null?");
						calc.init(connStrategy, distAzCalc);
						list.add(calc);
						
						in.endObject();
					}
					in.endArray();
					Preconditions.checkState(!list.isEmpty(), "No prob calcs?");
					calcs = list.toArray(new RuptureProbabilityCalc[0]);
					break;

				default:
					throw new IllegalStateException("Unexpected JSON field");
				}
			}
			in.endObject();
			
			Preconditions.checkNotNull(minProbability, "threshold not supplied");
			Preconditions.checkNotNull(calcs, "penalties not supplied");
			return new CumulativeProbabilityFilter(minProbability, calcs);
		}
		
	}
	
	@Override
	public boolean isDirectional(boolean splayed) {
		for (RuptureProbabilityCalc calc : calcs)
			if (calc.isDirectional(splayed))
				return true;
		return false;
	}
	
	public static void main(String[] args) {
//		HighestNTracker track = new HighestNTracker(2);
//		List<Double> vals = new ArrayList<>();
//		double sum = 0d;
//		for (int i=0; i<20; i++) {
//			double val = (int)(Math.random()*100d + 0.5);
//			System.out.println("\tadding value: "+val);
//			track.addValue(val);
//			vals.add(val);
//			sum += val;
//			Collections.sort(vals);
//			System.out.println("Track has sum="+track.sum+"\thighest values: "+Joiner.on(",").join(Doubles.asList(track.highVals)));
//			System.out.println("List has sum="+sum+"\tsorted values: "+Joiner.on(",").join(vals));
//		}
//		System.exit(0);
//		BiasiWesnousky2016SSJumpProb jumpProb = new BiasiWesnousky2016SSJumpProb(0d);
//		
//		for (double len=0; len<=10d; len++) {
//			System.out.println("Length="+(float)len+"\tr="+(float)jumpProb.calcPassingRatio(len)
//				+"\tp="+(float)jumpProb.calcPassingProb(len));
//		}
//		
//		System.out.println("\nSS Azimuth Passing Ratios/Probs");
//		for (int i=0; i<bw2017_ss_passRatio.size(); i++) {
//			double x = bw2017_ss_passRatio.getX(i);
//			double ratio = bw2017_ss_passRatio.getY(i);
//			double prob = passingRatioToProb(ratio);
//			System.out.println("azDiff="+(float)x+"\tr="+(float)ratio+"\tp="+(float)prob);
//		}
	}

}
