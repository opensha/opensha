package org.opensha.commons.util.modules;

@ModuleHelper
public interface SubModule<E extends ModuleContainer<?>> extends OpenSHA_Module {
	
	/**
	 * Sets the parent container of this module
	 * 
	 * @param parent
	 * @throws IllegalStateException if the given parent is not applicable to this module
	 */
	public void setParent(E parent) throws IllegalStateException;
	
	
	/**
	 * @return the parent container of this module, or null if not yet set
	 */
	public E getParent();
	
	/**
	 * Creates a copy of this module with the parent container set to passed in parent container
	 * 
	 * @param newParent
	 * @return copy of this module with the new parent set
	 * @throws IllegalStateException if the new parent is not applicable to this module
	 */
	public SubModule<E> copy(E newParent) throws IllegalStateException;

}
