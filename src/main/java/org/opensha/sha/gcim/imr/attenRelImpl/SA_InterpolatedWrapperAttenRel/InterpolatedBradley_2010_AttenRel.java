/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.sha.gcim.imr.attenRelImpl.SA_InterpolatedWrapperAttenRel;

import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.sha.gcim.imr.attenRelImpl.Bradley_2010_AttenRel;
import org.opensha.sha.imr.attenRelImpl.SA_InterpolatedWrapperAttenRel.InterpolatedSA_AttenRelWrapper;

/**
 * <b>Title:</b> InterpolatedBradley_2010_AttenRel<p>
 *
 * <b>Description:</b> 
 * 
 * @author     Brendon Bradley
 * @created    Nov, 2012
 * @version    1.0
 */


public class InterpolatedBradley_2010_AttenRel
    extends InterpolatedSA_AttenRelWrapper {

  // Debugging stuff
  public final static String SHORT_NAME = "Interp "+Bradley_2010_AttenRel.SHORT_NAME;
  private static final long serialVersionUID = 1234567890987654353L;
  public final static String NAME = "Interp "+Bradley_2010_AttenRel.NAME;

  /**
   *  This initializes several ParameterList objects.
   */
  public InterpolatedBradley_2010_AttenRel(ParameterChangeWarningListener warningListener) {
    super(warningListener,new Bradley_2010_AttenRel(warningListener));
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
