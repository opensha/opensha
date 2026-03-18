package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

import com.google.common.base.Preconditions;

public interface SectionSupraSeisBValues extends LogicTreeNode {
	
	/**
	 * @param rupSet
	 * @return section-specific b-values, or null if all b-values are the same
	 */
	public double[] getSectBValues(FaultSystemRupSet rupSet);
	
	/**
	 * @return the b-value (can be NaN if {@link #getSectBValues(List)} is non-null).
	 */
	public double getB();
	
	public static interface Constant extends SectionSupraSeisBValues, ValuedLogicTreeNode<Double> {
		
		/**
		 * @param rupSet
		 * @return section-specific b-values, or null if all b-values are the same
		 */
		public default double[] getSectBValues(FaultSystemRupSet rupSet) {
			return null;
		}

		@Override
		default Double getValue() {
			return getB();
		}

		@Override
		default Class<? extends Double> getValueType() {
			return Double.class;
		}

		@Override
		default void init(Double value, Class<? extends Double> valueClass, double weight, String name,
				String shortName, String filePrefix) {
			// do nothing
			Preconditions.checkState(value.doubleValue() == getB(), "Init called with b=%s but we have b=%s", value, getB());
		}
	}
	
	public static double momentWeightedAverage(FaultSystemRupSet rupSet, double[] sectSpecificBValues) {
		double sumMoment = 0d;
		double sumProduct = 0d;
		Preconditions.checkState(sectSpecificBValues.length == rupSet.getNumSections());
		for (int s=0; s<sectSpecificBValues.length; s++) {
			double moment = FaultMomentCalc.getMoment(rupSet.getAreaForSection(s), rupSet.getSlipRateForSection(s));
			sumMoment += moment;
			sumProduct += moment*sectSpecificBValues[s];
		}
		if (sumMoment == 0d)
			return StatUtils.mean(sectSpecificBValues);
		return sumProduct/sumMoment;
	}

}
