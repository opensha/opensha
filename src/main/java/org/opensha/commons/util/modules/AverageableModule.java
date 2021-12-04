package org.opensha.commons.util.modules;

import java.util.Collection;

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
	 * Creates an accumulator that can build an average of the given number of instance of this module
	 * 
	 * @param num
	 * @return
	 */
	public AveragingAccumulator<E> averagingAccumulator(int num);
	
	public default E getAverage(Collection<E> modules) {
		Preconditions.checkState(modules.size() > 0, "Must supply at least 1 module");
		
		AveragingAccumulator<E> accumulator = averagingAccumulator(modules.size());
		
		for (E module : modules)
			accumulator.process(module);
		
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
		
		public void process(E module);
		
		public E getAverage();
	}
	
	/**
	 * Helper for module that constant and can just be copied over from the first isntance in any averaging operation
	 * 
	 * @author kevin
	 *
	 * @param <E>
	 */
	@ModuleHelper
	public interface ConstantAverageable<E extends OpenSHA_Module> extends AverageableModule<E> {

		@Override
		default AveragingAccumulator<E> averagingAccumulator(int num) {
			return new AveragingAccumulator<>() {

				private E module;

				@Override
				public void process(E module) {
					this.module = module;
					// do nothing
				}

				@Override
				public E getAverage() {
					return module;
				}
				
			};
		}

		@Override
		default E getAverage(Collection<E> modules) {
			return modules.iterator().next();
		}
	}

}
