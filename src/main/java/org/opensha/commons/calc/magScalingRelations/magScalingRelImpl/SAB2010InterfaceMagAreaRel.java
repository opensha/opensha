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
 * <b>Title:</b>SAB2010InterfaceMagAreaRel<br>
 *
 * <b>Description:</b>
 * <p>
 * <b>Description:</b>
 * <p>
 * Implements Strasser, Arango and Bommer magnitude -- rupture area relationships for
 * interface events.
 * <p>
 * See F. O. Strasser, M. C. Arango, and J. J. Bommer Scaling of the Source
 * Dimensions of Interface and Intraslab Subduction-zone Earthquakes with
 * Moment Magnitude Seismological Research Letters, November/December 2010,
 * v. 81, p. 941-950, doi:10.1785/gssrl.81.6.941
 * Implements both magnitude-area and area-magnitude scaling relationships.
 * <p>
 * Also see: https://github.com/gem/oq-engine/blob/master/openquake/hazardlib/scalerel/strasser2010.py
 * </p>
 *
 * @version 0.0
 */

public class SAB2010InterfaceMagAreaRel extends MagAreaRelationship {

    final static String C = "SAB2010InterfaceMagAreaRel";
    public final static String NAME = "Strasser et al. (2010)";

    /**
     * Computes the median magnitude from rupture area
     * rake is ignored
     *
     * @param area in km
     * @return median magnitude MW
     */
    public double getMedianMag(double area) {
        return (4.441 + 0.846 * Math.log(area) * lnToLog);
    }

    /**
     * Gives the standard deviation for the magnitude as a function of area
     * rake is ignored
     *
     * @param area in km
     * @return standard deviation
     */
    public double getMagStdDev() {
        return 0.286;
    }


    /**
     * Computes the median rupture area from magnitude
     * Rake is ignored
     *
     * @param mag - moment magnitude
     * @return median area in km
     */

    public double getMedianArea(double mag) {
        return Math.pow(10.0, -3.476 + 0.952 * mag);
    }

    /**
     * Computes the standard deviation of log(area) (base-10) from magnitude (for
     * the previously set rake and regime values)
     *
     * @return standard deviation
     */
    public double getAreaStdDev() {
        return 0.304;
    }


    /**
     * Returns the name of the object
     */
    public String getName() {
        return NAME + " for interface events";
    }
}
