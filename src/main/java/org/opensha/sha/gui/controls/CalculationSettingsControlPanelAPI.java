package org.opensha.sha.gui.controls;

import org.opensha.commons.param.ParameterList;
/**
 * <p>Title: CalculationSettingsControlPanelAPI</p>
 * <p>Description: This interface has to be implemented by the class that needs to use
 * the PropagationEffect control panel.</p>
 * @author : Ned Field, Nitin Gupta and Vipin Gupta
 * @version 1.0
 */

public interface CalculationSettingsControlPanelAPI {

  /**
   *
   * @return the Adjustable parameters for the Hazardcurve and Scenarioshakemap calculator
   */
  public ParameterList getCalcAdjustableParams();

  /**
   *
   * @return the Metadata string for the Calculation Settings Adjustable Params
   */
  public String getCalcParamMetadataString();

}
