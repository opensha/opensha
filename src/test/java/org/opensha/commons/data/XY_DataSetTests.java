package org.opensha.commons.data;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.data.function.DefaultXY_DataSet;

import com.google.common.collect.Lists;

public class XY_DataSetTests {

	private static double[] arr = {0,1,2,3};
	private static double[] arr_null = null;
	private static double[] arr_empty = {};
	private static double[] arr_short = {0,1,2};

	private static List<Double> list = Lists.newArrayList(0d,1d,2d,3d);
	private static List<Double> list_null = null;
	private static List<Double> list_empty = new ArrayList<Double>();
	private static List<Double> list_short = Lists.newArrayList(0d,1d,2d);
	
	@Test (expected = NullPointerException.class)
	public void testXYdsNPE1() { new DefaultXY_DataSet(arr, arr_null); }
	@Test (expected = NullPointerException.class)
	public void testXYdsNPE2() { new DefaultXY_DataSet(arr_null, arr); }
	@Test (expected = NullPointerException.class)
	public void testXYdsNPE3() { new DefaultXY_DataSet(list, list_null); }
	@Test (expected = NullPointerException.class)
	public void testXYdsNPE4() { new DefaultXY_DataSet(list_null, list); }

	@Test (expected = IllegalArgumentException.class)
	public void testXYdsIAE1() { new DefaultXY_DataSet(arr, arr_empty); }
	@Test (expected = IllegalArgumentException.class)
	public void testXYdsIAE2() { new DefaultXY_DataSet(arr_empty, arr); }
	@Test (expected = IllegalArgumentException.class)
	public void testXYdsIAE3() { new DefaultXY_DataSet(arr, arr_short); }
	@Test (expected = IllegalArgumentException.class)
	public void testXYdsIAE4() { new DefaultXY_DataSet(list, list_empty); }
	@Test (expected = IllegalArgumentException.class)
	public void testXYdsIAE5() { new DefaultXY_DataSet(list_empty, list); }
	@Test (expected = IllegalArgumentException.class)
	public void testXYdsIAE6() { new DefaultXY_DataSet(list, list_short); }

	// TODO implementation tests
}
