package org.opensha.sha.earthquake.faultSysSolution.logicTree.dmSampling;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.data.Named;
import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.util.json.JsonObjectSerializable;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Sampler that extracts values from a {@link ContinuousDistribution} for each fault section. These can either be
 * random (per-fault) or at fixed fractiles for all faults (possibly randomly-drawn for each branch). 
 */
public interface DeformationModelDistSampler extends ShortNamed {
	
	public void init(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects);
	
	public double getValue(FaultSection subSect, ContinuousDistribution dist);
	
	public interface FractileSampler extends DeformationModelDistSampler {
		
		public double getFractile(FaultSection subSect);
		
		public default double getValue(FaultSection subSect, ContinuousDistribution dist) {
			return dist.inverseCumulativeProbability(getFractile(subSect));
		}
	}
	
	public interface FixedSampler extends DeformationModelDistSampler, JsonObjectSerializable {
		
		public double getValue(ContinuousDistribution dist);
		
		public default double getValue(FaultSection subSect, ContinuousDistribution dist) {
			return getValue(dist);
		}
	}
	
	public static class FixedFractileSampler implements FractileSampler, FixedSampler {
		
		private double fractile;
		
		@SuppressWarnings("unused") // deserialization
		private FixedFractileSampler() {}

		public FixedFractileSampler(double fractile) {
			Preconditions.checkState(fractile >= 0d && fractile <= 1d, "Bad P=%s", fractile);
			this.fractile = fractile;
		}
		
		public double getFixedFractile() {
			return fractile;
		}

		@Override
		public double getFractile(FaultSection subSect) {
			return fractile;
		}

		@Override
		public void init(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects) {
			// do nothing
		}

		@Override
		public double getValue(ContinuousDistribution dist) {
			return dist.inverseCumulativeProbability(fractile);
		}

		@Override
		public double getValue(FaultSection subSect, ContinuousDistribution dist) {
			return getValue(dist);
		}

		@Override
		public int hashCode() {
			return Objects.hash(fractile);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			FixedFractileSampler other = (FixedFractileSampler) obj;
			return Double.doubleToLongBits(fractile) == Double.doubleToLongBits(other.fractile);
		}

		@Override
		public String getName() {
			if ((float)fractile == 0f)
				return "Distribution Lower Bound";
			if ((float)fractile == 1f)
				return "Distribution Upper Bound";
			if ((float)fractile == 0.5f)
				return "Distribution Median";
			return "Distribution P="+(float)fractile;
		}

		@Override
		public String getShortName() {
			if ((float)fractile == 0f)
				return "Lower";
			if ((float)fractile == 1f)
				return "Upper";
			if ((float)fractile == 0.5f)
				return "Median";
			return "P="+(float)fractile;
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			json.add("fractile", new JsonPrimitive(fractile));
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			fractile = jsonObj.get("fractile").getAsDouble();
		}
	}
	
	public static enum SectionGroupingType {
		PARENT("Parent-Grouped") {
			@Override
			public SectionGrouping get(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects) {
				return new ParentSectionGrouping(subSects, fullSects);
			}
		},
		FULLY_INDEPENDENT("Fully-Independent") {
			@Override
			public SectionGrouping get(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects) {
				return new FullyIndependentSectionGrouping(subSects.size());
			}
		};
		
		private String label;

		private SectionGroupingType(String label) {
			this.label = label;
		}

		public abstract SectionGrouping get(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects);
		
		public String toString() {
			return label;
		}
	}
	
	public static interface SectionGrouping {
		
		public int getGroupIndex(FaultSection subSect);

		public int getNumGroups();
	}
	
	public static final class FullyIndependentSectionGrouping implements SectionGrouping {
		
		private int numSections;

		public FullyIndependentSectionGrouping(int numSections) {
			this.numSections = numSections;
		}

		@Override
		public int getGroupIndex(FaultSection subSect) {
			return subSect.getSectionId();
		}

		@Override
		public int getNumGroups() {
			return numSections;
		}
		
	}
	
	public static final class ParentSectionGrouping implements SectionGrouping {
		
		private Map<Integer, Integer> parentMappings;

		public ParentSectionGrouping(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects) {
			Map<Integer, Integer> parentMappings =
					fullSects == null ? new HashMap<>() : new HashMap<>(fullSects.size());
			for (FaultSection sect : subSects) {
				int parentID = sect.getParentSectionId();
				Preconditions.checkState(parentID >= 0);
				if (!parentMappings.containsKey(parentID))
					parentMappings.put(parentID, parentMappings.size());
			}
			this.parentMappings = parentMappings;
		}

		@Override
		public int getGroupIndex(FaultSection subSect) {
			Preconditions.checkNotNull(parentMappings);
			return parentMappings.get(subSect.getParentSectionId());
		}

		@Override
		public int getNumGroups() {
			Preconditions.checkNotNull(parentMappings);
			return parentMappings.size();
		}
		
	}
	
	public static class GroupedFractileSampler implements FractileSampler, JsonObjectSerializable {
		
		private long seed;
		private SectionGroupingType groupingType;
		
		private transient SectionGrouping grouping;
		private transient double[] groupFractiles;

		public GroupedFractileSampler(long seed, SectionGroupingType groupingType) {
			this.seed = seed;
			this.groupingType = groupingType;
		}

		@Override
		public void init(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects) {
			Random rand = new Random(seed);
			grouping = groupingType.get(subSects, fullSects);
			groupFractiles = new double[grouping.getNumGroups()];
			for (int i=0; i<groupFractiles.length; i++)
				groupFractiles[i] = rand.nextDouble();
		}

		public double getFractile(FaultSection subSect) {
			return groupFractiles[grouping.getGroupIndex(subSect)];
		}

		@Override
		public int hashCode() {
			return Objects.hash(seed, grouping);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			GroupedFractileSampler other = (GroupedFractileSampler) obj;
			return seed == other.seed && Objects.equals(grouping, other.grouping);
		}

		@Override
		public String getShortName() {
			return "GroupedSampler";
		}

		@Override
		public String getName() {
			return groupingType+" Fractile Sampler";
		}

		@Override
		public JsonObject toJsonObject() {
			JsonObject json = new JsonObject();
			json.add("seed", new JsonPrimitive(seed));
			json.add("grouping", new JsonPrimitive(groupingType.name()));
			return json;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
			seed = jsonObj.getAsJsonPrimitive("seed").getAsLong();
			groupingType = SectionGroupingType.valueOf(jsonObj.getAsJsonPrimitive("grouping").getAsString());
		}
	}
	
	public static final class AverageSampler implements FixedSampler {

		public AverageSampler() {
			
		}

		@Override
		public void init(List<? extends FaultSection> subSects, List<? extends FaultSection> fullSects) {
			// do nothing
		}

		@Override
		public double getValue(ContinuousDistribution dist) {
			return dist.getMean();
		}

		@Override
		public int hashCode() {
			return AverageSampler.class.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof AverageSampler;
		}

		@Override
		public String getShortName() {
			return "Average";
		}

		@Override
		public String getName() {
			return "Distribution Average";
		}

		@Override
		public JsonObject toJsonObject() {
			return null;
		}

		@Override
		public void initFromJsonObject(JsonObject jsonObj) {
		}
	}
}