package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.json.DoubleRangeAdapter;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.DataBackedLevel;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.PureGR;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.RateRecord;

public interface NSHM26_SeisRateModel extends LogicTreeNode {
	
	public abstract IncrementalMagFreqDist build(NSHM26_SeismicityRegions region, TectonicRegionType trt, EvenlyDiscretizedFunc refMFD, double mMax);
	
	public abstract RateRecord getRateRecord(NSHM26_SeismicityRegions region, TectonicRegionType trt);
	
	// this affects interface slip rates
	@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
	// this affects interface slip rates
	@Affects(FaultSystemSolution.RATES_FILE_NAME)
	@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
	@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
	@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
	public static class NSHM26_SiesRateModelSample extends SimpleValuedNode<PureGR> implements NSHM26_SeisRateModel {
		
		private NSHM26_SeismicityRegions region;
		private TectonicRegionType trt;

		@SuppressWarnings("unused") // deserialization
		private NSHM26_SiesRateModelSample() {}

		public NSHM26_SiesRateModelSample(PureGR value, NSHM26_SeismicityRegions region, TectonicRegionType trt, double weight, String name,
				String shortName, String filePrefix) {
			super(value, PureGR.class, weight, name, shortName, filePrefix);
			this.region = region;
			this.trt = trt;
		}

		@Override
		public IncrementalMagFreqDist build(NSHM26_SeismicityRegions region, TectonicRegionType trt,
				EvenlyDiscretizedFunc refMFD, double mMax) {
			PureGR value = getValue();
			Preconditions.checkState(this.region == null || region == this.region, "Region mismatch: %s != %s", region, this.region);
			Preconditions.checkState(this.trt == null || trt == this.trt, "TRT mismatch: %s != %s", trt, this.trt);
			return SeismicityRateFileLoader.buildIncrementalMFD(value, refMFD, mMax, Double.NaN);
		}

		@Override
		public RateRecord getRateRecord(NSHM26_SeismicityRegions region, TectonicRegionType trt) {
			Preconditions.checkState(this.region == null || region == this.region, "Region mismatch: %s != %s", region, this.region);
			Preconditions.checkState(this.trt == null || trt == this.trt, "TRT mismatch: %s != %s", trt, this.trt);
			return getValue();
		}
		
	}
	
	public static class BinnedSamplesLevel extends DataBackedLevel<BinnedSamplesNode>
	implements BinnedLevel<PureGR, BinnedSamplesNode>{
		
		private List<BinnedSamplesNode> nodes;
		
		@SuppressWarnings("unused") // deserialization
		private BinnedSamplesLevel() {}

		BinnedSamplesLevel(NSHM26_SeisRateModelSamples samplesLevel, List<BinnedSamplesNode> nodes) {
			super(samplesLevel.getName(), samplesLevel.getShortName());
			this.nodes = nodes;
		}

		@Override
		public Class<? extends BinnedSamplesNode> getType() {
			return BinnedSamplesNode.class;
		}

		@Override
		public List<? extends BinnedSamplesNode> getNodes() {
			return nodes;
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			return nodes.contains(node);
		}

		@Override
		public BinnedSamplesNode getBin(PureGR value) {
			for (BinnedSamplesNode node : nodes)
				if (node.isMember(value))
					return node;
			return null;
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			
			JsonArray binsArray = new JsonArray();
			
			DoubleRangeAdapter rangeAdapter = new DoubleRangeAdapter();
			
			for (BinnedSamplesNode node : nodes) {
				JsonObject binObj = new JsonObject();

				binObj.add("range", rangeAdapter.toJsonTree(node.rateRange));
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
				Range<Double> range = rangeAdapter.fromJsonTree(jsonObj.get("range"));
				String name = jsonObj.get("name").getAsString();
				String shortName = jsonObj.get("shortName").getAsString();
				String filePrefix = jsonObj.get("filePrefix").getAsString();
				double weight = jsonObj.get("weight").getAsDouble();
				nodes.add(new BinnedSamplesNode(name, shortName, filePrefix, weight, range));
			}
		}
		
	}
	
	public static class BinnedSamplesNode implements LogicTreeNode.FixedWeightNode {
		
		private String name;
		private String shortName;
		private String filePrefix;
		private double weight;
		private Range<Double> rateRange;

		public BinnedSamplesNode(String name, String shortName, String filePrefix, double weight,
				Range<Double> rateRange) {
			this.name = name;
			this.shortName = shortName;
			this.filePrefix = filePrefix;
			this.weight = weight;
			this.rateRange = rateRange;
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
		
		public boolean isMember(PureGR gr) {
			return rateRange.contains(gr.rateAboveM1);
		}
		
	}

}
