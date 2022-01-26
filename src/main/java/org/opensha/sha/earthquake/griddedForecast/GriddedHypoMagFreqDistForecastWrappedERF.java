package org.opensha.sha.earthquake.griddedForecast;


import org.opensha.sha.earthquake.ERF;
/**
 * <p>Title: GriddedHypoMagFreqDistForecastWrappedERF</p>
 *
 * <p>Description: This class wraps any Earthquake Rupture Forecast into a
 * GriddedHypoMagFreqDistForecast.</p>
 *
 * @author Nitin Gupta
 * @since Sept 16, 2005
 * @version 1.0
 */
public class GriddedHypoMagFreqDistForecastWrappedERF
    extends GriddedHypoMagFreqDistForecast {

  //ERF Object
  private ERF eqkRupForecast;

  /**
   * Class constructor that accepts the EqkRupForecast as the argument.
   * @param eqkRupforecast EqkRupForecastAPI
   */
  public GriddedHypoMagFreqDistForecastWrappedERF(ERF eqkRupForecast) {
    this.eqkRupForecast = eqkRupForecast;
  }

  /**
   * If any parameter has been changed then update the forecast.
   */
  public void updateForecast() {
    if (parameterChangeFlag) {
      eqkRupForecast.updateForecast();
    }
  }

  /**
   * gets the Hypocenter Mag.
   *
   * @param ithLocation int : Index of the location in the region
   * @return HypoMagFreqDistAtLoc Object using which user can retrieve the
   *   Magnitude Frequency Distribution.
   * @todo Implement this
   *   org.opensha.sha.earthquake.GriddedHypoMagFreqDistAtLocAPI method
   */
  public HypoMagFreqDistAtLoc getHypoMagFreqDistAtLoc(int ithLocation) {
    return null;
  }




}
