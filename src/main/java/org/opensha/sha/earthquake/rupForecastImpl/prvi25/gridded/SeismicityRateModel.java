package org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded.SeismicityRateFileLoader.Exact;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded.SeismicityRateFileLoader.RateRecord;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded.SeismicityRateFileLoader.RateType;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_DeclusteringAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SeisSmoothingAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader.PRVI25_SeismicityRegions;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

public class SeismicityRateModel {
	
	private final UncertaintyBoundType boundType;
	private final double lowerQuantile;
	private final double upperQuantile;
	
	private final RateRecord meanRecord;
	private final RateRecord lowerRecord;
	private final RateRecord upperRecord;
	
	public SeismicityRateModel(CSVFile<String> csv, RateType type, UncertaintyBoundType boundType) {
		this(SeismicityRateFileLoader.loadRecords(csv, type), boundType);
	}
	
	public SeismicityRateModel(List<? extends RateRecord> rates, UncertaintyBoundType boundType) {
		this.boundType = boundType;
		switch (boundType) {
		case CONF_68:
			lowerQuantile = 0.16;
			upperQuantile = 0.84;
			break;
		case ONE_SIGMA:
			lowerQuantile = 0.16;
			upperQuantile = 0.84;
			break;
		case CONF_95:
			lowerQuantile = 0.025;
			upperQuantile = 0.975;
			break;
		case TWO_SIGMA:
			lowerQuantile = 0.025;
			upperQuantile = 0.975;
			break;

		default:
			throw new IllegalStateException("Not supported: "+boundType);
		}
		
		meanRecord = SeismicityRateFileLoader.locateMean(rates);
		lowerRecord = SeismicityRateFileLoader.locateQuantile(rates, lowerQuantile);
		upperRecord = SeismicityRateFileLoader.locateQuantile(rates, upperQuantile);
	}
	
	public UncertaintyBoundType getBoundType() {
		return boundType;
	}

	public double getLowerQuantile() {
		return lowerQuantile;
	}

	public double getUpperQuantile() {
		return upperQuantile;
	}

	public RateRecord getMeanRecord() {
		return meanRecord;
	}

	public RateRecord getLowerRecord() {
		return lowerRecord;
	}

	public RateRecord getUpperRecord() {
		return upperRecord;
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
	
	public IncrementalMagFreqDist buildPreferred(EvenlyDiscretizedFunc refMFD, double mMax)
			throws IOException {
		return build(meanRecord, refMFD, mMax);
	}
	
	public IncrementalMagFreqDist buildLower(EvenlyDiscretizedFunc refMFD, double mMax)
			throws IOException {
		return build(lowerRecord, refMFD, mMax);
	}
	
	public IncrementalMagFreqDist buildUpper(EvenlyDiscretizedFunc refMFD, double mMax)
			throws IOException {
		return build(upperRecord, refMFD, mMax);
	}
	
	private IncrementalMagFreqDist build(RateRecord record, EvenlyDiscretizedFunc refMFD, double mMax)
			throws IOException {
		IncrementalMagFreqDist mfd = SeismicityRateFileLoader.buildIncrementalMFD(record, refMFD, mMax);
		
		boolean preferred = record.mean || record.quantile == 0.5;
		
		if (!(record instanceof Exact) && !preferred)
			mfd = adjustForCrossover(mfd, build(meanRecord, refMFD, mMax), record.quantile < 0.5);
		
		String name = "Observed";
		if (!preferred)
			name += " (p"+oDF.format(100d*record.quantile)+")";
		name += ", N"+oDF.format(record.M1)+"="+oDF.format(record.rateAboveM1);
		
		mfd.setName(name);
		
		return mfd;
	};
	
	private static final DecimalFormat oDF = new DecimalFormat("0.##");
	
	public UncertainBoundedIncrMagFreqDist getBounded(EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
		IncrementalMagFreqDist upper = buildUpper(refMFD, mMax);
		IncrementalMagFreqDist lower = buildLower(refMFD, mMax);
		IncrementalMagFreqDist pref = buildPreferred(refMFD, mMax);
		
		double M1 = meanRecord.M1;
		
		UncertainBoundedIncrMagFreqDist bounded = new UncertainBoundedIncrMagFreqDist(pref, lower, upper, boundType);
		bounded.setName(pref.getName());
		bounded.setBoundName(getBoundName(lower, upper, M1));
		
		return bounded;
	}
	
	String getBoundName(IncrementalMagFreqDist lower, IncrementalMagFreqDist upper, double M1) {
		String boundName = boundType.toString();
		double lowerN = lower.getCumRateDistWithOffset().getInterpolatedY(M1);
		double upperN = upper.getCumRateDistWithOffset().getInterpolatedY(M1);
		boundName += ": N"+oDF.format(M1)+"∈["+oDF.format(lowerN)+","+oDF.format(upperN)+"]";
		return boundName;
	}
	
	public UncertainBoundedIncrMagFreqDist getRescaled(double fractN, EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
		IncrementalMagFreqDist pref = buildPreferred(refMFD, mMax);
		pref.scale(fractN);
		IncrementalMagFreqDist uppper = buildUpper(refMFD, mMax);
		uppper.scale(fractN);
		IncrementalMagFreqDist lower = buildLower(refMFD, mMax);
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
		UncertainBoundedIncrMagFreqDist rescaled = new UncertainBoundedIncrMagFreqDist(pref, lower, uppper, boundType);
		double M1 = meanRecord.M1;
		
		double prefN = rescaled.getCumRateDistWithOffset().getInterpolatedY(M1);
		String name = "Remmapped Observed [pdfFractN="+oDF.format(fractN)+", N"+oDF.format(M1)+"="+oDF.format(prefN)+"]";
		
		rescaled.setName(name);
		rescaled.setBoundName(getBoundName(rescaled.getLower(), rescaled.getUpper(), M1));
		return rescaled;
	}
	
	public UncertainBoundedIncrMagFreqDist getRemapped(Region region, PRVI25_SeismicityRegions seisRegion,
			PRVI25_DeclusteringAlgorithms declustering, PRVI25_SeisSmoothingAlgorithms smoothing,
			EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
		GriddedGeoDataSet pdf = smoothing.loadXYZ(seisRegion, declustering);
		
		double fractN = 0d;
		for (int i=0; i<pdf.size(); i++)
			if (region.contains(pdf.getLocation(i)))
				fractN += pdf.get(i);
		
		return getRescaled(fractN, refMFD, mMax);
	}

}
