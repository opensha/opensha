package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
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
 * Abstract base class for a branch-averaged module. Work in progress
 * 
 * @author kevin
 *
 */
@ModuleHelper
public abstract class AbstractBranchAveragedModule implements ArchivableModule {

	private ZipFile zip;
	private String prefix;
	private LogicTree<?> logicTree;

	protected AbstractBranchAveragedModule(ZipFile zip, String prefix, LogicTree<?> logicTree) {
		this.zip = zip;
		this.prefix = prefix;
		this.logicTree = logicTree;
	}
	
	/**
	 * Logic tree specific files will live in their own unique sub-directory, with this name

	 * @return
	 */
	protected abstract String getSubDirectoryName();
	
	protected abstract List<? extends LogicTreeLevel<?>> getLevelsAffectingFile(String fileName);
	
	private Map<String, List<? extends LogicTreeLevel<?>>> levelsCache = new HashMap<>();
	
	protected String getBranchFileName(LogicTreeBranch<?> branch, String fileName) {
		return getBranchFileName(branch, prefix, fileName);
	}
	
	protected String getBranchFileName(LogicTreeBranch<?> branch, String prefix, String fileName) {
		List<? extends LogicTreeLevel<?>> mappingLevels;
		synchronized (levelsCache) {
			mappingLevels = levelsCache.get(fileName);
			if (mappingLevels == null) {
				mappingLevels = getLevelsAffectingFile(fileName);
				levelsCache.put(fileName, mappingLevels);
			}
		}
		StringBuilder ret = new StringBuilder(prefix);
		Preconditions.checkNotNull(mappingLevels, "No mappings available for %", fileName);
		for (LogicTreeLevel<?> level : mappingLevels) {
			LogicTreeNode value = branch.getValue(level.getType());
			Preconditions.checkNotNull(value,
					"Branch does not have value for %s, needed to retrieve %s", level.getName(), fileName);
			ret.append(value.getFilePrefix()).append("/");
		}
		ret.append(fileName);
		return ret.toString();
	}
	
	protected String buildPrefix(String upstreamPrefix) {
		if (upstreamPrefix == null)
			upstreamPrefix = "";
		return upstreamPrefix+getSubDirectoryName()+"/";
	}
	
	public LogicTree<?> getLogicTree() {
		return logicTree;
	}
	
	/**
	 * This will be called to write all files for the given branch to the zip file. Files should be written starting
	 * with the given prefix, which will end with <code>getSubDirectoryName()+"/"</code>. Implementations must check
	 * that a given file has not yet been written, and add the name of all written files to the writtenFiles set.
	 * 
	 * @param zout
	 * @param prefix
	 * @param branch
	 * @param writtenFiles set containing names of all files already written
	 * @throws IOException 
	 */
	protected abstract void writeBranchFilesToArchive(ZipOutputStream zout, String prefix, LogicTreeBranch<?> branch,
			HashSet<String> writtenFiles) throws IOException;
	
	protected String getFilePrefix() {
		Preconditions.checkNotNull(prefix, "Not yet initialized");
		return prefix;
	}
	
	protected ZipFile getZipFile() {
		Preconditions.checkNotNull(zip, "Not yet initialized");
		return zip;
	}

	@Override
	public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		String outPrefix = buildPrefix(entryPrefix);

		System.out.println("Writing full logic tree");
		// write the logic tree
		FileBackedModule.initEntry(zout, outPrefix, "logic_tree.json");
		Gson gson = new GsonBuilder().setPrettyPrinting()
				.registerTypeAdapter(LogicTree.class, new LogicTree.Adapter<>()).create();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zout));
		gson.toJson(logicTree, LogicTree.class, writer);
		writer.flush();
		zout.flush();
		zout.closeEntry();
		
		// write files for all branches
		HashSet<String> writtenFiles = new HashSet<>();
		for (int i=0; i<logicTree.size(); i++) {
			LogicTreeBranch<?> branch = logicTree.getBranch(i);
			System.out.println("Writing branch "+i+"/"+logicTree.size()+": "+branch);
			writeBranchFilesToArchive(zout, outPrefix, branch, writtenFiles);
		}
	}

	@Override
	public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
		this.zip = zip;
		this.prefix = buildPrefix(entryPrefix);
		
		// load the logic tree
		BufferedInputStream logicTreeIS = FileBackedModule.getInputStream(zip, prefix, "logic_tree.json");
		Gson gson = new GsonBuilder().registerTypeAdapter(LogicTree.class, new LogicTree.Adapter<>()).create();
		InputStreamReader reader = new InputStreamReader(logicTreeIS);
		logicTree = gson.fromJson(reader, LogicTree.class);
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
