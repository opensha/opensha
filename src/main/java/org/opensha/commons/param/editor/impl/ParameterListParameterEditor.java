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


import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ListIterator;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.ParameterListParameter;





/**
 * <p>Title: ParameterListParameterEditor</p>
 * <p>Description: This class is more like a parameterList consisting of any number of
 * parameters. This parameterList considered as a single Parameter.
 * This parameter editor will show up as the button on th GUI interface and
 * when the user punches the button, all the parameters will pop up in a seperate window
 * showing all the parameters contained within this parameter.</p>
 * @author : Ned Field, Nitin Gupta and Vipin Gupta
 * @created : Aug 14,2003
 * @version 1.0
 */

public class ParameterListParameterEditor extends AbstractParameterEditor<ParameterList> implements
ActionListener,ParameterChangeListener{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/** Class name for debugging. */
	protected final static String C = "ParameterListParameterEditor";
	/** If true print out debug statements. */
	protected final static boolean D = false;

	//Editor to hold all the parameters in this parameter
	protected ParameterListEditor editor;
	
	private JButton button;

	//Instance for the framee to show the all parameters in this editor
	protected JDialog frame;

	//checks if parameter has been changed
	protected boolean parameterChangeFlag;
	
	private boolean useButton;
	
	private boolean modal = true;
	
	private Dimension dialogDims = new Dimension(300,400);
	private Point dialogPosition = null;

	public ParameterListParameterEditor(Parameter<ParameterList> model) {
		this(model, true);
	}
	
	public ParameterListParameterEditor(Parameter<ParameterList> model, boolean useButton) {
		super(model);
		this.useButton = useButton;
		refreshParamEditor();
	}
	
	public void setDialogDimensions(Dimension dialogDims) {
		this.dialogDims = dialogDims;
	}

	/**
	 * It enables/disables the editor according to whether user is allowed to
	 * fill in the values.
	 */
	public void setEnabled(boolean isEnabled) {
		this.editor.setEnabled(isEnabled);
		if (useButton)
			this.button.setEnabled(isEnabled);
	}


//	/**
//	 * sets the title for this editor
//	 * @param title
//	 */
//	public void setEditorTitle(String title){
//		editor.setTitle(title);
//	}

	/**
	 *  Hides or shows one of the ParameterEditors in the ParameterList. setting
	 *  the boolean parameter to true shows the panel, setting it to false hides
	 *  the panel. <p>
	 *
	 * @param  parameterName  The parameter editor to toggle on or off.
	 * @param  visible      The boolean flag. If true editor is visible.
	 */
	public void setParameterVisible( String parameterName, boolean visible ) {
		editor.setParameterVisible(parameterName, visible);
	}

	/**
	 * Keeps track when parameter has been changed
	 * @param event
	 */
	public void parameterChange(ParameterChangeEvent event){
		parameterChangeFlag = true;
	}

	/**
	 * This function is called when the user click for the ParameterListParameterEditor Button
	 *
	 * @param ae
	 */
	public void actionPerformed(ActionEvent e ) {
		if (e.getSource() == button) {
			if (frame != null && frame.getWidth() > 0)
				dialogDims = frame.getSize();
			if (frame != null)
				dialogPosition = frame.getLocation();
			parameterChangeFlag = false;
			frame = new JDialog();
			frame.setModal(modal);
			frame.setSize(dialogDims);
			if (dialogPosition != null)
				frame.setLocation(dialogPosition);
			frame.setTitle(getParameter().getName());
			frame.getContentPane().setLayout(new GridBagLayout());
			frame.getContentPane().add(editor,new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
					,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(4, 4, 4, 4), 0, 0));

			//Adding Button to update the forecast
			JButton button = new JButton();
			button.setText("Update "+getParameter().getName());
			button.setForeground(new Color(80,80,133));
			button.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(ActionEvent e) {
					button_actionPerformed(e);
				}
			});
			frame.getContentPane().add(button,new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0
					,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(4, 4, 4, 4), 0, 0));
			frame.setVisible(true);
//			frame.getlo
//			frame.pack();
		}
	}


	/**
	 * This function is called when user punches the button to update the ParameterList Parameter
	 * @param e
	 */
	protected void button_actionPerformed(ActionEvent e) {
		ParameterList paramList = editor.getParameterList();
		if (parameterChangeFlag) {
			Parameter<ParameterList> param = getParameter();
			param.setValue(paramList);
			// this is needed because although the list hasn't changed, values inside of it have.
			param.firePropertyChange(new ParameterChangeEvent(param, param.getName(), param.getValue(), param.getValue()));
			parameterChangeFlag = false;
		}
		frame.dispose();
	}

	@Override
	public boolean isParameterSupported(Parameter<ParameterList> param) {
		if (param == null)
			return false;
		
		if (!(param instanceof ParameterListParameter))
			return false;
		
		return true;
	}

	@Override
	protected JComponent buildWidget() {
		ParameterList paramList = getParameter().getValue();
		ListIterator<Parameter<?>> it = paramList.getParametersIterator();
		while(it.hasNext())
			it.next().addParameterChangeListener(this);
		editor = new ParameterListEditor(paramList);
		editor.setTitle("Set "+getParameter().getName());
		
		if (useButton) {
			button = new JButton(getParameter().getName());
			button.addActionListener(this);
			return button;
		} else
			return editor;
	}

	@Override
	protected JComponent updateWidget() {
		ParameterList paramList = getParameter().getValue();
		ListIterator<Parameter<?>> it = paramList.getParametersIterator();
		while(it.hasNext())
			it.next().addParameterChangeListener(this);
		editor.setParameterList(paramList);
		
		if (useButton) {
			if (button == null) {
				button = new JButton(getParameter().getName());
				button.addActionListener(this);
			} else {
				button.setText(getParameter().getName());
			}
			return button;
		} else {
			return editor;
		}
	}
	
	public void setModal(boolean modal) {
		this.modal = modal;
		if (frame != null)
			frame.setModal(modal);
	}
}
