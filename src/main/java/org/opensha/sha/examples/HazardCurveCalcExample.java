package org.opensha.sha.examples;

import java.awt.geom.Point2D;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_Wrappers.ASK_2014_Wrapper;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;

/**
 * Example main class showing how to set up the various required objects, change parameter values,
 * and compute a hazard curve.
 * 
 * @author kevin
 *
 */
public class HazardCurveCalcExample {

	public static void main(String[] args) {
		// this is the location where we are going to calculate a hazard curve
		Location loc = new Location(34, -118);
		
		// this is the 'site': this contains the location, but also any site parameters (e.g., Vs30)
		// that are required in order to calculate ground motions
		Site site = new Site(loc);
		
		// choose an earthquake rupture forecast (ERF)
		// this example uses the branch averaged UCERF2 model
		ERF erf = new MeanUCERF2();
		
		// lets see what adjustable parameters this ERF has:
		System.out.println(erf.getName()+" adjustable parameters and current values:");
		for (Parameter<?> param : erf.getAdjustableParameterList()) {
			String name = param.getName();
			Object value = param.getValue();
			System.out.println("\t"+name+": "+value);
		}
		
		// lets change a parameter, in this case we'll change the probability model to Poisson.
		// parameter's can be fetched by name, which are often stored as static fields in the ERF's
		// class, or in individual parameter classes
		erf.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
		
		// ERFs also have time spans, which include the forecast duration. let's set that to 30 years
		TimeSpan timeSpan = erf.getTimeSpan();
		timeSpan.setDuration(30d); // years
		
		// now we need to build the forecast via the updateForecast() method. this must be called after you
		// instantiate the model, and also whenever a parameter or timespan is modified
		System.out.println("Updating forecast...");
		erf.updateForecast();
		System.out.println("DONE updating forecast");
		
		// now choose a ground motion prediction equation (GMPE)/attenuation relationship
		// we'll use the ASK 2014 model here
		ScalarIMR gmpe = new ASK_2014_Wrapper();
		
		// first step with a GMPE is to set all parameters to default values
		gmpe.setParamDefaults();
		
		// something that can be a little tricky here is that the GMPE has 'site' parmeters, but it gets their values
		// from the site object. so first, we need to set up those parameters in the site
		System.out.println(gmpe.getShortName()+" site paramters and default values:");
		for (Parameter<?> param : gmpe.getSiteParams()) {
			String name = param.getName();
			Object value = param.getValue();
			System.out.println("\t"+name+": "+value);
			// add that parameter to the site. if you are only ever going to have one site in memory you can
			// remove the clone() call, but it's safer to give the site it's own copy so that you can change values
			// for each site without affecting anything else
			site.addParameter((Parameter<?>)param.clone());
		}
		
		// lets set Vs30 to 500 m/s
		// the Double.class here is telling the GMPE that the generic type of the Vs30 parameters
		// is Double. You can leave that argument out and it will work file but compiling will show a warning
		site.getParameter(Double.class, Vs30_Param.NAME).setValue(500d);
		
		// lets also set our intensity measure type (IMT). First, we'll list them
		System.out.println(gmpe.getShortName()+" supported intensity measures:");
		for (Parameter<?> param : gmpe.getSupportedIntensityMeasures()) {
			System.out.println("\t"+param.getName());
		}
		
		// set it to PGA
		gmpe.setIntensityMeasure(PGA_Param.NAME);
		
		// we're just about ready to compute a hazard curve. curves are computed at a set of x-values.
		// first lets choose those x-values
		
		// this will get the default x-values for the current IMT. these are in linear space
		DiscretizedFunc linearXVals = new IMT_Info().getDefaultHazardCurve(gmpe.getIntensityMeasure());
		
		// hazard curve calculations are done in natural log space though, so we need to convert these
		// x-values to log, and create the object which will store our hazard curve
		DiscretizedFunc hazardCurve = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : linearXVals)
			hazardCurve.set(Math.log(pt.getX()), 0d); // y-value doesn't matter here yet
		
		// create a new hazard curve calculator instance.
		HazardCurveCalculator curveCalc = new HazardCurveCalculator();
		
		System.out.println("Calculating hazard curve...");
		// this will populate the hazrd curve object, filling in the y-values with exceedance probabilities
		curveCalc.getHazardCurve(hazardCurve, site, gmpe, erf);
		System.out.println("DONE calculating hazard curve");
		
		System.out.println("Hazard curve (with log x-values):\n"+hazardCurve);
		
		// now lets calculate spectral acceleration
		gmpe.setIntensityMeasure(SA_Param.NAME);
		// need to set the period as well, which is stored within the IMT as a sub-parameter.
		// this is the simplest way:
		SA_Param.setPeriodInSA_Param(gmpe.getIntensityMeasure(), 1d); // 1-second
		
		// can re-use the same x-values if we want:
		curveCalc.getHazardCurve(hazardCurve, site, gmpe, erf);
		
		System.out.println("SA hazard curve:\n"+hazardCurve);
	}

}
