package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_SeismicityDataFiles;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Ordered epoch catalog for one region, rate-model scope, dataset, and weighting scheme. */
public final class NSHM27_SeisRateModelEpochCatalog {

	public enum EpochWeighting {
		DURATION("Duration weights"), N("N weights"), SQRTN("SQRTN weights");

		private final String csvLabel;

		private EpochWeighting(String csvLabel) {
			this.csvLabel = csvLabel;
		}
	}

	public static final EpochWeighting DEFAULT_WEIGHTING = EpochWeighting.SQRTN;

	private final NSHM27_SeismicityRegions region;
	private final TectonicRegionType trt;
	private final boolean union;
	private final String datasetId;
	private final EpochWeighting weighting;
	private final List<NSHM27_SeisRateModelEpoch> epochs;

	private NSHM27_SeisRateModelEpochCatalog(NSHM27_SeismicityRegions region, TectonicRegionType trt, boolean union,
			String datasetId, EpochWeighting weighting, List<NSHM27_SeisRateModelEpoch> epochs) {
		this.region = Preconditions.checkNotNull(region);
		Preconditions.checkArgument(union == (trt == null),
				"Union catalogs must omit TRT; regime catalogs must supply TRT");
		this.trt = trt;
		this.union = union;
		this.datasetId = Preconditions.checkNotNull(datasetId);
		this.weighting = Preconditions.checkNotNull(weighting);
		Preconditions.checkArgument(!epochs.isEmpty(), "Epoch catalog is empty");
		for (int i=0; i<epochs.size(); i++) {
			NSHM27_SeisRateModelEpoch epoch = epochs.get(i);
			Preconditions.checkState(epoch.getEpochIndex() == i,
					"Epoch indexes must be contiguous and ordered: expected %s, found %s", i, epoch.getEpochIndex());
			Preconditions.checkState(epoch.getRegion() == region && epoch.getTectonicRegion() == trt
					&& epoch.isUnion() == union
					&& epoch.getDatasetId().equals(datasetId), "Epoch does not belong to this catalog: %s", epoch);
		}
		double sum = 0d;
		for (NSHM27_SeisRateModelEpoch epoch : epochs) {
			double weight = epoch.getNodeWeight();
			Preconditions.checkState(Double.isFinite(weight) && weight >= 0d,
					"Invalid weight for %s: %s", epoch, weight);
			sum += weight;
		}
		Preconditions.checkState(Math.abs(sum-1d) <= 1e-10, "Epoch weights sum to %s", sum);
		this.epochs = Collections.unmodifiableList(new ArrayList<>(epochs));
	}

	public static NSHM27_SeisRateModelEpochCatalog load(NSHM27_SeismicityRegions region, TectonicRegionType trt) {
		return load(region, trt, DEFAULT_WEIGHTING);
	}

	public static NSHM27_SeisRateModelEpochCatalog load(NSHM27_SeismicityRegions region, TectonicRegionType trt,
			EpochWeighting weighting) {
		return load(region, Preconditions.checkNotNull(trt), false, weighting);
	}

	public static NSHM27_SeisRateModelEpochCatalog loadUnion(NSHM27_SeismicityRegions region) {
		return loadUnion(region, DEFAULT_WEIGHTING);
	}

	public static NSHM27_SeisRateModelEpochCatalog loadUnion(NSHM27_SeismicityRegions region,
			EpochWeighting weighting) {
		return load(region, null, true, weighting);
	}

	private static NSHM27_SeisRateModelEpochCatalog load(NSHM27_SeismicityRegions region,
			TectonicRegionType trt, boolean union, EpochWeighting weighting) {
		try {
			ParsedEpochs[] parsed = new ParsedEpochs[NSHM27_SeisBValueMethod.values().length];
			for (NSHM27_SeisBValueMethod bMethod : NSHM27_SeisBValueMethod.values()) {
				File weightsFile = union ? NSHM27_SeismicityDataFiles.locateUnionRateWeights(region, bMethod)
						: NSHM27_SeismicityDataFiles.locateRateWeights(region, trt, bMethod);
				parsed[bMethod.ordinal()] = parse(weightsFile, weighting);
			}
			ParsedEpochs reference = parsed[0];
			for (int m=1; m<parsed.length; m++)
				reference.validateDefinitions(parsed[m], NSHM27_SeisBValueMethod.values()[m]);
			double[][] weightsByMethod = new double[parsed.length][];
			for (int m=0; m<parsed.length; m++)
				weightsByMethod[m] = parsed[m].weights;
			double[] averagedWeights = calcLogicTreeWeightedEpochWeights(weightsByMethod);
			List<NSHM27_SeisRateModelEpoch> epochs = new ArrayList<>(reference.size());
			for (int i=0; i<reference.size(); i++) {
				NSHM27_SeisRateModelEpoch epoch = union
						? NSHM27_SeisRateModelEpoch.forUnion(region, NSHM27_SeismicityDataFiles.RATE_DATASET,
								reference.startYears[i], reference.endYears[i], reference.mcs[i], i, averagedWeights[i])
						: new NSHM27_SeisRateModelEpoch(region, trt, NSHM27_SeismicityDataFiles.RATE_DATASET,
								reference.startYears[i], reference.endYears[i], reference.mcs[i], i, averagedWeights[i]);
				epochs.add(epoch);
			}
			return new NSHM27_SeisRateModelEpochCatalog(region, trt, union,
					NSHM27_SeismicityDataFiles.RATE_DATASET, weighting, epochs);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}

	static double[] calcLogicTreeWeightedEpochWeights(double[][] weightsByMethod) {
		NSHM27_SeisBValueMethod[] methods = NSHM27_SeisBValueMethod.values();
		Preconditions.checkArgument(weightsByMethod.length == methods.length,
				"Expected weights for %s b-value methods, found %s", methods.length, weightsByMethod.length);
		Preconditions.checkArgument(weightsByMethod.length > 0 && weightsByMethod[0].length > 0,
				"Epoch weights cannot be empty");
		int numEpochs = weightsByMethod[0].length;
		double[] averaged = new double[numEpochs];
		double methodWeightSum = 0d;
		for (int m=0; m<methods.length; m++) {
			Preconditions.checkArgument(weightsByMethod[m].length == numEpochs,
					"Epoch count mismatch for %s", methods[m]);
			double methodWeight = methods[m].getNodeWeight();
			Preconditions.checkState(Double.isFinite(methodWeight) && methodWeight >= 0d,
					"Invalid logic-tree weight for %s: %s", methods[m], methodWeight);
			methodWeightSum += methodWeight;
			for (int i=0; i<numEpochs; i++)
				averaged[i] += methodWeight*weightsByMethod[m][i];
		}
		Preconditions.checkState(methodWeightSum > 0d, "No positive b-value method weights");
		for (int i=0; i<numEpochs; i++)
			averaged[i] /= methodWeightSum;
		return averaged;
	}

	static NSHM27_SeisRateModelEpochCatalog load(File weightsFile, NSHM27_SeismicityRegions region,
			TectonicRegionType trt, String datasetId, EpochWeighting weighting) throws IOException {
		ParsedEpochs parsed = parse(weightsFile, weighting);
		List<NSHM27_SeisRateModelEpoch> epochs = new ArrayList<>(parsed.size());
		for (int i=0; i<parsed.size(); i++)
			epochs.add(new NSHM27_SeisRateModelEpoch(region, trt, datasetId,
					parsed.startYears[i], parsed.endYears[i], parsed.mcs[i], i, parsed.weights[i]));
		return new NSHM27_SeisRateModelEpochCatalog(region, trt, false, datasetId, weighting, epochs);
	}

	private static ParsedEpochs parse(File weightsFile, EpochWeighting weighting) throws IOException {
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
		for (int i=0; i<numEpochs; i++)
			weights[i] /= weightSum;
		return new ParsedEpochs(startYears, endYears, mcs, weights);
	}

	private static final class ParsedEpochs {
		final double[] startYears;
		final double[] endYears;
		final double[] mcs;
		final double[] weights;

		ParsedEpochs(double[] startYears, double[] endYears, double[] mcs, double[] weights) {
			this.startYears = startYears;
			this.endYears = endYears;
			this.mcs = mcs;
			this.weights = weights;
		}

		int size() { return weights.length; }

		void validateDefinitions(ParsedEpochs other, NSHM27_SeisBValueMethod bMethod) {
			Preconditions.checkState(size() == other.size(),
					"Epoch count differs for b-value method %s: %s != %s", bMethod, size(), other.size());
			for (int i=0; i<size(); i++)
				Preconditions.checkState(Double.doubleToLongBits(startYears[i]) == Double.doubleToLongBits(other.startYears[i])
						&& Double.doubleToLongBits(endYears[i]) == Double.doubleToLongBits(other.endYears[i])
						&& Double.doubleToLongBits(mcs[i]) == Double.doubleToLongBits(other.mcs[i]),
						"Epoch %s definition differs for b-value method %s", i+1, bMethod);
		}
	}

	public NSHM27_SeismicityRegions getRegion() { return region; }
	public boolean isUnion() { return union; }
	public TectonicRegionType getTectonicRegion() { return trt; }
	public String getDatasetId() { return datasetId; }
	public EpochWeighting getWeighting() { return weighting; }
	public List<NSHM27_SeisRateModelEpoch> getEpochs() { return epochs; }
	public NSHM27_SeisRateModelEpoch getEpoch(int index) { return epochs.get(index); }

	public JsonObject toJSON() {
		JsonObject json = new JsonObject();
		json.addProperty("region", region.name());
		json.addProperty("union", isUnion());
		if (!isUnion())
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
		boolean union = json.has("union") && json.get("union").getAsBoolean();
		TectonicRegionType trt = union ? null
				: TectonicRegionType.valueOf(json.get("tectonicRegime").getAsString());
		String datasetId = json.get("datasetId").getAsString();
		EpochWeighting weighting = EpochWeighting.valueOf(json.get("weighting").getAsString());
		JsonArray array = json.getAsJsonArray("epochs");
		List<NSHM27_SeisRateModelEpoch> epochs = new ArrayList<>(array.size());
		for (int i=0; i<array.size(); i++) {
			JsonObject epochJSON = array.get(i).getAsJsonObject();
			double startYear = epochJSON.get("startYear").getAsDouble();
			double endYear = epochJSON.get("endYear").getAsDouble();
			double mc = epochJSON.get("mc").getAsDouble();
			int index = epochJSON.get("index").getAsInt();
			double weight = epochJSON.get("weight").getAsDouble();
			epochs.add(union ? NSHM27_SeisRateModelEpoch.forUnion(region, datasetId,
					startYear, endYear, mc, index, weight)
					: new NSHM27_SeisRateModelEpoch(region, trt, datasetId,
							startYear, endYear, mc, index, weight));
		}
		return new NSHM27_SeisRateModelEpochCatalog(region, trt, union, datasetId, weighting, epochs);
	}

	@Override
	public int hashCode() {
		int result = java.util.Objects.hash(region, trt, union, datasetId, weighting);
		for (NSHM27_SeisRateModelEpoch epoch : epochs)
			result = 31*result + java.util.Objects.hash(epoch.getEpochIndex(), epoch.getStartYear(),
					epoch.getEndYear(), epoch.getMagComplete());
		for (NSHM27_SeisRateModelEpoch epoch : epochs)
			result = 31*result + Double.hashCode(epoch.getNodeWeight());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof NSHM27_SeisRateModelEpochCatalog)) return false;
		NSHM27_SeisRateModelEpochCatalog other = (NSHM27_SeisRateModelEpochCatalog)obj;
		if (region != other.region || trt != other.trt || union != other.union || !datasetId.equals(other.datasetId)
				|| weighting != other.weighting || epochs.size() != other.epochs.size())
			return false;
		for (int i=0; i<epochs.size(); i++) {
			NSHM27_SeisRateModelEpoch e1 = epochs.get(i);
			NSHM27_SeisRateModelEpoch e2 = other.epochs.get(i);
			if (e1.getEpochIndex() != e2.getEpochIndex()
					|| Double.doubleToLongBits(e1.getStartYear()) != Double.doubleToLongBits(e2.getStartYear())
					|| Double.doubleToLongBits(e1.getEndYear()) != Double.doubleToLongBits(e2.getEndYear())
					|| Double.doubleToLongBits(e1.getMagComplete()) != Double.doubleToLongBits(e2.getMagComplete()))
				return false;
			if (Double.doubleToLongBits(e1.getNodeWeight()) != Double.doubleToLongBits(e2.getNodeWeight()))
				return false;
		}
		return true;
	}
}
