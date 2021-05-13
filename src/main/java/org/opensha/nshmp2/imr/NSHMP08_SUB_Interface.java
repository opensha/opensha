package org.opensha.nshmp2.imr;

import static org.opensha.sha.util.TectonicRegionType.*;

import java.util.List;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.IMRException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.nshmp2.imr.impl.AB2003_AttenRel;
import org.opensha.nshmp2.imr.impl.YoungsEtAl_1997_AttenRel;
import org.opensha.nshmp2.imr.impl.ZhaoEtAl_2006_AttenRel;
import org.opensha.nshmp2.util.Period;
import org.opensha.nshmp2.util.Utils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.rupForecastImpl.PointEqkSource;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.PropagationEffect;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupTopDepthParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * <p>This is an implementation of the combined attenuation relationships used 
 * for Cascadia subduction 'interface' events in northern California and the 
 * Pacific Northwest. The three attenuation relationships used are:
 * <ul>
 * <li>{@link AB2003_AttenRel Atkinson and Boore (2003)}</li>
 * <li>{@link YoungsEtAl_1997_AttenRel Youngs et al. (Geomatrix) (2003)}</li>
 * <li>{@link ZhaoEtAl_2006_AttenRel  Zhao et al. (2006)}</li>
 * </ul>
 * Each attenuation relationship gets 1/3 weight.</p>
 * @author Peter Powers
 * @version $Id:$
 */
public class NSHMP08_SUB_Interface extends AttenuationRelationship implements
		ParameterChangeListener {

	public final static String NAME = "NSHMP 2008 Subduction Interface Combined";
	public final static String SHORT_NAME = "NSHMP08_SUB_INTERFACE";
	private static final long serialVersionUID = 1L;

	// possibly temp; child imrs should be ignoring
	private final static double VS30_WARN_MIN = 80;
	private final static double VS30_WARN_MAX = 1300;

	// imr weight maps
	private Map<ScalarIMR, Double> imrMap;
	
	//private StringParameter siteTypeParam;

	/**
	 * Create a new NSHMP subduction interface instance.
	 */
	public NSHMP08_SUB_Interface() {
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
		NSHMP_AB03_2008 ab = new NSHMP_AB03_2008();
		imrMap.put(ab, 0.25);
		NSHMP_Y97_2008 yea = new NSHMP_Y97_2008();
		imrMap.put(yea, 0.25);
		NSHMP_ZH_2008 zh = new NSHMP_ZH_2008();
		imrMap.put(zh, 0.5);
	}
	
	@Override
	public void setUserMaxDistance(double maxDist) {
		for (ScalarIMR imr : imrMap.keySet()) {
			imr.setUserMaxDistance(maxDist);
		}
	}

	@Override
	public void setParamDefaults() {

		vs30Param.setValueAsDefault();

		pgaParam.setValueAsDefault();
		saParam.setValueAsDefault();
		saPeriodParam.setValueAsDefault();
		saDampingParam.setValueAsDefault();

		componentParam.setValueAsDefault();

		sigmaTruncTypeParam.setValueAsDefault();
		sigmaTruncLevelParam.setValueAsDefault();

		magParam.setValueAsDefault();
		rupTopDepthParam.setValueAsDefault();
		distanceRupParam.setValueAsDefault();

		tectonicRegionTypeParam.setValueAsDefault();
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

		// Create PGA Parameter (pgaParam):
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
	}
	
	@Override
	protected void initOtherParams() {
		super.initOtherParams();

		// Component Parameter - uneditable
		// first is default, the rest are all options (including default)
		componentParam = new ComponentParam(Component.AVE_HORZ, Component.AVE_HORZ);
		componentParam.setValueAsDefault();
		otherParams.addParameter(componentParam);

		// TRT, uneditable and no connection to TRT in child imrs
		StringConstraint trtConst = new StringConstraint();
		String trtDefault = SUBDUCTION_INTERFACE.toString();
		trtConst.addString(trtDefault);
		tectonicRegionTypeParam = new TectonicRegionTypeParam(trtConst,
			trtDefault);
		otherParams.replaceParameter(
			TectonicRegionTypeParam.NAME,
			tectonicRegionTypeParam);

		// currrently ignored and not being propogated to children
		sigmaTruncTypeParam.setValue(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED);
		sigmaTruncLevelParam.setValue(3.0);

	}

	@Override
	protected void initEqkRuptureParams() {
		magParam = new MagParam();
		rupTopDepthParam = new RupTopDepthParam(20);
		rupTopDepthParam.setConstraint(new DoubleConstraint(0,100));
		eqkRuptureParams.clear();
		eqkRuptureParams.addParameter(magParam);
		eqkRuptureParams.addParameter(rupTopDepthParam);
	}

	@Override
	protected void initPropagationEffectParams() {
		distanceRupParam = new DistanceRupParameter(0.0);
		distanceRupParam.setNonEditable();
		propagationEffectParams.addParameter(distanceRupParam);
	}

	@Override
	protected void initParameterEventListeners() {
		vs30Param.addParameterChangeListener(this);
		saPeriodParam.addParameterChangeListener(this);
		magParam.addParameterChangeListener(this);
		distanceRupParam.addParameterChangeListener(this);
		rupTopDepthParam.addParameterChangeListener(this);
		sigmaTruncTypeParam.addParameterChangeListener(this);
		sigmaTruncLevelParam.addParameterChangeListener(this);
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
			distanceRupParam.setValue(eqkRupture, site);
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
		Utils.zeroFunc(imls);
		DiscretizedFunc f = (DiscretizedFunc) imls.deepClone();
		for (ScalarIMR imr : imrMap.keySet()) {
			f = imr.getExceedProbabilities(f);
			f.scale(imrMap.get(imr));
			Utils.addFunc(imls, f);
		}
//		System.out.println(imls);
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
		throw new UnsupportedOperationException(
			"getTotExceedProbability is unsupported for " + C);
	}

	@Override
	public void setIntensityMeasureLevel(Double iml) throws ParameterException {
		for (ScalarIMR imr : imrMap.keySet()) {
			imr.setIntensityMeasureLevel(iml);
		}
	}

	@Override
	public void setIntensityMeasureLevel(Object iml) throws ParameterException {
		for (ScalarIMR imr : imrMap.keySet()) {
			imr.setIntensityMeasureLevel(iml);
		}
	}

	@Override
	public void setIntensityMeasure(String intensityMeasureName)
			throws ParameterException {
		super.setIntensityMeasure(intensityMeasureName);
		for (ScalarIMR imr : imrMap.keySet()) {
			imr.setIntensityMeasure(intensityMeasureName);
		}
	}

	@Override
	public void parameterChange(ParameterChangeEvent e) {
		String pName = e.getParameterName();

		// pass through changes
		for (ScalarIMR imr : imrMap.keySet()) {
			ParameterChangeListener pcl = (ParameterChangeListener) imr;
			pcl.parameterChange(e);
		}

		// pass through those changes that are picked up at calculation time
		if (otherParams.containsParameter(e.getParameter())) {
			for (ScalarIMR imr : imrMap.keySet()) {
				ParameterList pList = imr.getOtherParams();
				// String pName = e.getParameterName();
				if (!pList.containsParameter(pName)) continue;
				// TODO above shouldn't be necessary; pLists should not throw
				// exceptions fo missing parameters
				Parameter<?> param = imr.getOtherParams().getParameter(
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
			for (ScalarIMR imr : imrMap.keySet()) {
				ParameterList pList = imr.getSupportedIntensityMeasures();
				SA_Param sap = (SA_Param) pList.getParameter(SA_Param.NAME);
				sap.getPeriodParam().setValue(saPeriodParam.getValue());
			}
		}
	}
	

	
	// KLUDGY 
	// these subclass overrides insert a correction to rRup. NSHMP distance 
	// algorithms give consistently lower values that have nothing to do with 
	// grid spacing options.
	private static final double RRUP_CORR = 0.987195; // 0.99358;

	private class NSHMP_AB03_2008 extends AB2003_AttenRel {
		
		NSHMP_AB03_2008() { super(null); }
		
		@Override
		public double getMean() {
			distanceRupParam.setValue(distanceRupParam.getValue() * RRUP_CORR);
			return super.getMean();
		}
	}
	
	private class NSHMP_Y97_2008 extends YoungsEtAl_1997_AttenRel {
		
		NSHMP_Y97_2008() { super(null); }
		
		@Override
		public double getMean() {
			distanceRupParam.setValue(distanceRupParam.getValue() * RRUP_CORR);
			return super.getMean();
		}
	}

	private class NSHMP_ZH_2008 extends ZhaoEtAl_2006_AttenRel {
		
		NSHMP_ZH_2008() { super(null); }
		
		@Override
		public double getMean() {
			distanceRupParam.setValue(distanceRupParam.getValue() * RRUP_CORR);
			return super.getMean();
		}
	}
		

	/*
	 * This represents an initial attempt to override the existing
	 * Zhao implementation. During testing, inclusion of this class
	 * in NSHMP08_SUB_Interface was causing two different hazard
	 * curve results. Use of AB and Youngs together had no problems,
	 * and this Zhao by itself had no problems, but this Zhao in
	 * conjuction with either Youngs, AB, or both yielded inconsistent
	 * results. This was in a single threaded environment so I don't
	 * know what was happening.
	 * 
	 * As an attempt to fix this, the existing Zhao has been replicated
	 * and these overridden methods merged in , and the resultant class
	 * cleaned up.
	 */
//	private class NSHMP_ZH_2008 extends ZhaoEtAl_2006_AttenRel {
		
//		NSHMP_ZH_2008() {
//			super(null); 
//			setParamDefaults();
//		}
//		
//		@Override
//		protected void initSiteParams() {
//			super.initSiteParams();
//			vs30Param = new Vs30_Param(150.0, 1500.0);
//			siteParams.addParameter(vs30Param);
//		}
//
//		@Override
//		public void setSite(Site site) throws ParameterException {	 	
//	    	// Overridden to skip reading siteTypeParam
//			vs30Param.setValue((Double) site.getParameter(
//				Vs30_Param.NAME).getValue());
//			this.site = site;
//			setPropagationEffectParams();
//		}
//		
//		@Override
//		public void setPropagationEffect(PropagationEffect propEffect) throws
//		ParameterException, InvalidRangeException {
//			// this is a poor implementation in parent Zhao that should be
//			// updated to match NGA single path using propEffect
//
//			this.site = propEffect.getSite();
//			this.eqkRupture = propEffect.getEqkRupture();
//			vs30Param.setValue((Double) site.getParameter(
//				Vs30_Param.NAME).getValue());
//			magParam.setValueIgnoreWarning(new Double(eqkRupture.getMag()));
//			propEffect.setParamValue(distanceRupParam);
//		}
//
//		@Override
//		public DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls) {
//			return Utils.getExceedProbabilities(imls, getMean(), getStdDev(), 
//				false, 0.0);
//		}
//
//		@Override
//		public double getStdDev(int iper, String stdDevType, String tecRegType) {
//			// KLUDGY Overridden to ignore stdDevType and tecRegionType
//			// args; stdDevType = TOTAL; NSHMP uses standard sigma instead
//			// of interface spcific sigma per A. Frankel. See comment in
//			// hazSUBXnga in zhao subroutine:
//			//
//			// Frankel email may 22 2007: use sigt from table 5. Not the
//			// reduced-tau sigma associated with mag correction seen in table 6.
//			// Zhao says "truth" is somewhere in between.
//
////			System.out.println("zhSig: " + Math.sqrt(tau[iper]*tau[iper]+sigma[iper]*sigma[iper]));
//			return Math.sqrt(tau[iper]*tau[iper]+sigma[iper]*sigma[iper]);
//		}
//		
//		// zhao does not use dtor but probably should
//		private static final double DEPTH = 20.0;
//		private static final double HC = 15.0;
//		private static final double MC = 6.3;
//		private static final double GCOR = 6.88806;
//		
//		@Override
//		public double getMean(int iper, double mag, double rRup) {
//			// Overridden to match NSHMP; too many funny hooks in original
//			// implementation that would require equivalent hacks to match.
//			// Required making a variety of fields in Zhao protected
//			double vs30 = vs30Param.getValue();
//			rRup = distanceRupParam.getValue() * RRUP_CORR;
//
//			double site = (vs30 > 599) ? C1[iper] : (vs30 > 299) ? C2[iper]
//				: C3[iper];
//
//			double afac = Si[iper];
//			double hfac = DEPTH - HC;
//			double m2 = mag - MC;
//			double xmcor = Qi[iper] * m2 * m2 + Wi[iper];
//			double h = DEPTH;
//			double r = rRup + c[iper] * Math.exp(d[iper] * mag);
//			double gnd = a[iper] * mag + b[iper] * rRup - Math.log(r) + site;
//			gnd = gnd + e[iper] * hfac + afac;
//			gnd = gnd + xmcor;
//			gnd = gnd - GCOR;
////			System.out.println("zh " + gnd + " " +rRup + " "+site+ " "+afac+ " "+hfac+ " ");
//			return gnd;
//		}
//	}

}
