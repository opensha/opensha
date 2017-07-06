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
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.IntegerDiscreteConstraint;
import org.opensha.commons.param.editor.AbstractParameterEditor;

/**
 * <b>Title:</b> ConstrainedDoubleDiscreteParameterEditor<p>
 *
 * <b>Description:</b> This editor is for editing DoubleDiscreteParameters.
 * The widget is simply a picklist of all possible constrained values you
 * can choose from. <p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */

public class ConstrainedIntegerDiscreteParameterEditor
extends AbstractParameterEditor<Integer>
implements ItemListener
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/** Class name for debugging. */
	protected final static String C = "ConstrainedDoubleDiscreteParameterEditor";
	/** If true print out debug statements. */
	protected static final boolean D = false;

	private JComponent widget;

	/** No-Arg constructor calls super(); */
	public ConstrainedIntegerDiscreteParameterEditor() { super(); }

	/**
	 * Sets the model in this constructor. The parameter is checked that it is a
	 * DoubleDiscreteParameter, and the constraint is checked that it is a
	 * DoubleDiscreteConstraint. Then the constraints are checked that
	 * there is at least one. If any of these fails an error is thrown. <P>
	 *
	 * The widget is then added to this editor, based on the number of
	 * constraints. If only one the editor is made into a non-editable label,
	 * else a picklist of values to choose from are presented to the user.
	 * A tooltip is given to the name label if model info is available.
	 */
	public ConstrainedIntegerDiscreteParameterEditor(Parameter<Integer> model)
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

		Integer value = (Integer) ((JComboBox) widget).getSelectedItem();
		this.setValue(value);

		if(D) System.out.println(S + "Ending");
	}

	@Override
	public boolean isParameterSupported(Parameter<Integer> param) {
		if (param == null)
			return false;
		
		if (!(param.getValue() instanceof Integer))
			return false;

		ParameterConstraint constraint = param.getConstraint();
		
		if (constraint == null)
			return false;
		
		if (constraint.isNullAllowed())
			return false;

		if (!(constraint instanceof IntegerDiscreteConstraint))
			return false;
		
		IntegerDiscreteConstraint iconst = (IntegerDiscreteConstraint)constraint;

		int numConstriants = iconst.size();
		if(numConstriants < 1)
			return false;
		return true;
	}

	@Override
	public void setEnabled(boolean enabled) {
		// TODO Auto-generated method stub

	}

	@Override
	protected JComponent buildWidget() {
		IntegerDiscreteConstraint con = ((IntegerDiscreteConstraint)
				getParameter().getConstraint());
		
		ArrayList<Integer> vals = con.getAllowed();

		if(vals.size() > 1){
			JComboBox combo = new JComboBox(vals.toArray());
			combo.setMaximumRowCount(32);
			widget = combo;
			widget.setPreferredSize(WIGET_PANEL_DIM);
			widget.setMinimumSize(WIGET_PANEL_DIM);
//			widget.setFont(JCOMBO_FONT);
			//valueEditor.setBackground(this.BACK_COLOR);
			combo.setSelectedIndex(vals.indexOf(getParameter().getValue()));
			combo.addItemListener(this);
		}
		else{
			JLabel label = makeSingleConstraintValueLabel( vals.get(0).toString() );
			widget = new JPanel(new BorderLayout());
			widget.setBackground(Color.LIGHT_GRAY);
			widget.add(label);
//			widget.add(valueEditor, WIDGET_GBC);
		}
		return widget;
	}

	@Override
	protected JComponent updateWidget() {
		IntegerDiscreteConstraint con = ((IntegerDiscreteConstraint)
				getParameter().getConstraint());
		
		ArrayList<Integer> vals = con.getAllowed();
		
		if (vals.size() > 1) {
			if (widget instanceof JComboBox) {
				JComboBox combo = (JComboBox)widget;
				combo.removeItemListener(this);
				combo.setModel(new DefaultComboBoxModel(vals.toArray()));
				combo.setSelectedIndex(vals.indexOf(getParameter().getValue()));
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
