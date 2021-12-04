package org.opensha.commons.util.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;

/**
 * Interface for a module that can be averaged across multiple instances of the same type
 * 
 * @author kevin
 *
 * @param <E>
 */
@ModuleHelper
public interface AverageableModule<E extends OpenSHA_Module> extends OpenSHA_Module {
	
	/**
	 * Creates an accumulator that can build an average of multiple instances of this module
	 * 
	 * @return
	 */
	public AveragingAccumulator<E> averagingAccumulator();
	
	public default E getAverage(Collection<E> modules) {
		return getAverage(new ArrayList<>(modules), null);
	}
	
	public default E getAverage(List<E> modules, List<Double> relWeights) {
		Preconditions.checkState(modules.size() > 0, "Must supply at least 1 module");
		Preconditions.checkState(relWeights == null || relWeights.size() == modules.size());
		
		AveragingAccumulator<E> accumulator = averagingAccumulator();
		
		for (int i=0; i<modules.size(); i++) {
			E module = modules.get(i);
			double relWeight = relWeights == null ? 1d/(double)modules.size() : relWeights.get(i);
			accumulator.process(module, relWeight);
		}
		
		return accumulator.getAverage();
	}
	
	/**
	 * Accumulator that can average multiple instances of the same module
	 * 
	 * @author kevin
	 *
	 * @param <E>
	 */
	public interface AveragingAccumulator<E extends OpenSHA_Module> {
		
		public void process(E module, double relWeight);
		
		public E getAverage();
	}
	
	/**
	 * Helper for a module that is a constant value, where any instance can be copied over in an averaging operation
	 * 
	 * @author kevin
	 *
	 * @param <E>
	 */
	@ModuleHelper
	public interface ConstantAverageable<E extends OpenSHA_Module> extends AverageableModule<E> {

		@Override
		default AveragingAccumulator<E> averagingAccumulator() {
			return new AveragingAccumulator<>() {

				private E module;

				@Override
				public void process(E module, double relWeight) {
					if (this.module == null)
						this.module = module;
				}

				@Override
				public E getAverage() {
					Preconditions.checkNotNull(module);
					return module;
				}
				
			};
		}

		@Override
		default E getAverage(Collection<E> modules) {
			return modules.iterator().next();
		}
	}
	
	public static void scaleToTotalWeight(double[] values, double totWeight) {
		Preconditions.checkState(totWeight > 0d);
		if (totWeight == 1d)
			return;
		double scale = 1d/totWeight;
		for (int i=0; i<values.length; i++)
			values[i] *= scale;
	}

}
