package org.opensha.commons.data;

public class Point2DToleranceSortedArrayListTest extends
		Point2DToleranceSortedListTest {

	@Override
	protected Point2DToleranceSortedList buildList() {
		return new Point2DToleranceSortedArrayList(new Point2DToleranceComparator());
	}

}
