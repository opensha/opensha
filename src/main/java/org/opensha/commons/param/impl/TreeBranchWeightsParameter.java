package org.opensha.commons.param.impl;

import java.util.ListIterator;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.TreeBranchWeightsParameterEditor;

/**
 * <p>Title: TreeBranchWeightsParameter</p>
 * <p>Description: This is a new parameter which contains the parameterList of the
 * different weights for the branches</p>
 * @author : Edward (Ned) Field, Nitin Gupta and Vipin Gupta
 * @version 1.0
 */

public class TreeBranchWeightsParameter extends ParameterListParameter {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/** Class name for debugging. */
	protected final static String C = "TreeBranchWeightsParameter";
	/** If true print out debug statements. */
	protected final static boolean D = false;
	protected final static String PARAM_TYPE = C;

	private double tolerance = .01;
	
	private transient ParameterEditor<ParameterList> paramEdit = null;

	/**
	 *  No constraints specified for this parameter. Sets the name of this
	 *  parameter.
	 *
	 * @param  name  Name of the parameter
	 */
	public TreeBranchWeightsParameter(String name) {
		super(name);
	}

	/**
	 * No constraints specified, all values allowed. Sets the name and value.
	 *
	 * @param  name   Name of the parameter
	 * @param  paramList  ParameterList  object
	 */
	public TreeBranchWeightsParameter(String name, ParameterList paramList){
		super(name,paramList);
	}


	/**
	 * sets the tolerance for the sums of the weights
	 * @param tolerance
	 */
	public void setTolerence(double tolerance){
		this.tolerance = tolerance;
	}

	/**
	 * gets the tolerence for the sum of branch weights
	 * @return
	 */
	public double getTolerance(){
		return this.tolerance;
	}

	/**
	 * Set's the parameter's value. It checks that all the weights Parameter in this parameterList
	 * should be DoubleParameter.
	 *
	 * @param  value                 The new value for this Parameter
	 * @throws  ParameterException   Thrown if the object is currenlty not
	 *      editable
	 * @throws  ConstraintException  Thrown if the object value is not allowed
	 */
	public void setValue( ParameterList value ) throws ParameterException {

		ListIterator it  = value.getParametersIterator();
		while(it.hasNext()){
			Parameter param = (Parameter)it.next();
			if(!(param instanceof DoubleParameter))
				throw new RuntimeException(C+" Only DoubleParameter allowed in this Parameter");
		}
		setValue(value );
	}

	/**
	 *
	 * @return true if the Branch Weight Values sum to One, inside the parameterList
	 * lie within the range of "1".
	 * else return false.
	 */
	public boolean doWeightsSumToOne(ParameterList paramList){
		ListIterator it =paramList.getParametersIterator();
		double paramsSum=0;
		while(it.hasNext()){
			paramsSum += ((Double)((Parameter)it.next()).getValue()).doubleValue();
		}
		return isInTolerence(paramsSum);
	}

	/**
	 * check if this parameter values  lies in tolerence
	 * @param num - sum of the parameter value
	 * @return
	 */
	private boolean isInTolerence(double num){
		if((num <= (1+this.tolerance)) && (num >= (1-this.tolerance)))
			return true;
		return false;
	}

	/**
	 * Returns the name of the parameter class
	 */
	public String getType() {
		String type = this.PARAM_TYPE;
		return type;
	}
	
	@Override
	public ParameterEditor<ParameterList> getEditor() {
		if (paramEdit == null)
			paramEdit = new TreeBranchWeightsParameterEditor(this);
		return paramEdit;
	}
}


