package org.opensha.sha.gcim.imr.attenRelImpl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

import org.opensha.commons.data.Named;
import org.opensha.commons.data.Site;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.Ds575_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.Ds595_Param;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

/**
 * <b>Title:</b> KS_2006_AttenRel<p>
 *
 * <b>Description:</b> This implements the Attenuation Relationship published by Kempton and Stewart
 * (2006, "Prediction equations for significant duration of earthquake ground motions considering
 *  site and near-source effects", Earthquake Spectra,  22(4):985-1013.
 *  
 *  The "base" model is given here (i.e. their Equation 13 and Table 6)
 *	At this point (v1.0) only the "acceleration-based" durations are coded
 * 
 * Supported Intensity-Measure Parameters:<p>
 * <UL>
 * <LI>Ds575Param - Significant duration over which 5% to 75% of the arias intensity is developed 
 * <LI>Ds595Param - Significant duration over which 5% to 95% of the arias intensity is developed
 * </UL><p>
 * Other Independent Parameters:<p>
 * <UL>
 * <LI>magParam - moment Magnitude
 * <LI>distanceRupParam - closest distance to fault surface  
 * <LI>vs30Param - 30-meter shear wave velocity
 * <LI>stdDevTypeParam - The type of standard deviation
 * </UL></p>
 * 
 *<p>
 *
 * Verification - Verified against the Matlab implementation of Bradley and also with the figures in
 * the above paper, but yet to do Junit tests  - //TODO
 *</p>
 *
 *
 * @author     Brendon Bradley
 * @created    Oct., 2010
 * @version    1.0
 */


public class KS_2006_AttenRel
    extends AttenuationRelationship implements
    ScalarIMR,
    Named, ParameterChangeListener {

  // Debugging stuff
  private final static String C = "KS_2006_AttenRel";
  private final static boolean D = false;
  public final static String SHORT_NAME = "KS2006";
  private static final long serialVersionUID = 1234567890987654358L;


  // Name of IMR
  public final static String NAME = "Kempton & Stewart (2006)";
  
//coefficients:
  //Note that index 0 is for Ds575 and index 1 for Ds595 (both "acceleration-based" definitions)
  // index 2 is for Ddv575 and index 3 for Dsv595 ("velocity-based" definitions not implemented in v1.0)
  double beta = 3.2;
  double Mstar = 6;
  double[] b1 ={   6.02,    2.79, 5.46,       1.53};
  double[] b2 ={      0,    0.82,    0,       1.34};
  double[] c2 ={   0.07,    0.15,  0.1,       0.15};
  double[] c4 ={   0.82,    3.00,  1.4,       3.99};
  double[] c5 ={-0.0013, -0.0041, -0.0022, -0.0062};
  double[] tau ={  0.32,    0.26,    0.45,    0.31};
  double[] sigma ={0.42,    0.36,    0.51,    0.39};
  double[] sigmaT ={0.53,   0.44,    0.68,    0.50};

  private int imIndex;
  private double vs30, rRup, mag; 
  private String stdDevType; 
  private boolean parameterChange;
  
  //private PropagationEffect propagationEffect;

  // values for warning parameters
  protected final static Double MAG_WARN_MIN = new Double(5.0);
  protected final static Double MAG_WARN_MAX = new Double(7.6);
  protected final static Double DISTANCE_RUP_WARN_MIN = new Double(0.0);
  protected final static Double DISTANCE_RUP_WARN_MAX = new Double(200.0);
  protected final static Double VS30_WARN_MIN = new Double(150.0);
  protected final static Double VS30_WARN_MAX = new Double(1500.0);

  // for issuing warnings:
  private transient ParameterChangeWarningListener warningListener = null;

  /**
   *  This initializes several ParameterList objects.
   */
  public KS_2006_AttenRel(ParameterChangeWarningListener warningListener) {

    super();
    
    this.warningListener = warningListener;
    initSupportedIntensityMeasureParams();

    initEqkRuptureParams();
    initPropagationEffectParams();
    initSiteParams();
    initOtherParams();

    initIndependentParamLists(); // This must be called after the above
    initParameterEventListeners(); //add the change listeners to the parameters
  }
  
  
  /**
   *  This sets the eqkRupture related parameters (magParam)
   *   based on the eqkRupture passed in.
   *  The internally held eqkRupture object is also set as that
   *  passed in.  Warning constraints are ingored.
   *
   * @param  eqkRupture  The new eqkRupture value
   * @throws InvalidRangeException thrown if rake is out of bounds
   */
  public void setEqkRupture(EqkRupture eqkRupture) throws InvalidRangeException {
	  
	  magParam.setValueIgnoreWarning(new Double(eqkRupture.getMag()));
	  
	  this.eqkRupture = eqkRupture;
	  setPropagationEffectParams();
	  
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

    vs30Param.setValue((Double)site.getParameter(Vs30_Param.NAME).getValue());

    this.site = site;
    setPropagationEffectParams();

  }

  /**
   * This sets the propagation-effect parameter (distanceRupParam)
   *  based on the current site and eqkRupture.  
   */
  protected void setPropagationEffectParams() {

    if ( (this.site != null) && (this.eqkRupture != null)) {
    	distanceRupParam.setValue(eqkRupture, site);
    }
  }

  /**
   * This function returns the array index for the coeffs corresponding to the chosen IMT
   */
  protected void setCoeffIndex() throws ParameterException {

    // Check that parameter exists
    if (im == null) {
      throw new ParameterException(C +
                                   ": updateCoefficients(): " +
                                   "The Intensity Measusre Parameter has not been set yet, unable to process."
          );
    }

    if (im.getName().equalsIgnoreCase(Ds575_Param.NAME)) {
        imIndex = 0;
      }
      else if (im.getName().equalsIgnoreCase(Ds595_Param.NAME)) {
        imIndex = 1;
      }
    
    parameterChange = true;
    intensityMeasureChanged = false;

  }

  /**
   * Calculates the mean of the exceedence probability distribution. <p>
   * @return    The mean value
   */
  public double getMean() {
	  
	  
	  // check if distance is beyond the user specified max
	  if (rRup > USER_MAX_DISTANCE) {
		  return VERY_SMALL_MEAN;
	  }
	  
	  if (intensityMeasureChanged) {
		  setCoeffIndex();  // intensityMeasureChanged is set to false in this method
	  }
	  
	  double mean = getMean(imIndex, vs30, rRup, mag); 
	  return mean;
	  
  }



  /**
   * @return    The stdDev value
   */
  public double getStdDev() {
	  if (intensityMeasureChanged) {
		  setCoeffIndex();  // intensityMeasureChanged is set to false in this method
	  }
	  
	  double stdDev = getStdDev(imIndex, stdDevType); 
	  return stdDev;
  }

  /**
   * Allows the user to set the default parameter values for the selected Attenuation
   * Relationship.
   */
  public void setParamDefaults() {

    vs30Param.setValueAsDefault();
    magParam.setValueAsDefault();
    distanceRupParam.setValueAsDefault();
    ds575Param.setValueAsDefault();
    ds595Param.setValueAsDefault();
    componentParam.setValueAsDefault();
    stdDevTypeParam.setValueAsDefault(); 
    vs30 = ( (Double) vs30Param.getValue()).doubleValue(); 
    mag = ( (Double) magParam.getValue()).doubleValue();
    stdDevType = (String) stdDevTypeParam.getValue();
    
  }

  /**
   * This creates the lists of independent parameters that the various dependent
   * parameters (mean, standard deviation, exceedance probability, and IML at
   * exceedance probability) depend upon. NOTE: these lists do not include anything
   * about the intensity-measure parameters or any of thier internal
   * independentParamaters.
   */
  protected void initIndependentParamLists() {

    // params that the mean depends upon
    meanIndependentParams.clear();
    meanIndependentParams.addParameter(distanceRupParam);
    meanIndependentParams.addParameter(vs30Param);
    meanIndependentParams.addParameter(magParam);
    meanIndependentParams.addParameter(componentParam);
    

    // params that the stdDev depends upon
    stdDevIndependentParams.clear();
    stdDevIndependentParams.addParameter(stdDevTypeParam);

    // params that the exceed. prob. depends upon
    exceedProbIndependentParams.clear();
    exceedProbIndependentParams.addParameterList(meanIndependentParams);
    exceedProbIndependentParams.addParameter(stdDevTypeParam);
    exceedProbIndependentParams.addParameter(sigmaTruncTypeParam);
    exceedProbIndependentParams.addParameter(sigmaTruncLevelParam);

    // params that the IML at exceed. prob. depends upon
    imlAtExceedProbIndependentParams.addParameterList(
        exceedProbIndependentParams);
    imlAtExceedProbIndependentParams.addParameter(exceedProbParam);
  }

  /**
   *  Creates the Site-Type parameter and adds it to the siteParams list.
   *  Makes the parameters noneditable.
   */
  protected void initSiteParams() {

	vs30Param = new Vs30_Param(VS30_WARN_MIN, VS30_WARN_MAX);

    siteParams.clear();
    siteParams.addParameter(vs30Param);

  }

  /**
   *  Creates the two Potential Earthquake parameters (magParam and
   *  fltTypeParam) and adds them to the eqkRuptureParams
   *  list. Makes the parameters noneditable.
   */
  protected void initEqkRuptureParams() {

	magParam = new MagParam(MAG_WARN_MIN, MAG_WARN_MAX);
    
    eqkRuptureParams.clear();
    eqkRuptureParams.addParameter(magParam);
  }

  /**
   *  Creates the Propagation Effect parameters and adds them to the
   *  propagationEffectParams list. Makes the parameters noneditable.
   */
  protected void initPropagationEffectParams() {

    distanceRupParam = new DistanceRupParameter(0.0);
    DoubleConstraint warn = new DoubleConstraint(DISTANCE_RUP_WARN_MIN,
                                                 DISTANCE_RUP_WARN_MAX);
    warn.setNonEditable();
    distanceRupParam.setWarningConstraint(warn);
    distanceRupParam.addParameterChangeWarningListener(warningListener);

    distanceRupParam.setNonEditable();

    propagationEffectParams.addParameter(distanceRupParam);

  }

  /**
   *  Creates the two supported IM parameters (PGA and SA), as well as the
   *  independenParameters of SA (periodParam and dampingParam) and adds
   *  them to the supportedIMParams list. Makes the parameters noneditable.
   */
  protected void initSupportedIntensityMeasureParams() {

//  Create Ds575 Parameter (ds575Param):
	ds575Param = new Ds575_Param();
	ds575Param.setNonEditable();
	
//  Create Ds595 Parameter (ds595Param):
	ds595Param = new Ds595_Param();
	ds595Param.setNonEditable();

    // Add the warning listeners:
	ds575Param.addParameterChangeWarningListener(warningListener);
	ds595Param.addParameterChangeWarningListener(warningListener);

    // Put parameters in the supportedIMParams list:
    supportedIMParams.clear();
    supportedIMParams.addParameter(ds575Param);
    supportedIMParams.addParameter(ds595Param);
  }

  /**
   *  Creates other Parameters that the mean or stdDev depends upon,
   *  such as the Component or StdDevType parameters.
   */
  protected void initOtherParams() {

    // init other params defined in parent class
    super.initOtherParams();

    // the Component Parameter
    // first is default, the rest are all options (including default)
    componentParam = new ComponentParam(Component.AVE_HORZ, Component.AVE_HORZ);

    // the stdDevType Parameter
    StringConstraint stdDevTypeConstraint = new StringConstraint();
    stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_TOTAL);
    stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_NONE);
    stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_INTER);
    stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_INTRA);
    stdDevTypeConstraint.setNonEditable();
    stdDevTypeParam = new StdDevTypeParam(stdDevTypeConstraint);

    // add these to the list
    otherParams.addParameter(componentParam);
    otherParams.addParameter(stdDevTypeParam);  
    
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

  
  /**
   * 
   * @param imIndex
   * @param vs30
   * @param rRup
   * @param mag
   * @return
   */
  public double getMean(int imIndex, double vs30, double rRup,double mag) { //depthTop


	  double meanPart1 = Math.exp(b1[imIndex]+b2[imIndex]*(mag-Mstar)) / Math.pow(10.0,1.5*mag+16.05);
	  double meanPart2 = 4.9*Math.pow(10.,6.)*beta;
	  double meanPart3 = rRup*c2[imIndex]+(c4[imIndex]+c5[imIndex]*vs30);
	  double lnDs =  Math.log(Math.pow(meanPart1,-1./3.)/meanPart2 + meanPart3);
	  return lnDs;

  }

 /**
  * 
  * @param imIndex
  * @param stdDevType
  * @param component
  * @return
  */
 public double getStdDev(int imIndex, String stdDevType) { //, String component

	  if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_NONE))
		  return 0.0;
	  else {
		  if(stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_TOTAL))
				return sigmaT[imIndex];
			else if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_NONE))
				return 0;
			else if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_INTER))
				return tau[imIndex];
			else if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_INTRA))
				return sigma[imIndex];
			else 
				return Double.NaN; 
	  }
  }

  /**
   * This listens for parameter changes and updates the primitive parameters accordingly
   * @param e ParameterChangeEvent
   */
  public void parameterChange(ParameterChangeEvent e) {
	  
	  String pName = e.getParameterName();
	  Object val = e.getNewValue();
	  parameterChange = true;
	  if (pName.equals(DistanceRupParameter.NAME)) {
		  rRup = ( (Double) val).doubleValue();
	  }
	  else if (pName.equals(Vs30_Param.NAME)) {
		  vs30 = ( (Double) val).doubleValue();
	  }
	  else if (pName.equals(magParam.NAME)) {
		  mag = ( (Double) val).doubleValue();
	  }
	  else if (pName.equals(StdDevTypeParam.NAME)) {
		  stdDevType = (String) val;
	  }
  }

  /**
   * Allows to reset the change listeners on the parameters
   */
  public void resetParameterEventListeners(){
    distanceRupParam.removeParameterChangeListener(this);
    vs30Param.removeParameterChangeListener(this);
    magParam.removeParameterChangeListener(this);
    stdDevTypeParam.removeParameterChangeListener(this);

    this.initParameterEventListeners();
  }

  /**
   * Adds the parameter change listeners. This allows to listen to when-ever the
   * parameter is changed.
   */
  protected void initParameterEventListeners() {

    distanceRupParam.addParameterChangeListener(this);
    vs30Param.addParameterChangeListener(this);
    magParam.addParameterChangeListener(this);
    stdDevTypeParam.addParameterChangeListener(this);
  }

  
  /**
   * This provides a URL where more info on this model can be obtained
   * @throws MalformedURLException if returned URL is not a valid URL.
   * @return the URL to the AttenuationRelationship document on the Web.
   */
//  public URL getInfoURL() throws MalformedURLException{
//	  return new URL("http://www.opensha.org/documentation/modelsImplemented/attenRel/CB_2008.html");
//  }

  
}
