/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with the Southern California
 * Earthquake Center (SCEC, http://www.scec.org) at the University of Southern
 * California and the UnitedStates Geological Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package org.opensha.nshmp2.erf.source;

import java.util.Map;

import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.nshmp2.util.FaultCode;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.FrankelGriddedSurface;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.FocalMech;

/**
 * This is a custom Fixed-Strike Source (Point-Source variant) representation
 * used for the NSHMP. It was derived from the UCERF2
 * {@code Point2Vert_FaultPoisSource} and was initially created to provide built in approximations of distance and
 * hanging wall effects as well as to override getMinDistance to provide
 * consistency with distances determined during hazard calcs.
 * 
 * THis is kind of kludgy for now. In NSHMP calcs, we're not concered with dip
 * for fixed strike sources. THis is due to the fact that in the CEUS, gridded
 * finite faults are always strike slip, and in the West with NGA's, dip is
 * handled by reading the rupture rake to
 * 
 * TODO the eclosed ruptures can be much simpler than Frankel gridded surfaces
 * we're going to try settin gupper and lower seis depth to dtor
 * 
 * @author Peter Powers
 * @version $Id:$
 */

public class FixedStrikeSource extends PointSource13 {

	private static final String NAME = "NSHMP Fixed Strike Source";
	private static final String RUP_NAME = "NSHMP Fixed Strike Fault";

	private MagLengthRelationship mlr;
	private double strike = 0.0;
	private FrankelGriddedSurface surface;

	/**
	 * Constructs a new fixed-strike earthquake source. This is a variant of a
	 * {@link PointSource} where ruptures are always represented as finite
	 * faults with a fixed strike. Fault length is computed using
	 * {@link WC1994_MagLengthRelationship}.
	 * 
	 * @param loc <code>Location</code> of the point source
	 * @param mfd magnitude frequency distribution of the source
	 * @param mlr magnitude length relationship to use
	 * @param duration of the parent forecast
	 * @param depths 2 element array of rupture top depths;
	 *        <code>depths[0]</code> used for M&lt;6.5, <code>depths[1]</code>
	 *        used for M&ge;6.5
	 * @param mechWtMap <code>Map</code> of focal mechanism weights
	 * @param strike of the source
	 */
	public FixedStrikeSource(Location loc, IncrementalMagFreqDist mfd,
		MagLengthRelationship mlr, double duration, double[] depths,
		Map<FocalMech, Double> mechWtMap, double strike) {

		super(loc, mfd, duration, depths, mechWtMap);
		name = NAME;
		this.mlr = mlr;
		this.strike = strike;
	}

	/*
	 * NOTE Don't need to override initRupture(). Most fixed strike sources are
	 * relatively small so the extra baggage of a point surface (in parent) that
	 * may not be used on occasion is inconsequential.
	 * 
	 * The NSHMP uses the point location to decide if a source is in or out so
	 * no need to override getMinDistance(Site)
	 */

	@Override
	protected void updateRupture(double mag, double dip, double rake,
			double depth, double width, boolean footwall) {
		if (mag >= M_FINITE_CUT) {
			// finite rupture
			double halfLen = mlr.getMedianLength(mag) / 2;
//			System.out.println("HL: " + halfLen);
			Location faultEnd1 = LocationUtils.location(getLocation(),
				new LocationVector(strike, halfLen, 0));
			LocationVector faultVec = LocationUtils.vector(faultEnd1,
				getLocation());
			faultVec.setHorzDistance(halfLen * 2);
			Location faultEnd2 = LocationUtils.location(faultEnd1, faultVec);
			FaultTrace fault = new FaultTrace(RUP_NAME);
			fault.add(faultEnd1);
//			System.out.println("FV1: " + faultEnd1);
			fault.add(faultEnd2);
//			System.out.println("FV2: " + faultEnd2 + " dip: " + dip);
			surface = new FrankelGriddedSurface(fault, dip, depth, depth + 0.01,
				1.0);
			probEqkRupture.setMag(mag);
			probEqkRupture.setAveRake(rake);
			probEqkRupture.setRuptureSurface(surface);
		} else {
			// point rupture
			super.updateRupture(mag, dip, rake, depth, width, footwall);
		}
	}

}
