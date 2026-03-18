package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.text.DecimalFormat;
import java.util.List;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.logicTree.LogicTreeLevel.AbstractContinuousDistributionSampledLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.DataBackedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.ValueBackedLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.logicTree.LogicTreeNode.SimpleValuedNode;
import org.opensha.sha.earthquake.faultSysSolution.util.MaxMagOffFaultBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class NSHM26_OffFaultMmax extends SimpleValuedNode<Double> implements MaxMagOffFaultBranchNode {
	
	private TectonicRegionType trt;

	@SuppressWarnings("unused") // deserialization
	private NSHM26_OffFaultMmax() {};

	NSHM26_OffFaultMmax(TectonicRegionType trt, double value, double weight, String name, String shortName, String filePrefix) {
		super(value, Double.class, weight, name, shortName, filePrefix);
		this.trt = trt;
	}

	@Override
	public double getMaxMagOffFault() {
		return getValue();
	}

	@Override
	public TectonicRegionType getTectonicRegime() {
		return trt;
	}
	
	public static class DistributionSamplingLevel extends AbstractContinuousDistributionSampledLevel<NSHM26_OffFaultMmax> {
		
		protected TectonicRegionType trt;

		@SuppressWarnings("unused") // deserialization
		private DistributionSamplingLevel() {
			
		}

		protected DistributionSamplingLevel(TectonicRegionType trt, ContinuousDistribution dist) {
			super(NSHM26_RegionLoader.getNameForTRT(trt)+" Off Fault Mmax Samples",
					NSHM26_RegionLoader.getNameForTRT(trt)+"-MmaxOffSamples", dist, "Sample ", "Sample", "Sample");
			this.trt = trt;
		}

		@Override
		protected NSHM26_OffFaultMmax build(int index, Double value, double weightEach) {
			return new NSHM26_OffFaultMmax(trt, value, weightEach,
					getNodeName(index), getNodeShortName(index), getNodeFilePrefix(index));
		}

		@Override
		public Class<? extends NSHM26_OffFaultMmax> getType() {
			return NSHM26_OffFaultMmax.class;
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
	
	public static class FixedValueLevel extends DataBackedLevel<NSHM26_OffFaultMmax> implements ValueBackedLevel<Double, NSHM26_OffFaultMmax> {
		
		protected TectonicRegionType trt;
		private double mmax = Double.NaN;
		
		private NSHM26_OffFaultMmax node;

		@SuppressWarnings("unused") // deserialization
		private FixedValueLevel() {
		}

		protected FixedValueLevel(TectonicRegionType trt, double value) {
			super(NSHM26_RegionLoader.getNameForTRT(trt)+" Fixed Off Fault Mmax",
					NSHM26_RegionLoader.getNameForTRT(trt)+"-FixedMmaxOff");
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
		public Class<? extends NSHM26_OffFaultMmax> getType() {
			return NSHM26_OffFaultMmax.class;
		}
		
		private static final DecimalFormat oDF = new DecimalFormat("0.#");

		@Override
		public List<? extends NSHM26_OffFaultMmax> getNodes() {
			if (node == null) {
				String name = oDF.format(mmax);
				node = new NSHM26_OffFaultMmax(trt, mmax, 1d, NSHM26_RegionLoader.getNameForTRT(trt)+" Mmax="+name, name, "Mmax"+name);
			}
			return List.of(node);
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			return this.node == node;
		}

		@Override
		public NSHM26_OffFaultMmax build(Double value, double weight, String name, String shortName, String filePrefix) {
			if (node == null) {
				// build it
				Preconditions.checkState(Double.isNaN(mmax) || value.doubleValue() == mmax);
				node = new NSHM26_OffFaultMmax(trt, mmax, weight, name, shortName, filePrefix);
			} else {
				Preconditions.checkState(value.doubleValue() == mmax);
				Preconditions.checkState(weight == node.getNodeWeight(null));
			}
			return node;
		}
		
	}

}
