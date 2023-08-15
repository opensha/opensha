package org.opensha.commons.data.function;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.opensha.commons.data.Point2DComparator;
import org.opensha.commons.data.Point2DToleranceSortedArrayList;

public class EmpiricalPoint2DToleranceSortedList extends Point2DToleranceSortedArrayList {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public EmpiricalPoint2DToleranceSortedList(Point2DComparator comparator) {
		this(comparator, null);
	}
	
	public EmpiricalPoint2DToleranceSortedList(Point2DComparator comparator, Collection<Point2D> initialValues) {
		super(comparator);
		
		if (initialValues != null && !initialValues.isEmpty()) {
			// add them all efficiently
			List<Point2D> sorted = new ArrayList<>(initialValues);
			Collections.sort(sorted, comparator);
			
			int index = -1;
			Point2D prev = null;
			
			for (Point2D val : sorted) {
				if (prev == null || comparator.compare(prev, val) != 0) {
					// this is either the first value, or a new x value
					index++;
					super.add(index, val);
					prev = val;
				} else {
					// this is a duplicate, add to previous
					prev = new Point2D.Double(prev.getX(), prev.getY() + val.getY());
					super.set(index, prev);
				}
			}
		}
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
