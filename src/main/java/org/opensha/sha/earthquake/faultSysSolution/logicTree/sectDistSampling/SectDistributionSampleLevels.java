package org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.logicTree.LogicTreeLevel.AbstractRandomlySampledLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnableLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.DataBackedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.FractileSamplingLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.ValueBackedLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeNode.SimpleValuedNode;
import org.opensha.commons.logicTree.LogicTreeNode.ValuedLogicTreeNode;
import org.opensha.commons.util.RandomSeedUtils;
import org.opensha.commons.util.json.DoubleRangeAdapter;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling.SectDistributionSampler.AverageSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling.SectDistributionSampler.FixedFractileSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling.SectDistributionSampler.GroupedFractileSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.sectDistSampling.SectDistributionSampler.SectionGroupingType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;

/**
 * Generic logic-tree level structures for section-specific {@link SectDistributionSampler}s.
 */
public final class SectDistributionSampleLevels {
	
	private SectDistributionSampleLevels() {}
	
	public static abstract class GroupedSamplingLevel<E extends ValuedLogicTreeNode<? super GroupedFractileSampler>>
	extends AbstractRandomlySampledLevel<GroupedFractileSampler, E> {

		private SectionGroupingType groupingType;

		protected GroupedSamplingLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}
		
		public GroupedSamplingLevel(String levelName, String levelShortName, SectionGroupingType groupingType) {
			this(levelName, levelShortName, groupingType, "Distribution Sample ", "Dist-Sample-", "DistSample");
		}
		
		public GroupedSamplingLevel(String levelName, String levelShortName, SectionGroupingType groupingType,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
			this.groupingType = groupingType;
		}

		@Override
		public Class<? extends GroupedFractileSampler> getValueType() {
			return GroupedFractileSampler.class;
		}

		@Override
		protected void doBuild(double[] unitSamples, double weightEach) {
			List<E> nodes = new ArrayList<>(unitSamples.length);
			long salt = RandomSeedUtils.seedForStrings(getClass().getName(), getName(), getShortName());
			for (int i=0; i<unitSamples.length; i++) {
				long seed = RandomSeedUtils.uniqueSeedCombination(salt, Double.doubleToLongBits(unitSamples[i]));
				nodes.add(build(i, new GroupedFractileSampler(seed, groupingType), weightEach));
			}
			this.nodes = nodes;
		}
		
	}
	
	public static abstract class UniformSamplingLevel<E extends ValuedLogicTreeNode<? super FixedFractileSampler>>
	extends AbstractRandomlySampledLevel<FixedFractileSampler, E>
	implements BinnableLevel<FixedFractileSampler, E, BinnedUniformSamplingLevel>,
	FractileSamplingLevel<FixedFractileSampler, E> {

		private double fractileFloor;

		protected UniformSamplingLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
			this.fractileFloor = 0d;
		}
		
		protected UniformSamplingLevel(String levelName, String levelShortName, double fractileFloor,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
			this.fractileFloor = fractileFloor;
		}

		@Override
		public Class<? extends FixedFractileSampler> getValueType() {
			return FixedFractileSampler.class;
		}

		@Override
		protected void doBuild(double[] unitSamples, double weightEach) {
			double[] samples = unitSamples.clone();
			if (fractileFloor > 0d) {
				Preconditions.checkState(fractileFloor < 1d);
				int numBelow = 0;
				for (int s=0; s<samples.length; s++) {
					if (samples[s] < fractileFloor) {
						samples[s] = fractileFloor;
						numBelow++;
					}
				}
				if (numBelow > 0)
					System.out.println(getName()+": set "+numBelow+"/"+samples.length+" to fractileFloor="+(float)fractileFloor);
			}
			
			List<E> nodes = new ArrayList<>(samples.length);
			for (int i=0; i<samples.length; i++)
				nodes.add(build(i, new FixedFractileSampler(samples[i]), weightEach));
			this.nodes = nodes;
		}

		@Override
		public BinnedUniformSamplingLevel toBinnedLevel() {
			return toBinnedLevel(3);
		}

		@Override
		public BinnedUniformSamplingLevel toBinnedLevel(int numBins) {
			Preconditions.checkState(numBins > 1);
			List<SimpleValuedNode<Range<Double>>> nodes = new ArrayList<>(numBins);
			double weightEach = 1d/(double)numBins;
			DecimalFormat df = new DecimalFormat("0.#%");
			for (int i=0; i<numBins; i++) {
				double binStart = (double)i / numBins;
				double binEnd = (double)(i + 1) / numBins;
				
				Range<Double> range = i == numBins-1 ? Range.closed(binStart, binEnd) : Range.closedOpen(binStart, binEnd);
				
				String name, shortName, binStr;
				if (i == 0) {
					binStr = "< p"+df.format(binEnd);
					if (numBins == 3) {
						name = "Low: "+binStr;
						shortName = "Low";
					} else {
						name = binStr;
						shortName = binStr;
					}
				} else if (i == numBins-1) {
					binStr = "> p"+df.format(binStart);
					if (numBins == 3) {
						name = "High: "+binStr;
						shortName = "High";
					} else {
						name = binStr;
						shortName = binStr;
					}
				} else {
					binStr = "p"+df.format(binStart)+"-"+df.format(binEnd);
					if (numBins == 3) {
						name = "Middle";
						shortName = "Middle";
					} else {
						name = binStr;
						shortName = binStr;
					}
				}
				
				nodes.add(new SimpleValuedNode<Range<Double>>(range, BinnedUniformSamplingLevel.VALUE_TYPE,
						weightEach, name, shortName, "Bin"+i));
			}
			return new BinnedUniformSamplingLevel(this, nodes);
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject obj = super.toJsonObject();
			
			obj.add("fractileFloor", new JsonPrimitive(fractileFloor));
			
			return obj;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			super.initFromJsonObject(jsonObj);
			
			if (jsonObj.has("fractileFloor"))
				fractileFloor = jsonObj.get("fractileFloor").getAsDouble();
		}

		@Override
		public double getFractile(FixedFractileSampler value) {
			return value.getFixedFractile();
		}
		
	}
	
	public static class BinnedUniformSamplingLevel extends DataBackedLevel<SimpleValuedNode<Range<Double>>> 
	implements ValueBackedLevel<Range<Double>, SimpleValuedNode<Range<Double>>>,
	BinnedLevel<FixedFractileSampler, SimpleValuedNode<Range<Double>>> {
		
		private List<SimpleValuedNode<Range<Double>>> nodes;
		
		private static Class<? extends SimpleValuedNode<Range<Double>>> TYPE =
				(Class<SimpleValuedNode<Range<Double>>>) (Class<?>) SimpleValuedNode.class;
		private static Class<? extends Range<Double>> VALUE_TYPE =
				(Class<? extends Range<Double>>) (Class<?>) Range.class;
		
		@SuppressWarnings("unused") // deserialization
		private BinnedUniformSamplingLevel() {};
		
		public BinnedUniformSamplingLevel(
				UniformSamplingLevel<?> samplingLevel,
				List<SimpleValuedNode<Range<Double>>> nodes) {
			super(samplingLevel.getName(), samplingLevel.getShortName());
			this.nodes = nodes;
			setAffected(samplingLevel.getAffected(), samplingLevel.getNotAffected(), false);
		}

		@Override
		public Class<? extends SimpleValuedNode<Range<Double>>> getType() {
			return TYPE;
		}

		@Override
		public List<? extends SimpleValuedNode<Range<Double>>> getNodes() {
			return nodes;
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			return nodes.contains(node);
		}
		
		public SimpleValuedNode<Range<Double>> getBin(FixedFractileSampler node) {
			return getBin(node.getFixedFractile());
		}
		
		public SimpleValuedNode<Range<Double>> getBin(Double value) {
			for (SimpleValuedNode<Range<Double>> bin : nodes)
				if (bin.getValue().contains(value))
					return bin;
			return null;
		}

		@Override
		public Class<? extends Range<Double>> getValueType() {
			return VALUE_TYPE;
		}

		@Override
		public TypeAdapter<Range<Double>> getValueTypeAdapter() {
			return new DoubleRangeAdapter();
		}

		@Override
		public SimpleValuedNode<Range<Double>> build(Range<Double> value, double weight, String name, String shortName,
				String filePrefix) {
			return new SimpleValuedNode<Range<Double>>(value, VALUE_TYPE, weight, name, shortName, filePrefix);
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			
			JsonArray binsArray = new JsonArray();
			
			DoubleRangeAdapter rangeAdapter = new DoubleRangeAdapter();
			
			for (SimpleValuedNode<Range<Double>> node : nodes) {
				JsonObject binObj = new JsonObject();

				binObj.add("range", rangeAdapter.toJsonTree(node.getValue()));
				binObj.add("name", new JsonPrimitive(node.getName()));
				binObj.add("shortName", new JsonPrimitive(node.getShortName()));
				binObj.add("filePrefix", new JsonPrimitive(node.getFilePrefix()));
				binObj.add("weight", new JsonPrimitive(node.getNodeWeight()));
				
				binsArray.add(binObj);
			}
			
			json.add("bins", binsArray);
			return json;
			
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			JsonArray bins = jsonObj.getAsJsonArray("bins");
			
			DoubleRangeAdapter rangeAdapter = new DoubleRangeAdapter();
			
			nodes = new ArrayList<>(bins.size());
			for (int i=0; i<bins.size(); i++) {
				JsonObject binObj = bins.get(i).getAsJsonObject();
				Range<Double> range = rangeAdapter.fromJsonTree(binObj.get("range"));
				String name = binObj.get("name").getAsString();
				String shortName = binObj.get("shortName").getAsString();
				String filePrefix = binObj.get("filePrefix").getAsString();
				double weight = binObj.get("weight").getAsDouble();
				nodes.add(build(range, weight, name, shortName, filePrefix));
			}
		}
		
	}
	
	public static abstract class FixedProbabilityLevel<E extends ValuedLogicTreeNode<? super FixedFractileSampler>>
	extends DataBackedLevel<E> implements ValueBackedLevel<FixedFractileSampler, E> {
		
		private double fixedValue = Double.NaN;
		private E node;

		@SuppressWarnings("unused") // deserialization
		protected FixedProbabilityLevel(String name, String shortName) {
			super(name, shortName);
		}

		public FixedProbabilityLevel(String name, String shortName, double fixedValue) {
			super(name, shortName);
			this.fixedValue = fixedValue;
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			
			json.add("cmlProb", new JsonPrimitive(fixedValue));
			
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			fixedValue = jsonObj.get("cmlProb").getAsDouble();
		}

		@Override
		public Class<? extends FixedFractileSampler> getValueType() {
			return FixedFractileSampler.class;
		}
		
		private void checkInitNode() {
			if (node == null) {
				synchronized (this) {
					if (node == null) {
						Preconditions.checkState(Double.isFinite(fixedValue));
						node = build(new FixedFractileSampler(fixedValue), 1d, "Fixed P="+(float)fixedValue,
								"FixedP="+(float)fixedValue, "FixedP"+(float)fixedValue);
					}
				}
			}
		}

		@Override
		public List<? extends E> getNodes() {
			checkInitNode();
			return List.of(node);
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			checkInitNode();
			return node != null && this.node.equals(node);
		}
		
	}
	
	public static abstract class AverageLevel<E extends ValuedLogicTreeNode<? super AverageSampler>>
	extends DataBackedLevel<E> implements ValueBackedLevel<AverageSampler, E> {
		
		private E node;
		private AverageSampler sampler;
		private String nodeName;
		private String nodeShortName;
		private String nodeFilePrefix;

		public AverageLevel(String name, String shortName) {
			this(name, shortName, "Average Distribution", "Average", "Average");
		}
		
		public AverageLevel(String name, String shortName, String nodeName, String nodeShortName,
				String nodeFilePrefix) {
			super(name, shortName);
			sampler = new AverageSampler();
			this.nodeName = nodeName;
			this.nodeShortName = nodeShortName;
			this.nodeFilePrefix = nodeFilePrefix;
		}

		@Override
		public JsonObject toJsonObject() {
			return null;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			// do nothing
		}

		@Override
		public Class<? extends AverageSampler> getValueType() {
			return AverageSampler.class;
		}
		
		private void checkInitNode() {
			if (node == null) {
				synchronized (this) {
					if (node == null)
						node = build(sampler, 1d, nodeName, nodeShortName, nodeFilePrefix);
				}
			}
		}

		@Override
		public List<? extends E> getNodes() {
			checkInitNode();
			return List.of(node);
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			checkInitNode();
			return node != null && this.node.equals(node);
		}
		
	}
}
