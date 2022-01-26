package org.opensha.commons.param;

import java.io.Serializable;
import java.util.ListIterator;

import org.dom4j.Element;
import org.opensha.commons.data.Named;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;

/**
 * <b>Title:</b> ParameterAPI Interface
 * <p>
 * 
 * <b>Description:</b> All parameter classes must implement this API to "plug"
 * into the framework. A parameter basically contains some type of java object,
 * such as a String, Double, etc. This parameter framework extends on the basic
 * Java DataTypes by adding constraint objects, a name, information string,
 * units string, parameter change fail and succede listeners, etc. These
 * parameters are "Supercharged" data types with alot of functionality added.
 * This API defines the basic added functionality and getter and setter
 * functions for adding these extra features.
 * <p>
 * 
 * The parameter value can be any type of object as defined by subclasses. One
 * reason for having this framework is to enable new types of parameter in the
 * future to be defined and added to a Site, ProbEqkRupture, or
 * PropagationEffect object without having to rewrite the Java code.
 * <p>
 * 
 * By defining the parameter value here as a generic object, one is not
 * restricted to adding scalar quantities. For example, one could create a
 * subclass of parameter where the value is a moment tensor (which could then be
 * added to a ProbEqkRupture object). As another example, one could define a
 * subclass of parameter where the value is a shear-wave velocity profile (which
 * could be added to a Site object).
 * <p>
 * 
 * Representing such non-scalar quantities as Parameters might seem confusing
 * semantically (e.g., perhaps Attribute would be better). However, the term
 * Parameter is consistent with the notion that an IntensityMeasureRealtionship
 * will used this information as an independent variable when computing
 * earthquake motion.
 * <p>
 * 
 * <b>Revision History</b> <br>
 * 1/1/2002 SWR
 * <ul>
 * <LI>Removed setName(), setUnits(), setConstraints. These can only be set in
 * Constructors now. Only the value can be changed after creation.
 * <LI>Added compareTo() and equals(). This will test if another parameter is
 * equal to this one based on value, not if they point to the same object in the
 * Java Virtual Machine as the default equals() does. CompareTo() will become
 * useful for sorting a list of parameters.
 * <LI>
 * </ul>
 * <p>
 * 
 * Parameter classes must provide concrete implementations  
 * compareTo
 * 
 * The abstract Parameter overrides  equals(Object) that shouod suffice for
 * most subclasses.
 * 
 * Comparable implementations must accomodate the possibility that the runtime
 * type of a Parameter supplied to compareTo may throw a ClassCastException
 * (e.g. in the case of sorting a paramterized <code>List&lt;T&gt;</code>). The
 * recommended protocol for comparisons is to compare the name of a parameter
 * and then the value, assuming the runtime types match. Such a comparison is
 * not necessarily <i>consistent with equals</i> in that different classes with
 * the same runtime type may be considered equal.
 * 
 * @author Steven W. Rock
 * @created February 21, 2002
 * @version 1.0
 */

public interface Parameter<E> extends
		Named,
		Comparable<Parameter<?>>,
		XMLSaveable,
		Serializable {

    /** 
     * Sets this <code>Parameter</code>'s name.
     * 
     * TODO this is bad form; a parameter's name should be immutable and required
     * and therefore always set via a constructor. The abstract implementation
     * should not have a no-arg constructor to prevent this 
     * 
     * @param name to set
     */
    public void setName(String name);

    /**
     * Every parameter constraint has a name, this
     * function gets that name. Defaults to the name
     * of the parameter but in some cases may be different.
     * TODO this is wierd and unecessary; why do constraints have names?
     */
    public String getConstraintName();

    /**
     * Returns the constraint of this parameter. Each
     * subclass may store any type of constraint it likes.
     * @return the <code>Parameter</code>'s constraint
     */
    public ParameterConstraint getConstraint();

	/**
	 * Sets the constraints of this parameter.
	 * @param constraint to set
	 */
	public void setConstraint(ParameterConstraint constraint);

    /** 
     * Returns the units of this parameter as a <code>String</code>.
     * @return the units
     */
    public String getUnits();

	/**
	 * Sets the units of measurement for this parameter.
	 * TODO like name, units should be immutable and set on construction
	 * @param units to set
	 */
	public void setUnits(String units);

    /** 
     * Returns a description of this Parameter, typically used for tooltips.
     * @return a brief description of this <code>Parameter</code>
     */
    public String getInfo();

    /** 
     * Sets the info attribute of the Parameter object.
	 * TODO like name, units should be immutable and set on construction
     * @param info to set
     */
    public void setInfo( String info );

    /** 
     * Returns the value of this <code>Parameter</code>.
     * @return the <code>Parameter</code> value
     */
    public E getValue();
    
	/**
	 * Set the default value of this <code>Parameter</code>.
	 * TODO like name, the default value should be immutable and always set
	 * via a constructor. Moreover, any 'new' parameter should automatically
	 * be initialized to the default; requiring every instance of every
	 * parameter to have to (possibly) call setDefaultValue() and then call
	 * setValueAsDefault() to actually get it set is sloppy
	 * @param value to set as default
	 */
	public void setDefaultValue(E value);

    /**
     * This sets the Parameter value to the default value.
     * TODO this is wierd; the signature suggests one is going to change the
     * current param value to be the default value; this would be better named
     * reset()
     */
    public void setValueAsDefault();
    
    /** 
     * Returns the default value of this <code>Parameter</code>.
     * @return the default value
     */
    public E getDefaultValue();


    /**
     *  Set's the parameter's value.
     *
     * @param  value                 The new value for this Parameter
     * @throws  ParameterException   Thrown if the object type of value to set
     *      is not the correct type.
     * @throws  ConstraintException  Thrown if the object value is not allowed
     */
    public void setValue( E value ) throws ConstraintException, ParameterException;
    
	/**
	 * Sets the value of this parameter from the supplied XML metadata
	 * <code>Element</code>.
	 * @param e metadata to use
	 * @return <code>true</code> if value was successfully updated from supplied
	 *         metadata; <code>false</code> otherwise
	 */
    public boolean setValueFromXMLMetadata(Element e);
    
    /**
     * Saves this parameter into the supplied XML metadata <code>Element</code> using the specified name
     * instead of the default.
     * @param root
     * @param elementName
     * @return
     */
    public Element toXMLMetadata(Element root, String name);


     /** 
      * Needs to be called by subclasses when field change fails due to constraint problems.
      * TODO this is wierd
      */
     public void unableToSetValue( Object value ) throws ConstraintException;



    /**
     *  Adds a feature to the ParameterChangeFailListener attribute
     *  
     *  TODO this is wierd; either a parameter changes or it doesn't, but creating
     *  a separate class to inform of failures is just plain nutty
     *
     * @param  listener  The feature to be added to the
     *      ParameterChangeFailListener attribute
     */
    public void addParameterChangeFailListener( org.opensha.commons.param.event.ParameterChangeFailListener listener );


    /**
     *  Description of the Method
     *
     * @param  listener  Description of the Parameter
     */
    public void removeParameterChangeFailListener( org.opensha.commons.param.event.ParameterChangeFailListener listener );

    /**
     *  Description of the Method
     *
     * @param  event  Description of the Parameter
     */
    public void firePropertyChangeFailed( org.opensha.commons.param.event.ParameterChangeFailEvent event ) ;


    /**
     *  Adds a feature to the ParameterChangeListener attribute
     *
     * @param  listener  The feature to be added to the ParameterChangeListener
     *      attribute
     */
    public void addParameterChangeListener( org.opensha.commons.param.event.ParameterChangeListener listener );
    /**
     *  Description of the Method
     *
     * @param  listener  Description of the Parameter
     */
    public void removeParameterChangeListener( org.opensha.commons.param.event.ParameterChangeListener listener );

    /**
     *  Description of the Method
     *
     * @param  event  Description of the Parameter
     */
    public void firePropertyChange( ParameterChangeEvent event ) ;


    /**
     *  Returns the data type of the value object. Used to determine which type
     *  of Editor to use in a GUI.
     *  TODO this is wierd; each call should be using a .class reference; this
     *  islittle used and needs rethinking
     */
    public String getType();


    /**
     *  Compares the values to see if they are the same. Returns -1 if obj is
     *  less than this object, 0 if they are equal in value, or +1 if the object
     *  is greater than this.
     *
     * @param  parameter            the parameter to compare this object to.
     * @return                      -1 if this value < obj value, 0 if equal, +1
     *      if this value > obj value
     * @throws  ClassCastException  Thrown if the object type of the parameter
     *      argument are not the same.
     */
    //public int compareTo( Object parameter ) throws ClassCastException;


	/**
	 * Compares passed in parameter value to this one to see if equal.
	 * 
	 * TODO shouldn't be (re)declared in interface; should be robust in abstract
	 * implementation
	 * @param parameter the parameter to compare this object to.
	 * @return True if the values are identical
	 * @throws ClassCastException Thrown if the object type of the parameter
	 *         argument are not the same.
	 * 
	 */
    public boolean equals( Object parameter ) throws ClassCastException;


	/**
	 * Returns whether the supplied value is allowed. Method generally checks
	 * any constraints, if such exist; otherwise returns <code>true</code>.
	 * @param value to check
	 * @return <code>true</code> if the supplied value is permitted;
	 *         <code>false</code> otherwise
	 */
	public boolean isAllowed(E value);

	/**
	 * Returns whether the value can be edited or altered after initialization.
	 * @return <code>true</code> if this <code>Parameter</code> is editable;
	 *         <code>false</code> otherwise
	 */
	public boolean isEditable();

    /**
     * 
     *  Disables editing units, info, constraints, etc. Basically all set()s disabled
     *  except for setValue(). Once set non-editable, it cannot be set back.
     *  This is a one-time operation.
     */
    public void setNonEditable();


    /** 
     * Returns a deep copy so you can't edit or damage the origial. */
    public Object clone();

    /**
     * Checks if null values are permitted via the constraint. If true then
     * nulls are allowed.
     */
    public boolean isNullAllowed();

    /**
     *
     * @return the matadata string for parameter.
     * This function returns the metadata which can be used to reset the values
     * of the parameters created.
     * *NOTE : Look at the function getMetadataXML() which return the values of
     * these parameters in the XML format and can used recreate the parameters
     * from scratch.
     */
    public String getMetadataString() ;
    
	/**
	 * Returns the <code>Editor</code> for this <code>Parameter</code>. It is
	 * recommended that editors be lazily instantiated.
	 * @return the <code>Editor</code>
	 */
	public ParameterEditor getEditor();
    
    public static final String XML_INDEPENDENT_PARAMS_NAME = "IndependentParameters";
	
	/*
	 * These methods are from the original ParameterAPI
	 */

    /**
     * Returns an list of all indepenedent parameters this parameter
     * depends upon. Returns the parametes in the order they were added.
     */
    public ParameterList getIndependentParameterList();

    /**
     * Locates and returns an independent parameter from the list if it
     * exists. Throws a parameter excpetion if the requested parameter
     * is not one of the independent parameters.
     *
     * @param name  Parameter name to lookup.
     * @return      The found independent Parameter.
     * @throws ParameterException   Thrown if not one of the independent parameters.
     */
    public Parameter getIndependentParameter(String name)throws ParameterException;

    /** Set's a complete list of independent parameters this parameter requires */
    public void setIndependentParameters(ParameterList list);

    /** Adds the parameter if it doesn't exist, else throws exception */
    public void addIndependentParameter(Parameter parameter) throws ParameterException;

    /** Returns true if this parameter is one of the independent ones */
    public boolean containsIndependentParameter(String name);

    /** Removes the parameter if it exist, else throws exception */
    public void removeIndependentParameter(String name) throws ParameterException;

    /** Returns all the names of the independent parameters concatenated */
    public String getIndependentParametersKey();

    /** see implementation in the DependentParameter class for information */
    public String getDependentParamMetadataString();
    
    /** see implementation in the DependentParameter class for information */
    public int getNumIndependentParameters();
}
