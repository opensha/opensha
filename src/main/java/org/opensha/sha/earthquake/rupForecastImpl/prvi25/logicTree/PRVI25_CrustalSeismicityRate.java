package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded.PRVI25_GridSourceBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded.SeismicityRateFileLoader;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded.SeismicityRateFileLoader.RateRecord;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded.SeismicityRateFileLoader.RateType;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded.SeismicityRateModel;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader.PRVI25_SeismicityRegions;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@DoesNotAffect(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME)
@Affects(MFDGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum PRVI25_CrustalSeismicityRate implements LogicTreeNode {
	LOW("Lower Seismicity Bound (p2.5)", "Low", 0.13d) {
		@Override
		public IncrementalMagFreqDist build(EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
			return loadRateModel(TYPE).buildLower(refMFD, mMax);
		}
	},
	PREFFERRED("Preffered Seismicity Rate", "Preferred", 0.74d) {
		@Override
		public IncrementalMagFreqDist build(EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
			return loadRateModel(TYPE).buildPreferred(refMFD, mMax);
		}
	},
	HIGH("Upper Seismicity Bound (p97.5)", "High", 0.13d) {
		@Override
		public IncrementalMagFreqDist build(EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
			return loadRateModel(TYPE).buildUpper(refMFD, mMax);
		}
	},
	AVERAGE("Average Seismicity Rate", "Average", 0d) {

		@Override
		public IncrementalMagFreqDist build(EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
			IncrementalMagFreqDist ret = null;
			double weightSum = 0d;
			for (PRVI25_CrustalSeismicityRate seis : values()) {
				if (seis == this || seis.weight == 0d)
					continue;
				weightSum += seis.weight;
				IncrementalMagFreqDist mfd = seis.build(refMFD, mMax);
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
	
	public static String RATE_DATE = "2025_03_26";
	private static final String RATES_PATH_PREFIX = "/data/erf/prvi25/seismicity/rates/";
	public static RateType TYPE = RateType.M1_TO_MMAX;
	
	/*
	 * If changed, don't forget to update:
	 * WEIGHTS ABOVE
	 * High/Low labels ABOVE
	 */
	private static final UncertaintyBoundType BOUND_TYPE = UncertaintyBoundType.CONF_95;
	
	private static Map<RateType, SeismicityRateModel> rateModels;
	private static CSVFile<String> csv;
	
	public synchronized static void clearCache() {
		rateModels = null;
	}
	
	public abstract IncrementalMagFreqDist build(EvenlyDiscretizedFunc refMFD, double mMax) throws IOException;
	
	public static List<? extends RateRecord> loadRates(RateType type) throws IOException {
		CSVFile<String> csv = loadCSV();
		return SeismicityRateFileLoader.loadRecords(csv, type);
	}
	
	public static SeismicityRateModel loadRateModel() throws IOException {
		return loadRateModel(TYPE);
	}
	
	public synchronized static SeismicityRateModel loadRateModel(RateType type) throws IOException {
		if (rateModels == null)
			rateModels = new HashMap<>();
		SeismicityRateModel rateModel = rateModels.get(type);
		if (rateModel != null)
			return rateModel;
		CSVFile<String> csv = loadCSV();
		rateModel = new SeismicityRateModel(csv, type, BOUND_TYPE);
		rateModels.put(type, rateModel);
		return rateModel;
	}
	
	private synchronized static CSVFile<String> loadCSV() throws IOException {
		if (csv != null)
			return csv;
		String resourceaName = RATES_PATH_PREFIX+RATE_DATE+"/"+PRVI25_SeismicityRegions.CRUSTAL.name()+".csv";
		InputStream stream = PRVI25_CrustalSeismicityRate.class.getResourceAsStream(resourceaName);
		Preconditions.checkNotNull(stream, "Error loading stream for '%s'", resourceaName);
		csv = CSVFile.readStream(stream, false);
		return csv;
	}
	
	
	
	private String name;
	private String shortName;
	private double weight;

	private PRVI25_CrustalSeismicityRate(String name, String shortName, double weight) {
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
	
	public static void main(String[] args) throws IOException {
		double mMax = 7.6;
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(PRVI25_GridSourceBuilder.OVERALL_MMIN, mMax);
		IncrementalMagFreqDist pref = PREFFERRED.build(refMFD, mMax);
		IncrementalMagFreqDist low = LOW.build(refMFD, mMax);
		IncrementalMagFreqDist high = HIGH.build(refMFD, mMax);
		
		for (int i=0; i<refMFD.size(); i++) {
			if (refMFD.getX(i) > refMFD.getClosestXIndex(mMax))
				break;
			System.out.println((float)refMFD.getX(i)+"\t"+(float)pref.getY(i)+"\t["+(float)low.getY(i)+","+(float)high.getY(i)+"]");
		}
		System.out.println();
	}

}
