package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.util.random.RandomGenerator;

import org.apache.commons.rng.UniformRandomProvider;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.logicTree.LogicTreeNode;

public interface BranchChoiceSampler<E> {
	
	public E sample(UniformRandomProvider random);
	
	public E sample(RandomGenerator random);
	
	public static class EnumSampler<E extends Enum<E> & LogicTreeNode> implements BranchChoiceSampler<E> {
		
		private Class<E> clazz;
		private IntegerPDF_FunctionSampler sampler;
		private E[] values;

		public EnumSampler(Class<E> clazz) {
			this.clazz = clazz;
			values = clazz.getEnumConstants();
			double[] weights = new double[values.length];
			for (int i=0; i<values.length; i++)
				weights[i] = values[i].getNodeWeight(null);
			sampler = new IntegerPDF_FunctionSampler(weights);
		}

		@Override
		public E sample(UniformRandomProvider random) {
			return values[sampler.getRandomInt(random.nextDouble())];
		}

		@Override
		public E sample(RandomGenerator random) {
			return values[sampler.getRandomInt(random.nextDouble())];
		}
		
	}
	
//	public static class DistributionSampler implements BranchChoiceSampler<Double> {
//		
//	}

}
