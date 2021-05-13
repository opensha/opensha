package org.opensha.commons.param.impl;

import org.dom4j.Element;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.WeightedListParameterEditor;

public class WeightedListParameter<E> extends AbstractParameter<WeightedList<E>> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	WeightedListParameterEditor paramEdit;
	
	public WeightedListParameter(String name, WeightedList<E> value) {
		super(name, null, null, value);
//		System.out.println(getMetadataString());
	}

//	@Override
//	public int compareTo(Parameter<WeightedList<E>> param) {
//		return 0;
//	}

	@Override
	public ParameterEditor getEditor() {
		if (paramEdit == null)
			paramEdit = new WeightedListParameterEditor(this);
		return paramEdit;
	}
	
	public boolean isParameterEditorBuilt() {
		return paramEdit != null;
	}

	@Override
	public boolean setIndividualParamValueFromXML(Element el) {
		if (value == null)
			return false;
		Element valEl = el.element(XML_COMPLEX_VAL_EL_NAME);
		Element listEl = valEl.element(WeightedList.XML_METADATA_NAME);
		value.setWeightsFromXMLMetadata(listEl);
		return true;
	}

	@Override
	public String getMetadataString() {
		WeightedList<E> val = getValue();
		if(val !=null) {
			String str = name+" = [";
			
			for (int i=0; i<val.size(); i++) {
				if (i > 0)
					str += ", ";
				String name = WeightedList.getName(val.get(i));
				str += "'"+name+"': "+val.getWeight(i);
			}
			
			str += "]";
			return str;
		} else {
			return name+" = "+"null";
		}
	}

	@Override
	public Object clone() {
		// TODO Auto-generated method stub
		return null;
	}

}
