package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.logicTree.LogicTreeLevel.AbstractRandomlySampledLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnableLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.FractileSamplingLevel;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.PureGR;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.NSHM27_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModel.BinnedSamplesLevel;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModel.BinnedSamplesNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModel.ClassificationDependentGR;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModel.NSHM27_SiesRateModelSample;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;

public class NSHM27_SeisRateModelSamples extends AbstractRandomlySampledLevel<ClassificationDependentGR, NSHM27_SiesRateModelSample>
implements BinnableLevel<ClassificationDependentGR, NSHM27_SiesRateModelSample, BinnedSamplesLevel>,
FractileSamplingLevel<ClassificationDependentGR, NSHM27_SiesRateModelSample> {
	
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
		Preconditions.checkNotNull(region, "Region not set; can only be built upon initial construction");
		Preconditions.checkNotNull(trt, "TRT not set; can only be built upon initial construction");
		return loadCSV(region, classification, trt);
	}
	
	private static CSVFile<String> loadCSV(NSHM27_SeismicityRegions seisReg,
			NSHM27_SeisClassificationMethod classification, TectonicRegionType trt) throws IOException {
		Preconditions.checkNotNull(seisReg, "Region not set; can only be built upon initial construction");
		Preconditions.checkNotNull(classification, "Classification method cannot be null");
		// trt can be null
		File data = new File(NSHM27_InvConfigFactory.locateDataDirectory(), "seis_rate_samples");
		Preconditions.checkState(data.exists(), "Data directory doesn't exist: %s", data.getAbsolutePath());
		Preconditions.checkNotNull(seisReg, "Region not set; can only be built upon initial construction");
		data = new File(data, seisReg.name().toLowerCase());
		Preconditions.checkState(data.exists(), "Region directory doesn't exist: %s", data.getAbsolutePath());
		data = new File(data, NSHM27_SeisRateModelBranch.getRateModelDate(seisReg, classification));
		Preconditions.checkState(data.exists(), "Date directory doesn't exist: %s", data.getAbsolutePath());
		File csvFile = new File(data, NSHM27_SeisRateModelBranch.getRateModelCSVName(trt));
		Preconditions.checkState(csvFile.exists(), "CSV doesn't exist: %s", data.getAbsolutePath());
		return CSVFile.readFile(csvFile, false);
	}
	
	public static List<PureGR> loadOrigSamples(NSHM27_SeismicityRegions seisReg,
			NSHM27_SeisClassificationMethod classification, TectonicRegionType trt) throws IOException {
		CSVFile<String> csv = loadCSV(seisReg, classification, trt);
		return SeismicityRateFileLoader.loadSamplesCSV(csv);
	}
	
	public List<PureGR> loadOrigSamples(NSHM27_SeisClassificationMethod classification) {
		if (classification == NSHM27_SeisClassificationMethod.AVERAGE) {
			WeightedList<List<PureGR>> samplesLists = new WeightedList<>();
			for (NSHM27_SeisClassificationMethod oClass : NSHM27_SeisClassificationMethod.values()) {
				double weight = oClass.getNodeWeight();
				if (weight != 0d) {
					Preconditions.checkState(oClass != NSHM27_SeisClassificationMethod.AVERAGE);
					samplesLists.add(loadOrigSamples(oClass), weight);
				}
			}
			samplesLists.normalize();
			int numSamples = samplesLists.getValue(0).size();
			double maxWeight = 0d;
			for (int i=0; i<samplesLists.size(); i++) {
				maxWeight = Math.max(samplesLists.getWeight(i), maxWeight);
				Preconditions.checkState(numSamples == samplesLists.getValue(i).size());
			}
			List<PureGR> totalSamples = new ArrayList<>();
			for (int i=0; i<samplesLists.size(); i++) {
				List<PureGR> samples = samplesLists.getValue(i);
				double weight = samplesLists.getWeight(i);
				if ((float)weight == (float)maxWeight) {
					totalSamples.addAll(samples);
				} else {
					int fractSamples = (int)(samples.size() * weight/maxWeight + 0.5);
					totalSamples.addAll(samples.subList(0, fractSamples));
				}
			}
			return totalSamples;
		}
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
	protected void doBuild(double[] unitSamples, double weightEach) {
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
		List<ClassificationDependentGR> samples = new ArrayList<>(unitSamples.length);
		for (double p : unitSamples) {
			int index = Math.min(numOrigSamples-1, (int)(p*numOrigSamples));
			EnumMap<NSHM27_SeisClassificationMethod, PureGR> grs = new EnumMap<>(NSHM27_SeisClassificationMethod.class);
			for (NSHM27_SeisClassificationMethod classification : origSamples.keySet())
				grs.put(classification, origSamples.get(classification).get(index));
			samples.add(new ClassificationDependentGR(grs, p));
		}
		setValues(samples, weightEach);
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

	@Override
	public double getFractile(ClassificationDependentGR value) {
		return value.getSampleFractile();
	}

}
