package org.opensha.commons.param.constraint.impl;

import org.opensha.commons.param.constraint.AbstractParameterConstraint;

/**
 * Constraint for Long values
 * @author kevin
 *
 */
public class LongConstraint extends AbstractParameterConstraint<Long> {

	private long min;
	private long max;

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a constraint with the given min/max values
	 * @param min
	 * @param max
	 */
	public LongConstraint(long min, long max) {
		this.min = min;
		this.max = max;
	}

	@Override
	public boolean isAllowed(Long l) {
		if (nullAllowed && l == null)
			return true;
		if (l.compareTo( this.min ) >= 0 && l.compareTo( this.max ) <= 0)
			return true;
		return false;
	}

	@Override
	public Object clone() {
		LongConstraint c1 = new LongConstraint(min, max);
		c1.setName( name );
		c1.setNullAllowed( nullAllowed );
		c1.editable = true;
		return c1;
	}

}
