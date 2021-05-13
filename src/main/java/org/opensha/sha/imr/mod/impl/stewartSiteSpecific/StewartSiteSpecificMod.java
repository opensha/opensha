package org.opensha.sha.imr.mod.impl.stewartSiteSpecific;

import java.io.IOException;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.mod.AbstractAttenRelMod;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

public class StewartSiteSpecificMod extends AbstractAttenRelMod implements ParameterChangeListener {
	
	private static final boolean D = true;
	private static final boolean DD = D && true;
	
	public static final String NAME = "Stewart 2014 Site Specific";
	public static final String SHORT_NAME = "Stewart2014";
	
	public enum Params {
		F1("f1"),
		F2("f2"),
		F3("f3"),
		PHI_lnY("ϕ lnY"),
		PHI_S2S("ϕ S2S"),
		F("F"),
		Ymax("Ymax");
		
		private String name;
		
		private Params(String name) {
			this.name = name;
		}
		
		@Override
		public String toString() {
			return name;
		}
	}
	
	private PeriodDependentParamSet<Params> periodParams;
	private double curPeriod;
	private double[] curParamValues;
	private PeriodDependentParamSetParam<Params> periodParamsParam;
	
	private ParameterList paramList;
	
	private Parameter<Double> imt;
	
	private ParameterList referenceSiteParams;
	
	public StewartSiteSpecificMod() {
		try {
			periodParams = PeriodDependentParamSet.loadCSV(Params.values(), this.getClass().getResourceAsStream("./params.csv"));
			System.out.println("Loaded default params:\n"+periodParams);
		} catch (IOException e) {
			System.err.println("Error loading default params:");
			e.printStackTrace();
			periodParams = new PeriodDependentParamSet<StewartSiteSpecificMod.Params>(Params.values());
		}
		periodParamsParam = new PeriodDependentParamSetParam<Params>("Period Dependent Params", periodParams);
		periodParamsParam.addParameterChangeListener(this);
		
		paramList = new ParameterList();
		paramList.addParameter(periodParamsParam);
		
		setReferenceSiteParams(getDefaultReferenceSiteParams());
	}
	
	static ParameterList getDefaultReferenceSiteParams() {
		ParameterList params = new ParameterList();
		
		Vs30_Param vs30 = new Vs30_Param(760);
		vs30.setValueAsDefault();
		params.addParameter(vs30);
		
		Vs30_TypeParam vs30Type = new Vs30_TypeParam();
		vs30Type.setValue(Vs30_TypeParam.VS30_TYPE_INFERRED);
		params.addParameter(vs30Type);
		
		DepthTo2pt5kmPerSecParam z25 = new DepthTo2pt5kmPerSecParam(null, true);
		z25.setValueAsDefault();
		params.addParameter(z25);
		
		DepthTo1pt0kmPerSecParam z10 = new DepthTo1pt0kmPerSecParam(null, true);
		z10.setValueAsDefault();
		params.addParameter(z10);
		
		return params;
	}
	
	public void setReferenceSiteParams(ParameterList referenceSiteParams) {
		this.referenceSiteParams = referenceSiteParams;
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
	public void setIMRParams(ScalarIMR imr) {
		// TODO Auto-generated method stub

		// surround each with a try catch as certain IMRs may not have the given site parameter
		try {
			Parameter<Double> param = imr.getParameter(Vs30_Param.NAME);
			param.setValue(referenceSiteParams.getParameter(Double.class, Vs30_Param.NAME).getValue());
		} catch (ParameterException e) {
			// do nothing - IMR does not have this parameter
			if (D) System.out.println("IMR doesn't support Vs30");
		}
		try {
			Parameter<String> param = imr.getParameter(Vs30_TypeParam.NAME);
			param.setValue(referenceSiteParams.getParameter(String.class, Vs30_TypeParam.NAME).getValue());
		} catch (ParameterException e) {
			// do nothing - IMR does not have this parameter
			if (D) System.out.println("IMR doesn't support Vs30 Type");
		}
		try {
			Parameter<Double> param = imr.getParameter(DepthTo2pt5kmPerSecParam.NAME);
			param.setValue(referenceSiteParams.getParameter(Double.class, DepthTo2pt5kmPerSecParam.NAME).getValue()); // disable Z2500
		} catch (ParameterException e) {
			// do nothing - IMR does not have this parameter
			if (D) System.out.println("IMR doesn't support Z2.5");
		}
		try {
			Parameter<Double> param = imr.getParameter(DepthTo1pt0kmPerSecParam.NAME);
			param.setValue(referenceSiteParams.getParameter(Double.class, DepthTo1pt0kmPerSecParam.NAME).getValue());
		} catch (ParameterException e) {
			// do nothing - IMR does not have this parameter
			if (D) System.out.println("IMR doesn't support Z1.0");
		}
		if (D) System.out.println("Set site params to default");
	}

	@Override
	public void setIMT_IMT(ScalarIMR imr, Parameter<Double> imt) {
		this.imt = imt;
		if (imt.getName().equals(SA_Param.NAME)) {
			curPeriod = SA_Param.getPeriodInSA_Param(imt);
		} else {
			Preconditions.checkState(imt.getName().equals(PGA_Param.NAME), "Only SA and PGA supported");
			curPeriod = 0;
		}
		
		curParamValues = null;
		super.setIMT_IMT(imr, imt);
	}
	
	private synchronized double[] getCurParams() {
		if (curParamValues == null)
			curParamValues = periodParams.getInterpolated(periodParams.getParams(), curPeriod);
		return curParamValues;
	}
	
	public void setSiteAmpParams(double period, Map<Params, Double> values) {
		for (Params param : values.keySet())
			periodParams.set(period, param, values.get(param));
	}

	@Override
	public void setIMRSiteParams(ScalarIMR imr, Site site) {
		// just update the site location, don't call setSite as it will override our site parameters
		imr.setSiteLocation(site.getLocation());
	}

	@Override
	public double getModMean(ScalarIMR imr) {
		double u_lnX = imr.getMean();
		if (DD) {
			String imt = imr.getIntensityMeasure().getName();
			if (imt.equals(SA_Param.NAME))
				imt += " "+(float)+SA_Param.getPeriodInSA_Param(imr.getIntensityMeasure())+"s";
			if (DD) System.out.println("Orig IMT: "+imt);
		}
		if (DD) System.out.println("Orig Mean, u_X="+Math.exp(u_lnX));
		
		// now set to to PGA
		if (DD) System.out.println("Setting to PGA");
		imr.setIntensityMeasure(PGA_Param.NAME);
		Preconditions.checkState(imr.getIntensityMeasure().getName().equals(PGA_Param.NAME));
		double x_ref_ln = imr.getMean();
		double x_ref = Math.exp(x_ref_ln); // ref IMR, must be linear
		if (DD) System.out.println("Ref PGA, x_ref="+x_ref);
		// set back to orig IMT
		imr.setIntensityMeasure(imt);
		Preconditions.checkState(imr.getIntensityMeasure().getName().equals(imt.getName()));
		
		double[] params = getCurParams();
		double f1 = params[periodParams.getParamIndex(Params.F1)];
		double f2 = params[periodParams.getParamIndex(Params.F2)];
		double f3 = params[periodParams.getParamIndex(Params.F3)];
		
		if (DD) System.out.println("Calculating mean with f1="+f1+", f2="+f2+", f3="+f3);
		
		double ln_y = f1 + f2*Math.log((x_ref + f3)/f3);
		if (DD) System.out.println("y="+Math.exp(ln_y));
		double yMax = Math.log(params[periodParams.getParamIndex(Params.Ymax)]);
		if (!Double.isNaN(yMax)) {
			ln_y = Math.min(ln_y, yMax);
			if (DD) System.out.println("new y (after yMax="+yMax+"): "+Math.exp(ln_y));
		}
		
		Preconditions.checkState(Doubles.isFinite(ln_y));
		
		return u_lnX + ln_y;
	}

	@Override
	public double getModStdDev(ScalarIMR imr) {
		StringParameter imrTypeParam = (StringParameter) imr.getParameter(StdDevTypeParam.NAME);
		String origIMRType = imrTypeParam.getValue();
		imrTypeParam.setValue(StdDevTypeParam.STD_DEV_TYPE_INTER);
		double interStdDev = imr.getStdDev();
		if (DD) System.out.println("Orig inter event, tau="+interStdDev);
		imrTypeParam.setValue(StdDevTypeParam.STD_DEV_TYPE_INTRA);
		double intraStdDev = imr.getStdDev();
		if (DD) System.out.println("Orig intra event, phi="+intraStdDev);
		imrTypeParam.setValue(origIMRType);
		
		double[] params = getCurParams();
		double f2 = params[periodParams.getParamIndex(Params.F2)];
		double f3 = params[periodParams.getParamIndex(Params.F3)];
		double F = params[periodParams.getParamIndex(Params.F)];
		double phiS2S = params[periodParams.getParamIndex(Params.PHI_S2S)];
		double phiLnY = params[periodParams.getParamIndex(Params.PHI_lnY)];
		
		if (DD) System.out.println("Calculating std dev with f2="+f2+", f3="+f3+", F="+F+", phiS2S="+phiS2S+", phiLnY="+phiLnY);
		
		// now set to to PGA
		imr.setIntensityMeasure(PGA_Param.NAME);
		double x_ref_ln = imr.getMean();
		double x_ref = Math.exp(x_ref_ln); // ref IMR, must be linear
		if (DD) System.out.println("x_ref="+x_ref);
		// set back to orig IMT
		imr.setIntensityMeasure(imt);
		
		double term1 = Math.pow((f2*x_ref)/(x_ref+f3) + 1, 2);
		
		double phi_lnZ = Math.sqrt(term1 * (intraStdDev*intraStdDev - F*phiS2S*phiS2S) + phiLnY*phiLnY);
		
		if (DD) System.out.println("phi_lnZ="+phi_lnZ);
		
		return Math.sqrt(phi_lnZ*phi_lnZ + interStdDev*interStdDev);
	}

	@Override
	public ParameterList getModParams() {
		// TODO Auto-generated method stub
		return paramList;
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (D) System.out.println("Period params change, clearing");
		curParamValues = null;
	}
	
	public static void main(String[] args) {
		new StewartSiteSpecificMod();
	}

}
