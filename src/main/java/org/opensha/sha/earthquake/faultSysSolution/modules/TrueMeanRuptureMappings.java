package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

import com.google.common.base.Preconditions;

public abstract class TrueMeanRuptureMappings extends AbstractLogicTreeModule {

	public static final String SUB_DIRECTORY_NAME = "mappings";
	
	public static final String SECT_MAPPING_FILE_NAME = "sect_mappings.csv";
	public static final String RUP_MAPPING_FILE_NAME = "rup_mappings.csv";
	
	protected List<? extends LogicTreeLevel<?>> sectMappingLevels;
	protected List<LogicTreeLevel<?>> rupMappingLevels;
	
	protected Map<String, int[]> sectFileMappingsCache = new HashMap<>();
	protected Map<String, int[]> rupFileMappingsCache = new HashMap<>();
	
	private TrueMeanRuptureMappings(ZipFile zip, String prefix, LogicTree<?> logicTree) {
		super(zip, prefix, logicTree);
	}

	@Override
	protected void setLogicTreeLevels(List<? extends LogicTreeLevel<?>> levels) {
		super.setLogicTreeLevels(levels);
		
		if (levels == null) {
			sectMappingLevels = null;
			rupMappingLevels = null;
		} else {
			sectMappingLevels = getLevelsAffectingFile(FaultSystemRupSet.SECTS_FILE_NAME, true);
			
			List<? extends LogicTreeLevel<?>> rupSectLevels = getLevelsAffectingFile(
					FaultSystemRupSet.RUP_SECTS_FILE_NAME, true);
			List<? extends LogicTreeLevel<?>> rupPropLevels = getLevelsAffectingFile(
					FaultSystemRupSet.RUP_PROPS_FILE_NAME, true);
			// rupture mappings depend on all levels affecting sections, rup/sect indices, and rupture properties
			rupMappingLevels = new ArrayList<>();
			for (LogicTreeLevel<?> level : levels)
				if (sectMappingLevels.contains(level) || rupSectLevels.contains(level) || rupPropLevels.contains(level))
					rupMappingLevels.add(level);
		}
	}

	@Override
	public String getName() {
		return "True Mean Rupture Mappings";
	}

	@Override
	protected String getSubDirectoryName() {
		return SUB_DIRECTORY_NAME;
	}
	
	private CSVFile<String> buildMappingCSV(String type, int[] mappings) {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("Branch "+type+" Index", "Mapped "+type+" Index");
		for (int i=0; i<mappings.length; i++)
			csv.addLine(i+"", mappings[i]+"");
		return csv;
	}
	
	/**
	 * @param branch
	 * @return array of of size numSects, where each value is the corresponding section index in the true mean solution 
	 */
	public abstract int[] getSectionMappings(LogicTreeBranch<?> branch);
	
	/**
	 * @param branch
	 * @return array of of size numRups, where each value is the corresponding rupture index in the true mean solution 
	 */
	public abstract int[] getRuptureMappings(LogicTreeBranch<?> branch);

	@Override
	protected synchronized Map<String, String> writeBranchFilesToArchive(ZipOutputStream zout, String prefix, LogicTreeBranch<?> branch,
			HashSet<String> writtenFiles) throws IOException {
		Map<String, String> mappings = new LinkedHashMap<>();
		
		String entryPrefix = null; // prefixes will be encoded in the results of getBranchFileName(...) calls
		
		String sectMappingFile = getRecordBranchFileName(branch, prefix, SECT_MAPPING_FILE_NAME, sectMappingLevels, mappings);
		if (writtenFiles.contains(sectMappingFile)) {
			if (!(this instanceof FileBacked)) {
				// already written, validate that it is indeed identical
				// but skip this check if we're just loading it from another archive directly
				int[] sectMappings = getSectionMappings(branch);
				int[] prev = sectFileMappingsCache.get(sectMappingFile);
				Preconditions.checkNotNull(prev, "Already written %s but prev not cached?", sectMappingFile);
				Preconditions.checkState(Arrays.equals(sectMappings, prev),
						"Mappings not identical for %s, new branch: %s", sectMappingFile, branch);
			}
		} else {
			int[] sectMappings = getSectionMappings(branch);
			CSVFile<String> csv = buildMappingCSV("Section", sectMappings);
			
			CSV_BackedModule.writeToArchive(csv, zout, entryPrefix, sectMappingFile);
			
			sectFileMappingsCache.put(sectMappingFile, sectMappings);
			writtenFiles.add(sectMappingFile);
		}
		
		String rupMappingFile = getRecordBranchFileName(branch, prefix, RUP_MAPPING_FILE_NAME, rupMappingLevels, mappings);
		if (writtenFiles.contains(rupMappingFile)) {
			if (!(this instanceof FileBacked)) {
				// already written, validate that it is indeed identical
				// but skip this check if we're just loading it from another archive directly
				int[] rupMappings = getRuptureMappings(branch);
				int[] prev = rupFileMappingsCache.get(rupMappingFile);
				Preconditions.checkNotNull(prev, "Already written %s but prev not cached?", rupMappingFile);
				Preconditions.checkState(Arrays.equals(rupMappings, prev),
						"Mappings not identical for %s, new branch: %s", rupMappingFile, branch);
			}
		} else {
			int[] rupMappings = getRuptureMappings(branch);
			CSVFile<String> csv = buildMappingCSV("Rupture", rupMappings);
			
			CSV_BackedModule.writeToArchive(csv, zout, entryPrefix, rupMappingFile);
			
			rupFileMappingsCache.put(rupMappingFile, rupMappings);
			writtenFiles.add(rupMappingFile);
		}
		return mappings;
	}
	
	
	
	@Override
	public Class<? extends ArchivableModule> getLoadingClass() {
		return FileBacked.class;
	}

	public static TrueMeanRuptureMappings build(LogicTree<?> tree, List<int[]> branchSectMappings,
			List<int[]> branchRupMappings) {
		Preconditions.checkState(tree.size() == branchSectMappings.size());
		Preconditions.checkState(tree.size() == branchRupMappings.size());
		
		Map<LogicTreeBranch<?>, int[]> branchSectMappingsMap = new HashMap<>(tree.size());
		Map<LogicTreeBranch<?>, int[]> branchRupMappingsMap = new HashMap<>(tree.size());
		for (int i=0; i<tree.size(); i++) {
			LogicTreeBranch<?> branch = tree.getBranch(i);
			branchSectMappingsMap.put(branch, branchSectMappings.get(i));
			branchRupMappingsMap.put(branch, branchRupMappings.get(i));
		}
		
		return new InMemory(tree, branchSectMappingsMap, branchRupMappingsMap);
	}
	
	private static class InMemory extends TrueMeanRuptureMappings {
		
		private Map<LogicTreeBranch<?>, int[]> branchSectMappings;
		private Map<LogicTreeBranch<?>, int[]> branchRupMappings;

		private InMemory(LogicTree<?> logicTree,
				Map<LogicTreeBranch<?>, int[]> branchSectMappings, Map<LogicTreeBranch<?>, int[]> branchRupMappings) {
			super(null, null, logicTree);
			this.branchSectMappings = branchSectMappings;
			this.branchRupMappings = branchRupMappings;
		}

		@Override
		public int[] getSectionMappings(LogicTreeBranch<?> branch) {
			return branchSectMappings.get(branch);
		}

		@Override
		public int[] getRuptureMappings(LogicTreeBranch<?> branch) {
			return branchRupMappings.get(branch);
		}

	}
	
	private static class FileBacked extends TrueMeanRuptureMappings {
		
		private FileBacked() {
			super(null, null, null);
		}

		private FileBacked(ZipFile zip, String prefix) {
			super(zip, prefix, null);
		}

		@Override
		public int[] getSectionMappings(LogicTreeBranch<?> branch) {
			String sectMappingFile = getBranchFileName(branch, SECT_MAPPING_FILE_NAME, sectMappingLevels);
			return getCache(sectMappingFile, sectFileMappingsCache);
		}

		@Override
		public int[] getRuptureMappings(LogicTreeBranch<?> branch) {
			String rupMappingFile = getBranchFileName(branch, RUP_MAPPING_FILE_NAME, rupMappingLevels);
			return getCache(rupMappingFile, rupFileMappingsCache);
		}
		
		private synchronized int[] getCache(String fileName, Map<String, int[]> cache) {
			int[] ret = cache.get(fileName);
			if (ret == null) {
				// load it
				ZipFile zip = getZipFile();
				ZipEntry entry = zip.getEntry(fileName);
				Preconditions.checkNotNull(entry, "Entry not found: %s", fileName);
				
				CSVFile<String> csv;
				try {
					csv = CSVFile.readStream(zip.getInputStream(entry), true);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				ret = new int[csv.getNumRows()-1];
				for (int i=0; i<ret.length; i++) {
					int row = i+1;
					int index = csv.getInt(row, 0);
					Preconditions.checkState(index == i,
							"File out of order, expected index %s at row %s, found %s", i, row, index);
					ret[index] = csv.getInt(row, 1);
				}
				cache.put(fileName, ret);
			}
			return ret;
		}

	}

}
