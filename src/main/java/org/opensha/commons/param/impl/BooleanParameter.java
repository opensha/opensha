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

package org.opensha.commons.param.impl;

import org.dom4j.Element;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.BooleanParameterEditor;


/**
 * <p>Title: BooleanParameter</p>
 * <p>Description: Makes a parameter which is a boolean</p>
 * @author : Nitin Gupta
 * @created : Dec 28, 2003
 * @version 1.0
 */

public class BooleanParameter extends AbstractParameter<Boolean> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/** Class name for debugging. */
	protected final static String C = "BooleanParameter";
	/** If true print out debug statements. */
	protected final static boolean D = false;

	protected final static String PARAM_TYPE ="BooleanParameter";

	private transient ParameterEditor<Boolean> paramEdit;

	/**
	 *  No constraints specified for this parameter. Sets the name of this
	 *  parameter.
	 *
	 * @param  name  Name of the parameter
	 */
	public BooleanParameter(String name) {
		super(name,null,null,new Boolean(false));
		setDefaultValue(value);
	}

	/**
	 * No constraints specified, all values allowed. Sets the name and value.
	 *
	 * @param  name   Name of the parameter
	 * @param  paramList  ParameterList  object
	 */
	public BooleanParameter(String name, Boolean value){
		super(name,null,null,value);
		setDefaultValue(value);
	}
	
	@Override
	public boolean isNullAllowed() { return false; }

//	@Override
//	public int compareTo(Parameter<Boolean> param) {
//		return value.compareTo(param.getValue());
//		if (param instanceof BooleanParameter)
//			return value.compareTo(((BooleanParameter)obj).getValue());
//		else
//			throw new ClassCastException("Cannot call compareTo on an object other than a boolean parameter");
//	}

	/**
	 * Returns the name of the parameter class
	 */
	public String getType() {
		String type = PARAM_TYPE;
		return type;
	}

	/** Returns a copy so you can't edit or damage the original. */
	public Object clone() {
		BooleanParameter param = null;
		param = new BooleanParameter(name,(Boolean)value);
		if( param == null ) return null;
		param.editable = true;
		param.info = info;
		return param;
	}

	/**
	 * Parses the XML element for a boolean value
	 */
	public boolean setIndividualParamValueFromXML(Element el) {
		this.setValue(Boolean.parseBoolean(el.attributeValue("value")));
		return true;
	}

	public ParameterEditor<Boolean> getEditor() {
		if (paramEdit == null)
			paramEdit = new BooleanParameterEditor(this);
		return paramEdit;
	}

}
