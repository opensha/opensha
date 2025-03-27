package org.opensha.sha.imr.attenRelImpl.nshmp.util;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.WarningParameter;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.util.NEHRP_TestCity;

import gov.usgs.earthquake.nshmp.gmm.Gmm;
import gov.usgs.earthquake.nshmp.gmm.GmmInput;
import scratch.UCERF3.erf.FaultSystemSolutionERF;

class CompareWithPrevImpl {

	public static void main(String[] args) throws IOException {
		// this is the file at https://opensha.usc.edu/ftp/kmilner/markdown/batch_inversions/nshm23-draft-latest/results_NSHM23_v2_CoulombRupSet_branch_averaged_gridded.zip
		File solFile = new File("/data/kevin/nshm23/batch_inversions/"
				+ "2023_01_17-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR/"
				+ "results_NSHM23_v2_CoulombRupSet_branch_averaged_gridded.zip");
		FaultSystemSolution fss = FaultSystemSolution.load(solFile);

		fss.removeAvailableModuleInstances(RupMFDsModule.class);
		
		boolean fullParams = false;
		boolean paramDebug = true;
		
//		ScalarIMR gmpePrevImpl = AttenRelRef.ASK_2014.get();
//		NSHMP_GMM_WrapperFullParam gmpeNewWrapper = new NSHMP_GMM_WrapperFullParam(Gmm.ASK_14_BASE, fullParams);
//		ScalarIMR gmpePrevImpl = AttenRelRef.BSSA_2014.get();
//		NSHMP_GMM_WrapperFullParam gmpeNewWrapper = new NSHMP_GMM_WrapperFullParam(Gmm.BSSA_14_BASE, fullParams);
//		ScalarIMR gmpePrevImpl = AttenRelRef.CB_2014.get();
//		NSHMP_GMM_WrapperFullParam gmpeNewWrapper = new NSHMP_GMM_WrapperFullParam(Gmm.CB_14_BASE, fullParams);
//		ScalarIMR gmpePrevImpl = AttenRelRef.CY_2014.get();
//		NSHMP_GMM_WrapperFullParam gmpeNewWrapper = new NSHMP_GMM_WrapperFullParam(Gmm.CY_14_BASE, fullParams);
//		ScalarIMR gmpePrevImpl = AttenRelRef.BA_2008.get();
//		NSHMP_GMM_WrapperFullParam gmpeNewWrapper = new NSHMP_GMM_WrapperFullParam(Gmm.BA_08_BASE, fullParams);
//		ScalarIMR gmpePrevImpl = AttenRelRef.CY_2008.get();
//		NSHMP_GMM_WrapperFullParam gmpeNewWrapper = new NSHMP_GMM_WrapperFullParam(Gmm.CY_08_BASE, fullParams);
		ScalarIMR gmpePrevImpl = AttenRelRef.CB_2008.get();
		NSHMP_GMM_Wrapper gmpeNewWrapper = new NSHMP_GMM_Wrapper.Single(Gmm.CB_08_BASE, fullParams);
		
		double period = 0.2d;

		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(fss);
		erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.INCLUDE);
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.getTimeSpan().setDuration(1d);
		erf.updateForecast();

		gmpePrevImpl.setParamDefaults();
		gmpeNewWrapper.setParamDefaults();
		
		if (period > 0d) {
			gmpePrevImpl.setIntensityMeasure(SA_Param.NAME);
			gmpeNewWrapper.setIntensityMeasure(SA_Param.NAME);
			SA_Param.setPeriodInSA_Param(gmpePrevImpl.getIntensityMeasure(), period);
			SA_Param.setPeriodInSA_Param(gmpeNewWrapper.getIntensityMeasure(), period);
		} else if (period == 0d) {
			gmpePrevImpl.setIntensityMeasure(PGA_Param.NAME);
			gmpeNewWrapper.setIntensityMeasure(PGA_Param.NAME);
		} else {
			throw new IllegalStateException();
		}

		DiscretizedFunc xVals = new IMT_Info().getDefaultHazardCurve(gmpePrevImpl.getIntensityMeasure());
		DiscretizedFunc logXVals = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : xVals)
			logXVals.set(Math.log(pt.getX()), 0d);
		
		System.out.println("Current IMT: "+gmpeNewWrapper.getIntensityMeasure().getName());
		System.out.println("Translated IMT: "+gmpeNewWrapper.getCurrentIMT());
		System.out.println("Fields: "+gmpeNewWrapper.getFieldsUsed());
		
		System.out.println("GMM Params:");
		printGMMParams(gmpeNewWrapper);
		HazardCurveCalculator calc = new HazardCurveCalculator();

		NEHRP_TestCity[] cities = {
				NEHRP_TestCity.LOS_ANGELES
		};
		
		Random rand = new Random();

		for (NEHRP_TestCity city : cities) {
			System.out.println("City: "+city);
			ScalarIMR[] gmms = {gmpePrevImpl, gmpeNewWrapper};
			DiscretizedFunc[] curves = new DiscretizedFunc[2];
			
			ProbEqkSource randSource = null;
			ProbEqkRupture randRup = null;
			if (paramDebug) {
				randSource = erf.getSource(rand.nextInt(erf.getNumSources()));
				randRup = randSource.getRupture(rand.nextInt(randSource.getNumRuptures()));
			}
			
			for (int i=0; i<gmms.length; i++) {
				ScalarIMR gmm = gmms[i];
				Site site = new Site(city.location());
//				System.out.println("Calculating for "+gmm.getName()+", "+city);
				site.addParameterList(gmpePrevImpl.getSiteParams());

				calc.getHazardCurve(logXVals, site, gmm, erf);

				DiscretizedFunc curve = new ArbitrarilyDiscretizedFunc();
				for (int j=0; j<xVals.size(); j++)
					curve.set(xVals.getX(j), logXVals.getY(j));
				
				curves[i] = curve;
				
				if (paramDebug) {
					System.out.println("Parameter debug for "+gmm.getName()+", "+city);
					System.out.println("\tRandom source: "+randSource.getName());
					System.out.println("\tRandom rupture is an M"+(float)randRup.getMag());
					
					gmm.setEqkRupture(randRup);
					printGMMParams(gmm);
					if (gmm instanceof NSHMP_GMM_Wrapper) {
						GmmInput input = ((NSHMP_GMM_Wrapper)gmm).getCurrentGmmInput();
						System.out.println("GmmInput:\t"+input);
					}
					System.out.println("\tMean: "+(float)gmm.getMean());
					System.out.println("\tStd. Dev.: "+(float)gmm.getStdDev());
				}
			}

			// compare
			System.out.println("IML\tPrevous\tWrapped\tDifference\t% Diff");
			for (int i=0; i<xVals.size(); i++) {
				double iml = xVals.getX(i);
				double prevVal = curves[0].getY(i);
				double newVal = curves[1].getY(i);
				double diff = newVal - prevVal;
				double pDiff = 100d*diff/prevVal;
				
				System.out.println((float)iml+"\t"+(float)prevVal+"\t"+(float)newVal+"\t"+(float)diff+"\t"+(float)pDiff+" %");
			}
		}
	}
	
	private static void printGMMParams(ScalarIMR gmm) {
		System.out.println("\tEqk Rup Params:");
		for (Parameter<?> param : gmm.getEqkRuptureParams())
			System.out.println("\t\t"+paramNameValueRange(param));
		System.out.println("\tProp Effect Params:");
		for (Parameter<?> param : gmm.getPropagationEffectParams())
			System.out.println("\t\t"+paramNameValueRange(param));
		System.out.println("\tSite Params:");
		for (Parameter<?> param : gmm.getSiteParams())
			System.out.println("\t\t"+paramNameValueRange(param));
		System.out.println("\tOther Params:");
		for (Parameter<?> param : gmm.getOtherParams())
			System.out.println("\t\t"+paramNameValueRange(param));
	}
	
	private static String paramNameValueRange(Parameter<?> param) {
		String ret = param.getName()+":\t"+param.getValue();
		if (param instanceof WarningParameter<?>) {
			WarningParameter<?> warn = (WarningParameter<?>)param;
			ret += "\t["+warn.getWarningMin()+", "+warn.getWarningMax()+"]";
		}
		return ret;
	}

}
