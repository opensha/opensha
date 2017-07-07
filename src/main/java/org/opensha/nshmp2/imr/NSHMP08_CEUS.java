package org.opensha.nshmp2.imr;

import static org.opensha.nshmp2.util.SiteType.*;
import static org.opensha.sha.imr.PropagationEffect.*;
import static org.opensha.sha.imr.AttenRelRef.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.IMRException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.nshmp2.imr.impl.AB2006_140_AttenRel;
import org.opensha.nshmp2.imr.impl.AB2006_200_AttenRel;
import org.opensha.nshmp2.imr.impl.Campbell_2003_AttenRel;
import org.opensha.nshmp2.imr.impl.FrankelEtAl_1996_AttenRel;
import org.opensha.nshmp2.imr.impl.SilvaEtAl_2002_AttenRel;
import org.opensha.nshmp2.imr.impl.SomervilleEtAl_2001_AttenRel;
import org.opensha.nshmp2.imr.impl.TP2005_AttenRel;
import org.opensha.nshmp2.imr.impl.ToroEtAl_1997_AttenRel;
import org.opensha.nshmp2.util.Period;
import org.opensha.nshmp2.util.SiteType;
import org.opensha.nshmp2.util.Utils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.rupForecastImpl.PointEqkSource;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.PropagationEffect;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * This is an implementation of the combined attenuation relationships used in
 * the CEUS for the 2008 National Seismic Hazard Mapping Program (NSHMP). The
 * eight attenuation relationships used are:
 * 
 * <ul>
 * <li>{@link AB2006_140_AttenRel Atkinson &amp; Booore (2006) with 140bar stress drop}</li>
 * <li>{@link AB2006_200_AttenRel Atkinson &amp; Booore (2006) with 200bar stress drop}</li>
 * <li>{@link Campbell_2003_AttenRel Campbell CEUS (2003)}</li>
 * <li>{@link FrankelEtAl_1996_AttenRel Frankel et al. (1996)}</li>
 * <li>{@link SomervilleEtAl_2001_AttenRel Somerville et al. (2001)}</li>
 * <li>{@link SilvaEtAl_2002_AttenRel Silva et al. (2002)}</li>
 * <li>{@link ToroEtAl_1997_AttenRel Toro et al. (1997)}</li>
 * <li>{@link TP2005_AttenRel Tavakoli &amp; Pezeshk (2005)}</li>
 * </ul>
 * 
 * Please take note of the following implementation details:
 * 
 * <p>Mag conversions: ALL CEUS IMRs were developed for Mw, however, gridded
 * sources are based on mblg. As such, M conversions are applied according to
 * which of two gridded sources are passed in (AB or J) when building curve
 * tables. The exception is Toro which uses it's own coefficients to handle
 * mblg. See RateTable for switches.
 * 
 * <p>Moreover, Mw is used to calculate distances so for AB or J sources an
 * eqkRupture must have its magnitude modified when set.
 * 
 * <p>If a source is not gridded, no such M conversion options are available or
 * necessary. If mag conversion is required, it should ALWAYS be set before 
 * sourceType as it does not trigger a rebuild of a rate table. setMagConv
 * setSourceType
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class NSHMP08_CEUS extends AttenuationRelationship implements
		ParameterChangeListener {

	public final static String NAME = "NSHMP 2008 CEUS Combined";
	public final static String SHORT_NAME = "NSHMP08_CEUS";
	private static final long serialVersionUID = 1L;

	// possibly temp; child imrs should be ignoring
	private final static double VS30_WARN_MIN = 80;
	private final static double VS30_WARN_MAX = 1300;

	// imr weight maps; n.b. Charleston fixed-strike uses fault weights
	Map<ScalarIMR, Double> imrMap;

	// custom params
	private EnumParameter<SiteType> siteTypeParam;

	public NSHMP08_CEUS() {
		initImrMap();

		// these methods are called for each attenRel upon construction; we
		// do some local cloning so that a minimal set of params may be
		// exposed and modified in gui's and/or to ensure some parameters
		// adhere to NSHMP values
		initSupportedIntensityMeasureParams();
		initSiteParams();
		initOtherParams();
		initParameterEventListeners();
	}

	void initImrMap() {
		imrMap = Maps.newHashMap();
		imrMap.put(TORO_1997.instance(null), 0.2);
		imrMap.put(SOMERVILLE_2001.instance(null), 0.2);
		imrMap.put(FEA_1996.instance(null), 0.1);
		imrMap.put(AB_2006_140.instance(null), 0.1);
		imrMap.put(AB_2006_200.instance(null), 0.1);
		imrMap.put(CAMPBELL_2003.instance(null), 0.1);
		imrMap.put(TP_2005.instance(null), 0.1);
		imrMap.put(SILVA_2002.instance(null), 0.1);
	}

	@Override
	public void setUserMaxDistance(double maxDist) {
		for (ScalarIMR imr : imrMap.keySet()) {
			imr.setUserMaxDistance(maxDist);
		}
	}

	@Override
	public void setParamDefaults() {

		// NOTE should switch to vs30 and use numeric cutoffs for site type
		vs30Param.setValueAsDefault();

		pgaParam.setValueAsDefault();
		saParam.setValueAsDefault();
		saPeriodParam.setValueAsDefault();
		saDampingParam.setValueAsDefault();

		componentParam.setValueAsDefault();
		//stdDevTypeParam.setValueAsDefault();

		sigmaTruncTypeParam.setValueAsDefault();
		sigmaTruncLevelParam.setValueAsDefault();

		siteTypeParam.setValueAsDefault(); // shouldn't be necessary

		// sourceTypeParam.setValueAsDefault();
		// magConvParam.setValueAsDefault();
		tectonicRegionTypeParam.setValueAsDefault();

		for (ScalarIMR imr : imrMap.keySet()) {
			imr.setParamDefaults();
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

		List<Double> perVals = Lists.newArrayList();
		for (Period p : Period.getCEUS()) {
			perVals.add(p.getValue());
		}
		DoubleDiscreteConstraint periodConstraint = new DoubleDiscreteConstraint(
			perVals);
		periodConstraint.setNonEditable();
		saPeriodParam = new PeriodParam(periodConstraint, 1.0, false);
		saPeriodParam.addParameterChangeListener(this);
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

		siteTypeParam = new EnumParameter<SiteType>("Site Type", EnumSet.of(
			FIRM_ROCK, HARD_ROCK), FIRM_ROCK, null);
		siteParams.clear();
		siteParams.addParameter(siteTypeParam);
	}

	@Override
	protected void initOtherParams() {
		super.initOtherParams();

		// Component Parameter - uneditable
		// first is default, the rest are all options (including default)
		componentParam = new ComponentParam(Component.AVE_HORZ, Component.AVE_HORZ);
		componentParam.setValueAsDefault();
		otherParams.addParameter(componentParam);

		// currrently ignored and not being propogated to children
		sigmaTruncTypeParam.setValue(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED);
		sigmaTruncLevelParam.setValue(3.0);
		sigmaTruncTypeParam.addParameterChangeListener(this);
		sigmaTruncLevelParam.addParameterChangeListener(this);

		// enforce default values used by NSHMP
		for (ScalarIMR imr : imrMap.keySet()) {
			ParameterList list = imr.getOtherParams();

			// ComponentParam cp = (ComponentParam) list.getParameter(
			// ComponentParam.NAME);
			// cp.setValue(ComponentParam.COMPONENT_GMRotI50);

			// StdDevTypeParam stp = (StdDevTypeParam) list.getParameter(
			// StdDevTypeParam.NAME);
			// stp.setValue(StdDevTypeParam.STD_DEV_TYPE_TOTAL);

			// TODO HOW TO REINTEGRATE AND INCLUDE CLAMPING
			// SigmaTruncTypeParam sttp = (SigmaTruncTypeParam)
			// list.getParameter(
			// SigmaTruncTypeParam.NAME);
			// sttp.setValue(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED);
			//
			// SigmaTruncLevelParam stlp = (SigmaTruncLevelParam)
			// list.getParameter(
			// SigmaTruncLevelParam.NAME);
			// stlp.setValue(3.0);
		}
	}

	@Override
	protected void initEqkRuptureParams() {}

	@Override
	protected void initPropagationEffectParams() {}

	@Override
	protected void initParameterEventListeners() {
		vs30Param.addParameterChangeListener(this);
		saPeriodParam.addParameterChangeListener(this);
	}

	@Override
	public void setSite(Site site) {
		this.site = site;

		// being done to satisfy unit tests HUH?????????
		vs30Param.setValueIgnoreWarning((Double) site.getParameter(
			Vs30_Param.NAME).getValue());

		for (ScalarIMR imr : imrMap.keySet()) {
			imr.setSite(site);
		}
	}

	@Override
	public void setEqkRupture(EqkRupture eqkRupture) {
		this.eqkRupture = eqkRupture;
		for (ScalarIMR imr : imrMap.keySet()) {
			imr.setEqkRupture(eqkRupture);
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
		// System.out.println(e);
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
