package org.opensha.commons.param.impl;

import org.dom4j.Element;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.constraint.impl.LongConstraint;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.LongParameterEditor;

public class LongParameter extends AbstractParameter<Long> {
	
	private LongParameterEditor editor;
	
	public LongParameter(String name) {
		super(name, null, null, null);
	}
	
	public LongParameter(String name, long min, long max) {
		this(name, min, max, null);
	}
	
	public LongParameter(String name, long min, long max, Long value) {
		super(name, new LongConstraint(min, max), null, value);
	}

	@Override
	public synchronized ParameterEditor<Long> getEditor() {
		if (editor == null)
			editor = new LongParameterEditor(this);
		return editor;
	}

	@Override
	public Object clone() {
		LongParameter o = new LongParameter(getName());
		o.setConstraint(getConstraint());
		o.setValue(getValue());
		o.setInfo(getInfo());
		return o;
	}

	@Override
	protected boolean setIndividualParamValueFromXML(Element el) {
		try {
			long val = Long.parseLong(el.attributeValue("value"));
			this.setValue(val);
			return true;
		} catch (NumberFormatException e) {
			e.printStackTrace();
			return false;
		}
	}

}
