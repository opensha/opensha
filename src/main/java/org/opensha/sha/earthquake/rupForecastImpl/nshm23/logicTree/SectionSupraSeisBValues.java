package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.text.DecimalFormat;
import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

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
	
	public static class Default implements Constant {
		
		private double value;
		private double weight;
		private String name;
		private String shortName;
		private String filePrefix;

		public Default(double value, double weight, String name, String shortName, String filePrefix) {
			init(value, Double.class, weight, name, shortName, filePrefix);
		}

		@Override
		public double getB() {
			return value;
		}

		@Override
		public String getFilePrefix() {
			return filePrefix;
		}

		@Override
		public String getShortName() {
			return shortName;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public double getNodeWeight() {
			return weight;
		}

		@Override
		public void init(Double value, Class<? extends Double> valueClass, double weight, String name,
				String shortName, String filePrefix) {
			this.value = value;
			this.weight = weight;
			this.name = name;
			this.shortName = shortName;
			this.filePrefix = filePrefix;
		}
		
	}
	
	public static class DistributionSamplingLevel extends LogicTreeLevel.AbstractContinuousDistributionSampledLevel<Default> {

		@SuppressWarnings("unused") // deserialization
		private DistributionSamplingLevel(String name, String shortName) {
			super(name, shortName);
		}

		public DistributionSamplingLevel(String name, String shortName, ContinuousDistribution dist) {
			super(name, shortName, dist, 3, "b Sample ", "bSample", "bSample");
		}

		@Override
		public Default build(Double value, double weight, String name, String shortName,
				String filePrefix) {
			return new Default(value, weight, name, shortName, filePrefix);
		}

		@Override
		public Class<? extends Default> getType() {
			return Default.class;
		}
		
	}
	
	public static class FixedValueLevel extends LogicTreeLevel.DataBackedLevel<Default>
	implements LogicTreeLevel.ValueBackedLevel<Double, Default> {
		
		private double b = Double.NaN;
		
		private Default node;

		@SuppressWarnings("unused") // deserialization
		private FixedValueLevel(String name, String shortName) {
			super(name, shortName);
		}

		public FixedValueLevel(String name, String shortName, double value) {
			super(name, shortName);
			this.b = value;
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			
			json.add("b", new JsonPrimitive(b));
			
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			b = jsonObj.get("b").getAsDouble();
		}
		
		public void setValue(double b) {
			this.b = b;
			this.node = null;
		}

		@Override
		public Class<? extends Default> getType() {
			return Default.class;
		}
		
		private static final DecimalFormat oDF = new DecimalFormat("0.#");

		@Override
		public List<? extends Default> getNodes() {
			if (node == null) {
				String name = oDF.format(b);
				node = new Default(b, 1d, "b="+name, name, "b"+name);
			}
			return List.of(node);
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			return this.node == node;
		}

		@Override
		public Default build(Double value, double weight, String name, String shortName,
				String filePrefix) {
			if (node == null) {
				// build it
				Preconditions.checkState(Double.isNaN(b) || value.doubleValue() == b);
				node = new Default(b, weight, name, shortName, filePrefix);
			} else {
				Preconditions.checkState(value.doubleValue() == b);
				Preconditions.checkState(weight == node.getNodeWeight(null));
			}
			return node;
		}

		@Override
		public Class<? extends Double> getValueType() {
			return Double.class;
		}
		
	}

}
