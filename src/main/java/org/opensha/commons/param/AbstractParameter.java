package org.opensha.commons.param;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.EditableException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeFailListener;
import org.opensha.commons.param.event.ParameterChangeListener;

/**
 * Parial implementation of the <code>Paameter</code>. The common fields with
 * get and setters are here, as well as a default constructor that sets all
 * these fields, and the setValue field that always checks if the value is
 * allowed before setting. The fields with gettesr and setters are:
 * 
 * <ul>
 * <li>name
 * <li>units
 * <li>constraint
 * <li>editable
 * <li>value
 * </ul>
 * 
 * These fields are common to all parameters.
 * <p>
 * 
 * @author Steve W. Rock
 * @author Kevin Milner
 * @author Peter Powers
 * @version $Id: AbstractParameter.java 11133 2015-10-26 23:05:09Z kmilner $
 */
public abstract class AbstractParameter<E> implements Parameter<E> {

	private static final long serialVersionUID = 1L;
	/**
	 * Class name used for debug statements and building the parameter type for
	 * getType().
	 */
	protected final static String C = "Parameter";
	public final static String XML_GROUP_METADATA_NAME = "Parameters";
	public final static String XML_METADATA_NAME = "Parameter";
	public final static String XML_COMPLEX_VAL_EL_NAME = "ComplexValue";
	public final static String XML_NULL_VALUE = "(null)";
	/** If true print out debug statements. */
	protected final static boolean D = false;

	/** Name of the parameter. */
	protected String name = "";

	/**
	 * Information about this parameter. This is usually used to describe what
	 * this object represents. May be used in gui tooltips.
	 */
	protected String info = "";

	/** The units of this parameter represented as a String */
	protected String units = "";

	/** The constraint for this Parameter. This is consulted when setting values */
	protected ParameterConstraint constraint = null;

	/**
	 * This value indicates if fields and constraint in this parameter are
	 * editable after it is first initialized.
	 */
	protected boolean editable = true;

	/**
	 * The value object of this Parameter, subclasses will define the object
	 * type.
	 */
	protected E value;

	/**
	 * The default value object of this Parameter, subclasses will define the
	 * object type.
	 */
	protected E defaultValue = null;

	/**
	 * ArrayList of all the objects who want to listen on change of this
	 * paramter
	 */
	private transient ArrayList<ParameterChangeListener> changeListeners;

	/**
	 * ArrayList of all the objects who want to listen if the value for this
	 * paramter is not valid
	 */
	private transient ArrayList<ParameterChangeFailListener> failListeners;

	/** Empty no-arg constructor. Does nothing but initialize object, should only be used for cloning */
	protected AbstractParameter() {}

	/**
	 * If the editable boolean is set to true, the parameter value can be
	 * edited, else an EditableException is thrown.
	 */
	protected void checkEditable(String S) throws EditableException {
		if (!this.editable)
			throw new EditableException(S +
				"This parameter is currently not editable");
	}

	/**
	 * This is the main constructor. All subclass constructors call this one.
	 * Constraints must be set first, because the value may not be an allowed
	 * one. Null values are always allowed in the constructor.
	 * 
	 * @param name Name of this parameter
	 * @param constraint Constraints for this Parameter. May be set to null
	 * @param units The units for this parameter
	 * @param value The value object of this parameter.
	 * @throws ConstraintException This is thrown if the passes in parameter is
	 *         not allowed.
	 */
	public AbstractParameter(String name, ParameterConstraint constraint,
		String units, E value) throws ConstraintException {

		String S = C + ": Constructor(): ";
		if (D) System.out.println(S + "Starting");

		if (value != null && constraint != null) {
			if (!constraint.isAllowed(value)) {
				System.out.println(S + "Value not allowed");
				throw new ConstraintException(S + "Value not allowed: "+value);
			}
		}

		this.constraint = constraint;
		this.name = name;
		this.value = value;
		this.units = units;

		// if( (constraint != null) && (constraint.getName() == null) )
		// constraint.setName( name );

		if (D) System.out.println(S + "Ending");

	}

	/**
	 * Uses the constraint object to determine if the new value being set is
	 * allowed. If no Constraints are present all values are allowed. This
	 * function is now available to all subclasses, since any type of constraint
	 * object follows the same api.
	 * 
	 * @param obj Object to check if allowed via constraints
	 * @return True if the value is allowed
	 */
	public boolean isAllowed(E obj) {
		// if it's null, and null isn't allowed, return false
		if (obj == null && !isNullAllowed()) return false;
		// if there's a constraint, use that
		if (constraint != null) return constraint.isAllowed(obj);
		// otherwise just return true
		return true;
	}

	/**
	 * Set's the parameter's value.
	 * 
	 * @param value The new value for this Parameter.
	 * @throws ParameterException Thrown if the object is currenlty not
	 *         editable.
	 * @throws ConstraintException Thrown if the object value is not allowed.
	 */
	public void setValue(E value) throws ConstraintException,
			ParameterException {
		String S = getName() + ": setValue(): ";

		if (!isAllowed(value)) {
			throw new ConstraintException(S + "Value is not allowed: " + value);
		}

		// do not fire the event if new value is same as current value
		if (this.value != null && this.value.equals(value)) return;

		org.opensha.commons.param.event.ParameterChangeEvent event = new org.opensha.commons.param.event.ParameterChangeEvent(
			this, getName(), getValue(), value);

		this.value = value;

		firePropertyChange(event);
	}

	/**
	 * Set's the default value.
	 * 
	 * @param defaultValue The default value for this Parameter.
	 * @throws ConstraintException Thrown if the object value is not allowed.
	 */
	public void setDefaultValue(E defaultValue) throws ConstraintException {
		checkEditable(C + ": setDefaultValue(): ");

		if (!isAllowed(defaultValue)) {
			throw new ConstraintException(getName() +
				": setDefaultValue(): Value is not allowed: " +
				defaultValue.toString());
		}

		this.defaultValue = defaultValue;
	}

	/**
	 * This sets the value as the default setting
	 * @param value
	 */
	public void setValueAsDefault() throws ConstraintException,
			ParameterException {
		setValue(defaultValue);
	}

	/**
	 * Returns the parameter's default value. Each subclass defines what type of
	 * object it returns.
	 */
	public E getDefaultValue() {
		return defaultValue;
	}

	/**
	 * Needs to be called by subclasses when field change fails due to
	 * constraint problems.
	 */
	public void unableToSetValue(Object value) throws ConstraintException {

		org.opensha.commons.param.event.ParameterChangeFailEvent event = new org.opensha.commons.param.event.ParameterChangeFailEvent(
			this, getName(), getValue(), value);

		firePropertyChangeFailed(event);

	}

	/**
	 * Adds a feature to the ParameterChangeFailListener attribute of the
	 * ParameterEditor object
	 * 
	 * @param listener The feature to be added to the
	 *        ParameterChangeFailListener attribute
	 */
	public synchronized void addParameterChangeFailListener(
			ParameterChangeFailListener listener) {
		if (failListeners == null) failListeners = new ArrayList<ParameterChangeFailListener>();
		if (!failListeners.contains(listener)) failListeners.add(listener);
	}

	/**
	 * Every parameter constraint has a name, this function gets that name.
	 * Defaults to the name of the parameter but in a few cases may be
	 * different.
	 */
	public String getConstraintName() {
		if (constraint != null) {
			String name = constraint.getName();
			if (name == null) return "";
			return name;
		}
		return "";
	}

	/**
	 * Description of the Method
	 * 
	 * @param listener Description of the Parameter
	 */
	public synchronized void removeParameterChangeFailListener(
			ParameterChangeFailListener listener) {
		if (failListeners != null && failListeners.contains(listener))
			failListeners.remove(listener);
	}

	/**
	 * Description of the Method
	 * 
	 * @param event Description of the Parameter
	 */
	public void firePropertyChangeFailed(
			org.opensha.commons.param.event.ParameterChangeFailEvent event) {

		String S = C + ": firePropertyChange(): ";
		if (D)
			System.out.println(S +
				"Firing failed change event for parameter = " +
				event.getParameterName());
		if (D) System.out.println(S + "Old Value = " + event.getOldValue());
		if (D) System.out.println(S + "Bad Value = " + event.getBadValue());
		if (D)
			System.out.println(S + "Model Value = " +
				event.getSource().toString());

		ParameterChangeFailListener[] listeners;
		synchronized (this) {
			if (failListeners == null) return;
			listeners = new ParameterChangeFailListener[failListeners.size()];
			failListeners.toArray(listeners);
		}

		for (ParameterChangeFailListener listener : failListeners) {
			listener.parameterChangeFailed(event);
		}
	}

	/**
	 * Adds a feature to the ParameterChangeListener attribute of the
	 * ParameterEditor object
	 * 
	 * @param listener The feature to be added to the ParameterChangeListener
	 *        attribute
	 * 
	 */

	public synchronized void addParameterChangeListener(
			ParameterChangeListener listener) {
		if (changeListeners == null) changeListeners = new ArrayList<ParameterChangeListener>();
		if (!changeListeners.contains(listener)) changeListeners.add(listener);
	}

	/**
	 * Description of the Method
	 * 
	 * @param listener Description of the Parameter
	 */
	public synchronized void removeParameterChangeListener(
			ParameterChangeListener listener) {
		if (changeListeners != null && changeListeners.contains(listener))
			changeListeners.remove(listener);
	}

	/**
	 * 
	 * Description of the Method
	 * 
	 * @param event Description of the Parameter
	 * 
	 *        Every parameter constraint has a name, this function gets that
	 *        name. Defaults to the name of the parameter but in a few cases may
	 *        be different.
	 */
	public void firePropertyChange(ParameterChangeEvent event) {

		String S = C + ": firePropertyChange(): ";
		if (D)
			System.out.println(S + "Firing change event for parameter = " +
				event.getParameterName());
		if (D) System.out.println(S + "Old Value = " + event.getOldValue());
		if (D) System.out.println(S + "New Value = " + event.getNewValue());
		if (D)
			System.out.println(S + "Model Value = " +
				event.getSource().toString());

		ParameterChangeListener[] listeners;
		synchronized (this) {
			if (changeListeners == null) return;
			listeners = new ParameterChangeListener[changeListeners.size()];
			changeListeners.toArray(listeners);
		}

		for (ParameterChangeListener listener : listeners) {
			listener.parameterChange(event);
		}
	}

	/**
	 * Proxy function call to the constraint to see if null values are permitted
	 */
	public boolean isNullAllowed() {
		if (constraint != null) {
			return constraint.isNullAllowed();
		} else
			return true;
	}

	/**
	 * Sets the info string of the Parameter object if editable. This is usually
	 * used to describe what this object represents. May be used in gui
	 * tooltips.
	 */
	public void setInfo(String info) throws EditableException {

		checkEditable(C + ": setInfo(): ");
		this.info = info;
	}

	/** Sets the units string of this parameter. Can be used in tooltips, etc. */
	public void setUnits(String units) throws EditableException {
		checkEditable(C + ": setUnits(): ");
		this.units = units;
	}

	/**
	 * Returns the parameter's value. Each subclass defines what type of object
	 * it returns.
	 */
	public E getValue() {
		return value;
	}

	/** Returns the units of this parameter, represented as a String. */
	public String getUnits() {
		return units;
	}

	/** Gets the constraints of this parameter. */
	public ParameterConstraint getConstraint() {
		return constraint;
	}

	/**
	 * Sets the constraints of this parameter. Each subclass may implement any
	 * type of constraint it likes. An EditableException is thrown if this
	 * parameter is currently uneditable.
	 * 
	 * @return The constraint value
	 */
	public void setConstraint(ParameterConstraint constraint)
			throws EditableException {
		checkEditable(C + ": setConstraint(): ");
		// setting the new constraint for the parameter
		this.constraint = constraint;

		// getting the existing value for the Parameter
		Object value = getValue();

		/**
		 * Check to see if the existing value of the parameter is within the new
		 * constraint of the parameter, if so then leave the value of the
		 * parameter as it is currently else if the value is outside the
		 * constraint then give the parameter a temporaray null value, which can
		 * be changed later by the user.
		 */
		if (!constraint.isAllowed(value)) {

			/*
			 * allowing the constraint to have null values.This has to be done
			 * becuase if the previous value for the parameter is not within the
			 * constraints then it will throw the exception:
			 * "Value not allowed". so we have have allow "null" in the
			 * parameters.
			 */
			constraint.setNullAllowed(true);

			// now set the current param value to be null.
			/*
			 * null is just a new temp value of the parameter, which can be
			 * changed by setting a value in the parameter that is compatible
			 * with the parameter constraints.
			 */
			this.setValue(null);
			constraint.setNullAllowed(false);
		}
	}

	/** Returns a description of this Parameter, typically used for tooltips. */
	public String getInfo() {
		return info;
	}

	/**
	 * Disables editing units, info, constraints, etc. Basically all set()s
	 * disabled except for setValue(). Once set non-editable, it cannot be set
	 * back. This is a one-time operation.
	 */
	public void setNonEditable() {
		editable = false;
	}

	/** Every parameter has a name, this function returns that name. */
	public String getName() {
		return name;
	}

	/** Every parameter has a name, this function sets that name, if editable. */
	public void setName(String name) {
		checkEditable(C + ": setName(): ");
		this.name = name;
	}

	/**
	 * Returns the short class name of this object. Used by the editor framework
	 * to dynamically assign an editor to subclasses. If there are constraints
	 * present, typically "Constrained" is prepended to the short class name.
	 */
	public String getType() {
		return C;
	}

	/** Determines if the value can be edited, i.e. changed once initialized. */
	public boolean isEditable() {
		return editable;
	}

	/**
	 * 
	 * @return the matadata string for parameter. This function returns the
	 *         metadata which can be used to reset the values of the parameters
	 *         created. *NOTE : Look at the function getMetadataXML() which
	 *         return the values of these parameters in the XML format and can
	 *         used recreate the parameters from scratch.
	 */
	public String getMetadataString() {
		if (value != null)
			return name + " = " + value.toString();
		else
			return name + " = " + "null";
	}

	/** Returns a copy so you can't edit or damage the origial. */
	public abstract Object clone();

	/**
	 * Provides basic equivalency test.
	 * @return <code>true</code> if this <code>Parameter</code> is the supplied
	 *         object, or if this <code>Parameter</code> is of the same class,
	 *         has the same name, and has the same value as the supplied object
	 *         
	 *         TODO write test that null returns false
	 *         TODO write tests for null param values
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Parameter)) return false;
		Parameter<?> p = (Parameter<?>) obj;
		// check class
		if (!getClass().equals(p.getClass())) return false;
		// then name
		if (!name.equals(p.getName())) return false;
		// then value
		if (value == null) {
			return (p.getValue() == null) ? true : false;
		}
		return value.equals(p.getValue()) ? true : false;
	}

	/**
	 * Compares this <code>Parameter</code> to another by name, ignoring case.
	 */
	@Override
	public int compareTo(Parameter<?> param) {
		return getName().compareToIgnoreCase(param.getName());
	}

	public Element toXMLMetadata(Element root) {
		return toXMLMetadata(root, AbstractParameter.XML_METADATA_NAME);
	}
	
	/**
	 * Stores this parameter's value in the given XML element. Defaults to calling the toXMLMetadata
	 * method if the value implements XMLSaveable, otherwise stores the value as a string by calling
	 * toString.
	 * <br>
	 * <br>It can be overridden in the rare case that you need to specify a complex value, but the value
	 * doesn't/can't implement XMLSaveable (such as a complex built in type, like "File").
	 * special cases
	 * 
	 * @param paramEl
	 */
	protected void valueToXML(Element paramEl) {
		E val = getValue();
		if (val instanceof XMLSaveable) {
			Element valEl = paramEl.addElement(XML_COMPLEX_VAL_EL_NAME);
			((XMLSaveable) val).toXMLMetadata(valEl);
		} else {
			paramEl.addAttribute("value", val.toString());
		}
	}

	public Element toXMLMetadata(Element root, String elementName) {
		Element xml = root.addElement(elementName);
		xml.addAttribute("name", getName());
		xml.addAttribute("type", getType());
		xml.addAttribute("units", getUnits());
		
		if (getValue() == null) {
			xml.addAttribute("value", XML_NULL_VALUE);
		} else {
			valueToXML(xml);
		}
		if (this instanceof Parameter) {
			Parameter<E> param = (Parameter<E>) this;
			int num = param.getNumIndependentParameters();
			if (num > 0) {
				Element dependent = xml
					.addElement(Parameter.XML_INDEPENDENT_PARAMS_NAME);
				for (Parameter<?> depParam : param.getIndependentParameterList())
					dependent = depParam.toXMLMetadata(dependent);
			}
		}
		return root;
	}

	/**
	 * This should set the value of this individual parameter. The values of the
	 * independent parameters will be set by the final setValueFromXMLMetadata
	 * method
	 * 
	 * @param el
	 * @return
	 */
	protected abstract boolean setIndividualParamValueFromXML(Element el);

	public final boolean setValueFromXMLMetadata(Element el) {
		boolean setToNull = false;

		// System.out.println("setValueFromXMLMetadata: "+getName());

		boolean success = true;
		// first check for null
		Attribute valueAtt = el.attribute("value");
		if (valueAtt != null) {
			if (valueAtt.getStringValue().equals(XML_NULL_VALUE)) {
				try {
					this.setValue(null);
					setToNull = true;
				} catch (ConstraintException e) {
					success = false;
				} catch (ParameterException e) {
					success = false;
				}
			}
		}

		// System.out.println("setToNull? "+setToNull);

		// first set the value of this parameter
		if (!setToNull && success)
			success = this.setIndividualParamValueFromXML(el);

		boolean indepsuccess = setIndepParamsFromXML(el);
		if (success) success = indepsuccess;
		
//		if (!success)
//			System.err.println("Hmmm...why'd we fail? setToNull="+setToNull+" indepsuccess="+indepsuccess
//					+" cur val: "+getValue()+" val att="+valueAtt+" equals Null? "+valueAtt.getStringValue().equals(XML_NULL_VALUE)
//					+" null allowed? "+isNullAllowed());

		// System.out.println("success? "+success);
		return success;
	}

	// public boolean setValueFromXMLMetadata(Element el) {
	// String value = el.attribute("value").getValue();
	//
	// if (this.setValueFromString(value)) {
	// return true;
	// } else {
	// System.err.println(this.getType() + " " + this.getName() +
	// " could not be set to " + value);
	// System.err.println("It is possible that the parameter type doesn't yet support loading from XML");
	// return false;
	// }
	// }

	/*
	 * **********************************************************************
	 * The following is code from DependentParameter.java
	 * **********************************************************************
	 */

	/**
	 * ArrayList to store the independent Parameters
	 */
	protected ArrayList<Parameter<?>> independentParameters = new ArrayList<Parameter<?>>();
	// gets the Parameters Metadata
	protected String metadataString;

	/** Returns parameter from list if exist else throws exception */
	public Parameter getIndependentParameter(String name)
			throws ParameterException {

		int index = getIndexOf(name);
		if (index != -1) {
			Parameter<?> param = (Parameter<?>) independentParameters
				.get(index);
			return param;
		} else {
			String S = C + ": getParameter(): ";
			throw new ParameterException(S + "No parameter exists named " +
				name);
		}

	}

	/**
	 * 
	 * @param paramName
	 * @return the index of the parameter Name in the ArrayList
	 */
	private int getIndexOf(String paramName) {
		int size = independentParameters.size();
		for (int i = 0; i < size; ++i) {
			if (((Parameter<?>) independentParameters.get(i)).getName().equals(
				paramName)) return i;
		}
		return -1;
	}

	/**
	 * 
	 * @return the dependent parameter name and all its independent Params
	 *         Values.
	 */
	public String getIndependentParametersKey() {

		// This provides a key for coefficient lookup in hashtable
		StringBuffer key = new StringBuffer(name);

		Iterator it = independentParameters.iterator();
		while (it.hasNext()) {
			Object value = ((Parameter) it.next()).getValue();
			if (value != null) {
				key.append('/');
				key.append(value.toString());
			}
		}

		// Update the currently selected coefficient
		return key.toString();

	}

	/**
	 * Checks if the parameter exists in the list, returns true only if it finds
	 * a name match. No other comparision is done. We may want to increase the
	 * comparision in the future, i.e. returns true if has same independent
	 * parameters, etc.
	 */
	public boolean containsIndependentParameter(String paramName) {
		int index = getIndexOf(paramName);
		if (index != -1) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Clears out any existing parameters, then adds all parameters of the input
	 * parameterlist to this object
	 */
	public void setIndependentParameters(ParameterList list)
			throws ParameterException, EditableException {

		String S = C + ": setIndependentParameters(): ";
		checkEditable(S);
		independentParameters.clear();
		if (list != null) {
			ListIterator it = list.getParametersIterator();
			while (it.hasNext()) {
				Parameter param = (Parameter) it.next();
				independentParameters.add(param);
			}
		}
	}

	/**
	 * Rather than giving the name and value info, this returns the name and the
	 * name/value pairs for all the parameters in the IndependentParameterList
	 * of this parameter. This can be used for any parameters where the value
	 * does not have a sensible ascii representation (e.g., a
	 * ParameterListParameter).
	 * @return
	 */
	public String getDependentParamMetadataString() {
		if (independentParameters.size() > 0) {
			StringBuffer metadata = new StringBuffer();
			metadata.append(getName() + " [ ");
			for (Parameter<?> tempParam : getIndependentParameterList()) {
				metadata.append(tempParam.getMetadataString() + " ; ");
				/*
				 * Note that the getmetadatSring is called here rather than the
				 * getDependentParamMetadataString() method becuase the former
				 * is so far overriden in all Parameter types that have
				 * independent parameters; we may want to change this later on.
				 */
			}
			metadata.replace(metadata.length() - 2, metadata.length(), " ]");
			metadataString = metadata.toString();
		}
		return metadataString;
	}

	/**
	 * Sets the Metadata String for the parameter that has dependent parameters.
	 */
	public void setDependentParamMetadataString(
			String dependentParamMedataString) {
		metadataString = dependentParamMedataString;
	}

	/** Adds the parameter if it doesn't exist, else throws exception */
	public void addIndependentParameter(Parameter parameter)
			throws ParameterException, EditableException {

		String S = C + ": addIndependentParameter(): ";
		checkEditable(S);

		String name = parameter.getName();
		int index = getIndexOf(name);
		if (index == -1)
			independentParameters.add(parameter);
		else
			throw new ParameterException(S +
				"A Parameter already exists named " + name);

	}

	/** removes parameter if it exists, else throws exception */
	public void removeIndependentParameter(String name)
			throws ParameterException, EditableException {

		String S = C + ": removeIndependentParameter(): ";
		checkEditable(S);
		int index = getIndexOf(name);
		if (index != -1) {
			independentParameters.remove(index);
		} else
			throw new ParameterException(S + "No Parameter exist named " +
				name + ", unable to remove");

	}

	/**
	 * 
	 * @return the independent parameter list for the dependent parameter
	 */
	public ParameterList getIndependentParameterList() {
		ParameterList params = new ParameterList();
		for (Parameter<?> param : independentParameters)
			params.addParameter(param);
		return params;
	}

	/**
	 * Returns the number of independent parameters
	 */
	public int getNumIndependentParameters() {
		return independentParameters.size();
	}

	protected final boolean setIndepParamsFromXML(Element el) {
		// System.out.println("Setting indep params form XML (param="+getName()+")!");
		Element depParamsEl = el.element(Parameter.XML_INDEPENDENT_PARAMS_NAME);

		if (depParamsEl == null) return true;

		boolean success = true;

		Iterator<Element> it = depParamsEl.elementIterator();
		while (it.hasNext()) {
			Element paramEl = it.next();
			String name = paramEl.attribute("name").getValue();
			Parameter<?> param;
			try {
				param = this.getIndependentParameter(name);
				boolean newSuccess = param.setValueFromXMLMetadata(paramEl);
//				System.out.println("Setting indep param " + name +
//					" from XML...success? " + newSuccess);
				if (!newSuccess) success = false;
			} catch (ParameterException e) {
				System.err.println("Parameter '" + getName() +
					"' doesn't have an independent parameter named" + " '" +
					name + "', and cannot be set from XML");
			}
		}

		return success;
	}
}
