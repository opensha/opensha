package org.opensha.commons.param.impl;

import java.util.ListIterator;

import org.dom4j.Element;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.ParameterListParameterEditor;


/**
 * <p>Title: ParameterListParameter</p>
 * <p>Description: Make a parameter which is basically a parameterList</p>
 * @author : Nitin Gupta and Vipin Gupta
 * @created : Aug 18, 2003
 * @version 1.0
 */

public class ParameterListParameter extends AbstractParameter<ParameterList> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/** Class name for debugging. */
	protected final static String C = "ParameterListParameter";
	/** If true print out debug statements. */
	protected final static boolean D = false;

	protected final static String PARAM_TYPE ="ParameterListParameter";

	private transient ParameterEditor<ParameterList> paramEdit = null;

	/**
	 *  No constraints specified for this parameter. Sets the name of this
	 *  parameter.
	 *
	 * @param  name  Name of the parameter
	 */
	public ParameterListParameter(String name) {
		super(name,null,null,null);
	}

	/**
	 * No constraints specified, all values allowed. Sets the name and value.
	 *
	 * @param  name   Name of the parameter
	 * @param  paramList  ParameterList  object
	 */
	public ParameterListParameter(String name, ParameterList paramList) {
		super(name,null,null,paramList);
		// setting the independent Param List for this parameter
		setIndependentParameters(paramList);
	}

	/**
	 * Set's the parameter's value, which is basically a parameterList.
	 *
	 * @param  value                 The new value for this Parameter
	 * @throws  ParameterException   Thrown if the object is currenlty not
	 *      editable
	 */
	public void setValue(ParameterList value) throws ParameterException {
		// ListIterator it = value.getParametersIterator();
		super.setValue(value);
		// Setting the independent Param List for this parameter
		this.setIndependentParameters(value);
	}

	/**
	 *  Returns a copy so you can't edit or damage the origial.
	 *
	 * @return    Exact copy of this object's state
	 */
	public Object clone() {
		ParameterListParameter param = null;
		if (value == null)
			param = new ParameterListParameter(name);
		else
			param = new ParameterListParameter(name, (ParameterList)value);
		param.editable = true;
		return param;
	}

	/**
	 * Returns the ListIterator of the parameters included within this parameter
	 * @return
	 */
	public ListIterator getParametersIterator() {
		return ((ParameterList)this.getValue()).getParametersIterator();
	}

	/**
	 *
	 * @return the parameterList contained in this parameter
	 */
	public ParameterList getParameter(){
		return (ParameterList)getValue();
	}

	/**
	 * Returns the name of the parameter class
	 */
	public String getType() {
		return PARAM_TYPE;
	}

	/**
	 * This overrides the getmetadataString() method because the value here
	 * does not have an ASCII representation (and we need to know the values
	 * of the independent parameter instead).
	 * @return Sstring
	 */
	public String getMetadataString() {
		return getDependentParamMetadataString();
	}

	public boolean setIndividualParamValueFromXML(Element el) {
		// just return true, param values are actually stored as independent params and will be set
		return true;
	}

	public ParameterEditor<ParameterList> getEditor() {
		if (paramEdit == null)
			paramEdit = new ParameterListParameterEditor(this);
		return paramEdit;
	}

	@Override
	public boolean isEditorBuilt() {
		return paramEdit != null;
	}
}

