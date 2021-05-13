package org.opensha.commons.data;

import static org.junit.Assert.*;

import java.awt.geom.Point2D;
import java.util.Random;

import org.junit.Test;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;

public abstract class Point2DToleranceSortedListTest {

	private static Random rand = new Random();
	
	protected abstract Point2DToleranceSortedList buildList();
//		return new Point2DToleranceSortedArrayList(new Point2DToleranceComparator());
////		return new Point2DToleranceSortedTreeSet(new Point2DToleranceComparator());
//	}
	
	private static double getRandVal() {
		double val = rand.nextDouble();
		
		val = val * 40d - 20d;
		
		return val;
	}
	
	private static void addRandom(Point2DToleranceSortedList list) {
		double x = getRandVal();
		double y = getRandVal();
		Point2D pt = new Point2D.Double(x, y);
		
		list.add(pt);
	}
	
	private static void addRandom(Point2DToleranceSortedList list, int num) {
		for (int i=0; i<num; i++) {
			addRandom(list);
		}
	}
	
	private static void removeRandom(Point2DToleranceSortedList list) {
		int index = rand.nextInt(list.size());
		list.remove(index);
	}
	
	private static void removeRandom(Point2DToleranceSortedList list, int num) {
		for (int i=0; i<num; i++) {
			removeRandom(list);
		}
	}
	
	@Test
	public void testMinMaxYSimple() {
		Point2DToleranceSortedList list = buildList();
		
		addRandom(list, 100);
		
		validateMinMax(list);
	}
	
	@Test
	public void testMinMaxYSpecial() {
		Point2DToleranceSortedList list = buildList();
		
		list.add(new Point2D.Double(0d, 0d));
		list.add(new Point2D.Double(55d, 5d));
		
		validateMinMax(list);
		
		list.add(new Point2D.Double(0d, 2d));
		
		validateMinMax(list);
	}
	
	private void validateMinMax(Point2DToleranceSortedList list) {
		MinMaxAveTracker xTrack = new MinMaxAveTracker();
		MinMaxAveTracker yTrack = new MinMaxAveTracker();
		
		for (Point2D pt : list) {
			xTrack.addValue(pt.getX());
			yTrack.addValue(pt.getY());
		}
		
		assertEquals("getMinX() is wrong!", xTrack.getMin(), list.getMinX(), 0.0d);
		assertEquals("getMaxX()", xTrack.getMax(), list.getMaxX(), 0.0d);
		assertEquals("getMinX() is wrong!", yTrack.getMin(), list.getMinY(), 0.0d);
		assertEquals("getMaxX()", yTrack.getMax(), list.getMaxY(), 0.0d);
	}
	
	@Test
	public void testMinMaxYComplicated() {
		Point2DToleranceSortedList list = buildList();
		
		addRandom(list, 100);
		
		validateMinMax(list);
		
		for (int i=0; i<50; i++) {
			removeRandom(list);
			validateMinMax(list);
		}
		
		for (int i=0; i<10; i++) {
			addRandom(list);
			validateMinMax(list);
		}
		
		for (int i=0; i<10; i++) {
			removeRandom(list);
			validateMinMax(list);
		}
	}
	
	private static void validateListOrder(Point2DToleranceSortedList list) {
		Point2D prev = null;
		for (Point2D pt : list) {
			if (prev != null) {
				double x1 = prev.getX();
				double x2 = pt.getX();
				assertTrue("list order error: " + prev + ", " + pt, x1 < x2);
				// now assert that it's not greater than the tolerance
				assertTrue(Math.abs(x1 - x2) > list.getTolerance());
			}
			prev = pt;
		}
	}
	
	@Test
	public void testListOrder() {
		Point2DToleranceSortedList list = buildList();
		
		for (int i=0; i<100; i++) {
			addRandom(list);
			validateListOrder(list);
		}
		
		for (int i=0; i<50; i++) {
			removeRandom(list);
			validateListOrder(list);
		}
		
		for (int i=0; i<10; i++) {
			addRandom(list);
			validateListOrder(list);
		}
	}
	
	@Test
	public void testAddIdentical() {
		Point2DToleranceSortedList list = buildList();
		list.add(new Point2D.Double(5d, 5d));
		list.add(new Point2D.Double(5d, 4d));
		
		assertEquals("list size should equal 1 when duplicate x value added", 1, list.size());
		assertEquals("old value still present when duplicate added", 4d, list.get(0).getY(), 0.00001);
		
		// now test adding identical within tolerance
		// disabled pending ticket #341
//		list.setTolerance(0.5);
//		
//		list.add(new Point2D.Double(5.5, 3d));
//		
//		assertEquals("list size should equal 1 when duplicate (within tolerance) x value added", 1, list.size());
//		assertEquals("old value still present when duplicate added", 3d, list.get(0).getY(), 0.00001);
//		
//		list.add(new Point2D.Double(4.9, 2d));
//		
//		assertEquals("list size should equal 1 when duplicate (within tolerance) x value added", 1, list.size());
//		
//		list.add(new Point2D.Double(4.5001, 2d));
//		
//		assertEquals("list size should equal 1 when duplicate (within tolerance) x value added", 1, list.size());
//		assertEquals("old value still present when duplicate added", 2d, list.get(0).getY(), 0.00001);
//		
//		list.add(new Point2D.Double(4.49, 2d));
//		
//		assertEquals("list size should increase when x value added just outside of tolerence", 2, list.size());
//		
//		list.add(new Point2D.Double(5.51, 2d));
//		
//		assertEquals("list size should increase when x value added just outside of tolerence", 3, list.size());
	}
	
}
