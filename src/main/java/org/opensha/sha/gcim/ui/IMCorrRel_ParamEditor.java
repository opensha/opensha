package org.opensha.sha.gcim.ui;

import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;

public class IMCorrRel_ParamEditor extends ParameterListEditor implements ParameterChangeListener {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static final String DEFAULT_NAME = "IMCorrRel Params";
	
	private ImCorrelationRelationship imCorrRel;
	
	public IMCorrRel_ParamEditor() {
		this(null);
	}
	
	public IMCorrRel_ParamEditor(ImCorrelationRelationship imCorrRel) {
		setTitle(DEFAULT_NAME);
		this.setIMCorrRel(imCorrRel);
	}
	
	public void setIMCorrRel(ImCorrelationRelationship imCorrRel) {
		if (imCorrRel == this.imCorrRel) {
			this.validate();
			return;
		}
		this.imCorrRel = imCorrRel;
		if (imCorrRel == null) {
			this.setParameterList(null);
			this.validate();
			return;
		}
		ParameterList paramList = imCorrRel.getOtherParamsList();
		this.setParameterList(paramList);
		for (Parameter<?> param : paramList) {
			if (param.getName().equals(SigmaTruncTypeParam.NAME)) {
				String val = (String)param.getValue();
				param.addParameterChangeListener(this);
			}
		}
		this.validate();
	}
	
	/**
	 * Set the Tectonic Region Parameter visibility
	 * 
	 * @param visible
	 */
	public void setTRTParamVisible(boolean visible) {
		if (this.imCorrRel == null)
			return;
		try {
			// if it doesn't have the param, an exception will be thrown here
			this.imCorrRel.getParameter(TectonicRegionTypeParam.NAME); 
		} catch (ParameterException e) {
			// the IMCorrRel doesn't have a TRT param...do nothing
			return;
		}
	}

	public void parameterChange(ParameterChangeEvent event) {

	}

}

