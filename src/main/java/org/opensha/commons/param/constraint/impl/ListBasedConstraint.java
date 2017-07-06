package org.opensha.commons.param.constraint.impl;

import java.util.Collection;
import java.util.List;

import org.opensha.commons.param.constraint.AbstractParameterConstraint;


public class ListBasedConstraint<E> extends AbstractParameterConstraint<E> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private List<E> allowed;
	
	public ListBasedConstraint(List<E> allowed) {
		this.allowed = allowed;
	}
	
	public void setAllowed(List<E> allowed) {
		this.allowed = allowed;
	}
	
	public List<E> getAllowed() {
		return allowed;
	}

	@Override
	public boolean isAllowed(E obj) {
		return allowed.contains(obj);
	}

	@Override
	public Object clone() {
		return new ListBasedConstraint<E>(allowed);
	}

}
