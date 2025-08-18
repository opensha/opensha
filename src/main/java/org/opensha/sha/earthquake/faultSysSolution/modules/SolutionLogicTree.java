package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.CSVReader;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.commons.util.modules.helpers.LargeCSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet.RuptureProperties;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.AnnealingProgress;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRupture;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider.AbstractPrecomputed;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.util.BranchAverageSolutionCreator;
import org.opensha.sha.earthquake.faultSysSolution.util.SolModuleStripper;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.imr.logicTree.ScalarIMR_ParamsLogicTreeNode;
import org.opensha.sha.imr.logicTree.ScalarIMRsLogicTreeNode;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.U3FaultSystemSolutionFetcher;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.U3InversionConfigFactory;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.logicTree.U3LogicTreeBranchNode;
import scratch.UCERF3.utils.LastEventData;

/**
 * Module that stores/loads fault system solutions for individual branches of a logic tree.
 * 
 * @author kevin
 *
 */
public class SolutionLogicTree extends AbstractLogicTreeModule {
	
	public static final String SUB_DIRECTORY_NAME = "solution_logic_tree";
	private static final boolean SERIALIZE_GRIDDED_DEFAULT = true;
	private boolean serializeGridded = SERIALIZE_GRIDDED_DEFAULT;
	private SolutionProcessor processor;
	
	protected ModuleArchive<OpenSHA_Module> archive;
	
	protected GridSourceProvider constantGridProv;
	protected boolean firstConstantGridProvTry = true;
	
	protected ArchiveInput directCopySource;
	
	/**
	 * Class that can be used to attach any necessary modules to an already loaded rupture set/solution
	 * for the given branch.
	 * <p>
	 * This is useful in order to add extra information not stored in the standard logic tree files. One
	 * use case is to infer a grid source provider based on logic tree branch choices and supraseismogenic
	 * rates, rather than serializing one.
	 * 
	 * @author kevin
	 *
	 */
	public interface SolutionProcessor {
		
		public FaultSystemRupSet processRupSet(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch);
		
		public FaultSystemSolution processSolution(FaultSystemSolution sol, LogicTreeBranch<?> branch);
	}
	
	private static U3LogicTreeBranch asU3Branch(LogicTreeBranch<?> branch) {
		if (branch instanceof U3LogicTreeBranch)
			return (U3LogicTreeBranch)branch;
		Preconditions.checkState(branch.size() >= U3LogicTreeBranch.getLogicTreeLevels().size());
		List<U3LogicTreeBranchNode<?>> vals = new ArrayList<>();
		for (LogicTreeNode val : branch) {
			Preconditions.checkState(val instanceof U3LogicTreeBranchNode);
			vals.add((U3LogicTreeBranchNode<?>) val);
		}
		return U3LogicTreeBranch.fromValues(vals);
	}
	
	public static class UCERF3 extends AbstractExternalFetcher {

		private U3FaultSystemSolutionFetcher oldFetcher;

		private UCERF3() {
			super(new U3InversionConfigFactory.UCERF3_SolutionProcessor(), null);
		}
		
		public UCERF3(LogicTree<?> logicTree) {
			super(new U3InversionConfigFactory.UCERF3_SolutionProcessor(), logicTree);
		}
		
		public UCERF3(U3FaultSystemSolutionFetcher oldFetcher) {
			super(new U3InversionConfigFactory.UCERF3_SolutionProcessor(),
					LogicTree.fromExisting(U3LogicTreeBranch.getLogicTreeLevels(), oldFetcher.getBranches()));
			this.oldFetcher = oldFetcher;
		}

		private Map<Integer, List<LastEventData>> lastEventData = null;
		
		@Override
		protected FaultSystemSolution loadExternalForBranch(LogicTreeBranch<?> branch) throws IOException {
			if (oldFetcher != null) {
				synchronized (this) {
					if (lastEventData == null)
						lastEventData = LastEventData.load();
				}
				FaultSystemSolution sol = oldFetcher.getSolution(asU3Branch(branch));
				LastEventData.populateSubSects(sol.getRupSet().getFaultSectionDataList(), lastEventData);
				return sol;
			}
			return null;
		}
	}
	
	public static class InMemory extends AbstractExternalFetcher {
		
		private Map<LogicTreeBranch<?>, Integer> branchIndexMap;
		private List<FaultSystemSolution> solutions;
		
		/**
		 * Single solution constructor
		 * 
		 * @param sol
		 * @param branch
		 */
		public InMemory(FaultSystemSolution sol, LogicTreeBranch<?> branch) {
			super(null, singleSolTree(branch));
			init(List.of(sol), getLogicTree());
		}
		
		private static LogicTree<?> singleSolTree(LogicTreeBranch<?> branch) {
			if (branch == null)
				return null;
			List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
			List<LogicTreeNode> nodes = new ArrayList<>();
			for (int i=0; i<branch.size(); i++) {
				levels.add(branch.getLevel(i));
				nodes.add(branch.getValue(i));
			}
			LogicTreeBranch<LogicTreeNode> modBranch = new LogicTreeBranch<>(levels, nodes);
			modBranch.setOrigBranchWeight(branch.getOrigBranchWeight());
			return LogicTree.fromExisting(levels, List.of(modBranch));
		}
		
		/**
		 * Multiple solution constructor. Solutions should be in tree-index order
		 * 
		 * @param solutions
		 * @param tree
		 */
		public InMemory(List<FaultSystemSolution> solutions, LogicTree<?> tree) {
			super(null, tree);
			init(solutions, tree);
		}
		
		private void init(List<FaultSystemSolution> solutions, LogicTree<?> tree) {
			this.solutions = solutions;
			branchIndexMap = new HashMap<>(solutions.size());
			if (tree == null) {
				// no tree
				Preconditions.checkState(solutions.size() == 1);
				branchIndexMap.put(null, 0);
			} else {
				Preconditions.checkState(solutions.size() == tree.size());
				for (int i=0; i<tree.size(); i++)
					branchIndexMap.put(tree.getBranch(i), i);
			}
		}

		@Override
		protected FaultSystemSolution loadExternalForBranch(LogicTreeBranch<?> branch) throws IOException {
			Integer index = branchIndexMap.get(branch);
			Preconditions.checkNotNull(index, "Unexpected branch: %s", branch);
			return solutions.get(index);
		}

		@Override
		public synchronized double[] loadRatesForBranch(LogicTreeBranch<?> branch) throws IOException {
			return loadExternalForBranch(branch).getRateForAllRups();
		}

		@Override
		public synchronized RuptureProperties loadPropsForBranch(LogicTreeBranch<?> branch) throws IOException {
			FaultSystemRupSet rupSet = loadExternalForBranch(branch).getRupSet();
			return new RuptureProperties(rupSet);
		}

		@Override
		public synchronized GridSourceProvider loadGridProvForBranch(LogicTreeBranch<?> branch) throws IOException {
			return loadExternalForBranch(branch).getGridSourceProvider();
		}
	}
	
	public static abstract class AbstractExternalFetcher extends SolutionLogicTree {

		protected AbstractExternalFetcher(SolutionProcessor processor, LogicTree<?> logicTree) {
			super(processor, null, null, logicTree);
		}
		
		protected abstract FaultSystemSolution loadExternalForBranch(LogicTreeBranch<?> branch) throws IOException;

		@Override
		public synchronized FaultSystemSolution forBranch(LogicTreeBranch<?> branch, boolean process)
				throws IOException {
			FaultSystemSolution external = loadExternalForBranch(branch);
			if (external != null) {
				if (process) {
					SolutionProcessor processor = getProcessor();
					if (processor != null) {
						FaultSystemRupSet origRupSet = external.getRupSet();
						FaultSystemRupSet rupSet = processor.processRupSet(origRupSet, branch);
						FaultSystemSolution sol;
						if (rupSet != origRupSet) {
							// rupSet is a new instance, create new solution that uses that instance
							sol = new FaultSystemSolution(rupSet, external.getRateForAllRups());
							for (OpenSHA_Module module : external.getModules()) {
								try {
									sol.addModule(module);
								} catch (Exception e) {
									System.err.println("WARNING: couldn't copy module to updated solution with "
											+ "processed rupture set: "+e.getMessage());
								}
							}
						} else {
							sol = external;
						}
						return processor.processSolution(sol, branch);
					}
				}
				return external;
			}
			return super.forBranch(branch, process);
		}
		
	}
	
	public static class SubsetSolutionLogicTree extends AbstractExternalFetcher {
		
		private SolutionLogicTree slt;

		public SubsetSolutionLogicTree(SolutionLogicTree slt, LogicTree<?> subsetTree) {
			super(slt.getProcessor(), subsetTree);
			this.slt = slt;
			this.setArchiveInput(slt.getArchiveInput());
		}

		@Override
		protected FaultSystemSolution loadExternalForBranch(LogicTreeBranch<?> branch) throws IOException {
			return slt.forBranch(branch, false);
		}

		@Override
		public synchronized double[] loadRatesForBranch(LogicTreeBranch<?> branch) throws IOException {
			return slt.loadRatesForBranch(branch);
		}

		@Override
		public synchronized RuptureProperties loadPropsForBranch(LogicTreeBranch<?> branch) throws IOException {
			return slt.loadPropsForBranch(branch);
		}

		@Override
		public synchronized GridSourceProvider loadGridProvForBranch(LogicTreeBranch<?> branch) throws IOException {
			return slt.loadGridProvForBranch(branch);
		}
	}
	
	public static class FileBuilder extends Builder {
		
		private static final String DNAME = ClassUtils.getClassNameWithoutPackage(FileBuilder.class);
		private static final boolean D = false;

		private SolutionProcessor processor;
		
		private ModuleArchive<OpenSHA_Module> archive;
		private BuildInfoModule buildInfo;

		private CompletableFuture<Void> startModuleWriteFuture = null;
		private CompletableFuture<Void> endModuleWriteFuture = null;
		private CompletableFuture<Void> endArchiveWriteFuture = null;
		private Thread archiveWriteThread;
		
		private ArchiveOutput output;
		private String entryPrefix;
		private SolutionLogicTree solTree;
		private HashSet<String> writtenFiles = new HashSet<>();
		
		private BranchWeightProvider weightProv;
		
		private List<LogicTreeBranch<LogicTreeNode>> branches = new ArrayList<>();
		private List<Map<String, String>> branchMappings = new ArrayList<>();
		private List<LogicTreeLevel<? extends LogicTreeNode>> levels = null;

		private boolean serializeGridded = SERIALIZE_GRIDDED_DEFAULT;
		private ArchiveInput directCopyGriddedFrom;
		private GridSourceProvider constantGridProv;
		
		private ArchiveInput directCopySource = null;
		
		public FileBuilder(File outputFile) throws IOException {
			this(null, outputFile);
		}
		
		public FileBuilder(SolutionProcessor processor, File outputFile) throws IOException {
			this(processor, ArchiveOutput.getDefaultOutput(outputFile));
		}
		
		public FileBuilder(ArchiveOutput output) throws IOException {
			this(null, output);
		}
		
		public FileBuilder(SolutionProcessor processor, ArchiveOutput output) throws IOException {
			this(processor, output, "");
		}

		public FileBuilder(SolutionProcessor processor, ArchiveOutput output, String entryPrefix) throws IOException {
			this.processor = processor;
			this.output = output;
			archive = new ModuleArchive<>();
		}
		
		/**
		 * By default, the written logic tree zip file will have a logic tree attached with branches in the order that
		 * they are passed in. If you want that ordering to match an existing tree, sort them with this method before 
		 * calling {@link #build()}.
		 * 
		 * @param tree
		 * @throws NullPointerException if branches exist that aren't in the supplied tree
		 */
		public synchronized void sortLogicTreeBranchesToMatchTree(LogicTree<?> tree) {
			Map<LogicTreeBranch<?>, Integer> branchIndexes = new HashMap<>(tree.size());
			for (int i=0; i<tree.size(); i++)
				branchIndexes.put(tree.getBranch(i), i);
			sortLogicTreeBranches(new Comparator<LogicTreeBranch<LogicTreeNode>>() {
				
				@Override
				public int compare(LogicTreeBranch<LogicTreeNode> o1, LogicTreeBranch<LogicTreeNode> o2) {
					int index1 = branchIndexes.get(o1);
					int index2 = branchIndexes.get(o2);
					return Integer.compare(index1, index2);
				}
			});
		}
		
		/**
		 * By default, the written logic tree zip file will have a logic tree attached with branches in the order that
		 * they are passed in. If you want custom ordering, sort them with this method before calling {@link #build()}.
		 * 
		 * @param tree
		 */
		public synchronized void sortLogicTreeBranches(Comparator<LogicTreeBranch<LogicTreeNode>> comparator) {
			// need to sort branches and mappings together
			List<ComparablePairing<LogicTreeBranch<LogicTreeNode>, Map<String, String>>> pairings = new ArrayList<>(branches.size());
			for (int i=0; i<branches.size(); i++)
				pairings.add(new ComparablePairing<LogicTreeBranch<LogicTreeNode>, Map<String, String>>(
						branches.get(i), branchMappings.get(i), comparator));
			Collections.sort(pairings);
			List<LogicTreeBranch<LogicTreeNode>> branches = new ArrayList<>(pairings.size());
			List<Map<String, String>> branchMappings = new ArrayList<>(pairings.size());
			for (ComparablePairing<LogicTreeBranch<LogicTreeNode>, Map<String, String>> pairing : pairings) {
				branches.add(pairing.getComparable());
				branchMappings.add(pairing.getData());
			}
			this.branches = branches;
			this.branchMappings = branchMappings;
		}
		
		private void debug(String message) {
			System.out.println(DNAME+"["+Thread.currentThread().getName()+"]: "+message);
		}
		
		public void setSerializeGridded(boolean serializeGridded) {
			this.serializeGridded = serializeGridded;
			if (serializeGridded)
				Preconditions.checkState(directCopyGriddedFrom == null,
				"Cannot set serialize gridded to true when directCopyGriddedFrom != null");
			if (solTree != null)
				solTree.setSerializeGridded(serializeGridded);
		}
		
		public void setConstantGridProv(GridSourceProvider constantGridProv) {
			this.constantGridProv = constantGridProv;
		}
		
		public void setDirectCopyGriddedFrom(ArchiveInput input) {
			if (input != null)
				serializeGridded = false;
			this.directCopyGriddedFrom = input;
		}
		public void setDirectCopySource(ArchiveInput input) {
			this.directCopySource = input;
		}
		
		public void setBuildInfo(BuildInfoModule buildInfo) {
			this.buildInfo = buildInfo;
		}
		
		private void initSolTree(LogicTreeBranch<?> branch) {
			if (D) debug("initSolTree");
			if (levels == null) {
				levels = new ArrayList<>();
				for (LogicTreeLevel<?> level : branch.getLevels())
					levels.add(level);
			}
			startModuleWriteFuture = new CompletableFuture<>();
			endModuleWriteFuture = new CompletableFuture<>();
			// first time
			if (D) debug("initSolTree: SolutionLogicTree constructor");
			this.solTree = new SolutionLogicTree() {

				@Override
				public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
					if (D) debug("initSolTree: SolutionLogicTree.writeToArchive start");
					FileBuilder.this.output = output;
					FileBuilder.this.entryPrefix = entryPrefix;
					// signal that we have started writing this module
					if (D) debug("initSolTree: startModuleWriteFuture.complete");
					startModuleWriteFuture.complete(null);
					if (D) debug("initSolTree: waiting on endModuleWriteFuture.get");
					// now wait until we're done writing it externally
					try {
						endModuleWriteFuture.get();
					} catch (Exception e) {
						if (D) debug("endModuleWriteFuture: exception: "+e.getMessage());
						e.printStackTrace();
						throw ExceptionUtils.asRuntimeException(e);
					}
					if (D) debug("initSolTree: got endModuleWriteFuture, exiting writeToArchive");
				}
				
			};
			solTree.setLogicTreeLevels(levels);
			solTree.setSerializeGridded(serializeGridded);
			solTree.setConstantGridProv(constantGridProv);
			solTree.setDirectCopySource(directCopySource);
			archive.addModule(solTree);
			if (buildInfo == null) {
				try {
					archive.addModule(BuildInfoModule.detect());
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				archive.addModule(buildInfo);
			}
			// begin asynchronous module archive write
			if (D) debug("initSolTree starting async write");
			endArchiveWriteFuture = new CompletableFuture<>();
			// use our own thread here rather than CompletableFuture.runAsync, which will sometimes run it in the
			// caller thread causing deadlock
			archiveWriteThread = new Thread("slt-file-builder-archive-write") {

				@Override
				public void run() {
					try {
						if (D) debug("initSolTree: endArchiveWriteFuture async running");
						archive.write(output);
						if (D) debug("initSolTree: endArchiveWriteFuture.complete");
						endArchiveWriteFuture.complete(null);
						if (D) debug("initSolTree: endArchiveWriteFuture async done");
					} catch (Exception e) {
						if (D) debug("initSolTree: exception: "+e.getMessage());
						e.printStackTrace();
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
				
			};
			archiveWriteThread.start();
			
			if (D) debug("initSolTree DONE");
		}
		
		private void waitUntilWriting() {
			try {
				if (D) debug("waitUntilWriting: waiting on startModuleWriteFuture.get");
				startModuleWriteFuture.get();
				if (D) debug("waitUntilWriting: got startModuleWriteFuture, exiting");
			} catch (Exception e) {
				if (D) debug("waitUntilWriting: exception: "+e.getMessage());
				e.printStackTrace();
				throw ExceptionUtils.asRuntimeException(e);
			}
			// we have started writing it!
			Preconditions.checkNotNull(output);
			Preconditions.checkNotNull(entryPrefix);
		}

		@SuppressWarnings("unchecked")
		@Override
		public synchronized void solution(FaultSystemSolution sol, LogicTreeBranch<?> branch) throws IOException {
			if (solTree == null)
				initSolTree(branch);
			if (D) debug("solution: for "+branch);
			List<? extends LogicTreeLevel<?>> myLevels = branch.getLevels();
			Preconditions.checkState(myLevels.size() == levels.size(),
					"Branch %s has a different number of levels than the first branch", branch);
			for (int i=0; i<myLevels.size(); i++)
				Preconditions.checkState(myLevels.get(i).equals(levels.get(i)),
						"Branch %s has a different level at position %s than the first branch", i, branch);
			branches.add((LogicTreeBranch<LogicTreeNode>) branch);
			// wait until we have started writing the module...
			waitUntilWriting();
			
			String outPrefix = solTree.buildPrefix(entryPrefix);

			if (D) System.out.println("Writing branch: "+branch);
			Map<String, String> mappings = new HashMap<>();
//			if (directCopyRatesFrom != null)
//				mappings.putAll(solTree.directCopyRatesToArchive(directCopyRatesFrom, output, outPrefix, branch, writtenFiles));
			mappings.putAll(solTree.writeBranchFilesToArchive(output, outPrefix, branch, writtenFiles, sol));

			if (directCopyGriddedFrom != null) {
				Preconditions.checkState(!serializeGridded);
				if (D) System.out.println("Direct copying gridded for: "+branch);
				mappings.putAll(solTree.directCopyGridProvToArchive(directCopyGriddedFrom, output, outPrefix, branch, writtenFiles));
			}
			branchMappings.add(mappings);
			if (D) debug("solution: DONE for "+branch);
		}
		
		public void setWeightProv(BranchWeightProvider weightProv) {
			this.weightProv = weightProv;
		}

		public synchronized void close() throws IOException {
			if (output != null) {
				// write logic tree
				if (D) debug("close: writing logic tree");
				LogicTree<?> tree = LogicTree.fromExisting(levels, branches);
				if (weightProv != null)
					tree.setWeightProvider(weightProv);
				solTree.writeLogicTreeToArchive(output, solTree.buildPrefix(entryPrefix), tree);
				
				if (processor != null) {
					if (D) debug("close: writing processor json");
					writeProcessorJSON(output, solTree.buildPrefix(entryPrefix), processor);
				}
				
				solTree.writeLogicTreeMappingsToArchive(output, solTree.buildPrefix(entryPrefix), tree, branchMappings);
				
				solTree.writeREADMEToArchive(output);
				
				// don't need to call output.close(), will be closed by archiveWriteThread
				entryPrefix = null;
				solTree = null;
				if (D) debug("close: endModuleWriteFuture.complete");
				endModuleWriteFuture.complete(null);
				
				// now wait until the archive is done writing
				try {
					if (D) debug("close: waiting on endArchiveWriteFuture.get");
					endArchiveWriteFuture.get();
					if (D) debug("close: got endArchiveWriteFuture");
					archiveWriteThread.join();
				} catch (Exception e) {
					if (D) debug("close: exception: "+e.getMessage());
					e.printStackTrace();
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		}

		@Override
		public SolutionLogicTree build() throws IOException {
			close();
			
			return new ModuleArchive<>(output.getCompletedInput(), SolutionLogicTree.class).requireModule(SolutionLogicTree.class);
		}
		
		public synchronized void copyDataFrom(ArchiveInput input, List<LogicTreeBranch<?>> branches) throws IOException {
			if (D) debug("copyDataFrom: for "+input.getName());
			if (solTree == null)
				initSolTree(branches.get(0));
			waitUntilWriting();
			
			String processorName = solTree.getSubDirectoryName()+"/"+PROCESSOR_FILE_NAME;
			String treeName = solTree.getSubDirectoryName()+"/"+PROCESSOR_FILE_NAME;
			String modulesName = ModuleArchive.MODULE_FILE_NAME;
			for (String name : input.getEntries()) {
				if (!writtenFiles.contains(name) && !name.equals(processorName)
						&& !name.equals(treeName) && !name.endsWith(modulesName)) {
					// need to copy this over
					System.out.println("Copying over file from previous archive: "+name);
					output.transferFrom(input, name);
					writtenFiles.add(name);
				}
			}
			branches.addAll(branches);
			if (D) debug("copyDataFrom: DONE for "+input.getName());
		}

		public synchronized void writeGridProvToArchive(GridSourceProvider prov, LogicTreeBranch<?> branch)
				throws IOException {
			if (D) debug("writeGridProvToArchive: for "+branch);
			waitUntilWriting();
			String prefix = solTree.buildPrefix(entryPrefix);
			if (D) debug("writeGridProvToArchive: writing for "+branch);
			solTree.writeGridProvToArchive(prov, output, prefix, branch, writtenFiles);
			if (D) debug("writeGridProvToArchive: DONE for "+branch);
		}
		
	}
	
	protected static abstract class Builder {
		
		public abstract void solution(FaultSystemSolution sol, LogicTreeBranch<?> branch) throws IOException;
		
		public abstract SolutionLogicTree build() throws IOException;
	}
	
	public static class ResultsDirReader extends AbstractExternalFetcher {
		
		private File resultsDir;

		public ResultsDirReader(File resultsDir, LogicTree<?> logicTree) {
			this(resultsDir, logicTree, null);
		}
		
		public ResultsDirReader(File resultsDir, LogicTree<?> logicTree, SolutionProcessor processor) {
			super(processor, logicTree);
			this.resultsDir = resultsDir;
		}
		
		private File prevSolFile;
		private FaultSystemSolution prevSol;
		
		private double[] prevRates;
		private RuptureProperties prevProps;
		private GridSourceProvider prevGridProv;
		
		private File getBranchSubDir(LogicTreeBranch<?> branch, List<LogicTreeNode> gridOnlyNodes,
				List<LogicTreeLevel<? extends LogicTreeNode>> gridOnlyLevels) {
			File subDir = branch.getBranchDirectory(resultsDir, false);
			if (!subDir.exists()) {
				// see if we have branch levels that don't affect the raw solution
				List<LogicTreeLevel<? extends LogicTreeNode>> levelsAffecting = new ArrayList<>();
				List<LogicTreeNode> nodesAffecting = new ArrayList<>();
				for (int i=0; i<branch.size(); i++) {
					LogicTreeNode node = branch.getValue(i);
					LogicTreeLevel<? extends LogicTreeNode> level = branch.getLevel(i);
					if (level.affects(FaultSystemSolution.RATES_FILE_NAME, true)) {
						levelsAffecting.add(level);
						nodesAffecting.add(node);
					} else if (level.affects(MFDGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME, true)
							|| level.affects(MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME, true)) {
						if (gridOnlyLevels != null)
							gridOnlyLevels.add(level);
						if (gridOnlyNodes != null)
							gridOnlyNodes.add(node);
					}
				}
				if (nodesAffecting.size() < branch.size()) {
					// that is the case, build new branch
					LogicTreeBranch<LogicTreeNode> rateBranch = new LogicTreeBranch<>(levelsAffecting, nodesAffecting);
					File testSubDir = new File(resultsDir, rateBranch.buildFileName());
					if (testSubDir.exists()) {
						System.out.println("Not all branches affect solution, reverting to branch: "+testSubDir.getName());
						subDir = testSubDir;
					}
				}
			}
			Preconditions.checkState(subDir.exists(), "Branch directory doesn't exist: %s", subDir.getAbsolutePath());
			return subDir;
		}

		@Override
		protected FaultSystemSolution loadExternalForBranch(LogicTreeBranch<?> branch) throws IOException {
			List<LogicTreeNode> gridOnlyNodes = new ArrayList<>();
			List<LogicTreeLevel<? extends LogicTreeNode>> gridOnlyLevels = new ArrayList<>();
			File subDir = getBranchSubDir(branch, gridOnlyNodes, gridOnlyLevels);
			File solFile = new File(subDir, "solution.zip");
			Preconditions.checkState(solFile.exists(), "Solution file doesn't exist: %s", solFile.getAbsolutePath());
			FaultSystemSolution sol = null;
			synchronized (this) {
				// check if it's the same as the previous one
				if (prevSol != null && solFile.equals(prevSolFile)) {
					sol = prevSol;
				}
			}
			if (sol == null) {
				sol = FaultSystemSolution.load(solFile);
				synchronized (this) {
					clearPrevSol();
					prevSol = sol;
					prevSolFile = solFile;
					prevRates = sol.getRateForAllRups();
					prevProps = new RuptureProperties(sol.getRupSet());
				}
			}
			if (!sol.hasAvailableModule(GridSourceProvider.class)) {
				// see if we have one available
				File gridProvsDir = new File(subDir, "grid_source_providers");
//				System.out.println("Looking for GridSourceProviders in "+gridProvsDir.getAbsolutePath()+" (exists? "+gridProvsDir.exists()+")");
				if (!gridProvsDir.exists()) {
					List<LogicTreeLevel<? extends LogicTreeNode>> gridLevels = new ArrayList<>();
					List<LogicTreeNode> gridNodes = new ArrayList<>();
					for (int i=0; i<branch.size(); i++) {
						LogicTreeLevel<?> level = branch.getLevel(i);
						LogicTreeNode node = branch.getValue(i);
						if (gridOnlyLevels.contains(level))
							continue;
						if (GridSourceProvider.affectedByLevel(level)
								|| node instanceof ScalarIMRsLogicTreeNode || node instanceof ScalarIMR_ParamsLogicTreeNode) {
							gridLevels.add(level);
							gridNodes.add(branch.getValue(i));
						}
					}
					if (gridLevels.size() < branch.size()) {
						LogicTreeBranch<LogicTreeNode> subBranch = new LogicTreeBranch<>(gridLevels, gridNodes);
						File subRunDir = subBranch.getBranchDirectory(resultsDir, false);
//						System.out.println("Testing subRunDir="+subRunDir.getAbsolutePath()+" (exists? "+subRunDir.exists()+")");
						if (subRunDir.exists()) {
							gridProvsDir = new File(subRunDir, "grid_source_providers");
//							System.out.println("Looking for GridSourceProviders in "+gridProvsDir.getAbsolutePath()+" (exists? "+gridProvsDir.exists()+")");
						}
					}
				}
				if (gridProvsDir.exists()) {
					File gridProvFile;
					if (gridOnlyLevels.isEmpty()) {
						gridProvFile = new File(gridProvsDir, "avg_grid_seis.zip");
					} else {
						LogicTreeBranch<LogicTreeNode> gridBranch = new LogicTreeBranch<>(gridOnlyLevels, gridOnlyNodes);
						gridProvFile = new File(gridProvsDir, gridBranch.buildFileName()+".zip");
						if (gridProvFile.exists()) {
							// want to avoid caching this grid provider as the next call could use a different one
							FaultSystemSolution copy = new FaultSystemSolution(sol.getRupSet(), sol.getRateForAllRups());
							for (OpenSHA_Module module : sol.getModules())
								copy.addModule(module);
							sol = copy;
						}
					}
					if (gridProvFile.exists()) {
						sol.addAvailableModule(new Callable<GridSourceProvider>() {

							@Override
							public GridSourceProvider call() throws Exception {
								ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(gridProvFile);
								return archive.requireModule(GridSourceProvider.class);
							}
						}, GridSourceProvider.class);
					}
				}
			}
			
			return sol;
		}
		
		private synchronized void clearPrevSol() {
			prevSolFile = null;
			prevSol = null;
			prevRates = null;
			prevProps = null;
			prevGridProv = null;
		}

		@Override
		public synchronized double[] loadRatesForBranch(LogicTreeBranch<?> branch) throws IOException {
			File subDir = getBranchSubDir(branch, null, null);
			File solFile = new File(subDir, "solution.zip");
			
			if (!solFile.equals(prevSolFile)) {
				clearPrevSol();
				prevSolFile = solFile;
			}
			if (prevRates != null)
				return prevRates;
			// load them directly
			ArchiveInput input = new ArchiveInput.ZipFileInput(solFile);
			String ratesFile = FaultSystemSolution.NESTING_PREFIX+FaultSystemSolution.RATES_FILE_NAME;
			System.out.println("\tLoading rate data from "+ratesFile);
			CSVFile<String> ratesCSV = CSV_BackedModule.loadFromArchive(input, null, ratesFile);
			double[] rates = FaultSystemSolution.loadRatesCSV(ratesCSV);
			prevRates = rates;
			input.close();
			return rates;
		}

		@Override
		public synchronized RuptureProperties loadPropsForBranch(LogicTreeBranch<?> branch) throws IOException {
			File subDir = getBranchSubDir(branch, null, null);
			File solFile = new File(subDir, "solution.zip");
			
			if (!solFile.equals(prevSolFile)) {
				clearPrevSol();
				prevSolFile = solFile;
			}
			if (prevProps != null)
				return prevProps;
			// load them directly
			ArchiveInput input = new ArchiveInput.ZipFileInput(solFile);
			String propsFile = FaultSystemRupSet.NESTING_PREFIX+FaultSystemRupSet.RUP_PROPS_FILE_NAME;
			System.out.println("\tLoading rupture properties from "+propsFile);
			CSVFile<String> rupPropsCSV = CSV_BackedModule.loadFromArchive(input, null, propsFile);
			RuptureProperties props = new RuptureProperties(rupPropsCSV);
			prevProps = props;
			input.close();
			return props;
		}

		@Override
		public synchronized GridSourceProvider loadGridProvForBranch(LogicTreeBranch<?> branch) throws IOException {
			List<LogicTreeNode> gridOnlyNodes = new ArrayList<>();
			List<LogicTreeLevel<? extends LogicTreeNode>> gridOnlyLevels = new ArrayList<>();
			File subDir = getBranchSubDir(branch, gridOnlyNodes, gridOnlyLevels);
			File solFile = new File(subDir, "solution.zip");
			if (gridOnlyLevels.isEmpty() && solFile.equals(prevSolFile)) {
				if (prevGridProv != null) {
					return prevGridProv;
				}
				if (prevSol != null && prevSol.hasAvailableModule(GridSourceProvider.class))
					return prevSol.getGridSourceProvider();
			} else {
				clearPrevSol();
				prevSolFile = solFile;
			}
			// see if we have one available
			File gridProvsDir = new File(subDir, "grid_source_providers");
			if (gridProvsDir.exists()) {
				File gridProvFile;
				if (gridOnlyLevels.isEmpty()) {
					gridProvFile = new File(gridProvsDir, "avg_grid_seis.zip");
				} else {
					LogicTreeBranch<LogicTreeNode> gridBranch = new LogicTreeBranch<>(gridOnlyLevels, gridOnlyNodes);
					gridProvFile = new File(gridProvsDir, gridBranch.buildFileName()+".zip");
				}
				if (gridProvFile.exists()) {
					ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(gridProvFile);
					prevGridProv = archive.requireModule(GridSourceProvider.class);
					return prevGridProv;
				}
			}
			return null;
		}
		
	}
	
	private SolutionLogicTree() {
		this(null, null);
	}
	
	protected SolutionLogicTree(SolutionProcessor processor, LogicTree<?> logicTree) {
		this(processor, null, null, logicTree);
	}

	protected SolutionLogicTree(SolutionProcessor processor, ArchiveInput input, String prefix, LogicTree<?> logicTree) {
		super(input, prefix, logicTree);
		this.processor = processor;
	}
	
	public SolutionProcessor getProcessor() {
		return processor;
	}
	
	public void setProcessor(SolutionProcessor processor) {
		this.processor = processor;
	}

	@Override
	public String getName() {
		return "Rupture Set Logic Tree";
	}

	@Override
	protected String getSubDirectoryName() {
		return SUB_DIRECTORY_NAME;
	}
	
	public void setSerializeGridded(boolean serializeGridded) {
		this.serializeGridded = serializeGridded;
	}
	
	public void setConstantGridProv(GridSourceProvider constantGridProv) {
		this.constantGridProv = constantGridProv;
	}
	
	public void setDirectCopySource(ArchiveInput input) {
		this.directCopySource = input;
	}

	@Override
	protected Map<String, String> writeBranchFilesToArchive(ArchiveOutput output, String prefix, LogicTreeBranch<?> branch,
			HashSet<String> writtenFiles) throws IOException {
		FaultSystemSolution sol = forBranch(branch);
		return writeBranchFilesToArchive(output, prefix, branch, writtenFiles, sol);
	}
	

	protected Map<String, String> writeBranchFilesToArchive(ArchiveOutput output, String prefix, LogicTreeBranch<?> branch,
			HashSet<String> writtenFiles, FaultSystemSolution sol) throws IOException {
		// could try to be fancy and copy files over without loading, but these things will be written out so rarely
		// (usually one and done) so it's not worth the added complexity
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		String entryPrefix = null; // prefixes will be encoded in the results of getBranchFileName(...) calls
		
		Map<String, String> mappings = new LinkedHashMap<>();
		
		String rsPrefix = FaultSystemRupSet.NESTING_PREFIX;
		String solPrefix = FaultSystemSolution.NESTING_PREFIX;
		
		ArchiveInput input = directCopySource;
		boolean inputIsSolArchive = false;
		if (input == null) {
			input = sol.getArchive().getInput();
			inputIsSolArchive = true;
		}
		
		String sectsFile = getRecordBranchFileName(branch, prefix, FaultSystemRupSet.SECTS_FILE_NAME, true, mappings);
		if (!writtenFiles.contains(sectsFile)) {
			if (!directCopy(input, output, rsPrefix+FaultSystemRupSet.SECTS_FILE_NAME, sectsFile, inputIsSolArchive)) {
				FileBackedModule.initEntry(output, entryPrefix, sectsFile);
				OutputStreamWriter writer = new OutputStreamWriter(output.getOutputStream());
				GeoJSONFaultReader.writeFaultSections(writer, rupSet.getFaultSectionDataList());
				writer.flush();
				output.closeEntry();
			}
			writtenFiles.add(sectsFile);
		}
		
		String indicesFile = getRecordBranchFileName(branch, prefix, FaultSystemRupSet.RUP_SECTS_FILE_NAME, true, mappings);
		if (!writtenFiles.contains(indicesFile)) {
			if (!directCopy(input, output, rsPrefix+FaultSystemRupSet.RUP_SECTS_FILE_NAME, indicesFile, inputIsSolArchive)) {
				FileBackedModule.initEntry(output, entryPrefix, indicesFile);
				CSVWriter entryWriter = new CSVWriter(output.getOutputStream(), false);
				FaultSystemRupSet.buildRupSectsCSV(rupSet,entryWriter);
				entryWriter.flush();
				output.closeEntry();
			}
			writtenFiles.add(indicesFile);
		}
		
		String propsFile = getRecordBranchFileName(branch, prefix, FaultSystemRupSet.RUP_PROPS_FILE_NAME, true, mappings);
		if (!writtenFiles.contains(propsFile)) {
			if (!directCopy(input, output, rsPrefix+FaultSystemRupSet.RUP_PROPS_FILE_NAME, propsFile, inputIsSolArchive)) {
				FileBackedModule.initEntry(output, entryPrefix, propsFile);
				CSVWriter entryWriter = new CSVWriter(output.getOutputStream(), true);
				new RuptureProperties(rupSet).buildCSV(entryWriter);
				entryWriter.flush();
				output.closeEntry();
			}
			writtenFiles.add(propsFile);
		}
		
		// will use rup-sect indicies file as mappings for a few levels
		List<? extends LogicTreeLevel<?>> rupSectLevels = getLevelsAffectingFile(
				FaultSystemRupSet.RUP_SECTS_FILE_NAME, true);
		if (rupSet.hasModule(RupSetTectonicRegimes.class)) {
			String trtsFile = getRecordBranchFileName(branch, prefix, RupSetTectonicRegimes.DATA_FILE_NAME, rupSectLevels, mappings);
			if (!writtenFiles.contains(trtsFile)) {
				if (!directCopy(input, output, rsPrefix+RupSetTectonicRegimes.DATA_FILE_NAME, trtsFile, inputIsSolArchive)) {
					RupSetTectonicRegimes trts = rupSet.requireModule(RupSetTectonicRegimes.class);
					CSV_BackedModule.writeToArchive(trts.getCSV(), output, entryPrefix, trtsFile);
				}
				writtenFiles.add(trtsFile);
			}
		}
		
		String ratesFile = getRecordBranchFileName(branch, prefix, FaultSystemSolution.RATES_FILE_NAME, true, mappings);
		if (!writtenFiles.contains(ratesFile)) {
			if (!directCopy(input, output, solPrefix+FaultSystemSolution.RATES_FILE_NAME, ratesFile, inputIsSolArchive)) {
				CSV_BackedModule.writeToArchive(FaultSystemSolution.buildRatesCSV(sol), output, entryPrefix, ratesFile);
			}
			writtenFiles.add(ratesFile);
		}
		
		if (sol.hasModule(RupMFDsModule.class)) {
			String mfdsFile = getRecordBranchFileName(branch, prefix, RupMFDsModule.FILE_NAME, true, mappings);
			if (!writtenFiles.contains(mfdsFile)) {
				if (!directCopy(input, output, solPrefix+RupMFDsModule.FILE_NAME, mfdsFile, inputIsSolArchive)) {
					RupMFDsModule mfds = sol.requireModule(RupMFDsModule.class);
					CSV_BackedModule.writeToArchive(mfds.getCSV(), output, entryPrefix, mfdsFile);
				}
				writtenFiles.add(mfdsFile);
			}
		}
		
		if (constantGridProv != null) {
			mappings.putAll(writeGridProvToArchive(constantGridProv, output, prefix, null, writtenFiles));
		} else if (serializeGridded && sol.hasModule(GridSourceProvider.class)) {
			GridSourceProvider prov = sol.getModule(GridSourceProvider.class);
			// TODO direct
			mappings.putAll(writeGridProvToArchive(prov, output, prefix, branch, writtenFiles));
		}
		
		InversionMisfitStats misfitStats = sol.getModule(InversionMisfitStats.class);
		if (misfitStats == null && sol.hasModule(InversionMisfits.class))
			misfitStats = sol.requireModule(InversionMisfits.class).getMisfitStats();
		
		if (misfitStats != null) {
			String statsFile = getRecordBranchFileName(branch, prefix,
					InversionMisfitStats.MISFIT_STATS_FILE_NAME, true, mappings);
			if (!writtenFiles.contains(statsFile)) {
				if (!directCopy(input, output, solPrefix+InversionMisfitStats.MISFIT_STATS_FILE_NAME, statsFile, inputIsSolArchive)) {
					CSV_BackedModule.writeToArchive(misfitStats.getCSV(), output, entryPrefix, statsFile);
				}
				writtenFiles.add(statsFile);
			}
		}
		
		AnnealingProgress progress = sol.getModule(AnnealingProgress.class);
		
		if (progress != null) {
			String progressFile = getRecordBranchFileName(branch, prefix,
					AnnealingProgress.PROGRESS_FILE_NAME, true, mappings);
			if (!writtenFiles.contains(progressFile)) {
				if (!directCopy(input, output, solPrefix+AnnealingProgress.PROGRESS_FILE_NAME, progressFile, inputIsSolArchive)) {
					CSV_BackedModule.writeToArchive(progress.getCSV(), output, entryPrefix, progressFile);
				}
				writtenFiles.add(progressFile);
			}
		}
		
		InversionMisfitProgress misfitProgress = sol.getModule(InversionMisfitProgress.class);
		
		if (misfitProgress != null) {
			String progressFile = getRecordBranchFileName(branch, prefix,
					InversionMisfitProgress.MISFIT_PROGRESS_FILE_NAME, true, mappings);
			if (!writtenFiles.contains(progressFile)) {
				if (!directCopy(input, output, solPrefix+InversionMisfitProgress.MISFIT_PROGRESS_FILE_NAME, progressFile, inputIsSolArchive)) {
					CSV_BackedModule.writeToArchive(misfitProgress.getCSV(), output, entryPrefix, progressFile);
				}
				writtenFiles.add(progressFile);
			}
		}
		
		// use rupture-sections file to figure out which things affect plausibility
		String plausibilityFile = getBranchFileName(branch, prefix,
				PlausibilityConfiguration.JSON_FILE_NAME, rupSectLevels);
		if (!writtenFiles.contains(plausibilityFile)) {
			PlausibilityConfiguration plausibility = rupSet.getModule(PlausibilityConfiguration.class);
			
			if (plausibility != null) {
				if (!writtenFiles.contains(plausibilityFile)) {
					if (!directCopy(input, output, rsPrefix+PlausibilityConfiguration.JSON_FILE_NAME, plausibilityFile, inputIsSolArchive)) {
						plausibility.writeToArchive(output, entryPrefix, plausibilityFile);
					}
					writtenFiles.add(plausibilityFile);
				}
				mappings.put(PlausibilityConfiguration.JSON_FILE_NAME, plausibilityFile);
			}
		}
		
		return mappings;
	}
	
	public static final String GRID_PROV_INSTANCE_FILE_NAME = "grid_provider_instance.json";

	public Map<String, String> writeGridProvToArchive(GridSourceProvider prov, ArchiveOutput output, String prefix,
			LogicTreeBranch<?> branch, HashSet<String> writtenFiles) throws IOException {
		return writeGridProvToArchive(prov, output, prefix, branch, writtenFiles, directCopySource, false);
	}

	protected Map<String, String> writeGridProvToArchive(GridSourceProvider prov, ArchiveOutput output, String prefix,
			LogicTreeBranch<?> branch, HashSet<String> writtenFiles, ArchiveInput input, boolean inputIsSolArchive) throws IOException {
		Map<String, String> mappings = new LinkedHashMap<>();
		String solPrefix = FaultSystemSolution.NESTING_PREFIX;
		if (prov instanceof MFDGridSourceProvider) {
			MFDGridSourceProvider.AbstractPrecomputed precomputed;
			if (prov instanceof MFDGridSourceProvider.AbstractPrecomputed)
				precomputed = (MFDGridSourceProvider.AbstractPrecomputed)prov;
			else
				precomputed = new MFDGridSourceProvider.Default((MFDGridSourceProvider)prov);
			
			Class<? extends ArchivableModule> loadingClass = precomputed.getLoadingClass();
			if (!MFDGridSourceProvider.AbstractPrecomputed.class.isAssignableFrom(loadingClass))
				loadingClass = MFDGridSourceProvider.Default.class;
			
			String gridRegFile = getRecordBranchFileName(branch, prefix,
					GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME, false, mappings);
			if (gridRegFile != null && !writtenFiles.contains(gridRegFile)) {
				if (!directCopy(input, output, solPrefix+GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME, gridRegFile, inputIsSolArchive)) {
					FileBackedModule.initEntry(output, null, gridRegFile);
					Feature regFeature = precomputed.getGriddedRegion().toFeature();
					OutputStreamWriter writer = new OutputStreamWriter(output.getOutputStream());
					Feature.write(regFeature, writer);
					writer.flush();
					output.closeEntry();
				}
				writtenFiles.add(gridRegFile);
			}

			String mechFile = getRecordBranchFileName(branch, prefix,
					MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME, false, mappings);
			if (mechFile != null && !writtenFiles.contains(mechFile)) {
				if (!directCopy(input, output, solPrefix+MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME, mechFile, inputIsSolArchive)) {
					CSV_BackedModule.writeToArchive(precomputed.buildWeightsCSV(), output, null, mechFile);
				}
				writtenFiles.add(mechFile);
			}
			String subSeisFile = getRecordBranchFileName(branch, prefix,
					MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME, true, mappings);
			if (subSeisFile != null && !writtenFiles.contains(subSeisFile)) {
				if (!directCopy(input, output, solPrefix+MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME, subSeisFile, inputIsSolArchive)) {
					CSVFile<String> csv = precomputed.buildSubSeisCSV();
					if (csv != null) {
						CSV_BackedModule.writeToArchive(csv, output, null, subSeisFile);
						writtenFiles.add(subSeisFile);
					}
				} else {
					// direct copied
					writtenFiles.add(subSeisFile);
				}
			}
			String unassociatedFile = getRecordBranchFileName(branch, prefix,
					MFDGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME, true, mappings);
			if (unassociatedFile != null && !writtenFiles.contains(unassociatedFile)) {
				if (!directCopy(input, output, solPrefix+MFDGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME, unassociatedFile, inputIsSolArchive)) {
					CSVFile<String> csv = precomputed.buildUnassociatedCSV();
					if (csv != null) {
						CSV_BackedModule.writeToArchive(csv, output, null, unassociatedFile);
						writtenFiles.add(unassociatedFile);
					}
				} else {
					// direct copied
					writtenFiles.add(unassociatedFile);
				}
			}
			
			// write the implementing class
			List<? extends LogicTreeLevel<?>> mappingLevels = getLevelsAffectingFile(MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME, true);
			String gridProvFile = getRecordBranchFileName(branch, prefix,
					GRID_PROV_INSTANCE_FILE_NAME, mappingLevels, mappings);
			if (!writtenFiles.contains(gridProvFile)) {
				FileBackedModule.initEntry(output, null, gridProvFile);
				writeGridSourceProvInstanceFile(output.getOutputStream(), loadingClass);
				output.closeEntry();
				writtenFiles.add(gridProvFile);
			}
		} else if (prov instanceof GridSourceList) {
			GridSourceList gridSources = (GridSourceList)prov;
			
			if (gridSources.getGriddedRegion() != null) {
				String gridRegFile = getRecordBranchFileName(branch, prefix,
						GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME, false, mappings);
				if (gridRegFile != null && !writtenFiles.contains(gridRegFile)) {
					if (!directCopy(input, output, solPrefix+GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME, gridRegFile, inputIsSolArchive)) {
						FileBackedModule.initEntry(output, null, gridRegFile);
						Feature regFeature = gridSources.getGriddedRegion().toFeature();
						OutputStreamWriter writer = new OutputStreamWriter(output.getOutputStream());
						Feature.write(regFeature, writer);
						writer.flush();
						output.closeEntry();
					}
					writtenFiles.add(gridRegFile);
				}
			}

			String locsFile = getRecordBranchFileName(branch, prefix,
					GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME, false, mappings);
			if (locsFile != null && !writtenFiles.contains(locsFile)) {
				if (!directCopy(input, output, solPrefix+GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME, locsFile, inputIsSolArchive)) {
					CSV_BackedModule.writeToArchive(gridSources.buildGridLocsCSV(), output, null, locsFile);
				}
				writtenFiles.add(locsFile);
			}
			String sourcesFile = getRecordBranchFileName(branch, prefix,
					GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME, true, mappings);
			if (sourcesFile != null && !writtenFiles.contains(sourcesFile)) {
				if (!directCopy(input, output, solPrefix+GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME, sourcesFile, inputIsSolArchive)) {
					gridSources.writeGridSourcesCSV(output, sourcesFile);
				}
				writtenFiles.add(sourcesFile);
			}
		} else {
			throw new UnsupportedOperationException("Don't yet support writing grid source provider of type: "+prov.getClass().getName());
		}
		
		
		return mappings;
	}
	
	protected boolean directCopy(ArchiveInput input, ArchiveOutput output, String origFileName,
			String outputFileName, boolean useOrigFileName) throws IOException {
		if (input == null)
			return false;
		String testFileName = origFileName;
		if (origFileName.contains("/"))
			testFileName = testFileName.substring(testFileName.lastIndexOf('/'));
		Preconditions.checkState(outputFileName.endsWith(testFileName),
				"directCopy name mismatch, probably a typo? origFileName='%s'; outputFileName='%s'",
				origFileName, outputFileName);
		String inputName = useOrigFileName ? origFileName : outputFileName;
		if (!input.hasEntry(inputName))
			return false;
		output.transferFrom(input, inputName, outputFileName);
		return true;
	}

//	protected Map<String, String> directCopyRatesToArchive(ModuleArchiveInput input, ModuleArchiveOutput output, String prefix,
//			LogicTreeBranch<?> branch, HashSet<String> writtenFiles) throws IOException {
//		Map<String, String> mappings = new LinkedHashMap<>();
//		
//		String ratesFile = getRecordBranchFileName(branch, prefix, FaultSystemSolution.RATES_FILE_NAME, true, mappings);
//		if (!writtenFiles.contains(ratesFile) && input.hasEntry(ratesFile)) {
////			System.out.println("Direct copying rates: "+ratesFile+" (from a "+ClassUtils.getClassNameWithoutPackage(input.getClass())
////				+" to a "+ClassUtils.getClassNameWithoutPackage(output.getClass())+")");
//			output.transferFrom(input, ratesFile);
//			writtenFiles.add(ratesFile);
//			mappings.put(FaultSystemSolution.RATES_FILE_NAME, ratesFile);
//		}
//		
//		return mappings;
//	}

	protected Map<String, String> directCopyGridProvToArchive(ArchiveInput input, ArchiveOutput output, String prefix,
			LogicTreeBranch<?> branch, HashSet<String> writtenFiles) throws IOException {
		
		List<String> inputFileNames = new ArrayList<>(6);
		List<Boolean> inputAffectedByDefaults = new ArrayList<>(6);
		
		inputFileNames.add(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME);
		inputAffectedByDefaults.add(false);
		inputFileNames.add(MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME);
		inputAffectedByDefaults.add(false);
		inputFileNames.add(MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME);
		inputAffectedByDefaults.add(true);
		inputFileNames.add(MFDGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME);
		inputAffectedByDefaults.add(true);
		inputFileNames.add(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME);
		inputAffectedByDefaults.add(false);
		inputFileNames.add(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME);
		inputAffectedByDefaults.add(true);
		
		Map<String, String> possibleFiles = new LinkedHashMap<>(inputFileNames.size());
		for (int i=0; i<inputFileNames.size(); i++) {
			String fileName = inputFileNames.get(i);
			String branchFileName = getBranchFileName(branch, prefix, fileName, inputAffectedByDefaults.get(i));
			if (branchFileName != null)
				possibleFiles.put(fileName, branchFileName);
		}
		
		Map<String, String> mappings = new LinkedHashMap<>(possibleFiles.size());
		
		boolean anyPresent = false;
		for (String fileName : possibleFiles.keySet()) {
			String branchFileName = possibleFiles.get(fileName);
			if (writtenFiles.contains(branchFileName)) {
				// already written
				anyPresent = true;
				mappings.put(fileName, branchFileName);
				continue;
			}
			if (input.hasEntry(branchFileName)) {
				output.transferFrom(input, branchFileName);
				writtenFiles.add(branchFileName);
				mappings.put(fileName, branchFileName);
				anyPresent = true;
			}
		}
		Preconditions.checkState(anyPresent, "Couldn't direct-copy a GridSourceProvider (no matching entries found in zip)");
		
		return mappings;
	}
	
	/**
	 * Writes a grid source provider instance JSON file to the given output stream, which is probably a zip outputstream,
	 * in which case it should have the entry already created.
	 * 
	 * @param out
	 * @param loadingClass
	 * @throws IOException
	 */
	public static void writeGridSourceProvInstanceFile(OutputStream out, Class<? extends ArchivableModule> loadingClass) throws IOException {
		BufferedWriter bWrite = new BufferedWriter(new OutputStreamWriter(out));
		@SuppressWarnings("resource")
		JsonWriter writer = new JsonWriter(bWrite);
		writer.beginObject().name("gridSourceProvider").value(loadingClass.getName()).endObject();
		writer.flush();
		bWrite.flush();
	}
	
	// cache files
	private List<? extends FaultSection> prevSubSects;
	private String prevSectsFile;
	
	private List<List<Integer>> prevRupIndices;
	private String prevIndicesFile;
	
	private RuptureProperties prevProps;
	private String prevPropsFile;
	
	private RupSetTectonicRegimes prevTRTs;
	private String prevTRTsFile;
	
	private GriddedRegion prevGridReg;
	private String prevGridRegFile;
	
	private LocationList prevGridLocs;
	private String prevGridLocsFile;
	
	private CSVFile<String> prevGridMechs;
	private String prevGridMechFile;
	
	private double[] prevRates;
	private String prevRatesFile;
	
	/**
	 * 
	 * @param branch
	 * @return solution rates only for the given branch
	 * @throws IOException
	 */
	public synchronized double[] loadRatesForBranch(LogicTreeBranch<?> branch) throws IOException {
		String ratesFile = getBranchFileName(branch, FaultSystemSolution.RATES_FILE_NAME, true);
		if (prevRates != null && ratesFile.equals(prevRatesFile)) {
			if (verbose) System.out.println("\tRe-using previous rupture rates");
			return prevRates;
		}
		if (verbose) System.out.println("\tLoading rate data from "+ratesFile);
		ArchiveInput input = getArchiveInput();
		CSVFile<String> ratesCSV = CSV_BackedModule.loadFromArchive(input, null, ratesFile);
		double[] rates = FaultSystemSolution.loadRatesCSV(ratesCSV);
		prevRates = rates;
		prevRatesFile = ratesFile;
		return rates;
	}
	
	public synchronized RuptureProperties loadPropsForBranch(LogicTreeBranch<?> branch) throws IOException {
		String propsFile = getBranchFileName(branch, FaultSystemRupSet.RUP_PROPS_FILE_NAME, true);
		RuptureProperties props;
		if (prevProps != null && propsFile.equals(prevPropsFile)) {
			if (verbose) System.out.println("\tRe-using previous rupture properties");
			props = prevProps;
		} else {
			if (verbose) System.out.println("\tLoading rupture properties from "+propsFile);
			ArchiveInput input = getArchiveInput();
			CSVFile<String> rupPropsCSV = CSV_BackedModule.loadFromArchive(input, null, propsFile);
			props = new RuptureProperties(rupPropsCSV);
			prevProps = props;
			prevPropsFile = propsFile;
		}
		return props;
	}
	
	public synchronized GridSourceProvider loadGridProvForBranch(LogicTreeBranch<?> branch) throws IOException {
		String gridRegFile = getBranchFileName(branch, GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME, false);
		String mechFile = getBranchFileName(branch, MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME, false);
		String locsFile = getBranchFileName(branch, GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME, false);

		ArchiveInput input = getArchiveInput();
		if (locsFile != null && input.hasEntry(locsFile)) {
			// GridSourceList
			GriddedRegion region;
			synchronized (SolutionLogicTree.this) {
				if (prevGridReg != null && gridRegFile.equals(prevGridRegFile)) {
					region = prevGridReg;
				} else {
					if (input.hasEntry(gridRegFile)) {
						BufferedInputStream regionIS = FileBackedModule.getInputStream(input, null, gridRegFile);
						InputStreamReader regionReader = new InputStreamReader(regionIS);
						Feature regFeature = Feature.read(regionReader);
						region = GriddedRegion.fromFeature(regFeature);
						prevGridReg = region;
						prevGridRegFile = gridRegFile;
					} else {
						region = null;
					}
				}
			}
			
			LocationList locs;
			if (region != null) {
				locs = region.getNodeList();
			} else {
				synchronized (SolutionLogicTree.this) {
					if (prevGridLocs != null && locsFile.equals(prevGridLocsFile)) {
						locs = prevGridLocs;
					} else {
						CSVFile<String> locsCSV = CSV_BackedModule.loadFromArchive(input, null, locsFile);
						locs = GridSourceList.loadGridLocsCSV(locsCSV, region);
						prevGridLocs = locs;
						prevGridLocsFile = locsFile;
					}
				}
			}

			String sourcesFile = getBranchFileName(branch, GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME, true);
			CSVReader rupSectsCSV = LargeCSV_BackedModule.loadFromArchive(input, null, sourcesFile);
			
			EnumMap<TectonicRegionType, List<List<GriddedRupture>>> trtRuptureLists = GridSourceList.loadGridSourcesCSV(rupSectsCSV, locs);
			if (region != null)
				return new GridSourceList.Precomputed(region, trtRuptureLists);
			return new GridSourceList.Precomputed(locs, trtRuptureLists);
		} else {
			// MFDGridSourceProvider
			if (gridRegFile == null || !input.hasEntry(gridRegFile) || mechFile == null || !input.hasEntry(mechFile))
				return null;
			GriddedRegion region;
			CSVFile<String> mechCSV;
			synchronized (SolutionLogicTree.this) {
				if (prevGridReg != null && gridRegFile.equals(prevGridRegFile)) {
					region = prevGridReg;
				} else {
					BufferedInputStream regionIS = FileBackedModule.getInputStream(input, null, gridRegFile);
					InputStreamReader regionReader = new InputStreamReader(regionIS);
					Feature regFeature = Feature.read(regionReader);
					region = GriddedRegion.fromFeature(regFeature);
					prevGridReg = region;
					prevGridRegFile = gridRegFile;
				}
				
				// load mechanisms
				if (prevGridMechs != null && mechFile.equals(prevGridMechFile)) {
					mechCSV = prevGridMechs;
				} else {
					mechCSV = CSV_BackedModule.loadFromArchive(input, null, mechFile);
					prevGridMechs = mechCSV;
					prevGridMechFile = mechFile;
				}
			}
			
			CSVFile<String> subSeisCSV = null;
			CSVFile<String> nodeUnassociatedCSV = null;
			
			String subSeisFile = getBranchFileName(branch, MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME, true);
			String nodeUnassociatedFile = getBranchFileName(branch, MFDGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME, true);
			if (subSeisFile != null && input.hasEntry(subSeisFile))
				subSeisCSV = MFDGridSourceProvider.AbstractPrecomputed.loadCSV(input, null, subSeisFile);
			if (nodeUnassociatedFile != null && input.hasEntry(nodeUnassociatedFile))
				nodeUnassociatedCSV = MFDGridSourceProvider.AbstractPrecomputed.loadCSV(input, null, nodeUnassociatedFile);
			
			List<? extends LogicTreeLevel<?>> mappingLevels = getLevelsAffectingFile(MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME, true);
			String gridProvFile = getBranchFileName(branch, GRID_PROV_INSTANCE_FILE_NAME, mappingLevels);
			if (input.hasEntry(gridProvFile)) {
				// try to read the actual implementing class
				try {
					BufferedReader bRead = new BufferedReader(new InputStreamReader(input.getInputStream(gridProvFile)));
					JsonReader reader = new JsonReader(bRead);
					reader.beginObject();
					reader.nextName();
					String className = reader.nextString();
					reader.endObject();
					reader.close();
					bRead.close();
					Class<? extends MFDGridSourceProvider.AbstractPrecomputed> loadingClass =
							(Class<? extends MFDGridSourceProvider.AbstractPrecomputed>) Class.forName(className);

					Constructor<? extends MFDGridSourceProvider.AbstractPrecomputed> constructor = loadingClass.getDeclaredConstructor();
					
					constructor.setAccessible(true);
					
					MFDGridSourceProvider.AbstractPrecomputed module = constructor.newInstance();
					module.init(region, subSeisCSV, nodeUnassociatedCSV, mechCSV);
					return module;
				} catch (Exception e) {
					System.err.println("Warning: couldn't load specified GridSourceProvider instance: "+e.getMessage());
				}
			}
			// defer to default
			return new MFDGridSourceProvider.Default(region, subSeisCSV, nodeUnassociatedCSV, mechCSV);
		}
	}
	
	/**
	 * @param branch
	 * @return solution for the given branch
	 * @throws IOException
	 */
	public synchronized FaultSystemSolution forBranch(LogicTreeBranch<?> branch) throws IOException {
		return forBranch(branch, true);
	}
	
	/**
	 * 
	 * @param branch
	 * @param process enables/disables solution/rupture set processing if a {@link SolutionProcessor} is present
	 * @return solution for the given branch
	 * @throws IOException
	 */
	public synchronized FaultSystemSolution forBranch(LogicTreeBranch<?> branch, boolean process) throws IOException {
		if (verbose) System.out.println("Loading rupture set for logic tree branch: "+branch);
		ArchiveInput input = getArchiveInput();
		String entryPrefix = null; // prefixes will be encoded in the results of getBranchFileName(...) calls
		
		String sectsFile = getBranchFileName(branch, FaultSystemRupSet.SECTS_FILE_NAME, true);
		List<? extends FaultSection> subSects;
		if (prevSubSects != null && sectsFile.equals(prevSectsFile)) {
			if (verbose) System.out.println("\tRe-using previous section data");
			subSects = prevSubSects;
		} else {
			if (verbose) System.out.println("\tLoading section data from "+sectsFile);
			subSects = GeoJSONFaultReader.readFaultSections(
					new InputStreamReader(FileBackedModule.getInputStream(input, entryPrefix, sectsFile)));
			for (int s=0; s<subSects.size(); s++)
				Preconditions.checkState(subSects.get(s).getSectionId() == s,
						"Fault sections must be provided in order starting with ID=0");
			prevSubSects = subSects;
			prevSectsFile = sectsFile;
		}

		RuptureProperties props = loadPropsForBranch(branch);

		String indicesFile = getBranchFileName(branch, FaultSystemRupSet.RUP_SECTS_FILE_NAME, true);
		List<List<Integer>> rupIndices;
		if (prevRupIndices != null && indicesFile.equals(prevIndicesFile)) {
			if (verbose) System.out.println("\tRe-using previous rupture indices");
			rupIndices = prevRupIndices;
		} else {
			if (verbose) System.out.println("\tLoading rupture indices from "+indicesFile);
			CSVReader rupSectsCSV = LargeCSV_BackedModule.loadFromArchive(input, entryPrefix, indicesFile);
			rupIndices = FaultSystemRupSet.loadRupSectsCSV(rupSectsCSV, subSects.size(), props.mags.length);
			prevRupIndices = rupIndices;
			prevIndicesFile = indicesFile;
		}

		FaultSystemRupSet rupSet = new FaultSystemRupSet(subSects, rupIndices,
				props.mags, props.rakes, props.areas, props.lengths);
		if (process && processor != null)
			rupSet = processor.processRupSet(rupSet, branch);
		
		// will use for trts and plausibility
		List<? extends LogicTreeLevel<?>> rupSectLevels = getLevelsAffectingFile(
				FaultSystemRupSet.RUP_SECTS_FILE_NAME, true);
		String trtsFile = getBranchFileName(branch, RupSetTectonicRegimes.DATA_FILE_NAME, rupSectLevels);
		if (prevTRTs != null && prevTRTsFile.equals(trtsFile)) {
			if (verbose) System.out.println("\tRe-using previous TRTs data");
			rupSet.addModule(prevTRTs);
		} else if (input.hasEntry(trtsFile)) {
			CSVFile<String> trtsCSV = CSV_BackedModule.loadFromArchive(input, entryPrefix, trtsFile);
			RupSetTectonicRegimes trts = RupSetTectonicRegimes.forCSV(rupSet, trtsCSV);
			rupSet.addModule(trts);
			prevTRTs = trts;
			prevTRTsFile = trtsFile;
		}
		
		double[] rates = loadRatesForBranch(branch);
		Preconditions.checkState(rates.length == rupIndices.size());
		
		rupSet.addModule(branch);
		
		FaultSystemSolution sol = new FaultSystemSolution(rupSet, rates);
		if (process && processor != null)
			sol = processor.processSolution(sol, branch);
		
		sol.addModule(branch);
		
		String gridRegFile = getBranchFileName(branch, GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME, false);
		String mechFile = getBranchFileName(branch, MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME, false);
		String locsFile = getBranchFileName(branch, GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME, false);
//		System.out.println("Trying to load GridSoruceProv");
//		System.out.println("\tregFile: "+gridRegFile+"; null ? "+(zip.getEntry(gridRegFile) == null));
//		System.out.println("\tregFile: "+mechFile+"; null ? "+(zip.getEntry(mechFile) == null));
//		System.out.println("\tregFile: "+locsFile+"; null ? "+(zip.getEntry(locsFile) == null));
//		String gridSourcesFile = getBranchFileName(branch, GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME, true);
//		System.out.println("\tsourcesFile: "+gridSourcesFile+"; null ? "+(zip.getEntry(gridSourcesFile) == null));
		Class<? extends GridSourceProvider> provClass = null;
		if (gridRegFile != null && input.hasEntry(gridRegFile) && mechFile != null && input.hasEntry(mechFile))
			provClass = MFDGridSourceProvider.class;
		else if (locsFile != null && input.hasEntry(locsFile))
			provClass = GridSourceList.class;
		if (provClass == null && constantGridProv == null && firstConstantGridProvTry) {
			// see if we can load a top level GridSourceProvider
			String prefix = getFilePrefix();
			gridRegFile = ArchivableModule.getEntryName(prefix, GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME);
			mechFile = ArchivableModule.getEntryName(prefix, MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME);
			locsFile = ArchivableModule.getEntryName(prefix, GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME);
			if (gridRegFile != null && input.hasEntry(gridRegFile) && mechFile != null && input.hasEntry(mechFile)) {
				try {
					if (verbose)
						System.out.println("Loading constantGridProv as an MFDGridSourceProvider");
					constantGridProv = archive.loadUnlistedModule(MFDGridSourceProvider.class, prefix);
				} catch (Exception e) {
					System.err.println("Failed to load a constantGridProv: "+e.getMessage());
				}
			} else if (locsFile != null && input.hasEntry(locsFile)) {
				if (verbose)
					System.out.println("Loading constantGridProv as a GridSourceList");
				constantGridProv = archive.loadUnlistedModule(GridSourceList.class, prefix);
			}
			firstConstantGridProvTry = false;
		}
		if (provClass != null) {
			sol.addAvailableModule(new Callable<GridSourceProvider>() {

				@Override
				public GridSourceProvider call() throws Exception {
					return loadGridProvForBranch(branch);
				}
			}, provClass);
		} else if (constantGridProv != null) {
			sol.addModule(constantGridProv);
		}
		
		String statsFile = getBranchFileName(branch, InversionMisfitStats.MISFIT_STATS_FILE_NAME, true);
		if (statsFile != null && input.hasEntry(statsFile)) {
			sol.addAvailableModule(new Callable<InversionMisfitStats>() {

				@Override
				public InversionMisfitStats call() throws Exception {
					CSVFile<String> misfitStatsCSV = CSV_BackedModule.loadFromArchive(input, entryPrefix, statsFile);
					InversionMisfitStats stats = new InversionMisfitStats(null);
					stats.initFromCSV(misfitStatsCSV);
					return stats;
				}
			}, InversionMisfitStats.class);
		}
		
		String progressFile = getBranchFileName(branch, AnnealingProgress.PROGRESS_FILE_NAME, true);
		if (progressFile != null && input.hasEntry(progressFile)) {
			sol.addAvailableModule(new Callable<AnnealingProgress>() {

				@Override
				public AnnealingProgress call() throws Exception {
					CSVFile<String> progressCSV = CSV_BackedModule.loadFromArchive(input, entryPrefix, progressFile);
					return new AnnealingProgress(progressCSV);
				}
			}, AnnealingProgress.class);
		}
		
		String misfitProgressFile = getBranchFileName(branch, InversionMisfitProgress.MISFIT_PROGRESS_FILE_NAME, true);
		if (misfitProgressFile != null && input.hasEntry(misfitProgressFile)) {
			sol.addAvailableModule(new Callable<InversionMisfitProgress>() {

				@Override
				public InversionMisfitProgress call() throws Exception {
					CSVFile<String> progressCSV = CSV_BackedModule.loadFromArchive(input, entryPrefix, misfitProgressFile);
					return new InversionMisfitProgress(progressCSV);
				}
			}, InversionMisfitProgress.class);
		}
		
		// use rupture-sections file to figure out which things affect plausibility
		String plausibilityFile = getBranchFileName(branch, PlausibilityConfiguration.JSON_FILE_NAME, rupSectLevels);
		if (plausibilityFile != null && input.hasEntry(plausibilityFile)) {
			List<? extends FaultSection> fsd = rupSet.getFaultSectionDataList();
			
			rupSet.addAvailableModule(new Callable<PlausibilityConfiguration>() {

				@Override
				public PlausibilityConfiguration call() throws Exception {
					BufferedInputStream zin = FileBackedModule.getInputStream(input, entryPrefix, plausibilityFile);
					InputStreamReader reader = new InputStreamReader(zin);
					PlausibilityConfiguration plausibility = PlausibilityConfiguration.readJSON(reader, fsd);
					reader.close();
					return plausibility;
				}
			}, PlausibilityConfiguration.class);
		}
		
		String mfdsFile = getBranchFileName(branch, RupMFDsModule.FILE_NAME, true);
		if (mfdsFile != null && input.hasEntry(mfdsFile)) {
			FaultSystemSolution solTmp = sol;
			sol.addAvailableModule(new Callable<RupMFDsModule>() {

				@Override
				public RupMFDsModule call() throws Exception {
					CSVFile<String> csv = CSV_BackedModule.loadFromArchive(input, entryPrefix, mfdsFile);
					RupMFDsModule mfds = new RupMFDsModule(solTmp, null);
					mfds.initFromCSV(csv);
					return mfds;
				}
			}, RupMFDsModule.class);
		}
		
		return sol;
	}
	
	public void write(File outputFile) throws IOException {
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
		
		archive.addModule(this);
		
		try {
			archive.addModule(BuildInfoModule.detect());
		} catch (IOException e) {
			// don't fail on a BuildInfoModule
			e.printStackTrace();
		}
		
		writeREADME = true;
		archive.write(outputFile);
		writeREADME = false;
	}
	
	public static SolutionLogicTree load(File treeFile) throws IOException {
		return load(treeFile, null);
	}
	
	public static SolutionLogicTree load(File treeFile, LogicTree<?> logicTree) throws IOException {
		return load(new ZipFile(treeFile), logicTree);
	}
	
	public static SolutionLogicTree load(ZipFile treeZip) throws IOException {
		return load(treeZip, null);
	}
	
	public static SolutionLogicTree load(ZipFile treeZip, LogicTree<?> logicTree) throws IOException {
		return load(new ArchiveInput.ZipFileInput(treeZip), logicTree);
	}
	
	public static SolutionLogicTree load(ArchiveInput input) throws IOException {
		return load(input, null);
	}
	
	public static SolutionLogicTree load(ArchiveInput input, LogicTree<?> logicTree) throws IOException {
		ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(input, SolutionLogicTree.class);
		
		SolutionLogicTree ret = archive.requireModule(SolutionLogicTree.class);
		ret.archive = archive;
		
		if (logicTree != null)
			// override the logic tree
			ret.setLogicTree(logicTree);
		
		return ret;
	}
	
	public FaultSystemSolution calcBranchAveraged() throws IOException {
		BranchAverageSolutionCreator baCreator = new BranchAverageSolutionCreator(getLogicTree().getWeightProvider());
		
		for (LogicTreeBranch<?> branch : getLogicTree().getBranches()) {
			FaultSystemSolution sol = forBranch(branch);
			baCreator.addSolution(sol, branch);
		}
		
		return baCreator.build();
	}
	
	@Override
	public Class<? extends ArchivableModule> getLoadingClass() {
		// default to primary implementation, don't load from files as subclasses
		return SolutionLogicTree.class;
	}
	
	private static final String PROCESSOR_FILE_NAME = "solution_processor.json";
	// will only write a readme if initiated via the .write(File) method, or via FileBuilder
	// otherwise it could conflict with other modules if written as a submodule
	protected boolean writeREADME = false;
	
	protected void writeREADMEToArchive(ArchiveOutput output) throws IOException {
		FileBackedModule.initEntry(output, null, "README");
		OutputStreamWriter writer = new OutputStreamWriter(output.getOutputStream());
		if (verbose) System.out.println("Writing README at root");
		BufferedWriter readme = new BufferedWriter(writer);
		readme.write("This is an OpenSHA Fault System Solution Logic Tree zip file.\n\n");
		
		readme.write("The file format is described in detail at https://opensha.org/Modular-Fault-System-Solution\n\n");
		
		readme.write("Unlike a regular Fault System Solution, this archive contains information for multiple solutions "
				+ "across multiple logic tree branches. Individual files that make up a solution are often only affected "
				+ "by some logic tree branching levels. For example, fault section data ('"+FaultSystemRupSet.SECTS_FILE_NAME
				+ "') is often affected by fault and deformation model branching levels, but neither scaling relationships "
				+ "nor rate model branches. We store information efficiently for each branch by not duplicating those "
				+ "files that are constant across multiple branches.\n\n");
		
		readme.write("All relevant files are stored in the '"+SUB_DIRECTORY_NAME+"' directory. Solution and rupture "
				+ "set files will be stored in branch-specific subdirectories, and information on the logic tree "
				+ "branches available and their file mapping structure are available in the following files:\n\n");
		
		readme.write(" - "+SUB_DIRECTORY_NAME+"/"+LOGIC_TREE_FILE_NAME+": Logic Tree JSON file listing all logic tree "
				+ "branches, weights, and details of the branch levels.\n");
		readme.write(" - "+SUB_DIRECTORY_NAME+"/"+LOGIC_TREE_MAPPINGS_FILE_NAME+": File name mappings and weights for each logic "
				+ "tree branch. This file is not used by OpenSHA, but is written to help external users quickly identify "
				+ "the location of each solution or rupture set file for individual logic tree branches.\n");
		readme.write(" - "+SUB_DIRECTORY_NAME+"/"+PROCESSOR_FILE_NAME+": This file format does not support all optional "
				+ "modules that can be attached to a solution or rupture set. Instead, a solution processor class in "
				+ "OpenSHA can be used to provide additional modules as a function of logic tree branch. This file, if "
				+ "present, gives the class name in OpenSHA of that processor.\n");
		
		readme.flush();
		writer.flush();
		output.closeEntry();
	}

	@Override
	public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
		super.writeToArchive(output, entryPrefix);
		
		if (processor != null)
			writeProcessorJSON(output, buildPrefix(entryPrefix), processor);
		
		// write README
		if (writeREADME)
			writeREADMEToArchive(output);
	}

	private static void writeProcessorJSON(ArchiveOutput output, String prefix, SolutionProcessor processor)
			throws IOException {
		// check for no-arg constructor
		try {
			Preconditions.checkNotNull(processor.getClass().getDeclaredConstructor());
		} catch (Throwable t) {
			System.err.println("WARNING: Loading class for solution logic tree processor doesn't contain a"
					+ "no-arg constructor, loading from a zip file will fail: "+processor.getClass().getName());
		}
		
		// write the logic tree
		FileBackedModule.initEntry(output, prefix, PROCESSOR_FILE_NAME);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output.getOutputStream()));
		gson.toJson(processor.getClass().getName(), String.class, writer);
		writer.flush();
		output.closeEntry();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
		super.initFromArchive(input, entryPrefix);
		
		// see if we have a processor
		String outPrefix = buildPrefix(entryPrefix);
		if (FileBackedModule.hasEntry(input, outPrefix, PROCESSOR_FILE_NAME)) {
			BufferedInputStream is = FileBackedModule.getInputStream(input, outPrefix, PROCESSOR_FILE_NAME);
			
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			InputStreamReader reader = new InputStreamReader(is);
			String className = gson.fromJson(reader, String.class);
			
			Class<?> clazz;
			try {
				clazz = Class.forName(className);
			} catch(Exception e) {
				if (className.endsWith("$UCERF3_SolutionProcessor")) {
					System.err.println("WARNING: found reference to previous oudated solution processor, changing to new class");
					processor = new U3InversionConfigFactory.UCERF3_SolutionProcessor();
					return;
				}
				System.err.println("WARNING: Skipping solution processor', couldn't locate class: "+className);
				return;
			}
			
			Class<SolutionProcessor> processorClass;
			try {
				processorClass = (Class<SolutionProcessor>)clazz;
			} catch (Exception e) {
				System.err.println("WARNING: cannot load solution processor: "+e.getMessage());
				return;
			}
			
			Constructor<SolutionProcessor> constructor;
			try {
				constructor = processorClass.getDeclaredConstructor();
			} catch (Exception e) {
				System.err.println("WARNING: cannot load solution processor as the loading class doesn't "
						+ "have a no-arg constructor: "+clazz.getName()+"");
				return;
			}
			
			try {
				constructor.setAccessible(true);
			} catch (Exception e) {
				System.err.println("WANRING: couldn't make constructor accessible, loading will likely fail: "+e.getMessage());
				return;
			}
			
			try {
				if (verbose) System.out.println("Building instance: "+processorClass.getName());
				this.processor = constructor.newInstance();
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Error loading solution processor, skipping.");
			}
		}
	}
	
	/**
	 * Removes extraneous data not needed for hazard, such as plausibility configuration and misfit stats
	 * 
	 * @param slt
	 * @param outputFile
	 * @throws IOException 
	 */
	public static void simplify(SolutionLogicTree slt, File outputFile) throws IOException {
		simplify(slt, outputFile, false, false);
	}
	
	/**
	 * Removes extraneous data not needed for hazard, such as plausibility configuration and misfit stats
	 * 
	 * @param slt
	 * @param outputFile
	 * @param keepRupMFDs if true, rupture MFDs will be kept (written), false discarded
	 * @param updateBuildInfo if true, OpenSHA build information attached to the SLT will be replaced with current infor
	 * @throws IOException 
	 */
	public static void simplify(SolutionLogicTree slt, File outputFile, boolean keepRupMFDs, boolean updateBuildInfo) throws IOException {
		UnaryOperator<FaultSystemSolution> reprocessor = sol -> SolModuleStripper.stripModules(sol, 5d, keepRupMFDs, false);
		
		reprocess(slt, outputFile, reprocessor, updateBuildInfo, false);
	}
	
	/**
	 * Removes extraneous data not needed for hazard, such as plausibility configuration and misfit stats
	 * 
	 * @param slt
	 * @param outputFile
	 * @param updateBuildInfo if true, OpenSHA build information attached to the SLT will be replaced with current infor
	 * @param processModules if true, the {@link SolutionProcessor} will be called before the reprocess step
	 * @throws IOException 
	 */
	public static void reprocess(SolutionLogicTree slt, File outputFile,
			UnaryOperator<FaultSystemSolution> reprocessor, boolean updateBuildInfo, boolean processModules) throws IOException {
		LogicTree<?> tree = slt.getLogicTree();
		
		ArchiveInput directCopyInput = slt.getArchiveInput();
		if (directCopyInput instanceof ArchiveInput.FileBacked && !(directCopyInput instanceof ArchiveInput.ApacheZipFileInput))
			// switch to apache for efficient copying without de/re-compression
			directCopyInput = new ArchiveInput.ApacheZipFileInput(((ArchiveInput.FileBacked)directCopyInput).getInputFile());
		boolean directCopyGridded = false;
		boolean hasBranchSpecificGridded;
		if (slt.constantGridProv == null && slt.forBranch(tree.getBranch(0), false).hasModule(GridSourceProvider.class)) {
			hasBranchSpecificGridded = true;
			try {
				ArchiveInput input = slt.getArchiveInput();
				if (input instanceof ArchiveInput.FileBacked && !(input instanceof ArchiveInput.ApacheZipFileInput))
					// switch to apache for efficient copying without de/re-compression
					input = new ArchiveInput.ApacheZipFileInput(((ArchiveInput.FileBacked)input).getInputFile());
				System.out.println("Will directly copy gridded seismicity data");
				directCopyInput = input;
				directCopyGridded = true;
			} catch (Exception e) {
				System.out.println("Will load and write gridded seismicity data (if applicable): "+e.getMessage());
			}			
		} else {
			hasBranchSpecificGridded = false;
		}
		FileBuilder builder = new FileBuilder(slt.getProcessor(), ArchiveOutput.getDefaultOutput(outputFile, directCopyInput));
		if (slt.constantGridProv != null)
			builder.setConstantGridProv(slt.constantGridProv);
		if (hasBranchSpecificGridded) {
			if (directCopyInput == null)
				builder.setSerializeGridded(true);
			else
				builder.setDirectCopyGriddedFrom(directCopyInput);
		} else {
			builder.setSerializeGridded(false);
		}
		if (directCopyInput != null)
			builder.setDirectCopySource(directCopyInput);
		builder.setWeightProv(tree.getWeightProvider());
		if (!updateBuildInfo && slt.archive != null)
			// copy existing build info over
			builder.setBuildInfo(slt.archive.getModule(BuildInfoModule.class));
		
		boolean prevVerbose = ModuleContainer.VERBOSE_DEFAULT;
		Stopwatch totalWatch = Stopwatch.createStarted();
		Stopwatch blockingWriteWatch = Stopwatch.createUnstarted();
		ModuleContainer.VERBOSE_DEFAULT = false;
		CompletableFuture<Void> writeFuture = null;
		DecimalFormat pDF = new DecimalFormat("0.0%");
		DecimalFormat tDF = new DecimalFormat("0.##");
		for (int i=0; i<tree.size(); i++) {
			LogicTreeBranch<?> branch = tree.getBranch(i);
			FaultSystemSolution sol = slt.forBranch(branch, processModules);
			if (directCopyGridded)
				sol.removeAvailableModuleInstances(GridSourceList.class);
			
			FaultSystemSolution outputSol;
			if (reprocessor != null)
				outputSol = reprocessor.apply(sol);
			else
				outputSol = sol;
			
//			FaultSystemSolution simplifiedSol = SolModuleStripper.stripModules(sol, 5d, keepRupMFDs, false);
			
			if (writeFuture != null) {
				blockingWriteWatch.start();
				writeFuture.join();
				blockingWriteWatch.stop();
			}
			
			int myIndex = i;
			writeFuture = CompletableFuture.runAsync(new Runnable() {
				
				@Override
				public void run() {
					try {
						builder.solution(outputSol, branch);
						
						double elapsedSecs = totalWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
						double blockWritingSecs = blockingWriteWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
						double estSecs = elapsedSecs*(double)tree.size()/(myIndex+1d);
						double estSecsRemaining = estSecs-elapsedSecs;
						String timeStr;
						if (estSecs > 90d) {
							double elapsedMins = elapsedSecs/60d;
							double estMins = estSecs/60d;
							double estMinsRemaining = estSecsRemaining/60d;
							
							if (estMins > 60d) {
								double elapsedHours = elapsedMins/60d;
								double estHoursRemaining = estMinsRemaining/60d;
								timeStr = tDF.format(elapsedHours)+" h";
								if (myIndex < tree.size()-1)
									timeStr += ", "+tDF.format(estHoursRemaining)+" h remaining";
							} else {
								timeStr = tDF.format(elapsedMins)+" m";
								if (myIndex < tree.size()-1)
									timeStr += ", "+tDF.format(estMinsRemaining)+" m remaining";
							}
						} else {
							timeStr = tDF.format(elapsedSecs)+" s";
							if (myIndex < tree.size()-1)
								timeStr += ", "+tDF.format(estSecsRemaining)+" s remaining";
						}
						
						System.out.println("DONE branch "+myIndex+"/"+tree.size()+" in "+timeStr
								+" ("+pDF.format(blockWritingSecs/elapsedSecs)+" waiting on blocking write)");
					} catch (IOException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
			});
		}
		
		if (writeFuture != null) {
			blockingWriteWatch.start();
			writeFuture.join();
			blockingWriteWatch.stop();
		}
		
		ModuleContainer.VERBOSE_DEFAULT = prevVerbose;
		
		builder.close();
	}

	public static void main(String[] args) throws IOException {
		File dir = new File("/home/kevin/OpenSHA/nshm23/batch_inversions/2024_02_02-nshm23_branches-WUS_FM_v3");
		
//		File inSLTfile = new File(dir, "results.zip");
//		File outSLTfile = new File(dir, "results_simplified.zip");
//		SolutionLogicTree inSLT = SolutionLogicTree.load(inSLTfile);
//		inSLT.setConstantGridProv(FaultSystemSolution.load(
//				new File(dir, "results_WUS_FM_v3_branch_averaged_gridded_simplified.zip")).getGridSourceProvider());
		
		File inSLTfile = new File(dir, "results.zip");
		File outSLTfile = new File("/tmp/results_simplified.zip");
		SolutionLogicTree inSLT = SolutionLogicTree.load(inSLTfile);
		
//		File inSLTfile = new File(dir, "results_gridded_branches.zip");
//		File outSLTfile = new File(dir, "results_gridded_branches_simplified.zip");
//		SolutionLogicTree inSLT = SolutionLogicTree.load(inSLTfile);
		
		simplify(inSLT, outSLTfile);
		
//		File dir = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
//				+ "2021_11_23-u3_branches-FM3_1-5h/");
//		SolutionLogicTree tree = SolutionLogicTree.load(new File(dir, "results.zip"));
////		
////		FaultSystemSolution ba = tree.calcBranchAveraged();
////		
////		ba.write(new File(dir, "branch_averaged.zip"));
//		
////		SolutionLogicTree tree = SolutionLogicTree.load(new File("/tmp/results.zip"));
//		if (tree.processor == null)
//			System.out.println("No solution processor");
//		else
//			System.out.println("Solution processor type: "+tree.processor.getClass().getName());
//		
//		FileBuilder builder = new FileBuilder(tree.processor, new File("/tmp/sol_tree_test.zip"));
//		BranchAverageSolutionCreator avgBuilder = new BranchAverageSolutionCreator(tree.getLogicTree().getWeightProvider());
//		for (LogicTreeBranch<?> branch : tree.getLogicTree()) {
//			if (Math.random() < 0.05) {
//				FaultSystemSolution sol = tree.forBranch(branch);
//				builder.solution(sol, branch);
//				if (branch.getValue(FaultModels.class) == FaultModels.FM3_1)
//					avgBuilder.addSolution(sol, branch);
//			}
//		}
//		
//		builder.build();
//		FaultSystemSolution avgSol = avgBuilder.build();
//		avgSol.write(new File("/tmp/sol_tree_test_ba.zip"));
	}

}
