/**
 * 
 */
package org.opensha.sha.gui.beans;



/**
 * The IMR_GuiBeanAPI should be implemented by any application which uses the IMR_GuiBean.
 * @author nitingupta
 *
 */
@Deprecated
public interface IMR_GuiBeanAPI {

	/**
	 * Updates the application using the IMR_GuiBean to update its Supported IM and show the correct 
	 * IM in the IMT Gui.
	 */
	public void updateIM();
	
    /**
     * Update the Site Params for the selected AttenuationRelationship, so the similar can be shown by the application and shown by
     * siteGuiBean.
     */	
    public void updateSiteParams();
}
