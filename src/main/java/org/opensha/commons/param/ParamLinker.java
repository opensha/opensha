package org.opensha.commons.param;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;

/**
 * This class links two parameters together. Whenever the master parameter's value is
 * changed, that same value will be set in the child parameter.
 * 
 * By default, exceptions will be thrown if the child parameter cannot be set, but this can be
 * overriden with the <code>throwExceptions</code> field.
 * 
 * @author kevin
 *
 * @param <E>
 */
public class ParamLinker<E> implements ParameterChangeListener {
	
	private static final boolean D = false;
	
	private Parameter<E> parentParam;
	private Parameter<E> childParam;
	private WarningParameter<E> warnChild = null;
	
	private boolean throwExceptions = true;
	
	public ParamLinker(Parameter<E> parentParam, Parameter<E> childParam) {
		this(parentParam, childParam, true);
	}
	
	public ParamLinker(Parameter<E> parentParam, Parameter<E> childParam, boolean throwExceptions) {
		this.parentParam = parentParam;
		this.childParam = childParam;
		this.throwExceptions = throwExceptions;
		if (childParam instanceof WarningParameter) {
			warnChild = (WarningParameter<E>)childParam;
		}
		E parentVal = parentParam.getValue();
		E childVal = childParam.getValue();
		if (parentVal == null || !parentVal.equals(childVal))
			setChildVal(parentVal);
		parentParam.addParameterChangeListener(this);
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (D) System.out.println("ParamLinker: ("+parentParam.getName()+") propogating change: "+event.getNewValue());
		setChildVal((E)event.getNewValue());
	}
	
	private void setChildVal(E value) {
		try {
			if (warnChild != null)
				warnChild.setValueIgnoreWarning(value);
			else
				childParam.setValue(value);
		} catch (ConstraintException e) {
			System.err.println("WARNING: child parameter '" + childParam.getName()
					+ "' could not be set to '" + value + "'");
			if (throwExceptions)
				throw e;
		} catch (ParameterException e) {
			System.err.println("WARNING: child parameter '" + childParam.getName()
					+ "' could not be set to '" + value + "'");
			if (throwExceptions)
				throw e;
		}
	}

}
