package org.opensha.commons.data.function;

import java.awt.geom.Point2D;
import java.util.Collection;

import org.opensha.commons.data.Point2DComparator;
import org.opensha.commons.data.Point2DToleranceSortedArrayList;

public class EmpiricalPoint2DToleranceSortedList extends Point2DToleranceSortedArrayList {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public EmpiricalPoint2DToleranceSortedList(Point2DComparator comparator) {
		super(comparator);
	}

	@Override
	public boolean add(Point2D e) {
		double x = e.getX();
		double y = e.getY();
		
		// new way: only do the binary search once, the reuse thie index for add/set calls
		int ind = binarySearch(new Point2D.Double(x, 0d));
		if (ind < 0)
			super.add(-ind-1, new Point2D.Double(x, y));
		else
			super.set(ind, new Point2D.Double(x, get(ind).getY()+y));
		// true means anything changed, not that it's a new point
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends Point2D> c) {
		boolean isNew = false;
		for (Point2D pt : c) {
			if (add(pt))
				isNew = true;
		}
		return isNew;
	}

}
