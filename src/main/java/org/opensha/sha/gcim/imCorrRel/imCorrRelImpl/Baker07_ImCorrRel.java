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
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodInterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_InterpolatedParam;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;
import org.opensha.sha.util.TectonicRegionType;

/**
 * <b>Title:</b>Baker07_SaPga_ImCorrRel<br>
 *
 * <b>Description:</b>  This implements the Baker correlation
 * relationship between: (i)PGA and spectral acceleration at period Ti ; and 
 * (ii) IA and SA at period Ti.
 * 
 * See: J. W. Baker. (2007). Correlation of ground motion intensity parameters 
 * used for predicting structural and geotechnical response, 10th International 
 * Conference on Application of Statistics and Probability in Civil Engineering: 8.<p>
 * 
 * Range of applicability: 0.05 < Ti < 5
 *
 * @author Brendon Bradley
 * @version 1.0 1 April 2010
 * 
 * verified against the matlab code developed based on the above reference
 */

public class Baker07_ImCorrRel extends ImCorrelationRelationship {

    final static String C = "Baker07_ImCorrRel";
    public final static String NAME = "Baker (2007)";
    public final static String SHORT_NAME = "Baker2007";
    private static final long serialVersionUID = 1234567890987654353L;
    
    public final static String TRT_ACTIVE_SHALLOW = TectonicRegionType.ACTIVE_SHALLOW.toString();

    private double t_min = 0.05, t_max = 5; //min and max periods
    
    /**
     * no-argument constructor.  All this does is set ti to Double.NaN
     * (as the default)
     */
    public Baker07_ImCorrRel() {

    	super();
        
    	initOtherParams();
      	initSupportedIntensityMeasureParams();
        
      	this.ti = Double.NaN;
    }
    
    /**
     * Computes the correlation coefficient between PGA and Sa at period Ti.
     * @param ti, spectral period in seconds
     * @return pearson correlation coefficient between lnPGA and lnSa(Ti)
     */
    public double getImCorrelation(){
    	//The Sa, PGA correlation
    	if ((imi.getName()==SA_InterpolatedParam.NAME&&imj.getName()==PGA_Param.NAME)||
        		(imi.getName()==PGA_Param.NAME&&imj.getName()==SA_InterpolatedParam.NAME)) {
    		
    		if (imi.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imi).getPeriodInterpolatedParam().getValue();
    		else if (imj.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imj).getPeriodInterpolatedParam().getValue();
    		
    		//if ti is out of range [0.05,5] then take it to be at end of range
        	if ( ti < 0.05)
        		ti = 0.05;
        	else if ( ti > 5.0)
        		ti = 5;
        	
        	if ( ti < 0.11)
        		return 0.5 - 0.127 * Math.log(ti);
        	else if ( ti < 0.25)
        		return 0.968 + 0.085 * Math.log(ti);
        	else
        		return 0.568 - 0.204 * Math.log(ti);
        	
    	} 
    	//The Sa, IA correlation
    	else if ((imi.getName()==SA_InterpolatedParam.NAME&&imj.getName()==IA_Param.NAME)||
    		(imi.getName()==IA_Param.NAME&&imj.getName()==SA_InterpolatedParam.NAME)) {
    		
    		if (imi.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imi).getPeriodInterpolatedParam().getValue();
    		else if (imj.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imj).getPeriodInterpolatedParam().getValue();
    		
    		//if ti is out of range [0.05,5] then take it to be at end of range
        	if ( ti < 0.05)
        		ti = 0.05;
        	else if ( ti > 5.0)
        		ti = 5;
        	
        	if ( ti < 0.11)
        		return 0.344 - 0.152 * Math.log(ti);
        	else if ( ti < 0.4)
        		return 0.971 + 0.131 * Math.log(ti);
        	else
        		return 0.697 - 0.166 * Math.log(ti);
        	
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
    	trtConstraint.addString(TRT_ACTIVE_SHALLOW);
    	//trtConstraint.setNonEditable();
		tectonicRegionTypeParam = new TectonicRegionTypeParam(trtConstraint,TRT_ACTIVE_SHALLOW); // Constraint and default value
		tectonicRegionTypeParam.setValueAsDefault();
		// add these to the list
		otherParams.replaceParameter(tectonicRegionTypeParam.NAME, tectonicRegionTypeParam);
    }
    
    /**
     *  Creates the supported IM parameters (SA and PGA), as well as the
     *  independenParameters of SA (periodParam and dampingParam) and adds
     *  them to the supportedIMParams list. Makes the parameters noneditable.
     */
    protected void initSupportedIntensityMeasureParams() {
    	
    	
        // Create saiInterpParam:
  	  	InterpPeriodiParam = new PeriodInterpolatedParam(t_min, t_max, 1.0, false);
  	  	saiDampingParam = new DampingParam();
  	  	saiInterpParam = new SA_InterpolatedParam(InterpPeriodiParam, saiDampingParam);
  	  	saiInterpParam.setNonEditable();
  	  
  	  	//Create pgaParam
  	  	pgaParam = new PGA_Param();
  	  	pgaParam.setNonEditable();
  	  	
  	  	//Create iaParam
    	iaParam = new IA_Param();
    	iaParam.setNonEditable();

  	  	//Now add the supported IMi and IMj params to the two lists 
    	supportedIMiParams.clear();       			supportedIMjParams.clear();
    	//The Sa, PGA correlation
    	supportedIMiParams.add(saiInterpParam);		supportedIMjParams.add(pgaParam);
    	supportedIMiParams.add(pgaParam);			supportedIMjParams.add(saiInterpParam);
	  	//The Sa, IA correlation
	  	supportedIMiParams.add(saiInterpParam);		supportedIMjParams.add(iaParam); 
	  	supportedIMiParams.add(iaParam);			supportedIMjParams.add(saiInterpParam);
  
  	  	
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

