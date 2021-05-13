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

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.util.ParamUtils;

/**
 * <b>Title:</b> ConstrainedDoubleParameterEditor<p>
 *
 * <b>Description:</b> Special ParameterEditor for editing Constrained
 * DoubleParameters. The widget is a NumericTextField so that only
 * numbers can be typed in. When hitting <enter> or moving the
 * mouse away from the NumericField, the value will change back to the
 * original if the new number is outside the constraints range. The constraints
 * also appear as a tool tip when you hold the mouse cursor over
 * the NumericTextField. <p>
 *
 * @author Steven W. Rock
 * @version 1.0
 */
public class ConstrainedDoubleParameterEditor extends DoubleParameterEditor {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/** Class name for debugging. */
	protected final static String C = "ConstrainedDoubleParameterEditor";
	/** If true print out debug statements. */
	protected final static boolean D = false;


	/** No-Arg constructor calls parent constructtor */
	protected ConstrainedDoubleParameterEditor() { super(); }

	/**
	 * Constructor that sets the parameter that it edits.
	 * Only calls the super() function.
	 */
	public ConstrainedDoubleParameterEditor(Parameter model)
	throws Exception {
		super(model);
	}

	//    /**
	//     * Calls the super().;setFunction() and uses the constraints
	//     * to set the JTextField tooltip to show the constraint values.
	//     */
	//    public void setParameter(ParameterAPI model) {
	//
	//        String S = C + ": setParameter(): ";
	//        if(D)System.out.println(S + "Starting");
	//        super.setParameter(model);
	//        setToolTipText();
	//        this.setNameLabelToolTip(model.getInfo());
	//        if(D) System.out.println(S + "Ending");
	//    }
	//
	//    public void setWidgetBorder(Border b){
	//        ((NumericTextField)valueEditor).setBorder(b);
	//    }
	
	/**
	 * @return the DoubleConstraint
	 */
	protected DoubleConstraint getConstraint(){
		//Double constraint declaration
		DoubleConstraint constraint;
		if( ParamUtils.isWarningParameterAPI( getParameter() ) ){
			constraint = (DoubleConstraint)((WarningParameter)getParameter()).getWarningConstraint();
			if( constraint == null ) constraint = (DoubleConstraint) getParameter().getConstraint();
		}
		else constraint = (DoubleConstraint) getParameter().getConstraint();
		return constraint;
	}

	@Override
	protected String getWidgetToolTipText() {
		DoubleConstraint constraint =getConstraint();
		return "Min = " + constraint.getMin().toString() + "; Max = " + constraint.getMax().toString();
	}

}
