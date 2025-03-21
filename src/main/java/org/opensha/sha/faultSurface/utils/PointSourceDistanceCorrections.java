package org.opensha.sha.faultSurface.utils;

import java.util.EnumSet;
import java.util.function.Supplier;

import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.rupForecastImpl.PointSourceNshm.DistanceCorrection2013;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.util.NSHMP_Util;

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
	NSHM_2008("USGS NSHM (2008)", new PointSourceDistanceCorrection() {
		
		private EvenlyDiscretizedFunc magBinFunc = new EvenlyDiscretizedFunc(6.05, 26, 0.1);

		@Override
		public double getCorrectedDistanceJB(double mag, PointSurface surf, double horzDist) {
			if(mag<=6) {
				return horzDist;
			} else if (horzDist == 0d) {
				return 0d;
			} else { //if (mag<=7.6) {
				// NSHMP getMeanRJB is built on the assumption of 0.05 M
				// centered bins. Non-UCERF erf's often do not make
				// this assumption and are 0.1 based so we push
				// the value down to the next closest compatible M
				
				// this was Peter's original correction, but it explodes if it's given say 6.449999999999999 (which converts to 6.39999999999999)
//				double adjMagAlt = ((int) (mag*100) % 10 != 5) ? mag - 0.05 : mag;
				// this doesn't work either for values like 6.0 and 6.1 (only works when close to an 0.x5)
//				double adjMag = ((double)Math.round(mag/0.05))*0.05;
//				if (adjMag > 8.6) adjMag = 8.55;
				// this works
				double nearestTenth = Math.round(mag*10)/10d;
//				System.out.println("Nearest 10th to "+mag+" is "+nearestTenth);
				if ((float)nearestTenth > 6f && (float)nearestTenth == (float)mag)
					// we're right at a 10th and want it to always round down
					// e.g., we don't want 6.449999999999999 to round down, but 6.450000000000001 to round up
					// so subtract a tiny bit from the nearest tenth to force it to always round down
					mag = nearestTenth - 0.0001;
				double adjMag = magBinFunc.getX(magBinFunc.getClosestXIndex(mag));
//				if(adjMagAlt != adjMag)
//					System.out.println("mag,adj,alt:\t"+mag+"\t"+adjMag+"\t"+adjMagAlt);
//				System.out.println("\tadjMag="+(float)adjMag);
				return NSHMP_Util.getMeanRJB(adjMag, horzDist);
			}
		}
		
		@Override
		public String toString() {
			return PointSourceDistanceCorrections.NSHM_2008.name;
		}
		
	}),
	NSHM_2013("USGS NSHM (2013)", new DistanceCorrection2013()),
	ANALYTICAL_MEDIAN("Analytical Median (centered)",
			// NaN here indicates to use mean and not a fractile
			new AnalyticalPointSourceDistanceCorrection(Double.NaN, false, false)),
	ANALYTICAL_FIVE_POINT("Analytical 5-Point (centered)",
//			AnalyticalPointSourceDistanceCorrection.getEvenlyWeightedFractiles(5, false, false)),
			AnalyticalPointSourceDistanceCorrection.getImportanceSampledFractiles(new double[] {0d, 0.05, 0.2, 0.5, 0.8, 1d}, false, false));
	
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
