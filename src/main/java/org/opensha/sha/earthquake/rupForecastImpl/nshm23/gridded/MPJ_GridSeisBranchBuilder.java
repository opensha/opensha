package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipFile;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.mpj.AbstractAsyncLogicTreeWriter;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.SolutionProcessor;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.PrimaryRegions;

import com.google.common.base.Preconditions;

import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;

public class MPJ_GridSeisBranchBuilder extends MPJTaskCalculator {
	
	private LogicTree<?> tree;
	private File solsDir;
	
	private LogicTree<?> gridSeisOnlyTree;
	
	private Region region;
	
	private ExecutorService exec;
	
	public MPJ_GridSeisBranchBuilder(CommandLine cmd) throws IOException {
		super(cmd);
		
		this.shuffle = false;
		
		tree = LogicTree.read(new File(cmd.getOptionValue("logic-tree")));
		if (rank == 0)
			debug("Loaded "+tree.size()+" tree nodes");
		
		solsDir = new File(cmd.getOptionValue("sol-dir"));
		Preconditions.checkState(solsDir.exists());
		
		gridSeisOnlyTree = LogicTree.buildExhaustive(NSHM23_LogicTreeBranch.levelsOffFault, true);
		int totBranches = getNumTasks()*gridSeisOnlyTree.size();
		if (rank == 0)
			debug("Will build "+gridSeisOnlyTree.size()+" grid-seis branches per fault branch, "+totBranches+" in total");
		
		if (cmd.hasOption("region")) {
			File regFile = new File(cmd.getOptionValue("region"));
			Preconditions.checkState(regFile.exists(), "Supplied region file doesn't exist: %s", regFile.getAbsolutePath());
			Feature feature = Feature.read(regFile);
			region = Region.fromFeature(feature);
		}
		
		int threads = getNumThreads();
		if (threads > 1)
			exec = Executors.newFixedThreadPool(threads);
		
		if (rank == 0)
			this.postBatchHook = new AsyncLogicTreeWriter(new NSHM23_InvConfigFactory.NSHM23SolProcessor());
	}
	
	private class AsyncLogicTreeWriter extends AbstractAsyncLogicTreeWriter {

		protected Map<String, AveragingAccumulator<GridSourceProvider>> gridSourceAveragers;
		protected Map<String, AveragingAccumulator<FaultGridAssociations>> faultGridAveragers;
		
		
		private boolean first = true;
		private boolean lightCopy = false;
		
		public AsyncLogicTreeWriter(SolutionProcessor processor) throws IOException {
			super(solsDir, processor, new BranchWeightProvider.OriginalWeights());
			// just average the grid source providers instead
			this.baCreators = null;
			gridSourceAveragers = new HashMap<>();
			faultGridAveragers = new HashMap<>();
		}

		@Override
		public File getOutputFile(File resultsDir) {
			return new File(resultsDir.getParentFile(), resultsDir.getName()+"_gridded.zip");
		}

		@Override
		public int getNumTasks() {
			return MPJ_GridSeisBranchBuilder.this.getNumTasks();
		}

		@Override
		public void debug(String message) {
			MPJ_GridSeisBranchBuilder.this.debug(message);
		}

		@Override
		public LogicTreeBranch<?> getBranch(int calcIndex) {
			return tree.getBranch(calcIndex);
		}

		@Override
		public FaultSystemSolution getSolution(LogicTreeBranch<?> branch, int calcIndex) throws IOException {
			return FaultSystemSolution.load(getSolFile(branch));
		}
		
		private void copyArchive(File origZipFile, List<LogicTreeBranch<?>> branches) throws IOException {
			ZipFile zip = new ZipFile(origZipFile);
			sltBuilder.copyDataFrom(zip, branches);
		}
		
		@Override
		protected void doProcessIndex(int index) throws IOException {
			if (first) {
				first = false;
				File origZipFile = super.getOutputFile(solsDir);
				if (origZipFile.exists()) {
					List<LogicTreeBranch<?>> allBranches = new ArrayList<>();
					for (LogicTreeBranch<?> origBranch : tree)
						for (LogicTreeBranch<?> gridBranch : gridSeisOnlyTree)
							allBranches.add(getCombinedBranch(origBranch, gridBranch));
					debug("AsyncLogicTree: copying zip file over from "+origZipFile.getAbsolutePath());
					copyArchive(origZipFile, allBranches);
					lightCopy = true;
				}
			}
			LogicTreeBranch<?> origBranch = getBranch(index);
			
			debug("AsyncLogicTree: calcDone "+index+" = branch "+origBranch);
			
			FaultSystemSolution origSol = null;
			
			File solDir = getSolFile(origBranch).getParentFile();
			File gridSeisDir = new File(solDir, "grid_source_providers");
			Preconditions.checkState(gridSeisDir.exists());
			
			File associationsFile = new File(gridSeisDir, GRID_ASSOCIATIONS_ARCHIVE_NAME);
			ModuleArchive<OpenSHA_Module> assocArchive = new ModuleArchive<>(associationsFile);
			FaultGridAssociations associations = assocArchive.requireModule(FaultGridAssociations.class);
			
			for (LogicTreeBranch<?> gridSeisBranch : gridSeisOnlyTree) {
				File gridProvFile = new File(gridSeisDir, gridSeisBranch.buildFileName()+".zip");
				ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(gridProvFile);
				GridSourceProvider prov = archive.requireModule(GridSourceProvider.class);
				
				LogicTreeBranch<?> combBranch = getCombinedBranch(origBranch, gridSeisBranch);
				
				if (lightCopy) {
					sltBuilder.writeGridProvToArchive(prov, combBranch);
				} else {
					if (origSol == null)
						origSol = getSolution(origBranch, index);
					FaultSystemSolution newSol = solCopy(origSol);
					
					newSol.getRupSet().addModule(combBranch);
					newSol.addModule(combBranch);
					
					newSol.setGridSourceProvider(prov);
					newSol.getRupSet().addModule(associations);
					sltBuilder.solution(newSol, combBranch);
				}
				
				// now add in to branch averaged
				if (gridSourceAveragers != null) {
					String baPrefix = getBA_prefix(combBranch);
					
					if (baPrefix == null) {
						debug("AsyncLogicTree won't branch average, all levels affect "+FaultSystemRupSet.RUP_PROPS_FILE_NAME);
					} else {
						if (!gridSourceAveragers.containsKey(baPrefix))
							gridSourceAveragers.put(baPrefix, prov.averagingAccumulator());
						AveragingAccumulator<GridSourceProvider> accumulator = gridSourceAveragers.get(baPrefix);
						try {
							accumulator.process(prov, weightProv.getWeight(combBranch));
						} catch (Exception e) {
							e.printStackTrace();
							System.err.flush();
							debug("AsyncLogicTree: Branch averaging failed for branch "+combBranch+", disabling averaging");
							gridSourceAveragers = null;
						}
						
						if (faultGridAveragers != null) {
							if (!faultGridAveragers.containsKey(baPrefix))
								faultGridAveragers.put(baPrefix, associations.averagingAccumulator());
							AveragingAccumulator<FaultGridAssociations> assocAccumulator = faultGridAveragers.get(baPrefix);
							try {
								assocAccumulator.process(associations, weightProv.getWeight(combBranch));
							} catch (Exception e) {
								e.printStackTrace();
								System.err.flush();
								debug("AsyncLogicTree: Branch averaging failed for branch "+combBranch+", disabling averaging");
								faultGridAveragers = null;
							}
						}
					}
				}
			}
		}

		@Override
		public void abortAndExit(Throwable t, int exitCode) {
			MPJ_GridSeisBranchBuilder.abortAndExit(t, exitCode);
		}

		@Override
		public void shutdown() {
			super.shutdown();
			
			if (gridSourceAveragers != null && !gridSourceAveragers.isEmpty()) {
				for (String baPrefix : gridSourceAveragers.keySet()) {
					File baFile = getBAOutputFile(solsDir, baPrefix);
					Preconditions.checkState(baFile.exists());

					memoryDebug("AsyncLogicTree: building "+baFile.getAbsolutePath());
					try {
						GridSourceProvider avgProv = gridSourceAveragers.get(baPrefix).getAverage();
						FaultSystemSolution baSol = FaultSystemSolution.load(baFile);
						baSol.setGridSourceProvider(avgProv);
						
						if (faultGridAveragers != null && faultGridAveragers.containsKey(baPrefix)) {
							FaultGridAssociations associations = faultGridAveragers.get(baPrefix).getAverage();
							baSol.getRupSet().addModule(associations);
						}
						
						String fileName = baFile.getName().replace(".zip", "")+"_gridded.zip";
						baSol.write(new File(baFile.getParentFile(), fileName));
					} catch (Exception e) {
						memoryDebug("AsyncLogicTree: failed to build BA for "+baFile.getAbsolutePath());
						e.printStackTrace();
						continue;
					}
				}
			}
		}
		
	}

	@Override
	protected int getNumTasks() {
		return tree.size();
	}
	
	private static final String GRID_ASSOCIATIONS_ARCHIVE_NAME = "fault_grid_associations.zip"; 

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		for (int index : batch) {
			LogicTreeBranch<?> origBranch = tree.getBranch(index);
			
			debug("Building gridded seismicity for "+index+": "+origBranch);
			
			File solFile = getSolFile(origBranch);
			Preconditions.checkState(solFile.exists());
			
			File gridSeisDir = new File(solFile.getParentFile(), "grid_source_providers");
			Preconditions.checkState(gridSeisDir.exists() || gridSeisDir.mkdir(), "Couldn't create grid source dir: %s", gridSeisDir);
			
			if (isAlreadyDone(gridSeisDir)) {
				debug(index+" is already done, skipping");
				continue;
			}
			
			FaultSystemSolution sol = FaultSystemSolution.load(solFile);
			FaultSystemRupSet rupSet = sol.getRupSet();
			
			// figure out region
			Region region;
			if (this.region == null) {
				// detect it
				ModelRegion modelReg = rupSet.getModule(ModelRegion.class);
				if (modelReg == null) {
					modelReg = NSHM23_FaultModels.getDefaultRegion(origBranch);
					rupSet.addModule(modelReg);
				}
				region = modelReg.getRegion();
			} else {
				// use the one from the command line
				region = this.region;
				rupSet.addModule(new ModelRegion(region));
			}
			
			// pre-cache the cube associations once for this branch
			List<PrimaryRegions> seisRegions = NSHM23_InvConfigFactory.getSeismicityRegions(region);
			Preconditions.checkState(seisRegions.size() >= 1);
			NSHM23_FaultCubeAssociations cubeAssoc = null;
			if (seisRegions.size() == 1) {
				Region seisRegion = seisRegions.get(0).load();
				GriddedRegion seisGridReg = NSHM23_InvConfigFactory.getGriddedSeisRegion(seisRegion);
				cubeAssoc = new NSHM23_FaultCubeAssociations(rupSet,
						new CubedGriddedRegion(seisGridReg),
						NSHM23_SingleRegionGridSourceProvider.DEFAULT_MAX_FAULT_NUCL_DIST);
				if (!seisRegion.equalsRegion(region)) {
					// need to nest it
					GriddedRegion modelGridReg = NSHM23_InvConfigFactory.getGriddedSeisRegion(region);
					cubeAssoc = new NSHM23_FaultCubeAssociations(rupSet,
							new CubedGriddedRegion(modelGridReg), List.of(cubeAssoc));
				}
			} else {
				List<NSHM23_FaultCubeAssociations> regionalAssociations = new ArrayList<>();
				for (PrimaryRegions seisRegion : seisRegions) {
					GriddedRegion seisGridReg = NSHM23_InvConfigFactory.getGriddedSeisRegion(seisRegion.load());
					regionalAssociations.add(new NSHM23_FaultCubeAssociations(rupSet,
							new CubedGriddedRegion(seisGridReg),
							NSHM23_SingleRegionGridSourceProvider.DEFAULT_MAX_FAULT_NUCL_DIST));
				}
				GriddedRegion modelGridReg = NSHM23_InvConfigFactory.getGriddedSeisRegion(region);
				cubeAssoc = new NSHM23_FaultCubeAssociations(rupSet,
						new CubedGriddedRegion(modelGridReg), regionalAssociations);
			}
			
			File assocFile = new File(gridSeisDir, GRID_ASSOCIATIONS_ARCHIVE_NAME);
			ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
			archive.addModule(cubeAssoc);
			archive.write(assocFile);
			
			List<Future<?>> futures = new ArrayList<>();
			for (LogicTreeBranch<?> gridSeisBranch : gridSeisOnlyTree) {
				CalcRunnable run = new CalcRunnable(origBranch, gridSeisBranch, gridSeisDir, sol, cubeAssoc);
				if (exec == null)
					run.run();
				else
					futures.add(exec.submit(run));
			}
			
			for (Future<?> future : futures)
				future.get();
			
			debug("done with "+index);
		}
	}
	
	protected File getSolFile(LogicTreeBranch<?> branch) {
		String dirName = branch.buildFileName();
		File runDir = new File(solsDir, dirName);
		Preconditions.checkState(runDir.exists() || runDir.mkdir());
		
		return new File(runDir, "solution.zip");
	}
	
	private LogicTreeBranch<?> getCombinedBranch(LogicTreeBranch<?> origBranch, LogicTreeBranch<?> gridSeisBranch) {
		List<LogicTreeLevel<? extends LogicTreeNode>> combLevels = new ArrayList<>();
		combLevels.addAll(origBranch.getLevels());
		combLevels.addAll(gridSeisBranch.getLevels());
		
		LogicTreeBranch<LogicTreeNode> combinedBranch = new LogicTreeBranch<>(combLevels);
		for (LogicTreeNode node : origBranch)
			combinedBranch.setValue(node);
		for (LogicTreeNode node : gridSeisBranch)
			combinedBranch.setValue(node);
		
		double origWeight = origBranch.getOrigBranchWeight() * gridSeisBranch.getOrigBranchWeight();
		combinedBranch.setOrigBranchWeight(origWeight);
		
		return combinedBranch;
	}

	private FaultSystemSolution solCopy(FaultSystemSolution origSol) {
		FaultSystemRupSet origRupSet = origSol.getRupSet();
		FaultSystemRupSet newRupSet = FaultSystemRupSet.buildFromExisting(origRupSet, true).build();
		FaultSystemSolution newSol = new FaultSystemSolution(newRupSet, origSol.getRateForAllRups());
		for (OpenSHA_Module module : origSol.getModules())
			newSol.addModule(module);
		return newSol;
	}
	
	private boolean isAlreadyDone(File gridSeisDir) {
		if (!new File(gridSeisDir, GRID_ASSOCIATIONS_ARCHIVE_NAME).exists())
			return false;
		for (LogicTreeBranch<?> gridSeisBranch : gridSeisOnlyTree) {
			File gridSeisFile =  new File(gridSeisDir, gridSeisBranch.buildFileName()+".zip");
			if (!gridSeisFile.exists())
				return false;
			try {
				new ModuleArchive<>(gridSeisFile).getModule(GridSourceProvider.class);
			} catch (Exception e) {
				return false;
			}
		}
		return true;
	}
	
	private class CalcRunnable implements Runnable {
		
		private LogicTreeBranch<?> origBranch;
		private LogicTreeBranch<?> gridSeisBranch;
		private File gridSeisDir;
		private FaultSystemSolution origSol;
		private NSHM23_FaultCubeAssociations cubeAssoc;

		public CalcRunnable(LogicTreeBranch<?> origBranch, LogicTreeBranch<?> gridSeisBranch, File gridSeisDir,
				FaultSystemSolution origSol, NSHM23_FaultCubeAssociations cubeAssoc) {
			this.origBranch = origBranch;
			this.gridSeisBranch = gridSeisBranch;
			this.gridSeisDir = gridSeisDir;
			this.origSol = origSol;
			this.cubeAssoc = cubeAssoc;
		}

		@Override
		public void run() {
			LogicTreeBranch<?> combinedBranch = getCombinedBranch(origBranch, gridSeisBranch);
			
			debug("Building for combined branch: "+combinedBranch);
			
			// build a copy of the rupture set/solution for this branch with all original modules attached
			FaultSystemSolution newSol = solCopy(origSol);
			FaultSystemRupSet newRupSet = newSol.getRupSet();
			
			// add cube associations so that we can reuse them
			newRupSet.addModule(cubeAssoc);
			newRupSet.addModule(combinedBranch);
			newSol.addModule(combinedBranch);
			
			try {
				NSHM23_AbstractGridSourceProvider gridProv = NSHM23_InvConfigFactory.buildGridSourceProv(
						newSol, combinedBranch);
				newSol.setGridSourceProvider(gridProv);
				
				File outputFile = new File(gridSeisDir, gridSeisBranch.buildFileName()+".zip");
				ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
				archive.addModule(gridProv);
				archive.write(outputFile);
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		if (exec != null)
			exec.shutdown();
		if (rank == 0) {
			memoryDebug("waiting for any post batch hook operations to finish");
			((AsyncLogicTreeWriter)postBatchHook).shutdown();
			memoryDebug("post batch hook done");
		}
	}
	
	private void memoryDebug(String info) {
		if (info == null || info.isBlank())
			info = "";
		else
			info += "; ";
	    
	    System.gc();
		Runtime rt = Runtime.getRuntime();
		long totalMB = rt.totalMemory() / 1024 / 1024;
		long freeMB = rt.freeMemory() / 1024 / 1024;
		long usedMB = totalMB - freeMB;
		debug(info+"mem t/u/f: "+totalMB+"/"+usedMB+"/"+freeMB);
	}
	
	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		ops.addRequiredOption("lt", "logic-tree", true, "Path to logic tree JSON file");
		ops.addRequiredOption("sd", "sol-dir", true, "Path to directory containing solutions");
		ops.addOption("r", "region", true, "Optional path to GeoJSON file containing a region for which we should compute hazard. "
				+ "Can be a gridded region or an outline. If not supplied, then one will be detected from the model.");
		
		return ops;
	}

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");
		try {
			args = MPJTaskCalculator.initMPJ(args);
			
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args, MPJ_GridSeisBranchBuilder.class);
			
			MPJ_GridSeisBranchBuilder driver = new MPJ_GridSeisBranchBuilder(cmd);
			driver.run();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
