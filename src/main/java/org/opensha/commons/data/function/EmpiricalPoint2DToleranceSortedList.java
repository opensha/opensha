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
		Point2D point = this.get(x);
		if (point == null)
			point = new Point2D.Double(x, y);
		else
			point.setLocation(x, point.getY() + y);
		boolean isNew = super.add(point);
		
//		super.recalcMinMaxYs();
		
		return isNew;
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
