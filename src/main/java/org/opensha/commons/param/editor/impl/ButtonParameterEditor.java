package org.opensha.commons.param.editor.impl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComponent;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.impl.ButtonParameter;

public class ButtonParameterEditor extends AbstractParameterEditor<Integer> implements ActionListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private JButton button;

	public ButtonParameterEditor(ButtonParameter buttonParameter) {
		super(buttonParameter);
	}

	@Override
	public boolean isParameterSupported(Parameter<Integer> param) {
		return param instanceof ButtonParameter;
	}

	@Override
	public void setEnabled(boolean enabled) {
		button.setEnabled(enabled);
	}

	@Override
	protected JComponent buildWidget() {
		if (button == null) {
			button = new JButton();
			button.addActionListener(this);
		}
		return updateWidget();
	}

	@Override
	protected JComponent updateWidget() {
		ButtonParameter buttonParam = (ButtonParameter)getParameter();
		button.setText(buttonParam.getButtonText());
		return button;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == button) {
			Parameter<Integer> param = getParameter();
			param.setValue(param.getValue()+1);
		}
	}

}
