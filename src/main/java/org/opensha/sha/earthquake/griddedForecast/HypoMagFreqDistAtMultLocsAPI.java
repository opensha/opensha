package org.opensha.sha.earthquake.griddedForecast;

/**
 * <p>Title: GriddedHypoMagFreqDistAtLocAPI </p>
 *
 * <p>Description: This API constitutes an interface to a number of
 * HypoMagFreqDistAtLoc objects (e.g., for multiple locations, although each
 * location is not necessarily unique).</p>
 *
 * Note : Additiional info needs to be added like binning of Lat, Lon and Depth).
 * @author Nitin Gupta, Vipin Gupta and Edward (Ned) Field
 *
 * @version 1.0
 */
public interface HypoMagFreqDistAtMultLocsAPI {




  /**
   * This gets the HypoMagFreqDistAtLoc for the ith location.
   * @param ithLocation int : Index of the location in the region
   * @return HypoMagFreqDistAtLoc Object.
   *
   * Note : This always gives out yearly Rate.
   */
  public HypoMagFreqDistAtLoc getHypoMagFreqDistAtLoc(int ithLocation);

  public int getNumHypoLocs();

}
