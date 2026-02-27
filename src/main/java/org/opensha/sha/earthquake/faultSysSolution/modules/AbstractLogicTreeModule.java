package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.ModuleHelper;
import org.opensha.commons.util.modules.helpers.FileBackedModule;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Abstract base class for a module that spans multiple logic tree branches.
 * 
 * @author kevin
 *
 */
@ModuleHelper
public abstract class AbstractLogicTreeModule implements ArchivableModule {

	private ArchiveInput input;
	private String prefix;
	private LogicTree<?> logicTree;
	private List<LogicTreeLevel<?>> levels;
	private Map<LogicTreeLevel<?>, Integer> levelIndexes;
	
	protected boolean verbose;

	public static final String LOGIC_TREE_FILE_NAME = "logic_tree.json";
	public static final String LOGIC_TREE_MAPPINGS_FILE_NAME = "logic_tree_mappings.json";

	protected AbstractLogicTreeModule(ArchiveInput input, String prefix, LogicTree<?> logicTree) {
		this.input = input;
		this.prefix = prefix;
		setLogicTree(logicTree);
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
	
	/**
	 * Logic tree specific files will live in their own unique sub-directory, with this name

	 * @return
	 */
	protected abstract String getSubDirectoryName();
	
	private Map<String, List<? extends LogicTreeLevel<?>>> levelsCache = new HashMap<>();
	
	protected List<? extends LogicTreeLevel<?>> getLevelsAffectingFile(String fileName, boolean affectedByDefault) {
		List<? extends LogicTreeLevel<?>> mappingLevels;
		synchronized (levelsCache) {
			if (levelsCache.containsKey(fileName)) {
				mappingLevels = levelsCache.get(fileName);
			} else {
				mappingLevels = getLevelsAffectingFile(fileName, affectedByDefault, this.levels);
				if (mappingLevels == null) {
					if (verbose) System.out.println("no levels specified for file '"+fileName+"', assuming it's affected by all levels");
					mappingLevels = logicTree.getLevels();
				}
				levelsCache.put(fileName, mappingLevels);
			}
		}
		return getLevelsAffectingFile(fileName, affectedByDefault, this.levels);
	}
	
	public static List<? extends LogicTreeLevel<?>> getLevelsAffectingFile(String fileName, boolean affectedByDefault,
			List<LogicTreeLevel<?>> branchLevels) {
		List<LogicTreeLevel<?>> levels = new ArrayList<>();
//		System.out.println("levels affecting "+fileName);
		for (LogicTreeLevel<?> level : branchLevels) {
//			System.out.println(level);
			if (level.affects(fileName, affectedByDefault)) {
				levels.add(level);
//				System.out.println("Affects!");
			}
		}
		return levels;
	}
	
	protected String getBranchFileName(LogicTreeBranch<?> branch, String fileName, boolean affectedByDefault) {
		return getBranchFileName(branch, prefix, fileName, affectedByDefault);
	}
	
	protected String getBranchFileName(LogicTreeBranch<?> branch, String fileName, List<? extends LogicTreeLevel<?>> mappingLevels) {
		return getBranchFileName(branch, prefix, fileName, mappingLevels);
	}

	protected String getBranchFileName(LogicTreeBranch<?> branch, String prefix, String fileName,
			boolean affectedByDefault) {
		return getRecordBranchFileName(branch, prefix, fileName, affectedByDefault, null);
	}
	
	protected String getRecordBranchFileName(LogicTreeBranch<?> branch, String prefix, String fileName,
			boolean affectedByDefault, Map<String, String> mappingTracker) {
		List<? extends LogicTreeLevel<?>> mappingLevels = getLevelsAffectingFile(fileName, affectedByDefault);
		return getRecordBranchFileName(branch, prefix, fileName, mappingLevels, mappingTracker);
	}

	protected String getBranchFileName(LogicTreeBranch<?> branch, String prefix, String fileName,
			List<? extends LogicTreeLevel<?>> mappingLevels) {
		return getRecordBranchFileName(branch, prefix, fileName, mappingLevels, null);
	}

	protected String getRecordBranchFileName(LogicTreeBranch<?> branch, String prefix, String fileName,
			List<? extends LogicTreeLevel<?>> mappingLevels, Map<String, String> mappingTracker) {
		String retStr;
		if (branch == null) {
			retStr = ArchivableModule.getEntryName(prefix, fileName);
		} else {
			StringBuilder ret = new StringBuilder(prefix);
			Preconditions.checkNotNull(mappingLevels, "No mappings available for %", fileName);
			for (LogicTreeLevel<?> level : mappingLevels) {
				int levelIndex = levelIndexes.get(level);
				LogicTreeNode value = branch.getValue(levelIndex);
				Preconditions.checkNotNull(value,
						"Branch does not have value for %s, needed to retrieve %s", level.getName(), fileName);
				ret.append(value.getFilePrefix()).append("/");
			}
			ret.append(fileName);
			retStr = ret.toString();
		}
		if (mappingTracker != null)
			mappingTracker.put(fileName, retStr);
		return retStr;
	}
	
	protected String buildPrefix(String upstreamPrefix) {
		if (upstreamPrefix == null)
			upstreamPrefix = "";
		return upstreamPrefix+getSubDirectoryName()+"/";
	}
	
	protected void setLogicTree(LogicTree<?> logicTree) {
		if (logicTree == null)
			return;
		this.logicTree = logicTree;
		setLogicTreeLevels(logicTree.getLevels());
	}
	
	protected void setLogicTreeLevels(List<? extends LogicTreeLevel<?>> levels) {
		if (levels == null)
			return;
		this.levels = new ArrayList<>(levels);
		this.levelIndexes = new HashMap<>();
		for (int i=0; i<levels.size(); i++)
			levelIndexes.put(levels.get(i), i);
		Preconditions.checkState(levelIndexes.size() == levels.size());
	}
	
	public LogicTree<?> getLogicTree() {
		return logicTree;
	}
	
	/**
	 * This will be called to write all files for the given branch to the zip file. Files should be written starting
	 * with the given prefix, which will end with <code>getSubDirectoryName()+"/"</code>. Implementations must check
	 * that a given file has not yet been written, and add the name of all written files to the writtenFiles set.
	 * 
	 * @param output
	 * @param prefix
	 * @param branch
	 * @param writtenFiles set containing names of all files already written
	 * @return file name mappings for this branch
	 * @throws IOException 
	 */
	protected abstract Map<String, String> writeBranchFilesToArchive(ArchiveOutput output, String prefix, LogicTreeBranch<?> branch,
			HashSet<String> writtenFiles) throws IOException;
	
	protected String getFilePrefix() {
		Preconditions.checkNotNull(prefix, "Not yet initialized");
		return prefix;
	}
	
	protected void setArchiveInput(ArchiveInput input) {
		this.input = input;
	}
	
	protected ArchiveInput getArchiveInput() {
		Preconditions.checkNotNull(input, "Not yet initialized");
		return input;
	}
	
	protected void writeLogicTreeToArchive(ArchiveOutput output, String prefix, LogicTree<?> logicTree)
			throws IOException {
		if (verbose) System.out.println("Writing full logic tree");
		// write the logic tree
		FileBackedModule.initEntry(output, prefix, LOGIC_TREE_FILE_NAME);
		Gson gson = new GsonBuilder().setPrettyPrinting()
				.registerTypeAdapter(LogicTree.class, new LogicTree.Adapter<>()).create();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output.getOutputStream()));
		gson.toJson(logicTree, LogicTree.class, writer);
		writer.flush();
		output.closeEntry();
	}
	
	protected void writeLogicTreeMappingsToArchive(ArchiveOutput output, String prefix, LogicTree<?> logicTree,
			List<Map<String, String>> branchMappings) throws IOException {
		if (verbose) System.out.println("Writing full logic tree");
		if (verbose) System.out.println("Writing branch file mappings");
		FileBackedModule.initEntry(output, prefix, LOGIC_TREE_MAPPINGS_FILE_NAME);
		
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output.getOutputStream()));
		writeLogicTreeMappings(writer, logicTree, branchMappings);
		
		output.closeEntry();
	}
	
	public static void writeLogicTreeMappings(Writer out, LogicTree<?> logicTree, List<Map<String, String>> branchMappings) throws IOException {
		if (!(out instanceof BufferedWriter))
			out = new BufferedWriter(out);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		JsonWriter writer = gson.newJsonWriter(out);
		
		double sumWeight = 0d;
		for (int i=0; i<logicTree.size(); i++)
			sumWeight += logicTree.getBranchWeight(i);
		
		writer.beginArray();
		for (int i=0; i<logicTree.size(); i++) {
			writer.beginObject();
			
			LogicTreeBranch<?> branch = logicTree.getBranch(i);
			writer.name("branch").beginArray();
			for (LogicTreeNode node : branch) {
				if (node == null)
					writer.nullValue();
				else
					writer.value(node.getFilePrefix());
			}
			writer.endArray();
			
			double weight = logicTree.getBranchWeight(i);
			if ((float)sumWeight != 1f)
				weight /= sumWeight;
			writer.name("weight").value(weight);
			
			Map<String, String> mappings = branchMappings.get(i);
			writer.name("mappings").beginObject();
			for (String key : mappings.keySet())
				writer.name(key).value(mappings.get(key));
			writer.endObject();
			
			writer.endObject();
		}
		writer.endArray();
		
		writer.flush();
	}
	
	public static List<Map<String, String>> loadBranchMappings(Reader read, LogicTree<?> logicTree) throws IOException {
		if (!(read instanceof BufferedReader))
			read = new BufferedReader(read);
		
		Gson gson = new GsonBuilder().create();
		JsonReader in = gson.newJsonReader(read);
		
		List<Map<String, String>> ret = new ArrayList<>(logicTree.size());
		
		in.beginArray();
		for (int i=0; i<logicTree.size(); i++) {
			in.beginObject();
			
			LogicTreeBranch<?> branch = logicTree.getBranch(i);
			
			Map<String, String> mappings = null;
			while (in.hasNext()) {
				String name = in.nextName();
				switch (name) {
				case "branch":
					// validate that the tree is as expected
					in.beginArray();
					for (int j=0; j<branch.size(); j++) {
						LogicTreeNode val = branch.getValue(j);
						if (val == null) {
							in.nextNull();
						} else {
							String nodeName = in.nextString();
							Preconditions.checkState(nodeName.equals(val.getFilePrefix()));
						}
					}
					in.endArray();
					break;
				case "mappings":
					mappings = new LinkedHashMap<>();
					in.beginObject();
					while (in.hasNext()) {
						String key = in.nextName();
						String value = in.nextString();
						mappings.put(key, value);
					}
					in.endObject();
					break;
				case "weight":
					in.skipValue();
					break;

				default:
					throw new IllegalStateException("Unexpected JSON name: "+name);
				}
			}
			Preconditions.checkNotNull(mappings);
			ret.add(mappings);
			
			in.endObject();
		}
		in.endArray();
		
		read.close();
		
		return ret;
	}

	@Override
	public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
		String outPrefix = buildPrefix(entryPrefix);

		writeLogicTreeToArchive(output, outPrefix, logicTree);
		
		// write files for all branches
		HashSet<String> writtenFiles = new HashSet<>();
		
		List<Map<String, String>> branchMappings = new ArrayList<>();
		for (int i=0; i<logicTree.size(); i++) {
			LogicTreeBranch<?> branch = logicTree.getBranch(i);
			if (verbose) System.out.println("Writing branch "+i+"/"+logicTree.size()+": "+branch);
			branchMappings.add(writeBranchFilesToArchive(output, outPrefix, branch, writtenFiles));
		}
		
		// write mappings file
		writeLogicTreeMappingsToArchive(output, outPrefix, logicTree, branchMappings);
	}

	@Override
	public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
		this.input = input;
		this.prefix = buildPrefix(entryPrefix);
		
		// load the logic tree
		BufferedInputStream logicTreeIS = FileBackedModule.getInputStream(input, prefix, LOGIC_TREE_FILE_NAME);
		Gson gson = new GsonBuilder().registerTypeAdapter(LogicTree.class, new LogicTree.Adapter<>()).create();
		InputStreamReader reader = new InputStreamReader(logicTreeIS);
		setLogicTree(gson.fromJson(reader, LogicTree.class));
	}
	
	protected LogicTreeLevel<?> getLevelForType(Class<? extends LogicTreeNode> type) {
		LogicTree<?> logicTree = getLogicTree();
		Preconditions.checkNotNull(logicTree, "Not yet initialized");
		for (LogicTreeLevel<?> level : logicTree.getLevels())
			if (level.matchesType(type))
				return level;
		throw new IllegalStateException("No level found for type: "+type);
	}

}
