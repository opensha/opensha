package org.opensha.sha.imr.attenRelImpl.nshmp.util;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.MultiIMR_Averaged_AttenRel;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

import com.google.common.base.Stopwatch;

import gov.usgs.earthquake.nshmp.gmm.Gmm;
import scratch.UCERF3.erf.FaultSystemSolutionERF;

class Benchmark {

	public static void main(String[] args) throws IOException {
		File solFile = new File("/data/kevin/nshm23/batch_inversions/"
				+ "2023_09_01-nshm23_branches-mod_pitas_ddw-NSHM23_v2-CoulombRupSet-DsrUni-TotNuclRate-NoRed-ThreshAvgIterRelGR/"
				+ "results_NSHM23_v2_CoulombRupSet_branch_averaged_gridded.zip");
		FaultSystemSolution fss = FaultSystemSolution.load(solFile);

		fss.removeAvailableModuleInstances(RupMFDsModule.class);
		
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(fss);
		erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.INCLUDE);
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.getTimeSpan().setDuration(1d);
		erf.updateForecast();
		
		Location baseLoc = new Location(34, -118);
		int benchmarkSites = 100;
		int numBurnerSites = 20;
		int totNumSites = benchmarkSites+numBurnerSites;
		double period = 1d;
		
		File outputFile = new File("/tmp/gmm_benchmark.csv");
		
		List<ScalarIMR> gmms = new ArrayList<>();
		List<String> gmmNames = new ArrayList<>();
		
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.ASK_14_BASE, true));
		gmmNames.add("NSHMP ASK 2014 Base, Parameterized");
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.ASK_14_BASE, false));
		gmmNames.add("NSHMP ASK 2014 Base");
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.ASK_14, false));
		gmmNames.add("NSHMP ASK 2014 USGS");
		gmms.add(AttenRelRef.ASK_2014.get());
		gmmNames.add("Original ASK 2014");
		
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.BSSA_14_BASE, true));
		gmmNames.add("NSHMP BSSA 2014 Base, Parameterized");
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.BSSA_14_BASE, false));
		gmmNames.add("NSHMP BSSA 2014 Base");
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.BSSA_14, false));
		gmmNames.add("NSHMP BSSA 2014 USGS");
		gmms.add(AttenRelRef.BSSA_2014.get());
		gmmNames.add("Original BSSA 2014");
		
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.CB_14_BASE, true));
		gmmNames.add("NSHMP CB 2014 Base, Parameterized");
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.CB_14_BASE, false));
		gmmNames.add("NSHMP CB 2014 Base");
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.CB_14, false));
		gmmNames.add("NSHMP CB 2014 USGS");
		gmms.add(AttenRelRef.CB_2014.get());
		gmmNames.add("Original CB 2014");
		
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.CY_14_BASE, true));
		gmmNames.add("NSHMP CY 2014 Base, Parameterized");
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.CY_14_BASE, false));
		gmmNames.add("NSHMP CY 2014 Base");
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.CY_14, false));
		gmmNames.add("NSHMP CY 2014 USGS");
		gmms.add(AttenRelRef.CY_2014.get());
		gmmNames.add("Original CY 2014");
		
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.COMBINED_ACTIVE_CRUST_2014_42, true));
		gmmNames.add("NSHMP 2014 Combined, Parameterized");
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.COMBINED_ACTIVE_CRUST_2014_42, false));
		gmmNames.add("NSHMP 2014 Combined");
		gmms.add(AttenRelRef.NGAWest_2014_AVG_NOIDRISS.get());
		gmmNames.add("Original NGA-West2 (No Idriss)");
		
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.COMBINED_ACTIVE_CRUST_2023, true));
		gmmNames.add("NSHMP 2023 Combined, Parameterized");
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.COMBINED_ACTIVE_CRUST_2023, false));
		gmmNames.add("NSHMP 2023 Combined");
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.COMBINED_ACTIVE_CRUST_2023_LOS_ANGELES, false));
		gmmNames.add("NSHMP 2023 Combined, LA");
		gmms.add(new NSHMP_GMM_Wrapper.Single(Gmm.COMBINED_ACTIVE_CRUST_2023_SAN_FRANCISCO, false));
		gmmNames.add("NSHMP 2023 Combined, SF");
		
		Random rand = new Random(totNumSites*fss.getRupSet().getNumRuptures());
		
		List<Double> vs30s = new ArrayList<>(totNumSites);
		List<Double> z1s = new ArrayList<>(totNumSites);
		List<Double> z25s = new ArrayList<>(totNumSites);
		List<Location> siteLocs = new ArrayList<>(totNumSites);
		double shiftLocTol = 0.01;
		for (int s=0; s<totNumSites; s++) {
			siteLocs.add(new Location(baseLoc.getLatitude()+rand.nextDouble(), baseLoc.getLongitude()+rand.nextDouble()));
			vs30s.add(200 + 600*rand.nextDouble());
			z1s.add(50 + 1000*rand.nextDouble());
			z25s.add(rand.nextDouble()*5d);
		}
		
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("Model", "Seconds Per Site", "Num Sub-Models", "Seconds Per Site-Model");
		
		for (int g=0; g<gmms.size(); g++) {
			ScalarIMR gmm = gmms.get(g);
			gmm.setParamDefaults();
			String name = gmmNames.get(g);
			
			if (period > 0d) {
				gmm.setIntensityMeasure(SA_Param.NAME);
				SA_Param.setPeriodInSA_Param(gmm.getIntensityMeasure(), period);
			} else if (period == 0d) {
				gmm.setIntensityMeasure(PGA_Param.NAME);
			} else {
				throw new IllegalStateException();
			}
			DiscretizedFunc xVals = new IMT_Info().getDefaultHazardCurve(gmm.getIntensityMeasure().getName());
			DiscretizedFunc logXVals = new ArbitrarilyDiscretizedFunc();
			for (Point2D pt : xVals)
				logXVals.set(Math.log(pt.getX()), 1d);
			
			List<Site> burnerSites = new ArrayList<>();
			List<Site> sites = new ArrayList<>();
			for (int s=0; s<totNumSites; s++) {
				Location loc = siteLocs.get(s);
				// shift a tiny amount to avoid cache hits
				loc = new Location(loc.getLatitude()+rand.nextDouble()*shiftLocTol,
						loc.getLongitude()+rand.nextDouble()*shiftLocTol);
				
				Site site = new Site(loc);
				
				Vs30_Param vs30 = new Vs30_Param(vs30s.get(s));
				vs30.setValue(vs30s.get(s));
				site.addParameter(vs30);
				
				DepthTo1pt0kmPerSecParam z10 = new DepthTo1pt0kmPerSecParam();
				z10.setValue(z1s.get(s));
				site.addParameter(z10);
				
				DepthTo2pt5kmPerSecParam z25 = new DepthTo2pt5kmPerSecParam();
				z25.setValue(z25s.get(s));
				site.addParameter(z25);
				
				Vs30_TypeParam type = new Vs30_TypeParam();
				type.setValue(Vs30_TypeParam.VS30_TYPE_INFERRED);
				site.addParameter(type);
				
				if (s < numBurnerSites)
					burnerSites.add(site);
				else
					sites.add(site);
			}
			
			HazardCurveCalculator calc = new HazardCurveCalculator();
			
//			System.out.println("Calculating "+burnerSites+" burner (warm up) curves for "+name);
			for (Site site : burnerSites)
				calc.getHazardCurve(logXVals, site, gmm, erf);
			System.out.println("Calculating "+sites.size()+" curves for "+name);
			Stopwatch watch = Stopwatch.createStarted();
			for (Site site : sites)
				calc.getHazardCurve(logXVals, site, gmm, erf);
			watch.stop();
			double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
			double secsEach = secs/sites.size();
			System.out.println("\tTook "+(float)secs+" s;\t"+(float)secsEach+" s per site");
			
			int subModels;
			if (gmm instanceof NSHMP_GMM_Wrapper) {
				subModels = ((NSHMP_GMM_Wrapper) gmm).getGroundMotionTree().size();
			} else if (gmm instanceof MultiIMR_Averaged_AttenRel) {
				subModels = ((MultiIMR_Averaged_AttenRel)gmm).getIMRs().size();
			} else {
				subModels = 1;
			}
			double secsPerSubModel = secsEach/(double)subModels;
			
			csv.addLine(name, (float)secsEach+"", subModels+"", (float)secsPerSubModel+"");
		}
		
		csv.writeToFile(outputFile);
	}

}
