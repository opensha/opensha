package org.opensha.commons.param.constraint;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;


/**
 * <b>Title:</b> DiscreteParameterConstraintAPI<p>
 *
 * <b>Description:</b> This interface must be implemented by all parameters
 * that wish to restrict allowed values to a definite set. These values are
 * typically presented in a GUI with a picklist.<p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */

public interface DiscreteParameterConstraint<E> extends ParameterConstraint<E> {

    /** Returns cloned vector of allowed values, unable to modify original values. */
    public List<E> getAllowedValues();

    /**  Returns Iterator over allowed values, able to modify original. */
    public ListIterator<E> listIterator();

    /** Returns the number of allowed values in the list */
    public int size();
}
