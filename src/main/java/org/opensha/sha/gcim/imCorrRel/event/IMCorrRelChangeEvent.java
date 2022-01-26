package org.opensha.sha.gcim.imCorrRel.event;

import java.util.EventObject;
import java.util.HashMap;

import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.util.TectonicRegionType;

/**
 *  <b>Title:</b> IMCorrRelChangeEvent<p>
 *
 *  <b>Description:</b> Any time the selected IM Correlation Relation changed via the IMCorrRel
 *  GUI bean, this event is triggered and received by all listeners<p>
 *
 * @author     Brendon Bradley
 * @created    July 5 2010
 * @version    1.0
 */

public class IMCorrRelChangeEvent extends EventObject {

    /** New value for the Parameter. */
	HashMap<TectonicRegionType, ImCorrelationRelationship> newIMCorrRelMap;

    /** Old value for the Parameter. */
	HashMap<TectonicRegionType, ImCorrelationRelationship> oldIMCorrRelMap;


    /**
     * Constructor for the ImCorrelationRelationChangeEvent object.
     *
     * @param  reference      Object which created this event
     * @param  oldCorrRel       Old ImCorrelationRelationship
     * @param  newCorrRel       New ImCorrelationRelationship
     */
    public IMCorrRelChangeEvent(
            Object reference,
            HashMap<TectonicRegionType, ImCorrelationRelationship> oldIMCorrRelMap,
            HashMap<TectonicRegionType, ImCorrelationRelationship> newIMCorrRelMap
             ) {
        super( reference );
        this.oldIMCorrRelMap = oldIMCorrRelMap;
        this.newIMCorrRelMap = newIMCorrRelMap;

    }

    /**
     *  Gets the new ImCorrelationRelationship.
     *
     * @return    New ImCorrelationRelationship
     */
    public HashMap<TectonicRegionType, ImCorrelationRelationship> getNewIMCorrRels() {
        return newIMCorrRelMap;
    }


    /**
     *  Gets the old ImCorrelationRelationship.
     *
     * @return    Old ImCorrelationRelationship
     */
    public HashMap<TectonicRegionType, ImCorrelationRelationship> getOldValue() {
        return oldIMCorrRelMap;
    }
}
