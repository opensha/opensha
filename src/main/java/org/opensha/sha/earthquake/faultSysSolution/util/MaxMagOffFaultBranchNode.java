package org.opensha.sha.earthquake.faultSysSolution.util;

import java.text.DecimalFormat;
import java.util.List;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public interface MaxMagOffFaultBranchNode extends LogicTreeNode {
	
	public double getMaxMagOffFault();
	
	public TectonicRegionType getTectonicRegime();
	
	@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
	@DoesNotAffect(FaultSystemSolution.RATES_FILE_NAME)
	@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
	@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
	@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
	public static class Default implements MaxMagOffFaultBranchNode, ValuedLogicTreeNode<Double> {
		
		private TectonicRegionType trt;
		private double mMax;
		private double weight;
		private String name;
		private String shortName;
		private String prefix;

		public Default(TectonicRegionType trt, double mMax, double weight, String name, String shortName, String prefix) {
			this.trt = trt;
			init(mMax, Double.class, weight, name, shortName, prefix);
		}
		
		public void init(Double value, Class<? extends Double> valueClass, double weight, String name, String shortName,
				String filePrefix) {
			this.mMax = value;
			this.weight = weight;
			this.name = name;
			this.shortName = shortName;
			this.prefix = filePrefix;
		}

		@Override
		public Double getValue() {
			return getMaxMagOffFault();
		}

		@Override
		public Class<? extends Double> getValueType() {
			return Double.class;
		}

		@Override
		public double getNodeWeight() {
			return weight;
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
		public double getMaxMagOffFault() {
			return mMax;
		}

		@Override
		public TectonicRegionType getTectonicRegime() {
			return trt;
		}
		
	}
	
	public static class DistributionSamplingLevel extends LogicTreeLevel.AbstractContinuousDistributionSampledLevel<Default> {
		
		protected TectonicRegionType trt;

		@SuppressWarnings("unused") // deserialization
		private DistributionSamplingLevel(String name, String shortName) {
			super(name, shortName);
		}

		public DistributionSamplingLevel(String name, String shortName, TectonicRegionType trt, ContinuousDistribution dist) {
			super(name, shortName, dist, 1, "Sample ", "Sample", "Sample");
			this.trt = trt;
		}

		@Override
		public Default build(Double value, double weight, String name, String shortName,
				String filePrefix) {
			return new Default(trt, value, weight, name, shortName, filePrefix);
		}

		@Override
		public Class<? extends Default> getType() {
			return Default.class;
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = super.toJsonObject();
			
			if (trt != null)
				json.add("tectonicRegime", new JsonPrimitive(trt.name()));
			
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			if (jsonObj.has("tectonicRegime"))
				trt = TectonicRegionType.valueOf(jsonObj.get("tectonicRegime").getAsString());
			super.initFromJsonObject(jsonObj);
		}
		
	}
	
	public static class FixedValueLevel extends LogicTreeLevel.DataBackedLevel<Default>
	implements LogicTreeLevel.ValueBackedLevel<Double, Default> {
		
		protected TectonicRegionType trt;
		private double mmax = Double.NaN;
		
		private Default node;

		@SuppressWarnings("unused") // deserialization
		private FixedValueLevel(String name, String shortName) {
			super(name, shortName);
		}

		public FixedValueLevel(String name, String shortName, TectonicRegionType trt, double value) {
			super(name, shortName);
			this.trt = trt;
			this.mmax = value;
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			
			json.add("tectonicRegime", new JsonPrimitive(trt.name()));
			json.add("mmax", new JsonPrimitive(mmax));
			
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			trt = TectonicRegionType.valueOf(jsonObj.get("tectonicRegime").getAsString());
			mmax = jsonObj.get("mmax").getAsDouble();
		}
		
		public void setValue(double mmax) {
			this.mmax = mmax;
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
				String name = oDF.format(mmax);
				node = new Default(trt, mmax, 1d, "Mmax="+name, name, "Mmax"+name);
			}
			return List.of(node);
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			return this.node == node;
		}

		@Override
		public Default build(Double value, double weight, String name, String shortName, String filePrefix) {
			if (node == null) {
				// build it
				Preconditions.checkState(Double.isNaN(mmax) || value.doubleValue() == mmax);
				node = new Default(trt, mmax, weight, name, shortName, filePrefix);
			} else {
				Preconditions.checkState(value.doubleValue() == mmax);
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
