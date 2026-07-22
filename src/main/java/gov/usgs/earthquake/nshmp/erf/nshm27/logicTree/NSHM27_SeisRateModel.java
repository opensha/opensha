package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import org.opensha.commons.data.WeightedList;
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
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.PureGR;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.RateRecord;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.RateType;

public interface NSHM27_SeisRateModel extends LogicTreeNode {
	
	public abstract IncrementalMagFreqDist build(NSHM27_SeismicityRegions region,
			NSHM27_SeisClassificationMethod classification, TectonicRegionType trt,
			EvenlyDiscretizedFunc refMFD, double mMax);
	
	public abstract RateRecord getRateRecord(NSHM27_SeismicityRegions region,
			NSHM27_SeisClassificationMethod classification, TectonicRegionType trt);
	
	static PureGR getAverageGR(WeightedList<PureGR> grs) {
		if (!grs.isNormalized()) {
			grs = new WeightedList<>(grs);
			grs.normalize();
		}
		double avgRate = 0d;
		double avgB = 0d;
		System.out.println("Averaging GRs");
		for (int i=0; i<grs.size(); i++) {
			double weight = grs.getWeight(i);
			PureGR gr = grs.getValue(i);
			System.out.println("\t"+i+": "+gr+"\t(wt="+(float)weight+")");
			avgRate += weight*gr.rateAboveM1;
			avgB += weight*gr.b;
		}
		PureGR gr0 = grs.getValue(0);
		PureGR ret = new PureGR(gr0.type, gr0.M1, gr0.Mmax, avgRate, avgB, gr0.quantile, gr0.mean);
		System.out.println("AVERAGE:\t"+ret);
		return ret;
	}
	
	@JsonAdapter(ClassificationDependentGRAdapter.class)
	public static class ClassificationDependentGR {
		private EnumMap<NSHM27_SeisClassificationMethod, PureGR> grs;
		private double sampleFractile;
		
		public ClassificationDependentGR(EnumMap<NSHM27_SeisClassificationMethod, PureGR> grs, double sampleFractile) {
			Preconditions.checkState(!grs.isEmpty());
			Preconditions.checkState(sampleFractile >= 0d && sampleFractile < 1d);
			this.grs = grs;
			this.sampleFractile = sampleFractile;
		}
		
		public PureGR getValue(NSHM27_SeisClassificationMethod classification) {
			if (classification == NSHM27_SeisClassificationMethod.AVERAGE) {
				WeightedList<PureGR> grs = new WeightedList<>();
				for (NSHM27_SeisClassificationMethod oClass : NSHM27_SeisClassificationMethod.values()) {
					if (oClass != NSHM27_SeisClassificationMethod.AVERAGE && oClass.getNodeWeight() > 0d)
						grs.add(getValue(oClass), oClass.getNodeWeight());
				}
				return getAverageGR(grs);
			}
			PureGR value = grs.get(classification);
			Preconditions.checkNotNull(value);
			return value;
		}
		
		public double getSampleFractile() {
			return sampleFractile;
		}
	}
	
	public static ClassificationDependentGRAdapter CLASS_GR_ADAPTER = new ClassificationDependentGRAdapter();
	
	public static class ClassificationDependentGRAdapter extends TypeAdapter<ClassificationDependentGR> {

		@Override
		public void write(JsonWriter out, ClassificationDependentGR value) throws IOException {
			out.beginObject();
			
			out.name("rateModels").beginArray();
			for (NSHM27_SeisClassificationMethod classification : value.grs.keySet()) {
				out.beginArray();
				out.value(classification.name());
				PureGR gr = value.getValue(classification);
				out.value(gr.M1);
				out.value(gr.rateAboveM1);
				out.value(gr.b);
				out.endArray();
			}
			out.endArray();
			out.name("fractile").value(value.getSampleFractile());
			
			out.endObject();
		}

		@Override
		public ClassificationDependentGR read(JsonReader in) throws IOException {
			EnumMap<NSHM27_SeisClassificationMethod, PureGR> grs = new EnumMap<>(NSHM27_SeisClassificationMethod.class);
			Double fractile = null;
			in.beginObject();
			
			while (in.hasNext()) {
				String name = in.nextName();
				switch (name) {
				case "rateModels":
					in.beginArray();
					while (in.hasNext()) {
						in.beginArray();
						NSHM27_SeisClassificationMethod classification = NSHM27_SeisClassificationMethod.valueOf(in.nextString());
						double m1 = in.nextDouble();
						double rateAboveM1 = in.nextDouble();
						double b = in.nextDouble();
						PureGR gr = new PureGR(RateType.M1, m1, Double.POSITIVE_INFINITY, rateAboveM1, b, Double.NaN, true);
						grs.put(classification, gr);
						in.endArray();
					}
					in.endArray();
					break;
				case "fractile":
					fractile = in.nextDouble();
					break;

				default:
					System.err.println("Skipping unexpected Json token with name: "+name);
					in.skipValue();
					break;
				}
			}
			
			in.endObject();
			return new ClassificationDependentGR(grs, fractile);
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
	public static class NSHM27_SiesRateModelSample extends SimpleValuedNode<ClassificationDependentGR> implements NSHM27_SeisRateModel {
		
		private NSHM27_SeismicityRegions region;
		private TectonicRegionType trt;

		@SuppressWarnings("unused") // deserialization
		private NSHM27_SiesRateModelSample() {}

		public NSHM27_SiesRateModelSample(ClassificationDependentGR value, NSHM27_SeismicityRegions region,
				TectonicRegionType trt, double weight, String name,
				String shortName, String filePrefix) {
			super(value, ClassificationDependentGR.class, weight, name, shortName, filePrefix);
			this.region = region;
			this.trt = trt;
		}

		@Override
		public IncrementalMagFreqDist build(NSHM27_SeismicityRegions region,
				NSHM27_SeisClassificationMethod classification, TectonicRegionType trt,
				EvenlyDiscretizedFunc refMFD, double mMax) {
			ClassificationDependentGR value = getValue();
			Preconditions.checkState(this.region == null || region == this.region,
					"Region mismatch: %s != %s", region, this.region);
			Preconditions.checkState(this.trt == null || trt == this.trt,
					"TRT mismatch: %s != %s", trt, this.trt);
			PureGR gr = value.getValue(classification);
			return SeismicityRateFileLoader.buildIncrementalMFD(gr, refMFD, mMax, Double.NaN);
		}

		@Override
		public RateRecord getRateRecord(NSHM27_SeismicityRegions region,
				NSHM27_SeisClassificationMethod classification, TectonicRegionType trt) {
			Preconditions.checkState(this.region == null || region == this.region,
					"Region mismatch: %s != %s", region, this.region);
			Preconditions.checkState(this.trt == null || trt == this.trt,
					"TRT mismatch: %s != %s", trt, this.trt);
			return getValue().getValue(classification);
		}
		
	}
	
	public static class BinnedSamplesLevel extends DataBackedLevel<BinnedSamplesNode>
	implements BinnedLevel<ClassificationDependentGR, BinnedSamplesNode>{
		
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
		public BinnedSamplesNode getBin(ClassificationDependentGR value) {
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
		
		public boolean isMember(ClassificationDependentGR gr) {
			return fractileRange.contains(gr.sampleFractile);
		}
		
	}

}
