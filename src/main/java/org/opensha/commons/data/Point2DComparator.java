package org.opensha.commons.data;

import java.awt.geom.Point2D;
import java.util.Comparator;

import org.opensha.commons.exceptions.InvalidRangeException;

/**
 *  <b>Title:</b> DataPoint2DComparatorAPI<p>
 *
 *  <b>Description:</b> This interface must be implemented by all comparators of
 *  DataPoint2D. The comparator uses a tolerance to specify when two values are
 *  within tolerance of each other, they are equal<p>
 *
 * @author     Steven W. Rock
 * @created    February 20, 2002
 * @see        DataPoint2D
 * @version    1.0
 */

public interface Point2DComparator extends Comparator<Point2D> {

    /**
     *  Tolerance indicates the distance two values can be apart, but still
     *  considered equal. This function sets the tolerance.
     *
     * @param  newTolerance               The new tolerance value
     * @exception  InvalidRangeException  Is Thrown if the tolarance is negative
     */
    public void setTolerance( double newTolerance ) throws InvalidRangeException;


    /**
     *  Tolerance indicates the distance two values can be apart, but still
     *  considered equal. This function returns the tolerance.
     *
     * @return    The tolerance value
     */
    public double getTolerance();

}
