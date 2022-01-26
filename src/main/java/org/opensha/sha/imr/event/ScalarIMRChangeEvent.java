package org.opensha.sha.imr.event;

import java.util.EventObject;
import java.util.HashMap;

import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.util.TectonicRegionType;

/**
 *  <b>Title:</b> ScalarIMRChangeEvent<p>
 *
 *  <b>Description:</b> Any time the selected Attenuation Relationship changed via the IMR
 *  GUI bean, this event is triggered and received by all listeners<p>
 *
 * @author     Kevin Milner
 * @created    February 27 2009
 * @version    1.0
 */

public class ScalarIMRChangeEvent extends EventObject {

    /** New value for the Parameter. */
	HashMap<TectonicRegionType, ScalarIMR> newIMRMap;
//    private ScalarIntensityMeasureRelationshipAPI newAttenRel;

    /** Old value for the Parameter. */
	HashMap<TectonicRegionType, ScalarIMR> oldIMRMap;
//    private ScalarIntensityMeasureRelationshipAPI oldAttenRel;


    /**
     * Constructor for the AttenuationRelationshipChangeEvent object.
     *
     * @param  reference      Object which created this event
     * @param  oldAttenRel       Old AttenuationRelationship
     * @param  newAttenRel       New AttenuationRelationship
     */
    public ScalarIMRChangeEvent(
            Object reference,
            HashMap<TectonicRegionType, ScalarIMR> oldIMRMap,
            HashMap<TectonicRegionType, ScalarIMR> newIMRMap
             ) {
        super( reference );
        this.oldIMRMap = oldIMRMap;
        this.newIMRMap = newIMRMap;

    }

    /**
     *  Gets the new AttenuationRelationship.
     *
     * @return    New AttentuationRelationship
     */
    public HashMap<TectonicRegionType, ScalarIMR> getNewIMRs() {
        return newIMRMap;
    }


    /**
     *  Gets the old AttentuationRelationship.
     *
     * @return    Old AttentuationRelationship
     */
    public HashMap<TectonicRegionType, ScalarIMR> getOldValue() {
        return oldIMRMap;
    }
}
