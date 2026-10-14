package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
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
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.RateType;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public interface NSHM27_SeisRateModel extends LogicTreeNode {
	
	public abstract IncrementalMagFreqDist build(NSHM27_SeismicityRegions region,
			NSHM27_SeisRateModelEpoch epoch, TectonicRegionType trt,
			EvenlyDiscretizedFunc refMFD, double mMax);
	
	public abstract RateRecord getRateRecord(NSHM27_SeismicityRegions region,
			NSHM27_SeisRateModelEpoch epoch, TectonicRegionType trt);
	
	public static class EpochDependentGR {
		private final List<PureGR> grs;
		private final double sampleFractile;
		
		public EpochDependentGR(List<PureGR> grs, double sampleFractile) {
			Preconditions.checkState(!grs.isEmpty());
			Preconditions.checkState(sampleFractile >= 0d && sampleFractile < 1d);
			for (PureGR gr : grs)
				Preconditions.checkNotNull(gr, "Epoch GR values cannot contain null");
			this.grs = Collections.unmodifiableList(new ArrayList<>(grs));
			this.sampleFractile = sampleFractile;
		}
		
		public PureGR getValue(NSHM27_SeisRateModelEpoch epoch) {
			Preconditions.checkNotNull(epoch, "Epoch cannot be null");
			Preconditions.checkState(epoch.getEpochIndex() < grs.size(),
					"Epoch index %s outside sampled epoch count %s", epoch.getEpochIndex(), grs.size());
			return grs.get(epoch.getEpochIndex());
		}

		public int getEpochCount() { return grs.size(); }
		
		public double getSampleFractile() {
			return sampleFractile;
		}
	}
	
	public static class EpochDependentGRAdapter extends TypeAdapter<EpochDependentGR> {
		private final int epochCount;

		public EpochDependentGRAdapter(int epochCount) {
			Preconditions.checkArgument(epochCount > 0);
			this.epochCount = epochCount;
		}

		@Override
		public void write(JsonWriter out, EpochDependentGR value) throws IOException {
			Preconditions.checkState(value.grs.size() == epochCount,
					"Expected %s epoch values, found %s", epochCount, value.grs.size());
			out.beginArray();
			out.value(value.getSampleFractile());
			out.beginArray();
			for (PureGR gr : value.grs) {
				out.beginArray();
				out.value(gr.M1);
				out.value(gr.rateAboveM1);
				out.value(gr.b);
				out.endArray();
			}
			out.endArray();
			out.endArray();
		}

		@Override
		public EpochDependentGR read(JsonReader in) throws IOException {
			in.beginArray();
			double fractile = in.nextDouble();
			List<PureGR> grs = new ArrayList<>(epochCount);
			in.beginArray();
			while (in.hasNext()) {
				in.beginArray();
				double m1 = in.nextDouble();
				double rateAboveM1 = in.nextDouble();
				double b = in.nextDouble();
				grs.add(new PureGR(RateType.M1, m1, Double.POSITIVE_INFINITY, rateAboveM1, b, Double.NaN, true));
				in.endArray();
			}
			in.endArray();
			in.endArray();
			if (grs.size() != epochCount)
				throw new IOException("Expected "+epochCount+" epoch GR values, found "+grs.size());
			return new EpochDependentGR(grs, fractile);
		}
		
	}
	
	// this affects interface slip rates
	@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
	@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
	// this affects interface slip rates
	@Affects(FaultSystemSolution.RATES_FILE_NAME)
	@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
	@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
	@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
	public static class NSHM27_SeisRateModelSample extends SimpleValuedNode<EpochDependentGR> implements NSHM27_SeisRateModel {
		
		private NSHM27_SeismicityRegions region;
		private TectonicRegionType trt;
		private String datasetId;

		@SuppressWarnings("unused") // deserialization
		private NSHM27_SeisRateModelSample() {}

		public NSHM27_SeisRateModelSample(EpochDependentGR value, NSHM27_SeisRateModelEpochCatalog catalog,
				double weight, String name,
				String shortName, String filePrefix) {
			super(value, EpochDependentGR.class, weight, name, shortName, filePrefix);
			this.region = catalog.getRegion();
			this.trt = catalog.getTectonicRegion();
			this.datasetId = catalog.getDatasetId();
		}

		@Override
		public IncrementalMagFreqDist build(NSHM27_SeismicityRegions region,
				NSHM27_SeisRateModelEpoch epoch, TectonicRegionType trt,
				EvenlyDiscretizedFunc refMFD, double mMax) {
			EpochDependentGR value = getValue();
			Preconditions.checkState(this.region == null || region == this.region,
					"Region mismatch: %s != %s", region, this.region);
			Preconditions.checkState(this.trt == null || trt == this.trt,
					"TRT mismatch: %s != %s", trt, this.trt);
			validateEpoch(epoch);
			PureGR gr = value.getValue(epoch);
			return SeismicityRateFileLoader.buildIncrementalMFD(gr, refMFD, mMax, Double.NaN);
		}

		@Override
		public RateRecord getRateRecord(NSHM27_SeismicityRegions region,
				NSHM27_SeisRateModelEpoch epoch, TectonicRegionType trt) {
			Preconditions.checkState(this.region == null || region == this.region,
					"Region mismatch: %s != %s", region, this.region);
			Preconditions.checkState(this.trt == null || trt == this.trt,
					"TRT mismatch: %s != %s", trt, this.trt);
			validateEpoch(epoch);
			return getValue().getValue(epoch);
		}

		private void validateEpoch(NSHM27_SeisRateModelEpoch epoch) {
			Preconditions.checkNotNull(epoch, "Epoch cannot be null");
			Preconditions.checkState(region == null || epoch.getRegion() == region, "Epoch region mismatch");
			Preconditions.checkState(trt == null || epoch.getTectonicRegion() == trt, "Epoch TRT mismatch");
			Preconditions.checkState(datasetId == null || epoch.getDatasetId().equals(datasetId), "Epoch dataset mismatch");
		}
		
	}
	
	public static class BinnedSamplesLevel extends DataBackedLevel<BinnedSamplesNode>
	implements BinnedLevel<EpochDependentGR, BinnedSamplesNode>{
		
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
		public BinnedSamplesNode getBin(EpochDependentGR value) {
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
		
		public boolean isMember(EpochDependentGR gr) {
			return fractileRange.contains(gr.sampleFractile);
		}
		
	}

}
