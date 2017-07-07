package org.opensha.sha.imr.attenRelImpl.ngaw2;

import static java.lang.Math.sin;
import static org.opensha.commons.geo.GeoTools.TO_RAD;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.IMRException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.gcim.imr.param.EqkRuptureParams.FocalDepthParam;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.param.EqkRuptureParams.DipParam;
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
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceX_Parameter;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

import com.google.common.base.Preconditions;

/**
 * This wraps Peter's NGA implementation to conform to the AttenuationRelationship construct
 * 
 * @author kevin
 *
 */
public class NGAW2_WrapperFullParam extends AttenuationRelationship implements ParameterChangeListener {
	
	private String shortName;
	private NGAW2_GMM gmpe;
	private ScalarGroundMotion gm;
	
	private boolean supportsPhiTau;
	
	// params not in parent class
	private DistanceX_Parameter distanceXParam;
	
	public static final String EPISTEMIC_PARAM_NAME = "Additional Epistemic Uncertainty";
	private EnumParameter<EpistemicOption> epiParam;
	
	public enum EpistemicOption {
		UPPER("Upper", 1d),
		LOWER("Lower", -1d);
		
		private String name;
		private double sign;
		private EpistemicOption(String name, double sign) {
			this.name = name;
			this.sign = sign;
		}
		
		@Override
		public String toString() {
			return name;
		}
	}
	
	public NGAW2_WrapperFullParam(String shortName, NGAW2_GMM gmpe, boolean supportsPhiTau) {
		this.shortName = shortName;
		this.gmpe = gmpe;
		this.supportsPhiTau = supportsPhiTau;
		
		initSupportedIntensityMeasureParams();
		initSiteParams();
		initEqkRuptureParams();
		initPropagationEffectParams();
		initOtherParams();
		
		initIndependentParamLists();
	}
	
	NGAW2_GMM getGMPE() {
		return gmpe;
	}

	@Override
	public double getMean() {
		ScalarGroundMotion gm = getGroundMotion();
		return gm.mean();
	}

	@Override
	public double getStdDev() {
		ScalarGroundMotion gm = getGroundMotion();
		String stdType = stdDevTypeParam.getValue();
//		System.out.println("GET called with stdType: "+stdType);
		if (stdType.equals(StdDevTypeParam.STD_DEV_TYPE_TOTAL))
			return gm.stdDev();
		else if (stdType.equals(StdDevTypeParam.STD_DEV_TYPE_INTER))
			return gm.tau();
		else if (stdType.equals(StdDevTypeParam.STD_DEV_TYPE_INTRA))
			return gm.phi();
		else
			throw new IllegalStateException("Unsupported Std Dev Type: "+stdType);
	}

	@Override
	public void setParamDefaults() {
		for (Parameter<?> param : siteParams)
			param.setValueAsDefault();
		for (Parameter<?> param : propagationEffectParams)
			param.setValueAsDefault();
		for (Parameter<?> param : eqkRuptureParams)
			param.setValueAsDefault();
		for (Parameter<?> param : otherParams)
			param.setValueAsDefault();
	}

	@Override
	public String getName() {
		return gmpe.getName();
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public void setSite(Site site) {
		super.setSite(site);
		this.vs30Param.setValueIgnoreWarning(site.getParameter(Double.class, Vs30_Param.NAME).getValue());
		this.vs30_TypeParam.setValue(site.getParameter(String.class, Vs30_TypeParam.NAME).getValue());
		this.depthTo1pt0kmPerSecParam.setValue(site.getParameter(Double.class,
				DepthTo1pt0kmPerSecParam.NAME).getValue());
		this.depthTo2pt5kmPerSecParam.setValue(site.getParameter(Double.class,
				DepthTo2pt5kmPerSecParam.NAME).getValue());
		
		setPropagationEffectParams();
	}

	@Override
	public void setEqkRupture(EqkRupture eqkRupture) {
		super.setEqkRupture(eqkRupture);
		
		RuptureSurface surf = eqkRupture.getRuptureSurface();
		
		magParam.setValue(eqkRupture.getMag());
		rakeParam.setValue(eqkRupture.getAveRake());
		dipParam.setValueIgnoreWarning(surf.getAveDip());
		rupWidthParam.setValueIgnoreWarning(surf.getAveWidth());
		rupTopDepthParam.setValueIgnoreWarning(surf.getAveRupTopDepth());
		double zHyp;
		if (eqkRupture.getHypocenterLocation() != null) {
			zHyp = eqkRupture.getHypocenterLocation().getDepth();
		} else {
			zHyp = surf.getAveRupTopDepth() +
				Math.sin(surf.getAveDip() * TO_RAD) * surf.getAveWidth() /
				2.0;
		}
		focalDepthParam.setValueIgnoreWarning(zHyp);
		
		setPropagationEffectParams();
	}

	@Override
	protected void setPropagationEffectParams() {
		if (site != null && eqkRupture != null) {
			Location siteLoc = site.getLocation();
			RuptureSurface surf = eqkRupture.getRuptureSurface();
			
			distanceJBParam.setValueIgnoreWarning(surf.getDistanceJB(siteLoc));
			distanceRupParam.setValueIgnoreWarning(surf.getDistanceRup(siteLoc));
			distanceXParam.setValueIgnoreWarning(surf.getDistanceX(siteLoc));
		}
	}
	
	/**
	 * This clears any stored GM value and should be called whenever a parameter is changed
	 */
	private void clear() {
		gm = null;
	}
	
	/**
	 * This updates all values in the wrapped GMPE
	 */
	private synchronized ScalarGroundMotion getGroundMotion() {
		if (gm != null)
			return gm;
		
		// IMT
		IMT imt;
		if (im.getName().equals(SA_Param.NAME))
			imt = IMT.getSA(SA_Param.getPeriodInSA_Param(im));
		else
			imt = IMT.parseIMT(im.getName());
		
		Double rake = rakeParam.getValue();
		// assert in range [-180 180]
		if (rake != null)
			FaultUtils.assertValidRake(rake);
		FaultStyle style = getFaultStyle(rake);
		
		gmpe.set_IMT(imt);
		
		double mag = magParam.getValue();
		gmpe.set_Mw(mag);
		double rJB = distanceJBParam.getValue();
		gmpe.set_rJB(rJB);
		gmpe.set_rRup(distanceRupParam.getValue());
		gmpe.set_rX(distanceXParam.getValue());
		
		gmpe.set_dip(dipParam.getValue());
		gmpe.set_width(rupWidthParam.getValue());
		gmpe.set_zTop(rupTopDepthParam.getValue());
		gmpe.set_zHyp(focalDepthParam.getValue());
		gmpe.set_vs30(vs30Param.getValue());
		gmpe.set_vsInf(vs30_TypeParam.getValue().equals(Vs30_TypeParam.VS30_TYPE_INFERRED));
		if (depthTo2pt5kmPerSecParam.getValue() == null)
			gmpe.set_z2p5(Double.NaN);
		else
			gmpe.set_z2p5(depthTo2pt5kmPerSecParam.getValue());
		if (depthTo1pt0kmPerSecParam.getValue() == null)
			gmpe.set_z1p0(Double.NaN);
		else
			// OpenSHA has Z1.0 in m instead of km, need to convert
			gmpe.set_z1p0(depthTo1pt0kmPerSecParam.getValue()/1000d);
		
		gmpe.set_fault(style);
		
		gm = gmpe.calc();
		
		if (epiParam.getValue() != null) {
			double sign = epiParam.getValue().sign;
			double val = getUncertainty(mag, rJB);
			if (supportsPhiTau)
				gm = new DefaultGroundMotion(gm.mean()+val*sign, gm.stdDev(), gm.phi(), gm.tau());
			else
				gm = new DefaultGroundMotion(gm.mean(), gm.stdDev());
		}
		
		return gm;
	}
	
	static FaultStyle getFaultStyle(Double rake) {
		if (rake == null)
			return FaultStyle.UNKNOWN;
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
	protected void initSupportedIntensityMeasureParams() {
		supportedIMParams.clear();
		
		// Create saParam:
		DoubleDiscreteConstraint periodConstraint = new DoubleDiscreteConstraint();
		HashSet<IMT> imtsSet = new HashSet<IMT>(gmpe.getSupportedIMTs());
		for (IMT imt : imtsSet) {
			Double p = imt.getPeriod();
			if (p != null)
				periodConstraint.addDouble(p);
		}
		periodConstraint.setNonEditable();
		saPeriodParam = new PeriodParam(periodConstraint);
		saPeriodParam.setValueAsDefault();
		saPeriodParam.addParameterChangeListener(this);
		saDampingParam = new DampingParam();
		saDampingParam.setValueAsDefault();
		saDampingParam.addParameterChangeListener(this);
		saParam = new SA_Param(saPeriodParam, saDampingParam);
		saParam.setNonEditable();
		saParam.addParameterChangeWarningListener(listener);
		supportedIMParams.addParameter(saParam);

		if (imtsSet.contains(IMT.PGA)) {
			//  Create PGA Parameter (pgaParam):
			pgaParam = new PGA_Param();
			pgaParam.setNonEditable();
			pgaParam.addParameterChangeWarningListener(listener);
			supportedIMParams.addParameter(pgaParam);
		}

		if (imtsSet.contains(IMT.PGV)) {
			//  Create PGV Parameter (pgvParam):
			pgvParam = new PGV_Param();
			pgvParam.setNonEditable();
			pgvParam.addParameterChangeWarningListener(listener);
			supportedIMParams.addParameter(pgvParam);
		}

		if (imtsSet.contains(IMT.PGD)) {
			//  Create PGD Parameter (pgdParam):
			pgdParam = new PGD_Param();
			pgdParam.setNonEditable();
			pgdParam.addParameterChangeWarningListener(listener);
			supportedIMParams.addParameter(pgdParam);
		}
	}

	@Override
	protected void initSiteParams() {
		// we just have these such that they show up as requirements to use this IMR
		vs30Param = new Vs30_Param(760, 150, 1500);
		vs30_TypeParam = new Vs30_TypeParam();
		depthTo1pt0kmPerSecParam = new DepthTo1pt0kmPerSecParam(
				null, DepthTo1pt0kmPerSecParam.MIN, DepthTo1pt0kmPerSecParam.MAX, true);
		depthTo2pt5kmPerSecParam = new DepthTo2pt5kmPerSecParam(
				null, DepthTo2pt5kmPerSecParam.MIN, DepthTo2pt5kmPerSecParam.MAX, true);
		
		siteParams.clear();
		siteParams.addParameter(vs30Param);
		siteParams.addParameter(vs30_TypeParam);
		siteParams.addParameter(depthTo1pt0kmPerSecParam);
		siteParams.addParameter(depthTo2pt5kmPerSecParam);
		
		for (Parameter<?> param : siteParams)
			param.addParameterChangeListener(this);
	}

	@Override
	protected void initEqkRuptureParams() {
		eqkRuptureParams.clear();
		
		magParam = new MagParam(4, 9, 5.0);
		eqkRuptureParams.addParameter(magParam);
		
		dipParam = new DipParam(15, 90, 90);
		eqkRuptureParams.addParameter(dipParam);
		
		rupWidthParam = new RupWidthParam(0d, 500d, 10d);
		eqkRuptureParams.addParameter(rupWidthParam);
		
		rakeParam = new RakeParam(null, true);
		eqkRuptureParams.addParameter(rakeParam);
		
		rupTopDepthParam = new RupTopDepthParam(0, 15, 0);
		eqkRuptureParams.addParameter(rupTopDepthParam);
		
		focalDepthParam = new FocalDepthParam(0, 15, 0);
		eqkRuptureParams.addParameter(focalDepthParam);
		
		for (Parameter<?> param : eqkRuptureParams)
			param.addParameterChangeListener(this);
	}

	@Override
	protected void initPropagationEffectParams() {
		propagationEffectParams.clear();
		
		distanceJBParam = new DistanceJBParameter(new DoubleConstraint(0d, 400d), 0d);
		propagationEffectParams.addParameter(distanceJBParam);
		
		distanceRupParam = new DistanceRupParameter(new DoubleConstraint(0d, 400d), 0d);
		propagationEffectParams.addParameter(distanceRupParam);
		
		distanceXParam = new DistanceX_Parameter(new DoubleConstraint(-400, 400d), 0d);
		propagationEffectParams.addParameter(distanceXParam);
		
		for (Parameter<?> param : propagationEffectParams)
			param.addParameterChangeListener(this);
	}

	@Override
	protected void initOtherParams() {
		super.initOtherParams();
		
		componentParam = new ComponentParam(Component.RotD50, Component.RotD50);
		componentParam.setValueAsDefault();
		componentParam.addParameterChangeListener(this);
		otherParams.addParameter(componentParam);
		
		// the stdDevType Parameter
		StringConstraint stdDevTypeConstraint = new StringConstraint();
		stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_TOTAL);
		if (supportsPhiTau) {
			stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_INTER);
			stdDevTypeConstraint.addString(StdDevTypeParam.STD_DEV_TYPE_INTRA);
		}
		stdDevTypeConstraint.setNonEditable();
		stdDevTypeParam = new StdDevTypeParam(stdDevTypeConstraint);
		otherParams.addParameter(stdDevTypeParam); 
		
		StringConstraint options = new StringConstraint();
		options.addString(gmpe.get_TRT().toString());
		tectonicRegionTypeParam.setConstraint(options);
	    tectonicRegionTypeParam.setDefaultValue(gmpe.get_TRT().toString());
	    tectonicRegionTypeParam.setValueAsDefault();
	    
		epiParam = new EnumParameter<EpistemicOption>(EPISTEMIC_PARAM_NAME,
	    		EnumSet.allOf(EpistemicOption.class), null, "(Disabled)");
	    epiParam.setValue(null);
	    epiParam.addParameterChangeListener(this);
	    otherParams.addParameter(epiParam);
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		clear();
	}
	
	@Override
	public void setIntensityMeasure(String intensityMeasureName) throws ParameterException {
		super.setIntensityMeasure(intensityMeasureName);
		if (epiParam != null) {
			boolean supports = supportsEpi(intensityMeasureName);
			if (!supports) {
				epiParam.setValue(null);
				epiParam.getEditor().refreshParamEditor();
			}
			epiParam.getEditor().setEnabled(supports);
		}
		clear();
	}
	
	private boolean supportsEpi(String imt) {
		return imt.equals(PGA_Param.NAME) || imt.equals(SA_Param.NAME);
	}

	public NGAW2_GMM getGMM() {
		return gmpe;
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
		meanIndependentParams.addParameter(distanceRupParam);
		meanIndependentParams.addParameter(distanceJBParam);
		meanIndependentParams.addParameter(distanceXParam);
		
		meanIndependentParams.addParameter(vs30Param);
		meanIndependentParams.addParameter(depthTo2pt5kmPerSecParam);
		meanIndependentParams.addParameter(depthTo1pt0kmPerSecParam);
		
		meanIndependentParams.addParameter(magParam);
		meanIndependentParams.addParameter(rakeParam);
		meanIndependentParams.addParameter(dipParam);
		meanIndependentParams.addParameter(rupTopDepthParam);
		meanIndependentParams.addParameter(rupWidthParam);
		meanIndependentParams.addParameter(focalDepthParam);
		meanIndependentParams.addParameter(componentParam);

		// params that the stdDev depends upon
		stdDevIndependentParams.clear();
		stdDevIndependentParams.addParameterList(meanIndependentParams);
		stdDevIndependentParams.addParameter(vs30_TypeParam);
		stdDevIndependentParams.addParameter(stdDevTypeParam);

		// params that the exceed. prob. depends upon
		exceedProbIndependentParams.clear();
		exceedProbIndependentParams.addParameterList(stdDevIndependentParams);
		exceedProbIndependentParams.addParameter(sigmaTruncTypeParam);
		exceedProbIndependentParams.addParameter(sigmaTruncLevelParam);

		// params that the IML at exceed. prob. depends upon
		imlAtExceedProbIndependentParams.addParameterList(
				exceedProbIndependentParams);
		imlAtExceedProbIndependentParams.addParameter(exceedProbParam);
	}
	
	private static final double[][] EPI_VAL = {
			{0.375, 0.250, 0.400},
			{0.220, 0.230, 0.360},
			{0.220, 0.230, 0.330}};

	/*
	 * Returns the epistemic uncertainty for the supplied magnitude (M) and
	 * distance (D) that
	 */
	private static double getUncertainty(double M, double D) {
		int mi = (M<6) ? 0 : (M<7) ? 1 : 2;
		int di = (D<10) ? 0 : (D<30) ? 1 : 2;
		return EPI_VAL[di][mi];
	}

}
