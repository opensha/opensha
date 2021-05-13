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

package org.opensha.sha.gcim.imCorrRel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;

import org.opensha.commons.data.Named;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.ASI_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.CAV_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.DSI_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.Ds575_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.Ds595_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.SI_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.IA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGD_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodInterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_InterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;
import org.opensha.sha.util.TectonicRegionType;


/**
 * <b>Title:</b>ImCorrelationRelationship<br>
 *
 * <b>Description:  This is an abstract class that gives the Pearson correlation
 * coefficient between (the natural logarithm of) two different intensity measures</b>  <p>
 *
 * @author Brendon Bradley
 * @version 1.0 1 April 2010
 */

public abstract class ImCorrelationRelationship implements Named  {

    final static String C = "ImCorrelationRelationship";
    private static final long serialVersionUID = 1234567890987654353L;
    /**
     * The supported IM parameters for which correlation equations can be developed
     */
    protected PGA_Param pgaParam = null;
	protected PGV_Param pgvParam = null;
	protected PGD_Param pgdParam = null;
	protected SA_Param saiParam = null;
	protected PeriodParam saPeriodiParam = null;
	protected SA_Param sajParam = null;
	protected PeriodParam saPeriodjParam = null;
	protected SA_InterpolatedParam saiInterpParam = null;
	protected PeriodInterpolatedParam InterpPeriodiParam = null;
	protected SA_InterpolatedParam sajInterpParam = null;
	protected PeriodInterpolatedParam InterpPeriodjParam = null;
	protected DampingParam saiDampingParam = null;
	protected DampingParam sajDampingParam = null;
	protected IA_Param iaParam = null;
	protected SI_Param siParam = null;
	protected ASI_Param asiParam = null;
	protected DSI_Param dsiParam = null;
	protected CAV_Param cavParam = null;
	protected Ds575_Param ds575Param = null;
	protected Ds595_Param ds595Param = null;
    
	protected ParameterList supportedImParams = new ParameterList();
	
	protected TectonicRegionTypeParam tectonicRegionTypeParam = null;
	
    /**
     * The spectral period for IMi and IMj (when either are SA).  The default is Double.NaN
     */
    protected double ti = Double.NaN;
    protected double tj = Double.NaN;
    
    
    /**
     * Computes the correlation between two intensity measures (neither of which are spectral acceleration)
     * @return intensity measure correlation between imi and imj
     */
    public abstract double getImCorrelation();
    
    /**
     * Computes the correlation between two intensity measures (one of which is spectral acceleration)
     * @return intensity measure correlation between Sa at T=Ti and imj
     */
    public double getImCorrelation(double ti) {
    	setTi(ti);
    	return getImCorrelation();
    }
    
    /**
     * Computes the correlation between two intensity measures (both of which are spectral acceleration)
     * @return intensity measure correlation between Sa at T=Ti and imj
     */
    public double getImCorrelation(double ti, double tj) {
    	setTi(ti);
    	setTj(tj);
    	return getImCorrelation();
    }

    public void setTi(double ti) {
      this.ti = ti;
    }
    
    public void setTj(double tj) {
        this.tj = tj;
      }
    
    /**
	 * Returns a pointer to a parameter if it exists in one of the parameter lists
	 *
	 * @param name                  Parameter key for lookup
	 * @return                      The found parameter
	 * @throws ParameterException   If parameter with that name doesn't exist
	 */
	public Parameter getParameter(String name) throws ParameterException {
		for (int i=0; i<supportedIMjParams.size(); i++) {
			//Put the ith value in a parameter list (its the only entry so this 
			//is somewhat redundant, but it means that existing similar code can be used)
			supportedImParams.clear();
			supportedImParams.addParameter(supportedIMjParams.get(i));
			try {
				return supportedImParams.getParameter(name);
			}
			catch (ParameterException e) {}

			ListIterator<Parameter<?>> it = supportedImParams.getParametersIterator();
			while (it.hasNext()) {

				Parameter param = (Parameter) it.next();
				if (param.containsIndependentParameter(name)) {
					return param.getIndependentParameter(name);
				}
			}
		}
		
		try {
			return otherParams.getParameter(name);
		}
		catch (ParameterException e) {}

		throw new ParameterException(C +
				": getParameter(): Parameter doesn't exist named " + name);
	}
	
	
	/**
	 *  Intensity Measurei.  This is a specification of the type of shaking one
	 *  is concerned about.  Its representation as a Parameter makes the
	 *  specification quite general and flexible.  IMRs compute the probability
	 *  of exceeding the "value" field of this im Parameter.
	 */
	protected Parameter imi;
	
	/**
	 *  Intensity Measurej.  This is a specification of the type of shaking one
	 *  is concerned about.  Its representation as a Parameter makes the
	 *  specification quite general and flexible.  IMRs compute the probability
	 *  of exceeding the "value" field of this im Parameter.
	 */
	protected Parameter imj;

	protected boolean intensityMeasureiChanged;
	
	protected boolean intensityMeasurejChanged;
	
	/**
	 *  Gets a reference to the currently chosen Intensity-Measure i Parameters
	 *  from the IMCorrRel.
	 *
	 * @return    The intensityMeasure Parameter
	 */
	public Parameter getIntensityMeasurei() {
		return imi;
	}
	
	/**
	 *  Gets a reference to the currently chosen Intensity-Measure j Parameters
	 *  from the IMCorrRel.
	 *
	 * @return    The intensityMeasure Parameter
	 */
	public Parameter getIntensityMeasurej() {
		return imj;
	}
	
	/**
	 *  Returns a list of all supported Intensity-Measure i
	 *  Parameters.
	 *
	 * @return    The Supported Intensity-Measures i Iterator
	 */
	public ArrayList<Parameter<?>> getSupportedIntensityMeasuresiList() {
		return supportedIMiParams;
	}
	
	/**
	 *  Returns a list of all supported Intensity-Measure j
	 *  Parameters.
	 *
	 * @return    The Supported Intensity-Measures j Iterator
	 */
	public ArrayList<Parameter<?>> getSupportedIntensityMeasuresjList() {
		return supportedIMjParams;
	}
	
	/**
	 *  This sets the intensityMeasure i parameter as that of the name passed in
	 *  ; no value (level) is set, nor are any of the IMi's independent
	 *  parameters set (since it's only given the name).
	 *
	 * @param  intensityMeasureiName  The new intensityMeasureiParameter name
	 */
	public void setIntensityMeasurei(String intensityMeasureiName) throws
	RuntimeException {
		boolean intensityMeasureiNameFound = false;
		for (int i=0; i<supportedIMiParams.size(); i++) {
			if (supportedIMiParams.get(i).getName()==intensityMeasureiName) {
				intensityMeasureiNameFound = true;
				imi = supportedIMiParams.get(i);
				intensityMeasureiChanged = true;
				break;
			}
		}
		if (!intensityMeasureiNameFound) {
			String S = C + ": setIntensityMeasurei(): ";
			throw new RuntimeException(S + "No parameter exists named " + intensityMeasureiName);
		}
	}
	
	/**
	 *  This sets the intensityMeasure j parameter as that of the name passed in
	 *  ; no value (level) is set, nor are any of the IMj's independent
	 *  parameters set (since it's only given the name).
	 *
	 * @param  intensityMeasurejName  The new intensityMeasurejParameter name
	 */
	public void setIntensityMeasurej(String intensityMeasurejName) throws
	RuntimeException {
		boolean intensityMeasurejNameFound = false;
		for (int i=0; i<supportedIMjParams.size(); i++) {
			if (supportedIMjParams.get(i).getName()==intensityMeasurejName) {
				intensityMeasurejNameFound = true;
				imj = (Parameter<?>) supportedIMjParams.get(i);
				intensityMeasurejChanged = true;
				break;
			}
		}
		if (!intensityMeasurejNameFound) {
			String S = C + ": setIntensityMeasurej(): ";
			throw new RuntimeException(S + "No parameter exists named " + intensityMeasurejName);
		}
	}
	
	
	/**
	 * Checks if the Parameter is a supported intensity-Measure i (checking
	 * only the name).
	 * @param intensityMeasurei Name of the intensity Measure I parameter
	 * @return
	 */
	public boolean isIntensityMeasureiSupported(String intensityMeasureiName) {
		for (int i=0; i<supportedIMiParams.size(); i++) {
			if (supportedIMiParams.get(i).getName()==intensityMeasureiName)
				return true;
		}
		return false;
	}
	
	/**
	 * Checks if the Parameter is a supported intensity-Measure j (checking
	 * only the name).
	 * @param intensityMeasurej Name of the intensity Measure j parameter
	 * @return
	 */
	public boolean isIntensityMeasurejSupported(String intensityMeasurejName) {
		for (int i=0; i<supportedIMjParams.size(); i++) {
			if (supportedIMjParams.get(i).getName()==intensityMeasurejName)
				return true;
		}
		return false;
	}
	
	/**
	 *  This creates the supported intensity-measure i ParameterList.   
	 */
	protected ArrayList<Parameter<?>> supportedIMiParams = new ArrayList<Parameter<?>>();
	/**
	 *  This creates the supported intensity-measure j ParameterList.  
	 */
	protected ArrayList<Parameter<?>> supportedIMjParams = new ArrayList<Parameter<?>>();

	/**
	 * ParameterList of other parameters.
	 */
	protected ParameterList otherParams = new ParameterList();
	
	/**
	 *  Returns an iterator over all other parameters.  Other parameters are those
	 *  that the exceedance probability depends upon, but that are not a
	 *  supported IMT (or one of their independent parameters) and are not contained
	 *  in, or computed from, the site or eqkRutpure objects.  Note that this does not
	 *  include the exceedProbParam (which exceedance probability does not depend on).
	 *
	 * @return    Iterator for otherParameters
	 */
	public ListIterator<Parameter<?>> getOtherParamsIterator() {
		return otherParams.getParametersIterator();
	}
	
	public ParameterList getOtherParamsList() {
		return otherParams;
	}
	
	/**
	 * This creates the otherParams list.
	 * The tectonicRegionTypeParam is instantiated here with default options 
	 * (TYPE_ACTIVE_SHALLOW); this should be overridden in subclass if other options 
	 * are desired (and you'll need use the replaceParameter method to change the one in the
	 * otherParams list).
	 */
	protected void initOtherParams() {

		tectonicRegionTypeParam = new TectonicRegionTypeParam();
		tectonicRegionTypeParam.setValueAsDefault();
		System.out.println("1: " + tectonicRegionTypeParam);
		
		// Put parameters in the otherParams list:
		otherParams.clear();
		otherParams.addParameter(tectonicRegionTypeParam);
	}
	
	/**
	 * Tells whether the given tectonic region is supported
	 * @param tectRegionName
	 * @return
	 */
	public boolean isTectonicRegionSupported(String tectRegionName) {
		if (tectonicRegionTypeParam == null)
			return false;
		return tectonicRegionTypeParam.isAllowed(tectRegionName);
	}
	
	/**
	 * Tells whether the given tectonic region is supported
	 * @param tectRegion
	 * @return
	 */
	public boolean isTectonicRegionSupported(TectonicRegionType tectRegion) {
		return isTectonicRegionSupported(tectRegion.toString());
	}

    /**
     * Returns the name of the object
     *
     */
    public abstract String getName() ;
    
    /**
     * Returns the short name of the object
     *
     */
    public abstract String getShortName() ;

}
