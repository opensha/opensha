package org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.UnaryOperator;

import org.apache.commons.rng.RestorableUniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel.AbstractRandomlySampledLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnableLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.DataBackedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.SamplingMethod;
import org.opensha.commons.logicTree.LogicTreeLevel.ValueBackedLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeNode.ValuedLogicTreeNode;
import org.opensha.commons.util.json.DoubleRangeAdapter;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.DeformationModelDistSampler.AverageSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.DeformationModelDistSampler.FixedFractileSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.DeformationModelDistSampler.GroupedFractileSampler;
import org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling.DeformationModelDistSampler.SectionGroupingType;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;

/**
 * Abstract class and level structures for a {@link RupSetDeformationModel} where slip rates are sampled from
 * {@link ContinuousDistribution}s according to a {@link DeformationModelDistSampler}.
 * @param <S>
 */
public abstract class RupSetDeformationModelDistribution<S extends DeformationModelDistSampler>
implements RupSetDeformationModel, ValuedLogicTreeNode<S> {
	
	private S sampler;
	private double weight;
	private String name;
	private String shortName;
	private String filePrefix;
	private Class<? extends S> valueClass;

	protected RupSetDeformationModelDistribution() {}

	public RupSetDeformationModelDistribution(String name, String shortName, String prefix, double weight, S sampler) {
		Preconditions.checkNotNull(sampler, "Sampler cannot be null");
		init(sampler, (Class<? extends S>)sampler.getClass(), weight, name, shortName, prefix);
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
	public String getFilePrefix() {
		return filePrefix;
	}
	
	@Override
	public String toString() {
		return shortName;
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
	public void init(S sampler, Class<? extends S> valueClass, double weight, String name, String shortName,
			String filePrefix) {
		this.sampler = sampler;
		this.valueClass = valueClass;
		this.weight = weight;
		this.name = name;
		this.shortName = shortName;
		this.filePrefix = filePrefix;
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, filePrefix, sampler, shortName, weight);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RupSetDeformationModelDistribution other = (RupSetDeformationModelDistribution) obj;
		return Objects.equals(name, other.name) && Objects.equals(filePrefix, other.filePrefix) && Objects.equals(sampler, other.sampler)
				&& Objects.equals(shortName, other.shortName)
				&& Double.doubleToLongBits(weight) == Double.doubleToLongBits(other.weight);
	}
	
	public static abstract class Simple extends RupSetDeformationModelDistribution<DeformationModelDistSampler> {
		
		protected Simple() {}

		public Simple(String name, String shortName, String prefix, double weight, DeformationModelDistSampler sampler) {
			super(name, shortName, prefix, weight, sampler);
		}
		
		public abstract void initDistributions(LogicTreeBranch<? extends LogicTreeNode> branch,
				List<? extends FaultSection> fullSects, List<? extends FaultSection> subSects) throws IOException;
		
		public abstract ContinuousDistribution getSlipRateDistribution(FaultSection subSect);
		
		public abstract UnaryOperator<List<? extends FaultSection>> getPostProcessor();

		@Override
		public List<? extends FaultSection> apply(RupSetFaultModel faultModel,
				LogicTreeBranch<? extends LogicTreeNode> branch, List<? extends FaultSection> fullSects,
				List<? extends FaultSection> subSects) throws IOException {
			return apply(branch, fullSects, subSects, getValue());
		}
		
		public List<? extends FaultSection> apply(LogicTreeBranch<? extends LogicTreeNode> branch,
				List<? extends FaultSection> fullSects, List<? extends FaultSection> subSects,
				DeformationModelDistSampler sampler) throws IOException {
			initDistributions(branch, fullSects, subSects);
			sampler.init(subSects, fullSects);
			for (FaultSection subSect : subSects) {
				ContinuousDistribution dist = getSlipRateDistribution(subSect);
				Preconditions.checkNotNull(dist, "No distribution found for sect %s with parentID=%s",
						subSect.getSectionName(), subSect.getParentSectionId());
				double slipRate = sampler.getValue(subSect, dist);
				subSect.setAveSlipRate(slipRate);
			}
			UnaryOperator<List<? extends FaultSection>> postProcess = getPostProcessor();
			if (postProcess != null)
				subSects = postProcess.apply(subSects);
			return subSects;
		}
		
	}

	public static abstract class GroupedSamplingLevel<E extends RupSetDeformationModelDistribution<? super GroupedFractileSampler>>
	extends AbstractRandomlySampledLevel<GroupedFractileSampler, E> {

		private SectionGroupingType groupingType;

		private GroupedSamplingLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName, "DM Sample ", "DM-Sample-", "DMSample");
		}
		
		public GroupedSamplingLevel(String levelName, String levelShortName, SectionGroupingType groupingType) {
			this(levelName, levelShortName);
			this.groupingType = groupingType;
		}

		@Override
		public Class<? extends GroupedFractileSampler> getValueType() {
			return GroupedFractileSampler.class;
		}

		@Override
		protected void doBuild(long seed, int numNodes, SamplingMethod samplingMethod, double weightEach) {
			List<E> nodes = new ArrayList<>(numNodes);
			Random rand = new Random(seed);
			for (int i=0; i<numNodes; i++)
				nodes.add(build(i, new GroupedFractileSampler(rand.nextLong(), groupingType), weightEach));
			this.nodes = nodes;
		}
		
	}
	
	/**
	 * Builds an array of samples from U(0,1) for the given seed and sampling method
	 * 
	 * @param seed
	 * @param numSamples
	 * @param samplingMethod
	 * @return
	 */
	private static double[] buildCmlProbSamples(long seed, int numSamples, SamplingMethod samplingMethod) {
		RestorableUniformRandomProvider rand = RandomSource.XO_RO_SHI_RO_128_PP.create(seed);
		
		double[] samples = new double[numSamples];
		if (samplingMethod.isLHS()) {
			for (int i=0; i<numSamples; i++) {
				double binStart = (double)i / numSamples;
				double binEnd = (double)(i + 1) / numSamples;
				samples[i] = rand.nextDouble(binStart, binEnd);
			}
		} else {
			for (int i=0; i<numSamples; i++)
				samples[i] = rand.nextDouble();
		}
		
		return samples;
	}

	public static abstract class UniformSamplingLevel<E extends RupSetDeformationModelDistribution<? super FixedFractileSampler>>
	extends AbstractRandomlySampledLevel<FixedFractileSampler, E>
	implements BinnableLevel<FixedFractileSampler, E, BinnedUniformSamplingLevel> {

		public UniformSamplingLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName, "DM Sample ", "DM-Sample-", "DMSample");
		}

		@Override
		public Class<? extends FixedFractileSampler> getValueType() {
			return FixedFractileSampler.class;
		}

		@Override
		protected void doBuild(long seed, int numNodes, SamplingMethod samplingMethod, double weightEach) {
			double[] samples = buildCmlProbSamples(seed, numNodes, samplingMethod);
			
			List<E> nodes = new ArrayList<>(numNodes);
			for (int i=0; i<numNodes; i++)
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
			DecimalFormat df = new DecimalFormat("0.#");
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
		
	}
	
	public static class BinnedUniformSamplingLevel extends DataBackedLevel<SimpleValuedNode<Range<Double>>> 
	implements ValueBackedLevel<Range<Double>, SimpleValuedNode<Range<Double>>>,
	BinnedLevel<FixedFractileSampler, SimpleValuedNode<Range<Double>>> {
		
		private List<SimpleValuedNode<Range<Double>>> nodes;
		
		// this extra cast to Class<?> resolves compile errors that don't show up in eclipse, which is annoying
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
	
	public static abstract class FixedProbabilityLevel<E extends RupSetDeformationModelDistribution<? super FixedFractileSampler>>
	extends DataBackedLevel<E> implements ValueBackedLevel<FixedFractileSampler, E> {
		
		private double fixedValue = Double.NaN;
		private E node;

		@SuppressWarnings("unused") // deserialization
		private FixedProbabilityLevel(String name, String shortName) {
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
	
	public static abstract class AverageLevel<E extends RupSetDeformationModelDistribution<? super AverageSampler>>
	extends DataBackedLevel<E> implements ValueBackedLevel<AverageSampler, E> {
		
		private E node;
		private AverageSampler sampler;

		public AverageLevel(String name, String shortName) {
			super(name, shortName);
			sampler = new AverageSampler();
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
						node = build(sampler, 1d, "Average Deformation Model", "Average", "Average");
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
	
//	public static class ContinuousDistributionBinnedLevel extends DataBackedLevel<SimpleValuedNode<Range<Double>>> 
//	implements ValueBackedLevel<Range<Double>, SimpleValuedNode<Range<Double>>>,
//	BinnedLevel<Double, SimpleValuedNode<Range<Double>>> {

}
