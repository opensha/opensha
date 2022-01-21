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
public interface AverageableModule<E extends AverageableModule<E>> extends OpenSHA_Module {
	
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
		
		/**
		 * @return the module class that this accumulator is applicable to
		 */
		public Class<E> getType();
		
		/**
		 * Convenience method to fetch and process a module of the correct type from the given container
		 * 
		 * @param container
		 * @param relWeight
		 * @throws IllegalStateException if the container doesn't contain a module of the correct type
		 */
		public default void processContainer(ModuleContainer<? super E> container, double relWeight)
				throws IllegalStateException {
			process(container.requireModule(getType()), relWeight);
		}
		
		/**
		 * Process the given instance of this module type, with the given weight
		 * 
		 * @param module
		 * @param relWeight
		 */
		public void process(E module, double relWeight);
		
		/**
		 * Builds an average version of this module from all previously processed instances
		 * 
		 * @return
		 */
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
	public interface ConstantAverageable<E extends AverageableModule<E>> extends AverageableModule<E> {
		
		/**
		 * @return the type of the returned constant averageable value
		 */
		public Class<E> getAveragingType();
		
		/**
		 * Used as a check when averaging to ensure that this really is constant
		 * 
		 * @param module
		 * @return true if identical, false otherwise
		 */
		public boolean isIdentical(E module);

		@Override
		default AveragingAccumulator<E> averagingAccumulator() {
			return new AveragingAccumulator<>() {

				private E module;

				@Override
				public void process(E module, double relWeight) {
					Preconditions.checkState(ConstantAverageable.this.isIdentical(module),
							"Averaging a ConstantAverageable instance but encountered a version (%s) that is not"
							+ " identical to the original instance (%s).", module, ConstantAverageable.this);
					if (this.module == null)
						this.module = module;
				}

				@Override
				public E getAverage() {
					Preconditions.checkNotNull(module);
					return module;
				}

				@Override
				public Class<E> getType() {
					return getAveragingType();
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
