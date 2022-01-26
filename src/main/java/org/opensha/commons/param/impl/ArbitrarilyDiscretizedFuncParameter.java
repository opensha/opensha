package org.opensha.commons.param.impl;

import org.dom4j.Element;
import org.opensha.commons.data.function.AbstractDiscretizedFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.EditableException;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.ArbitrarilyDiscretizedFuncParameterEditor;

/**
 * <p>Title: ArbitrarilyDiscretizedFuncParameter.java </p>
 * <p>Description: Makes a textfield to enter X and Y values.
 *  This class  has 2 methods for setting the units :
 * 1. setUnits() : This method sets the units for Y values
 * 2. setXUnits(): This method sets the units for X values.
 * Similarly, we have 2 different methods for getting the units.
 * </p>
 * @author : Nitin Gupta and Vipin Gupta
 * @created : April 01,2004
 * @version 1.0
 */

public class ArbitrarilyDiscretizedFuncParameter extends AbstractParameter<ArbitrarilyDiscretizedFunc>
implements java.io.Serializable{


	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/** Class name for debugging. */
	protected final static String C = "ArbitrarilyDiscretizedFuncParameter";
	/** If true print out debug statements. */
	protected final static boolean D = false;

	protected final static String PARAM_TYPE ="ArbitrarilyDiscretizedFuncParameter";
	private String xUnits="";

	private transient ParameterEditor<ArbitrarilyDiscretizedFunc>  paramEdit = null;

	/**
	 * No constraints specified, all values allowed. Sets the name and value.
	 *
	 * @param  name   Name of the parameter
	 * @param  discretizedFunc  DiscretizedFunc  object
	 */
	public ArbitrarilyDiscretizedFuncParameter(String name, ArbitrarilyDiscretizedFunc discretizedFunc){
		super(name,null,null,discretizedFunc);

	}



	/**
	 * Sets the units for X Values. To set the units for Y values, use
	 * the setUnits() method
	 *
	 **/
	public void setXUnits(String units) throws EditableException {
		checkEditable(C + ": setUnits(): ");
		this.xUnits = units;
	}


	/**
	 * Returns the units of X values for  this parameter. To get the units for
	 * Y values, use getUnits() method
	 * represented as a String.
	 * */
	public String getXUnits() {
		return xUnits;
	}





	/**
	 *  Compares the values to if this is less than, equal to, or greater than
	 *  the comparing objects.
	 *
	 * @param  obj                     The object to compare this to
	 * @return                         -1 if this value < obj value, 0 if equal,
	 *      +1 if this value > obj value
	 * @exception  ClassCastException  Is thrown if the comparing object is not
	 *      a ParameterListParameter.
	 */
//	@Override
//	public int compareTo(Parameter<ArbitrarilyDiscretizedFunc> param) {
		// TODO I don't think this even does a full comparison; should it
		// be value by value
		
//		String S = C + ":compareTo(): ";
//
//		if ( !( obj instanceof ArbitrarilyDiscretizedFunc ) ) {
//			throw new ClassCastException( S + "Object not a DiscretizedFuncAPI, unable to compare" );
//		}

		//ArbitrarilyDiscretizedFuncParameter param = ( ArbitrarilyDiscretizedFuncParameter ) obj;

//		if (value == null && param.getValue() == null) return 0;
//		//int result = 0;
//
//		ArbitrarilyDiscretizedFunc n1 = ( ArbitrarilyDiscretizedFunc) this.getValue();
//		ArbitrarilyDiscretizedFunc n2 = ( ArbitrarilyDiscretizedFunc ) param.getValue();
//
//		if(n1.equals(n2)) return 0;
//		if (getValue().equals(param.getValue())) return 0;
//		return -1;
//	}

	/*  This function just checks that we only allow an object of ArbitrarilyDiscretizedFunc.
	 *
	 * @param  obj  Object to check if allowed via constraints
	 * @return      True if the value is allowed
	 */
	public boolean isAllowed(ArbitrarilyDiscretizedFunc obj) {
		if(obj == null && this.isNullAllowed()) return true;
		if(obj instanceof ArbitrarilyDiscretizedFunc) return true;
		else return false;
	}


	/**
	 * Compares value to see if equal.
	 *
	 * @param  obj                     The object to compare this to
	 * @return                         True if the values are identical
	 * @exception  ClassCastException  Is thrown if the comparing object is not
	 *      a LocationListParameter.
	 */
	public boolean equals(Object obj) {
		String S = C + ":equals(): ";

		if (! (obj instanceof ArbitrarilyDiscretizedFuncParameter)) {
			throw new ClassCastException(S +
					"Object not a DiscretizedFuncAPI, unable to compare");
		}
		return ((ArbitrarilyDiscretizedFunc)value)
				.equals(((ArbitrarilyDiscretizedFuncParameter)obj).value);
	}

	/**
	 *  Returns a copy so you can't edit or damage the origial.
	 *
	 * @return    Exact copy of this object's state
	 */
	public Object clone(){

		ArbitrarilyDiscretizedFuncParameter param = null;
		param = new ArbitrarilyDiscretizedFuncParameter(
				name,(ArbitrarilyDiscretizedFunc)((ArbitrarilyDiscretizedFunc)value)
				.deepClone());
		param.editable = true;
		param.info = info;
		return param;
	}


	/**
	 *
	 * @return the ArbitrarilyDiscretizedFunc contained in this parameter
	 */
	public ArbitrarilyDiscretizedFunc getParameter(){
		return (ArbitrarilyDiscretizedFunc)getValue();
	}

	/**
	 * Returns the name of the parameter class
	 */
	public String getType() {
		String type = PARAM_TYPE;
		return type;
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
		try {
			Element valEl = el.element(XML_COMPLEX_VAL_EL_NAME);
			Element funcEl = valEl.element(ArbitrarilyDiscretizedFunc.XML_METADATA_NAME);
			AbstractDiscretizedFunc func = ArbitrarilyDiscretizedFunc.fromXMLMetadata(funcEl);
			if (func instanceof ArbitrarilyDiscretizedFunc)
				this.setValue((ArbitrarilyDiscretizedFunc)func);
			else
				this.setValue(new ArbitrarilyDiscretizedFunc(func));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public ParameterEditor<ArbitrarilyDiscretizedFunc> getEditor() {
		if (paramEdit == null)
			try {
				paramEdit = new ArbitrarilyDiscretizedFuncParameterEditor(this);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		return paramEdit;
	}

}


