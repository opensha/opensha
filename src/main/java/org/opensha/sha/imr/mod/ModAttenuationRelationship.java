package org.opensha.sha.imr.mod;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.IMRException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.ParamLinker;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.ParameterListParameter;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Modified Attenduation Relationship which can modify any underlying IMR via instances of AbstractAttenRelMod.
 * Add references to AbstractAttenRelMod's in ModAttenRelRef to make them available here. Site effects and available
 * IMTs are hardcoded and may not be applicable to each underlying IMR.
 * @author kevin
 *
 */
public class ModAttenuationRelationship extends AttenuationRelationship implements ParameterChangeListener {
	
	private static final boolean D = false;
	
	public static final String NAME = "Modified Attenuation Relationship";
	public static final String SHORT_NAME = "ModAttenRel";
	
	// supported periods
	private double[] periods = {0.010, 0.020, 0.030, 0.050, 0.075, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 0.50, 0.75,
			1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 7.5, 10.0};
	
	private ParameterListParameter imrParams;
	protected ParameterListParameter modParams;
	
	public static final String IMRS_PARAM_NAME = "Reference IMR";
	private EnumSet<AttenRelRef> supportedIMRs;
	private EnumParameter<AttenRelRef> imrsParam;
	public static final String MODS_PARAM_NAME = "Modifier";
	private EnumSet<ModAttenRelRef> supportedMods;
	private EnumParameter<ModAttenRelRef> modsParam;
	
	private Map<AttenRelRef, ScalarIMR> imrsCache = Maps.newHashMap();
	private Map<ModAttenRelRef, AbstractAttenRelMod> modsCache = Maps.newHashMap();
	
	private ScalarIMR imr;
	private AbstractAttenRelMod mod;
	
	private boolean imtStale = true;
	
	public ModAttenuationRelationship() {
		this(null);
	}
	
	public ModAttenuationRelationship(AttenRelRef supportedIMR, ModAttenRelRef supportedMod) {
		this(Lists.newArrayList(supportedIMR), Lists.newArrayList(supportedMod));
	}
	
	public ModAttenuationRelationship(Collection<AttenRelRef> supportedIMRs, ModAttenRelRef supportedMod) {
		this(supportedIMRs, Lists.newArrayList(supportedMod));
	}
	
	public ModAttenuationRelationship(Collection<AttenRelRef> supportedIMRs,
			Collection<ModAttenRelRef> supportedMods) {
		this(null, EnumSet.copyOf(supportedIMRs), EnumSet.copyOf(supportedMods));
	}
	
	public ModAttenuationRelationship(ParameterChangeWarningListener l) {
		this(l, EnumSet.copyOf(AttenRelRef.get(ServerPrefUtils.SERVER_PREFS)), EnumSet.allOf(ModAttenRelRef.class));
	}
	
	public ModAttenuationRelationship(ParameterChangeWarningListener l,
			EnumSet<AttenRelRef> supportedIMRs, EnumSet<ModAttenRelRef> supportedMods) {
		this.supportedIMRs = supportedIMRs;
		this.supportedMods = supportedMods;
		initSupportedIntensityMeasureParams();
		initEqkRuptureParams();
		initPropagationEffectParams();
		initSiteParams();
		initOtherParams();
	}

	@Override
	public ParameterList getMeanIndependentParams() {
		return imr.getMeanIndependentParams();
	}

	@Override
	public ParameterList getStdDevIndependentParams() {
		return imr.getStdDevIndependentParams();
	}

	@Override
	public ParameterList getExceedProbIndependentParams() {
		return imr.getExceedProbIndependentParams();
	}

	@Override
	public ParameterList getIML_AtExceedProbIndependentParams() {
		return imr.getIML_AtExceedProbIndependentParams();
	}

	@Override
	public double getMean() {
		Preconditions.checkNotNull(imr, "IMR is null!");
		Preconditions.checkNotNull(mod, "Mod is null!");
		checkUpdateIMT();
		return mod.getModMean(imr);
	}

	@Override
	public double getStdDev() {
		Preconditions.checkNotNull(imr, "IMR is null!");
		Preconditions.checkNotNull(mod, "Mod is null!");
		checkUpdateIMT();
		return mod.getModStdDev(imr);
	}

	@Override
	public double getExceedProbability() throws ParameterException, IMRException {
		double iml = ((Double) im.getValue()).doubleValue();
		ArbitrarilyDiscretizedFunc imls = new ArbitrarilyDiscretizedFunc();
		imls.set(iml, 1d);
		return getExceedProbabilities(imls).getY(0);
	}

	@Override
	public double getExceedProbability(double iml) throws ParameterException, IMRException {
		return super.getExceedProbability(iml);
	}

	@Override
	public DiscretizedFunc getExceedProbabilities(DiscretizedFunc intensityMeasureLevels) throws ParameterException {
		checkUpdateIMT();
		return mod.getModExceedProbabilities(getCurrentIMR(), intensityMeasureLevels);
	}

	@Override
	public void setParamDefaults() {
		saParam.setValueAsDefault();
		saPeriodParam.setValueAsDefault();
		saDampingParam.setValueAsDefault();
		pgaParam.setValueAsDefault();
		pgvParam.setValueAsDefault();
		vs30Param.setValueAsDefault();
		depthTo2pt5kmPerSecParam.setValueAsDefault();
		depthTo1pt0kmPerSecParam.setValueAsDefault();
		vs30_TypeParam.setValueAsDefault();
		
		updateCurrentMod();
		updateCurrentIMR();
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
	protected void setPropagationEffectParams() {}

	@Override
	protected void initSupportedIntensityMeasureParams() {
		/*
		 * IMPORTANT:
		 * 
		 * If IMTs are added here with dependent parameters, you must add a parameter change
		 * listener to those dependent parameters, then set imtStale=true on each update. See
		 * saPeriodParam below and treatment in parameterChange(...);
		 */
		
		// Create saParam:
		DoubleDiscreteConstraint periodConstraint = new DoubleDiscreteConstraint();
		for (double period : periods)
			periodConstraint.addDouble(new Double(period));
		periodConstraint.setNonEditable();
		saPeriodParam = new PeriodParam(periodConstraint);
		saDampingParam = new DampingParam();
		saParam = new SA_Param(saPeriodParam, saDampingParam);
		saPeriodParam.addParameterChangeListener(this);
		saDampingParam.addParameterChangeListener(this);
		saParam.setNonEditable();

		//  Create PGA Parameter (pgaParam):
		pgaParam = new PGA_Param();
		pgaParam.setNonEditable();

		//  Create PGV Parameter (pgvParam):
		pgvParam = new PGV_Param();
		pgvParam.setNonEditable();
		
		// Put parameters in the supportedIMParams list:
		supportedIMParams.clear();
		supportedIMParams.addParameter(saParam);
		supportedIMParams.addParameter(pgaParam);
		supportedIMParams.addParameter(pgvParam);
	}

	@Override
	protected void initSiteParams() {
		vs30Param = new Vs30_Param(760);
		depthTo2pt5kmPerSecParam = new DepthTo2pt5kmPerSecParam(null, 0d, 30000d, true);
		depthTo1pt0kmPerSecParam = new DepthTo1pt0kmPerSecParam(null, true);
		depthTo1pt0kmPerSecParam.setValueAsDefault();
		vs30_TypeParam = new Vs30_TypeParam();

		siteParams.clear();
		siteParams.addParameter(vs30Param);
		siteParams.addParameter(vs30_TypeParam);
		siteParams.addParameter(depthTo2pt5kmPerSecParam);
		siteParams.addParameter(depthTo1pt0kmPerSecParam);
	}

	@Override
	protected void initEqkRuptureParams() {}

	@Override
	protected void initPropagationEffectParams() {}

	@Override
	protected void initOtherParams() {
		super.initOtherParams();
		
		AttenRelRef defaultIMR = AttenRelRef.CB_2008;
		if (!supportedIMRs.contains(defaultIMR))
			defaultIMR = supportedIMRs.iterator().next();
		
		imrsParam = new EnumParameter<AttenRelRef>(IMRS_PARAM_NAME,
				supportedIMRs, defaultIMR, null);
		imrsParam.addParameterChangeListener(this);
		otherParams.addParameter(0, imrsParam);

		imrParams = new ParameterListParameter("Reference IMR Params");
		imrParams.setDefaultValue(new ParameterList());
		otherParams.addParameter(1, imrParams);
		
		ModAttenRelRef defaultMod = ModAttenRelRef.SIMPLE_SCALE;
		if (!supportedMods.contains(defaultMod))
			defaultMod = supportedMods.iterator().next();
		
		modsParam = new EnumParameter<ModAttenRelRef>(MODS_PARAM_NAME, supportedMods,
				defaultMod, null);
		modsParam.addParameterChangeListener(this);
		otherParams.addParameter(2, modsParam);
		
		modParams = new ParameterListParameter("Modifier Params");
		modParams.setDefaultValue(new ParameterList());
		modParams.setValueAsDefault();
		otherParams.addParameter(3, modParams);
	}
	
	private synchronized void updateCurrentIMR() {
		if (D) System.out.println("Updating IMR");
		AttenRelRef ref = imrsParam.getValue();
		imr = imrsCache.get(ref);
		
		if (imr == null) {
			imr = ref.instance(null);
			Preconditions.checkNotNull(imr, "Could not build IMR: "+ref.name());
			imr.setParamDefaults();
			
			// link truncation params, forwards and backwards
			new ParamLinker<String>(sigmaTruncTypeParam, (Parameter<String>)imr.getParameter(SigmaTruncTypeParam.NAME));
			new ParamLinker<String>((Parameter<String>)imr.getParameter(SigmaTruncTypeParam.NAME), sigmaTruncTypeParam);
			
			new ParamLinker<Double>(sigmaTruncLevelParam, (Parameter<Double>)imr.getParameter(SigmaTruncLevelParam.NAME));
			new ParamLinker<Double>((Parameter<Double>)imr.getParameter(SigmaTruncLevelParam.NAME), sigmaTruncLevelParam);
			
			imrsCache.put(ref, imr);
		}
		
		imrParams.setValue(getReferenceIMRParams(imr.getOtherParams()));
		try {
			// update GUI if applicable
			imrParams.getEditor().refreshParamEditor();
		} catch (Exception e) {
			// exception if headless, ignore
		}
		
		if (D) System.out.println("Loaded IMR: "+imr.getName());
		
		synchModAndIMR();
	}
	
	/**
	 * Can be overridden to modify referenceIMR params
	 * @param paramsFromIMR
	 * @return
	 */
	protected ParameterList getReferenceIMRParams(ParameterList paramsFromIMR) {
		return paramsFromIMR;
	}
	
	private synchronized void updateCurrentMod() {
		if (D) System.out.println("Updating Mod");
		ModAttenRelRef ref = modsParam.getValue();
		mod = modsCache.get(ref);
		
		if (mod == null) {
			mod = ref.instance();
			
			modsCache.put(ref, mod);
		}
		
		ParameterList params = mod.getModParams();
		if (params == null)
			params = new ParameterList();
		modParams.setValue(params);
		try {
			// update GUI if applicable
			modParams.getEditor().refreshParamEditor();
		} catch (Exception e) {
			// exception if headless, ignore
		}
		
		if (D) System.out.println("Loaded Mod: "+mod.getName());
		
		synchModAndIMR();
	}
	
	private void synchModAndIMR() {
		if (mod != null && imr != null) {
			mod.setIMRParams(imr);
			if (getIntensityMeasure() != null)
				mod.setIMT_IMT(imr, getIntensityMeasure());
			if (site != null)
				mod.setIMRSiteParams(imr, site);
			if (eqkRupture != null)
				mod.setIMRRupParams(imr, eqkRupture);
		}
	}

	@Override
	public void setIntensityMeasure(String intensityMeasureName)
			throws ParameterException {
		super.setIntensityMeasure(intensityMeasureName);
		imtStale = true;
	}
	
	private void checkUpdateIMT() {
		// must do this separately from the overridden setIntensityMeasure(...) method in order to correctly
		// set independent parameters (such as SA period)
		
		if (imtStale) {
			Parameter<Double> newIMT = getIntensityMeasure();
			if (D) System.out.println("Detected new IMT: "+newIMT.getName());
			if (D && getIntensityMeasure() instanceof SA_Param)
				System.out.println("Period: "+SA_Param.getPeriodInSA_Param(newIMT));
			if (mod != null && imr != null && getIntensityMeasure() != null) {
				mod.setIMT_IMT(imr, newIMT);
				if (D) System.out.println("Updated IMT for "+imr.getShortName()+": "+imr.getIntensityMeasure().getName());
				if (D && imr.getIntensityMeasure() instanceof SA_Param)
					System.out.println("Period: "+SA_Param.getPeriodInSA_Param(imr.getIntensityMeasure()));
			}
			imtStale = false;
		}
	}

	@Override
	public void setSite(Site site) {
		super.setSite(site);
		if (mod != null && imr != null && site != null)
			mod.setIMRSiteParams(imr, site);
	}

	@Override
	public void setEqkRupture(EqkRupture eqkRupture) {
		super.setEqkRupture(eqkRupture);
		if (mod != null && imr != null && eqkRupture != null)
			mod.setIMRRupParams(imr, eqkRupture);
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (event.getParameter() == imrsParam) {
			updateCurrentIMR();
		} else if (event.getParameter() == modsParam) {
			updateCurrentMod();
		} else if (event.getParameter() == saPeriodParam || event.getParameter() == saDampingParam) {
			imtStale = true;
		}
	}
	
	protected ScalarIMR getCurrentIMR() {
		return imr;
	}
	
	protected AbstractAttenRelMod getCurrentMod() {
		return mod;
	}

}
