package org.opensha.sha.earthquake.rupForecastImpl.nshm23.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.Inversions;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateSegmentationConstraint.RateCombiner;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.InputJumpsOrDistClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SegmentationCalculator;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.DistDependSegShift;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.MaxJumpDistModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationMFD_Adjustment;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationModelBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.ShawSegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.SectNucleationMFD_Estimator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.io.Files;

public class StrictSegReproductionMFDAdjustment extends SectNucleationMFD_Estimator {
	
	private List<IncrementalMagFreqDist> sectSupraSeisTargets;
	private JumpProbabilityCalc segModel;
	
	public StrictSegReproductionMFDAdjustment(JumpProbabilityCalc segModel) {
		this.segModel = segModel;
		
	}

	@Override
	public void init(FaultSystemRupSet rupSet, List<IncrementalMagFreqDist> origSectSupraSeisMFDs,
			double[] targetSectSupraMoRates, double[] targetSectSupraSlipRates, double[] sectSupraSlipRateStdDevs,
			List<BitSet> sectRupUtilizations, int[] sectMinMagIndexes, int[] sectMaxMagIndexes,
			int[][] sectRupInBinCounts, EvenlyDiscretizedFunc refMFD) {
		LogicTreeBranch<?> origBranch = rupSet.requireModule(LogicTreeBranch.class);
		
		double targetR0 = segModel instanceof Shaw07JumpDistProb ? ((Shaw07JumpDistProb)segModel).getR0() : 3d;
		
		List<LogicTreeLevel<?>> levels = new ArrayList<>();
		for (int i=0; i<origBranch.size(); i++) {
			LogicTreeLevel<?> level = origBranch.getLevel(i);
			Class<?> type = level.getType();
			if (!(segModel instanceof Shaw07JumpDistProb)) {
				LogicTreeNode val = origBranch.getValue(i);
				if (val instanceof ShawSegmentationModels)
					targetR0 = ((ShawSegmentationModels)val).getR0();
				else if (val instanceof NSHM23_SegmentationModels)
					targetR0 = ((NSHM23_SegmentationModels)val).getShawR0();
			}
			if (SegmentationModelBranchNode.class.isAssignableFrom(type)
					|| SegmentationMFD_Adjustment.class.isAssignableFrom(type)
					|| DistDependSegShift.class.isAssignableFrom(type))
				continue;
			levels.add(level);
		}
		levels.add(NSHM23_LogicTreeBranch.MAX_DIST);
		
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		ClusterConnectionStrategy connStrat;
		SectionDistanceAzimuthCalculator distAzCalc;
		if (rupSet.hasModule(PlausibilityConfiguration.class)) {
			PlausibilityConfiguration plausibility = rupSet.requireModule(PlausibilityConfiguration.class);
			connStrat = plausibility.getConnectionStrategy();
			distAzCalc = plausibility.getDistAzCalc();
		} else {
			HashSet<Jump> allJumps = new HashSet<>();
			for (ClusterRupture rup : cRups)
				for (Jump jump : rup.getJumpsIterable())
					allJumps.add(jump);
			distAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
			connStrat = new InputJumpsOrDistClusterConnectionStrategy(rupSet.getFaultSectionDataList(), distAzCalc, 0d, allJumps);
		}
		
		File tempDir = Files.createTempDir();
		
		NSHM23_InvConfigFactory factory = new NSHM23_InvConfigFactory();
		MaxJumpDistModels[] maxDists = MaxJumpDistModels.values();
		
		int totThreads = FaultSysTools.defaultNumThreads();
		int threadsEach = Integer.max(2, totThreads/maxDists.length);
		int execThreads = Integer.max(1, totThreads/threadsEach);
		
		ExecutorService exec = Executors.newFixedThreadPool(execThreads);
		List<Future<InvertCallable>> futures = new ArrayList<>();
		
		for (MaxJumpDistModels maxDist : maxDists) {
			LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<LogicTreeNode>(levels);
			for (LogicTreeLevel<?> level : levels) {
				LogicTreeNode origVal = origBranch.getValue(level.getType());
				if (origVal != null) {
					branch.setValue(origVal);
				} else {
					// see if there's a default value
					LogicTreeNode defaultVal = NSHM23_LogicTreeBranch.DEFAULT.getValue(level.getType());
					if (defaultVal instanceof NSHM23_ScalingRelationships)
						defaultVal = NSHM23_ScalingRelationships.AVERAGE;
					else if (defaultVal instanceof NSHM23_DeformationModels)
						defaultVal = NSHM23_DeformationModels.AVERAGE;
					if (defaultVal != null)
						branch.setValue(defaultVal);
				}
			}
			branch.setValue(maxDist);
			
			System.out.println("Running inversion for branch: "+branch);
			
			// invert
			FaultSystemRupSet tempRupSet = FaultSystemRupSet.buildFromExisting(rupSet, true).build();
			tempRupSet.removeModuleInstances(InversionTargetMFDs.class);
			InversionConfiguration config = factory.buildInversionConfig(tempRupSet, branch, threadsEach);
			
			futures.add(exec.submit(new InvertCallable(tempRupSet, cRups, config, connStrat, distAzCalc, maxDist, tempDir)));
		}
		List<CSVFile<String>> passthroughCSVs = new ArrayList<>();
		List<List<? extends IncrementalMagFreqDist>> supraMFDs = new ArrayList<>();
		for (Future<InvertCallable> future : futures) {
			try {
				InvertCallable call = future.get();
				passthroughCSVs.add(call.passthroughCSV);
				supraMFDs.add(call.supraMFDs);
			} catch (InterruptedException | ExecutionException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		exec.shutdown();
		
		MaxJumpDistModels.invertForWeights(maxDists, passthroughCSVs, targetR0);
		FileUtils.deleteRecursive(tempDir);
		
		System.out.println("Max dist weights:");
		for (MaxJumpDistModels model : maxDists)
			System.out.println("\t"+model.getName()+": "+model.getNodeWeight(null));
		
		sectSupraSeisTargets = new ArrayList<>();
		for (int s=0; s<rupSet.getNumSections(); s++) {
			SummedMagFreqDist mfd = null;
			double sumWeight = 0d;
			for (int i=0; i<maxDists.length; i++) {
				double weight = maxDists[i].getNodeWeight(null);
				IncrementalMagFreqDist maxDistMFD = supraMFDs.get(i).get(s);
				if (weight > 0d && maxDistMFD != null) {
					sumWeight += weight;
					if (mfd == null)
						mfd = new SummedMagFreqDist(refMFD.getMinX(), refMFD.getMaxX(), refMFD.size());
					mfd.addIncrementalMagFreqDist(maxDistMFD);
				}
			}
			if (mfd != null)
				mfd.scale(1d/sumWeight);
			sectSupraSeisTargets.add(mfd);
		}
		
		super.init(rupSet, origSectSupraSeisMFDs, targetSectSupraMoRates, targetSectSupraSlipRates, sectSupraSlipRateStdDevs,
				sectRupUtilizations, sectMinMagIndexes, sectMaxMagIndexes, sectRupInBinCounts, refMFD);
	}

	@Override
	public boolean appliesTo(FaultSection sect) {
		return sectSupraSeisTargets.get(sect.getSectionId()) != null;
	}

	@Override
	public IncrementalMagFreqDist estimateNuclMFD(FaultSection sect, IncrementalMagFreqDist curSectSupraSeisMFD,
			List<Integer> availableRupIndexes, List<Double> availableRupMags, UncertainDataConstraint sectMomentRate,
			boolean sparseGR) {
		IncrementalMagFreqDist mfd = sectSupraSeisTargets.get(sect.getSectionId());
		if (mfd != null) {
			mfd = mfd.deepClone();
			mfd.scaleToTotalMomentRate(curSectSupraSeisMFD.getTotalMomentRate());
		}
		return mfd;
	}
	
	private static class InvertCallable implements Callable<InvertCallable> {
		
		// inputs
		private FaultSystemRupSet rupSet;
		private ClusterRuptures cRups;
		private InversionConfiguration config;
		private ClusterConnectionStrategy connStrat;
		private SectionDistanceAzimuthCalculator distAzCalc;
		private MaxJumpDistModels maxDist;
		private File tempDir;
		
		// outputs
		private CSVFile<String> passthroughCSV;
		private List<? extends IncrementalMagFreqDist> supraMFDs;

		public InvertCallable(FaultSystemRupSet rupSet, ClusterRuptures cRups, InversionConfiguration config,
				ClusterConnectionStrategy connStrat, SectionDistanceAzimuthCalculator distAzCalc,
				MaxJumpDistModels maxDist, File tempDir) {
			this.rupSet = rupSet;
			this.cRups = cRups;
			this.config = config;
			this.connStrat = connStrat;
			this.distAzCalc = distAzCalc;
			this.maxDist = maxDist;
			this.tempDir = tempDir;
		}

		@Override
		public InvertCallable call() throws Exception {
			FaultSystemSolution sol = Inversions.run(rupSet, config);
			
			SegmentationCalculator calc = new SegmentationCalculator(
					sol, cRups.getAll(), connStrat, distAzCalc, new double[] {0d});
			String prefix = maxDist.name();
			try {
				calc.plotDistDependComparison(tempDir, maxDist.name(), false, RateCombiner.MIN);
				File csvFile = new File(tempDir, prefix+"_supra_seis.csv");
				passthroughCSV = CSVFile.readFile(csvFile, true);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
			supraMFDs = rupSet.requireModule(InversionTargetMFDs.class).getOnFaultSupraSeisNucleationMFDs();
			return this;
		}
		
	}

}
