package org.opensha.commons.param;


import static org.junit.Assert.*;

import org.dom4j.Document;
import org.dom4j.Element;
import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.data.Named;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.param.impl.WeightedListParameter;
import org.opensha.commons.util.XMLUtils;

public class WeightedListParameterXMLText {
	
	private class SimpleNamedObject implements Named {
		private String name;
		
		public SimpleNamedObject(String name) {
			this.name = name;
		}
		@Override
		public String getName() {
			return name;
		}
		
	}
	
	private WeightedList<SimpleNamedObject> list1234;
	private WeightedList<SimpleNamedObject> list4321;
	private WeightedListParameter<SimpleNamedObject> param1;
	private WeightedListParameter<SimpleNamedObject> param2;
	
	private Document doc;
	private Element root;
	

	@Before
	public void setUp() throws Exception {
		list1234 = new WeightedList<WeightedListParameterXMLText.SimpleNamedObject>();
		list4321 = new WeightedList<WeightedListParameterXMLText.SimpleNamedObject>();
		
		list1234.add(new SimpleNamedObject("first"), 1);
		list1234.add(new SimpleNamedObject("second"), 2);
		list1234.add(new SimpleNamedObject("third"), 3);
		list1234.add(new SimpleNamedObject("fourth"), 4);
		
		list4321.add(new SimpleNamedObject("first"), 4);
		list4321.add(new SimpleNamedObject("second"), 3);
		list4321.add(new SimpleNamedObject("third"), 2);
		list4321.add(new SimpleNamedObject("fourth"), 1);
		
		param1 = new WeightedListParameter<WeightedListParameterXMLText.SimpleNamedObject>("param1", list1234);
		param2 = new WeightedListParameter<WeightedListParameterXMLText.SimpleNamedObject>("param2", list4321);
		
		doc = XMLUtils.createDocumentWithRoot();
		root = doc.getRootElement();
	}
	
	@Test
	public void testListXML() {
		list1234.toXMLMetadata(root);
		
		for (int i=0; i<list1234.size(); i++) {
			assertFalse("weights shouldn't match at first", (float)list1234.getWeight(i) == (float)list4321.getWeight(i));
		}
		
		list4321.setWeightsFromXMLMetadata(root.element(WeightedList.XML_METADATA_NAME));
		
		for (int i=0; i<list1234.size(); i++) {
			assertEquals("weights not set correctly!", list1234.getWeight(i), list4321.getWeight(i), 0.00001);
		}
	}
	
	@Test
	public void testParamXML() {
		param1.toXMLMetadata(root);
		
		for (int i=0; i<list1234.size(); i++) {
			assertFalse("weights shouldn't match at first", (float)list1234.getWeight(i) == (float)list4321.getWeight(i));
		}
		
		param2.setValueFromXMLMetadata(root.element(AbstractParameter.XML_METADATA_NAME));
	
		for (int i=0; i<list1234.size(); i++) {
			assertEquals("weights not set correctly!", list1234.getWeight(i), list4321.getWeight(i), 0.00001);
		}
	}

}
