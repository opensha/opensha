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

import javax.swing.JComponent;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.constraint.impl.IntegerConstraint;
import org.opensha.commons.util.ParamUtils;


/**
 * <b>Title:</b> ConstrainedIntegerParameterEditor<pr>
 *
 * <b>Description:</b> Special ParameterEditor for editing
 * ConstrainedIntegetParameters which recall have a minimum and maximum
 * allowed values. The widget is an IntegerTextField
 * so that only integers can be typed in. When hitting <enter> or moving
 * the mouse away from the IntegerTextField, the value will change back
 * to the original if the new number is outside the constraints range.
 * The constraints also appear as a tool tip when you hold the mouse
 * cursor over the IntegerTextField. <p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */
public class ConstrainedIntegerParameterEditor extends IntegerParameterEditor
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/** Class name for debugging. */
	protected final static String C = "ConstrainedIntegerParameterEditor";
	/** If true print out debug statements. */
	protected final static boolean D = false;
	
	private Color origFG;

	/** No-Arg constructor calls parent constructtor */
	public ConstrainedIntegerParameterEditor() { super(); }

	/**
	 * Constructor that sets the parameter that it edits.
	 * Only calls the super() function.
	 */
	public ConstrainedIntegerParameterEditor(Parameter model)
	throws Exception{
		super(model);
		this.setParameter(model);
	}

	@Override
	protected JComponent buildWidget() {
		// TODO Auto-generated method stub
		IntegerTextField comp = (IntegerTextField)super.buildWidget();
		
		if (origFG == null)
			origFG = comp.getForeground();
		
		IntegerConstraint constraint =getConstraint();
		// .intValue is required because == on Integer's doesn
		if (constraint.getMax().equals(constraint.getMin())) {
			comp.setEditable(false);
			comp.setForeground( Color.blue );
//			comp.setBorder( CONST_BORDER ); // TODO
		}
		
		return comp;
	}

	@Override
	protected JComponent updateWidget() {
		IntegerTextField comp = (IntegerTextField)super.updateWidget();
		
		IntegerConstraint constraint =getConstraint();
		if (constraint.getMax().equals(constraint.getMin())) {
			comp.setEditable(false);
			comp.setForeground( Color.blue );
		} else {
			comp.setEditable(true);
			comp.setForeground( origFG );
		}
		return comp;
	}

	/**
	 * @return the IntegerConstraint
	 */
	protected IntegerConstraint getConstraint(){
		//Integer constraint declaration
		IntegerConstraint constraint;
		
		Parameter<Integer> param = getParameter();

		if( ParamUtils.isWarningParameterAPI( param ) ){
			constraint = (IntegerConstraint)((WarningParameter)param).getWarningConstraint();
			if( constraint == null ) constraint = (IntegerConstraint) param.getConstraint();
		}
		else
			constraint = (IntegerConstraint) param.getConstraint();

		return constraint;
	}

	/**
	 * set the tool tip contraint text
	 */
	@Override
	protected String getWidgetToolTipText() {
		IntegerConstraint constraint =getConstraint();
		String text = super.getWidgetToolTipText();
		if (text == null || text.length() == 0) {
			text = "";
		} else {
			text += "\n";
		}
		text += "Min = " + constraint.getMin().toString() + "; Max = " + constraint.getMax().toString();
		return text;
	}


}
