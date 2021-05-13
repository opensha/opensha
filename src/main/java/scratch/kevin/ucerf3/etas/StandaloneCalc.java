package scratch.kevin.ucerf3.etas;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.dom4j.DocumentException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.MaximumMagnitudeParam;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_Simulator;
import scratch.UCERF3.erf.ETAS.FaultSystemSolutionERF_ETAS;
import scratch.UCERF3.erf.ETAS.ETAS_Params.ETAS_ParameterList;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.ETAS.NoFaultsModel.ETAS_Simulator_NoFaults;
import scratch.UCERF3.erf.ETAS.NoFaultsModel.UCERF3_GriddedSeisOnlyERF_ETAS;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.LastEventData;
import scratch.UCERF3.utils.MatrixIO;
import scratch.UCERF3.utils.RELM_RegionUtils;

public class StandaloneCalc {

	@SuppressWarnings("unused")
	public static void main(String[] args) throws IOException, DocumentException {
		/*
		 * INPUTS
		 */
		double duration = 100;
		boolean includeSpontEvents = true;
		int startYear = 2017;
		long ot = Math.round((startYear-1970.0)*ProbabilityModelsCalc.MILLISEC_PER_YEAR);
//		int startYear = 2017;
//		long ot = -1631410050000l;
		U3ETAS_ProbabilityModelOptions probModel = U3ETAS_ProbabilityModelOptions.FULL_TD;
		boolean applySubSeisForSupraNucl = true;
		double totRateScaleFactor = 1.14;
		boolean gridSeisCorr = false;
		boolean griddedOnly = false;
		boolean imposeGR = false;
		
		long randSeed = 1034492315904040044l;
//		long randSeed = new Random().nextLong();
		
		boolean includeIndirectTriggering = true;
		double gridSeisDiscr = 0.1;
		
		Location triggerHypo = null; // trigger hypo, or null for FSS scenario or spontaneous only
		double triggerMag = 0d; // mag for point source, or to override mag of FSS rupture
		int fssScenarioRupID = -1; // FSS rup index (note, if triggerMag >0 that is used for the mag)
		
//		File catFile = null;
//		File surfsFile = null;
		File catFile = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/ofr2013-1165_EarthquakeCat.txt");
		File surfsFile = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/finite_fault_mappings.xml");
		
		File cacheDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/cache_fm3p1_ba");
		boolean generateCaches = false;
//		File cacheDir = null;
//		boolean generateCaches = true; // will go in src/scratch/UCERF3/data/scratch/InversionSolutions, make sure previous files are cleared out
		
		boolean debug = false;
		
		File outputDir = new File("/tmp/etas_test");
		File solFile = new File("/home/kevin/workspace/opensha-ucerf3/src/scratch/UCERF3/data/scratch/InversionSolutions/"
				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip");
//		File solFile = new File("/home/kevin/workspace/opensha-ucerf3/src/scratch/UCERF3/data/scratch/InversionSolutions/"
//				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_2_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip");
//		File solFile = new File(outputDir, "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip");
		
		/*
		 * prepare simulation
		 */
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF = 2.55;
		ETAS_Simulator.D = debug;
		if (griddedOnly)
			ETAS_Simulator_NoFaults.D = debug;
		
		Map<Integer, List<LastEventData>> lastEventData = LastEventData.load();
		
		FaultSystemSolution sol = FaultSystemIO.loadSol(solFile);
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		double[] gridSeisCorrections = null;
		
		if (gridSeisCorr) {
			File cacheFile = new File(cacheDir, "griddedSeisCorrectionCache");
			System.out.println("Loading gridded seismicity correction cache file from "+cacheFile.getAbsolutePath());
			gridSeisCorrections = MatrixIO.doubleArrayFromFile(cacheFile);
			
			ETAS_Simulator.correctGriddedSeismicityRatesInERF(sol, false, gridSeisCorrections);
		}

		ETAS_ParameterList params = new ETAS_ParameterList();
		params.setImposeGR(imposeGR);
		params.setU3ETAS_ProbModel(probModel);
		// already applied if applicable, setting here for metadata
		params.setApplyGridSeisCorr(gridSeisCorrections != null);
		params.setApplySubSeisForSupraNucl(applySubSeisForSupraNucl);
		params.setTotalRateScaleFactor(totRateScaleFactor);
		
		List<ETAS_EqkRupture> histQkList = Lists.newArrayList();
		if (catFile != null) {
			histQkList.addAll(ETAS_Launcher.loadHistoricalCatalog(catFile, surfsFile, sol, ot, null, params.getStatewideCompletenessModel()));
		}
		
		// purge any last event data after OT
		LastEventData.filterDataAfterTime(lastEventData, ot);
		
		String simulationName;
		ETAS_EqkRupture triggerRup = null;
		
		if (fssScenarioRupID >= 0) {
			// FSS rupture
			ETAS_EqkRupture mainshockRup = new ETAS_EqkRupture();
			mainshockRup.setOriginTime(ot);

//			ProbEqkRupture rupFromERF = erf.getSource(srcID).getRupture(0);
//			mainshockRup.setAveRake(rupFromERF.getAveRake());
//			mainshockRup.setMag(rupFromERF.getMag());
//			mainshockRup.setRuptureSurface(rupFromERF.getRuptureSurface());
			mainshockRup.setAveRake(rupSet.getAveRakeForRup(fssScenarioRupID));
			mainshockRup.setMag(rupSet.getMagForRup(fssScenarioRupID));
			mainshockRup.setRuptureSurface(rupSet.getSurfaceForRupture(fssScenarioRupID, 1d));
			mainshockRup.setID(0);
			mainshockRup.setFSSIndex(fssScenarioRupID);
//			debug("test Mainshock: "+erf.getSource(srcID).getName());
			
			if (triggerMag > 0)
				mainshockRup.setMag(triggerMag);
			
			if (triggerHypo != null)
				mainshockRup.setHypocenterLocation(triggerHypo);
			
			// date of last event will be updated for this rupture in the calculateBatch method below
			
			simulationName = "FSS simulation. M="+mainshockRup.getMag()+", fss ID="+fssScenarioRupID;
			
			triggerRup = mainshockRup;
		} else if (triggerHypo != null) {
			ETAS_EqkRupture mainshockRup = new ETAS_EqkRupture();
			mainshockRup.setOriginTime(ot);	
			
//			if (cmd.hasOption("trigger-rake"))
//				mainshockRup.setAveRake(Double.parseDouble(cmd.getOptionValue("trigger-rake")));
//			else
				mainshockRup.setAveRake(0.0);
			mainshockRup.setMag(triggerMag);
			mainshockRup.setPointSurface(triggerHypo);
			mainshockRup.setID(0);
			mainshockRup.setHypocenterLocation(triggerHypo);
			
			simulationName = "Pt Source. M="+triggerMag+", "+triggerHypo;
			
			triggerRup = mainshockRup;
		} else {
			// only spontaneous
			simulationName = "Spontaneous events";
		}
		
		GriddedRegion griddedRegion = RELM_RegionUtils.getGriddedRegionInstance();
		
		/*
		 * Caches
		 */
		System.out.println("Loading caches");
		List<float[]> fractionSrcAtPointList;
		List<int[]> srcAtPointList;
		int[] isCubeInsideFaultPolygon;
		if (generateCaches) {
			fractionSrcAtPointList = null;
			srcAtPointList = null;
			isCubeInsideFaultPolygon = null;
		} else {
			File fractionSrcAtPointListFile = new File(cacheDir, "sectDistForCubeCache");
			File srcAtPointListFile = new File(cacheDir, "sectInCubeCache");
			File isCubeInsideFaultPolygonFile = new File(cacheDir, "cubeInsidePolyCache");
			Preconditions.checkState(fractionSrcAtPointListFile.exists(),
					"cache file not found: "+fractionSrcAtPointListFile.getAbsolutePath());
			Preconditions.checkState(srcAtPointListFile.exists(),
					"cache file not found: "+srcAtPointListFile.getAbsolutePath());
			Preconditions.checkState(isCubeInsideFaultPolygonFile.exists(),
					"cache file not found: "+isCubeInsideFaultPolygonFile.getAbsolutePath());
			fractionSrcAtPointList = MatrixIO.floatArraysListFromFile(fractionSrcAtPointListFile);
			srcAtPointList = MatrixIO.intArraysListFromFile(srcAtPointListFile);
			isCubeInsideFaultPolygon = MatrixIO.intArrayFromFile(isCubeInsideFaultPolygonFile);
		}
		
		/*
		 * Do the simulation
		 */
		LastEventData.populateSubSects(sol.getRupSet().getFaultSectionDataList(), lastEventData);
		AbstractERF erf;
		if (griddedOnly) {
			erf = new UCERF3_GriddedSeisOnlyERF_ETAS();
			
//			// set parameters
			erf.setParameter(BackgroundRupParam.NAME, BackgroundRupType.POINT);
			erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, false);
			erf.setParameter(MaximumMagnitudeParam.NAME, 8.3);
			erf.setParameter("Total Regional Rate",TotalMag5Rate.RATE_7p9);
			erf.setParameter("Spatial Seis PDF",SpatialSeisPDF.UCERF3);

			erf.getTimeSpan().setStartTimeInMillis(ot);
			erf.getTimeSpan().setDuration(duration);
			
			erf.updateForecast();
		} else {
			erf = ETAS_Launcher.buildERF_millis(sol, false, duration, ot);
			
			if (fssScenarioRupID >= 0) {
				// This sets the rupture as having occurred in the ERF (to apply elastic rebound)
				((FaultSystemSolutionERF_ETAS)erf).setFltSystemSourceOccurranceTimeForFSSIndex(fssScenarioRupID, ot);
			}

			erf.updateForecast();
		}
		
		System.out.println("Running simulation...");
		if (griddedOnly) {
			gridSeisDiscr = 0.1;
			ETAS_Simulator_NoFaults.runETAS_Simulation(outputDir, (UCERF3_GriddedSeisOnlyERF_ETAS)erf, griddedRegion,
					triggerRup, histQkList, includeSpontEvents, includeIndirectTriggering, gridSeisDiscr, simulationName,
					randSeed, params, null);
		} else {
			ETAS_Simulator.runETAS_Simulation(outputDir, (FaultSystemSolutionERF_ETAS)erf, griddedRegion, triggerRup,
					histQkList, includeSpontEvents, includeIndirectTriggering, gridSeisDiscr, simulationName, randSeed,
					fractionSrcAtPointList, srcAtPointList, isCubeInsideFaultPolygon, params, null, null);
		}
		
		System.out.println("DONE");
	}

}
