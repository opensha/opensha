package org.opensha.commons.param.editor.impl;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JComponent;
import javax.swing.JTextField;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.WarningException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditor;

public class LongParameterEditor extends AbstractParameterEditor<Long> implements FocusListener, KeyListener {
	
	private JTextField widget;
	
	private boolean keyTypeProcessing;
	private boolean focusLostProcessing;
	
	public LongParameterEditor(Parameter<Long> param) {
		super(param);
	}

	@Override
	public boolean isParameterSupported(Parameter<Long> param) {
		if (param == null)
			return false;
		if (param.getValue() != null && !(param.getValue() instanceof Long))
			return false;
		return true;
	}

	@Override
	public void setEnabled(boolean enabled) {
		widget.setEnabled(enabled);
	}
	
	/**
	 * Called everytime a key is typed in the text field to validate it
	 * as a valid integer character ( digits and - sign in first position ).
	 */
	public void keyTyped(KeyEvent e) {


		String S = C + ": keyTyped(): ";
		if(D) System.out.println(S + "Starting");

		keyTypeProcessing = false;
		if( focusLostProcessing == true ) return;


		if (e.getKeyChar() == '\n') {
			keyTypeProcessing = true;
			if(D) System.out.println(S + "Return key typed");
			
			setParamFromField();
		}

		keyTypeProcessing = false;
		if(D) System.out.println(S + "Ending");


	}

	/**
	 * Called when the user clicks on another area of the GUI outside
	 * this editor panel. This synchornizes the editor text field
	 * value to the internal parameter reference.
	 */
	public void focusLost(FocusEvent e) {

		String S = C + ": focusLost(): ";
		if(D) System.out.println(S + "Starting");

		focusLostProcessing = false;
		if( keyTypeProcessing == true ) return;
		focusLostProcessing = true;

		setParamFromField();

		focusLostProcessing = false;
		if(D) System.out.println(S + "Ending");

	}
	
	private void setParamFromField() {
		String value = widget.getText();

		if(D) System.out.println("New Value = " + value);
		try {
			Long val ;
			if (value.equals(""))
				val = null;
			else
				val = Long.parseLong(value);
			setValue(val);
			refreshParamEditor();
			widget.validate();
			widget.repaint();
		} catch (NumberFormatException ee) {
			if(D) System.out.println("Error = " + ee.toString());

			Long obj = getValue();
			if (obj == null)
				widget.setText("");
			else
				widget.setText(obj.toString());

			this.unableToSetValue(value);
		} catch (ConstraintException ee) {
			if(D) System.out.println("Error = " + ee.toString());

			Long obj = getValue();
			if (obj == null)
				widget.setText("");
			else
				widget.setText(obj.toString());

			this.unableToSetValue(value);
		}
		catch (WarningException ee){
			refreshParamEditor();
			widget.validate();
			widget.repaint();
		}
	}
	
	@Override
	protected JComponent buildWidget() {
		widget = new JTextField();
		widget.setPreferredSize(LABEL_DIM);
		widget.setMinimumSize(LABEL_DIM);
		widget.setBorder(ETCHED);

		widget.addFocusListener( this );
		widget.addKeyListener(this);

		updateWidget();
		
		return widget;
	}

	@Override
	protected JComponent updateWidget() {
		Long val = getValue();
		if (val == null)
			widget.setText("");
		else
			widget.setText(val+"");
		return widget;
	}

	@Override
	public void keyPressed(KeyEvent e) {}

	@Override
	public void keyReleased(KeyEvent e) {}

	@Override
	public void focusGained(FocusEvent e) {}

}
