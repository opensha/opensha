package org.opensha.commons.param.event;

import java.util.EventObject;

import org.opensha.commons.param.WarningParameter;

/**
 *  <b>Title:</b> ParameterChangeWarningEvent<p>
 *
 *  <b>Description:</b> This event is thrown when you try to modify a parameter's value
 *  beyond it's recommended value. This event gives the calling class the ability
 *  to either head the warnings or ignore it and update the parameter anyways. <p>
 *
 * @author     Steven W. Rock
 * @created    April 17, 2002
 * @version    1.0
 */

public class ParameterChangeWarningEvent extends EventObject {

    /**
     *  Name of Parameter tried to change.
     */
    private WarningParameter param;

    /**
     *  New value for the Parameter that failed.
     */
    private Object newValue;

    /**
     *  Old value for the Parameter.
     */
    private Object oldValue;


    /**
     *  Constructor for the ParameterChangeWarningEvent object.
     *
     * @param  reference      Object which created this event, i.e. the parametr
     * @param  parameterName  Name of Parameter tried to change.
     * @param  oldValue       Old value for the Parameter
     * @param  badValue       New value for the Parameter that failed
     */
    public ParameterChangeWarningEvent(
            Object reference,
            WarningParameter param,
            Object oldValue,
            Object newValue
             ) {
        super( reference );
        this.param = param;
        this.newValue = newValue;
        this.oldValue = oldValue;
    }


    /**
     *  Gets the name of Parameter that failed a change.
     *
     * @return    Name of Parameter tried to change
     */
    public WarningParameter getWarningParameter() {
        return param;
    }


    /**
     *  Gets the desired new value.
     *
     * @return    new value for the Parameter
     */
    public Object getNewValue() {
        return newValue;
    }


    /**
     *  Gets the old value for the Parameter.
     *
     * @return    Old value for the Parameter
     */
    public Object getOldValue() {
        return oldValue;
    }


    /**
     * Set's the new value ignoring the warning
     */
    public void commitNewChange(){

    }

}
