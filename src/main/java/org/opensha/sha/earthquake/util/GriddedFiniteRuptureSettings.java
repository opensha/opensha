package org.opensha.sha.earthquake.util;

import com.google.common.base.Preconditions;

/**
 * Settings for gridded seismicity finite rupture properties; used when BackgroundRupType is FINITE
 * 
 * TODO: GUI parameter editor
 */
public class GriddedFiniteRuptureSettings {
	

	public static final GriddedFiniteRuptureSettings DEFAULT_SINGLE =
			new GriddedFiniteRuptureSettings(1, null, false, false);
	public static final GriddedFiniteRuptureSettings DEFAULT_CROSSHAIR =
			new GriddedFiniteRuptureSettings(2, null, false, false);
	
	public static final GriddedFiniteRuptureSettings DEFAULT = DEFAULT_CROSSHAIR;
	
	/**
	 * The number of finite surfaces per rupture
	 */
	public final int numSurfaces;
	
	/**
	 * The strike (of the first surface if numSurfaces>1) in decimal degrees, or null to randomly sample
	 */
	public final Double strike;
	
	/**
	 * If true, finite surfaces will be randomly sampled such that the grid node can be anywhere along-strike
	 * of the rupture surface (assuming a uniform distribution). If false, the rupture will be centered along-strike
	 * about the grid node.
	 */
	public final boolean sampleAlongStrike;
	
	/**
	 * If true, finite surfaces will be randomly sampled such that the grid node can be anywhere down-dip
	 * of the rupture surface (assuming a uniform distribution). If false, the rupture will be centered down-dip
	 * about the grid node.
	 */
	public final boolean sampleDownDip;

	public GriddedFiniteRuptureSettings(int numSurfaces, Double strike, boolean sampleAlongStrike,
			boolean sampleDownDip) {
		super();
		Preconditions.checkState(numSurfaces > 0);
		this.numSurfaces = numSurfaces;
		Preconditions.checkState(strike == null || Double.isFinite(strike));
		this.strike = strike;
		this.sampleAlongStrike = sampleAlongStrike;
		this.sampleDownDip = sampleDownDip;
	}
	
	public GriddedFiniteRuptureSettings forNumSurfaces(int numSurfaces) {
		Preconditions.checkState(numSurfaces > 0);
		return new GriddedFiniteRuptureSettings(numSurfaces, strike, sampleAlongStrike, sampleDownDip);
	}
	
	public GriddedFiniteRuptureSettings forStrike(Double strike) {
		Preconditions.checkState(strike == null || Double.isFinite(strike));
		return new GriddedFiniteRuptureSettings(numSurfaces, strike, sampleAlongStrike, sampleDownDip);
	}
	
	public GriddedFiniteRuptureSettings forRandStrike() {
		return new GriddedFiniteRuptureSettings(numSurfaces, null, sampleAlongStrike, sampleDownDip);
	}
	
	public GriddedFiniteRuptureSettings forSampleAlongStrike(boolean sampleAlongStrike) {
		return new GriddedFiniteRuptureSettings(numSurfaces, strike, sampleAlongStrike, sampleDownDip);
	}
	
	public GriddedFiniteRuptureSettings forSampleDownDip(boolean sampleDownDip) {
		return new GriddedFiniteRuptureSettings(numSurfaces, strike, sampleAlongStrike, sampleDownDip);
	}

	@Override
	public String toString() {
		return "GriddedFiniteRuptureSettings [numSurfaces=" + numSurfaces + ", strike=" + strike
				+ ", sampleAlongStrike=" + sampleAlongStrike + ", sampleDownDip=" + sampleDownDip + "]";
	}

}
