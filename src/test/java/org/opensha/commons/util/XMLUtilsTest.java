package org.opensha.commons.util;


import static org.junit.Assert.*;

import java.awt.Color;
import java.util.Random;

import org.dom4j.Document;
import org.dom4j.Element;
import org.junit.Before;
import org.junit.Test;

public class XMLUtilsTest {
	
	Document doc;
	Element root;

	@Before
	public void setUp() throws Exception {
		doc = XMLUtils.createDocumentWithRoot();
		root = doc.getRootElement();
	}
	
	@Test
	public void testColorXML() {
		Color[] colors = { Color.BLACK, Color.BLUE, Color.CYAN, Color.RED };
		
		String elName = "color";
		
		for (Color c : colors) {
			XMLUtils.colorToXML(root, c, elName);
			Element el = root.element(elName);
			Color c2 = XMLUtils.colorFromXML(el);
			assertEquals("colors not equal!", c, c2);
			root.remove(el);
		}
	}
	
	@Test
	public void testDoubleArrayXML() {
		double[] array = new double[1000];
		for (int i=0; i<array.length; i++) {
			if (i % 5 == 0)
				array[i] = Double.NaN;
			else if (i % 7 == 0)
				array[i] = Double.POSITIVE_INFINITY;
			else if (i % 11 == 0)
				array[i] = Double.MAX_VALUE;
			else if (i % 13 == 0)
				array[i] = Double.MIN_VALUE;
			else
				array[i] = Math.random();
		}
		
		String elName = "doubles";
		
		XMLUtils.doubleArrayToXML(root, array, elName);
		Element doubleArrayEl = root.element(elName);
		double[] array2 = XMLUtils.doubleArrayFromXML(doubleArrayEl);
		
		assertEquals("array size incorrect", array.length, array2.length);
		for (int i=0; i<array.length; i++) {
			if (Double.isNaN(array[i]))
				assertTrue("should be NaN!", Double.isNaN(array2[i]));
			else if (Double.isInfinite(array[i]))
				assertTrue("should be infinite!", Double.isInfinite(array2[i]));
			else
				assertEquals("wrong at index: "+i, array[i], array2[i], array[i]/10000d);
		}
	}
	
	@Test
	public void testIntArrayXML() {
		int[] array = new int[1000];
		Random r = new Random();
		for (int i=0; i<array.length; i++) {
			if (i % 5 == 0)
				array[i] = Integer.MAX_VALUE;
			else if (i % 7 == 0)
				array[i] = Integer.MIN_VALUE;
			else
				array[i] = r.nextInt(Integer.MAX_VALUE) - (Integer.MAX_VALUE/2);
		}
		
		String elName = "doubles";
		
		XMLUtils.intArrayToXML(root, array, elName);
		Element intArrayEl = root.element(elName);
		int[] array2 = XMLUtils.intArrayFromXML(intArrayEl);
		
		assertEquals("array size incorrect", array.length, array2.length);
		for (int i=0; i<array.length; i++) {
			assertEquals("wrong at index: "+i, array[i], array2[i]);
		}
	}

}
