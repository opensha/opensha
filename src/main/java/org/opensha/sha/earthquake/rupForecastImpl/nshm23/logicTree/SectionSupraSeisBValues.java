package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.json.ContinuousDistributionTypeAdapter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling.SectDistributionSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling.SectDistributionSampler.AverageSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling.SectDistributionSampler.FixedFractileSampler;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public interface SectionSupraSeisBValues extends LogicTreeNode {
	
	/**
	 * @param rupSet
	 * @param branch
	 * @return section-specific b-values, or null if all b-values are the same
	 */
	public double[] getSectBValues(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch);
	
	/**
	 * @param rupSet
	 * @param branch
	 * @return the b-value (can be NaN if {@link #getSectBValues(List)} is non-null).
	 */
	public double getB(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch);
	
	public static interface FixedWeight extends SectionSupraSeisBValues, FixedWeightNode {}
	
	public static interface Constant extends FixedWeight, ValuedLogicTreeNode<Double> {
		
		/**
		 * @param rupSet
		 * @return section-specific b-values, or null if all b-values are the same
		 */
		public default double[] getSectBValues(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branc) {
			return null;
		}
		
		public double getB();
		
		public default double getB(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch) {
			return getB();
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
	
	@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
	@Affects(FaultSystemSolution.RATES_FILE_NAME)
	@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
	@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
	@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
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
		
		public static DistributionSamplingLevel fromJson(JsonObject json) {
			LogicTreeLevel.Adapter<Default> adapter = new LogicTreeLevel.Adapter<>();
			return (DistributionSamplingLevel)adapter.fromJsonTree(json);
		}
		
	}
	
	public static class DistributionAverageLevel extends FixedValueLevel {

		private ContinuousDistribution dist;

		@SuppressWarnings("unused") // deserialization
		private DistributionAverageLevel(String name, String shortName) {
			super(name, shortName);
		}

		public DistributionAverageLevel(String name, String shortName, ContinuousDistribution dist) {
			super(name, shortName, dist.getMean());
			this.dist = dist;
		}
		
		public ContinuousDistribution getDistribution() {
			return dist;
		}
		
		private static ContinuousDistributionTypeAdapter distAdapter = ContinuousDistributionTypeAdapter.get();
		
		@Override
		public JsonObject toJsonObject() {
			JsonObject json = super.toJsonObject();
			
			if (dist != null)
				json.add("dist", distAdapter.toJsonTree(dist));
			
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			super.initFromJsonObject(jsonObj);
			
			if (jsonObj.has("dist"))
				dist = distAdapter.fromJsonTree(jsonObj.get("dist"));
		}

		public List<Default> getDiscretizedValues(int numDiscr) {
			Preconditions.checkState(numDiscr > 1);
			double[] fractiles = new double[numDiscr];
			double step = 1.0 / numDiscr; // also the weight
			for (int i = 0; i < numDiscr; i++)
				fractiles[i] = (i + 0.5) * step;
			
			List<Default> ret = new ArrayList<>(numDiscr);
			
			for (double fractile : fractiles) {
				double b = dist.inverseCumulativeProbability(fractile);
				
				String name = "f"+(float)fractile+", b="+(float)b;
				
				ret.add(new Default(b, step, name, name, name));
			}
			
			return ret;
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
	
	public static abstract class SectSpecificDistributionSample<S extends SectDistributionSampler>
	implements FixedWeight, ValuedLogicTreeNode<S> {
		
		private double weight;
		private String name;
		private String shortName;
		private String prefix;
		private S sampler;
		private Class<? extends S> valueClass;

		public SectSpecificDistributionSample(String name, String shortName, String prefix, double weight, S sampler) {
			init(sampler, (Class<? extends S>) sampler.getClass(), weight, name, shortName, prefix);
		}
		
		public abstract void initDistributions(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch);
		
		public abstract ContinuousDistribution getSectDistribution(FaultSection subSect);

		@Override
		public double[] getSectBValues(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch) {
			initDistributions(rupSet, branch);
			
			S sampler = getValue();
			List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
			sampler.init(subSects);
			
			double[] ret = new double[rupSet.getNumSections()];
			for (int s=0; s<ret.length; s++) {
				FaultSection sect = subSects.get(s);
				ContinuousDistribution dist = getSectDistribution(sect);
				ret[s] = sampler.getValue(sect, dist);
			}
			return ret;
		}

		@Override
		public double getB(FaultSystemRupSet rupSet, LogicTreeBranch<? extends LogicTreeNode> branch) {
			return Double.NaN;
		}

		@Override
		public String getFilePrefix() {
			return prefix;
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
		public S getValue() {
			return sampler;
		}

		@Override
		public Class<? extends S> getValueType() {
			return valueClass;
		}

		@Override
		public void init(S value, Class<? extends S> valueClass, double weight, String name, String shortName,
				String filePrefix) {
			this.valueClass = valueClass;
			this.weight = weight;
			this.name = name;
			this.shortName = shortName; 
			this.prefix = filePrefix;
			this.sampler = value;
		}
		
	}
	
	public static abstract class SectSpecificDistributionAverage extends SectSpecificDistributionSample<SectDistributionSampler> {

		public SectSpecificDistributionAverage(String name, String shortName, String prefix, double weight) {
			super(name, shortName, prefix, weight, new AverageSampler());
		}
		
		public List<SectSpecificDistributionSample<SectDistributionSampler>> getDiscretizedSamplers(
				FaultSystemRupSet rupSet, LogicTreeBranch<?> branch, int numDiscr) {
			Preconditions.checkState(numDiscr > 1);
			double[] fractiles = new double[numDiscr];
			double step = 1.0 / numDiscr; // also the weight
			for (int i = 0; i < numDiscr; i++)
				fractiles[i] = (i + 0.5) * step;
			
			List<SectSpecificDistributionSample<SectDistributionSampler>> ret = new ArrayList<>(numDiscr);
			
			final SectSpecificDistributionAverage avg = this;
			avg.initDistributions(rupSet, branch);
			
			for (double fractile : fractiles) {
				String name = "f"+(float)fractile;
				
				final FixedFractileSampler sampler = new FixedFractileSampler(fractile);
				ret.add(new SectSpecificDistributionSample<SectDistributionSampler>(name, name, name, step, sampler) {
					
					@Override
					public void initDistributions(FaultSystemRupSet rupSet2, LogicTreeBranch<? extends LogicTreeNode> branch2) {
						Preconditions.checkState(rupSet2.equals(rupSet));
						Preconditions.checkState(branch2.equals(branch));
					}
					
					@Override
					public ContinuousDistribution getSectDistribution(FaultSection subSect) {
						return avg.getSectDistribution(subSect);
					}
				});
			}
			
			return ret;
		}
		
	}

}
