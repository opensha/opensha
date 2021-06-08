package org.opensha.commons.logicTree;

import java.io.Serializable;

import org.opensha.commons.data.ShortNamed;

public interface LogicTreeNode<E> extends ShortNamed, Serializable {
	
	/**
	 * This returns the relative weight assigned to this branch node.
	 * <p>
	 * Sometimes the weight assigned to one branch node can depend on the choices made at other levels, and thus the full
	 * branch is supplied if available.
	 * @param fullBranch full logic tree branch, in case the weight of this node depends on other branch choices
	 * @return the relative weight assigned to this branch choice
	 */
	public double getNodeWeight(LogicTreeBranch<?> fullBranch); // TODO: rethink generics here
	
	/**
	 * This encodes the choice as a string that can be used in file names
	 * @return
	 */
	public String getFilePrefix();
	
	static class FileBackedNode implements LogicTreeNode<FileBackedNode> {
		
		private String name;
		private String shortName;
		private double weight;
		private String choiceStr;

		FileBackedNode(String name, String shortName, double weight, String choiceStr) {
			this.name = name;
			this.shortName = shortName;
			this.weight = weight;
			this.choiceStr = choiceStr;
		}

		@Override
		public String getShortName() {
			return name;
		}

		@Override
		public String getName() {
			return shortName;
		}

		@Override
		public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
			return weight;
		}

		@Override
		public String getFilePrefix() {
			return choiceStr;
		}
		
	}

}