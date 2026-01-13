package org.opensha.commons.param;

import org.opensha.commons.data.Named;
import org.opensha.commons.param.impl.EnumParameterizedModelarameter;

/**
 * Interface for a model that has adjustable parameters.
 * <p>
 * Potentially useful when paired with an enum to supply instances of the model and an {@link EnumParameterizedModelarameter}
 * parameter to configure them. 
 */
public interface ParameterizedModel extends Named {

	/**
	 * This returns any adjustable parameters for this model, or null if there are none.
	 * 
	 * @return adjustable parameters or null if there are none
	 */
	public ParameterList getAdjustableParameters();
	
	/**
	 * Metadata string for this model that returns '{@link #getName()}' if no parameters, else
	 * '{@link #getName()} [{@link ParameterList#getParameterListMetadataString()}]'.
	 * @return
	 */
	public default String getMetadataString() {
		return buildMetadataString(getName(), getAdjustableParameters());
	}
	
	public default String getMetadataString(String name) {
		return buildMetadataString(name, getAdjustableParameters());
	}
	
	/**
	 * Metadata string for this model that returns 'name' if no parameters, else
	 * 'name [{@link ParameterList#getParameterListMetadataString()}]'.
	 * @return
	 */
	public static String buildMetadataString(String name, ParameterList params) {
		StringBuffer ret = new StringBuffer();
		ret.append(name);
		if (params != null && !params.isEmpty())
			ret.append(" [").append(params.getParameterListMetadataString()).append("]");
		return ret.toString();
	}
}
