package org.opensha.commons.logicTree;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.logicTree.LogicTreeNode.FileBackedNode;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public abstract class LogicTreeLevel<E extends LogicTreeNode> implements ShortNamed {
	
	public abstract Class<? extends E> getType();
	
	public abstract List<? extends E> getNodes();
	
	public abstract boolean isMember(LogicTreeNode node);
	
	public String toString() {
		return getName();
	}
	
	public boolean matchesType(Class<?> clazz) {
		Class<? extends E> type = getType();
		if (type.equals(clazz) || type.isAssignableFrom(clazz))
			return true;
		return false;
	}
	
	static class FileBackedLevel extends LogicTreeLevel<FileBackedNode> {
		
		private String name;
		private String shortName;
		private List<FileBackedNode> choices;

		FileBackedLevel(String name, String shortName) {
			this(name, shortName, new ArrayList<>());
		}

		FileBackedLevel(String name, String shortName, FileBackedNode choice) {
			this(name, shortName, new ArrayList<>());
			if (choice != null)
				addChoice(choice);
		}

		FileBackedLevel(String name, String shortName, List<FileBackedNode> choices) {
			this.name = name;
			this.shortName = shortName;
			if (choices == null)
				choices = new ArrayList<>();
			this.choices = choices;
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
		
		void addChoice(FileBackedNode choice) {
			Preconditions.checkNotNull(choice);
			choices.add(choice);
		}

		@Override
		public List<FileBackedNode> getNodes() {
			return choices;
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			return choices.contains(node);
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
			if (choices != null && other.choices != null) {
				if (!choices.equals(other.choices))
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

	public static abstract class AdapterBackedLevel extends LogicTreeLevel<LogicTreeNode>{
		String name;
		String shortName;
		Class<? extends LogicTreeNode> nodeType;

		public AdapterBackedLevel(String name, String shortName, Class<? extends LogicTreeNode>nodeType){
		    this.name = name;
			this.shortName = shortName;
			this.nodeType = nodeType;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getShortName() {
			return shortName;
		}

		@Override
		public Class<? extends LogicTreeNode> getType() {
			return nodeType;
		}

		@Override
		public List<? extends LogicTreeNode> getNodes() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			return node.getClass() == getType();
		}

		@Override
		public boolean equals(Object o){
			if(o instanceof LogicTreeLevel.AdapterBackedLevel){
				AdapterBackedLevel other = (AdapterBackedLevel) o;
				return other.getName() == getName() && other.getType() == getType();
			}else{
				return false;
			}
		}
	}

	public static <E extends Enum<E> & LogicTreeNode> LogicTreeLevel<E> forEnum(
			Class<E> type, String name, String shortName) {
		return new EnumBackedLevel<>(name, shortName, type);
	}
	
	@SuppressWarnings("unchecked")
	public static <E extends LogicTreeNode> LogicTreeLevel<E> forEnumUnchecked(
			Class<?> type, String name, String shortName) {
		return new EnumBackedLevel<>(name, shortName, (Class<E>)type);
	}
	
	@SuppressWarnings("unchecked")
	public static <E extends LogicTreeNode> LogicTreeLevel<E> forEnumUnchecked(
			Object enumValue, String name, String shortName) {
		Class<? extends Object> type = enumValue.getClass();
		if (!type.isEnum())
			type = (Class<E>) type.getEnclosingClass();
		Preconditions.checkState(type.isEnum(), "Class is not an enum: %s", type);
		Preconditions.checkState(enumValue instanceof LogicTreeNode);
		return new EnumBackedLevel<>(name, shortName, (Class<E>)type);
	}
	
	static class EnumBackedLevel<E extends LogicTreeNode> extends LogicTreeLevel<E> {
		
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
			EnumBackedLevel<?> other = (EnumBackedLevel<?>) obj;
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
	
	public static class Adapter<E extends LogicTreeNode> extends TypeAdapter<LogicTreeLevel<? extends E>> {

		@Override
		public void write(JsonWriter out, LogicTreeLevel<? extends E> level) throws IOException {
			out.beginObject();
			
			out.name("name").value(level.getName());
			out.name("shortName").value(level.getShortName());
			if (level.getType().isEnum()) {
				// it's an enum
				out.name("enumClass").value(level.getType().getName());
			} else if (!(level instanceof FileBackedLevel)) {
				out.name("class").value(level.getClass().getName());
			}
			out.endObject();
		}

		@SuppressWarnings("unchecked")
		@Override
		public LogicTreeLevel<E> read(JsonReader in) throws IOException {
			String name = null;
			String shortName = null;
			String enumClassName = null;
			String className = null;
			in.beginObject();
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "name":
					name = in.nextString();
					break;
				case "shortName":
					shortName = in.nextString();
					break;
				case "enumClass":
					enumClassName = in.nextString();
					break;
				case "class":
					className = in.nextString();
					break;

				default:
					break;
				}
			}
			
			LogicTreeLevel<E> level = null;
			
			if (enumClassName != null) {
				// load it as an enum
				try {
					Class<?> rawClass = Class.forName(enumClassName);
					level = (LogicTreeLevel<E>) LogicTreeLevel.forEnumUnchecked(rawClass, name, shortName);
				} catch (ClassNotFoundException e) {
					System.err.println("WARNING: couldn't locate logic tree branch node enum class '"+enumClassName+"', "
							+ "loading plain/hardcoded version instead");
				} catch (ClassCastException e) {
					System.err.println("WARNING: logic tree branch node class '"+enumClassName+"' is of the wrong type, "
							+ "loading plain/hardcoded version instead");
				}
			}
			if (level == null && className != null) {
				// try to load it as a class via default constructor
				try {
					Class<?> rawClass = Class.forName(className);
					Class<? extends LogicTreeLevel<E>> clazz = (Class<? extends LogicTreeLevel<E>>)rawClass;
					Constructor<? extends LogicTreeLevel<E>> constructor = clazz.getDeclaredConstructor();
					constructor.setAccessible(true);
					
					level = constructor.newInstance();
				} catch (ClassNotFoundException e) {
					System.err.println("WARNING: couldn't locate logic tree branch node class '"+className+"', "
							+ "loading plain/hardcoded version instead");
				} catch (ClassCastException e) {
					System.err.println("WARNING: logic tree branch node class '"+className+"' is of the wrong type, "
							+ "loading plain/hardcoded version instead");
				} catch (Exception e) {
					System.err.println("Couldn't instantiate default no-arg constructor of declared logic tree node class, "
								+ "loading plain/hardcoded version instead");
				}
			}
			if (level == null) {
				// file-backed
				level = (LogicTreeLevel<E>) new FileBackedLevel(name, shortName);
			}
			
			in.endObject();
			return level;
		}
		
	}

}
