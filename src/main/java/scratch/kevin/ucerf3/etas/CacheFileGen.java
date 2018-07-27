package scratch.kevin.ucerf3.etas;

import java.io.File;
import java.io.IOException;

import org.dom4j.DocumentException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_PrimaryEventSampler;
import scratch.UCERF3.erf.ETAS.ETAS_Simulator;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.erf.ETAS.FaultSystemSolutionERF_ETAS;
import scratch.UCERF3.erf.ETAS.ETAS_Params.ETAS_ParameterList;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.LastEventData;
import scratch.UCERF3.utils.RELM_RegionUtils;

public class CacheFileGen {

	public static void main(String[] args) throws IOException, DocumentException {
		AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF = 2.55;
//		File solFile = new File(args[0]);
		FaultSystemSolution fss = FaultSystemIO.loadSol(
//				new File("src/scratch/UCERF3/data/scratch/InversionSolutions/"
//						+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip"));
				new File("src/scratch/UCERF3/data/scratch/InversionSolutions/"
						+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_2_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip"));
		LastEventData.populateSubSects(fss.getRupSet().getFaultSectionDataList(), LastEventData.load());
		FaultSystemSolutionERF_ETAS erf = ETAS_Launcher.buildERF(fss, false, 1d, 2014);
//		FaultSystemSolutionERF_ETAS erf = ETAS_Simulator.getU3_ETAS_ERF(fss);
//		FaultSystemSolutionERF_ETAS erf = ETAS_Simulator.getU3_ETAS_ERF();
		erf.updateForecast();
		
		File resultsDir = new File("/tmp");
		GriddedRegion reg = RELM_RegionUtils.getGriddedRegionInstance();
		
//		long randSeed = 1408453138855l;
		Long randSeed = null;
		
		boolean includeIndirectTriggering = true;
		boolean includeSpontEvents = true;
		
		double gridSeisDiscr = 0.1;
		
		ObsEqkRupList histQkList = new ObsEqkRupList();
		
		ETAS_Simulator.D = false;
		
		ETAS_EqkRupture mainshockRup = null;
//		ETAS_EqkRupture mainshockRup = ETAS_Simulator.buildScenarioRup(TestScenario.MOJAVE, erf);
//		ETAS_EqkRupture mainshockRup = new ETAS_EqkRupture();
//		long ot = Math.round((2014.0-1970.0)*ProbabilityModelsCalc.MILLISEC_PER_YEAR); // occurs at 2014
//		mainshockRup.setOriginTime(ot);
//		
//		// Mojave M 7.05 rupture
//		int fssScenarioRupID = 30473;
//		mainshockRup.setAveRake(fss.getRupSet().getAveRakeForRup(fssScenarioRupID));
//		mainshockRup.setMag(fss.getRupSet().getMagForRup(fssScenarioRupID));
//		mainshockRup.setRuptureSurface(fss.getRupSet().getSurfaceForRupupture(fssScenarioRupID, 1d, false));
//		mainshockRup.setID(0);
//		erf.setFltSystemSourceOccurranceTimeForFSSIndex(fssScenarioRupID, ot);
//		
//		erf.updateForecast();
		
		ETAS_ParameterList params = new ETAS_ParameterList();
		params.setApplyGridSeisCorr(true);
		
		ETAS_Simulator.testETAS_Simulation(resultsDir, erf, reg, mainshockRup, histQkList, includeSpontEvents,
				includeIndirectTriggering, gridSeisDiscr, null, randSeed, params);
		
		params.setU3ETAS_ProbModel(U3ETAS_ProbabilityModelOptions.POISSON);
		
		// Overide to Poisson if needed
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.updateForecast();

		// first make array of rates for each source
		double sourceRates[] = new double[erf.getNumSources()];
		double duration = erf.getTimeSpan().getDuration();
		for(int s=0;s<erf.getNumSources();s++) {
			sourceRates[s] = erf.getSource(s).computeTotalEquivMeanAnnualRate(duration);
			//					if(sourceRates[s] == 0)
			//						System.out.println("HERE "+erf.getSource(s).getName());
		}

		ETAS_PrimaryEventSampler etas_PrimEventSampler = new ETAS_PrimaryEventSampler(reg, erf, sourceRates, 
				gridSeisDiscr,null, params, new ETAS_Utils(),null,null,null);
		
		etas_PrimEventSampler.getExpectedAfterShockRateInGridCellsFromSupraRates(10d, new ETAS_ParameterList(), 2.0, null);
	}

}
