package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ThreadedSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.TimeCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.MinSectsPerParentFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveUnilateralRuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_SingleRegionGridSourceProvider.NSHM23_WUS_FiniteRuptureConverter;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

import scratch.UCERF3.U3FaultSystemRupSet;
import scratch.UCERF3.U3FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.utils.U3FaultSystemIO;

class DemoFileCreator {

	public static void main(String[] args) throws IOException {
		// this writes out a very simple fault system rupture set and solution for use in explaining the file formats
		// and unit tests
		
		File outputDir = new File("src/test/resources/org/opensha/sha/earthquake/faultSysSolution");
		
		FaultSectionPrefData fault1 = new FaultSectionPrefData();
		fault1.setSectionName("Demo Strike-Slip Fault");
		fault1.setSectionId(11);
		FaultTrace trace1 = new FaultTrace(null);
		trace1.add(new Location(34.7, -118));
		trace1.add(new Location(35, -118));
		fault1.setFaultTrace(trace1);
		fault1.setAveDip(90d);
		fault1.setAveRake(180d);
		fault1.setAveUpperDepth(0d);
		fault1.setAveLowerDepth(12d);
		fault1.setAveSlipRate(10d);
		fault1.setSlipRateStdDev(1d);
		fault1.setDipDirection((float)(trace1.getAveStrike()+90d));
		
		FaultSectionPrefData fault2 = new FaultSectionPrefData();
		fault2.setSectionName("Demo Reverse Fault");
		fault2.setSectionId(25);
		FaultTrace trace2 = new FaultTrace(null);
		trace2.add(new Location(35.2, -118.2));
		trace2.add(new Location(35.35, -118.35));
		fault2.setFaultTrace(trace2);
		fault2.setAveDip(45d);
		fault2.setAveRake(90d);
		fault2.setAveUpperDepth(0d);
		fault2.setAveLowerDepth(12d);
		fault2.setAveSlipRate(3d);
		fault2.setSlipRateStdDev(0.5d);
		fault2.setDipDirection((float)(trace2.getAveStrike()+90d));
		
		List<FaultSection> sects = new ArrayList<>();
		sects.add(fault1);
		sects.add(fault2);
		
		List<FaultSection> subSects = SubSectionBuilder.buildSubSects(sects);
		Preconditions.checkState(!subSects.isEmpty());
		
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		
		List<PlausibilityFilter> filters = new ArrayList<>();
		filters.add(new MinSectsPerParentFilter(2, false, false, null));
		PlausibilityConfiguration config = new PlausibilityConfiguration(filters, 0,
				new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, 50d), distAzCalc);
		
		ClusterRuptureBuilder rupBuilder = new ClusterRuptureBuilder(config);
		List<ClusterRupture> rups = rupBuilder.build(new ExhaustiveUnilateralRuptureGrowingStrategy());
		
		System.out.println("Created "+rups.size()+" ruptures");
		
		FaultSystemRupSet rupSet = FaultSystemRupSet.builderForClusterRups(subSects, rups)
				.forScalingRelationship(NSHM23_ScalingRelationships.LOGA_C4p2)
				.slipAlongRupture(SlipAlongRuptureModels.UNIFORM)
				.tectonicRegime(TectonicRegionType.ACTIVE_SHALLOW)
				.build(true);
		rupSet.getArchive().write(new File(outputDir, "demo_rup_set.zip"));
		// write old style as well
		U3FaultSystemRupSet oldRupSet = new U3FaultSystemRupSet(subSects, rupSet.getSlipRateForAllSections(),
				rupSet.getSlipRateStdDevForAllSections(), rupSet.getAreaForAllSections(),
				rupSet.getSectionIndicesForAllRups(), rupSet.getMagForAllRups(), rupSet.getAveRakeForAllRups(),
				rupSet.getAreaForAllRups(), rupSet.getLengthForAllRups(), rupSet.getInfoString());
		U3FaultSystemIO.writeRupSet(oldRupSet, new File(outputDir, "demo_old_rup_set.zip"));
		
		List<InversionConstraint> constraints = new ArrayList<>();
		constraints.add(new SlipRateInversionConstraint(1d, ConstraintWeightingType.UNNORMALIZED, rupSet,
				rupSet.requireModule(AveSlipModule.class), rupSet.requireModule(SlipAlongRuptureModel.class),
				rupSet.requireModule(SectSlipRates.class)));
		InversionInputGenerator invGen = new InversionInputGenerator(rupSet, constraints);
		
		invGen.generateInputs(true);
		
		int threads = 32;
		CompletionCriteria subCompletion = new IterationCompletionCriteria(1000000l);
		CompletionCriteria completion = new IterationCompletionCriteria(20000000l); // about 45s
//		CompletionCriteria completion = TimeCompletionCriteria.getInSeconds(10);
		invGen.columnCompress();
		ThreadedSimulatedAnnealing tsa = new ThreadedSimulatedAnnealing(
				invGen.getA(), invGen.getD(), invGen.getInitialSolution(), threads, subCompletion);
		tsa.setRandom(new Random(123456789l));
		long iterations = tsa.iterate(completion).iterations;
		System.out.println("Completed "+iterations+" iterations");
		double[] rates = invGen.adjustSolutionForWaterLevel(tsa.getBestSolution());
		
		FaultSystemSolution sol = new FaultSystemSolution(rupSet, rates);
		
		// make a fake grid source provider
		GriddedRegion gridReg = new GriddedRegion(new Location(34, -118), new Location(36, -120), 0.25, null);
		
		FaultGridAssociations assoc = FaultGridAssociations.getIntersectionAssociations(oldRupSet, gridReg);
		
		IncrementalMagFreqDist demoMFD = new IncrementalMagFreqDist(5.05, 8.45, 35);
		
		GutenbergRichterMagFreqDist unassociatedMFD =
				new GutenbergRichterMagFreqDist(1d, 0.05d, demoMFD.getMinX(), demoMFD.getMaxX(), demoMFD.size());
		
		List<RuptureSurface> sectSurfs = new ArrayList<>();
		for (FaultSection sect : subSects)
			sectSurfs.add(sect.getFaultSurface(1d));
		int numGridNodes = gridReg.getNumLocations();
		Map<Integer, IncrementalMagFreqDist> subSeisMFDs = new HashMap<>();
		Map<Integer, IncrementalMagFreqDist> otherMFDs = new HashMap<>();
		double[] fractSS = new double[numGridNodes];
		double[] fractR = new double[numGridNodes];
		double[] fractN = new double[numGridNodes];
		for (int i=0; i<numGridNodes; i++) {
			Location loc = gridReg.getLocation(i);
			
			otherMFDs.put(i, unassociatedMFD);
			fractSS[i] = 0.5;
			fractN[i] = 0.25;
			fractR[i] = 0.25;

			Map<Integer, Double> scaledNodeFracts = assoc.getScaledSectFracsOnNode(i);
			if (scaledNodeFracts != null && !scaledNodeFracts.isEmpty()) {
				double sumAssoc = 0d;
				for (int sectIndex : scaledNodeFracts.keySet()) {
					double fract = scaledNodeFracts.get(sectIndex);
					double sectFract = assoc.getScaledNodeFractions(sectIndex).get(i);
					System.out.println("Grid node "+i+" has a "+(float)fract+" association with section "+sectIndex+" (reverse assoc is "+(float)sectFract+")");
					sumAssoc += fract;
					IncrementalMagFreqDist subSeisMFD =
							new GutenbergRichterMagFreqDist(1d, 0.05d, demoMFD.getMinX(), demoMFD.getMaxX(), demoMFD.size());
					double minMag = rupSet.getMinMagForSection(sectIndex);
					int magIndex = subSeisMFD.getClosestXIndex(minMag);
					double sectTotalRate = 0d;
					for (int r : rupSet.getRupturesForSection(sectIndex))
						sectTotalRate += sol.getRateForRup(r);
					// scale for the fraction that the section is associated with this node
					sectTotalRate *= sectFract;
					System.out.println("\tpinning MFD to sectRate="+(float)sectTotalRate+" at minMag="+(float)minMag+", idx="+magIndex);
					subSeisMFD.scaleToCumRate(subSeisMFD.getX(magIndex), sectTotalRate);
					for (int j=magIndex; j<subSeisMFD.size(); j++)
						subSeisMFD.set(j, 0d);
					if (subSeisMFDs.containsKey(i)) {
						// sum with previous
						IncrementalMagFreqDist prev = subSeisMFDs.get(i);
						Preconditions.checkState(prev.size() == subSeisMFD.size());
						for (int j=0; j<prev.size(); j++)
							subSeisMFD.set(j, subSeisMFD.getY(j) + prev.getY(j));
					}
					subSeisMFDs.put(i, subSeisMFD);
				}
				Preconditions.checkState((float)sumAssoc <= 1f);
			}
		}
		MFDGridSourceProvider mfdGridProv = new AbstractGridSourceProvider.Precomputed(gridReg, subSeisMFDs, otherMFDs, fractSS, fractN, fractR);
		GridSourceList gridSources = GridSourceList.convert(mfdGridProv, assoc, new NSHM23_WUS_FiniteRuptureConverter());
		sol.addModule(gridSources);
		
		sol.getArchive().write(new File(outputDir, "demo_sol.zip"));
		U3FaultSystemSolution oldSol = new U3FaultSystemSolution(oldRupSet, rates);
		oldSol.setGridSourceProvider(mfdGridProv);
		U3FaultSystemIO.writeSol(oldSol, new File(outputDir, "demo_old_sol.zip"));
	}

}
