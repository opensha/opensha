package org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.RateRecord;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.RateType;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateModel;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModelEpochCatalog.EpochWeighting;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_SeismicityDataFiles;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM27_SeisRateModelBranch implements NSHM27_SeisRateModel, LogicTreeNode.FixedWeightNode {
	LOW("Lower seismicity bound (p2.5)", "Low", 0.13d),
	PREFFERRED("Preffered seismicity rate", "Preferred", 0.74d),
	HIGH("Upper seismicity bound (p97.5)", "High", 0.13d);

	public static RateType TYPE = RateType.M1_TO_MMAX;
	private static final UncertaintyBoundType BOUND_TYPE = UncertaintyBoundType.CONF_95;

	private final String name;
	private final String shortName;
	private final double weight;

	private static Map<String, SeismicityRateModel> epochRateModelsCache;
	private static Map<String, SeismicityRateModel> mixtureRateModelsCache;

	private NSHM27_SeisRateModelBranch(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}

	@Override public String getShortName() { return shortName; }
	@Override public String getName() { return name; }
	@Override public double getNodeWeight() { return weight; }
	@Override public String getFilePrefix() { return getShortName(); }

	@Override
	public IncrementalMagFreqDist build(NSHM27_SeismicityRegions region, TectonicRegionType trt,
			LogicTreeBranch<?> branch, EvenlyDiscretizedFunc refMFD, double mMax) {
		SeismicityRateModel model = loadRateModel(region, trt, branch);
		return switch (this) {
		case LOW -> model.buildLower(refMFD, mMax);
		case PREFFERRED -> model.buildPreferred(refMFD, mMax);
		case HIGH -> model.buildUpper(refMFD, mMax);
		};
	}

	@Override
	public RateRecord getRateRecord(NSHM27_SeismicityRegions region, TectonicRegionType trt,
			LogicTreeBranch<?> branch) {
		SeismicityRateModel model = loadRateModel(region, trt, branch);
		return switch (this) {
		case LOW -> model.getLowerRecord();
		case PREFFERRED -> model.getMeanRecord();
		case HIGH -> model.getUpperRecord();
		};
	}

	private static SeismicityRateModel loadRateModel(NSHM27_SeismicityRegions region,
			TectonicRegionType trt, LogicTreeBranch<?> branch) {
		NSHM27_SeisBValueMethod bMethod = branch.requireValue(NSHM27_SeisBValueMethod.class);
		NSHM27_SeisRateModelEpoch epoch = branch.getValue(NSHM27_SeisRateModelEpoch.class);
		if (epoch == null)
			return loadMixtureRateModel(region, trt, bMethod);
		return loadRateModel(region, epoch, trt, bMethod);
	}

	public static double getPlotMmax(TectonicRegionType trt) {
		return switch (trt) {
		case ACTIVE_SHALLOW, SUBDUCTION_SLAB -> 8d;
		case SUBDUCTION_INTERFACE -> 9d;
		default -> throw new IllegalArgumentException("Unexpected tectonic regime: "+trt);
		};
	}

	public static SeismicityRateModel loadRateModel(NSHM27_SeismicityRegions region,
			NSHM27_SeisRateModelEpoch epoch, TectonicRegionType trt, NSHM27_SeisBValueMethod bMethod) {
		return loadRateModel(region, epoch, trt, bMethod, TYPE);
	}

	public synchronized static SeismicityRateModel loadRateModel(NSHM27_SeismicityRegions region,
			NSHM27_SeisRateModelEpoch epoch, TectonicRegionType trt, NSHM27_SeisBValueMethod bMethod,
			RateType type) {
		Preconditions.checkNotNull(epoch, "Epoch cannot be null; use loadMixtureRateModel for mixture models");
		Preconditions.checkState(epoch.getRegion() == region,
				"Epoch region mismatch: %s != %s", epoch.getRegion(), region);
		Preconditions.checkState(epoch.getTectonicRegion() == trt,
				"Epoch TRT mismatch: %s != %s", epoch.getTectonicRegion(), trt);
		if (epochRateModelsCache == null)
			epochRateModelsCache = new HashMap<>();
		String key = region.name()+"/"+trt.name()+"/"+bMethod.name()+"/"+epoch.getDatasetId()
				+"/"+epoch.getEpochIndex();
		SeismicityRateModel model = type == TYPE ? epochRateModelsCache.get(key) : null;
		if (model == null) {
			try {
				model = new SeismicityRateModel(loadEpochCSV(region, epoch, trt, bMethod), type, BOUND_TYPE);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			if (type == TYPE)
				epochRateModelsCache.put(key, model);
		}
		return model;
	}

	public synchronized static SeismicityRateModel loadMixtureRateModel(NSHM27_SeismicityRegions region,
			TectonicRegionType trt, NSHM27_SeisBValueMethod bMethod) {
		return loadMixtureRateModel(region, trt, bMethod,
				NSHM27_SeisRateModelEpochCatalog.DEFAULT_WEIGHTING, TYPE);
	}

	public synchronized static SeismicityRateModel loadMixtureRateModel(NSHM27_SeismicityRegions region,
			TectonicRegionType trt, NSHM27_SeisBValueMethod bMethod, EpochWeighting weighting, RateType type) {
		if (mixtureRateModelsCache == null)
			mixtureRateModelsCache = new HashMap<>();
		String key = region.name()+"/"+trt.name()+"/"+bMethod.name()+"/"+weighting.name();
		SeismicityRateModel model = type == TYPE ? mixtureRateModelsCache.get(key) : null;
		if (model == null) {
			try {
				model = new SeismicityRateModel(loadMixtureCSV(region, trt, bMethod, weighting), type, BOUND_TYPE);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			if (type == TYPE)
				mixtureRateModelsCache.put(key, model);
		}
		return model;
	}

	public static SeismicityRateModel loadUnionMixtureRateModel(NSHM27_SeismicityRegions region,
			NSHM27_SeisBValueMethod bMethod) {
		return loadUnionMixtureRateModel(region, bMethod,
				NSHM27_SeisRateModelEpochCatalog.DEFAULT_WEIGHTING, TYPE);
	}

	public static SeismicityRateModel loadUnionMixtureRateModel(NSHM27_SeismicityRegions region,
			NSHM27_SeisBValueMethod bMethod, EpochWeighting weighting) {
		return loadUnionMixtureRateModel(region, bMethod, weighting, TYPE);
	}

	public synchronized static SeismicityRateModel loadUnionMixtureRateModel(NSHM27_SeismicityRegions region,
			NSHM27_SeisBValueMethod bMethod, EpochWeighting weighting, RateType type) {
		if (mixtureRateModelsCache == null)
			mixtureRateModelsCache = new HashMap<>();
		String key = region.name()+"/UNION/"+bMethod.name()+"/"+weighting.name();
		SeismicityRateModel model = type == TYPE ? mixtureRateModelsCache.get(key) : null;
		if (model == null) {
			try {
				model = new SeismicityRateModel(loadUnionMixtureCSV(region, bMethod, weighting), type, BOUND_TYPE);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			if (type == TYPE)
				mixtureRateModelsCache.put(key, model);
		}
		return model;
	}

	private static String getRateModelPath(NSHM27_SeismicityRegions region, String datasetId) {
		return "/data/erf/nshm27/"+region.name().toLowerCase()+"/seismicity/rates/"+datasetId+"/";
	}

	private static String getMixtureRateModelCSVName(TectonicRegionType trt, EpochWeighting weighting) {
		return NSHM27_RegionLoader.getNameForTRT(trt).toUpperCase()
				+"_MIXTURE_"+weighting.name()+".csv";
	}

	private static String getEpochRateModelCSVName(TectonicRegionType trt, NSHM27_SeisRateModelEpoch epoch) {
		return NSHM27_RegionLoader.getNameForTRT(trt).toUpperCase()
				+"_EPOCH_"+(epoch.getEpochIndex()+1)+".csv";
	}

	private static CSVFile<String> loadEpochCSV(NSHM27_SeismicityRegions region,
			NSHM27_SeisRateModelEpoch epoch, TectonicRegionType trt,
			NSHM27_SeisBValueMethod bMethod) throws IOException {
		String resourceName = getRateModelPath(region, NSHM27_SeismicityDataFiles.rateDatasetDirectoryName(bMethod))
				+getEpochRateModelCSVName(trt, epoch);
		return loadResourceCSV(resourceName, "epoch rate-model");
	}

	private static CSVFile<String> loadMixtureCSV(NSHM27_SeismicityRegions region,
			TectonicRegionType trt, NSHM27_SeisBValueMethod bMethod, EpochWeighting weighting) throws IOException {
		String resourceName = getRateModelPath(region, NSHM27_SeismicityDataFiles.rateDatasetDirectoryName(bMethod))
				+getMixtureRateModelCSVName(trt, weighting);
		return loadResourceCSV(resourceName, "mixture rate-model");
	}

	private static CSVFile<String> loadUnionMixtureCSV(NSHM27_SeismicityRegions region,
			NSHM27_SeisBValueMethod bMethod, EpochWeighting weighting) throws IOException {
		String resourceName = getRateModelPath(region, NSHM27_SeismicityDataFiles.rateDatasetDirectoryName(bMethod))
				+"UNION_MIXTURE_"+weighting.name()+".csv";
		return loadResourceCSV(resourceName, "union mixture rate-model");
	}

	private static CSVFile<String> loadResourceCSV(String resourceName, String description) throws IOException {
		try (InputStream stream = NSHM27_SeisRateModelBranch.class.getResourceAsStream(resourceName)) {
			if (stream == null)
				throw new IOException("Missing "+description+" CSV '"+resourceName
						+"'; update the centralized filename convention when final files are supplied");
			return CSVFile.readStream(stream, false);
		}
	}
}
