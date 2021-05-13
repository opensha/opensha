package org.opensha.nshmp2.imr;

import static org.opensha.sha.imr.PropagationEffect.*;

import java.util.List;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.IMRException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.nshmp2.util.NSHMP_IMR_Util;
import org.opensha.nshmp2.util.Period;
import org.opensha.nshmp2.util.Utils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.rupForecastImpl.PointEqkSource;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.BA_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CY_2008_AttenRel;
import org.opensha.sha.imr.param.EqkRuptureParams.DipParam;
import org.opensha.sha.imr.param.EqkRuptureParams.FaultTypeParam;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupTopDepthParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistRupMinusDistX_OverRupParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistRupMinusJB_OverRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

//@formatter:off
/**
 * This is an implementation of the combined attenuation relationships used 
 * in California and the Western US for the 2008 National Seismic Hazard Mapping
 * Program (NSHMP). The three next generation attenuation relationships (NGAs)
 * used are:
 * <ul>
 * <li>{@link BA_2008_AttenRel Boore &amp; Atkinson (2008)}</li>
 * <li>{@link CB_2008_AttenRel Cambell &amp; Bozorgnia (2008)}</li>
 * <li>{@link CY_2008_AttenRel Chiou &amp; Youngs (2008)}</li>
 * </ul>
 * Each attenuation relationship gets 1/3 weight.
 * 
 * <p>As with other NSHMP attenutation relationships, this may only be used via
 * {@code setSite()} and {@code setEqkRupture()} as the calculations are
 * {@code PropagationEffect} dependent.</p>
 * 
 * <p><b>Additional Epistemic Uncertainty</b></p>
 * <p>Additional epistemic uncertainty is considered for each NGA according to
 * the following distance and magnitude matrix:
 * <pre>
 *             M<6      6%le;M<7      7&le;M
 *          =============================
 *   D<10     0.375  |  0.230  |  0.400v
 * 10&le;D<30    0.210  |  0.225  |  0.360
 *   30&le;D     0.245  |  0.230  |  0.310
 *          =============================
 * </pre>
 * For an earthquake rupture at a given distance and magnitude, the
 * corresponding uncertainty is applied to a particular NGA with the following
 * weights:
 * <pre>
 *     hazard curve           weight
 * ======================================
 *      mean + unc            0.185
 *      mean                  0.630
 *      mean - unc            0.185
 * ======================================
 * </pre>
 * 
 * @author Peter Powers
 * @version $Id:$
 */
//@formatter:on
public class NSHMP08_WUS extends AttenuationRelationship implements
		ParameterChangeListener {

	public final static String NAME = "NSHMP 2008 Western US Combined";
	public final static String SHORT_NAME = "NSHMP08_WUS";
	private static final long serialVersionUID = 1L;

	// this is the minimum range of vs30 spanned by BA, CB, & CY (the NGA's)
	private final static double VS30_WARN_MIN = 80;
	private final static double VS30_WARN_MAX = 1300;

	// imr weight maps
	Map<ScalarIMR, Double> imrMap;
	
	// custom params
	public static final String IMR_UNCERT_PARAM_NAME = "IMR uncertainty";
	private boolean includeImrUncert = true;
	private static final String HW_EFFECT_PARAM_NAME = "Hanging Wall Effect Approx.";
	private boolean hwEffectApprox = true;
	
	public NSHMP08_WUS() {
		initImrMap();
		
		// these methods are called for each attenRel upon construction; we
		// do some local cloning so that a minimal set of params may be
		// exposed and modified in gui's and/or to ensure some parameters
		// adhere to NSHMP values
		initSupportedIntensityMeasureParams();
		initEqkRuptureParams();
		initPropagationEffectParams();
		initSiteParams();
		initOtherParams();
		initParameterEventListeners();
		
		setParamDefaults();
	}
		
	void initImrMap() {
		imrMap = Maps.newHashMap();
		imrMap.put(new NSHMP_BA_2008(), 0.3333);
		imrMap.put(new NSHMP_CB_2008(), 0.3333);
		imrMap.put(new NSHMP_CY_2008(), 0.3334);
	}
	
	@Override
	public void setUserMaxDistance(double maxDist) {
		for (ScalarIMR imr : imrMap.keySet()) {
			imr.setUserMaxDistance(maxDist);
		}
	}

	@Override
	public void setParamDefaults() {
		
		// The individual subclassed imrs call their own setParamDefaults();
		// these default overrides then propagate via parameterChanges to child
		// IMRs. this.parameterChange makes changes to child IMR params, which
		// in turn trigger their local parameterChange calls. This is done
		// because not all imrs adhere to a standard of picking up new values
		// from the parameterChangeEvent, some refer to the parameter itself.
		
		vs30Param.setValueAsDefault();
		
		pgaParam.setValueAsDefault();
		saParam.setValueAsDefault();
		saPeriodParam.setValueAsDefault();
		saDampingParam.setValueAsDefault();
		
		componentParam.setValueAsDefault();
		stdDevTypeParam.setValueAsDefault();

		sigmaTruncTypeParam.setValueAsDefault();
		sigmaTruncLevelParam.setValueAsDefault();
		
		magParam.setValueAsDefault();
		fltTypeParam.setValueAsDefault();
		rupTopDepthParam.setValueAsDefault();
		dipParam.setValueAsDefault();

		distanceJBParam.setValueAsDefault();
		distanceRupParam.setValueAsDefault();
		distRupMinusJB_OverRupParam.setValueAsDefault();
		distRupMinusDistX_OverRupParam.setValueAsDefault();

		depthTo2pt5kmPerSecParam.setValueAsDefault();
		depthTo1pt0kmPerSecParam.setValueAsDefault();
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	protected void initSupportedIntensityMeasureParams() {
		
		List<Double> perVals = Lists.newArrayList();
		for (Period p : Period.getWUS()) {
			perVals.add(p.getValue());
		}
		DoubleDiscreteConstraint periodConstraint = new DoubleDiscreteConstraint(
			perVals);
		periodConstraint.setNonEditable();
		saPeriodParam = new PeriodParam(periodConstraint, 1.0, false);
		saDampingParam = new DampingParam();
		saParam = new SA_Param(saPeriodParam, saDampingParam);
		saParam.setNonEditable();

		//  Create PGA Parameter (pgaParam):
		pgaParam = new PGA_Param();
		pgaParam.setNonEditable();

		// Put parameters in the supportedIMParams list:
		supportedIMParams.clear();
		supportedIMParams.addParameter(saParam);
		supportedIMParams.addParameter(pgaParam);
	}

	@Override
	protected void initSiteParams() {
		siteParams.clear();
		
		vs30Param = new Vs30_Param(VS30_WARN_MIN, VS30_WARN_MAX);
		siteParams.addParameter(vs30Param);
		
		// Campbell & Bozorgnia hidden
		depthTo2pt5kmPerSecParam = new DepthTo2pt5kmPerSecParam(null, 0.0, 10.0, true);
		depthTo2pt5kmPerSecParam.setValueAsDefault();
		// depthTo2pt5kmPerSecParam.getEditor().setVisible(false);
		siteParams.addParameter(depthTo2pt5kmPerSecParam);

		// Chiou & Youngs hidden
		depthTo1pt0kmPerSecParam = new DepthTo1pt0kmPerSecParam(null, true);
		depthTo1pt0kmPerSecParam.setValueAsDefault();
		// depthTo1pt0kmPerSecParam.getEditor().setVisible(false);
		siteParams.addParameter(depthTo1pt0kmPerSecParam);
		
		vs30_TypeParam = new Vs30_TypeParam();
		// vs30_TypeParam.getEditor().setVisible(false);
		siteParams.addParameter(vs30_TypeParam);

	}

	@Override
	protected void initOtherParams() {
		super.initOtherParams();
		
		// the Component Parameter - common to NGA's but uneditable
	    // first is default, the rest are all options (including default)
	    componentParam = new ComponentParam(Component.GMRotI50, Component.GMRotI50);
		componentParam.setValueAsDefault();
		
		// the stdDevType Parameter - common to NGA's
		StringConstraint stdDevTypeConstraint = new StringConstraint();
		stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_TOTAL);
		stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_NONE);
		stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_INTER);
		stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_INTRA);
		stdDevTypeConstraint.setNonEditable();
		stdDevTypeParam = new StdDevTypeParam(stdDevTypeConstraint);
		stdDevTypeParam.setValueAsDefault();
		
		// allow toggling of AttenRel epistemic uncertainty
		BooleanParameter imrUncertParam = new BooleanParameter(
			IMR_UNCERT_PARAM_NAME, includeImrUncert);
		
		// allow toggling of hanging wall effect approximation
		BooleanParameter hwEffectApproxParam = new BooleanParameter(
			HW_EFFECT_PARAM_NAME, hwEffectApprox);

		// add these to the list
		otherParams.addParameter(componentParam);
		otherParams.addParameter(stdDevTypeParam);
		otherParams.addParameter(imrUncertParam);
		otherParams.addParameter(hwEffectApproxParam);
		
		sigmaTruncTypeParam.setValue(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED);
		sigmaTruncLevelParam.setValue(3.0);
		
		imrUncertParam.addParameterChangeListener(this);
		hwEffectApproxParam.addParameterChangeListener(this);
		
		// enforce default values used by NSHMP
		for (ScalarIMR imr : imrMap.keySet()) {
			ParameterList list = imr.getOtherParams();

			ComponentParam cp = (ComponentParam) list.getParameter(
				ComponentParam.NAME);
			cp.setValue(Component.GMRotI50);

			StdDevTypeParam stp = (StdDevTypeParam) list.getParameter(
				StdDevTypeParam.NAME);
			stp.setValue(StdDevTypeParam.STD_DEV_TYPE_TOTAL);

			SigmaTruncTypeParam sttp = (SigmaTruncTypeParam) list.getParameter(
				SigmaTruncTypeParam.NAME);
			sttp.setValue(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED);

			SigmaTruncLevelParam stlp = (SigmaTruncLevelParam) list.getParameter(
				SigmaTruncLevelParam.NAME);
			stlp.setValue(3.0);
		}
	}

	@Override
	protected void initEqkRuptureParams() {
		magParam = new MagParam(5.0, 8.0); // BA limits
		dipParam = new DipParam(15, 90); // CB limits
		rupTopDepthParam = new RupTopDepthParam(0.0, 15.0); // CB limits

		StringConstraint constraint = new StringConstraint();
		constraint.addString(CB_2008_AttenRel.FLT_TYPE_STRIKE_SLIP);
		constraint.addString(CB_2008_AttenRel.FLT_TYPE_NORMAL);
		constraint.addString(CB_2008_AttenRel.FLT_TYPE_REVERSE);
		constraint.setNonEditable();
		fltTypeParam = new FaultTypeParam(constraint, CB_2008_AttenRel.FLT_TYPE_STRIKE_SLIP);
		
		eqkRuptureParams.clear();
		eqkRuptureParams.addParameter(magParam);
		eqkRuptureParams.addParameter(fltTypeParam);
		eqkRuptureParams.addParameter(dipParam);
		eqkRuptureParams.addParameter(rupTopDepthParam);
	}

	@Override
	protected void initPropagationEffectParams() {
		distanceJBParam = new DistanceJBParameter(0.0);
		DoubleConstraint warn = new DoubleConstraint(0.0, 200.0);
		distanceJBParam.setWarningConstraint(warn);
		distanceJBParam.setNonEditable();
		
		distanceRupParam = new DistanceRupParameter(0.0);
		distanceRupParam.setWarningConstraint(warn);
		distanceRupParam.setNonEditable();

		distRupMinusJB_OverRupParam = new DistRupMinusJB_OverRupParameter(0.0);
		DoubleConstraint warnJB = new DoubleConstraint(0.0, 50.0); // CB limits
		distRupMinusJB_OverRupParam.setWarningConstraint(warnJB);
		distRupMinusJB_OverRupParam.setNonEditable();

		distRupMinusDistX_OverRupParam = new DistRupMinusDistX_OverRupParam();

		propagationEffectParams.addParameter(distanceJBParam);
		propagationEffectParams.addParameter(distanceRupParam);
		propagationEffectParams.addParameter(distRupMinusJB_OverRupParam);
		propagationEffectParams.addParameter(distRupMinusDistX_OverRupParam);
	}

	@Override
	protected void initParameterEventListeners() {

		distanceJBParam.addParameterChangeListener(this);
		vs30Param.addParameterChangeListener(this);
		magParam.addParameterChangeListener(this);
		fltTypeParam.addParameterChangeListener(this);
		stdDevTypeParam.addParameterChangeListener(this);
		saPeriodParam.addParameterChangeListener(this);

		vs30_TypeParam.addParameterChangeListener(this);
		rupTopDepthParam.addParameterChangeListener(this);
		dipParam.addParameterChangeListener(this);
		depthTo1pt0kmPerSecParam.addParameterChangeListener(this);
		depthTo2pt5kmPerSecParam.addParameterChangeListener(this);
		distanceRupParam.addParameterChangeListener(this);
		distRupMinusJB_OverRupParam.addParameterChangeListener(this);
		distRupMinusDistX_OverRupParam.addParameterChangeListener(this);

		sigmaTruncTypeParam.addParameterChangeListener(this);
		sigmaTruncLevelParam.addParameterChangeListener(this);
		tectonicRegionTypeParam.addParameterChangeListener(this);
		componentParam.addParameterChangeListener(this);
		stdDevTypeParam.addParameterChangeListener(this);
	}

	@Override
	public void setSite(Site site) {
		this.site = site;
		
		// NOTE being done to satisfy unit tests HUH??????????
		vs30Param.setValueIgnoreWarning((Double) site.getParameter(
			Vs30_Param.NAME).getValue());
		
		for (ScalarIMR imr : imrMap.keySet()) {
			imr.setSite(site);
		}
		setPropagationEffectParams();
	}
	
	@Override
	public void setEqkRupture(EqkRupture eqkRupture) {
		this.eqkRupture = eqkRupture;
		magParam.setValueIgnoreWarning(eqkRupture.getMag()); // needed at getExceedProbs()
		for (ScalarIMR imr : imrMap.keySet()) {
			imr.setEqkRupture(eqkRupture);
		}
		setPropagationEffectParams();
	}
	
	@Override
	protected void setPropagationEffectParams() {
		if (site != null && eqkRupture != null) {
			distanceJBParam.setValue(eqkRupture, site);
		}
	}
	
	// scratch
	public String getEpsilonDataVals() {
		String data = "";
		for (ScalarIMR imr : imrMap.keySet()) {
			data += imr.getShortName() + " " + im.getValue() + " " + imr.getMean() + " " + imr.getStdDev() + "\n";
		}
		return data;
	}
	
	//sratch
	public String getPEs() {
		String data = "";
		for (ScalarIMR imr : imrMap.keySet()) {
			double mean = imr.getMean();
			double std = imr.getStdDev();
			double iml = ((Double) im.getValue()).doubleValue();
			double pe1 = ((AttenuationRelationship) imr).getExceedProbability();
			double pe2 = Utils.getExceedProbability(iml, mean, std, false, 0.0);
			data += imr.getShortName() + " " + pe1 + " " + pe2 + "\n";
		}
		return data;
	}
	

	
	@Override
	public double getMean() {
		// tmp KLUDGY for Nico
		double mean = 0;
		for (ScalarIMR imr : imrMap.keySet()) {
			mean += 0.3333 * Math.exp(imr.getMean());
		}
		return Math.log(mean);
//		throw new UnsupportedOperationException();
	}

	@Override
	public double getStdDev() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getEpsilon() {
		// NOTE experimental, redundant mean & sigma calcs
		
		// set mean, sigma, and weight arrays
		int curveCount = imrMap.size();
		if (includeImrUncert) curveCount *= EPI_CT;
		double[] means = new double[curveCount];
		double[] sigmas = new double[curveCount];
		double[] weights = new double[curveCount];
		
		int idx = 0;
		for (ScalarIMR imr : imrMap.keySet()) {
			double m = imr.getMean();
			double s = imr.getStdDev();
			double w = imrMap.get(imr);
			if (includeImrUncert) {
				double mag = magParam.getValue();
				double dist = distanceJBParam.getValue();
				double epiVal = getUncertainty(mag, dist);
				for (int i=0; i<EPI_CT; i++) {
					means[idx] = m + epiVal * EPI_SIGN[i];
					weights[idx] = w * EPI_WT[i];
					sigmas[idx] = s;
					idx++;
				}
			} else {
				means[idx] = m;
				sigmas[idx] = s;
				weights[idx] = w;
				idx++;
			}
		}
		
		// get and sum values
		double iml = ((Double) im.getValue()).doubleValue();
		double epsilon = 0.0;
		for (int i=0; i<means.length; i++) {
			epsilon += ((iml - means[i]) / sigmas[i]) * weights[i];
		}
		return epsilon;
	}

	@Override
	public double getEpsilon(double iml) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls)
			throws ParameterException {
		
		// set mean, sigma, and weight arrays
		int curveCount = imrMap.size();
		if (includeImrUncert) curveCount *= EPI_CT;
		double[] means = new double[curveCount];
		double[] sigmas = new double[curveCount];
		double[] weights = new double[curveCount];
		
		int idx = 0;
//		System.out.println(buildParamString());
		for (ScalarIMR imr : imrMap.keySet()) {
			double m = imr.getMean();
			double s = imr.getStdDev();
			double w = imrMap.get(imr);
//			System.out.println(imr.getShortName() + " " + String.format("%.3f", Math.log(m)) + " " + String.format("%.3f", s));
			if (includeImrUncert) {
				double mag = magParam.getValue();
				double dist = distanceJBParam.getValue();
				double epiVal = getUncertainty(mag, dist);
				for (int i=0; i<EPI_CT; i++) {
					means[idx] = m + epiVal * EPI_SIGN[i];
					weights[idx] = w * EPI_WT[i];
					sigmas[idx] = s;
					idx++;
				}
			} else {
				means[idx] = m;
				sigmas[idx] = s;
				weights[idx] = w;
				idx++;
			}
		}

		// get and sum curves
		Utils.zeroFunc(imls);
		DiscretizedFunc f = imls.deepClone();
		for (int i=0; i<means.length; i++) {
			f = Utils.getExceedProbabilities(f, means[i], sigmas[i], false, 0.0);
			f.scale(weights[i]);
			Utils.addFunc(imls, f);
		}
		return imls;
	}
	
	private String buildParamString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		sb.append("mag=").append(String.format("%.3f", eqkRupture.getMag())).append(", ");
		sb.append("rjb=").append(String.format("%.3f", eqkRupture.getRuptureSurface().getDistanceJB(site.getLocation()))).append(", ");
		sb.append("rrup=").append(String.format("%.3f", eqkRupture.getRuptureSurface().getDistanceRup(site.getLocation()))).append(", ");
		sb.append("rx=").append(String.format("%.3f", eqkRupture.getRuptureSurface().getDistanceX(site.getLocation()))).append(", ");
		sb.append("dip=").append(String.format("%.3f", eqkRupture.getRuptureSurface().getAveDip())).append(", ");
		sb.append("width=").append(String.format("%.3f", eqkRupture.getRuptureSurface().getAveWidth())).append(", ");
		sb.append("}");
		return sb.toString();
	}
	
	@Override
	public double getExceedProbability() throws ParameterException,
			IMRException {
		// NOTE experimental, redundant mean & sigma calcs

		// set mean, sigma, and weight arrays
		int curveCount = imrMap.size();
		if (includeImrUncert) curveCount *= EPI_CT;
		double[] means = new double[curveCount];
		double[] sigmas = new double[curveCount];
		double[] weights = new double[curveCount];
		
		int idx = 0;
		for (ScalarIMR imr : imrMap.keySet()) {
			double m = imr.getMean();
			double s = imr.getStdDev();
			double w = imrMap.get(imr);
			if (includeImrUncert) {
				double mag = magParam.getValue();
				double dist = distanceJBParam.getValue();
				double epiVal = getUncertainty(mag, dist);
				for (int i=0; i<EPI_CT; i++) {
					means[idx] = m + epiVal * EPI_SIGN[i];
					weights[idx] = w * EPI_WT[i];
					sigmas[idx] = s;
					idx++;
				}
			} else {
				means[idx] = m;
				sigmas[idx] = s;
				weights[idx] = w;
				idx++;
			}
		}

		// get and sum values
		double pe = 0.0;
		for (int i=0; i<means.length; i++) {
			pe += Utils.getExceedProbability(
				Math.exp((Double) im.getValue()), means[i], sigmas[i], false, 0.0) * weights[i];
		}
		return pe;
	}

	@Override
	protected double getExceedProbability(double mean, double stdDev, double iml)
			throws ParameterException, IMRException {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getExceedProbability(double iml) throws ParameterException,
			IMRException {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getIML_AtExceedProb() throws ParameterException {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getIML_AtExceedProb(double exceedProb)
			throws ParameterException {
		throw new UnsupportedOperationException();
	}

	@Override
	public DiscretizedFunc getSA_ExceedProbSpectrum(double iml)
			throws ParameterException, IMRException {
		throw new UnsupportedOperationException();
	}

	@Override
	public DiscretizedFunc getSA_IML_AtExceedProbSpectrum(double exceedProb)
			throws ParameterException, IMRException {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getTotExceedProbability(PointEqkSource ptSrc, double iml) {
		throw new UnsupportedOperationException("getTotExceedProbability is unsupported for "+C);
	}

	@Override
	public void setIntensityMeasureLevel(Double iml) throws ParameterException {
		for (ScalarIMR ar : imrMap.keySet()) {
			ar.setIntensityMeasureLevel(iml);
		}
	}

	@Override
	public void setIntensityMeasureLevel(Object iml) throws ParameterException {
		for (ScalarIMR ar : imrMap.keySet()) {
			ar.setIntensityMeasureLevel(iml);
		}
	}

	@Override
	public void setIntensityMeasure(String intensityMeasureName)
			throws ParameterException {
		super.setIntensityMeasure(intensityMeasureName);
		for (ScalarIMR ar : imrMap.keySet()) {
			ar.setIntensityMeasure(intensityMeasureName);
		}
	}

	@Override
	public void parameterChange(ParameterChangeEvent e) {
		
		// pass through changes to params we know nga's are listeneing to
		for (ScalarIMR ar : imrMap.keySet()) {
			try {
				Parameter p = ar.getParameter(e.getParameterName());
				Object value = e.getNewValue();
				// TODO THIS TOTALLY SUCKS
				if (p instanceof WarningParameter) {
					((WarningParameter) p).setValueIgnoreWarning(value);
				} else {
					p.setValue(value);
				}
			} catch (ParameterException pe) {
				// do nothing; skipping non-existent parameter in child imr's
			}
		}
		
		// pass through those changes that are picked up at calculation time
		if (otherParams.containsParameter(e.getParameter())) {
			for (ScalarIMR ar : imrMap.keySet()) {
				ParameterList pList = ar.getOtherParams();
				String pName = e.getParameterName();
				if (!pList.containsParameter(pName)) continue;
				// TODO above shouldn't be necessary; pLists should not throw
				// exceptions fo missing parameters
				Parameter<?> param = ar.getOtherParams().getParameter(
					e.getParameterName());
				if (param instanceof StringParameter) {
					((StringParameter) param).setValue((String) e
						.getParameter().getValue());
				} else {
					((DoubleParameter) param).setValue((Double) e
						.getParameter().getValue());
				}
			}
		}
		
		// handle SA period change; this is picked up independently by atten
		// rels at calculation time so changes here need to be transmitted to
		// children
		if (e.getParameterName().equals(PeriodParam.NAME)) {
			for (ScalarIMR ar : imrMap.keySet()) {
				ParameterList pList = ar.getSupportedIntensityMeasures();
				SA_Param sap = (SA_Param) pList.getParameter(SA_Param.NAME);
				sap.getPeriodParam().setValue(saPeriodParam.getValue());
			}
		}
		
		// handle locals
		if (e.getParameterName().equals(IMR_UNCERT_PARAM_NAME)) {
			includeImrUncert = (Boolean) e.getParameter().getValue();
		}
		
		// handle locals
		if (e.getParameterName().equals(HW_EFFECT_PARAM_NAME)) {
			hwEffectApprox = (Boolean) e.getParameter().getValue();
		}
		
	}

	private static final int EPI_CT = 3;
	private static final double[] EPI_SIGN = {-1.0, 0.0, 1,0};
	private static final double[] EPI_WT = {0.185, 0.630, 0.185};
	private static final double[][] EPI_VAL = {
		{0.375, 0.230, 0.400},
		{0.210, 0.225, 0.360},
		{0.245, 0.230, 0.310}};

	/*
	 * Returns the epistemic uncertainty for the supplied magnitude (M) and
	 * distance (D) that
	 */
	private static double getUncertainty(double M, double D) {
		int mi = (M<6) ? 0 : (M<7) ? 1 : 2;
		int di = (D<10) ? 0 : (D<30) ? 1 : 2;
		return EPI_VAL[di][mi];
	}
	
	/*
	 * Returns the NSHMP interpretation of fault type based on rake; divisions
	 * on 45 deg. diagonals. KLUDGY because the String 'constraints' for the
	 * faultTypeParam are the same for all 3 NGA's we just use Campbells
	 * String identifiers.
	 */
	private String getFaultTypeForRake(double rake) {
		if (rake >= 45 && rake <= 135) return CB_2008_AttenRel.FLT_TYPE_REVERSE;
		if (rake >= -135 && rake <= -45) return CB_2008_AttenRel.FLT_TYPE_NORMAL;
		return CB_2008_AttenRel.FLT_TYPE_STRIKE_SLIP;
	}
	
	public class NSHMP_BA_2008 extends BA_2008_AttenRel {
		
		public NSHMP_BA_2008() {
			super(null);
			setParamDefaults();
		}
		
		@Override
		public void setFaultTypeFromRake(double rake) {
			fltTypeParam.setValue(getFaultTypeForRake(rake));
		}
	}
	
	public class NSHMP_CB_2008 extends CB_2008_AttenRel {
		
		public NSHMP_CB_2008() {
			super(null);
			setParamDefaults();
		}
		
		@Override
		public void setFaultTypeFromRake(double rake) {
			fltTypeParam.setValue(getFaultTypeForRake(rake));
		}
		
		@Override
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
			fhng = 0;
			if (hwEffectApprox && (eqkRupture == null || (eqkRupture.getRuptureSurface() instanceof PointSurface))) {
				double hwScale = f_rv + f_nm; // should never both be > 0
				if (hwScale > 0) {
					fhng = NSHMP_IMR_Util.getAvgHW_CB(mag, distJB, per[iper]) * hwScale;
				}
			} else {
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
			}
	
			//modeling dependence on linear and non-linear site conditions
			if(vs30< k1[iper])
				fsite = 	c10[iper]*Math.log(vs30/k1[iper]) +
				k2[iper]*(Math.log(pga_rock+c*Math.pow(vs30/k1[iper],n)) - Math.log(pga_rock+c));
			else if (vs30<1100) {
				fsite = (c10[iper]+k2[iper]*n)*Math.log(vs30/k1[iper]);
			} else 
				fsite = (c10[iper]+k2[iper]*n)*Math.log(1100/k1[iper]);
	
			//modeling dependence on shallow sediments effects and 3-D basin effects
			if(depthTo2pt5kmPerSec<1)
				fsed = c11[iper]*(depthTo2pt5kmPerSec - 1);
			else if(depthTo2pt5kmPerSec <=3)
				fsed = 0;
			else
				fsed = c12[iper]*k3[iper]*Math.exp(-0.75)*(1-Math.exp(-0.25*(depthTo2pt5kmPerSec-3)));
	
//			 System.out.println(mean+"\t"+iper+"\t"+vs30+"\t"+rRup+"\t"+rJB+"\t"+f_rv+"\t"+f_nm+"\t"+mag+"\t"+dip+"\t"+depthTop+"\t"+depthTo2pt5kmPerSec+"\t"+magSaturation+"\t"+pga_rock);
//			System.out.println("f1: " + fmag+" f2: " + fdis+" f3: " + fflt+" f4: " + fhng+" f5: " + fsite+" f6: " + fsed);
			return fmag+fdis+fflt+fhng+fsite+fsed;
		}
		
	}

	public class NSHMP_CY_2008 extends CY_2008_AttenRel {
		
		public NSHMP_CY_2008() {
			super(null);
			setParamDefaults();
		}
		
		@Override
		public void setFaultTypeFromRake(double rake) {
			fltTypeParam.setValue(getFaultTypeForRake(rake));
		}
		
		@Override
		protected void compute_lnYref(int iper, double f_rv, double f_nm, double rRup, double distRupMinusJB_OverRup,
			double distRupMinusDistX_OverRup, double f_hw, double dip, double mag, double depthTop, double aftershock) {
			
			// compute rJB
			double distanceJB = rRup - distRupMinusJB_OverRup*rRup;
			double distX  = rRup - distRupMinusDistX_OverRup*rRup;
			// System.out.println(depthTop);
	
			double cosDelta = Math.cos(dip*Math.PI/180);
			double altDist = Math.sqrt(distanceJB*distanceJB+depthTop*depthTop);
	
			// point source hw effect approximation
			double hw_effect = 0;
			if (hwEffectApprox && (eqkRupture == null || (eqkRupture.getRuptureSurface() instanceof PointSurface))) {
				double hwScale = f_rv + f_nm; // should never both be > 0
				if (hwScale > 0) {
//					System.out.println("hwScale: " + hwScale);
					hw_effect = NSHMP_IMR_Util.getAvgHW_CY(mag, distanceJB, period[iper]) * hwScale;
				}
			} else {
				hw_effect = c9[iper] * f_hw * Math.tanh(distX*cosDelta*cosDelta/c9a[iper]) * (1-altDist/(rRup+0.001));
			}
			
			// restructured to mirror nshmp for debugging
			double r1 = c1[iper] + c2*(mag-6.0) + ((c2-c3)/cn[iper])*Math.log(1.0 + Math.exp(cn[iper]*(cm[iper]-mag)));
			
			double r2 = c4*Math.log(rRup+c5[iper]*Math.cosh(c6[iper]*Math.max(mag-chm,0)));
			
			double r3 = (c4a-c4)*0.5*Math.log(rRup*rRup+crb*crb) + (cg1[iper] + cg2[iper]/Math.cosh(Math.max(mag-cg3,0.0)))*rRup;
			
			
			double r4 = (c1a[iper]*f_rv+c1b[iper]*f_nm+c7[iper]*(depthTop-4.0))*(1-aftershock);
			
			double r5 = (c10[iper]+c7a[iper]*(depthTop-4.0))*aftershock;
			
			lnYref = r1 + r2 + r3 + r4 + r5 + hw_effect;
			
//			System.out.println("lnYref: " + lnYref  + " r1: " + r1+" r2: " + r2+" r3: " + r3+" r4: " + r4+" r5: " + r5 + " hw: " + hw_effect);

//			lnYref = c1[iper] + (c1a[iper]*f_rv+c1b[iper]*f_nm+c7[iper]*(depthTop-4.0))*(1-aftershock) +
//			(c10[iper]+c7a[iper]*(depthTop-4.0))*aftershock +
//			c2*(mag-6.0) + ((c2-c3)/cn[iper])*Math.log(1.0 + Math.exp(cn[iper]*(cm[iper]-mag))) +
//			c4*Math.log(rRup+c5[iper]*Math.cosh(c6[iper]*Math.max(mag-chm,0))) +
//			(c4a-c4)*0.5*Math.log(rRup*rRup+crb*crb) + 
//			(cg1[iper] + cg2[iper]/Math.cosh(Math.max(mag-cg3,0.0)))*rRup +
//			hw_effect;
			
			lnYref_is_not_fresh = false;
	
	//		double Fhw = c9[iper] * f_hw * Math.tanh(distX*cosDelta*cosDelta/c9a[iper]) * (1-altDist/(rRup+0.001));
	//		DecimalFormat df1 = new DecimalFormat("#.######");
	//		DecimalFormat df2 = new DecimalFormat("#.##");
	//		System.out.println(df2.format(mag) + " " + df1.format(rRup) + " " + df1.format(distX) + " " + df1.format(Fhw) + " " + f_hw);
			//		System.out.println(rRup+"\t"+distanceJB+"\t"+distX+"\t"+f_hw+"\t"+lnYref);

		}

	}
	
}
