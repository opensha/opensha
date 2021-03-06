package org.opensha.commons.param.constraint.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.EditableException;
import org.opensha.commons.param.constraint.DiscreteParameterConstraint;
import org.opensha.commons.param.constraint.AbstractParameterConstraint;

/**
 * <p>Title: StringListConstraint.java </p>
 * <p>Description: This constraint is used for allowing multiple String selections
 * by the user. It is typically presented in GUI as a JList where multiple item
 * selection is allowed. </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class StringListConstraint extends AbstractParameterConstraint<List<String>>
implements DiscreteParameterConstraint<List<String>> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private StringConstraint strConst;


	/** No-Arg constructor for the StringConstraint object. Calls the super() constructor. */
	public StringListConstraint(){
		this(null);
	}

	/**
	 * Accepts a list of strings to be displayed in the list. User can choose
	 * one or more items from this list
	 * @param strings
	 * @throws ConstraintException
	 */
	public StringListConstraint(List<String> strings) throws ConstraintException {
		super();
		if (strings == null)
			strConst = new StringConstraint();
		else
			strConst = new StringConstraint(strings);
	}

	/**
	 * Determines whether Strings in the ArrayList are allowed.
	 * It first checks that all the elements in arraylist should be String objects.
	 * Then it checks that all String objects in ArrayList are included in
	 * the list of allowed Strings
	 *
	 * @param valsList
	 * @return
	 */
	public boolean isAllowed(List<String> valsList) {
		int size = valsList.size();
		for(int i=0; i<size; ++i) {
			if(!(valsList.get(i) instanceof String)) return false;
			if(!strConst.isAllowed((String)valsList.get(i))) return false;
		}
		return true;
	}

	/** Returns a copy so you can't edit or damage the origial. */
	public Object clone() {
		StringListConstraint c1 = new StringListConstraint();
		c1.name = name;
		ArrayList v = strConst.getAllowedStrings();
		ListIterator it = v.listIterator();
		while ( it.hasNext() ) {
			String val = ( String ) it.next();
			c1.addString( val );
		}
		c1.setNullAllowed( nullAllowed );
		c1.editable = true;
		return c1;
	}

	/** Adds a String to the list of allowed values, if this constraint is editable. */
	public void addString( String str ) throws EditableException {
		checkEditable(C + ": addString(): ");
		strConst.addString(str);
	}

	public ArrayList getAllowedValues() {
		return strConst.getAllowedValues();
	}

	public ListIterator listIterator() {
		return strConst.listIterator();
	}

	public int size() {
		return strConst.size();
	}

}
