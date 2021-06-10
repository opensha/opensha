package org.opensha.commons.logicTree;

import java.util.Collections;
import java.util.List;

import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.logicTree.LogicTreeNode.FileBackedNode;

import com.google.common.base.Preconditions;

public abstract class LogicTreeLevel implements ShortNamed {
	
	public abstract Class<? extends LogicTreeNode> getType();
	
	public abstract List<LogicTreeNode> getNodes();
	
	public abstract boolean isMember(LogicTreeNode node);
	
	public String toString() {
		return getName();
	}
	
	static class FileBackedLevel extends LogicTreeLevel {
		
		private String name;
		private String shortName;
		private FileBackedNode choice;

		FileBackedLevel(String name, String shortName, FileBackedNode choice) {
			this.name = name;
			this.shortName = shortName;
			this.choice = choice;
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
		public Class<FileBackedNode> getType() {
			return FileBackedNode.class;
		}
		
		void setChoice(FileBackedNode choice) {
			this.choice = choice;
		}

		@Override
		public List<LogicTreeNode> getNodes() {
			if (choice == null)
				return Collections.emptyList();
			return Collections.singletonList(choice);
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			return choice != null && choice.equals(node);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
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
			FileBackedLevel other = (FileBackedLevel) obj;
			if (choice != null && other.choice != null) {
				if (!choice.equals(other.choice))
					return false;
			}
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
	
	public static <E extends Enum<E> & LogicTreeNode> EnumBackedLevel<E> forEnum(
			Class<E> type, String name, String shortName) {
		return new EnumBackedLevel<>(name, shortName, type);
	}
	
	@SuppressWarnings("unchecked")
	public static <E extends Enum<E> & LogicTreeNode> EnumBackedLevel<E> forEnumUnchecked(
			Class<?> type, String name, String shortName) {
		return new EnumBackedLevel<>(name, shortName, (Class<E>)type);
	}
	
	static class EnumBackedLevel<E extends Enum<E> & LogicTreeNode> extends LogicTreeLevel {
		
		private String name;
		private String shortName;
		private Class<E> type;

		public EnumBackedLevel(String name, String shortName, Class<E> type) {
			this.name = name;
			this.shortName = shortName;
			Preconditions.checkState(type.isEnum(), "Supplied type is not an enum");
			this.type = type;
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
		public Class<E> getType() {
			return type;
		}

		@Override
		public List<LogicTreeNode> getNodes() {
			return List.of(type.getEnumConstants());
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			return node != null && type.isAssignableFrom(node.getClass());
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + ((shortName == null) ? 0 : shortName.hashCode());
			result = prime * result + ((type == null) ? 0 : type.hashCode());
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
			EnumBackedLevel other = (EnumBackedLevel) obj;
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
			if (type == null) {
				if (other.type != null)
					return false;
			} else if (!type.equals(other.type))
				return false;
			return true;
		}
		
	}

}
