package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

//double check what's needed

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;

/**
 * <b>Title:</b>PEER_testsMagAreaRelationship<br>
 *
 * <b>Description:</b>  This implements the mag-area relationship defined for the PEER
 * PSHA test cases (based on log(A)=Mag-4.0 w/ stdDev=0.25. .  All calculations are
 * completely independent of rake (i.e., you can set the rake but it will be ignored).  <p>
 *
 * @author Edward H. Field
 * @version 1.0
 */

public class PEER_testsMagAreaRelationship extends MagAreaRelationship {

    final static String C = "PEER_testsMagAreaRelationship";

    public final static String NAME = "PEER Tests Mag-Area Rel.";


    /**
     * Computes the median magnitude from rupture area
     * @param area in km-squared
     * @return median magnitude
     */
    public double getMedianMag(double area) {
      return lnToLog*Math.log(area) + 4.0;
    }

    /**
     * Gives the standard deviation for the magnitude as a function of area
     * @param area in km-squared
     * @return this returns NaN because I'm not sure the
     */
    public double getMagStdDev() {
      return 0.25;
    }

    /**
     * Computes the median rupture area from magnitude
     * @param mag - moment magnitude
     * @return median area in km-squared
     */
    public double getMedianArea(double mag) {
      return Math.pow(10,mag-4.0);
    }

    /**
     * Computes the standard deviation of log(area) (base-10) from magnitude
     * @param mag - moment magnitude
     * @param rake in degrees
     * @return standard deviation
     */
    public double getAreaStdDev() {
      return 0.25;
    }

    /**
     * Returns the name of the object
     *
     */
    public String getName() {
      return NAME;
    }
/*
    public static void main(String args[]) {
      PEER_testsMagAreaRelationship magRel = new PEER_testsMagAreaRelationship();
      System.out.println("Area(6)="+magRel.getMedianArea(6.0));
      System.out.println("Mag(1000)="+magRel.getMedianMag(1000));
    }
*/

}
