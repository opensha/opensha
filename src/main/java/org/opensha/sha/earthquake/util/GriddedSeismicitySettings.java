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
			5d, // M>5
			BackgroundRupType.POINT, // point sources
			6d, // always finite >6 (if set to a finite option, or point sources have strikes)
//			5d,
			PointSourceDistanceCorrections.DEFAULT, // NSHM 2013 distance correction
			null); // no supersampling

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
	 */
	public final double pointSourceMagnitudeCutoff;
	/**
	 * Distance corrections to be applied to point surfaces (i.e., all surfaces if the surface type is
	 * {@link BackgroundRupType#POINT} and no strike information is provided, or ruptures below
	 * {@link #pointSourceMagnitudeCutoff}). Can be null;
	 */
	public final WeightedList<PointSourceDistanceCorrection> distanceCorrections;
	/**
	 * Supersampling settings to further subdivide each grid cell into many sources when calculating hazard for
	 * nearby sites.
	 */
	public final GridCellSupersamplingSettings supersamplingSettings;
	
	private GriddedSeismicitySettings(
			double minimumMagnitude,
			BackgroundRupType surfaceType,
			double pointSourceMagnitudeCutoff,
			PointSourceDistanceCorrections distanceCorrs,
			GridCellSupersamplingSettings supersamplingSettings) {
		this(minimumMagnitude, surfaceType, pointSourceMagnitudeCutoff,
				distanceCorrs == null ? null : distanceCorrs.get(), supersamplingSettings);
	}
	
	private GriddedSeismicitySettings(
			double minimumMagnitude,
			BackgroundRupType surfaceType,
			double pointSourceMagnitudeCutoff,
			WeightedList<PointSourceDistanceCorrection> distanceCorrs,
			GridCellSupersamplingSettings supersamplingSettings) {
		super();
		Preconditions.checkState(surfaceType != null, "Surface type cannot be null");
		this.minimumMagnitude = minimumMagnitude;
		this.surfaceType = surfaceType;
		this.pointSourceMagnitudeCutoff = pointSourceMagnitudeCutoff;
		this.distanceCorrections = distanceCorrs;
		this.supersamplingSettings = supersamplingSettings;
	}
	
	/**
	 * @param minimumMagnitude
	 * @return a copy with the minimum magnitude setting changed to that passed in
	 */
	public GriddedSeismicitySettings forMinimumMagnitude(double minimumMagnitude) {
		if (this.minimumMagnitude == minimumMagnitude)
			return this;
		return new GriddedSeismicitySettings(minimumMagnitude, surfaceType,
				pointSourceMagnitudeCutoff, distanceCorrections, supersamplingSettings);
	}
	
	/**
	 * @param surfaceType
	 * @return a copy with the surface type setting changed to that passed in
	 */
	public GriddedSeismicitySettings forSurfaceType(BackgroundRupType surfaceType) {
		if (this.surfaceType == surfaceType)
			return this;
		return new GriddedSeismicitySettings(minimumMagnitude, surfaceType,
				pointSourceMagnitudeCutoff, distanceCorrections, supersamplingSettings);
	}
	
	/**
	 * @param pointSourceMagnitudeCutoff
	 * @return a copy with the point source magnitude cutoff setting changed to that passed in
	 */
	public GriddedSeismicitySettings forPointSourceMagCutoff(double pointSourceMagnitudeCutoff) {
		if (this.pointSourceMagnitudeCutoff == pointSourceMagnitudeCutoff)
			return this;
		return new GriddedSeismicitySettings(minimumMagnitude, surfaceType,
				pointSourceMagnitudeCutoff, distanceCorrections, supersamplingSettings);
	}
	
	/**
	 * @param distanceCorrs
	 * @return a copy with the distance corrections changed to those passed in
	 */
	public GriddedSeismicitySettings forDistanceCorrections(PointSourceDistanceCorrections distanceCorrs) {
		if (distanceCorrs != null && this.distanceCorrections == distanceCorrs.get())
			return this;
		return new GriddedSeismicitySettings(minimumMagnitude, surfaceType,
				pointSourceMagnitudeCutoff, distanceCorrs, supersamplingSettings);
	}
	
	/**
	 * @param distanceCorrections
	 * @return a copy with the distance corrections changed to those passed in
	 */
	public GriddedSeismicitySettings forDistanceCorrections(WeightedList<PointSourceDistanceCorrection> distanceCorrections) {
		if (this.distanceCorrections == distanceCorrections)
			return this;
		return new GriddedSeismicitySettings(minimumMagnitude, surfaceType,
				pointSourceMagnitudeCutoff, distanceCorrections, supersamplingSettings);
	}
	
	/**
	 * @param supersamplingSettings
	 * @return a copy with the supersampling settings changed to that passed in
	 */
	public GriddedSeismicitySettings forSupersamplingSettings(GridCellSupersamplingSettings supersamplingSettings) {
		if (this.supersamplingSettings == supersamplingSettings)
			return this;
		return new GriddedSeismicitySettings(minimumMagnitude, surfaceType,
				pointSourceMagnitudeCutoff, distanceCorrections, supersamplingSettings);
	}

	@Override
	public String toString() {
		return "GriddedSeismicitySettings [minMag=" + minimumMagnitude + ", type=" + surfaceType
				+ ", ptSrcMagCut=" + pointSourceMagnitudeCutoff + ", distCorrs="
				+ distanceCorrections + ", superssample=" + supersamplingSettings + "]";
	}

}
