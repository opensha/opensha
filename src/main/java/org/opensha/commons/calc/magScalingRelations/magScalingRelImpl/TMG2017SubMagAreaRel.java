package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.exceptions.InvalidRangeException;
import static org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.TMG2017FaultingType.*;

/**
 * <b>Title:</b>TMG2017_MagAreaRel<br>
 *
 * <b>Description:</b>
 * <p>
 * <b>Description:</b>
 * <p>
 * This implements SUBDUCTION specific magnitude versus rupture area relations
 * of Thingbaijam K.K.S., P.M. Mai and K. Goda 2017, Bull. Seism. Soc. Am., 107,
 * 2225–2246.
 * <p>
 * <p>
 * We consider rake to differentiate different broad faulting-types: interface
 * (reverse-faulting) and inslab (normal-faulting). The classification is as
 * follows: Interface (reverse-faulting): Rake angles within 45 to 135, and
 * Inslab (normal-faulting): Rake angles within -45 to -135.
 * <p>
 * Notes: [1] Relations for strike-slip events are not defined. [2] The standard
 * deviation for area as a function of mag is given for log(area) (base-10) not
 * area.
 * <p>
 * Also see: https://github.com/thingbaijam/sceqsrc
 * </p>
 *
 * @version 0.0
 */

public class TMG2017SubMagAreaRel extends MagAreaRelationship {

    final static String C = "TMG2017SubMagAreaRel";
    public final static String NAME = "Thingbaijam et al.(2017)";

    TMG2017FaultingType faultingType = NONE;

    public TMG2017SubMagAreaRel(){
        super();
    }

    public TMG2017SubMagAreaRel(double initialRake){
        super();
        setRake(initialRake);
    }

    /**
     * Computes the median magnitude from rupture area for previously set rake
     * values that distinguish between interface (reverse) events and inslab normal
     * faulting events .
     *
     * @param area in km
     * @return median magnitude MW
     */
    public double getMedianMag(double area) {

        if (NONE == faultingType || STRIKE_SLIP == faultingType) {
            return Double.NaN;
        } else if (REVERSE_FAULTING == faultingType) {
            // interface
            return 3.469 + 1.054 * Math.log(area) * lnToLog;
        } else {
            // normal faulting
            return 3.157 + 1.238 * Math.log(area) * lnToLog;
        }
    }

    /**
     * Gives the standard deviation for the magnitude as a function of area for
     * previously-set rake and regime values
     *
     * @return standard deviation
     */
    public double getMagStdDev() {
        if (NONE == faultingType || STRIKE_SLIP == faultingType) {
            return Double.NaN;
        } else if (REVERSE_FAULTING == faultingType) {
            // interface
            return 0.150;
        } else {
            // normal
            return 0.181;
        }
    }

    /**
     * Computes the median rupture area from magnitude (for the previously set rake
     * and regime values).
     *
     * @param mag - moment magnitude
     * @return median area in km
     */
    public double getMedianArea(double mag) {
        if (NONE == faultingType || STRIKE_SLIP == faultingType) {
            return Double.NaN;
        } else if (REVERSE_FAULTING == faultingType) {
            // interface
            return Math.pow(10.0, -3.292 + 0.949 * mag);
        } else {
            // inslab normal
            return Math.pow(10.0, -2.551 + 0.808 * mag);
        }
    }

    /**
     * Computes the standard deviation of log(area) (base-10) from magnitude (for
     * the previously set rake and regime values)
     *
     * @return standard deviation
     */
    public double getAreaStdDev() {
        return getMagStdDev();
    }

    /**
     * This overrides the parent method to disallow strike-slip
     * faulting type.
     *
     * @param rake
     */
    public void setRake(double rake) {
        super.setRake(rake);
        TMG2017FaultingType type = TMG2017FaultingType.fromRake(rake);
        if (NONE == type || STRIKE_SLIP == type) {
            throw new InvalidRangeException(
                    "Rake angle should be either within (45, 135) for interface or (-45, -135) for inslab-normal events"
            );
        } else {
            this.faultingType = type;
        }
    }

    /**
     * Returns the name of the object
     */
    public String getName() {
        String type;
        if (NONE == faultingType || STRIKE_SLIP == faultingType) {
            type = "InvalidRake";
        } else if (REVERSE_FAULTING == faultingType) {
            type = "Interface";
        } else {
            type = "Inslab-Normal";
        }
        return NAME + " for " + type + " events";
    }
}
