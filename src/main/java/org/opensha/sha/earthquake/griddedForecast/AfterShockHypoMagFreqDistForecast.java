package org.opensha.sha.earthquake.griddedForecast;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;


/**
 * <p>Title: AfterShockHypoMagFreqDistForecast</p>
 *
 * <p>Description: This class represents a poissonian aftershock hypocenter
 * forecast.
 *
 * The indexing over HypMagFreqDistAtLoc objects is exactly the same as the
 * EvenlyGriddedGeographicRegionAPI afterShockZone.</p>
 *
 * @author Nitin Gupta, Vipin Gupta and Edward (Ned) Field
 * @version 1.0
 */
public abstract class AfterShockHypoMagFreqDistForecast
    extends GriddedHypoMagFreqDistForecast {


  protected ObsEqkRupture mainShock;
  protected ObsEqkRupList afterShocks;

  /**
   * Class no-arg constructor
   */
  public AfterShockHypoMagFreqDistForecast() {
  }


  /**
   * Gets the Aftershock list for the forecast model.
   * @return ObsEqkRupList
   */
  public ObsEqkRupList getAfterShocks() {
    return afterShocks;
  }

  /**
   * Allows the user to set the AfterShockZone as EvelyGriddedGeographicRegion.
   * @return EvenlyGriddedGeographicRegionAPI AfterShockZone.
   */
  public GriddedRegion getAfterShockZone() {
    return getRegion();
  }

  /**
   * Returns the main shock
   * @return ObsEqkRupture
   */
  public ObsEqkRupture getMainShock() {
    return mainShock;
  }

  /**
   * Sets the list of ObsEqkRuptures for the given AfterShockHypoMagFreqDistForecast.
   * @param afterShocks ObsEqkRupList
   */
  public void setAfterShocks(ObsEqkRupList aftershockList) {
	//SortAftershocks_Calc afterShockCalc = new   SortAftershocks_Calc();
    //afterShocks = afterShockCalc.selectAfterShocksToNewMainshock_Calc();
	  afterShocks = aftershockList;
  }

  /**
   * addToAftershockList
   */
  public void addToAftershockList(ObsEqkRupture newAftershock) {
    afterShocks.add(newAftershock);
  }



  /**
   * Sets the mainshock event for the given forecast model.
   * @param mainShock ObsEqkRupture
   */
  public void setMainShock(ObsEqkRupture mainShock) {
    this.mainShock = mainShock;
  }
}
