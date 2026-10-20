package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.json.DoubleRangeAdapter;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.DataBackedLevel;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.PureGR;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.RateRecord;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public interface NSHM27_SeisRateModel extends LogicTreeNode {
	
	public abstract IncrementalMagFreqDist build(NSHM27_SeismicityRegions region,
			TectonicRegionType trt, LogicTreeBranch<?> branch,
			EvenlyDiscretizedFunc refMFD, double mMax);
	
	public abstract RateRecord getRateRecord(NSHM27_SeismicityRegions region,
			TectonicRegionType trt, LogicTreeBranch<?> branch);
	
	// this affects interface slip rates
	@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
	// this affects interface slip rates
	@Affects(FaultSystemSolution.RATES_FILE_NAME)
	@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
	@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
	@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
	public static class NSHM27_SeisRateModelSample extends SimpleValuedNode<Double> implements NSHM27_SeisRateModel {
		
		private NSHM27_SeismicityRegions region;
		private TectonicRegionType trt;
		private String datasetId;

		@SuppressWarnings("unused") // deserialization
		private NSHM27_SeisRateModelSample() {}

		public NSHM27_SeisRateModelSample(Double value, NSHM27_SeisRateModelEpochCatalog catalog,
				double weight, String name,
				String shortName, String filePrefix) {
			super(value, Double.class, weight, name, shortName, filePrefix);
			this.region = catalog.getRegion();
			this.trt = catalog.getTectonicRegion();
			this.datasetId = catalog.getDatasetId();
		}

		@Override
		public IncrementalMagFreqDist build(NSHM27_SeismicityRegions region,
				TectonicRegionType trt, LogicTreeBranch<?> branch,
				EvenlyDiscretizedFunc refMFD, double mMax) {
			return SeismicityRateFileLoader.buildIncrementalMFD(
					(PureGR)getRateRecord(region, trt, branch), refMFD, mMax, Double.NaN);
		}

		@Override
		public RateRecord getRateRecord(NSHM27_SeismicityRegions region,
				TectonicRegionType trt, LogicTreeBranch<?> branch) {
			Preconditions.checkState(this.region == null || region == this.region,
					"Region mismatch: %s != %s", region, this.region);
			Preconditions.checkState(this.trt == null || trt == this.trt,
					"TRT mismatch: %s != %s", trt, this.trt);
			NSHM27_SeisRateModelEpoch epoch = branch.requireValue(NSHM27_SeisRateModelEpoch.class);
			validateEpoch(epoch);
			NSHM27_SeisBValueMethod bMethod = branch.requireValue(NSHM27_SeisBValueMethod.class);
			return NSHM27_SeisRateModelSamples.loadSample(region, epoch, trt, bMethod, getValue());
		}

		private void validateEpoch(NSHM27_SeisRateModelEpoch epoch) {
			Preconditions.checkNotNull(epoch, "Epoch cannot be null");
			Preconditions.checkState(region == null || epoch.getRegion() == region, "Epoch region mismatch");
			Preconditions.checkState(trt == null || epoch.getTectonicRegion() == trt, "Epoch TRT mismatch");
			Preconditions.checkState(datasetId == null || epoch.getDatasetId().equals(datasetId), "Epoch dataset mismatch");
		}
		
	}
	
	public static class BinnedSamplesLevel extends DataBackedLevel<BinnedSamplesNode>
	implements BinnedLevel<Double, BinnedSamplesNode>{
		
		private List<BinnedSamplesNode> nodes;
		
		@SuppressWarnings("unused") // deserialization
		private BinnedSamplesLevel() {}

		BinnedSamplesLevel(NSHM27_SeisRateModelSamples samplesLevel, List<BinnedSamplesNode> nodes) {
			super(samplesLevel.getName(), samplesLevel.getShortName());
			this.nodes = nodes;
			setAffected(samplesLevel.getAffected(), samplesLevel.getNotAffected(), false);
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
		public BinnedSamplesNode getBin(Double value) {
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

				binObj.add("range", rangeAdapter.toJsonTree(node.fractileRange));
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
				nodes.add(new BinnedSamplesNode(name, shortName, filePrefix, weight, range));
			}
		}
		
	}
	
	public static class BinnedSamplesNode implements LogicTreeNode.FixedWeightNode {
		
		private String name;
		private String shortName;
		private String filePrefix;
		private double weight;
		private Range<Double> fractileRange;

		public BinnedSamplesNode(String name, String shortName, String filePrefix, double weight,
				Range<Double> fractileRange) {
			this.name = name;
			this.shortName = shortName;
			this.filePrefix = filePrefix;
			this.weight = weight;
			this.fractileRange = fractileRange;
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
		
		public boolean isMember(Double fractile) {
			return fractileRange.contains(fractile);
		}
		
	}

}
