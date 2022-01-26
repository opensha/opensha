package org.opensha.commons.data;

/**
 *  <b>Title:</b> Named<p>
 *
 *  <b>Description:</b> This interface flags all implementing classes as being
 *  Named objects, i.e. they all have a name field and implements getName().
 *  Used in all Parameters, among other areas.<p>
 *
 * @author     Steven W. Rock
 * @created    February 20, 2002
 * @version    1.0
 */

public interface Named {
    /** Returns the name of this object */
    public String getName();
}
