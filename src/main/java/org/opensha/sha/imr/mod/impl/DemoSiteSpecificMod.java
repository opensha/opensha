package org.opensha.sha.imr.mod.impl;

import org.opensha.commons.data.Site;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.mod.AbstractAttenRelMod;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

/**
 * Simple modifier demo which allows the user to scale the mean/std dev of an underlying IMR
 * 
 * @author kevin
 *
 */
public class DemoSiteSpecificMod extends AbstractAttenRelMod {
	
	private static final boolean D = true;
	
	public static final String NAME = "Demo Site Specific";
	public static final String SHORT_NAME = "SimpleScalar";
	
	private ParameterList modParams = null;

	private double vs30;
	private String vs30Type;
	private Double z2500; // in km, can be null
	private Double z1000; // in km, can be null
	
	public DemoSiteSpecificMod() {
		// can add adjustable parameters here if needed
//		modParams = new ParameterList();
	}
	
	@Override
	public void setIMRParams(ScalarIMR imr) {
		// set default site parameters
		// TODO make sure these default parameters are what you want!
		
		// surround each with a try catch as certain IMRs may not have the given site parameter
		try {
			Parameter<Double> param = imr.getParameter(Vs30_Param.NAME);
			param.setValue(760d);
		} catch (ParameterException e) {
			// do nothing - IMR does not have this parameter
			if (D) System.out.println("IMR doesn't support Vs30");
		}
		try {
			Parameter<String> param = imr.getParameter(Vs30_TypeParam.NAME);
			param.setValue(Vs30_TypeParam.VS30_TYPE_INFERRED);
		} catch (ParameterException e) {
			// do nothing - IMR does not have this parameter
			if (D) System.out.println("IMR doesn't support Vs30 Type");
		}
		try {
			Parameter<Double> param = imr.getParameter(DepthTo2pt5kmPerSecParam.NAME);
			param.setValue(null); // disable Z2500
		} catch (ParameterException e) {
			// do nothing - IMR does not have this parameter
			if (D) System.out.println("IMR doesn't support Z2.5");
		}
		try {
			Parameter<Double> param = imr.getParameter(DepthTo1pt0kmPerSecParam.NAME);
			param.setValue(null);
		} catch (ParameterException e) {
			// do nothing - IMR does not have this parameter
			if (D) System.out.println("IMR doesn't support Z1.0");
		}
		System.out.println("Set site params to default");
	}

	@Override
	public void setIMRSiteParams(ScalarIMR imr, Site site) {
		// just update the site location, don't call setSite as it will override our site parameters
		imr.setSiteLocation(site.getLocation());
		
		// now update our site parameter primitives with values from this site
		vs30 = site.getParameter(Double.class, Vs30_Param.NAME).getValue();
		vs30Type = site.getParameter(String.class, Vs30_TypeParam.NAME).getValue();
		z2500 = site.getParameter(Double.class, DepthTo2pt5kmPerSecParam.NAME).getValue();
		z1000 = site.getParameter(Double.class, DepthTo1pt0kmPerSecParam.NAME).getValue();
		if (z1000 != null)
			// convert to km
			z1000 /= 1000d;
		
		if (D) System.out.println("Site params intercepted: vs30="+vs30
					+", vs30_Type="+vs30Type+", z2.5="+z2500+" z1.0="+z1000);
	}

	@Override
	public double getModMean(ScalarIMR imr) {
		double origMean = imr.getMean();
		// TODO do any required scaling using site type primitives
		// remember that this is in natural log units
		// if you need to know which IMT we have, then call imr.getIntensityMeasure()
		double scaleValue = origMean;
		if (vs30 < 500)
			scaleValue = Math.log(Math.exp(origMean)*2d);
		return scaleValue;
	}

	@Override
	public double getModStdDev(ScalarIMR imr) {
		double origStdDev = imr.getStdDev();
		// TODO do any required scaling using site type primitives
		double scaleValue = origStdDev * 1d;
		return scaleValue;
	}

	@Override
	public ParameterList getModParams() {
		return modParams;
	}

	@Override
	public String getShortName() {
		return SHORT_NAME;
	}

	@Override
	public String getName() {
		return NAME;
	}

}
