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
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.RateRecord;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateFileLoader.RateType;
import org.opensha.sha.earthquake.nshmp.seismicity.SeismicityRateModel;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.logicTree.NSHM27_SeisRateModelEpochCatalog.EpochWeighting;
import org.opensha.sha.earthquake.rupForecastImpl.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM27_SeisRateModelBranch implements NSHM27_SeisRateModel, LogicTreeNode.FixedWeightNode {
	LOW("Lower Seismicity Bound (p2.5)", "Low", 0.13d) {
		@Override
		public IncrementalMagFreqDist build(NSHM27_SeismicityRegions region, NSHM27_SeisRateModelEpoch epoch,
				TectonicRegionType trt, EvenlyDiscretizedFunc refMFD, double mMax) {
			return loadRateModel(region, epoch, trt, TYPE).buildLower(refMFD, mMax);
		}

		@Override
		public RateRecord getRateRecord(NSHM27_SeismicityRegions region, NSHM27_SeisRateModelEpoch epoch,
				TectonicRegionType trt) {
			return loadRateModel(region, epoch, trt, TYPE).getLowerRecord();
		}
	},
	PREFFERRED("Preffered Seismicity Rate", "Preferred", 0.74d) {
		@Override
		public IncrementalMagFreqDist build(NSHM27_SeismicityRegions region, NSHM27_SeisRateModelEpoch epoch,
				TectonicRegionType trt, EvenlyDiscretizedFunc refMFD, double mMax) {
			return loadRateModel(region, epoch, trt, TYPE).buildPreferred(refMFD, mMax);
		}

		@Override
		public RateRecord getRateRecord(NSHM27_SeismicityRegions region, NSHM27_SeisRateModelEpoch epoch,
				TectonicRegionType trt) {
			return loadRateModel(region, epoch, trt, TYPE).getMeanRecord();
		}
	},
	HIGH("Upper Seismicity Bound (p97.5)", "High", 0.13d) {
		@Override
		public IncrementalMagFreqDist build(NSHM27_SeismicityRegions region, NSHM27_SeisRateModelEpoch epoch,
				TectonicRegionType trt, EvenlyDiscretizedFunc refMFD, double mMax) {
			return loadRateModel(region, epoch, trt, TYPE).buildUpper(refMFD, mMax);
		}

		@Override
		public RateRecord getRateRecord(NSHM27_SeismicityRegions region, NSHM27_SeisRateModelEpoch epoch,
				TectonicRegionType trt) {
			return loadRateModel(region, epoch, trt, TYPE).getUpperRecord();
		}
	};

	public static RateType TYPE = RateType.M1_TO_MMAX;
	private static final UncertaintyBoundType BOUND_TYPE = UncertaintyBoundType.CONF_95;

	private final String name;
	private final String shortName;
	private final double weight;

	private static Table<NSHM27_SeismicityRegions, TectonicRegionType,
			Map<NSHM27_SeisRateModelEpoch, SeismicityRateModel>> rateModelsCache;
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

	public static double getPlotMmax(TectonicRegionType trt) {
		return switch (trt) {
		case ACTIVE_SHALLOW, SUBDUCTION_SLAB -> 8d;
		case SUBDUCTION_INTERFACE -> 9d;
		default -> throw new IllegalArgumentException("Unexpected tectonic regime: "+trt);
		};
	}

	public static SeismicityRateModel loadRateModel(NSHM27_SeismicityRegions region,
			NSHM27_SeisRateModelEpoch epoch, TectonicRegionType trt) {
		return loadRateModel(region, epoch, trt, TYPE);
	}

	public synchronized static SeismicityRateModel loadRateModel(NSHM27_SeismicityRegions region,
			NSHM27_SeisRateModelEpoch epoch, TectonicRegionType trt, RateType type) {
		Preconditions.checkNotNull(epoch, "Epoch cannot be null; use loadMixtureRateModel for mixture models");
		Preconditions.checkState(epoch.getRegion() == region, "Epoch region mismatch: %s != %s", epoch.getRegion(), region);
		Preconditions.checkState(epoch.getTectonicRegion() == trt, "Epoch TRT mismatch: %s != %s", epoch.getTectonicRegion(), trt);
		if (rateModelsCache == null)
			rateModelsCache = HashBasedTable.create();
		if (type == TYPE) {
			Map<NSHM27_SeisRateModelEpoch, SeismicityRateModel> models = rateModelsCache.get(region, trt);
			if (models == null) {
				models = new HashMap<>();
				rateModelsCache.put(region, trt, models);
			}
			SeismicityRateModel model = models.get(epoch);
			if (model == null) {
				model = buildModel(region, epoch, trt, type);
				models.put(epoch, model);
			}
			return model;
		}
		return buildModel(region, epoch, trt, type);
	}

	private static SeismicityRateModel buildModel(NSHM27_SeismicityRegions region,
			NSHM27_SeisRateModelEpoch epoch, TectonicRegionType trt, RateType type) {
		try {
			return new SeismicityRateModel(loadEpochCSV(region, epoch, trt), type, BOUND_TYPE);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}

	public static SeismicityRateModel loadMixtureRateModel(NSHM27_SeismicityRegions region, TectonicRegionType trt) {
		return loadMixtureRateModel(region, trt, NSHM27_SeisRateModelEpochCatalog.DEFAULT_WEIGHTING);
	}

	public synchronized static SeismicityRateModel loadMixtureRateModel(NSHM27_SeismicityRegions region,
			TectonicRegionType trt, EpochWeighting weighting) {
		if (mixtureRateModelsCache == null)
			mixtureRateModelsCache = new HashMap<>();
		String key = region.name()+"/"+trt.name()+"/"+weighting.name();
		SeismicityRateModel model = mixtureRateModelsCache.get(key);
		if (model == null) {
			try {
				model = new SeismicityRateModel(loadMixtureCSV(region, trt, weighting), TYPE, BOUND_TYPE);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			mixtureRateModelsCache.put(key, model);
		}
		return model;
	}

	private static String getRateModelPath(NSHM27_SeismicityRegions region, String datasetId) {
		return "/data/erf/nshm27/"+region.name().toLowerCase()+"/seismicity/rates/"+datasetId+"/";
	}

	/** Placeholder convention until final epoch branch files are supplied. */
	private static String getEpochRateModelCSVName(TectonicRegionType trt, NSHM27_SeisRateModelEpoch epoch) {
		return NSHM27_SeisRateModelEpochCatalog.regimeFileLabel(trt).toUpperCase()
				+"_EPOCH_"+(epoch.getEpochIndex()+1)+".csv";
	}

	/** Placeholder convention until final mixture branch files are supplied. */
	private static String getMixtureRateModelCSVName(TectonicRegionType trt, EpochWeighting weighting) {
		return NSHM27_SeisRateModelEpochCatalog.regimeFileLabel(trt).toUpperCase()
				+"_MIXTURE_"+weighting.name()+".csv";
	}

	private static CSVFile<String> loadEpochCSV(NSHM27_SeismicityRegions region,
			NSHM27_SeisRateModelEpoch epoch, TectonicRegionType trt) throws IOException {
		String resourceName = getRateModelPath(region, epoch.getDatasetId())+getEpochRateModelCSVName(trt, epoch);
		return loadResourceCSV(resourceName, "epoch rate-model");
	}

	private static CSVFile<String> loadMixtureCSV(NSHM27_SeismicityRegions region,
			TectonicRegionType trt, EpochWeighting weighting) throws IOException {
		String resourceName = getRateModelPath(region, NSHM27_SeisRateModelEpochCatalog.DEFAULT_DATASET_ID)
				+getMixtureRateModelCSVName(trt, weighting);
		return loadResourceCSV(resourceName, "mixture rate-model");
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
