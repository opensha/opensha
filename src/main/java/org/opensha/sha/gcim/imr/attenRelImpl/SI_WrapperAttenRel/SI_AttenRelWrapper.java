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

package org.opensha.sha.gcim.imr.attenRelImpl.SI_WrapperAttenRel;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.ListIterator;

import org.opensha.commons.data.Named;
import org.opensha.commons.data.Site;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.gcim.imCorrRel.imCorrRelImpl.BakerJayaram08_ImCorrRel;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.SI_Param;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.EqkRuptureParams.FaultTypeParam;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodInterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_InterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

/**
 * <b>Title:</b> SI_AttenRelWrapper<p>
 *
 * <b>Description:</b> 
 * Provides the wrapper that allows all ground motion prediction equations for Sa to be used to compute
 * SI, Specturm Intensity
 * 
 * Reference:
 * Bradley, B.A., Cubrinovski, M., MacRae, G.A., Dhakal, R.P., 2009. Ground motion prediction equation
 * for spectrum intensity from spectral acceleration relationships, Bulletin of the Seismological
 * Society of America,  99 (1): 277-285.
 *
 * Supported Intensity-Measure Parameters:<p>
 * <UL>
 * <LI>siParam - Spectrum intensity
 * </UL><p>
 * Other Independent Parameters:<p>
 *<p>
 *
 * Verification - This model has been verified against the original Matlab code used in the above reference.
 * 
 *</p>
 *
 * @author     Brendon Bradley
 * @created    June, 2010
 * @version    1.0
 */

public class SI_AttenRelWrapper
    extends AttenuationRelationship implements
    ScalarIMR,
    Named, ParameterChangeListener {

  // Debugging stuff
  private final static String C = "SI_AttenRelWrapper";
  private final static boolean D = false;
  public final static String SHORT_NAME = "SI";
  private static final long serialVersionUID = 1234567890987654353L; 
  private AttenuationRelationship attenRelToWrap; 
  PeriodInterpolatedParam origPeriodParam; 
  private SI_Param siParam; 
  private ImCorrelationRelationship corr;
  double[] ti, intWeight, meanLnSa, sigmaLnSa, meanPsv, sigmaPsv;
  double meanLnSi, sigmaLnSi;
  double accGravity = 981.0; //gravity in cm/s2
  int numIntPoints=21; 

  // Name of IMR
  public final static String NAME = "SI Atten Rel Wrapper";
  
  // URL Info String
  private final static String URL_INFO_STRING = null;

  private boolean parameterChange;

  // for issuing warnings:
  private transient ParameterChangeWarningListener warningListener = null;
  
  
  // No arg constructor
  public SI_AttenRelWrapper() {
  }
  
  
  /**
   *  This initializes several ParameterList objects.
   */
  public SI_AttenRelWrapper(ParameterChangeWarningListener warningListener, 
		  AttenuationRelationship attenRelToWrap) {
	  
    super();
    this.attenRelToWrap = attenRelToWrap;
    attenRelToWrap.setIntensityMeasure(SA_InterpolatedParam.NAME);  // set this now and forever
    
    this.warningListener = warningListener;

    setDefaultImCorrRel();
    
    initSupportedIntensityMeasureParams();
    siteParams = attenRelToWrap.getSiteParams();
    eqkRuptureParams = attenRelToWrap.getEqkRuptureParams();
    propagationEffectParams = attenRelToWrap.getPropagationEffectParams();
    otherParams = attenRelToWrap.getOtherParams();
    sigmaTruncTypeParam = (SigmaTruncTypeParam)otherParams.getParameter(SigmaTruncTypeParam.NAME);
    sigmaTruncLevelParam = (SigmaTruncLevelParam)otherParams.getParameter(SigmaTruncLevelParam.NAME);
    initIndependentParamLists(); // This must be called after the above
    initParameterEventListeners(); //add the change listeners to the parameters
    
    // add this as listener to gui params
    addParameterListener(siteParams);
    addParameterListener(eqkRuptureParams);
    addParameterListener(propagationEffectParams);
    addParameterListener(otherParams); 
   
    
    //parameters needed to carry out the SI -> Sa calculations
	ti = new double[numIntPoints];
	intWeight = new double[numIntPoints];
	meanLnSa = new double[numIntPoints];
	sigmaLnSa = new double[numIntPoints];
	meanPsv = new double[numIntPoints];
	sigmaPsv = new double[numIntPoints];
  }
  
  // NOTE not sure this is the right way to be listening to any parameter
  // changes
  private void addParameterListener(ParameterList list) {
	  for (Parameter<?> p : list) {
		  p.addParameterChangeListener(this);
	  }
  }
  

  /**
   *  This sets the eqkRupture related parameters 
   *  based on the eqkRupture passed in.
   *  The internally held eqkRupture object is also set as that
   *  passed in.  Warning constrains are ingored.
   *
   * @param  eqkRupture  The new eqkRupture value
   * @throws InvalidRangeException thrown if rake is out of bounds
   */
  public void setEqkRupture(EqkRupture eqkRupture) throws InvalidRangeException {
	  attenRelToWrap.setEqkRupture(eqkRupture);
	  this.eqkRupture = eqkRupture;
  }

  /**
   *  This sets the site-related parameter (siteTypeParam) based on what is in
   *  the Site object passed in (the Site object must have a parameter with
   *  the same name as that in siteTypeParam).  This also sets the internally held
   *  Site object as that passed in.
   *
   * @param  site             The new site object
   * @throws ParameterException Thrown if the Site object doesn't contain a
   * Vs30 parameter
   */
  public void setSite(Site site) throws ParameterException {
	  attenRelToWrap.setSite(site);
	  this.site = site;
  }

  /**
   * This does nothing; is it needed?
   */
  protected void setPropagationEffectParams() {
  }


  /**
   * Calculates the mean of the exceedence probability distribution. <p>
   * @return    The mean value
   */
  public double getMean() {
	  if(parameterChange) {
		  getPsvAtPeriods();
		  getMeanSigmaLnSi();
		  parameterChange = false;
	  }
	  return (meanLnSi);
}

  /**
   * @return    The stdDev value
   */
  public double getStdDev() {
	  if(parameterChange) {
		  getPsvAtPeriods();
		  getMeanSigmaLnSi();
		  parameterChange = false;
	  }
	  return (sigmaLnSi);
  }
  
  /**
   * This method sets the default ImCorrelationRelationship to use for initialization
   */
  public void setDefaultImCorrRel() {
	  this.corr = new BakerJayaram08_ImCorrRel();
  }
  /**
   * This method sets the ImCorrRel to use (possibly different from default)
   */
  public void setImCorrRel(ImCorrelationRelationship imCorrRel) {
	  this.corr = imCorrRel;
  }
  
  /**
   * Get the mean and sigma values of PSV for a range of periods between 0.1 and 2.5 seconds
   */
  private void getPsvAtPeriods() {
	  if (D) System.out.println("Getting PSV mean and sigma at various periods");
	//loop over the periods and calculate mean and sigma SA then convert to PSV 
	  for (int i=0; i<numIntPoints;i++) {
		  ti[i]=Math.exp( Math.log(0.1) + Math.log(2.5/0.1)*((double)i/((double)numIntPoints-1)));
		  origPeriodParam.setValue(ti[i]);
		  meanLnSa[i] = attenRelToWrap.getMean();
		  sigmaLnSa[i] = attenRelToWrap.getStdDev();
		  //convert to non-log SA form
		  double meanSa = Math.exp(meanLnSa[i] + 0.5*sigmaLnSa[i]*sigmaLnSa[i]);
		  double sigmaSa = meanSa*Math.sqrt(Math.exp(sigmaLnSa[i]*sigmaLnSa[i]) - 1.0);
		  //convert to PSV
		  double omegai = 2*Math.PI/ti[i];
		  meanPsv[i] = meanSa/omegai*accGravity;
		  sigmaPsv[i] = sigmaSa/omegai*accGravity;
	  }  
  }
  
  /**
   * Get the median and sigma values of lnSI
   */
  private void getMeanSigmaLnSi() {
	  if (D) System.out.println("Getting mean and sigma of ln SI");
	
	  //set these two SA's in the IMCorrRel
	  corr.setIntensityMeasurei(SA_InterpolatedParam.NAME);
	  corr.setIntensityMeasurej(SA_InterpolatedParam.NAME);
	  
	  //loop over the periods to incrementally get SI 
	  double meanSi = 0.0; double varSi = 0.0;
	  for (int i=0; i<numIntPoints;i++) {
		  //calculate integration weights
		  if (i==0) 
			  intWeight[0] = (ti[1]-ti[0])/2.0;
		  else if (i==numIntPoints-1) 
			  intWeight[numIntPoints-1] = (ti[numIntPoints-1]-ti[numIntPoints-2])/2.0;
		  else 
		  	  intWeight[i] = (ti[i+1]-ti[i-1])/2.0;	
		  
		  //calculate increment of mean SI
		  meanSi += intWeight[i]*meanPsv[i];
		  //calculate increment of variance of SI due to PSV variance
		  varSi += Math.pow(intWeight[i]*sigmaPsv[i],2);
		  //sum over the periods to account for PSV covariance 
		  
		  Parameter newIMTi = (Parameter) corr.getIntensityMeasurei();
		  ((SA_InterpolatedParam)newIMTi).getPeriodInterpolatedParam().setValue(ti[i]);  
		  
		  for (int j=0; j<i;j++) {
			  //calculate correlation between log SA at two periods
			  Parameter<?> newIMTj = (Parameter<?>) corr.getIntensityMeasurej();
				((SA_InterpolatedParam)newIMTj).getPeriodInterpolatedParam().setValue(ti[j]);
				
			  double rhoLnSa = corr.getImCorrelation();
			  //convert to correlation between non-log SA
			  double rhoSa = (Math.exp(rhoLnSa*sigmaLnSa[i]*sigmaLnSa[j]) - 1.0)/ 
			  		Math.sqrt((Math.exp(sigmaLnSa[i]*sigmaLnSa[i]) - 1.0)*(Math.exp(sigmaLnSa[j]*sigmaLnSa[j]) - 1.0));
			  //compute PSV covariance contribution to varSi
			  double rhoPsv = rhoSa;
			  varSi += 2*rhoPsv*intWeight[i]*intWeight[j]*sigmaPsv[i]*sigmaPsv[j];
		  }
	  }
	  //now that mean and variance of SI have been obtained get the mean and sigma of lnSI
	  meanLnSi = Math.log( (meanSi*meanSi) / Math.sqrt(varSi + meanSi*meanSi));
	  sigmaLnSi = Math.sqrt(Math.log( varSi/(meanSi*meanSi) + 1.0 ));
  }

  
  /**
   * Allows the user to set the default parameter values for the selected Attenuation
   * Relationship.
   */
  public void setParamDefaults() {
	  attenRelToWrap.setParamDefaults();
	  siParam.setValueAsDefault();
  }

  /**
   * This sets the lists of independent parameters that the various dependent
   * parameters (mean, standard deviation, exceedance probability, and IML at
   * exceedance probability) depend upon. NOTE: these lists do not include anything
   * about the intensity-measure parameters or any of their internal
   * independentParamaters.
   */
  protected void initIndependentParamLists() {
    meanIndependentParams = attenRelToWrap.getMeanIndependentParams();
    stdDevIndependentParams = attenRelToWrap.getStdDevIndependentParams();
    //because of the SI formulation on Sa, stdDev depends on the meanIndependentParams also
    stdDevIndependentParams.addParameterList(meanIndependentParams);
    exceedProbIndependentParams = attenRelToWrap.getExceedProbIndependentParams();
    imlAtExceedProbIndependentParams = attenRelToWrap.getIML_AtExceedProbIndependentParams();
  }




  /**
   *  Creates the supported IM parameter (siParam), and adds
   *  this to the supportedIMParams list. Makes the parameters noneditable.
   */
  protected void initSupportedIntensityMeasureParams() {

	  origPeriodParam = (PeriodInterpolatedParam)attenRelToWrap.getParameter(PeriodInterpolatedParam.NAME);
 
	  siParam = new SI_Param();
	  siParam.setNonEditable();

	  // Add the warning listeners:
	  siParam.addParameterChangeWarningListener(warningListener);

	  // Put parameters in the supportedIMParams list:
	  supportedIMParams.clear();
	  supportedIMParams.addParameter(siParam);

  }
  
  protected void initSiteParams() {}
  protected void initEqkRuptureParams() {}
  protected void initPropagationEffectParams() {}

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


  /**
   * This listens for parameter changes and updates the primitive parameters accordingly
   * @param e ParameterChangeEvent
   */
  public void parameterChange(ParameterChangeEvent e) {
    String pName = e.getParameterName();
    Object val = e.getNewValue();
    parameterChange = true;
    if (D) System.out.println(pName+" value changed to "+val);
  }

  /**
   * Allows to reset the change listeners on the parameters
   */
  public void resetParameterEventListeners(){
	  this.attenRelToWrap.resetParameterEventListeners();
	  this.initParameterEventListeners();
  }

  /**
   * Adds the parameter change listeners. This allows to listen to when-ever the
   * parameter is changed.
   */
  protected void initParameterEventListeners() {
  }

  /**
   * This provides a URL where more info on this model can be obtained
   * @throws MalformedURLException if returned URL is not a valid URL.
   * @return the URL to the AttenuationRelationship document on the Web.
   */
  public URL getInfoURL() throws MalformedURLException{
	  return new URL(URL_INFO_STRING);
  }

  
}
