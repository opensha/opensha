package org.opensha.sha.gcim.imCorrRel.event;

import java.util.EventListener;

/**
 *  <b>Title:</b> ScalarIMCorrRelChangeListener<p>
 *
 *  <b>Description:</b> The change listener receives change events whenever a new
 *  Correlation Relationship is selected, such as from an IMCorrRel Gui Bean.<p>
 *
 * @author     Brendon Bradley
 * @created    5 July 2010
 * @version    1.0
 */

public interface IMCorrRelChangeListener extends EventListener {
    /**
     *  Function that must be implemented by all Listeners for
     *  ImCorrelationRelationChangeEvents.
     *
     * @param  event  The Event which triggered this function call
     */
    public void imCorrRelChange( IMCorrRelChangeEvent event );
}
