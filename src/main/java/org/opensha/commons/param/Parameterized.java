package org.opensha.commons.param;

import org.opensha.commons.param.impl.ParameterizedEnumParameter;

/**
 * Interface for a model that has adjustable parameters.
 * <p>
 * Potentially useful when paired with an enum to supply instances of the model and an {@link ParameterizedEnumParameter}
 * parameter to configure them. 
 */
public interface Parameterized {

	/**
	 * This returns any adjustable parameters for this model, or null if there are none.
	 * 
	 * @return adjustable parameters or null if there are none
	 */
	public ParameterList getAdjustableParameters();
}
