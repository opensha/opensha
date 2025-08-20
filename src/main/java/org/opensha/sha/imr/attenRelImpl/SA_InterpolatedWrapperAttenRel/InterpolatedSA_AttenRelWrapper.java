package org.opensha.sha.imr.attenRelImpl.SA_InterpolatedWrapperAttenRel;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.ListIterator;

import org.opensha.commons.data.Named;
import org.opensha.commons.data.Site;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
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
 * <b>Title:</b> InterpolatedSA_AttenRelWrapper<p>
 *
 * <b>Description:</b> 
 * 
 *
 * Supported Intensity-Measure Parameters:<p>
 * <UL>
 * <LI>pgaParam - Peak Ground Acceleration
 * <LI>pgaParam - Peak Ground Velocity
 * <LI>saParam - Response Spectral Acceleration
 * </UL><p>
 * Other Independent Parameters:<p>
 * <UL>
 * <LI>magParam - moment Magnitude
 * <LI>distanceJBParam - closest distance to surface projection of fault
 * <LI>vs30Param - 30-meter shear wave velocity
 * <LI>fltTypeParam - Style of faulting
 * <LI>componentParam - Component of shaking (only one)
 * <LI>stdDevTypeParam - The type of standard deviation
 * </UL></p>
 * 
 *<p>
 *
 * Verification - This model is unverified.
 * 
 *</p>
 *
 *
 * @author     Edward H. Field
 * @created    June, 2010
 * @version    1.0
 */


public class InterpolatedSA_AttenRelWrapper extends AttenuationRelationship implements
		ParameterChangeListener {

  // Debugging stuff
  private final static String C = "InterpolatedSA_AttenRelWrapper";
  private final static boolean D = false;
  public final static String SHORT_NAME = "SA Interp";
  private static final long serialVersionUID = 1234567890987654353L;
  private AttenuationRelationship attenRelToWrap;
  PeriodParam origPeriodParam;
  private SA_InterpolatedParam saInterpParam;
  private PeriodInterpolatedParam periodInterpParam;
  double period, periodBelow, periodAbove;
  double[] periods;

  // Name of IMR
  public final static String NAME = "Interpolated SA Atten Rel Wrapper";
  
  // URL Info String
  private final static String URL_INFO_STRING = null;

  private boolean parameterChange;
  
  // No arg constructor
  public InterpolatedSA_AttenRelWrapper() {}
  
  

  /**
   *  This initializes several ParameterList objects.
   */
  public InterpolatedSA_AttenRelWrapper(ParameterChangeWarningListener listener, 
		  AttenuationRelationship attenRelToWrap) {

    this.attenRelToWrap = attenRelToWrap;
    attenRelToWrap.setIntensityMeasure(SA_Param.NAME);  // set this now and forever
    
    this.listener = listener;

    initSupportedIntensityMeasureParams();
    siteParams = attenRelToWrap.getSiteParams();
    eqkRuptureParams = attenRelToWrap.getEqkRuptureParams();
    propagationEffectParams = attenRelToWrap.getPropagationEffectParams();
    otherParams = attenRelToWrap.getOtherParams();
    sigmaTruncTypeParam = (SigmaTruncTypeParam)otherParams.getParameter(SigmaTruncTypeParam.NAME);
    sigmaTruncLevelParam = (SigmaTruncLevelParam)otherParams.getParameter(SigmaTruncLevelParam.NAME);
    initIndependentParamLists(); // This must be called after the above
    initParameterEventListeners(); //add the change listeners to the parameters

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
	  super.setEqkRupture(eqkRupture);
	  attenRelToWrap.setEqkRupture(eqkRupture);
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

	@Override
	public void setSiteLocation(Location loc) {
		attenRelToWrap.setSiteLocation(loc);
		super.setSiteLocation(loc);
	}

  /**
   * This does nothing; is it needed?
   */
  protected void setPropagationEffectParams() {
  }


  @Override
  public void setPropagationEffectParams(SurfaceDistances distances) {
	  attenRelToWrap.setPropagationEffectParams(distances);
  }



  /**
   * Calculates the mean of the exceedence probability distribution. <p>
   * @return    The mean value
   */
  public double getMean() {
	  if(intensityMeasureChanged) {
		  setPeriodsAboveAndBelow();
	  }
	  origPeriodParam.setValue(periodBelow);
	  double meanBelow = attenRelToWrap.getMean();
	  origPeriodParam.setValue(periodAbove);
	  double meanAbove = attenRelToWrap.getMean();
//	  return (meanBelow*(periodAbove-period) + meanAbove*(period-periodBelow))/(periodAbove-periodBelow);
	  return (meanBelow + (meanAbove-meanBelow)*Math.log(period/periodBelow)/Math.log(periodAbove/periodBelow)); //log interpolation
  }

  /**
   * @return    The stdDev value
   */
  public double getStdDev() {
	  if(intensityMeasureChanged) {
		  setPeriodsAboveAndBelow();
	  }
	  origPeriodParam.setValue(periodAbove);
	  double stdAbove = attenRelToWrap.getStdDev();
	  origPeriodParam.setValue(periodBelow);
	  double stdBelow = attenRelToWrap.getStdDev();
//	  return (stdBelow*(periodAbove-period) + stdAbove*(period-periodBelow))/(periodAbove-periodBelow);
	  return (stdBelow + (stdAbove-stdBelow)*Math.log(period/periodBelow)/Math.log(periodAbove/periodBelow)); //log interpolation

  }
  
 /**
  * Find the supported period above and below the target value
  */
  private void setPeriodsAboveAndBelow() {
	  if (D) System.out.println("target period = "+period);
	  for(int i=0; i<periods.length-1;i++) {
		  if(periods[i] <= period && period <= periods[i+1]) {
			  periodBelow=periods[i];
			  periodAbove=periods[i+1];
			  if (D)System.out.println("\tbelow = "+periodBelow+"\tabove = "+periodAbove);

			  break;
		  }
	  }
	  
  }

  
  /**
   * Allows the user to set the default parameter values for the selected Attenuation
   * Relationship.
   */
  public void setParamDefaults() {
	  attenRelToWrap.setParamDefaults();
	  saInterpParam.setValueAsDefault();
	  periodInterpParam.setValueAsDefault();
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
    exceedProbIndependentParams = attenRelToWrap.getExceedProbIndependentParams();
    imlAtExceedProbIndependentParams = attenRelToWrap.getIML_AtExceedProbIndependentParams();
  }


  /**
   *  Creates the supported IM parameter (saInterpParam), as well as the
   *  independenParameters of SA (periodInterpolatedParam and dampingParam) and adds
   *  this to the supportedIMParams list. Makes the parameters noneditable.
   */
  protected void initSupportedIntensityMeasureParams() {

	  origPeriodParam = (PeriodParam)attenRelToWrap.getParameter(PeriodParam.NAME);
	  
	  if (D) System.out.println("orig min, max, & default: "+origPeriodParam.getMinPeriod()+"\t"+origPeriodParam.getMaxPeriod()+"\t"+origPeriodParam.getDefaultValue());
	  
	  periodInterpParam = new PeriodInterpolatedParam(origPeriodParam.getMinPeriod(),
			  origPeriodParam.getMaxPeriod(), origPeriodParam.getDefaultValue(), false);
	  periodInterpParam.addParameterChangeListener(this);  // do this before the next
	  periodInterpParam.setValue(origPeriodParam.getDefaultValue());
	  if (D) System.out.println("periodInterpParam value = "+periodInterpParam.getValue());

	  saInterpParam = new SA_InterpolatedParam(periodInterpParam, new DampingParam());
	  saInterpParam.setNonEditable();
	  	  
	  // make the list of periods
	  periods = origPeriodParam.getPeriods();

	  // Add the warning listeners:
	  saInterpParam.addParameterChangeWarningListener(listener);

	  // Put parameters in the supportedIMParams list:
	  supportedIMParams.clear();
	  supportedIMParams.addParameter(saInterpParam);

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
    if (D) System.out.println(pName+" value changed to "+val);
    if (pName.equals(periodInterpParam.NAME)) {
        period = ( (Double) val).doubleValue();
        intensityMeasureChanged = true;
    }
  }

  /**
   * Allows to reset the change listeners on the parameters
   */
  public void resetParameterEventListeners(){
	  this.attenRelToWrap.resetParameterEventListeners();
 //   saPeriodParam.removeParameterChangeListener(this);
	  this.initParameterEventListeners();
  }

  /**
   * Adds the parameter change listeners. This allows to listen to when-ever the
   * parameter is changed.
   */
  protected void initParameterEventListeners() {
//    saPeriodParam.addParameterChangeListener(this);
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
