package org.opensha.sha.simulators.stiffness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.util.DataUtils;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessDistribution;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

public class AggregatedStiffnessCalculator {

	private StiffnessType type;
	private SubSectStiffnessCalculator calc;
	private transient AggregatedStiffnessCache cache;
	
	/**
	 * Optional. If supplied, patch interactions will be first aggregated at the receiver patch level
	 * using this method. Otherwise, all interactions will be bundled together for aggregation at the
	 * section-to-section level.
	 */
	private AggregationMethod receiverPatchAggMethod;
	/**
	 * Aggregation method of patch interactions at the section-to-section level.
	 */
	private AggregationMethod sectToSectAggMethod;
	/**
	 * Aggregation method for many sources to one receiver.
	 */
	private AggregationMethod sectsToSectAggMethod;
	/**
	 * Aggregation method for many sources to many receivers. If a sects-to-sect aggregation method is
	 * supplied, it will first be aggregated at that level (and with that method) and then aggregated
	 * further with this method.
	 */
	private AggregationMethod sectsToSectsAggMethod;
	
	public static class StiffnessAggregation {
		
		private final double[] aggValues;
//		private final int numValues;
		
		public StiffnessAggregation(double[] values) {
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			int numPositive = 0;
			double sum = 0d;
			List<Double> sortedVals = new ArrayList<>();
			int count = 0;
			for (double val : values) {
				count++;
				min = Math.min(min, val);
				max = Math.max(max, val);
				sum += val;
				if (val >= 0)
					numPositive++;
				int index = Collections.binarySearch(sortedVals, val);
				if (index < 0)
					index = -(index+1);
				sortedVals.add(index, val);
			}
			this.aggValues = new double[AggregationMethod.values().length];
			aggValues[AggregationMethod.MEAN.ordinal()] = sum/(double)count;
			aggValues[AggregationMethod.MEDIAN.ordinal()] = DataUtils.median_sorted(Doubles.toArray(sortedVals));
			aggValues[AggregationMethod.SUM.ordinal()] = sum;
			aggValues[AggregationMethod.MIN.ordinal()] = min;
			aggValues[AggregationMethod.MAX.ordinal()] = max;
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
		
//		public StiffnessAggregation(StiffnessAggregation prevAgg, double[] values) {
//			this.numValues = prevAgg.numValues + values.length;
//			StiffnessAggregation newAgg = new StiffnessAggregation(values);
//			this.aggValues = new double[AggregationMethod.values().length];
//			aggValues[AggregationMethod.SUM.ordinal()] = prevAgg.get(AggregationMethod.SUM) + newAgg.get(AggregationMethod.SUM);
//			aggValues[AggregationMethod.MEAN.ordinal()] = aggValues[AggregationMethod.SUM.ordinal()]/(double)numValues;
//			aggValues[AggregationMethod.MEDIAN.ordinal()] = Double.NaN; // can't combine medians
//			aggValues[AggregationMethod.MIN.ordinal()] = Math.min(prevAgg.get(AggregationMethod.MIN), newAgg.get(AggregationMethod.MIN));
//			aggValues[AggregationMethod.MAX.ordinal()] = Math.max(prevAgg.get(AggregationMethod.MAX), newAgg.get(AggregationMethod.MAX));
//			aggValues[AggregationMethod.FRACT_POSITIVE.ordinal()] = (prevAgg.get(AggregationMethod.FRACT_POSITIVE)*prevAgg.numValues
//					+ newAgg.get(AggregationMethod.FRACT_POSITIVE)*values.length)/(double)numValues;
//			aggValues[AggregationMethod.NUM_POSITIVE.ordinal()] =
//					prevAgg.get(AggregationMethod.NUM_POSITIVE) + newAgg.get(AggregationMethod.NUM_POSITIVE);
//			aggValues[AggregationMethod.NUM_NEGATIVE.ordinal()] =
//					prevAgg.get(AggregationMethod.NUM_NEGATIVE) + newAgg.get(AggregationMethod.NUM_NEGATIVE);
//			aggValues[AggregationMethod.GREATER_SUM_MEDIAN.ordinal()] = Double.NaN; // can't combine medians
//			aggValues[AggregationMethod.GREATER_MEAN_MEDIAN.ordinal()] = Double.NaN; // can't combine medians
//		}
		
		public double get(AggregationMethod aggMethod) {
			return aggValues[aggMethod.ordinal()];
		}
	}
	
	public enum AggregationMethod {
		MEAN("Mean", true) {
			@Override
			public double calculate(double[] values) {
				return StatUtils.mean(values);
			}

			@Override
			public double calculate(List<Double> values) {
				return values.stream().collect(Collectors.averagingDouble(f -> f));
			}
		},
		MEDIAN("Median", true) {
			@Override
			public double calculate(double[] values) {
				return DataUtils.median(values);
			}

			@Override
			public double calculate(List<Double> values) {
				return calculate(Doubles.toArray(values));
			}
		},
		SUM("Sum", true) {
			@Override
			public double calculate(double[] values) {
				return StatUtils.sum(values);
			}

			@Override
			public double calculate(List<Double> values) {
				return values.stream().collect(Collectors.summingDouble(f -> f));
			}
		},
		MIN("Minimum", true) {
			@Override
			public double calculate(double[] values) {
				return StatUtils.min(values);
			}

			@Override
			public double calculate(List<Double> values) {
				return values.stream().collect(Collectors.summarizingDouble(f -> f)).getMin();
			}
		},
		MAX("Maximum", true) {
			@Override
			public double calculate(double[] values) {
				return StatUtils.max(values);
			}

			@Override
			public double calculate(List<Double> values) {
				return values.stream().collect(Collectors.summarizingDouble(f -> f)).getMax();
			}
		},
		FRACT_POSITIVE("Fraction Positive", false) {
			@Override
			public double calculate(double[] values) {
				return (double)NUM_POSITIVE.calculate(values)/(double)values.length;
			}

			@Override
			public double calculate(List<Double> values) {
				return (double)NUM_POSITIVE.calculate(values)/(double)values.size();
			}
		},
		NUM_POSITIVE("Num Positive", false) {
			@Override
			public double calculate(double[] values) {
				int count = 0;
				for (double val : values)
					if (val >= 0)
						val++;
				return count;
			}

			@Override
			public double calculate(List<Double> values) {
				int count = 0;
				for (double val : values)
					if (val >= 0)
						val++;
				return count;
			}
		},
		NUM_NEGATIVE("Num Negative", false) {
			@Override
			public double calculate(double[] values) {
				return values.length - NUM_POSITIVE.calculate(values);
			}

			@Override
			public double calculate(List<Double> values) {
				return values.size() - NUM_POSITIVE.calculate(values);
			}
		},
		GREATER_SUM_MEDIAN("Max[Sum,Median]", true) {
			@Override
			public double calculate(double[] values) {
				return Double.max(SUM.calculate(values), MEDIAN.calculate(values));
			}

			@Override
			public double calculate(List<Double> values) {
				return Double.max(SUM.calculate(values), MEDIAN.calculate(values));
			}
		},
		GREATER_MEAN_MEDIAN("Max[Mean,Median]", true) {
			@Override
			public double calculate(double[] values) {
				return Double.max(MEAN.calculate(values), MEDIAN.calculate(values));
			}

			@Override
			public double calculate(List<Double> values) {
				return Double.max(MEAN.calculate(values), MEDIAN.calculate(values));
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
		
		public abstract double calculate(double[] values);
		
		public abstract double calculate(List<Double> values);
	}
	
	/**
	 * Convencience method to get an aggregated calculator that uses the median patch interaction for sect-to-sect,
	 * and sums those medians across multiple sects (sects-to-sect or sects-to-sects).
	 * 
	 * @param type
	 * @param calc
	 * @return
	 */
	public static AggregatedStiffnessCalculator buildMedianPatchSumSects(StiffnessType type, SubSectStiffnessCalculator calc) {
		return builder(type, calc).sectToSectAgg(AggregationMethod.MEDIAN).sectsToSectsAgg(AggregationMethod.SUM).get();
	}
	
	public static Builder builder(StiffnessType type, SubSectStiffnessCalculator calc) {
		return new Builder(type, calc);
	}
	
	public static class Builder {
		
		private AggregatedStiffnessCalculator calc;
		
		private Builder(StiffnessType type, SubSectStiffnessCalculator calc) {
			this.calc = new AggregatedStiffnessCalculator(type, calc);
		}
		
		/**
		 * Aggregate section-to-section patch interactions at the receiver patch level first using the given
		 * aggregation method.
		 * 
		 * @param aggMethod
		 * @return
		 */
		public Builder receiverPatchAgg(AggregationMethod aggMethod) {
			Preconditions.checkNotNull(calc, "Builder already finalized");
			calc.receiverPatchAggMethod = aggMethod;
			return this;
		}
		
		/**
		 * Aggregate method for section-to-section patch interactions. If a receiver patch aggregation is supplied,
		 * that will be applied first.
		 * 
		 * @param aggMethod
		 * @return
		 */
		public Builder sectToSectAgg(AggregationMethod aggMethod) {
			Preconditions.checkNotNull(calc, "Builder already finalized");
			calc.sectToSectAggMethod = aggMethod;
			return this;
		}
		
		/**
		 * Aggregate method for multiple source sections to a single receiver section. Must also supply a
		 * section-section aggregation level (via the sectToSectAggg(AggregationMethod) method).
		 * 
		 * @param aggMethod
		 * @return
		 */
		public Builder sectsToSectAgg(AggregationMethod aggMethod) {
			Preconditions.checkNotNull(calc, "Builder already finalized");
			calc.sectsToSectAggMethod = aggMethod;
			return this;
		}
		
		/**
		 * Aggregate method for multiple source sections to a multiple receiver sections. If a
		 * sections-to-section aggregation method is supplied, it will be applied first. Must also supply a
		 * section-section aggregation level (via the sectToSectAggg(AggregationMethod) method).
		 * 
		 * @param aggMethod
		 * @return
		 */
		public Builder sectsToSectsAgg(AggregationMethod aggMethod) {
			Preconditions.checkNotNull(calc, "Builder already finalized");
			calc.sectsToSectsAggMethod = aggMethod;
			return this;
		}
		
		public AggregatedStiffnessCalculator get() {
			AggregatedStiffnessCalculator calc = this.calc;
			this.calc = null;
			return calc;
		}
	}
	
	private AggregatedStiffnessCalculator(StiffnessType type, SubSectStiffnessCalculator calc) {
		super();
		this.type = type;
		this.calc = calc;
	}
	
	public double[] calcRecieverPatchAggregated(FaultSection source, FaultSection receiver) {
		Preconditions.checkNotNull(receiverPatchAggMethod,
				"Can't aggregate at the receiver patch level without a patch aggregation method");
		StiffnessDistribution dist = calc.calcStiffnessDistribution(source, receiver);
		double[][] values = dist.get(type);
		double[] receiverPatchVals = new double[values.length];
		for (int r=0; r<receiverPatchVals.length; r++) {
			StiffnessAggregation aggregated = new StiffnessAggregation(values[r]);
			receiverPatchVals[r] = aggregated.get(receiverPatchAggMethod);
		}
		
		return receiverPatchVals;
	}
	
	public double calcSectAggregated(FaultSection source, FaultSection receiver) {
		Preconditions.checkNotNull(sectToSectAggMethod, "sect-to-sect aggregation method is null");
		return getSectAggregation(source, receiver).get(sectToSectAggMethod);
	}
	
	public StiffnessAggregation getSectAggregation(FaultSection source, FaultSection receiver) {
		
		if (cache == null)
			cache = calc.getAggregationCache(type);
		
		StiffnessAggregation aggregation = cache.get(receiverPatchAggMethod, source, receiver);
		if (aggregation != null) {
			// it's already cached
//			System.out.println("Cache hit!");
			return aggregation;
		}
		
		// need to calculate it
		if (receiverPatchAggMethod == null) {
			// not aggregating at receiver patch level, calculate from full source-receiver patch distribution
			StiffnessDistribution dist = calc.calcStiffnessDistribution(source, receiver);
			double[][] values = dist.get(type);
			Preconditions.checkState(values.length > 0);
			int numSources = values[0].length;
			Preconditions.checkState(numSources > 0);
			double[] flattened = new double[values.length*values[0].length];
			for (int r=0; r<values.length; r++) {
				Preconditions.checkState(values[r].length == numSources, "array sizes inconsistent");
				System.arraycopy(values[r], 0, flattened, r*numSources, numSources);
			}
			aggregation = new StiffnessAggregation(flattened);
		} else {
			// aggregate on receiver pathces, and then aggregate those 
			double[] patchValues = calcRecieverPatchAggregated(source, receiver);
			aggregation = new StiffnessAggregation(patchValues);
		}
		// cache it
//		System.out.println("Cache miss!");
		cache.put(receiverPatchAggMethod, source, receiver, aggregation);
		return aggregation;
	}
	
	public double calcSectsToSect(Collection<FaultSection> sources, FaultSection receiver) {
		AggregationMethod method = sectsToSectAggMethod;
		if (method == null)
			// use the sects-to-sects aggregation method
			method = sectsToSectsAggMethod;
		Preconditions.checkNotNull(method, "sects-to-sect and sects-to-sects aggregation methods both null");
		List<Double> vals = new ArrayList<>();
		for (FaultSection source : sources) {
			if (source == receiver)
				continue;
			vals.add(calcSectAggregated(source, receiver));
		}
		return method.calculate(vals);
	}
	
	public StiffnessAggregation getSectsToSectAggregation(Collection<FaultSection> sources, FaultSection receiver) {
		List<Double> vals = new ArrayList<>();
		for (FaultSection source : sources) {
			if (source == receiver)
				continue;
			vals.add(calcSectAggregated(source, receiver));
		}
		
		return new StiffnessAggregation(Doubles.toArray(vals));
	}
	
	public double calcSectsToSects(Collection<FaultSection> sources, Collection<FaultSection> receivers) {
		Preconditions.checkNotNull(sectsToSectsAggMethod, "sects-to-sect aggregation method is null");
		List<Double> vals = getSectsToSectsVals(sources, receivers);
		return sectsToSectsAggMethod.calculate(vals);
	}
	
	public StiffnessAggregation getSectsToSectsAggregation(Collection<FaultSection> sources, Collection<FaultSection> receivers) {
		List<Double> vals = getSectsToSectsVals(sources, receivers);
		
		return new StiffnessAggregation(Doubles.toArray(vals));
	}

	private List<Double> getSectsToSectsVals(Collection<FaultSection> sources, Collection<FaultSection> receivers) {
		List<Double> vals = new ArrayList<>();
		if (sectsToSectAggMethod == null) {
			// throw all sect-to-sect interactions in one bin and aggregate
			for (FaultSection receiver : receivers) {
				for (FaultSection source : sources) {
					if (source == receiver)
						continue;
					vals.add(calcSectAggregated(source, receiver));
				}
			}
		} else {
			for (FaultSection receiver : receivers)
				vals.add(calcSectsToSect(sources, receiver));
		}
		return vals;
	}

	/**
	 * 
	 * @return true if the sequence of aggregations will result in values with units of stiffness (MPa),
	 * otherwise false (e.g., for fractions or counts)
	 */
	public boolean hasUnits() {
		boolean hasUnits = true;
		for (AggregationMethod aggMethod : getAggMethods())
			hasUnits = hasUnits && aggMethod.hasUnits;
		return hasUnits;
	}
	
	public String getScalarName() {
		String name = type.getName();
		for (AggregationMethod aggMethod : getAggMethods()) {
			if (aggMethod == AggregationMethod.FRACT_POSITIVE)
				name = "Fract ≥0";
			else if (aggMethod == AggregationMethod.NUM_POSITIVE)
				name = "Num ≥0";
			else if (aggMethod == AggregationMethod.NUM_NEGATIVE)
				name = "Num Negative";
		}
		return name;
	}
	
	private List<AggregationMethod> getAggMethods() {
		List<AggregationMethod> aggMethods = new ArrayList<>();
		if (receiverPatchAggMethod != null)
			aggMethods.add(receiverPatchAggMethod);
		if (sectToSectAggMethod != null)
			aggMethods.add(sectToSectAggMethod);
		if (sectsToSectAggMethod != null)
			aggMethods.add(sectsToSectAggMethod);
		if (sectsToSectsAggMethod != null)
			aggMethods.add(sectsToSectsAggMethod);
		return aggMethods;
	}
	
	public StiffnessType getType() {
		return type;
	}
	
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder("AggStiffnessCalc[");
		if (receiverPatchAggMethod != null)
			str.append("receiverPatches=").append(receiverPatchAggMethod);
		else
			str.append("allPatchInteractions");
		if (sectToSectAggMethod != null) {
			str.append(" -> sectToSect=").append(sectToSectAggMethod);
			
			if (sectsToSectAggMethod != null)
				str.append(" -> sectsToSect=").append(sectsToSectAggMethod);
			
			if (sectsToSectsAggMethod != null)
				str.append(" -> sectsToSects=").append(sectsToSectsAggMethod);
		}
		return str.append("]").toString();
	}

	public SubSectStiffnessCalculator getCalc() {
		return calc;
	}

	public AggregationMethod getReceiverPatchAggMethod() {
		return receiverPatchAggMethod;
	}

	public AggregationMethod getSectToSectAggMethod() {
		return sectToSectAggMethod;
	}

	public AggregationMethod getSectsToSectAggMethod() {
		return sectsToSectAggMethod;
	}

	public AggregationMethod getSectsToSectsAggMethod() {
		return sectsToSectsAggMethod;
	}

}
