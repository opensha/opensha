package org.opensha.sha.imr;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

import org.opensha.commons.calc.GaussianDistCalc;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.IMRException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.impl.WarningDoubleParameter;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.rupForecastImpl.PointEqkSource;
import org.opensha.sha.gcim.imr.param.EqkRuptureParams.FocalDepthParam;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.CAV_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.Ds575_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.Ds595_Param;
import org.opensha.sha.imr.param.EqkRuptureParams.AftershockParam;
import org.opensha.sha.imr.param.EqkRuptureParams.DipParam;
import org.opensha.sha.imr.param.EqkRuptureParams.FaultTypeParam;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RakeParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupTopDepthParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupWidthParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGD_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistRupMinusDistX_OverRupParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistRupMinusJB_OverRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceSeisParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.HangingWallFlagParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;
import org.opensha.sha.util.TectonicRegionType;

/**
 *  <b>Title:</b> AttenuationRelationship</p> <p>
 *
 *  <b>Description:</b> This subclass of IntensityMeasureRelationship is the abstract 
 *  implementation for Attenuation Relationships (also known as Ground Motion Prediction 
 *  Equations (GMPEs)).  In addition to implementing the ScalarIntensityMeasureRelationshipAPI 
 *  interface (where all IMLs are double values), this also assumes that the probability 
 *  distribution for the IMLs is Gaussian.  The probability calculation is identical for all
 *  subclasses, and is therefore handled here in the abstract class (according to the 
 *  truncation-type and truncation-level parameters defined here), whereas subclasses 
 *  simply implement the getMean() and getStdDev() methods (as well as others).<p>
 *  
 *  Remember, the probability that an intensity-measure type (IMT, defined by a 
 *  parameter in the subclass) will exceed some IML is computed from
 *  parameters defined in the subclass, and these parameters are categorized according to
 *  whether they relate to a Site, EqkRupture, Propagation Effect (meaning the value depends on 
 *  both the given Site and EqkRupture), or to some "Other" category (there is a separate list defined for each 
 *  of these categories).  In addition, each subclass has a list of supported IMTs.<p>
 *  
 *  We need to avoid subclasses from defining the same parameter with different names, or
 *  defining different parameters with he same name.  To this end, we define the following 
 *  parameters here in this abstract class (all of which are instances of the associated parameter 
 *  defined in the "param" folder at the same directory level of this class, see that code for exact
 *  definitions):<p>
 *  
 *  Intensity-Measure parameters (IMTs)<p>
 * <UL>
 * <LI><b>pgaParam</b> - Natural-log of Peak Ground Acceleration
 * <LI><b>pgvParam</b> - Natural-log of Peak Ground Velocity
 * <LI><b>pgdParam</b> - Natural-log of Peak Ground Displacement
 * <LI><b>saParam</b> - Natural-log of Response Spectral Acceleration (also depends on saPeriodParam and saDampingParam)
 * </UL><p>
 *  
 *  Site-related parameters<p>
 *  <UL>
 *  <LI><b>vs30Param</b> - Average shear-wave velocity between 0 and 30 meters depth
 *  <LI><b>vs30_TypeParam</b> - Indicates whether Vs30 is Measured or Inferred
 *  <LI><b>depthTo2pt5kmPerSecParam</b> - Depth to where shear-wave velocity equals 2.5 km/sec
 *  <LI><b>depthTo1pt0kmPerSecParam</b> - Depth to where shear-wave velocity equals 1.0 km/sec
 *  </UL><p>
 * 
 *  EqkRupture-related parameters<p>
 *  <UL>
 *  <LI><b>magParam</b> - Moment Magnitude
 *  <LI><b>fltTypeParam</b> - Text field indicating type of faulting (e.g., "Strike Slip")
 *  <LI><b>aftershockParam</b> - Indicates whether event is an aftershock
 *  <LI><b>rakeParam</b> - Average rake of rupture
 *  <LI><b>dipParam</b> - Average dip of rupture
 *  <LI><b>rupTopDepthParam</b> - Depth to the top-edge of the rupture
 *  <LI><b>rupWidthParam</b> - Down-dip width of rupture
 *  
 *  </UL><p>
 *  Propagation-Effect related parameters<p>
 *  <UL>
 *  <LI><b>distanceRupParam</b> -  See class for definition
 *  <LI><b>distanceJBParam</b> -  See class for definition
 *  <LI><b>distanceSeisParam</b> -  See class for definition
 *  <LI><b>distRupMinusJB_OverRupParam</b> - See class for definition
 *  <LI><b>distRupMinusDistX_OverRupParam</b> - See class for definition
 *  <LI><b>hangingWallFlagParam</b> - Indicates whether Site is on the hanging wall of the rupture
 *  </UL>
 * 
 *  Other parameters<p>
 *  <UL>
 *  <LI><b>stdDevTypeParam</b> - The standard deviation type (e.g., total vs intra-event vs inter-event)
 *  <LI><b>componentParam</b> - The component of shaking (e.g., ave horizontal vs vertical)
 *  <LI><b>sigmaTruncTypeParam</b>  - Type of truncation to apply to the Gaussian distribution 
 *  <LI><b>sigmaTruncLevelParam</b> - Level of truncation
 *  </UL><p>
 *  Note that these parameters are only defined here, and need to be instantiated in a 
 *  given subclass if their use is desired (otherwise they can be ignored).  Again, most 
 *  of the parameters do not really need to be defined here (other than the few that are 
 *  actually used in methods here), but we define them here anyway to encourage consistent 
 *  usage.  These parameters can also be overridden if different attributes are desired.<p>
 *  
 *  
 *  <b>Notes for Implementing Subclasses:</b><p>
 *  The easiest way to learn how to implement an AttenuationRelationship is to look at 
 *  one that's already been implemented.  In fact, you can simply duplicate the one that
 *  is closest to what you want to implement, change the name, and modify accordingly (that's
 *  what we generally do).<p>
 *  
 *  The first step is to identify the intensity-measure types the model is to support, and to
 *  initialize these from the constructor using the initPropagationEffectParams() method, which 
 *  also populates the supportedIMParams list.<p>  
 *  When defining a new parameter (either for an IMT or for one of the other parameter types 
 *  described below), you should always choose from the parameters already defined in the 
 *  "param" folder (at the same level as this class) if it exists.  Otherwise you need to
 *  define and create a new one in your subclass.  If you think anyone else might want to use 
 *  your new one (or could accidentally adopt the same name), then we should add it to "params" 
 *  folder here to avoid effort duplication (or inconsistencies).  <p>
 *  The second step is to identify the parameters the model depends upon (categorized by those that
 *  depend on the Site, EqkRupture, Propagation Effect, or Other), and to initialize them from
 *  the constructor using the following associated methods:
 *  <UL>
 *  <LI>initEqkRuptureParams()
 *  <LI>initPropagationEffectParams()
 *  <LI>initSiteParams()
 *  <LI>initOtherParams()
 *  </UL><p>
 *  All but the last method is defined as abstract here in order to remind developers to implement
 *  these methods (they can have an empty implementation if there are no such parameters in the
 *  subclass).  The initOtherParams() method is implemented here because the two truncation-related
 *  parameters will likely exist in every subclass (although this method must still be called as 
 *  "super.initOtherParams()" from the subclass in order to include them). Each of the above methods also populates 
 *  the associated list (siteParams, eqkRuptureParams, propagationEffectParams, or otherParams).<p>
 *  
 *  The third step is to populate the following lists (typically done using a 
 *  initIndependentParamLists() method called from the constructor): meanIndependentParams, 
 *  stdDevIndependentParams, exceedProbIndependentParams, and imlAtExceedProbIndependentParams.  If the
 *  Attenuation Relationship is to listen to, and act, on any parameter changes, the following methods
 *  need to be implement: initParameterEventListeners(), resetParameterEventListeners() and 
 *  parameterChange(*).<p>
 *  
 *  The fourth step is to implement the setEqkRupture(qkRup) and setSite(site) methods, which simply set
 *  the values of the EqkRupture-related and Site-related parameters from the objects passed in.  We also
 *  need to implement the initPropagationEffectParams() method, which sets those parameters from the
 *  current Site and EqkRupture objects (this method is generally called at the end of the 
 *  setEqkRupture(qkRup) and setSite(site) methods).<p>
 *  
 *  The fifth step is to implement the getMean() and getStdDev() methods, which simply calculate those 
 *  respective values from current parameter settings.<p>  Note that if the value of the distance parameter
 *  used by the model exceeds the USER_MAX_DISTANCE field, then the value of VERY_SMALL_MEAN should be returned 
 *  by the getMean() method (implemented in order to get results consistent with the 2003 NSHMP Fortran code).
 *  The final step is to document the attenuation relationship, both in terms of Java docs and the 
 *  glossary at our web page (http://www.opensha.org/documentation/glossary), for which you can use 
 *  another model as a guide (e.g., see CB_2008_AttenRel and its glossary entry -
 *  http://www.opensha.org/documentation/modelsImplemented/attenRel/CB_2008.html).<p>
 *  
 *  We've skipped some details in these instructions, but again, the easiest way to implement a model is
 *  look at one that's already been implemented<p>
 *  
 *  Please ask questions and feel free to improve these notes if you can.
 *  
 *  
 * @author     Edward H. Field
 * @created    April 1st, 2002
 * @version    1.0
 */

/* 
 * 
 *
 */

public abstract class AttenuationRelationship
extends AbstractIMR implements ScalarIMR {

	/**
	 *  Classname constant used for debugging statements
	 */
	public final static String C = "AttenuationRelationship";

	/**
	 *  Prints out debugging statements if true
	 */
	protected final static boolean D = false;

	/**
	 * Intensity-Measure Parameters
	 * (see classes for exact definitions)
	 */
	protected PGA_Param pgaParam = null;
	protected PGV_Param pgvParam = null;
	protected PGD_Param pgdParam = null;
	protected SA_Param saParam = null;
	protected PeriodParam saPeriodParam = null;
	protected DampingParam saDampingParam = null;
	// gcim
	protected CAV_Param cavParam = null;
	protected Ds575_Param ds575Param = null;
	protected Ds595_Param ds595Param = null;

	/**
	 * Other Parameters
	 * (see classes for exact definitions)
	 */
	protected StdDevTypeParam stdDevTypeParam = null;
	protected SigmaTruncTypeParam sigmaTruncTypeParam = null;
	protected SigmaTruncLevelParam sigmaTruncLevelParam = null;
	protected ComponentParam componentParam = null;
	protected TectonicRegionTypeParam tectonicRegionTypeParam = null;

	/**
	 * Earthquake Rupture related parameters
	 * (see classes for exact definitions)
	 */
	protected MagParam magParam = null;
	protected FaultTypeParam fltTypeParam = null;
	protected AftershockParam aftershockParam = null;
	protected RakeParam rakeParam = null;
	protected DipParam dipParam = null;
	protected RupTopDepthParam rupTopDepthParam = null;
	protected RupWidthParam rupWidthParam;
	// gcim
	protected FocalDepthParam focalDepthParam;

	/**
	 * Propagation Effect Parameters
	 * (see classes for exact definitions)
	 */
	protected DistanceRupParameter distanceRupParam = null;
	protected DistanceJBParameter distanceJBParam = null;
	protected DistanceSeisParameter distanceSeisParam = null;
	protected DistRupMinusJB_OverRupParameter distRupMinusJB_OverRupParam = null;
	protected DistRupMinusDistX_OverRupParam distRupMinusDistX_OverRupParam = null;  // not a subclass of PropagationEffectParameter
	protected HangingWallFlagParam hangingWallFlagParam = null;  	// not a subclass of PropagationEffectParameter

	/**
	 * Site related parameters
	 * (see classes for exact definitions)
	 */
	protected Vs30_Param vs30Param = null;
	protected Vs30_TypeParam vs30_TypeParam;
	protected DepthTo2pt5kmPerSecParam depthTo2pt5kmPerSecParam = null;
	protected DepthTo1pt0kmPerSecParam depthTo1pt0kmPerSecParam;


	/**
	 * This allows users to set a maximul distance (beyond which the mean will
	 * be effectively zero)
	 */
	protected double USER_MAX_DISTANCE = Double.MAX_VALUE;
	protected final static double VERY_SMALL_MEAN = -35.0; // in ln() space

	/**
	 *  Common error message = "Not all parameters have been set"
	 */
	protected final static String ERR = "Not all parameters have been set";

	/**
	 *  List of all Parameters that the mean calculation depends upon, except for
	 *  the intensity-measure related parameters (type/level) and any independentdent parameters
	 *  they contain.
	 */
	protected ParameterList meanIndependentParams = new ParameterList();

	/**
	 *  List of all Parameters that the stdDev calculation depends upon, except for
	 *  the intensity-measure related parameters (type/level) and any independentdent parameters
	 *  they contain.
	 */
	protected ParameterList stdDevIndependentParams = new ParameterList();

	/**
	 *  List of all Parameters that the exceed. prob. calculation depends upon, except for
	 *  the intensity-measure related parameters (type/level) and any independentdent parameters
	 *  they contain.  Note that this and its iterator method could be applied in the parent class.
	 */
	protected ParameterList exceedProbIndependentParams = new ParameterList();

	/**
	 *  List of all Parameters that the IML at exceed. prob. calculation depends upon, except for
	 *  the intensity-measure related parameters (type/level) and any independentdent parameters
	 *  they contain.
	 */
	protected ParameterList imlAtExceedProbIndependentParams = new ParameterList();

	/**
	 *  Constructor for the AttenuationRelationship object - subclasses should execute the
	 *  various init*() methods (in proper order)
	 */
	public AttenuationRelationship() {}

	/**
	 * This method sets the user-defined distance beyond which ground motion is
	 * set to effectively zero (the mean is a large negative value).
	 * @param maxDist
	 */
	public void setUserMaxDistance(double maxDist) {
		USER_MAX_DISTANCE = maxDist;
	}

	/**
	 *  Sets the value of the currently selected intensityMeasure (if the
	 *  value is allowed); this will reject anything that is not a Double.
	 *
	 * @param  iml                     The new intensityMeasureLevel value
	 * @exception  ParameterException  Description of the Exception
	 */
	public void setIntensityMeasureLevel(Object iml) throws ParameterException {

		if (! (iml instanceof Double)) {
			throw new ParameterException(C +
					": setIntensityMeasureLevel(): Object not a DoubleParameter, unable to set.");
		}

		setIntensityMeasureLevel( (Double) iml);
	}

	/**
	 *  Sets the value of the selected intensityMeasure;
	 *
	 * @param  iml                     The new intensityMeasureLevel value
	 * @exception  ParameterException  Description of the Exception
	 */
	public void setIntensityMeasureLevel(Double iml) throws ParameterException {

		if (im == null) {
			throw new ParameterException(C +
					": setIntensityMeasureLevel(): Intensity Measure is null, unable to set."
			);
		}

		if (im instanceof WarningDoubleParameter)
			((WarningDoubleParameter)im).setValueIgnoreWarning(iml);
		else
			this.im.setValue(iml);
	}

	/**
	 * This method sets the location in the site.
	 * This is helpful because it allows to  set the location within the
	 * site without setting the Site Parameters. Thus allowing the capability
	 * of setting the site once and changing the location of the site to do the
	 * calculations.
	 */
	public void setSiteLocation(Location loc) {
		//if site is null create a new Site
		if (site == null) {
			site = new Site();
		}
		site.setLocation(loc);
		setPropagationEffectParams();
	}

	/**
	 *  Calculates the value of each propagation effect parameter from the
	 *  current Site and ProbEqkRupture objects.
	 */
	protected abstract void setPropagationEffectParams();

	/**
	 *  This calculates the probability that the intensity-measure level
	 *  (the value in the Intensity-Measure Parameter) will be exceeded
	 *  given the mean and stdDev computed from current independent parameter
	 *  values.  Note that the answer is not stored in the internally held
	 *  exceedProbParam (this latter param is used only for the
	 *  getIML_AtExceedProb() method).
	 *
	 * @return                         The exceedProbability value
	 * @exception  ParameterException  Description of the Exception
	 * @exception  IMRException        Description of the Exception
	 */
	public double getExceedProbability() throws ParameterException, IMRException {

		// Calculate the standardized random variable
		double iml = ((Double) im.getValue()).doubleValue();
		double stdDev = getStdDev();
		double mean = getMean();

		return getExceedProbability(mean, stdDev, iml);
	}

	/**
	 *  This calculates the probability that the supplied intensity-measure level
	 *  will be exceeded given the mean and stdDev computed from current independent
	 *  parameter values.  Note that the answer is not stored in the internally held
	 *  exceedProbParam (this latter param is used only for the
	 *  getIML_AtExceedProb() method).
	 *
	 * @return                         The exceedProbability value
	 * @exception  ParameterException  Description of the Exception
	 * @exception  IMRException        Description of the Exception
	 */
	public double getExceedProbability(double iml) throws ParameterException,
	IMRException {

		// set the im parameter in order to verify that it's a permitted value
		setIntensityMeasureLevel(Double.valueOf(iml));

		return getExceedProbability();
	}


	/**
	 *  This calculates the exceed-probability at each SA Period for
	 *  the supplied intensity-measure level (a hazard spectrum).  The x values 
	 *  in the returned function correspond to the periods supported by the IMR.
	 *
	 * @return     DiscretizedFuncAPI - The hazard spectrum
	 */
	public DiscretizedFunc getSA_ExceedProbSpectrum(double iml) throws ParameterException,
	IMRException {
		this.setIntensityMeasure(SA_Param.NAME);
		setIntensityMeasureLevel(Double.valueOf(iml));
		DiscretizedFunc exeedProbFunction =  new ArbitrarilyDiscretizedFunc();
		List allowedSA_Periods = saPeriodParam.getAllowedDoubles();
		int size = allowedSA_Periods.size();
		for(int i=0;i<size;++i){
			Double saPeriod = (Double)allowedSA_Periods.get(i);
			getParameter(PeriodParam.NAME).setValue(saPeriod);
			exeedProbFunction.set(saPeriod.doubleValue(),getExceedProbability());
		}
		return exeedProbFunction;
	}


	/**
	 * This calculates the intensity-measure level for each SA Period
	 * associated with the given probability.  The x values in the
	 * returned function correspond to the periods supported by the IMR.
	 * @param exceedProb
	 * @return DiscretizedFuncAPI - the IML function
	 */
	public DiscretizedFunc getSA_IML_AtExceedProbSpectrum(double exceedProb) throws ParameterException,
	IMRException {
		this.setIntensityMeasure(SA_Param.NAME);
		//sets the value of the exceedProb Param.
		exceedProbParam.setValue(exceedProb);
		DiscretizedFunc imlFunction =  new ArbitrarilyDiscretizedFunc();
		List allowedSA_Periods = saPeriodParam.getAllowedDoubles();
		int size = allowedSA_Periods.size();
		for(int i=0;i<size;++i){
			Double saPeriod = (Double)allowedSA_Periods.get(i);
			getParameter(PeriodParam.NAME).setValue(saPeriod);
			imlFunction.set(saPeriod.doubleValue(),getIML_AtExceedProb());
		}

		return imlFunction;
	}



	/**
	 * This returns (iml-mean)/stdDev, ignoring any truncation.  This gets the iml
	 * from the value in the Intensity-Measure Parameter.
	 * @return double
	 */
	public double getEpsilon(){
		double iml = ((Double) im.getValue()).doubleValue();
		return (iml - getMean())/getStdDev();
	}


	/**
	 * This returns (iml-mean)/stdDev, ignoring any truncation.
	 *
	 * @param iml double
	 * @return double
	 */
	public double getEpsilon(double iml){
		// set the im parameter in order to verify that it's a permitted value
		setIntensityMeasureLevel(Double.valueOf(iml));

		return getEpsilon();
	}

	/**
	 * This method computed the probability of exceeding the IM-level given the
	 * mean and stdDev, and considering the sigma truncation type and level.
	 * @param mean
	 * @param stdDev
	 * @param iml
	 * @return
	 * @throws ParameterException
	 * @throws IMRException
	 */
	protected double getExceedProbability(double mean, double stdDev, double iml) throws
	ParameterException, IMRException {
		return getExceedProbability(mean, stdDev, iml, sigmaTruncTypeParam, sigmaTruncLevelParam);
	}
	
	public static double getExceedProbability(double mean, double stdDev, double iml,
			SigmaTruncTypeParam sigmaTruncTypeParam, SigmaTruncLevelParam sigmaTruncLevelParam) {

		if (stdDev != 0) {
			double stRndVar = (iml - mean) / stdDev;
			// compute exceedance probability based on truncation type
			if (sigmaTruncTypeParam == null ||
					sigmaTruncTypeParam.getValue().equals(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_NONE)) {
				return GaussianDistCalc.getExceedProb(stRndVar);
			} else {
				double numSig = ( (Double) ( (Parameter) sigmaTruncLevelParam).
						getValue()).doubleValue();
				if (sigmaTruncTypeParam.getValue().equals(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED)) {
					return GaussianDistCalc.getExceedProb(stRndVar, 1, numSig);
				}
				else {
					return GaussianDistCalc.getExceedProb(stRndVar, 2, numSig);
				}
			}
		}
		else {
			if (iml > mean) {
				return 0;
			}
			else {
				return 1;
			}
		}
	}
	
	/**
	 * This returns a random IML.  Performance could probably be improved by
	 * not testing random samples for being within truncation limits.
	 * @return
	 */
	public double getRandomIML(Random randomSampler) {
		
		double stdDev = getStdDev();
		double mean = getMean();
		
		if(randomSampler == null)
			randomSampler = new Random();
		
		if (sigmaTruncTypeParam == null || sigmaTruncTypeParam.getValue().equals(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_NONE)) {
			return mean + stdDev*randomSampler.nextGaussian();
		} else {
			double numSig = ( (Double) ( (Parameter) sigmaTruncLevelParam).getValue()).doubleValue();
			boolean done = false;
			double randIML = Double.NaN;
			if (sigmaTruncTypeParam.getValue().equals(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED)) {
				while(!done) {
					randIML = mean + stdDev*randomSampler.nextGaussian();
					if(randIML<mean+numSig*stdDev)
						done = true;
				}
				return randIML;
			}
			else {
				while(!done) {
					randIML = mean + stdDev*randomSampler.nextGaussian();
					if(randIML<mean+numSig*stdDev && randIML>mean-numSig*stdDev)
						done = true;
				}
				return randIML;
			}
		}
	}


	/**
	 *  This fills in the exceedance probability for multiple intensityMeasure
	 *  levels (often called a "hazard curve"); the levels are obtained from
	 *  the X values of the input function, and Y values are filled in with the
	 *  asociated exceedance probabilities.
	 *
	 * @param  intensityMeasureLevels  The function to be filled in
	 * @return                         The function filled in
	 * @exception  ParameterException  Description of the Exception
	 */
	public DiscretizedFunc getExceedProbabilities(
			DiscretizedFunc intensityMeasureLevels
	) throws ParameterException {

		double stdDev = getStdDev();
		double mean = getMean();

		for (int i=0; i<intensityMeasureLevels.size(); i++) {
			double x = intensityMeasureLevels.getX(i);
			double y = getExceedProbability(mean, stdDev, x);
			intensityMeasureLevels.set(i, y);
		}

		return intensityMeasureLevels;
	}

	/**
	 * This method will compute the total probability of exceedance for a PointEqkSource
	 * (including the probability of each rupture).  It is assumed that this
	 * source is Poissonian (not checked).  This saves time by computing distance only
	 * once for all ruptures in this source.  This could be extended to include the
	 * point-source distance correction as well (a boolean in the constructor?), although
	 * this would have to check for each distance type.
	 * @param ptSrc
	 * @param iml
	 * @return
	 */
	public double getTotExceedProbability(PointEqkSource ptSrc, double iml) {

		double totProb = 1.0, qkProb;
		ProbEqkRupture tempRup;

		//set the IML
		setIntensityMeasureLevel(Double.valueOf(iml));

		// set the eqRup- and propEffect-params from the first rupture
		this.setEqkRupture(ptSrc.getRupture(0));

		//now loop over ruptures changing only the magnitude parameter.
		for (int i = 0; i < ptSrc.getNumRuptures(); i++) {
			tempRup = ptSrc.getRupture(i);
			magParam.setValueIgnoreWarning(Double.valueOf(tempRup.getMag()));
			qkProb = tempRup.getProbability();

			// check for numerical problems
			if (Math.log(1.0 - qkProb) < -30.0) {
				throw new RuntimeException(
						"Error: The probability for this ProbEqkRupture (" + qkProb +
				") is too high for a Possion source (~infinite number of events)");
			}

			totProb *= Math.pow(1.0 - qkProb, getExceedProbability());
		}
		return 1 - totProb;
	}

	/**
	 *  This calculates the intensity-measure level associated with probability
	 *  held by the exceedProbParam given the mean and standard deviation
	 * (according to the chosen truncation type and level).  Note
	 *  that this does not store the answer in the value of the internally held
	 *  intensity-measure parameter.
	 *
	 * @return                         The intensity-measure level
	 * @exception  ParameterException  Description of the Exception
	 */
	public double getIML_AtExceedProb() throws ParameterException {

		if (exceedProbParam.getValue() == null) {
			throw new ParameterException(C +
					": getExceedProbability(): " +
					"exceedProbParam or its value is null, unable to run this calculation."
			);
		}

		double exceedProb = ( (Double) ( (Parameter) exceedProbParam).getValue()).
		doubleValue();
		
		return getIML_AtExceedProb(getMean(), getStdDev(), exceedProb, sigmaTruncTypeParam, sigmaTruncLevelParam);
	}
	
	protected double getIML_AtExceedProb(double mean, double stdDev, double exceedProb,
			SigmaTruncTypeParam sigmaTruncTypeParam, SigmaTruncLevelParam sigmaTruncLevelParam) {
		double stRndVar;
		String sigTrType = (String) sigmaTruncTypeParam.getValue();

		// compute the iml from exceed probability based on truncation type:

		// check for the simplest, most common case (median from symmectric truncation)

		if (!sigTrType.equals(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED) && exceedProb == 0.5) {
			return getMean();
		}
		else {
			if (sigTrType.equals(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_NONE)) {
				stRndVar = GaussianDistCalc.getStandRandVar(exceedProb, 0, 0, 1e-6);
			}
			else {
				double numSig = ( (Double) ( (Parameter) sigmaTruncLevelParam).
						getValue()).doubleValue();
				if (sigTrType.equals(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED)) {
					stRndVar = GaussianDistCalc.getStandRandVar(exceedProb, 1, numSig,
							1e-6);
				}
				else {
					stRndVar = GaussianDistCalc.getStandRandVar(exceedProb, 2, numSig,
							1e-6);
				}
			}
			return mean + stRndVar * stdDev;
		}
	}

	/**
	 *  This calculates the intensity-measure level associated with 
	 *  given probability and the calculated mean and standard deviation
	 * (and according to the chosen truncation type and level).  Note
	 *  that this does not store the answer in the value of the internally 
	 *  held intensity-measure parameter.
	 * @param exceedProb : Sets the Value of the exceed Prob param with this value.
	 * @return                         The intensity-measure level
	 * @exception  ParameterException  Description of the Exception
	 */
	public double getIML_AtExceedProb(double exceedProb) throws
	ParameterException {

		//sets the value of the exceedProb Param.
		exceedProbParam.setValue(exceedProb);
		return getIML_AtExceedProb();
	}

	/**
	 *  Returns an iterator over all the Parameters that the Mean calculation depends upon.
	 *  (not including the intensity-measure related parameters and their internal,
	 *  independent parameters).
	 *
	 * @return    The Independent Params Iterator
	 */
	public ListIterator<Parameter<?>> getMeanIndependentParamsIterator() {
		return getMeanIndependentParams().getParametersIterator();
	}
	
	public ParameterList getMeanIndependentParams() {
		return meanIndependentParams;
	}


	/**
	 *  Returns an iterator over all the Parameters that the StdDev calculation depends upon
	 *  (not including the intensity-measure related parameters and their internal,
	 *  independent parameters).
	 *
	 * @return    The Independent Parameters Iterator
	 */
	public ListIterator<Parameter<?>> getStdDevIndependentParamsIterator() {
		return getStdDevIndependentParams().getParametersIterator();
	}
	
	public ParameterList getStdDevIndependentParams() {
		return stdDevIndependentParams;
	}


	/**
	 *  Returns an iterator over all the Parameters that the exceedProb calculation
	 *  depends upon (not including the intensity-measure related parameters and
	 *  their internal, independent parameters).
	 *
	 * @return    The Independent Params Iterator
	 */
	public ListIterator<Parameter<?>> getExceedProbIndependentParamsIterator() {
		return getExceedProbIndependentParams().getParametersIterator();
	}
	
	public ParameterList getExceedProbIndependentParams() {
		return exceedProbIndependentParams;
	}


	/**
	 *  Returns an iterator over all the Parameters that the IML-at-exceed-
	 *  probability calculation depends upon. (not including the intensity-measure
	 *  related paramters and their internal, independent parameters).
	 *
	 * @return    The Independent Params Iterator
	 */
	public ListIterator<Parameter<?>> getIML_AtExceedProbIndependentParamsIterator() {
		return getIML_AtExceedProbIndependentParams().getParametersIterator();
	}
	
	public ParameterList getIML_AtExceedProbIndependentParams() {
		return imlAtExceedProbIndependentParams;
	}


	/**
	 * This returns metadata for all parameters (only showing the independent parameters
	 * relevant for the presently chosen imt)
	 * @return
	 */
	public String getAllParamMetadata() {
		String metadata = imlAtExceedProbIndependentParams.getParameterListMetadataString();
		metadata += "; " + im.getMetadataString() + " [ ";
		for (Parameter<?> param : im.getIndependentParameterList()) {
			metadata += param.getMetadataString() + "; ";
		}
		metadata = metadata.substring(0, metadata.length() - 2);
		metadata += " ]";
		return metadata;

	}

	/**
	 *  This creates the supported intensity-measure parameters.  
	 *  All implementation is in the subclass (it's defined here as a reminder/suggestions).
	 */
	protected abstract void initSupportedIntensityMeasureParams();

	/**
	 *  This creates Site-related parameters, which are all associated parameters 
	 *  that the exceedance probability depends upon.
	 *  All implementation is in the subclass (it's defined here as a reminder/suggestions).
	 */
	protected abstract void initSiteParams();


	/**
	 *  Creates the EqkRupture-related parameters, which are all associated parameters 
	 *  that the exceedance probability depends upon.
	 *  All implementation is in the subclass (it's defined here as a reminder/suggestions).
	 */
	protected abstract void initEqkRuptureParams();

	/**
	 *  Creates Propagation-Effect related parameters, which are all associated parameters 
	 *  that the exceedance probability depends upon.
	 *  All implementation is in the subclass (it's defined here as a reminder/suggestions).
	 */
	protected abstract void initPropagationEffectParams();

	/**
	 * This creates the otherParams list.
	 * These are any parameters that the exceedance probability depends upon that is
	 * not a supported IMT (or one of their independent parameters) and is not contained
	 * in, or computed from, the site or eqkRutpure objects.  Note that this does not
	 * include the exceedProbParam (which exceedance probability does not depend on).
	 * sigmaTruncTypeParam and sigmaTruncLevelParam are instantiated here and added
	 * to the otherParams list (others should be implemented as desired in subclasses).
	 * The tectonicRegionTypeParam is also instantiated here with default options 
	 * (TYPE_ACTIVE_SHALLOW); this should be overridden in subclass if other options 
	 * are desired (and you'll need use the replaceParameter method to change the one in the
	 * otherParams list).
	 */
	protected void initOtherParams() {

		sigmaTruncTypeParam = new SigmaTruncTypeParam();
		sigmaTruncLevelParam = new SigmaTruncLevelParam();
		tectonicRegionTypeParam = new TectonicRegionTypeParam();
		tectonicRegionTypeParam.setValueAsDefault();

		// Put parameters in the otherParams list:
		otherParams.clear();
		otherParams.addParameter(sigmaTruncTypeParam);
		otherParams.addParameter(sigmaTruncLevelParam);
		otherParams.addParameter(tectonicRegionTypeParam);
	}

	/**
	 * Adds the Listeners to the parameters so that Attenuation can listen
	 * to any kind of changes to parameter values.
	 */
	protected  void initParameterEventListeners(){};

	/**
	 * Allows to reset the change listeners on the parameters
	 */
	public void resetParameterEventListeners(){};

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

}
