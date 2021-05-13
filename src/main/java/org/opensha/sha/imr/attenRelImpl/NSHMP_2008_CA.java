package org.opensha.sha.imr.attenRelImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.IMRException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.rupForecastImpl.PointEqkSource;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;
import org.opensha.sha.util.NSHMP_Util;

/**
 * This is an implementation of the combined attenuation relationships used in
 * California for the 2008 National Seismic Hazard Mapping Program (NSHMP). The
 * three next generation attenuation relationships (NGAs) used are:
 * <ul>
 * <li>{@link BA_2008_AttenRel Boore &amp; Atkinson (2008)}</li>
 * <li>{@link CB_2008_AttenRel Cambell &amp; Bozorgnia (2008)}</li>
 * <li>{@link CY_2008_AttenRel Chiou &amp; Youngs (2008)}</li>
 * </ul>
 * Please take note of the following implementation details:
 * <ul>
 * <li>Each NGA is given 1/3 weight.</li>
 * <li>Epistemic uncertainties are considered for each NGA (see below).</li>
 * <li></li>
 * <li></li>
 * <li></li>
 * <li></li>
 * <li></li>
 * <li></li>
 * <li></li>
 * </ul>
 * 
 * 
 * <b>Additional Epistemic Uncertainty</b><br />
 * Additional epistemic uncertainty is considered for each NGA according to the
 * following distance and magnitude matrix:
 * <pre>
 *             M<6      6%le;M<7      7&le;M
 *          =============================
 *   D<10     0.375  |  0.210  |  0.245
 * 10&le;D<30    0.230  |  0.225  |  0.230
 *   30&le;D     0.400  |  0.360  |  0.310
 *          =============================
 * </pre>
 * For an earthquake rupture at a given distance and magnitude, the corresponding
 * uncertainty is applied to a particular NGA with the following weights:
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
public class NSHMP_2008_CA extends AttenuationRelationship implements
ParameterChangeListener {

	public final static String NAME = "NSHMP 2008 California Combined";
	public final static String SHORT_NAME = "NSHMP_2008_CA";
	private static final long serialVersionUID = 1L;

	// Wrapped Attenuation Relationship instances
	private NSHMP_BA_2008 ba08;
	private NSHMP_CB_2008 cb08;
	private NSHMP_CY_2008 cy08;
	private List<AttenuationRelationship> arList;

	// this is the minimum range of vs30 spanned by BA, CB, & CY
	private final static double VS30_WARN_MIN = 80;
	private final static double VS30_WARN_MAX = 1300;
	
	// custom params
	public static final String IMR_UNCERT_PARAM_NAME = "IMR uncertainty";
	private boolean includeImrUncert = false;
	private static final String HW_EFFECT_PARAM_NAME = "Hanging Wall Effect Approx.";
	private boolean hwEffectApprox = true;

	// flags and tuning values
	private double imrUncert = 0.0;
	
	public NSHMP_2008_CA(ParameterChangeWarningListener listener) {

		ba08 = new NSHMP_BA_2008(listener);
		cb08 = new NSHMP_CB_2008(listener);
		cy08 = new NSHMP_CY_2008(listener);

		arList = new ArrayList<AttenuationRelationship>();
		arList.add(ba08);
		arList.add(cb08);
		arList.add(cy08);

		// these methods are called for each attenRel upon construction; we
		// do some local cloning so that a minimal set of params may be
		// exposed and modified in gui's and/or to ensure some parameters
		// adhere to NSHMP values
		initSupportedIntensityMeasureParams();
		initSiteParams();
		initOtherParams();
		initParameterEventListeners();
	}

	@Override
	public void setParamDefaults() {
		
		vs30Param.setValueAsDefault();
		depthTo2pt5kmPerSecParam.setValueAsDefault();
		depthTo1pt0kmPerSecParam .setValueAsDefault();
		
		saParam.setValueAsDefault();
		saPeriodParam.setValueAsDefault();
		saDampingParam.setValueAsDefault();
		
		pgaParam.setValueAsDefault();
		pgvParam.setValueAsDefault();
		
		componentParam.setValueAsDefault();
		stdDevTypeParam.setValueAsDefault();

		sigmaTruncTypeParam.setValueAsDefault();
		sigmaTruncLevelParam.setValueAsDefault();
		
		for (ScalarIMR ar : arList) {
			ar.setParamDefaults();
		}
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
	protected void setPropagationEffectParams() {}

	@Override
	protected void initSupportedIntensityMeasureParams() {
		// clone periods from ba08
		DoubleDiscreteConstraint periodConstraint = new DoubleDiscreteConstraint(
			((SA_Param) ba08.getSupportedIntensityMeasures().getParameter(
				SA_Param.NAME)).getPeriodParam().getAllowedDoubles());
		periodConstraint.setNonEditable();
		saPeriodParam = new PeriodParam(periodConstraint, 1.0, false);
		saPeriodParam.addParameterChangeListener(this);
		saDampingParam = new DampingParam();
		saParam = new SA_Param(saPeriodParam, saDampingParam);
		saParam.setNonEditable();

		//  Create PGA Parameter (pgaParam):
		pgaParam = new PGA_Param();
		pgaParam.setNonEditable();

		//  Create PGV Parameter (pgvParam):
		pgvParam = new PGV_Param();
		pgvParam.setNonEditable();

		// Add the warning listeners: TODO clean?
//		saParam.addParameterChangeWarningListener(listener);
//		pgaParam.addParameterChangeWarningListener(listener);
//		pgvParam.addParameterChangeWarningListener(listener);

		// Put parameters in the supportedIMParams list:
		supportedIMParams.clear();
		supportedIMParams.addParameter(saParam);
		supportedIMParams.addParameter(pgaParam);
		supportedIMParams.addParameter(pgvParam);
		
		
	}

	@Override
	protected void initSiteParams() {
		siteParams.clear();
		vs30Param = new Vs30_Param(VS30_WARN_MIN, VS30_WARN_MAX);
		siteParams.addParameter(vs30Param);
		vs30Param.setValueAsDefault();
		
		// Campbell & bozorgnia hidden
		depthTo2pt5kmPerSecParam = new DepthTo2pt5kmPerSecParam(null, 0.0, 10.0, true);
		depthTo2pt5kmPerSecParam.setValueAsDefault();
//		depthTo2pt5kmPerSecParam.getEditor().setVisible(false);
		siteParams.addParameter(depthTo2pt5kmPerSecParam);

		// Chiou & Youngs hidden
		depthTo1pt0kmPerSecParam = new DepthTo1pt0kmPerSecParam(null, true);
		depthTo1pt0kmPerSecParam.setValueAsDefault();
//		depthTo1pt0kmPerSecParam.getEditor().setVisible(false);
		siteParams.addParameter(depthTo1pt0kmPerSecParam);
		
		vs30_TypeParam = new Vs30_TypeParam();
		vs30_TypeParam.getEditor().setVisible(false);
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
		
		// remove, override and hide parent truncation param to 
		// default to 1-sided 3s
		otherParams.removeParameter(sigmaTruncTypeParam);
		otherParams.removeParameter(sigmaTruncLevelParam);
		sigmaTruncTypeParam = new SigmaTruncTypeParam(
			SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED);
		sigmaTruncLevelParam = new SigmaTruncLevelParam(3.0);
		
		sigmaTruncTypeParam.addParameterChangeListener(this);
		sigmaTruncLevelParam.addParameterChangeListener(this);
		tectonicRegionTypeParam.addParameterChangeListener(this);
		componentParam.addParameterChangeListener(this);
		stdDevTypeParam.addParameterChangeListener(this);
		imrUncertParam.addParameterChangeListener(this);
		hwEffectApproxParam.addParameterChangeListener(this);
		
		// enforce default values used by NSHMP
		for (AttenuationRelationship ar : arList) {
			ParameterList list = ar.getOtherParams();

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
	protected void initEqkRuptureParams() {}

	@Override
	protected void initPropagationEffectParams() {}

	@Override
	protected void initParameterEventListeners() {
		vs30Param.addParameterChangeListener(this);
		depthTo1pt0kmPerSecParam.addParameterChangeListener(this);
		depthTo2pt5kmPerSecParam.addParameterChangeListener(this);
		saPeriodParam.addParameterChangeListener(this);
	}

	@Override
	public void setSite(Site site) {
		this.site = site;

		// being done to satisfy unit tests
		vs30Param.setValueIgnoreWarning((Double) site.getParameter(Vs30_Param.NAME).getValue());

		if (eqkRupture != null) {
			setPropagationEffect();
		}
	}
	
	@Override
	public void setEqkRupture(EqkRupture eqkRupture) {
		this.eqkRupture = eqkRupture;
		if (site != null) {
			setPropagationEffect();
		}
	}
	
	public void setPropagationEffect() {
		for (AttenuationRelationship ar : arList) {
			ar.setEqkRupture(eqkRupture);
			ar.setSite(site);
		}
	}

	@Override
	public double getMean() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getStdDev() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getEpsilon() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getEpsilon(double iml) {
		throw new UnsupportedOperationException();
	}

	@Override
	public DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls)
			throws ParameterException {
		
		// function collator with associated weights
		Map<DiscretizedFunc, Double> funcs = new HashMap<DiscretizedFunc, Double>();
		
		double imrWeight = 1 / (double) arList.size();
		
		// flag for epistemic uncertainty
		if (includeImrUncert) {
			// lookup M and dist
			double mag =eqkRupture.getMag();
			double dist = eqkRupture.getRuptureSurface().getDistanceJB(site.getLocation());
			double uncert = getUncertainty(mag, dist);
			for (AttenuationRelationship ar : arList) {
				for (int i=0; i<3; i++) {
					imrUncert = imrUncertSign[i] * uncert;
					DiscretizedFunc func = (DiscretizedFunc) imls.deepClone();
					funcs.put(ar.getExceedProbabilities(func), imrWeight * imrUncertWeights[i]);
				}
			}
		} else {
			imrUncert = 0;
			for (AttenuationRelationship ar : arList) {
				DiscretizedFunc func = (DiscretizedFunc) imls.deepClone();
				funcs.put(ar.getExceedProbabilities(func), imrWeight);
			}
		}
		
		// populate original
		for (int i=0; i<imls.size(); i++) {
			double val = 0.0;
			for (DiscretizedFunc f : funcs.keySet()) {
				val += f.getY(i) * funcs.get(f);
			}
			imls.set(i, val);
		}
		
		return imls;
	}
	
	@Override
	public double getExceedProbability() throws ParameterException,
			IMRException {
		throw new UnsupportedOperationException();
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
		for (AttenuationRelationship ar : arList) {
			ar.setIntensityMeasureLevel(iml);
		}
	}

	@Override
	public void setIntensityMeasureLevel(Object iml) throws ParameterException {
		for (AttenuationRelationship ar : arList) {
			ar.setIntensityMeasureLevel(iml);
		}
	}

	@Override
	public void setIntensityMeasure(String intensityMeasureName)
			throws ParameterException {
		super.setIntensityMeasure(intensityMeasureName);
		for (ScalarIMR ar : arList) {
			ar.setIntensityMeasure(intensityMeasureName);
		}
	}

	@Override
	public void parameterChange(ParameterChangeEvent e) {
		
		// pass through changes to params we know nga's are listeneing to
		for (AttenuationRelationship ar : arList) {
			ParameterChangeListener pcl = (ParameterChangeListener) ar;
			pcl.parameterChange(e);
		}
		
		// pass through those changes that are picked up at calculation time
		if (otherParams.containsParameter(e.getParameter())) {
			for (AttenuationRelationship ar : arList) {
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
			for (AttenuationRelationship ar : arList) {
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

	private static double[] imrUncertSign = {-1.0, 0.0, 1,0};
	private static double[] imrUncertWeights = {0.185, 0.630, 0.185};
	private static double[][] imrUncertVals = {
		{0.375, 0.210, 0.245},
		{0.230, 0.225, 0.230},
		{0.400, 0.360, 0.310}};

	/*
	 * Returns the epistemic uncertainty for the supplied magnitude (M) and
	 * distance (D) that
	 */
	private static double getUncertainty(double M, double D) {
		int mi = (M<6) ? 0 : (M<7) ? 1 : 2;
		int di = (D<10) ? 0 : (D<30) ? 1 : 2;
		return imrUncertVals[di][mi];
	}

//	public static void main(String[] args) {
//		System.out.println(getUncertainty(5,5));
//		System.out.println(getUncertainty(6,5));
//		System.out.println(getUncertainty(7,5));
//
//		System.out.println(getUncertainty(5,20));
//		System.out.println(getUncertainty(6,20));
//		System.out.println(getUncertainty(7,20));
//
//		System.out.println(getUncertainty(5,35));
//		System.out.println(getUncertainty(6,35));
//		System.out.println(getUncertainty(7,35));
//
//		System.out.println(getUncertainty(5,5));
//		System.out.println(getUncertainty(5,10));
//		System.out.println(getUncertainty(5,30));
//
//		System.out.println(getUncertainty(6,5));
//		System.out.println(getUncertainty(6,10));
//		System.out.println(getUncertainty(6,30));
//
//		System.out.println(getUncertainty(7,5));
//		System.out.println(getUncertainty(7,10));
//		System.out.println(getUncertainty(7,30));
//
//	}
	//public static void main(String[] args) {
		//RegionUtils.regionToKML(new CaliforniaRegions.RELM_NOCAL(), "relm_nocal2", Color.RED);
	//}
	
	/*
	 * Returns the NSHMP interpretation of fault type based on rake; divisions
	 * on 45 deg. diagonals. KLUDGY because the String 'constraints' for the
	 * faultTypeParam are the same for all 3 NGA's we just use Campbells
	 * String identifiers.
	 */
	private String getFaultTypeForRake(double rake) {
		if (rake >= 45 && rake <= 135) return CB_2008_AttenRel.FLT_TYPE_REVERSE;
		if(rake >= -135 && rake <= -45) return CB_2008_AttenRel.FLT_TYPE_NORMAL;
		return CB_2008_AttenRel.FLT_TYPE_STRIKE_SLIP;
	}
	
	private class NSHMP_BA_2008 extends BA_2008_AttenRel {
		
		public NSHMP_BA_2008(ParameterChangeWarningListener listener) {
			super(listener);
		}
		
		@Override
		public double getExceedProbability(double mean, double stdDev, double iml) {
			return super.getExceedProbability(
				mean + imrUncert, stdDev, iml);
		}
		
		@Override
		public void setFaultTypeFromRake(double rake) {
			fltTypeParam.setValue(getFaultTypeForRake(rake));
		}
	}

	private class NSHMP_CB_2008 extends CB_2008_AttenRel {
		
		public NSHMP_CB_2008(ParameterChangeWarningListener listener) {
			super(listener);
		}
		
		@Override
		public double getExceedProbability(double mean, double stdDev, double iml) {
			return super.getExceedProbability(
				mean + imrUncert, stdDev, iml);
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
			if (hwEffectApprox && eqkRupture.getRuptureSurface() instanceof PointSurface) {
				if (dip != 90) fhng = NSHMP_Util.getAvgHW_CB(mag, rRup, per[iper]);
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

	}

	private class NSHMP_CY_2008 extends CY_2008_AttenRel {
		public NSHMP_CY_2008(ParameterChangeWarningListener listener) {
			super(listener);
		}
		
		@Override
		public double getExceedProbability(double mean, double stdDev, double iml) {
			return super.getExceedProbability(
				mean + imrUncert, stdDev, iml);
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
			if (hwEffectApprox && eqkRupture.getRuptureSurface() instanceof PointSurface && iper != 23) {
				if (dip != 90) hw_effect = NSHMP_Util.getAvgHW_CY(mag, rRup, period[iper]);
			} else {
				hw_effect = c9[iper] * f_hw * Math.tanh(distX*cosDelta*cosDelta/c9a[iper]) * (1-altDist/(rRup+0.001));
			}
			
			lnYref = c1[iper] + (c1a[iper]*f_rv+c1b[iper]*f_nm+c7[iper]*(depthTop-4.0))*(1-aftershock) +
	
			(c10[iper]+c7a[iper]*(depthTop-4.0))*aftershock +
	
			c2*(mag-6.0) + ((c2-c3)/cn[iper])*Math.log(1.0 + Math.exp(cn[iper]*(cm[iper]-mag))) +
	
			c4*Math.log(rRup+c5[iper]*Math.cosh(c6[iper]*Math.max(mag-chm,0))) +
	
			(c4a-c4)*0.5*Math.log(rRup*rRup+crb*crb) + 
	
			(cg1[iper] + cg2[iper]/Math.cosh(Math.max(mag-cg3,0.0)))*rRup +
	
			hw_effect;
	
			lnYref_is_not_fresh = false;
	
	//		double Fhw = c9[iper] * f_hw * Math.tanh(distX*cosDelta*cosDelta/c9a[iper]) * (1-altDist/(rRup+0.001));
	//		DecimalFormat df1 = new DecimalFormat("#.######");
	//		DecimalFormat df2 = new DecimalFormat("#.##");
	//		System.out.println(df2.format(mag) + " " + df1.format(rRup) + " " + df1.format(distX) + " " + df1.format(Fhw) + " " + f_hw);
			//		System.out.println(rRup+"\t"+distanceJB+"\t"+distX+"\t"+f_hw+"\t"+lnYref);

	}

	}
	public static void main(String[] args) {
		Site s = new Site(new Location(34 , -118));
		System.out.println(s.clone());
	}
}
