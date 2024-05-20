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
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.CAV_Param;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.EqkRuptureParams.DipParam;
import org.opensha.sha.imr.param.EqkRuptureParams.FaultTypeParam;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupTopDepthParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGD_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistRupMinusJB_OverRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

/**
 * <b>Title:</b> CB_2010_CAV_AttenRel<p>
 *
 * <b>Description:</b> This implements the Attenuation Relationship published by Campbell & Bozorgnia 
 * (2010, "A Ground Motion Prediction Equation for the Horizontal Component of Cumulative Absolute 
 * Velocity (CAV) Based on the PEER-NGA Strong Motion Database", 
 * Earthquake Spectra, Volume 26, Number 3, pp. 635-650) <p>
 *
 * Supported Intensity-Measure Parameters:<p>
 * <UL>
 * <LI>cavParam - Cumulative Absolute Velocity
 * </UL><p>
 * Other Independent Parameters:<p>
 * <UL>
 * <LI>magParam - moment Magnitude
 * <LI>fltTypeParam - Style of faulting
 * <LI>rupTopDepthParam - depth to top of rupture
 * <LI>dipParam - rupture surface dip
 * <LI>distanceRupParam - closest distance to surface projection of fault
 * <li>distRupMinusJB_OverRupParam - used as a proxy for hanging wall effect
 * <LI>vs30Param - average Vs over top 30 meters
 * <li>depthTo2pt5kmPerSecParam - depth to where Vs30 equals 2.5 km/sec
 * <LI>componentParam - Component of shaking
 * <LI>stdDevTypeParam - The type of standard deviation
 * </UL></p>
 * <p>
 * NOTES: distRupMinusJB_OverRupParam is used rather than distancJBParameter because the latter 
 * should not be held constant when distanceRupParameter is changed (e.g., in the 
 * AttenuationRelationshipApplet).  This includes the stipulation that the mean of 0.2-sec  (or less)  SA 
 * should not be less than that of PGA (the latter being given if so). If depthTo2pt5kmPerSec is null 
 * (unknown), it is set as 2 km if vs30 <= 2500, and zero otherwise.
 * <p>
 * Verification - Verified against the Matlab implementation of Bradley and also with the figures in
 * the above paper, but yet to do Juni tests  - //TODO
 * </p>
 *
 * @author     Brendon Bradley
 * @created    Oct., 2010
 * @version    1.0
 */


public class CB_2010_CAV_AttenRel
    extends AttenuationRelationship implements
    ScalarIMR,
    Named, ParameterChangeListener {

  // Debugging stuff
  private final static String C = "CB_2010_CAV_AttenRel";
  private final static boolean D = false;
  public final static String SHORT_NAME = "CB2010";
  private static final long serialVersionUID = 1234567890987654358L;


  // Name of IMR
  public final static String NAME = "Campbell & Bozorgnia (2010)";
  private final static String CB_2008_CoeffFile = "campbell_2008_coeff.txt"; //TODO just add values herein
  
  double[] c0,c1,c2,c3,c4,c5,c6,c7,c8,c9,c10,c11,c12,k1,k2,k3,s_lny,t_lny,s_c,rho;
  
  double s_lnAF = 0.3;
  double n = 1.18;
  double c = 1.88;

  
   private HashMap indexFromPerHashMap;

  private double vs30, rJB, rRup, distRupMinusJB_OverRup, f_rv, f_nm, mag, depthTop, depthTo2pt5kmPerSec,dip;
  private String stdDevType;
  private Component component;
  private boolean magSaturation;
  private boolean parameterChange;
  
  //private PropagationEffect propagationEffect;

  // values for warning parameters
  protected final static Double MAG_WARN_MIN = Double.valueOf(4.0);
  protected final static Double MAG_WARN_MAX = Double.valueOf(8.5);
  protected final static Double DISTANCE_RUP_WARN_MIN = Double.valueOf(0.0);
  protected final static Double DISTANCE_RUP_WARN_MAX = Double.valueOf(200.0);
  protected final static Double DISTANCE_MINUS_WARN_MIN = Double.valueOf(0.0);
  protected final static Double DISTANCE_MINUS_WARN_MAX = Double.valueOf(50.0);
  protected final static Double VS30_WARN_MIN = Double.valueOf(150.0);
  protected final static Double VS30_WARN_MAX = Double.valueOf(1500.0);
  protected final static Double DEPTH_2pt5_WARN_MIN = Double.valueOf(0);
  protected final static Double DEPTH_2pt5_WARN_MAX = Double.valueOf(10);
  protected final static Double DIP_WARN_MIN = Double.valueOf(15);
  protected final static Double DIP_WARN_MAX = Double.valueOf(90);
  protected final static Double RUP_TOP_WARN_MIN = Double.valueOf(0);
  protected final static Double RUP_TOP_WARN_MAX = Double.valueOf(15);
  
  // style of faulting options
  public final static String FLT_TYPE_STRIKE_SLIP = "Strike-Slip";
  public final static String FLT_TYPE_REVERSE = "Reverse";
  public final static String FLT_TYPE_NORMAL = "Normal";

  // for issuing warnings:
  private transient ParameterChangeWarningListener warningListener = null;

  /**
   *  This initializes several ParameterList objects.
   */
  public CB_2010_CAV_AttenRel(ParameterChangeWarningListener warningListener) {

    super();
    
    this.warningListener = warningListener;
    getCoeffs(); 
    initSupportedIntensityMeasureParams();

    initEqkRuptureParams();
    initPropagationEffectParams();
    initSiteParams();
    initOtherParams();

    initIndependentParamLists(); // This must be called after the above
    initParameterEventListeners(); //add the change listeners to the parameters
    
  }
  
  public void getCoeffs(){
		  			  //    CAV    PGA (rock)
		c0 = new double[] {-4.354, -1.715};
		c1 = new double[] { 0.942,  0.500};
		c2 = new double[] {-0.178, -0.530};
		c3 = new double[] {-0.346, -0.262};
		c4 = new double[] {-1.309, -2.118};
		c5 = new double[] { 0.087,  0.170};
		c6 = new double[] {  7.24,   5.60};
		c7 = new double[] { 0.111,  0.280};
		c8 = new double[] {-0.108, -0.120};
		c9 = new double[] { 0.362,  0.490};
		c10= new double[] { 2.549,  1.058};
		c11= new double[] { 0.090,  0.040};
		c12= new double[] { 1.277,  0.610};
		k1 = new double[] {  400.,   865.};
		k2 = new double[] {-2.690, -1.186};
		k3 = new double[] {   1.0,  1.839};
		s_lny=new double[] {0.371,  0.478};
		t_lny=new double[] {0.196,  0.219};
		s_c= new double[] { 0.089,  0.166};
		rho= new double[] { 0.735,   1.00};
  }

  
  /**
   *  This sets the eqkRupture related parameters (magParam
   *  and fltTypeParam) based on the eqkRupture passed in.
   *  The internally held eqkRupture object is also set as that
   *  passed in.  Warning constraints are ingored.
   *
   * @param  eqkRupture  The new eqkRupture value
   * @throws InvalidRangeException thrown if rake is out of bounds
   */
  public void setEqkRupture(EqkRupture eqkRupture) throws InvalidRangeException {
	  
	  magParam.setValueIgnoreWarning(Double.valueOf(eqkRupture.getMag()));
	  
	  double rake = eqkRupture.getAveRake();
	  if(rake >30 && rake <150) {
		  fltTypeParam.setValue(FLT_TYPE_REVERSE);
	  }
	  else if(rake >-150 && rake<-30) {
		  fltTypeParam.setValue(FLT_TYPE_NORMAL);
	  }
	  else { // strike slip
		  fltTypeParam.setValue(FLT_TYPE_STRIKE_SLIP);
	  }
	  
	  RuptureSurface surface = eqkRupture.getRuptureSurface();
	  rupTopDepthParam.setValueIgnoreWarning(surface.getAveRupTopDepth());
	  dipParam.setValueIgnoreWarning(surface.getAveDip());
	  
//	  setFaultTypeFromRake(eqkRupture.getAveRake());
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
    depthTo2pt5kmPerSecParam.setValueIgnoreWarning((Double)site.getParameter(DepthTo2pt5kmPerSecParam.NAME).
                                      getValue());
    this.site = site;
    setPropagationEffectParams();

  }

  /**
   * This sets the two propagation-effect parameters (distanceRupParam and
   * distRupMinusJB_OverRupParam) based on the current site and eqkRupture.  
   */
  protected void setPropagationEffectParams() {

    if ( (this.site != null) && (this.eqkRupture != null)) {
   
    	distanceRupParam.setValue(eqkRupture, site);
		double dist_jb = eqkRupture.getRuptureSurface().getDistanceJB(site.getLocation());
    	if(rRup == 0)
    		distRupMinusJB_OverRupParam.setValueIgnoreWarning(0.0);
    	else
    		distRupMinusJB_OverRupParam.setValueIgnoreWarning((rRup-dist_jb)/rRup);
    }
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
	  
	  // compute rJB
	  rJB = rRup - distRupMinusJB_OverRup*rRup;
	  
	  // set default value of basin depth based on the final value of vs30
	  // (must do this here because we get pga_rock below by passing in 1100 m/s)
	  if(Double.isNaN(depthTo2pt5kmPerSec)){
		  if(vs30 <= 2500)
			  depthTo2pt5kmPerSec = 2;
		  else
			  depthTo2pt5kmPerSec = 0;
	  }
	    
	  double pga_rock = Math.exp(getMean(1, 1100, rRup, rJB, f_rv, f_nm, mag, dip,
			  depthTop, depthTo2pt5kmPerSec, magSaturation, 0));
	  
	  double mean = getMean(0, vs30, rRup, rJB, f_rv, f_nm, mag, dip,
			  depthTop, depthTo2pt5kmPerSec, magSaturation, pga_rock);
	  
	  return mean;
  }


  /**
   * @return    The stdDev value
   */
  public double getStdDev() {
	  
	  // compute rJB
	  rJB = rRup - distRupMinusJB_OverRup*rRup;


	  // set default value of basin depth based on the final value of vs30
	  // (must do this here because we get pga_rock below by passing in 1100 m/s)
	  if(Double.isNaN(depthTo2pt5kmPerSec)){
		  if(vs30 <= 2500)
			  depthTo2pt5kmPerSec = 2;
		  else
			  depthTo2pt5kmPerSec = 0;
	  }

	  double pga_rock = Double.NaN;
	  if(vs30 < k1[0]) 
		  pga_rock = Math.exp(getMean(1, 1100, rRup, rJB, f_rv, f_nm, mag,dip,depthTop, depthTo2pt5kmPerSec, magSaturation, 0));
	  
	  component = componentParam.getValue();
	  
	  double stdDev = getStdDev(0, stdDevType, component, vs30, pga_rock);
	  
	  return stdDev;
  }

  /**
   * Allows the user to set the default parameter values for the selected Attenuation
   * Relationship.
   */
  public void setParamDefaults() {

    vs30Param.setValueAsDefault();
    magParam.setValueAsDefault();
    fltTypeParam.setValueAsDefault();
    rupTopDepthParam.setValueAsDefault();
    distanceRupParam.setValueAsDefault();
    distRupMinusJB_OverRupParam.setValueAsDefault();
    cavParam.setValueAsDefault();
    componentParam.setValueAsDefault();
    stdDevTypeParam.setValueAsDefault();
    depthTo2pt5kmPerSecParam.setValueAsDefault();
    dipParam.setValueAsDefault();    
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
    meanIndependentParams.addParameter(distRupMinusJB_OverRupParam);
    meanIndependentParams.addParameter(vs30Param);
    meanIndependentParams.addParameter(depthTo2pt5kmPerSecParam);
    meanIndependentParams.addParameter(magParam);
    meanIndependentParams.addParameter(fltTypeParam);
    meanIndependentParams.addParameter(rupTopDepthParam);
    meanIndependentParams.addParameter(dipParam);
    meanIndependentParams.addParameter(componentParam);
    

    // params that the stdDev depends upon
    stdDevIndependentParams.clear();
    stdDevIndependentParams.addParameterList(meanIndependentParams);
    stdDevIndependentParams.addParameter(stdDevTypeParam);

    // params that the exceed. prob. depends upon
    exceedProbIndependentParams.clear();
    exceedProbIndependentParams.addParameterList(stdDevIndependentParams);
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
	//depthTo2pt5kmPerSecParam = new DepthTo2pt5kmPerSecParam(DEPTH_2pt5_WARN_MIN, DEPTH_2pt5_WARN_MAX);
	depthTo2pt5kmPerSecParam = new DepthTo2pt5kmPerSecParam(DEPTH_2pt5_WARN_MIN, DEPTH_2pt5_WARN_MAX,true);

    siteParams.clear();
    siteParams.addParameter(vs30Param);
    siteParams.addParameter(depthTo2pt5kmPerSecParam);

  }

  /**
   *  Creates the two Potential Earthquake parameters (magParam and
   *  fltTypeParam) and adds them to the eqkRuptureParams
   *  list. Makes the parameters noneditable.
   */
  protected void initEqkRuptureParams() {

	magParam = new MagParam(MAG_WARN_MIN, MAG_WARN_MAX);
	dipParam = new DipParam(DIP_WARN_MIN,DIP_WARN_MAX);
	rupTopDepthParam = new RupTopDepthParam(RUP_TOP_WARN_MIN, RUP_TOP_WARN_MAX);
    
    StringConstraint constraint = new StringConstraint();
    constraint.addString(FLT_TYPE_STRIKE_SLIP);
    constraint.addString(FLT_TYPE_NORMAL);
    constraint.addString(FLT_TYPE_REVERSE);
    constraint.setNonEditable();
    fltTypeParam = new FaultTypeParam(constraint,FLT_TYPE_STRIKE_SLIP);

    eqkRuptureParams.clear();
    eqkRuptureParams.addParameter(magParam);
    eqkRuptureParams.addParameter(fltTypeParam);
    eqkRuptureParams.addParameter(dipParam);
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

    //create distRupMinusJB_OverRupParam
    distRupMinusJB_OverRupParam = new DistRupMinusJB_OverRupParameter(0.0);
    DoubleConstraint warnJB = new DoubleConstraint(DISTANCE_MINUS_WARN_MIN, DISTANCE_MINUS_WARN_MAX);
    distRupMinusJB_OverRupParam.addParameterChangeWarningListener(warningListener);
    warn.setNonEditable();
    distRupMinusJB_OverRupParam.setWarningConstraint(warnJB);
    distRupMinusJB_OverRupParam.setNonEditable();
    
    propagationEffectParams.addParameter(distanceRupParam);
    propagationEffectParams.addParameter(distRupMinusJB_OverRupParam);

  }

  /**
   *  Creates the two supported IM parameters (PGA and SA), as well as the
   *  independenParameters of SA (periodParam and dampingParam) and adds
   *  them to the supportedIMParams list. Makes the parameters noneditable.
   */
  protected void initSupportedIntensityMeasureParams() {

    // Create cavParam:
	cavParam = new CAV_Param();

    
    // Add the warning listeners:
    cavParam.addParameterChangeWarningListener(warningListener);
    
    // Put parameters in the supportedIMParams list:
    supportedIMParams.clear();
    supportedIMParams.addParameter(cavParam);
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
    componentParam = new ComponentParam(Component.GMRotI50, Component.GMRotI50, Component.RANDOM_HORZ);

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
   * @param iper
   * @param vs30
   * @param rRup
   * @param distJB
   * @param f_rv
   * @param f_nm
   * @param mag
   * @param depthTop
   * @param depthTo2pt5kmPerSec
   * @param magSaturation
   * @param pga_rock
   * @return
   */
  public double getMean(int iper, double vs30, double rRup,
                            double distJB,double f_rv,
                            double f_nm, double mag, double dip, double depthTop,
                            double depthTo2pt5kmPerSec,
                            boolean magSaturation, double pga_rock) {


    double fmag,fdis,fflt,fhng,fsite,fsed;
    
    //modeling depence on magnitude
    if(mag<= 5.5)
    		fmag = c0[iper]+c1[iper]*mag;
    else if(mag > 5.5  && mag <=6.5)
    	   fmag = c0[iper]+c1[iper]*mag+c2[iper]*(mag-5.5);
    else
    	  fmag  = c0[iper]+c1[iper]*mag+c2[iper]*(mag-5.5)+c3[iper]*(mag - 6.5);
    
    //source to site distance
    fdis = (c4[iper]+c5[iper]*mag)*Math.log(Math.sqrt(rRup*rRup+c6[iper]*c6[iper]));
    
    //style of faulting
    double ffltz; //getting the depth top or also called Ztor in Campbell's paper
    if(depthTop <1)
    	  ffltz = depthTop;
    else
    	 ffltz = 1;

    // fault-style term
    fflt = c7[iper]*f_rv*ffltz+c8[iper]*f_nm;
    
    //hanging wall effects
    double fhngr;
    if(distJB == 0)
    	 fhngr = 1;
    else if(depthTop < 1 && distJB >0)
    	 fhngr = (Math.max(rRup,Math.sqrt(distJB*distJB+1)) - distJB)/
    	 	      Math.max(rRup,Math.sqrt(distJB*distJB+1));
    else
    	 fhngr = (rRup-distJB)/rRup;

// if(pga_rock !=0) System.out.print((float)distJB+"\t"+(float)rRup+"\t"+fhngr+"\n");

    double fhngm;
    if(mag<=6.0)
    	  fhngm =0;
    else if(mag>6.0 && mag<6.5)
    	  fhngm = 2*(mag-6);
    else
    	 fhngm= 1;
    
    double fhngz;
    if(depthTop >=20)
    	  fhngz =0;
    else
    	 fhngz = (20-depthTop)/20;
    
    double fhngd;
    if(dip <= 70)
    	 fhngd =1;
    else
    	 fhngd = (90-dip)/20; 
    
    fhng = c9[iper]*fhngr*fhngm*fhngz*fhngd;
    
    
    //modelling dependence on linear and non-linear site conditions
    if(vs30< k1[iper])
    	fsite = 	c10[iper]*Math.log(vs30/k1[iper]) +
    	k2[iper]*(Math.log(pga_rock+c*Math.pow(vs30/k1[iper],n)) - Math.log(pga_rock+c));
    else if (vs30<1100)
    	fsite = (c10[iper]+k2[iper]*n)*Math.log(vs30/k1[iper]);
    else 
    	fsite = (c10[iper]+k2[iper]*n)*Math.log(1100/k1[iper]);
    
    //modelling depence on shallow sediments effects and 3-D basin effects
    if(depthTo2pt5kmPerSec<1)
    	 fsed = c11[iper]*(depthTo2pt5kmPerSec - 1);
    else if(depthTo2pt5kmPerSec <=3)
    	 fsed = 0;
    else
    	 fsed = c12[iper]*k3[iper]*Math.exp(-0.75)*(1-Math.exp(-0.25*(depthTo2pt5kmPerSec-3)));
    

    return fmag+fdis+fflt+fhng+fsite+fsed;
  }

 /**
  * 
  * @param iper
  * @param stdDevType
  * @param component
  * @return
  */
  public double getStdDev(int iper, String stdDevType, Component component, double vs30, double rock_pga) {

	  if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_NONE))
		  return 0.0;
	  else {

		  // get tau - inter-event term
		  double tau = t_lny[iper];

		  // compute intra-event sigma
		  double sigma;
		  if(vs30 >= k1[iper])
			  sigma = s_lny[iper];
		  else {
			  double s_lnYb = Math.sqrt(s_lny[iper]*s_lny[iper]-s_lnAF*s_lnAF);
			  double s_lnAb = Math.sqrt(s_lny[1]*s_lny[1]-s_lnAF*s_lnAF); // iper=1 is for PGA
			  double alpha = k2[iper]*rock_pga*((1/(rock_pga+c*Math.pow(vs30/k1[iper], n)))-1/(rock_pga+c));
			  sigma = Math.sqrt(s_lnYb*s_lnYb + s_lnAF*s_lnAF + alpha*alpha*s_lnAb*s_lnAb + 2*alpha*rho[iper]*s_lnYb*s_lnAb);
		  }

		  // compute total sigma
		  double sigma_total = Math.sqrt(tau*tau + sigma*sigma);

		  // compute multiplicative factor in case component is random horizontal
		  double random_ratio;
		  if(component == Component.RANDOM_HORZ)
			  random_ratio = Math.sqrt(1 + (s_c[iper]*s_c[iper])/(sigma_total*sigma_total));
		  else
			  random_ratio = 1;

		  // return appropriate value
		  if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_TOTAL))
			  return sigma_total*random_ratio;
		  else if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_INTRA))
			  return sigma*random_ratio;
		  else if (stdDevType.equals(StdDevTypeParam.STD_DEV_TYPE_INTER))
			  return tau*random_ratio;
		  else
			  return Double.NaN;   // just in case invalid stdDev given			  

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
	  else if (pName.equals(DistRupMinusJB_OverRupParameter.NAME)) {
		  distRupMinusJB_OverRup = ( (Double) val).doubleValue();
	  }
	  else if (pName.equals(Vs30_Param.NAME)) {
		  vs30 = ( (Double) val).doubleValue();
	  }
	  else if (pName.equals(DepthTo2pt5kmPerSecParam.NAME)) {
		  if(val == null)
			  depthTo2pt5kmPerSec = Double.NaN;  // can't set the defauly here because vs30 could still change
		  else
			  depthTo2pt5kmPerSec = ( (Double) val).doubleValue();
	  }
	  else if (pName.equals(magParam.NAME)) {
		  mag = ( (Double) val).doubleValue();
	  }
	  else if (pName.equals(FaultTypeParam.NAME)) {
		  String fltType = (String)fltTypeParam.getValue();
		  if (fltType.equals(FLT_TYPE_NORMAL)) {
			  f_rv = 0 ;
			  f_nm = 1;
		  }
		  else if (fltType.equals(FLT_TYPE_REVERSE)) {
			  f_rv = 1;
			  f_nm = 0;
		  }
		  else {
			  f_rv =0 ;
			  f_nm = 0;
		  }
	  }
	  else if (pName.equals(RupTopDepthParam.NAME)) {
		  depthTop = ( (Double) val).doubleValue();
	  }
	  else if (pName.equals(StdDevTypeParam.NAME)) {
		  stdDevType = (String) val;
	  }
	  else if (pName.equals(DipParam.NAME)) {
		  dip = ( (Double) val).doubleValue();
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
    distRupMinusJB_OverRupParam.removeParameterChangeListener(this);
    vs30Param.removeParameterChangeListener(this);
    depthTo2pt5kmPerSecParam.removeParameterChangeListener(this);
    magParam.removeParameterChangeListener(this);
    fltTypeParam.removeParameterChangeListener(this);
    rupTopDepthParam.removeParameterChangeListener(this);
    dipParam.removeParameterChangeListener(this);
    stdDevTypeParam.removeParameterChangeListener(this);

    this.initParameterEventListeners();
  }

  /**
   * Adds the parameter change listeners. This allows to listen to when-ever the
   * parameter is changed.
   */
  protected void initParameterEventListeners() {

    distanceRupParam.addParameterChangeListener(this);
    distRupMinusJB_OverRupParam.addParameterChangeListener(this);
    vs30Param.addParameterChangeListener(this);
    depthTo2pt5kmPerSecParam.addParameterChangeListener(this);
    magParam.addParameterChangeListener(this);
    fltTypeParam.addParameterChangeListener(this);
    rupTopDepthParam.addParameterChangeListener(this);
    stdDevTypeParam.addParameterChangeListener(this);
    dipParam.addParameterChangeListener(this);
  }

  
  /**
   * This provides a URL where more info on this model can be obtained
   * @throws MalformedURLException if returned URL is not a valid URL.
   * @return the URL to the AttenuationRelationship document on the Web.
   */
//  public URL getInfoURL() throws MalformedURLException{
////	  return new URL("http://www.opensha.org/documentation/modelsImplemented/attenRel/CB_2008.html"); //TODO
//  }

  
//  /**
//   * This tests DistJB numerical precision with respect to the f_hngR term.  Looks OK now.
//   * @param args
//   */
//  public static void main(String[] args) {
//
//	  Location loc1 = new Location(-0.1, 0.0, 0);
//	  Location loc2 = new Location(+0.1, 0.0, 0);
//	  FaultTrace faultTrace = new FaultTrace("test");
//	  faultTrace.add(loc1);
//	  faultTrace.add(loc2);	  
//	  StirlingGriddedSurface surface = new StirlingGriddedSurface(faultTrace, 45.0,0,10,1);
//	  EqkRupture rup = new EqkRupture();
//	  rup.setMag(7);
//	  rup.setAveRake(90);
//	  rup.setRuptureSurface(surface);
//	  
//	  CB_2010_CAV_AttenRel attenRel = new CB_2010_CAV_AttenRel(null);
//	  attenRel.setParamDefaults();
//	  attenRel.setIntensityMeasure("PGA");
//	  attenRel.setEqkRupture(rup);
//	  
//	  Site site = new Site();
//	  site.addParameter(attenRel.getParameter(Vs30_Param.NAME));
//	  site.addParameter(attenRel.getParameter(DepthTo2pt5kmPerSecParam.NAME));
//	  
//	  Location loc;
//	  for(double dist=-0.3; dist<=0.3; dist+=0.01) {
//		  loc = new Location(0,dist);
//		  site.setLocation(loc);
//		  attenRel.setSite(site);
////		  System.out.print((float)dist+"\t");
//		  attenRel.getMean();
//	  }
//	  
//  }
  
}
