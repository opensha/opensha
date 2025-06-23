package org.opensha.sha.faultSurface.utils;

import java.util.EnumSet;
import java.util.function.Supplier;

import org.apache.commons.lang3.ArrayUtils;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.DevStatus;
import org.opensha.commons.util.ServerPrefs;
import org.opensha.sha.earthquake.rupForecastImpl.PointSourceNshm.DistanceCorrection2013;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.utils.ptSrcCorr.FieldPointSourceCorrection;

import com.google.common.base.Preconditions;

/**
 * {@link PointSourceDistanceCorrection} implementation enum.
 * 
 * Implementations return one or more correction via a {@link WeightedList}. If multiple corrections are returned,
 * then a rupture should be split into multiple realizations with the given weight. Weights will always be normalized
 * to sum to 1.
 */
public enum PointSourceDistanceCorrections implements Supplier<PointSourceDistanceCorrection> {
	
	NONE("None", DevStatus.PRODUCTION) {
		@Override
		protected PointSourceDistanceCorrection initCorr() {
			// none
			return null;
		}
	},
	FIELD("Field", DevStatus.PRODUCTION) {
		@Override
		protected PointSourceDistanceCorrection initCorr() {
			return new FieldPointSourceCorrection();
		}
	},
	NSHM_2013("USGS NSHM (2013)", DevStatus.PRODUCTION) {
		@Override
		protected PointSourceDistanceCorrection initCorr() {
			return new DistanceCorrection2013();
		}
	},
	MEDIAN_RJB("Median rJB (centered)", DevStatus.DEVELOPMENT) {
		@Override
		protected WeightedList<? extends PointSourceDistanceCorrection> initCorrs() {
			return WeightedList.evenlyWeighted(new RjbDistributionDistanceCorrection(0.5, false, false));
		}
	},
	FIVE_POINT_RJB_DIST("5-Point rJB Distribution (centered)", DevStatus.DEVELOPMENT) {
		@Override
		protected WeightedList<? extends PointSourceDistanceCorrection> initCorrs() {
			// for the simple case I found that evenly weighted fractiles performs better than importance sampled
			return RjbDistributionDistanceCorrection.getEvenlyWeightedFractiles(5, false, false);
//			return RjbDistributionDistanceCorrection.getImportanceSampledFractiles(
////					new double[] {0d, 0.05, 0.2, 0.5, 0.8, 1d}, false, false);
////					new double[] {0d, 0.02, 0.10, 0.35, 0.65, 1d}, false, false);
//					new double[] {0d, 0.05, 0.30, 0.60, 0.95, 1d}, false, false);
		}
	},
	FIVE_POINT_RJB_DIST_ALONG("5-Point rJB Distribution (sample along)", DevStatus.DEVELOPMENT) {
		@Override
		protected WeightedList<? extends PointSourceDistanceCorrection> initCorrs() {
			// when sampling along-strike I found that importance sampling performs best
//			return RjbDistributionDistanceCorrection.getEvenlyWeightedFractiles(5, true, true);
			return RjbDistributionDistanceCorrection.getImportanceSampledFractiles(
//					new double[] {0d, 0.05, 0.20, 0.5, 0.8, 1d}, true, true);
					new double[] {0d, 0.05, 0.15, 0.4, 0.6, 1d}, true, true); // best
//					new double[] {0d, 0.05, 0.20, 0.4, 0.6, 1d}, true, true);
//					new double[] {0d, 0.05, 0.30, 0.60, 0.95, 1d}, true, true);
//					new double[] {0d, 0.02, 0.10, 0.35, 0.65, 1d}, true, true);
//					new double[] {0d, 0.02, 0.10, 0.2, 0.5, 1d}, true, true);
		}
	},
	TWENTY_POINT_RJB_DIST_ALONG("20-Point rJB Distribution (sample along)", DevStatus.EXPERIMENTAL) {
		@Override
		protected WeightedList<? extends PointSourceDistanceCorrection> initCorrs() {
			return RjbDistributionDistanceCorrection.getEvenlyWeightedFractiles(20, true, true);
		}
	},
	TWENTY_POINT_RJB_DIST("20-Point rJB Distribution (centered)", DevStatus.EXPERIMENTAL) {
		@Override
		protected WeightedList<? extends PointSourceDistanceCorrection> initCorrs() {
			return RjbDistributionDistanceCorrection.getEvenlyWeightedFractiles(20, false, false);
		}
	},
	SUPERSAMPLING_0p1_FIVE_POINT_RJB_DIST("Super-sampling 5-Point rJB (centered, 0.1 deg)", DevStatus.DEVELOPMENT) {
		@Override
		protected WeightedList<? extends PointSourceDistanceCorrection> initCorrs() {
			// for this one, importance sampling does better because the closest distance can really be at a corner
			// that isn't captured by the wide evenly spaced bins
			return SupersamplingRjbDistributionDistanceCorrection.getImportanceSampledFractiles(
//					new double[] {0d, 0.05, 0.2, 0.5, 0.8, 1d}, 0.1, 21, 5, false, false);
					new double[] {0d, 0.05, 0.15, 0.4, 0.6, 1d}, 0.1, 21, 5, false, false);
//					new double[] {0d, 0.02, 0.10, 0.35, 0.65, 1d}, 0.1, 21, 5, false, false);
//					new double[] {0d, 0.05, 0.30, 0.60, 0.95, 1d}, 0.1, 21, 5, false, false);
//			return SupersamplingRjbDistributionDistanceCorrection.getEvenlyWeightedFractiles(
//					5, 0.1, 11, 3, false, false);
		}
	},
	SUPERSAMPLING_0p1_FIVE_POINT_RJB_DIST_ALONG("Super-sampling 5-Point rJB (centered, 0.1 deg, sample along)", DevStatus.DEVELOPMENT) {
		@Override
		protected WeightedList<? extends PointSourceDistanceCorrection> initCorrs() {
			// for this one, importance sampling does better because the closest distance can really be at a corner
			// that isn't captured by the wide evenly spaced bins
			return SupersamplingRjbDistributionDistanceCorrection.getImportanceSampledFractiles(
					new double[] {0d, 0.05, 0.15, 0.4, 0.6, 1d}, 0.1, 11, 5, true, true);
		}
	},
	SUPERSAMPLING_0p1_TWENTY_POINT_RJB_DIST("Super-sampling 20-Point rJB (centered, 0.1 deg)", DevStatus.EXPERIMENTAL) {
		@Override
		protected WeightedList<? extends PointSourceDistanceCorrection> initCorrs() {
			return SupersamplingRjbDistributionDistanceCorrection.getEvenlyWeightedFractiles(
					20, 0.1, 11, 3, false, false);
		}
	};
	
	// TODO: decide on default
	public static final PointSourceDistanceCorrections DEFAULT = NSHM_2013;
	
	/**
	 * Set of all {@link PointSourceDistanceCorrections} that produce a single {@link PointSourceDistanceCorrection}.
	 */
	public static final EnumSet<PointSourceDistanceCorrections> SINGLE_CORRS;
	static {
		SINGLE_CORRS = EnumSet.noneOf(PointSourceDistanceCorrections.class);
		for (PointSourceDistanceCorrections corr : values()) {
			if (corr.corrs == null || corr.corrs.size() == 1)
				SINGLE_CORRS.add(corr);
		}
	}
	
	public static final EnumSet<PointSourceDistanceCorrections> forDevStatus(DevStatus... stati) {
		EnumSet<PointSourceDistanceCorrections> set = EnumSet.allOf(PointSourceDistanceCorrections.class);
		for (PointSourceDistanceCorrections corr : set) {
			if (!ArrayUtils.contains(stati, corr.devStatus)) set.remove(corr);
		}
		return set;
	}
	
	public static EnumSet<PointSourceDistanceCorrections> forServerPrefs(ServerPrefs prefs) {
		if (prefs == ServerPrefs.DEV_PREFS)
			return forDevStatus(DevStatus.PRODUCTION, DevStatus.DEVELOPMENT, DevStatus.EXPERIMENTAL);
		else if (prefs == ServerPrefs.PRODUCTION_PREFS)
			return forDevStatus(DevStatus.PRODUCTION);
		else
			throw new IllegalArgumentException(
				"Unknown ServerPrefs instance: " + prefs);
	}
	
	private String name;
	private DevStatus devStatus;
	private volatile boolean intialized;
	private PointSourceDistanceCorrection corr;

	private PointSourceDistanceCorrections(String name, DevStatus devStatus) {
		this.name = name;
		this.devStatus = devStatus;
	}
	
	protected abstract PointSourceDistanceCorrection initCorr();
	
	private void checkInitCorrs() {
		if (!intialized) {
			synchronized (this) {
				if (!intialized) {
					PointSourceDistanceCorrection corr = initCorr();
					this.corr = corr;
					intialized = true;
				}
			}
		}
	}
	
	@Override
	public String toString() {
		return getName();
	}
	
	public String getName() {
		return name;
	}

	@Override
	public PointSourceDistanceCorrection get() {
		checkInitCorrs();
		return corr;
	}
	
	/**
	 * Finds the enum that generated this list (if any), otherwise null
	 * @param corr
	 * @return
	 */
	public static PointSourceDistanceCorrections forCorrection(PointSourceDistanceCorrection corrInst) {
		if (corrInst == null)
			return NONE;
		for (PointSourceDistanceCorrections corr : values()) {
			corr.checkInitCorrs();
			if (corr.corr == corrInst)
				return corr;
		}
		return null;
	}

}
