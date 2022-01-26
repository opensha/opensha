package org.opensha.commons.util;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;

/**
 * <b>Title:</b>ParamUtils<p>
 *
 * <b>Description:</b>Generic functions used in handling parameters, basically
 * verifying the class type of a Parameter. Recall that all Parameters implement
 * the ParameterAPI. Because of this they are passed around functions as ParameterAPI.
 * In some cases you need to know more specifically the class type in  order to access
 * the special functions of these subclasses. This utility class verifies the class type
 * so you can cast to the right type without throwing errors.
 *
 * @author Steven W. Rock
 * @version 1.0
 */

public class ParamUtils {

    /**
     * Returns true if the ParameterAPI is a DoubleParameter or DoubleDiscreteParameter.
     * This allows you to get and set the value as a Double.
     * @param           The parameter to verify
     * @return          boolean true if is either parameter type, else false
     */
    public static boolean isDoubleOrDoubleDiscreteConstraint(Parameter param) {
        if( isDoubleConstraint(param) || isDoubleDiscreteConstraint(param) ) return true;
        else return false;
    }

    /**
     * Returns true if the ParameterAPI contained constraint is a DoubleConstraint.
     * @param           The parameter to verify
     * @return          boolean true if constraint is DOubleConstraint, false otherwise.
     */
    public static boolean isDoubleConstraint(Parameter param) {
        ParameterConstraint constraint = param.getConstraint();
        if ( constraint instanceof DoubleConstraint ) return true;
        else return false;
    }

    /**
     * Returns true if the ParameterAPI contained constraint is a DoubleDiscreteConstraint.
     * @param           The parameter to verify
     * @return          boolean if is either parameter type
     */
    public static boolean isDoubleDiscreteConstraint(Parameter param) {
        ParameterConstraint constraint = param.getConstraint();
        if ( constraint instanceof DoubleDiscreteConstraint ) return true;
        else return false;
    }

    /**
     * Returns true if the ParameterAPI is an instance of WarningParameterAPI
     * @param           The parameter to verify
     * @return          boolean if is either parameter type
     */
    public static boolean isWarningParameterAPI(Parameter param) {
        if ( param instanceof WarningParameter ) return true;
        else return false;
    }

}
