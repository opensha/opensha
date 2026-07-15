package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
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
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateModel;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.RateRecord;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.RateType;

// this affects interface slip rates
@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
// this affects interface slip rates
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM27_SeisRateModelBranch implements NSHM27_SeisRateModel, LogicTreeNode.FixedWeightNode {
	LOW("Lower Seismicity Bound (p2.5)", "Low", 0.13d) {
		@Override
		public IncrementalMagFreqDist build(NSHM27_SeismicityRegions region, NSHM27_SeisClassificationMethod classification, TectonicRegionType trt, EvenlyDiscretizedFunc refMFD, double mMax) {
			return loadRateModel(region, classification, trt, TYPE).buildLower(refMFD, mMax);
		}

		@Override
		public RateRecord getRateRecord(NSHM27_SeismicityRegions region, NSHM27_SeisClassificationMethod classification, TectonicRegionType trt) {
			return loadRateModel(region, classification, trt, TYPE).getLowerRecord();
		}
	},
	PREFFERRED("Preffered Seismicity Rate", "Preferred", 0.74d) {
		@Override
		public IncrementalMagFreqDist build(NSHM27_SeismicityRegions region, NSHM27_SeisClassificationMethod classification, TectonicRegionType trt, EvenlyDiscretizedFunc refMFD, double mMax) {
			return loadRateModel(region, classification, trt, TYPE).buildPreferred(refMFD, mMax);
		}

		@Override
		public RateRecord getRateRecord(NSHM27_SeismicityRegions region, NSHM27_SeisClassificationMethod classification, TectonicRegionType trt) {
			return loadRateModel(region, classification, trt, TYPE).getMeanRecord();
		}
	},
	HIGH("Upper Seismicity Bound (p97.5)", "High", 0.13d) {
		@Override
		public IncrementalMagFreqDist build(NSHM27_SeismicityRegions region, NSHM27_SeisClassificationMethod classification, TectonicRegionType trt, EvenlyDiscretizedFunc refMFD, double mMax) {
			return loadRateModel(region, classification, trt, TYPE).buildUpper(refMFD, mMax);
		}

		@Override
		public RateRecord getRateRecord(NSHM27_SeismicityRegions region, NSHM27_SeisClassificationMethod classification, TectonicRegionType trt) {
			return loadRateModel(region, classification, trt, TYPE).getUpperRecord();
		}
	},
	AVERAGE("Average Seismicity Rate", "Average", 0d) {

		@Override
		public IncrementalMagFreqDist build(NSHM27_SeismicityRegions region, NSHM27_SeisClassificationMethod classification, TectonicRegionType trt, EvenlyDiscretizedFunc refMFD, double mMax) {
			IncrementalMagFreqDist ret = null;
			double weightSum = 0d;
			for (NSHM27_SeisRateModelBranch seis : values()) {
				if (seis == this || seis.weight == 0d)
					continue;
				weightSum += seis.weight;
				IncrementalMagFreqDist mfd = seis.build(region, classification, trt, refMFD, mMax);
				if (ret == null)
					ret = new IncrementalMagFreqDist(mfd.getMinX(), mfd.getMaxX(), mfd.size());
				else
					Preconditions.checkState(mfd.size() == ret.size());
				for (int i=0; i<ret.size(); i++)
					ret.add(i, mfd.getY(i)*seis.weight);
			}
			if ((float)weightSum != 1f)
				ret.scale(1d/weightSum);
			return ret;
		}

		@Override
		public RateRecord getRateRecord(NSHM27_SeismicityRegions region, NSHM27_SeisClassificationMethod classification, TectonicRegionType trt) {
			return PREFFERRED.getRateRecord(region, classification, trt);
		}
		
	};
	
	public static double getPlotMmax(TectonicRegionType trt) {
		return switch (trt) {
		case ACTIVE_SHALLOW:
//			yield 7.6;
			yield 8d;
		case SUBDUCTION_INTERFACE:
			yield 9d;
		case SUBDUCTION_SLAB:
			yield 8d;
		default:
			throw new IllegalArgumentException("Unexpected value: " + trt);
		};
	}
	
	public static final String getRateModelDate(NSHM27_SeismicityRegions region, NSHM27_SeisClassificationMethod classification) {
		return "2026_07_10-"+classification.name().toLowerCase();
//		return switch (region) {
//			case AMSAM:
//				yield "2026_02_27-v1";
//			case GNMI:
//				yield "2026_02_27-v1";
//			default:
//				throw new IllegalArgumentException("Unexpected region: "+region);
//		};
	}
	
	public static final String getRateModelPath(NSHM27_SeismicityRegions region, NSHM27_SeisClassificationMethod classification) {
		String date = getRateModelDate(region, classification);
		return switch (region) {
			case AMSAM:
				yield "/data/erf/nshm27/amsam/seismicity/rates/"+date+"/";
			case GNMI:
				yield "/data/erf/nshm27/gnmi/seismicity/rates/"+date+"/";
			default:
				throw new IllegalArgumentException("Unexpected region: "+region);
		};
	}
	
	public static final String getRateModelCSVName(TectonicRegionType trt) {
		return switch (trt) {
		case ACTIVE_SHALLOW:
			yield "CRUSTAL.csv";
		case SUBDUCTION_INTERFACE:
			yield "INTERFACE.csv";
		case SUBDUCTION_SLAB:
			yield "INTRASLAB.csv";
		default:
			throw new IllegalArgumentException("Unexpected TRT: "+trt);
		};
	}
	
	public static RateType TYPE = RateType.M1_TO_MMAX;
	
	/*
	 * If changed, don't forget to update:
	 * WEIGHTS ABOVE
	 * High/Low labels ABOVE
	 */
	private static final UncertaintyBoundType BOUND_TYPE = UncertaintyBoundType.CONF_95;
	
	private String name;
	private String shortName;
	private double weight;

	private static Table<NSHM27_SeismicityRegions, TectonicRegionType, Map<NSHM27_SeisClassificationMethod, SeismicityRateModel>> rateModelsCache;

	private NSHM27_SeisRateModelBranch(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
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
		return getShortName();
	}
	
	public static SeismicityRateModel loadRateModel(NSHM27_SeismicityRegions region,
			NSHM27_SeisClassificationMethod classification, TectonicRegionType trt) {
		return loadRateModel(region, classification, trt, TYPE);
	}
	
	public synchronized static SeismicityRateModel loadRateModel(NSHM27_SeismicityRegions region,
			NSHM27_SeisClassificationMethod classification, TectonicRegionType trt, RateType type) {
		if (rateModelsCache == null)
			rateModelsCache = HashBasedTable.create();
		if (type == TYPE) {
			Map<NSHM27_SeisClassificationMethod, SeismicityRateModel> rateModels = rateModelsCache.get(region, trt);
			if (rateModels == null) {
				rateModels = new EnumMap<>(NSHM27_SeisClassificationMethod.class);
				rateModelsCache.put(region, trt, rateModels);
			}
			SeismicityRateModel rateModel = rateModels.get(classification);
			if (rateModel != null)
				return rateModel;
			try {
				rateModel = new SeismicityRateModel(loadCSV(region, classification, trt), type, BOUND_TYPE);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			rateModels.put(classification, rateModel);
			return rateModel;
		}
		try {
			return new SeismicityRateModel(loadCSV(region, classification, trt), type, BOUND_TYPE);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	private synchronized static CSVFile<String> loadCSV(NSHM27_SeismicityRegions region,
			NSHM27_SeisClassificationMethod classification, TectonicRegionType trt) throws IOException {
		String resourceName = getRateModelPath(region, classification)+getRateModelCSVName(trt);
		
		InputStream stream = NSHM27_SeisRateModelBranch.class.getResourceAsStream(resourceName);
		Preconditions.checkNotNull(stream, "Error loading stream for '%s'", resourceName);
		CSVFile<String> csv = CSVFile.readStream(stream, false);
		stream.close();
		return csv;
	}
}
