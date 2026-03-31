package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.AbstractRandomlySampledLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnableLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import gov.usgs.earthquake.nshmp.erf.nshm27.NSHM26_InvConfigFactory;
import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM26_SeisRateModel.BinnedSamplesLevel;
import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM26_SeisRateModel.BinnedSamplesNode;
import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM26_SeisRateModel.NSHM26_SiesRateModelSample;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM26_RegionLoader;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.PureGR;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.RateType;

public class NSHM26_SeisRateModelSamples extends AbstractRandomlySampledLevel<PureGR, NSHM26_SiesRateModelSample>
implements BinnableLevel<PureGR, NSHM26_SiesRateModelSample, BinnedSamplesLevel> {
	
	private NSHM26_SeismicityRegions region;
	private TectonicRegionType trt;
	
	@SuppressWarnings("unused") // deserialization
	private NSHM26_SeisRateModelSamples(String name, String shortName) {
		super(name, shortName);
	}
	
	public NSHM26_SeisRateModelSamples(NSHM26_SeismicityRegions region, TectonicRegionType trt) {
		super(NSHM26_RegionLoader.getNameForTRT(trt)+" Seismicity Rate Model Samples",
				NSHM26_RegionLoader.getNameForTRT(trt)+"RateSamples",
				"Rate sample ", "RateSample", "RateSample");
		this.region = region;
		this.trt = trt;
	}
	
	@Override
	public Class<? extends PureGR> getValueType() {
		return PureGR.class;
	}
	
	protected CSVFile<String> loadCSV() throws IOException {
		File data = new File(NSHM26_InvConfigFactory.locateDataDirectory(), "seis_rate_samples");
		Preconditions.checkState(data.exists(), "Data directory doesn't exist: %s", data.getAbsolutePath());
		Preconditions.checkNotNull(region, "Region not set; can only be built upon initial construction");
		Preconditions.checkNotNull(trt, "TRT not set; can only be built upon initial construction");
		data = new File(data, region.name().toLowerCase());
		Preconditions.checkState(data.exists(), "Region directory doesn't exist: %s", data.getAbsolutePath());
		data = new File(data, NSHM26_SeisRateModelBranch.getRateModelDate(region));
		Preconditions.checkState(data.exists(), "Date directory doesn't exist: %s", data.getAbsolutePath());
		File csvFile = new File(data, NSHM26_SeisRateModelBranch.getRateModelCSVName(trt));
		Preconditions.checkState(csvFile.exists(), "CSV doesn't exist: %s", data.getAbsolutePath());
		return CSVFile.readFile(csvFile, false);
	}

	@Override
	protected void doBuild(long seed, int numNodes, double weightEach) {
		CSVFile<String> csv;
		try {
			csv = loadCSV();
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		List<PureGR> samplesList = SeismicityRateFileLoader.loadSamplesCSV(csv);
		Random rand = new Random(seed);
		ArrayDeque<PureGR> samples = new ArrayDeque<>(numNodes);
		for (int i=0; i<numNodes; i++) {
			int sampleIndex = i % samplesList.size();
			if (sampleIndex == 0)
				// shuffle the list (and reshuffle on any subsequent passes through it)
				Collections.shuffle(samplesList, rand);
			samples.addLast(samplesList.get(sampleIndex));
		}
		build(()->{ return samples.pop(); }, numNodes, weightEach);
	}

	@Override
	public NSHM26_SiesRateModelSample build(PureGR value, double weight, String name, String shortName,
			String filePrefix) {
		return new NSHM26_SiesRateModelSample(value, region, trt, weight, name, shortName, filePrefix);
	}

	@Override
	public Class<? extends NSHM26_SiesRateModelSample> getType() {
		return NSHM26_SiesRateModelSample.class;
	}

	@Override
	public JsonObject toJsonObject() {
		JsonObject json = super.toJsonObject();

		json.add("region", new JsonPrimitive(region.name()));
		json.add("tectonicRegime", new JsonPrimitive(trt.name()));
		
		return json;
	}
	
	@Override
	public TypeAdapter<PureGR> getValueTypeAdapter() {
		return GR_TYPE_ADAPTER;
	}
	
	private static TypeAdapter<PureGR> GR_TYPE_ADAPTER = new TypeAdapter<PureGR>() {

		@Override
		public void write(JsonWriter out, PureGR value) throws IOException {
			out.beginArray();
			out.value(value.M1);
			out.value(value.rateAboveM1);
			out.value(value.b);
			out.endArray();
		}

		@Override
		public PureGR read(JsonReader in) throws IOException {
			in.beginArray();
			double[] vals = new double[3];
			for (int i=0; i<3; i++)
				vals[i] = in.nextDouble();
			Preconditions.checkState(!in.hasNext());
			in.endArray();
			return new PureGR(RateType.M1, vals[0], Double.POSITIVE_INFINITY, vals[1], vals[2], Double.NaN, true);
		}
		
	};

	@Override
	public void initFromJsonObject(JsonObject jsonObj) {
		region = NSHM26_SeismicityRegions.valueOf(jsonObj.get("region").getAsString());
		trt = TectonicRegionType.valueOf(jsonObj.get("tectonicRegime").getAsString());
		
		super.initFromJsonObject(jsonObj);
	}

	@Override
	public BinnedSamplesLevel toBinnedLevel() {
		return toBinnedLevel(3);
	}

	@Override
	public BinnedSamplesLevel toBinnedLevel(int numBins) {
		Preconditions.checkState(numBins > 0);
		List<Double> binEdges = new ArrayList<>(numBins+1);
		List<String> names = new ArrayList<>(numBins);
		List<String> shortNames = new ArrayList<>(numBins);
		
		double[] allRates = new double[nodes.size()];
		double minRate = Double.POSITIVE_INFINITY;
		double maxRate = 0d;
		for (int i=0; i<allRates.length; i++) {
			allRates[i] = nodes.get(i).getValue().rateAboveM1;
			minRate = Math.min(minRate, allRates[i]);
			maxRate = Math.max(maxRate, allRates[i]);
		}
		
		LightFixedXFunc cdf = ArbDiscrEmpiricalDistFunc.calcQuickNormCDF(allRates, null);
		
		DecimalFormat rateDF = new DecimalFormat("0.0#");
		
		DecimalFormat mDF = new DecimalFormat("0.#");
		String nmLabel = "N"+mDF.format(nodes.get(0).getValue().M1);
		
		binEdges.add(minRate);
		double probEach = 1d/(double)numBins;
		double startP = 0d;
		List<BinnedSamplesNode> binNodes = new ArrayList<>();
		for (int i=0; i<numBins; i++) {
			double lower = binEdges.get(i);
			double upper;
			double endP;
			if (i == numBins-1) {
				// last
				endP = 1d;
				upper = maxRate;
			} else {
				// intermediate
				endP = startP + probEach;
				upper = ArbDiscrEmpiricalDistFunc.calcFractileFromNormCDF(cdf, endP);
			}
			
			binEdges.add(upper);
			
			String binStr;
			if (Double.isInfinite(lower) && Double.isInfinite(upper))
				binStr = nmLabel+" ∈ ["+Double.POSITIVE_INFINITY+"]";
			else if (Double.isInfinite(lower))
				binStr = nmLabel+" < "+rateDF.format(upper);
			else if (Double.isInfinite(upper))
				binStr = nmLabel+" > "+rateDF.format(lower);
			else
				binStr = nmLabel+" ∈ ["+rateDF.format(lower)+", "+rateDF.format(upper)+"]";
			
			String name, shortName;
			Range<Double> range;
			if (numBins == 1 || numBins > 3) {
				name = binStr;
				shortName = binStr;
				range = Range.all();
			} else if (i == 0) {
				shortName = "Low";
				name = shortName+": "+binStr;
				range = Range.atMost(upper);
			} else if (i == numBins-1) {
				shortName = "High";
				name = shortName+": "+binStr;
				range = Range.atLeast(lower);
			} else {
				shortName = "Middle";
				name = shortName+": "+binStr;
				range = Range.closed(lower, upper);
			}
			names.add(name);
			shortNames.add(binStr);
			
			startP = endP;
			
			binNodes.add(new BinnedSamplesNode(name, shortName, "Bin"+i, probEach, range));
		}
		return new BinnedSamplesLevel(this, binNodes);
	}

}
