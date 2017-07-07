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

package org.opensha.sha.earthquake;

import java.util.ArrayList;

/**
 * <p>Title: EpistemicListERF (was ERF_ListAPI)</p>
 * <p>Description: This represents an epistemic list of earthquake rupture forecasts and their associated weights.</p>
 * @author : Ned Field, Nitin Gupta and Vipin Gupta
 * @created June 19,2003
 * @version 1.0
 */

public interface EpistemicListERF extends BaseERF{



  /**
   * get the ERF in the list with the specified index
   * @param index : index of Eqk Rup forecast to return
   * @return
   */
  public ERF getERF(int index);

  /**
   * get the weight of the ERF at the specified index
   * @param index : index of ERF
   * @return : relative weight of ERF
   */
  public double getERF_RelativeWeight(int index)  ;


  /**
   * Return the vector containing the Double values with
   * relative weights for each ERF
   * @return : ArrayList of Double values
   */
  public ArrayList<Double> getRelativeWeightsList();


  /**
   * get the number of Eqk Rup Forecasts in this list
   * @return : number of eqk rup forecasts in this list
   */
  public int getNumERFs();

}
