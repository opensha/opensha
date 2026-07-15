package org.opensha.commons.logicTree;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

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
import org.opensha.commons.logicTree.LogicTreeNode.FixedWeightNode;
import org.opensha.commons.logicTree.LogicTreeNode.RandomlyGeneratedNode;
import org.opensha.commons.logicTree.LogicTreeNode.SimpleValuedNode;
import org.opensha.commons.logicTree.LogicTreeNode.ValuedLogicTreeNode;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.FileNameUtils;
import org.opensha.commons.util.json.ContinuousDistributionTypeAdapter;
import org.opensha.commons.util.json.DoubleRangeAdapter;
import org.opensha.commons.util.json.JsonAdapterHelper;
import org.opensha.commons.util.json.JsonObjectSerializable;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
		Preconditions.checkState(!affectsAll, "Can't override an individual affected for %s when affectsAll=true", getShortName());
		Preconditions.checkState(!affectsNone, "Can't override an individual affected for %s when affectsNone=true", getShortName());
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
		
		protected final E getNodeForFilePrefix(String filePrefix) {
			Preconditions.checkNotNull(nodeFilePrefix);
			filePrefix = filePrefix.trim();
			if (!filePrefix.startsWith(nodeFilePrefix))
				return null;
			int index = Integer.valueOf(filePrefix.substring(nodeFilePrefix.length()));
			E node = getNodes().get(index);
			Preconditions.checkState(node.getFilePrefix().equals(filePrefix));
			return node;
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
			String namePrefix = getNodeNamePrefix();
			if (namePrefix != null)
				json.add("nodeNamePrefix", new JsonPrimitive(namePrefix));
			String shortNamePrefix = getNodeShortNamePrefix();
			if (shortNamePrefix != null)
				json.add("nodeShortNamePrefix", new JsonPrimitive(shortNamePrefix));
			String nodeFilePrefix = getNodeFilePrefix();
			if (nodeFilePrefix != null)
				json.add("nodeFilePrefix", new JsonPrimitive(nodeFilePrefix));
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
	public static interface ValueBackedLevel<E, N extends ValuedLogicTreeNode<? super E>> {
		
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
	public static abstract class IndexedValuedLevel<E, N extends ValuedLogicTreeNode<? super E>> extends IndexedLevel<N>
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
	public static interface ValueByIndexLevel<E, N extends ValuedLogicTreeNode<? super E>> {
		
		public abstract E valueForIndex(int index);
		
		public void init(List<Double> weights);
		
	}
	
	/**
	 * Valued {@link LogicTreeLevel} that can be fully reconstructed from an index and weight
	 * @param <E>
	 * @param <N>
	 */
	public static abstract class AbstractValueByIndexLevel<E, N extends ValuedLogicTreeNode<E>> extends IndexedValuedLevel<E,N>
	implements ValueByIndexLevel<E, N> {
		
		protected AbstractValueByIndexLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}
		
		protected AbstractValueByIndexLevel(String levelName, String levelShortName,
				String nodeNamePrefix, String nodeShortNamePrefix, String nodeFilePrefix) {
			super(levelName, levelShortName, nodeNamePrefix, nodeShortNamePrefix, nodeFilePrefix);
		}
		
		public void init(List<Double> weights) {
			List<N> nodes = new ArrayList<>(weights.size());
			for (int i=0; i<weights.size(); i++)
				nodes.add(build(i, valueForIndex(i), weights.get(i)));
			setNodes(nodes);
		}
		
	}
	
	public enum SamplingMethod implements ShortNamed {
		/**
		 * Purely random-sampling
		 */
		MONTE_CARLO("Monte Carlo", "MCS", "mcs"),
		/**
		 * Stratify the uncertainties into equal-probability bins, sample a value from each bin, and then shuffle the
		 * order. This ensures the full marginal distribution is sampled for each level
		 */
		LATIN_HYPERCUBE("Latin Hypercube", "LHS", "lhs"),
		/**
		 * Extension of {@link #LATIN_HYPERCUBE} in which samples are balanced between pairs of choices and not just
		 * their own marginal distributions. This is done iteratively.
		 */
		PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE("Pairwise-Optimized Latin Hypercube", "Pairwise-LHS", "lhs_pairwise");
		
		private String name;
		private String shortName;
		private String filePrefix;

		private SamplingMethod(String name, String shortName, String filePrefix) {
			this.name = name;
			this.shortName = shortName;
			this.filePrefix = filePrefix;
		}
		
		public boolean isMC() {
			return !isLHS();
		}
		
		public boolean isLHS() {
			return this == LATIN_HYPERCUBE || this == PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getShortName() {
			return shortName;
		}
		
		public String getFilePrefix() {
			return filePrefix;
		}
	}
	
	public static abstract class RandomLevel<E, N extends ValuedLogicTreeNode<? super E>> extends IndexedValuedLevel<E,N> {
		
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
			build(seed, numNodes, SamplingMethod.MONTE_CARLO);
		}
		
		public final void build(long seed, int numNodes, SamplingMethod samplingMethod) {
			build(seed, numNodes, samplingMethod, 1d/(double)numNodes);
		}
		
		public final void build(long seed, int numNodes, SamplingMethod samplingMethod, double weightEach) {
			this.origSeed = seed;
			doBuild(seed, numNodes, samplingMethod, weightEach);
		}
		
		protected abstract void doBuild(long seed, int numNodes, SamplingMethod samplingMethod, double weightEach);

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
	
	public static abstract class AbstractRandomlySampledLevel<E, N extends ValuedLogicTreeNode<? super E>> extends RandomLevel<E,N> {
		
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
			return getType().isInstance(node);
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
		protected void doBuild(long seed, int numSamples, SamplingMethod samplingMethod, double weightEach) {
			if (samplingMethod.isLHS()) {
				doBuildLHS(seed, numSamples, weightEach);
			} else {
				// monte-carlo otherwise
				Random rand = new Random(seed);
				build(()->{return weightedValues.sample(rand);}, numSamples, weightEach);
			}
		}
		
		protected void doBuildLHS(long seed, int numSamples, double weightEach) {
			List<E> samples = weightedValues.sampleEvenly(numSamples, new Random(seed));
			
			Iterator<E> it = samples.iterator();
			build(() -> {
				return it.next();
			}, numSamples, weightEach);
		}
	}
	
	public static abstract class AbstractContinuousDistributionSampledLevel<N extends ValuedLogicTreeNode<Double>>
	extends AbstractRandomlySampledLevel<Double, N> implements BinnableLevel<Double, N, ContinuousDistributionBinnedLevel> {

		private ContinuousDistribution dist;
		private int precisionScale;
		private String units;
		
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
		protected void doBuild(long seed, int numSamples, SamplingMethod samplingMethod, double weightEach) {
			build(RandomSource.XO_RO_SHI_RO_128_PP.create(seed), numSamples, samplingMethod, weightEach);
		}
		
		protected void build(UniformRandomProvider rand, int numSamples, SamplingMethod samplingMethod, double weightEach) {
			if (samplingMethod.isLHS())
				buildLHS(rand, numSamples, weightEach);
			else
				// fallback
				buildMonteCarlo(rand, numSamples, weightEach);
		}
		
		protected void buildLHS(UniformRandomProvider rand, int numSamples, double weightEach) {
			double[] samples = new double[numSamples];
			for (int i=0; i<numSamples; i++) {
				double binStart = (double)i / numSamples;
				double binEnd = (double)(i + 1) / numSamples;
				double p = rand.nextDouble(binStart, binEnd);
				samples[i] = getRoundedToPrecision(dist.inverseCumulativeProbability(p));
			}
			// shuffle them according to the uniform random provider
			DataUtils.shuffle(samples, rand);
			
			build(new Supplier<Double>() {
				int index = 0;
				@Override
				public Double get() {
					if (index >= samples.length)
						throw new IllegalStateException("No more LHS samples available");
					return samples[index++];
				}
			}, numSamples, weightEach);
		}
		
		protected void buildMonteCarlo(UniformRandomProvider rand, int numSamples, double weightEach) {
			Sampler sampler = dist.createSampler(rand);
			build(()->{
				double sample = getRoundedToPrecision(sampler.sample());
				return sample;
			}, numSamples, weightEach);
		}
		
		public ContinuousDistribution getDistribution() {
			return dist;
		}
		
		public int getPrecisionScale() {
			return precisionScale;
		}
		
		public void setUnits(String units) {
			this.units = units;
		}
		
		public String getUnits() {
			return units;
		}
		
		public double getRoundedToPrecision(double value) {
			if (precisionScale > 0)
				return Precision.round(value, precisionScale);
			return value;
		}
		
		public double getLowerBound() {
			double lower = dist.getSupportLowerBound();
			if (precisionScale < 1 || !Double.isFinite(lower))
				return lower;
			double binWidth = 1d/Math.pow(10, precisionScale);
			// round edges up
			return getRoundedToPrecision(lower + 0.01*binWidth);
		}
		
		public double getUpperBound() {
			double upper = dist.getSupportUpperBound();
			if (precisionScale < 1 || !Double.isFinite(upper))
				return upper;
			double binWidth = 1d/Math.pow(10, precisionScale);
			// round edges down
			return getRoundedToPrecision(upper - 0.01*binWidth);
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
			if (units != null && !units.isBlank())
				json.add("units", new JsonPrimitive(units));
			
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
			if (jsonObj.has("units"))
				units = jsonObj.get("units").getAsString();
		}
		
		private static DecimalFormat oDF = new DecimalFormat("0.##");
		
		@Override
		public ContinuousDistributionBinnedLevel toBinnedLevel() {
			return toBinnedLevel(3);
		}
		
		@Override
		public ContinuousDistributionBinnedLevel toBinnedLevel(int numBins) {
			Preconditions.checkState(numBins > 0);
			List<Double> binEdges = new ArrayList<>(numBins+1);
			List<String> names = new ArrayList<>(numBins);
			List<String> shortNames = new ArrayList<>(numBins);
			
			binEdges.add(getLowerBound());
			double probEach = 1d/(double)numBins;
			double startP = 0d;
			for (int i=0; i<numBins; i++) {
				double lower = binEdges.get(i);
				double upper;
				double endP;
				if (i == numBins-1) {
					// last
					endP = 1d;
					upper = dist.getSupportUpperBound();
				} else {
					// intermediate
					endP = startP + probEach;
					upper = dist.inverseCumulativeProbability(endP);
				}
				
				double lowerForStr = lower;
				double upperForStr = upper;
				
				if (precisionScale > 0) {
					double binWidth = 1d/Math.pow(10, precisionScale);
//					System.out.println("raw lower="+lower+", upper="+upper+", binWidth="+binWidth);
					
					if (numBins == 3) {
						// expand center bin to precision edges
						if (i == 0) {
							// we're working on the left edge of the center bin
							upper = Precision.round(upper, precisionScale) - 0.5*binWidth;
						} else if (i == 1) {
							// we're working on the right edge of the center bin
							upper = Precision.round(upper, precisionScale) + 0.5*binWidth;
						}
//						System.out.println("mod lower="+lower+", upper="+upper);
					}
					
					// make it round up
					lowerForStr = Precision.round(lower + 0.01*binWidth, precisionScale);
					// make it round down
					upperForStr = Precision.round(upper - 0.01*binWidth, precisionScale);
//					System.out.println("Building bin "+i+"; lower="+lower+", lowerForStr="+lowerForStr+", upper="+upper+", upperForStr="+upperForStr);
				}
				
				binEdges.add(upper);
				
				String binStr;
				if (Double.isInfinite(lower) && Double.isInfinite(upper))
					binStr = "["+Double.POSITIVE_INFINITY+"]";
				else if (Double.isInfinite(lowerForStr))
					binStr = "<"+formatVal(upperForStr);
				else if (Double.isInfinite(upperForStr))
					binStr = ">"+formatVal(lowerForStr);
				else
					binStr = "["+formatVal(lowerForStr)+", "+formatVal(upperForStr)+"]";
				
				String name;
				if (numBins == 1 || numBins > 3) {
					name = binStr;
				} else if (i == 0) {
					name = "Low: "+binStr;
				} else if (i == numBins-1) {
					name = "High: "+binStr;
				} else {
					name = "Middle: "+binStr;
				}
				names.add(name);
				shortNames.add(binStr);
				
				startP = endP;
			}
			
			return toBinnedLevel(binEdges, names, shortNames);
		}

		private double cumulativeProbabilityForBound(double value) {
			if (Double.isInfinite(value) || value >= dist.getSupportUpperBound())
				return 1d;
			if (value <= dist.getSupportLowerBound())
				return 0d;
			return dist.cumulativeProbability(value);
		}

		private double discretizedLowerBound(double value, double step, double lowerSupport) {
			return Math.max(lowerSupport, value - 0.5d * step);
		}

		private double discretizedUpperBound(double value, double step, double upperSupport) {
			return Math.min(upperSupport, value + 0.5d * step);
		}

		private String[] buildBinLabels(int numBins, int binIndex, double lower, double upper) {
			String binStr;
			if (Double.isInfinite(lower) && Double.isInfinite(upper))
				binStr = "["+Double.POSITIVE_INFINITY+"]";
			else if (Double.isInfinite(lower))
				binStr = "<"+formatVal(upper);
			else if (Double.isInfinite(upper))
				binStr = ">"+formatVal(lower);
			else
				binStr = "["+formatVal(lower)+", "+formatVal(upper)+"]";
			
			String name;
			if (numBins == 1 || numBins > 3) {
				name = binStr;
			} else if (binIndex == 0) {
				name = "Low: "+binStr;
			} else if (binIndex == numBins-1) {
				name = "High: "+binStr;
			} else {
				name = "Middle: "+binStr;
			}
			return new String[] {name, binStr};
		}
		
		private static String formatVal(double val) {
			if (Double.isInfinite(val))
				return val+"";
			if (Math.abs(val) < 1e-1)
				return (float)val+"";
			if (val >= 999.9) {
				if (val > 1e5)
					return (float)val+"";
				return (int)val+"";
			}
			return oDF.format(val);
		}
		
		public ContinuousDistributionBinnedLevel toBinnedLevel(List<Double> binEdges, List<String> names, List<String> shortNames) {
			Preconditions.checkState(binEdges.size() > 1);
			int numBins = binEdges.size()-1;
			Preconditions.checkState(names.size() == numBins,
					"Must have exactly one name per bin (numBins=%s, edges=%s, names=%s)", numBins, binEdges, names);
			Preconditions.checkState(shortNames == null || shortNames.size() == numBins);
			List<SimpleValuedNode<Range<Double>>> nodes = new ArrayList<>();
			
			for (int i=0; i<numBins; i++) {
				double lower = binEdges.get(i);
				double upper = binEdges.get(i+1);
				Range<Double> range;
				if (numBins == 1 || i == numBins -1)
					range = Range.closed(lower, upper);
				else
					range = Range.closedOpen(lower, upper);
				String name = names.get(i);
				String shortName = shortNames == null ? name : shortNames.get(i);
				double cdf0;
				if (Double.isInfinite(lower) || lower <= dist.getSupportLowerBound())
					cdf0 = 0;
				else
					cdf0 = dist.cumulativeProbability(lower);
				double cdf1;
				if (Double.isInfinite(upper) || upper >= dist.getSupportUpperBound())
					cdf1 = 1d;
				else
					cdf1 = dist.cumulativeProbability(upper);
				double weight = cdf1 - cdf0;
				SimpleValuedNode<Range<Double>> node = new SimpleValuedNode<Range<Double>>(
						range, ContinuousDistributionBinnedLevel.VALUE_TYPE, weight, name, shortName, "Bin"+i);
				nodes.add(node);
			}
			
			return new ContinuousDistributionBinnedLevel(this, nodes);
		}
		
	}
	
	public interface BinnableLevel<E, N extends ValuedLogicTreeNode<? super E>,
	B extends LogicTreeLevel<?> & BinnedLevel<E,?>> extends ValueBackedLevel<E, N> {
		
		public B toBinnedLevel();
		
		public B toBinnedLevel(int numBins);
		
	}
	
	public interface BinnedLevel<E, N extends FixedWeightNode> {
		
		@SuppressWarnings("unchecked")
		public default N getBinUnchecked(LogicTreeNode node) {
			return getBin((ValuedLogicTreeNode<E>)node);
		}
		
		public default N getBin(ValuedLogicTreeNode<E> node) {
			return getBin(node.getValue());
		}
		
		public N getBin(E value);
		
	}
	
	public static class ContinuousDistributionBinnedLevel extends DataBackedLevel<SimpleValuedNode<Range<Double>>> 
	implements ValueBackedLevel<Range<Double>, SimpleValuedNode<Range<Double>>>,
	BinnedLevel<Double, SimpleValuedNode<Range<Double>>> {
		
		private List<SimpleValuedNode<Range<Double>>> nodes;
		
		// this extra cast to Class<?> resolves compile errors that don't show up in eclipse, which is annoying
		private static Class<? extends SimpleValuedNode<Range<Double>>> TYPE =
				(Class<SimpleValuedNode<Range<Double>>>) (Class<?>) SimpleValuedNode.class;
		private static Class<? extends Range<Double>> VALUE_TYPE =
				(Class<? extends Range<Double>>) (Class<?>) Range.class;
		
		@SuppressWarnings("unused") // deserialization
		private ContinuousDistributionBinnedLevel() {};
		
		public ContinuousDistributionBinnedLevel(
				AbstractContinuousDistributionSampledLevel<? extends ValuedLogicTreeNode<Double>> samplingLevel,
				List<SimpleValuedNode<Range<Double>>> nodes) {
			super(samplingLevel.getName(), samplingLevel.getShortName());
			this.nodes = nodes;
			setAffected(samplingLevel.getAffected(), samplingLevel.getNotAffected(), false);
		}

		@Override
		public Class<? extends SimpleValuedNode<Range<Double>>> getType() {
			return TYPE;
		}

		@Override
		public List<? extends SimpleValuedNode<Range<Double>>> getNodes() {
			return nodes;
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			return nodes.contains(node);
		}
		
		public SimpleValuedNode<Range<Double>> getBin(ValuedLogicTreeNode<Double> node) {
			return getBin(node.getValue());
		}
		
		public SimpleValuedNode<Range<Double>> getBin(Double value) {
			for (SimpleValuedNode<Range<Double>> bin : nodes)
				if (bin.getValue().contains(value))
					return bin;
			return null;
		}

		@Override
		public Class<? extends Range<Double>> getValueType() {
			return VALUE_TYPE;
		}

		@Override
		public TypeAdapter<Range<Double>> getValueTypeAdapter() {
			return new DoubleRangeAdapter();
		}

		@Override
		public SimpleValuedNode<Range<Double>> build(Range<Double> value, double weight, String name, String shortName,
				String filePrefix) {
			return new SimpleValuedNode<Range<Double>>(value, VALUE_TYPE, weight, name, shortName, filePrefix);
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			
			JsonArray binsArray = new JsonArray();
			
			DoubleRangeAdapter rangeAdapter = new DoubleRangeAdapter();
			
			for (SimpleValuedNode<Range<Double>> node : nodes) {
				JsonObject binObj = new JsonObject();

				binObj.add("range", rangeAdapter.toJsonTree(node.getValue()));
				binObj.add("name", new JsonPrimitive(node.getName()));
				binObj.add("shortName", new JsonPrimitive(node.getShortName()));
				binObj.add("filePrefix", new JsonPrimitive(node.getFilePrefix()));
				binObj.add("weight", new JsonPrimitive(node.getNodeWeight()));
				
				binsArray.add(binObj);
			}
			
			json.add("bins", binsArray);
			return json;
			
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			JsonArray bins = jsonObj.getAsJsonArray("bins");
			
			DoubleRangeAdapter rangeAdapter = new DoubleRangeAdapter();
			
			nodes = new ArrayList<>(bins.size());
			for (int i=0; i<bins.size(); i++) {
				JsonObject binObj = bins.get(i).getAsJsonObject();
				Range<Double> range = rangeAdapter.fromJsonTree(binObj.get("range"));
				String name = binObj.get("name").getAsString();
				String shortName = binObj.get("shortName").getAsString();
				String filePrefix = binObj.get("filePrefix").getAsString();
				double weight = binObj.get("weight").getAsDouble();
				nodes.add(build(range, weight, name, shortName, filePrefix));
			}
		}
		
	}
	
	public static class ContinuousDistributionSampledLevel
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
	
	public static abstract class CombinedSamplingNode<E extends FixedWeightNode> extends SimpleValuedNode<int[]> {
		
		private E node;

		protected CombinedSamplingNode() {
			super();
		}

		public CombinedSamplingNode(int[] indexes, E value, Class<? extends int[]> valueClass, double weight, String name,
				String shortName, String filePrefix) {
			super(indexes, valueClass, weight, name, shortName, filePrefix);
			setNodeValue(value);
		}

		protected void setNodeValue(E node) {
			this.node = node;
		}
		
		protected E getNodeValue() {
			Preconditions.checkNotNull(node, "Not value not yet set? indexes=%s", getValue());
			return node;
		}
	}
	
	public static abstract class AbstractCombinedSamplingLevel<E extends FixedWeightNode, N extends CombinedSamplingNode<E>>
	extends AbstractRandomlySampledLevel<int[],N> implements BinnableLevel<int[], N, CombinedSampledBinnedLevel<E,N>> {

		private WeightedList<? extends LogicTreeLevel<? extends E>> levels;
		
		protected AbstractCombinedSamplingLevel(String levelName, String levelShortName) {
			super(levelName, levelShortName);
		}
		
		protected AbstractCombinedSamplingLevel(String levelName, String levelShortName, String nodeFilePrefix) {
			super(levelName, levelShortName, null, null, nodeFilePrefix);
		}

		public AbstractCombinedSamplingLevel(String levelName, String levelShortName, String nodeFilePrefix,
				WeightedList<? extends LogicTreeLevel<? extends E>> levels) {
			super(levelName, levelShortName, null, null, nodeFilePrefix);
			init(levels);
		}
		
		protected void init(WeightedList<? extends LogicTreeLevel<? extends E>> levels) {
			Preconditions.checkState(levels.size() > 1);
			if (!(levels instanceof WeightedList.Unmodifiable<?>))
				levels = new WeightedList.Unmodifiable<>(levels);
			this.levels = levels;
			LinkedHashSet<String> affected = new LinkedHashSet<>();
			LinkedHashSet<String> notAffected = new LinkedHashSet<>();
			for (int l=0; l<levels.size(); l++) {
				LogicTreeLevel<? extends E> level = levels.getValue(l);
				for (String a : level.getAffected()) {
					if (notAffected.contains(a))
						notAffected.remove(a);
					affected.add(a);
				}
				for (String n : level.getNotAffected()) {
					if (!affected.contains(n))
						notAffected.add(n);
				}
			}
			setAffected(affected, notAffected, false);
		}
		
		public WeightedList<? extends LogicTreeLevel<? extends E>> getSubLevels() {
			return levels;
		}
		
		protected abstract N buildCombinedNode(int[] indexes, E value, double weight, String name, String shortName, String filePrefix);

		@Override
		public N build(int[] indexes, double weight, String name, String shortName, String filePrefix) {
			LogicTreeLevel<? extends E> level = levels.getValue(indexes[0]);
			E node = level.getNodes().get(indexes[1]);
			// use the downstream node names, but keep the custom file prefix
			name = node.getShortName();
			shortName = node.getShortName();
			N combNode = buildCombinedNode(indexes, node, weight, name, shortName, filePrefix);
			Preconditions.checkNotNull(combNode);
			return combNode;
		}

		@Override
		protected void doBuild(long seed, int numNodes, SamplingMethod samplingMethod, double weightEach) {
			Random rand = new Random(seed);
			
			long[] levelSeeds = new long[levels.size()];
			for (int l=0; l<levels.size(); l++)
				levelSeeds[l] = rand.nextLong();
			
			// first randomly assign sub-levels to indexes
			WeightedList<Integer> levelIndexesList = new WeightedList<>(levels.size());
			for (int l=0; l<levels.size(); l++)
				levelIndexesList.add(l, levels.getWeight(l));
			
			List<Integer> nodeLevelIndexesList;
			if (samplingMethod.isLHS())
				nodeLevelIndexesList = levelIndexesList.sampleEvenly(numNodes, rand);
			else
				nodeLevelIndexesList = levelIndexesList.sampleMonteCarlo(numNodes, rand);
			
			// figure out how many were sampled for each sub-level
			List<List<Integer>> levelSampleIndexes = new ArrayList<>(levels.size());
			for (int l=0; l<levels.size(); l++)
				levelSampleIndexes.add(new ArrayList<>());
			for (int i=0; i<numNodes; i++) {
				int levelIndex = nodeLevelIndexesList.get(i);
				levelSampleIndexes.get(levelIndex).add(i);
			}
			
			// now actually build them
			List<int[]> nodeLevelIndexes = new ArrayList<>(numNodes);
			for (int i=0; i<numNodes; i++)
				nodeLevelIndexes.add(null);
			
			for (int l=0; l<levels.size(); l++) {
				List<Integer> indexes = levelSampleIndexes.get(l);
				int numLevelSamples = indexes.size();
				if (numLevelSamples == 0)
					// this level was never sampled
					continue;
				LogicTreeLevel<? extends E> level = levels.getValue(l);
//				System.out.println("Level "+l+". "+level.getShortName()+" has "+numLevelSamples+" samples");
				if (level instanceof RandomLevel) {
					RandomLevel<E, ?> randomLevel = (RandomLevel<E, ?>)level;
					randomLevel.build(levelSeeds[l], numLevelSamples, samplingMethod, weightEach);
					
					for (int i=0; i<numLevelSamples; i++) {
						int nodeIndex = indexes.get(i);
						int[] nodeLevelIndex = {l,i};
						Preconditions.checkState(nodeLevelIndexes.set(nodeIndex, nodeLevelIndex) == null);
					}
				} else if (level.getNodes().size() == 1) {
					// single value
					for (int i=0; i<numLevelSamples; i++) {
						int nodeIndex = indexes.get(i);
						int[] nodeLevelIndex = {l,0};
						Preconditions.checkState(nodeLevelIndexes.set(nodeIndex, nodeLevelIndex) == null);
					}
				} else {
					// sample values from the level
					List<? extends E> levelNodes = level.getNodes();
					WeightedList<Integer> weightedNodes = new WeightedList<>();
					for (int i=0; i<levelNodes.size(); i++) {
						E node = levelNodes.get(i);
						double nodeWeight = node.getNodeWeight();
						if (nodeWeight > 0d)
							weightedNodes.add(i, nodeWeight);
					}
					Random levelRand = new Random(levelSeeds[l]);
					List<Integer> levelSampledIndexes;
					if (samplingMethod.isLHS())
						levelSampledIndexes = weightedNodes.sampleEvenly(numLevelSamples, levelRand);
					else
						levelSampledIndexes = weightedNodes.sampleMonteCarlo(numLevelSamples, levelRand);
					for (int i=0; i<numLevelSamples; i++) {
						int nodeIndex = indexes.get(i);
						int levelNodeIndex = levelSampledIndexes.get(i);
						int[] nodeLevelIndex = {l,levelNodeIndex};
						Preconditions.checkState(nodeLevelIndexes.set(nodeIndex, nodeLevelIndex) == null);
					}
				}
			}
			
			for (int i=0; i<numNodes; i++)
				Preconditions.checkNotNull(nodeLevelIndexes.get(i));
			
			setValues(nodeLevelIndexes, weightEach);
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = super.toJsonObject();
			
			Preconditions.checkNotNull(json);
			
			JsonArray levelsArray = new JsonArray();
			Adapter<E> levelAdapter = new Adapter<>();
			for (int l=0; l<levels.size(); l++) {
				double weight = levels.getWeight(l);
				LogicTreeLevel<? extends E> level = levels.getValue(l);
				JsonObject levelObj = new JsonObject();
				levelObj.add("weight", new JsonPrimitive(weight));
				levelObj.add("level", levelAdapter.toJsonTree(level));
				levelsArray.add(levelObj);
			}
			json.add("levels", levelsArray);
			
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			super.initFromJsonObject(jsonObj);
			
			JsonArray levelsArray = jsonObj.getAsJsonArray("levels");
			WeightedList<LogicTreeLevel<? extends E>> levels = new WeightedList<>(levelsArray.size());
			Adapter<E> levelAdapter = new Adapter<>();
			for (int l=0; l<levelsArray.size(); l++) {
				JsonObject levelObj = levelsArray.get(l).getAsJsonObject();
				double weight = levelObj.get("weight").getAsDouble();
				LogicTreeLevel<? extends E> level = levelAdapter.fromJsonTree(levelObj.get("level"));
				levels.add(level, weight);
			}
			this.levels = new WeightedList.Unmodifiable<>(levels);
		}

		@Override
		protected void setNodes(List<N> nodes) {
			// now set the values on each node
			for (N node : nodes) {
				int[] indexes = node.getValue();
				node.setNodeValue(levels.getValue(indexes[0]).getNodes().get(indexes[1]));
			}
			
			super.setNodes(nodes);
		}

		@Override
		public CombinedSampledBinnedLevel<E, N> toBinnedLevel() {
			return new CombinedSampledBinnedLevel<>(this);
		}

		@Override
		public CombinedSampledBinnedLevel<E, N> toBinnedLevel(int numBins) {
			return toBinnedLevel();
		}
		
	}
	
	public static class CombinedSampledBinnedLevel<E extends FixedWeightNode, N extends CombinedSamplingNode<E>>
	extends DataBackedLevel<SimpleValuedNode<int[]>>
	implements ValueBackedLevel<int[], SimpleValuedNode<int[]>>, BinnedLevel<int[], SimpleValuedNode<int[]>> {
		
		// this extra cast to Class<?> resolves compile errors that don't show up in eclipse, which is annoying
		private static Class<? extends SimpleValuedNode<int[]>> TYPE =
				(Class<SimpleValuedNode<int[]>>) (Class<?>) SimpleValuedNode.class;

		private AbstractCombinedSamplingLevel<E, N> samplingLevel;
		private WeightedList<? extends LogicTreeLevel<? extends E>> levels;
		private List<BinnedLevel<?,?>> binnedLevels;
		private List<List<? extends FixedWeightNode>> binnedLevelNodes;
		private List<List<SimpleValuedNode<int[]>>> nodesByLevel;
		private List<SimpleValuedNode<int[]>> nodes;
		
		@SuppressWarnings("unused") // deserialization
		private CombinedSampledBinnedLevel() {};
		
		public CombinedSampledBinnedLevel(
				AbstractCombinedSamplingLevel<E, N> samplingLevel) {
			super(samplingLevel.getName(), samplingLevel.getShortName());
			this.samplingLevel = samplingLevel;
			levels = samplingLevel.getSubLevels();
			nodes = new ArrayList<>();
			nodesByLevel = new ArrayList<>(levels.size());
			binnedLevels = new ArrayList<>(levels.size());
			binnedLevelNodes = new ArrayList<>(levels.size());
			
			double[] sampledLevelWeights = new double[levels.size()];
			List<? extends N> allNodes = samplingLevel.getNodes();
			for (N node : allNodes)
				sampledLevelWeights[node.getValue()[0]] += node.getNodeWeight();
//			System.out.println("Binning for "+levels.size()+" levels and "+allNodes.size()+" nodes");
//			System.out.println("Nodes:");
//			for (int i=0; i<allNodes.size(); i++) {
//				N node = allNodes.get(i);
//				int[] indexes = node.getValue();
//				System.out.println(i+". "+indexes[0]+"-"+indexes[1]+": "+node.getShortName()+" "+(float)node.getNodeWeight());
//			}
			for (int l=0; l<levels.size(); l++) {
				List<SimpleValuedNode<int[]>> levelNodes;
				double weight = sampledLevelWeights[l];
				LogicTreeLevel<? extends E> level = levels.getValue(l);
//				System.out.println("Binning sub-level "+l+". "+level+" with weight="+weight);
				if (level instanceof BinnableLevel) {
					// sub-level is binned, use those
					BinnedLevel<?,?> binned = ((BinnableLevel<?, ?, ?>)level).toBinnedLevel();
					LogicTreeLevel<? extends FixedWeightNode> binnedLevel = (LogicTreeLevel<? extends FixedWeightNode>)binned;
					List<? extends FixedWeightNode> subBins = binnedLevel.getNodes();
					levelNodes = new ArrayList<>(subBins.size());
					double sumBinWeights = 0d;
					for (FixedWeightNode bin : subBins)
						sumBinWeights += bin.getNodeWeight();
					for (int i=0; i<subBins.size(); i++) {
						int[] indexes = {l, i};
						FixedWeightNode subBin = subBins.get(i);
						double binWeight = weight * subBin.getNodeWeight() / sumBinWeights;
//						System.out.println("\tSummed sub-bin weights for "+subBin.getName()+": "+binWeight+"; this="+this.toString());
						levelNodes.add(build(indexes, binWeight, subBin.getName(), subBin.getShortName(), "Level"+l+"-"+subBin.getFilePrefix()));
					}
					binnedLevels.add(binned);
					binnedLevelNodes.add(subBins);
				} else if (level instanceof RandomLevel || level.getNodes().size() > 3) {
					// single bin for this level
					int[] indexes = {l, 0};
					levelNodes = List.of(build(indexes, weight, level.getName(), level.getShortName(), "Level"+l));
					binnedLevels.add(null);
					binnedLevelNodes.add(null);
				} else {
					// bin for each value
					List<? extends E> subNodes = level.getNodes();
					levelNodes = new ArrayList<>(subNodes.size());
					double[] levelValueWeights = new double[subNodes.size()];
					for (N node : samplingLevel.getNodes()) {
						int[] nodeIndexes = node.getValue();
						if (nodeIndexes[0] == l)
							levelValueWeights[nodeIndexes[1]] += node.getNodeWeight();
					}
					for (int i=0; i<subNodes.size(); i++) {
						int[] indexes = {l, i};
						E node = subNodes.get(i);
//						System.out.println("\tSummed weights for "+node.getName()+": "+levelValueWeights[i]+"; this="+this.toString());
						levelNodes.add(build(indexes, levelValueWeights[i], node.getName(), node.getShortName(), "Level"+l+"-"+node.getFilePrefix()));
					}
					binnedLevels.add(null);
					binnedLevelNodes.add(null);
				}
				nodesByLevel.add(levelNodes);
				nodes.addAll(levelNodes);
			}
			setAffected(samplingLevel.getAffected(), samplingLevel.getNotAffected(), false);
		}

		@Override
		public SimpleValuedNode<int[]> getBin(int[] value) {
			Preconditions.checkState(levels != null, "Cannot convert values to bins after deserialization");
			int levelIndex = value[0];
			int nodeIndex = value[1];
			LogicTreeLevel<? extends E> level = samplingLevel.getSubLevels().getValue(levelIndex);
			List<? extends E> levelNodes = level.getNodes();
			E node = levelNodes.get(nodeIndex);
			BinnedLevel<?, ?> binnedLevel = binnedLevels.get(levelIndex);
			List<SimpleValuedNode<int[]>> levelBins = nodesByLevel.get(levelIndex);
			if (binnedLevel == null) {
				if (levelBins.size() == 1) {
					// single bin for this level
					return levelBins.get(0);
				} else {
					Preconditions.checkState(levelBins.size() == levelNodes.size());
					return levelBins.get(nodeIndex);
				}
			}
			LogicTreeNode origBin = binnedLevel.getBinUnchecked(node);
			List<? extends FixedWeightNode> origLevelBins = binnedLevelNodes.get(levelIndex);
			int binIndex = origLevelBins.indexOf(origBin);
			Preconditions.checkState(binIndex >= 0,
					"Bin %s not found for value %s in level %s. %s\n\tBins: %s", origBin, value, levelIndex, level, origLevelBins);
			SimpleValuedNode<int[]> bin = levelBins.get(binIndex);
			Preconditions.checkState(bin.getShortName().equals(origBin.getShortName()));
			return bin;
		}

		@Override
		public Class<? extends int[]> getValueType() {
			return int[].class;
		}

		@Override
		public SimpleValuedNode<int[]> build(int[] value, double weight, String name, String shortName,
				String filePrefix) {
			return new SimpleValuedNode<int[]>(value, getValueType(), weight, name, shortName, filePrefix);
		}

		@Override
		public Class<? extends SimpleValuedNode<int[]>> getType() {
			return TYPE;
		}

		@Override
		public List<? extends SimpleValuedNode<int[]>> getNodes() {
			return nodes;
		}

		@Override
		public boolean isMember(LogicTreeNode node) {
			if (!(node instanceof SimpleValuedNode))
				return false;
			Object value = ((SimpleValuedNode<?>)node).getValue();
			if (!(value instanceof int[]))
				return false;
			return nodes.contains(node);
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			
			JsonArray levelsArray = new JsonArray(nodesByLevel.size());
			for (int l=0; l<nodesByLevel.size(); l++) {
				JsonArray binsArray = new JsonArray();
				
				for (SimpleValuedNode<int[]> node : nodesByLevel.get(l)) {
					JsonObject binObj = new JsonObject();

					binObj.add("name", new JsonPrimitive(node.getName()));
					binObj.add("shortName", new JsonPrimitive(node.getShortName()));
					binObj.add("filePrefix", new JsonPrimitive(node.getFilePrefix()));
					binObj.add("weight", new JsonPrimitive(node.getNodeWeight()));
					
					binsArray.add(binObj);
				}
				
				levelsArray.add(binsArray);
			}
			json.add("nodes", levelsArray);
			
			return json;
			
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			JsonArray levels = jsonObj.getAsJsonArray("nodes");
			
			nodes = new ArrayList<>();
			nodesByLevel = new ArrayList<>(levels.size());
			for (int l=0; l<levels.size(); l++) {
				JsonArray binsArray = levels.get(l).getAsJsonArray();
				
				List<SimpleValuedNode<int[]>> levelNodes = new ArrayList<>(binsArray.size());
				
				for (int i=0; i<binsArray.size(); i++) {
					JsonObject binObj = binsArray.get(i).getAsJsonObject();
					String name = binObj.get("name").getAsString();
					String shortName = binObj.get("shortName").getAsString();
					String filePrefix = binObj.get("filePrefix").getAsString();
					double weight = binObj.get("weight").getAsDouble();
//					System.out.println("Deserializing bin "+name+" with weight="+weight+"; this="+this.toString());
					levelNodes.add(build(new int[] {l,i}, weight, name, shortName, filePrefix));
				}
				
				nodesByLevel.add(levelNodes);
				nodes.addAll(levelNodes);
			}
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
		protected void doBuild(long seed, int num, SamplingMethod samplingMethod, double weightEach) {
			// sampling method currently ignored for randomly-generated (always Monte Carlo)
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
//			System.out.println("Writing level "+level.getName()+" of type "+level.getClass());
			if (level instanceof DataBackedLevel<?>) {
				// don't need to write node data
				// (there's no method to set them were we to deserialize them anyway)
				
				if (level instanceof IndexedValuedLevel) {
					// do need to write values/weights
					out.name("values").beginObject();
					boolean allSameWeight = true;
					List<? extends E> nodes = level.getNodes();
					Preconditions.checkNotNull(nodes);
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
						// write the values themselves
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
				}
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

}
