package org.opensha.sha.earthquake.util;

public class GridCellSupersamplingSettings {
	
	public static final double TARGET_SPACING_DEFAULT = 1d;
	public static final double FULL_DISTANCE_DEFAULT = 30d;
	public static final double BORDER_DISTANCE_DEFAULT = 60d;
	public static final double CORNER_DISTANCE_DEFAULT = 0d;
	public static final boolean APPLY_TO_FINITE_DEFAULT = false;
	
	public static final GridCellSupersamplingSettings DEFAULT = new GridCellSupersamplingSettings(
			TARGET_SPACING_DEFAULT, FULL_DISTANCE_DEFAULT, BORDER_DISTANCE_DEFAULT, CORNER_DISTANCE_DEFAULT, APPLY_TO_FINITE_DEFAULT);
	
	public static final GridCellSupersamplingSettings QUICK = new GridCellSupersamplingSettings(
			TARGET_SPACING_DEFAULT*1.5, FULL_DISTANCE_DEFAULT*0.666, BORDER_DISTANCE_DEFAULT*0.75, CORNER_DISTANCE_DEFAULT, APPLY_TO_FINITE_DEFAULT);

	public final double targetSpacingKM;
	public final double fullDist;
	public final double borderDist;
	public final double cornerDist;
	public final boolean applyToFinite;
	
	/**
	 * 
	 * @param targetSpacingKM target sample spacing (km)
	 * @param fullDist site-to-center distance (km) below which we should use the full resampled grid node
	 * @param borderDist site-to-center distance (km) below which we should use just the exterior of the resampled grid node
	 * @param cornerDist site-to-center distance (km) below which we should use all 4 corners of the grid cell
	 * @param applyToFinite whether or not finite surfaces should be supersample (if false, only applies to point surfaces)
	 */
	public GridCellSupersamplingSettings(double targetSpacingKM, double fullDist, double borderDist, double cornerDist, boolean applyToFinite) {
		super();
		this.targetSpacingKM = targetSpacingKM;
		this.fullDist = fullDist;
		this.borderDist = borderDist;
		this.cornerDist = cornerDist;
		this.applyToFinite = applyToFinite;
	}

	@Override
	public String toString() {
		return "GridCellSupersamplingSettings[targetSpacingKM="+(float)targetSpacingKM+", fullDist="+(float)fullDist
				+ ", borderDist="+(float)borderDist+", cornerDist="+(float)cornerDist+", applyToFinite="+applyToFinite+"]";
	}
	
}
