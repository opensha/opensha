package org.opensha.commons.logicTree;

import java.io.Serializable;

import org.opensha.commons.data.ShortNamed;

public interface LogicTreeNode extends ShortNamed, Serializable {
	
	/**
	 * This returns the relative weight assigned to this branch node.
	 * <p>
	 * Sometimes the weight assigned to one branch node can depend on the choices made at other levels, and thus the full
	 * branch is supplied if available.
	 * @param fullBranch full logic tree branch, in case the weight of this node depends on other branch choices
	 * @return the relative weight assigned to this branch choice
	 */
	public double getNodeWeight(LogicTreeBranch<?> fullBranch);
	
	/**
	 * This encodes the choice as a string that can be used in file names
	 * @return
	 */
	public String getFilePrefix();
	
	static class FileBackedNode implements LogicTreeNode {
		
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
			return shortName;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
			return weight;
		}

		@Override
		public String getFilePrefix() {
			return choiceStr;
		}
		
		@Override
		public String toString() {
			return getName();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((choiceStr == null) ? 0 : choiceStr.hashCode());
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + ((shortName == null) ? 0 : shortName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			FileBackedNode other = (FileBackedNode) obj;
			if (choiceStr == null) {
				if (other.choiceStr != null)
					return false;
			} else if (!choiceStr.equals(other.choiceStr))
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			if (shortName == null) {
				if (other.shortName != null)
					return false;
			} else if (!shortName.equals(other.shortName))
				return false;
			return true;
		}
		
	}

}