package org.opensha.commons.param.editor.impl;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.jfree.data.Range;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.WarningException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditor;

public class RangeParameterEditor extends AbstractParameterEditor<Range> implements FocusListener, KeyListener {
	
	private NumericTextField lowerField, upperField;
	
	private JPanel panel;
	
	private boolean keyTypeProcessing;
	private boolean focusLostProcessing;
	
	public RangeParameterEditor(Parameter<Range> param) {
		super(param);
	}

	@Override
	public boolean isParameterSupported(Parameter<Range> param) {
		return param != null;
	}

	@Override
	public void setEnabled(boolean enabled) {
		lowerField.setEnabled(enabled);
		upperField.setEnabled(enabled);
	}

	@Override
	protected JComponent buildWidget() {
		Dimension sizeDim = new Dimension((int)(LABEL_DIM.getWidth()*0.6), (int)LABEL_DIM.getHeight());
		
		lowerField = new NumericTextField();
		lowerField.setMinimumSize( sizeDim );
		lowerField.setPreferredSize( sizeDim );
		lowerField.setBorder(ETCHED);
		lowerField.setFont(DEFAULT_FONT);

		lowerField.addFocusListener( this );
		lowerField.addKeyListener( this );
		
		upperField = new NumericTextField();
		upperField.setMinimumSize( sizeDim );
		upperField.setPreferredSize( sizeDim );
		upperField.setBorder(ETCHED);
		upperField.setFont(DEFAULT_FONT);

		upperField.addFocusListener( this );
		upperField.addKeyListener( this );
		
		panel = new JPanel(new BorderLayout());
		panel.add(lowerField, BorderLayout.WEST);
		panel.add(upperField, BorderLayout.EAST);
		
		updateWidget();
		
		return panel;
	}

	@Override
	protected JComponent updateWidget() {
		Range val = getValue();
		if (val == null) {
			lowerField.setText("");
			upperField.setText("");
		} else {
			lowerField.setText(val.getLowerBound()+"");
			lowerField.setCaretPosition(0);
			upperField.setText(val.getUpperBound()+"");
			upperField.setCaretPosition(0);
		}
		return panel;
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
		String lowerValue = lowerField.getText();
		String upperValue = upperField.getText();

		if(D) System.out.println("New Value = " + lowerValue+" => "+upperValue);
		try {
			Double lowerVal;
			if (lowerValue.equals(""))
				lowerVal = null;
			else
				lowerVal = Double.parseDouble(lowerValue);
			Double upperVal;
			if (upperValue.equals(""))
				upperVal = null;
			else
				upperVal = Double.parseDouble(upperValue);
			if (lowerVal == null || upperVal == null) {
				setValue(null);
			} else {
				if (lowerVal > upperVal) {
					double t = upperVal;
					upperVal = lowerVal;
					lowerVal = t;
				}
				setValue(new Range(lowerVal, upperVal));
			}
			refreshParamEditor();
			panel.validate();
			panel.repaint();
		} catch (WarningException ee){
			refreshParamEditor();
			panel.validate();
			panel.repaint();
		} catch (Exception ee) {
			if(D) System.out.println("Error = " + ee.toString());

			this.unableToSetValue(lowerValue+", "+upperValue);
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {}

	@Override
	public void keyReleased(KeyEvent e) {}

	@Override
	public void focusGained(FocusEvent e) {}

}
