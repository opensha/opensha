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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.impl.StringParameter;

/**
 * <b>Title:</b> ConstrainedStringParameterEditor<p>
 *
 * <b>Description:</b> This editor is for editing
 * ConstrainedStringParameters. Recall a ConstrainedStringParameter
 * contains a list of the only allowed values. Therefore this editor
 * presents a picklist of those allowed values, instead of a
 * JTextField or subclass. <p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */

public class ConstrainedStringParameterEditor
extends AbstractParameterEditor<String>
implements ItemListener
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/** Class name for debugging. */
	protected final static String C = "ConstrainedStringParameterEditor";
	/** If true print out debug statements. */
	protected final static boolean D = false;
	
	private JComponent widget;

	/**
	 * Sets the model in this constructor. The parameter is checked that it is a
	 * StringParameter, and the constraint is checked that it is a
	 * StringConstraint. Then the constraints are checked that
	 * there is at least one. If any of these fails an error is thrown. <P>
	 *
	 * The widget is then added to this editor, based on the number of
	 * constraints. If only one the editor is made into a non-editable label,
	 * else a picklist of values to choose from are presented to the user.
	 * A tooltip is given to the name label if model info is available.
	 */
	public ConstrainedStringParameterEditor(Parameter<String> model)
	throws ConstraintException {

		super(model);
	}

	/**
	 * Called whenever a user picks a new value in the picklist, i.e.
	 * synchronizes the model to the new GUI value. This is where the
	 * picklist value is set in the ParameterAPI of this editor.
	 */
	public void itemStateChanged(ItemEvent e) {
		String S = C + ": itemStateChanged(): ";
		if(D) System.out.println(S + "Starting: " + e.toString());

		String value = ((JComboBox) widget).getSelectedItem().toString();
		if(D) System.out.println(S + "New Value = " + (value) );
		this.setValue(value);

		if(D) System.out.println(S + "Ending");
	}

	@Override
	public boolean isParameterSupported(Parameter<String> param) {
		if (param == null)
			return false;
		
		if (!(param.getValue() instanceof String))
			return false;

		if (!(param instanceof StringParameter))
			return false;

		ParameterConstraint constraint = param.getConstraint();

		if (!(constraint instanceof StringConstraint))
			return false;

		int numConstriants = ((StringConstraint)constraint).size();
		if(numConstriants < 1)
			return false;

		if (param.isNullAllowed())
			return false;

		if (param.getValue() == null)
			return false;
		return true;
	}

	@Override
	public void setEnabled(boolean enabled) {
		widget.setEnabled(enabled);
	}

	@Override
	protected JComponent buildWidget() {
		StringConstraint con =
			(StringConstraint) (getParameter()).getConstraint();
		
		ArrayList<String> strs = con.getAllowedStrings();

		if(strs.size() > 1){
			JComboBox combo = new JComboBox(strs.toArray());
			combo.setMaximumRowCount(32);
			widget = combo;
			widget.setPreferredSize(WIGET_PANEL_DIM);
			widget.setMinimumSize(WIGET_PANEL_DIM);
//			widget.setFont(JCOMBO_FONT);
			//valueEditor.setBackground(this.BACK_COLOR);
			combo.setSelectedIndex(strs.indexOf(getParameter().getValue()));
			combo.addItemListener(this);
		}
		else{
			JLabel label = makeSingleConstraintValueLabel( strs.get(0).toString() );
			widget = new JPanel(new BorderLayout());
			widget.setBackground(Color.LIGHT_GRAY);
			widget.add(label);
//			widget.add(valueEditor, WIDGET_GBC);
		}
		return widget;
	}

	@Override
	protected JComponent updateWidget() {
		StringConstraint con =
			(StringConstraint) (getParameter()).getConstraint();
		
		ArrayList<String> strs = con.getAllowedStrings();
		
		if (strs.size() > 1) {
			if (widget instanceof JComboBox) {
				JComboBox combo = (JComboBox)widget;
				combo.removeItemListener(this);
				combo.setModel(new DefaultComboBoxModel(strs.toArray()));
				combo.setSelectedIndex(strs.indexOf(getParameter().getValue()));
				combo.addItemListener(this);
				return widget;
			} else {
				return buildWidget();
			}
		} else {
			return buildWidget();
		}
	}


}
