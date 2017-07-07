package org.opensha.sha.imr.attenRelImpl.ngaw2;

import static java.lang.Math.sin;
import static org.opensha.commons.geo.GeoTools.TO_RAD;

import java.util.HashSet;
import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.IMRException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.constraint.impl.DoubleDiscreteConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGD_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.Component;
import org.opensha.sha.imr.param.OtherParams.ComponentParam;
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
public class NGAW2_Wrapper extends AttenuationRelationship implements ParameterChangeListener {
	
	private String shortName;
	private NGAW2_GMM gmpe;
	private ScalarGroundMotion gm;
	
	public NGAW2_Wrapper(String shortName, NGAW2_GMM gmpe) {
		this.shortName = shortName;
		this.gmpe = gmpe;
		
		initSupportedIntensityMeasureParams();
		initSiteParams();
		initOtherParams();
	}
	
	NGAW2_GMM getGMPE() {
		return gmpe;
	}

	@Override
	public double getMean() {
		if (gm == null)
			gm = gmpe.calc();
		return gm.mean();
	}

	@Override
	public double getStdDev() {
		if (gm == null)
			gm = gmpe.calc();
		return gm.stdDev();
	}

	@Override
	public void setParamDefaults() {
		for (Parameter<?> param : siteParams)
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
		update();
	}

	@Override
	public void setEqkRupture(EqkRupture eqkRupture) {
		super.setEqkRupture(eqkRupture);
		update();
	}

	@Override
	public void setIntensityMeasure(Parameter intensityMeasure)
			throws ParameterException, ConstraintException {
		super.setIntensityMeasure(intensityMeasure);
		update();
	}

	@Override
	public void setIntensityMeasure(String intensityMeasureName)
			throws ParameterException {
		super.setIntensityMeasure(intensityMeasureName);
		update();
	}

	@Override
	public void setAll(EqkRupture eqkRupture, Site site,
			Parameter intensityMeasure) throws ParameterException,
			IMRException, ConstraintException {
		// use super so as to not trigger multiple update calls
		super.setSite(site);
		super.setEqkRupture(eqkRupture);
		super.setIntensityMeasure(intensityMeasure);
		update();
	}

	@Override
	protected void setPropagationEffectParams() {
		update();
	}
	
	/**
	 * This updates all values in the wrapped GMPE
	 */
	private void update() {
		gm = null;
		
		if (site != null && eqkRupture != null) {
			// IMT
			IMT imt;
			if (im.getName().equals(SA_Param.NAME))
				imt = IMT.getSA(SA_Param.getPeriodInSA_Param(im));
			else
				imt = IMT.parseIMT(im.getName());
			
			RuptureSurface surf = eqkRupture.getRuptureSurface();
			Location siteLoc = site.getLocation();
			
			this.vs30Param.setValue(site.getParameter(Double.class, Vs30_Param.NAME).getValue());
			this.vs30_TypeParam.setValue(site.getParameter(String.class, Vs30_TypeParam.NAME).getValue());
			this.depthTo1pt0kmPerSecParam.setValue(site.getParameter(Double.class,
					DepthTo1pt0kmPerSecParam.NAME).getValue());
			this.depthTo2pt5kmPerSecParam.setValue(site.getParameter(Double.class,
					DepthTo2pt5kmPerSecParam.NAME).getValue());
			
			double rake = eqkRupture.getAveRake();
			// assert in range [-180 180]
			FaultUtils.assertValidRake(rake);
			FaultStyle style = getFaultStyle(rake);
			
			gmpe.set_IMT(imt);
			
			gmpe.set_Mw(eqkRupture.getMag());
			
			gmpe.set_rJB(surf.getDistanceJB(siteLoc));
			gmpe.set_rRup(surf.getDistanceRup(siteLoc));
			gmpe.set_rX(surf.getDistanceX(siteLoc));
			
			gmpe.set_dip(surf.getAveDip());
			gmpe.set_width(surf.getAveWidth());
			gmpe.set_zTop(surf.getAveRupTopDepth());
			if (eqkRupture.getHypocenterLocation() != null) {
				gmpe.set_zHyp(eqkRupture.getHypocenterLocation().getDepth());
			} else {
				double zHyp = surf.getAveRupTopDepth() +
					Math.sin(surf.getAveDip() * TO_RAD) * surf.getAveWidth() /
					2.0;
				gmpe.set_zHyp(zHyp);
			}
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
			
		} else {
			gmpe.set_IMT(null);
			
			gmpe.set_Mw(Double.NaN);
			
			gmpe.set_rJB(Double.NaN);
			gmpe.set_rRup(Double.NaN);
			gmpe.set_rX(Double.NaN);
			
			gmpe.set_dip(Double.NaN);
			gmpe.set_width(Double.NaN);
			gmpe.set_zTop(Double.NaN);
			gmpe.set_zHyp(Double.NaN);

			gmpe.set_vs30(Double.NaN);
			gmpe.set_vsInf(true);
			gmpe.set_z2p5(Double.NaN);
			gmpe.set_z1p0(Double.NaN);
			
			gmpe.set_fault(null);
		}
	}
	
	static FaultStyle getFaultStyle(double rake) {
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
		vs30Param = new Vs30_Param();
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
	}

	@Override
	protected void initEqkRuptureParams() {
		
	}

	@Override
	protected void initPropagationEffectParams() {
		
	}

	@Override
	protected void initOtherParams() {
		super.initOtherParams();
		
		componentParam = new ComponentParam(Component.RotD50, Component.RotD50);
		componentParam.setValueAsDefault();
		otherParams.addParameter(componentParam);
		
		StringConstraint options = new StringConstraint();
		options.addString(gmpe.get_TRT().toString());
		tectonicRegionTypeParam.setConstraint(options);
	    tectonicRegionTypeParam.setDefaultValue(gmpe.get_TRT().toString());
	    tectonicRegionTypeParam.setValueAsDefault();
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		update();
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
		meanIndependentParams.addParameter(distRupMinusJB_OverRupParam);
		meanIndependentParams.addParameter(vs30Param);
		meanIndependentParams.addParameter(depthTo2pt5kmPerSecParam);
		meanIndependentParams.addParameter(magParam);
		meanIndependentParams.addParameter(fltTypeParam);
		meanIndependentParams.addParameter(rupTopDepthParam);
		meanIndependentParams.addParameter(dipParam);
		meanIndependentParams.addParameter(componentParam);


		// params that the stdDev depends upon
		stdDevIndependentParams.clear();
		stdDevIndependentParams.addParameterList(meanIndependentParams);
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

}
