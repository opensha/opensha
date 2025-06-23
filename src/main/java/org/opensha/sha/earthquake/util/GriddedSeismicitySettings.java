package org.opensha.sha.earthquake.util;

import org.opensha.commons.data.WeightedList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrection;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;

import com.google.common.base.Preconditions;

/**
 * Settings for gridded seismicity ruptures, initially designed for use with a {@link GridSourceProvider}.
 */
public class GriddedSeismicitySettings {
	
	public static GriddedSeismicitySettings DEFAULT = new GriddedSeismicitySettings(
			// minimum magnitude
			5d, // M>5
			// rupture surface type
			BackgroundRupType.POINT, // point sources
			// minimum magnitude for finite (if FINITE selected, below this will still be POINT)
			// also applies with POINT selected if finite ruptures have strikes
			6d, // always finite >6
//			5d,
			// distance corrections
			PointSourceDistanceCorrections.DEFAULT.get(),
			// supersampling
			null, // none
			// finite rupture settings
			null); // none (note neded with POINT selected above)

	/**
	 * Minimum magnitude; ruptures below this magnitude will be ignored
	 */
	public final double minimumMagnitude;
	/**
	 * Surface type, e.g., point or finite
	 */
	public final BackgroundRupType surfaceType;
	/**
	 * Magnitude below which point surfaces should always be generated regardless of {@link #surfaceType} choice.
	 * No distance corrections will be applied below this magnitude.
	 */
	public final double pointSourceMagnitudeCutoff;
	/**
	 * Distance corrections to be applied to point surfaces (i.e., all surfaces if the surface type is
	 * {@link BackgroundRupType#POINT} and no strike information is provided, or ruptures below
	 * {@link #pointSourceMagnitudeCutoff}). Can be null;
	 */
	public final PointSourceDistanceCorrection distanceCorrection;
	/**
	 * Supersampling settings to further subdivide each grid cell into many sources when calculating hazard for
	 * nearby sites.
	 */
	public final GridCellSupersamplingSettings supersamplingSettings;
	/**
	 * Finite rupture settings (used if surfaceType == FINITE)
	 */
	public final GriddedFiniteRuptureSettings finiteRuptureSettings;
	
	private GriddedSeismicitySettings(
			double minimumMagnitude,
			BackgroundRupType surfaceType,
			double pointSourceMagnitudeCutoff,
			PointSourceDistanceCorrection distanceCorr,
			GridCellSupersamplingSettings supersamplingSettings,
			GriddedFiniteRuptureSettings finiteRuptureSettings) {
		super();
		Preconditions.checkState(surfaceType != null, "Surface type cannot be null");
		this.minimumMagnitude = minimumMagnitude;
		this.surfaceType = surfaceType;
		this.pointSourceMagnitudeCutoff = pointSourceMagnitudeCutoff;
		this.distanceCorrection = distanceCorr;
		this.supersamplingSettings = supersamplingSettings;
		Preconditions.checkState(surfaceType == BackgroundRupType.POINT || finiteRuptureSettings != null);
		this.finiteRuptureSettings = finiteRuptureSettings;
	}
	
	/**
	 * @param minimumMagnitude
	 * @return a copy with the minimum magnitude setting changed to that passed in
	 */
	public GriddedSeismicitySettings forMinimumMagnitude(double minimumMagnitude) {
		if (this.minimumMagnitude == minimumMagnitude)
			return this;
		return new GriddedSeismicitySettings(minimumMagnitude, surfaceType,
				pointSourceMagnitudeCutoff, distanceCorrection, supersamplingSettings, finiteRuptureSettings);
	}
	
	/**
	 * @param surfaceType
	 * @return a copy with the surface type setting changed to that passed in
	 */
	public GriddedSeismicitySettings forSurfaceType(BackgroundRupType surfaceType) {
		if (this.surfaceType == surfaceType)
			return this;
		GriddedFiniteRuptureSettings finiteRuptureSettings = this.finiteRuptureSettings;
		if (surfaceType == BackgroundRupType.FINITE && finiteRuptureSettings == null)
			finiteRuptureSettings = GriddedFiniteRuptureSettings.DEFAULT;
		return new GriddedSeismicitySettings(minimumMagnitude, surfaceType,
				pointSourceMagnitudeCutoff, distanceCorrection, supersamplingSettings, finiteRuptureSettings);
	}
	
	/**
	 * @param pointSourceMagnitudeCutoff
	 * @return a copy with the point source magnitude cutoff setting changed to that passed in
	 */
	public GriddedSeismicitySettings forPointSourceMagCutoff(double pointSourceMagnitudeCutoff) {
		if (this.pointSourceMagnitudeCutoff == pointSourceMagnitudeCutoff)
			return this;
		return new GriddedSeismicitySettings(minimumMagnitude, surfaceType,
				pointSourceMagnitudeCutoff, distanceCorrection, supersamplingSettings, finiteRuptureSettings);
	}
	
	/**
	 * @param distanceCorrections
	 * @return a copy with the distance correction changed to that passed in
	 */
	public GriddedSeismicitySettings forDistanceCorrection(PointSourceDistanceCorrection distanceCorrection) {
		if (this.distanceCorrection == distanceCorrection)
			return this;
		return new GriddedSeismicitySettings(minimumMagnitude, surfaceType,
				pointSourceMagnitudeCutoff, distanceCorrection, supersamplingSettings, finiteRuptureSettings);
	}
	
	/**
	 * @param supersamplingSettings
	 * @return a copy with the supersampling settings changed to that passed in
	 */
	public GriddedSeismicitySettings forSupersamplingSettings(GridCellSupersamplingSettings supersamplingSettings) {
		if (this.supersamplingSettings == supersamplingSettings)
			return this;
		return new GriddedSeismicitySettings(minimumMagnitude, surfaceType,
				pointSourceMagnitudeCutoff, distanceCorrection, supersamplingSettings, finiteRuptureSettings);
	}
	
	/**
	 * @param supersamplingSettings
	 * @return a copy with the supersampling settings changed to that passed in
	 */
	public GriddedSeismicitySettings forFiniteRuptureSettings(GriddedFiniteRuptureSettings finiteRuptureSettings) {
		if (this.finiteRuptureSettings == finiteRuptureSettings)
			return this;
		return new GriddedSeismicitySettings(minimumMagnitude, surfaceType,
				pointSourceMagnitudeCutoff, distanceCorrection, supersamplingSettings, finiteRuptureSettings);
	}

	@Override
	public String toString() {
		return "GriddedSeismicitySettings [minMag=" + minimumMagnitude + ", type=" + surfaceType.name()
				+ ", ptSrcMagCut=" + pointSourceMagnitudeCutoff + ", distCorr="
				+ distanceCorrection + ", superssample=" + supersamplingSettings
				+ ", finiteRuptureSettings=" + finiteRuptureSettings + "]";
	}

}
