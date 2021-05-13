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

package org.opensha.sha.earthquake.observedEarthquake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.ListIterator;

import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.util.EqkRuptureMagComparator;

/**
 * <p>Title: ObsEqkRupList</p>
 *
 * <p>Description: This class provide capabilty of storing observed events as
 * a list. Also defines the function using which user can extract a subset of the
 * list based on Magnitude  range, origin time or geographic Region.
 * It also defines functions that allows user to Sort the list based on Mag,
 * Origin Time.</p>
 *
 * @author Nitin Gupta, Vipin Gupta and Edward (Ned) Field
 * @version 1.0
 */


public class ObsEqkRupList extends ArrayList<ObsEqkRupture> implements java.io.Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/** Class name used for debugging purposes */
	protected final static String C = "ObsEqkRupList";

	/** if true print out debugging statements */
	protected final static boolean D = false;

	/**
	 * Returns the list of the Observed events above/at the given magnitude.
	 * @param mag double Magnitude
	 * @return the subset of total observed events as ObsEqkRupList list
	 * above a given magnitude
	 */
	public ObsEqkRupList getRupsAboveMag(double mag) {
		ObsEqkRupList obsEventList = new ObsEqkRupList();
		for (ObsEqkRupture eqkRup : this) {
			if (eqkRup.getMag() >= mag)
				obsEventList.add(eqkRup);
		}
		return obsEventList;
	}

	/**
	 * Returns the list of the Observed events below the given magnitude.
	 * @param mag double Magnitude
	 * @return the subset of total observed events as ObsEqkRupList list
	 * below a given magnitude
	 */
	public ObsEqkRupList getRupsBelowMag(double mag) {
		ObsEqkRupList obsEventList = new ObsEqkRupList();
		for (ObsEqkRupture eqkRup : this) {
			if (eqkRup.getMag() < mag)
				obsEventList.add(eqkRup);

		}
		return obsEventList;
	}

	/**
	 * Returns the list of the Observed events between 2 given magnitudes.
	 * It includes lower magnitude in the range but excludes the upper magnitude.
	 * @param mag1 double lower magnitude
	 * @param mag2 double upper magnitude
	 * @return the subset of total observed events as ObsEqkRupList list
	 * between 2 given magnitudes.
	 */
	public ObsEqkRupList getRupsBetweenMag(double mag1, double mag2) {
		ObsEqkRupList obsEventList = new ObsEqkRupList();
		for (ObsEqkRupture eqkRup : this) {
			double eventMag = eqkRup.getMag();
			if (eventMag >= mag1 && eventMag < mag2)
				obsEventList.add(eqkRup);
		}
		return obsEventList;

	}

	/**
	 * Returns the list of Observed events before a given time in milliseconds (epoch)
	 * @param timeInMillis - what returned by GregorianCalendar.getTimeInMillis()
	 * @return the subset of total observed events as ObsEqkRupList list
	 * before a given time period
	 */
	public ObsEqkRupList getRupsBefore(long timeInMillis) {
		ObsEqkRupList obsEventList = new ObsEqkRupList();
		for (ObsEqkRupture eqkRup : this) {
			long eventTime = eqkRup.getOriginTime();
			if (eventTime < timeInMillis)
				obsEventList.add(eqkRup);
		}
		return obsEventList;
	}



	/**
	 * Returns the list of Observed events after a given time in milliseconds (epoch)
	 * @param timeInMillis - what returned by GregorianCalendar.getTimeInMillis()
	 * @return the subset of total observed events as ObsEqkRupList list
	 * after a given time period
	 */
	public ObsEqkRupList getRupsAfter(long timeInMillis) {
		ObsEqkRupList obsEventList = new ObsEqkRupList();
		for (ObsEqkRupture eqkRup : this) {
			long eventTime = eqkRup.getOriginTime();
			if (eventTime > timeInMillis)
				obsEventList.add(eqkRup);
		}
		return obsEventList;

	}

	/**
	 * Returns the list of the Observed events between 2 given time periods.
	 * @param cal1 GregorianCalendar Time Period
	 * @param cal2 GregorianCalendar Time Period
	 * @return the subset of total observed events as ObsEqkRupList list
	 * between 2 given time periods.
	 */
	public ObsEqkRupList getRupsBetween(long timeInMillis1,
			long timeInMillis2) {
		ObsEqkRupList obsEventList = new ObsEqkRupList();
		for (ObsEqkRupture eqkRup : this) {
			long eventTime = eqkRup.getOriginTime();
			if (eventTime > timeInMillis1 && eventTime < timeInMillis2)
				obsEventList.add(eqkRup);
		}
		return obsEventList;

	}

	/**
	 * Returns the list of the Observed events inside a given geographic region
	 * @param region Region
	 * @return the subset of total observed events as ObsEqkRupList list
	 * inside a given region.
	 */
	public ObsEqkRupList getRupsInside(Region region) {
		ObsEqkRupList obsEventList = new ObsEqkRupList();
		for (ObsEqkRupture eqkRup : this) {
			Location loc = eqkRup.getHypocenterLocation();
			if(region.contains(loc))
				obsEventList.add(eqkRup);
		}
		return obsEventList;

	}

	/**
	 * Returns the list of the Observed events outside a given geographic region
	 * @param region Region
	 * @return the subset of total observed events as ObsEqkRupList list
	 * outside a given region.
	 */
	public ObsEqkRupList getRupsOutside(Region region) {
		ObsEqkRupList obsEventList = new ObsEqkRupList();
		for (ObsEqkRupture eqkRup : this) {
			Location loc = eqkRup.getHypocenterLocation();
			if (!region.contains(loc))
				obsEventList.add(eqkRup);
		}
		return obsEventList;
	}


	/**
	 * Sorts the Observed Eqk Rupture Event list based on the magitude.
	 *
	 */
	public void sortByMag(){
		Collections.sort(this, new EqkRuptureMagComparator());
	}

	/**
	 * Sorts the Observed Eqk Rupture Event list based on the Origin time.
	 *
	 */
	public void sortByOriginTime() {
		Collections.sort(this, new ObsEqkRupOrigTimeComparator());
	}

}
