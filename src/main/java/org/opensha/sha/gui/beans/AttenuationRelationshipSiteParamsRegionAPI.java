package org.opensha.sha.gui.beans;

/**
 * <p>Title: AttenuationRelationshipSiteParamsRegionAPI</p>
 * <p>Description: This class is implemented by the application that uses the
 * MultipleIMR_GuiBean. This gets the site parameters for all the selected
 * AttenuationRelationships and send it to the corresponding Gui Bean.</p>
 * @author : Nitin Gupta
 * @created March 12, 2004
 * @version 1.0
 */

public interface AttenuationRelationshipSiteParamsRegionAPI {


  /**
   * sets the Site Params from the AttenuationRelationships in the GriddedRegion
   * Gui Bean.
   */
  public void setGriddedRegionSiteParams();

}
