package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.NSHM27_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Ordered epoch catalog for one region, tectonic regime, dataset, and weighting scheme. */
public final class NSHM27_SeisRateModelEpochCatalog {

	public enum EpochWeighting {
		DURATION("Duration weights"), N("N weights"), SQRTN("SQRTN weights");

		private final String csvLabel;

		private EpochWeighting(String csvLabel) {
			this.csvLabel = csvLabel;
		}
	}

	public static final String DEFAULT_DATASET_ID = "2026_10_13";
	public static final EpochWeighting DEFAULT_WEIGHTING = EpochWeighting.SQRTN;

	private final NSHM27_SeismicityRegions region;
	private final TectonicRegionType trt;
	private final String datasetId;
	private final EpochWeighting weighting;
	private final List<NSHM27_SeisRateModelEpoch> epochs;

	private NSHM27_SeisRateModelEpochCatalog(NSHM27_SeismicityRegions region, TectonicRegionType trt,
			String datasetId, EpochWeighting weighting, List<NSHM27_SeisRateModelEpoch> epochs) {
		this.region = Preconditions.checkNotNull(region);
		this.trt = Preconditions.checkNotNull(trt);
		this.datasetId = Preconditions.checkNotNull(datasetId);
		this.weighting = Preconditions.checkNotNull(weighting);
		Preconditions.checkArgument(!epochs.isEmpty(), "Epoch catalog is empty");
		for (int i=0; i<epochs.size(); i++) {
			NSHM27_SeisRateModelEpoch epoch = epochs.get(i);
			Preconditions.checkState(epoch.getEpochIndex() == i,
					"Epoch indexes must be contiguous and ordered: expected %s, found %s", i, epoch.getEpochIndex());
			Preconditions.checkState(epoch.getRegion() == region && epoch.getTectonicRegion() == trt
					&& epoch.getDatasetId().equals(datasetId), "Epoch does not belong to this catalog: %s", epoch);
		}
		this.epochs = Collections.unmodifiableList(new ArrayList<>(epochs));
	}

	public static NSHM27_SeisRateModelEpochCatalog load(NSHM27_SeismicityRegions region, TectonicRegionType trt) {
		return load(region, trt, DEFAULT_DATASET_ID, DEFAULT_WEIGHTING);
	}

	public static NSHM27_SeisRateModelEpochCatalog load(NSHM27_SeismicityRegions region, TectonicRegionType trt,
			String datasetId, EpochWeighting weighting) {
		try {
			return load(locateWeightsCSV(region, trt, datasetId), region, trt, datasetId, weighting);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}

	static NSHM27_SeisRateModelEpochCatalog load(File weightsFile, NSHM27_SeismicityRegions region,
			TectonicRegionType trt, String datasetId, EpochWeighting weighting) throws IOException {
		CSVFile<String> csv = CSVFile.readFile(weightsFile, false);
		int numEpochs = -1;
		int epochStartRow = -1;
		double[] weights = null;
		for (int row=0; row<csv.getNumRows(); row++) {
			String label = csv.get(row, 0).trim();
			if (numEpochs < 0 && label.equalsIgnoreCase("n epochs")) {
				numEpochs = Integer.parseInt(csv.get(row, 1).trim());
			} else if (epochStartRow < 0 && label.equalsIgnoreCase("t start")) {
				epochStartRow = row+1;
			} else if (weights == null && label.equalsIgnoreCase(weighting.csvLabel)) {
				Preconditions.checkState(numEpochs > 0, "Encountered weights before epoch count in %s", weightsFile);
				weights = new double[numEpochs];
				for (int i=0; i<numEpochs; i++)
					weights[i] = Double.parseDouble(csv.get(row, i+1).trim());
			}
			if (numEpochs > 0 && epochStartRow >= 0 && weights != null)
				break; // deliberately ignore a duplicated trailing block
		}
		Preconditions.checkState(numEpochs > 0, "No epoch count found in %s", weightsFile);
		Preconditions.checkState(epochStartRow >= 0 && epochStartRow+numEpochs <= csv.getNumRows(),
				"No complete epoch table found in %s", weightsFile);
		Preconditions.checkNotNull(weights, "No '%s' row found in %s", weighting.csvLabel, weightsFile);

		double[] startYears = new double[numEpochs];
		double[] endYears = new double[numEpochs];
		double[] mcs = new double[numEpochs];
		double weightSum = 0d;
		for (int i=0; i<numEpochs; i++) {
			int row = epochStartRow+i;
			startYears[i] = Double.parseDouble(csv.get(row, 0).trim());
			endYears[i] = Double.parseDouble(csv.get(row, 1).trim());
			mcs[i] = Double.parseDouble(csv.get(row, 2).trim());
			Preconditions.checkState(endYears[i] > startYears[i], "Bad epoch range at index %s in %s", i, weightsFile);
			Preconditions.checkState(Double.isFinite(weights[i]) && weights[i] >= 0d,
					"Bad epoch weight at index %s in %s", i, weightsFile);
			weightSum += weights[i];
		}
		Preconditions.checkState(Math.abs(weightSum-1d) <= 1e-5,
				"Epoch weights do not sum to 1 in %s: %s", weightsFile, weightSum);
		List<NSHM27_SeisRateModelEpoch> epochs = new ArrayList<>(numEpochs);
		for (int i=0; i<numEpochs; i++)
			epochs.add(new NSHM27_SeisRateModelEpoch(region, trt, datasetId,
					startYears[i], endYears[i], mcs[i], i, weights[i]/weightSum));
		return new NSHM27_SeisRateModelEpochCatalog(region, trt, datasetId, weighting, epochs);
	}

	private static File locateWeightsCSV(NSHM27_SeismicityRegions region, TectonicRegionType trt, String datasetId) {
		File dir = new File(NSHM27_InvConfigFactory.locateDataDirectory(),
				"seis_rate_samples/"+region.name().toLowerCase()+"/"+datasetId);
		Preconditions.checkState(dir.isDirectory(), "Seismicity-rate dataset directory does not exist: %s", dir);
		String prefix = "comb-"+region.name()+"-"+regimeFileLabel(trt)+"-";
		File[] matches = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith("-weights.csv"));
		Preconditions.checkState(matches != null && matches.length == 1,
				"Expected exactly one epoch weights CSV beginning '%s' in %s; found %s",
				prefix, dir, matches == null ? 0 : matches.length);
		return matches[0];
	}

	static String regimeFileLabel(TectonicRegionType trt) {
		return switch (trt) {
		case ACTIVE_SHALLOW -> "Crustal";
		case SUBDUCTION_INTERFACE -> "Interface";
		case SUBDUCTION_SLAB -> "Downgoing";
		default -> throw new IllegalArgumentException("Unsupported tectonic regime: "+trt);
		};
	}

	public NSHM27_SeismicityRegions getRegion() { return region; }
	public TectonicRegionType getTectonicRegion() { return trt; }
	public String getDatasetId() { return datasetId; }
	public EpochWeighting getWeighting() { return weighting; }
	public List<NSHM27_SeisRateModelEpoch> getEpochs() { return epochs; }
	public NSHM27_SeisRateModelEpoch getEpoch(int index) { return epochs.get(index); }

	public JsonObject toJSON() {
		JsonObject json = new JsonObject();
		json.addProperty("region", region.name());
		json.addProperty("tectonicRegime", trt.name());
		json.addProperty("datasetId", datasetId);
		json.addProperty("weighting", weighting.name());
		JsonArray array = new JsonArray();
		for (NSHM27_SeisRateModelEpoch epoch : epochs) {
			JsonObject epochJSON = new JsonObject();
			epochJSON.addProperty("index", epoch.getEpochIndex());
			epochJSON.addProperty("startYear", epoch.getStartYear());
			epochJSON.addProperty("endYear", epoch.getEndYear());
			epochJSON.addProperty("mc", epoch.getMagComplete());
			epochJSON.addProperty("weight", epoch.getNodeWeight());
			array.add(epochJSON);
		}
		json.add("epochs", array);
		return json;
	}

	public static NSHM27_SeisRateModelEpochCatalog fromJSON(JsonObject json) {
		NSHM27_SeismicityRegions region = NSHM27_SeismicityRegions.valueOf(json.get("region").getAsString());
		TectonicRegionType trt = TectonicRegionType.valueOf(json.get("tectonicRegime").getAsString());
		String datasetId = json.get("datasetId").getAsString();
		EpochWeighting weighting = EpochWeighting.valueOf(json.get("weighting").getAsString());
		JsonArray array = json.getAsJsonArray("epochs");
		List<NSHM27_SeisRateModelEpoch> epochs = new ArrayList<>(array.size());
		for (int i=0; i<array.size(); i++) {
			JsonObject epochJSON = array.get(i).getAsJsonObject();
			epochs.add(new NSHM27_SeisRateModelEpoch(region, trt, datasetId,
					epochJSON.get("startYear").getAsDouble(), epochJSON.get("endYear").getAsDouble(),
					epochJSON.get("mc").getAsDouble(), epochJSON.get("index").getAsInt(),
					epochJSON.get("weight").getAsDouble()));
		}
		return new NSHM27_SeisRateModelEpochCatalog(region, trt, datasetId, weighting, epochs);
	}

	@Override
	public int hashCode() {
		int result = java.util.Objects.hash(region, trt, datasetId, weighting);
		for (NSHM27_SeisRateModelEpoch epoch : epochs)
			result = 31*result + java.util.Objects.hash(epoch.getEpochIndex(), epoch.getStartYear(),
					epoch.getEndYear(), epoch.getMagComplete(), epoch.getNodeWeight());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof NSHM27_SeisRateModelEpochCatalog)) return false;
		NSHM27_SeisRateModelEpochCatalog other = (NSHM27_SeisRateModelEpochCatalog)obj;
		if (region != other.region || trt != other.trt || !datasetId.equals(other.datasetId)
				|| weighting != other.weighting || epochs.size() != other.epochs.size())
			return false;
		for (int i=0; i<epochs.size(); i++) {
			NSHM27_SeisRateModelEpoch e1 = epochs.get(i);
			NSHM27_SeisRateModelEpoch e2 = other.epochs.get(i);
			if (e1.getEpochIndex() != e2.getEpochIndex()
					|| Double.doubleToLongBits(e1.getStartYear()) != Double.doubleToLongBits(e2.getStartYear())
					|| Double.doubleToLongBits(e1.getEndYear()) != Double.doubleToLongBits(e2.getEndYear())
					|| Double.doubleToLongBits(e1.getMagComplete()) != Double.doubleToLongBits(e2.getMagComplete())
					|| Double.doubleToLongBits(e1.getNodeWeight()) != Double.doubleToLongBits(e2.getNodeWeight()))
				return false;
		}
		return true;
	}
}
