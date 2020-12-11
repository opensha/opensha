package org.opensha.sha.simulators.stiffness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.opensha.commons.data.Named;
import org.opensha.commons.util.DataUtils;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessDistribution;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AtomicDouble;

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
 * You must specify the aggregation behavior at each of those levels. The choices are as follows:
 * <br><ol>
 * <li>Process: chose a single statistic from each distribution of values, specified as an {@link AggregationMethod}</li>
 * <li>Flatten: combine all distributions of values as input to this layer into a single (flattened) distribution,
 * from which a single statistic can be calculated if passed to a process layer</li>
 * <li>Passthrough: dont't do any aggregation at this layer, pass each full distribution onto the next layer</li>
 * <li>Receiver Flatten: Take each distribution that has the same receiverID (be that a section or patch ID), and
 * flatten the distribution for that receiver.</li>
 * </ol>
 * <br>
 * In practice, you will usually want to perform a process aggregation at either the first (receiver patch) or second
 * (receiver section) levels. For example, to get the median of all section-to-section patch interactions, the ordering
 * would be: Flatten -> Median. But if you wanted to instead get the fraction of passes which are net positive, you would
 * do: Sum -> Fact. Positive.
 * 
 * @author kevin
 *
 */
public class AggregatedStiffnessCalculator {

	private StiffnessType type;
	private SubSectStiffnessCalculator calc;
	private transient AggregatedStiffnessCache cache;
	
	public static final int MAX_LAYERS = 4;
	private AggregationLayer[] layers;
	
	// multiplier to use for section IDs, to which patch IDs are added to get unique patch IDs
	private final int sect_id_multiplier;
	
	public static class StiffnessAggregation {
		
		private final double[] aggValues;
//		private final int numValues;
		
		public StiffnessAggregation(DoubleStream stream) {
			this.aggValues = new double[AggregationMethod.values().length];
			initSorted(stream.sorted().toArray());
		}
		
		public StiffnessAggregation(double[] values) {
			Arrays.sort(values);
			this.aggValues = new double[AggregationMethod.values().length];
			initSorted(values);
		}
		
		private void initSorted(double[] sorted) {
			int numPositive = 0;
			double sum = 0d;
			int count = sorted.length;
			for (double val : sorted) {
				sum += val;
				if (val >= 0)
					numPositive++;
			}
			aggValues[AggregationMethod.MEAN.ordinal()] = sum/(double)count;
			aggValues[AggregationMethod.MEDIAN.ordinal()] = DataUtils.median_sorted(sorted);
			aggValues[AggregationMethod.SUM.ordinal()] = sum;
			aggValues[AggregationMethod.MIN.ordinal()] = sorted[0];
			aggValues[AggregationMethod.MAX.ordinal()] = sorted[count-1];
			aggValues[AggregationMethod.FRACT_POSITIVE.ordinal()] = (double)numPositive/(double)count;
			aggValues[AggregationMethod.NUM_POSITIVE.ordinal()] = numPositive;
			aggValues[AggregationMethod.NUM_NEGATIVE.ordinal()] = count-numPositive;
			aggValues[AggregationMethod.GREATER_SUM_MEDIAN.ordinal()] = Math.max(get(AggregationMethod.SUM), get(AggregationMethod.MEDIAN));
			aggValues[AggregationMethod.GREATER_MEAN_MEDIAN.ordinal()] = Math.max(get(AggregationMethod.MEAN), get(AggregationMethod.MEDIAN));
//			this.numValues = values.length;
		}
		
		StiffnessAggregation(AggregationMethod[] methods, double[] values) {
			Preconditions.checkState(methods.length == values.length);
			this.aggValues = new double[AggregationMethod.values().length];
			for (int i=0; i<aggValues.length; i++)
				aggValues[i] = Double.NaN;
			for (int i=0; i<methods.length; i++)
				aggValues[methods[i].ordinal()] = values[i];
//			this.numValues
		}
		
		public double get(AggregationMethod aggMethod) {
			return aggValues[aggMethod.ordinal()];
		}
	}
	
	private static class DoubleSignCounter implements DoubleConsumer {
		
		private final AtomicInteger count = new AtomicInteger();
		private final AtomicInteger numPositive = new AtomicInteger();

		@Override
		public void accept(double val) {
			count.incrementAndGet();
			if (val >= 0d)
				numPositive.incrementAndGet();
		}
		
	}
	
	private static class DoubleMedianConsumer implements DoubleConsumer {
		
		private final List<Double> sortedVals = new ArrayList<>();
		private double sum = 0d; // in case we need sum/mean

		@Override
		public void accept(double val) {
			sum += val;
			int index = Collections.binarySearch(sortedVals, val);
			if (index < 0)
				index = -(index+1);
			sortedVals.add(index, val);
		}
		
		public double getMedian() {
			int len = sortedVals.size();
			Preconditions.checkState(len > 0, "must have at least one value");
			if (len % 2 == 1)
				return sortedVals.get((len+1)/2-1);
			else {
				double lower = sortedVals.get(len/2-1);
				double upper = sortedVals.get(len/2);

				return (lower + upper) * 0.5;
			}	
		}
		
		public double getSum() {
			return sum;
		}
		
		public double getMean() {
			return sum/(double)sortedVals.size();
		}
		
	}
	
	public enum AggregationMethod {
		MEAN("Mean", true) {
			@Override
			public double calculate(DoubleStream stream) {
				return stream.average().getAsDouble();
			}
		},
		MEDIAN("Median", true) {
			@Override
			public double calculate(DoubleStream stream) {
//				return DataUtils.median_sorted(stream.sorted().toArray());
				DoubleMedianConsumer counter = new DoubleMedianConsumer();
				stream.forEach(counter);
				return counter.getMedian();
			}
		},
		SUM("Sum", true) {
			@Override
			public double calculate(DoubleStream stream) {
				return stream.sum();
			}
		},
		MIN("Minimum", true) {
			@Override
			public double calculate(DoubleStream stream) {
				return stream.min().getAsDouble();
			}
		},
		MAX("Maximum", true) {
			@Override
			public double calculate(DoubleStream stream) {
				return stream.max().getAsDouble();
			}
		},
		FRACT_POSITIVE("Fraction Positive", false) {
			@Override
			public double calculate(DoubleStream stream) {
				DoubleSignCounter counter = new DoubleSignCounter();
				stream.forEach(counter);
				return counter.numPositive.doubleValue()/counter.count.doubleValue();
			}
		},
		NUM_POSITIVE("Num Positive", false) {
			@Override
			public double calculate(DoubleStream stream) {
				DoubleSignCounter counter = new DoubleSignCounter();
				stream.forEach(counter);
				return counter.numPositive.doubleValue();
			}
		},
		NUM_NEGATIVE("Num Negative", false) {
			@Override
			public double calculate(DoubleStream stream) {
				DoubleSignCounter counter = new DoubleSignCounter();
				stream.forEach(counter);
				return (double)(counter.count.get() - counter.numPositive.get());
			}
		},
		GREATER_SUM_MEDIAN("Max[Sum,Median]", true) {
			public double calculate(DoubleStream stream) {
				DoubleMedianConsumer counter = new DoubleMedianConsumer();
				stream.forEach(counter);
				return Math.max(counter.getSum(), counter.getMedian());
			}
		},
		GREATER_MEAN_MEDIAN("Max[Mean,Median]", true) {
			public double calculate(DoubleStream stream) {
				DoubleMedianConsumer counter = new DoubleMedianConsumer();
				stream.forEach(counter);
				return Math.max(counter.getMean(), counter.getMedian());
			}
		};
		
		private String name;
		private boolean hasUnits;
		
		private AggregationMethod(String name, boolean hasUnits) {
			this.name = name;
			this.hasUnits = hasUnits;
		}
		
		@Override
		public String toString() {
			return name;
		}
		
		public abstract double calculate(DoubleStream stream);
	}
	
	public static class ReceiverDistribution {
		public final int receiverID;
		public final DoubleStream stream;
		
		public ReceiverDistribution(int receiverID, List<Double> values) {
			this(receiverID, values.stream().mapToDouble(d -> d));
		}
		
		public ReceiverDistribution(int receiverID, double[] values) {
			this(receiverID, DoubleStream.of(values));
		}
		
		public ReceiverDistribution(int receiverID, DoubleStream stream) {
			this.receiverID = receiverID;
			this.stream = stream;
		}
		
		public int getReceiverID() {
			return receiverID;
		}
	}
	
	public static interface AggregationLayer extends Named {
		
		public Collection<ReceiverDistribution> aggregate(int higherLevelID, Collection<ReceiverDistribution> dists);
		
	}
	
	public static interface TerminalLayer extends AggregationLayer {
		
		public double get(Collection<ReceiverDistribution> dists);
		
	}
	
	private static ReceiverDistribution flatten(int receiverID, Collection<ReceiverDistribution> dists) {
		Preconditions.checkState(!dists.isEmpty());
		DoubleStream stream;
		if (dists.size() == 1) {
			stream = dists.iterator().next().stream;
		} else if (dists.size() == 2) {
			Iterator<ReceiverDistribution> it = dists.iterator();
			ReceiverDistribution dist1 = it.next();
			ReceiverDistribution dist2 = it.next();
			stream = DoubleStream.concat(dist1.stream, dist2.stream);
		} else {
			stream = dists.stream().flatMapToDouble(s -> s.stream);
		}
		return new ReceiverDistribution(receiverID, stream);
	}
	
	public static class FlatteningLayer implements AggregationLayer {

		@Override
		public Collection<ReceiverDistribution> aggregate(int higherLevelID, Collection<ReceiverDistribution> dists) {
			return Collections.singleton(flatten(higherLevelID, dists));
		}

		@Override
		public String getName() {
			return "Flatten";
		}
		
	}
	
	public static class ReceiverFlatteningLayer implements AggregationLayer {

		@Override
		public Collection<ReceiverDistribution> aggregate(int higherLevelID, Collection<ReceiverDistribution> dists) {
			Map<Integer, List<ReceiverDistribution>> grouped = dists.stream().collect(
					Collectors.groupingBy(ReceiverDistribution::getReceiverID));
			List<ReceiverDistribution> ret = new ArrayList<>(grouped.keySet().size());
			for (Integer receiverID : grouped.keySet())
				ret.add(flatten(receiverID, grouped.get(receiverID)));
			return ret;
		}

		@Override
		public String getName() {
			return "ReceiverFlatten";
		}
		
	}
	
	public static class ProcessLayer implements TerminalLayer {
		
		private AggregationMethod method;

		public ProcessLayer(AggregationMethod method) {
			this.method = method;
		}

		@Override
		public Collection<ReceiverDistribution> aggregate(int higherLevelID, Collection<ReceiverDistribution> dists) {
			double[] values = new double[dists.size()];
			int index = 0;
			for (ReceiverDistribution dist : dists)
				values[index++] = method.calculate(dist.stream);
			return Collections.singleton(new ReceiverDistribution(higherLevelID, values));
		}

		@Override
		public double get(Collection<ReceiverDistribution> dists) {
			ReceiverDistribution dist;
			if (dists.size() > 1) {
				// flatten it for final processing
				dist = flatten(-1, dists);
			} else {
				Preconditions.checkState(dists.size() == 1, "No distributions left at this layer");
				dist = dists.iterator().next();
			}
			return method.calculate(dist.stream);
		}

		@Override
		public String getName() {
			return method.toString();
		}
		
	}
	
	public static class PassthroughLayer implements AggregationLayer {

		@Override
		public Collection<ReceiverDistribution> aggregate(int higherLevelID, Collection<ReceiverDistribution> dists) {
			return dists;
		}

		@Override
		public String getName() {
			return "Passthrough";
		}
		
	}
	
//	/**
//	 * Convencience method to get an aggregated calculator that uses the median patch interaction for sect-to-sect,
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
		private List<AggregationLayer> layers;

		private Builder(StiffnessType type, SubSectStiffnessCalculator calc) {
			this.type = type;
			this.calc = calc;
			this.layers = new ArrayList<>();
		}
		
		public Builder flatten() {
			layers.add(new FlatteningLayer());
			return this;
		}
		
		public Builder receiverFlatten() {
			layers.add(new ReceiverFlatteningLayer());
			return this;
		}
		
		public Builder process(AggregationMethod method) {
			layers.add(new ProcessLayer(method));
			return this;
		}
		
		public Builder passthrough() {
			layers.add(new PassthroughLayer());
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
			Preconditions.checkState(layers.size() == 2, "Must have supplied a sect-to-sect aggregation level (and nothing further)");
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
			Preconditions.checkState(layers.size() >= 2, "Must have supplied at least a sect-to-sect aggregation level first");
			Preconditions.checkState(layers.size() < 4, "Aggregation levels already completely specified");
			if (layers.size() == 2)
				flatten(); // flatten at sects-to-sect first
			process(aggMethod);
			return this;
		}
		
		public AggregatedStiffnessCalculator get() {
			return new AggregatedStiffnessCalculator(type, calc, this.layers.toArray(new AggregationLayer[0]));
		}
	}
	
	public AggregatedStiffnessCalculator(StiffnessType type, SubSectStiffnessCalculator calc, AggregationLayer... layers) {
		super();
		this.type = type;
		this.calc = calc;
		Preconditions.checkState(layers.length > 0, "must supply at least 1 aggregation layer");
		Preconditions.checkState(layers.length < 5, "only 4 aggregation layers are possible");
		for (int i=0; i<layers.length; i++)
			Preconditions.checkNotNull(layers[i], "Layer at level %s is null", i);
		Preconditions.checkState(layers[layers.length-1] instanceof TerminalLayer,
				"Final layer must be a terminal layer (but is %s)", layers[layers.length-1].getName());
		this.layers = layers;
		this.sect_id_multiplier = Integer.MAX_VALUE / calc.getSubSects().size();
	}
	
	private int uniquePatchID(int sectID, int patchID) {
		return sectID*sect_id_multiplier + patchID;
	}
	
	private Collection<ReceiverDistribution> aggRecieverPatches(FaultSection source, FaultSection receiver) {
		Preconditions.checkState(layers.length > 0, "Patch aggregation layer not supplied");
		
		StiffnessDistribution dist = calc.calcStiffnessDistribution(source, receiver);
		double[][] values = dist.get(type);
		double[] receiverPatchVals = new double[values.length];
		Preconditions.checkState(receiverPatchVals.length < sect_id_multiplier-1, "Potential unique patch ID overflow");
		
		List<ReceiverDistribution> receiverDists = new ArrayList<>(values.length);
		for (int r=0; r<receiverPatchVals.length; r++)
			receiverDists.add(new ReceiverDistribution(uniquePatchID(receiver.getSectionId(), r), values[r]));
		
		return layers[0].aggregate(receiver.getSectionId(), receiverDists);
	}
	
	private boolean isSectToSectCacheable() {
		return layers[1] instanceof ProcessLayer && (layers[0] instanceof ProcessLayer || layers[0] instanceof FlatteningLayer);
	}
	
	private double getCachedSectToSect(FaultSection source, FaultSection receiver) {
		AggregationMethod patchAggMethod = null;
		if (layers[0] instanceof ProcessLayer) {
			// we aggregated at the patch layer
			patchAggMethod = ((ProcessLayer)layers[0]).method;
		}  else {
			Preconditions.checkState(layers[0] instanceof FlatteningLayer);
		}
		
		if (cache == null)
			cache = calc.getAggregationCache(type);
		
		StiffnessAggregation aggregated = cache.get(patchAggMethod, source, receiver);
		if (aggregated == null) {
			// need to calculate and cache it
			Collection<ReceiverDistribution> receiverPatchDists = aggRecieverPatches(source, receiver);
			Preconditions.checkState(receiverPatchDists.size() == 1,
					"should only have 1 flattened or procssed distribution at sect-to-sect if cacheable");
			aggregated = new StiffnessAggregation(receiverPatchDists.iterator().next().stream);
			cache.put(patchAggMethod, source, receiver, aggregated);
		}
		return aggregated.get(((ProcessLayer)layers[1]).method);
	}
	
	private Collection<ReceiverDistribution> aggSectToSect(FaultSection source, FaultSection receiver) {
		Preconditions.checkState(layers.length > 1, "Section-to-section aggregation layer not supplied");
		
		// check the cache if possible at this layer
		if (isSectToSectCacheable())
			return Collections.singleton(new ReceiverDistribution(receiver.getSectionId(),
					DoubleStream.of(getCachedSectToSect(source, receiver))));
		
		Collection<ReceiverDistribution> receiverPatchDists = aggRecieverPatches(source, receiver);
		return layers[1].aggregate(receiver.getSectionId(), receiverPatchDists);
	}
	
	/**
	 * @param startIndex
	 * @return the first terminal layer encountered, starting at the given layer
	 */
	private TerminalLayer getTerminalLayer(int startIndex) {
		for (int i=startIndex; i<layers.length; i++)
			if (layers[i] instanceof TerminalLayer)
				return (TerminalLayer)layers[i];
		throw new IllegalStateException("Does not end in terminal layer?"); // should be checked before
	}
	
	public double calc(FaultSection source, FaultSection receiver) {
		Preconditions.checkState(layers.length > 1, "Section-to-section aggregation layer not supplied");
		
		if (isSectToSectCacheable())
			return getCachedSectToSect(source, receiver);
		
		Collection<ReceiverDistribution> receiverPatchDists = aggRecieverPatches(source, receiver);
		
		return getTerminalLayer(1).get(receiverPatchDists);
	}
	
	private Collection<ReceiverDistribution> aggSectsToSect(List<FaultSection> sources, FaultSection receiver) {
		Preconditions.checkState(layers.length > 2, "Sections-to-section aggregation layer not supplied");
		
		List<ReceiverDistribution> receiverSectDists = new ArrayList<>();
		for (FaultSection source : sources)
			receiverSectDists.addAll(aggSectToSect(source, receiver));
		
		return layers[2].aggregate(receiver.getSectionId(), receiverSectDists);
	}
	
	public double calc(List<FaultSection> sources, FaultSection receiver) {
		Preconditions.checkState(layers.length > 2, "Sections-to-section aggregation layer not supplied");
		
		List<ReceiverDistribution> receiverSectDists = new ArrayList<>();
		for (FaultSection source : sources)
			receiverSectDists.addAll(aggSectToSect(source, receiver));
		
		return getTerminalLayer(2).get(receiverSectDists);
	}
	
	// TODO delete this?
	private Collection<ReceiverDistribution> aggSectsToSects(List<FaultSection> sources, List<FaultSection> receivers) {
		Preconditions.checkState(layers.length > 3, "Sections-to-sections aggregation layer not supplied");
		
		List<ReceiverDistribution> receiverSectDists = new ArrayList<>(receivers.size());
		for (FaultSection receiver : receivers)
			receiverSectDists.addAll(aggSectsToSect(sources, receiver));
		
		return layers[3].aggregate(-1, receiverSectDists);
	}
	
	public double calc(List<FaultSection> sources, List<FaultSection> receivers) {
		Preconditions.checkState(layers.length > 3, "Sections-to-sections aggregation layer not supplied");
		Preconditions.checkState(layers[3] instanceof TerminalLayer,
				"Final layer must be terminal: %s", layers[3].getName());
		
		List<ReceiverDistribution> receiverSectDists = new ArrayList<>();
		for (FaultSection receiver : receivers)
			receiverSectDists.addAll(aggSectsToSect(sources, receiver));
		
		return ((TerminalLayer)layers[3]).get(receiverSectDists);
	}

	/**
	 * 
	 * @return true if the sequence of aggregations will result in values with units of stiffness (MPa),
	 * otherwise false (e.g., for fractions or counts)
	 */
	public boolean hasUnits() {
		boolean hasUnits = true;
		for (AggregationLayer layer : layers)
			if (layer instanceof ProcessLayer)
				hasUnits = hasUnits && ((ProcessLayer)layer).method.hasUnits;
		return hasUnits;
	}
	
	public String getScalarName() {
		String name = type.getName();
		for (AggregationLayer layer : layers) {
			if (layer instanceof ProcessLayer) {
				AggregationMethod aggMethod = ((ProcessLayer)layer).method;
				if (aggMethod == AggregationMethod.FRACT_POSITIVE)
					name = "Fract ≥0";
				else if (aggMethod == AggregationMethod.NUM_POSITIVE)
					name = "Num ≥0";
				else if (aggMethod == AggregationMethod.NUM_NEGATIVE)
					name = "Num Negative";
			}
		}
		return name;
	}
	
	public StiffnessType getType() {
		return type;
	}
	
	@Override
	public String toString() {
		return Arrays.stream(layers).map(s -> s.getName()).collect(Collectors.joining(" -> ", "AggStiffnessCalc[", "]"));
	}

	public SubSectStiffnessCalculator getCalc() {
		return calc;
	}

}
