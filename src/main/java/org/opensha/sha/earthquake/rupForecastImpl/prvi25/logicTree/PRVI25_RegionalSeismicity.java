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
public enum PRVI25_RegionalSeismicity implements LogicTreeNode {
	LOW("Lower Seismicity Bound (p2.5)", "Low", 0.025, 0.13d),
	PREFFERRED("Preffered Seismicity Rate", "Preferred", Double.NaN, 0.74d),
	HIGH("Upper Seismicity Bound (p97.5)", "High", 0.975, 0.13d),
	AVERAGE("Average Seismicity Rate", "Average", Double.NaN, 0d) {

		@Override
		public IncrementalMagFreqDist build(PRVI25_SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax)
				throws IOException {
			IncrementalMagFreqDist ret = null;
			double weightSum = 0d;
			for (PRVI25_RegionalSeismicity seis : values()) {
				if (seis == this || seis.weight == 0d)
					continue;
				weightSum += seis.weight;
				IncrementalMagFreqDist mfd = seis.build(region, refMFD, mMax);
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
	
	public static String RATE_DATE = "2024_12_11";
	private static final String RATES_PATH_PREFIX = "/data/erf/prvi25/seismicity/rates/";
	public static RateType TYPE = RateType.EXACT;
	
	/*
	 * If changed, don't forget to update:
	 * WEIGHTS ABOVE
	 * QUANTILES ABOVE
	 * High/Low labels ABOVE
	 */
	private static final UncertaintyBoundType BOUND_TYPE = UncertaintyBoundType.CONF_95;
	
	private static Map<String, CSVFile<String>> regionRateCSVs;
	
	private static Table<String, RateType, List<? extends RateRecord>> regionRates;
	
	public synchronized static void clearCache() {
		regionRateCSVs = null;
		regionRates = null;
	}
	
	public synchronized static List<? extends RateRecord> loadRates(PRVI25_SeismicityRegions region, RateType type) throws IOException {
		return loadRates(region.name(), type);
	}
	
	private synchronized static List<? extends RateRecord> loadRates(String regionName, RateType type) throws IOException {
		if (regionRates == null)
			regionRates = HashBasedTable.create();
		List<? extends RateRecord> rates = regionRates.get(regionName, type);
		if (rates != null)
			return rates;
		CSVFile<String> csv = loadCSV(regionName);
		rates = SeismicityRateFileLoader.loadRecords(csv, type);
		regionRates.put(regionName, type, rates);
		return rates;
	}
	
	private synchronized static CSVFile<String> loadCSV(String regionName) throws IOException {
		if (regionRateCSVs == null)
			regionRateCSVs = new HashMap<>();
		CSVFile<String> csv = regionRateCSVs.get(regionName);
		if (csv != null)
			return csv;
		String resourceaName = RATES_PATH_PREFIX+RATE_DATE+"/"+regionName+".csv";
		InputStream stream = PRVI25_RegionalSeismicity.class.getResourceAsStream(resourceaName);
		Preconditions.checkNotNull(stream, "Error loading stream for '%s'", resourceaName);
		csv = CSVFile.readStream(stream, false);
		regionRateCSVs.put(regionName, csv);
		return csv;
	}
	
	/**
	 * If b various on outlier branches, they can cross over such that that low > pref and high < pref for really small
	 * magnitudes. This sets low/high to the pref values in that case.
	 * @param gr
	 * @param lower
	 * @param region
	 * @param mMax
	 * @return
	 */
	private static IncrementalMagFreqDist adjustForCrossover(IncrementalMagFreqDist branchIncr,
			IncrementalMagFreqDist prefIncr, boolean lower) {
		Preconditions.checkState(prefIncr.size() == branchIncr.size());
		Preconditions.checkState((float)prefIncr.getMinX() == (float)branchIncr.getMinX());
		IncrementalMagFreqDist ret = new IncrementalMagFreqDist(branchIncr.getMinX(), branchIncr.getMaxX(), branchIncr.size());
		
		boolean anyOutside = false;
		for (int i=0; i<branchIncr.size(); i++) {
			double prefY = prefIncr.getY(i);
			double myY = branchIncr.getY(i);
			if ((lower && myY > prefY) || (!lower && myY < prefY)) {
				anyOutside = true;
				ret.set(i, prefY);
			} else {
				ret.set(i, myY);
			}
		}
		
		if (anyOutside) {
			ret.setName(branchIncr.getName());
			return ret;
		}
		return branchIncr;
	}
	
	private String name;
	private String shortName;
	private double quantile;
	private double weight;

	private PRVI25_RegionalSeismicity(String name, String shortName, double qwuantile, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.quantile = qwuantile;
		this.weight = weight;
	}
	
	public IncrementalMagFreqDist build(PRVI25_SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax)
			throws IOException {
		List<? extends RateRecord> records = loadRates(region, TYPE);
		RateRecord record = Double.isNaN(quantile) ?
				SeismicityRateFileLoader.locateMean(records) : SeismicityRateFileLoader.locateQuantile(records, quantile);
		IncrementalMagFreqDist mfd = SeismicityRateFileLoader.buildIncrementalMFD(record, refMFD, mMax);
		
		if (TYPE != RateType.EXACT && this != PREFFERRED)
			mfd = adjustForCrossover(mfd, PREFFERRED.build(region, refMFD, mMax), this == LOW);
		
		String name = "Observed";
		if (this != PREFFERRED)
			name += " ("+this.shortName+")";
		name += ", N"+oDF.format(record.M1)+"="+oDF.format(record.rateAboveM1);
		
		mfd.setName(name);
		
		return mfd;
	};
	
	private static final DecimalFormat oDF = new DecimalFormat("0.##");
	
	public static UncertainBoundedIncrMagFreqDist getBounded(PRVI25_SeismicityRegions region,
			EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
		IncrementalMagFreqDist upper = HIGH.build(region, refMFD, mMax);
		IncrementalMagFreqDist lower = LOW.build(region, refMFD, mMax);
		IncrementalMagFreqDist pref = PREFFERRED.build(region, refMFD, mMax);
		
		double M1 = SeismicityRateFileLoader.locateMean(loadRates(region, TYPE)).M1;
		
		UncertainBoundedIncrMagFreqDist bounded = new UncertainBoundedIncrMagFreqDist(pref, lower, upper, BOUND_TYPE);
		bounded.setName(pref.getName());
		bounded.setBoundName(getBoundName(lower, upper, M1));
		
		return bounded;
	}
	
	static String getBoundName(IncrementalMagFreqDist lower, IncrementalMagFreqDist upper, double M1) {
		String boundName = BOUND_TYPE.toString();
		double lowerN = lower.getCumRateDistWithOffset().getInterpolatedY(M1);
		double upperN = upper.getCumRateDistWithOffset().getInterpolatedY(M1);
		boundName += ": N"+oDF.format(M1)+"∈["+oDF.format(lowerN)+","+oDF.format(upperN)+"]";
		return boundName;
	}
	
	public static UncertainBoundedIncrMagFreqDist getRescaled(PRVI25_SeismicityRegions seisRegion,
			double fractN, EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
		IncrementalMagFreqDist pref = PREFFERRED.build(seisRegion, refMFD, mMax);
		pref.scale(fractN);
		IncrementalMagFreqDist uppper = HIGH.build(seisRegion, refMFD, mMax);
		uppper.scale(fractN);
		IncrementalMagFreqDist lower = LOW.build(seisRegion, refMFD, mMax);
		lower.scale(fractN);
		
		// now further scale bounds to account for less data
		for (int i=0; i<pref.size(); i++) {
			double prefVal = pref.getY(i);
			if (prefVal > 0d) {
				double origUpper = uppper.getY(i);
				double origLower = lower.getY(i);
				
				double upperRatio = origUpper/prefVal;
				double lowerRatio = origLower/prefVal;
				
				upperRatio *= 1/Math.sqrt(fractN);
				lowerRatio /= 1/Math.sqrt(fractN);
				uppper.set(i, prefVal*upperRatio);
				lower.set(i, prefVal*lowerRatio);
			}
		}
		UncertainBoundedIncrMagFreqDist rescaled = new UncertainBoundedIncrMagFreqDist(pref, lower, uppper, BOUND_TYPE);
		double M1 = SeismicityRateFileLoader.locateMean(loadRates(seisRegion.name(), TYPE)).M1;
		
		double prefN = rescaled.getCumRateDistWithOffset().getInterpolatedY(M1);
		String name = "Remmapped Observed [pdfFractN="+oDF.format(fractN)+", N"+oDF.format(M1)+"="+oDF.format(prefN)+"]";
		
		rescaled.setName(name);
		rescaled.setBoundName(getBoundName(rescaled.getLower(), rescaled.getUpper(), M1));
		return rescaled;
	}
	
	public static UncertainBoundedIncrMagFreqDist getRemapped(Region region, PRVI25_SeismicityRegions seisRegion,
			PRVI25_DeclusteringAlgorithms declustering, PRVI25_SeisSmoothingAlgorithms smoothing,
			EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
		GriddedGeoDataSet pdf = smoothing.loadXYZ(seisRegion, declustering);
		
		double fractN = 0d;
		for (int i=0; i<pdf.size(); i++)
			if (region.contains(pdf.getLocation(i)))
				fractN += pdf.get(i);
		
		return getRescaled(seisRegion, fractN, refMFD, mMax);
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
		for (PRVI25_SeismicityRegions seisReg : PRVI25_SeismicityRegions.values()) {
			IncrementalMagFreqDist pref = PREFFERRED.build(seisReg, refMFD, mMax);
			IncrementalMagFreqDist low = LOW.build(seisReg, refMFD, mMax);
			IncrementalMagFreqDist high = HIGH.build(seisReg, refMFD, mMax);
			
			System.out.println(seisReg);
			for (int i=0; i<refMFD.size(); i++) {
				if (refMFD.getX(i) > refMFD.getClosestXIndex(mMax))
					break;
				System.out.println((float)refMFD.getX(i)+"\t"+(float)pref.getY(i)+"\t["+(float)low.getY(i)+","+(float)high.getY(i)+"]");
			}
			System.out.println();
			
		}
	}

}
