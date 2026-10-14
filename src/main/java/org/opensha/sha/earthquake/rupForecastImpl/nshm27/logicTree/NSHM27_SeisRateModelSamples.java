package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.logicTree.LogicTreeLevel.AbstractRandomlySampledLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnableLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.FractileSamplingLevel;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.PureGR;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.NSHM27_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModel.BinnedSamplesLevel;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModel.BinnedSamplesNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModel.EpochDependentGR;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModel.NSHM27_SeisRateModelSample;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;

public class NSHM27_SeisRateModelSamples extends AbstractRandomlySampledLevel<EpochDependentGR, NSHM27_SeisRateModelSample>
implements BinnableLevel<EpochDependentGR, NSHM27_SeisRateModelSample, BinnedSamplesLevel>,
FractileSamplingLevel<EpochDependentGR, NSHM27_SeisRateModelSample> {
	
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
	public Class<? extends EpochDependentGR> getValueType() {
		return EpochDependentGR.class;
	}
	
	protected CSVFile<String> loadCSV(NSHM27_SeisRateModelEpoch epoch) throws IOException {
		return loadCSV(catalog, epoch);
	}
	
	private static CSVFile<String> loadCSV(NSHM27_SeisRateModelEpochCatalog catalog,
			NSHM27_SeisRateModelEpoch epoch) throws IOException {
		Preconditions.checkNotNull(epoch, "Epoch cannot be null");
		Preconditions.checkState(epoch.getRegion() == catalog.getRegion()
				&& epoch.getTectonicRegion() == catalog.getTectonicRegion()
				&& epoch.getDatasetId().equals(catalog.getDatasetId()), "Epoch/catalog mismatch");
		File dir = new File(NSHM27_InvConfigFactory.locateDataDirectory(), "seis_rate_samples/"
				+catalog.getRegion().name().toLowerCase()+"/"+catalog.getDatasetId());
		String prefix = "comb-"+catalog.getRegion().name()+"-"
				+NSHM27_SeisRateModelEpochCatalog.regimeFileLabel(catalog.getTectonicRegion())+"-";
		String suffix = "-epoch-"+(epoch.getEpochIndex()+1)+"-pairs.csv";
		File[] matches = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(suffix));
		Preconditions.checkState(matches != null && matches.length == 1,
				"Expected exactly one epoch sample CSV beginning '%s' and ending '%s' in %s; found %s",
				prefix, suffix, dir, matches == null ? 0 : matches.length);
		return CSVFile.readFile(matches[0], false);
	}
	
	public static List<PureGR> loadOrigSamples(NSHM27_SeismicityRegions seisReg,
			NSHM27_SeisRateModelEpoch epoch, TectonicRegionType trt) throws IOException {
		NSHM27_SeisRateModelEpochCatalog catalog = NSHM27_SeisRateModelEpochCatalog.load(
				seisReg, trt, epoch.getDatasetId(), NSHM27_SeisRateModelEpochCatalog.DEFAULT_WEIGHTING);
		CSVFile<String> csv = loadCSV(catalog, epoch);
		return SeismicityRateFileLoader.loadSamplesCSV(csv);
	}
	
	public List<PureGR> loadOrigSamples(NSHM27_SeisRateModelEpoch epoch) {
		Preconditions.checkNotNull(epoch, "Epoch cannot be null");
		CSVFile<String> csv;
		try {
			csv = loadCSV(epoch);
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
		List<List<PureGR>> origSamples = new ArrayList<>(catalog.getEpochs().size());
		int numOrigSamples = -1;
		for (NSHM27_SeisRateModelEpoch epoch : catalog.getEpochs()) {
			List<PureGR> samples = loadOrigSamples(epoch);
			if (numOrigSamples == -1)
				numOrigSamples = samples.size();
			else
				Preconditions.checkState(numOrigSamples == samples.size(),
						"Epoch sample count mismatch: expected %s, found %s for %s", numOrigSamples, samples.size(), epoch);
			// sort by rate
			samples.sort(COMP);
			origSamples.add(samples);
		}
		List<EpochDependentGR> samples = new ArrayList<>(unitSamples.length);
		for (double p : unitSamples) {
			int index = Math.min(numOrigSamples-1, (int)(p*numOrigSamples));
			List<PureGR> grs = new ArrayList<>(origSamples.size());
			for (List<PureGR> epochSamples : origSamples)
				grs.add(epochSamples.get(index));
			samples.add(new EpochDependentGR(grs, p));
		}
		setValues(samples, weightEach);
	}

	@Override
	public NSHM27_SeisRateModelSample build(EpochDependentGR value, double weight, String name, String shortName,
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
	public TypeAdapter<EpochDependentGR> getValueTypeAdapter() {
		return new NSHM27_SeisRateModel.EpochDependentGRAdapter(catalog.getEpochs().size());
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
	public double getFractile(EpochDependentGR value) {
		return value.getSampleFractile();
	}

}
