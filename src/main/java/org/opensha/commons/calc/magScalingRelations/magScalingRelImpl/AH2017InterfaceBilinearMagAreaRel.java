package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.util.FaultUtils;

/**
 * <b>Title:</b>AH2017InterfaceBilinearMagAreaRel<br>
 *
 * <b>Description:</b>
 * <p>
 * <b>Description:</b>
 * <p>
 * Implements Allen and Hayes Alternative Rupture-Scaling Relationships for Subduction
 * Interface and Other Offshore Environments interface events.
 * <p>
 * Bulletin of the Seismological Society of America, Vol. 107, No. 3,
 * pp. 1240–1253, June 2017, doi: 10.1785/0120160255
 * <p>
 * Implements the bilinear option for both magnitude-area and area-magnitude
 * scaling relationships.
 * <p>
 * Also see - https://github.com/gem/oq-engine/blob/master/openquake/hazardlib/scalerel/allenhayes2017.py
 *
 * </p>
 *
 * @version 0.0
 */

public class AH2017InterfaceBilinearMagAreaRel extends MagAreaRelationship {

    final static String C = "AH2017InterfaceBilinearMagAreaRel";
    public final static String NAME = "Allen and Hayes (2017)";

    /**
     * Computes the median magnitude from rupture area
     * rake is ignored
     *
     * @param area in km
     * @return median magnitude MW
     */
    public double getMedianMag(double area) {

        if (area <= 74000) {
            return (Math.log(area) * lnToLog + 5.62) / 1.22;
        } else {
            return (Math.log(area) * lnToLog - 2.23) / 0.31;
        }
    }

    /**
     * Gives the standard deviation for the magnitude as a function of area
     * rake is ignored
     *
     * @param area in km
     * @return standard deviation
     */
    public double getMagStdDev() {
        return 0.266;
    }

    /**
     * Computes the median rupture area from magnitude
     * Rake is ignored
     *
     * @param mag - moment magnitude
     * @return median area in km
     */
    public double getMedianArea(double mag) {

        if (mag <= 8.63) {
            return Math.pow(10.0, -5.62 + 1.22 * mag);
        } else {
            return Math.pow(10.0, 2.23 + 0.31 * mag);
        }

    }

    /**
     * Computes the standard deviation of log(area) (base-10) from magnitude (for
     * the previously set rake and regime values)
     *
     * @return standard deviation
     */
    public double getAreaStdDev() {
        return 0.256;
    }


    /**
     * Returns the name of the object
     */
    public String getName() {
        return NAME + " for interface events";
    }
}
