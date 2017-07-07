package org.opensha.nshmp2.imr.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import org.opensha.commons.data.Named;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.nshmp2.util.Params;
import org.opensha.nshmp2.util.Utils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.PropagationEffect;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.EqkRuptureParams.FaultTypeParam;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.util.TectonicRegionType;

/**
 * This is an edited instance of the existing OpenSHA Zhao et al. subduction
 * attenuation relation. It has been modified to conform to the calculation
 * structure used in the NSHMP and skips testing for a variety of options. It
 * was created to try and fix hazard calculation inconsistencies observed when
 * using Zhao in conjunction with other IMRs.
 * 
 * This is for interface events only.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class ZhaoEtAl_2006_AttenRel extends AttenuationRelationship implements
		ParameterChangeListener {

	public final static String NAME = "ZhaoEtAl (2006)";
	public final static String SHORT_NAME = "ZhaoEtAl2006";
	private static final long serialVersionUID = 1L;	

	private static final double[] period = { 0.00, 0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.00, 1.25, 1.50, 2.00, 2.50, 3.00, 4.00, 5.00 };
	private static final double[] a = { 1.101, 1.076, 1.118, 1.134, 1.147, 1.149, 1.163, 1.200, 1.250, 1.293, 1.336, 1.386, 1.433, 1.479, 1.551, 1.621, 1.694, 1.748, 1.759, 1.826, 1.825 }; // checked
	private static final double[] b = { -0.00564, -0.00671, -0.00787, -0.00722, -0.00659, -0.00590, -0.00520, -0.00422, -0.00338, -0.00282, -0.00258, -0.00242, -0.00232, -0.00220, -0.00207, -0.00224, -0.00201, -0.00187, -0.00147, -0.00195, -0.00237 }; // checked
	private static final double[] c = { 0.0055, 0.0075, 0.0090, 0.0100, 0.0120, 0.0140, 0.0150, 0.0100, 0.0060, 0.003, 0.0025, 0.0022, 0.0020, 0.0020, 0.0020, 0.0020, 0.0025, 0.0028, 0.0032, 0.004, 0.005 }; // checked
	private static final double[] d = { 1.07967, 1.05984, 1.08274, 1.05292, 1.01360, 0.96638, 0.93427, 0.95880, 1.00779, 1.08773, 1.08384, 1.08849, 1.10920, 1.11474, 1.08295, 1.09117, 1.05492, 1.05191, 1.02452, 1.04356, 1.06518 };
	private static final double[] e = { 0.01412, 0.01463, 0.01423, 0.01509, 0.01462, 0.01459, 0.01458, 0.01257, 0.01114, 0.010190, 0.009790, 0.00944, 0.00972, 0.01005, 0.01003, 0.00928, 0.00833, 0.00776, 0.00644, 0.005900, 0.00510 };
	private static final double[] Si = { 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, -0.0412, -0.0528, -0.1034, -0.1460, -0.1638, -0.2062, -0.2393, -0.2557, -0.3065, -0.3214, -0.3366, -0.3306, -0.3898, -0.4978 }; // from
	private static final double[] C1 = { 1.1111, 1.6845, 2.0609, 1.9165, 1.6688, 1.4683, 1.1720, 0.6548, 0.0713, -0.4288, -0.8656, -1.3250, -1.7322, -2.1522, -2.9226, -3.5476, -4.4102, -5.0492, -5.4307, -6.1813, -6.3471 }; // from
	private static final double[] C2 = { 1.3440, 1.7930, 2.1346, 2.1680, 2.0854, 1.9416, 1.6829, 1.1271, 0.5149, -0.0027, -0.4493, -0.9284, -1.3490, -1.7757, -2.5422, -3.1689, -4.0387, -4.6979, -5.0890, -5.8821, -6.0512 }; // from
	private static final double[] C3 = { 1.3548, 1.7474, 2.0311, 2.0518, 2.0007, 1.9407, 1.8083, 1.4825, 0.9339, 0.3936, -0.1109, -0.6200, -1.0665, -1.5228, -2.3272, -2.9789, -3.8714, -4.4963, -4.8932, -5.6981, -5.8733 }; // from
	private static final double[] sigma = { 0.6039, 0.6399, 0.6936, 0.7017, 0.6917, 0.6823, 0.6696, 0.6589, 0.6530, 0.6527, 0.6516, 0.6467, 0.6525, 0.6570, 0.6601, 0.6640, 0.6694, 0.6706, 0.6671, 0.6468, 0.6431 };
	private static final double[] tau = { 0.3976, 0.4437, 0.4903, 0.4603, 0.4233, 0.3908, 0.3790, 0.3897, 0.3890, 0.4014, 0.4079, 0.4183, 0.4106, 0.4101, 0.4021, 0.4076, 0.4138, 0.4108, 0.3961, 0.3821, 0.3766 };
	private static final double[] Qi = { 0.0000, 0.0000, 0.0000, -0.0138, -0.0256, -0.0348, -0.0423, -0.0541, -0.0632, -0.0707, -0.0771, -0.0825, -0.0874, -0.0917, -0.1009, -0.1083, -0.1202, -0.1293, -0.1368, -0.1486, -0.1578 };
	private static final double[] Wi = { 0.0000, 0.0000, 0.0000, 0.0286, 0.0352, 0.0403, 0.0445, 0.0511, 0.0562, 0.0604, 0.0639, 0.0670, 0.0697, 0.0721, 0.0772, 0.0814, 0.0880, 0.0931, 0.0972, 0.1038, 0.1090 };

	private HashMap<Double, Integer> indexFromPerHashMap;

	private int iper;
	private double mag, rRup, vs30;
	private TectonicRegionType subType;
	
	// parent TRTParam should be updated to enumParam
	private EnumParameter<TectonicRegionType> subTypeParam;

	private transient ParameterChangeWarningListener warningListener = null;

	/**
	 *  This initializes several ParameterList objects.
	 */
	public ZhaoEtAl_2006_AttenRel(ParameterChangeWarningListener listener) {
		warningListener = listener;
		initSupportedIntensityMeasureParams();
		indexFromPerHashMap = new HashMap<Double, Integer>();
		for (int i = 0; i < period.length; i++) { 
			indexFromPerHashMap.put(new Double(period[i]), new Integer(i));
		}
		initEqkRuptureParams();
		initPropagationEffectParams();
		initSiteParams();
		initOtherParams();

		initIndependentParamLists(); // This must be called after the above
		initParameterEventListeners(); //add the change listeners to the parameters
		
		setParamDefaults();
	}

	@Override
	public void setEqkRupture(EqkRupture eqkRupture) {
		magParam.setValueIgnoreWarning(new Double(eqkRupture.getMag()));		
		this.eqkRupture = eqkRupture;
		setPropagationEffectParams();
	}

	@Override
	public void setSite(Site site) throws ParameterException {
		vs30Param.setValueIgnoreWarning((Double) site.getParameter(
			Vs30_Param.NAME).getValue());
		this.site = site;
		setPropagationEffectParams();
	}
	

	@Override
	public void setPropagationEffectParams() {
		if ( (this.site != null) && (this.eqkRupture != null)) {
			distanceRupParam.setValue(eqkRupture,site);
		}
	}

	private void setCoeffIndex() throws ParameterException {
		if (im == null) {
			throw new ParameterException("Intensity Measure Param not set");
		}
		iper = indexFromPerHashMap.get(saPeriodParam.getValue());
		intensityMeasureChanged = false;
	}

	@Override
	public double getMean() { 
		if (rRup > USER_MAX_DISTANCE) {
			return VERY_SMALL_MEAN;
		}

		if (intensityMeasureChanged) {
			setCoeffIndex(); // updates intensityMeasureChanged
		}
		return getMean(iper,mag,rRup,vs30);
	}

	@Override
	public double getStdDev() {
		if (intensityMeasureChanged) setCoeffIndex();
		return getStdDev(iper);
	}

	@Override
	public void setParamDefaults() {
		vs30Param.setValueAsDefault();
		subTypeParam.setValueAsDefault();
		magParam.setValueAsDefault();
		distanceRupParam.setValueAsDefault();
		saParam.setValueAsDefault();
		saPeriodParam.setValueAsDefault();
		saDampingParam.setValueAsDefault();
		pgaParam.setValueAsDefault();

		vs30 = vs30Param.getValue();
		subType = subTypeParam.getValue();
		mag = magParam.getValue();
		rRup = distanceRupParam.getValue();
	}

	/**
	 * This creates the lists of independent parameters that the various dependent
	 * parameters (mean, standard deviation, exceedance probability, and IML at
	 * exceedance probability) depend upon. NOTE: these lists do not include anything
	 * about the intensity-measure parameters or any of their internal
	 * independentParamaters.
	 */
	protected void initIndependentParamLists() {

		// params that the mean depends upon
		meanIndependentParams.clear();
		meanIndependentParams.addParameter(magParam);
		meanIndependentParams.addParameter(vs30Param);
		meanIndependentParams.addParameter(distanceRupParam);
		meanIndependentParams.addParameter(subTypeParam);

		// params that the stdDev depends upon
		stdDevIndependentParams.clear();
		stdDevIndependentParams.addParameter(saPeriodParam);
		stdDevIndependentParams.addParameter(magParam);
		
		// params that the exceed. prob. depends upon
		exceedProbIndependentParams.clear();
		exceedProbIndependentParams.addParameterList(meanIndependentParams);
		exceedProbIndependentParams.addParameter(sigmaTruncTypeParam);
		exceedProbIndependentParams.addParameter(sigmaTruncLevelParam);

		// params that the IML at exceed. prob. depends upon
		imlAtExceedProbIndependentParams.addParameterList(
				exceedProbIndependentParams);
		imlAtExceedProbIndependentParams.addParameter(exceedProbParam);
	}

	@Override
	protected void initSiteParams() {
		vs30Param = new Vs30_Param(150.0, 1500.0);
		siteParams.clear();
		siteParams.addParameter(vs30Param);
	}

	@Override
	protected void initEqkRuptureParams() {		
		magParam = new MagParam(7.5);
		eqkRuptureParams.clear();
		eqkRuptureParams.addParameter(magParam);
	}

	@Override
	protected void initPropagationEffectParams() {
		distanceRupParam = new DistanceRupParameter(0.0);
		distanceRupParam.addParameterChangeWarningListener(listener);
		distanceRupParam.setNonEditable();
		propagationEffectParams.addParameter(distanceRupParam);
	}

	@Override
	protected void initSupportedIntensityMeasureParams() {
		// Create saParam:
		DoubleDiscreteConstraint periodConstraint = new DoubleDiscreteConstraint();
		for (int i = 0; i < period.length; i++) {
			periodConstraint.addDouble(new Double(period[i]));
		}
		periodConstraint.setNonEditable();
		saPeriodParam = new PeriodParam(periodConstraint);
		saDampingParam = new DampingParam();
		saParam = new SA_Param(saPeriodParam, saDampingParam);
		saParam.setNonEditable();

		//  Create PGA Parameter (pgaParam):
		pgaParam = new PGA_Param();
		pgaParam.setNonEditable();

		// Add the warning listeners:
		saParam.addParameterChangeWarningListener(warningListener);
		pgaParam.addParameterChangeWarningListener(warningListener);

		// Put parameters in the supportedIMParams list:
		supportedIMParams.clear();
		supportedIMParams.addParameter(saParam);
		supportedIMParams.addParameter(pgaParam);	
	}
	
	@Override
	protected void initOtherParams() {
		super.initOtherParams();
		subTypeParam = Params.createSubType();
		otherParams.addParameter(subTypeParam);
	}	

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls) {
		return Utils.getExceedProbabilities(imls, getMean(), getStdDev(), 
			false, 0.0);
	}

	// zhao does not use dtor but probably should
	private static final double DEPTH = 20.0;
	private static final double HC = 15.0;
	private static final double MC = 6.3;
	private static final double GCOR = 6.88806;

	/**
	 * Returns the mean ground motion.
	 * @param iper
	 * @param mag
	 * @param rRup
	 * @param vs30 
	 * @return the mean ground motion
	 */
	public double getMean(int iper, double mag, double rRup, double vs30) {

		double site = (vs30 > 599) ? C1[iper] : (vs30 > 299) ? C2[iper]
			: C3[iper];

		double afac = Si[iper];
		double hfac = DEPTH - HC;
		double m2 = mag - MC;
		double xmcor = Qi[iper] * m2 * m2 + Wi[iper];
//		double h = DEPTH;
		double r = rRup + c[iper] * Math.exp(d[iper] * mag);
		double gnd = a[iper] * mag + b[iper] * rRup - Math.log(r) + site;
		gnd = gnd + e[iper] * hfac + afac;
		gnd = gnd + xmcor;
		gnd = gnd - GCOR;
//		System.out.println("zh " + gnd + " " +rRup + " "+site+ " "+afac+ " "+hfac+ " ");
		return gnd;
	}

	/**
	 * Returns the standard deviation.
	 * @param iper
	 * @return the standard deviation
	 */
	public double getStdDev(int iper) {
		//  NSHMP uses standard sigma instead
		// of interface spcific sigma per A. Frankel. See comment in
		// hazSUBXnga in zhao subroutine:
		//
		// Frankel email may 22 2007: use sigt from table 5. Not the
		// reduced-tau sigma associated with mag correction seen in table 6.
		// Zhao says "truth" is somewhere in between.

//		System.out.println("zhSig: " + Math.sqrt(tau[iper]*tau[iper]+sigma[iper]*sigma[iper]));
		return Math.sqrt(tau[iper]*tau[iper]+sigma[iper]*sigma[iper]);
	}

	@Override
	public void parameterChange(ParameterChangeEvent e) {
		String pName = e.getParameterName();
		Object val = e.getNewValue();
		if (pName.equals(DistanceRupParameter.NAME)) {
			rRup = ( (Double) val).doubleValue();
		} else if (pName.equals(MagParam.NAME)) {
			mag = ( (Double) val).doubleValue();
		} else if (pName.equals(Vs30_Param.NAME)) {
			vs30 = ((Double) val).doubleValue();
		} else if (pName.equals(PeriodParam.NAME)) {
			intensityMeasureChanged = true;
		} else if (pName.equals(subTypeParam.getName())) {
			subType = (TectonicRegionType) val;
		}
	}
	
	@Override
	public void resetParameterEventListeners(){
		distanceRupParam.removeParameterChangeListener(this);
		vs30Param.removeParameterChangeListener(this);
		magParam.removeParameterChangeListener(this);
		saPeriodParam.removeParameterChangeListener(this);
		subTypeParam.removeParameterChangeListener(this);
		this.initParameterEventListeners();
	}
	
	@Override
	protected void initParameterEventListeners() {
		distanceRupParam.addParameterChangeListener(this);
		vs30Param.addParameterChangeListener(this);
		magParam.addParameterChangeListener(this);
		saPeriodParam.addParameterChangeListener(this);
		subTypeParam.addParameterChangeListener(this);
	}

	@Override
	public URL getInfoURL() throws MalformedURLException{
		return new URL("http://www.opensha.org/documentation/modelsImplemented/attenRel/ZhaoEtAl_2006.html");
	}

}
