package org.opensha.commons.logicTree;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.numbers.core.Precision;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.apache.commons.statistics.distribution.ContinuousDistribution.Sampler;
import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.logicTree.Affects.Affected;
import org.opensha.commons.logicTree.DoesNotAffect.NotAffected;
import org.opensha.commons.logicTree.LogicTreeBranch.NodeTypeAdapter;
import org.opensha.commons.logicTree.LogicTreeNode.FileBackedNode;
import org.opensha.commons.logicTree.LogicTreeNode.RandomlyGeneratedNode;
import org.opensha.commons.logicTree.LogicTreeNode.SimpleValuedNode;
import org.opensha.commons.logicTree.LogicTreeNode.ValuedLogicTreeNode;
import org.opensha.commons.util.FileNameUtils;
import org.opensha.commons.util.json.ContinuousDistributionTypeAdapter;
import org.opensha.commons.util.json.JsonAdapterHelper;
import org.opensha.commons.util.json.JsonObjectSerializable;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public abstract class LogicTreeLevel<E extends LogicTreeNode> implements ShortNamed {
	
	public abstract Class<? extends E> getType();
	
	public abstract List<? extends E> getNodes();
	
	public abstract boolean isMember(LogicTreeNode node);
	
	public String getFilePrefix() {
		return FileNameUtils.simplify(getShortName());
	}
	
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
	
	public void overrideIndividualAffected(String name, boolean toAffected) {
		checkParseAnnotations();
		Preconditions.checkState(!affectsAll, "Can't override an individual affected when affectsAll=true");
		Preconditions.checkState(!affectsNone, "Can't override an individual affected when affectsNone=true");
		if (toAffected) {
			this.notAffected.remove(name);
			if (!this.affected.contains(name))
				this.affected.add(name);
		} else {
			this.affected.remove(name);
			if (!this.notAffected.contains(name))
				this.notAffected.add(name);
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

	/**
	 * This is a LogicTreeLevel where the nodes have a custom adapter (not the data itself)
	 */
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
	
	
	/**
	 * Abstract class for a {@link LogicTreeLevel} with underlying data that can be serialized/deserialized as a
	 * {@link JsonObject} via the {@link JsonObjectSerializable} interface.
	 * @param <E>
	 */
	public static abstract class DataBackedLevel<E extends LogicTreeNode> extends LogicTreeLevel<E> implements JsonObjectSerializable {
		
		private String levelName;
		private String levelShortName;
		
		protected DataBackedLevel() {}
		
		protected DataBackedLevel(String levelName, String levelShortName) {
			this.levelName = levelName;
			this.levelShortName = levelShortName;
		}

		@Override
		public final String getShortName() {
			return levelShortName;
		}

		@Override
		public final String getName() {
			return levelName;
		}
	}
	
	/**
	 * Abstract implementation of {@link IndexedLevel} where node names are built by prepending prefixes to the index
	 * @param <E>
	 */
	public static abstract class IndexedLevel<E extends LogicTreeNode> extends DataBackedLevel<E> {
		
		private String nodeNamePrefix;
		private String nodeShortNamePrefix;
		private String nodeFilePrefix;
		
		protected IndexedLevel() {}
		
		protected IndexedLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}
		
		protected IndexedLevel(String levelName, String levelShortName,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName);
			this.nodeNamePrefix = nodeNamePrefix;
			this.nodeShortNamePrefix = nodeShortNamePrefix;
			this.nodeFilePrefix = nodeFilePrefix;
		}
		
		protected final String getNodeNamePrefix() {
			return nodeNamePrefix;
		}

		protected final String getNodeShortNamePrefix() {
			return nodeShortNamePrefix;
		}

		protected final String getNodeFilePrefix() {
			return nodeFilePrefix;
		}
		
		public final String getNodeName(int index) {
			return getNodeNamePrefix()+index;
		}
		public final String getNodeShortName(int index) {
			return getNodeShortNamePrefix()+index;
		}
		public final String getNodeFilePrefix(int index) {
			return getNodeFilePrefix()+index;
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			json.add("nodeType", new JsonPrimitive(getType().getName()));
			json.add("nodeNamePrefix", new JsonPrimitive(getNodeNamePrefix()));
			json.add("nodeShortNamePrefix", new JsonPrimitive(getNodeShortNamePrefix()));
			json.add("nodeFilePrefix", new JsonPrimitive(getNodeFilePrefix()));
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			if (jsonObj.has("nodeNamePrefix"))
				nodeNamePrefix = jsonObj.get("nodeNamePrefix").getAsString();
			if (jsonObj.has("nodeShortNamePrefix"))
				nodeShortNamePrefix = jsonObj.get("nodeShortNamePrefix").getAsString();
			if (jsonObj.has("nodeFilePrefix"))
				nodeFilePrefix = jsonObj.get("nodeFilePrefix").getAsString();
		}
		
	}
	
	/**
	 * {@link LogicTreeLevel} where nodes are of the {@link ValuedLogicTreeNode} type and can have a custom type
	 * adapter.
	 * 
	 * @param <E>
	 * @param <N>
	 */
	public static interface ValueBackedLevel<E, N extends ValuedLogicTreeNode<E>> {
		
		public abstract Class<? extends E> getValueType();
		
		@SuppressWarnings("unchecked")
		public default TypeAdapter<E> getValueTypeAdapter() {
			return JsonAdapterHelper.initTypeAdapter(getValueType(), true);
		}
		
		public N build(E value, double weight, String name, String shortName, String filePrefix);
		
		public default N buildUnchecked(Object value, double weight, String name, String shortName, String filePrefix) {
			return build((E)value, weight, name, shortName, filePrefix);
		}
	}
	
	/**
	 * {@link LogicTreeLevel} that is both indexed and value-backed; for these types, individual nodes do not need
	 * to be serialized (only their values)
	 * @param <E>
	 * @param <N>
	 */
	public static abstract class IndexedValuedLevel<E, N extends ValuedLogicTreeNode<E>> extends IndexedLevel<N>
	implements ValueBackedLevel<E,N> {
		
		protected IndexedValuedLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}
		
		protected IndexedValuedLevel(String levelName, String levelShortName,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
		}
		
		public N build(int index, E value, double weight) {
			return build(value, weight, getNodeName(index), getNodeShortName(index), getNodeFilePrefix(index));
		}
		
		protected abstract void setNodes(List<N> nodes);
		
		@SuppressWarnings("unchecked")
		void setNodesUnchecked(List<?> nodes) {
			setNodes((List<N>)nodes);
		}
		
		protected void init(List<E> values, List<Double> weights) {
			Preconditions.checkState(values.size() == weights.size());
			List<N> nodes = new ArrayList<>(values.size());
			for (int i=0; i<values.size(); i++)
				nodes.add(build(i, values.get(i), weights.get(i)));
			setNodes(nodes);
		}
		
		@SuppressWarnings("unchecked")
		private void initUnchecked(List<Object> values, List<Double> weights) {
			init((List<E>)values, weights);
		}
		
	}
	
	/**
	 * Valued {@link LogicTreeLevel} that can be fully reconstructed from an index and weight
	 * @param <E>
	 * @param <N>
	 */
	public static abstract class ValueByIndexLevel<E, N extends ValuedLogicTreeNode<E>> extends IndexedValuedLevel<E,N> {
		
		protected ValueByIndexLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}
		
		protected ValueByIndexLevel(String levelName, String levelShortName,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
		}
		
		public abstract E valueForIndex(int index);
		
		protected void init(List<Double> weights) {
			List<N> nodes = new ArrayList<>(weights.size());
			for (int i=0; i<weights.size(); i++)
				nodes.add(build(i, valueForIndex(i), weights.get(i)));
			setNodes(nodes);
		}
		
	}
	
	public static abstract class RandomLevel<E, N extends ValuedLogicTreeNode<E>> extends IndexedValuedLevel<E,N> {
		
		private long origSeed = -1l;
		
		protected List<N> nodes;
		
		protected RandomLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}
		
		protected RandomLevel(String levelName, String levelShortName,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
		}
		
		public final void build(long seed, int numNodes) {
			build(seed, numNodes, 1d/(double)numNodes);
		}
		
		public final void build(long seed, int numNodes, double weightEach) {
			this.origSeed = seed;
			doBuild(seed, numNodes, weightEach);
		}
		
		protected abstract void doBuild(long seed, int numNodes, double weightEach);

		public final boolean isBuilt() {
			return nodes != null;
		}
		
		@Override
		protected void setNodes(List<N> nodes) {
			this.nodes = nodes;
		}

		public final long getOriginalSeed() {
			return origSeed;
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = super.toJsonObject();
			json.add("nodeType", new JsonPrimitive(getType().getName()));
			if (origSeed != -1l)
				json.add("originalSeed", new JsonPrimitive(origSeed));
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			super.initFromJsonObject(jsonObj);
			if (jsonObj.has("originalSeed"))
				origSeed = jsonObj.get("originalSeed").getAsLong();
		}
		
	}
	
	public static abstract class AbstractRandomlySampledLevel<E, N extends ValuedLogicTreeNode<E>> extends RandomLevel<E,N> {
		
		protected AbstractRandomlySampledLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}

		public AbstractRandomlySampledLevel(String levelName, String levelShortName,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
		}
		
		protected void build(Supplier<E> randomValueSupplier, int numValues, double weightEach) {
			nodes = new ArrayList<>(numValues);
			for (int i=0; i<numValues; i++) {
				nodes.add(build(i, randomValueSupplier.get(), weightEach));
			}
		}
		
		void setValuesUnchecked(List<Object> values, double weightEach) {
			nodes = new ArrayList<>(values.size());
			int numValues = values.size();
			for (int i=0; i<numValues; i++) {
				nodes.add(build(i, (E)values.get(i), weightEach));
			}
		}
		
		public void setValues(List<? extends E> values, double weightEach) {
			nodes = new ArrayList<>(values.size());
			int numValues = values.size();
			for (int i=0; i<numValues; i++) {
				nodes.add(build(i, values.get(i), weightEach));
			}
		}

		@Override
		public List<? extends N> getNodes() {
			Preconditions.checkNotNull(nodes, "Nodes have not yet been built/set");
			return Collections.unmodifiableList(nodes);
		}
		
		@Override
		public boolean isMember(LogicTreeNode node) {
			return getType().isInstance(node) && nodes.contains(node);
		}
		
		public abstract Class<? extends E> getValueType();
		
		@SuppressWarnings("unchecked")
		public TypeAdapter<E> getValueTypeAdapter() {
			return JsonAdapterHelper.initTypeAdapter(getValueType(), true);
		}
		
	}
	
	public static abstract class RandomlySampledLevel<E> extends AbstractRandomlySampledLevel<E, SimpleValuedNode<E>> {
		
		protected RandomlySampledLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}

		public RandomlySampledLevel(String levelName, String levelShortName,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
		}

		@Override
		public SimpleValuedNode<E> build(E value, double weight, String name, String shortName, String filePrefix) {
			return new SimpleValuedNode<E>(value, getValueType(), weight, name, shortName, filePrefix);
		}

		@SuppressWarnings("unchecked")
		@Override
		public Class<? extends SimpleValuedNode<E>> getType() {
			return (Class<? extends SimpleValuedNode<E>>) (Class<?>) SimpleValuedNode.class;
		}
		
	}
	
	public static abstract class WeightedListSampledLevel<E> extends RandomlySampledLevel<E> {

		private WeightedList<E> weightedValues;
		
		protected WeightedListSampledLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}

		public WeightedListSampledLevel(String levelName, String levelShortName, WeightedList<E> weightedValues,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
			this.weightedValues = weightedValues;
		}

		@Override
		protected void doBuild(long seed, int numSamples, double weightEach) {
			Random rand = new Random(seed);
			build(()->{return weightedValues.sample(rand);}, numSamples, weightEach);
		}
		
	}
	
	public static abstract class AbstractContinuousDistributionSampledLevel<N extends ValuedLogicTreeNode<Double>>
	extends AbstractRandomlySampledLevel<Double, N> {

		private ContinuousDistribution dist;
		private int precisionScale;
		
		protected AbstractContinuousDistributionSampledLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}
		
		public AbstractContinuousDistributionSampledLevel(String levelName, String levelShortName,
				ContinuousDistribution dist, String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			this(levelName, levelShortName, dist, -1, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
		}

		public AbstractContinuousDistributionSampledLevel(String levelName, String levelShortName,
				ContinuousDistribution dist, int precisionScale,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
			this.dist = dist;
			this.precisionScale = precisionScale;
		}
		
		@Override
		protected void doBuild(long seed, int numSamples, double weightEach) {
			build(RandomSource.XO_RO_SHI_RO_128_PP.create(seed), numSamples, weightEach);
		}
		
		protected void build(UniformRandomProvider rand, int numSamples, double weightEach) {
			Sampler sampler = dist.createSampler(rand);
			build(()->{
				double sample = sampler.sample();
				if (precisionScale > 0)
					sample = Precision.round(sample, precisionScale);
				return sample;
			}, numSamples, weightEach);
		}
		
		public ContinuousDistribution getDistribution() {
			return dist;
		}
		
		public void setDistribution(ContinuousDistribution dist) {
			Preconditions.checkState(!isBuilt(), "Cannot change the distribution after random nodes have been built");
		}

		@Override
		public Class<? extends Double> getValueType() {
			return Double.class;
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = super.toJsonObject();
			
			if (dist != null)
				json.add("distribution", ContinuousDistributionTypeAdapter.get().toJsonTree(dist));
			if (precisionScale > 0)
				json.add("precisionScale", new JsonPrimitive(precisionScale));
			
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			super.initFromJsonObject(jsonObj);
			
			if (jsonObj.has("distribution"))
				dist = ContinuousDistributionTypeAdapter.get().fromJsonTree(jsonObj.get("distribution"));
			if (jsonObj.has("precisionScale"))
				precisionScale = jsonObj.get("precisionScale").getAsInt();
			else
				precisionScale = -1;
		}
		
	}
	
	public static abstract class ContinuousDistributionSampledLevel
	extends AbstractContinuousDistributionSampledLevel<SimpleValuedNode<Double>> {
		
		protected ContinuousDistributionSampledLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}

		public ContinuousDistributionSampledLevel(String levelName, String levelShortName, ContinuousDistribution dist,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName, dist, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
		}

		public ContinuousDistributionSampledLevel(String levelName, String levelShortName,
				ContinuousDistribution dist, int precisionScale,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName, dist, precisionScale, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
		}

		@Override
		public SimpleValuedNode<Double> build(Double value, double weight, String name, String shortName,
				String filePrefix) {
			return new SimpleValuedNode<Double>(value, Double.class, weight, name, shortName, filePrefix);
		}

		@SuppressWarnings("unchecked")
		@Override
		public Class<? extends SimpleValuedNode<Double>> getType() {
			// this extra cast to Class<?> resolves compile errors that don't show up in eclipse, which is annoying
			return (Class<? extends SimpleValuedNode<Double>>) (Class<?>) SimpleValuedNode.class;
		}
		
	}
	
	public static abstract class RandomlyGeneratedLevel<E extends RandomlyGeneratedNode> extends RandomLevel<Long, E> {
		
		protected RandomlyGeneratedLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}
		
		protected RandomlyGeneratedLevel(String levelName, String levelShortName,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
		}
		
		@Override
		protected void doBuild(long seed, int num, double weightEach) {
			buildNodes(new Random(seed), num, weightEach);
		}
		
		protected void buildNodes(Random rand, int num, double weightEach) {
			List<E> nodes = new ArrayList<>(num);
			
			Preconditions.checkState(num >= 1);
			for (int i=0; i<num; i++)
				nodes.add(build(i, rand.nextLong(), weightEach));
			
			this.nodes = nodes;
		}
		
		protected void buildNodes(List<Long> seeds, double weightEach) {
			List<E> nodes = new ArrayList<>(seeds.size());
			
			Preconditions.checkState(seeds.size() >= 1);
			for (int i=0; i<seeds.size(); i++)
				nodes.add(build(i, seeds.get(i), weightEach));
			
			this.nodes = nodes;
		}
		
		@Override
		public List<E> getNodes() {
			Preconditions.checkNotNull(nodes, "Nodes have not yet been built/set");
			return Collections.unmodifiableList(nodes);
		}
		
		@Override
		public boolean isMember(LogicTreeNode node) {
			if (!(node instanceof RandomlyGeneratedNode))
				return false;
			long seed = ((RandomlyGeneratedNode)node).getSeed();
			for (E nodeTest : getNodes())
				if (node.equals(nodeTest) || seed == nodeTest.getSeed())
					return true;
			return false;
		}

		@Override
		public Class<? extends Long> getValueType() {
			return Long.class;
		}
	}
	
	public static class Adapter<E extends LogicTreeNode> extends TypeAdapter<LogicTreeLevel<? extends E>> {
		
		private boolean writeNodes;
		private NodeTypeAdapter nodeAdapter;
		private boolean forceFileBacked;

		public Adapter() {
			this(true);
		}
		
		public Adapter(boolean writeNodes) {
			this(writeNodes, false);
		}
		
		public Adapter(boolean writeNodes, boolean forceFileBacked) {
			this.writeNodes = writeNodes;
			this.forceFileBacked = forceFileBacked;
			nodeAdapter = new NodeTypeAdapter(null, forceFileBacked);
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
			if (level instanceof JsonObjectSerializable) {
				JsonObject data = ((JsonObjectSerializable)level).toJsonObject();
				if (data != null) {
					out.name("data");
					JsonObjectSerializable.writeJsonObjectToWriter(data, out);
				}
			}
			if (level instanceof IndexedValuedLevel) {
				// don't need to write node data, can just write values/weights
				out.name("values").beginObject();
				boolean allSameWeight = true;
				List<? extends E> nodes = level.getNodes();
				double[] weights = new double[nodes.size()];
				for (int i=0; i<weights.length; i++) {
					weights[i] = ((ValuedLogicTreeNode<?>)nodes.get(i)).getNodeWeight();
					allSameWeight &= weights[i] == weights[0];
				}
				ValueBackedLevel<?,?> valueLevel = (ValueBackedLevel<?,?>)level;
				out.name("valueClass").value(valueLevel.getValueType().getName());
				out.name("count").value(nodes.size());
				if (allSameWeight) {
					out.name("weightEach").value(weights[0]);
				} else {
					out.name("weights").beginArray();
					for (double weight : weights)
						out.value(weight);
					out.endArray();
				}
				if (!(level instanceof ValueByIndexLevel)) {
					// write the values
					out.name("values").beginArray();
					TypeAdapter valueAdapter = valueLevel.getValueTypeAdapter();
					for (E node : nodes) {
						Object value = ((ValuedLogicTreeNode<?>)node).getValue();
						if (value == null)
							out.nullValue();
						else
							valueAdapter.write(out, value);
					}
					out.endArray();
				}
				out.endObject();
			} else if (writeNodes) {
				out.name("nodes").beginArray();
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
			
			// for JsonObjectSerializable levels
			JsonObject data = null;
			JsonObject valueData = null;
			
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
					Preconditions.checkState(nodes.isEmpty(), "Nodes already supplied?");
					in.beginArray();
					while (in.hasNext())
						nodes.add(nodeAdapter.read(in));
					in.endArray();
					break;
				case "data":
					if (in.peek() == JsonToken.NULL) {
						in.skipValue();
						continue;
					}
					JsonElement dataElem = JsonParser.parseReader(in);
					Preconditions.checkState(dataElem.isJsonObject(), "Level data must be a JsonObject");
					data = dataElem.getAsJsonObject();
					break;
				case "values":
					if (in.peek() == JsonToken.NULL) {
						in.skipValue();
						continue;
					}
					JsonElement valueElem = JsonParser.parseReader(in);
					Preconditions.checkState(valueElem.isJsonObject(), "Level values must be a JsonObject");
					valueData = valueElem.getAsJsonObject();
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
			
			if (!forceFileBacked && enumClassName != null) {
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
			if (!forceFileBacked && level == null && className != null) {
				// try to load it as a class via default constructor
				try {
					Class<?> rawClass = Class.forName(className);
					Class<? extends LogicTreeLevel<E>> clazz = (Class<? extends LogicTreeLevel<E>>)rawClass;
					
					if (DataBackedLevel.class.isAssignableFrom(clazz)) {
						// try first with level name and short name
						try {
							Constructor<? extends LogicTreeLevel<E>> constructor = clazz.getDeclaredConstructor(String.class, String.class);
							constructor.setAccessible(true);
							
							level = constructor.newInstance(name, shortName);
							Preconditions.checkState(name == null || level.getName().equals(name));
							Preconditions.checkState(shortName == null || level.getShortName().equals(shortName));
						} catch (NoSuchMethodException e) {
							// fall back to no arg constructor
							Constructor<? extends LogicTreeLevel<E>> constructor = clazz.getDeclaredConstructor();
							constructor.setAccessible(true);
							
							level = constructor.newInstance();
							if (name != null)
								((DataBackedLevel<?>)level).levelName = name;
							if (shortName != null)
								((DataBackedLevel<?>)level).levelShortName = shortName;
						}
					} else {
						Constructor<? extends LogicTreeLevel<E>> constructor = clazz.getDeclaredConstructor();
						constructor.setAccessible(true);
						
						level = constructor.newInstance();
					}
					
					if (data != null || JsonObjectSerializable.class.isAssignableFrom(clazz)) {
						if (data == null) {
							// no data
							if (nodes != null && level instanceof RandomlyGeneratedLevel<?>) {
								System.err.println("Warning: old (pre-JsonObjectSerializable) JSON for class "+clazz
										+" encounterd, will load nodes directly");
								Preconditions.checkState(!nodes.isEmpty(), "Nodes are empty");
								LogicTreeNode node0 = nodes.get(0);
								RandomlyGeneratedLevel<?> randLevel = ((RandomlyGeneratedLevel<?>)level);
								randLevel.setNodesUnchecked(nodes);
								IndexedLevel<?> indexedLevel = ((IndexedLevel<?>)level);
								String name0 = node0.getName();
								indexedLevel.nodeNamePrefix = name0.substring(0, name0.lastIndexOf('0'));
								String shortName0 = node0.getShortName();
								indexedLevel.nodeShortNamePrefix = shortName0.substring(0, shortName0.lastIndexOf('0'));
								String filePrefix0 = node0.getFilePrefix();
								indexedLevel.nodeFilePrefix = filePrefix0.substring(0, filePrefix0.lastIndexOf('0'));
							} else {
								System.err.println("Warning: class "+clazz+" is an instance of JsonObjectSerializable but "
										+ "no data was found");
							}
						} else if (!JsonObjectSerializable.class.isAssignableFrom(clazz)) {
							// not an JsonObjectSerializable instance
							System.err.println("Warning: class "+clazz+" is not an instance of JsonObjectSerializable "
									+ "but we encountered a data object, reverting to file-backed");
							level = null;
						} else {
							// have both
							((JsonObjectSerializable)level).initFromJsonObject(data);
						}
					}
					
					if (valueData != null || IndexedValuedLevel.class.isAssignableFrom(clazz)) {
						if (valueData == null) {
							// no values
							System.err.println("Warning: class "+clazz+" is an IndexedValuedLevel instance but no value"
									+ " data was found");
						} else if (!IndexedValuedLevel.class.isAssignableFrom(clazz)) {
							System.err.println("Warning: class "+clazz+" is not an instance of IndexedValuedLevel "
									+ "but we encountered a values object, reverting to file-backed");
							level = null;
						} else {
							// have both
							List<Double> weights = null;
							Double weightEach = null;
							if (valueData.has("weightEach")) {
								weightEach = valueData.get("weightEach").getAsDouble();
							} else if (valueData.has("weights")) {
								JsonArray weightsArray = valueData.getAsJsonArray("weights");
								weights = new ArrayList<>(weightsArray.size());
								for (int i=0; i<weightsArray.size(); i++)
									weights.add(weightsArray.get(i).getAsDouble());
							} else {
								throw new IllegalStateException("Value data has neither weights nor weightEach");
							}
							if (valueData.has("values")) {
								IndexedValuedLevel<?, ?> valueLevel = (IndexedValuedLevel<?, ?>)level;
								TypeAdapter<?> adapter = valueLevel.getValueTypeAdapter();
								JsonArray valuesArray = valueData.getAsJsonArray("values");
								List<Object> values = new ArrayList<>(valuesArray.size());
								if (weights == null)
									weights = new ArrayList<>(valuesArray.size());
								for (int i=0; i<valuesArray.size(); i++) {
									JsonElement valueElem = valuesArray.get(i);
									if (valueElem.isJsonNull())
										values.add(null);
									else
										values.add(adapter.fromJsonTree(valueElem));
									if (weightEach != null)
										weights.add(weightEach);
								}
								Preconditions.checkState(weights.size() == values.size());
								valueLevel.initUnchecked(values, weights);
							} else if (level instanceof ValueByIndexLevel<?,?>) {
								if (weights == null) {
									Preconditions.checkState(valueData.has("count"), "Have weightEach but no count and not values array");
									int count = valueData.get("count").getAsInt();
									weights = new ArrayList<>(count);
									for (int i=0; i<count; i++)
										weights.add(weightEach);
								}
								((ValueByIndexLevel<?,?>)level).init(weights);
							}
						}
					}
					
					if (affectsAll)
						level.setAffectsAll();
					else if (affectsNone)
						level.setAffectsNone();
					else if (!affected.isEmpty() || !notAffected.isEmpty())
						// set the serialzed affected/unaffected levels
						level.setAffected(affected, notAffected, true);
//					System.out.println("Built a level of type "+level.getClass().getName()+" and name="+level.getName()+", shortName="+level.getShortName());
				} catch (ClassNotFoundException e) {
					System.err.println("WARNING: couldn't locate logic tree branch node class '"+className+"', "
							+ "loading plain/hardcoded version instead");
				} catch (ClassCastException e) {
					System.err.println("WARNING: logic tree branch node class '"+className+"' is of the wrong type, "
							+ "loading plain/hardcoded version instead");
				} catch (Exception e) {
					e.printStackTrace();
					System.err.println("Couldn't instantiate default no-arg constructor of declared logic tree node class, "
								+ "loading plain/hardcoded version instead: "+e.getMessage());
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
				if (nodes.isEmpty() && data != null && valueData != null) {
					// failed valued/random load
					String nodeNamePrefix = data.get("nodeNamePrefix").getAsString();
					String nodeShortNamePrefix = data.get("nodeShortNamePrefix").getAsString();
					String nodeFilePrefix = data.get("nodeFilePrefix").getAsString();
					List<Double> weights = null;
					Double weightEach = null;
					if (valueData.has("weightEach")) {
						weightEach = valueData.get("weightEach").getAsDouble();
					} else if (valueData.has("weights")) {
						JsonArray weightsArray = valueData.getAsJsonArray("weights");
						weights = new ArrayList<>(weightsArray.size());
						for (int i=0; i<weightsArray.size(); i++)
							weights.add(weightsArray.get(i).getAsDouble());
					} else {
						throw new IllegalStateException("Value data has neither weights nor weightEach");
					}
					int num = data.has("count") ? data.get("count").getAsInt() : data.getAsJsonArray("values").size();
					for (int i=0; i<num; i++) {
						double weight = weights == null ? weightEach : weights.get(i);
						nodes.add(new FileBackedNode(nodeNamePrefix+i, nodeShortNamePrefix+i, weight, nodeFilePrefix+i));
					}
				}
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
			
			in.endObject();
			return level;
		}
		
	}
	
	public static void main(String[] args) throws IOException {
		LogicTree.read(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2021_12_14-nshm23_draft_branches-coulomb-ineq-FM3_1-ZENGBB-Shaw09Mod-TotNuclRate-SubB1/logic_tree.json"));
	}

}
