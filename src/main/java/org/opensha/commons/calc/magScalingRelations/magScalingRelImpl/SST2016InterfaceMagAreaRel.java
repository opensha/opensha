/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.util.FaultUtils;

/**
 * <b>Title:</b>SST2016InterfaceMagAreaRel<br>
 *
 * <b>Description:</b>
 * <p>
 * <b>Description:</b>
 * <p>
 * Implements Skarlatoudis, A.A., Somerville, P. G., and Thio, H.K. (2016).
 * Source-Scaling Relations of Interface Subduction Earthquakes for Strong Ground Motion and Tsunami Simulation.
 * Bulletin of the Seismological Society of America, Vol. 106, No. 4, pp. 1652–1662, August 2016, doi: 10.1785/0120150320
 * <p>
 * Mo(Nm) = ((LOG10(A)-LOG10(0.000000000177))*3)/2 A in km2
 * Mw = ((Mo+7)-16.05)/1.5
 *
 * </p>
 *
 * @version 0.0
 */

public class SST2016InterfaceMagAreaRel extends MagAreaRelationship {

    final static String C = "SST2016InterfaceMagAreaRel";
    public final static String NAME = "Skarlatoudis et al. (2016)";

    /**
     * Computes the median magnitude from rupture area
     * rake is ignored
     *
     * @param area in km
     * @return median magnitude MW
     */
    public double getMedianMag(double area) {
        double log10Mo;
        log10Mo = 1.5 * (Math.log10(area) - Math.log10(0.000000000177));

        return (log10Mo - 9.05) / 1.5;
    }

    /**
     * Gives the standard deviation for the magnitude as a function of area
     * rake is ignored
     * standard deviation is unknown
     *
     * @return standard deviation
     */
    public double getMagStdDev() {
        return Double.NaN;
    }


    /**
     * Computes the median rupture area from magnitude
     * Rake is ignored
     *
     * @param mag - moment magnitude
     * @return median area in km
     */

    public double getMedianArea(double mag) {
        double log10Mo;

        log10Mo = 1.5 * mag + 9.05;
        return Math.pow(10.0, (log10Mo / 1.5 + Math.log10(0.000000000177)));
    }

    /**
     * Computes the standard deviation of log(area) (base-10) from magnitude (for
     * the previously set rake and regime values)
     *
     * @return standard deviation
     */
    public double getAreaStdDev() {
        return 1.498;
    }


    /**
     * Returns the name of the object
     */
    public String getName() {
        return NAME + " for interface events";
    }
}
