package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet.RuptureProperties;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.AnnealingProgress;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.util.BranchAverageSolutionCreator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import scratch.UCERF3.U3FaultSystemSolutionFetcher;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.inversion.U3InversionConfigFactory;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.logicTree.U3LogicTreeBranchNode;

/**
 * Module that stores/loads fault system solutions for individual branches of a logic tree.
 * 
 * @author kevin
 *
 */
public class SolutionLogicTree extends AbstractLogicTreeModule {
	
	private boolean serializeGridded = true;
	private SolutionProcessor processor;
	
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

		@Override
		protected FaultSystemSolution loadExternalForBranch(LogicTreeBranch<?> branch) throws IOException {
			if (oldFetcher != null)
				return oldFetcher.getSolution(asU3Branch(branch));
			return null;
		}
	}
	
	public static abstract class AbstractExternalFetcher extends SolutionLogicTree {

		protected AbstractExternalFetcher(SolutionProcessor processor, LogicTree<?> logicTree) {
			super(processor, null, null, logicTree);
		}
		
		protected abstract FaultSystemSolution loadExternalForBranch(LogicTreeBranch<?> branch) throws IOException;

		@Override
		public synchronized final FaultSystemSolution forBranch(LogicTreeBranch<?> branch) throws IOException {
			FaultSystemSolution external = loadExternalForBranch(branch);
			if (external != null)
				return external;
			return super.forBranch(branch);
		}
		
	}
	
	public static class FileBuilder extends Builder {

		private SolutionProcessor processor;
		private File outputFile;
		
		private ModuleArchive<SolutionLogicTree> archive;

		private CompletableFuture<Void> startModuleWriteFuture = null;
		private CompletableFuture<Void> endModuleWriteFuture = null;
		private CompletableFuture<Void> endArchiveWriteFuture = null;
		
		private ZipOutputStream zout;
		private String entryPrefix;
		private SolutionLogicTree solTree;
		private HashSet<String> writtenFiles = new HashSet<>();
		
		private List<LogicTreeBranch<LogicTreeNode>> branches = new ArrayList<>();
		private List<LogicTreeLevel<? extends LogicTreeNode>> levels = null;
		
		public FileBuilder(File outputFile) throws IOException {
			this(null, outputFile);
		}
		
		public FileBuilder(SolutionProcessor processor, File outputFile) throws IOException {
			this(processor, outputFile, "");
		}

		public FileBuilder(SolutionProcessor processor, File outputFile, String entryPrefix) throws IOException {
			this.processor = processor;
			this.outputFile = outputFile;
			archive = new ModuleArchive<>();
		}

		@SuppressWarnings("unchecked")
		@Override
		public synchronized void solution(FaultSystemSolution sol, LogicTreeBranch<?> branch) throws IOException {
			if (levels == null) {
				levels = new ArrayList<>();
				for (LogicTreeLevel<?> level : branch.getLevels())
					levels.add(level);
			} else {
				List<? extends LogicTreeLevel<?>> myLevels = branch.getLevels();
				Preconditions.checkState(myLevels.size() == levels.size(),
						"Branch %s has a different number of levels than the first branch", branch);
				for (int i=0; i<myLevels.size(); i++)
					Preconditions.checkState(myLevels.get(i).equals(levels.get(i)),
							"Branch %s has a different level at position %s than the first branch", i, branch);
			}
			branches.add((LogicTreeBranch<LogicTreeNode>) branch);
			
			if (solTree == null) {
				startModuleWriteFuture = new CompletableFuture<>();
				endModuleWriteFuture = new CompletableFuture<>();
				// first time
				this.solTree = new SolutionLogicTree() {

					@Override
					public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
						FileBuilder.this.zout = zout;
						FileBuilder.this.entryPrefix = entryPrefix;
						// signal that we have started writing this module
						startModuleWriteFuture.complete(null);
						// now wait until we're done writing it externally
						try {
							endModuleWriteFuture.get();
						} catch (Exception e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
					}
					
				};
				solTree.setLogicTreeLevels(levels);
				archive.addModule(solTree);
				// begin asynchronous module archive write
				endArchiveWriteFuture = CompletableFuture.runAsync(new Runnable() {
					
					@Override
					public void run() {
						try {
							archive.write(outputFile);
						} catch (Exception e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
					}
				});
			}
			// wait until we have started writing the module...
			try {
				startModuleWriteFuture.get();
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			// we have started writing it!
			Preconditions.checkNotNull(zout);
			Preconditions.checkNotNull(entryPrefix);
			
			String outPrefix = solTree.buildPrefix(entryPrefix);

			System.out.println("Writing branch: "+branch);
			solTree.writeBranchFilesToArchive(zout, outPrefix, branch, writtenFiles, sol);
		}
		
		public synchronized void close() throws IOException {
			if (zout != null) {
				// write logic tree
				LogicTree<?> tree = LogicTree.fromExisting(levels, branches);
				solTree.writeLogicTreeToArchive(zout, solTree.buildPrefix(entryPrefix), tree);
				
				if (processor != null)
					writeProcessorJSON(zout, solTree.buildPrefix(entryPrefix), processor);
				
				zout = null;
				entryPrefix = null;
				solTree = null;
				endModuleWriteFuture.complete(null);
				
				// now wait until the archive is done writing
				try {
					endArchiveWriteFuture.get();
				} catch (Exception e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		}

		@Override
		public SolutionLogicTree build() throws IOException {
			close();
			
			return new ModuleArchive<>(outputFile, SolutionLogicTree.class).requireModule(SolutionLogicTree.class);
		}
		
	}
	
	protected static abstract class Builder {
		
		public abstract void solution(FaultSystemSolution sol, LogicTreeBranch<?> branch) throws IOException;
		
		public abstract SolutionLogicTree build() throws IOException;
	}
	
	@SuppressWarnings("unused") // used for serialization
	private SolutionLogicTree() {
		this(null, null);
	}
	
	protected SolutionLogicTree(SolutionProcessor processor, LogicTree<?> logicTree) {
		this(processor, null, null, logicTree);
	}

	protected SolutionLogicTree(SolutionProcessor processor, ZipFile zip, String prefix, LogicTree<?> logicTree) {
		super(zip, prefix, logicTree);
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
		return "solution_logic_tree";
	}
	
	public void setSerializeGridded(boolean serializeGridded) {
		this.serializeGridded = serializeGridded;
	}

	@Override
	protected void writeBranchFilesToArchive(ZipOutputStream zout, String prefix, LogicTreeBranch<?> branch,
			HashSet<String> writtenFiles) throws IOException {
		FaultSystemSolution sol = forBranch(branch);
		writeBranchFilesToArchive(zout, prefix, branch, writtenFiles, sol);
	}
	

	protected void writeBranchFilesToArchive(ZipOutputStream zout, String prefix, LogicTreeBranch<?> branch,
			HashSet<String> writtenFiles, FaultSystemSolution sol) throws IOException {
		// could try to be fancy and copy files over without loading, but these things will be written out so rarely
		// (usually one and done) so it's not worth the added complexity
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		String entryPrefix = null; // prefixes will be encoded in the results of getBranchFileName(...) calls
		
		String sectsFile = getBranchFileName(branch, prefix, FaultSystemRupSet.SECTS_FILE_NAME, true);
		if (!writtenFiles.contains(sectsFile)) {
			FileBackedModule.initEntry(zout, entryPrefix, sectsFile);
			OutputStreamWriter writer = new OutputStreamWriter(zout);
			GeoJSONFaultReader.writeFaultSections(writer, rupSet.getFaultSectionDataList());
			writer.flush();
			zout.flush();
			zout.closeEntry();
			writtenFiles.add(sectsFile);
		}
		
		String indicesFile = getBranchFileName(branch, prefix, FaultSystemRupSet.RUP_SECTS_FILE_NAME, true);
		if (!writtenFiles.contains(indicesFile)) {
			CSV_BackedModule.writeToArchive(FaultSystemRupSet.buildRupSectsCSV(rupSet), zout, entryPrefix, indicesFile);
			writtenFiles.add(indicesFile);
		}
		
		String propsFile = getBranchFileName(branch, prefix, FaultSystemRupSet.RUP_PROPS_FILE_NAME, true);
		if (!writtenFiles.contains(propsFile)) {
			CSV_BackedModule.writeToArchive(new RuptureProperties(rupSet).buildCSV(), zout, entryPrefix, propsFile);
			writtenFiles.add(propsFile);
		}
		
		String ratesFile = getBranchFileName(branch, prefix, FaultSystemSolution.RATES_FILE_NAME, true);
		if (!writtenFiles.contains(ratesFile)) {
			CSV_BackedModule.writeToArchive(FaultSystemSolution.buildRatesCSV(sol), zout, entryPrefix, ratesFile);
			writtenFiles.add(ratesFile);
		}
		
		if (serializeGridded && sol.hasModule(GridSourceProvider.class)) {
			GridSourceProvider prov = sol.getModule(GridSourceProvider.class);
			AbstractGridSourceProvider.Precomputed precomputed;
			if (prov instanceof AbstractGridSourceProvider.Precomputed)
				precomputed = (AbstractGridSourceProvider.Precomputed)prov;
			else
				precomputed = new AbstractGridSourceProvider.Precomputed(prov);
			
			String gridRegFile = getBranchFileName(branch, prefix,
					AbstractGridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME, false);
			if (gridRegFile != null && !writtenFiles.contains(gridRegFile)) {
				FileBackedModule.initEntry(zout, entryPrefix, gridRegFile);
				Feature regFeature = precomputed.getGriddedRegion().toFeature();
				OutputStreamWriter writer = new OutputStreamWriter(zout);
				Feature.write(regFeature, writer);
				writer.flush();
				zout.flush();
				zout.closeEntry();
				writtenFiles.add(gridRegFile);
			}

			String mechFile = getBranchFileName(branch, prefix,
					AbstractGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME, false);
			if (mechFile != null && !writtenFiles.contains(mechFile)) {
				CSV_BackedModule.writeToArchive(precomputed.buildWeightsCSV(), zout, entryPrefix, mechFile);
				writtenFiles.add(mechFile);
			}
			String subSeisFile = getBranchFileName(branch, prefix,
					AbstractGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME, true);
			if (subSeisFile != null && !writtenFiles.contains(subSeisFile)) {
				CSVFile<String> csv = precomputed.buildSubSeisCSV();
				if (csv != null) {
					CSV_BackedModule.writeToArchive(csv, zout, entryPrefix, subSeisFile);
					writtenFiles.add(subSeisFile);
				}
			}
			String unassociatedFile = getBranchFileName(branch, prefix,
					AbstractGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME, true);
			if (unassociatedFile != null && !writtenFiles.contains(unassociatedFile)) {
				CSVFile<String> csv = precomputed.buildUnassociatedCSV();
				if (csv != null) {
					CSV_BackedModule.writeToArchive(csv, zout, entryPrefix, unassociatedFile);
					writtenFiles.add(unassociatedFile);
				}
			}
		}
		
		InversionMisfitStats misfitStats = sol.getModule(InversionMisfitStats.class);
		if (misfitStats == null && sol.hasModule(InversionMisfits.class))
			misfitStats = sol.requireModule(InversionMisfits.class).getMisfitStats();
		
		if (misfitStats != null) {
			String statsFile = getBranchFileName(branch, prefix,
					InversionMisfitStats.MISFIT_STATS_FILE_NAME, true);
			Preconditions.checkState(!writtenFiles.contains(statsFile));
			CSV_BackedModule.writeToArchive(misfitStats.getCSV(), zout, entryPrefix, statsFile);
			writtenFiles.add(statsFile);
		}
		
		AnnealingProgress progress = sol.getModule(AnnealingProgress.class);
		
		if (progress != null) {
			String progressFile = getBranchFileName(branch, prefix,
					AnnealingProgress.PROGRESS_FILE_NAME, true);
			Preconditions.checkState(!writtenFiles.contains(progressFile));
			CSV_BackedModule.writeToArchive(progress.getCSV(), zout, entryPrefix, progressFile);
			writtenFiles.add(progressFile);
		}
		
		InversionMisfitProgress misfitProgress = sol.getModule(InversionMisfitProgress.class);
		
		if (misfitProgress != null) {
			String progressFile = getBranchFileName(branch, prefix,
					InversionMisfitProgress.MISFIT_PROGRESS_FILE_NAME, true);
			Preconditions.checkState(!writtenFiles.contains(progressFile));
			CSV_BackedModule.writeToArchive(misfitProgress.getCSV(), zout, entryPrefix, progressFile);
			writtenFiles.add(progressFile);
		}
		
		// use rupture-sections file to figure out which things affect plausibility
		List<? extends LogicTreeLevel<?>> rupSectLevels = getLevelsAffectingFile(
				FaultSystemRupSet.RUP_SECTS_FILE_NAME, true);
		String plausibilityFile = getBranchFileName(branch, prefix,
				PlausibilityConfiguration.JSON_FILE_NAME, rupSectLevels);
		if (!writtenFiles.contains(plausibilityFile)) {
			PlausibilityConfiguration plausibility = rupSet.getModule(PlausibilityConfiguration.class);
			
			if (plausibility != null) {
				plausibility.writeToArchive(zout, entryPrefix, plausibilityFile);
				writtenFiles.add(plausibilityFile);
			}
		}
	}
	
	// cache files
	private List<? extends FaultSection> prevSubSects;
	private String prevSectsFile;
	
	private List<List<Integer>> prevRupIndices;
	private String prevIndicesFile;
	
	private RuptureProperties prevProps;
	private String prevPropsFile;
	
	private GriddedRegion prevGridReg;
	private String prevGridRegFile;
	
	private CSVFile<String> prevGridMechs;
	private String prevGridMechFile;
	
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
		System.out.println("Loading rupture set for logic tree branch: "+branch);
		ZipFile zip = getZipFile();
		String entryPrefix = null; // prefixes will be encoded in the results of getBranchFileName(...) calls
		
		String sectsFile = getBranchFileName(branch, FaultSystemRupSet.SECTS_FILE_NAME, true);
		List<? extends FaultSection> subSects;
		if (prevSubSects != null && sectsFile.equals(prevSectsFile)) {
			System.out.println("\tRe-using previous section data");
			subSects = prevSubSects;
		} else {
			System.out.println("\tLoading section data from "+sectsFile);
			subSects = GeoJSONFaultReader.readFaultSections(
					new InputStreamReader(FileBackedModule.getInputStream(zip, entryPrefix, sectsFile)));
			for (int s=0; s<subSects.size(); s++)
				Preconditions.checkState(subSects.get(s).getSectionId() == s,
						"Fault sections must be provided in order starting with ID=0");
			prevSubSects = subSects;
			prevSectsFile = sectsFile;
		}
		
		String indicesFile = getBranchFileName(branch, FaultSystemRupSet.RUP_SECTS_FILE_NAME, true);
		List<List<Integer>> rupIndices;
		if (prevRupIndices != null && indicesFile.equals(prevIndicesFile)) {
			System.out.println("\tRe-using previous rupture indices");
			rupIndices = prevRupIndices;
		} else {
			System.out.println("\tLoading rupture indices from "+indicesFile);
			CSVFile<String> rupSectsCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, indicesFile);
			rupIndices = FaultSystemRupSet.loadRupSectsCSV(rupSectsCSV, subSects.size());
			prevRupIndices = rupIndices;
			prevIndicesFile = indicesFile;
		}
		
		String propsFile = getBranchFileName(branch, FaultSystemRupSet.RUP_PROPS_FILE_NAME, true);
		RuptureProperties props;
		if (prevProps != null && propsFile.equals(prevPropsFile)) {
			System.out.println("\tRe-using previous rupture properties");
			props = prevProps;
		} else {
			System.out.println("\tLoading rupture properties from "+propsFile);
			CSVFile<String> rupPropsCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, propsFile);
			props = new RuptureProperties(rupPropsCSV);
			prevProps = props;
			prevPropsFile = propsFile;
		}
		
		FaultSystemRupSet rupSet = new FaultSystemRupSet(subSects, rupIndices,
				props.mags, props.rakes, props.areas, props.lengths);
		if (process && processor != null)
			rupSet = processor.processRupSet(rupSet, branch);
		
		String ratesFile = getBranchFileName(branch, FaultSystemSolution.RATES_FILE_NAME, true);
		System.out.println("\tLoading rate data from "+ratesFile);
		CSVFile<String> ratesCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, ratesFile);
		double[] rates = FaultSystemSolution.loadRatesCSV(ratesCSV);
		Preconditions.checkState(rates.length == rupIndices.size());
		
		rupSet.addModule(branch);
		
		FaultSystemSolution sol = new FaultSystemSolution(rupSet, rates);
		if (process && processor != null)
			sol = processor.processSolution(sol, branch);
		
		sol.addModule(branch);
		
		String gridRegFile = getBranchFileName(branch, AbstractGridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME, false);
		String mechFile = getBranchFileName(branch, AbstractGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME, false);
		if (gridRegFile != null && zip.getEntry(gridRegFile) != null && mechFile != null && zip.getEntry(mechFile) != null) {
			sol.addAvailableModule(new Callable<GridSourceProvider>() {

				@Override
				public GridSourceProvider call() throws Exception {
					GriddedRegion region;
					CSVFile<String> mechCSV;
					synchronized (SolutionLogicTree.this) {
						if (prevGridReg != null && gridRegFile.equals(prevGridRegFile)) {
							region = prevGridReg;
						} else {
							BufferedInputStream regionIS = FileBackedModule.getInputStream(zip, entryPrefix, gridRegFile);
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
							mechCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, mechFile);
							prevGridMechs = mechCSV;
							prevGridMechFile = mechFile;
						}
					}
					
					CSVFile<String> subSeisCSV = null;
					CSVFile<String> nodeUnassociatedCSV = null;
					
					String subSeisFile = getBranchFileName(branch, AbstractGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME, true);
					String nodeUnassociatedFile = getBranchFileName(branch, AbstractGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME, true);
					if (subSeisFile != null && zip.getEntry(subSeisFile) != null)
						subSeisCSV = AbstractGridSourceProvider.Precomputed.loadCSV(zip, entryPrefix, subSeisFile);
					if (nodeUnassociatedFile != null && zip.getEntry(nodeUnassociatedFile) != null)
						nodeUnassociatedCSV = AbstractGridSourceProvider.Precomputed.loadCSV(zip, entryPrefix, nodeUnassociatedFile);
					
					return new AbstractGridSourceProvider.Precomputed(region, subSeisCSV, nodeUnassociatedCSV, mechCSV);
				}
			}, GridSourceProvider.class);
		}
		
		String statsFile = getBranchFileName(branch, InversionMisfitStats.MISFIT_STATS_FILE_NAME, true);
		if (statsFile != null && zip.getEntry(statsFile) != null) {
			CSVFile<String> misfitStatsCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, statsFile);
			InversionMisfitStats stats = new InversionMisfitStats(null);
			stats.initFromCSV(misfitStatsCSV);
			sol.addModule(stats);
		}
		
		String progressFile = getBranchFileName(branch, AnnealingProgress.PROGRESS_FILE_NAME, true);
		if (progressFile != null && zip.getEntry(progressFile) != null) {
			CSVFile<String> progressCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, progressFile);
			AnnealingProgress progress = new AnnealingProgress(progressCSV);
			sol.addModule(progress);
		}
		
		String misfitProgressFile = getBranchFileName(branch, InversionMisfitProgress.MISFIT_PROGRESS_FILE_NAME, true);
		if (misfitProgressFile != null && zip.getEntry(misfitProgressFile) != null) {
			CSVFile<String> progressCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, misfitProgressFile);
			InversionMisfitProgress progress = new InversionMisfitProgress(progressCSV);
			sol.addModule(progress);
		}
		
		// use rupture-sections file to figure out which things affect plausibility
		List<? extends LogicTreeLevel<?>> rupSectLevels = getLevelsAffectingFile(
				FaultSystemRupSet.RUP_SECTS_FILE_NAME, true);
		String plausibilityFile = getBranchFileName(branch, PlausibilityConfiguration.JSON_FILE_NAME, rupSectLevels);
		if (plausibilityFile != null && zip.getEntry(plausibilityFile) != null) {
			BufferedInputStream zin = FileBackedModule.getInputStream(zip, entryPrefix, plausibilityFile);
			InputStreamReader reader = new InputStreamReader(zin);
			PlausibilityConfiguration plausibility = PlausibilityConfiguration.readJSON(
					reader, rupSet.getFaultSectionDataList());
			reader.close();
			rupSet.addModule(plausibility);
		}
		
		return sol;
	}
	
	public void write(File outputFile) throws IOException {
		ModuleArchive<SolutionLogicTree> archive = new ModuleArchive<>();
		
		archive.addModule(this);
		
		archive.write(outputFile);
	}
	
	public static SolutionLogicTree load(File treeFile) throws IOException {
		ModuleArchive<SolutionLogicTree> archive = new ModuleArchive<>(treeFile, SolutionLogicTree.class);
		
		return archive.requireModule(SolutionLogicTree.class);
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

	@Override
	public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		super.writeToArchive(zout, entryPrefix);
		
		if (processor != null)
			writeProcessorJSON(zout, buildPrefix(entryPrefix), processor);
	}

	private static void writeProcessorJSON(ZipOutputStream zout, String prefix, SolutionProcessor processor)
			throws IOException {
		// check for no-arg constructor
		try {
			Preconditions.checkNotNull(processor.getClass().getDeclaredConstructor());
		} catch (Throwable t) {
			System.err.println("WARNING: Loading class for solution logic tree processor doesn't contain a"
					+ "no-arg constructor, loading from a zip file will fail: "+processor.getClass().getName());
		}
		
		// write the logic tree
		FileBackedModule.initEntry(zout, prefix, PROCESSOR_FILE_NAME);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zout));
		gson.toJson(processor.getClass().getName(), String.class, writer);
		writer.flush();
		zout.flush();
		zout.closeEntry();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		super.initFromArchive(zip, entryPrefix);
		
		// see if we have a processor
		String outPrefix = buildPrefix(entryPrefix);
		if (FileBackedModule.hasEntry(zip, outPrefix, PROCESSOR_FILE_NAME)) {
			BufferedInputStream is = FileBackedModule.getInputStream(zip, outPrefix, PROCESSOR_FILE_NAME);
			
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
				System.out.println("Building instance: "+processorClass.getName());
				this.processor = constructor.newInstance();
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Error loading solution processor, skipping.");
			}
		}
	}

	public static void main(String[] args) throws IOException {
		File dir = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2021_11_23-u3_branches-FM3_1-5h/");
		SolutionLogicTree tree = SolutionLogicTree.load(new File(dir, "results.zip"));
//		
//		FaultSystemSolution ba = tree.calcBranchAveraged();
//		
//		ba.write(new File(dir, "branch_averaged.zip"));
		
//		SolutionLogicTree tree = SolutionLogicTree.load(new File("/tmp/results.zip"));
		if (tree.processor == null)
			System.out.println("No solution processor");
		else
			System.out.println("Solution processor type: "+tree.processor.getClass().getName());
		
		FileBuilder builder = new FileBuilder(tree.processor, new File("/tmp/sol_tree_test.zip"));
		BranchAverageSolutionCreator avgBuilder = new BranchAverageSolutionCreator(tree.getLogicTree().getWeightProvider());
		for (LogicTreeBranch<?> branch : tree.getLogicTree()) {
			if (Math.random() < 0.05) {
				FaultSystemSolution sol = tree.forBranch(branch);
				builder.solution(sol, branch);
				if (branch.getValue(FaultModels.class) == FaultModels.FM3_1)
					avgBuilder.addSolution(sol, branch);
			}
		}
		
		builder.build();
		FaultSystemSolution avgSol = avgBuilder.build();
		avgSol.write(new File("/tmp/sol_tree_test_ba.zip"));
	}

}
