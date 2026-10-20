package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.logicTree.LogicTreeLevel.AbstractRandomlySampledLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnableLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.FractileSamplingLevel;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.PureGR;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModel.BinnedSamplesLevel;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModel.BinnedSamplesNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModel.NSHM27_SeisRateModelSample;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_SeismicityDataFiles;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.gson.JsonObject;

public class NSHM27_SeisRateModelSamples extends AbstractRandomlySampledLevel<Double, NSHM27_SeisRateModelSample>
implements BinnableLevel<Double, NSHM27_SeisRateModelSample, BinnedSamplesLevel>,
FractileSamplingLevel<Double, NSHM27_SeisRateModelSample> {
	
	private NSHM27_SeisRateModelEpochCatalog catalog;
	
	@SuppressWarnings("unused") // deserialization
	private NSHM27_SeisRateModelSamples(String name, String shortName) {
		super(name, shortName);
	}
	
	public NSHM27_SeisRateModelSamples(NSHM27_SeismicityRegions region, TectonicRegionType trt) {
		this(NSHM27_SeisRateModelEpochCatalog.load(region, trt));
	}

	public NSHM27_SeisRateModelSamples(NSHM27_SeisRateModelEpochCatalog catalog) {
		super(NSHM27_RegionLoader.getNameForTRT(catalog.getTectonicRegion())+" Seismicity Rate Model Samples",
				NSHM27_RegionLoader.getNameForTRT(catalog.getTectonicRegion())+"RateSamples",
				"Rate sample ", "RateSample", "RateSample");
		this.catalog = Preconditions.checkNotNull(catalog);
	}

	public NSHM27_SeisRateModelEpochCatalog getEpochCatalog() {
		return catalog;
	}
	
	@Override
	public Class<? extends Double> getValueType() {
		return Double.class;
	}
	
	protected CSVFile<String> loadCSV(NSHM27_SeisBValueMethod bMethod,
			NSHM27_SeisRateModelEpoch epoch) throws IOException {
		return loadCSV(catalog, bMethod, epoch);
	}
	
	private static CSVFile<String> loadCSV(NSHM27_SeisRateModelEpochCatalog catalog,
			NSHM27_SeisBValueMethod bMethod, NSHM27_SeisRateModelEpoch epoch) throws IOException {
		Preconditions.checkNotNull(epoch, "Epoch cannot be null");
		Preconditions.checkState(epoch.getRegion() == catalog.getRegion()
				&& epoch.getTectonicRegion() == catalog.getTectonicRegion()
				&& epoch.getDatasetId().equals(catalog.getDatasetId()), "Epoch/catalog mismatch");
		Preconditions.checkState(catalog.getDatasetId().equals(NSHM27_SeismicityDataFiles.RATE_DATASET),
				"Unsupported rate dataset '%s'; configured dataset is '%s'", catalog.getDatasetId(),
				NSHM27_SeismicityDataFiles.RATE_DATASET);
		File file = NSHM27_SeismicityDataFiles.locateEpochRatePairs(catalog.getRegion(),
				catalog.getTectonicRegion(), bMethod, epoch.getEpochIndex());
		return CSVFile.readFile(file, false);
	}
	
	public static List<PureGR> loadOrigSamples(NSHM27_SeismicityRegions seisReg,
			NSHM27_SeisRateModelEpoch epoch, TectonicRegionType trt,
			NSHM27_SeisBValueMethod bMethod) throws IOException {
		Preconditions.checkState(epoch.getDatasetId().equals(NSHM27_SeismicityDataFiles.RATE_DATASET),
				"Unsupported rate dataset '%s'; configured dataset is '%s'", epoch.getDatasetId(),
				NSHM27_SeismicityDataFiles.RATE_DATASET);
		NSHM27_SeisRateModelEpochCatalog catalog = NSHM27_SeisRateModelEpochCatalog.load(
				seisReg, trt, NSHM27_SeisRateModelEpochCatalog.DEFAULT_WEIGHTING);
		CSVFile<String> csv = loadCSV(catalog, bMethod, epoch);
		return SeismicityRateFileLoader.loadSamplesCSV(csv);
	}
	
	public List<PureGR> loadOrigSamples(NSHM27_SeisBValueMethod bMethod, NSHM27_SeisRateModelEpoch epoch) {
		Preconditions.checkNotNull(epoch, "Epoch cannot be null");
		CSVFile<String> csv;
		try {
			csv = loadCSV(bMethod, epoch);
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

	private static final Map<File, List<PureGR>> SAMPLE_CACHE = new HashMap<>();

	private static synchronized List<PureGR> loadSortedSamples(NSHM27_SeismicityRegions region,
			TectonicRegionType trt, NSHM27_SeisBValueMethod bMethod, NSHM27_SeisRateModelEpoch epoch) {
		File file = NSHM27_SeismicityDataFiles.locateEpochRatePairs(region, trt, bMethod, epoch.getEpochIndex());
		List<PureGR> samples = SAMPLE_CACHE.get(file);
		if (samples == null) {
			try {
				samples = SeismicityRateFileLoader.loadSamplesCSV(CSVFile.readFile(file, false));
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			Preconditions.checkState(!samples.isEmpty(), "No rate samples found in %s", file);
			samples.sort(COMP);
			samples = Collections.unmodifiableList(samples);
			SAMPLE_CACHE.put(file, samples);
		}
		return samples;
	}

	public static PureGR loadSample(NSHM27_SeismicityRegions region, NSHM27_SeisRateModelEpoch epoch,
			TectonicRegionType trt, NSHM27_SeisBValueMethod bMethod, double fractile) {
		Preconditions.checkArgument(fractile >= 0d && fractile < 1d,
				"Sample fractile must be in [0,1): %s", fractile);
		Preconditions.checkState(epoch.getRegion() == region && epoch.getTectonicRegion() == trt,
				"Epoch does not match region/TRT");
		Preconditions.checkState(epoch.getDatasetId().equals(NSHM27_SeismicityDataFiles.RATE_DATASET),
				"Unsupported rate dataset '%s'; configured dataset is '%s'", epoch.getDatasetId(),
				NSHM27_SeismicityDataFiles.RATE_DATASET);
		List<PureGR> samples = loadSortedSamples(region, trt, bMethod, epoch);
		int index = Math.min(samples.size()-1, (int)(fractile*samples.size()));
		return samples.get(index);
	}

	@Override
	protected void doBuild(double[] unitSamples, double weightEach) {
		List<Double> samples = new ArrayList<>(unitSamples.length);
		for (double p : unitSamples)
			samples.add(p);
		setValues(samples, weightEach);
	}

	@Override
	public NSHM27_SeisRateModelSample build(Double value, double weight, String name, String shortName,
			String filePrefix) {
		return new NSHM27_SeisRateModelSample(value, catalog, weight, name, shortName, filePrefix);
	}

	@Override
	public Class<? extends NSHM27_SeisRateModelSample> getType() {
		return NSHM27_SeisRateModelSample.class;
	}

	@Override
	public JsonObject toJsonObject() {
		JsonObject json = super.toJsonObject();

		json.add("epochCatalog", catalog.toJSON());
		
		return json;
	}
	
	@Override
	public void initFromJsonObject(JsonObject jsonObj) {
		catalog = NSHM27_SeisRateModelEpochCatalog.fromJSON(jsonObj.getAsJsonObject("epochCatalog"));
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
	public double getFractile(Double value) {
		return value;
	}

}
