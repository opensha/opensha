package org.opensha.sha.earthquake.faultSysSolution.ruptures;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.commons.util.ComparablePairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * This class represents a cluster of contiguous subsections. It can either be a full parent
 * fault section, or a smaller subset of a parent section
 * @author kevin
 *
 */
public class FaultSubsectionCluster implements Comparable<FaultSubsectionCluster> {

	/*
	 * Public final fields
	 */
	/**
	 * Parent fault section ID
	 */
	public final int parentSectionID;
	/**
	 * Parent section name
	 */
	public final String parentSectionName;
	/**
	 * Immutable list of subsections that constitute this cluster. For simple, unliateral ruptures, they must be in
	 * order from the start of the rupture to the end. For bilateral ruptures, they should be in order from the shorter
	 * end to the longer end, i.e., the startSect should be closer to the start of this list than the end. Order can be
	 * more flexible for faults with multiple subsections down-dip, but ensure that any plausibility filters can handle
	 * this.
	 */
	public final ImmutableList<FaultSection> subSects;
	/**
	 * The starting section (entry point to) this cluster. This is typically the first section in the subSects list,
	 * but need not always be if a rupture jumps to this cluster and spreads bilaterally.
	 */
	public final FaultSection startSect;
	/**
	 * The end point(s) of this cluster, from which a jump can happen without it being considered
	 * a splay jump. For crustal faults this will typically be only the last section, but for faults
	 * with down-dip subsections this could be an entire edge on the far and/or upper/lower ends of the
	 * cluster
	 */
	public final ImmutableSet<FaultSection> endSects;
	/**
	 * Set of section IDs contained in this cluster
	 */
	public final UniqueRupture unique;
	/**
	 * Range of {@link FaultSection#getSubSectionIndexAlong()} indexes included in this cluster, or null if all
	 * are <0
	 */
	public final Range<Integer> indexAlongRange;
	/**
	 * Range of {@link FaultSection#getSubSectionIndexDownDip()} indexes included in this cluster, or null if all
	 * are <0
	 */
	public final Range<Integer> indexDownDipRange;
	/**
	 * Organized view of the subsections in this cluster according to their internal indexing order (which can differ
	 * from their order in {@link #subSects}). The outer list contains an entry for each row down-dip (in order of
	 * increasing {@link FaultSection#getSubSectionIndexDownDip()})), and each inner list contains the subsections in
	 * that row (in order of increasing {@link FaultSection#getSubSectionIndexAlong()}).
	 * <p>
	 * The list will contain a single row if the passed in subsections don't contain down dip indexes (i.e., all 
	 * {@link FaultSection#getSubSectionIndexDownDip()} < 0 and thus {@link #indexDownDipRange} is null), or each such
	 * index is identical.
	 * <p> If down-dip indexes are supplied, this list will contain <code>numRows = 1 + maxIndexDD - minIndexDD</code>
	 * rows. The top (at index 0) and bottom (last index) rows will contain the subsections with the smallest and
	 * largest encountered {@link FaultSection#getSubSectionIndexDownDip()}, respectively. Intermediate row lists can be
	 * empty if a down-dip index is skipped.
	 * <p>
	 * Within each row, subsections will be sorted according to {@link FaultSection#getSubSectionIndexAlong()}, reverting
	 * to {@link FaultSection#getSubSectionIndex()} and finally {@link FaultSection#getSectionId()} if those indexes
	 * are not supplied.
	 */
	public final ImmutableList<ImmutableList<FaultSection>> organized;
	
	/*
	 * Internal private fields
	 */
	
	/**
	 * Internal (mutable) list of allowed jumps. Set via constructor, or via addConnection(Jump) method
	 */
	private final List<Jump> possibleJumps;
	
	/**
	 * Constructor for a simple unliateral cluster that begins at the first section and ends at the last section.
	 * 
	 * @param subSects
	 */
	public FaultSubsectionCluster(List<? extends FaultSection> subSects) {
		this(subSects, null);
	}
	
	/**
	 * Constructor for a more complicated cluster that has multiple end points (from where jumps can occur),
	 * but starts at the first section. USeful for faults with multiple subsections down dip. 
	 * 
	 * @param subSects
	 * @param endSects
	 */
	public FaultSubsectionCluster(List<? extends FaultSection> subSects, Collection<FaultSection> endSects) {
		this(subSects, subSects.get(0), endSects);
	}
	
	/**
	 * Constructor for a more complicated cluster that has multiple end points (from where jumps can occur),
	 * but starts at the first section. Useful for faults with multiple subsections down dip. 
	 * 
	 * @param subSects
	 * @param endSects
	 */
	public FaultSubsectionCluster(List<? extends FaultSection> subSects, FaultSection startSect,
			Collection<FaultSection> endSects) {
		this(subSects, startSect, endSects, null);
	}
	
	/**
	 * Constructor for a more complicated cluster that has multiple end points (from where jumps can occur),
	 * but starts at the first section. Useful for faults with multiple subsections down dip. 
	 * 
	 * @param subSects
	 * @param endSects
	 */
	public FaultSubsectionCluster(List<? extends FaultSection> subSects, FaultSection startSect,
			Collection<FaultSection> endSects, ImmutableList<ImmutableList<FaultSection>> organized) {
		Preconditions.checkArgument(!subSects.isEmpty(), "Must supply at least 1 subsection");
		this.subSects = ImmutableList.copyOf(subSects);
		this.startSect = startSect;
		unique = UniqueRupture.forSects(subSects);
		int parentSectionID = -1;
		String parentSectionName = null;
		int minRow = Integer.MAX_VALUE;
		int maxRow = -1;
		int minCol = Integer.MAX_VALUE;
		int maxCol = -1;
		for (FaultSection subSect : subSects) {
			minRow = Integer.min(minRow, subSect.getSubSectionIndexDownDip());
			maxRow = Integer.max(maxRow, subSect.getSubSectionIndexDownDip());
			minCol = Integer.min(minCol, subSect.getSubSectionIndexAlong());
			maxCol = Integer.max(maxCol, subSect.getSubSectionIndexAlong());
			Preconditions.checkNotNull(subSect);
			if (parentSectionID < 0) {
				parentSectionID = subSect.getParentSectionId();
				parentSectionName = subSect.getParentSectionName();
			} else {
				Preconditions.checkState(subSect.getParentSectionId() == parentSectionID,
						"Cluster subSects list has sedctios with multiple parentSectionIDs");
			}
		}
		this.parentSectionID = parentSectionID;
		this.parentSectionName = parentSectionName;
		this.possibleJumps = new ArrayList<>();
		if (maxRow >= 0) {
			Preconditions.checkState(minRow >= 0,
					"Some sections have indexes down dip (max=%s) but others have negative values, min=%s", maxRow, minRow);
			indexDownDipRange = Range.closed(minRow, maxRow);
		} else {
			indexDownDipRange = null;
		}
		if (maxCol >= 0) {
			Preconditions.checkState(maxCol >= 0,
					"Some sections have indexes along strike (max=%s) but others have negative values, min=%s", maxCol, minCol);
			indexAlongRange = Range.closed(minCol, maxCol);
		} else {
			indexAlongRange = null;
		}
		
		if (organized != null) {
			// passed in from an internal operation
			this.organized = organized;
		} else if (indexDownDipRange != null) {
			// we have down-dip indexes
			int numRows = 1 + maxRow - minRow;
			ImmutableList.Builder<ImmutableList<FaultSection>> rowColBuilder = ImmutableList.builderWithExpectedSize(numRows);
			
			if (indexAlongRange != null) {
				// we have indexes along, clean
				int numCols = 1 + maxCol - minCol;
				FaultSection[][] rowColArray = new FaultSection[numRows][numCols];
				for (FaultSection subSect : subSects) {
					int row = subSect.getSubSectionIndexDownDip();
					int col = subSect.getSubSectionIndexAlong();
					Preconditions.checkState(row >= 0, "Bad row index=%s for down-dip cluster: %s", row, subSect);
					Preconditions.checkState(col >= 0, "Bad col index=%s for down-dip cluster: %s", col, subSect);
					int rowIndex = row-minRow;
					int colIndex = col-minCol;
					Preconditions.checkState(rowColArray[rowIndex][colIndex] == null,
							"Duplicate section found at row=%s, col=%s: %s", row, col, subSect);
					rowColArray[rowIndex][colIndex] = subSect;
				}
				for (int row=0; row<numRows; row++) {
					int numNonZero = 0;
					for (FaultSection sect : rowColArray[row])
						if (sect != null)
							numNonZero++;
					if (numNonZero == 0) {
						rowColBuilder.add(ImmutableList.of());
					} else {
						ImmutableList.Builder<FaultSection> rowBuilder = ImmutableList.builderWithExpectedSize(numNonZero);
						for (FaultSection sect : rowColArray[row])
							if (sect != null)
								rowBuilder.add(sect);
						rowColBuilder.add(rowBuilder.build());
					}
				}
			} else {
				// we're inferring indexes along, dirtier
				List<List<FaultSection>> rowColLists = new ArrayList<>(numRows);
				for (int i=0; i<numRows; i++)
					rowColLists.add(null);
				for (FaultSection subSect : subSects) {
					int row = subSect.getSubSectionIndexDownDip();
					Preconditions.checkState(row >= 0, "Bad row index=%s for down-dip cluster: %s", row, subSect);
					int rowIndex = row-minRow;
					rowColLists.get(rowIndex).add(subSect);
				}
				for (List<FaultSection> rowSects : rowColLists) {
					if (rowSects.isEmpty())
						rowColBuilder.add(ImmutableList.of());
					else
						rowColBuilder.add(ImmutableList.sortedCopyOf(indexAlongComparator, rowSects));
				}
			}
			this.organized = rowColBuilder.build();
		} else {
			// no down-dip indexes
			this.organized = ImmutableList.of(ImmutableList.sortedCopyOf(indexAlongComparator, subSects));
		}
		
		if (endSects == null) {
			if (this.organized.size() == 1 || subSects.size() == 1) {
				// default behavior: last section
				this.endSects = ImmutableSet.of(subSects.get(subSects.size()-1));
			} else {
				// default behavior: first and last columns in each row
				ImmutableSet.Builder<FaultSection> endSectsBuilder = ImmutableSet.builder();
				for (List<FaultSection> row : this.organized) {
					if (!row.isEmpty()) {
						FaultSection first = row.get(0);
						FaultSection last = row.get(row.size()-1);
						if (first != startSect)
							endSectsBuilder.add(first);
						if (first != last && last != startSect)
							endSectsBuilder.add(last);
					}
				}
				this.endSects = endSectsBuilder.build();
			}
		} else {
			ImmutableSet.Builder<FaultSection> endBuilder = ImmutableSet.builderWithExpectedSize(endSects.size());
			endBuilder.addAll(endSects);
			this.endSects = endBuilder.build();
		}
	}
	
	private static final Comparator<FaultSection> indexAlongComparator = (s1, s2) -> {
		int cmp = Integer.compare(s1.getSubSectionIndexAlong(), s2.getSubSectionIndexAlong());
		if (cmp != 0)
			return cmp;
		cmp = Integer.compare(s1.getSubSectionIndex(), s2.getSubSectionIndex());
		if (cmp != 0)
			return cmp;
		return Integer.compare(s1.getSectionId(), s2.getSectionId());
	};
	
	public void addConnection(Jump jump) {
		Preconditions.checkState(jump.fromCluster == this);
		Preconditions.checkState(jump.fromSection.getParentSectionId() == parentSectionID);
		Preconditions.checkState(contains(jump.fromSection));
		for (Jump existing : possibleJumps)
			if (existing.toCluster.equals(jump.toCluster) && existing.toSection.equals(jump.toSection)
					&& existing.fromSection.equals(jump.fromSection))
				// this is a duplicate, skip adding
				return;
		possibleJumps.add(jump);
	}
	
	public boolean contains(FaultSection sect) {
		return unique.contains(sect.getSectionId());
	}
	
	public Set<FaultSection> getExitPoints() {
		HashSet<FaultSection> exitPoints = new HashSet<>();
		for (Jump jump : possibleJumps)
			exitPoints.add(jump.fromSection);
		return exitPoints;
	}
	
	public Set<FaultSubsectionCluster> getConnectedClusters() {
		HashSet<FaultSubsectionCluster> set = new HashSet<>();
		for (Jump jump : possibleJumps)
			set.add(jump.toCluster);
		return set;
	}
	
	public List<FaultSubsectionCluster> getDistSortedConnectedClusters() {
		Map<FaultSubsectionCluster, Double> distMap = new HashMap<>();
		for (Jump jump : possibleJumps) {
			Double prevDist = distMap.get(jump.toCluster);
			if (prevDist == null)
				distMap.put(jump.toCluster, jump.distance);
			else if (jump.distance < prevDist)
				distMap.put(jump.toCluster, jump.distance);
		}
		return ComparablePairing.getSortedData(distMap);
	}
	
	public Collection<Jump> getConnectionsTo(FaultSubsectionCluster cluster) {
		List<Jump> jumps = new ArrayList<>();
		for (Jump jump : possibleJumps)
			if (jump.toCluster.equals(cluster))
				jumps.add(jump);
		return jumps;
	}
	
	public List<Jump> getConnections() {
		return Collections.unmodifiableList(possibleJumps);
//		return ImmutableList.copyOf(possibleJumps);
	}
	
	public Collection<Jump> getConnections(FaultSection exitPoint) {
		Preconditions.checkState(contains(exitPoint), "Given section is not part of this cluster");
		List<Jump> jumps = new ArrayList<>();
		for (Jump jump : possibleJumps)
			if (jump.fromSection.equals(exitPoint))
				jumps.add(jump);
		return jumps;
	}
	@Override
	public String toString() {
		return toString(false);
	}
	
	public String toString(boolean verbose) {
		int jumpToID = -1;
		if (startSect != subSects.get(0))
			jumpToID = startSect.getSectionId();
		return toString(verbose, jumpToID);
	}
	
	public String toString(boolean verbose, int jumpToID) {
		StringBuilder str = null;
		for (FaultSection sect : subSects) {
			if (str == null) {
				if (verbose && parentSectionName != null && !parentSectionName.isBlank())
					str = new StringBuilder("[").append(parentSectionID).append(". ").append(parentSectionName).append(": ");
				else
					str = new StringBuilder("[").append(parentSectionID).append(":");
			} else {
				str.append(",");
			}
			if (sect.getSectionId() == jumpToID)
				str.append("->");
			str.append(sect.getSectionId());
		}
		return str.append("]").toString();
	}
	
	public FaultSubsectionCluster reversed() {
		return reversed(null);
	}
	
	public FaultSubsectionCluster reversed(FaultSection newStartSect) {
		List<FaultSection> sects = new ArrayList<>(subSects);
		Collections.reverse(sects);
		if (newStartSect == null)
			newStartSect = sects.get(0);
		FaultSubsectionCluster reversed = new FaultSubsectionCluster(sects, newStartSect, null, organized);
		for (Jump jump : possibleJumps)
			reversed.addConnection(new Jump(jump.fromSection, reversed,
					jump.toSection, jump.toCluster, jump.distance));
		return reversed;
	}

	@Override
	public int compareTo(FaultSubsectionCluster o) {
		int cmp = Integer.compare(parentSectionID, o.parentSectionID);
		if (cmp != 0)
			return cmp;
		cmp = Integer.compare(subSects.size(), o.subSects.size());
		if (cmp != 0)
			return cmp;
		for (int i=0; i<subSects.size(); i++) {
			cmp = Integer.compare(subSects.get(i).getSectionId(), o.subSects.get(i).getSectionId());
			if (cmp != 0)
				return cmp;
		}
		return 0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + endSects.hashCode();
		result = prime * result + parentSectionID;
		result = prime * result + startSect.hashCode();
		result = prime * result + subSects.hashCode();
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
		FaultSubsectionCluster other = (FaultSubsectionCluster) obj;
		if (parentSectionID != other.parentSectionID)
			return false;
		if (!startSect.equals(other.startSect))
			return false;
		if (!endSects.equals(other.endSects))
			return false;
		if (!subSects.equals(other.subSects))
			return false;
		return true;
	}
	
	/**
	 * This will write this FaultSubsectionCluster to the given JsonWriter
	 * 
	 * @param out
	 * @throws IOException
	 */
	public void writeJSON(JsonWriter out) throws IOException {
		out.beginObject();
		out.name("parentID").value(parentSectionID);
		if (!startSect.equals(subSects.get(0)))
			out.name("startSectID").value(startSect.getSectionId());
		out.name("subSectIDs").beginArray();
		for (FaultSection sect : subSects)
			out.value(sect.getSectionId());
		out.endArray();
		boolean simpleEndSects = endSects.size() == 1;
		if (simpleEndSects) {
			// now make sure that it is indeed the last section
			simpleEndSects = endSects.iterator().next().equals(subSects.get(subSects.size()-1));
		}
		if (!simpleEndSects) {
			out.name("endSectIDs").beginArray();
			for (FaultSection sect : endSects)
				out.value(sect.getSectionId());
			out.endArray();
		}
		if (possibleJumps != null && !possibleJumps.isEmpty()) {
			out.name("connections").beginArray();
			for (Jump jump : possibleJumps) {
				out.beginObject();
				out.name("fromSectID").value(jump.fromSection.getSectionId());
				out.name("toParentID").value(jump.toSection.getParentSectionId());
				out.name("toSectID").value(jump.toSection.getSectionId());
				out.name("distance").value(jump.distance);
				out.endObject();
			}
			out.endArray();
		}
		out.endObject();
	}
	
	private static FaultSection getSect(List<? extends FaultSection> allSects, int sectID) {
		Preconditions.checkState(sectID >= 0 && sectID < allSects.size(),
				"sectID=%s outside valid range: [0,%s]", sectID, allSects.size()-1);
		FaultSection sect = allSects.get(sectID);
		Preconditions.checkState(sect.getSectionId() == sectID,
				"Subsection indexing mismatch. Section at index %s has sectID=%s",
				sectID, sect.getSectionId());
		return sect;
	}
	
	/**
	 * This will read the next FaultSubsectionCluster from a JsonReader. This gets complicated because
	 * jumps cannot be setup until all clusters have been read, so instead you must supply a map
	 * of FaultSubsectionClusters to a list of JumpStub instances. Once all clusters have been read
	 * from JSON, you can build/add all of the ruptures with the buildJumpsFromStubs(..) method.
	 * 
	 * If prevClustersMap is supplied, then previous instances of the exact same cluster will be reused in
	 * order to conserve memory.
	 * 
	 * @param in
	 * @param allSects
	 * @param jumpStubsMap
	 * @param prevClustersMap
	 * @return
	 * @throws IOException
	 */
	public static FaultSubsectionCluster readJSON(JsonReader in, List<? extends FaultSection> allSects,
			Map<FaultSubsectionCluster, List<JumpStub>> jumpStubsMap,
			Map<FaultSubsectionCluster, FaultSubsectionCluster> prevClustersMap) throws IOException {
		in.beginObject();
		
		Integer parentID = null;
		FaultSection startSect = null;
		List<FaultSection> subSects = null;
		List<FaultSection> endSects = null;
		List<JumpStub> jumps = null;
		
		while (in.hasNext()) {
			switch (in.nextName()) {
			case "parentID":
				parentID = in.nextInt();
				break;
			case "startSectID":
				startSect = getSect(allSects, in.nextInt());
				break;
			case "subSectIDs":
				in.beginArray();
				subSects = new ArrayList<>();
				while (in.hasNext())
					subSects.add(getSect(allSects, in.nextInt()));
				in.endArray();
				break;
			case "endSectIDs":
				in.beginArray();
				endSects = new ArrayList<>();
				while (in.hasNext())
					endSects.add(getSect(allSects, in.nextInt()));
				in.endArray();
				break;
			case "connections":
				in.beginArray();
				jumps = new ArrayList<>();
				while (in.hasNext()) {
					in.beginObject();
					FaultSection fromSect = null;
					FaultSection toSect = null;
					Double distance = null;
					Integer toParentID = null;
					while (in.hasNext()) {
						switch (in.nextName()) {
						case "fromSectID":
							fromSect = getSect(allSects, in.nextInt());
							break;
						case "toSectID":
							toSect = getSect(allSects, in.nextInt());
							break;
						case "toParentID":
							toParentID = in.nextInt();
							break;
						case "distance":
							distance = in.nextDouble();
							break;

						default:
							break;
						}
					}
					Preconditions.checkNotNull(fromSect, "Jump fromSectID not specified");
					Preconditions.checkNotNull(toSect, "Jump toSectID not specified");
					Preconditions.checkNotNull(toParentID, "Jump toParentSectID not specified");
					Preconditions.checkState(toParentID.intValue() == toSect.getParentSectionId(),
							"toParentID=%s but toSect.getParentSectionId()=%s",
							toParentID, toSect.getParentSectionId());
					Preconditions.checkNotNull(distance, "Jump distance not specified");
					jumps.add(new JumpStub(fromSect, toSect, distance));
				
					in.endObject();
				}
				in.endArray();
				break;

			default:
				break;
			}
		}
		Preconditions.checkNotNull(parentID, "Cluster parentID not specified");
		Preconditions.checkNotNull(subSects, "Cluster subSects not specified");
		if (startSect == null)
			startSect = subSects.get(0);
		FaultSubsectionCluster cluster = new FaultSubsectionCluster(subSects, startSect, endSects);
		if (prevClustersMap != null) {
			if (prevClustersMap.containsKey(cluster))
				cluster = prevClustersMap.get(cluster);
			else
				prevClustersMap.put(cluster, cluster);
		}
		if (jumps != null) {
			if (jumpStubsMap == null)
				System.err.println("WARNING: skipping loading all jumps (jumpStubsMap is null)");
			else
				jumpStubsMap.put(cluster, jumps);
		}
		
		in.endObject();
		return cluster;
	}
	
	public static void buildJumpsFromStubs(Collection<FaultSubsectionCluster> clusters,
			Map<FaultSubsectionCluster, List<JumpStub>> jumpStubsMap) {
		if (jumpStubsMap.isEmpty())
			return;
		Map<Integer, List<FaultSubsectionCluster>> parentIDsMap = new HashMap<>();
		for (FaultSubsectionCluster cluster : clusters) {
			List<FaultSubsectionCluster> parentClusters = parentIDsMap.get(cluster.parentSectionID);
			if (parentClusters == null) {
				parentClusters = new ArrayList<>();
				parentIDsMap.put(cluster.parentSectionID, parentClusters);
			}
			parentClusters.add(cluster);
		}
		
		for (FaultSubsectionCluster fromCluster : jumpStubsMap.keySet()) {
			List<JumpStub> jumpStubs = jumpStubsMap.get(fromCluster);
			
			for (JumpStub stub : jumpStubs) {
				int toParent = stub.toSection.getParentSectionId();
				FaultSubsectionCluster toCluster = null;
				for (FaultSubsectionCluster possible : parentIDsMap.get(toParent)) {
					if (possible.contains(stub.toSection)) {
						toCluster = possible;
						break;
					}
				}
				Preconditions.checkNotNull(toCluster,
						"Jump to a non-existent cluster with parentID=%s", toParent);
				Preconditions.checkState(stub.fromSection.getParentSectionId() == fromCluster.parentSectionID,
						"Jump fromSect is from an unexpected parent section");
				Preconditions.checkState(fromCluster.contains(stub.fromSection),
						"Jump fromSect is from an unexpected parent section");
				fromCluster.addConnection(new Jump(stub.fromSection, fromCluster,
						stub.toSection, toCluster, stub.distance));
			}
		}
	}
	
	public static class JumpStub {
		final FaultSection fromSection;
		final FaultSection toSection;
		final double distance;
		public JumpStub(FaultSection fromSection, FaultSection toSection, double distance) {
			super();
			this.fromSection = fromSection;
			this.toSection = toSection;
			this.distance = distance;
		}
	}

}
