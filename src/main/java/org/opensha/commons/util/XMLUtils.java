/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.commons.util;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.opensha.commons.metadata.XMLSaveable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Static XML utility functions for creating XML documents, parsing XML files,
 * and saving XML to a file.
 * 
 * @author kevin
 *
 */
public class XMLUtils {
	
	/**
	 * Default name for document root element
	 */
	public static String DEFAULT_ROOT_NAME="OpenSHA";
	
	public static OutputFormat format = OutputFormat.createPrettyPrint();
	
	/**
	 * Writes an XML document to a file
	 * 
	 * @param fileName
	 * @param document
	 * @throws IOException
	 */
	public static void writeDocumentToFile(File file, Document document) throws IOException {
		
		XMLWriter writer;
		
		writer = new XMLWriter(new FileWriter(file), format);
		writer.write(document);
		writer.close();
	}
	
	public static String getDocumentAsString(Document document) throws IOException {
		StringWriter swrite = new StringWriter();
		
		XMLWriter writer;
		
		writer = new XMLWriter(swrite, format);
		writer.write(document);
		writer.close();
		
		return swrite.getBuffer().toString();
	}
	
	/**
	 * Creates a new XML document with a root element.
	 * 
	 * @return
	 */
	public static Document createDocumentWithRoot() {
		return createDocumentWithRoot(DEFAULT_ROOT_NAME);
	}
	
	/**
	 * Creates a new XML document with a root element.
	 * 
	 * @return
	 */
	public static Document createDocumentWithRoot(String rootName) {
		Document doc = DocumentHelper.createDocument();
		
		doc.addElement(rootName);
		
		return doc;
	}
	
	/**
	 * Loads an XML document from a file path
	 * 
	 * @return XML document
	 */
	public static Document loadDocument(File file) throws MalformedURLException, DocumentException {
		SAXReader read = new SAXReader();
		
		return read.read(file);
	}
	
	/**
	 * Loads an XML document from a file path
	 * 
	 * @return XML document
	 */
	public static Document loadDocument(String path) throws MalformedURLException, DocumentException {
		return loadDocument(new File(path));
	}
	
	/**
	 * Loads an XML document from a file path
	 * 
	 * @return XML document
	 */
	public static Document loadDocument(URL url) throws MalformedURLException, DocumentException {
		SAXReader read = new SAXReader();
		
		return read.read(url);
	}
	
	/**
	 * Loads an XML document from an input stream
	 * 
	 * @return XML document
	 * @throws DocumentException 
	 */
	public static Document loadDocument(InputStream is) throws DocumentException {
		SAXReader read = new SAXReader();
		
		return read.read(is);
	}
	
	/**
	 * Convenience method to write an XMLSaveable object to a file. It will be the only Element
	 * in the XML document under the default document root. 
	 * 
	 * @param obj
	 * @param fileName
	 * @throws IOException
	 */
	public static void writeObjectToXMLAsRoot(XMLSaveable obj, File file) throws IOException {
		Document document = createDocumentWithRoot();
		
		Element root = document.getRootElement();
		
		root = obj.toXMLMetadata(root);
		
		writeDocumentToFile(file, document);
	}
	
	/**
	 * Convenience method for writing a java 'Color' object to XML with the default
	 * element name of 'Color'
	 * 
	 * @param parent
	 * @param color
	 */
	public static void colorToXML(Element parent, Color color) {
		colorToXML(parent, color, "Color");
	}
	
	/**
	 * Convenience method for writing a java 'Color' object to XML with the given
	 * element name
	 * 
	 * @param parent
	 * @param color
	 * @param elName
	 */
	public static void colorToXML(Element parent, Color color, String elName) {
		Element el = parent.addElement(elName);
		el.addAttribute("r", color.getRed() + "");
		el.addAttribute("g", color.getGreen() + "");
		el.addAttribute("b", color.getBlue() + "");
		el.addAttribute("a", color.getAlpha() + "");
	}
	
	/**
	 * Convenience method for loading a java 'Color' object from XML
	 * 
	 * @param colorEl
	 * @return
	 */
	public static Color colorFromXML(Element colorEl) {
		int r = Integer.parseInt(colorEl.attributeValue("r"));
		int g = Integer.parseInt(colorEl.attributeValue("g"));
		int b = Integer.parseInt(colorEl.attributeValue("b"));
		int a = Integer.parseInt(colorEl.attributeValue("a"));
		
		return new Color(r, g, b, a);
	}
	
	public static void doubleArrayToXML(Element parent, double[] array, String elName) {
		byte[] bytes = new byte[array.length * 8];
		ByteBuffer buf = ByteBuffer.wrap(bytes);
		for (double val : array)
			buf.putDouble(val);
		byteArrayToXML(parent, buf.array(), elName);
	}
	
	public static double[] doubleArrayFromXML(Element doubleArrayEl) {
		byte[] data = byteArrayFromXML(doubleArrayEl);
		
		Preconditions.checkState(data.length % 8 == 0, "binary data not a multiple of 8 bits");
		int size = data.length / 8;
		
		ByteBuffer buf = ByteBuffer.wrap(data);
		double[] array = new double[size];
		for (int i=0; i<size; i++)
			array[i] = buf.getDouble();
		
		return array;
	}
	
	public static void intArrayToXML(Element parent, int[] array, String elName) {
		byte[] bytes = new byte[array.length * 4];
		ByteBuffer buf = ByteBuffer.wrap(bytes);
		for (int val : array)
			buf.putInt(val);
		byteArrayToXML(parent, buf.array(), elName);
	}
	
	public static int[] intArrayFromXML(Element intArrayEl) {
		byte[] data = byteArrayFromXML(intArrayEl);
		
		Preconditions.checkState(data.length % 4 == 0, "binary data not a multiple of 4 bits");
		int size = data.length / 4;
		
		ByteBuffer buf = ByteBuffer.wrap(data);
		int[] array = new int[size];
		for (int i=0; i<size; i++)
			array[i] = buf.getInt();
		
		return array;
	}
	
	public static void byteArrayToXML(Element parent, byte[] array, String elName) {
		Preconditions.checkNotNull(parent, "parent element can't be null");
		Preconditions.checkNotNull(array, "array cannot be null");
		Preconditions.checkArgument(array.length > 0, "array cannot be empty");
		Preconditions.checkNotNull(elName, "elName cannot be null");
		Preconditions.checkArgument(!elName.isEmpty(), "elName cannot be empty");
		Element el = parent.addElement(elName);
		String str = Base64.encodeBase64String(array);
		el.addCDATA(str);
	}
	
	public static byte[] byteArrayFromXML(Element byteArrayEl) {
		Preconditions.checkNotNull(byteArrayEl, "byteArrayEl element can't be null");
		String str = byteArrayEl.getText().trim();
		byte[] data = Base64.decodeBase64(str);
		return data;
	}
	
	/**
	 * Returns a list of sub elements sorted by the numerical value of the given attribute
	 * 
	 * @param parentEl
	 * @param sortAttributeName
	 * @return
	 */
	public List<Element> getSortedChildElements(Element parentEl, String sortAttributeName) {
		return getSortedChildElements(parentEl, null, sortAttributeName);
	}
	
	/**
	 * Returns a list of sub elements sorted by the numerical value of the given attribute
	 * 
	 * @param parentEl
	 * @param subElName name of sub elements, or null to consider any subelements
	 * @param sortAttributeName
	 * @return
	 */
	public static List<Element> getSortedChildElements(Element parentEl, String subElName, final String sortAttributeName) {
		Iterator<Element> it;
		if (subElName != null && !subElName.isEmpty())
			it = parentEl.elementIterator(subElName);
		else
			it = parentEl.elementIterator();
		
		List<Element> elems = Lists.newArrayList(it);
		
		// now sort
		Collections.sort(elems, new Comparator<Element>() {
			
			@Override
			public int compare(Element e1, Element e2) {
				double d1 = Double.parseDouble(e1.attributeValue(sortAttributeName));
				double d2 = Double.parseDouble(e2.attributeValue(sortAttributeName));
				return Double.compare(d1, d2);
			}
		});
		
		return elems;
	}
	
	public static List<Element> getSubElementsList(Element parentEl) {
		return getSubElementsList(parentEl, null);
	}
	
	public static List<Element> getSubElementsList(Element parentEl, String subElName) {
		if (subElName != null && !subElName.isEmpty())
			return parentEl.elements(subElName);
		return parentEl.elements();
	}

}
