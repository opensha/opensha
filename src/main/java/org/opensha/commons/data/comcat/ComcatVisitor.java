package org.opensha.commons.data.comcat;

import gov.usgs.earthquake.event.JsonEvent;

import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;


/**
 * Visitor for Comcat results.
 * Author: Michael Barall 06/06/2019.
 *
 * This interface defines a function that is called for each earthquake
 * obtained from a Comcat query.
 */
public interface ComcatVisitor {


	/**
	 * visit - Visit an earthquake obtained from a Comcat query.
	 * @param rup = Earthquake rupture.  It is guaranteed that this is non-null
	 *              and contains valid information.
	 * @param geojson = GeoJSON for the earthquake, as supplied by Comcat.  This may
	 *                  be null if the earthquake info is obtained from a source
	 *                  other than Comcat, e.g., a local earthquake catalog.  Note
	 *                  that JsonEvent is a subclass of org.json.simple.JSONObject.
	 * @return
	 * Return zero to continue visiting other earthquakes obtained from Comcat.
	 * Return non-zero to immediately stop visiting earthquakes;  in this case,
	 * the non-zero value is returned from the Comcat visitEventList routine.
	 */
	public int visit (ObsEqkRupture rup, JsonEvent geojson);

}
