package org.opensha.commons.logicTree;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.logicTree.Affects.Affected;
import org.opensha.commons.logicTree.DoesNotAffect.NotAffected;
import org.opensha.commons.logicTree.LogicTreeBranch.NodeTypeAdapter;
import org.opensha.commons.logicTree.LogicTreeNode.FileBackedNode;
import org.opensha.commons.logicTree.LogicTreeNode.RandomlySampledNode;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public abstract class LogicTreeLevel<E extends LogicTreeNode> implements ShortNamed {
	
	public abstract Class<? extends E> getType();
	
	public abstract List<? extends E> getNodes();
	
	public abstract boolean isMember(LogicTreeNode node);
	
	/**
	 * Determines if this level affects the given thing (e.g., a file name or some sort of property key).
	 * If this level does not know anything about this thing, then the given default value will be returned.
	 * <p>
	 * Mappings can be specified via JSON for {@link FileBackedLevel} implementations, or via the
	 * {@link Affects} or {@link DoesNotAffect} annotations on an {@link EnumBackedLevel}.
	 * <p>
	 * Other implementations should override {@link LogicTreeLevel#getAffected()} and
	 * {@link LogicTreeLevel#getNotAffected()}.
	 * <p>
	 * This is originally designed for use for efficient storage of {@link SolutionLogicTree}'s, but other
	 * use cases may exist.
	 * 
	 * @param name the thing we are seeing if this level effects
	 * @param affectedByDefault return value if this level neither reports that it affects
	 * nor does not affect this thing
	 * @return true if things (e.g., files or properties) with the the given name are affected
	 * by the choice at this logic tree level
	 */
	public boolean affects(String name, boolean affectedByDefault) {
		checkParseAnnotations();
		if (affectsAll)
			return true;
		if (affectsNone)
			return false;
		for (String affected : affected)
			if (name.equals(affected))
				return true;
		for (String notAffected : notAffected)
			if (name.equals(notAffected))
				return false;
		return affectedByDefault;
	}
	
	/**
	 * Gets list of things (e.g., a file name or some sort of property key) that are explicitly affected by
	 * the choice at this branching level.
	 * <b>
	 * This is originally designed for use for efficient storage of {@link SolutionLogicTree}'s, but other
	 * use cases may exist.
	 * <b>
	 * The default implementation parses {@link Affects} annotations on the level class.
	 * 
	 * @return collection of affected things, or empty collection if none (should never return null)
	 */
	public final Collection<String> getAffected() {
		checkParseAnnotations();
		return Collections.unmodifiableCollection(affected);
	}
	
	/**
	 * Gets list of things (e.g., a file name or some sort of property key) that are explicitly not affected by
	 * the choice at this branching level.
	 * <b>
	 * This is originally designed for use for efficient storage of {@link SolutionLogicTree}'s, but other
	 * use cases may exist.
	 * <b>
	 * The default implementation parses {@link DoesNotAffect} annotations on the level class.
	 * 
	 * @return collection of not affected things, or empty collection if none (should never return null)
	 */
	public final Collection<String> getNotAffected() {
		checkParseAnnotations();
		return Collections.unmodifiableCollection(notAffected);
	}
	
	private List<String> affected, notAffected;
	private boolean affectsAll, affectsNone;
	
	private void checkParseAnnotations() {
		if (affected == null) {
			synchronized (this) {
				if (affected == null) {
					List<String> affected = new ArrayList<>();
					List<String> notAffected = new ArrayList<>();
					
					Class<? extends E> type = getType();
					
					Affected multAffected = type.getAnnotation(Affected.class);
					if (multAffected != null) {
						// multiple
						for (Affects affects : multAffected.value())
							affected.add(affects.value());
					} else {
						// single
						Affects affects = type.getAnnotation(Affects.class);
						if (affects != null)
							affected.add(affects.value());
					}
					
					NotAffected multiNotAffected = type.getAnnotation(NotAffected.class);
					if (multiNotAffected != null) {
						// multiple
						for (DoesNotAffect doesNot : multiNotAffected.value()) {
							String name = doesNot.value();
							Preconditions.checkState(!affected.contains(name),
									"Node type %s annotates '%s' as both affected and not affected!",
									type.getName(), name);
							notAffected.add(name);
						}
					} else {
						// single
						DoesNotAffect doesNot = type.getAnnotation(DoesNotAffect.class);
						if (doesNot != null) {
							String name = doesNot.value();
							Preconditions.checkState(!affected.contains(name),
									"Node type %s annotates '%s' as both affected and not affected!",
									type.getName(), name);
							notAffected.add(name);
						}
					}
					
					// now see if we are marked as all/none
					AffectsNone noneAffected = type.getAnnotation(AffectsNone.class);
					AffectsAll allAffected = type.getAnnotation(AffectsAll.class);
					Preconditions.checkState(noneAffected == null || allAffected == null,
							"Can't specify both none and all affected");
					if (noneAffected != null) {
						Preconditions.checkState(affected.isEmpty() && notAffected.isEmpty(),
								"Supplied buth @AffectsNone and also individual @Affects/@DoesNotAffect annotation(s)");
						affectsNone = true;
					} else if (allAffected != null) {
						Preconditions.checkState(affected.isEmpty() && notAffected.isEmpty(),
								"Supplied buth @AffectsNone and also individual @Affects/@DoesNotAffect annotation(s)");
						affectsAll = true;
					}
					
//					System.out.println(getName()+" affected:");
//					for (String name : affected)
//						System.out.println("\t"+name);
//					System.out.println(getName()+" unaffected:");
//					for (String name : notAffected)
//						System.out.println("\t"+name);
//					System.out.println();
					
					this.notAffected = notAffected;
					this.affected = affected;
				}
			}
		}
	}
	
	/**
	 * Sets list of things that are affected/unaffected by this logic tree level. If processAnnotations is true,
	 * then annotations attached to this enum will be processed first but may be overridden by those passed in.
	 * If false, only those passed in will be retained.
	 * 
	 * @param affected collection of affected things
	 * @param notAffected collection of not affected things
	 * @param processAnnotations if true, annotations will be processed first and then these rules will be added
	 * (passed in rules will supersede if there is any overlap)
	 */
	public void setAffected(Collection<String> affected, Collection<String> notAffected, boolean processAnnotations) {
		Preconditions.checkNotNull(affected);
		Preconditions.checkNotNull(notAffected);
		if (processAnnotations) {
			// make sure we've loaded annotations
			checkParseAnnotations();
			
			// remove any references to those passed in
			List<String> allNew = new ArrayList<>();
			if (affected != null)
				allNew.addAll(affected);
			if (notAffected != null)
				allNew.addAll(notAffected);
			for (String val : allNew) {
				this.affected.remove(val);
				this.notAffected.remove(val);
			}
			
			// add those passed in
			this.affected = new ArrayList<>(this.affected);
			this.affected.addAll(affected);
			this.notAffected = new ArrayList<>(this.notAffected);
			this.notAffected.addAll(notAffected);
		} else {
			// override everything with those passed in
			this.affected = new ArrayList<>(affected);
			this.notAffected = new ArrayList<>(notAffected);
		}
	}
	
	public void setAffectsAll() {
		this.affected = List.of();
		this.notAffected = List.of();
		affectsAll = true;
		affectsNone = false;
	}
	
	public void setAffectsNone() {
		this.affected = List.of();
		this.notAffected = List.of();
		affectsAll = false;
		affectsNone = true;
	}
	
	public String toString() {
		return getName();
	}
	
	public boolean matchesType(Class<?> clazz) {
		Class<? extends E> type = getType();
		if (type.equals(clazz) || type.isAssignableFrom(clazz))
			return true;
		return false;
	}
	
	public static class FileBackedLevel extends LogicTreeLevel<FileBackedNode> {
		
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

		public FileBackedLevel(String name, String shortName, List<FileBackedNode> choices) {
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

		public AdapterBackedLevel(String name, String shortName, Class<? extends LogicTreeNode> nodeType) {
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
			// TODO why not force child class to implement this method?
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

	public static <E extends Enum<E> & LogicTreeNode> EnumBackedLevel<E> forEnum(
			Class<E> type, String name, String shortName) {
		return new EnumBackedLevel<>(name, shortName, type);
	}
	
	@SuppressWarnings("unchecked")
	public static <E extends LogicTreeNode> EnumBackedLevel<E> forEnumUnchecked(
			Class<?> type, String name, String shortName) {
		return new EnumBackedLevel<>(name, shortName, (Class<E>)type);
	}
	
	@SuppressWarnings("unchecked")
	public static <E extends LogicTreeNode> EnumBackedLevel<E> forEnumUnchecked(
			Object enumValue, String name, String shortName) {
		Class<? extends Object> type = enumValue.getClass();
		if (!type.isEnum())
			type = (Class<E>) type.getEnclosingClass();
		Preconditions.checkState(type.isEnum(), "Class is not an enum: %s", type);
		Preconditions.checkState(enumValue instanceof LogicTreeNode);
		return new EnumBackedLevel<>(name, shortName, (Class<E>)type);
	}
	
	public static class EnumBackedLevel<E extends LogicTreeNode> extends LogicTreeLevel<E> {
		
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
	
	public static abstract class RandomlySampledLevel<E extends RandomlySampledNode> extends LogicTreeLevel<E> {
		
		private List<? extends E> nodes;
		
		public void buildNodes(Random rand, int num) {
			double weightEach = 1d/(double)num;
			buildNodes(rand, num, weightEach);
		}
		
		public void buildNodes(Random rand, int num, double weightEach) {
			List<E> nodes = new ArrayList<>(num);
			
			Preconditions.checkState(num >= 1);
			for (int i=0; i<num; i++)
				nodes.add(buildNodeInstance(i, rand.nextLong(), weightEach));
			
			this.nodes = nodes;
		}
		
		public void buildNodes(List<Long> seeds, double weightEach) {
			List<E> nodes = new ArrayList<>(seeds.size());
			
			Preconditions.checkState(seeds.size() >= 1);
			for (int i=0; i<seeds.size(); i++)
				nodes.add(buildNodeInstance(i, seeds.get(i), weightEach));
			
			this.nodes = nodes;
		}
		
		@SuppressWarnings("unchecked")
		public void setNodes(List<? extends LogicTreeNode> nodes) {
			List<E> cast = new ArrayList<>(nodes.size());
			for (LogicTreeNode node : nodes) {
				Preconditions.checkState(node instanceof RandomlySampledNode);
				Preconditions.checkState(getType().isInstance(node));
				cast.add((E)node);
			}
			this.nodes = cast;
		}

		@Override
		public List<E> getNodes() {
			Preconditions.checkNotNull(nodes, "Nodes have not yet been built/set");
			return Collections.unmodifiableList(nodes);
		}
		
		public abstract E buildNodeInstance(int index, long seed, double weight);
		
		@Override
		public boolean isMember(LogicTreeNode node) {
			if (!(node instanceof RandomlySampledNode))
				return false;
			long seed = ((RandomlySampledNode)node).getSeed();
			for (E nodeTest : getNodes())
				if (node.equals(nodeTest) || seed == nodeTest.getSeed())
					return true;
			return false;
		}
	}
	
	public static class Adapter<E extends LogicTreeNode> extends TypeAdapter<LogicTreeLevel<? extends E>> {
		
		private boolean writeNodes;
		private NodeTypeAdapter nodeAdapter;

		public Adapter() {
			this(true);
		}
		
		public Adapter(boolean writeNodes) {
			this.writeNodes = writeNodes;
			nodeAdapter = new NodeTypeAdapter(null);
		}

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
			Collection<String> affected = level.getAffected();
			if (affected != null && !affected.isEmpty()) {
				out.name("affects").beginArray();
				for (String name : affected)
					out.value(name);
				out.endArray();
			}
			Collection<String> notAffected = level.getNotAffected();
			if (notAffected != null && !notAffected.isEmpty()) {
				out.name("doesNotAffect").beginArray();
				for (String name : notAffected)
					out.value(name);
				out.endArray();
			}
			if (level.affectsAll)
				out.name("affectsAll").value(true);
			if (level.affectsNone)
				out.name("affectsNone").value(true);
			if (writeNodes) {
				out.name("nodes").beginArray();;
				for (LogicTreeNode node : level.getNodes())
					nodeAdapter.write(out, node);
				out.endArray();
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
			List<String> affected = new ArrayList<>();
			List<String> notAffected = new ArrayList<>();
			boolean affectsAll = false;
			boolean affectsNone = false;
			List<LogicTreeNode> nodes = new ArrayList<>();
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
				case "affects":
					in.beginArray();
					while (in.hasNext())
						affected.add(in.nextString());
					in.endArray();
					break;
				case "doesNotAffect":
					in.beginArray();
					while (in.hasNext())
						notAffected.add(in.nextString());
					in.endArray();
					break;
				case "affectsAll":
					affectsAll = in.nextBoolean();
					break;
				case "affectsNone":
					affectsNone = in.nextBoolean();
					break;
				case "nodes":
					in.beginArray();
					while (in.hasNext())
						nodes.add(nodeAdapter.read(in));
					in.endArray();
					break;

				default:
					in.skipValue();
					break;
				}
			}
			
			if (affectsAll || affectsNone) {
				Preconditions.checkState(!affectsAll || !affectsNone, "both affectsAll and affectsNone are true?");
				Preconditions.checkState(affected.isEmpty(), "can't specify individual and blanket affectations");
				Preconditions.checkState(notAffected.isEmpty(), "can't specify individual and blanket affectations");
			}
			
			LogicTreeLevel<E> level = null;
			
			if (enumClassName != null) {
				// load it as an enum
				try {
					Class<?> rawClass = Class.forName(enumClassName);
					level = (LogicTreeLevel<E>) LogicTreeLevel.forEnumUnchecked(rawClass, name, shortName);
					// make sure all of the listed nodes still exist
					for (LogicTreeNode node : nodes) {
						if (!level.isMember(node)) {
							System.err.println("WARNING: Node "+node.getName()+" not found in enum "+enumClassName
									+", reverting to file backed level");
							level = null;
							break;
						}
					}
				} catch (ClassNotFoundException e) {
					System.err.println("WARNING: couldn't locate logic tree branch node enum class '"+enumClassName+"', "
							+ "loading plain/hardcoded version instead");
				} catch (ClassCastException e) {
					System.err.println("WARNING: logic tree branch node class '"+enumClassName+"' is of the wrong type, "
							+ "loading plain/hardcoded version instead");
				}
				if (level != null) {
					if (affectsAll)
						level.setAffectsAll();
					else if (affectsNone)
						level.setAffectsNone();
					else if (!affected.isEmpty() || !notAffected.isEmpty())
						//set the serialzed affected/unaffected levels
						level.setAffected(affected, notAffected, true);
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
					
					if (level instanceof RandomlySampledLevel<?>)
						((RandomlySampledLevel<?>)level).setNodes(nodes);
					if (affectsAll)
						level.setAffectsAll();
					else if (affectsNone)
						level.setAffectsNone();
					else if (!affected.isEmpty() || !notAffected.isEmpty())
						// set the serialzed affected/unaffected levels
						level.setAffected(affected, notAffected, true);
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
				FileBackedLevel fileLevel = new FileBackedLevel(name, shortName);
				if (affectsAll)
					fileLevel.setAffectsAll();
				else if (affectsNone)
					fileLevel.setAffectsNone();
				else
					fileLevel.setAffected(affected, notAffected, false);
				level = (LogicTreeLevel<E>) fileLevel;
				if (nodes != null) {
					for (LogicTreeNode node : nodes) {
						FileBackedNode fileNode;
						if (node instanceof FileBackedNode)
							fileNode = (FileBackedNode)node;
						else
							fileNode = new FileBackedNode(node.getName(), node.getShortName(),
									node.getNodeWeight(null), node.getFilePrefix());
						fileLevel.addChoice(fileNode);
					}
				}
			}
			
			in.endObject();
			return level;
		}
		
	}
	
	public static void main(String[] args) throws IOException {
		LogicTree.read(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2021_12_14-nshm23_draft_branches-coulomb-ineq-FM3_1-ZENGBB-Shaw09Mod-TotNuclRate-SubB1/logic_tree.json"));
	}

}
