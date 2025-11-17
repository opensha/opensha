package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
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
public enum PRVI25_SubductionCaribbeanSeismicityRate implements LogicTreeNode {
	LOW("Lower Seismicity Bound (p2.5)", "Low", 0.13d) {
		@Override
		public IncrementalMagFreqDist build(PRVI25_SeismicityRateEpoch epoch, EvenlyDiscretizedFunc refMFD, double mMax, double magCorner, boolean slab) throws IOException {
			return loadRateModel(epoch, TYPE, slab).buildLower(refMFD, mMax, magCorner);
		}
	},
	PREFFERRED("Preffered Seismicity Rate", "Preferred", 0.74d) {
		@Override
		public IncrementalMagFreqDist build(PRVI25_SeismicityRateEpoch epoch, EvenlyDiscretizedFunc refMFD, double mMax, double magCorner, boolean slab) throws IOException {
			return loadRateModel(epoch, TYPE, slab).buildPreferred(refMFD, mMax, magCorner);
		}
	},
	HIGH("Upper Seismicity Bound (p97.5)", "High", 0.13d) {
		@Override
		public IncrementalMagFreqDist build(PRVI25_SeismicityRateEpoch epoch, EvenlyDiscretizedFunc refMFD, double mMax, double magCorner, boolean slab) throws IOException {
			return loadRateModel(epoch, TYPE, slab).buildUpper(refMFD, mMax, magCorner);
		}
	},
	AVERAGE("Average Seismicity Rate", "Average", 0d) {

		@Override
		public IncrementalMagFreqDist build(PRVI25_SeismicityRateEpoch epoch, EvenlyDiscretizedFunc refMFD, double mMax, double magCorner, boolean slab) throws IOException {
			IncrementalMagFreqDist ret = null;
			double weightSum = 0d;
			for (PRVI25_SubductionCaribbeanSeismicityRate seis : values()) {
				if (seis == this || seis.weight == 0d)
					continue;
				weightSum += seis.weight;
				IncrementalMagFreqDist mfd = seis.build(epoch, refMFD, mMax, magCorner, slab);
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
	
	public static String RATE_DATE = PRVI25_CrustalSeismicityRate.RATE_DATE;
	private static final String RATES_PATH_PREFIX = "/data/erf/prvi25/seismicity/rates/";
	public static RateType TYPE = RateType.M1_TO_MMAX;
	
	/*
	 * If changed, don't forget to update:
	 * WEIGHTS ABOVE
	 * High/Low labels ABOVE
	 */
	private static final UncertaintyBoundType BOUND_TYPE = UncertaintyBoundType.CONF_95;
	
	private static Table<PRVI25_SeismicityRateEpoch, RateType, SeismicityRateModel> slabRateModels;
	private static Table<PRVI25_SeismicityRateEpoch, RateType, SeismicityRateModel> interfaceRateModels;
	private static Map<PRVI25_SeismicityRateEpoch, CSVFile<String>> slabCSVs;
	private static Map<PRVI25_SeismicityRateEpoch, CSVFile<String>> interfaceCSVs;
	
	public synchronized static void clearCache() {
		slabRateModels = null;
		slabCSVs = null;
		interfaceRateModels = null;
		interfaceCSVs = null;
	}
	
	public abstract IncrementalMagFreqDist build(PRVI25_SeismicityRateEpoch epoch, EvenlyDiscretizedFunc refMFD, double mMax, double magCorner, boolean slab) throws IOException;
	
	public static SeismicityRateModel loadRateModel(PRVI25_SeismicityRateEpoch epoch, boolean slab) throws IOException {
		return loadRateModel(epoch, TYPE, slab);
	}
	
	public static List<? extends RateRecord> loadRates(PRVI25_SeismicityRateEpoch epoch, RateType type, boolean slab) throws IOException {
		if (epoch == PRVI25_SeismicityRateEpoch.RECENT_SCALED)
			return PRVI25_CrustalSeismicityRate.getScaledToFull(loadRates(PRVI25_SeismicityRateEpoch.RECENT, type, slab));
		CSVFile<String> csv = loadCSV(epoch, slab);
		return SeismicityRateFileLoader.loadRecords(csv, type);
	}
	
	public synchronized static SeismicityRateModel loadRateModel(PRVI25_SeismicityRateEpoch epoch, RateType type, boolean slab) throws IOException {
		SeismicityRateModel rateModel;
		if (slab) {
			if (slabRateModels == null)
				slabRateModels = HashBasedTable.create();
			rateModel = slabRateModels.get(epoch, type);
		} else {
			if (interfaceRateModels == null)
				interfaceRateModels = HashBasedTable.create();
			rateModel = interfaceRateModels.get(epoch, type);
		}
		if (rateModel != null)
			return rateModel;
		if (epoch == PRVI25_SeismicityRateEpoch.RECENT_SCALED) {
			rateModel = PRVI25_CrustalSeismicityRate.getScaledToFull(loadRateModel(PRVI25_SeismicityRateEpoch.RECENT, type, slab), type);
		} else {
			CSVFile<String> csv = loadCSV(epoch, slab);
			rateModel = new SeismicityRateModel(csv, type, BOUND_TYPE);
		}
		if (slab)
			slabRateModels.put(epoch, type, rateModel);
		else
			interfaceRateModels.put(epoch, type, rateModel);
		return rateModel;
	}
	
	private synchronized static CSVFile<String> loadCSV(PRVI25_SeismicityRateEpoch epoch, boolean slab) throws IOException {
		CSVFile<String> csv;
		if (slab) {
			if (slabCSVs == null)
				slabCSVs = new HashMap<>();
			csv = slabCSVs.get(epoch);
		} else {
			if (interfaceCSVs == null)
				interfaceCSVs = new HashMap<>();
			csv = interfaceCSVs.get(epoch);
		}
		if (csv != null)
			return csv;
		String resourceName = RATES_PATH_PREFIX+RATE_DATE+"/"+epoch.getRateSubDirName()+"/";
		if (slab)
			resourceName += PRVI25_SeismicityRegions.CAR_INTRASLAB.name();
		else
			resourceName += PRVI25_SeismicityRegions.CAR_INTERFACE.name();
		resourceName += ".csv";
		InputStream stream = PRVI25_SubductionCaribbeanSeismicityRate.class.getResourceAsStream(resourceName);
		Preconditions.checkNotNull(stream, "Error loading stream for '%s'", resourceName);
		csv = CSVFile.readStream(stream, false);
		stream.close();
		if (slab)
			slabCSVs.put(epoch, csv);
		else
			interfaceCSVs.put(epoch, csv);
		return csv;
	}
	
	private String name;
	private String shortName;
	private double weight;

	private PRVI25_SubductionCaribbeanSeismicityRate(String name, String shortName, double weight) {
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
		for (boolean slab : new boolean[] {false,true}) {
			if (slab)
				System.out.println("SLAB");
			else
				System.out.println("INTERFACE");
			double mMax = 7.95;
			double magCorner = Double.NaN;
			EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(5.01, mMax);
			
			for (PRVI25_SeismicityRateEpoch epoch : PRVI25_SeismicityRateEpoch.values()) {
				System.out.println("Epoch: "+epoch);
				IncrementalMagFreqDist pref = PREFFERRED.build(epoch, refMFD, mMax, magCorner, true);
				IncrementalMagFreqDist low = LOW.build(epoch, refMFD, mMax, magCorner, true);
				IncrementalMagFreqDist high = HIGH.build(epoch, refMFD, mMax, magCorner, true);
				
				for (int i=0; i<refMFD.size(); i++) {
					float x = (float)refMFD.getX(i);
					if (x > (float)refMFD.getClosestXIndex(mMax))
						break;
					if (x == 5.05f || x == 6.05f || x == 7.05f)
						System.out.println(x+"\t"+(float)pref.getY(i)+"\t["+(float)low.getY(i)+","+(float)high.getY(i)+"]");
				}
				System.out.println();
			}
		}
	}

}
