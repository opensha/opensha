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
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.PRVI25_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader.PRVI25_SeismicityRegions;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

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
	LOW("Lower Seismicity Bound (p2.5)", "Low", 0.13d) {
		@Override
		public IncrementalMagFreqDist build(PRVI25_SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax) {
			if (hasRegion(region)) {
				// lower is index 1
				double rate = loadRate(region, 1);
				double b = loadBVal(region, 1);
				return adjustForCrossover(gr(refMFD, mMax, rate, b), true, region, mMax);
			}
			return null;
		}
	},
	PREFFERRED("Preffered Seismicity Rate", "Preferred", 0.74d) {
		@Override
		public IncrementalMagFreqDist build(PRVI25_SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax) {
			if (hasRegion(region)) {
				// preferred is index 0
				double rate = loadRate(region, 0);
				double b = loadBVal(region, 0);
				return gr(refMFD, mMax, rate, b);
			}
			return null;
		}
	},
	HIGH("Upper Seismicity Bound (p97.5)", "High", 0.13d) {
		@Override
		public IncrementalMagFreqDist build(PRVI25_SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax) {
			if (hasRegion(region)) {
				// upper is index 2
				double rate = loadRate(region, 2);
				double b = loadBVal(region, 2);
				return adjustForCrossover(gr(refMFD, mMax, rate, b), false, region, mMax);
			}
			return null;
		}
	};
	
	public static String RATE_FILE_NAME = "rates_2024_09_04.csv";
	private static final String RATES_PATH_PREFIX = "/data/erf/prvi25/seismicity/rates/";
	
	private static Map<String, double[]> ratesMap;
	private static Map<String, double[]> bValsMap;
	
	public synchronized static void clearCache() {
		ratesMap = null;
		bValsMap = null;
	}
	
	private synchronized static void checkLoadRates() {
		if (ratesMap == null) {
			Map<String, double[]> ratesMap = new HashMap<>();
			Map<String, double[]> bValsMap = new HashMap<>();
			
			String resource = RATES_PATH_PREFIX+RATE_FILE_NAME;
			
			System.out.println("Loading spatial seismicity PDF from: "+resource);
			InputStream is = PRVI25_RegionalSeismicity.class.getResourceAsStream(resource);
			Preconditions.checkNotNull(is, "Spatial seismicity PDF not found: %s", resource);
			CSVFile<String> csv;
			try {
				csv = CSVFile.readStream(is, true);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
			for (int row=1; row<csv.getNumRows(); row++) {
				int col = 0;
				String name = csv.get(row, col++);
				double prefVal = csv.getDouble(row, col++);
				double prefB = csv.getDouble(row, col++);
				double lowerVal = csv.getDouble(row, col++);
				double lowerB = csv.getDouble(row, col++);
				double upperVal = csv.getDouble(row, col++);
				double upperB = csv.getDouble(row, col++);
				
				ratesMap.put(name, new double[] {prefVal, lowerVal, upperVal});
				bValsMap.put(name, new double[] {prefB, lowerB, upperB});
			}
			
			PRVI25_RegionalSeismicity.bValsMap = bValsMap;
			PRVI25_RegionalSeismicity.ratesMap = ratesMap;
		}
	}
	
	private static boolean hasRegion(PRVI25_SeismicityRegions region) {
		checkLoadRates();
		return ratesMap.containsKey(region.name());
	}
	
	private static double loadRate(PRVI25_SeismicityRegions region, int index) {
		checkLoadRates();
		return ratesMap.get(region.name())[index];
	}
	
	private static double loadBVal(PRVI25_SeismicityRegions region, int index) {
		checkLoadRates();
		return bValsMap.get(region.name())[index];
	}
	
	/**
	 * If b various on outlier branches, they can cross over suchat that low > pref and high < pref for really small
	 * magnitudes. This sets low/high to the pref values in that case.
	 * @param gr
	 * @param lower
	 * @param region
	 * @param mMax
	 * @return
	 */
	private static IncrementalMagFreqDist adjustForCrossover(GutenbergRichterMagFreqDist gr, boolean lower,
			PRVI25_SeismicityRegions region, double mMax) {
		IncrementalMagFreqDist pref = PREFFERRED.build(region, gr, mMax);
		IncrementalMagFreqDist ret = new IncrementalMagFreqDist(gr.getMinX(), gr.getMaxX(), gr.size());
		
		boolean anyOutside = false;
		for (int i=0; i<gr.size(); i++) {
			double prefY = pref.getY(i);
			double myY = gr.getY(i);
			if ((lower && myY > prefY) || (!lower && myY < prefY)) {
				anyOutside = true;
				ret.set(i, prefY);
			} else {
				ret.set(i, myY);
			}
		}
		
		if (anyOutside) {
			ret.setName(gr.getName());
			return ret;
		}
		return gr;
	}
	
	/*
	 * If changed, don't forget to update:
	 * WEIGHTS ABOVE
	 * High/Low labels ABOVE
	 */
	private static final UncertaintyBoundType BOUND_TYPE = UncertaintyBoundType.CONF_95;
	
	private String name;
	private String shortName;
	private double weight;

	private PRVI25_RegionalSeismicity(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}
	
	public abstract IncrementalMagFreqDist build(PRVI25_SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax);
	
	private static final DecimalFormat oDF = new DecimalFormat("0.##");
	
	public static UncertainBoundedIncrMagFreqDist getBounded(PRVI25_SeismicityRegions region, EvenlyDiscretizedFunc refMFD, double mMax) {
		IncrementalMagFreqDist upper = HIGH.build(region, refMFD, mMax);
		IncrementalMagFreqDist lower = LOW.build(region, refMFD, mMax);
		IncrementalMagFreqDist pref = PREFFERRED.build(region, refMFD, mMax);
		
		if (pref == null)
			return null;
		
		UncertainBoundedIncrMagFreqDist bounded = new UncertainBoundedIncrMagFreqDist(pref, lower, upper, BOUND_TYPE);
		bounded.setName(pref.getName());
		bounded.setBoundName(getBoundName(lower, upper));
		
		return bounded;
	}
	
	static String getBoundName(IncrementalMagFreqDist lower, IncrementalMagFreqDist upper) {
		String boundName = BOUND_TYPE.toString();
		double lowerN5 = lower.getCumRateDistWithOffset().getInterpolatedY(5d);
		double upperN5 = upper.getCumRateDistWithOffset().getInterpolatedY(5d);
		boundName += ": N5∈["+oDF.format(lowerN5)+","+oDF.format(upperN5)+"]";
		return boundName;
	}
	
	public static UncertainBoundedIncrMagFreqDist getRemapped(Region region, PRVI25_SeismicityRegions seisRegion, PRVI25_DeclusteringAlgorithms declustering,
			PRVI25_SeisSmoothingAlgorithms smoothing, EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
		IncrementalMagFreqDist upper = null;
		IncrementalMagFreqDist lower = null;
		IncrementalMagFreqDist pref = null;
		
		double sumTotalN = 0d;
		double sumFractN = 0d;
		
//		for (SeismicityRegions seisRegion : seisRegions) {
			// get pdf
			GriddedGeoDataSet pdf = smoothing.loadXYZ(seisRegion, declustering);
			
			double fractN = 0d;
			for (int i=0; i<pdf.size(); i++)
				if (region.contains(pdf.getLocation(i)))
					fractN += pdf.get(i);
			
//			if (fractN == 0d)
//				continue;
			
			sumTotalN += 1d;
			sumFractN += fractN;
			
			// rescale for this fractional N
			IncrementalMagFreqDist myPref = PREFFERRED.build(seisRegion, refMFD, mMax);
			myPref.scale(fractN);
			IncrementalMagFreqDist myUpper = HIGH.build(seisRegion, refMFD, mMax);
			myUpper.scale(fractN);
			IncrementalMagFreqDist myLower = LOW.build(seisRegion, refMFD, mMax);
			myLower.scale(fractN);
			
			// now further scale bounds to account for less data
			for (int i=0; i<refMFD.size(); i++) {
				double prefVal = myPref.getY(i);
				if (prefVal > 0d) {
					double origUpper = myUpper.getY(i);
					double origLower = myLower.getY(i);
					
					double upperRatio = origUpper/prefVal;
					double lowerRatio = origLower/prefVal;
					
					upperRatio *= 1/Math.sqrt(fractN);
					lowerRatio /= 1/Math.sqrt(fractN);
					myUpper.set(i, prefVal*upperRatio);
					myLower.set(i, prefVal*lowerRatio);
				}
			}
			
			if (upper == null) {
				upper = myUpper;
				lower = myLower;
				pref = myPref;
			} else {
				// add them
				// now further scale bounds to account for less data
				for (int i=0; i<refMFD.size(); i++) {
					double prefVal = myPref.getY(i);
					if (prefVal > 0d) {
						upper.add(i, myUpper.getY(i));
						lower.add(i, myLower.getY(i));
						pref.add(i, myPref.getY(i));
					}
				}
			}
//		}
		Preconditions.checkNotNull(pref);
		
		double prefN5 = pref.getCumRateDistWithOffset().getInterpolatedY(5d);
		String name = "Remmapped Observed [pdfFractN="+oDF.format(sumFractN/sumTotalN)+", N5="+oDF.format(prefN5)+"]";
		
		UncertainBoundedIncrMagFreqDist ret = new UncertainBoundedIncrMagFreqDist(pref, lower, upper, BOUND_TYPE);
		ret.setName(name);
		ret.setBoundName(getBoundName(lower, upper));
		return ret;
	}
	
	private static GutenbergRichterMagFreqDist gr(EvenlyDiscretizedFunc refMFD, double mMax,
			double rateM5, double bVal) {
		GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		
		// this sets shape, min/max
		// subtract a tiny amount from mMax so that if it's exactly at a bin edge, e.g. 7.9, it rounds down, e.g. to 7.85
		gr.setAllButTotCumRate(refMFD.getX(0), refMFD.getX(refMFD.getClosestXIndex(mMax-0.001)), 1e16, bVal);
		// this scales it to match
		gr.scaleToCumRate(refMFD.getClosestXIndex(5.001), rateM5);
		
		gr.setName("Total Observed [b="+oDF.format(bVal)+", N5="+oDF.format(rateM5)+"]");
		
		return gr;
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
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(mMax);
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
		
//		for (AnalysisRegions analysisReg : AnalysisRegions.values()) {
//			UncertainBoundedIncrMagFreqDist remapped = getRemapped(analysisReg.load(), NSHM23_DeclusteringAlgorithms.AVERAGE,
//					NSHM23_SeisSmoothingAlgorithms.AVERAGE, refMFD, mMax);
//			System.out.println(remapped);
//		}
	}

}
