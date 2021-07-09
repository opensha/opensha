package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
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
	private List<BranchSpecificFile> branchFiles;

	protected AbstractBranchAveragedModule(ZipFile zip, String prefix, LogicTree<?> logicTree,
			List<BranchSpecificFile> branchFiles) {
		this.zip = zip;
		this.prefix = prefix;
		this.logicTree = logicTree;
		this.branchFiles = branchFiles;
	}
	
	public static final class BranchSpecificFile {
		private String fileName;
		private List<LogicTreeLevel<?>> fileLevels;
		
		public BranchSpecificFile(String fileName, List<LogicTreeLevel<?>> fileLevels) {
			this.fileName = fileName;
			this.fileLevels = fileLevels;
		}
		
		public String getNameForBranch(LogicTreeBranch<?> branch, String prefix) {
			if (prefix == null)
				prefix = "";
			StringBuilder ret = new StringBuilder(prefix);
			Preconditions.checkNotNull(fileLevels, "No mappings available for %", fileName);
			for (LogicTreeLevel<?> level : fileLevels) {
				LogicTreeNode value = branch.getValue(level.getType());
				Preconditions.checkNotNull(value,
						"Branch does not have value for %s, needed to retrieve %s", level.getName(), fileName);
				ret.append(value.getFilePrefix()).append("/");
			}
			return ret.toString();
		}
	}
	
	private static final class BranchFileAdapter extends TypeAdapter<BranchSpecificFile> {
		
		private Map<String, LogicTreeLevel<?>> levelsMap;
		private BranchFileAdapter(LogicTree<?> logicTree) {
			levelsMap = new HashMap<>();
			for (LogicTreeLevel<?> level : logicTree.getLevels()) {
				String shortName = level.getShortName();
				Preconditions.checkState(shortName.length() > 0);
				Preconditions.checkState(!levelsMap.containsKey(shortName),
						"Level short name is not unique: %s", shortName);
				levelsMap.put(shortName, level);
			}
		}

		@Override
		public void write(JsonWriter out, BranchSpecificFile value) throws IOException {
			out.beginObject();
			
			out.name("fileName").value(value.fileName);
			out.name("levels").beginArray();
			for (LogicTreeLevel<?> level : value.fileLevels)
				out.value(level.getShortName());
			out.endArray();
			
			out.endObject();
		}

		@Override
		public BranchSpecificFile read(JsonReader in) throws IOException {
			in.beginObject();
			
			String fileName = null;
			List<LogicTreeLevel<?>> levels = null;
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "fileName":
					fileName = in.nextString();
					break;
				case "levels":
					levels = new ArrayList<>();
					in.beginArray();
					while (in.hasNext()) {
						String shortName = in.nextString();
						Preconditions.checkState(levelsMap.containsKey(shortName),
								"No level found with short name=%s", shortName);
						levels.add(levelsMap.get(shortName));
					}
					in.endArray();
					break;

				default:
					break;
				}
			}
			
			Preconditions.checkNotNull(fileName, "'fileName' not specified in JSON");
			Preconditions.checkNotNull(levels, "'levels' not specified in JSON");
			
			in.endObject();
			return new BranchSpecificFile(fileName, levels);
		}
		
	}
	
//	public String getBranchFileName(LogicTreeBranch<?> branch, String fileName) {
//		return getBranchFileName(branch, prefix, fileName);
//	}
//	
//	protected String getBranchFileName(LogicTreeBranch<?> branch, String prefix, String fileName) {
//		List<LogicTreeLevel<?>> mappingLevels = getLevelsAffectingFile(fileName);
//		StringBuilder ret = new StringBuilder(prefix);
//		Preconditions.checkNotNull(mappingLevels, "No mappings available for %", fileName);
//		for (LogicTreeLevel<?> level : mappingLevels) {
//			LogicTreeNode value = branch.getValue(level.getType());
//			Preconditions.checkNotNull(value,
//					"Branch does not have value for %s, needed to retrieve %s", level.getName(), fileName);
//			ret.append(value.getFilePrefix()).append("/");
//		}
//		return ret.toString();
//	}
	
	protected String buildPrefix(String upstreamPrefix) {
		if (upstreamPrefix == null)
			upstreamPrefix = "";
		return upstreamPrefix+"logic_tree/";
	}
	
	public LogicTree<?> getLogicTree() {
		return logicTree;
	}
	
	/**
	 * This will be called when writing to a file if this module was initialized without a source zip file. It can
	 * be used when building a branch averaged module for the first time.
	 * 
	 * @param zout
	 * @param prefix
	 */
	protected abstract void writeBranchFilesToArchive(ZipOutputStream zout, String prefix);

	@Override
	public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		String outPrefix = buildPrefix(entryPrefix);
		
		if (zip == null) {
			writeBranchFilesToArchive(zout, outPrefix);
		} else {
			// copy over all files
			Enumeration<? extends ZipEntry> entries = zip.entries();
			String origLogicTreeName = prefix+"logic_tree.json";
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (name.startsWith(prefix) && !name.equals(origLogicTreeName)) {
					zout.putNextEntry(new ZipEntry(outPrefix+name));
					
					BufferedInputStream bin = new BufferedInputStream(zip.getInputStream(entry));
					bin.transferTo(zout);
					zout.flush();
					
					zout.closeEntry();
				}
			}
		}
		
		// write the logic tree
		FileBackedModule.initEntry(zout, outPrefix, "logic_tree.json");
		Gson gson = new GsonBuilder().setPrettyPrinting()
				.registerTypeAdapter(LogicTree.class, new LogicTree.Adapter<>()).create();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zout));
		gson.toJson(logicTree, LogicTree.class, writer);
		writer.flush();
		zout.closeEntry();
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

}
