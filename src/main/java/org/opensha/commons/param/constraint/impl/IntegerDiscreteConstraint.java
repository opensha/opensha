package org.opensha.commons.param.constraint.impl;

import java.util.ArrayList;

import org.opensha.commons.param.constraint.AbstractParameterConstraint;


import com.google.common.base.Preconditions;

public class IntegerDiscreteConstraint extends AbstractParameterConstraint<Integer> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final String S = "IntegerDiscreteConstraint";
	
	private ArrayList<Integer> allowed;
	
	public IntegerDiscreteConstraint(ArrayList<Integer> allowed) {
		setAllowed(allowed);
	}
	
	public void setAllowed(ArrayList<Integer> allowed) {
		checkEditable(S);
		Preconditions.checkNotNull(allowed, "Allowed cannot be null!");
		this.allowed = allowed;
	}

	@Override
	public boolean isAllowed(Integer obj) {
		if (obj == null)
			return isNullAllowed();
		return allowed.contains(obj);
	}

	@Override
	public Object clone() {
		IntegerDiscreteConstraint iconst = new IntegerDiscreteConstraint(allowed);
		iconst.setNullAllowed(isNullAllowed());
		if (!this.isEditable())
			iconst.setNonEditable();
		return iconst;
	}
	
	public ArrayList<Integer> getAllowed() {
		return allowed;
	}
	
	public void addAllowed(Integer val) {
		allowed.add(val);
	}
	
	public int size() {
		return allowed.size();
	}

}
