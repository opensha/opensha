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


import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.border.Border;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditor;





/**
 * <p>Title: BooleanParameterEditor</p>
 * <p>Description: </p>
 * @author : Nitin Gupta
 * @created : Dec 28,2003
 * @version 1.0
 */

public class BooleanParameterEditor extends AbstractParameterEditor<Boolean> implements
ItemListener{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/** Class name for debugging. */
	protected final static String C = "BooleanParameterEditor";
	/** If true print out debug statements. */
	protected final static boolean D = false;
	
	private JCheckBox widget;

	public BooleanParameterEditor(Parameter<Boolean> model){
		super(model);
	}

	/**
	 * This function is called when the user click for the BooleanParameterEditor Button
	 *
	 * @param ae
	 */
	public void itemStateChanged(ItemEvent ae ) {
		this.setValue(widget.isSelected());
	}

	@Override
	public boolean isParameterSupported(Parameter<Boolean> param) {
		if (param == null)
			return false;
		if (!(param.getValue() instanceof Boolean))
			return false;
		return true;
	}

	@Override
	public void setEnabled(boolean enabled) {
		widget.setEnabled(enabled);
	}

	@Override
	protected JComponent buildWidget() {
		widget = new JCheckBox(getParameter().getName());
		widget.setPreferredSize(LABEL_DIM);
		widget.setMinimumSize(LABEL_DIM);
		widget.setBorder(ETCHED);

		widget.setSelected(getParameter().getValue());
		widget.addItemListener(this);
		
		return widget;
	}

	@Override
	protected JComponent updateWidget() {
		widget.setText(getParameter().getName());
		widget.setSelected(getParameter().getValue());
		return widget;
	}
	
	@Override
	protected void updateTitle() {
		super.setTitle(null);
	}

}
