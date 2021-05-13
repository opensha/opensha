package org.opensha.sha.imr.attenRelImpl;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.DurationTimeInterval;
import org.opensha.sha.imr.param.IntensityMeasureParams.SignificantDurationParam;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

public class AfshariStewart_2016_AttenRel extends AttenuationRelationship {
	
	public static final String NAME = "Afshari & Stewart (2016)";
	public static final String SHORT_NAME = "AfshariStewart2016";
	
	private SignificantDurationParam durParam;
	
	protected final static double VS30_WARN_MIN = 150.0;
	protected final static double VS30_WARN_MAX = 1500.0;
	protected final static double DEPTH_1pt0_WARN_MIN = 0;
	protected final static double DEPTH_1pt0_WARN_MAX = 3000;
	protected final static double MAG_WARN_MIN = 3d;
	protected final static double MAG_WARN_MAX = 8d;
	protected final static double DISTANCE_RUP_WARN_MIN = 0.0;
	protected final static double DISTANCE_RUP_WARN_MAX = 300.0;
	
	private EnumParameter<FaultStyle> faultStyleParam;
	public final static String FAULT_STYLE_PARAM_NAME = "Fault Style";
	
	public enum BasinDepthModel {
		CALIFORNIA("California"),
		JAPAN("Japan");
		
		private String name;
		private BasinDepthModel(String name) {
			this.name = name;
		}
		
		@Override
		public String toString() {
			return name;
		}
	}
	public final static String BASIN_DEPTH_MODEL_NAME = "Basin Depth Model Region";
	private EnumParameter<BasinDepthModel> basinDepthModelParam;
	
	private static class Coefficients {
		final double m1;
		final double m2;
		final double mStar;
		
		final Map<FaultStyle, Double> b0;
		final Map<FaultStyle, Double> b1;
		final double b2;
		final double b3;
		
		final double c1;
		final double c2;
		final double c3;
		final double c4;
		final double c5;
		
		final double R1;
		final double R2;
		final double V1;
		final double Vref;
		final double delZ1ref;
		
		final double tau1;
		final double tau2;
		final double phi1;
		final double phi2;
		public Coefficients(double m1, double m2, double mStar,
				Map<FaultStyle, Double> b0, Map<FaultStyle, Double> b1, double b2, double b3, double c1,
				double c2, double c3, double c4, double c5, double r1, double r2, double v1, double vref, double dZ1ref,
				double tau1, double tau2, double phi1, double phi2) {
			super();
			this.m1 = m1;
			this.m2 = m2;
			this.mStar = mStar;
			this.b0 = b0;
			this.b1 = b1;
			this.b2 = b2;
			this.b3 = b3;
			this.c1 = c1;
			this.c2 = c2;
			this.c3 = c3;
			this.c4 = c4;
			this.c5 = c5;
			R1 = r1;
			R2 = r2;
			V1 = v1;
			Vref = vref;
			this.delZ1ref = dZ1ref;
			this.tau1 = tau1;
			this.tau2 = tau2;
			this.phi1 = phi1;
			this.phi2 = phi2;
		}
	}
	
	static final Map<DurationTimeInterval, Coefficients> coeffs;
	
	static {
		coeffs = new HashMap<>();
		
		Map<FaultStyle, Double> b0 = new HashMap<>();
		b0.put(FaultStyle.NORMAL, 1.555);
		b0.put(FaultStyle.REVERSE, 0.7806);
		b0.put(FaultStyle.STRIKE_SLIP, 1.279);
		b0.put(FaultStyle.UNKNOWN, 1.280);
		Map<FaultStyle, Double> b1 = new HashMap<>();
		b1.put(FaultStyle.NORMAL, 4.992);
		b1.put(FaultStyle.REVERSE, 7.061);
		b1.put(FaultStyle.STRIKE_SLIP, 5.578);
		b1.put(FaultStyle.UNKNOWN, 5.576);
		coeffs.put(DurationTimeInterval.INTERVAL_5_75, new Coefficients(
				5.35, 7.15, 6d, b0, b1, 0.9011, -1.684, // table 1
				0.1159, 0.1065, 0.0682, -0.2246, 0.0006, 10d, 50d, 600d, 368.2, 200d, // table 2
				0.28, 0.25, 0.54, 0.41)); // table 3
		
		b0 = new HashMap<>();
		b0.put(FaultStyle.NORMAL, 2.541);
		b0.put(FaultStyle.REVERSE, 1.612);
		b0.put(FaultStyle.STRIKE_SLIP, 2.302);
		b0.put(FaultStyle.UNKNOWN, 2.182);
		b1 = new HashMap<>();
		b1.put(FaultStyle.NORMAL, 3.170);
		b1.put(FaultStyle.REVERSE, 4.536);
		b1.put(FaultStyle.STRIKE_SLIP, 3.467);
		b1.put(FaultStyle.UNKNOWN, 3.628);
		coeffs.put(DurationTimeInterval.INTERVAL_5_95, new Coefficients(
				5.2, 7.4, 6d, b0, b1, 0.9443, -3.911, // table 1
				0.3165, 0.2539, 0.0932, -0.3183, 0.0006, 10d, 50d, 600d, 369.9, 200d, // table 2
				0.25, 0.19, 0.43, 0.35)); // table 3
		
		b0 = new HashMap<>();
		b0.put(FaultStyle.NORMAL, 1.409);
		b0.put(FaultStyle.REVERSE, 0.7729);
		b0.put(FaultStyle.STRIKE_SLIP, 0.8804);
		b0.put(FaultStyle.UNKNOWN, 0.8822);
		b1 = new HashMap<>();
		b1.put(FaultStyle.NORMAL, 4.778);
		b1.put(FaultStyle.REVERSE, 6.579);
		b1.put(FaultStyle.STRIKE_SLIP, 6.188);
		b1.put(FaultStyle.UNKNOWN, 6.182);
		coeffs.put(DurationTimeInterval.INTERVAL_20_80, new Coefficients(
				5.2, 7.4, 6d, b0, b1, 0.7414, -3.164, // table 1
				0.0646, 0.0865, 0.0373, -0.4237, 0.0005, 10d, 50d, 600d, 369.6, 200d, // table 2
				0.3, 0.19, 0.56, 0.45)); // table 3
	}
	
	public AfshariStewart_2016_AttenRel() {
		this(null);
	}
	
	public AfshariStewart_2016_AttenRel(ParameterChangeWarningListener l) {
		super();
		this.listener = l;

		initEqkRuptureParams();
		initSiteParams();
		initPropagationEffectParams();
		initOtherParams();
		initSupportedIntensityMeasureParams();

		initIndependentParamLists(); // This must be called after the above
		initParameterEventListeners(); //add the change listeners to the parameters
	}

	@Override
	public void setParamDefaults() {
		// site params
		vs30Param.setValue(760d);
		depthTo1pt0kmPerSecParam.setValue(null);
		
		// rup params
		magParam.setValue(6d);
		distanceRupParam.setValue(0d);
		faultStyleParam.setValue(FaultStyle.UNKNOWN);
		
		// other params
		basinDepthModelParam.setValue(BasinDepthModel.CALIFORNIA);
		componentParam.setValue(Component.AVE_HORZ);
		stdDevTypeParam.setValue(StdDevTypeParam.STD_DEV_TYPE_TOTAL);
		
		// imt
		durParam.setTimeInterval(DurationTimeInterval.INTERVAL_5_75);
		setIntensityMeasure(durParam);
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
		meanIndependentParams.addParameter(magParam);
		meanIndependentParams.addParameter(faultStyleParam);
		meanIndependentParams.addParameter(vs30Param);
		meanIndependentParams.addParameter(depthTo1pt0kmPerSecParam);
		meanIndependentParams.addParameter(basinDepthModelParam);
		meanIndependentParams.addParameter(distanceRupParam);
		meanIndependentParams.addParameter(componentParam);

		// params that the stdDev depends upon
		stdDevIndependentParams.clear();
		stdDevIndependentParams.addParameter(magParam);
		stdDevIndependentParams.addParameter(stdDevTypeParam);
		stdDevIndependentParams.addParameter(componentParam);

		// params that the exceed. prob. depends upon
		exceedProbIndependentParams.clear();
		exceedProbIndependentParams.addParameterList(meanIndependentParams);
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
		depthTo1pt0kmPerSecParam = new DepthTo1pt0kmPerSecParam(DEPTH_1pt0_WARN_MIN, DEPTH_1pt0_WARN_MAX);
		depthTo1pt0kmPerSecParam.setValue(null);
		
		siteParams.clear();
		siteParams.addParameter(vs30Param);
		siteParams.addParameter(depthTo1pt0kmPerSecParam);
	}

	/**
	 *  Creates the two Potential Earthquake parameters (magParam and
	 *  fltTypeParam) and adds them to the eqkRuptureParams
	 *  list. Makes the parameters noneditable.
	 */
	protected void initEqkRuptureParams() {

		magParam = new MagParam(MAG_WARN_MIN, MAG_WARN_MAX);

		faultStyleParam = new EnumParameter<FaultStyle>(FAULT_STYLE_PARAM_NAME, EnumSet.allOf(FaultStyle.class),
				FaultStyle.UNKNOWN, null);

		eqkRuptureParams.clear();
		eqkRuptureParams.addParameter(magParam);
		eqkRuptureParams.addParameter(faultStyleParam);
	}

	/**
	 *  Creates the Propagation Effect parameters and adds them to the
	 *  propagationEffectParams list. Makes the parameters noneditable.
	 */
	protected void initPropagationEffectParams() {

		distanceRupParam = new DistanceRupParameter(0.0);
		distanceRupParam.addParameterChangeWarningListener(listener);
		DoubleConstraint warn = new DoubleConstraint(DISTANCE_RUP_WARN_MIN,
				DISTANCE_RUP_WARN_MAX);
		warn.setNonEditable();
		distanceRupParam.setWarningConstraint(warn);
		distanceRupParam.setNonEditable();

		propagationEffectParams.addParameter(distanceRupParam);

	}

	/**
	 *  Creates the two supported IM parameters (PGA and SA), as well as the
	 *  independenParameters of SA (periodParam and dampingParam) and adds
	 *  them to the supportedIMParams list. Makes the parameters noneditable.
	 */
	protected void initSupportedIntensityMeasureParams() {
		
		durParam = new SignificantDurationParam(DurationTimeInterval.INTERVAL_5_75,
				DurationTimeInterval.INTERVAL_5_95, DurationTimeInterval.INTERVAL_20_80);

		// Put parameters in the supportedIMParams list:
		supportedIMParams.clear();
		supportedIMParams.addParameter(durParam);
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
	    
	    basinDepthModelParam = new EnumParameter<BasinDepthModel>(BASIN_DEPTH_MODEL_NAME,
	    		EnumSet.allOf(BasinDepthModel.class), BasinDepthModel.CALIFORNIA, "Disabled");
	    basinDepthModelParam.setInfo("Model used for Z1.0 scaling, or disable to use the default "
	    		+ "Z1.0 model (in which case user supplied Z1.0 will also be ignored)");

		// the stdDevType Parameter
		StringConstraint stdDevTypeConstraint = new StringConstraint();
		stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_TOTAL);
		stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_NONE);
		stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_INTER);
		stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_INTRA);
		stdDevTypeConstraint.setNonEditable();
		stdDevTypeParam = new StdDevTypeParam(stdDevTypeConstraint);

		// add these to the list
		otherParams.addParameter(basinDepthModelParam);
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

	@Override
	public void setEqkRupture(EqkRupture eqkRupture) {
		this.eqkRupture = eqkRupture;
		magParam.setValueIgnoreWarning(eqkRupture.getMag());
		faultStyleParam.setValue(getFaultStyle(eqkRupture.getAveRake()));
		setPropagationEffectParams();
	}
	
	private void propEffectUpdate() {
		distanceRupParam.setValueIgnoreWarning(eqkRupture.getRuptureSurface().getDistanceRup(site.getLocation()));
	}

	static FaultStyle getFaultStyle(Double rake) {
		if (rake == null || Double.isNaN(rake))
			return FaultStyle.UNKNOWN;
		FaultUtils.assertValidRake(rake);
		if (rake >= 135 || rake <= -135)
			// right lateral
			return FaultStyle.STRIKE_SLIP;
		else if (rake >= -45 && rake <= 45)
			// left lateral
			return FaultStyle.STRIKE_SLIP;
		else if (rake >= 45 && rake <= 135)
			return FaultStyle.REVERSE;
		else
			return FaultStyle.NORMAL;
	}

	@Override
	public void setSite(Site site) throws ParameterException {
		this.site = site;
		vs30Param.setValueIgnoreWarning((Double) site.getParameter(Vs30_Param.NAME)
			.getValue());
		depthTo1pt0kmPerSecParam.setValueIgnoreWarning((Double) site.getParameter(
			DepthTo1pt0kmPerSecParam.NAME).getValue());
		setPropagationEffectParams();
	}

	@Override
	protected void setPropagationEffectParams() {
		if (site != null && eqkRupture != null) {
			propEffectUpdate();
		}
	}

	@Override
	public double getMean() {
		Coefficients c = coeffs.get(durParam.getTimeInterval());
		
		FaultStyle flt = faultStyleParam.getValue();
		double b0 = c.b0.get(flt);
		double b1 = c.b1.get(flt);
		double mag = magParam.getValue();
		double rRup = distanceRupParam.getValue();
		double vs30 = vs30Param.getValue();
		Double z1 = depthTo1pt0kmPerSecParam.getValue();
		if (z1 != null && Double.isNaN(z1))
			z1 = null;
		BasinDepthModel depthModel = basinDepthModelParam.getValue();
		
		// eqn 3;
		double eventTerm;
		if (mag > c.m1) {
			double m0 = Math.pow(10, 1.5*mag+16.05); // eqn 5
			double deltaSigma; // eqn 6
			if (mag <= c.m2)
				deltaSigma = Math.exp(b1 + c.b2*(mag - c.mStar));
			else
				deltaSigma = Math.exp(b1 + c.b2*(c.m2 - c.mStar) + c.b3*(mag - c.m2));
			double f0 = 4.9*1e6*3.2*Math.pow(deltaSigma/m0, 1d/3d); // eqn 4
			eventTerm = 1d/f0;
		} else {
			eventTerm = b0;
		}
		
		// eqn 7
		double pathTerm;
		if (rRup <= c.R1)
			pathTerm = c.c1*rRup;
		else if (rRup <= c.R2)
			pathTerm = c.c1*c.R1 + c.c2*(rRup - c.R1);
		else
			pathTerm = c.c1*c.R1 + c.c2*(c.R2 - c.R1) + c.c3*(rRup - c.R2);
		
		double delZ1;
		if (z1 == null || depthModel == null) {
			delZ1 = 0d;
		} else {
			double scalar, vsTerm;
			switch (depthModel) {
			case CALIFORNIA:
				// eqn 11
				scalar = -7.15/4d;
				vsTerm = (Math.pow(vs30, 4) + Math.pow(570.94, 4))/(Math.pow(1360, 4) + Math.pow(570.94, 4d));
				break;
			case JAPAN:
				// eqn 12
				scalar = -5.23/2d; // TODO: manuscript says /2, MATLAB impl says /4
				vsTerm = (Math.pow(vs30, 2) + Math.pow(412.39, 2))/(Math.pow(1360, 2) + Math.pow(412.39, 2d));
				break;

			default:
				throw new IllegalStateException("Unsupported basin model: "+depthModel);
			}
			// eqn 11/12
			double muZ1 = Math.exp(scalar*Math.log(vsTerm) - Math.log(1000));
			// eqn 10
			delZ1 = z1 - muZ1;
		}
		
		// eqn 9
		double fDelZ1;
		if (delZ1 <= c.delZ1ref)
			fDelZ1 = c.c5*delZ1;
		else
			fDelZ1 = c.c5*c.delZ1ref;
		
		// eqn 8
		double siteTerm;
		if (vs30 <= c.V1)
			siteTerm = c.c4*Math.log(vs30/c.Vref) + fDelZ1;
		else
			siteTerm = c.c4*Math.log(c.V1/c.Vref) + fDelZ1;
		
		// eqn 2
		return Math.log(eventTerm + pathTerm) + siteTerm;
	}

	@Override
	public double getStdDev() {
		Coefficients c = coeffs.get(durParam.getTimeInterval());
		
		double mag = magParam.getValue();
		
		// eqn 14
		double tau;
		if (mag < 6.5)
			tau = c.tau1;
		else if (mag < 7d)
			tau = c.tau1 + (c.tau2 - c.tau1)*(2d*(mag-6.5));
		else
			tau = c.tau2;
		
		// eqn 15
		double phi;
		if (mag < 5.5)
			phi = c.phi1;
		else if (mag < 5.75)
			phi = c.phi1 + (c.phi2 - c.phi1)*(4d*(mag-5.5));
		else
			phi = c.phi2;
		
		String stdDevType = stdDevTypeParam.getValue();
		switch (stdDevType) {
		case StdDevTypeParam.STD_DEV_TYPE_NONE:
			return 0d;
		case StdDevTypeParam.STD_DEV_TYPE_TOTAL:
			// eqn 13
			return Math.sqrt(tau*tau + phi*phi);
		case StdDevTypeParam.STD_DEV_TYPE_INTER:
			return tau;
		case StdDevTypeParam.STD_DEV_TYPE_INTRA:
			return phi;

		default:
			throw new IllegalStateException("Unsupported std dev type: "+stdDevType);
		}
	}

}
