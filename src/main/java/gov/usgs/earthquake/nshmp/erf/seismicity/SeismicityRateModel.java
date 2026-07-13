package gov.usgs.earthquake.nshmp.erf.seismicity;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

import org.apache.commons.numbers.core.Precision;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.BoundedUncertainty;
import org.opensha.commons.data.uncertainty.UncertainArbDiscFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_DeclusteringAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SeisSmoothingAlgorithms;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader.PRVI25_SeismicityRegions;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.Exact;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.RateRecord;
import gov.usgs.earthquake.nshmp.erf.seismicity.SeismicityRateFileLoader.RateType;

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
	
	public SeismicityRateModel(RateRecord meanRecord, RateRecord lowerRecord, RateRecord upperRecord,
			UncertaintyBoundType boundType) {
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
		
		this.meanRecord = meanRecord;
		this.lowerRecord = lowerRecord;
		this.upperRecord = upperRecord;
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
	
	public IncrementalMagFreqDist buildPreferred(EvenlyDiscretizedFunc refMFD, double mMax) {
		return build(meanRecord, refMFD, mMax);
	}
	
	public IncrementalMagFreqDist buildPreferred(EvenlyDiscretizedFunc refMFD, double mMax, double magCorner) {
		return build(meanRecord, refMFD, mMax, magCorner);
	}
	
	public IncrementalMagFreqDist buildLower(EvenlyDiscretizedFunc refMFD, double mMax) {
		return build(lowerRecord, refMFD, mMax);
	}
	
	public IncrementalMagFreqDist buildLower(EvenlyDiscretizedFunc refMFD, double mMax, double magCorner) {
		return build(lowerRecord, refMFD, mMax, magCorner);
	}
	
	public IncrementalMagFreqDist buildUpper(EvenlyDiscretizedFunc refMFD, double mMax) {
		return build(upperRecord, refMFD, mMax);
	}
	
	public IncrementalMagFreqDist buildUpper(EvenlyDiscretizedFunc refMFD, double mMax, double magCorner) {
		return build(upperRecord, refMFD, mMax, magCorner);
	}
	
	private IncrementalMagFreqDist build(RateRecord record, EvenlyDiscretizedFunc refMFD, double mMax) {
		return build(record, refMFD, mMax, Double.NaN);
	}
	
	private IncrementalMagFreqDist build(RateRecord record, EvenlyDiscretizedFunc refMFD, double mMax,
			double magCorner) {
		IncrementalMagFreqDist mfd = SeismicityRateFileLoader.buildIncrementalMFD(record, refMFD, mMax, magCorner);
		
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
		return getBounded(refMFD, mMax, Double.NaN);
	}
	
	public UncertainBoundedIncrMagFreqDist getBounded(EvenlyDiscretizedFunc refMFD, double mMax, double magCorner) throws IOException {
		IncrementalMagFreqDist upper = buildUpper(refMFD, mMax, magCorner);
		IncrementalMagFreqDist lower = buildLower(refMFD, mMax, magCorner);
		IncrementalMagFreqDist pref = buildPreferred(refMFD, mMax, magCorner);
		
		double M1 = meanRecord.M1;
		
		UncertainBoundedIncrMagFreqDist bounded = new UncertainBoundedIncrMagFreqDist(pref, lower, upper, boundType);
		bounded.setName(pref.getName());
		bounded.setBoundName(getBoundName(lower, upper, M1));
		
		return bounded;
	}
	
	public UncertainArbDiscFunc getBoundedCml(EvenlyDiscretizedFunc refMFD, double mMax) throws IOException {
		EvenlyDiscretizedFunc upper, lower, pref;
		if (upperRecord instanceof Exact) {
			upper = ((Exact)upperRecord).cumulativeDist;
			lower = ((Exact)lowerRecord).cumulativeDist;
			pref = ((Exact)meanRecord).cumulativeDist;
		} else {
			upper = buildUpper(refMFD, mMax).getCumRateDistWithOffset();
			lower = buildLower(refMFD, mMax).getCumRateDistWithOffset();
			pref = buildPreferred(refMFD, mMax).getCumRateDistWithOffset();
		}
		
		double M1 = meanRecord.M1;
		
		UncertainArbDiscFunc bounded = new UncertainArbDiscFunc(pref, lower, upper, boundType);
		bounded.setName(pref.getName());
		bounded.setBoundName(getBoundName(lower, upper, M1));
		
		return bounded;
	}
	
	String getBoundName(IncrementalMagFreqDist lower, IncrementalMagFreqDist upper, double M1) {
		return getBoundName(lower.getCumRateDistWithOffset(), upper.getCumRateDistWithOffset(), M1);
	}
	
	String getBoundName(EvenlyDiscretizedFunc lower, EvenlyDiscretizedFunc upper, double M1) {
		String boundName = boundType.toString();
		double lowerN = lower.getInterpolatedY(M1);
		double upperN = upper.getInterpolatedY(M1);
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
	
	private static boolean AVG_UNCERT_IN_LOG10 = true;
	
	/**
	 * Calculates a weighted average of multiple uncertain incremental magnitude-frequency distributions.
	 * The calculation is performed independently for each magnitude bin using the mixture-of-normals approach
	 * of {@link BoundedUncertainty#weightedCombination(WeightedList)} assuming log-normally distributed uncertainties.
	 *
	 * @param mfds weighted uncertain magnitude-frequency distributions to average
	 * @return the bin-by-bin weighted average, with uncertainties combined in log10 rate space
	 * @throws NullPointerException if any input distribution has a {@code null} uncertainty-bound type
	 * @throws IllegalStateException if the distributions have incompatible discretizations or bound types
	 */
	public static UncertainBoundedIncrMagFreqDist averageUncert(WeightedList<UncertainBoundedIncrMagFreqDist> mfds) {
		if (mfds.size() == 1)
			return mfds.getValue(0);
		if (!mfds.isNormalized()) {
			mfds = new WeightedList<>(mfds);
			mfds.normalize();
		}
		UncertainBoundedIncrMagFreqDist refMFD = mfds.getValue(0);
		for (int i=0; i<mfds.size(); i++) {
			UncertainBoundedIncrMagFreqDist mfd = mfds.getValue(i);
			Preconditions.checkNotNull(mfd.getBoundType());
			Preconditions.checkState(mfd.getBoundType() == refMFD.getBoundType());
			Preconditions.checkState(IncrementalMagFreqDist.areXValuesIdentical(refMFD, mfd));
		}
		
		IncrementalMagFreqDist pref = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		IncrementalMagFreqDist lower = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		IncrementalMagFreqDist upper = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		for (int i=0; i<refMFD.size(); i++) {
			boolean anyNonZero = false;
			for (int j=0; !anyNonZero && j<mfds.size(); j++)
				anyNonZero = mfds.getValue(j).getY(i) > 0d;
			if (!anyNonZero)
				continue;
			double average = 0d;
			double sumNonzeroWeight = 0d;
			WeightedList<BoundedUncertainty> uncertainties = new WeightedList<>(mfds.size());
			for (int j=0; j<mfds.size(); j++) {
				UncertainBoundedIncrMagFreqDist mfd = mfds.getValue(j);
				double y = mfd.getY(i);
				if (y > 0d) {
					double weight = mfds.getWeight(j);
					sumNonzeroWeight += weight;
					average += weight*y;
					double lowerY = mfd.getLowerY(i);
					double upperY = mfd.getUpperY(i);
					if (AVG_UNCERT_IN_LOG10)
						uncertainties.add(new BoundedUncertainty(mfd.getBoundType(),
								Math.log10(lowerY), Math.log10(upperY),
								mfd.getBoundType().estimateStdDev(Math.log10(lowerY), Math.log10(upperY))), weight);
					else
						uncertainties.add(new BoundedUncertainty(mfd.getBoundType(),
								lowerY, upperY,
								mfd.getStdDev(i)), weight);
				}
			}
			pref.set(i, average);
			BoundedUncertainty uncertMix = BoundedUncertainty.weightedCombination(uncertainties);
			if (AVG_UNCERT_IN_LOG10) {
				lower.set(i, Math.pow(10, uncertMix.lowerBound)*sumNonzeroWeight);
				upper.set(i, Math.pow(10, uncertMix.upperBound)*sumNonzeroWeight);
			} else {
				lower.set(i, uncertMix.lowerBound*sumNonzeroWeight);
				upper.set(i, uncertMix.upperBound*sumNonzeroWeight);
			}
		}
		UncertainBoundedIncrMagFreqDist ret = new UncertainBoundedIncrMagFreqDist(pref, lower, upper, refMFD.getBoundType());
		ret.setName(refMFD.getName());
		return ret;
	}
	
	/**
	 * Calculates a weighted average of multiple uncertain cumulative magnitude-frequency distributions.
	 * The calculation is performed independently for each magnitude bin using the mixture-of-normals approach
	 * of {@link BoundedUncertainty#weightedCombination(WeightedList)} assuming log-normally distributed uncertainties.
	 *
	 * @param mfds weighted uncertain magnitude-frequency distributions to average
	 * @return the bin-by-bin weighted average, with uncertainties combined in log10 rate space
	 * @throws NullPointerException if any input distribution has a {@code null} uncertainty-bound type
	 * @throws IllegalStateException if the distributions have incompatible discretizations or bound types
	 */
	public static UncertainArbDiscFunc averageUncertCml(WeightedList<UncertainArbDiscFunc> mfds) {
		if (mfds.size() == 1)
			return mfds.getValue(0);
		if (!mfds.isNormalized()) {
			mfds = new WeightedList<>(mfds);
			mfds.normalize();
		}
		UncertainArbDiscFunc refMFD = mfds.getValue(0);
		for (int i=0; i<mfds.size(); i++) {
			UncertainArbDiscFunc mfd = mfds.getValue(i);
			Preconditions.checkState(mfd.getMinX() == refMFD.getMinX(), "MFD minX=%s != ref minX=%s",
					mfd.getMinX(), refMFD.getMinX());
			Preconditions.checkState(mfd.getMaxX() == refMFD.getMaxX(), "MFD maxX=%s != ref maxX=%s",
					mfd.getMaxX(), refMFD.getMaxX());
			Preconditions.checkState(mfd.size() == refMFD.size(), "MFD size=%s != ref size=%s", mfd.size(),
					refMFD.size());
		}
		
		ArbitrarilyDiscretizedFunc pref = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc lower = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc upper = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<refMFD.size(); i++) {
			boolean anyNonZero = false;
			for (int j=0; !anyNonZero && j<mfds.size(); j++)
				anyNonZero = mfds.getValue(j).getY(i) > 0d;
			if (!anyNonZero)
				continue;
			double x = refMFD.getX(i);
			double average = 0d;
			double sumNonzeroWeight = 0d;
			WeightedList<BoundedUncertainty> uncertainties = new WeightedList<>(mfds.size());
			for (int j=0; j<mfds.size(); j++) {
				UncertainArbDiscFunc mfd = mfds.getValue(j);
				double y = mfd.getY(i);
				if (y > 0d) {
					double weight = mfds.getWeight(j);
					sumNonzeroWeight += weight;
					average += weight*mfd.getY(i);
					double lowerY = mfd.getLowerY(i);
					double upperY = mfd.getUpperY(i);
					if (AVG_UNCERT_IN_LOG10)
						uncertainties.add(new BoundedUncertainty(mfd.getBoundType(),
								Math.log10(lowerY), Math.log10(upperY),
								mfd.getBoundType().estimateStdDev(Math.log10(lowerY), Math.log10(upperY))), weight);
					else
						uncertainties.add(new BoundedUncertainty(mfd.getBoundType(),
								lowerY, upperY,
								mfd.getStdDev(i)), weight);
				}
			}
			pref.set(x, average);
			BoundedUncertainty uncertMix = BoundedUncertainty.weightedCombination(uncertainties);
			if (AVG_UNCERT_IN_LOG10) {
				lower.set(x, Math.pow(10, uncertMix.lowerBound)*sumNonzeroWeight);
				upper.set(x, Math.pow(10, uncertMix.upperBound)*sumNonzeroWeight);
			} else {
				lower.set(x, uncertMix.lowerBound*sumNonzeroWeight);
				upper.set(x, uncertMix.upperBound*sumNonzeroWeight);
			}
		}
		UncertainArbDiscFunc ret = new UncertainArbDiscFunc(pref, lower, upper, refMFD.getBoundType());
		ret.setName(refMFD.getName());
		return ret;
	}

}
