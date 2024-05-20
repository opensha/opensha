package org.opensha.commons.param.impl;

import java.util.ArrayList;
import java.util.ListIterator;

import org.dom4j.Element;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.EditableException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.exceptions.WarningException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.constraint.AbstractParameterConstraint;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.event.ParameterChangeWarningEvent;
import org.opensha.commons.param.event.ParameterChangeWarningListener;

/**
 * <b>Title:</b> WarningDoubleParameter<p>
 *
 * <b>Description:</b> Concrete implementation of the
 * WarningParameterAPI interface that stores a Double
 * for it's value object. Maintains a list of listeners
 * and passes them ParameterChangeWarningEvents when
 * the value is attemted to be set beyong the warning
 * constraints.<p>
 *
 * Typical use case is that there is one listener that also
 * acts as the editor of the value, ( such as the GUI component
 * attempting to update the value ). This listener attempts
 * to change the value outside the warning range. The
 * listener is notified of the attemp, i.e. "warned".
 * This listener then notifies the user via a DialogBox.
 * The user is then given the option to cancel or
 * ignore the warning and set the value. The listener will
 * then set the value ignoring the warning. <p>
 *
 * The whole reason for using a listener is that any
 * type of situation can be handled. This class doesn't
 * need to know anything about the listener other than it
 * adheres to the ParameterChangeWarningListener interface.
 * The listener can be any class, and can be updated to
 * any new class in the future. This class never has to be changed.
 * This means that this parameter component is not tied to
 * any specific class of editors, a guiding principle in
 * object-oriented programming. <p>
 *
 * Note: All listeners must implement the ParameterChangeFailListener
 * interface. <p>
 *
 * Note: Since this class extends from DoubleParameter it also
 * has a second absolute DoubleConstraint that can never be exceeded.
 * It's important that the programmer realizes this and ensures the
 * warning constraints are smaller than the absolute constraints when
 * using this parameter in their program. <p>
 *
 * @see ParameterChangeWarningListener
 * @see ParameterChangeWarningEvent
 * @author Steven W. Rock
 * @version 1.0
 */

public class WarningDoubleParameter extends DoubleParameter implements
WarningParameter<Double> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/** Class name for debugging. */
	protected final static String C = "WarningDoubleParameter";
	/** If true print out debug statements. */
	protected final static boolean D = false;


	/** The constraint for this Parameter. */
	protected DoubleConstraint warningConstraint = null;

	/**
	 * A list of listeners to receive warning events.
	 * Only created if needed, else kept null. This is
	 * known as "lazy instantiation".
	 */
	protected transient ArrayList<ParameterChangeWarningListener> warningListeners = null;


	/**
	 * Set to true to turn off warnings, i.e. bypass the warning
	 * constraint. Recall the absolute constraints are still active
	 * and may still block the value update.
	 */
	private boolean ignoreWarning;


	/**
	 *  No warning constraints or absolute constraints specified.
	 *  All values allowed. Also sets the name of this parameter.
	 */
	public WarningDoubleParameter( String name ) {
		super(name);
	}


	/**
	 *  No warning constraints or absolute constraints specified.
	 *  All values allowed. Also sets the name and units of this parameter.
	 */
	public WarningDoubleParameter( String name, String units ) {
		super( name, units );
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
	public WarningDoubleParameter( String name, double min, double max ) throws ConstraintException {
		super( name, min, max );
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
	public WarningDoubleParameter( String name, double min, double max, String units ) throws ConstraintException {
		super( name, min, max , units );
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
	public WarningDoubleParameter( String name, Double min, Double max ) throws ConstraintException {
		super( name, min, max );
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
	public WarningDoubleParameter( String name, Double min, Double max, String units ) throws ConstraintException {
		super( name, min, max , units );
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
	public WarningDoubleParameter( String name, DoubleConstraint constraint ) throws ConstraintException {
		super( name, constraint);
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
	public WarningDoubleParameter( String name, DoubleConstraint constraint, String units ) throws ConstraintException {
		super( name, constraint, units );
	}


	/**
	 *  No constraints specified, all values allowed. Sets the name and value.
	 *
	 * @param  name   Name of the parameter
	 * @param  value  Double value of this parameter
	 */
	public WarningDoubleParameter( String name, Double value ) {
		super( name, value );
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
	public WarningDoubleParameter( String name, String units, Double value ) throws ConstraintException {
		super( name, units, value );
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
	public WarningDoubleParameter( String name, double min, double max, Double value ) throws ConstraintException {
		super( name, min, max, value );
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
	public WarningDoubleParameter( String name, DoubleConstraint constraint, Double value ) throws ConstraintException {
		super( name, constraint, value );
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
	public WarningDoubleParameter( String name, double min, double max, String units, Double value ) throws ConstraintException {
		super( name, min, max, units, value );
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
	public WarningDoubleParameter( String name, Double min, Double max, String units, Double value ) throws ConstraintException {
		super( name, min, max, units, value );
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

	public WarningDoubleParameter( String name, DoubleConstraint constraint, String units, Double value )
			throws ConstraintException {
		super( name, constraint, units, value );
		//if( (constraint != null) && (constraint.getName() == null) )
		//constraint.setName( name );
	}



	/**
	 *  Adds a ParameterChangeFailListener to the list of listeners. This is
	 *  the interface all listeners must implement in order to fit into
	 *  this framework. This is where the listener list is created if null,
	 *  i.e. "lazy instantiation".
	 *
	 * @param  listener  The feature to be added to the
	 *      ParameterChangeFailListener attribute
	 */
	public synchronized void addParameterChangeWarningListener( ParameterChangeWarningListener listener )
			throws EditableException
	{

		String S = C + ": addParameterChangeWarningListener(): ";
		//checkEditable(S);

		if ( warningListeners == null ) warningListeners = new ArrayList<ParameterChangeWarningListener>();
		if ( !warningListeners.contains( listener ) ) {
			if(D) System.out.println(S + "Adding listener: " + listener.getClass().getName() );
			warningListeners.add( listener );

		}

	}

	/**
	 * Removes a ParameterChangeFailListener to the list of listeners. This listener
	 * will no longer receive warning events.
	 */
	public synchronized void removeParameterChangeWarningListener( ParameterChangeWarningListener listener )
			throws EditableException
	{
		String S = C + ": removeParameterChangeWarningListener(): ";
		//checkEditable(S);

		if ( warningListeners != null && warningListeners.contains( listener ) )
			warningListeners.remove( listener );
	}

	/**
	 * Replaces the warning constraints with a new constraint object. If this
	 * class is set to non-editable an EditableException is thrown.
	 */
	public void setWarningConstraint(AbstractParameterConstraint warningConstraint)
			throws ParameterException, EditableException
	{
		String S = C + ": setWarningConstraint(): ";
		checkEditable(S);

		this.warningConstraint = (DoubleConstraint)warningConstraint;

		//if( (this.warningConstraint != null) && (this.warningConstraint.getName() == null) )
		//this.warningConstraint.setName( this.getName() );
	}

	/**
	 * Replaces the warning constraints with a new constraint object. If this
	 * class is set to non-editable an EditableException is thrown.
	 */
	public AbstractParameterConstraint getWarningConstraint() throws ParameterException{
		return warningConstraint;
	}

	/**
	 * Proxy passthrough method to the DoubleConstraint to get the
	 * minimumn value below which warnings will be issued.
	 *
	 * @return                The min value
	 */
	public Double getWarningMin() {
		if ( warningConstraint != null ) return warningConstraint.getMin();
		else return null;
	}


	/**
	 * Proxy passthrough method to the DoubleConstraint to get the
	 * minimumn value below which warnings will be issued.
	 *
	 * @return                The min value
	 */
	public Double getWarningMax() {
		if ( warningConstraint != null ) return warningConstraint.getMax();
		else return null;

	}


	/**
	 * Attempts to update the value of this parameter with a new Double. All
	 * constraints are checked. If the value exceeds the warning levels all
	 * listeners are notified via fireParameterChangeWarning(). If the
	 * ignoreWarning flag is present the warning constraints are bypassed. Another
	 * condition checked is if the value is null and null values are allowed.
	 *
	 * @param value         The new value - must be a Double
	 * @throws ConstraintException      Thrown if the new value is beyond the constraint
	 *  levels or null values not allowed.
	 * @throws WarningException     Thrown if the new value is beyond the warning levels.
	 */
	public synchronized void setValue( Double value ) throws ConstraintException, WarningException {
		String S = getName() + ": setValue(): ";
		if(D) System.out.println(S + "Starting: ");

		if ( !isAllowed( value ) ) {
			String err = S + "Value is not allowed: ";
			if( value != null ) err += value.toString();
			if(D) System.out.println(err);
			throw new ConstraintException( err );
		}
		else if ( value == null ){
			if(D) System.out.println(S + "Setting allowed and recommended null value: ");
			// do not fire the event if new value is same as current value
			if (this.value == null) return;
			this.value = null;
			org.opensha.commons.param.event.ParameterChangeEvent event = new org.opensha.commons.param.event.ParameterChangeEvent(
					this, getName(), getValue(), value );
			firePropertyChange( event );
		}
		else if ( !ignoreWarning && !isRecommended( value ) ) {

			if(D) System.out.println(S + "Firing Warning Event");

			ParameterChangeWarningEvent event = new
					ParameterChangeWarningEvent( (Object)this, this, this.value, value );

			fireParameterChangeWarning( event );
			throw new WarningException( S + "Value is not recommended: " + value.toString() );
		}
		else {
			if(D) System.out.println(S + "Setting allowed and recommended value: ");
			// do not fire the event if new value is same as current value
			if (this.value != null && this.value.equals(value)) return;
			this.value = value;
			org.opensha.commons.param.event.ParameterChangeEvent event = new org.opensha.commons.param.event.ParameterChangeEvent(
					this, getName(), getValue(), value );
			firePropertyChange( event );
		}
		if(D) System.out.println(S + "Ending: ");
	}

	/**
	 * Attempts to update the value of this parameter with a new Double. All
	 * constraints are checked. If the value exceeds the warning levels all
	 * listeners are notified via fireParameterChangeWarning(). If the
	 * ignoreWarning flag is present the warning constraints are bypassed. Another
	 * condition checked is if the value is null and null values are allowed.
	 *
	 * @param value         The new value - must be a Double
	 * @throws ConstraintException      Thrown if the new value is beyond the constraint
	 *  levels or null values not allowed.
	 * @throws WarningException     Thrown if the new value is beyond the warning levels.
	 */
	public void setValueIgnoreWarning( Double value ) throws ConstraintException, ParameterException {
		String S = C + ": setValueIgnoreWarning(): ";
		if(D) System.out.println(S + "Setting value ignoring warning and constraint: ");
		//        this.value = value;
		super.setValue(value);
	}

	/**
	 *  Uses the warning constraint object to determine if the new value being set is
	 *  within recommended range. If no Constraints are present all values
	 *  are recommended. The implied intention is that the warning constraint is
	 *  more restrictive than the absolute constraints so that if a value
	 *  passes the warning test, it will also pass the absolute constraint test.
	 *
	 * @param  obj  Object to check if allowed via constraints
	 * @return      True if the value is allowed
	 */
	public boolean isRecommended( Double obj ) {
		if ( warningConstraint != null ) return warningConstraint.isAllowed( obj );
		else return true;

	}


	/**
	 * This is the function that notifies all listeners assigned to this parameter
	 * that the warning constraints have been exceeded.
	 *
	 * @param  event  The event encapsulating the attempted values passed to each listener.
	 */
	public void fireParameterChangeWarning( ParameterChangeWarningEvent event ) {

		String S = C + ": firePropertyChange(): ";
		if(D) System.out.println(S + "Starting: " + this.getName() );


		ArrayList vector;
		synchronized ( this ) {
			if ( warningListeners == null ) return;
			vector = ( ArrayList ) warningListeners.clone();
		}
		for ( int i = 0; i < vector.size(); i++ ) {
			ParameterChangeWarningListener listener = ( ParameterChangeWarningListener ) vector.get( i );
			if (listener == null)
				continue;
			if(D) System.out.println(S + "Firing warning to (" + i + ") " + listener.getClass().getName());
			listener.parameterChangeWarning( event );
		}

		if(D) System.out.println(S + "Ending: " + this.getName() );

	}



	/**
	 *  Compares the values to if this is less than, equal to, or greater than
	 *  the comparing objects. This implies that the value object of
	 *  both parameters must be a double.
	 *
	 * @param  obj                     The object to compare this to
	 * @return                         -1 if this value < obj value, 0 if equal,
	 *      +1 if this value > obj value
	 * @exception  ClassCastException  Is thrown if the comparing object is not
	 *      a DoubleParameter, or DoubleDiscreteParameter.
	 */
	//	@Override
	//	public int compareTo(ParameterAPI<Double> param) {
	//
	//        String S = C + ":compareTo(): ";
	//
	//        if ( !( obj instanceof DoubleParameter )
	//            && !( obj instanceof DoubleDiscreteParameter )
	//            && !( obj instanceof WarningDoubleParameter )
	//        ) {
	//            throw new ClassCastException( S +
	//                "Object not a DoubleParameter, WarningDoubleParameter, or DoubleDiscreteParameter, unable to compare"
	//            );
	//        }
	//
	//        int result = 0;
	//
	//        Double n1 = ( Double ) this.getValue();
	//        Double n2 = null;
	//
	//        if ( obj instanceof DoubleParameter ) {
	//            DoubleParameter param = ( DoubleParameter ) obj;
	//            n2 = ( Double ) param.getValue();
	//        }
	//        else if ( obj instanceof DoubleDiscreteParameter ) {
	//            DoubleDiscreteParameter param = ( DoubleDiscreteParameter ) obj;
	//            n2 = ( Double ) param.getValue();
	//        }
	//        else if ( obj instanceof WarningDoubleParameter ) {
	//            WarningDoubleParameter param = ( WarningDoubleParameter ) obj;
	//            n2 = ( Double ) param.getValue();
	//        }
	//        //if we have set a null value in the Parameter like in the basin depth
	//        //then compareTo function fails so we retun true is both are null
	//        if(n1 == null && n2 == null)
	//          return 0;
	//
	//        return n1.compareTo( n2 );

	//		return value.compareTo(param.getValue());
	//
	//	}


	/**
	 *  Compares the values to if this is less than, equal to, or greater than
	 *  the comparing objects. This implies that the value object of
	 *  both parameters must be a double.
	 *
	 * @param  obj                     The object to compare this to
	 * @return                         -1 if this value < obj value, 0 if equal,
	 *      +1 if this value > obj value
	 * @exception  ClassCastException  Is thrown if the comparing object is not
	 *      a DoubleParameter, or DoubleDiscreteParameter.
	 */
	//	@Override
	//    public boolean equals(Object obj) {
	////        String S = C + ":equals(): ";
	////
	////        if ( !( obj instanceof DoubleParameter )
	////            && !( obj instanceof DoubleDiscreteParameter )
	////            && !( obj instanceof WarningDoubleParameter )
	////        ) {
	////            throw new ClassCastException( S + "Object not a DoubleParameter, WarningDoubleParameter, or DoubleDiscreteParameter, unable to compare" );
	////        }
	////
	////        String otherName = ( ( DoubleParameter ) obj ).getName();
	////        if ( ( compareTo( obj ) == 0 ) && getName().equals( otherName ) ) {
	////            return true;
	////        }
	////        else return false;
	//        
	//		if (this == obj) return true;
	//		if (!(obj instanceof WarningDoubleParameter)) return false;
	//		WarningDoubleParameter wdp = (WarningDoubleParameter) obj;
	//		return (compareTo(wdp) == 0 && getName().equals(wdp.getName()));
	//
	//    }


	/**
	 *  Returns a copy so you can't edit or damage the origial. All fields,
	 *  constraints and the value object are cloned.
	 *
	 * @return    Exact copy of this object's state
	 */
	public Object clone() {

		String S = C + ":clone(): ";
		if(D) System.out.println(S + "Starting");

		DoubleConstraint c1 = null;
		DoubleConstraint c2 = null;

		if( constraint != null ) c1 = ( DoubleConstraint ) constraint.clone();
		if( warningConstraint != null ) c2 = ( DoubleConstraint ) warningConstraint.clone();


		WarningDoubleParameter param = null;
		if( value == null ) param = new WarningDoubleParameter( name, c1, units);
		else param = new WarningDoubleParameter( name, c1, units, Double.valueOf( this.value.toString() )  );
		if( param == null ) return null;

		param.setWarningConstraint(c2);


		for ( Parameter<?> p1 : getIndependentParameterList() ){

			Parameter p2 = (Parameter)p1.clone();
			param.addIndependentParameter(p2);

		}


		// NOTE: The listeners are NOT cloned. They were interested in the original,
		// so should be interested in the clone

		if( this.warningListeners != null ){
			for (ParameterChangeWarningListener listener : warningListeners)
				param.addParameterChangeWarningListener( listener );
		}


		param.setInfo(info);
		param.setIgnoreWarning(this.ignoreWarning);


		param.editable = true;

		if(D) System.out.println(S + "Ending");
		return param;
	}


	/**
	 * Set to true to turn off warnings, will automatically set the value, unless
	 * exceeds Absolute contrsints. Set to false so that warning constraints are
	 * enabled, i.e. throw a WarningConstraintException if exceed recommened warnings.
	 */
	public void setIgnoreWarning(boolean ignoreWarning) { this.ignoreWarning = ignoreWarning; }

	/**
	 * Returns warning constraint enabled/disabled. If true warnings are turned off ,
	 * will automatically set the value, unless exceeds Absolute contrsints.
	 * If set to false warning constraints are enabled, i.e. throw a
	 * WarningConstraintException if exceed recommened warnings.
	 */
	public boolean isIgnoreWarning() { return ignoreWarning; }

	/**
	 * Parses the given XML element for a double value and sets it
	 */
	public boolean setIndividualParamValueFromXML(Element el) {
		try {
			Double val = Double.parseDouble(el.attributeValue("value"));
			this.setValueIgnoreWarning(val);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}


}
