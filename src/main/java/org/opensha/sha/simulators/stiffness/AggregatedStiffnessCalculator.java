package org.opensha.sha.simulators.stiffness;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.zip.ZipException;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.summary.Sum;
import org.dom4j.DocumentException;
import org.opensha.commons.util.DataUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessDistribution;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.utils.U3FaultSystemIO;

/**
 * Stiffness (Coulomb) calculations are done between pairs of individual patches. Most faults are not
 * well represented by a single patch, so we instead divide them into many smaller patches and calculate
 * stiffness between patches. Ultimately, we want some aggregate measure of compatibility, such as the sum
 * of all interactions or the fraction that are positive. This class performs that aggregation in a flexible
 * manner consisting of layers of aggregation.
 * <br><br>
 * There are 4 possible layers of aggregation (places where aggregation occurs):
 * <ol>
 * <li>At the receiver patch level, across all source patches, for a given section-to-section pair</li>
 * <li>At the section-to-section level (1 source section, 1 receiver section, each with multiple patches)</li>
 * <li>At the sections-to-section level (N source sections, 1 receiver section, each with multiple patches)</li>
 * <li>At the sections-to-sections level (N source sections, M receiver sections, each with multiple patches)</li>
 * </ol>
 * You must specify the aggregation behavior at each of those levels, each specified as an {@link AggregationMethod}.:
 * <br><ol>
 * <li>Process: regular single statistic from each distribution of values, e.g., sum, median, mean, fraction positive</li>
 * <li>Flatten: combine all distributions of values as input to this layer into a single (flattened) distribution,
 * from which a single statistic can be calculated if passed to a process layer</li>
 * <li>Passthrough: don't do any aggregation at this layer, pass each full distribution onto the next layer</li>
 * <li>Receiver Sum: Flatten at the receiverID level (be that a section or patch ID), and then take the sum of that distribution
 * of values for that receiver</li>
 * </ol>
 * <br>
 * In practice, you will usually want to perform a process aggregation at either the first (receiver patch) or second
 * (receiver section) levels. For example, to get the median of all section-to-section patch interactions, the ordering
 * would be: Flatten -> Median. But if you wanted to instead get the fraction of receiver patches which are net positive,
 * you would do: Sum -> Fact. Positive. You must always end with a process layer to compute a final statistic.
 * 
 * @author kevin
 *
 */
public class AggregatedStiffnessCalculator {

	private static final boolean D = false;
	private static final boolean DD = D && false;

	private StiffnessType type;
	private SubSectStiffnessCalculator calc;
	private transient AggregatedStiffnessCache cache;

	public static final int MAX_LAYERS = 4;
	private AggregationMethod[] layers;
	private boolean allowSectToSelf = false;

	public static final EnumSet<AggregationMethod> CACHEABLE_AGG_METHODS = EnumSet.range(AggregationMethod.MEAN,
			AggregationMethod.COUNT);
	private static final int CACHE_ARRAY_SIZE;
	static {
		int maxTerminalIndex = -1;
		for (AggregationMethod method : AggregationMethod.values()) {
			if (CACHEABLE_AGG_METHODS.contains(method)) {
				Preconditions.checkState(method.isTerminal());
				maxTerminalIndex = Integer.max(maxTerminalIndex, method.ordinal());
			}
		}
		CACHE_ARRAY_SIZE = maxTerminalIndex + 1;
	}

	// multiplier to use for section IDs, to which patch IDs are added to get unique patch IDs
	private transient int patch_sect_id_multiplier = -1;
	private transient int patch_sect_id_add = -1;

	public static class StiffnessAggregation {

		private final double[] aggValues;
		private double interactionWeight;
		//		private final int numValues;

		public StiffnessAggregation(double[] values, long interactionCount) {
			this(AggregationInput.cached(values, null, interactionCount, 1), interactionCount);
		}

		StiffnessAggregation(ReceiverDistribution distribution) {
			this(AggregationInput.cached(distribution.values, distribution.weights, distribution.totNumInteractions,
					distribution.sourceSamples ? distribution.receiverWeight : 1), distribution.totInteractionWeight);
		}

		private StiffnessAggregation(AggregationInput input, double interactionWeight) {
			this.interactionWeight = interactionWeight;
			this.aggValues = new double[CACHE_ARRAY_SIZE];
			for (AggregationMethod method : CACHEABLE_AGG_METHODS)
				aggValues[method.ordinal()] = input.get(method);
		}

		public double getInteractionWeight() {
			return interactionWeight;
		}

		StiffnessAggregation(AggregationMethod[] methods, double[] aggValues) {
			Preconditions.checkState(methods.length == aggValues.length);
			this.aggValues = new double[CACHE_ARRAY_SIZE];
			for (int i = 0; i < this.aggValues.length; i++)
				this.aggValues[i] = Double.NaN;
			for (int i = 0; i < methods.length; i++)
				this.aggValues[methods[i].ordinal()] = aggValues[i];
			interactionWeight = get(AggregationMethod.COUNT);
		}

		StiffnessAggregation(AggregationMethod[] methods, double[] values, double interactionWeight) {
			this(methods, values);
			this.interactionWeight = interactionWeight;
		}

		public double get(AggregationMethod aggMethod) {
			Preconditions.checkState(aggMethod.isTerminal(), "Can only cache values for terminal layers");
			return aggValues[aggMethod.ordinal()];
		}
	}

	/**
	 * Sample data and optional memoized results; statistic definitions live in the
	 * enum. A batch cache reuses sums, sign weights and the median across methods.
	 * The divisor removes receiver area from additive source-stage results only.
	 */
	private static final class AggregationInput {
		final double[] values;
		final double[] weights;
		final long interactionCount;
		final double totalWeight;
		private final double receiverWeight;
		private double[] cachedValues;
		private boolean sorted;

		AggregationInput(double[] values, double[] weights, long interactionCount, double receiverWeight) {
			this.values = values;
			this.weights = weights;
			this.interactionCount = interactionCount;
			this.receiverWeight = receiverWeight;
			double totalWeight = values.length;
			if (weights != null) {
				Preconditions.checkArgument(values.length == weights.length && values.length > 0,
						"Invalid weighted samples");
				totalWeight = 0;
				for (int i = 0; i < values.length; i++) {
					Preconditions.checkState(Double.isFinite(values[i]),
							"Nonfinite stiffness in weighted statistics: %s", values[i]);
					Preconditions.checkArgument(Double.isFinite(weights[i]) && weights[i] > 0, "Invalid area weight");
					totalWeight += weights[i];
				}
				Preconditions.checkArgument(Double.isFinite(totalWeight), "Nonfinite total area weight");
			}
			this.totalWeight = totalWeight;
		}

		static AggregationInput cached(double[] values, double[] weights, long count, double receiverWeight) {
			AggregationInput input = new AggregationInput(values, weights, count, receiverWeight);
			if (weights == null) {
				// Preserve the original cache constructor's sorting and summation order.
				Arrays.sort(values);
				input.sorted = true;
			}
			input.cachedValues = new double[CACHE_ARRAY_SIZE];
			Arrays.fill(input.cachedValues, Double.NaN);
			return input;
		}

		double get(AggregationMethod method) {
			double result = getRaw(method);
			if (weights != null && method.additive)
				result /= receiverWeight;
			return result;
		}

		double getRaw(AggregationMethod method) {
			int index = method.ordinal();
			if (cachedValues != null && index < cachedValues.length && !Double.isNaN(cachedValues[index]))
				return cachedValues[index];
			double result = method.calculate(this);
			if (cachedValues != null && index < cachedValues.length)
				cachedValues[index] = result;
			return result;
		}
	}

	public enum AggregationMethod {
		MEAN("Mean", true, true) {
			@Override
			protected double calculate(AggregationInput input) {
				return input.weights == null ? StatUtils.mean(input.values)
						: new Mean().evaluate(input.values, input.weights);
			}
		},
		MEDIAN("Median", true, true) {
			@Override
			protected double calculate(AggregationInput input) {
				if (input.weights != null)
					return DataUtils.weightedMedian(input.values, input.weights);
				if (!input.sorted) {
					Arrays.sort(input.values);
					input.sorted = true;
				}
				return DataUtils.median_sorted(input.values);
			}
		},
		SUM("Sum", true, true, true) {
			@Override
			protected double calculate(AggregationInput input) {
				return input.weights == null ? StatUtils.sum(input.values)
						: new Sum().evaluate(input.values, input.weights);
			}

			@Override
			public boolean isSplittable() {
				return true;
			}
		},
		MIN("Minimum", true, true) {
			@Override
			protected double calculate(AggregationInput input) {
				return StatUtils.min(input.values);
			}

			@Override
			public boolean isSplittable() {
				return true;
			}
		},
		MAX("Maximum", true, true) {
			@Override
			protected double calculate(AggregationInput input) {
				return StatUtils.max(input.values);
			}

			@Override
			public boolean isSplittable() {
				return true;
			}
		},
		FRACT_POSITIVE("Fraction Positive", false, true) {
			@Override
			protected double calculate(AggregationInput input) {
				// Fractions use the full sample measure, before source-stage correction.
				return input.getRaw(NUM_POSITIVE) / input.totalWeight;
			}
		},
		NUM_POSITIVE("Num Positive", false, true, true) {
			@Override
			protected double calculate(AggregationInput input) {
				double positive = 0;
				for (int i = 0; i < input.values.length; i++)
					if (input.values[i] >= 0)
						positive += input.weights == null ? 1 : input.weights[i];
				return positive;
			}
		},
		NUM_NEGATIVE("Num Negative", false, true, true) {
			@Override
			protected double calculate(AggregationInput input) {
				return input.totalWeight - input.getRaw(NUM_POSITIVE);
			}
		},
		GREATER_SUM_MEDIAN("Max[Sum,Median]", true, true) {
			@Override
			protected double calculate(AggregationInput input) {
				return Math.max(input.get(SUM), input.get(MEDIAN));
			}
		},
		GREATER_MEAN_MEDIAN("Max[Mean,Median]", true, true) {
			@Override
			protected double calculate(AggregationInput input) {
				return Math.max(input.get(MEAN), input.get(MEDIAN));
			}
		},
		COUNT("Count", true, true) {
			@Override
			protected double calculate(AggregationInput input) {
				return input.interactionCount;
			}
		},
		FLATTEN("Flatten", true, false) {
			@Override
			public ReceiverDistribution[] aggregate(int higherLevelID, ReceiverDistribution[] dists) {
				return new ReceiverDistribution[] { flatten(higherLevelID, dists) };
			}
		},
		RECEIVER_SUM("ReceiverSum", true, false) {
			@Override
			public ReceiverDistribution[] aggregate(int higherLevelID, ReceiverDistribution[] dists) {
				Map<Integer, List<ReceiverDistribution>> grouped = Arrays.stream(dists).collect(
						Collectors.groupingBy(ReceiverDistribution::getReceiverID));
				ReceiverDistribution[] ret = new ReceiverDistribution[grouped.keySet().size()];
				int index = 0;
				for (Integer receiverID : grouped.keySet()) {
					List<ReceiverDistribution> receiverDists = grouped.get(receiverID);
					if (receiverDists.size() == 1) {
						ret[index++] = receiverDists.get(0);
					} else {
						if (receiverDists.get(0).weights != null) {
							double sum = 0, measure = 0;
							long count = 0;
							ReceiverDistribution first = receiverDists.get(0);
							double receiverWeight = first.sourceSamples ? first.receiverWeight : first.weights[0];
							for (ReceiverDistribution dist : receiverDists) {
								sum += dist.sourceSamples ? dist.calculate(SUM) : StatUtils.sum(dist.values);
								measure += dist.totInteractionWeight;
								count += dist.totNumInteractions;
							}
							ret[index++] = new ReceiverDistribution(receiverID, count, new double[] { sum },
									new double[] { receiverWeight }, measure, false, 1);
						} else {
							ReceiverDistribution flattened = flatten(receiverID, receiverDists);
							ret[index++] = new ReceiverDistribution(receiverID,
									flattened.totNumInteractions, StatUtils.sum(flattened.values));
						}
					}
				}
				return ret;
			}
		},
		NORM_BY_COUNT("Normalize By Interaction Count", false, true) {
			@Override
			public ReceiverDistribution[] aggregate(int higherLevelID, ReceiverDistribution[] dists) {
				if (dists.length > 0 && dists[0].weights != null) {
					ReceiverDistribution flat = flatten(higherLevelID, dists);
					Preconditions.checkState(flat.totInteractionWeight > 0, "No weighted interactions");
					return new ReceiverDistribution[] { flat.scalar(higherLevelID,
							flat.calculate(SUM) / flat.totInteractionWeight, 1) };
				}
				double sum = 0d;
				long interactionCount = 0;
				for (ReceiverDistribution dist : dists) {
					interactionCount += dist.totNumInteractions;
					for (double val : dist.values)
						sum += val;
				}
				double fract = sum / (double) interactionCount;
				Preconditions.checkState(interactionCount > 0,
						"No interactions (%s) found at this level. Have %s dists and sum=%s", interactionCount,
						(Object) dists.length, sum);
				return new ReceiverDistribution[] { new ReceiverDistribution(higherLevelID, interactionCount, fract) };
			}

			@Override
			public double get(ReceiverDistribution[] dists) {
				ReceiverDistribution[] agg = aggregate(-1, dists);
				Preconditions.checkState(agg.length == 1 && agg[0].values.length == 1);
				return agg[0].values[0];
			}
		},
		HALF_INTERACTIONS("1/2 Interactions", false, true) {
			@Override
			public ReceiverDistribution[] aggregate(int higherLevelID, ReceiverDistribution[] dists) {
				return new ReceiverDistribution[] { interactionTest(higherLevelID, dists, 0.5, 1, -1) };
			}

			@Override
			public double get(ReceiverDistribution[] dists) {
				ReceiverDistribution[] agg = aggregate(-1, dists);
				Preconditions.checkState(agg.length == 1 && agg[0].values.length == 1);
				return agg[0].values[0];
			}
		},
		THREE_QUARTER_INTERACTIONS("3/4 Interactions", false, true) {
			@Override
			public ReceiverDistribution[] aggregate(int higherLevelID, ReceiverDistribution[] dists) {
				return new ReceiverDistribution[] { interactionTest(higherLevelID, dists, 0.75, 1, -1) };
			}

			@Override
			public double get(ReceiverDistribution[] dists) {
				ReceiverDistribution[] agg = aggregate(-1, dists);
				Preconditions.checkState(agg.length == 1 && agg[0].values.length == 1);
				return agg[0].values[0];
			}
		},
		NINE_TENTH_INTERACTIONS("9/10 Interactions", false, true) {
			@Override
			public ReceiverDistribution[] aggregate(int higherLevelID, ReceiverDistribution[] dists) {
				return new ReceiverDistribution[] { interactionTest(higherLevelID, dists, 0.9, 1, -1) };
			}

			@Override
			public double get(ReceiverDistribution[] dists) {
				ReceiverDistribution[] agg = aggregate(-1, dists);
				Preconditions.checkState(agg.length == 1 && agg[0].values.length == 1);
				return agg[0].values[0];
			}
		},
		PASSTHROUGH("Passthrough", true, false) {
			@Override
			public ReceiverDistribution[] aggregate(int higherLevelID, ReceiverDistribution[] dists) {
				return dists;
			}
		},
		FLAT_SUM("FlatSum", true, true, true) {
			@Override
			protected double calculate(AggregationInput input) {
				return input.getRaw(SUM);
			}

			@Override
			public ReceiverDistribution[] aggregate(int higherLevelID, ReceiverDistribution[] dists) {
				if (dists.length > 0 && dists[0].weights != null) {
					ReceiverDistribution flat = flatten(higherLevelID, dists);
					return new ReceiverDistribution[] { flat.scalar(higherLevelID, flat.calculate(SUM), 1) };
				}
				double totalSum = 0d;
				long totalNumInts = 0;
				for (ReceiverDistribution dist : dists) {
					totalSum += calculate(dist.values);
					totalNumInts += dist.totNumInteractions;
				}
				return new ReceiverDistribution[] { new ReceiverDistribution(higherLevelID, totalNumInts, totalSum) };
			}
		};

		private String name;
		private boolean hasUnits;
		private boolean terminal;
		/** True when the weighted statistic scales with the total sample weight. */
		private final boolean additive;

		private AggregationMethod(String name, boolean hasUnits, boolean isTerminal) {
			this(name, hasUnits, isTerminal, false);
		}

		private AggregationMethod(String name, boolean hasUnits, boolean isTerminal, boolean additive) {
			this.name = name;
			this.hasUnits = hasUnits;
			this.terminal = isTerminal;
			this.additive = additive;
		}

		@Override
		public String toString() {
			return name;
		}

		/**
		 * Calculates the aggregated quantity for the given array of values. Must be a terminal layer.
		 * 
		 * @param values
		 * @return
		 */
		public final double calculate(double[] values) {
			return calculate(values, null);
		}

		/**
		 * Calculates a sample statistic with optional weights. Null weights retain
		 * unweighted behavior. COUNT returns the number of samples, while weighted
		 * positive/negative counts measure represented area. Layer-specific counts
		 * and receiver-area corrections are supplied by ReceiverDistribution.
		 */
		public final double calculate(double[] values, double[] weights) {
			return new AggregationInput(values, weights, values.length, 1).get(this);
		}

		protected double calculate(AggregationInput input) {
			throw new IllegalStateException("No sample statistic defined for " + name());
		}

		/**
		 * Aggregates the given distributions for processing by the next layer.
		 * 
		 * @param higherLevelID unique ID of the current aggregation level: receiver patch or receiver section, or
		 * -1 if this is the a multiple-receiver sections aggregation level (final layer)
		 * @param dists
		 * @return
		 */
		public ReceiverDistribution[] aggregate(int higherLevelID, ReceiverDistribution[] dists) {
			Preconditions.checkState(terminal, "Non-terminal aggreagation methods must override aggregate() method");
			ReceiverDistribution[] aggregated = new ReceiverDistribution[dists.length];
			int index = 0;
			for (ReceiverDistribution dist : dists)
				aggregated[index++] = dist.reduce(this);
			return aggregated;
		}

		/**
		 * Gets a single value from the given distributions. Must be a terminal layer.
		 * 
		 * @param dists
		 * @return single aggregated value
		 * @throws IllegalStateException if this is not a terminal layer
		 */
		public double get(ReceiverDistribution[] dists) {
			Preconditions.checkState(terminal, "Can't get a single value for a non-terminal aggregation method");
			ReceiverDistribution dist;
			if (dists.length > 1) {
				// flatten it for final processing
				dist = flatten(-1, dists);
			} else {
				Preconditions.checkState(dists.length == 1, "No distributions left at this layer");
				dist = dists[0];
			}
			return dist.calculate(this);
		}

		/**
		 * 
		 * @return true if this a terminal layer that can supply a single aggregated value, false if it is a layer that organizes or
		 * transforms higher level distributions
		 */
		public boolean isTerminal() {
			return terminal;
		}

		public boolean hasUnits() {
			return hasUnits;
		}

		/**
		 * @return true if this can be calculated in parallel and then aggregated (e.g., a sum or min/max value), false otherwise 
		 */
		public boolean isSplittable() {
			return false;
		}

	}

	private static ReceiverDistribution interactionTest(int receiverID, ReceiverDistribution[] dists, double threshold,
			double valPass, double valFail) {
		if (dists.length > 0 && dists[0].weights != null) {
			ReceiverDistribution flat = flatten(receiverID, dists);
			Preconditions.checkState(flat.totInteractionWeight > 0, "No weighted interactions");
			return flat.scalar(receiverID, flat.calculate(AggregationMethod.SUM) / flat.totInteractionWeight > threshold
					? valPass
					: valFail, 1);
		}
		double sum = 0d;
		long interactionCount = 0;
		for (ReceiverDistribution dist : dists) {
			interactionCount += dist.totNumInteractions;
			for (double val : dist.values)
				sum += val;
		}
		Preconditions.checkState(interactionCount > 0,
				"No interactions (%s) found at this level. Have %s dists and sum=%s", interactionCount,
				(Object) dists.length, sum);
		double fract = sum / (double) interactionCount;
		if (DD)
			System.out.println("calculated fract = " + (float) sum + "/" + interactionCount + " = " + fract);
		if (fract > threshold)
			return new ReceiverDistribution(receiverID, interactionCount, valPass);
		return new ReceiverDistribution(receiverID, interactionCount, valFail);
	}

	public static class ReceiverDistribution {
		public final int receiverID;
		public final double[] values;
		public final long totNumInteractions;
		/** Area-based interaction measure, distinct from the diagnostic element count. */
		public final double totInteractionWeight;
		/** Null preserves the legacy unweighted calculation path exactly. */
		final double[] weights;
		private final boolean sourceSamples;
		private final double receiverWeight;

		public ReceiverDistribution(int receiverID, long count, List<Double> values) {
			this(receiverID, count, Doubles.toArray(values));
		}

		public ReceiverDistribution(int receiverID, long count, double value) {
			this(receiverID, count, new double[] { value });
		}

		public ReceiverDistribution(int receiverID, long count, double[] values) {
			this(receiverID, count, values, null, count, false, 1);
		}

		ReceiverDistribution(int receiverID, long count, double[] values, double[] weights,
				double interactionWeight, boolean sourceSamples, double receiverWeight) {
			this.receiverID = receiverID;
			this.totNumInteractions = count;
			this.values = values;
			this.weights = weights;
			this.totInteractionWeight = interactionWeight;
			this.sourceSamples = sourceSamples;
			this.receiverWeight = receiverWeight;
		}

		double calculate(AggregationMethod method) {
			// Source-stage additive statistics return physical stress or source area.
			// Receiver area is applied at the next layer, or retained when flattening.
			return new AggregationInput(values, weights, weights == null ? values.length : totNumInteractions,
					sourceSamples ? receiverWeight : 1).get(method);
		}

		ReceiverDistribution reduce(AggregationMethod method) {
			if (weights == null)
				return new ReceiverDistribution(receiverID, totNumInteractions, calculate(method));
			return scalar(receiverID, calculate(method), sourceSamples ? receiverWeight : 1);
		}

		ReceiverDistribution scalar(int id, double value, double weight) {
			return new ReceiverDistribution(id, totNumInteractions, new double[] { value },
					weights == null ? null : new double[] { weight }, totInteractionWeight, false, 1);
		}

		public int getReceiverID() {
			return receiverID;
		}

		@Override
		public String toString() {
			return receiverID + ": " + Arrays.toString(values) + "\ttotNumInteractions=" + totNumInteractions;
		}
	}

	private static ReceiverDistribution flatten(int receiverID, ReceiverDistribution[] dists) {
		return flatten(receiverID, Arrays.asList(dists));
	}

	private static ReceiverDistribution flatten(int receiverID, List<ReceiverDistribution> dists) {
		Preconditions.checkState(!dists.isEmpty());
		int size = 0;
		long count = 0;
		double measure = 0;
		boolean weighted = dists.get(0).weights != null;
		for (ReceiverDistribution dist : dists) {
			Preconditions.checkState((dist.weights != null) == weighted,
					"Cannot mix weighted and unweighted distributions");
			size += dist.values.length;
			count += dist.totNumInteractions;
			measure += dist.totInteractionWeight;
		}
		double[] values = new double[size], weights = weighted ? new double[size] : null;
		int index = 0;
		for (ReceiverDistribution dist : dists) {
			System.arraycopy(dist.values, 0, values, index, dist.values.length);
			if (weighted)
				System.arraycopy(dist.weights, 0, weights, index, dist.values.length);
			index += dist.values.length;
		}
		return new ReceiverDistribution(receiverID, count, values, weights, measure, false, 1);
	}

	//	/**
	//	 * Convenience method to get an aggregated calculator that uses the median patch interaction for sect-to-sect,
	//	 * and sums those medians across multiple sects (sects-to-sect or sects-to-sects).
	//	 *
	//	 * @param type
	//	 * @param calc
	//	 * @return
	//	 */
	//	public static AggregatedStiffnessCalculator buildMedianPatchSumSects(StiffnessType type, SubSectStiffnessCalculator calc) {
	//		return builder(type, calc).sectToSectAgg(AggregationMethod.MEDIAN).sectsToSectsAgg(AggregationMethod.SUM).get();
	//	}
	//
	public static Builder builder(StiffnessType type, SubSectStiffnessCalculator calc) {
		return new Builder(type, calc);
	}

	public static class Builder {

		private StiffnessType type;
		private SubSectStiffnessCalculator calc;
		private List<AggregationMethod> layers;
		private boolean allowSectToSelf = false;

		private Builder(StiffnessType type, SubSectStiffnessCalculator calc) {
			this.type = type;
			this.calc = calc;
			this.layers = new ArrayList<>();
		}

		/**
		 * 
		 * @param allowSectToSelf if true, calculations between the same source and receiver section will be included.
		 * The calculation between the exact same source and receiver patch will always be excluded. 
		 * @return
		 */
		public Builder allowSectToSelf(boolean allowSectToSelf) {
			this.allowSectToSelf = allowSectToSelf;
			return this;
		}

		public Builder flatten() {
			layers.add(AggregationMethod.FLATTEN);
			return this;
		}

		public Builder receiverSum() {
			layers.add(AggregationMethod.RECEIVER_SUM);
			return this;
		}

		public Builder process(AggregationMethod method) {
			layers.add(method);
			return this;
		}

		public Builder passthrough() {
			layers.add(AggregationMethod.PASSTHROUGH);
			return this;
		}

		/**
		 * Aggregate section-to-section patch interactions at the receiver patch level first using the given
		 * aggregation method.
		 * 
		 * @param aggMethod
		 * @return
		 */
		public Builder receiverPatchAgg(AggregationMethod aggMethod) {
			Preconditions.checkState(layers.isEmpty(), "Receiver patch aggregation must be specified first");
			process(aggMethod);
			return this;
		}

		/**
		 * Aggregate method for section-to-section patch interactions. Receiver patch distributions will be flattened
		 * if no receiver path aggregation layer has been supplied.
		 * 
		 * @param aggMethod
		 * @return
		 */
		public Builder sectToSectAgg(AggregationMethod aggMethod) {
			Preconditions.checkState(layers.size() < 2);
			if (layers.isEmpty())
				flatten();
			process(aggMethod);
			return this;
		}

		/**
		 * Aggregate method for multiple source sections to a single receiver section. Must have already supplied a
		 * section-section aggregation level (via the sectToSectAggg(AggregationMethod) method or manual layer specification).
		 * 
		 * @param aggMethod
		 * @return
		 */
		public Builder sectsToSectAgg(AggregationMethod aggMethod) {
			Preconditions.checkState(layers.size() == 2,
					"Must have supplied a sect-to-sect aggregation level (and nothing further)");
			process(aggMethod);
			return this;
		}

		/**
		 * Aggregate method for multiple source sections to a multiple receiver sections. If a sections-to-section
		 * aggregation method is supplied, it will be applied first, otherwise a flatten layer will be applied first.
		 * 
		 * @param aggMethod
		 * @return
		 */
		public Builder sectsToSectsAgg(AggregationMethod aggMethod) {
			Preconditions.checkState(layers.size() >= 2,
					"Must have supplied at least a sect-to-sect aggregation level first");
			Preconditions.checkState(layers.size() < 4, "Aggregation levels already completely specified");
			if (layers.size() == 2)
				flatten(); // flatten at sects-to-sect first
			process(aggMethod);
			return this;
		}

		public AggregatedStiffnessCalculator get() {
			return new AggregatedStiffnessCalculator(type, calc, allowSectToSelf,
					this.layers.toArray(new AggregationMethod[0]));
		}
	}

	/**
	 * 
	 * @param type stiffness type that we are calculating
	 * @param calc stiffness calculator between two subsections
	 * @param allowSectToSelf if true, calculations between the same source and receiver section will be included.
	 * The calculation between the exact same source and receiver patch will always be excluded.
	 * @param layers aggregations layers. Must supply at least 1, and the final layer must be a terminal layer.
	 */
	public AggregatedStiffnessCalculator(StiffnessType type, SubSectStiffnessCalculator calc, boolean allowSectToSelf,
			AggregationMethod... layers) {
		super();
		this.type = type;
		this.calc = calc;
		this.allowSectToSelf = allowSectToSelf;
		Preconditions.checkState(layers.length > 0, "must supply at least 1 aggregation layer");
		Preconditions.checkState(layers.length < 5, "only 4 aggregation layers are possible");
		for (int i = 0; i < layers.length; i++)
			Preconditions.checkNotNull(layers[i], "Layer at level %s is null", i);
		Preconditions.checkState(layers[layers.length - 1].isTerminal(),
				"Final layer must be a terminal layer (but is %s)", layers[layers.length - 1]);
		this.layers = layers;
	}

	private int uniquePatchID(int sectID, int patchID) {
		if (patch_sect_id_multiplier <= 0) {
			int numSects = calc.getSubSects().size();
			patch_sect_id_add = numSects;
			patch_sect_id_multiplier = Integer.MAX_VALUE / numSects;
			//			System.out.println("MULTIPLIER="+patch_sect_id_multiplier+" for "+numSects+" sects");
		}
		Preconditions.checkState(patchID < patch_sect_id_multiplier - 1,
				"Patch ID overflow: patchID=%s, multiplier=%s", patchID, patch_sect_id_multiplier);
		return patch_sect_id_add + sectID * patch_sect_id_multiplier + patchID;
	}

	private ReceiverDistribution[] aggRecieverPatches(FaultSection source, FaultSection receiver) {
		Preconditions.checkState(layers.length > 0, "Patch aggregation layer not supplied");
		int sourceID = source.getSectionId();
		int receiverID = receiver.getSectionId();
		Preconditions.checkState(allowSectToSelf || sourceID != receiverID,
				"srouceID=receiverID and allowSectToSelf is false");

		if (layers[0].isTerminal()) {
			// we can cache at this level

			if (cache == null)
				cache = calc.getAggregationCache(type);

			ReceiverDistribution[] cached = cache.getPatchAggregated(layers[0], source, receiver);
			if (cached != null) {
				if (D) {
					System.out.println(
							source.getSectionId() + " -> " + receiver.getSectionId() + ", patchAgg=" + layers[0]
									+ cached.length + " aggregated receiver dists");
					if (DD) {
						System.out.println("\tCACHED:");
						for (ReceiverDistribution dist : cached)
							System.out.println("\t" + dist);
					}
				}
				return cached;
			}
		}

		StiffnessDistribution dist = calc.calcStiffnessDistribution(source, receiver);
		double[][] values = dist.get(type);
		double[] receiverPatchVals = new double[values.length];

		ReceiverDistribution[] receiverDists = new ReceiverDistribution[values.length];
		boolean sameSect = sourceID == receiverID;
		for (int r = 0; r < receiverPatchVals.length; r++) {
			double[] receiverVals;
			if (sameSect) {
				// source and receiver section are the same, make sure to exclude the patch self-stiffness value
				Preconditions.checkState(values[r].length == receiverDists.length);

				receiverVals = new double[receiverDists.length - 1];
				if (r > 0)
					System.arraycopy(values[r], 0, receiverVals, 0, r);
				if (r < receiverVals.length)
					System.arraycopy(values[r], r + 1, receiverVals, r, receiverVals.length - r);
				if (D) {
					System.out.println(source.getSectionId() + " -> " + receiver.getSectionId()
							+ " sect-to-self patch calc, removing index " + r);
					if (DD) {
						System.out.println("\traw[" + r + "]:\t" + DoubleStream.of(values[r]).mapToObj(String::valueOf)
								.collect(Collectors.joining(",")));
						System.out.println("\tprocessed:\t" + DoubleStream.of(receiverVals).mapToObj(String::valueOf)
								.collect(Collectors.joining(",")));
					}
				}
			} else {
				receiverVals = values[r];
			}
			int receiverPatchID = uniquePatchID(receiver.getSectionId(), r);
			if (calc.isAreaWeighted()) {
				double referenceArea = calc.getReferencePatchArea();
				double receiverWeight = dist.receiverPatches.get(r).patch.area / referenceArea;
				double[] normalized = new double[receiverVals.length], weights = new double[receiverVals.length];
				double measure = 0;
				int index = 0;
				for (int sourceIndex = 0; sourceIndex < dist.sourcePatches.size(); sourceIndex++) {
					if (sameSect && sourceIndex == r)
						continue;
					double sourceWeight = dist.sourcePatches.get(sourceIndex).patch.area / referenceArea;
					normalized[index] = receiverVals[index] / sourceWeight;
					weights[index] = sourceWeight * receiverWeight;
					measure += weights[index++];
				}
				receiverDists[r] = new ReceiverDistribution(receiverPatchID, receiverVals.length, normalized, weights,
						measure, true, receiverWeight);
			} else {
				receiverDists[r] = new ReceiverDistribution(receiverPatchID, receiverVals.length, receiverVals);
			}
		}

		ReceiverDistribution[] aggregated = layers[0].aggregate(receiver.getSectionId(), receiverDists);

		if (cache != null && layers[0].isTerminal())
			cache.putPatchAggregated(layers[0], source, receiver, aggregated);

		if (D) {
			System.out
					.println(source.getSectionId() + " -> " + receiver.getSectionId() + ", patchAgg=" + layers[0] + ". "
							+ aggregated.length + " aggregated receiver dists");
			if (DD) {
				for (ReceiverDistribution aggDist : aggregated)
					System.out.println("\t" + aggDist);
			}
		}

		return aggregated;
	}

	private boolean isSectToSectCacheable() {
		return layers[1].isTerminal() && (layers[0].isTerminal() || layers[0] == AggregationMethod.FLATTEN);
	}

	private StiffnessAggregation getCachedSectToSect(FaultSection source, FaultSection receiver) {
		AggregationMethod patchAggMethod = null;
		if (layers[0].isTerminal()) {
			// we aggregated at the patch layer
			patchAggMethod = layers[0];
		} else {
			Preconditions.checkState(layers[0] == AggregationMethod.FLATTEN);
		}

		if (cache == null)
			cache = calc.getAggregationCache(type);

		StiffnessAggregation aggregated = cache.getSectAggregated(patchAggMethod, source, receiver);
		if (aggregated == null) {
			// need to calculate and cache it
			ReceiverDistribution receiverPatchDist = flatten(receiver.getSectionId(),
					aggRecieverPatches(source, receiver));
			//			Preconditions.checkState(receiverPatchDists.length == 1,
			//					"should only have 1 flattened or procssed distribution at sect-to-sect if cacheable");
			aggregated = new StiffnessAggregation(receiverPatchDist);
			if (D) {
				System.out.println(
						source.getSectionId() + " -> " + receiver.getSectionId() + " sect-to-sect " + layers[1] + ":");
				System.out.println("\t" + receiverPatchDist);
			}
			cache.putSectAggregated(patchAggMethod, source, receiver, aggregated);
		} else if (D) {
			System.out.println(source.getSectionId() + " -> " + receiver.getSectionId()
					+ " sect-to-sect " + layers[1] + " is CACHED w/ patchAgg=" + patchAggMethod + ":");
		}

		return aggregated;
	}

	private ReceiverDistribution[] aggSectToSect(FaultSection source, FaultSection receiver) {
		Preconditions.checkState(allowSectToSelf || source.getSectionId() != receiver.getSectionId(),
				"Source and receiver ID are the same and allowSectToSelf=false: %s", source.getSectionId());
		Preconditions.checkState(layers.length > 1, "Section-to-section aggregation layer not supplied");

		// check the cache if possible at this layer
		if (isSectToSectCacheable()) {
			StiffnessAggregation aggregated = getCachedSectToSect(source, receiver);
			double val = aggregated.get(layers[1]);
			if (D)
				System.out.println("\t" + layers[1] + ": " + val);
			return new ReceiverDistribution[] { new ReceiverDistribution(receiver.getSectionId(),
					(long) aggregated.get(AggregationMethod.COUNT), new double[] { val },
					calc.isAreaWeighted() ? new double[] { 1 } : null, aggregated.getInteractionWeight(), false, 1) };
		}

		ReceiverDistribution[] receiverPatchDists = aggRecieverPatches(source, receiver);
		ReceiverDistribution[] aggregated = layers[1].aggregate(receiver.getSectionId(), receiverPatchDists);

		if (D) {
			System.out.println(
					source.getSectionId() + " -> " + receiver.getSectionId() + " sect-to-sect " + layers[1] + ". "
							+ aggregated.length + " aggregated receiver dists");
			if (DD) {
				for (ReceiverDistribution aggDist : aggregated)
					System.out.println("\t" + aggDist);
			}
		}

		return aggregated;
	}

	private double processUntilTerminal(int curLayer, int receiverID, ReceiverDistribution... dists) {
		if (layers[curLayer].isTerminal()) {
			double val = layers[curLayer].get(dists);
			if (D)
				System.out.println("\t" + layers[curLayer] + ": " + val);
			return val;
		} else {
			// need to process at this layer first
			dists = layers[curLayer].aggregate(receiverID, dists);

			if (D) {
				System.out.println("Intermediate layer " + curLayer + ", " + layers[curLayer] + ":");
				if (DD) {
					for (ReceiverDistribution aggDist : dists)
						System.out.println("\t" + aggDist);
				} else {
					System.out.println("\t" + dists.length + " dists");
				}
			}
			return processUntilTerminal(curLayer + 1, -1, dists);
		}
	}

	public double[] calcReceiverPatchAgg(FaultSection source, FaultSection receiver) {
		Preconditions.checkState(source.getSectionId() != receiver.getSectionId(),
				"Source and receiver ID are the same and allowSectToSelf=false: %s", source.getSectionId());

		ReceiverDistribution[] receiverPatchDists = aggRecieverPatches(source, receiver);

		if (D)
			System.out.println(
					source.getSectionId() + " -> " + receiver.getSectionId() + " sect-to-sect " + layers[1] + ":");
		double[] ret = new double[receiverPatchDists.length];
		if (layers[0].isTerminal()) {
			for (int i = 0; i < ret.length; i++) {
				Preconditions.checkState(receiverPatchDists[i].values.length == 1);
				ret[i] = receiverPatchDists[i].values[0];
			}
		} else {
			for (int i = 0; i < ret.length; i++)
				ret[i] = processUntilTerminal(1, i, receiverPatchDists[i]);
		}
		return ret;
	}

	public double calc(FaultSection source, FaultSection receiver) {
		Preconditions.checkState(source.getSectionId() != receiver.getSectionId(),
				"Source and receiver ID are the same and allowSectToSelf=false: %s", source.getSectionId());
		Preconditions.checkState(layers.length > 1, "Section-to-section aggregation layer not supplied");

		if (isSectToSectCacheable()) {
			StiffnessAggregation aggregated = getCachedSectToSect(source, receiver);
			double val = aggregated.get(layers[1]);
			if (D)
				System.out.println("\t" + layers[1] + ": " + val);
			return val;
		}

		ReceiverDistribution[] receiverPatchDists = aggRecieverPatches(source, receiver);

		if (D)
			System.out.println(
					source.getSectionId() + " -> " + receiver.getSectionId() + " sect-to-sect " + layers[1] + ":");
		return processUntilTerminal(1, receiver.getSectionId(), receiverPatchDists);
	}

	private ReceiverDistribution[] collectMultiSectsToSect(Collection<? extends FaultSection> sources,
			FaultSection receiver) {
		if (!allowSectToSelf) {
			List<FaultSection> filtered = new ArrayList<>(sources.size());
			int receiverID = receiver.getSectionId();
			//			for (int s=0; s<sources.size(); s++) {
			//				FaultSection source = sources.get(s);
			for (FaultSection source : sources) {
				if (source.getSectionId() != receiverID)
					filtered.add(source);
			}
			sources = filtered;
		}
		Preconditions.checkState(!sources.isEmpty(), "No sources that aren't the receiver");
		// start assuming that it's a one-to-one mapping
		ReceiverDistribution[] receiverSectDists = new ReceiverDistribution[sources.size()];
		ArrayList<ReceiverDistribution> distsList = null;
		//		for (int s=0; s<sources.size(); s++) {
		//			FaultSection source = sources.get(s);
		int s = 0;
		for (FaultSection source : sources) {
			ReceiverDistribution[] aggregated = aggSectToSect(source, receiver);
			if (distsList != null) {
				// we're already in list mode
				Collections.addAll(distsList, aggregated);
			} else if (aggregated.length != 1) {
				// we need to switch to list representation, not 1-to-1
				if (distsList == null) {
					distsList = new ArrayList<>(sources.size() * aggregated.length);
					// copy over that which we already added
					for (int i = 0; i < s; i++)
						distsList.add(receiverSectDists[i]);
				}
				Collections.addAll(distsList, aggregated);
			} else {
				// still 1-to-1
				receiverSectDists[s] = aggregated[0];
			}
			s++;
		}

		if (distsList != null) {
			if (distsList.size() > receiverSectDists.length)
				// reuse the array
				receiverSectDists = distsList.toArray(receiverSectDists);
			else
				receiverSectDists = distsList.toArray(new ReceiverDistribution[distsList.size()]);
		}
		return receiverSectDists;
	}

	public double[] calcReceiverPatchAgg(List<FaultSection> sources, FaultSection receiver) {
		ReceiverDistribution[][] receiverAgg = null;
		for (int s = 0; s < sources.size(); s++) {
			FaultSection source = sources.get(s);
			ReceiverDistribution[] receiverPatchDists = aggRecieverPatches(source, receiver);
			if (receiverAgg == null)
				receiverAgg = new ReceiverDistribution[receiverPatchDists.length][sources.size()];
			else
				Preconditions.checkState(receiverPatchDists.length == receiverAgg.length);
			for (int i = 0; i < receiverPatchDists.length; i++)
				receiverAgg[i][s] = receiverPatchDists[i];
		}

		double[] ret = new double[receiverAgg.length];
		for (int i = 0; i < receiverAgg.length; i++)
			ret[i] = processUntilTerminal(1, uniquePatchID(receiver.getSectionId(), i), receiverAgg[i]);
		return ret;
	}

	private ReceiverDistribution[] aggSectsToSect(Collection<? extends FaultSection> sources, FaultSection receiver) {
		Preconditions.checkState(layers.length > 2, "Sections-to-section aggregation layer not supplied");

		ReceiverDistribution[] receiverSectDists = collectMultiSectsToSect(sources, receiver);

		ReceiverDistribution[] aggregated = layers[2].aggregate(receiver.getSectionId(), receiverSectDists);

		if (D) {
			System.out.println(
					sources.stream().map(s -> s.getSectionId()).map(String::valueOf).collect(Collectors.joining(","))
							+ " -> " + receiver.getSectionId() + " sects-to-sect " + layers[2] + ". "
							+ aggregated.length + " agg receiver dists from "
							+ receiverSectDists.length + " input dists");
			if (DD) {
				for (ReceiverDistribution aggDist : aggregated)
					System.out.println("\t" + aggDist);
			}
		}

		return aggregated;
	}

	private static final boolean CACHE_SS2R = false;
	// array by receiver index, to map of <sources, val>>
	private transient List<ConcurrentMap<UniqueRupture, Double>> ss2rCache = null;

	public double calc(Collection<? extends FaultSection> sources, FaultSection receiver) {
		return calc(sources, receiver, CACHE_SS2R ? UniqueRupture.forSects(sources) : null);
	}

	private double calc(Collection<? extends FaultSection> sources, FaultSection receiver,
			UniqueRupture sourcesUnique) {
		Preconditions.checkState(layers.length > 2, "Sections-to-section aggregation layer not supplied");

		if (CACHE_SS2R) {
			if (ss2rCache == null) {
				synchronized (this) {
					if (ss2rCache == null) {
						int n = calc.getSubSects().size();
						List<ConcurrentMap<UniqueRupture, Double>> ss2rCache = new ArrayList<>(n);
						for (int i = 0; i < n; i++)
							ss2rCache.add(new ConcurrentHashMap<>());
						this.ss2rCache = ss2rCache;
					}
				}
			}
			int recID = receiver.getSectionId();
			Preconditions.checkState(recID < ss2rCache.size());
			Map<UniqueRupture, Double> cache = ss2rCache.get(recID);
			Double val = cache.get(sourcesUnique);
			if (D)
				System.out.println(sources.stream().map(s -> s.getSectionId()).map(String::valueOf)
						.collect(Collectors.joining(","))
						+ " -> " + receiver.getSectionId() + " sects-to-sect " + layers[2] + ":");
			if (val == null) {
				ReceiverDistribution[] receiverSectDists = collectMultiSectsToSect(sources, receiver);

				val = processUntilTerminal(2, receiver.getSectionId(), receiverSectDists);
				if (D)
					System.out.println("\tVAL: " + val);
				cache.putIfAbsent(sourcesUnique, val);
			} else if (D) {
				System.out.println("\tCAHCED VAL: " + val);
			}
			return val;
		} else {
			ReceiverDistribution[] receiverSectDists = collectMultiSectsToSect(sources, receiver);

			return processUntilTerminal(2, receiver.getSectionId(), receiverSectDists);
		}
	}

	// we seem to do slightly better without this caching
	private static final boolean CACHE_SS2RS = false;
	//	private transient Table<UniqueRupture, UniqueRupture, Double> ss2rsCache = null;
	private transient ConcurrentMap<UniqueRupture, ConcurrentMap<UniqueRupture, Double>> ss2rsCache = null;

	public double calc(Collection<? extends FaultSection> sources, Collection<? extends FaultSection> receivers) {
		Preconditions.checkState(layers.length > 3, "Sections-to-sections aggregation layer not supplied");
		Preconditions.checkState(layers[3].isTerminal(), "Final layer must be terminal: %s", layers[3]);

		if (layers[3].isSplittable()) {
			// short circuit to speed things up for simple operations like sum, min, max
			if (receivers.size() == 1 && layers[3] == layers[2])
				return calc(sources, receivers.iterator().next());
			double[] values = new double[receivers.size()];
			int r = 0;
			UniqueRupture sourcesUnique = CACHE_SS2R ? UniqueRupture.forSects(sources) : null;
			for (FaultSection receiver : receivers)
				values[r++] = calc(sources, receiver, sourcesUnique);
			return layers[3].calculate(values);
		}

		UniqueRupture sourcesUnique = null;
		UniqueRupture receiversUnique = null;

		ConcurrentMap<UniqueRupture, Double> sourcesCache = null;
		Double val = null;
		if (CACHE_SS2RS) {
			sourcesUnique = UniqueRupture.forSects(sources);
			receiversUnique = UniqueRupture.forSects(receivers);
			if (ss2rsCache == null) {
				synchronized (this) {
					if (ss2rsCache == null)
						//						ss2rsCache = Tables.synchronizedTable(HashBasedTable.create());
						ss2rsCache = new ConcurrentHashMap<>();
				}
			}
			if (!ss2rsCache.containsKey(sourcesUnique))
				ss2rsCache.putIfAbsent(sourcesUnique, new ConcurrentHashMap<>());
			sourcesCache = ss2rsCache.get(sourcesUnique);
			val = sourcesCache.get(receiversUnique);
			//			Double val = ss2rsCache.get(sourcesUnique, receiversUnique);
		}

		if (val == null) {
			ReceiverDistribution[] receiverSectDists = new ReceiverDistribution[receivers.size()];
			ArrayList<ReceiverDistribution> distsList = null;
			//			for (int r=0; r<receivers.size(); r++) {
			//				FaultSection receiver = receivers.get(r);
			int r = 0;
			for (FaultSection receiver : receivers) {
				ReceiverDistribution[] aggregated = aggSectsToSect(sources, receiver);
				if (distsList != null) {
					// we're already in list mode
					Collections.addAll(distsList, aggregated);
				} else if (aggregated.length > 1) {
					// we need to switch to list representation, not 1-to-1
					if (distsList == null) {
						distsList = new ArrayList<>(sources.size() * aggregated.length);
						// copy over that which we already added
						for (int i = 0; i < r; i++)
							distsList.add(receiverSectDists[i]);
					}
					Collections.addAll(distsList, aggregated);
				} else {
					// still 1-to-1
					receiverSectDists[r] = aggregated[0];
				}
				r++;
			}

			if (distsList != null) {
				if (distsList.size() > receiverSectDists.length)
					// reuse the array
					receiverSectDists = distsList.toArray(receiverSectDists);
				else
					receiverSectDists = distsList.toArray(new ReceiverDistribution[distsList.size()]);
			}
			//			System.out.println(receiverSectDists.length+" dists for "+sources.size()+" sources and "+receivers.size()+" receivers");

			val = layers[3].get(receiverSectDists);
			if (D) {
				System.out.println(sources.stream().map(s -> s.getSectionId()).map(String::valueOf)
						.collect(Collectors.joining(","))
						+ " -> "
						+ receivers.stream().map(s -> s.getSectionId()).map(String::valueOf)
								.collect(Collectors.joining(","))
						+ " sects-to-sects " + layers[3] + ":");
				for (ReceiverDistribution aggDist : receiverSectDists)
					System.out.println("\t" + aggDist);
				System.out.println("\tVAL: " + val);
			}
			if (CACHE_SS2RS) {
				//				ss2rsCache.put(sourcesUnique, receiversUnique, val);
				sourcesCache.put(receiversUnique, val);
			}
		} else if (D) {
			if (D) {
				System.out.println(sources.stream().map(s -> s.getSectionId()).map(String::valueOf)
						.collect(Collectors.joining(","))
						+ " -> "
						+ receivers.stream().map(s -> s.getSectionId()).map(String::valueOf)
								.collect(Collectors.joining(","))
						+ " sects-to-sects " + layers[3] + ":");
				System.out.println("\tCAHCED VAL: " + val);
			}
		}

		return val;
	}

	/**
	 * 
	 * @return true if the sequence of aggregations will result in values with units of stiffness (MPa),
	 * otherwise false (e.g., for fractions or counts)
	 */
	public boolean hasUnits() {
		boolean hasUnits = true;
		for (AggregationMethod layer : layers)
			hasUnits = hasUnits && layer.hasUnits;
		return hasUnits;
	}

	public AggregationMethod[] getAggregationLayers() {
		return layers;
	}

	public String getScalarName() {
		//		String name = type.getName();
		//		for (AggregationMethod aggMethod : layers) {
		//			if (aggMethod == AggregationMethod.FRACT_POSITIVE)
		//				name = "Fract ≥0";
		//			else if (aggMethod == AggregationMethod.NUM_POSITIVE)
		//				name = "Num ≥0";
		//			else if (aggMethod == AggregationMethod.NUM_NEGATIVE)
		//				name = "Num Negative";
		//		}
		//		return name;
		String name = null;
		for (int l = 0; l < layers.length; l++) {
			if (layers[l] == AggregationMethod.FLATTEN || layers[l] == AggregationMethod.PASSTHROUGH
					|| (l > 0 && layers[l] == layers[l - 1]))
				continue;

			if (layers[l] == AggregationMethod.FRACT_POSITIVE) {
				name = "Fract " + (name == null ? "" : "[" + name + "]") + "≥0";
			} else if (layers[l] == AggregationMethod.NUM_NEGATIVE) {
				name = "Num " + (name == null ? "" : "[" + name + "]") + "<0";
			} else if (layers[l] == AggregationMethod.NUM_POSITIVE) {
				name = "Num " + (name == null ? "" : "[" + name + "]") + "≥0";
			} else if (layers[l] == AggregationMethod.NORM_BY_COUNT) {
				name = "[" + (name == null ? "" : name) + "]" + "/Count";
			} else if (l == 0) {
				if (layers.length > 1 && layers[1] == layers[0])
					name = "Sect " + layers[l].name;
				else
					name = "Patch " + layers[l].name;
			} else if (layers[l] == AggregationMethod.RECEIVER_SUM) {
				// l is > 0 here
				if (layers[1].isTerminal())
					name = "Receiver Sect Aggregate" + (name == null ? "" : " [" + name + "]");
				else if (layers[0].isTerminal())
					name = "Receiver Patch Aggregate" + (name == null ? "" : " [" + name + "]");
				else
					name = "Receiver Aggregate " + (name == null ? "" : "[" + name + "]");
			} else if (l == 1 && name == null) {
				name = "Sect " + layers[l].name;
			} else if (layers[l] != layers[l - 1]) {
				name = layers[l].name + (name == null ? "" : " [" + name + "]");
			}
		}
		return name;
	}

	public String getScalarShortName() {
		return getScalarName().replaceAll("Receiver", "Rec").replaceAll("Median", "Mdn")
				.replaceAll("Aggregate", "Agg").replaceAll("imum", "").replaceAll("Sect", "S-")
				.replaceAll("Patch", "P-").replaceAll("Dominant", "Dom").replaceAll("Interaction", "Int")
				.replaceAll(" ", "");
	}

	public StiffnessType getType() {
		return type;
	}

	@Override
	public String toString() {
		return Arrays.stream(layers).map(s -> s.toString())
				.collect(Collectors.joining(" -> ", "AggStiffnessCalc[", "]"));
	}

	public SubSectStiffnessCalculator getCalc() {
		return calc;
	}

	public static void main(String[] args) throws ZipException, IOException, DocumentException {
		File fssFile = new File("/home/kevin/Simulators/catalogs/rundir4983_stitched/fss/"
				+ "rsqsim_sol_m6.5_skip5000_sectArea0.2.zip");
		FaultSystemRupSet rupSet = U3FaultSystemIO.loadRupSet(fssFile);
		double lambda = 30000;
		double mu = 30000;
		double coeffOfFriction = 0.5;
		double gridSpacing = 4d;
		List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
		SubSectStiffnessCalculator calc = new SubSectStiffnessCalculator(
				subSects, gridSpacing, lambda, mu, coeffOfFriction);
		calc.setPatchAlignment(PatchAlignment.FILL_OVERLAP);
		//		calc.setPatchAlignment(PatchAlignment.CENTER);

		AggregatedStiffnessCalculator aggCalc = new AggregatedStiffnessCalculator(StiffnessType.CFF, calc, true,
				//				AggregationMethod.SUM, AggregationMethod.PASSTHROUGH,
				//				AggregationMethod.RECEIVER_SUM, AggregationMethod.FRACT_POSITIVE);
				//				AggregationMethod.SUM, AggregationMethod.FLATTEN,
				//				AggregationMethod.FLATTEN, AggregationMethod.FRACT_POSITIVE);
				//				AggregationMethod.FLATTEN, AggregationMethod.MEDIAN,
				//				AggregationMethod.SUM, AggregationMethod.SUM);
				AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM,
				AggregationMethod.SUM, AggregationMethod.NORM_BY_COUNT);
		//				AggregationMethod.FLATTEN, AggregationMethod.NUM_POSITIVE,
		//				AggregationMethod.SUM, AggregationMethod.THREE_QUARTER_INTERACTIONS);
		//				AggregationMethod.SUM, AggregationMethod.SUM,
		//				AggregationMethod.FLAT_SUM, AggregationMethod.NUM_NEGATIVE);

		//		FaultSection s1 = subSects.get(0);
		//		FaultSection s2 = subSects.get(1);
		//		FaultSection s3 = subSects.get(2);
		//// aggCalc.calc(s2, s1); / aggCalc.calc(s3, s1);
		//		aggCalc.calc(Lists.newArrayList(s2, s3), s1);

		List<FaultSection> rupSects = Lists.newArrayList(subSects.get(419), subSects.get(420));
		System.out.println(aggCalc.calc(rupSects, rupSects));

		//		int targetNumSects = 4;
		//
		//		for (int r=0; r<rupSet.getNumRuptures(); r++) {
		//			List<FaultSection> rupSects = rupSet.getFaultSectionDataForRupture(r);
		//			if (rupSects.size() != targetNumSects)
		//				continue;
		//// System.out.println("Rupture
		/// "+r+": "+Joiner.on(",").join(rupSet.getSectionsIndicesForRup(r)));
		//			System.out.println("Rupture "+r+": "+rupSects.stream().map(s -> s.getSectionId())
		//					.map(String::valueOf).collect(Collectors.joining(",")));
		//			double val = aggCalc.calc(rupSects, rupSects);
		//			System.out.println("Value: "+val);
		//			aggCalc.calc(rupSects, rupSects);
		//			break;
		//		}
	}

}
