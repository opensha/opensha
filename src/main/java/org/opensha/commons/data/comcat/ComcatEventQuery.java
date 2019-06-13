package org.opensha.commons.data.comcat;

import gov.usgs.earthquake.event.EventQuery;

import java.math.BigDecimal;
import java.util.Date;


/**
 * Class to hold Comcat search parameters.
 * Author: Michael Barall 06/12/2019.
 *
 * This extends the USGS EventQuery class in order to add more parameters.
 */

public class ComcatEventQuery extends EventQuery {

	private Boolean includeSuperseded = null;

	/**
	 * Construct a blank ComcatEventQuery.
	 */
	public ComcatEventQuery() {
	}

	// Getters

	public Boolean getIncludeSuperseded() {
		return includeSuperseded;
	}

	// Setters

	public void setIncludeSuperseded(Boolean in) {
		includeSuperseded = in;
	}

}
