package org.opensha.sha.gcim.imr.attenRelImpl.ASI_WrapperAttenRel;

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
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.ASI_Param;
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
 * <b>Title:</b> ASI_AttenRelWrapper<p>
 *
 * <b>Description:</b> 
 * Provides the wrapper that allows all ground motion prediction equations for Sa to be used to compute
 * ASI, Acceleration Specturm Intensity
 * 
 * Reference:
 * Bradley, B.A., 2009. Site specific and spatially distributed prediction of acceleration spectrum 
 * intensity, Bulletin of the Seismological Society of America,  100 (2): 792-801.
 * 
 * Supported Intensity-Measure Parameters:<p>
 * <UL>
 * <LI>asiParam - AccelerationSpectrum intensity
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

public class ASI_AttenRelWrapper
    extends AttenuationRelationship implements
    ScalarIMR,
    Named, ParameterChangeListener {

  // Debugging stuff
  private final static String C = "ASI_AttenRelWrapper";
  private final static boolean D = false;
  public final static String SHORT_NAME = "ASI";
  private static final long serialVersionUID = 1234567890987654353L; 
  private AttenuationRelationship attenRelToWrap; 
  PeriodInterpolatedParam origPeriodParam; 
  private ASI_Param asiParam; 
  private ImCorrelationRelationship corr;
  double[] ti, intWeight, meanLnSa, sigmaLnSa, meanSa, sigmaSa;
  double meanLnAsi, sigmaLnAsi;
  int numIntPoints=9; 

  // Name of IMR
  public final static String NAME = "ASI Atten Rel Wrapper";
  
  // URL Info String
  private final static String URL_INFO_STRING = null;

  private boolean parameterChange;

  // for issuing warnings:
  private transient ParameterChangeWarningListener warningListener = null;
  
  
  // No arg constructor
  public ASI_AttenRelWrapper() {
  }
  
  
  /**
   *  This initializes several ParameterList objects.
   */
  public ASI_AttenRelWrapper(ParameterChangeWarningListener warningListener, 
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
	meanSa = new double[numIntPoints];
	sigmaSa = new double[numIntPoints];
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
		  getSaAtPeriods();
		  getMeanSigmaLnAsi();
		  parameterChange = false;
	  }
	  return (meanLnAsi);
}

  /**
   * @return    The stdDev value
   */
  public double getStdDev() {
	  if(parameterChange) {
		  getSaAtPeriods();
		  getMeanSigmaLnAsi();
		  parameterChange = false;
	  }
	  return (sigmaLnAsi);
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
   * Get the mean and sigma values of PSV for a range of periods between 0.1 and 0.5 seconds
   */
  private void getSaAtPeriods() {
	  if (D) System.out.println("Getting Sa mean and sigma at various periods");
	//loop over the periods and calculate mean and sigma SA then convert to PSV 
	  for (int i=0; i<numIntPoints;i++) {
		  ti[i]=Math.exp( Math.log(0.1) + Math.log(0.5/0.1)*((double)i/((double)numIntPoints-1)));
		  origPeriodParam.setValue(ti[i]);
		  meanLnSa[i] = attenRelToWrap.getMean();
		  sigmaLnSa[i] = attenRelToWrap.getStdDev();
		  //convert to non-log SA form
		  meanSa[i] = Math.exp(meanLnSa[i] + 0.5*sigmaLnSa[i]*sigmaLnSa[i]);
		  sigmaSa[i] = meanSa[i]*Math.sqrt(Math.exp(sigmaLnSa[i]*sigmaLnSa[i]) - 1.0);  
	  }  
	  
//	  if(D) {
//		  System.out.println("Ti meanlnSa sigmaLnSa");
//		  for (int i=0; i<numIntPoints;i++) {
//			  System.out.println(ti[i] + " " + Math.exp(meanLnSa[i]) + " " + sigmaLnSa[i]);
//		  }
//		  System.out.println("Ti meanSa sigmaSa");
//		  for (int i=0; i<numIntPoints;i++) {
//			  System.out.println(ti[i] + " " + meanSa[i] + " " + sigmaSa[i]);
//		  }
//	  }
	  
  }
  
  /**
   * Get the median and sigma values of lnASI
   */
  private void getMeanSigmaLnAsi() {
	  if (D) System.out.println("Getting mean and sigma of ln ASI");
	
	  //set these two SA's in the IMCorrRel
	  corr.setIntensityMeasurei(SA_InterpolatedParam.NAME);
	  corr.setIntensityMeasurej(SA_InterpolatedParam.NAME);
	  
	  //loop over the periods to incrementally get ASI 
	  double meanAsi = 0.0; double varAsi = 0.0;
	  for (int i=0; i<numIntPoints;i++) {
		  //calculate integration weights
		  if (i==0) 
			  intWeight[0] = (ti[1]-ti[0])/2.0;
		  else if (i==numIntPoints-1) 
			  intWeight[numIntPoints-1] = (ti[numIntPoints-1]-ti[numIntPoints-2])/2.0;
		  else 
		  	  intWeight[i] = (ti[i+1]-ti[i-1])/2.0;	
		  
		  //calculate increment of mean ASI
		  meanAsi += intWeight[i]*meanSa[i];
//		  if(D) System.out.println(ti[i] + " " + intWeight[i] + " " + meanAsi);
		  //calculate increment of variance of ASI due to Sa variance
		  varAsi += Math.pow(intWeight[i]*sigmaSa[i],2);
		  //sum over the periods to account for Sa covariance
		  

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
			  //compute Sa covariance contribution to varSi
			  varAsi += 2*rhoSa*intWeight[i]*intWeight[j]*sigmaSa[i]*sigmaSa[j];
		  }
	  }
	  
	  
	  //now that mean and variance of ASI have been obtained get the mean and sigma of lnASI
	  meanLnAsi = Math.log( (meanAsi*meanAsi) / Math.sqrt(varAsi + meanAsi*meanAsi));
	  sigmaLnAsi = Math.sqrt(Math.log( varAsi/(meanAsi*meanAsi) + 1.0 ));
  }

  
  /**
   * Allows the user to set the default parameter values for the selected Attenuation
   * Relationship.
   */
  public void setParamDefaults() {
	  attenRelToWrap.setParamDefaults();
	  asiParam.setValueAsDefault();
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
    //because of the ASI formulation on Sa, stdDev depends on the meanIndependentParams also
    stdDevIndependentParams.addParameterList(meanIndependentParams);
    exceedProbIndependentParams = attenRelToWrap.getExceedProbIndependentParams();
    imlAtExceedProbIndependentParams = attenRelToWrap.getIML_AtExceedProbIndependentParams();
  }


  /**
   *  Creates the supported IM parameter (asiParam), and adds
   *  this to the supportedIMParams list. Makes the parameters noneditable.
   */
  protected void initSupportedIntensityMeasureParams() {

	  origPeriodParam = (PeriodInterpolatedParam)attenRelToWrap.getParameter(PeriodInterpolatedParam.NAME);
 
	  asiParam = new ASI_Param();
	  asiParam.setNonEditable();

	  // Add the warning listeners:
	  asiParam.addParameterChangeWarningListener(warningListener);

	  // Put parameters in the supportedIMParams list:
	  supportedIMParams.clear();
	  supportedIMParams.addParameter(asiParam);

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
