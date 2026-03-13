package org.opensha.commons.logicTree;

import java.io.Serializable;
import java.util.Objects;

import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.data.ShortNamed;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;

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
	
	public static class FileBackedNode implements LogicTreeNode {
		
		private String name;
		private String shortName;
		private double weight;
		private String choiceStr;

		public FileBackedNode(String name, String shortName, double weight, String choiceStr) {
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
	
	/**
	 * This interface indicates that this LogicTreeNode can be written via a {@link TypeAdapter} and allows for the properties
	 * (names, prefix, weight) to be initialized. You must also specify the TypeAdapter via the {@link JsonAdapter} annotation.
	 */
	public static interface AdapterBackedNode extends LogicTreeNode {
		
		/**
		 * Initializes this node with the given properties
		 * 
		 * @param name
		 * @param shortName
		 * @param prefix
		 * @param weight
		 */
		public void init(String name, String shortName, String prefix, double weight);
	}
	
	public static interface ValuedLogicTreeNode<E> extends LogicTreeNode {
		
		public E getValue();
	}
	
	/**
	 * Randomly-generated logic tree node that is built on the fly from its own random seed. Must have a default
	 * constructor for deserialization, and be fully initializable via that constructor and the
	 * {@link RandomlyGeneratedNode#init(String, String, String, double, long)} method.
	 */
	public static abstract class RandomlyGeneratedNode implements LogicTreeNode {
		
		private String name;
		private String shortName;
		private String prefix;
		private double weight;
		private long seed;
		
		protected RandomlyGeneratedNode() {}

		public RandomlyGeneratedNode(String name, String shortName, String prefix, double weight, long seed) {
			init(name, shortName, prefix, weight, seed);
		}
		
		public long getSeed() {
			return seed;
		}
		
		/**
		 * Initializes this node with the given properties and seed
		 * 
		 * @param name
		 * @param shortName
		 * @param prefix
		 * @param weight
		 * @param seed
		 */
		public void init(String name, String shortName, String prefix, double weight, long seed) {
			this.name = name;
			this.shortName = shortName;
			this.prefix = prefix;
			this.weight = weight;
			this.seed = seed;
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
			return prefix;
		}
		
		@Override
		public String toString() {
			return shortName;
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, prefix, seed, shortName, weight);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			RandomlyGeneratedNode other = (RandomlyGeneratedNode) obj;
			return Objects.equals(name, other.name) && Objects.equals(prefix, other.prefix) && seed == other.seed
					&& Objects.equals(shortName, other.shortName)
					&& Double.doubleToLongBits(weight) == Double.doubleToLongBits(other.weight);
		}
		
	}

}