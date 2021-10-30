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
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

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
		fault1.setSectionName("Demo S-S Fault");
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
		
		List<FaultSection> subSects = new ArrayList<>();
		for (FaultSection sect : sects)
			subSects.addAll(sect.getSubSectionsList(0.5*sect.getOrigDownDipWidth(), subSects.size(), 2));
		System.out.println("Built "+subSects.size()+" subsections");
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
				.forScalingRelationship(ScalingRelationships.SHAW_2009_MOD)
				.slipAlongRupture(SlipAlongRuptureModels.UNIFORM).build(true);
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
		long iterations = tsa.iterate(completion);
		System.out.println("Completed "+iterations+" iterations");
		double[] rates = invGen.adjustSolutionForWaterLevel(tsa.getBestSolution());
		
		FaultSystemSolution sol = new FaultSystemSolution(rupSet, rates);
		
		GriddedRegion gridReg = new GriddedRegion(new Location(34, -118), new Location(36, -120), 0.25, null);
		
		IncrementalMagFreqDist demoMFD = new IncrementalMagFreqDist(5.05, 8.45, 35);
		
		GutenbergRichterMagFreqDist unassociatedMFD =
				new GutenbergRichterMagFreqDist(1d, 0.05d, demoMFD.getMinX(), demoMFD.getMaxX(), demoMFD.size());
		
		double associationDist = 15d;
		
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
			
			int closestSect = -1;
			double minDist = Double.POSITIVE_INFINITY;
			for (int s=0; s<sectSurfs.size(); s++) {
				RuptureSurface surf = sectSurfs.get(s);
				double dist = Math.abs(surf.getDistanceX(loc));
				if (dist < minDist) {
					minDist = dist;
					closestSect = s;
				}
			}
			
			if (minDist < associationDist) {
				// don't use this as a guide for how gridded seismicity should be handled, it's just a demo intended
				// to look lik a real model might
				System.out.println("Grid node "+i+" is associated with section "+closestSect+" (dist="+(float)minDist+")");
				IncrementalMagFreqDist subSeisMFD =
						new GutenbergRichterMagFreqDist(1d, 0.05d, demoMFD.getMinX(), demoMFD.getMaxX(), demoMFD.size());
				double minMag = rupSet.getMinMagForSection(closestSect);
				int magIndex = subSeisMFD.getClosestXIndex(minMag);
				double sectTotalRate = 0d;
				for (int r : rupSet.getRupturesForSection(closestSect))
					sectTotalRate += sol.getRateForRup(r);
				System.out.println("\tpinning MFD to sectRate="+(float)sectTotalRate+" at minMag="+(float)minMag+", idx="+magIndex);
				subSeisMFD.scaleToCumRate(subSeisMFD.getX(magIndex), sectTotalRate);
				for (int j=magIndex; j<subSeisMFD.size(); j++)
					subSeisMFD.set(j, 0d);
//				System.out.println(subSeisMFD);
				subSeisMFDs.put(i, subSeisMFD);
			} else {
				System.out.println("Grid node "+i+" is unassociated (closest dist="+(float)minDist+")");
			}
		}
		sol.addModule(new AbstractGridSourceProvider.Precomputed(gridReg, subSeisMFDs, otherMFDs, fractSS, fractN, fractR));
		
		sol.getArchive().write(new File(outputDir, "demo_sol.zip"));
		U3FaultSystemSolution oldSol = new U3FaultSystemSolution(oldRupSet, rates);
		oldSol.setGridSourceProvider(sol.getGridSourceProvider());
		U3FaultSystemIO.writeSol(oldSol, new File(outputDir, "demo_old_sol.zip"));
	}

}
