package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.logicTree.LogicTreeLevel.AbstractRandomlySampledLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnableLevel;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;

import gov.usgs.earthquake.nshmp.erf.nshm27.NSHM27_InvConfigFactory;
import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM27_SeisRateModel.BinnedSamplesLevel;
import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM27_SeisRateModel.BinnedSamplesNode;
import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM27_SeisRateModel.ClassificationDependentGR;
import gov.usgs.earthquake.nshmp.erf.nshm27.logicTree.NSHM27_SeisRateModel.NSHM27_SiesRateModelSample;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.PureGR;

public class NSHM27_SeisRateModelSamples extends AbstractRandomlySampledLevel<ClassificationDependentGR, NSHM27_SiesRateModelSample>
implements BinnableLevel<ClassificationDependentGR, NSHM27_SiesRateModelSample, BinnedSamplesLevel> {
	
	private NSHM27_SeismicityRegions region;
	private TectonicRegionType trt;
	
	@SuppressWarnings("unused") // deserialization
	private NSHM27_SeisRateModelSamples(String name, String shortName) {
		super(name, shortName);
	}
	
	public NSHM27_SeisRateModelSamples(NSHM27_SeismicityRegions region, TectonicRegionType trt) {
		super(NSHM27_RegionLoader.getNameForTRT(trt)+" Seismicity Rate Model Samples",
				NSHM27_RegionLoader.getNameForTRT(trt)+"RateSamples",
				"Rate sample ", "RateSample", "RateSample");
		this.region = region;
		this.trt = trt;
	}
	
	@Override
	public Class<? extends ClassificationDependentGR> getValueType() {
		return ClassificationDependentGR.class;
	}
	
	protected CSVFile<String> loadCSV(NSHM27_SeisClassificationMethod classification) throws IOException {
		File data = new File(NSHM27_InvConfigFactory.locateDataDirectory(), "seis_rate_samples");
		Preconditions.checkState(data.exists(), "Data directory doesn't exist: %s", data.getAbsolutePath());
		Preconditions.checkNotNull(region, "Region not set; can only be built upon initial construction");
		Preconditions.checkNotNull(trt, "TRT not set; can only be built upon initial construction");
		data = new File(data, region.name().toLowerCase());
		Preconditions.checkState(data.exists(), "Region directory doesn't exist: %s", data.getAbsolutePath());
		data = new File(data, NSHM27_SeisRateModelBranch.getRateModelDate(region, classification));
		Preconditions.checkState(data.exists(), "Date directory doesn't exist: %s", data.getAbsolutePath());
		File csvFile = new File(data, NSHM27_SeisRateModelBranch.getRateModelCSVName(trt));
		Preconditions.checkState(csvFile.exists(), "CSV doesn't exist: %s", data.getAbsolutePath());
		return CSVFile.readFile(csvFile, false);
	}
	
	public List<PureGR> loadOrigSamples(NSHM27_SeisClassificationMethod classification) {
		CSVFile<String> csv;
		try {
			csv = loadCSV(classification);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		return SeismicityRateFileLoader.loadSamplesCSV(csv);
	}
	
	private static Comparator<PureGR> COMP = (o1,o2) -> {
		int cmp = Double.compare(o1.rateAboveM1, o2.rateAboveM1);
		if (cmp == 0)
			// treat lower b-value as "higher" to break rate ties
			cmp = Double.compare(o2.b, o1.b);
		return cmp;
	};

	@Override
	protected void doBuild(long seed, int numNodes, SamplingMethod samplingMethod, double weightEach) {
//		List<PureGR> origSamples = loadOrigSamples();
		EnumMap<NSHM27_SeisClassificationMethod, List<PureGR>> origSamples = new EnumMap<>(NSHM27_SeisClassificationMethod.class);
		int numOrigSamples = -1;
		for (NSHM27_SeisClassificationMethod classification : NSHM27_SeisClassificationMethod.values()) {
			if (classification.getNodeWeight() == 0d)
				continue;
			List<PureGR> samples = loadOrigSamples(classification);
			if (numOrigSamples == -1)
				numOrigSamples = samples.size();
			else
				Preconditions.checkState(numOrigSamples == samples.size());
			// sort by rate
			samples.sort(COMP);
			origSamples.put(classification, samples);
		}
		Random rand = new Random(seed);
		ArrayDeque<ClassificationDependentGR> samples = new ArrayDeque<>(numNodes);
		if (samplingMethod.isLHS()) {
			List<ClassificationDependentGR> sampled = new ArrayList<>(numNodes);
			for (int i=0; i<numNodes; i++) {
				double binStart = (double)i / numNodes;
				double binEnd = (double)(i + 1) / numNodes;
				double p = rand.nextDouble(binStart, binEnd);

				int index = (int)(p * numOrigSamples);
				if (index == numOrigSamples)
					index = numOrigSamples - 1;
				
				EnumMap<NSHM27_SeisClassificationMethod, PureGR> grs = new EnumMap<>(NSHM27_SeisClassificationMethod.class);
				for (NSHM27_SeisClassificationMethod classification : origSamples.keySet())
					grs.put(classification, origSamples.get(classification).get(index));

				sampled.add(new ClassificationDependentGR(grs, p));
			}
			Collections.shuffle(sampled, rand);
			samples.addAll(sampled);
		} else {
			// monte carlo
			List<Integer> origIndexes = new ArrayList<>(numOrigSamples);
			for (int i=0; i<numOrigSamples; i++)
				origIndexes.add(i);
			for (int i=0; i<numNodes; i++) {
				int sampleIndex = i % numOrigSamples;
				if (sampleIndex == 0)
					// shuffle the list (and reshuffle on any subsequent passes through it)
					Collections.shuffle(origIndexes, rand);
				int index = origIndexes.get(sampleIndex);
				EnumMap<NSHM27_SeisClassificationMethod, PureGR> grs = new EnumMap<>(NSHM27_SeisClassificationMethod.class);
				for (NSHM27_SeisClassificationMethod classification : origSamples.keySet())
					grs.put(classification, origSamples.get(classification).get(index));
				double p = (double)index/(double)numOrigSamples;
				samples.addLast(new ClassificationDependentGR(grs, p));
			}
		}
		build(()->{ return samples.pop(); }, numNodes, weightEach);
	}

	@Override
	public NSHM27_SiesRateModelSample build(ClassificationDependentGR value, double weight, String name, String shortName,
			String filePrefix) {
		return new NSHM27_SiesRateModelSample(value, region, trt, weight, name, shortName, filePrefix);
	}

	@Override
	public Class<? extends NSHM27_SiesRateModelSample> getType() {
		return NSHM27_SiesRateModelSample.class;
	}

	@Override
	public JsonObject toJsonObject() {
		JsonObject json = super.toJsonObject();

		json.add("region", new JsonPrimitive(region.name()));
		json.add("tectonicRegime", new JsonPrimitive(trt.name()));
		
		return json;
	}
	
	@Override
	public TypeAdapter<ClassificationDependentGR> getValueTypeAdapter() {
		return NSHM27_SeisRateModel.CLASS_GR_ADAPTER;
	}

	@Override
	public void initFromJsonObject(JsonObject jsonObj) {
		region = NSHM27_SeismicityRegions.valueOf(jsonObj.get("region").getAsString());
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
		
		DecimalFormat pDF = new DecimalFormat("0%");
		
		double probEach = 1d/(double)numBins;
		binEdges.add(0d);
		List<BinnedSamplesNode> binNodes = new ArrayList<>();
		for (int i=0; i<numBins; i++) {
			double startP = binEdges.get(i);
			double endP;
			if (i == numBins-1) {
				// last
				endP = 1d;
			} else {
				// intermediate
				endP = startP + probEach;
			}
			
			binEdges.add(endP);
			
			String binStr = pDF.format(startP)+"-"+pDF.format(endP);
			
			String name, shortName;
			Range<Double> range;
			if (numBins == 1 || numBins > 3) {
				name = binStr;
				shortName = binStr;
				range = Range.closed(0d, 1d);
			} else if (i == 0) {
				shortName = "Low";
				name = shortName+": "+binStr;
				range = Range.closedOpen(startP, endP);
			} else if (i == numBins-1) {
				shortName = "High";
				name = shortName+": "+binStr;
				range = Range.closed(startP, endP);
			} else {
				shortName = "Middle";
				name = shortName+": "+binStr;
				range = Range.closedOpen(startP, endP);
			}
			names.add(name);
			shortNames.add(binStr);
			
			startP = endP;
			
			binNodes.add(new BinnedSamplesNode(name, shortName, "Bin"+i, probEach, range));
		}
		return new BinnedSamplesLevel(this, binNodes);
	}

}
