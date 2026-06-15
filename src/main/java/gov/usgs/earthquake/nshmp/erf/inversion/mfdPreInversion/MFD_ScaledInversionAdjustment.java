package gov.usgs.earthquake.nshmp.erf.inversion.mfdPreInversion;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.IntegerSampler;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExecutorUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.JumpProbabilityConstraint.SectParticipationRateEstimator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ColumnOrganizedAnnealingData;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SerialSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ThreadedSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompoundCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.EnergyChangeVsPrevCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.CoolingScheduleType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump.UniqueDistJump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.GRParticRateEstimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.SectNucleationMFD_Estimator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Ints;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;

/**
 * MFD adjustment to solve for realistic target MFDs that respect availability on corupturing sections (mitigates slip
 * rate changes) and segmentation constraints.
 */
public class MFD_ScaledInversionAdjustment extends SectNucleationMFD_Estimator {
	
	private JumpProbabilityCalc segModel;
	
	private RupSetCoruptureMFDStructure structure;
	private double[] solution;
	private int threads;
	
	private static volatile ExecutorService tsaExec;

	public MFD_ScaledInversionAdjustment(int threads, JumpProbabilityCalc segModel) {
		this.threads = threads;
		this.segModel = segModel;
	}

	@Override
	public boolean appliesTo(FaultSection sect) {
		return true;
	}

	@Override
	public void init(FaultSystemRupSet rupSet, List<IncrementalMagFreqDist> origSectSupraSeisMFDs,
			double[] targetSectSupraMoRates, double[] targetSectSupraSlipRates, double[] sectSupraSlipRateStdDevs,
			List<BitSet> sectRupUtilizations, int[] sectMinMagIndexes, int[] sectMaxMagIndexes,
			int[][] sectRupInBinCounts, EvenlyDiscretizedFunc refMFD) {
		boolean verbose = true;
		
		if (verbose) System.out.println("Building corupture structure");
		structure = new RupSetCoruptureMFDStructure(rupSet, origSectSupraSeisMFDs,
				targetSectSupraMoRates, targetSectSupraSlipRates, sectSupraSlipRateStdDevs,
				sectRupUtilizations, sectMinMagIndexes, sectMaxMagIndexes, sectRupInBinCounts, refMFD, segModel);
		
		int numSections = rupSet.getNumSections();
		int numIsolatedSects = structure.getNumIsolatedSections();
		int columns = structure.getNumColumns();
		if (numIsolatedSects == numSections) {
			if (verbose) System.out.println("All sections are isolated, skipping MFD inversion");
			// fully isolated, skip the inversion
			Preconditions.checkState(columns == numSections);
			solution = new double[columns];
			for (int i=0; i<solution.length; i++)
				solution[i] = 1d;
			return;
		}
		
		if (verbose) System.out.println("Building constraints");
		List<InversionConstraint> constraints = new ArrayList<>();
		
		/*
		 * Data constraints: create realistic target MFDs that the full rate model inversion will be able to satisfy
		 */
		// slip rate constraint: ensure each nucleation MFD matches the original slip rate
		constraints.add(new MFD_SectSlipRateConstraint(structure, 1e2, ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY));
		// slip corupture balancing constraint: ensure slip in large multifault rupture mag bins is available on all faults
		constraints.add(new SectCoruptureBudgetConstraint(structure, 1e0, ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY));
		if (segModel != null)
			// segmentation constraint: ensure multifault rupture MFD bins don't exceed segmentation rates
			constraints.add(new MFD_SegmentationConstraint(structure, 1e2, SegConstraintMethod.FULL_PARTICIPATION));
		/*
		 * Regularization constraints: force the MFD inversion to chose the solution we want from the many available
		 * to fit those data constraints
		 * 
		 * * Force it to never net reduce single-fault bins (scale factor >= 1)
		 * * Force it to never net increase multi-fault bins (scale factor <= 1)
		 * * Force it to try to keep scale factors near 1 unless absolutely needed to fit the data
		 */
		// scale factor normalization: ensure single fault scale factors are >=1 (strong)
		constraints.add(new ScaleFactorLimitConstraint(structure, true, 1e4));
		// scale factor normalization: ensure multi fault scale factors are <=1 (strong)
		constraints.add(new ScaleFactorLimitConstraint(structure, false, 1e4));
		// scale factor normalization: keep-near 1 (weakly), this applies to each scale factor with the same weight
		constraints.add(new ScaleFactorOneConstraint(structure, 1e0, 1e0));
		// scale factor smoothing: keep it similar (weakly) on neighboring sections for identical pathways
		constraints.add(new ScaleFactorParentStability(structure, 1e0, 1e0));
		// minimzation constraint: constraint to force it to use large magnitude bins if they don't break the other constraints
		constraints.add(new SectRateMinimizationConstraint(structure, 1e0, ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, false));
		
		if (verbose) System.out.println("Building inversion inputs");
		List<ConstraintRange> constraintRowRanges = InversionInputGenerator.buildConstraintRanges(constraints, verbose);
		int numRows = 0;
		int numIneqRows = 0;
		for (ConstraintRange range : constraintRowRanges) {
			if (range.inequality)
				numIneqRows = range.endRow;
			else
				numRows = range.endRow;
		}
		
		DoubleMatrix2D A = null;
		double[] d = null;
		
		if (numRows > 0) {
			A = new SparseDoubleMatrix2D(numRows, columns);
			d = new double[numRows];
		}
		
		DoubleMatrix2D A_ineq = null;
		double[] d_ineq = null;
		
		if (numIneqRows > 0) {
			A_ineq = new SparseDoubleMatrix2D(numIneqRows, columns);
			d_ineq = new double[numIneqRows];
		}
		
		if (verbose) System.out.println("Encoding matrices");
		
		Stopwatch watch = verbose ? Stopwatch.createStarted() : null;
		int numNonZero = 0;
		
		DecimalFormat oneDigit = new DecimalFormat("0.0");
		
		for (int i=0; i<constraints.size(); i++) {
			InversionConstraint constraint = constraints.get(i);
			ConstraintRange rowRange = constraintRowRanges.get(i);
			
			DoubleMatrix2D myA;
			double[] myD;
			if (constraint.isInequality()) {
				myA = A_ineq;
				myD = d_ineq;
			} else {
				myA = A;
				myD = d;
			}
			
			if (verbose)
				System.out.println("\tEncoding "+constraint.getName()
					+", ineq="+constraint.isInequality());
			Stopwatch subWatch = verbose ? Stopwatch.createStarted() : null;
			long myNonZero = constraint.encode(myA, myD, rowRange.startRow);
			if (verbose) {
				long maxNum = (rowRange.endRow - rowRange.startRow)*(long)columns;
				double density = 100d*(double)myNonZero/(double)maxNum;
				System.out.println("\t\tDONE, took "+InversionInputGenerator.getTimeStr(subWatch)+" to encode "
						+myNonZero+" values (density: "+oneDigit.format(density)+" %)");
				subWatch.stop();
			}
			numNonZero += myNonZero;
		}
		
		if (verbose) {
			long maxNum = (numRows+numIneqRows)*(long)columns;
			double density = 100d*(double)numNonZero/(double)maxNum;
			System.out.println("DONE encoding, took "+InversionInputGenerator.getTimeStr(watch)+" to encode "
					+numNonZero+" values (density: "+oneDigit.format(density)+" %)");
			watch.stop();
		}
		
//		for (ConstraintRange range : constraintRowRanges) {
//			DoubleMatrix2D myA = range.inequality ? A_ineq : A;
//			double[] myD = range.inequality ? d_ineq : d;
//			System.out.println("Sol=1 debug for "+range.name);
//			System.out.println("=== A ===");
//			printA(myA, range);
//			System.out.println("=== d ===");
//			printD(myD, range);
//			System.out.println("=== misfits ===");
//			misfitsDebugFor1s(myA, myD, range);
//		}
//		System.exit(0);
		
		// run the inversion
		ColumnOrganizedAnnealingData equalityData = numRows > 0 ? new ColumnOrganizedAnnealingData(A, d) : null;
		ColumnOrganizedAnnealingData inequalityData = numIneqRows > 0 ? new ColumnOrganizedAnnealingData(A_ineq, d_ineq) : null;
		
		double[] initial = new double[columns];
		// starting with unity scale factors will give quicker inversions and slightly better solutions for trivial cases
		// starting with zeros is slower but seems to give better solutions for complex cases
		for (int i=0; i<columns; i++)
			initial[i] = 1d;
		
		IntegerSampler sampler = null;
		
		if (numIsolatedSects > 0) {
			// we have isolated sections, skip them in the inversion and 
			HashSet<Integer> isolatedCols = new HashSet<>(numIsolatedSects);
			for (int sectIndex : structure.getIsolatedSections()) {
				int col = structure.getSectPathwayColumn(sectIndex, 0);
				isolatedCols.add(col);
				// make sure those excluded are locked at 1
				initial[col] = 1d;
			}
			sampler = new IntegerSampler.ExclusionIntegerSampler(0, columns, isolatedCols);
		}
		
		GenerationFunctionType perturb = GenerationFunctionType.EXPONENTIAL_SCALE;
		NonnegativityConstraintType nonneg = NonnegativityConstraintType.TRY_ZERO_RATES_OFTEN;
//		GenerationFunctionType perturb = GenerationFunctionType.UNIFORM_0p0001;
//		NonnegativityConstraintType nonneg = NonnegativityConstraintType.LIMIT_ZERO_RATES;
		int numVals = Integer.max(columns, 100);
		long itersPerVal = 10000l;
		CoolingScheduleType cool = CoolingScheduleType.FAST_SA;
//		long itersPerVal = 10000l;
//		CoolingScheduleType cool = CoolingScheduleType.CLASSICAL_SA;
		
		long minTotalIters = itersPerVal * numVals;
		CompletionCriteria completion;
		
//		threads = 1;
		SimulatedAnnealing sa;
		if (threads > 1) {
			// do at least this many averaging rounds
			int minRounds = 10;
			// do at most this many times minRounds rounds
			int maxRoundScalar = 10;
			// within each round, do across-thread-swapping this many times
			// 1 means just pull the best performing thread at the end of each round
			int subIterScalar = 1;
			
			long maxTotalIters = minTotalIters * maxRoundScalar;
			// require at least minTotalIters and convergence (change <1% for 4 consecutive rounds)
			CompletionCriteria minCompletion = new CompoundCompletionCriteria(
					List.of(new EnergyChangeVsPrevCompletionCriteria(0.01, 4),
							new IterationCompletionCriteria(minTotalIters)), true); // true here means logical and
			// but bail if we hit maxTotalIters
			completion = new CompoundCompletionCriteria(
					List.of(minCompletion, new IterationCompletionCriteria(maxTotalIters)), false); // false here means logical or
			
			int avgThreads = Integer.max(threads/4, 2);
			
			int threadsPerAvg = (int)Math.ceil((double)threads/(double)avgThreads);
			Preconditions.checkState(threadsPerAvg <= threads);
			Preconditions.checkState(threadsPerAvg > 0);
			
			long roundIters = minTotalIters/minRounds;
			CompletionCriteria avgCompletion = new IterationCompletionCriteria(roundIters);
			long threadIters = roundIters / subIterScalar;
			CompletionCriteria subCompletion = new IterationCompletionCriteria(threadIters);
			
			int threadsLeft = threads;
			
			if (tsaExec == null) {
				synchronized (MFD_ScaledInversionAdjustment.class) {
					if (tsaExec == null)
						tsaExec = ExecutorUtils.newCachedDaemonThreadPool("MFD-ScaledInvAdj");
				}
			}
			
			// arrange lower-level (actual worker) SAs
			List<SimulatedAnnealing> tsas = new ArrayList<>();
			while (threadsLeft > 0) {
				int myThreads = Integer.min(threadsLeft, threadsPerAvg);
				if (myThreads > 1) {
					ThreadedSimulatedAnnealing tsa = new ThreadedSimulatedAnnealing(equalityData, inequalityData,
							initial, 0d, myThreads, subCompletion);
					tsa.setExecutorService(tsaExec);
					tsas.add(tsa);
				} else {
					tsas.add(new SerialSimulatedAnnealing(equalityData, inequalityData,
							initial, 0d));
				}
				threadsLeft -= myThreads;
			}
			ThreadedSimulatedAnnealing tsa = new ThreadedSimulatedAnnealing(tsas, avgCompletion, true);
			tsa.setExecutorService(tsaExec);
			sa = tsa;
		} else {
			completion = new IterationCompletionCriteria(minTotalIters);
			
			sa = new SerialSimulatedAnnealing(equalityData, inequalityData, initial, 0d);
		}
		sa.setConstraintRanges(constraintRowRanges);
		
		sa.setPerturbationFunc(perturb);
		sa.setNonnegativeityConstraintAlgorithm(nonneg);
		sa.setCoolingFunc(cool);
		if (sampler != null)
			sa.setRuptureSampler(sampler);
		
		sa.iterate(completion);
		
		solution = sa.getBestSolution();

//		System.exit(0);
		
		if (verbose) {
			System.out.println("DONE estimating MFDs");
			DecimalFormat magDF = new DecimalFormat("0.0");
			DecimalFormat fractDF = new DecimalFormat("0.0000");
			for (int s=0; s<rupSet.getNumSections(); s++) {
				StringBuilder line = new StringBuilder();
				line.append(s).append(". ").append(rupSet.getFaultSectionData(s).getSectionName()).append(":");
				List<SectCommonPathwaysMagRange> pathways = structure.getSectCommonPathways(s);
				for (int p=0; p<pathways.size(); p++) {
					SectCommonPathwaysMagRange path = pathways.get(p);
					int col = structure.getSectPathwayColumn(s, p);
					int relCol = col-structure.getSectPathwayColumn(s, 0);
					line.append("    M[").append(magDF.format(refMFD.getX(path.minMagIndex))).append("-")
						.append(magDF.format(refMFD.getX(path.maxMagIndex)));
					line.append(", c=").append(relCol);
					line.append("]=").append(fractDF.format(solution[col]));
				}
				System.out.println(line);
			}
		}
		
//		System.exit(0);
		
//		for (ConstraintRange range : constraintRowRanges) {
//			DoubleMatrix2D myA = range.inequality ? A_ineq : A;
//			double[] myD = range.inequality ? d_ineq : d;
//			System.out.println("Sol=1 debug for "+range.name);
//			System.out.println("=== A ===");
//			printA(myA, range);
//			System.out.println("=== d ===");
//			printD(myD, range);
//			System.out.println("=== misfits ===");
//			misfitsDebug(myA, myD, solution, range);
//		}
//		System.exit(0);
	}

	@Override
	public IncrementalMagFreqDist estimateNuclMFD(FaultSection sect, IncrementalMagFreqDist curSectSupraSeisMFD,
			List<Integer> availableRupIndexes, List<Double> availableRupMags, UncertainDataConstraint sectMomentRate,
			boolean sparseGR) {
		EvenlyDiscretizedFunc refMFD = structure.getRefMFD();
		int sectIndex = sect.getSectionId();
		IncrementalMagFreqDist origMFD = structure.getOrigSectSupraSeisMFDs().get(sectIndex);
		IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		List<SectCommonPathwaysMagRange> pathways = structure.getSectCommonPathways(sectIndex);
		for (int p=0; p<pathways.size(); p++) {
			SectCommonPathwaysMagRange pathway = pathways.get(p);
			int col = structure.getSectPathwayColumn(sectIndex, p);
			double scalar = solution[col];
			Preconditions.checkState(Double.isFinite(scalar));
			for (int m=pathway.minMagIndex; m<=pathway.maxMagIndex; m++)
				mfd.set(m, origMFD.getY(m)*scalar);
		}
		return mfd;
	}
	
	/**
	 * Weighting schemes for ruptures, with higher weights assigned to ruptures the rate model inversion is most likely to use
	 */
	private enum RuptureWeightingMethod {
		/**
		 * Restrict by the minimum available slip rate. This works well because it doesn't assume bidirectional
		 * availability, i.e., ruptures on a slow slip rate section won't be weighted higher if they jump onto a
		 * different high slip section than those that stay on the the slow slip rate section.
		 * 
		 * The other options below all will increase weights for ruptures that include high slip rates sections.
		 */
		MIN_AVAIL_SLIP,
		MEAN_AVAIL_SLIP,
		GEOM_MEAN_AVAIL_SLIP,
		HARM_MEAN_AVAIL_SLIP,
	}
	
	private static class RupSetCoruptureMFDStructure {
		
		private Map<Integer, List<FaultSection>> parentSectsMap;
		
		private BitSet includedRups;
		private List<Integer> includedRupIndexes;
		
		// column organization
		private int numCols;
		private int[] sectColIndexes;
		private int[] sectMinMagIndexes;
		private int[] sectMaxMagIndexes;
		
		// rupture pathways
		private List<List<SectMagRupturePathways>> sectRupMagPathways;
		private List<List<SectCommonPathwaysMagRange>> sectCommonPathways;
		private boolean bundleAllSingleFaultPathways = true;
		private BitSet isolatedSections;

		private FaultSystemRupSet rupSet;

		private List<IncrementalMagFreqDist> origSectSupraSeisMFDs;

		private double[] targetSectSupraMoRates;
		private double[] targetSectSupraSlipRates;
		private double[] sectSupraSlipRateStdDevs;

		private List<BitSet> sectRupUtilizations;

		private int[][] sectRupInBinCounts;

		private EvenlyDiscretizedFunc refMFD;

		private JumpProbabilityCalc segModel;
		
		/**
		 * This controls how slip rates (potentially with segmentation-adjustments) within each rupture
		 * are combined to compute an availability weight for that rupture
		 */
		private RuptureWeightingMethod rupWeightingMethod = RuptureWeightingMethod.MIN_AVAIL_SLIP;
//		private RuptureWeightingMethod rupWeightingMethod = RuptureWeightingMethod.HARM_MEAN_AVAIL_SLIP;
		
		/**
		 *  Exponent applied to availability weights before normalization, designed to mimic how the rate model
		 *  inversion chooses the easiest pathway.
		 *  
		 *  A value of 1 assumes that the inversion will choose pathways proportionally to their availability score.
		 *  A value >1 concentrates rate on the best-supported pathways.
		 */
		private double pathwayWeightGamma = 2;
		
		public RupSetCoruptureMFDStructure(FaultSystemRupSet rupSet, List<IncrementalMagFreqDist> origSectSupraSeisMFDs,
				double[] targetSectSupraMoRates, double[] targetSectSupraSlipRates, double[] sectSupraSlipRateStdDevs,
				List<BitSet> sectRupUtilizations, int[] sectMinMagIndexes, int[] sectMaxMagIndexes,
				int[][] sectRupInBinCounts, EvenlyDiscretizedFunc refMFD, JumpProbabilityCalc segModel) {
			this.rupSet = rupSet;
			
			int numRuptures = rupSet.getNumRuptures();
			int numSections = rupSet.getNumSections();
			
			for (int s=0; s<numSections; s++) {
				IncrementalMagFreqDist mfd = origSectSupraSeisMFDs.get(s);
				Preconditions.checkNotNull(mfd);
				Preconditions.checkState(EvenlyDiscretizedFunc.areXValuesIdentical(mfd, refMFD));
				Preconditions.checkState(sectMinMagIndexes[s] >= 0);
				Preconditions.checkState(sectMaxMagIndexes[s] >= sectMinMagIndexes[s]);
			}
			this.origSectSupraSeisMFDs = origSectSupraSeisMFDs;
			this.targetSectSupraMoRates = targetSectSupraMoRates;
			this.targetSectSupraSlipRates = targetSectSupraSlipRates;
			this.sectSupraSlipRateStdDevs = sectSupraSlipRateStdDevs;
			this.sectRupUtilizations = sectRupUtilizations;
			this.sectRupInBinCounts = sectRupInBinCounts;
			this.refMFD = refMFD;
			this.segModel = segModel;
			List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
			
			// bin sections by parent
			parentSectsMap = new HashMap<>();
			for (FaultSection sect : subSects) {
				int parentID = sect.getParentSectionId();
				Preconditions.checkState(parentID >= 0, "ParentIDs are required");
				int subSectIndex = sect.getSubSectionIndex();
				Preconditions.checkState(subSectIndex >= 0, "Sub-section indexes are required");
				if (!parentSectsMap.containsKey(parentID))
					parentSectsMap.put(parentID, new ArrayList<>());
				List<FaultSection> parentSects = parentSectsMap.get(parentID);
				while (parentSects.size() <= subSectIndex)
					parentSects.add(null);
				Preconditions.checkState(parentSects.get(subSectIndex) == null);
				parentSects.set(subSectIndex, sect);
			}
			
			// ruptures we're considering (may not be all if some have been explicitly filtered out beforehand)
			includedRups = new BitSet(numRuptures);
			for (BitSet sectRups : sectRupUtilizations)
				includedRups.or(sectRups);
			
			this.sectMinMagIndexes = sectMinMagIndexes;
			this.sectMaxMagIndexes = sectMaxMagIndexes;
			
			ClusterRuptures cRups = segModel == null ? null : rupSet.requireModule(ClusterRuptures.class);
			
			// set of parent sections involved in each rupture
			List<Set<Integer>> rupParentSets = new ArrayList<>(numRuptures);
			includedRupIndexes = new ArrayList<>(numRuptures);
			for (int rupIndex=0; rupIndex<numRuptures; rupIndex++) {
				if (includedRups.get(rupIndex)) {
					Set<Integer> parents = new HashSet<>();
					for (FaultSection sect : rupSet.getFaultSectionDataForRupture(rupIndex))
						parents.add(sect.getParentSectionId());
					rupParentSets.add(parents);
					includedRupIndexes.add(rupIndex);
				} else {
					rupParentSets.add(null);
				}
			}
			
			sectRupMagPathways = new ArrayList<>(numSections);
			sectCommonPathways = new ArrayList<>(numSections);
			isolatedSections = new BitSet(numSections);
			for (int s=0; s<numSections; s++) {
				BitSet sectRups = sectRupUtilizations.get(s);
				
				int myNumMags = 1 + sectMaxMagIndexes[s] - sectMinMagIndexes[s];
				// ruptures on this section, grouped by magnitude bin
				List<List<Integer>> rupsByMagIndex = new ArrayList<>(myNumMags);
				for (int m=0; m<myNumMags; m++)
					rupsByMagIndex.add(null);
				
				for (int rupIndex : includedRupIndexes) {
					if (!sectRups.get(rupIndex))
						continue;
					
					int magIndex = refMFD.getClosestXIndex(rupSet.getMagForRup(rupIndex)) - sectMinMagIndexes[s];
					List<Integer> rupsForMag = rupsByMagIndex.get(magIndex);
					if (rupsForMag == null) {
						rupsForMag = new ArrayList<>();
						rupsByMagIndex.set(magIndex, rupsForMag);
					}
					rupsForMag.add(rupIndex);
				}

				List<SectMagRupturePathways> myPathways = new ArrayList<>(myNumMags);
				sectRupMagPathways.add(myPathways);
				
				int curCommonPathwayMmin = -1;
				int curCommonPathwayMmax = -1;
				boolean curCommonPathwayHasSingleFault = false;
				Set<Set<Integer>> curCommonPathways = null;
				List<SectCommonPathwaysMagRange> myCommonPathways = new ArrayList<>(Integer.min(4, myNumMags));
				sectCommonPathways.add(myCommonPathways);
				
				boolean anyMultiFault = false;
				
				for (int m=0; m<myNumMags; m++) {
					List<Integer> rupsForMag = rupsByMagIndex.get(m);
					if (rupsForMag == null) {
						// no ruptures at this magnitude
						myPathways.add(null);
						continue;
					}
					int magIndex = sectMinMagIndexes[s] + m;
					
					// figure out each unique parent combination/pathway and the associated ruptures
					// also keep track of the ruptures using those pathways (for seg constraints)
					Map<Set<Integer>, List<Integer>> uniqueParentCombRups = new HashMap<>();
					boolean anySingleFault = false;
					for (int rupIndex : rupsForMag) {
						Set<Integer> parents = rupParentSets.get(rupIndex);
						if (parents.size() == 1)
							anySingleFault = true;
						else
							anyMultiFault = true;
						if (!uniqueParentCombRups.containsKey(parents))
							uniqueParentCombRups.put(parents, new ArrayList<>());
						uniqueParentCombRups.get(parents).add(rupIndex);
					}
					
					List<Set<Integer>> parentPathways = new ArrayList<>(uniqueParentCombRups.size());
					List<Double> pathwayWeights = new ArrayList<>(uniqueParentCombRups.size());
					List<int[]> pathwayRups = new ArrayList<>(uniqueParentCombRups.size());
					List<double[]> pathwayRupWeights = new ArrayList<>(uniqueParentCombRups.size());
					
					double sumPathwayWeights = 0d;
					for (Set<Integer> parents : uniqueParentCombRups.keySet()) {
						List<Integer> rupsForPathway = uniqueParentCombRups.get(parents);
						
						double[] rupWeightsForPathway = new double[rupsForPathway.size()];
						
						for (int i=0; i<rupsForPathway.size(); i++) {
							int rupIndex = rupsForPathway.get(i);

							List<FaultSection> rupSects = rupSet.getFaultSectionDataForRupture(rupIndex);
							// figure out average slip rate for each parent in this rupture
							// we'll use this when weighting to smooth over any slip-rate fluctuations
							Map<Integer, DoubleAverager> parentSlips = new HashMap<>(parents.size());
							for (FaultSection sect : rupSects) {
								int parentID = sect.getParentSectionId();
								if (!parentSlips.containsKey(parentID))
									parentSlips.put(parentID, new DoubleAverager());
								parentSlips.get(parentID).add(targetSectSupraSlipRates[sect.getSectionId()]);
							}
							
							// calculate weight for this rupture, based on lowest segmentation-adjusted slip rate
							List<Double> rupParentWeights;
							if (segModel == null) {
								// simple, just use smallest slip rate as weight
								rupParentWeights = new ArrayList<>(parents.size());
								for (int parentID : parents)
									rupParentWeights.add(parentSlips.get(parentID).getAverage());
							} else {
								// also incorporate segmentation rates
								ClusterRupture cRup = cRups.get(rupIndex);
								RuptureTreeNavigator rupNav = cRup.getTreeNavigator();
								
								rupParentWeights = new ArrayList<>(cRup.getTotalNumClusters());
								for (FaultSubsectionCluster cluster : cRup.getClustersIterable()) {
									double slipRate  = parentSlips.get(cluster.parentSectionID).getAverage();
									double clusterWeight = slipRate;
									
									// process all jumps in the direction from this cluster to all neighbors so that we
									// get the seg constraint as it applies to this cluster
									FaultSubsectionCluster predecessor = rupNav.getPredecessor(cluster);
									if (predecessor != null) {
										Jump jump = rupNav.getJump(cluster, predecessor);
										double segRate = segModel.calcJumpProbability(cRup, jump, false);
										if (segRate < 1d)
											clusterWeight = Math.min(clusterWeight, segRate*slipRate);
									}
									for (FaultSubsectionCluster descendant : rupNav.getDescendants(cluster)) {
										Jump jump = rupNav.getJump(cluster, descendant);
										double segRate = segModel.calcJumpProbability(cRup, jump, false);
										if (segRate < 1d)
											clusterWeight = Math.min(clusterWeight, segRate*slipRate);
									}
									rupParentWeights.add(clusterWeight);
								}
							}
							Preconditions.checkState(!rupParentWeights.isEmpty());
							for (double val : rupParentWeights)
								Preconditions.checkState(Double.isFinite(val) && val >= 0d,
										"Bad weight encountered: %s for rup %s on sect %s", val, rupIndex, s);
							double weight = switch (rupWeightingMethod) {
							case MIN_AVAIL_SLIP: {
								double min = Double.POSITIVE_INFINITY;
								for (double val : rupParentWeights)
									min = Math.min(min, val);
								yield min;
							}
							case MEAN_AVAIL_SLIP: {
								yield rupParentWeights.stream().mapToDouble(D->D).average().getAsDouble();
							}
							case GEOM_MEAN_AVAIL_SLIP: {
								double sumOfLogs = 0.0;

								for (Double val : rupParentWeights) {
									if (val == 0)
										yield 0;
									sumOfLogs += Math.log(val);
								}

								// Return the exponentiated average of the logs
								yield Math.exp(sumOfLogs / rupParentWeights.size());
							}
							case HARM_MEAN_AVAIL_SLIP: {
								double reciprocalSum = 0.0;
						        for (Double val : rupParentWeights) {
						        	if (val == 0)
										yield 0;
						            reciprocalSum += 1.0 / val;
						        }

						        yield rupParentWeights.size() / reciprocalSum;
							}
							default:
								throw new IllegalArgumentException("Unexpected value: " + rupWeightingMethod);
							};
							Preconditions.checkState(Double.isFinite(weight));
							rupWeightsForPathway[i] = weight;
						}
						
						// use the average of all rupture weights as our pathway weight
						double pathwayWeight = StatUtils.mean(rupWeightsForPathway);
						if (pathwayWeightGamma != 1d)
							pathwayWeight = Math.pow(pathwayWeight, pathwayWeightGamma);
						sumPathwayWeights += pathwayWeight;
						
						parentPathways.add(parents);
						pathwayWeights.add(pathwayWeight);
						pathwayRups.add(Ints.toArray(rupsForPathway));
						pathwayRupWeights.add(rupWeightsForPathway);
					}
					Preconditions.checkState(sumPathwayWeights > 0d);
					
					// normalize and determine aggregate usage weights for each parent
					Map<Integer, Double> parentWeights = new HashMap<>();
					for (int i=0; i<parentPathways.size(); i++) {
						double relWeight = pathwayWeights.get(i)/sumPathwayWeights;
						pathwayWeights.set(i, relWeight);
						// normalize each rupture weight by the pathway's relative weight
						double[] rupWeightsForPathway = pathwayRupWeights.get(i);
						if (relWeight == 0d) {
							for (int r=0; r<rupWeightsForPathway.length; r++)
								rupWeightsForPathway[r] = 0d;
						} else {
							double origRupSum = StatUtils.sum(rupWeightsForPathway);
							Preconditions.checkState(origRupSum > 0d);
							double rupWeightScalar = relWeight/origRupSum;
							for (int r=0; r<rupWeightsForPathway.length; r++)
								rupWeightsForPathway[r] *= rupWeightScalar;
						}
						for (Integer parent : parentPathways.get(i)) {
							if (parentWeights.containsKey(parent))
								parentWeights.put(parent, parentWeights.get(parent)+relWeight);
							else
								parentWeights.put(parent, relWeight);
						}
					}
					
					myPathways.add(new SectMagRupturePathways(s, sectMinMagIndexes[s]+m, anySingleFault,
							parentPathways, pathwayWeights,
							pathwayRups, pathwayRupWeights, parentWeights));
					
					// now see if this magnitude bin used the same set of pathways
					if (curCommonPathways != null && !curCommonPathways.equals(uniqueParentCombRups.keySet())) {
						// new set of pathways
						
						// store the previous one
						Preconditions.checkState(curCommonPathwayMmax >= curCommonPathwayMmin);
						Preconditions.checkState(curCommonPathwayMmax >= 0);
						myCommonPathways.add(new SectCommonPathwaysMagRange(s, curCommonPathwayMmin, curCommonPathwayMmax,
								curCommonPathwayHasSingleFault, curCommonPathways));
						
						curCommonPathways = uniqueParentCombRups.keySet();
						curCommonPathwayMmin = magIndex;
						curCommonPathwayMmax = magIndex;
						curCommonPathwayHasSingleFault = anySingleFault;
					} else if (curCommonPathways == null) {
						// first bin
						Preconditions.checkState(m == 0);
						curCommonPathways = uniqueParentCombRups.keySet();
						curCommonPathwayMmin = magIndex;
						curCommonPathwayMmax = magIndex;
						curCommonPathwayHasSingleFault = anySingleFault;
					} else {
						// continuation
						curCommonPathwayMmax = magIndex;
						Preconditions.checkState(curCommonPathwayHasSingleFault == anySingleFault);
					}
				}
				
				// store the last set of common pathways
				Preconditions.checkState(curCommonPathways != null, "No pathways found for sect=%s", s);
				Preconditions.checkState(curCommonPathwayMmax >= curCommonPathwayMmin);
				Preconditions.checkState(curCommonPathwayMmax >= 0);
				myCommonPathways.add(new SectCommonPathwaysMagRange(s, curCommonPathwayMmin, curCommonPathwayMmax,
						curCommonPathwayHasSingleFault, curCommonPathways));
				
				Preconditions.checkState(myPathways.size() == myNumMags);
				
				if (!anyMultiFault)
					isolatedSections.set(s);
			}
			Preconditions.checkState(sectRupMagPathways.size() == numSections);
			Preconditions.checkState(sectCommonPathways.size() == numSections);
			// determine column indexes of the A matrix, one for each section-pathway combination
			sectColIndexes = new int[numSections];
			numCols = 0;
			for (int s=0; s<numSections; s++) {
				List<SectCommonPathwaysMagRange> pathways = sectCommonPathways.get(s);
				Preconditions.checkNotNull(pathways);
				Preconditions.checkState(!pathways.isEmpty());
				sectColIndexes[s] = numCols;
				if (bundleAllSingleFaultPathways) {
					int numSingleFault = 0;
					int numMultiFault = 0;
					for (SectCommonPathwaysMagRange pathway : pathways) {
						if (pathway.hasSingleFault)
							numSingleFault++;
						else
							numMultiFault++;
					}
					Preconditions.checkState(numSingleFault >= 1);
					numCols += 1 + numMultiFault; // 1 for single fault, + each multi fault
				} else {
					numCols += pathways.size();
				}
			}
		}
		
		public int getNumIsolatedSections() {
			return isolatedSections.cardinality();
		}
		
		public List<Integer> getIsolatedSections() {
			if (isolatedSections.cardinality() == 0)
				return null;
			List<Integer> isolatedList = new ArrayList<>(isolatedSections.cardinality());
			for (int s = isolatedSections.nextSetBit(0); s >= 0; s = isolatedSections.nextSetBit(s+1))
				isolatedList.add(s);
			return isolatedList;
		}
		
		public int getSectPathwayColumn(int sectIndex, int pathwayIndex) {
			List<SectCommonPathwaysMagRange> pathways = getSectCommonPathways(sectIndex);
			Preconditions.checkState(pathwayIndex >= 0 && pathwayIndex < pathways.size());
			int index = sectColIndexes[sectIndex];
			if (!bundleAllSingleFaultPathways)
				return index+pathwayIndex;
			int singleFaultIndex = index;
			if (pathways.get(pathwayIndex).hasSingleFault())
				return singleFaultIndex;
			for (int p=0; p<=pathwayIndex; p++) {
				if (!pathways.get(p).hasSingleFault())
					// we're bundling all single-fault pathways into the same scale factor bin
					// only increment if we encounter a multi-fault-only bin
					index++;
			}
			return index;
		}
		
		public int getSectMagColumn(int sectIndex, int magIndex) {
			List<SectCommonPathwaysMagRange> pathways = getSectCommonPathways(sectIndex);
			for (int p=0; p<pathways.size(); p++) {
				SectCommonPathwaysMagRange pathway = pathways.get(p);
				if (magIndex >= pathway.minMagIndex && magIndex <= pathway.maxMagIndex)
					return getSectPathwayColumn(sectIndex, p);
			}
			throw new IllegalStateException("No pathway found for sectIndex="+sectIndex+" containing magIndex="+magIndex);
		}
		
		public double getSectOrigRate(int sectIndex, int magIndex) {
			return origSectSupraSeisMFDs.get(sectIndex).getY(magIndex);
		}
		
		public List<SectCommonPathwaysMagRange> getSectCommonPathways(int sectIndex) {
			return sectCommonPathways.get(sectIndex);
		}
		
		public int getNumColumns() {
			return numCols;
		}
		
		public SectMagRupturePathways getMagBinPathways(int sectIndex, int magIndex) {
			return sectRupMagPathways.get(sectIndex).get(magIndex-sectMinMagIndexes[sectIndex]);
		}

		public FaultSystemRupSet getRupSet() {
			return rupSet;
		}

		public double[] getTargetSectSupraMoRates() {
			return targetSectSupraMoRates;
		}

		public double[] getTargetSectSupraSlipRates() {
			return targetSectSupraSlipRates;
		}

		public double[] getSectSupraSlipRateStdDevs() {
			return sectSupraSlipRateStdDevs;
		}

		public EvenlyDiscretizedFunc getRefMFD() {
			return refMFD;
		}
		
		public BitSet getSectRupUtilizations(int sectIndex) {
			return sectRupUtilizations.get(sectIndex);
		}
		
		public List<Integer> getIncludedRupIndexes() {
			return includedRupIndexes;
		}
		
		public List<Integer> getSectIncludedRupIndexes(int sectIndex) {
			BitSet sectRups = getSectRupUtilizations(sectIndex);
			List<Integer> rups = new ArrayList<>(sectRups.cardinality());
			for (int r = sectRups.nextSetBit(0); r >= 0; r = sectRups.nextSetBit(r+1))
				rups.add(r);
			return rups;
		}

		public int[] getSectMinMagIndexes() {
			return sectMinMagIndexes;
		}

		public int[] getSectMaxMagIndexes() {
			return sectMaxMagIndexes;
		}
		
		public Set<Integer> getParentIDs() {
			return parentSectsMap.keySet();
		}
		
		public List<FaultSection> getSectsForParent(int parentID) {
			return parentSectsMap.get(parentID);
		}

		public List<IncrementalMagFreqDist> getOrigSectSupraSeisMFDs() {
			return origSectSupraSeisMFDs;
		}
		
		/**
		 * For the given section index and magnitude index, return the estimated fractional weight summed across all
		 * ruptures involving the given other section index
		 * @param sectIndex
		 * @param magIndex
		 * @param toSectIndex
		 * @return
		 */
		public double getOtherSectFractContributionToBin(int sectIndex, int magIndex, int toSectIndex) {
			SectMagRupturePathways sectPaths = getMagBinPathways(sectIndex, magIndex);
			if (sectPaths == null)
				return 0d;
			BitSet toRups = sectRupUtilizations.get(toSectIndex);
			int toParentID = rupSet.getFaultSectionData(toSectIndex).getParentSectionId();
			double weight = 0d;
			for (int i=0; i<sectPaths.parentPathways.size(); i++) {
				if (!sectPaths.parentPathways.get(i).contains(toParentID))
					continue;
				int[] rups = sectPaths.pathwayRups.get(i);
				double[] weights = sectPaths.pathwayRupWeights.get(i);
				for (int j=0; j<rups.length; j++)
					if (toRups.get(rups[j]))
						weight += weights[j];
			}
			return weight;
		}
		
		/**
		 * For the given section index and magnitude index, return the estimated fractional weight assigned to the
		 * given rupture. The sum of all ruptures for this section/magnitude index will be 1.
		 * 
		 * @param sectIndex
		 * @param magIndex
		 * @param rupIndex
		 * @return
		 */
		public double getRupFractContributionToBin(int sectIndex, int magIndex, int rupIndex) {
			SectMagRupturePathways sectPaths = getMagBinPathways(sectIndex, magIndex);
			if (sectPaths == null)
				return 0d;
			for (int i=0; i<sectPaths.parentPathways.size(); i++) {
				int[] rups = sectPaths.pathwayRups.get(i);
				double[] weights = sectPaths.pathwayRupWeights.get(i);
				for (int j=0; j<rups.length; j++)
					if (rups[j] == rupIndex)
						return weights[j];
			}
			return 0d;
		}
		
		public JumpProbabilityCalc getSegModel() {
			return segModel;
		}
	}
	
	private static record SectMagRupturePathways(int sectIndex, int magIndex, boolean hasSingleFault,
			List<Set<Integer>> parentPathways, List<Double> pathwayWeights,
			List<int[]> pathwayRups, List<double[]> pathwayRupWeights,
			Map<Integer, Double> parentWeights) {};
	
	private static record SectCommonPathwaysMagRange(int sectIndex, int minMagIndex, int maxMagIndex,
			boolean hasSingleFault, Set<Set<Integer>> parentPathways) {};
	
	/**
	 * This constraint ensures that each section's individual MFD sums up to match the target slip rate
	 */
	private static class MFD_SectSlipRateConstraint extends InversionConstraint {
		private RupSetCoruptureMFDStructure structure;

		public MFD_SectSlipRateConstraint(RupSetCoruptureMFDStructure structure,
				double weight, ConstraintWeightingType weightingType) {
			super("Section Slip Rate Constraint", "SectSlipRate", weight, false, weightingType);
			this.structure = structure;
		}

		@Override
		public int getNumRows() {
			return structure.getRupSet().getNumSections();
		}

		@Override
		public long encode(DoubleMatrix2D A, double[] d, int startRow) {
			FaultSystemRupSet rupSet = structure.getRupSet();
			
			int numSections = rupSet.getNumSections();
			Preconditions.checkState(A.columns() == structure.getNumColumns());
			
			double[] targetSectSupraSlipRates = structure.getTargetSectSupraSlipRates();
			double[] sectSupraSlipRateStdDevs = structure.getSectSupraSlipRateStdDevs();
			
			double[] weights = new double[numSections];
			for (int s=0; s<numSections; s++)
				weights[s] = this.weight;
			if (weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
				for (int s=0; s<numSections; s++)
					if (sectSupraSlipRateStdDevs[s] != 0d)
						weights[s] /= sectSupraSlipRateStdDevs[s];
			}
			
			AveSlipModule aveSlipModule = rupSet.requireModule(AveSlipModule.class);
			SlipAlongRuptureModel slipAlongModule = rupSet.requireModule(SlipAlongRuptureModel.class);
			
			EvenlyDiscretizedFunc refMFD = structure.getRefMFD();
			
			int[] sectMinMagIndexes = structure.getSectMinMagIndexes();
			int[] sectMaxMagIndexes = structure.getSectMaxMagIndexes();
			
			// figure out section slips
			double[][] sectMagSlipConsumptions = new double[numSections][];
			double[][] sectMagSlipConsumptionSumWeights = new double[numSections][];
			for (int s=0; s<numSections; s++) {
				int numMag = 1 + sectMaxMagIndexes[s] - sectMinMagIndexes[s];
				sectMagSlipConsumptions[s] = new double[numMag];
				sectMagSlipConsumptionSumWeights[s] = new double[numMag];
			}
			for (int rupIndex : structure.getIncludedRupIndexes()) {
				double[] slips = slipAlongModule.calcSlipOnSectionsForRup(rupSet, aveSlipModule, rupIndex);
				List<Integer> sects = rupSet.getSectionsIndicesForRup(rupIndex);
				Preconditions.checkState(sects.size() == slips.length);
				int magIndex = refMFD.getClosestXIndex(rupSet.getMagForRup(rupIndex));
				double rupArea = rupSet.getAreaForRup(rupIndex);
				for (int s=0; s<slips.length; s++) {
					int sectID = sects.get(s);
					int m = magIndex - sectMinMagIndexes[sectID];
					double weight = structure.getRupFractContributionToBin(sectID, magIndex, rupIndex);
					// we're comparing to nucleation MFDs, but slip rate scales with participation
					// this scalar fixes that
					double particToNuclScalar = rupArea/rupSet.getAreaForSection(sectID);
					sectMagSlipConsumptions[sectID][m] += slips[s]*particToNuclScalar*weight;
					sectMagSlipConsumptionSumWeights[sectID][m] += weight;
				}
			}
			// normalize
			for (int s=0; s<numSections; s++)
				for (int m=0; m<sectMagSlipConsumptions[s].length; m++)
					if (sectMagSlipConsumptionSumWeights[s][m] > 0)
						sectMagSlipConsumptions[s][m] /= (double)sectMagSlipConsumptionSumWeights[s][m];
		
			long numNonZeroElements = 0l;
			
			// A matrix component of slip-rate constraint
			for (int s=0; s<numSections; s++) {
				int row = startRow + s;
				List<SectCommonPathwaysMagRange> pathways = structure.getSectCommonPathways(s);
//				System.out.println(row+". Debug for slip rate on sect "+s+" ("+rupSet.getFaultSectionData(s).getName()
//						+") w/ slipRate="+(float)targetSectSupraSlipRates[s]);
				for (int p=0; p<pathways.size(); p++) {
					SectCommonPathwaysMagRange pathway = pathways.get(p);
					int col = structure.getSectPathwayColumn(s, p);
//					System.out.println("\tPathway "+p+", Mags=["+(float)refMFD.getX(pathway.minMagIndex)+","
//							+(float)refMFD.getX(pathway.maxMagIndex)+"; parents="+pathway.parentPathways);
					for (int magIndex=pathway.minMagIndex; magIndex<=pathway.maxMagIndex; magIndex++) {
						int m = magIndex - sectMinMagIndexes[s];
						if (sectMagSlipConsumptions[s][m] > 0) {
							double avgSlipConsumption = sectMagSlipConsumptions[s][m];
							double origRate = structure.getSectOrigRate(s, magIndex);
							double val = avgSlipConsumption * origRate;
//							System.out.println("\t\tM"+(float)refMFD.getX(magIndex)+"; avgConsump="
//									+(float)avgSlipConsumption+"\torigRate="+(float)origRate);
							
							if (weightingType == ConstraintWeightingType.NORMALIZED) {
								double target = targetSectSupraSlipRates[s];
								if (target != 0d) {
									// Note that constraints for sections w/ slip rate < 0.1 mm/yr is not normalized by slip rate
									// -- otherwise misfit will be huge (e.g., UCERF3 GEOBOUND model has 10e-13 slip rates that will
									// dominate misfit otherwise)
									if (target < 1e-4 || Double.isNaN(target))
										target = 1e-4;
									val /= target;
								}
							}
							if (!addA(A, row, col, val*weights[s]))
								numNonZeroElements++;
						}
					}
				}
			}
			
			// d vector component of slip-rate constraint
			for (int s=0; s<numSections; s++) {
				double target = targetSectSupraSlipRates[s];
				double val = target;
				if (weightingType == ConstraintWeightingType.NORMALIZED) {
					if (target == 0d)
						// minimize
						val = 0d;
					else if (target < 1E-4 || Double.isNaN(target))
						// For very small slip rates, do not normalize by slip rate
						//  (normalize by 0.0001 instead) so they don't dominate misfit
						val = targetSectSupraSlipRates[s]/1e-4;
					else
						val = 1d;
				}
				int row = startRow+s;
				d[row] = val*weights[s];
				if (Double.isNaN(d[row]) || d[row]<0)
					throw new IllegalStateException("d["+row+"]="+d[row]+" is NaN or 0!  target="+target);
			}
			return numNonZeroElements;
		}
		
	}
	
	private static void printA(DoubleMatrix2D A, ConstraintRange range) {
		for (int row=range.startRow; row<range.endRow; row++) {
			for (int col=0; col<A.columns(); col++) {
				if (col > 0)
					System.out.print(" ");
				System.out.print((float)A.get(row, col));
			}
			System.out.println();
		}
	}
	
	private static void printD(double[] d, ConstraintRange range) {
		for (int row=range.startRow; row<range.endRow; row++)
			System.out.println((float)d[row]);
	}
	
	private static void misfitsDebugFor1s(DoubleMatrix2D A, double[] d, ConstraintRange range) {
		double[] sol1 = new double[A.columns()];
		for (int i=0; i<sol1.length; i++)
			sol1[i] = 1d;
		misfitsDebug(A, d, sol1, range);
	}
	
	private static void misfitsDebug(DoubleMatrix2D A, double[] d, double[] sol, ConstraintRange range) {
		DenseDoubleMatrix1D sol_clone = new DenseDoubleMatrix1D(sol);
		
		DenseDoubleMatrix1D syn = new DenseDoubleMatrix1D(A.rows());
		A.zMult(sol_clone, syn);
		
		System.out.println("\tData\tSnthetic\tMisfit");
		MinMaxAveTracker valTrack = new MinMaxAveTracker();
		MinMaxAveTracker misfitTrack = new MinMaxAveTracker();
		for (int row=range.startRow; row<range.endRow; row++) {
			double val = syn.get(row);
			double misfit = val - d[row];
			if (range.inequality)
				misfit = Math.max(0d, misfit);
			valTrack.addValue(val);
			misfitTrack.addValue(misfit);
			System.out.println("\t"+(float)d[row]+"\t"+(float)val+"\t"+(float)misfit);
		}
		System.out.println("Value stats:\t"+valTrack);
		System.out.println("Misfit stats:\t"+misfitTrack);
	}
	
	/**
	 * This constraint ensures that the co-rupture rate with other sections doesn't break the rate budget in each
	 * multifault rupture MFD bin
	 */
	private static class SectCoruptureBudgetConstraint extends InversionConstraint {

		private RupSetCoruptureMFDStructure structure;
		
		private List<SubsectCoruptureSet> corupSects;
		
		/**
		 * if false, will skip constraints on single fault magnitude bins, assuming that the inversion can find what
		 * it needs on its own parent fault section
		 */
		private boolean applyWhenSingleFaultAvailable = false;
		/**
		 * if true, will make sure that the other side of the constraint has not only rate available, but rate
		 * allocated to this section. if false, will only check against the total rate budget on the other section
		 */
		private boolean applyDoubleSided = false;
		/**
		 * if false, will skip balancing within the same parent for both multifault and single fault bins.
		 * if true and !applyWhenSingleFaultAvailable, will only balance on the same parent in multifault bins
		 */
		private boolean applySameParent = true;

		public SectCoruptureBudgetConstraint(RupSetCoruptureMFDStructure structure,
				double weight, ConstraintWeightingType weightingType) {
			// true means inequality constraint
			super("Section Corupture Budget Constraint", "CorupBudget", weight, true, weightingType);
			this.structure = structure;
			
			FaultSystemRupSet rupSet = structure.getRupSet();
			int numSections = rupSet.getNumSections();
			
			int[] sectMinMagIndexes = structure.getSectMinMagIndexes();
			int[] sectMaxMagIndexes = structure.getSectMaxMagIndexes();
			
			corupSects = new ArrayList<>();
			for (int sectID1=0; sectID1<numSections; sectID1++) {
				FaultSection sect1 = rupSet.getFaultSectionData(sectID1);
				int parentID1 = sect1.getParentSectionId();
				for (int magIndex=sectMinMagIndexes[sectID1]; magIndex<=sectMaxMagIndexes[sectID1]; magIndex++) {
					SectMagRupturePathways pathways = structure.getMagBinPathways(sectID1, magIndex);
					if (pathways == null)
						// empty mag bin
						continue;
					
					for (int parentID2 : pathways.parentWeights().keySet()) {
						if (!applySameParent && parentID2 == parentID1)
							// don't worry about shared ruptures on the same section
							continue;
						for (FaultSection sect2 : structure.getSectsForParent(parentID2)) {
							int sectID2 = sect2.getSectionId();
							if (sectID2 <= sectID1)
								// we'll add it both ways right now, only calc the fractContribs once
								continue;
							double sect1_fractCorupWith2 = structure.getOtherSectFractContributionToBin(sectID1, magIndex, sectID2);
							Preconditions.checkState(sect1_fractCorupWith2 <= 1.000001);
							if (sect1_fractCorupWith2 > 0) {
								double sect2_fractCorupWith1 = structure.getOtherSectFractContributionToBin(sectID2, magIndex, sectID1);
								Preconditions.checkState(sect2_fractCorupWith1 > 0d);
								// need to do it both ways as it's an inequality constraint
								// forwards
								if (applyWhenSingleFaultAvailable || !pathways.hasSingleFault())
									corupSects.add(new SubsectCoruptureSet(magIndex, sectID1, sectID2, sect1_fractCorupWith2, sect2_fractCorupWith1));
								// reversed
								if (applyWhenSingleFaultAvailable || !structure.getMagBinPathways(sectID2, magIndex).hasSingleFault())
									corupSects.add(new SubsectCoruptureSet(magIndex, sectID2, sectID1, sect2_fractCorupWith1, sect1_fractCorupWith2));
							}
						}
					}
				}
			}
		}

		@Override
		public int getNumRows() {
			return corupSects.size();
		}

		@Override
		public long encode(DoubleMatrix2D A, double[] d, int startRow) {
			FaultSystemRupSet rupSet = structure.getRupSet();
			
			int numSections = rupSet.getNumSections();
			Preconditions.checkState(A.columns() == structure.getNumColumns());
			double[] targetSectSupraSlipRates = structure.getTargetSectSupraSlipRates();
			double[] sectSupraSlipRateStdDevs = structure.getSectSupraSlipRateStdDevs();
			
			double[] weights = new double[numSections];
			for (int s=0; s<numSections; s++)
				weights[s] = this.weight;
			if (weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
				for (int s=0; s<numSections; s++)
					if (sectSupraSlipRateStdDevs[s] != 0d)
						weights[s] /= sectSupraSlipRateStdDevs[s];
			}
			
			long numNonZeroElements = 0;
			
			for (int i=0; i<corupSects.size(); i++) {
				// unique row for each section, magnitude, and other parent that it ruptures with
				int row = startRow + i;
				SubsectCoruptureSet corups = corupSects.get(i);
				int magIndex = corups.magIndex;
				
				Preconditions.checkState(applySameParent || rupSet.getFaultSectionData(corups.sectID1).getParentSectionId()
						!= rupSet.getFaultSectionData(corups.sectID2).getParentSectionId());
				
				double rowWeight = this.weight;
				if (weightingType == ConstraintWeightingType.NORMALIZED) {
					// normalize by the original value for this sect/mag
					rowWeight /= structure.getSectOrigRate(corups.sectID1, magIndex);
				} else if (weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
					if (sectSupraSlipRateStdDevs[corups.sectID1] > 0d && targetSectSupraSlipRates[corups.sectID1] > 0d) {
						// apply fractional slip rate uncertainty
						rowWeight /= sectSupraSlipRateStdDevs[corups.sectID1]/targetSectSupraSlipRates[corups.sectID1];
					}
					// normalize by the original rate for this sect/mag
					rowWeight /= structure.getSectOrigRate(corups.sectID1, magIndex);
				}
				
				// data vector is always 0. we ensure that the sum across the row is <= 0 when satisfied
				d[row] = 0;
				// the positive side represents sectID1's rate for this magnitude that coruptures with sectID2
				int col1 = structure.getSectMagColumn(corups.sectID1, magIndex);
				double origRate1 = structure.getSectOrigRate(corups.sectID1, magIndex);
//				Preconditions.checkState(getA(A, row, fromCol) == 0d);
				setA(A, row, col1, rowWeight * origRate1 * corups.sect1_fractCorupWith2);
				numNonZeroElements++;
				
				// the negative side represents sectID2's total rate for this magnitude
				// if applyDoubleSided, that's aclso scaled by sectID2's allocated ruptures with sectID1 (stronger constraint)
				int col2 = structure.getSectMagColumn(corups.sectID2, magIndex);
				double origRate2 = structure.getSectOrigRate(corups.sectID2, magIndex);
//				Preconditions.checkState(getA(A, row, fromCol) == 0d);
				// option 1: make sure 
				if (applyDoubleSided)
					setA(A, row, col2, -rowWeight * origRate2 * corups.sect2_fractCorupWith1);
				else
					setA(A, row, col2, -rowWeight * origRate2);
				numNonZeroElements++;
			}
			
			return numNonZeroElements;
		}
		
		private static record SubsectCoruptureSet(int magIndex, int sectID1, int sectID2,
				double sect1_fractCorupWith2, double sect2_fractCorupWith1) {};
		
	}
	
	private static class DoubleAverager {
		private double sum = 0d;
		private int count = 0;
		
		public void add(double value) {
			sum += value;
			count++;
		}
		
		public double getAverage() {
			return sum/(double)count;
		}
	}
	
	private enum SegConstraintMethod {
		JUMP_SECT_SCALED_NUCLEATION,
		PARENT_SECT_SCALED_NUCLEATION,
		FULL_PARTICIPATION
	}
	
	/**
	 * Segmentation constraint (corup budget only uses seg for path weighting, so an explicit constraint is needed)
	 */
	public static class MFD_SegmentationConstraint extends InversionConstraint {
		
		// never let a weight exceed this value, happens if rupture probability or section rate estimate is exceedingly low 
		private static final double MAX_WEIGHT_SCALAR = 1e5;
		
		private final static boolean D = false;
		
		private transient RupSetCoruptureMFDStructure structure;
		private transient FaultSystemRupSet rupSet;
		private transient Map<UniqueDistJump, List<Integer>> jumpRupsMap;
		private transient Map<UniqueDistJump, Set<Integer>> jumpParentSectsMap;
		private transient int numRows;

		private SegConstraintMethod method;
		
		/**
		 * if true, will ignore rate on the positive side from ruptures using each jump if they lie in a mag bin that
		 * also has single fault ruptures 
		 */
		private boolean ignoreSingleFaultBins = false;
		
		public MFD_SegmentationConstraint(RupSetCoruptureMFDStructure structure, double weight, SegConstraintMethod method) {
			super("MFD Segmentation", "MFD-Seg", weight, true, ConstraintWeightingType.NORMALIZED);
			this.structure = structure;
			this.method = method;
			this.rupSet = structure.getRupSet();		}
		
		private synchronized void checkInitJumpRups() {
			if (jumpRupsMap == null) {
				jumpRupsMap = new HashMap<>();
				
				numRows = 0;
				if (method == SegConstraintMethod.PARENT_SECT_SCALED_NUCLEATION)
					jumpParentSectsMap = new HashMap<>();
				
				ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
				
				for (int r=0; r<cRups.size(); r++) {
					ClusterRupture rup = cRups.get(r);
					for (Jump jump : rup.getJumpsIterable()) {
						for (boolean forwards : new boolean[] {true,false}) {
							if (!forwards)
								jump = jump.reverse();
							UniqueDistJump udJump = new UniqueDistJump(jump);
							List<Integer> jumpRups = jumpRupsMap.get(udJump);
							if (jumpRups == null) {
								jumpRups = new ArrayList<>();
								jumpRupsMap.put(udJump, jumpRups);
								if (method == SegConstraintMethod.PARENT_SECT_SCALED_NUCLEATION)
									jumpParentSectsMap.put(udJump, new HashSet<>());
							}
							jumpRups.add(r);
							if (method == SegConstraintMethod.PARENT_SECT_SCALED_NUCLEATION) {
								Set<Integer> jumpParentSects = jumpParentSectsMap.get(udJump);
								int parentID = jump.fromSection.getParentSectionId();
								for (FaultSubsectionCluster cluster : rup.getClustersIterable())
									if (cluster.parentSectionID == parentID)
										for (FaultSection sect : cluster.subSects)
											jumpParentSects.add(sect.getSectionId());
							}
						}
					}
				}
				if (method == SegConstraintMethod.PARENT_SECT_SCALED_NUCLEATION) {
					for (UniqueDistJump jump : jumpRupsMap.keySet()) {
						Set<Integer> parents = jumpParentSectsMap.get(jump);
						Preconditions.checkState(!parents.isEmpty());
						numRows += parents.size();
					}
				} else {
					numRows = jumpRupsMap.size();
				}
			}
		}

		@Override
		public int getNumRows() {
			checkInitJumpRups();
			return numRows;
		}
		
		private List<Integer> getFilteredRupsUsingJump(UniqueDistJump jump, int sectIndex) {
			List<Integer> rups = jumpRupsMap.get(jump);
			if (ignoreSingleFaultBins) {
				EvenlyDiscretizedFunc refMFD = structure.getRefMFD();
				
				int minMag = structure.getSectMinMagIndexes()[sectIndex];
				int numMag = 1 + structure.getSectMaxMagIndexes()[sectIndex] - minMag;
				boolean[] excludeIndexes = new boolean[numMag];
				for (int m=0; m<numMag; m++) {
					SectMagRupturePathways pathway = structure.getMagBinPathways(sectIndex, minMag + m);
					excludeIndexes[m] = pathway != null && pathway.hasSingleFault;
				}
				BitSet sectUtilizations = structure.getSectRupUtilizations(sectIndex);
				
				rups = new ArrayList<>(rups);
				for (int i=rups.size(); --i>=0;) {
					int rupIndex = rups.get(i);
					int magIndex = refMFD.getClosestXIndex(rupSet.getMagForRup(rupIndex));
					if (!sectUtilizations.get(rupIndex) || excludeIndexes[magIndex-minMag])
						rups.remove(i);
				}
			}
			return rups;
		}

		@Override
		public long encode(DoubleMatrix2D A, double[] d, int startRow) {
			long count = 0l;
			
			Preconditions.checkState(A.columns() == structure.getNumColumns());
			
			int row = startRow;
			
			checkInitJumpRups();
			List<UniqueDistJump> allJumps = new ArrayList<>(jumpRupsMap.keySet());
			allJumps.sort(Jump.id_comparator); // sort for consistent row ordering
			
			ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
			JumpProbabilityCalc segModel = structure.getSegModel();
			
			EvenlyDiscretizedFunc refMFD = structure.getRefMFD();
			
			SectParticipationRateEstimator rateEst = new GRParticRateEstimator(
					rupSet, structure.origSectSupraSeisMFDs, structure.sectRupUtilizations);
			
			long rawAddCount = 0;
			
			for (UniqueDistJump jump : allJumps) {
				List<Integer> rupsUsingJump = jumpRupsMap.get(jump);
				Preconditions.checkNotNull(rupsUsingJump);
				Preconditions.checkState(!rupsUsingJump.isEmpty());
				
				MinMaxAveTracker probTrack = new MinMaxAveTracker();
				for (int r : rupsUsingJump) {
					ClusterRupture rup = cRups.get(r);
					RuptureTreeNavigator nav = rup.getTreeNavigator();
					Jump myJump = nav.getJump(jump.fromSection, jump.toSection);
					Preconditions.checkState(myJump.fromSection.getSectionId() == jump.fromSection.getSectionId());
					
					// see if either end needs to be reversed
					if (!myJump.fromCluster.endSects.contains(myJump.fromSection))
						myJump = new Jump(myJump.fromSection, myJump.fromCluster.reversed(),
								myJump.toSection, myJump.toCluster, myJump.distance);
					Preconditions.checkState(myJump.fromCluster.endSects.contains(myJump.fromSection));
					if (!myJump.toCluster.startSect.equals(myJump.toSection))
						myJump = new Jump(myJump.fromSection, myJump.fromCluster,
								myJump.toSection, myJump.toCluster.reversed(), myJump.distance);
					Preconditions.checkState(myJump.toCluster.startSect.equals(myJump.toSection));
					
					double prob = segModel.calcJumpProbability(rup, myJump, false);
//					if (probTrack.getNum() > 0)
//						Preconditions.checkState((float)prob == (float)probTrack.getAverage(),
//								"%s != %s for jump %s", prob, probTrack.getAverage(), jump);
					probTrack.addValue(prob);
				}
				
				double jumpCondProb = probTrack.getAverage();
				Preconditions.checkState(jumpCondProb >= 0 && jumpCondProb <= 1d);
				if (jumpCondProb == 0) {
					if (method == SegConstraintMethod.PARENT_SECT_SCALED_NUCLEATION)
						row += jumpParentSectsMap.get(jump).size();
					else
						row++;
					continue;
				}
				
				double maxWeight = this.weight*MAX_WEIGHT_SCALAR;
				
				double rateEstWeight = this.weight;
				// scale weight by that estimated total event rate for this section
				double estRate = rateEst.estimateSectParticRate(jump.fromSection.getSectionId());
				
				if (estRate > 0d)
					rateEstWeight /= estRate;
				else
					rateEstWeight = maxWeight;
				
				if (D) System.out.println("Building constraint for jump: "+jump+" with "+rupsUsingJump.size()
					+" rups, prob="+(float)jumpCondProb+" with origWeight="+(float)weight
					+", rateEstWeight="+(float)rateEstWeight);
				
				double effectiveWeight = rateEstWeight/jumpCondProb;
				if (effectiveWeight > maxWeight) {
					if (D) System.err.println("WARNING: capping weight at max="+maxWeight+", would have been "+effectiveWeight);
					rateEstWeight = maxWeight*jumpCondProb;
				}
				
				// the inversion (inequality) will ensure that the negative side is net larger
				
				// negative side: sum the total rate on this section
				int sectIndex = jump.fromSection.getSectionId();
				// scalar is negative so that this works in inequality mode
				double scalarSectBins = -rateEstWeight;
				Preconditions.checkState(Double.isFinite(scalarSectBins),
						"Bad scalarSectBins=%s for jump %s with jumpCondProb=%s and weight=%s",
						scalarSectBins, jump, jumpCondProb, rateEstWeight);
				
				// positive side: sum the total rate of ruptures using the jump
				// then we'll add the MFD bins using this jump on the positive side, but divide by the segmentation
				// rate to ensure that the rate using the jump does not exceed segRate*totalRate
				double scalarAllUsing = rateEstWeight/jumpCondProb;
				Preconditions.checkState(Double.isFinite(scalarAllUsing),
						"Bad scalarAllUsing=%s for jump %s with jumpCondProb=%s and weight=%s",
						scalarAllUsing, jump, jumpCondProb, rateEstWeight);
				
				switch (method) {
				case JUMP_SECT_SCALED_NUCLEATION: {
					for (boolean positive : new boolean[] {false, true}) {
						double scalar = positive ? scalarAllUsing: scalarSectBins;

						List<Integer> rups = positive ? getFilteredRupsUsingJump(jump, sectIndex) : structure.getSectIncludedRupIndexes(sectIndex);

						// will need to scale from nuclation to participation
						double sectArea = rupSet.getAreaForSection(sectIndex);

						for (int rupIndex : rups) {
							double rupArea = rupSet.getAreaForRup(rupIndex);
							int magIndex = refMFD.getClosestXIndex(rupSet.getMagForRup(rupIndex));

							// bin nuclRate = particRate * sectArea / avgRupArea
							// bin particRate = nuclRate * avgRupArea / sectArea
							double particScalar = rupArea / sectArea;
							int col = structure.getSectMagColumn(sectIndex, magIndex);
							double origRate = structure.getSectOrigRate(sectIndex, magIndex);
							double weight = structure.getRupFractContributionToBin(sectIndex, magIndex, rupIndex);
							if (!addA(A, row, col, particScalar*scalar*weight*origRate))
								count++;
							rawAddCount++;
						}
					}
					row++;
					break;
				}
				case PARENT_SECT_SCALED_NUCLEATION: {
					Set<Integer> sectsOnParent = jumpParentSectsMap.get(jump);
					List<Integer> sorted = new ArrayList<>(sectsOnParent);
					Collections.sort(sorted);
					for (int sectOnParentIndex : sorted) {
						// will need to scale from nuclation to participation
						double sectArea = rupSet.getAreaForSection(sectOnParentIndex);

						BitSet sectRups = structure.getSectRupUtilizations(sectOnParentIndex);

						for (boolean positive : new boolean[] {false, true}) {
							double scalar = positive ? scalarAllUsing: scalarSectBins;

							List<Integer> rups = positive ? getFilteredRupsUsingJump(jump, sectOnParentIndex) : structure.getSectIncludedRupIndexes(sectOnParentIndex);

							for (int rupIndex : rups) {
								double rupArea = rupSet.getAreaForRup(rupIndex);
								int magIndex = refMFD.getClosestXIndex(rupSet.getMagForRup(rupIndex));

								if (!sectRups.get(rupIndex))
									// can happen on the negative side; not all rups using the jump will make it to this section
									continue;

								// bin nuclRate = particRate * sectArea / avgRupArea
								// bin particRate = nuclRate * avgRupArea / sectArea
								double particScalar = rupArea / sectArea;
								int col = structure.getSectMagColumn(sectOnParentIndex, magIndex);
								double origRate = structure.getSectOrigRate(sectOnParentIndex, magIndex);
								double weight = structure.getRupFractContributionToBin(sectOnParentIndex, magIndex, rupIndex);
								if (!addA(A, row, col, particScalar*scalar*weight*origRate))
									count++;
								rawAddCount++;
							}
						}

						row++;
					}
					break;
				}
				case FULL_PARTICIPATION: {
					for (boolean positive : new boolean[] {false, true}) {
						double scalar = positive ? scalarAllUsing: scalarSectBins;

						List<Integer> rups = positive ? getFilteredRupsUsingJump(jump, sectIndex) : structure.getSectIncludedRupIndexes(sectIndex);

						for (int rupIndex : rups) {
							int magIndex = refMFD.getClosestXIndex(rupSet.getMagForRup(rupIndex));

							for (int rupSectIndex : rupSet.getSectionsIndicesForRup(rupIndex)) {
								int col = structure.getSectMagColumn(rupSectIndex, magIndex);
								double origRate = structure.getSectOrigRate(rupSectIndex, magIndex);
								double weight = structure.getRupFractContributionToBin(rupSectIndex, magIndex, rupIndex);
								if (!addA(A, row, col, scalar*weight*origRate))
									count++;
								rawAddCount++;
							}
						}
					}
					row++;
					break;
				}
				default:
					throw new IllegalStateException("Unsupported method: "+method);
				}
			}
			
			int rows = row-startRow;
			Preconditions.checkState(rows == numRows);
			long maxPossibleCount = rows * structure.getNumColumns();
			Preconditions.checkState(count <= maxPossibleCount,
					"Count is impossibly-large; have %s, max possible is %s x %s = %s; rawAddCount=%s",
					count, rows, structure.getNumColumns(), maxPossibleCount, rawAddCount);
			
			return count;
		}
	}
	
	public static class ScaleFactorLimitConstraint extends InversionConstraint {

		private RupSetCoruptureMFDStructure structure;
		private boolean singleFault;

		public ScaleFactorLimitConstraint(RupSetCoruptureMFDStructure structure, boolean singleFault, double weight) {
			super(singleFault ? "Single Fault F>=1" : "Multi Fault F<=1", singleFault ? "SingleF>=1" : "MultiF<=1",
					weight, true, ConstraintWeightingType.NORMALIZED);
			this.structure = structure;
			this.singleFault = singleFault;
		}

		@Override
		public int getNumRows() {
			int numSections = structure.getRupSet().getNumSections();
			int rows = 0;
			for (int s=0; s<numSections; s++) {
				for (SectCommonPathwaysMagRange pathway : structure.getSectCommonPathways(s)) {
					if (singleFault == pathway.hasSingleFault()) {
						rows++;
						if (singleFault && structure.bundleAllSingleFaultPathways)
							// single fault pathways share the same bin, only set it once
							break;
					}
				}
			}
			return rows;
		}

		@Override
		public long encode(DoubleMatrix2D A, double[] d, int startRow) {
			Preconditions.checkState(A.columns() == structure.getNumColumns());
			
			int row = startRow;
			long count = 0l;
			
			int numSections = structure.getRupSet().getNumSections();
			
			// if single fault, we're we're ensureing that the value does not go below 1
			double val;
			if (singleFault) {
				// ensure the inverted value does not go below 1
				val = -this.weight;
			} else {
				// ensure that the inverted value does not go above 1
				val = this.weight;
			}
			
			for (int sectIndex=0; sectIndex<numSections; sectIndex++) {
				List<SectCommonPathwaysMagRange> pathways = structure.getSectCommonPathways(sectIndex);
				for (int p=0; p<pathways.size(); p++) {
					SectCommonPathwaysMagRange pathway = pathways.get(p);
					if (singleFault == pathway.hasSingleFault()) {
						int col = structure.getSectPathwayColumn(sectIndex, p);
						setA(A, row, col, val);
						d[row] = val;
						count++;
						row++;
						
						if (singleFault && structure.bundleAllSingleFaultPathways)
							// single fault pathways share the same bin, only set it once
							break;
					}
				}
			}
			
			return count;
		}
	}
	
	public static class ScaleFactorOneConstraint extends InversionConstraint {

		private RupSetCoruptureMFDStructure structure;
		private double weightSingle;
		private double weightMulti;
		
		public ScaleFactorOneConstraint(RupSetCoruptureMFDStructure structure, double weight) {
			this(structure, weight, weight);
		}

		public ScaleFactorOneConstraint(RupSetCoruptureMFDStructure structure, double weightSingle, double weightMulti) {
			super("Scale Factor of 1", "F~1", 0.5*(weightSingle + weightMulti), false, ConstraintWeightingType.NORMALIZED);
			this.structure = structure;
			this.weightSingle = weightSingle;
			this.weightMulti = weightMulti;
		}

		@Override
		public int getNumRows() {
			return structure.getNumColumns();
		}

		@Override
		public long encode(DoubleMatrix2D A, double[] d, int startRow) {
			Preconditions.checkState(A.columns() == structure.getNumColumns());
			
			int row = startRow;
			long count = 0l;
			
			int numSections = structure.getRupSet().getNumSections();
			
			for (int sectIndex=0; sectIndex<numSections; sectIndex++) {
				List<SectCommonPathwaysMagRange> pathways = structure.getSectCommonPathways(sectIndex);
				int numSingle = 0;
				for (int p=0; p<pathways.size(); p++) {
					SectCommonPathwaysMagRange pathway = pathways.get(p);
					double weight;
					if (pathway.hasSingleFault()) {
						weight = weightSingle;
						if (numSingle > 0 && structure.bundleAllSingleFaultPathways)
							continue;
						numSingle++;
					} else {
						weight = weightMulti;
					}
					int col = structure.getSectPathwayColumn(sectIndex, p);
					setA(A, row, col, weight);
					d[row] = weight;
					count++;
					row++;
				}
			}
			
			return count;
		}
	}
	
	/**
	 * Ensure that scale factors are nearly-identical for identical pathways on sections of the same parent
	 */
	public static class ScaleFactorParentStability extends InversionConstraint {

		private RupSetCoruptureMFDStructure structure;
		private double weightSingle;
		private double weightMulti;
		
		private List<int[]> pairs;
		private List<Double> weights;
		
		public ScaleFactorParentStability(RupSetCoruptureMFDStructure structure, double weight) {
			this(structure, weight, weight);
		}

		public ScaleFactorParentStability(RupSetCoruptureMFDStructure structure, double weightSingle, double weightMulti) {
			super("Stable-F Along Parents", "ParentStability", 0.5*(weightSingle + weightMulti), false, ConstraintWeightingType.NORMALIZED);
			this.structure = structure;
			this.weightSingle = weightSingle;
			this.weightMulti = weightMulti;
		}
		
		private synchronized void checkInitPairs() {
			if (pairs == null) {
				List<int[]> pairs = new ArrayList<>();
				List<Double> weights = new ArrayList<>();
				
				List<Integer> parents = new ArrayList<>(structure.getParentIDs());
				Collections.sort(parents);
				for (int parentID : parents) {
					List<FaultSection> sects = structure.getSectsForParent(parentID);
					for (int i1=0; i1<sects.size()-1; i1++) {
						int s1 = sects.get(i1).getSectionId();
						List<SectCommonPathwaysMagRange> paths1 = structure.getSectCommonPathways(s1);
						for (int i2=i1+1; i2<sects.size(); i2++) {
							int s2 = sects.get(i2).getSectionId();
							List<SectCommonPathwaysMagRange> paths2 = structure.getSectCommonPathways(s2);
							double weightScale = 1d;
							// decrease the weight if the slip rates differ
							double slip1 = structure.targetSectSupraSlipRates[s1];
							double slip2 = structure.targetSectSupraSlipRates[s2];
							if (slip1 != slip2 && slip1 > 0 && slip2 > 0)
								weightScale = Math.min(slip1, slip2)/Math.max(slip1, slip2);
							int numSingle1 = 0;
							for (int p1=0; p1<paths1.size(); p1++) {
								SectCommonPathwaysMagRange path1 = paths1.get(p1);
								if (path1.hasSingleFault) {
									if (structure.bundleAllSingleFaultPathways && numSingle1 > 0)
										continue;
									numSingle1++;
								}
								int col1 = structure.getSectPathwayColumn(s1, p1);
								int numSingle2 = 0;
								for (int p2=0; p2<paths2.size(); p2++) {
									SectCommonPathwaysMagRange path2 = paths2.get(p2);
									if (path2.hasSingleFault) {
										if (structure.bundleAllSingleFaultPathways && numSingle2 > 0)
											continue;
										numSingle2++;
									}
									if (path1.minMagIndex == path2.minMagIndex && path1.maxMagIndex == path2.maxMagIndex
											&& path1.parentPathways.equals(path2.parentPathways)) {
										int col2 = structure.getSectPathwayColumn(s2, p2);
										double weight = weightScale * (path1.hasSingleFault ? weightSingle : weightMulti);
										if (weight > 0d) {
											pairs.add(new int[] {col1, col2});
											weights.add(weight);
										}
									}
								}
							}
						}
					}
				}

				this.weights = weights;
				this.pairs = pairs;
			}
		}

		@Override
		public int getNumRows() {
			checkInitPairs();
			return pairs.size();
		}

		@Override
		public long encode(DoubleMatrix2D A, double[] d, int startRow) {
			Preconditions.checkState(A.columns() == structure.getNumColumns());
			
			int row = startRow;
			long count = 0l;
			
			for (int i=0; i<pairs.size(); i++) {
				int[] pair = pairs.get(i);
				double weight = weights.get(i);
				
				int col1 = pair[0];
				int col2 = pair[1];
				Preconditions.checkState(col1 != col2);
				
				setA(A, row, col1, -weight);
				setA(A, row, col2, weight);
				count += 2l;
				d[row] = 0d;
				
				row++;
			}
			
			return count;
		}
	}
	
	/**
	 * Constraint to try to minimize total rate for each section, to force it to use as much of the large magnitudes as
	 * it can, subject to the inequality and slip rate balancing constraints
	 */
	public static class SectRateMinimizationConstraint extends InversionConstraint {
		
		private transient RupSetCoruptureMFDStructure structure;
		
		private boolean minimizeToOrigRate = true;
		
		public SectRateMinimizationConstraint(RupSetCoruptureMFDStructure structure, double weight,
				ConstraintWeightingType weightingType, boolean minimizeToOrigRate) {
			super("Sect Rate Minimization",
					"SectMinimum", weight, false, weightingType);
			this.structure = structure;
			this.minimizeToOrigRate = minimizeToOrigRate;
		}

		@Override
		public int getNumRows() {
			return structure.getRupSet().getNumSections();
		}

		@Override
		public long encode(DoubleMatrix2D A, double[] d, int startRow) {
			long count = 0l;
			
			Preconditions.checkState(A.columns() == structure.getNumColumns());
			
			int row = startRow;

			int numSections = structure.getRupSet().getNumSections();
			int[] sectMinMagIndexes = structure.getSectMinMagIndexes();
			int[] sectMaxMagIndexes = structure.getSectMaxMagIndexes();
			
			List<IncrementalMagFreqDist> origMFDs = structure.getOrigSectSupraSeisMFDs();
			double[] weights = new double[numSections];
			for (int s=0; s<numSections; s++)
				weights[s] = this.weight;
			if (weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
				// weighted normalized by the (slip-rate-based) uncertainty in the total rate for each subsection
				// higher slip rate uncertainty -> lower weighting
				
				// weighting is equal for all subsections with the same fractional slip rate uncertainty, regardless
				// of their overal rate
				double[] sectSlipRates = structure.getTargetSectSupraSlipRates();
				double[] sectSlipRateStdDevs = structure.getSectSupraSlipRateStdDevs();
				for (int s=0; s<numSections; s++) {
					double origTotRate = origMFDs.get(s).calcSumOfY_Vals();
					if (sectSlipRates[s] != 0d && sectSlipRateStdDevs[s] != 0d) {
						double fractUncert = sectSlipRateStdDevs[s] / sectSlipRates[s];
						weights[s] /= origTotRate*fractUncert;
					}
				}
			} else if (weightingType == ConstraintWeightingType.UNNORMALIZED) {
				// normalized to the total original estimated rate for each subsection
				// i.e., higher rate subsections are minimized more
			} else if (weightingType == ConstraintWeightingType.NORMALIZED) {
				// weightecd normalized by the total rate for each subsection
				// weighting is then roughtly equal for all subsections
				for (int s=0; s<numSections; s++) {
					double origTotRate = origMFDs.get(s).calcSumOfY_Vals();
					weights[s] /= origTotRate;
				}
			}

			for (int sectIndex=0; sectIndex<numSections; sectIndex++) {
				double weight = weights[sectIndex];
				double origTotRate = 0d;
				for (int m=sectMinMagIndexes[sectIndex]; m<=sectMaxMagIndexes[sectIndex]; m++) {
					double origBinRate = structure.getSectOrigRate(sectIndex, m);
					origTotRate += origBinRate;
					if (origBinRate > 0d) {
						int col = structure.getSectMagColumn(sectIndex, m);
						if (!addA(A, row, col, weight*origBinRate))
							count++;
					}
				}
				d[row] = minimizeToOrigRate ? weight*origTotRate : 0d;
				row++;
			}
			
			return count;
		}
	}

}
