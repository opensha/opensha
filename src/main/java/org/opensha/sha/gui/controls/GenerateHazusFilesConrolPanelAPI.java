package org.opensha.sha.gui.controls;


import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.sha.imr.AttenuationRelationship;

/**
 * <p>Title: GenerateHazusFilesConrolPanelAPI</p>
 * <p>Description: This interface is the acts as the broker between the
 * application and the GenerateHazusFilesControlPanel</p>
 * @author : Nitin Gupta
 * @version 1.0
 */

public interface GenerateHazusFilesConrolPanelAPI {


  /**
   * This method calculates the probablity or the IML for the selected Gridded Region
   * and stores the value in each vectors(lat-ArrayList, Lon-ArrayList and IML or Prob ArrayList)
   * The IML or prob vector contains value based on what the user has selected in the Map type
   */
  public GeoDataSet generateShakeMap();

  /**
   *
   * @return the selected Attenuationrelationship model within the application
   */
  public AttenuationRelationship getSelectedAttenuationRelationship();


  /**
   * This function sets the Gridded region Sites and the type of plot user wants to see
   * IML@Prob or Prob@IML and it value.
   */
  public void getGriddedSitesAndMapType();

  /**
   * Gets the EqkRupture object from the Eqk Rupture GuiBean
   */
  public void getEqkRupture();

}
