package org.opensha.sha.earthquake.faultSysSolution.modules.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.JSON_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetModule;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;

public class LogicTreeBranchModule extends RupSetModule implements JSON_BackedModule {
	
	private LogicTreeBranch branch;

	public LogicTreeBranchModule() {
		super(null);
	}
	
	public LogicTreeBranchModule(FaultSystemRupSet rupSet, LogicTreeBranch branch) {
		super(rupSet);
		this.branch = branch;
	}

	@Override
	public String getName() {
		return "Logic Tree Branch";
	}
	
	public LogicTreeBranch getBranch() {
		return branch;
	}

	@Override
	public String getJSON_FileName() {
		return "logic_tree_branch.json";
	}

	@Override
	public void writeToJSON(JsonWriter out, Gson gson) throws IOException {
		BranchAdapter adapter = new BranchAdapter();
		adapter.write(out, getBranch());
	}

	@Override
	public void initFromJSON(JsonReader in, Gson gson) throws IOException {
		BranchAdapter adapter = new BranchAdapter();
		this.branch = adapter.read(in);
	}
	
	private static class BranchAdapter extends TypeAdapter<LogicTreeBranch> {

		@Override
		public void write(JsonWriter out, LogicTreeBranch branch) throws IOException {
			out.beginArray();
			
			for (int i=0; i<branch.size(); i++) {
				out.beginObject();
				
				LogicTreeBranchNode<?> node = branch.getValue(i);
				
				if (node != null) {
					out.name("level").value(node.getBranchLevelName());
					out.name("description").value(node.getName());
					out.name("relativeWeight").value(node.getRelativeWeight(null));
					out.name("class").value(LogicTreeBranch.getEnumEnclosingClass(node.getClass()).getName());
					out.name("name").value(node.name());
				}
				
				out.endObject();
			}
			
			out.endArray();
		}

		@Override
		public LogicTreeBranch read(JsonReader in) throws IOException {
			List<LogicTreeBranchNode<?>> nodes = new ArrayList<>();
			
			in.beginArray();
			
			while (in.hasNext()) {
				in.beginObject();
				
				Class<? extends LogicTreeBranchNode<?>> clazz = null;
				String name = null;
				while (in.hasNext()) {
					switch (in.nextName()) {
					case "class":
						try {
							clazz = (Class<? extends LogicTreeBranchNode<?>>) Class.forName(in.nextString());
						} catch (ClassNotFoundException e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
						break;
					case "name":
						name = in.nextString();;
						break;

					default:
						in.skipValue();
						break;
					}
				}
				
				if (clazz != null) {
					Preconditions.checkNotNull(name, "class supplied but not name?");
					LogicTreeBranchNode<?> value = null;
					LogicTreeBranchNode<?>[] options = clazz.getEnumConstants();
					for (LogicTreeBranchNode<?> option : options) {
						if (option.name().equals(name) || option.getShortName().equals(name)) {
							value = option;
							break;
						}
					}
					Preconditions.checkNotNull(value, "Could not load enum with name=%s of type %s", name, clazz.getName());
					nodes.add(value);
				} else {
					Preconditions.checkState(name == null, "name supplied but not class?");
					nodes.add(null);
				}
				
				in.endObject();
			}
			
			in.endArray();
			return LogicTreeBranch.fromValues(nodes);
		}
		
	}

}