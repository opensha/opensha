package org.opensha.commons.param.impl;

import org.dom4j.Element;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.ButtonParameterEditor;

/**
 * This is a dummy parameter useful in GUIs/ParameterLists where buttons are needed but not
 * associated with an actual parameter. A ParameterChangeEvent will be fired whenever the button
 * is clicked in the editor.
 * 
 * @author kevin
 *
 */
public class ButtonParameter extends AbstractParameter<Integer> {
	
	private String buttonText;
	private ButtonParameterEditor editor;
	
	public ButtonParameter(String name, String buttonText) {
		super(name, null, null, null);
		this.buttonText = buttonText;
		this.setInfo(buttonText);
		this.setValue(0);
	}

	@Override
	public ParameterEditor getEditor() {
		if (editor == null)
			editor = new ButtonParameterEditor(this);
		return editor;
	}
	
	public String getButtonText() {
		return buttonText;
	}
	
	public void setButtonText(String buttonText) {
		this.buttonText = buttonText;
	}

	@Override
	public Object clone() {
		ButtonParameter param = new ButtonParameter(getName(), buttonText);
		param.setValue(getValue());
		return param;
	}

	@Override
	protected boolean setIndividualParamValueFromXML(Element el) {
		// TODO Auto-generated method stub
		return false;
	}

}
