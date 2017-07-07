package org.opensha.sha.imr.mod.impl.stewartSiteSpecific;

import org.dom4j.Element;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.editor.ParameterEditor;

public class PeriodDependentParamSetParam<E extends Enum<E>> extends
		AbstractParameter<PeriodDependentParamSet<E>> {
	
	private PeriodDependentParamSetEditor<E> editor;
	
	public PeriodDependentParamSetParam(String name, PeriodDependentParamSet<E> value) {
		super(name, null, null, value);
	}

	@Override
	public ParameterEditor getEditor() {
		if (editor == null)
			editor = new PeriodDependentParamSetEditor<E>(this);
		return editor;
	}

	@Override
	public Object clone() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected boolean setIndividualParamValueFromXML(Element el) {
		return false;
	}

}
