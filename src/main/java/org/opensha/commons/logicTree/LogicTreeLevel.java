package org.opensha.commons.logicTree;

import java.util.Collections;
import java.util.List;

import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.logicTree.LogicTreeNode.FileBackedNode;

import com.google.common.base.Preconditions;

public abstract class LogicTreeLevel<E extends LogicTreeNode<?>> implements ShortNamed {
	
	public abstract Class<E> getType();
	
	public abstract List<E> getNodes();
	
	public abstract boolean isMember(E node);
	
	static class FileBackedLevel extends LogicTreeLevel<FileBackedNode> {
		
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
			return name;
		}

		@Override
		public String getName() {
			return shortName;
		}

		@Override
		public Class<FileBackedNode> getType() {
			return FileBackedNode.class;
		}
		
		void setChoice(FileBackedNode choice) {
			this.choice = choice;
		}

		@Override
		public List<FileBackedNode> getNodes() {
			if (choice == null)
				return Collections.emptyList();
			return Collections.singletonList(choice);
		}

		@Override
		public boolean isMember(FileBackedNode node) {
			return choice != null && choice.equals(node);
		}
		
	}
	
	@SuppressWarnings("unchecked")
	static <E extends Enum<E> & LogicTreeNode<E>> EnumBackedLevel<E> rawTypedEnumInstance(
			String name, String shortName, Class<?> type) {
		return new EnumBackedLevel<>(name, shortName, (Class<E>)type);
	}
	
	static class EnumBackedLevel<E extends Enum<E> & LogicTreeNode<E>> extends LogicTreeLevel<E> {
		
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
		public List<E> getNodes() {
			return List.of(type.getEnumConstants());
		}

		@Override
		public boolean isMember(E node) {
			return node != null && type.isAssignableFrom(node.getClass());
		}
		
	}

}
