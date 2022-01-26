package org.opensha.commons.param.event;

import java.util.EventListener;

/**
 *  <b>Title:</b> ParameterChangeFailListener<p>
 *
 *  <b>Description:</b> The change listener receives change events whenever an
 *  attempt was made to change a parameter and failed. The listener is typically
 *  the Main Application that wants to do something with the failure, such as
 *  put up a dialog box.<p>
 *
 * @author     Steven W. Rock
 * @created    February 21, 2002
 * @version    1.0
 */

public interface ParameterChangeFailListener extends EventListener {
    /**
     *  Function that must be implemented by all Listeners for
     *  ParameterChangeFailEvents.
     *
     * @param  event  The Event which triggered this function call
     */
    public void parameterChangeFailed( ParameterChangeFailEvent event );
}
