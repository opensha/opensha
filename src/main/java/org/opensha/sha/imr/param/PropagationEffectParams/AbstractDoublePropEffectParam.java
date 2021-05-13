package org.opensha.sha.imr.param.PropagationEffectParams;

import java.util.ArrayList;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.EditableException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.exceptions.WarningException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.constraint.AbstractParameterConstraint;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.ConstrainedDoubleParameterEditor;
import org.opensha.commons.param.editor.impl.DoubleParameterEditor;
import org.opensha.commons.param.event.ParameterChangeWarningEvent;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.param.impl.DoubleDiscreteParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * Add comments here
 *
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public abstract class AbstractDoublePropEffectParam extends
		PropagationEffectParameter<Double> implements
		WarningParameter<Double> {

	private transient ParameterEditor<Double> paramEdit = null;

	/** The warning constraint for this Parameter. */
	protected DoubleConstraint warningConstraint = null;

	/**
	 * Listeners that are interested in receiveing
	 * warnings when the warning constraints are exceeded.
	 * Only created if needed, else kept null, i.e. "Lazy Instantiation".
	 */
	protected transient ArrayList warningListeners = null;

	/**
	 * Set to true to turn off warnings, will automatically set the value,
	 * unless exceeds Absolute contrsints.
	 */
	protected boolean ignoreWarning;


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


	protected AbstractDoublePropEffectParam(String name) {
		setName(name);
	}

	/**
	 * Adds a listener to receive warning events when the warning constraints are exceeded.
	 * Only permitted if this parameter is currenlty editable, else an EditableException is thrown.
	 */
	public synchronized void addParameterChangeWarningListener( ParameterChangeWarningListener listener )
	throws EditableException
	{

		if( !this.editable ) throw new EditableException(C + ": setStrings(): " +
		"This constraint is currently not editable." );

		if ( warningListeners == null ) warningListeners = new ArrayList();
		if ( !warningListeners.contains( listener ) ) warningListeners.add( listener );

	}

	/**
	 * Adds a listener to receive warning events when the warning constraints are exceeded.
	 * Only permitted if this parameter is currenlty editable, else an EditableException is thrown.
	 */
	public synchronized void removeParameterChangeWarningListener( ParameterChangeWarningListener listener )
	throws EditableException
	{

		if( !this.editable ) throw new EditableException(C + ": setStrings(): " +
		"This constraint is currently not editable." );

		if ( warningListeners != null && warningListeners.contains( listener ) )
			warningListeners.remove( listener );
	}

	/**
	 * Sets the constraint if it is a DoubleConstraint and the parameter
	 *  is currently editable.
	 *
	 * @param warningConstraint     The new constraint for warnings
	 * @throws ParameterException   Thrown if the constraint is not a DoubleConstraint
	 * @throws EditableException    Thrown if the isEditable flag set to false.
	 */
	public void setWarningConstraint(AbstractParameterConstraint warningConstraint)
	throws ParameterException, EditableException
	{
		if( !this.editable ) throw new EditableException(C + ": setStrings(): " +
		"This constraint is currently not editable." );

		this.warningConstraint = (DoubleConstraint)warningConstraint;
	}

	/** Returns the warning constraint. May return null. */
	public AbstractParameterConstraint getWarningConstraint() throws ParameterException{
		return warningConstraint;
	}

	/**
	 *  Gets the min value of the constraint object. If the constraint
	 *  is not set returns null.
	 */
	public Double getWarningMin() throws Exception {
		if ( warningConstraint != null ) return warningConstraint.getMin();
		else return null;
	}


	/**
	 *  Returns the maximum allowed value of the constraint
	 *  object. If the constraint is not set returns null.
	 */
	public Double getWarningMax() {
		if ( warningConstraint != null ) return warningConstraint.getMax();
		else return null;

	}


	/**
	 *  Set's the parameter's value. There are several checks that must pass
	 *  before the value can be set.  The parameter must be currently editable,
	 *  else an EditableException is thrown. The warning constraints, if set will
	 *  throw a WarningException if exceeded. Finally if all other checks pass,
	 *  if the absoulte constraints are set, they cannot be exceeded. If they are
	 *  a Constraint Exception is thrown.
	 *
	 * @param  value                 The new value for this Parameter
	 * @throws  ParameterException   Thrown if the object is currenlty not
	 *      editable
	 * @throws  ConstraintException  Thrown if the object value is not allowed
	 */
	public synchronized void setValue( Double value ) throws ConstraintException, WarningException {

		String S = getName() + ": setValue(): ";
		if(D) System.out.println(S + "Starting: ");

		if ( !isAllowed( value ) ) {
			String err = S + "Value is not allowed: ";
			if( value != null ) err += value.toString();
			else err += "null value";

			if(D) System.out.println(err);
			throw new ConstraintException( err );
		}
		else if ( value == null ){
			if(D) System.out.println(S + "Setting allowed and recommended null value: ");
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
			this.value = value;
			org.opensha.commons.param.event.ParameterChangeEvent event = new org.opensha.commons.param.event.ParameterChangeEvent(
					this, getName(), getValue(), value );
			firePropertyChange( event );
		}
		if(D) System.out.println(S + "Ending: ");
	}


	/**
	 *  Set's the parameter's value bypassing all checks including
	 *  the absolute constraint check. WARNING: SWR: This may be a bug.
	 *  Should we bypass the Absolute Constraints. ???
	 */
	public void setValueIgnoreWarning( Double value ) throws ConstraintException, ParameterException {
		//        this.value = value;
		super.setValue(value);
	}

	/**
	 *  Uses the constraint object to determine if the new value being set is
	 *  within recommended range. If no Constraints are present all values are
	 *  recommended, including null.
	 *
	 * @param  obj  Object to check if allowed via constraints
	 * @return      True if the value is allowed.
	 */
	public boolean isRecommended( Double obj ) {
		if ( warningConstraint != null ) return warningConstraint.isAllowed( (Double)obj );
		else return true;

	}


	/**
	 * Notifes all listeners of a ChangeWarningEvent has occured.
	 */
	public void fireParameterChangeWarning( ParameterChangeWarningEvent event ) {

		ArrayList vector;
		synchronized ( this ) {
			if ( warningListeners == null ) return;
			vector = ( ArrayList ) warningListeners.clone();
		}

		for ( int i = 0; i < vector.size(); i++ ) {
			ParameterChangeWarningListener listener = ( ParameterChangeWarningListener ) vector.get( i );
			listener.parameterChangeWarning( event );
		}

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
	@Override
	public int compareTo(Parameter<?> param) {
//
//		String S = C + ":compareTo(): ";
//
//		if ( !( obj instanceof DoubleParameter )
//				&& !( obj instanceof DoubleDiscreteParameter )
//				&& !( obj instanceof WarningDoubleParameter )
//				&& !( obj instanceof PropagationEffectParameter )
//		) {
//			throw new ClassCastException( S +
//					"Object not a DoubleParameter, WarningDoubleParameter, DoubleDiscreteParameter, DistanceJBParameter, or WarningDoublePropagationEffectBParameter, unable to compare"
//			);
//		}
//
//		int result = 0;
//
//		Double n1 = ( Double ) this.getValue();
//		Double n2 = null;
//
//		if ( obj instanceof DoubleParameter ) {
//			DoubleParameter param = ( DoubleParameter ) obj;
//			n2 = ( Double ) param.getValue();
//		}
//		else if ( obj instanceof DoubleDiscreteParameter ) {
//			DoubleDiscreteParameter param = ( DoubleDiscreteParameter ) obj;
//			n2 = ( Double ) param.getValue();
//		}
//		else if ( obj instanceof WarningDoubleParameter ) {
//			WarningDoubleParameter param = ( WarningDoubleParameter ) obj;
//			n2 = ( Double ) param.getValue();
//		}
//
//		else if ( obj instanceof PropagationEffectParameter ) {
//			PropagationEffectParameter param = ( PropagationEffectParameter ) obj;
//			n2 = ( Double ) param.getValue();
//		}
//
//		return n1.compareTo( n2 );
		return value.compareTo((Double) param.getValue());
		// TODO override in subclasses for type comparison
	}




	/**
	 *  Compares value to see if equal.
	 *
	 * @param  obj                     The object to compare this to
	 * @return                         True if the values are identical
	 * @exception  ClassCastException  Is thrown if the comparing object is not
	 *      a DoubleParameter, or DoubleDiscreteParameter.
	 */
	public boolean equals(Object obj) {
//		String S = C + ":equals(): ";
//
//		if ( !( obj instanceof DoubleParameter )
//				&& !( obj instanceof DoubleDiscreteParameter )
//				&& !( obj instanceof WarningDoubleParameter )
//				&& !( obj instanceof PropagationEffectParameter )
//		) {
//			throw new ClassCastException( S + "Object not a DoubleParameter, WarningDoubleParameter, or DoubleDiscreteParameter, unable to compare" );
//		}
//
//		String otherName = ( ( ParameterAPI ) obj ).getName();
//		PropagationEffectParameter wdpep = (PropagationEffectParameter) obj;
//		if ( ( compareTo( wdpep ) == 0 ) && getName().equals( otherName ) ) {
//			return true;
//		}
//		else return false;
		if (this == obj) return true;
		if (!(obj instanceof AbstractDoublePropEffectParam)) return false;
		AbstractDoublePropEffectParam dp = (AbstractDoublePropEffectParam) obj;
		return (compareTo(dp) == 0 && getName().equals(dp.getName()));
		
		// TODO this should compare objID, then class, then name, then value
		// probably can be done in parent class; shouldn't rely on compareo
	}

	/**
	 * Standard Java function. Creates a copy of this class instance
	 * so originaly can not be modified
	 */
	//public abstract Object clone();

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

}
