package org.opensha.commons.param.event;

import java.util.EventObject;

/**
 *  <b>Title:</b> TimeSpanParameterChangeListener <p>
 *
 *  <b>Description:</b> The timespan change listener receives change events
 *  whenever one of the TimeSpan have been change. The listener is
 *  typically an ERF that wants to do something with the changed
 *  timespan <p>
 *
 * @author
 * @created
 * @version
 */

public interface TimeSpanChangeListener {

  /**
   *  Function that must be implemented by all Timespan Listeners for
   *  timeChangeEvents.
   *
   * @param  event  The Event which triggered this function call
   */
    public void timeSpanChange( EventObject event );
}
