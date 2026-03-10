package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.io.IOException;
import java.io.InputStream;
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
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded.SeismicityRateFileLoader.RateType;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded.SeismicityRateModel;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

//TODO: this might end up affecting slip rates, setting to affects
@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
//TODO: this might end up affecting slip rates, setting to affects
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM26_SeisRateModelBranch implements LogicTreeNode, NSHM26_SeisRateModel {
	LOW("Lower Seismicity Bound (p2.5)", "Low", 0.13d) {
		@Override
		public IncrementalMagFreqDist build(NSHM26_SeismicityRegions region, TectonicRegionType trt, EvenlyDiscretizedFunc refMFD, double mMax) {
			return loadRateModel(region, trt, TYPE).buildLower(refMFD, mMax);
		}
	},
	PREFFERRED("Preffered Seismicity Rate", "Preferred", 0.74d) {
		@Override
		public IncrementalMagFreqDist build(NSHM26_SeismicityRegions region, TectonicRegionType trt, EvenlyDiscretizedFunc refMFD, double mMax) {
			return loadRateModel(region, trt, TYPE).buildPreferred(refMFD, mMax);
		}
	},
	HIGH("Upper Seismicity Bound (p97.5)", "High", 0.13d) {
		@Override
		public IncrementalMagFreqDist build(NSHM26_SeismicityRegions region, TectonicRegionType trt, EvenlyDiscretizedFunc refMFD, double mMax) {
			return loadRateModel(region, trt, TYPE).buildUpper(refMFD, mMax);
		}
	},
	AVERAGE("Average Seismicity Rate", "Average", 0d) {

		@Override
		public IncrementalMagFreqDist build(NSHM26_SeismicityRegions region, TectonicRegionType trt, EvenlyDiscretizedFunc refMFD, double mMax) {
			IncrementalMagFreqDist ret = null;
			double weightSum = 0d;
			for (NSHM26_SeisRateModelBranch seis : values()) {
				if (seis == this || seis.weight == 0d)
					continue;
				weightSum += seis.weight;
				IncrementalMagFreqDist mfd = seis.build(region, trt, refMFD, mMax);
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
		
	};
	
	private static final Map<NSHM26_SeismicityRegions, String> PATHS = Map.of(
			NSHM26_SeismicityRegions.GNMI, "/data/erf/nshm26/gnmi/seismicity/rates/2026_02_27-v1/",
			NSHM26_SeismicityRegions.AMSAM, "/data/erf/nshm26/amsam/seismicity/rates/2026_02_27-v1/"
			);
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

	private static Table<NSHM26_SeismicityRegions, TectonicRegionType, CSVFile<String>> csvsCache;
	private static Table<NSHM26_SeismicityRegions, TectonicRegionType, SeismicityRateModel> rateModelsCache;

	private NSHM26_SeisRateModelBranch(String name, String shortName, double weight) {
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
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return getShortName();
	}
	
	public static SeismicityRateModel loadRateModel(NSHM26_SeismicityRegions region, TectonicRegionType trt) {
		return loadRateModel(region, trt, TYPE);
	}
	
	public synchronized static SeismicityRateModel loadRateModel(NSHM26_SeismicityRegions region, TectonicRegionType trt, RateType type) {
		if (rateModelsCache == null)
			rateModelsCache = HashBasedTable.create();
		if (type == TYPE) {
			SeismicityRateModel rateModel = rateModelsCache.get(region, trt);
			if (rateModel != null)
				return rateModel;
			try {
				rateModel = new SeismicityRateModel(loadCSV(region, trt), type, BOUND_TYPE);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			rateModelsCache.put(region, trt, rateModel);
			return rateModel;
		}
		try {
			return new SeismicityRateModel(loadCSV(region, trt), type, BOUND_TYPE);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	private synchronized static CSVFile<String> loadCSV(NSHM26_SeismicityRegions region, TectonicRegionType trt) throws IOException {
		if (csvsCache == null)
			csvsCache = HashBasedTable.create();
		CSVFile<String> csv = csvsCache.get(region, trt);
		if (csv != null)
			return csv;
		String resourceName = PATHS.get(region);
		switch (trt) {
		case ACTIVE_SHALLOW: {
			resourceName += "CRUSTAL.csv";
			break;
		}
		case SUBDUCTION_INTERFACE: {
			resourceName += "INTERFACE.csv";
			break;
		}
		case SUBDUCTION_SLAB: {
			resourceName += "INTRASLAB.csv";
			break;
		}
		default:
			throw new IllegalArgumentException("Unexpected value: " + trt);
		}
		
		InputStream stream = NSHM26_SeisRateModelBranch.class.getResourceAsStream(resourceName);
		Preconditions.checkNotNull(stream, "Error loading stream for '%s'", resourceName);
		csv = CSVFile.readStream(stream, false);
		stream.close();
		csvsCache.put(region, trt, csv);
		return csv;
	}
}
