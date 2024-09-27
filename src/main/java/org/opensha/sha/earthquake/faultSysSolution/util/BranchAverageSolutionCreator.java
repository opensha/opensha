package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.FaultUtils.AngleAverager;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchAverageableModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchAveragingOrder;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchModuleBuilder;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchParentSectParticMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchRegionalMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchSectBVals;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchSectNuclMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchSectParticMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.InfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.LogicTreeRateStatistics;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionSlipRates;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.math.DoubleMath;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

/**
 * Utility for building branch averaged solutions
 * 
 * @author kevin
 *
 */
public class BranchAverageSolutionCreator {
	
	private double totWeight = 0d; 
	private double[] avgRates = null;
	private double[] avgMags = null;
	private double[] avgAreas = null;
	private double[] avgLengths = null;
	private List<AngleAverager> avgRakes = null;
	private List<List<Integer>> sectIndices = null;
	private List<DiscretizedFunc> rupMFDs = null;
	
	private FaultSystemRupSet refRupSet = null;
	private FaultSectionBranchAverager sectAverager = null;
	
//	private List<? extends LogicTreeBranch<?>> branches = getLogicTree().getBranches();
	
	private LogicTreeBranch<LogicTreeNode> combBranch = null;
	
	private List<Double> weights = new ArrayList<>();

	private Map<LogicTreeNode, Integer> nodeCounts = new HashMap<>();
	private Map<LogicTreeNode, Double> nodeWeights = new HashMap<>();
	
	private boolean skipRupturesBelowSectMin = true;
	
	private boolean accumulatingSlipRates = true;
	
	private List<AveragingAccumulator<? extends BranchAverageableModule<?>>> rupSetAvgAccumulators;
	private List<AveragingAccumulator<? extends BranchAverageableModule<?>>> solAvgAccumulators;
	
	private List<Class<? extends OpenSHA_Module>> skipModules = new ArrayList<>();
	
	private BranchWeightProvider weightProv;
	
	private LogicTreeRateStatistics.Builder rateStatsBuilder;
	
	private List<BranchModuleBuilder<FaultSystemSolution, ?>> solBranchModuleBuilders;
	
	private ExecutorService exec;
	
	public BranchAverageSolutionCreator(BranchWeightProvider weightProv) {
		this.weightProv = weightProv;
	}
	
	public void setSkipRupturesBelowSectMin(boolean skipRupturesBelowSectMin) {
		this.skipRupturesBelowSectMin = skipRupturesBelowSectMin;
	}
	
	public void skipModule(Class<? extends BranchAverageableModule<?>> clazz) {
		skipModules.add(clazz);
		if (SolutionSlipRates.class.isAssignableFrom(clazz))
			accumulatingSlipRates = false;
	}

	public synchronized void addSolution(FaultSystemSolution sol, LogicTreeBranch<?> branch) {
		double weight = weightProv.getWeight(branch);
		Preconditions.checkState(weight > 0d, "Can't average in branch with weight=%s: %s", weight, branch);
		weights.add(weight);
		totWeight += weight;
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		ModSectMinMags modMinMags = rupSet.getModule(ModSectMinMags.class);
		
		if (accumulatingSlipRates && !sol.hasModule(SolutionSlipRates.class)) {
			if (rupSet.hasModule(AveSlipModule.class) && rupSet.hasModule(SlipAlongRuptureModel.class))
				// add a slip rate module so that actual branch-averaged slip rates are calculated
				// do this because averaged solutions & averaged slip modules won't result in the same solution
				// slip rates as averaging the solution slip rates themselves
				sol.addModule(SolutionSlipRates.calc(sol, rupSet.requireModule(AveSlipModule.class),
						rupSet.requireModule(SlipAlongRuptureModel.class)));
			else
				// don't bother trying to calculate any other slip rates, we don't have them for every branch
				accumulatingSlipRates = false;
		}
		
		if (avgRates == null) {
			// first time
			avgRates = new double[rupSet.getNumRuptures()];
			avgMags = new double[avgRates.length];
			avgAreas = new double[avgRates.length];
			avgLengths = new double[avgRates.length];
			avgRakes = new ArrayList<>();
			for (int r=0; r<rupSet.getNumRuptures(); r++)
				avgRakes.add(new AngleAverager());
			
			refRupSet = rupSet;
			
			sectAverager = new FaultSectionBranchAverager(rupSet.getFaultSectionDataList());
			
			// initialize accumulators
			rupSetAvgAccumulators = initAccumulators(rupSet);
			solAvgAccumulators = initAccumulators(sol);
			
			combBranch = (LogicTreeBranch<LogicTreeNode>)branch.copy();
			sectIndices = rupSet.getSectionIndicesForAllRups();
			rupMFDs = new ArrayList<>();
			for (int r=0; r<avgRates.length; r++)
				rupMFDs.add(new ArbitrarilyDiscretizedFunc());
			
			rateStatsBuilder = new LogicTreeRateStatistics.Builder();
			
			solBranchModuleBuilders = new ArrayList<>();
			solBranchModuleBuilders.add(new BranchAveragingOrder.Builder());
			solBranchModuleBuilders.add(new BranchRegionalMFDs.Builder());
			solBranchModuleBuilders.add(new BranchSectNuclMFDs.Builder());
			solBranchModuleBuilders.add(new BranchSectParticMFDs.Builder());
			solBranchModuleBuilders.add(new BranchParentSectParticMFDs.Builder());
			solBranchModuleBuilders.add(new BranchSectBVals.Builder());
		} else {
			Preconditions.checkState(refRupSet.isEquivalentTo(rupSet), "Rupture sets are not equivalent");
		}
		
		rateStatsBuilder.process(branch, sol.getRateForAllRups());
		
		// start work on modules and module builders in parallel, as they often take the longest
		List<Future<?>> futures = new ArrayList<>();
		if (exec == null) 
			exec = Executors.newFixedThreadPool(Integer.min(8, FaultSysTools.defaultNumThreads()));
		
		try {
			futures.addAll(processBuilders(solBranchModuleBuilders, sol, branch, weight));
			futures.addAll(processAccumulators(rupSetAvgAccumulators, rupSet, branch, weight));
			futures.addAll(processAccumulators(solAvgAccumulators, sol, branch, weight));
			
			for (int i=0; i<combBranch.size(); i++) {
				LogicTreeNode combVal = combBranch.getValue(i);
				LogicTreeNode branchVal = branch.getValue(i);
				if (combVal != null && !combVal.equals(branchVal))
					combBranch.clearValue(i);
				int prevCount = nodeCounts.containsKey(branchVal) ? nodeCounts.get(branchVal) : 0;
				nodeCounts.put(branchVal, prevCount+1);
				double prevWeight = nodeWeights.containsKey(branchVal) ? nodeWeights.get(branchVal) : 0d;
				nodeWeights.put(branchVal, prevWeight + weight);
			}
			
			for (int r=0; r<avgRates.length; r++) {
				avgRakes.get(r).add(rupSet.getAveRakeForRup(r), weight);
				double rate = sol.getRateForRup(r);
				Preconditions.checkState(rate >= 0d, "bad rate: %s", rate);
				if (rate == 0d)
					continue;
				double mag = rupSet.getMagForRup(r);
				
				if (skipRupturesBelowSectMin && modMinMags != null && modMinMags.isRupBelowSectMinMag(r))
					// skip
					continue;
				avgRates[r] += rate*weight;
				DiscretizedFunc rupMFD = rupMFDs.get(r);
				double y = rate*weight;
				if (rupMFD.hasX(mag))
					y += rupMFD.getY(mag);
				rupMFD.set(mag, y);
			}
			addWeighted(avgMags, rupSet.getMagForAllRups(), weight);
			addWeighted(avgAreas, rupSet.getAreaForAllRups(), weight);
			addWeighted(avgLengths, rupSet.getLengthForAllRups(), weight);
			
			sectAverager.addWeighted(rupSet.getFaultSectionDataList(), weight);
			
			// join all of the build futures
			for (Future<?> future : futures)
				future.get();
		} catch (RuntimeException | InterruptedException | ExecutionException e) {
			if (exec != null) {
				exec.shutdown();
				exec = null;
			}
			throw ExceptionUtils.asRuntimeException(e);
		}
		
	}
	
	private List<AveragingAccumulator<? extends BranchAverageableModule<?>>> initAccumulators(
			ModuleContainer<OpenSHA_Module> container) {
		List<AveragingAccumulator<? extends BranchAverageableModule<?>>> accumulators = new ArrayList<>();
		for (OpenSHA_Module module : container.getModulesAssignableTo(BranchAverageableModule.class, true, skipModules)) {
			Preconditions.checkState(module instanceof BranchAverageableModule<?>);
			System.out.println("Building branch-averaging accumulator for: "+module.getName());
			AveragingAccumulator<? extends BranchAverageableModule<?>> accumulator =
					((BranchAverageableModule<?>)module).averagingAccumulator();
			if (accumulator == null) {
				System.err.println("WARNING: accumulator is null for module "+module.getName()+", skipping averaging");
				continue;
			}
			accumulators.add(accumulator);
		}
		return accumulators;
	}
	
	private List<Future<?>> processAccumulators(List<AveragingAccumulator<? extends BranchAverageableModule<?>>> accumulators, 
			ModuleContainer<OpenSHA_Module> container, LogicTreeBranch<?> branch, double weight) {
		List<Runnable> runs = new ArrayList<>(accumulators.size());
		for (AveragingAccumulator<? extends BranchAverageableModule<?>> accumulator : accumulators)
			runs.add(new AccumulateRunnable(accumulators, accumulator, container, branch, weight));
		
		List<Future<?>> futures = new ArrayList<>(runs.size());
		
		for (Runnable run : runs)
			futures.add(exec.submit(run));
		
		return futures;
	}
	
	private class AccumulateRunnable implements Runnable {
		
		private List<AveragingAccumulator<? extends BranchAverageableModule<?>>> accumulators;
		private AveragingAccumulator<? extends BranchAverageableModule<?>> accumulator;
		private ModuleContainer<OpenSHA_Module> container;
		private LogicTreeBranch<?> branch;
		private double weight;

		public AccumulateRunnable(List<AveragingAccumulator<? extends BranchAverageableModule<?>>> accumulators,
				AveragingAccumulator<? extends BranchAverageableModule<?>> accumulator,
				ModuleContainer<OpenSHA_Module> container, LogicTreeBranch<?> branch, double weight) {
			this.accumulators = accumulators;
			this.accumulator = accumulator;
			this.container = container;
			this.branch = branch;
			this.weight = weight;
		}

		@Override
		public void run() {
			try {
				accumulator.processContainer(container, weight);
			} catch (Exception e) {
				synchronized (accumulators) {
//					e.printStackTrace();
					System.err.println("Error processing accumulator, will no longer average "+accumulator.getType().getName()
							+".\n\tFailed on branch: "+branch
							+"\n\tError message: "+e.getMessage());
					System.err.flush();
					accumulators.remove(accumulator);
					if (accumulatingSlipRates && SolutionSlipRates.class.isAssignableFrom(accumulator.getType()))
						// stop calculating slip rates
						accumulatingSlipRates = false;
				}
			}
		}
		
	}
	
	private static void buildAverageModules(List<AveragingAccumulator<? extends BranchAverageableModule<?>>> accumulators, 
			ModuleContainer<OpenSHA_Module> container) {
		for (AveragingAccumulator<? extends BranchAverageableModule<?>> accumulator : accumulators) {
			try {
				container.addModule(accumulator.getAverage());
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Error building average module of type "+accumulator.getType().getName());
				System.err.flush();
			}
		}
	}
	
	private <E extends ModuleContainer<OpenSHA_Module>> List<Future<?>> processBuilders(List<BranchModuleBuilder<E, ?>> builders, 
			E source, LogicTreeBranch<?> branch, double weight) {
		List<Runnable> runs = new ArrayList<>(builders.size());
		for (BranchModuleBuilder<E, ?> builder : builders)
			runs.add(new BuilderRunnable<>(builders, builder, source, branch, weight));
		
		List<Future<?>> futures = new ArrayList<>(runs.size());
		
		for (Runnable run : runs)
			futures.add(exec.submit(run));
		
		return futures;
	}
	
	private class BuilderRunnable<E extends ModuleContainer<OpenSHA_Module>> implements Runnable {
		
		private List<BranchModuleBuilder<E, ?>> builders;
		private BranchModuleBuilder<E, ?> builder;
		private E source;
		private LogicTreeBranch<?> branch;
		private double weight;

		public BuilderRunnable(List<BranchModuleBuilder<E, ?>> builders, BranchModuleBuilder<E, ?> builder, E source,
				LogicTreeBranch<?> branch, double weight) {
			this.builders = builders;
			this.builder = builder;
			this.source = source;
			this.branch = branch;
			this.weight = weight;
		}

		@Override
		public void run() {
			try {
				builder.process(source, branch, weight);
			} catch (Exception e) {
				synchronized (builders) {
//					e.printStackTrace();
					System.err.println("Error processing branch module builder, will no longer average "
							+builder.getClass().getName()
							+"\n\tError message: "+e.getMessage());
					System.err.flush();
					builders.remove(builder);
				}
			}
		}
		
	}
	
	private static <E extends ModuleContainer<OpenSHA_Module>> void buildBranchModules(List<BranchModuleBuilder<E, ?>> builders, 
			E container) {
		for (BranchModuleBuilder<?, ?> builder : builders) {
			try {
				container.addModule(builder.build());
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Error building branch module of type "+builder.getClass().getName());
				System.err.flush();
			}
		}
	}
	
	public synchronized FaultSystemSolution build() {
		Preconditions.checkState(!weights.isEmpty(), "No solutions added!");
		Preconditions.checkState(totWeight > 0, "Total weight is not positive: %s", totWeight);
		
		if (exec != null) {
			exec.shutdown();
			exec = null;
		}
		
		System.out.println("Common branches: "+combBranch);
//		if (!combBranch.hasValue(DeformationModels.class))
//			combBranch.setValue(DeformationModels.MEAN_UCERF3);
//		if (!combBranch.hasValue(ScalingRelationships.class))
//			combBranch.setValue(ScalingRelationships.MEAN_UCERF3);
//		if (!combBranch.hasValue(SlipAlongRuptureModels.class))
//			combBranch.setValue(SlipAlongRuptureModels.MEAN_UCERF3);
		
		// now scale by total weight
		System.out.println("Normalizing by total weight: "+totWeight);
		double[] rakes = new double[avgRates.length];
		for (int r=0; r<avgRates.length; r++) {
			avgRates[r] /= totWeight;
			avgMags[r] /= totWeight;
			avgAreas[r] /= totWeight;
			avgLengths[r] /= totWeight;
			if (avgRates[r] == 0d) {
				// clear the empty MFD
				int size = rupMFDs.get(r).size();
				Preconditions.checkState(size == 0, "rate=0 but mfd has %s values?", size);
				rupMFDs.set(r, null);
			} else {
				DiscretizedFunc rupMFD = rupMFDs.get(r);
				rupMFD.scale(1d/totWeight);
				double calcRate = rupMFD.calcSumOfY_Vals();
				Preconditions.checkState(DoubleMath.fuzzyEquals(calcRate, avgRates[r], 1e-12)
						|| DataUtils.getPercentDiff(calcRate,  avgRates[r]) < 1e-5,
						"Rupture MFD rate=%s, avgRate=%s", calcRate, avgRates[r]);
			}
			rakes[r] = FaultUtils.getInRakeRange(avgRakes.get(r).getAverage());
		}
		
		List<FaultSection> subSects = sectAverager.buildAverageSects();
		
//		FaultSystemRupSet avgRupSet = FaultSystemRupSet.builder(subSects, sectIndices).forU3Branch(combBranch).rupMags(avgMags).build();
//		// remove these as they're not correct for branch-averaged
//		avgRupSet.removeModuleInstances(InversionTargetMFDs.class);
//		avgRupSet.removeModuleInstances(SectSlipRates.class);
		FaultSystemRupSet avgRupSet = FaultSystemRupSet.builder(subSects, sectIndices).rupRakes(rakes)
				.rupAreas(avgAreas).rupLengths(avgLengths).rupMags(avgMags).build();
		
		// now build averaged modules
		buildAverageModules(rupSetAvgAccumulators, avgRupSet);
		
		int numNonNull = 0;
		boolean haveSlipAlong = false;
		for (int i=0; i<combBranch.size(); i++) {
			LogicTreeNode value = combBranch.getValue(i);
			if (value != null) {
				numNonNull++;
				if (value instanceof SlipAlongRuptureModel) {
					avgRupSet.addModule((SlipAlongRuptureModel)value);
					haveSlipAlong = true;
				} else if (value instanceof SlipAlongRuptureModelBranchNode) {
					avgRupSet.addModule(((SlipAlongRuptureModelBranchNode)value).getModel());
					haveSlipAlong = true;
				}
			}
		}
		// special cases for UCERF3 branches
		if (!haveSlipAlong && hasAllEqually(nodeCounts, SlipAlongRuptureModels.UNIFORM, SlipAlongRuptureModels.TAPERED)) {
			combBranch.setValue(SlipAlongRuptureModels.MEAN_UCERF3);
			avgRupSet.addModule(SlipAlongRuptureModels.MEAN_UCERF3.getModel());
			numNonNull++;
		}
		if (combBranch.getValue(ScalingRelationships.class) == null && hasAllEqually(nodeCounts, ScalingRelationships.ELLB_SQRT_LENGTH,
				ScalingRelationships.ELLSWORTH_B, ScalingRelationships.HANKS_BAKUN_08,
				ScalingRelationships.SHAW_2009_MOD, ScalingRelationships.SHAW_CONST_STRESS_DROP)) {
			combBranch.setValue(ScalingRelationships.MEAN_UCERF3);
			if (!avgRupSet.hasModule(AveSlipModule.class))
				avgRupSet.addModule(AveSlipModule.forModel(avgRupSet, ScalingRelationships.MEAN_UCERF3));
			numNonNull++;
		}
		if (combBranch.getValue(DeformationModels.class) == null && hasAllEqually(nodeCounts, DeformationModels.GEOLOGIC,
				DeformationModels.ABM, DeformationModels.NEOKINEMA, DeformationModels.ZENGBB)) {
			combBranch.setValue(DeformationModels.MEAN_UCERF3);
			numNonNull++;
		}
		
		if (numNonNull > 0) {
			avgRupSet.addModule(combBranch);
			System.out.println("Combined logic tree branch: "+combBranch);
		}
		
		FaultSystemSolution sol = new FaultSystemSolution(avgRupSet, avgRates);
		
		// now build averaged modules
		buildAverageModules(solAvgAccumulators, sol);
		
		sol.addModule(combBranch);
		sol.addModule(new RupMFDsModule(sol, rupMFDs.toArray(new DiscretizedFunc[0])));
		sol.addModule(rateStatsBuilder.build());
		
		buildBranchModules(solBranchModuleBuilders, sol);
		
		DecimalFormat weightDF = new DecimalFormat("0.###%");
		String info = "Branch Averaged Fault System Solution across "+weights.size()+" branches."
				+"\n\nThe utilized branches at each level are (counts and total relative weights in parenthesis):"
				+ "\n\n";
		for (int i=0; i<combBranch.size(); i++) {
			LogicTreeLevel<? extends LogicTreeNode> level = combBranch.getLevel(i);
			info += level.getName()+":\n";
			int numIncluded = 0;
			int numSkipped = 0;
			int lastSkippedCount = -1;
			int totalSkippedCount = 0;
			LogicTreeNode lastSkipped = null;
			for (LogicTreeNode choice : level.getNodes()) {
				Integer count = nodeCounts.get(choice);
				if (count != null) {
					if (numIncluded < 15) {
						double weight = nodeWeights.get(choice);
						info += "\t"+choice.getName()+" ("+count+"; "+weightDF.format(weight/totWeight)+")\n";
						numIncluded++;
					} else {
						lastSkipped = choice;
						lastSkippedCount = count;
						totalSkippedCount += count;
						numSkipped++;
					}
				}
			}
			if (lastSkipped != null) {
				if (numSkipped > 1)
					info += "\t...(skipping "+(numSkipped-1)+" branches used "+(totalSkippedCount-lastSkippedCount)+" times)...\n";
				double weight = nodeWeights.get(lastSkipped);
				info += "\t"+lastSkipped.getName()+" ("+lastSkippedCount+"; "+weightDF.format(weight/totWeight)+")\n";
			}
		}
		
		sol.addModule(new InfoModule(info));
		// reset these to make sure we don't reuse this as it's now invalid
		avgRates = null;
		weights = new ArrayList<>();
		totWeight = 0d;
		return sol;
	}
	
	private boolean hasAllEqually(Map<LogicTreeNode, Integer> nodeCounts, LogicTreeNode... nodes) {
		Integer commonCount = null;
		for (LogicTreeNode node : nodes) {
			Integer count = nodeCounts.get(node);
			if (count == null)
				return false;
			if (commonCount == null)
				commonCount = count;
			else if (commonCount.intValue() != count.intValue())
				return false;
		}
		return true;
	}
	
	private static void addWeighted(double[] running, double[] vals, double weight) {
		Preconditions.checkState(running.length == vals.length);
		for (int i=0; i<running.length; i++)
			running[i] += vals[i]*weight;
	}
	
	private static Options createOptions() {
		Options ops = new Options();
		
		// TODO add documentation
		
		ops.addRequiredOption("if", "input-file", true, "Input solution logic tree zip file.");
		ops.addRequiredOption("of", "output-file", true, "Output branch averaged solution file.");
		ops.addOption("rt", "restrict-tree", true, "Restrict the logic tree to the given value. Specify values by either "
				+ "their short name or file prefix. If such a name is ambiguous (applies to multiple branch levels), "
				+ "excplicitly set the level as <level-short-name>=<value>. Repeat this argument to specify multiple "
				+ "values (within a single level and/or across multiple levels).");
		ops.addOption("rw", "reweight", false, "Flag to use current branch weights rather than those when the "
				+ "simulation was originally run");
		
		return ops;
	}

	public static void main(String[] args) throws IOException {
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, BranchAverageSolutionCreator.class);
		
		File inputFile = new File(cmd.getOptionValue("input-file"));
		File outputFile = new File(cmd.getOptionValue("output-file"));
		
		SolutionLogicTree slt = SolutionLogicTree.load(inputFile);
		LogicTree<?> tree = slt.getLogicTree();
		
		BranchWeightProvider weightProv;
		if (cmd.hasOption("reweight"))
			weightProv = new BranchWeightProvider.CurrentWeights();
		else
			weightProv = tree.getWeightProvider();
		BranchAverageSolutionCreator ba = new BranchAverageSolutionCreator(weightProv);
		
//		List<LogicTreeNode> restrictTo = new ArrayList<>();
		List<List<LogicTreeNode>> restrictTos = null;
		
		String[] restrictNames = cmd.getOptionValues("restrict-tree");
		if (restrictNames != null && restrictNames.length > 0) {
			restrictTos = new ArrayList<>();
			for (int i=0; i<tree.getLevels().size(); i++)
				restrictTos.add(new ArrayList<>());
			for (String op : restrictNames) {
				String valName = op;
				String levelName = null;
				if (op.contains("=")) {
					levelName = op.substring(0, op.indexOf('=')).trim();
					valName = op.substring(op.indexOf('=')+1).trim();
					System.out.println("Looking for logic tree level with name '"+levelName+"', value of '"+valName+"'");
				} else {
					System.out.println("Looking for logic tree value of '"+valName+"'");
				}
				LogicTreeNode match = null;
				for (int i=0; i<tree.getLevels().size(); i++) {
					LogicTreeLevel<?> level = tree.getLevels().get(i);
					if (levelName != null && !level.getShortName().equals(levelName))
						continue;
					for (LogicTreeNode value : level.getNodes()) {
//						System.out.println("Testing value="+value+" with prefix="+value.getFilePrefix());
						if (value.getShortName().equals(valName) || value.getFilePrefix().equals(valName)) {
							if (match == null) {
								System.out.println("Found matching node: "+value);
								match = value;
								restrictTos.get(i).add(value);
							} else {
								throw new IllegalStateException("Logic tree value '"+valName+"' is ambiguous (matches "
										+ "multiple values across multiple levels). Specify the appropriate level as "
										+ "--restrict-tree <level-short-name>=<value>.");
							}
						}
					}
				}
				Preconditions.checkNotNull(match, "Didn't find matching logic tree value='%s' (level='%s')",
						valName, levelName);
			}
		}
		
		for (LogicTreeBranch<?> branch : slt.getLogicTree()) {
			if (restrictTos != null) {
				Preconditions.checkState(branch.size() == tree.getLevels().size());
				boolean hasAll = true;
				for (int i=0; i<branch.size(); i++) {
					List<LogicTreeNode> restrictTo = restrictTos.get(i);
					if (restrictTo.isEmpty())
						continue;
					boolean match = false;
					for (LogicTreeNode restrict : restrictTo)
						if (branch.hasValue(restrict))
							match = true;
					if (!match) {
						hasAll = false;
						break;
					}
				}
				if (!hasAll) {
					System.out.println("Skipping branch: "+branch);
					continue;
				}
			}
			FaultSystemSolution sol = slt.forBranch(branch);
			
			ba.addSolution(sol, branch);
		}
		
		System.out.println("Building final branch-averaged solution.");
		FaultSystemSolution baSol = ba.build();
		baSol.write(outputFile);
	}

}
