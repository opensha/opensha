package org.opensha.commons.param.constraint.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.EditableException;
import org.opensha.commons.param.constraint.DiscreteParameterConstraint;
import org.opensha.commons.param.constraint.AbstractParameterConstraint;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * <b>Title:</b> DoubleDiscreteConstraint<p>
 *
 * <b>Description:</b> This constraint contains a list of possible allowed
 * double values. These can tipically be presented in a GUI picklist. This is
 * the same fucntionality for all DiscreteParameterConstraints.
 *
 * @author     Steven W. Rock
 * @created    February 20, 2002
 * @version    1.0
 */

public class DoubleDiscreteConstraint
    extends AbstractParameterConstraint<Double>
    implements DiscreteParameterConstraint<Double>
{

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/** Class name for debugging. */
    protected final static String C = "DoubleDiscreteConstraint";
    /** If true print out debug statements. */
    protected final static boolean D = false;

    /** List of possible Double values allowed by this constraint */
    private List<Double> doubles = new ArrayList<Double>();


    /** No-arg Constructor, just calls super() */
    public DoubleDiscreteConstraint() { super(); }


    /**
     *  Constructor that assigns the list of allowed doubles.
     *
     * @param  doubles                  A vector of allowed Doubles in this constraint.
     * @exception  ConstraintException  Is thrown if passed in list is empty.
     */
    public DoubleDiscreteConstraint( List<Double> doubles ) throws ConstraintException {

        if ( doubles.size() > 0 ) {
            this.doubles = doubles;
        } else {
            String S = "DoubleDiscreteConstraint: Constructor(ArrayList doubles): ";
            throw new ConstraintException( S + "Input vector of constraint values cannot be empty" );
        }
    }


    /**
     * Assigns the list of allowed doubles. Throws an editable
     * Exception if this constraint is currently set to non-editable.
     *
     * @param  doubles  The new list of allowed doubles
     */
    public void setDoubles( List<Double> doubles ) throws EditableException {

        if( !this.editable ) throw new EditableException(C + ": setStrings(): " +
            "This constraint is currently not editable." );

        this.doubles = doubles;
    }


    /**
     *  Returns cloned vector of allowed values. unable to modify
     *  original values.
     *
     * @return    The allowed doubles in a Vectoru
     */
    public List<Double> getAllowedValues() { return getAllowedDoubles(); }


    /**
     *  Returns an immutable view of the allowed values.
     *
     * @return    The allowed doubles in a Vectoru
     */
    public List<Double> getAllowedDoubles() {
    	return Lists.newArrayList(doubles);
//    	return ImmutableList.copyOf(doubles);
//      return ( ArrayList<Double> ) doubles.clone();
    }

    /**
     *  Checks if the value is allowed by checking the list of doubles.
     *
     * @param  d  value to check.
     * @return    True if the value is allowed, i.e. in the doubles list.
     */
    public boolean isAllowed( Double d ) {
        if( nullAllowed && ( d == null ) ) return true;
        if ( !containsDouble( d ) ) return false;
        else return true;
    }


    /**
     *  Checks if the value is allowed by checking the list of doubles.
     *
     * @param  d  value to check.
     * @return    True if the value is allowed, i.e. in the doubles list.
     */
    public boolean isAllowed( double d ) { return isAllowed( new Double( d ) ); }


    /** Returns Iterator over all allowed values */
    public ListIterator<Double> listIterator() { return doubles.listIterator(); }


    /**
     * Adds a double to the list of allowed values. An EditableException
     * is thrown is the list is currently not editable.
     */
    public void addDouble( double d ) throws EditableException {
        addDouble( new Double(d) );
    }

    /**
     * Adds a Double to the list of allowed values. An EditableException
     * is thrown is the list is currently not editable.
     */
    public void addDouble( Double d ) throws EditableException {

        String S = C + ": addDouble( Double ): ";
        checkEditable(S);

        if ( !containsDouble( d ) ) doubles.add( d );

    }

    /**
     * Removes a Double from the list of allowed values. An EditableException
     * is thrown is the list is currently not editable.
     */
    public void removeDouble( Double d ) throws EditableException {

        String S = C + ": removeDouble( Double ): ";
        checkEditable(S);

        if ( containsDouble( d ) ) doubles.remove( d );
    }


    /**
     * Removes a Double from the list of allowed values. An EditableException
     * is thrown is the list is currently not editable.
     */
    public void removeDouble( double d ) throws EditableException {
        removeDouble( new Double(d) );
    }

    /**
     *  Checks if the value is one of the allowed objects
     *
     * @param  d  The value to check.
     * @return    True if this value is one of the allowed objects.
     */
    public boolean containsDouble( Double d ) {
        if ( doubles.contains( d ) ) return true;
        else return false;
    }

    /**
     *  Checks if the value is one of the allowed objects
     *
     * @param  d  The value to check.
     * @return    True if this value is one of the allowed objects.
     */
    public boolean containsDouble( double d ) { return containsDouble( new Double(d) ); }

    /** The number of allowed values for this constraint. */
    public int size() { return doubles.size(); }


    /** Prints out the state of this constraint for debugging. */
    public String toString() {

        String TAB = "    ";
        StringBuffer b = new StringBuffer();
        b.append( this.getClass().getName() );
        if( name != null) b.append( TAB + "Name = " + name + '\n' );
        //b.append( TAB + "Is Editable = " + this.editable + '\n' );
        b.append( TAB.concat( "Allowed values = " ) );

        ListIterator<Double> it = doubles.listIterator();
        boolean first = true;
        while ( it.hasNext() ) {
            if ( !first ) {
                b.append( TAB + ", " + it.next() );
            } else {
                b.append( TAB + it.next() );
                first = false;
            }
        }
        b.append( TAB + "Null Allowed = " + this.nullAllowed+ '\n' );
        return b.toString();
    }


    /**
     *  Creates a copy of this object instance. This makes the copy unable to
     *  alter the original, and any way that the cloned copy is used.
     *
     * @return    An exact copy of this constraint
     */
    public Object clone() {

        DoubleDiscreteConstraint c1 = new DoubleDiscreteConstraint();
        c1.setName( name );
        List<Double> v = getAllowedDoubles();
        ListIterator<Double> it = v.listIterator();
        while ( it.hasNext() ) {
            Double val = ( Double ) it.next();
            Double val2 = new Double( val.doubleValue() );
            c1.addDouble( val2 );
        }
        c1.setNullAllowed( nullAllowed );
        c1.editable = true;
        return c1;
    }
}
