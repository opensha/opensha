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
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.Ds575_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.Ds595_Param;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupTopDepthParam;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

/**
 * <b>Title:</b> BommerEtAl_2009_AttenRel<p>
 *
 * <b>Description:</b> This implements the Attenuation Relationship published by Bommer et al.
 * (2009). "Empirical Equations for the Prediction of the Significant, Bracketed, and Uniform 
 * Duration of Earthquake Ground Motion", Bulletin of the Seismological Society of America,  
 * 99(6):3217-3233.
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
 * <LI>ztor - the depth (in km) to the top of the rupture plane
 * <LI>component - either the geometric mean or random horizontal
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


public class BommerEtAl_2009_AttenRel
    extends AttenuationRelationship implements
    ScalarIMR,
    Named, ParameterChangeListener {

  // Debugging stuff
  private final static String C = "BommerEtAl_2009_AttenRel";
  private final static boolean D = false;
  public final static String SHORT_NAME = "BommerEtAl2009";
  private static final long serialVersionUID = 1234567890987654358L;


  // Name of IMR
  public final static String NAME = "Bommer et al. (2009)";
  
  // coefficients:
  //Note that index 0 is for Ds575 and index 1 for Ds595 (both "acceleration-based" definitions)
  double[] c0 ={       -5.6298, -2.2393};
  double[] m1 ={        1.2619,  0.9368};
  double[] r1 ={        2.0063,  1.5686};
  double[] r2 ={        -0.252, -0.1953};
  double[] h1 ={        2.3316,     2.5};
  double[] v1 ={         -0.29, -0.3478};
  double[] z1 ={       -0.0522, -0.0365};
  double[] tau ={       0.3527,  0.3252};
  double[] sigma ={     0.4304,   0.346};
  double[] sigmaC ={    0.1729,  0.1114};
  double[] sigmaT_arb ={0.5564,  0.4748};
  double[] sigmaT_gm ={ 0.5289,  0.4616};

  private int imIndex;
  private double vs30, rRup, mag, depthTop;
  private String stdDevType;
  private Component component;
  private boolean parameterChange;
  
  //private PropagationEffect propagationEffect;

  // values for warning parameters
  protected final static Double MAG_WARN_MIN = new Double(4.8);
  protected final static Double MAG_WARN_MAX = new Double(7.9);
  protected final static Double DISTANCE_RUP_WARN_MIN = new Double(0.0);
  protected final static Double DISTANCE_RUP_WARN_MAX = new Double(100.0);
  protected final static Double VS30_WARN_MIN = new Double(150.0);
  protected final static Double VS30_WARN_MAX = new Double(1500.0);
  protected final static Double RUP_TOP_WARN_MIN = new Double(0);
  protected final static Double RUP_TOP_WARN_MAX = new Double(15);

  // for issuing warnings:
  private transient ParameterChangeWarningListener warningListener = null;

  /**
   *  This initializes several ParameterList objects.
   */
  public BommerEtAl_2009_AttenRel(ParameterChangeWarningListener warningListener) {

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
	  rupTopDepthParam.setValueIgnoreWarning(eqkRupture.getRuptureSurface().getAveRupTopDepth());
	  
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
	  
	  double mean = getMean(imIndex, vs30, rRup, mag, depthTop); 
	  return mean;
	  
  }



  /**
   * @return    The stdDev value
   */
  public double getStdDev() {
	  if (intensityMeasureChanged) {
		  setCoeffIndex();  // intensityMeasureChanged is set to false in this method
	  }
	  
	  
	  component = componentParam.getValue();
	  
	  double stdDev = getStdDev(imIndex, stdDevType, component); 
	  return stdDev;
  }

  /**
   * Allows the user to set the default parameter values for the selected Attenuation
   * Relationship.
   */
  public void setParamDefaults() {

    vs30Param.setValueAsDefault();
    magParam.setValueAsDefault();
    rupTopDepthParam.setValueAsDefault();
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
    meanIndependentParams.addParameter(rupTopDepthParam);
    meanIndependentParams.addParameter(componentParam);
    

    // params that the stdDev depends upon
    stdDevIndependentParams.clear();
    stdDevIndependentParams.addParameter(stdDevTypeParam);
    stdDevIndependentParams.addParameter(componentParam);

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
	rupTopDepthParam = new RupTopDepthParam(RUP_TOP_WARN_MIN, RUP_TOP_WARN_MAX);
    
    eqkRuptureParams.clear();
    eqkRuptureParams.addParameter(magParam);
    eqkRuptureParams.addParameter(rupTopDepthParam);
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
    componentParam = new ComponentParam(Component.AVE_HORZ, Component.AVE_HORZ, Component.RANDOM_HORZ);

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
   * @param depthTop
   * @return
   */
  public double getMean(int imIndex, double vs30, double rRup,double mag, double depthTop) { 


	  double lnDs = c0[imIndex] + m1[imIndex]*mag + 
	  (r1[imIndex]+r2[imIndex]*mag)*Math.log(Math.sqrt(rRup*rRup+Math.pow(h1[imIndex],2))) 
	  + v1[imIndex]*Math.log(vs30) + z1[imIndex]*depthTop;
	  
	  return lnDs;

  }

 /**
  * 
  * @param imIndex
  * @param stdDevType
  * @param component
  * @return
  */
 public double getStdDev(int imIndex, String stdDevType, Component component) {

	  if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_NONE))
		  return 0.0;
	  else {
		  if(stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_TOTAL))
			  if (component == Component.AVE_HORZ)
				  return sigmaT_gm[imIndex];
			  else
				  return sigmaT_arb[imIndex];
		  else if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_NONE))
			  return 0;
		  else if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_INTER))
			  return tau[imIndex];
		  else if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_INTRA))
			  if (component == Component.AVE_HORZ)
				  return sigma[imIndex];
			  else
				  return Math.sqrt(Math.pow(sigma[imIndex],2)+Math.pow(sigmaC[imIndex],2));
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
	  else if (pName.equals(RupTopDepthParam.NAME)) {
		  depthTop = ( (Double) val).doubleValue();
	  }
	  else if (pName.equals(StdDevTypeParam.NAME)) {
		  stdDevType = (String) val;
	  }
	  else if (pName.equals(ComponentParam.NAME)) {
		  component = componentParam.getValue();
	  }
  }

  /**
   * Allows to reset the change listeners on the parameters
   */
  public void resetParameterEventListeners(){
    distanceRupParam.removeParameterChangeListener(this);
    vs30Param.removeParameterChangeListener(this);
    magParam.removeParameterChangeListener(this);
    rupTopDepthParam.removeParameterChangeListener(this);
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
    rupTopDepthParam.addParameterChangeListener(this);
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
