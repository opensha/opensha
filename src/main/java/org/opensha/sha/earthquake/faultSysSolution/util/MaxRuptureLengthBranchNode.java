package org.opensha.sha.earthquake.faultSysSolution.util;

import java.text.DecimalFormat;
import java.util.List;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeNode.ValuedLogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public interface MaxRuptureLengthBranchNode extends ValuedLogicTreeNode<Double> {
	
	@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
	@Affects(FaultSystemSolution.RATES_FILE_NAME)
	@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
	@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
	@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
	public static class Default implements MaxRuptureLengthBranchNode {
		
		private Double maxLen;
		private double weight;
		private String name;
		private String shortName;
		private String filePrefix;

		public Default(double maxLen, double weight, String name, String shortName, String filePrefix) {
			init(maxLen, null, weight, name, shortName, filePrefix);
		}

		@Override
		public Double getValue() {
			return maxLen;
		}

		@Override
		public Class<? extends Double> getValueType() {
			return Double.class;
		}

		@Override
		public void init(Double maxLen, Class<? extends Double> valueClass, double weight, String name, String shortName,
				String filePrefix) {
			this.maxLen = maxLen;
			this.weight = weight;
			this.name = name;
			this.shortName = shortName;
			this.filePrefix = filePrefix;
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
		public String getShortName() {
			return shortName;
		}

		@Override
		public String getName() {
			return name;
		}
		
	}
	
	public static class DistributionSamplingLevel extends LogicTreeLevel.AbstractContinuousDistributionSampledLevel<Default> {

		@SuppressWarnings("unused") // deserialization
		private DistributionSamplingLevel(String name, String shortName) {
			super(name, shortName);
		}

		public DistributionSamplingLevel(String name, String shortName, ContinuousDistribution dist) {
			super(name, shortName, dist, 1, "Lmax Sample ", "LmaxSample", "LmaxSample");
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
		
		private double maxLen = Double.NaN;
		
		private Default node;

		@SuppressWarnings("unused") // deserialization
		private FixedValueLevel(String name, String shortName) {
			super(name, shortName);
		}

		public FixedValueLevel(String name, String shortName, double value) {
			super(name, shortName);
			this.maxLen = value;
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			
			json.add("maxLen", new JsonPrimitive(maxLen));
			
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			maxLen = jsonObj.get("maxLen").getAsDouble();
		}
		
		public void setValue(double maxLen) {
			this.maxLen = maxLen;
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
				String name = oDF.format(maxLen);
				node = new Default(maxLen, 1d, "Lmax="+name, name, "Lmax"+name);
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
				Preconditions.checkState(Double.isNaN(maxLen) || value.doubleValue() == maxLen);
				node = new Default(maxLen, weight, name, shortName, filePrefix);
			} else {
				Preconditions.checkState(value.doubleValue() == maxLen);
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
