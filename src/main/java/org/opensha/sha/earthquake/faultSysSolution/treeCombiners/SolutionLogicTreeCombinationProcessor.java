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
			combSol = combineSols(outerSol, innerSol);
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
	
	public static FaultSystemSolution combineSols(FaultSystemSolution innerSol, FaultSystemSolution outerSol) {
		return combineSols(innerSol, outerSol, false);
	}
	
	public static FaultSystemSolution combineSols(FaultSystemSolution innerSol, FaultSystemSolution outerSol, boolean clusterRups) {
		int totNumSects = 0;
		int totNumRups = 0;
		for (boolean outer : new boolean[] {false, true}) {
			FaultSystemSolution sol = outer ? outerSol : innerSol;
			FaultSystemRupSet rupSet = sol.getRupSet();
//			System.out.println("RupSet "+i+" has "+rupSet.getNumSections()+" sects, "+rupSet.getNumRuptures()+" rups");
			totNumSects += rupSet.getNumSections();
			totNumRups += rupSet.getNumRuptures();
		}
//		System.out.println("Total: "+totNumSects+" sects, "+totNumRups+" rups");
		
		List<FaultSection> mergedSects = new ArrayList<>(totNumSects);
		List<List<Integer>> sectionForRups = new ArrayList<>(totNumRups);
		double[] mags = new double[totNumRups];
		double[] rakes = new double[totNumRups];
		double[] rupAreas = new double[totNumRups];
		double[] rupLengths = new double[totNumRups];
		double[] rates = new double[totNumRups];
		TectonicRegionType[] trts = new TectonicRegionType[totNumRups];
		
		int sectIndex = 0;
		int rupIndex = 0;
		
		List<ClusterRupture> cRups = clusterRups ? new ArrayList<>() : null;
		
		Map<Integer, Integer> outerSectMappings = new HashMap<>();
		Map<Integer, Integer> innerSectMappings = new HashMap<>();
		Map<Integer, Integer> outerRupMappings = new HashMap<>();
		Map<Integer, Integer> innerRupMappings = new HashMap<>();
		
		for (boolean outer : new boolean[] {false, true}) {
			FaultSystemSolution sol = outer ? outerSol : innerSol;
			FaultSystemRupSet rupSet = sol.getRupSet();
			int[] sectMappings = new int[rupSet.getNumSections()];
//			System.out.println("Merging sol with "+rupSet.getNumSections()+" sects and "+rupSet.getNumRuptures()+" rups");
			for (int s=0; s<sectMappings.length; s++) {
				FaultSection sect = rupSet.getFaultSectionData(s);
				sect = sect.clone();
				sectMappings[s] = sectIndex;
				sect.setSectionId(sectIndex);
				if (outer)
					outerSectMappings.put(s, sectIndex);
				else
					innerSectMappings.put(s, sectIndex);
				mergedSects.add(sect);
				
				sectIndex++;
			}
			
			RupSetTectonicRegimes myTRTs = trts == null ? null : rupSet.getModule(RupSetTectonicRegimes.class);
			if (myTRTs == null)
				trts = null;
			
			ClusterRuptures myCrups = null;
			if (clusterRups)
				myCrups = rupSet.requireModule(ClusterRuptures.class);
			
			for (int r=0; r<rupSet.getNumRuptures(); r++) {
				List<Integer> prevSectIDs = rupSet.getSectionsIndicesForRup(r);
				List<Integer> newSectIDs = new ArrayList<>(prevSectIDs.size());
				for (int s : prevSectIDs)
					newSectIDs.add(sectMappings[s]);
				sectionForRups.add(newSectIDs);
				mags[rupIndex] = rupSet.getMagForRup(r);
				rakes[rupIndex] = rupSet.getAveRakeForRup(r);
				rupAreas[rupIndex] = rupSet.getAreaForRup(r);
				rupLengths[rupIndex] = rupSet.getLengthForRup(r);
				rates[rupIndex] = sol.getRateForRup(r);
				if (trts != null)
					trts[rupIndex] = myTRTs.get(r);
				
				if (clusterRups)
					cRups.add(myCrups.get(r));
				
				if (outer)
					outerRupMappings.put(r, rupIndex);
				else
					innerRupMappings.put(r, rupIndex);
				
				rupIndex++;
			}
		}
		
		FaultSystemRupSet mergedRupSet = new FaultSystemRupSet(mergedSects, sectionForRups, mags, rakes, rupAreas, rupLengths);
		if (clusterRups)
			mergedRupSet.addModule(ClusterRuptures.instance(mergedRupSet, cRups, false));
		if (trts != null)
			mergedRupSet.addModule(new RupSetTectonicRegimes(mergedRupSet, trts));
		mergedRupSet.addModule(new CombinedRupSetMappings(innerSectMappings, outerSectMappings, innerRupMappings, outerRupMappings));
		return new FaultSystemSolution(mergedRupSet, rates);
	}
	
	public static class CombinedRupSetMappings implements OpenSHA_Module {

		private Map<Integer, Integer> innerSectMappings;
		private Map<Integer, Integer> outerSectMappings;
		private Map<Integer, Integer> innerRupMappings;
		private Map<Integer, Integer> outerRupMappings;

		public CombinedRupSetMappings(Map<Integer, Integer> innerSectMappings, Map<Integer, Integer> outerSectMappings,
				Map<Integer, Integer> innerRupMappings, Map<Integer, Integer> outerRupMappings) {
			this.innerSectMappings = innerSectMappings;
			this.outerSectMappings = outerSectMappings;
			this.innerRupMappings = innerRupMappings;
			this.outerRupMappings = outerRupMappings;
		}
		
		public Map<Integer, Integer> getInnerSectMappings() {
			return Collections.unmodifiableMap(innerSectMappings);
		}
		
		public Map<Integer, Integer> getOuterSectMappings() {
			return Collections.unmodifiableMap(outerSectMappings);
		}
		
		public Map<Integer, Integer> getInnerRupMappings() {
			return Collections.unmodifiableMap(innerRupMappings);
		}
		
		public Map<Integer, Integer> getOuterRupMappings() {
			return Collections.unmodifiableMap(outerRupMappings);
		}
		
		public int getCombinedSectIDForInner(int origInnerSectID) {
			return innerSectMappings.get(origInnerSectID);
		}
		
		public int getCombinedSectIDForOuter(int origOuterSectID) {
			return outerSectMappings.get(origOuterSectID);
		}
		
		public int getCombinedRupIDForInner(int origInnerRupID) {
			return innerRupMappings.get(origInnerRupID);
		}
		
		public int getCombinedRupIDForOuter(int origOuterRupID) {
			return outerRupMappings.get(origOuterRupID);
		}

		@Override
		public String getName() {
			return "Combined Rup Set Mappings";
		}
		
	}

}
