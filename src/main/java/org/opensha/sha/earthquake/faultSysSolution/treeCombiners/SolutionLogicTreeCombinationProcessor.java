package org.opensha.sha.earthquake.faultSysSolution.treeCombiners;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.treeCombiner.AbstractLogicTreeCombiner.LogicTreeCombinationContext;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.util.MergedSolutionCreator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.util.TectonicRegionType;
import org.opensha.commons.logicTree.treeCombiner.AbstractLogicTreeCombiner;
import org.opensha.commons.logicTree.treeCombiner.LogicTreeCombinationProcessor;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

public class SolutionLogicTreeCombinationProcessor implements LogicTreeCombinationProcessor {
	
	// inputs
	private SolutionLogicTree outerSLT;
	private SolutionLogicTree innerSLT;
	private File outputSLTFile;
	private boolean outerSuppliesSols;
	private boolean innerSuppliesSols;
	
	// data
	private LogicTreeCombinationContext treeCombination;
	private Map<LogicTreeBranch<?>, FaultSystemSolution> innerSols;
	private SolutionLogicTree.FileBuilder combTreeWriter;
	private boolean serializeGridded;
	private CompletableFuture<FaultSystemSolution> nextOuterSolLoadFuture = null;

	private LogicTreeBranch<?> prevOuter = null;
	private FaultSystemSolution prevOuterSol = null;
	private CompletableFuture<Void> writeSLTFuture = null;
	
	private Stopwatch writeWatch = Stopwatch.createUnstarted();
	private ExecutorService ioExec;

	public SolutionLogicTreeCombinationProcessor(SolutionLogicTree outerSLT, SolutionLogicTree innerSLT,
			File outputSLTFile, boolean outerSuppliesSols, boolean innerSuppliesSols, boolean serializeGridded) {
		super();
		this.outerSLT = outerSLT;
		this.innerSLT = innerSLT;
		this.outputSLTFile = outputSLTFile;
		this.outerSuppliesSols = outerSuppliesSols;
		this.innerSuppliesSols = innerSuppliesSols;
		this.serializeGridded = serializeGridded;
	}

	@Override
	public String getName() {
		return "Solution Logic Tree";
	}

	@Override
	public void init(LogicTreeCombinationContext treeCombination, ExecutorService exec, ExecutorService ioExec)
			throws IOException {
		this.treeCombination = treeCombination;
		this.ioExec = ioExec;
		
		combTreeWriter = new SolutionLogicTree.FileBuilder(outputSLTFile);
		Preconditions.checkState(treeCombination.averageAcrossLevels.isEmpty(), "Averaging not yet supported");
		if (treeCombination.numPairwiseSamples < 1 && innerSuppliesSols) {
			System.out.println("Pre-loading "+treeCombination.innerTree.size()+" inner solutions");
			innerSols = new HashMap<>();
			for (LogicTreeBranch<?> branch : treeCombination.innerTree)
				innerSols.put(branch, innerSLT.forBranch(branch));
		} else {
			innerSols = null;
		}
		
		combTreeWriter.setSerializeGridded(serializeGridded);
		combTreeWriter.setWeightProv(new BranchWeightProvider.OriginalWeights());
		
		if (outerSuppliesSols)
			nextOuterSolLoadFuture = solLoadFuture(outerSLT, treeCombination.combBranchesOuterPortion.get(0), ioExec);
	}

	@Override
	public void processBranch(LogicTreeBranch<?> combBranch, int combBranchIndex, double combBranchWeight,
			LogicTreeBranch<?> outerBranch, int outerBranchIndex, LogicTreeBranch<?> innerBranch, int innerBranchIndex)
			throws IOException {
		FaultSystemSolution outerSol;
		if (outerSuppliesSols) {
			if (prevOuter == null || !outerBranch.equals(prevOuter)) {
				outerSol = nextOuterSolLoadFuture.join();
				// start the next async read
				for (int m=combBranchIndex+1; m<treeCombination.combTree.size(); m++) {
					LogicTreeBranch<?> nextOuter = treeCombination.combBranchesOuterPortion.get(m);
					if (nextOuter != outerBranch) {
						nextOuterSolLoadFuture = solLoadFuture(outerSLT, nextOuter, ioExec);
						break;
					}
				}
			} else {
				// reuse
				outerSol = prevOuterSol;
			}
		} else {
			outerSol = null;
		}
		
		Preconditions.checkState(outerSuppliesSols || innerSuppliesSols);
		FaultSystemSolution innerSol = null;
//		doesInnerSupplySols() ? innerSLT.forBranch(innerBranch) : null;
		if (innerSuppliesSols) {
			if (innerSols == null)
				// need to load it
				innerSol = innerSLT.forBranch(innerBranch);
			else
				innerSol = innerSols.get(innerBranch);
		}
		FaultSystemSolution combSol;
		if (innerSol != null && outerSol != null)
			combSol = MergedSolutionCreator.merge(outerSol, innerSol);
		else if (innerSol != null)
			combSol = innerSol;
		else
			combSol = outerSol;
		
		if (writeSLTFuture != null) {
			writeWatch.start();
			writeSLTFuture.join();
			writeWatch.stop();
		}
		
		writeSLTFuture = CompletableFuture.runAsync(new Runnable() {
			@Override
			public void run() {
				try {
					
					combTreeWriter.solution(combSol, combBranch);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		}, ioExec);
		
		prevOuter = outerBranch;
		prevOuterSol = outerSol;
	}

	@Override
	public void close() throws IOException {
		System.out.println("Finalizing SLT file");
		writeWatch.start();
		writeSLTFuture.join();
		writeWatch.stop();
		combTreeWriter.build();
	}

	@Override
	public String getTimeBreakdownString(Stopwatch overallWatch) {
		return "Blocking writes:\t"+AbstractLogicTreeCombiner.blockingTimePrint(writeWatch, overallWatch);
	}
	
	private static CompletableFuture<FaultSystemSolution> solLoadFuture(SolutionLogicTree slt, LogicTreeBranch<?> branch, ExecutorService ioExec) {
		return CompletableFuture.supplyAsync(new Supplier<FaultSystemSolution>() {

			@Override
			public FaultSystemSolution get() {
				try {
					return slt.forBranch(branch);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		}, ioExec);
	}

}
