package org.opensha.nshmp2.imr;

import static org.opensha.sha.imr.PropagationEffect.NSHMP_PT_SRC_CORR_PARAM_NAME;
import static org.opensha.sha.imr.PropagationEffect.POINT_SRC_CORR_PARAM_NAME;
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
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.nshmp2.imr.impl.AB2003_AttenRel;
import org.opensha.nshmp2.imr.impl.YoungsEtAl_1997_AttenRel;
import org.opensha.nshmp2.util.CurveTable;
import org.opensha.nshmp2.util.FaultCode;
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
 * for "deep" events in northern California and the Pacific Northwest. These 
 * events are interpreted to be 'slab' events occurring within the subducting 
 * Juan de Fuca plate. The two attenuation relationships used are:
 * <ul>
 * <li>{@link AB2003_AttenRel Atkinson and Boore (2003)} - 
 * both Global and Cascadia forms</li>
 * <li>{@link YoungsEtAl_1997_AttenRel Youngs et al.(2003)} - 
 * aka Geomatrix)</li>
 * </ul>
 * The global form of Atkinson and Boore (2003) is given 1/4 weight, the 
 * Cascadia spcific form of Atkinson and Boore (2003) is given 1/4 weight, and
 * Youngs et al. (1997) is given 1/2 wieght.</p>
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class NSHMP08_SUB_Slab extends AttenuationRelationship implements
		ParameterChangeListener {

	public final static String NAME = "NSHMP 2008 Subduction Slab Combined";
	public final static String SHORT_NAME = "NSHMP08_SLAB";
	private static final long serialVersionUID = 1L;

	// possibly temp; child imrs should be ignoring
	private final static double VS30_WARN_MIN = 80;
	private final static double VS30_WARN_MAX = 1300;
	
	// imr weight maps
	Map<ScalarIMR, Double> imrMap;
	
	/**
	 * Create a new NSHMP deep event (slab) instance.
	 */
	public NSHMP08_SUB_Slab() {
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
	
	@SuppressWarnings("unchecked")
	void initImrMap() {
		imrMap = Maps.newHashMap();
		// cascadia slab
		AB2003_AttenRel ab = new AB2003_AttenRel(null);
		ab.getParameter("Subduction Type").setValue(SUBDUCTION_SLAB);
		ab.getParameter("Global").setValue(false);
		imrMap.put(ab, 0.25);
		// global slab
		ab = new AB2003_AttenRel(null);
		ab.getParameter("Subduction Type").setValue(SUBDUCTION_SLAB);
		imrMap.put(ab, 0.25);
		// slab
		YoungsEtAl_1997_AttenRel yea = new YoungsEtAl_1997_AttenRel(null);
		yea.getParameter("Subduction Type").setValue(SUBDUCTION_SLAB);
		imrMap.put(yea, 0.5);
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
		String trtDefault = SUBDUCTION_SLAB.toString();
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
		rupTopDepthParam = new RupTopDepthParam();
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
		DiscretizedFunc f = imls.deepClone();
		for (ScalarIMR imr : imrMap.keySet()) {
			f = imr.getExceedProbabilities(f);
			f.scale(imrMap.get(imr));
			Utils.addFunc(imls, f);
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
	


}
