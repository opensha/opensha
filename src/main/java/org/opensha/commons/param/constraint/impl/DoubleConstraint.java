package org.opensha.commons.param.constraint.impl;

import org.opensha.commons.exceptions.EditableException;
import org.opensha.commons.param.constraint.AbstractParameterConstraint;

/**
 * <b>Title:</b> DoubleConstraint<p>
 *
 * <b>Description:</b> A doubleConstraint represents a range of allowed
 * values between a min and max double value, inclusive. The main purpose of
 * this class is to call isAllowed() which will return true if the value
 * is withing the range. Null values may or may not be allowed. See the
 * ParameterConstraint javadocs for further documentation. <p>
 *
 * Note: It is up to the programmer using this class to ensure that the
 * min value is less than the max value. As an enhancement to this class
 * setting min and max could be validated that min is not greater than max. <p>
 *
 * @see AbstractParameterConstraint
 * @author     Sid Hellman, Steven W. Rock
 * @created    February 20, 2002
 * @version    1.0
 */
public class DoubleConstraint extends AbstractParameterConstraint<Double> {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/** Class name for debugging. */
    protected final static String C = "DoubleConstraint";
    /** If true print out debug statements. */
    protected final static boolean D = false;


    /** The minimum value allowed in this constraint, inclusive */
    protected double min;
    /** The maximum value allowed in this constraint, inclusive */
    protected double max;
    protected boolean hasMin;
    protected boolean hasMax;

    /** No-Arg Constructor, constraints are null so all values allowed */
    public DoubleConstraint() {
    	super();
    	hasMin = false;
    	hasMax = false;
    }


    /**
     * Constructor for the DoubleConstraint object. Sets the min and max values
     * allowed in this constraint. No checks are performed that min and max are
     * consistant with each other.
     *
     * @param  min  The min value allowed
     * @param  max  The max value allowed
     */
    public DoubleConstraint( double min, double max ) {
        this.setMinMax(min, max);
    }


    /**
     * Constructor for the DoubleConstraint object. Sets the min and max values
     * allowed in this constraint. No checks are performed that min and max are
     * consistant with each other.
     *
     * @param  min  The min value allowed
     * @param  max  The max value allowed
     */
    public DoubleConstraint( Double min, Double max ) {
    	this.setMinMax(min, max);
    }

    /**
     * Sets the min and max values allowed in this constraint. No checks
     * are performed that min and max are consistant with each other.
     *
     * @param  min  The new min value
     * @param  max  The new max value
     * @throws EditableException Thrown when the constraint or parameter
     * containing this constraint has been made non-editable.
     */
    public void setMinMax( double min, double max ) throws EditableException {
        String S = C + ": setMinMax(double, double): ";
        checkEditable(S);
        this.min = min;
        this.max = max;
        this.hasMin = Double.isFinite(min) || min < 0d;
        this.hasMax = Double.isFinite(max) || max > 0d;
    }


    /** Sets the min and max values allowed in this constraint. No checks
     * are performed that min and max are consistant with each other.
     *
     * @param  min  The new min value
     * @param  max  The new max value
     * @throws EditableException Thrown when the constraint or parameter
     * containing this constraint has been made non-editable.
     */
    public void setMinMax( Double min, Double max ) throws EditableException {
        String S = C + ": setMinMax(Double, Double): ";
        checkEditable(S);
        double mind = min == null ? Double.NaN : min.doubleValue();
        double maxd = max == null ? Double.NaN : max.doubleValue();
        this.setMinMax(mind, maxd);
    }


    /** Returns the min allowed value of this constraint. */
    public Double getMin() { return hasMin ? min : null; }

    /** Gets the max allowed value of this constraint */
    public Double getMax() { return hasMax ? max : null; }

    /**
     * Checks if the passed in value is within the min and max, inclusive of
     * the end points. First the value is chekced if it's null and null values
     * are allowed. Then it checks the passed in object is a Double. If the
     * constraint min and max values are null, true is returned, else the value
     * is compared against the min and max values. If any of these checks fails
     * false is returned. Otherwise true is returned.
     *
     * @param  obj  The object to check if allowed.
     * @return      True if this is a Double and one of the allowed values.
     */
    public boolean isAllowed( Double d ) {
        if( d == null ) return nullAllowed;
        double dv = d.doubleValue();
        if ((hasMin && dv < min) || (hasMax && dv > max))
        	return false;
        return true;
    }


    /**
     * Checks if the passed in value is within the min and max, inclusive of
     * the end points. First the value is checked if it's null and null values
     * are allowed. If the constraint min and max values are null, true is
     * returned, else the value is compared against the min and max values. If
     * any of these checks fails false is returned. Otherwise true is returned.
     *
     * @param  obj  The object to check if allowed.
     * @return      True if this is one of the allowed values.
     */
    public boolean isAllowed( double d ) { return isAllowed( Double.valueOf( d ) ); }


    /** returns the classname of the constraint, and the min & max as a debug string */
    public String toString() {
        String TAB = "    ";
        StringBuffer b = new StringBuffer();
        if( name != null ) b.append( TAB + "Name = " + name + '\n' );
        if( hasMin ) b.append( TAB + "Min = " + min + '\n' );
        if( hasMax ) b.append( TAB + "Max = " + max + '\n' );
        b.append( TAB + "Null Allowed = " + this.nullAllowed+ '\n' );
        return b.toString();
    }


    /** Creates a copy of this object instance so the original cannot be altered. */
    public Object clone() {
        DoubleConstraint c1 = new DoubleConstraint( min, max );
        c1.setName( name );
        c1.setNullAllowed( nullAllowed );
        c1.editable = true;
        return c1;
    }

}
