package org.opensha.sha.imr.attenRelImpl.test;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;

import org.dom4j.Document;
import org.dom4j.Element;
import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.imr.AbstractIMR;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;

public class XMLSaveLoadTest {

	//private AttenuationRelationshipsInstance attenRelInst;
	
	public XMLSaveLoadTest() {
	}

	@Before
	public void setUp() {
		//attenRelInst = new AttenuationRelationshipsInstance();
	}
	
	@Test
	public void testIMRSaveLoad() throws InvocationTargetException {
		List<? extends ScalarIMR> imrs = AttenRelRef.instanceList(null, true);
		
		Document doc = XMLUtils.createDocumentWithRoot();
		Element root = doc.getRootElement();
		
		for (ScalarIMR imr : imrs) {
			System.out.println("Handling '" + imr.getName() + "'");
			imr.setParamDefaults();
			imr.toXMLMetadata(root);
			Element imrElem = root.element(AbstractIMR.XML_METADATA_NAME);
			ScalarIMR fromXML = (ScalarIMR)
						AbstractIMR.fromXMLMetadata(imrElem, null);
			imrElem.detach();
			
			Iterator<Parameter<?>> it = imr.getOtherParamsIterator();
			while (it.hasNext()) {
				Parameter<?> origParam = it.next();
				Object origVal = origParam.getValue();
				Parameter<?> newParam = fromXML.getParameter(origParam.getName());
				Object newVal = newParam.getValue();
				if (origVal == null) {
					assertNull(newVal);
				} else {
					assertTrue(origParam.getValue().equals(newParam.getValue()));
				}
			}
		}
	}

}
