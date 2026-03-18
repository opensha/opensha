package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.text.DecimalFormat;
import java.util.List;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel.AbstractContinuousDistributionSampledLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.DataBackedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.ValueBackedLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SectionSupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class NSHM26_SupraSeisBValues implements SectionSupraSeisBValues.Constant {

	private TectonicRegionType trt;
	private double value;
	private double weight;
	private String name;
	private String shortName;
	private String filePrefix;
	
	@SuppressWarnings("unused") // deserialization
	private NSHM26_SupraSeisBValues() {}
	
	public NSHM26_SupraSeisBValues(TectonicRegionType trt, double value, double weight, String name, String shortName, String filePrefix) {
		this.trt = trt;
		this.value = value;
		this.weight = weight;
		this.name = name;
		this.shortName = shortName;
		this.filePrefix = filePrefix;
	}

	@Override
	public double getB() {
		return value;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
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

	public TectonicRegionType getTectonicRegime() {
		return trt;
	}

	@Override
	public void init(Double value, Class<? extends Double> valueClass, double weight, String name, String shortName,
			String filePrefix) {
		this.value = value;
		this.weight = weight;
		this.name = name;
		this.shortName = shortName;
		this.filePrefix = filePrefix;
	}
	
	public static class DistributionSamplingLevel extends AbstractContinuousDistributionSampledLevel<NSHM26_SupraSeisBValues> {
		
		protected TectonicRegionType trt;

		@SuppressWarnings("unused") // deserialization
		private DistributionSamplingLevel() {
			
		}

		protected DistributionSamplingLevel(TectonicRegionType trt, ContinuousDistribution dist) {
			super(NSHM26_RegionLoader.getNameForTRT(trt)+" b-value Samples",
					NSHM26_RegionLoader.getNameForTRT(trt)+"-bSamples", dist, "b Sample ", "bSample", "bSample");
			this.trt = trt;
		}

		@Override
		protected NSHM26_SupraSeisBValues build(int index, Double value, double weightEach) {
			return new NSHM26_SupraSeisBValues(trt, value, weightEach,
					getNodeName(index), getNodeShortName(index), getNodeFilePrefix(index));
		}

		@Override
		public Class<? extends NSHM26_SupraSeisBValues> getType() {
			return NSHM26_SupraSeisBValues.class;
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = super.toJsonObject();
			
			json.add("tectonicRegime", new JsonPrimitive(trt.name()));
			
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			trt = TectonicRegionType.valueOf(jsonObj.get("tectonicRegime").getAsString());
			super.initFromJsonObject(jsonObj);
		}
		
	}
	
	public static class FixedValueLevel extends DataBackedLevel<NSHM26_SupraSeisBValues> implements ValueBackedLevel<Double, NSHM26_SupraSeisBValues> {
		
		protected TectonicRegionType trt;
		private double b = Double.NaN;
		
		private NSHM26_SupraSeisBValues node;

		@SuppressWarnings("unused") // deserialization
		private FixedValueLevel() {
		}

		protected FixedValueLevel(TectonicRegionType trt, double value) {
			super(NSHM26_RegionLoader.getNameForTRT(trt)+" Fixed b-value",
					NSHM26_RegionLoader.getNameForTRT(trt)+"-FixedB");
			this.trt = trt;
			this.b = value;
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			
			json.add("tectonicRegime", new JsonPrimitive(trt.name()));
			json.add("b", new JsonPrimitive(b));
			
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			trt = TectonicRegionType.valueOf(jsonObj.get("tectonicRegime").getAsString());
			b = jsonObj.get("b").getAsDouble();
		}
		
		public void setValue(double b) {
			this.b = b;
			this.node = null;
		}

		@Override
		public Class<? extends NSHM26_SupraSeisBValues> getType() {
			return NSHM26_SupraSeisBValues.class;
		}
		
		private static final DecimalFormat oDF = new DecimalFormat("0.#");

		@Override
		public List<? extends NSHM26_SupraSeisBValues> getNodes() {
			if (node == null) {
				String name = oDF.format(b);
				node = new NSHM26_SupraSeisBValues(trt, b, 1d, NSHM26_RegionLoader.getNameForTRT(trt)+" b="+name, name, "b"+name);
			}
			return List.of(node);
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			return this.node == node;
		}

		@Override
		public NSHM26_SupraSeisBValues build(Double value, double weight, String name, String shortName,
				String filePrefix) {
			if (node == null) {
				// build it
				Preconditions.checkState(Double.isNaN(b) || value.doubleValue() == b);
				node = new NSHM26_SupraSeisBValues(trt, b, weight, name, shortName, filePrefix);
			} else {
				Preconditions.checkState(value.doubleValue() == b);
				Preconditions.checkState(weight == node.getNodeWeight(null));
			}
			return node;
		}
		
	}

}
