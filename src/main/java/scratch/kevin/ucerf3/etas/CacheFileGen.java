package scratch.kevin.ucerf3.etas;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.DocumentException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_CubeDiscretizationParams;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_LongTermMFDs;
import scratch.UCERF3.erf.ETAS.ETAS_PrimaryEventSampler;
import scratch.UCERF3.erf.ETAS.ETAS_Simulator;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.erf.ETAS.FaultSystemSolutionERF_ETAS;
import scratch.UCERF3.erf.ETAS.ETAS_Params.ETAS_ParameterList;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.griddedSeismicity.GridSourceFileReader;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.LastEventData;
import scratch.UCERF3.utils.RELM_RegionUtils;

public class CacheFileGen {
	
	private static FaultSystemSolution buildFakeTestFSS() throws IOException, DocumentException {
		FaultSystemSolution origFSS = FaultSystemIO.loadSol(
				new File("src/scratch/UCERF3/data/scratch/InversionSolutions/"
						+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip"));
		// restirct to only ruptures on Mojave S & Mojave N
		HashSet<Integer> parentIDs = new HashSet<>();
		parentIDs.add(286);
		parentIDs.add(301);
		List<Integer> validRupIDs = new ArrayList<>();
		FaultSystemRupSet origRupSet = origFSS.getRupSet();
		for (int r=0; r<origRupSet.getNumRuptures(); r++) {
			boolean match = true;
			for (FaultSection sect : origRupSet.getFaultSectionDataForRupture(r)) {
				if (!parentIDs.contains(sect.getParentSectionId())) {
					match = false;
					break;
				}
			}
			if (match)
				validRupIDs.add(r);
		}
		System.out.println("We have "+validRupIDs.size()+" ruptures");
		List<FaultSection> subSects = new ArrayList<>();
		Map<Integer, Integer> sectID_oldToNew = new HashMap<>();
		Map<Integer, Integer> sectID_newToOld = new HashMap<>();
		for (FaultSection sect : origRupSet.getFaultSectionDataList()) {
			if (parentIDs.contains(sect.getParentSectionId())) {
				int newID = subSects.size();
				sectID_oldToNew.put(sect.getSectionId(), newID);
				sectID_newToOld.put(newID, sect.getSectionId());
				sect.setSectionId(newID);
				subSects.add(sect);
			}
		}
		System.out.println("We have "+subSects.size()+" sections");
		double[] sectSlipRates = new double[subSects.size()];
		double[] sectSlipRateStdDevs = new double[subSects.size()];
		double[] sectAreas = new double[subSects.size()];
		List<IncrementalMagFreqDist> subSeismoOnFaultMFDs = new ArrayList<>();
		for (int s=0; s<subSects.size(); s++) {
			int sectIndex = sectID_newToOld.get(subSects.get(s).getSectionId());
			sectSlipRates[s] = origRupSet.getSlipRateForSection(sectIndex);
			sectSlipRateStdDevs[s] = origRupSet.getSlipRateStdDevForSection(sectIndex);
			sectAreas[s] = origRupSet.getAreaForSection(sectIndex);
			subSeismoOnFaultMFDs.add(origFSS.getSubSeismoOnFaultMFD_List().get(sectIndex));
		}
		List<List<Integer>> sectionForRups = new ArrayList<>();
		int numRups = validRupIDs.size();
		double[] mags = new double[numRups];
		double[] rakes = new double[numRups];
		double[] rupAreas = new double[numRups];
		double[] rupLengths = new double[numRups];
		double[] rates = new double[numRups];
		for (int r=0; r<numRups; r++) {
			int rupIndex = validRupIDs.get(r);
			mags[r] = origRupSet.getMagForRup(rupIndex);
			rakes[r] = origRupSet.getAveRakeForRup(rupIndex);
			rupAreas[r] = origRupSet.getAreaForRup(rupIndex);
			rupLengths[r] = origRupSet.getLengthForRup(rupIndex);
			List<Integer> rupSections = new ArrayList<>();
			for (Integer prevID : origRupSet.getSectionsIndicesForRup(rupIndex))
				rupSections.add(sectID_oldToNew.get(prevID));
			sectionForRups.add(rupSections);
			rates[r] = origFSS.getRateForRup(rupIndex);
		}
		FaultSystemRupSet rupSet = new FaultSystemRupSet(subSects, sectSlipRates, sectSlipRateStdDevs, sectAreas, sectionForRups,
				mags, rakes, rupAreas, rupLengths, "Fake reduced rup set");
		// now override some rates to make them really likely
		// but can't go over sum of 1, or things will fail?
		double mfdScale = 1d;
//		double mfdScale = 20d;
		double totSum = StatUtils.sum(rates);
//		double scaleRatio = 0.99d/totSum;
		double scaleRatio = 5;
		System.out.println("Original supra rate sum: "+totSum);
		for (int r=0; r<rates.length; r++)
			rates[r] *= scaleRatio;
		System.out.println("Scaled by "+scaleRatio+" supra rate sum: "+StatUtils.sum(rates));
//		double leftover = 1d - totSum;
//		if (leftover > 0) {
//			rates[0] = 0.7*leftover;
//			rates[rates.length-1] = 0.2*leftover;
//		}
		
		// only include grid sources within 100km of the given sections
		Region regionForGridded = null;
		for (FaultSection sect : subSects) {
			for (Location loc : new Location[] {sect.getFaultTrace().first(), sect.getFaultTrace().last()}) {
				if (regionForGridded == null)
					regionForGridded = new Region(loc, 100d);
				else
					regionForGridded = Region.union(regionForGridded, new Region(loc, 100d));
				Preconditions.checkNotNull(regionForGridded);
			}
		}
		GridSourceProvider origGridProv = origFSS.getGridSourceProvider();
		GriddedRegion region = origGridProv.getGriddedRegion();
		Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs = new HashMap<>();
		Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs = new HashMap<>();
		FaultPolyMgr faultPolyMgr = FaultPolyMgr.create(subSects, InversionTargetMFDs.FAULT_BUFFER);
		for (int index=0; index<region.getNodeCount(); index++) {
			Location loc = region.getLocation(index);
			IncrementalMagFreqDist subSeisMFD = origGridProv.getNodeSubSeisMFD(index);
			IncrementalMagFreqDist offMFD = origGridProv.getNodeUnassociatedMFD(index);
			Preconditions.checkState(subSeisMFD != null || offMFD != null);
			IncrementalMagFreqDist zeroMFD; // give things outside a very, very tiny G-R MFD, otherwise bad things happen
			if (subSeisMFD != null)
				zeroMFD = new GutenbergRichterMagFreqDist(1d, 1e-1, subSeisMFD.getMinX(), subSeisMFD.getMaxX(), subSeisMFD.size());
			else
				zeroMFD = new GutenbergRichterMagFreqDist(1d, 1e-1, offMFD.getMinX(), offMFD.getMaxX(), offMFD.size());
			double fract = faultPolyMgr.getNodeFraction(index);
			if (fract < 1e-10)
				// if previously non-null, this was from another fault
				subSeisMFD = null;
			// every node needs something
			if (regionForGridded.contains(loc)) {
				if (subSeisMFD != null) {
					subSeisMFD.scale(mfdScale);
					nodeSubSeisMFDs.put(index, subSeisMFD);
				}
				if (offMFD != null) {
					offMFD.scale(mfdScale);
					nodeUnassociatedMFDs.put(index, offMFD);
				} else if (subSeisMFD == null || fract < 1d) {
					nodeUnassociatedMFDs.put(index, zeroMFD);
				}
			} else {
				nodeUnassociatedMFDs.put(index, zeroMFD);
			}
			Preconditions.checkState(nodeUnassociatedMFDs.get(index) != null || nodeSubSeisMFDs.get(index) != null);
		}
		
		FaultSystemSolution sol = new FaultSystemSolution(rupSet, rates);
		sol.setGridSourceProvider(new GridSourceFileReader(region, nodeSubSeisMFDs, nodeUnassociatedMFDs));
		sol.setSubSeismoOnFaultMFD_List(subSeismoOnFaultMFDs);
		return sol;
	}

	public static void main(String[] args) throws IOException, DocumentException {
		AbstractGridSourceProvider.SOURCE_MIN_MAG_CUTOFF = 2.55;
//		File solFile = new File(args[0]);
//		FaultSystemSolution fss = FaultSystemIO.loadSol(
////				new File("src/scratch/UCERF3/data/scratch/InversionSolutions/"
//////						+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip"));
////				new File("src/scratch/UCERF3/data/scratch/InversionSolutions/"
////						+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_2_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip"));
//				new File("/home/kevin/OpenSHA/UCERF3/cybershake_etas/ucerf2_mapped_sol.zip"));
		FaultSystemSolution fss = buildFakeTestFSS();
		FaultSystemIO.writeSol(fss, new File("/home/kevin/git/ucerf3-etas-launcher/inputs/small_test_solution.zip"));
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
		
		ETAS_Simulator.runETAS_Simulation(resultsDir, erf, reg, mainshockRup, histQkList, includeSpontEvents,
				includeIndirectTriggering, gridSeisDiscr, null, randSeed,
				null, null, null, params, null, null);

		
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

		ETAS_CubeDiscretizationParams cubeParams = new ETAS_CubeDiscretizationParams(reg);
		ETAS_LongTermMFDs longTermMFDs = new ETAS_LongTermMFDs(erf, params.getApplySubSeisForSupraNucl());
		ETAS_PrimaryEventSampler etas_PrimEventSampler = new ETAS_PrimaryEventSampler(cubeParams, erf, longTermMFDs, sourceRates, 
				null, params, new ETAS_Utils(),null,null,null);
		
		etas_PrimEventSampler.getExpectedAfterShockRateInGridCellsFromSupraRates(10d, new ETAS_ParameterList(), 2.0, null);
	}

}
