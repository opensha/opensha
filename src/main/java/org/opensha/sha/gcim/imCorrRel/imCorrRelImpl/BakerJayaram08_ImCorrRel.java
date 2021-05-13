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
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodInterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_InterpolatedParam;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;
import org.opensha.sha.util.TectonicRegionType;

/**
 * <b>Title:</b>BakerJayaram_SaSa_ImCorrRel<br>
 *
 * <b>Description:</b>  This implements the Baker and Jayaram correlation
 * relationship between two spectral accelerations at periods Ti and Tj
 * 
 * See: J. W. Baker and N. Jayaram. (2008). Correlation of spectral acceleration 
 * values from NGA ground motion models, Earthquake Spectra,  24: 1, 299-317.<p>
 * 
 * Range of applicability: 0.01 < Ti , Tj < 10
 *
// * * Supported Intensity-Measure Parameters:<p>
// * <UL>
// * <LI>saParam - Response Spectral Acceleration
// * </UL><p>
// * Other Independent Parameters:<p>
// * <UL>
// * <LI>ti,ti - vibration periods to compute the correlation for
// * </UL></p>
 * 
 * @author Brendon Bradley
 * @version 1.0 1 April 2010
 * 
 * verified against the matlab code developed based on the above reference
 */

public class BakerJayaram08_ImCorrRel extends ImCorrelationRelationship {

    final static String C = "BakerJayaram08_ImCorrRel";
    public final static String NAME = "Baker and Jayaram (2008)";
    public final static String SHORT_NAME = "BJ2008";
    private static final long serialVersionUID = 1234567890987654353L;
    
    public final static String TRT_ACTIVE_SHALLOW = TectonicRegionType.ACTIVE_SHALLOW.toString();
    
    private double t_min = 0.01, t_max = 10; //min and max periods

    /**
     * no-argument constructor.  All this does is set ti and tj to Double.NaN
     * (as the default)
     */
    public BakerJayaram08_ImCorrRel() {
    	
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
        	double c2=Double.NaN, c3, c4;


        	double c1 = (1.0 - Math.cos( Math.PI / 2.0 - Math.log( t_max / Math.max( t_min , 0.109 ) ) * 0.366 ) );
        	if (t_max < 0.2) 
        		c2 = 1.0 - 0.105 * ( 1.0 - 1.0 / ( 1.0 + Math.exp( 100. * t_max - 5. ))) * ( t_max - t_min ) / ( t_max - 0.0099);
        	
        	if (t_max < 0.109)
        		c3 = c2;
        	else
        		c3 = c1;
        	
        	c4 = c1 + 0.5 * ( Math.sqrt(c3) - c3 ) * ( 1. + Math.cos( Math.PI * t_min/0.109));

        	if ( t_max <= 0.109 ) 
        		return c2;
        	else if ( t_min > 0.109 ) 
        		return c1;
        	else if ( t_max < 0.2 )
        		return Math.min( c2, c4 );
        	else
        		return c4;
    		
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

