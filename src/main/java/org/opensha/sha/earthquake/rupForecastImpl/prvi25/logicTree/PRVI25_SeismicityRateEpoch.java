package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.util.List;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.BoundedUncertainty;
import org.opensha.commons.data.uncertainty.UncertainArbDiscFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
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
public enum PRVI25_SeismicityRateEpoch implements LogicTreeNode {
	FULL("Full (1900-2023)", "Full", "1900_2023", 1d/3d),
	RECENT("Recent (1973-2023)", "Recent", "1973_2023", 1d/3d),
	RECENT_SCALED("Recent, scaled to full rate", "Recent (scaled to full)", null, 1d/3d);
	
	public static PRVI25_SeismicityRateEpoch DEFAULT = FULL; // TODO

	private String name;
	private String shortName;
	private String rateDirName;
	private double weight;


	private PRVI25_SeismicityRateEpoch(String name, String shortName, String rateDirName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.rateDirName = rateDirName;
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
		return name();
	}
	
	public String getRateSubDirName() {
		return rateDirName;
	}
	
	private static boolean AVG_UNCERT_IN_LOG10 = true;
	
	public static UncertainBoundedIncrMagFreqDist averageUncert(List<UncertainBoundedIncrMagFreqDist> mfds, List<Double> weights) {
		if (mfds.size() == 1)
			return mfds.get(0);
		UncertainBoundedIncrMagFreqDist refMFD = mfds.get(0);
		double sumWeight = 0d;
		for (int i=0; i<mfds.size(); i++) {
			UncertainBoundedIncrMagFreqDist mfd = mfds.get(i);
			Preconditions.checkNotNull(mfd.getBoundType());
			Preconditions.checkState(mfd.getBoundType() == refMFD.getBoundType());
			Preconditions.checkState(mfd.getDelta() == refMFD.getDelta());
			Preconditions.checkState(mfd.getMinX() == refMFD.getMinX());
			Preconditions.checkState(mfd.size() == refMFD.size());
			sumWeight += weights.get(i);
		}
		Preconditions.checkState(Precision.equals(sumWeight, 1d, 1e-4));
		
		IncrementalMagFreqDist pref = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		IncrementalMagFreqDist lower = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		IncrementalMagFreqDist upper = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		for (int i=0; i<refMFD.size(); i++) {
			boolean anyNonZero = false;
			for (int j=0; !anyNonZero && j<mfds.size(); j++)
				anyNonZero = mfds.get(j).getY(i) > 0d;
			if (!anyNonZero)
				continue;
			double average = 0d;
			double sumNonzeroWeight = 0d;
			WeightedList<BoundedUncertainty> uncertainties = new WeightedList<>(mfds.size());
			for (int j=0; j<mfds.size(); j++) {
				UncertainBoundedIncrMagFreqDist mfd = mfds.get(j);
				double y = mfd.getY(i);
				if (y > 0d) {
					double weight = weights.get(j);
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
	
	public static UncertainArbDiscFunc averageUncertCml(List<UncertainArbDiscFunc> mfds, List<Double> weights) {
		if (mfds.size() == 1)
			return mfds.get(0);
		UncertainArbDiscFunc refMFD = mfds.get(0);
		double sumWeight = 0d;
		for (int i=0; i<mfds.size(); i++) {
			UncertainArbDiscFunc mfd = mfds.get(i);
			Preconditions.checkState(mfd.getMinX() == refMFD.getMinX(), "MFD minX=%s != ref minX=%s",
					mfd.getMinX(), refMFD.getMinX());
			Preconditions.checkState(mfd.getMaxX() == refMFD.getMaxX(), "MFD maxX=%s != ref maxX=%s",
					mfd.getMaxX(), refMFD.getMaxX());
			Preconditions.checkState(mfd.size() == refMFD.size(), "MFD size=%s != ref size=%s", mfd.size(),
					refMFD.size());
			sumWeight += weights.get(i);
		}
		Preconditions.checkState(Precision.equals(sumWeight, 1d, 1e-4));
		
		ArbitrarilyDiscretizedFunc pref = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc lower = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc upper = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<refMFD.size(); i++) {
			boolean anyNonZero = false;
			for (int j=0; !anyNonZero && j<mfds.size(); j++)
				anyNonZero = mfds.get(j).getY(i) > 0d;
			if (!anyNonZero)
				continue;
			double x = refMFD.getX(i);
			double average = 0d;
			double sumNonzeroWeight = 0d;
			WeightedList<BoundedUncertainty> uncertainties = new WeightedList<>(mfds.size());
			for (int j=0; j<mfds.size(); j++) {
				UncertainArbDiscFunc mfd = mfds.get(j);
				double y = mfd.getY(i);
				if (y > 0d) {
					double weight = weights.get(j);
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
