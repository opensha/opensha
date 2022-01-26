package org.opensha.sha.gcim.imr.attenRelImpl.SA_InterpolatedWrapperAttenRel;

import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.sha.imr.attenRelImpl.CY_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SA_InterpolatedWrapperAttenRel.InterpolatedSA_AttenRelWrapper;

/**
 * <b>Title:</b> InterpolatedCY_2008_AttenRel<p>
 *
 * <b>Description:</b> 
 * 
 * @author     Brendon Bradley
 * @created    July, 2010
 * @version    1.0
 */


public class InterpolatedCY_2008_AttenRel
    extends InterpolatedSA_AttenRelWrapper {

  // Debugging stuff
  public final static String SHORT_NAME = "Interp "+CY_2008_AttenRel.SHORT_NAME;
  private static final long serialVersionUID = 1234567890987654353L;
  public final static String NAME = "Interp "+CY_2008_AttenRel.NAME;

  /**
   *  This initializes several ParameterList objects.
   */
  public InterpolatedCY_2008_AttenRel(ParameterChangeWarningListener warningListener) {
    super(warningListener,new CY_2008_AttenRel(warningListener));
  }
  
  /**
   * get the name of this IMR
   *
   * @return the name of this IMR
   */
  public String getName() {
    return NAME;
  }

  /**
   * Returns the Short Name of each AttenuationRelationship
   * @return String
   */
  public String getShortName() {
    return SHORT_NAME;
  }


}
