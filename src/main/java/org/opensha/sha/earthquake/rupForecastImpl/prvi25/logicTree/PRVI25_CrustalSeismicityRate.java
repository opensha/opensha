package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
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
		public IncrementalMagFreqDist build(PRVI25_SeismicityRateEpoch epoch, EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
			return loadRateModel(epoch, TYPE).buildLower(refMFD, mMax);
		}
	},
	PREFFERRED("Preffered Seismicity Rate", "Preferred", 0.74d) {
		@Override
		public IncrementalMagFreqDist build(PRVI25_SeismicityRateEpoch epoch, EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
			return loadRateModel(epoch, TYPE).buildPreferred(refMFD, mMax);
		}
	},
	HIGH("Upper Seismicity Bound (p97.5)", "High", 0.13d) {
		@Override
		public IncrementalMagFreqDist build(PRVI25_SeismicityRateEpoch epoch, EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
			return loadRateModel(epoch, TYPE).buildUpper(refMFD, mMax);
		}
	},
	AVERAGE("Average Seismicity Rate", "Average", 0d) {

		@Override
		public IncrementalMagFreqDist build(PRVI25_SeismicityRateEpoch epoch, EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
			IncrementalMagFreqDist ret = null;
			double weightSum = 0d;
			for (PRVI25_CrustalSeismicityRate seis : values()) {
				if (seis == this || seis.weight == 0d)
					continue;
				weightSum += seis.weight;
				IncrementalMagFreqDist mfd = seis.build(epoch, refMFD, mMax);
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
	
	public static String RATE_DATE = "2025_08_13";
	private static final String RATES_PATH_PREFIX = "/data/erf/prvi25/seismicity/rates/";
	public static RateType TYPE = RateType.M1_TO_MMAX;
	
	public static String getDirectRateFileName(PRVI25_SeismicityRegions seisReg, PRVI25_SeismicityRateEpoch epoch) {
		String yearStr;
		String vStr;
		switch (epoch) {
		case FULL:
			yearStr = "1900";
			vStr = "-v9";
			break;
		case RECENT:
			yearStr = "1973";
			vStr = "-v10";
			break;

		default:
			throw new IllegalStateException("Can't load direct rates for epoch: "+epoch);
		}
		if (seisReg == null)
			return "directrates-PRVI Union-Full-"+yearStr+"-2024"+vStr+".csv";
		else
			return "directrates-PRVI "+seisReg.getShortName()+"-Prob-"+yearStr+"-2024"+vStr+".csv";
	}
	
	/*
	 * If changed, don't forget to update:
	 * WEIGHTS ABOVE
	 * High/Low labels ABOVE
	 */
	private static final UncertaintyBoundType BOUND_TYPE = UncertaintyBoundType.CONF_95;
	
	private static Table<PRVI25_SeismicityRateEpoch, RateType, SeismicityRateModel> rateModels;
	private static Map<PRVI25_SeismicityRateEpoch, CSVFile<String>> csvs;
	
	public synchronized static void clearCache() {
		rateModels = null;
		csvs = null;
	}
	
	public abstract IncrementalMagFreqDist build(PRVI25_SeismicityRateEpoch epoch, EvenlyDiscretizedFunc refMFD, double mMax) throws IOException;
	
	public static List<? extends RateRecord> loadRates(PRVI25_SeismicityRateEpoch epoch, RateType type) throws IOException {
		if (epoch == PRVI25_SeismicityRateEpoch.RECENT_SCALED)
			return getScaledToFull(loadRates(PRVI25_SeismicityRateEpoch.RECENT, type));
		CSVFile<String> csv = loadCSV(epoch);
		return SeismicityRateFileLoader.loadRecords(csv, type);
	}
	
	public static SeismicityRateModel loadRateModel(PRVI25_SeismicityRateEpoch epoch) throws IOException {
		return loadRateModel(epoch, TYPE);
	}
	
	public synchronized static SeismicityRateModel loadRateModel(PRVI25_SeismicityRateEpoch epoch, RateType type) throws IOException {
		if (rateModels == null)
			rateModels = HashBasedTable.create();
		SeismicityRateModel rateModel = rateModels.get(epoch, type);
		if (rateModel != null)
			return rateModel;
		if (epoch == PRVI25_SeismicityRateEpoch.RECENT_SCALED) {
			rateModel = getScaledToFull(loadRateModel(PRVI25_SeismicityRateEpoch.RECENT, type), type);
		} else {
			CSVFile<String> csv = loadCSV(epoch);
			rateModel = new SeismicityRateModel(csv, type, BOUND_TYPE);
		}
		rateModels.put(epoch, type, rateModel);
		return rateModel;
	}
	
	private static Double RECENT_TO_FULL_SCALAR = null;
	
	public static SeismicityRateModel getScaledToFull(SeismicityRateModel recentModel, RateType type) throws IOException {
		checkLoadRecentToFullScalar();
		
		return new SeismicityRateModel(recentModel.getMeanRecord().getScaled(RECENT_TO_FULL_SCALAR),
				recentModel.getLowerRecord().getScaled(RECENT_TO_FULL_SCALAR),
				recentModel.getUpperRecord().getScaled(RECENT_TO_FULL_SCALAR), BOUND_TYPE);
	}
	
	public static List<? extends RateRecord> getScaledToFull(List<? extends RateRecord> rates) throws IOException {
		checkLoadRecentToFullScalar();
		List<RateRecord> ret = new ArrayList<>();
		for (RateRecord rec : rates)
			ret.add(rec.getScaled(RECENT_TO_FULL_SCALAR));
		return ret;
	}
	
	private synchronized static void checkLoadRecentToFullScalar() throws IOException {
		if (RECENT_TO_FULL_SCALAR == null) {
			double sumRecent = 0d;
			double sumFull = 0d;
			String commonPrefix = RATES_PATH_PREFIX+RATE_DATE+"/";
			for (PRVI25_SeismicityRegions region : PRVI25_SeismicityRegions.values()) {
				String recentCSVname = commonPrefix+PRVI25_SeismicityRateEpoch.RECENT.getRateSubDirName()+"/"+region.name()+".csv";
				InputStream recentStream = PRVI25_CrustalSeismicityRate.class.getResourceAsStream(recentCSVname);
				Preconditions.checkNotNull(recentStream, "Error loading stream for '%s'", recentCSVname);
				CSVFile<String> recentCSV = CSVFile.readStream(recentStream, false);
				recentStream.close();
				
				String fullCSVname = commonPrefix+PRVI25_SeismicityRateEpoch.FULL.getRateSubDirName()+"/"+region.name()+".csv";
				InputStream fullStream = PRVI25_CrustalSeismicityRate.class.getResourceAsStream(fullCSVname);
				Preconditions.checkNotNull(fullStream, "Error loading stream for '%s'", fullCSVname);
				CSVFile<String> fullCSV = CSVFile.readStream(fullStream, false);
				fullStream.close();
				
				SeismicityRateModel recentRegionalModel = new SeismicityRateModel(recentCSV, TYPE, BOUND_TYPE);
				SeismicityRateModel fullRegionalModel = new SeismicityRateModel(fullCSV, TYPE, BOUND_TYPE);
				
				RateRecord recentMean = recentRegionalModel.getMeanRecord();
				RateRecord fullMean = fullRegionalModel.getMeanRecord();
				
				Preconditions.checkState(recentMean.M1 == fullMean.M1);
				
				sumRecent += recentMean.rateAboveM1;
				sumFull += fullMean.rateAboveM1;
			}
			
			double rateScalar = sumFull/sumRecent;
			System.out.println("Recent-to-full seismicity rate scalar:\t"
					+(float)sumFull+" / "+(float)sumRecent+" = "+(float)rateScalar);
			RECENT_TO_FULL_SCALAR = rateScalar;
		}
	}
	
	private synchronized static CSVFile<String> loadCSV(PRVI25_SeismicityRateEpoch epoch) throws IOException {
		if (csvs == null) {
			csvs = new HashMap<>();
		}
		CSVFile<String> csv = csvs.get(epoch);
		if (csv == null) {
			String resourceName = RATES_PATH_PREFIX+RATE_DATE+"/"+epoch.getRateSubDirName()+"/"+PRVI25_SeismicityRegions.CRUSTAL.name()+".csv";
			InputStream stream = PRVI25_CrustalSeismicityRate.class.getResourceAsStream(resourceName);
			Preconditions.checkNotNull(stream, "Error loading stream for '%s'", resourceName);
			csv = CSVFile.readStream(stream, false);
			stream.close();
			csvs.put(epoch, csv);
		}
		
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
		
		for (PRVI25_SeismicityRateEpoch epoch : PRVI25_SeismicityRateEpoch.values()) {
			System.out.println("Epoch: "+epoch);
			IncrementalMagFreqDist pref = PREFFERRED.build(epoch, refMFD, mMax);
			IncrementalMagFreqDist low = LOW.build(epoch, refMFD, mMax);
			IncrementalMagFreqDist high = HIGH.build(epoch, refMFD, mMax);
			
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
