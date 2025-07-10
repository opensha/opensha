package org.opensha.sha.faultSurface.utils.ptSrcCorr;

import java.util.EnumSet;
import java.util.function.Supplier;

import org.apache.commons.lang3.ArrayUtils;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.util.DevStatus;
import org.opensha.commons.util.ServerPrefs;
import org.opensha.sha.earthquake.rupForecastImpl.PointSourceNshm.DistanceCorrection2013;
import org.opensha.sha.faultSurface.utils.RjbDistributionDistanceCorrection;
import org.opensha.sha.faultSurface.utils.ptSrcCorr.PointSourceDistanceCorrection.Single;

/**
 * {@link PointSourceDistanceCorrection} implementation enum.
 * 
 * Implementations return one or more correction via a {@link WeightedList}. If multiple corrections are returned,
 * then a rupture should be split into multiple realizations with the given weight. Weights will always be normalized
 * to sum to 1.
 */
public enum PointSourceDistanceCorrections implements Supplier<PointSourceDistanceCorrection> {
	
	NONE("None", null, DevStatus.PRODUCTION) {
		@Override
		protected PointSourceDistanceCorrection initCorr() {
			// none
			return null;
		}
	},
	FIELD("Field", FieldPointSourceCorrection.class, DevStatus.PRODUCTION) {
		@Override
		protected PointSourceDistanceCorrection initCorr() {
			return new FieldPointSourceCorrection();
		}
	},
	NSHM_2013("USGS NSHM (2013)", DistanceCorrection2013.class, DevStatus.PRODUCTION) {
		@Override
		protected PointSourceDistanceCorrection initCorr() {
			return new DistanceCorrection2013();
		}
	},
	AVERAGE_SPINNING("Spinning Average", DistanceDistributionCorrection.class, DevStatus.DEVELOPMENT) {
		@Override
		protected DistanceDistributionCorrection initCorr() {
			return DistanceDistributionCorrection.getSingleAverage(false, false);
		}
	},
	AVERAGE_SPINNING_ALONG("Spinning Average (sample along)", DistanceDistributionCorrection.class, DevStatus.DEVELOPMENT) {
		@Override
		protected DistanceDistributionCorrection initCorr() {
			return DistanceDistributionCorrection.getSingleAverage(true, true);
		}
	},
	FIVE_POINT_SPINNING_DIST("5-Point Spinning Distribution (centered)",
			DistanceDistributionCorrection.class, DevStatus.DEVELOPMENT) {
		@Override
		protected DistanceDistributionCorrection initCorr() {
			// for the simple case I found that evenly weighted fractiles performs better than importance sampled
			return DistanceDistributionCorrection.getEvenlyWeightedFractiles(5, false, false);
		}
	},
	FIVE_POINT_SPINNING_DIST_ALONG("5-Point Spinning Distribution (sample along)",
			DistanceDistributionCorrection.class, DevStatus.DEVELOPMENT) {
		@Override
		protected DistanceDistributionCorrection initCorr() {
			// when sampling along-strike and down-dip I found that importance sampling performs best
//			return DistanceDistributionCorrection.getEvenlyWeightedFractiles(5, true, true);
			return DistanceDistributionCorrection.getImportanceSampledFractiles(
//					new double[] {0d, 0.05, 0.15, 0.4, 0.6, 1d}, true, true); // my original "best so far"
//					// "Pure importance transform F = (i/5)². Gives each bin the same area in index space after the transform."
//					new double[] {0, 0.04, 0.16, 0.36, 0.64, 1}, true, true);
					new double[] {0, 0.05, 0.15, 0.30, 0.55, 1}, true, true); // new "best so far"?
		}
	},
	TWENTY_POINT_SPINNING_DIST("20-Point Spinning Distribution (centered)",
			DistanceDistributionCorrection.class, DevStatus.DEVELOPMENT) {
		@Override
		protected DistanceDistributionCorrection initCorr() {
			return DistanceDistributionCorrection.getEvenlyWeightedFractiles(20, false, false);
		}
	},
	TWENTY_POINT_SPINNING_DIST_ALONG("20-Point Spinning Distribution (sample along)",
			DistanceDistributionCorrection.class, DevStatus.DEVELOPMENT) {
		@Override
		protected DistanceDistributionCorrection initCorr() {
			return DistanceDistributionCorrection.getEvenlyWeightedFractiles(20, true, true);
		}
	};
	
	// TODO: decide on default
	public static final PointSourceDistanceCorrections DEFAULT = NSHM_2013;
	
	/**
	 * Set of all {@link PointSourceDistanceCorrections} that produce a {@link PointSourceDistanceCorrection.Single}.
	 */
	public static final EnumSet<PointSourceDistanceCorrections> SINGLE_CORRS;
	static {
		SINGLE_CORRS = EnumSet.noneOf(PointSourceDistanceCorrections.class);
		for (PointSourceDistanceCorrections corr : values()) {
			if (corr.clazz == null || PointSourceDistanceCorrection.Single.class.isAssignableFrom(corr.clazz))
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
	private Class<? extends PointSourceDistanceCorrection> clazz;

	private PointSourceDistanceCorrections(String name,
			Class<? extends PointSourceDistanceCorrection> clazz, DevStatus devStatus) {
		this.name = name;
		this.clazz = clazz;
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
