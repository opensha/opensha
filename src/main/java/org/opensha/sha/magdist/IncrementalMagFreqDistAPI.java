package org.opensha.sha.magdist;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;



/**
 *
 * <p>Title: IncrementalMagFreqDistAPI</p>
 * <p>Description:  this Interface gives the rate of EarthQuakes(number per year)in  succession. This class
 * has been made as a generalised API for others to implement depending on their functionality</p>
 *
 * @author :Nitin Gupta Date: July,26,2002
 * @version 1.0
 */

public interface IncrementalMagFreqDistAPI {


  /**
   * This function finds IncrRate for the given magnitude
   * @param mag
   * @return
   */
  public double getIncrRate(double mag) ;

    /**
     * This function finds the IncrRate at the given index
     * @param index
     * @return
     */
  public double getIncrRate(int index) ;

     /**
      * This function finds the cumulative Rate at a specified magnitude (the rate greater than
      * and equal to that magnitude)
      * @param mag
      * @return
      */
  public double getCumRate(double mag) ;

    /**
     * This function finds the cumulative Rate at a specified index  (the rate greater than
     * and equal to that index)
     * @param index
     * @return
     */
  public double getCumRate(int index) ;


    /**
      * This function finds the moment Rate at a specified magnitude
      * @param mag
      * @return
      */
  public double getMomentRate(double mag) ;

    /**
     * This function finds the moment Rate at a specified index
     * @param index
     * @return
     */
  public double getMomentRate(int index) ;

   /**
    * This function return the sum of all the moment rates as a double variable
    * @return
    */
  public double getTotalMomentRate();

  /**
   * This function returns the sum of all the incremental rate as the double varibale
   * @return
   */
  public double getTotalIncrRate();

  /**
   * This function normalises the values of all the Incremental rate at each point, by dividing each one
   * by the totalIncrRate, so that after normalization the sum addition of all incremental rate at each point
   * comes to be 1.
   */

  public void normalizeByTotalRate();


  /**
   * This returns the object of the class EvenlyDiscretizedFunc which contains all the points
   * with Incr Rate Distribution  (the rate greater than and equal to each magnitude)
   * @return
   */
  public EvenlyDiscretizedFunc getCumRateDist() ;

  /**
   * This returns the object of the class EvenlyDiscretizedFunc which contains all the points
   * with Moment Rate Distribution
   * @return
   */
  public EvenlyDiscretizedFunc getMomentRateDist() ;

  /**
   * Using this function each data point is scaled to ratio of specified newTotalMomentRate
   * and oldTotalMomentRate.
   * @param newTotMoRate
   */

  public void scaleToTotalMomentRate(double newTotMoRate);


  /**
   * Using this function each data point is scaled to the ratio of the CumRate at a given
   * magnitude and the specified rate.
   * @param mag
   * @param rate
   */

  public void scaleToCumRate(double mag,double rate);

  /**
   * Using this function each data point is scaled to the ratio of the CumRate at a given
   * index and the specified rate
   * @param index
   * @param rate
   */

  public void scaleToCumRate(int index,double rate);

   /**
    * Using this function each data point is scaled to the ratio of the IncrRate at a given
    * magnitude and the specified newRate
    * @param mag
    * @param newRate
    */
  public void scaleToIncrRate(double mag, double newRate) ;

   /**
    * Using this function each data point is scaled to the ratio of the IncrRate at a given
    * index and the specified newRate
    * @param index
    * @param newRate
    */

  public void scaleToIncrRate(int index, double newRate);

}
