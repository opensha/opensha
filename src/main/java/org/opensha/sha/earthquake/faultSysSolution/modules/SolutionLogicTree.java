package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet.RuptureProperties;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolutionFetcher;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.griddedSeismicity.UCERF3_GridSourceGenerator;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

/**
 * Module that stores/loads fault system solutions for individual branches of a logic tree.
 * 
 * @author kevin
 *
 */
public abstract class SolutionLogicTree extends AbstractBranchAveragedModule {
	
	private boolean serializeGridded = true;
	
	public static class UCERF3 extends SolutionLogicTree {

		private FaultSystemSolutionFetcher oldFetcher;

		private UCERF3() {
			super(null, null, null);
		}
		
		public UCERF3(FaultSystemSolutionFetcher oldFetcher) {
			super(null, null, LogicTree.fromExisting(U3LogicTreeBranch.getLogicTreeLevels(), oldFetcher.getBranches()));
			this.oldFetcher = oldFetcher;
		}
		
		@Override
		public synchronized FaultSystemSolution forBranch(LogicTreeBranch<?> branch) throws IOException {
			if (oldFetcher != null)
				return oldFetcher.getSolution(asU3Branch(branch));
			return super.forBranch(branch);
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
			List<LogicTreeBranchNode<?>> vals = new ArrayList<>();
			for (LogicTreeNode val : branch) {
				Preconditions.checkState(val instanceof LogicTreeBranchNode);
				vals.add((LogicTreeBranchNode<?>) val);
			}
			return U3LogicTreeBranch.fromValues(vals);
		}

		@Override
		protected List<? extends LogicTreeLevel<?>> getLevelsForFaultSections() {
			return List.of(getLevelForType(FaultModels.class), getLevelForType(DeformationModels.class));
		}

		@Override
		protected List<? extends LogicTreeLevel<?>> getLevelsForRuptureSectionIndices() {
			return List.of(getLevelForType(FaultModels.class));
		}

		@Override
		protected List<? extends LogicTreeLevel<?>> getLevelsForRuptureProperties() {
			return List.of(getLevelForType(FaultModels.class), getLevelForType(DeformationModels.class),
					getLevelForType(ScalingRelationships.class));
		}

		@Override
		protected List<? extends LogicTreeLevel<?>> getLevelsForRuptureRates() {
			return getLogicTree().getLevels();
		}

		@Override
		protected List<? extends LogicTreeLevel<?>> getLevelsForGridRegion() {
			return List.of();
		}

		@Override
		protected List<? extends LogicTreeLevel<?>> getLevelsForGridMechs() {
			return List.of();
		}

		@Override
		protected List<? extends LogicTreeLevel<?>> getLevelsForGridMFDs() {
			return getLogicTree().getLevels();
		}
		
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
	protected List<? extends LogicTreeLevel<?>> getLevelsAffectingFile(String fileName) {
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
			return null;
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
		
		return sol;
	}

}
