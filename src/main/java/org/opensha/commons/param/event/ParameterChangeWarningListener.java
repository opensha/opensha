package org.opensha.commons.param.event;

import java.util.EventListener;

/**
 *  <b>Title:</b> ParameterChangeWarnListener<p>
 *
 *  <b>Description:</b> The change listener receives change events whenever an
 *  attempt was made to change a parameter and a warning was issued. The listener is typically
 *  the Main Application that wants to do something with the warning, such as
 *  put up a dialog box to accemtt or cancel.<p>
 *
 * @author     Steven W. Rock
 * @created    February 21, 2002
 * @version    1.0
 */

public interface ParameterChangeWarningListener extends EventListener {

    /**
     *  Function that must be implemented by all Listeners for
     *  ParameterChangeWarnEvents.
     *
     * @param  event  The Event which triggered this function call
     */
    public void parameterChangeWarning( ParameterChangeWarningEvent event );

}
