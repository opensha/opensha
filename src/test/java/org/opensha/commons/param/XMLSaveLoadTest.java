package org.opensha.commons.param;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.dom4j.Document;
import org.dom4j.Element;
import org.junit.Test;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.param.impl.ArbitrarilyDiscretizedFuncParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.IntegerParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.XMLUtils;


public class XMLSaveLoadTest {
	
	private void testXMLSave(Parameter param1, Parameter param2) {
		Object origVal = param1.getValue();
		
		Document doc = XMLUtils.createDocumentWithRoot();
		Element root = doc.getRootElement();
		
		param1.toXMLMetadata(root);
		
		param2.setValueFromXMLMetadata(root.element(AbstractParameter.XML_METADATA_NAME));
		
		String cname = ClassUtils.getClassNameWithoutPackage(param1.getClass());
		
		assertEquals("Param of type '"+cname+"' XML didn't work!", origVal, param2.getValue());
	}
	
	@Test
	public void testStringParam() {
		StringParameter param1 = new StringParameter("param1");
		StringParameter param2 = new StringParameter("param2");
		
		param1.setValue("this is my value");
		testXMLSave(param1, param2);
		
		param1.setValue(null);
		testXMLSave(param1, param2);
		
		param1.setValue("this is my new value");
		testXMLSave(param1, param2);
	}
	
	@Test
	public void testDoubleParam() {
		DoubleParameter param1 = new DoubleParameter("param1");
		DoubleParameter param2 = new DoubleParameter("param2");
		
		param1.setValue(5d);
		testXMLSave(param1, param2);
		
		param1.setValue(null);
		testXMLSave(param1, param2);
		
		param1.setValue(2d);
		testXMLSave(param1, param2);
	}
	
	@Test
	public void testIntegerParam() {
		IntegerParameter param1 = new IntegerParameter("param1");
		IntegerParameter param2 = new IntegerParameter("param2");
		
		param1.setValue(5);
		testXMLSave(param1, param2);
		
		param1.setValue(null);
		testXMLSave(param1, param2);
		
		param1.setValue(2);
		testXMLSave(param1, param2);
	}
	
	@Test
	public void testConstStringParam() {
		ArrayList<String> strings = new ArrayList<String>();
		strings.add("String 1");
		strings.add("String 2");
		strings.add("String 3");
		
		StringParameter param1 = new StringParameter("param1", strings);
		StringParameter param2 = new StringParameter("param2", strings);
		
		param1.setValue("String 1");
		testXMLSave(param1, param2);
		
		param1.setValue("String 2");
		testXMLSave(param1, param2);
	}
	
	@Test
	public void testArbDiscFunc() {
		ArbitrarilyDiscretizedFuncParameter param1 = new ArbitrarilyDiscretizedFuncParameter("param1", null);
		ArbitrarilyDiscretizedFuncParameter param2 = new ArbitrarilyDiscretizedFuncParameter("param2", null);
		
		ArbitrarilyDiscretizedFunc func1 = new ArbitrarilyDiscretizedFunc();
		func1.set(1d, -1d);
		func1.set(2d, -2d);
		func1.setInfo("first func");
		ArbitrarilyDiscretizedFunc func2 = new ArbitrarilyDiscretizedFunc();
		func2.set(19d, -41d);
		func2.set(223d, -421d);
		func2.setInfo("second func");
		
		param1.setValue(func1);
		testXMLSave(param1, param2);
		
		param1.setValue(null);
		testXMLSave(param1, param2);
		
		param1.setValue(func2);
		testXMLSave(param1, param2);
	}

}
