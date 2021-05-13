package org.opensha.sha.cybershake.maps;

import java.io.Serializable;

public class GMT_InterpolationSettings implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The discretization that the interpolation should be performed at. It will then be resampled
	 * to match the discretization of the basemap AFTER interpolation
	 */
	private double interpSpacing;
	
	/**
	 * Tension factor[s].  These must be between 0 and 1.  Tension may be used in the interior solution (above equation, where  it  sup‐
     * presses  spurious  oscillations)  and  in the boundary conditions (where it tends to flatten the solution approaching the edges).
     * Using zero for both values results in a minimum curvature surface with free edges, i.e. a natural  bicubic  spline.   Use  -Tten‐
     * sion_factori  to  set interior tension, and -Ttension_factorb to set boundary tension.  If you do not append i or b, both will be
     * set to the same value.  [Default = 0 for both gives minimum curvature solution.]
	 */
	private double interiorTension;
	private double exteriorTension;
	
	/**
	 * Convergence  limit.   Iteration is assumed to have converged when the maximum absolute change in any grid value is less than con‐
	 * vergence_limit.  (Units same as data z units).  [Default is scaled to 0.1 percent of typical gradient in input data.]
	 */
	private double convergenceLimit;
	
	/**
	 * search radius.  Enter search_radius in same units as x,y data; append m to indicate minutes.  This is used to initialize the grid
     * before  the first iteration; it is not worth the time unless the grid lattice is prime and cannot have regional stages.
     * [Default = 0.0 and no search is made.]
	 */
	private double searchRadius;
	
	private boolean saveInterpSurface = false;
	
	public static final String INTERP_XYZ_FILE_NAME = "map_data_interpolated.txt";
	
	public static GMT_InterpolationSettings getDefaultSettings() {
		return new GMT_InterpolationSettings(0.02, 0.0, 0.1, -1.0, 1.0);
	}
	
	public GMT_InterpolationSettings(double interpSpacing, double interiorTension, double exteriorTension,
			double convergenceLimit, double searchRadius) {
		this.interpSpacing = interpSpacing;
		this.interiorTension = interiorTension;
		this.exteriorTension = exteriorTension;
		this.convergenceLimit = convergenceLimit;
		this.searchRadius = searchRadius;
	}

	public double getInterpSpacing() {
		return interpSpacing;
	}

	public void setInterpSpacing(double interpSpacing) {
		this.interpSpacing = interpSpacing;
	}

	public double getInteriorTension() {
		return interiorTension;
	}

	public void setInteriorTension(double interiorTension) {
		this.interiorTension = interiorTension;
	}

	public double getExteriorTension() {
		return exteriorTension;
	}

	public void setExteriorTension(double exteriorTension) {
		this.exteriorTension = exteriorTension;
	}
	
	public String getTensionArg() {
		return "-T"+(float)getInteriorTension()+"i"+(float)getExteriorTension()+"b";
	}

	public double getConvergenceLimit() {
		return convergenceLimit;
	}

	public void setConvergenceLimit(double convergenceLimit) {
		this.convergenceLimit = convergenceLimit;
	}
	
	public String getConvergenceArg() {
		if (convergenceLimit <= 0)
			return "";
		return "-C"+(float)convergenceLimit;
	}

	public double getSearchRadius() {
		return searchRadius;
	}

	public void setSearchRadius(double searchRadius) {
		this.searchRadius = searchRadius;
	}
	
	public String getSearchArg() {
		return "-S\""+(float)getSearchRadius()+" \""; // surround in quotes, otherwise unit detection but in GMT 5
	}

	public boolean isSaveInterpSurface() {
		return saveInterpSurface;
	}

	public void setSaveInterpSurface(boolean saveInterpSurface) {
		this.saveInterpSurface = saveInterpSurface;
	}

}
