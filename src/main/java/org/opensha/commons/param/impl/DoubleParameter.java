package org.opensha.commons.param.impl;

import org.dom4j.Element;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.EditableException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.ConstrainedDoubleParameterEditor;
import org.opensha.commons.param.editor.impl.DoubleParameterEditor;




/**
 *  <b>Title:</b> DoubleParameter<p>
 *
 *  <b>Description:</b> Generic Data Object that contains a Double and
 *  optionally a min and max allowed values stored in a constraint object. If no
 *  constraint object is present then all values should be permitted.<p>
 *
 * @author     Sid Hellman, Steven W. Rock
 * @created    February 21, 2002
 * @version    1.0
 */

public class DoubleParameter extends AbstractParameter<Double> {

	private static final long serialVersionUID = 1L;

	/** Class name for debugging. */
	protected final static String C = "DoubleParameter";
	/** If true print out debug statements. */
	protected final static boolean D = false;

	private transient ParameterEditor<Double> paramEdit = null;

	/**
	 *  No constraints specified, all values allowed. Sets the name of this
	 *  parameter.
	 *
	 * @param  name  Name of the parameter
	 */
	public DoubleParameter( String name ) {
		super( name, null, null, null );
	}


	/**
	 *  No constraints specified, all values allowed. Sets the name and untis of
	 *  this parameter.
	 *
	 * @param  name   Name of the parameter
	 * @param  units  Units of this parameter
	 */
	public DoubleParameter( String name, String units ) {
		super( name, null, units, null );
	}


	/**
	 *  Sets the name, defines the constraints min and max values. Creates the
	 *  constraint object from these values.
	 *
	 * @param  name                     Name of the parameter
	 * @param  min                      defines min of allowed values
	 * @param  max                      defines max of allowed values
	 * @exception  ConstraintException  thrown if the value is not allowed
	 * @throws  ConstraintException     Is thrown if the value is not allowed
	 */
	public DoubleParameter( String name, double min, double max ) throws ConstraintException {
		super( name, new DoubleConstraint( min, max ), null, null );
		//constraint.setName( name );
	}


	/**
	 *  Sets the name, defines the constraints min and max values, and sets the
	 *  units. Creates the constraint object from these values.
	 *
	 * @param  name                     Name of the parameter
	 * @param  min                      defines min of allowed values
	 * @param  max                      defines max of allowed values
	 * @param  units                    Units of this parameter
	 * @exception  ConstraintException  thrown if the value is not allowed
	 * @throws  ConstraintException     Is thrown if the value is not allowed
	 */
	public DoubleParameter( String name, double min, double max, String units ) throws ConstraintException {
		super( name, new DoubleConstraint( min, max ), units, null );
		//constraint.setName( name );
	}


	/**  Sets the name, defines the constraints min and max values. Creates the
	 *  constraint object from these values.
	 *
	 * @param  name                     Name of the parameter
	 * @param  min                      defines min of allowed values
	 * @param  max                      defines max of allowed values
	 * @exception  ConstraintException  thrown if the value is not allowed
	 * @throws  ConstraintException     Is thrown if the value is not allowed
	 */
	public DoubleParameter( String name, Double min, Double max ) throws ConstraintException {
		super( name, new DoubleConstraint( min, max ), null, null );
		//constraint.setName( name );
	}


	/**
	 *  Sets the name, defines the constraints min and max values, and sets the
	 *  units. Creates the constraint object from these values.
	 *
	 * @param  name                     Name of the parameter
	 * @param  min                      defines min of allowed values
	 * @param  max                      defines max of allowed values
	 * @param  units                    Units of this parameter
	 * @exception  ConstraintException  thrown if the value is not allowed
	 * @throws  ConstraintException     Is thrown if the value is not allowed
	 */
	public DoubleParameter( String name, Double min, Double max, String units ) throws ConstraintException {
		super( name, new DoubleConstraint( min, max ), units, null );
		//constraint.setName( name );
	}


	/**
	 *  Sets the name and Constraints object.
	 *
	 * @param  name                     Name of the parameter
	 * @param  constraint               defines min and max range of allowed
	 *      values
	 * @exception  ConstraintException  thrown if the value is not allowed
	 * @throws  ConstraintException     Is thrown if the value is not allowed
	 */
	public DoubleParameter( String name, DoubleConstraint constraint ) throws ConstraintException {
		super( name, constraint, null, null );
		//if( (constraint != null) && (constraint.getName() == null) )
		//constraint.setName( name );
	}


	/**
	 *  Sets the name, constraints, and sets the units.
	 *
	 * @param  name                     Name of the parameter
	 * @param  constraint               defines min and max range of allowed
	 *      values
	 * @param  units                    Units of this parameter
	 * @exception  ConstraintException  thrown if the value is not allowed
	 * @throws  ConstraintException     Is thrown if the value is not
	 *      allowedallowed one. Null values are always allowed in the
	 *      constructors
	 */
	public DoubleParameter( String name, DoubleConstraint constraint, String units ) throws ConstraintException {
		super( name, constraint, units, null );
		//if( (constraint != null) && (constraint.getName() == null) )
		//constraint.setName( name );
	}


	/**
	 *  No constraints specified, all values allowed. Sets the name and value.
	 *
	 * @param  name   Name of the parameter
	 * @param  value  Double value of this parameter
	 */
	public DoubleParameter( String name, Double value ) {
		super(name, null, null, value);
	}


	/**
	 *  Sets the name, units and value. All values allowed because constraints.
	 *  not set.
	 *
	 * @param  name                     Name of the parameter
	 * @param  value                    Double value of this parameter
	 * @param  units                    Units of this parameter
	 * @exception  ConstraintException  thrown if the value is not allowed
	 * @throws  ConstraintException     Is thrown if the value is not allowed
	 */
	public DoubleParameter( String name, String units, Double value ) throws ConstraintException {
		super( name, null, units, value );
	}


	/**
	 *  Sets the name, and value. Also defines the min and max from which the
	 *  constraint is constructed.
	 *
	 * @param  name                     Name of the parameter
	 * @param  value                    Double value of this parameter
	 * @param  min                      defines max of allowed values
	 * @param  max                      defines min of allowed values
	 * @exception  ConstraintException  thrown if the value is not allowed
	 * @throws  ConstraintException     Is thrown if the value is not allowed
	 */
	public DoubleParameter( String name, double min, double max, Double value ) throws ConstraintException {
		super( name, new DoubleConstraint( min, max ), null, value );
		//if( constraint != null ) constraint.setName( name );
	}


	/**
	 *  Sets the name, and value. Also defines the min and max from which the
	 *  constraint is constructed.
	 *
	 * @param  name                     Name of the parameter
	 * @param  value                    Double value of this parameter
	 * @param  min                      defines max of allowed values
	 * @param  max                      defines min of allowed values
	 * @exception  ConstraintException  thrown if the value is not allowed
	 * @throws  ConstraintException     Is thrown if the value is not allowed
	 */
	public DoubleParameter( String name, Double min, Double max, Double value ) throws ConstraintException {
		super( name, new DoubleConstraint( min, max ), null, value );
		//if( constraint != null ) constraint.setName( name );
	}

	/**
	 *  Sets the name, value and constraint. The value is checked if it is
	 *  within constraints.
	 *
	 * @param  name                     Name of the parameter
	 * @param  constraint               defines min and max range of allowed
	 *      values
	 * @param  value                    Double value of this parameter
	 * @exception  ConstraintException  thrown if the value is not allowed
	 * @throws  ConstraintException     Is thrown if the value is not allowed
	 */
	public DoubleParameter( String name, DoubleConstraint constraint, Double value ) throws ConstraintException {
		super( name, constraint, null, value );
		//if( (constraint != null) && (constraint.getName() == null) )
		//constraint.setName( name );
	}


	/**
	 *  Sets all values, and the constraint is created from the min and max
	 *  values. The value is checked if it is within constraints.
	 *
	 * @param  name                     Name of the parameter
	 * @param  value                    Double value of this parameter
	 * @param  min                      defines min of allowed values
	 * @param  max                      defines max of allowed values
	 * @param  units                    Units of this parameter
	 * @exception  ConstraintException  Is thrown if the value is not allowed
	 * @throws  ConstraintException     Is thrown if the value is not allowed
	 */
	public DoubleParameter( String name, double min, double max, String units, Double value ) throws ConstraintException {
		super( name, new DoubleConstraint( min, max ), units, value );
		//if( constraint != null ) constraint.setName( name );
	}


	/**
	 *  Sets all values, and the constraint is created from the min and max
	 *  values. The value is checked if it is within constraints.
	 *
	 * @param  name                     Name of the parameter
	 * @param  value                    Double value of this parameter
	 * @param  min                      defines min of allowed values
	 * @param  max                      defines max of allowed values
	 * @param  units                    Units of this parameter
	 * @exception  ConstraintException  Is thrown if the value is not allowed
	 * @throws  ConstraintException     Is thrown if the value is not allowed
	 */
	public DoubleParameter( String name, Double min, Double max, String units, Double value ) throws ConstraintException {
		super( name, new DoubleConstraint( min, max ), units, value );
		//if( constraint != null ) constraint.setName( name );
	}


	/**
	 *  This is the main constructor. All other constructors call this one.
	 *  Constraints must be set first, because the value may not be an allowed
	 *  one. Null values are always allowed in the constructor. If the
	 *  constraints are null, all values are allowed.
	 *
	 * @param  name                     Name of the parameter
	 * @param  constraint               defines min and max range of allowed
	 *      values
	 * @param  value                    Double value of this parameter
	 * @param  units                    Units of this parameter
	 * @exception  ConstraintException  Is thrown if the value is not allowed
	 * @throws  ConstraintException     Is thrown if the value is not allowed
	 */

	public DoubleParameter( String name, DoubleConstraint constraint, String units, Double value )
	throws ConstraintException {
		super( name, constraint, units, value );
	}


	/**
	 *  Sets the constraint if it is a StringConstraint and the parameter
	 *  is currently editable.
	 */
	/**
	 * Sets the constraint if it is a StringConstraint and the parameter
	 * is currently editable.
	 *
	 * @param constraint            The new constraint.
	 * @throws ParameterException   Thrown if constraint is not a DoubleConstraint.
	 * @throws EditableException    Thrown if the parameter is currently uneditable.
	 */
	public void setConstraint(ParameterConstraint constraint)
	throws ParameterException, EditableException
	{

		String S = C + ": setConstraint(): ";
		checkEditable(S);

		if ( !(constraint instanceof DoubleConstraint )) {
			throw new ParameterException( S +
					"This parameter only accepts DoubleConstraints, unable to set the constraint."
			);
		}
		else super.setConstraint( constraint );

	}

	/** Gets the min value of the constraint object. */
	public Double getMin() throws Exception {
		if ( constraint != null ) return ( ( DoubleConstraint ) constraint ).getMin();
		else return null;
	}


	/** Returns the maximum allowed values. */
	public Double getMax() {
		if ( constraint != null ) return ( ( DoubleConstraint ) constraint ).getMax();
		else return null;
	}


	/**
	 *  Gets the type of this parameter. Used by the Editor framework to decide
	 *  what type of editor to create for this parameter.
	 */
	public String getType() {
		String type = C;
		// Modify if constrained
		ParameterConstraint constraint = this.constraint;
		if (constraint != null) type = "Constrained" + type;
		return type;
	}


	/**
	 *  Compares the values to if this is less than, equal to, or greater than
	 *  the comparing objects.
	 *
	 * @param  obj                     The object to compare this to
	 * @return                         -1 if this value < obj value, 0 if equal,
	 *      +1 if this value > obj value
	 * @exception  ClassCastException  Is thrown if the comparing object is not
	 *      a DoubleParameter, or DoubleDiscreteParameter.
	 */
	
	/**
	 * Compares this Parameter with another based on name and then, if the two
	 * share the same runtime type, value.
	 */
//	@Override
//	public int compareTo(Parameter<Double> param) {
//		int compare = getName().compareTo(param.getName());
//		if (compare == 0) {
//			try {
//				compare = getValue().compareTo(param.getValue());
//			} catch (ClassCastException cce) {}
//		}
//		return compare;
//	}
		
		
//
//		String S = C + ":compareTo(): ";
//
//		if ( !( obj instanceof DoubleParameter ) && !( obj instanceof DoubleDiscreteParameter ) ) {
//			throw new ClassCastException( S + "Object not a DoubleParameter, or DoubleDiscreteParameter, unable to compare" );
//		}

		//   int result = 0;

//		Double n1 = ( Double ) this.getValue();
//		Double n2 = null;
//
//		if ( obj instanceof DoubleParameter ) {
//			DoubleParameter param = ( DoubleParameter ) obj;
//			n2 = ( Double ) param.getValue();
//		} else if ( obj instanceof DoubleDiscreteParameter ) {
//			DoubleDiscreteParameter param = ( DoubleDiscreteParameter ) obj;
//			n2 = ( Double ) param.getValue();
//		}
//		if (!(param.getValue() instanceof Double)) {
//			return getName().compareTo(param.getName());
//		}
		//if (value != null && value instanceof Double)
		
//		try {
//		System.out.println("v1: " + value);
//		System.out.println("v2: " + param.getValue());
//		return value.compareTo(param.getValue());
//		} catch (ClassCastException cce) {
//			System.out.println("you suck");
//			return -4;
//		}
//	}

//	public static void main(String[] args) {
//		ArrayList<ParameterAPI<?>> list = new ArrayList<ParameterAPI<?>>();
//		list.add(new StringParameter("zzz"));
//		list.add(new DoubleParameter("Test double"));
//		LocationParameter lp = new LocationParameter("Loc param");
//		lp.setValue(new Location(40,-120));
//		
//		list.add(lp);
//		System.out.println("original:\n============");
//		for (ParameterAPI<?> p:list) {
//			System.out.println(p);
//		}
//		Collections.sort(list);
//		System.out.println("\nsorted:\n============");
//		for (ParameterAPI<?> p:list) {
//			System.out.println(p);
//		}
//	}

	/**
	 *  Set's the parameter's value.
	 *
	 * @param  value                 The new value for this Parameter
	 * @throws  ParameterException   Thrown if the object is currenlty not
	 *      editable.
	 * @throws  ConstraintException  Thrown if the object value is not allowed.
	 */
	public void setValue( double value ) throws ConstraintException, ParameterException {
		setValue( Double.valueOf( value ) );
	}

	/**
	 *  Uses the constraint object to determine if the new value being set is
	 *  allowed. If no Constraints are present all values are allowed. This
	 *  function is now available to all subclasses, since any type of
	 *  constraint object follows the same api.
	 *
	 * @param  obj  Object to check if allowed via constraints
	 * @return      True if the value is allowed
	 */
	public boolean isAllowed( Double d ) {
		return super.isAllowed(d);
	}

	/**
	 *  Compares value to see if equal.
	 *
	 * @param  obj                     The object to compare this to
	 * @return                         True if the values are identical
	 * @exception  ClassCastException  Is thrown if the comparing object is not
	 *      a DoubleParameter, or DoubleDiscreteParameter.
	 */
//	@Override
//	public boolean equals(Object obj) {
//		String S = C + ":equals(): ";
//
//		if ( !( obj instanceof DoubleParameter ) && !( obj instanceof DoubleDiscreteParameter ) ) {
//			throw new ClassCastException( S + "Object not a DoubleParameter, or DoubleDiscreteParameter, unable to compare" );
//		}
//
//		String otherName = ( ( DoubleParameter ) obj ).getName();
//		if ( ( compareTo( obj ) == 0 ) && getName().equals( otherName ) ) {
//			return true;
//		} else {
//			return false;
//		}
//		if (this == obj) return true;
//		if (!(obj instanceof DoubleParameter)) return false;
//		DoubleParameter dp = (DoubleParameter) obj;
//		return (compareTo(dp) == 0 && getName().equals(dp.getName()));
//		
//		
//	}


	/** Returns a copy so you can't edit or damage the origial. */
	public Object clone() {
		DoubleConstraint c1=null;
		if(constraint != null)
			c1 = ( DoubleConstraint ) constraint.clone();
		DoubleParameter param = null;
		if( value == null ) param = new DoubleParameter( name, c1, units);
		else param = new DoubleParameter( name, c1, units, Double.valueOf( this.value.toString() )  );
		if( param == null ) return null;
		param.editable = true;
		param.info = info;
		return param;
	}

	/**
	 * Parses the given XML element for a double value and sets it
	 */
	public boolean setIndividualParamValueFromXML(Element el) {
		try {
			Double val = Double.parseDouble(el.attributeValue("value"));
			this.setValue(val);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}


	public ParameterEditor<Double> getEditor() {
		if (paramEdit == null) {
			try {
				if (constraint == null)
					paramEdit = new DoubleParameterEditor(this);
				else
					paramEdit = new ConstrainedDoubleParameterEditor(this);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return paramEdit;
	}

	@Override
	public boolean isEditorBuilt() {
		return paramEdit != null;
	}
	
//	public static void main(String[] args) {
//		DoubleParameter dp = new DoubleParameter("tmpD", 1d);
//		//dp.setValue(null);
//		IntegerParameter ip = new IntegerParameter("tmpI", 1);
//		//DoubleDiscreteParameter ddp = new DoubleDiscreteParameter("tmpD", -1d);
//		DoubleParameter ddp = new DoubleParameter("tmpD", -1d);
////		ddp.setValue(null);
//	
//		System.out.println(ddp.equals(dp));
//		
//		
//		List<Parameter<?>> pl = new ArrayList<Parameter<?>>();
//		pl.add(dp);
//		pl.add(ip);
//		pl.add(ddp);
//		
//		Collections.sort(pl);
//		System.out.println(pl.toString());
//	}
}
