package org.opensha.sha.imr.mod.impl.stewartSiteSpecific;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.data.Site;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.ButtonParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.Interpolate;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.ngaw2.BSSA_2014;
import org.opensha.sha.imr.attenRelImpl.ngaw2.IMT;
import org.opensha.sha.imr.mod.AbstractAttenRelMod;
import org.opensha.sha.imr.mod.impl.IML_DependentAttenRelMod;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.StdDevTypeParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

public class NonErgodicSiteResponseMod extends IML_DependentAttenRelMod implements ParameterChangeListener {
	
	private static final boolean D = false;
	private static final boolean DD = D && false;
	
	public static final String NAME = "Non Ergodic Site Response 2016 Mod";
	public static final String SHORT_NAME = "NonErgodic2016";
	
	public enum Params implements ShortNamed {
		F1("f1", "Weak-motion (linear) amplification"),
		F2("f2", "The level of nonlinearity in site response."),
		F3("f3", "The level of reference site ground shaking below which the amplification"
				+ "\nconverges towards a linear (constant) upper limit."),
		PHI_lnY("ϕ lnY", "Standard deviation of amplification function"),
		PHI_S2S("ϕ S2S", "Represents site-to-site amplification variability"),
		F("F", "Reduction factor for site-to-site variability for single site analysis which is between 0 and 1. "
				+ "\nF=1 means completely eliminating site-to-site variability, F=0 means no reduction of site-to-site variability."),
		PHI_SS("ϕ SS", "Represents within-event single site standard deviation of ground motion. "
				+ "\nIn the case of activating \"Use ϕ SS\" button, ϕ S2S will be ignored, and ϕ SSs will be used."),
		Ymax("Ymax", "(Optional) Weak motion amplification level beyond which the amplification function will be truncated to Ymax. "
				+ "\nIf no values (or NaN) are entered, no truncation will happen.");
		
		private String name;
		private String description;
		
		private Params(String name, String description) {
			this.name = name;
			this.description = description;
		}
		
		@Override
		public String toString() {
			return name;
		}

		@Override
		public String getName() {
			return description;
		}

		@Override
		public String getShortName() {
			return name;
		}
	}
	
	private static final Map<Params, Double> defaultParamValues = Maps.newHashMap();
	static {
		defaultParamValues.put(Params.F1, null); // null means from empirical
		defaultParamValues.put(Params.F2, null); // null means from empirical
		defaultParamValues.put(Params.F3, 0.1);
		defaultParamValues.put(Params.PHI_lnY, 0.3);
		defaultParamValues.put(Params.PHI_S2S, 0.4);
		defaultParamValues.put(Params.F, 1d);
		defaultParamValues.put(Params.PHI_SS, 0.437);
		defaultParamValues.put(Params.Ymax, Double.NaN);
		
		// make sure complete
		for (Params param : Params.values())
			Preconditions.checkState(defaultParamValues.containsKey(param), "Missing default for %s", param);
	}
	private static double[] periodsForDefault = { 0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1d };
	
	public enum RatioParams {
		RATIO("Ratio");
		
		private String name;
		
		private RatioParams(String name) {
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
	
	private BooleanParameter usePhiSSParam;
	
//	private static final HashSet<String> allowedRefIMTs = new HashSet<String>();
//	static {
//		allowedRefIMTs.add(PGA_Param.NAME);
//		allowedRefIMTs.add(PGV_Param.NAME);
//		allowedRefIMTs.add(SA_Param.NAME);
//	}
	
	private DoubleParameter rhoParam;
	
	private static final String REF_IMT_DEFAULT = PGA_Param.NAME;
	private static final String REF_IMT_OF_INTEREST = "IMT of Interst";
	private StringParameter refIMTParam;
	
	private PeriodDependentParamSet<RatioParams> imtRatios;
	private PeriodDependentParamSetParam<RatioParams> imtRatiosParam;
	
	private DoubleParameter tSiteParam;
	private DoubleParameter tSiteNParam;
	
	private ButtonParameter plotInterpParam;
	
	private ParameterList paramList;
	
	private Parameter<Double> imt;
	
	private Site curSite;
	
	private BSSA_ParamInterpolator interp;
	
	private ParameterList referenceSiteParams;
	private boolean refSiteParamsStale = true;
	
	public NonErgodicSiteResponseMod() {
		// this is for loading defaults from CSV
		try {
			periodParams = PeriodDependentParamSet.loadCSV(Params.values(), this.getClass().getResourceAsStream("params.csv"));
			if (DD) System.out.println("Loaded default params:\n"+periodParams);
		} catch (IOException e) {
			System.err.println("Error loading default params:");
			e.printStackTrace();
			periodParams = new PeriodDependentParamSet<NonErgodicSiteResponseMod.Params>(Params.values());
		}
		// this is for leaving it blank to be filled in my ergodic model
//		periodParams = new PeriodDependentParamSet<NonErgodicSiteResponseMod.Params>(Params.values());
		
		periodParamsParam = new PeriodDependentParamSetParam<Params>("Period Dependent Params", periodParams);
		periodParamsParam.addParameterChangeListener(this);
		
		paramList = new ParameterList();
		paramList.addParameter(periodParamsParam);
		
		usePhiSSParam = new BooleanParameter("Use "+Params.PHI_SS.toString(), false);
		paramList.addParameter(usePhiSSParam);
		
		tSiteParam = new DoubleParameter("Tsite", Double.NaN);
		tSiteParam.setInfo(
					"(Optional) The natural period of the site (sec). The transition of amplification parameters from "
				+ 	"\nuser-entered values to the Semi-empirical model starts from Tsite. If no values are entered, the "
				+ 	"\ntransition will not happen, and the last user-entered value for amplification parameters will be used "
				+ 	"\nfor all periods longer than the longest period for which the user has entered the amplification values.");
		tSiteParam.addParameterChangeListener(this);
		paramList.addParameter(tSiteParam);
		
		tSiteNParam = new DoubleParameter("N for Tsite", 2d);
		tSiteNParam.setInfo("The transition from user-entered values to the Semi-empirical model starts at Tsite and ends at N*Tsite.");
		tSiteNParam.addParameterChangeListener(this);
		paramList.addParameter(tSiteNParam);
		
		plotInterpParam = new ButtonParameter("Interpolation", "Plot Interpolation");
		plotInterpParam.addParameterChangeListener(this);
		paramList.addParameter(plotInterpParam);
		
		// TODO name
		rhoParam = new DoubleParameter("lnZ, lnX correlation", 0d, 1d, new Double(0.65));
		rhoParam.setInfo("Correlation between surface intensity measure and the reference intensity measure.");
		// don't need to listen for changes
		paramList.addParameter(rhoParam);
		
		refIMTParam = new StringParameter("Reference IMT", Lists.newArrayList(REF_IMT_DEFAULT, REF_IMT_OF_INTEREST));
		refIMTParam.setValue(REF_IMT_DEFAULT);
		refIMTParam.addParameterChangeListener(this);
		paramList.addParameter(refIMTParam);
		
		try {
			imtRatios = PeriodDependentParamSet.loadCSV(RatioParams.values(), this.getClass().getResourceAsStream("ratios.csv"));
		} catch (IOException e) {
			System.err.println("Error loading default ratios:");
			e.printStackTrace();
			imtRatios = new PeriodDependentParamSet<NonErgodicSiteResponseMod.RatioParams>(RatioParams.values());
		}
		imtRatiosParam = new PeriodDependentParamSetParam<RatioParams>("Ref IMT Ratio for f3 interpolation", imtRatios);
		imtRatiosParam.setInfo(
				"In the case that Reference IM is not PGA, these values will be used to convert PGA to the\n"
				+ "reference IM for f3 calculations. For details see Stewart et al. (2014).");
		paramList.addParameter(imtRatiosParam);
		
		setReferenceSiteParams(getDefaultReferenceSiteParams());
	}
	
	static ParameterList getDefaultReferenceSiteParams() {
		ParameterList params = new ParameterList();
		
		Vs30_Param vs30 = new Vs30_Param(760);
		vs30.setValueAsDefault();
		params.addParameter(vs30);
		
		Vs30_TypeParam vs30Type = new Vs30_TypeParam();
		vs30Type.setValue(Vs30_TypeParam.VS30_TYPE_MEASURED);
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
		if (this.referenceSiteParams == null) {
			this.referenceSiteParams = referenceSiteParams;
			for (Parameter<?> param : referenceSiteParams)
				param.addParameterChangeListener(this);
		} else {
			for (Parameter<?> param : referenceSiteParams)
				this.referenceSiteParams.getParameter(param.getName()).setValue(param.getValue());
		}
	}
	
	ParameterList getReferenceSiteParams() {
		return this.referenceSiteParams;
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
		refSiteParamsStale = true;
		checkUpdateRefIMRSiteParams(imr);
		
		Preconditions.checkState(imr.isIntensityMeasureSupported(REF_IMT_DEFAULT));
		// the following can be used to enable other reference IMRs. for now just force PGA
		// (will have to update interpolation for F3 if enabled and add specifics for other GMPEs)
//		StringConstraint imtConstr = (StringConstraint) refIMTParam.getConstraint();
//		ArrayList<String> allowedIMTs = Lists.newArrayList();
//		for (Parameter<?> param : imr.getSupportedIntensityMeasures())
//			if (allowedRefIMTs.contains(param.getName()))
//				allowedIMTs.add(param.getName());
//		Preconditions.checkState(!allowedIMTs.isEmpty(), "IMR doesn't support any IMTs???");
//		imtConstr.setStrings(allowedIMTs);
//		if (imtConstr.isAllowed(REF_IMT_DEFAULT)) {
//			if (D && !refIMTParam.getValue().equals(REF_IMT_DEFAULT))
//				System.out.println("Resetting reference IMT to default, "+REF_IMT_DEFAULT);
//			refIMTParam.setValue(REF_IMT_DEFAULT);
//		} else {
//			String val = allowedIMTs.get(0);
//			if (D) System.out.println("WARNING: Reference IMR doesn't support default ref IMT, "
//					+REF_IMT_DEFAULT+". Fell back to "+val);
//			refIMTParam.setValue(val);
//		}
//		refIMTParam.getEditor().refreshParamEditor();
	}
	
	private void checkUpdateRefIMRSiteParams(ScalarIMR imr) {
		if (!refSiteParamsStale)
			return;
		if (D) System.out.println("Updating site params");
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
		if (DD) System.out.println("Set site params to default");
		refSiteParamsStale = false;
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
	
	private BSSA_ParamInterpolator getInterpolator() {
		if (interp == null)
			interp = new BSSA_ParamInterpolator(imtRatios);
		return interp;
	}
	
	private synchronized double[] getCurParams(ScalarIMR imr) {
		if (curParamValues == null) {
			BSSA_ParamInterpolator interp = getInterpolator();
//			if (imr.getName().startsWith(BSSA_2014.NAME)) {
//				if (interp == null || !(interp instanceof BSSA_ParamInterpolator))
//					interp = new BSSA_ParamInterpolator();
//			} else {
//				interp = null;
//			}
			if (periodParams.isEmpty()) {
				// it's empty, populate with values from empirical model
				double vs30 = (Double)curSite.getParameter(Vs30_Param.NAME).getValue();
				Double z1p0 = (Double)curSite.getParameter(DepthTo1pt0kmPerSecParam.NAME).getValue();
				if (z1p0 == null)
					z1p0 = Double.NaN;
				else
					z1p0 /= 1000d;
				
				List<Double> periods = Lists.newArrayList();
				for (double period : periodsForDefault)
					periods.add(period);
				periods.add(curPeriod);
				for (double period : periods) {
					for (Params param : Params.values()) {
						Double defaultVal = defaultParamValues.get(param);
						if (defaultVal == null)
							// get from empirical
							defaultVal = interp.calcEmpirical(param, period, vs30, z1p0);
						periodParams.set(period, param, defaultVal);
					}
				}
				periodParamsParam.getEditor().refreshParamEditor();
				curParamValues = periodParams.getInterpolated(periodParams.getParams(), curPeriod);
			} else {
				double refPeriod = getRefPeriodForInterpolation(imr.getIntensityMeasure().getName());
				if (interp == null || curPeriod <= 0)
					curParamValues = periodParams.getInterpolated(periodParams.getParams(), curPeriod);
				else
					curParamValues = interp.getInterpolated(periodParams, curPeriod, refPeriod,
							tSiteParam.getValue(), tSiteNParam.getValue(), curSite);
			}
		}
		return curParamValues;
	}
	
	private double getRefPeriodForInterpolation(String imt) {
		if (refIMTParam.getValue().equals(REF_IMT_OF_INTEREST)) {
			if (imt.equals(SA_Param.NAME))
				return Double.NaN;
			else if (imt.equals(PGA_Param.NAME))
				return 0d;
			else if (imt.equals(PGV_Param.NAME))
				return -1;
			throw new IllegalStateException("Unknown IMT: "+imt);
		} else {
			Preconditions.checkState(refIMTParam.getValue().equals(PGA_Param.NAME));
			return 0d;
		}
	}
	
	void printCurParams() {
		// only used for testing
		if (curParamValues == null)
			return;
		StringBuilder sb = new StringBuilder("Current Params:\n");
		sb.append("\tPeriod\t").append(PeriodDependentParamSet.j.join(periodParams.getParams())).append("\n");
		sb.append("\t").append(curPeriod).append("\t").append(
				PeriodDependentParamSet.j.join(Doubles.asList(curParamValues))).append("\n");
		System.out.println(sb.toString());
	}
	
	public void setSiteAmpParams(double period, Map<Params, Double> values) {
		for (Params param : values.keySet())
			periodParams.set(period, param, values.get(param));
		curParamValues = null;
	}
	
	public void setTsite(double tSite) {
		tSiteParam.setValue(tSite);
	}

	@Override
	public void setIMRSiteParams(ScalarIMR imr, Site site) {
		// just update the site location, don't call setSite as it will override our site parameters
		imr.setSiteLocation(site.getLocation());
		// store site with params for empirical model
		this.curSite = site;
		// params when interpolated can depend on Vs30, clear
		curParamValues = null;
	}
	
	public void setReferenceIMT(String refIMT) {
		Preconditions.checkArgument(refIMTParam.isAllowed(refIMT), "Value is not allowed: %s", refIMT);
		refIMTParam.setValue(refIMT);
	}
	
	@Override
	public double getModMean(ScalarIMR imr, double iml) {
		checkUpdateRefIMRSiteParams(imr);
		String origIMT = imt.getName();
		Preconditions.checkState(imr.getIntensityMeasure().getName().equals(origIMT));
		double u_lnX = imr.getMean();
		if (DD) {
			String imt = imr.getIntensityMeasure().getName();
			if (imt.equals(SA_Param.NAME))
				imt += " "+(float)+SA_Param.getPeriodInSA_Param(imr.getIntensityMeasure())+"s";
			if (DD) System.out.println("Orig IMT: "+imt);
		}
		if (DD) System.out.println("Orig Mean, "+origIMT+", u_X="+Math.exp(u_lnX));
		
		double x_ref = calcXref(imr, iml);
		Preconditions.checkState(imr.getIntensityMeasure().getName().equals(origIMT));
		
		double[] params = getCurParams(imr);
		double f1 = params[periodParams.getParamIndex(Params.F1)];
		double f2 = params[periodParams.getParamIndex(Params.F2)];
		double f3 = params[periodParams.getParamIndex(Params.F3)];
		
		if (DD) System.out.println("Calculating mean with f1="+f1+", f2="+f2+", f3="+f3);
		
		double ln_y = f1 + f2*Math.log((x_ref + f3)/f3);
		Preconditions.checkState(Doubles.isFinite(ln_y));
		if (DD) System.out.println("y="+Math.exp(ln_y));
		double yMax = Math.log(params[periodParams.getParamIndex(Params.Ymax)]);
		if (Doubles.isFinite(yMax)) {
			ln_y = Math.min(ln_y, yMax);
			if (DD) System.out.println("new y (after yMax="+yMax+"): "+Math.exp(ln_y));
		}
		
		Preconditions.checkState(Doubles.isFinite(ln_y));
		
		return u_lnX + ln_y;
	}

	private double calcXref(ScalarIMR imr, double iml) {
		double x_ref;
		String refIMT = refIMTParam.getValue();
		double sd_x_ref;
		if (!refIMT.equals(REF_IMT_OF_INTEREST)) {
			// now set to to ref IMT
			if (DD) System.out.println("Setting to reference IMT: "+refIMT);
			imr.setIntensityMeasure(refIMT);
			Preconditions.checkState(imr.getIntensityMeasure().getName().equals(refIMT));
			double x_ref_ln = imr.getMean();
			x_ref = Math.exp(x_ref_ln); // ref IMR, must be linear
			sd_x_ref = imr.getStdDev();
			if (DD) System.out.println("Ref IMT, "+refIMT+", x_ref="+x_ref+", sd_x_ref="+sd_x_ref);
			// set back to orig IMT
			imr.setIntensityMeasure(imt);
		} else {
			x_ref = Math.exp(imr.getMean());
			sd_x_ref = imr.getStdDev();
		}
		
		double rho = rhoParam.getValue();
		if (rho > 0) {
			imr.setIntensityMeasureLevel(iml);
			double epsilon = imr.getEpsilon(); // epsilon from the reference IMR for the IMT of interest
			x_ref = x_ref * Math.exp(rho*epsilon*sd_x_ref);
		}
		return x_ref;
	}
	
	@Override
	protected boolean isCurrentlyIML_Dependent() {
		return rhoParam.getValue() > 0;
	}

	@Override
	public double getModStdDev(ScalarIMR imr, double iml) {
		checkUpdateRefIMRSiteParams(imr);
		// get values for the IMT of interest
		StringParameter imrTypeParam = (StringParameter) imr.getParameter(StdDevTypeParam.NAME);
		String origIMRType = imrTypeParam.getValue();
		imrTypeParam.setValue(StdDevTypeParam.STD_DEV_TYPE_INTER);
		double origTau = imr.getStdDev();
		if (DD) System.out.println("Orig inter event, tau="+origTau);
		imrTypeParam.setValue(StdDevTypeParam.STD_DEV_TYPE_INTRA);
		double origPhi = imr.getStdDev();
		if (DD) System.out.println("Orig intra event, phi="+origPhi);
		imrTypeParam.setValue(origIMRType);
		
		double[] params = getCurParams(imr);
		double f2 = params[periodParams.getParamIndex(Params.F2)];
		double f3 = params[periodParams.getParamIndex(Params.F3)];
		double F = params[periodParams.getParamIndex(Params.F)];
		double phiS2S = params[periodParams.getParamIndex(Params.PHI_S2S)];
		double phiLnY = params[periodParams.getParamIndex(Params.PHI_lnY)];
		double phiSS = params[periodParams.getParamIndex(Params.PHI_SS)];
		
		boolean usePhiSS = usePhiSSParam.getValue();
		if (DD) System.out.println("Calculating std dev with f2="+f2+", f3="+f3+", F="+F
				+", phiS2S="+phiS2S+", phiLnY="+phiLnY+", phiSS="+phiSS+", usePhiSS="+usePhiSS);
		
		double x_ref = calcXref(imr, iml);
		Preconditions.checkState(imr.getIntensityMeasure().getName().equals(imt.getName()));
		
		double term1 = Math.pow((f2*x_ref)/(x_ref+f3) + 1, 2);
		double term2;
		if (usePhiSS)
			term2 = phiSS*phiSS;
		else
			term2 = (origPhi*origPhi - F*phiS2S*phiS2S);
		
		double phi_lnZ = Math.sqrt(term1 * term2 + phiLnY*phiLnY);
		
		if (DD) System.out.println("phi_lnZ="+phi_lnZ);
		
		double modStdDev = Math.sqrt(phi_lnZ*phi_lnZ + origTau*origTau);
		
		if (DD) System.out.println("modStdDev="+modStdDev);
		
		return modStdDev;
	}

	@Override
	public ParameterList getModParams() {
		return paramList;
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (event.getSource() == periodParamsParam || event.getSource() == tSiteParam || event.getSource() == tSiteNParam) {
			if (D) System.out.println("Period params change, clearing");
			curParamValues = null;
		} else if (referenceSiteParams.containsParameter(event.getParameterName())) {
			refSiteParamsStale = true;
		} else if (event.getParameter() == refIMTParam) {
			curParamValues = null;
		} else if (event.getSource() == plotInterpParam) {
			List<Double> periods = Lists.newArrayList();
			BSSA_ParamInterpolator interp = getInterpolator();
			for (IMT imt : interp.bssa.getSupportedIMTs())
				if (imt.isSA())
					periods.add(imt.getPeriod());
			Collections.sort(periods);
			Site site = new Site();
			ParameterList interpSiteParams = new ParameterList();
			interpSiteParams.addParameter(new Vs30_Param(760));
			interpSiteParams.getParameter(Vs30_Param.NAME).setValueAsDefault();
			interpSiteParams.addParameter(new DepthTo1pt0kmPerSecParam(null, true));
			ParameterListEditor editor = new ParameterListEditor(interpSiteParams);
			editor.setTitle("Empirical Model Site Parameters");
			JOptionPane.showMessageDialog(null, editor, "Enter Site Parameters for Interpolation", JOptionPane.QUESTION_MESSAGE);
			site.addParameterList(interpSiteParams);
			double refPeriod = getRefPeriodForInterpolation(SA_Param.NAME); // assume SA
			interp.plotInterpolation(periodParams, periods, refPeriod, tSiteParam.getValue(), tSiteNParam.getValue(), site);
		}
	}
	
	public static void main(String[] args) {
		new NonErgodicSiteResponseMod();
	}

}
