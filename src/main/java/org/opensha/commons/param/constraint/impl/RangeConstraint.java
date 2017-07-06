package org.opensha.commons.param.constraint.impl;

import org.jfree.data.Range;
import org.opensha.commons.param.constraint.AbstractParameterConstraint;

public class RangeConstraint extends AbstractParameterConstraint<Range> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private Range constraintRange;
	
	public RangeConstraint(Range constraintRange) {
		this.constraintRange = constraintRange;
	}

	@Override
	public boolean isAllowed(Range range) {
		if (range == null)
			return isNullAllowed();
		return range.getLowerBound() >= constraintRange.getLowerBound()
				&& range.getUpperBound() <= constraintRange.getUpperBound();
	}

	@Override
	public Object clone() {
		return new RangeConstraint(constraintRange);
	}

}
