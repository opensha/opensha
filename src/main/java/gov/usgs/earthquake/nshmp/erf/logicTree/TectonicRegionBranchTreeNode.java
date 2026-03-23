package gov.usgs.earthquake.nshmp.erf.logicTree;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.IndexedValuedLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;

public class TectonicRegionBranchTreeNode implements LogicTreeNode.ValuedLogicTreeNode<LogicTreeBranch<?>> {
	
	private TectonicRegionType trt;
	private LogicTreeBranch<?> branch;
	private double weight;
	private String name;
	private String shortName;
	private String filePrefix;

	public TectonicRegionBranchTreeNode(TectonicRegionType trt, LogicTreeBranch<?> branch, double weight,
			String name, String shortName, String filePrefix) {
		this.trt = trt;
		init(branch, getValueType(), weight, name, shortName, filePrefix);
	}

	@Override
	public double getNodeWeight() {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return filePrefix;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public LogicTreeBranch<?> getValue() {
		return branch;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<? extends LogicTreeBranch<?>> getValueType() {
		// this extra cast to Class<?> resolves compile errors that don't show up in eclipse, which is annoying
		return (Class<? extends LogicTreeBranch<?>>) (Class<?>) LogicTreeBranch.class;
	}
	
	public TectonicRegionType getTectonicRegime() {
		return trt;
	}

	@Override
	public void init(LogicTreeBranch<?> branch, Class<? extends LogicTreeBranch<?>> valueClass, double weight,
			String name, String shortName, String filePrefix) {
		this.branch = branch;
		this.weight = weight;
		this.name = name;
		this.shortName = shortName;
		this.filePrefix = filePrefix;
	}
	
	private static LogicTree.Adapter<LogicTreeNode> treeAdapter = new LogicTree.Adapter<>();
	
	public static class Level extends LogicTreeLevel.ValueByIndexLevel<LogicTreeBranch<?>, TectonicRegionBranchTreeNode> {
		
		private TectonicRegionType trt;
		private LogicTree<LogicTreeNode> tree;
		private List<TectonicRegionBranchTreeNode> nodes;

		@SuppressWarnings("unused") // deserialization
		private Level(String name, String shortName) {
			super(name, shortName);
		}
		
		public Level(TectonicRegionType trt, LogicTree<LogicTreeNode> tree, String levelName, String levelShortName,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
			this.trt = trt;
			this.tree = tree;
			
			detectAffected();
		}
		
		private void detectAffected() {
			List<String> affected = new ArrayList<>();
			List<String> notAffected = new ArrayList<>();
			
			// determine affected/not effected status based on underlying levels, checking to see if they actually vary
			
			ImmutableList<LogicTreeLevel<? extends LogicTreeNode>> levels = tree.getLevels();
			LogicTreeBranch<LogicTreeNode> branch0 = tree.getBranch(0);
			for (int l=0; l<levels.size(); l++) {
				LogicTreeLevel<? extends LogicTreeNode> level = levels.get(l);
				boolean varies = false;
				LogicTreeNode node0 = branch0.getValue(l);
				for (int i=1; !varies && i<tree.size(); i++)
					varies = !node0.equals(tree.getBranch(i).getValue(l));
				if (varies) {
					// then it does truly affect
					for (String affects : level.getAffected()) {
						if (!affected.contains(affects)) {
//							System.out.println("Level '"+level.getShortName()+"' first to affect:\t"+affects);
							affected.add(affects);
							if (notAffected.contains(affects))
								notAffected.remove(affects);
						}
					}
					for (String noAffects : level.getNotAffected()) {
						if (!notAffected.contains(noAffects) && !affected.contains(noAffects))
							notAffected.add(noAffects);
					}
				}
			}
			
			setAffected(affected, notAffected, false);
		}
		
		public TectonicRegionType getTectonicRegime() {
			return trt;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Class<? extends LogicTreeBranch<?>> getValueType() {
			// this extra cast to Class<?> resolves compile errors that don't show up in eclipse, which is annoying
			return (Class<? extends LogicTreeBranch<?>>) (Class<?>) LogicTreeBranch.class;
		}

		@Override
		public TectonicRegionBranchTreeNode build(LogicTreeBranch<?> value, double weight, String name,
				String shortName, String filePrefix) {
			Preconditions.checkState(tree.contains(value));
			return new TectonicRegionBranchTreeNode(trt, value, weight, name, shortName, filePrefix);
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = super.toJsonObject();
			
			json.add("tectonicRegime", new JsonPrimitive(trt.name()));
			
			json.add("tree", treeAdapter.toJsonTree(tree));
			
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			trt = TectonicRegionType.valueOf(jsonObj.get("tectonicRegime").getAsString());
			
			tree = treeAdapter.fromJsonTree(jsonObj.get("tree"));
			
			super.initFromJsonObject(jsonObj);
		}

		@Override
		public Class<? extends TectonicRegionBranchTreeNode> getType() {
			return TectonicRegionBranchTreeNode.class;
		}

		@Override
		public List<? extends TectonicRegionBranchTreeNode> getNodes() {
			if (nodes == null) {
				synchronized (this) {
					if (nodes == null) {
						List<TectonicRegionBranchTreeNode> nodes = new ArrayList<>(tree.size());
						for (int i=0; i<tree.size(); i++)
							nodes.add(build(i, tree.getBranch(i), tree.getBranchWeight(i)));
						this.nodes = nodes;
					}
				}
			}
			return nodes;
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			return getNodes().contains(node);
		}

		@Override
		protected void setNodes(List<TectonicRegionBranchTreeNode> nodes) {
			this.nodes = nodes;
		}

		@Override
		public LogicTreeBranch<?> valueForIndex(int index) {
			return tree.getBranch(index);
		}
		
	}

}
