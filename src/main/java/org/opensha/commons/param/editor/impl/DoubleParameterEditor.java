/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.commons.param.editor.impl;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JComponent;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.WarningException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditor;

/**
 * <b>Title:</b> DoubleParameterEditor<p>
 *
 * <b>Description:</b> Subclass of ParameterEditor for editing DoubleParameters.
 * The widget is an DoubleTextField so that only numbers can be typed in. <p>
 *
 * The main functionality overidden from the parent class to achive Double
 * cusomization are the setWidgetObject() and AddWidget() functions. The parent's
 * class JComponent valueEditor field becomes an NumericTextField,  a subclass
 * of a JTextField.
 * Note: We have to create a double parameter with constraints if we want to reflect the constarints
 *       as the tooltip text in the GUI. Because when we editor is created for that
 *       double parameter, it creates a constraint double parameter and then we can
 *       change the constraint and it will be reflected in the tool tip text.
 * <p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */
public class DoubleParameterEditor extends AbstractParameterEditor<Double> implements FocusListener, KeyListener
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/** Class name for debugging. */
	protected final static String C = "DoubleParameterEditor";
	/** If true print out debug statements. */
	protected final static boolean D = false;
	
	private NumericTextField widget;
	
	private boolean focusLostProcessing = false;
	private boolean keyTypeProcessing = false;

	/** No-Arg constructor calls parent constructtor */
	protected DoubleParameterEditor() { super(); }

	/**
	 * Constructor that sets the parameter that it edits.
	 *
	 * Note: When calling the super() constuctor addWidget() is called
	 * which configures the NumericTextField as the editor widget. <p>
	 */
	public DoubleParameterEditor(Parameter model) throws Exception {

		super(model);
	}

	@Override
	protected JComponent buildWidget() {
		String S = C + "DoubleParameterEditor: addWidget(): ";
		if(D) System.out.println(S + "Starting");

		widget = new NumericTextField();
		widget.setMinimumSize( LABEL_DIM );
		widget.setPreferredSize( LABEL_DIM );
		widget.setBorder(ETCHED);
		widget.setFont(DEFAULT_FONT);

		widget.addFocusListener( this );
		widget.addKeyListener( this );
		
		updateWidget();

		if(D) System.out.println(S + "Ending");
		
		return widget;
	}

	/**
	 * Called everytime a key is typed in the text field to validate it
	 * as a valid number character ( digits, - sign in first position, etc. ).
	 */
	public void keyTyped(KeyEvent e) throws NumberFormatException {

		String S = C + ": keyTyped(): ";
		if(D) System.out.println(S + "Starting");

		keyTypeProcessing = false;
		if( focusLostProcessing == true ) return;


		if (e.getKeyChar() == '\n') {
			keyTypeProcessing = true;
			if(D) System.out.println(S + "Return key typed");
			String value = ((NumericTextField) widget).getText();

			if(D) System.out.println(S + "New Value = " + value);
			try {
				Double d = null;
				if( !value.equals( "" ) ) d = new Double(value);
				setValue(d);
				refreshParamEditor();
				widget.validate();
				widget.repaint();
			}
			catch (ConstraintException ee) {
				if(D) System.out.println(S + "Error = " + ee.toString());

				Object obj = getValue();
				if( obj != null )
					widget.setText(obj.toString());
				else
					widget.setText( "" );

				this.unableToSetValue(value);
				keyTypeProcessing = false;
			}
			catch (NumberFormatException ee) {
				if(D) System.out.println(S + "Error = " + ee.toString());

				Object obj = getValue();
				if( obj != null )
					widget.setText(obj.toString());
				else
					widget.setText( "" );

				this.unableToSetValue(value);
				keyTypeProcessing = false;
			}
			catch (WarningException ee){
				keyTypeProcessing = false;
				refreshParamEditor();
				widget.validate();
				widget.repaint();
			}
		}

		keyTypeProcessing = false;
		if(D) System.out.println(S + "Ending");
	}

	/**
	 * Called when the user clicks on another area of the GUI outside
	 * this editor panel. This synchornizes the editor text field
	 * value to the internal parameter reference.
	 */
	public void focusLost(FocusEvent e)throws ConstraintException {


		String S = C + ": focusLost(): ";
		if(D) System.out.println(S + "Starting");
		focusLostProcessing = false;
		if( keyTypeProcessing == true ) return;
		focusLostProcessing = true;

		String value = ((NumericTextField) widget).getText();
		try {

			Double d = null;
			if( !value.equals( "" ) ) d = new Double(value);
			setValue(d);
			refreshParamEditor();
			widget.validate();
			widget.repaint();
		}
		catch (ConstraintException ee) {
			if(D) System.out.println(S + "Error = " + ee.toString());

			Object obj = getValue();
			if( obj != null )
				widget.setText(obj.toString());
			else
				widget.setText( "" );

			this.unableToSetValue(value);
			focusLostProcessing = false;
		}
		catch (NumberFormatException ee) {
			if(D) System.out.println(S + "Error = " + ee.toString());

			Object obj = getValue();
			if( obj != null )
				widget.setText(obj.toString());
			else
				widget.setText( "" );

			this.unableToSetValue(value);
			keyTypeProcessing = false;
		}
		catch (WarningException ee){
			focusLostProcessing = false;
			refreshParamEditor();
			widget.validate();
			widget.repaint();
		}
		focusLostProcessing = false;
		if(D) System.out.println(S + "Ending");
	}

//	/** Sets the parameter to be edited. */
//	public void setParameter(ParameterAPI model) {
//		String S = C + ": setParameter(): ";
//		if(D) System.out.println(S + "Starting");
//
//		super.setParameter(model);
//		((NumericTextField) valueEditor).setToolTipText("No Constraints");
//
//		String info = model.getInfo();
//		if( (info != null ) && !( info.equals("") ) ){
//			this.nameLabel.setToolTipText( info );
//		}
//		else this.nameLabel.setToolTipText( null);
//
//
//		if(D) System.out.println(S + "Ending");
//	}

	@Override
	public boolean isParameterSupported(Parameter<Double> param) {
		if (param == null)
			return false;
		
		if (param.getValue() != null && !(param.getValue() instanceof Double))
			return false;
		
		return true;
	}

	@Override
	public void setEnabled(boolean enabled) {
		widget.setEditable(enabled);
	}

	@Override
	protected JComponent updateWidget() {
		Double val = getValue();
		if (val == null)
			widget.setText("");
		else
			widget.setText(val.toString());
		return widget;
	}

	@Override
	public void keyPressed(KeyEvent e) {}

	@Override
	public void keyReleased(KeyEvent e) {}

	@Override
	public void focusGained(FocusEvent e) {}
}
