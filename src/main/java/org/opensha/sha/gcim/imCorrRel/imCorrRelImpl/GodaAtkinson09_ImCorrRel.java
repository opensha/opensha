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

package org.opensha.sha.gcim.imCorrRel.imCorrRelImpl;


import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.IA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodInterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_InterpolatedParam;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;
import org.opensha.sha.util.TectonicRegionType;

/**
 * <b>Title:</b>GodaAtkinson09_SaSa_ImCorrRel<br>
 *
 * <b>Description:</b>  This implements the Goda and Atkinson correlation
 * relationship between two spectral accelerations at periods Ti and Tj from KiK-Net data
 * 
 * See: Goda, K., Atkinson, G.M., 2009. Probabilistic characterization of spatially correlated
 * response spectra for earthquakes in Japan, Bulletin of the Seismological Society of 
 * America,  99 (5): 3003-3020.
 * 
 * Range of applicability: 0.1 < Ti , Tj < 5
 *
 * @author Brendon Bradley
 * @version 1.0 1 June 2010
 * 
 * verified against the matlab code developed based on the above reference
 */

public class GodaAtkinson09_ImCorrRel extends ImCorrelationRelationship {

    final static String C = "GodaAtkinson09_ImCorrRel";
    public final static String NAME = "Goda and Atkinson (2009)";
    public final static String SHORT_NAME = "GA2009";
    private static final long serialVersionUID = 1234567890987654353L;

    public final static String TRT_SUBDUCTION_INTERFACE = TectonicRegionType.SUBDUCTION_INTERFACE.toString();
    public final static String TRT_SUBDUCTION_SLAB = TectonicRegionType.SUBDUCTION_SLAB.toString();
    
    private double t_min = 0.1, t_max = 5; //min and max periods
    
    /**
     * no-argument constructor.  All this does is set ti and tj to Double.NaN
     * (as the default)
     */
    public GodaAtkinson09_ImCorrRel() {

    	super();
        
    	initOtherParams();
      	initSupportedIntensityMeasureParams();
        
      	this.ti = Double.NaN;
      	this.tj = Double.NaN;
    }
    
    /**
     * Computes the correlation coefficient between two Sa's at periods Ti and Tj.
     * @param ti, spectral period in seconds
     * @param tj, spectral period in seconds
     * @return pearson correlation coefficient between lnSa(Ti) and lnSa(Tj)
     */
    public double getImCorrelation(){
    	if (imi.getName()==SA_InterpolatedParam.NAME&&imj.getName()==SA_InterpolatedParam.NAME) {
    		
    		ti = ((SA_InterpolatedParam) imi).getPeriodInterpolatedParam().getValue();
    		tj = ((SA_InterpolatedParam) imj).getPeriodInterpolatedParam().getValue();
    		
    		double t_min = Math.min(ti, tj);
        	double t_max = Math.max(ti, tj);
        	
        	double theta1 = 1.374;
        	double theta2 = 5.586;
        	double theta3 = 0.728;
        	
        	double Itmin;
        	if (t_min<0.25) 
        		Itmin = 1.0;
        	else
        		Itmin = 0.0;
        	
        	
        	//compute the Sa correlation
        	double term1 = theta1 + theta2*Itmin*Math.pow(t_min/t_max,theta3)*Math.log10(t_min/0.25);
        	double rho = (1./3.)*(1.-Math.cos(Math.PI/2.-term1*Math.log10(t_max/t_min))) +
        					(1./3.)*(1.+Math.cos(-1.5*Math.log10(t_max/t_min)));

        	return rho;
        	
    	} else {
    		return Double.NaN;
    	}

    	
    }
    
    /**
     *  Creates other Parameters
     *  such as the tectonic region (and possibly others)
     */
    protected void initOtherParams() {
    	
    	// init other params defined in parent class
        super.initOtherParams();
        
    	// tectonic region
    	StringConstraint trtConstraint = new StringConstraint();
    	trtConstraint.addString(TRT_SUBDUCTION_INTERFACE);
    	trtConstraint.addString(TRT_SUBDUCTION_SLAB);
    	//trtConstraint.setNonEditable();
		tectonicRegionTypeParam = new TectonicRegionTypeParam(trtConstraint,TRT_SUBDUCTION_INTERFACE); // Constraint and default value
		tectonicRegionTypeParam.setValueAsDefault();
		// add these to the list
		otherParams.replaceParameter(tectonicRegionTypeParam.NAME, tectonicRegionTypeParam);
    }

    /**
     *  Creates the supported IM parameter (SA), as well as the
     *  independenParameters of SA (periodParam and dampingParam) and adds
     *  them to the supportedIMParams list. Makes the parameters noneditable.
     */
    protected void initSupportedIntensityMeasureParams() {

    	// Create saParam for i:
  	  	InterpPeriodiParam = new PeriodInterpolatedParam(t_min, t_max, 1.0, false);
  	  	saiDampingParam = new DampingParam();
  	  	saiInterpParam = new SA_InterpolatedParam(InterpPeriodiParam, saiDampingParam);
  	  	saiInterpParam.setNonEditable();
  	  	
  	  	// Create saParam for j:
  	  	InterpPeriodjParam = new PeriodInterpolatedParam(t_min, t_max, 1.0, false);
  	  	sajDampingParam = new DampingParam();
  	  	sajInterpParam = new SA_InterpolatedParam(InterpPeriodjParam, sajDampingParam);
  	  	sajInterpParam.setNonEditable();

  	  	//Now add the supported IMi and IMj params to the two lists 
  	  	supportedIMiParams.clear();       			supportedIMjParams.clear();
  	  	supportedIMiParams.add(saiInterpParam);		supportedIMjParams.add(sajInterpParam);
	  
    }

    /**
     * Returns the name of the object
     *
     */
    public String getName() {
      return NAME;
    }
    
    /**
     * Returns the short name of the object
     *
     */
    public String getShortName() {
      return SHORT_NAME;
    }
}

