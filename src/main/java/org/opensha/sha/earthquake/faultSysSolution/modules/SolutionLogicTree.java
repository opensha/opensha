package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.dom4j.Element;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet.RuptureProperties;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.AnnealingProgress;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolutionFetcher;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.griddedSeismicity.UCERF3_GridSourceGenerator;
import scratch.UCERF3.logicTree.U3LogicTreeBranchNode;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

/**
 * Module that stores/loads fault system solutions for individual branches of a logic tree.
 * 
 * @author kevin
 *
 */
public abstract class SolutionLogicTree extends AbstractBranchAveragedModule {
	
	private boolean serializeGridded = true;
	
	public static class UCERF3 extends AbstractExternalFetcher {

		private FaultSystemSolutionFetcher oldFetcher;

		private UCERF3() {
			super(null);
		}
		
		public UCERF3(LogicTree<?> logicTree) {
			super(logicTree);
		}
		
		public UCERF3(FaultSystemSolutionFetcher oldFetcher) {
			super(LogicTree.fromExisting(U3LogicTreeBranch.getLogicTreeLevels(), oldFetcher.getBranches()));
			this.oldFetcher = oldFetcher;
		}

		@Override
		protected FaultSystemSolution loadExternalForBranch(LogicTreeBranch<?> branch) throws IOException {
			if (oldFetcher != null)
				return oldFetcher.getSolution(asU3Branch(branch));
			return null;
		}

		@Override
		protected FaultSystemRupSet processRupSet(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
//			System.out.println("Start process");
			rupSet = FaultSystemRupSet.buildFromExisting(rupSet).u3BranchModules(asU3Branch(branch)).build();
//			System.out.println("End process");
			return rupSet;
		}

		@Override
		protected FaultSystemSolution processSolution(FaultSystemSolution sol, LogicTreeBranch<?> branch) {
			sol.addAvailableModule(new Callable<SubSeismoOnFaultMFDs>() {

				@Override
				public SubSeismoOnFaultMFDs call() throws Exception {
					FaultSystemRupSet rupSet = sol.getRupSet();
					return new SubSeismoOnFaultMFDs(
							rupSet.requireModule(InversionTargetMFDs.class).getOnFaultSubSeisMFDs().getAll());
				}
			}, SubSeismoOnFaultMFDs.class);
			sol.addAvailableModule(new Callable<GridSourceProvider>() {

				@Override
				public GridSourceProvider call() throws Exception {
					FaultSystemRupSet rupSet = sol.getRupSet();
					return new UCERF3_GridSourceGenerator(sol, branch.getValue(SpatialSeisPDF.class),
							branch.getValue(MomentRateFixes.class),
							rupSet.requireModule(InversionTargetMFDs.class),
							sol.requireModule(SubSeismoOnFaultMFDs.class),
							branch.getValue(MaxMagOffFault.class).getMaxMagOffFault(),
							rupSet.requireModule(FaultGridAssociations.class));
				}
			}, GridSourceProvider.class);
			return sol;
		}
		
		private U3LogicTreeBranch asU3Branch(LogicTreeBranch<?> branch) {
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

		@Override
		public List<? extends LogicTreeLevel<?>> getLevelsForFaultSections() {
			return List.of(getLevelForType(FaultModels.class), getLevelForType(DeformationModels.class));
		}

		@Override
		public List<? extends LogicTreeLevel<?>> getLevelsForRuptureSectionIndices() {
			return List.of(getLevelForType(FaultModels.class));
		}

		@Override
		public List<? extends LogicTreeLevel<?>> getLevelsForRuptureProperties() {
			return List.of(getLevelForType(FaultModels.class), getLevelForType(DeformationModels.class),
					getLevelForType(ScalingRelationships.class));
		}

		@Override
		public List<? extends LogicTreeLevel<?>> getLevelsForRuptureRates() {
			return getLogicTree().getLevels();
		}

		@Override
		public List<? extends LogicTreeLevel<?>> getLevelsForGridRegion() {
			return List.of();
		}

		@Override
		public List<? extends LogicTreeLevel<?>> getLevelsForGridMechs() {
			return List.of();
		}

		@Override
		public List<? extends LogicTreeLevel<?>> getLevelsForGridMFDs() {
			return getLogicTree().getLevels();
		}
		
	}
	
	public static abstract class AbstractExternalFetcher extends SolutionLogicTree {

		protected AbstractExternalFetcher(LogicTree<?> logicTree) {
			super(null, null, logicTree);
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
	
	protected SolutionLogicTree(LogicTree<?> logicTree) {
		super(null, null, logicTree);
	}

	protected SolutionLogicTree(ZipFile zip, String prefix, LogicTree<?> logicTree) {
		super(zip, prefix, logicTree);
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
	public List<? extends LogicTreeLevel<?>> getLevelsAffectingFile(String fileName) {
		switch (fileName) {
		case FaultSystemRupSet.SECTS_FILE_NAME:
			return getLevelsForFaultSections();
		case FaultSystemRupSet.RUP_SECTS_FILE_NAME:
			return getLevelsForRuptureSectionIndices();
		case FaultSystemRupSet.RUP_PROPS_FILE_NAME:
			return getLevelsForRuptureProperties();
		case FaultSystemSolution.RATES_FILE_NAME:
			return getLevelsForRuptureRates();
		case AbstractGridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME:
			return getLevelsForGridRegion();
		case AbstractGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME:
			return getLevelsForGridMechs();
		case AbstractGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME:
			return getLevelsForGridMFDs();
		case AbstractGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME:
			return getLevelsForGridMFDs();

		default:
			return getLogicTree().getLevels();
		}
	}
	
	protected abstract List<? extends LogicTreeLevel<?>> getLevelsForFaultSections();
	
	protected abstract List<? extends LogicTreeLevel<?>> getLevelsForRuptureSectionIndices();
	
	protected abstract List<? extends LogicTreeLevel<?>> getLevelsForRuptureProperties();
	
	protected abstract List<? extends LogicTreeLevel<?>> getLevelsForRuptureRates();
	
	protected abstract List<? extends LogicTreeLevel<?>> getLevelsForGridRegion();
	
	protected abstract List<? extends LogicTreeLevel<?>> getLevelsForGridMechs();
	
	protected abstract List<? extends LogicTreeLevel<?>> getLevelsForGridMFDs();

	@Override
	protected void writeBranchFilesToArchive(ZipOutputStream zout, String prefix, LogicTreeBranch<?> branch,
			HashSet<String> writtenFiles) throws IOException {
		// could try to be fancy and copy files over without loading, but these things will be written out so rarely
		// (usually one and done) so it's not worth the added complexity
		FaultSystemSolution sol = forBranch(branch);
		FaultSystemRupSet rupSet = sol.getRupSet();
		
		String entryPrefix = null; // prefixes will be encoded in the results of getBranchFileName(...) calls
		
		String sectsFile = getBranchFileName(branch, prefix, FaultSystemRupSet.SECTS_FILE_NAME);
		if (!writtenFiles.contains(sectsFile)) {
			FileBackedModule.initEntry(zout, entryPrefix, sectsFile);
			OutputStreamWriter writer = new OutputStreamWriter(zout);
			GeoJSONFaultReader.writeFaultSections(writer, rupSet.getFaultSectionDataList());
			writer.flush();
			zout.flush();
			zout.closeEntry();
			writtenFiles.add(sectsFile);
		}
		
		String indicesFile = getBranchFileName(branch, prefix, FaultSystemRupSet.RUP_SECTS_FILE_NAME);
		if (!writtenFiles.contains(indicesFile)) {
			CSV_BackedModule.writeToArchive(FaultSystemRupSet.buildRupSectsCSV(rupSet), zout, entryPrefix, indicesFile);
			writtenFiles.add(indicesFile);
		}
		
		String propsFile = getBranchFileName(branch, prefix, FaultSystemRupSet.RUP_PROPS_FILE_NAME);
		if (!writtenFiles.contains(propsFile)) {
			CSV_BackedModule.writeToArchive(new RuptureProperties(rupSet).buildCSV(), zout, entryPrefix, propsFile);
			writtenFiles.add(propsFile);
		}
		
		String ratesFile = getBranchFileName(branch, prefix, FaultSystemSolution.RATES_FILE_NAME);
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
			
			String gridRegFile = getBranchFileName(branch, prefix, AbstractGridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME);
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

			String mechFile = getBranchFileName(branch, prefix, AbstractGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME);
			if (mechFile != null && !writtenFiles.contains(mechFile)) {
				CSV_BackedModule.writeToArchive(precomputed.buildWeightsCSV(), zout, entryPrefix, mechFile);
				writtenFiles.add(mechFile);
			}
			String subSeisFile = getBranchFileName(branch, prefix, AbstractGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME);
			if (subSeisFile != null && !writtenFiles.contains(subSeisFile)) {
				CSVFile<String> csv = precomputed.buildSubSeisCSV();
				if (csv != null) {
					CSV_BackedModule.writeToArchive(csv, zout, entryPrefix, subSeisFile);
					writtenFiles.add(subSeisFile);
				}
			}
			String unassociatedFile = getBranchFileName(branch, prefix, AbstractGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME);
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
			String statsFile = getBranchFileName(branch, prefix, InversionMisfitStats.MISFIT_STATS_FILE_NAME);
			Preconditions.checkState(!writtenFiles.contains(statsFile));
			CSV_BackedModule.writeToArchive(misfitStats.getCSV(), zout, entryPrefix, statsFile);
			writtenFiles.add(statsFile);
		}
		
		AnnealingProgress progress = sol.getModule(AnnealingProgress.class);
		
		if (progress != null) {
			String progressFile = getBranchFileName(branch, prefix, AnnealingProgress.PROGRESS_FILE_NAME);
			Preconditions.checkState(!writtenFiles.contains(progressFile));
			CSV_BackedModule.writeToArchive(progress.getCSV(), zout, entryPrefix, progressFile);
			writtenFiles.add(progressFile);
		}
	}
	
	protected FaultSystemRupSet processRupSet(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
		return rupSet;
	}
	
	protected FaultSystemSolution processSolution(FaultSystemSolution sol, LogicTreeBranch<?> branch) {
		return sol;
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
	
	public synchronized FaultSystemSolution forBranch(LogicTreeBranch<?> branch) throws IOException {
		System.out.println("Loading rupture set for logic tree branch: "+branch);
		ZipFile zip = getZipFile();
		String entryPrefix = null; // prefixes will be encoded in the results of getBranchFileName(...) calls
		
		String sectsFile = getBranchFileName(branch, FaultSystemRupSet.SECTS_FILE_NAME);
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
		
		String indicesFile = getBranchFileName(branch, FaultSystemRupSet.RUP_SECTS_FILE_NAME);
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
		
		String propsFile = getBranchFileName(branch, FaultSystemRupSet.RUP_PROPS_FILE_NAME);
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
		
		FaultSystemRupSet rupSet = processRupSet(new FaultSystemRupSet(subSects, rupIndices,
				props.mags, props.rakes, props.areas, props.lengths), branch);
		
		String ratesFile = getBranchFileName(branch, FaultSystemSolution.RATES_FILE_NAME);
		System.out.println("\tLoading rate data from "+ratesFile);
		CSVFile<String> ratesCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, ratesFile);
		double[] rates = FaultSystemSolution.loadRatesCSV(ratesCSV);
		Preconditions.checkState(rates.length == rupIndices.size());
		
		rupSet.addModule(branch);
		
		FaultSystemSolution sol = processSolution(new FaultSystemSolution(rupSet, rates), branch);
		
		sol.addModule(branch);
		
		String gridRegFile = getBranchFileName(branch, AbstractGridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME);
		String mechFile = getBranchFileName(branch, AbstractGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME);
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
					
					String subSeisFile = getBranchFileName(branch, AbstractGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME);
					String nodeUnassociatedFile = getBranchFileName(branch, AbstractGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME);
					if (subSeisFile != null && zip.getEntry(subSeisFile) != null)
						subSeisCSV = AbstractGridSourceProvider.Precomputed.loadCSV(zip, entryPrefix, subSeisFile);
					if (nodeUnassociatedFile != null && zip.getEntry(nodeUnassociatedFile) != null)
						nodeUnassociatedCSV = AbstractGridSourceProvider.Precomputed.loadCSV(zip, entryPrefix, nodeUnassociatedFile);
					
					return new AbstractGridSourceProvider.Precomputed(region, subSeisCSV, nodeUnassociatedCSV, mechCSV);
				}
			}, GridSourceProvider.class);
		}
		
		String statsFile = getBranchFileName(branch, InversionMisfitStats.MISFIT_STATS_FILE_NAME);
		if (statsFile != null && zip.getEntry(statsFile) != null) {
			CSVFile<String> misfitStatsCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, statsFile);
			InversionMisfitStats stats = new InversionMisfitStats(null);
			stats.initFromCSV(misfitStatsCSV);
			sol.addModule(stats);
		}
		
		String progressFile = getBranchFileName(branch, AnnealingProgress.PROGRESS_FILE_NAME);
		if (progressFile != null && zip.getEntry(progressFile) != null) {
			CSVFile<String> progressCSV = CSV_BackedModule.loadFromArchive(zip, entryPrefix, progressFile);
			AnnealingProgress progress = new AnnealingProgress(progressCSV);
			sol.addModule(progress);
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
		double totWeight = 0d; 
		double[] avgRates = null;
		double[] avgMags = null;
		double[] avgAreas = null;
		double[] avgLengths = null;
		double[] avgSlips = null;
		List<List<Double>> avgRakes = null;
		List<List<Integer>> sectIndices = null;
		List<DiscretizedFunc> rupMFDs = null;
		
		FaultSystemRupSet refRupSet = null;
		double[] avgSectAseis = null;
		double[] avgSectCoupling = null;
		double[] avgSectSlipRates = null;
		double[] avgSectSlipRateStdDevs = null;
		List<List<Double>> avgSectRakes = null;
		
		// related to gridded seismicity
		GridSourceProvider refGridProv = null;
		GriddedRegion gridReg = null;
		Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs = null;
		Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs = null;
		List<IncrementalMagFreqDist> sectSubSeisMFDs = null;
		
		List<? extends LogicTreeBranch<?>> branches = getLogicTree().getBranches();
		
		LogicTreeBranch<LogicTreeNode> combBranch = null;
		
		List<Double> weights = new ArrayList<>();
		
		PaleoseismicConstraintData paleoData = null;
		
		NamedFaults namedFaults = null;
		
		Map<LogicTreeNode, Integer> nodeCounts = new HashMap<>();
		
		for (LogicTreeBranch<?> branch : branches) {
			double weight = branch.getBranchWeight();
			weights.add(weight);
			totWeight += weight;
			
			FaultSystemSolution sol = forBranch(branch);
			FaultSystemRupSet rupSet = sol.getRupSet();
			GridSourceProvider gridProv = sol.getGridSourceProvider();
			SubSeismoOnFaultMFDs ssMFDs = sol.getModule(SubSeismoOnFaultMFDs.class);
			
			if (avgRates == null) {
				// first time
				avgRates = new double[rupSet.getNumRuptures()];
				avgMags = new double[avgRates.length];
				avgAreas = new double[avgRates.length];
				avgLengths = new double[avgRates.length];
				avgRakes = new ArrayList<>();
				for (int r=0; r<rupSet.getNumRuptures(); r++)
					avgRakes.add(new ArrayList<>());
				
				refRupSet = rupSet;
				
				avgSectAseis = new double[rupSet.getNumSections()];
				avgSectSlipRates = new double[rupSet.getNumSections()];
				avgSectSlipRateStdDevs = new double[rupSet.getNumSections()];
				avgSectCoupling = new double[rupSet.getNumSections()];
				avgSectRakes = new ArrayList<>();
				for (int s=0; s<rupSet.getNumSections(); s++)
					avgSectRakes.add(new ArrayList<>());
				
				if (gridProv != null) {
					refGridProv = gridProv;
					gridReg = gridProv.getGriddedRegion();
					nodeSubSeisMFDs = new HashMap<>();
					nodeUnassociatedMFDs = new HashMap<>();
				}
				
				if (ssMFDs != null) {
					sectSubSeisMFDs = new ArrayList<>();
					for (int s=0; s<rupSet.getNumSections(); s++)
						sectSubSeisMFDs.add(null);
				}
				
				if (rupSet.hasModule(AveSlipModule.class))
					avgSlips = new double[avgRates.length];
				
				combBranch = (LogicTreeBranch<LogicTreeNode>)branch.copy();
				sectIndices = rupSet.getSectionIndicesForAllRups();
				rupMFDs = new ArrayList<>();
				for (int r=0; r<avgRates.length; r++)
					rupMFDs.add(new ArbitrarilyDiscretizedFunc());
				
				paleoData = rupSet.getModule(PaleoseismicConstraintData.class);
				
				namedFaults = rupSet.getModule(NamedFaults.class);
			} else {
				Preconditions.checkState(refRupSet.isEquivalentTo(rupSet), "Rupture sets are not equivalent");
				if (refGridProv != null)
					Preconditions.checkNotNull(gridProv, "Some solutions have grid source providers and others don't");
				
				if (paleoData != null) {
					// see if it's the same
					PaleoseismicConstraintData myPaleoData = rupSet.getModule(PaleoseismicConstraintData.class);
					if (myPaleoData != null) {
						boolean same = paleoConstraintsSame(paleoData.getPaleoRateConstraints(),
								myPaleoData.getPaleoRateConstraints());
						same = same && paleoConstraintsSame(paleoData.getPaleoSlipConstraints(),
								myPaleoData.getPaleoSlipConstraints());
						if (same && paleoData.getPaleoProbModel() != null)
							same = paleoData.getPaleoProbModel().getClass().equals(myPaleoData.getPaleoProbModel().getClass());
						if (same && paleoData.getPaleoSlipProbModel() != null)
							same = paleoData.getPaleoSlipProbModel().getClass().equals(myPaleoData.getPaleoSlipProbModel().getClass());
						if (!same)
							paleoData = null;
					} else {
						// not all branches have it
						paleoData = null;
					}
				}
			}
			
			for (int i=0; i<combBranch.size(); i++) {
				LogicTreeNode combVal = combBranch.getValue(i);
				LogicTreeNode branchVal = branch.getValue(i);
				if (combVal != null && !combVal.equals(branchVal))
					combBranch.clearValue(i);
				int prevCount = nodeCounts.containsKey(branchVal) ? nodeCounts.get(branchVal) : 0;
				nodeCounts.put(branchVal, prevCount+1);
			}
			
			AveSlipModule slipModule = rupSet.getModule(AveSlipModule.class);
			if (avgSlips != null)
				Preconditions.checkNotNull(slipModule);
			addWeighted(avgRates, sol.getRateForAllRups(), weight);
			for (int r=0; r<avgRates.length; r++) {
				double rate = sol.getRateForRup(r);
				double mag = rupSet.getMagForRup(r);
				DiscretizedFunc rupMFD = rupMFDs.get(r);
				double y = rate*weight;
				if (rupMFD.hasX(mag))
					y += rupMFD.getY(mag);
				rupMFD.set(mag, y);
				avgRakes.get(r).add(rupSet.getAveRakeForRup(r));
				
				if (avgSlips != null)
					avgSlips[r] += weight*slipModule.getAveSlip(r);
			}
			addWeighted(avgMags, rupSet.getMagForAllRups(), weight);
			addWeighted(avgAreas, rupSet.getAreaForAllRups(), weight);
			addWeighted(avgLengths, rupSet.getLengthForAllRups(), weight);
			
			for (int s=0; s<rupSet.getNumSections(); s++) {
				FaultSection sect = rupSet.getFaultSectionData(s);
				avgSectAseis[s] += sect.getAseismicSlipFactor()*weight;
				avgSectSlipRates[s] += sect.getOrigAveSlipRate()*weight;
				avgSectSlipRateStdDevs[s] += sect.getOrigSlipRateStdDev()*weight;
				avgSectCoupling[s] += sect.getCouplingCoeff()*weight;
				avgSectRakes.get(s).add(sect.getAveRake());
			}
			
			if (gridProv != null) {
				Preconditions.checkNotNull(refGridProv, "Some solutions have grid source providers and others don't");
				for (int i=0; i<gridReg.getNodeCount(); i++) {
					addWeighted(nodeSubSeisMFDs, i, gridProv.getNodeSubSeisMFD(i), weight);
					addWeighted(nodeUnassociatedMFDs, i, gridProv.getNodeUnassociatedMFD(i), weight);
				}
			}
			if (ssMFDs == null) {
				Preconditions.checkState(sectSubSeisMFDs == null, "Some solutions have sub seismo MFDs and others don't");
			} else {
				Preconditions.checkNotNull(sectSubSeisMFDs, "Some solutions have sub seismo MFDs and others don't");
				for (int s=0; s<rupSet.getNumSections(); s++) {
					IncrementalMagFreqDist subSeisMFD = ssMFDs.get(s);
					Preconditions.checkNotNull(subSeisMFD);
					IncrementalMagFreqDist avgMFD = sectSubSeisMFDs.get(s);
					if (avgMFD == null) {
						avgMFD = new IncrementalMagFreqDist(subSeisMFD.getMinX(), subSeisMFD.getMaxX(), subSeisMFD.size());
						sectSubSeisMFDs.set(s, avgMFD);
					}
					addWeighted(avgMFD, subSeisMFD, weight);
				}
			}
		}
		
		System.out.println("Common branches: "+combBranch);
//		if (!combBranch.hasValue(DeformationModels.class))
//			combBranch.setValue(DeformationModels.MEAN_UCERF3);
//		if (!combBranch.hasValue(ScalingRelationships.class))
//			combBranch.setValue(ScalingRelationships.MEAN_UCERF3);
//		if (!combBranch.hasValue(SlipAlongRuptureModels.class))
//			combBranch.setValue(SlipAlongRuptureModels.MEAN_UCERF3);
		
		// now scale by total weight
		System.out.println("Normalizing by total weight");
		double[] rakes = new double[avgRates.length];
		for (int r=0; r<avgRates.length; r++) {
			avgRates[r] /= totWeight;
			avgMags[r] /= totWeight;
			avgAreas[r] /= totWeight;
			avgLengths[r] /= totWeight;
			DiscretizedFunc rupMFD = rupMFDs.get(r);
			rupMFD.scale(1d/totWeight);
			Preconditions.checkState((float)rupMFD.calcSumOfY_Vals() == (float)avgRates[r]);
			rakes[r] = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(avgRakes.get(r), weights));
			if (avgSlips != null)
				avgSlips[r] /= totWeight;
		}
		
		GridSourceProvider combGridProv = null;
		if (refGridProv != null) {
			double[] fractSS = new double[refGridProv.size()];
			double[] fractR = new double[fractSS.length];
			double[] fractN = new double[fractSS.length];
			for (int i=0; i<fractSS.length; i++) {
				IncrementalMagFreqDist subSeisMFD = nodeSubSeisMFDs.get(i);
				if (subSeisMFD != null)
					subSeisMFD.scale(1d/totWeight);
				IncrementalMagFreqDist nodeUnassociatedMFD = nodeUnassociatedMFDs.get(i);
				if (nodeUnassociatedMFD != null)
					nodeUnassociatedMFD.scale(1d/totWeight);
				fractSS[i] = refGridProv.getFracStrikeSlip(i);
				fractR[i] = refGridProv.getFracReverse(i);
				fractN[i] = refGridProv.getFracNormal(i);
			}
			
			
			combGridProv = new AbstractGridSourceProvider.Precomputed(refGridProv.getGriddedRegion(),
					nodeSubSeisMFDs, nodeUnassociatedMFDs, fractSS, fractN, fractR);
		}
		if (sectSubSeisMFDs != null)
			for (int s=0; s<sectSubSeisMFDs.size(); s++)
				sectSubSeisMFDs.get(s).scale(1d/totWeight);
		
		List<FaultSection> subSects = new ArrayList<>();
		for (int s=0; s<refRupSet.getNumSections(); s++) {
			FaultSection refSect = refRupSet.getFaultSectionData(s);
			
			avgSectAseis[s] /= totWeight;
			avgSectCoupling[s] /= totWeight;
			avgSectSlipRates[s] /= totWeight;
			avgSectSlipRateStdDevs[s] /= totWeight;
			double avgRake = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(avgSectRakes.get(s), weights));
			
			GeoJSONFaultSection avgSect = new GeoJSONFaultSection(new AvgFaultSection(refSect, avgSectAseis[s],
					avgSectCoupling[s], avgRake, avgSectSlipRates[s], avgSectSlipRateStdDevs[s]));
			subSects.add(avgSect);
		}
		
//		FaultSystemRupSet avgRupSet = FaultSystemRupSet.builder(subSects, sectIndices).forU3Branch(combBranch).rupMags(avgMags).build();
//		// remove these as they're not correct for branch-averaged
//		avgRupSet.removeModuleInstances(InversionTargetMFDs.class);
//		avgRupSet.removeModuleInstances(SectSlipRates.class);
		FaultSystemRupSet avgRupSet = FaultSystemRupSet.builder(subSects, sectIndices).rupRakes(rakes)
				.rupAreas(avgAreas).rupLengths(avgLengths).rupMags(avgMags).build();
		int numNonNull = 0;
		boolean haveSlipAlong = false;
		for (int i=0; i<combBranch.size(); i++) {
			LogicTreeNode value = combBranch.getValue(i);
			if (value != null) {
				numNonNull++;
				if (value instanceof SlipAlongRuptureModel) {
					avgRupSet.addModule((SlipAlongRuptureModel)value);
					haveSlipAlong = true;
				} else if (value instanceof SlipAlongRuptureModels) {
					avgRupSet.addModule(((SlipAlongRuptureModels)value).getModel());
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
			if (avgSlips == null)
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
			System.out.println("Combined logic tre branch: "+combBranch);
		}
		if (avgSlips != null)
			avgRupSet.addModule(AveSlipModule.precomputed(avgRupSet, avgSlips));
		if (paleoData != null)
			avgRupSet.addModule(paleoData);
		if (namedFaults != null)
			avgRupSet.addModule(namedFaults);
		
		FaultSystemSolution sol = new FaultSystemSolution(avgRupSet, avgRates);
		sol.addModule(combBranch);
		if (combGridProv != null)
			sol.setGridSourceProvider(combGridProv);
		if (sectSubSeisMFDs != null)
			sol.addModule(new SubSeismoOnFaultMFDs(sectSubSeisMFDs));
		sol.addModule(new RupMFDsModule(sol, rupMFDs.toArray(new DiscretizedFunc[0])));
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
	
	private static boolean paleoConstraintsSame(List<? extends SectMappedUncertainDataConstraint> constr1,
			List<? extends SectMappedUncertainDataConstraint> constr2) {
		if ((constr1 == null) != (constr2 == null))
			return false;
		if (constr1 == null && constr2 == null)
			return true;
		if (constr1.size() != constr2.size())
			return false;
		for (int i=0; i<constr1.size(); i++) {
			SectMappedUncertainDataConstraint c1 = constr1.get(i);
			SectMappedUncertainDataConstraint c2 = constr2.get(i);
			if (c1.sectionIndex != c2.sectionIndex)
				return false;
			if ((float)c1.bestEstimate != (float)c2.bestEstimate)
				return false;
		}
		return true;
	}
	
	private class AvgFaultSection implements FaultSection {
		
		private FaultSection refSect;
		private double avgAseis;
		private double avgCoupling;
		private double avgRake;
		private double avgSlip;
		private double avgSlipStdDev;

		public AvgFaultSection(FaultSection refSect, double avgAseis, double avgCoupling, double avgRake, double avgSlip, double avgSlipStdDev) {
			this.refSect = refSect;
			this.avgAseis = avgAseis;
			this.avgCoupling = avgCoupling;
			this.avgRake = avgRake;
			this.avgSlip = avgSlip;
			this.avgSlipStdDev = avgSlipStdDev;
		}

		@Override
		public String getName() {
			return refSect.getName();
		}

		@Override
		public Element toXMLMetadata(Element root) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long getDateOfLastEvent() {
			return refSect.getDateOfLastEvent();
		}

		@Override
		public void setDateOfLastEvent(long dateOfLastEventMillis) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setSlipInLastEvent(double slipInLastEvent) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getSlipInLastEvent() {
			return refSect.getSlipInLastEvent();
		}

		@Override
		public double getAseismicSlipFactor() {
			if ((float)avgCoupling == 0f)
				return 0d;
			return avgAseis;
		}

		@Override
		public void setAseismicSlipFactor(double aseismicSlipFactor) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getCouplingCoeff() {
			if ((float)avgCoupling == 1f)
				return 1d;
			return avgCoupling;
		}

		@Override
		public void setCouplingCoeff(double couplingCoeff) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getAveDip() {
			return refSect.getAveDip();
		}

		@Override
		public double getOrigAveSlipRate() {
			return avgSlip;
		}

		@Override
		public void setAveSlipRate(double aveLongTermSlipRate) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getAveLowerDepth() {
			return refSect.getAveLowerDepth();
		}

		@Override
		public double getAveRake() {
			return avgRake;
		}

		@Override
		public void setAveRake(double aveRake) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getOrigAveUpperDepth() {
			return refSect.getOrigAveUpperDepth();
		}

		@Override
		public float getDipDirection() {
			return refSect.getDipDirection();
		}

		@Override
		public FaultTrace getFaultTrace() {
			return refSect.getFaultTrace();
		}

		@Override
		public int getSectionId() {
			return refSect.getSectionId();
		}

		@Override
		public void setSectionId(int sectID) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setSectionName(String sectName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getParentSectionId() {
			return refSect.getParentSectionId();
		}

		@Override
		public void setParentSectionId(int parentSectionId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getParentSectionName() {
			return refSect.getParentSectionName();
		}

		@Override
		public void setParentSectionName(String parentSectionName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<? extends FaultSection> getSubSectionsList(double maxSubSectionLen, int startId,
				int minSubSections) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getOrigSlipRateStdDev() {
			return avgSlipStdDev;
		}

		@Override
		public void setSlipRateStdDev(double slipRateStdDev) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isConnector() {
			return refSect.isConnector();
		}

		@Override
		public Region getZonePolygon() {
			return refSect.getZonePolygon();
		}

		@Override
		public void setZonePolygon(Region zonePolygon) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Element toXMLMetadata(Element root, String name) {
			throw new UnsupportedOperationException();
		}

		@Override
		public RuptureSurface getFaultSurface(double gridSpacing) {
			throw new UnsupportedOperationException();
		}

		@Override
		public RuptureSurface getFaultSurface(double gridSpacing, boolean preserveGridSpacingExactly,
				boolean aseisReducesArea) {
			throw new UnsupportedOperationException();
		}

		@Override
		public FaultSection clone() {
			throw new UnsupportedOperationException();
		}
		
	}
	
	public static void addWeighted(Map<Integer, IncrementalMagFreqDist> mfdMap, int index,
			IncrementalMagFreqDist newMFD, double weight) {
		if (newMFD == null)
			// simple case
			return;
		IncrementalMagFreqDist runningMFD = mfdMap.get(index);
		if (runningMFD == null) {
			runningMFD = new IncrementalMagFreqDist(newMFD.getMinX(), newMFD.size(), newMFD.getDelta());
			mfdMap.put(index, runningMFD);
		}
		addWeighted(runningMFD, newMFD, weight);
	}
	
	public static void addWeighted(IncrementalMagFreqDist runningMFD,
			IncrementalMagFreqDist newMFD, double weight) {
		Preconditions.checkState(runningMFD.size() == newMFD.size(), "MFD sizes inconsistent");
		Preconditions.checkState((float)runningMFD.getMinX() == (float)newMFD.getMinX(), "MFD min x inconsistent");
		Preconditions.checkState((float)runningMFD.getDelta() == (float)newMFD.getDelta(), "MFD delta inconsistent");
		for (int i=0; i<runningMFD.size(); i++)
			runningMFD.add(i, newMFD.getY(i)*weight);
	}
	
	private static void addWeighted(double[] running, double[] vals, double weight) {
		Preconditions.checkState(running.length == vals.length);
		for (int i=0; i<running.length; i++)
			running[i] += vals[i]*weight;
	}
	
	public static void main(String[] args) throws IOException {
		File dir = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2021_11_23-u3_branches-FM3_1-5h/");
		SolutionLogicTree tree = SolutionLogicTree.load(new File(dir, "results.zip"));
		
		FaultSystemSolution ba = tree.calcBranchAveraged();
		
		ba.write(new File(dir, "branch_averaged.zip"));
	}

}
