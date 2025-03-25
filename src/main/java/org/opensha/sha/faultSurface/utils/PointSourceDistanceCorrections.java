package org.opensha.sha.faultSurface.utils;

import java.util.EnumSet;
import java.util.function.Supplier;

import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.rupForecastImpl.PointSourceNshm.DistanceCorrection2013;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.util.NSHMP_Util;
import org.opensha.sha.util.NSHMP_Util.DistanceCorrection2008;

import com.google.common.base.Preconditions;

/**
 * {@link PointSourceDistanceCorrection} implementation enum.
 * 
 * Implementations return one or more correction via a {@link WeightedList}. If multiple corrections are returned,
 * then a rupture should be split into multiple realizations with the given weight. Weights will always be normalized
 * to sum to 1.
 */
public enum PointSourceDistanceCorrections implements Supplier<WeightedList<? extends PointSourceDistanceCorrection>> {
	
	NONE("None"),
//	NONE("None", new PointSourceDistanceCorrection() {
//
//		@Override
//		public double getCorrectedDistanceJB(double mag, PointSurface surf, double horzDist) {
//			return horzDist;
//		}
//		
//		@Override
//		public String toString() {
//			return PointSourceDistanceCorrections.NONE.name;
//		}
//		
//	}),
	FIELD("Field", new PointSourceDistanceCorrection() {
		// TODO is there a reference? more specific name?

		@Override
		public double getCorrectedDistanceJB(double mag, PointSurface surf, double horzDist) {
			// Wells and Coppersmith L(M) for "all" focal mechanisms
			// this correction comes from work by Ned Field and Bruce Worden
			// it assumes a vertically dipping straight fault with random
			// hypocenter and strike
			double rupLen =  Math.pow(10.0,-3.22+0.69*mag);
			double corrFactor = 0.7071 + (1.0-0.7071)/(1 + Math.pow(rupLen/(horzDist*0.87),1.1));
			return horzDist*corrFactor;
		}
		
		@Override
		public String toString() {
			return PointSourceDistanceCorrections.FIELD.name;
		}
		
	}),
	NSHM_2008("USGS NSHM (2008)", new DistanceCorrection2008()),
	NSHM_2013("USGS NSHM (2013)", new DistanceCorrection2013()),
	ANALYTICAL_MEDIAN("Analytical Median (centered)",
			// NaN here indicates to use mean and not a fractile
			new AnalyticalPointSourceDistanceCorrection(Double.NaN, false, false)),
	ANALYTICAL_FIVE_POINT("Analytical 5-Point (centered)",
//			AnalyticalPointSourceDistanceCorrection.getEvenlyWeightedFractiles(5, false, false)),
			AnalyticalPointSourceDistanceCorrection.getImportanceSampledFractiles(new double[] {0d, 0.05, 0.2, 0.5, 0.8, 1d}, false, false)),
	ANALYTICAL_TWENTY_POINT("Analytical 20-Point (centered)",
			AnalyticalPointSourceDistanceCorrection.getEvenlyWeightedFractiles(20, false, false));
	
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
	
	private String name;
	private WeightedList<? extends PointSourceDistanceCorrection> corrs;

	private PointSourceDistanceCorrections(String name) {
		this.name = name;
	}

	private PointSourceDistanceCorrections(String name, PointSourceDistanceCorrection corr) {
//		this(name, new WeightedList<>(List.of(corr), List.of(1d)));
		this(name, WeightedList.evenlyWeighted(corr));
	}

	private PointSourceDistanceCorrections(String name, WeightedList<? extends PointSourceDistanceCorrection> corrs) {
		this.name = name;
		Preconditions.checkState(corrs.isNormalized(), "Weights not normalized for %s", name);
		if (!(corrs instanceof WeightedList.Unmodifiable<?>))
			corrs = new WeightedList.Unmodifiable<>(corrs);
		this.corrs = corrs;
	}
	
	@Override
	public String toString() {
		return name;
	}

	@Override
	public WeightedList<? extends PointSourceDistanceCorrection> get() {
		return corrs;
	}
	
	/**
	 * Finds the enum that generated this list (if any), otherwise null
	 * @param corrs
	 * @return
	 */
	public static PointSourceDistanceCorrections forCorrections(WeightedList<? extends PointSourceDistanceCorrection> corrs) {
		if (corrs == null || corrs.isEmpty())
			return NONE;
		for (PointSourceDistanceCorrections corr : values()) {
			if (corr.corrs == corrs)
				return corr;
		}
		return null;
	}

}
