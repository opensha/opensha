package org.opensha.sha.earthquake.faultSysSolution.ruptures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

/**
 * Rupture which is constructed as a set of connected FaultSubsectionCluster's. Jump's occur
 * between each cluster, and multiple splays are possible. Each rupture has a primary
 * strand defined by the clusters listed below in the 'clusters' array. Additional
 * splays off of that primary strand are contained in the splays map. These ruptures are recursive
 * in nature: splays can have splays. The key contract is that sections only exist once in the
 * rupture, and although each section can have multiple descendcents (sections directly downstream
 * of that section), there can only be one predecessor (section directly upstream of that section).
 * 
 * Build ruptures by starting with an initial cluster and calling the public constructor, and then
 * take jumps to each additional cluster via the 'take(Jump)' method, which will return a new rupture
 * each time 'take(Jump)' is called.
 * 
 * All visible fields are final and immutable, and are thus safe for direct access.
 * 
 * @author kevin
 *
 */
public class ClusterRupture {
	
	/**
	 * Clusters for this rupture's primary strand, in order. The first section of this rupture
	 * is clusters[0].startSect
	 */
	public final FaultSubsectionCluster[] clusters;
	/**
	 * Jumps internal to the primary strand, between the primary clusters
	 */
	public final ImmutableSet<Jump> internalJumps;
	/**
	 * Jumps to each splay sub-rupture
	 */
	public final ImmutableMap<Jump, ClusterRupture> splays;
	
	// these are for navigating the rupture section tree
	/**
	 * Multimap from each fault section to each descendant (sections immediately downstream) of 
	 * that section. If jumps occur anywhere before the last subsection in a cluster, then multiple 
	 * descendants are possible for a single parent
	 */
	public final ImmutableMultimap<FaultSection, FaultSection> sectDescendantsMap;
	/**
	 * Map from each fault section to the predecessor (section immediately upstream) of that section
	 */
	public final ImmutableMap<FaultSection, FaultSection> sectPredecessorsMap;
	
	/**
	 * UniqueRupture instance, useful for determining if this rupture has already been built and included
	 * in a rupture set (as defined by the set of subsection IDs included, regardless of order)
	 */
	public final UniqueRupture unique;
	
	private final Set<FaultSection> internalSects;
	
	/**
	 * Initiate a ClusterRupture with the given starting cluster. You can grow it later with the
	 * take(Jump) method
	 * @param cluster
	 */
	public ClusterRupture(FaultSubsectionCluster cluster) {
		this(new FaultSubsectionCluster[] {cluster}, ImmutableSet.of(),
				ImmutableMap.of(), initialDescendentsMap(cluster), initialPredecessorsMap(cluster),
				new HashSet<>(cluster.subSects), new UniqueRupture(cluster));
	}
	
	private static ImmutableMultimap<FaultSection, FaultSection> initialDescendentsMap(
			FaultSubsectionCluster cluster) {
		ImmutableMultimap.Builder<FaultSection, FaultSection> builder = ImmutableMultimap.builder();
		for (int i=0; i<cluster.subSects.size()-1; i++)
			builder.put(cluster.subSects.get(i), cluster.subSects.get(i+1));
		return builder.build();
	}
	
	private static ImmutableMap<FaultSection, FaultSection> initialPredecessorsMap(
			FaultSubsectionCluster cluster) {
		ImmutableMap.Builder<FaultSection, FaultSection> builder = ImmutableMap.builder();
		for (int i=0; i<cluster.subSects.size()-1; i++)
			builder.put(cluster.subSects.get(i+1), cluster.subSects.get(i));
		return builder.build();
	}

	private ClusterRupture(FaultSubsectionCluster[] clusters, ImmutableSet<Jump> internalJumps,
			ImmutableMap<Jump, ClusterRupture> splays,
			ImmutableMultimap<FaultSection, FaultSection> sectDescendentsMap,
			ImmutableMap<FaultSection, FaultSection> sectPredecessorsMap,
			Set<FaultSection> internalSects, UniqueRupture unique) {
		super();
		this.clusters = clusters;
		this.internalJumps = internalJumps;
		this.splays = splays;
		this.sectDescendantsMap = sectDescendentsMap;
		this.sectPredecessorsMap = sectPredecessorsMap;
		this.internalSects = internalSects;
		this.unique = unique;
	}
	
	/**
	 * @param sect
	 * @return true if this rupture or any splays contains the given section
	 */
	public boolean contains(FaultSection sect) {
		if (internalSects.contains(sect))
			return true;
		for (ClusterRupture splay : splays.values())
			if (splay.contains(sect))
				return true;
		return false;
	}
	
	/**
	 * 
	 * @return total number of sections across this and any splays
	 */
	public int getTotalNumSects() {
		int tot = internalSects.size();
		for (ClusterRupture splay : splays.values())
			tot += splay.getTotalNumSects();
		return tot;
	}
	
	/**
	 * 
	 * @return the number of sections on the primary strand of this rupture
	 */
	public int getNumInternalSects() {
		return internalSects.size();
	}
	
	/**
	 * 
	 * @return the total number of jumps (including to and within any splays) of this rupture
	 */
	public int getTotalNumJumps() {
		int tot = internalJumps.size();
		for (ClusterRupture splay : splays.values())
			tot += splay.getTotalNumJumps();
		return tot;
	}
	
	/**
	 * Creates and returns a new rupture which has taken the given jump. The jump can be from any section
	 * on any splay, so long as the toCluster does not contain any sections already included in this rupture
	 * @param jump
	 * @return new rupture which has taken this jump
	 */
	public ClusterRupture take(Jump jump) {
		Preconditions.checkState(contains(jump.fromSection),
				"Cannot take jump because this rupture doesn't have the fromSection: %s", jump);
		Preconditions.checkState(!contains(jump.toSection),
				"Cannot take jump because this rupture already has the toSection: %s", jump);
		ImmutableMap.Builder<FaultSection, FaultSection> predecessorBuilder = ImmutableMap.builder();
		predecessorBuilder.putAll(sectPredecessorsMap);
		predecessorBuilder.put(jump.toSection, jump.fromSection);
		ImmutableMultimap.Builder<FaultSection, FaultSection> descendentsBuilder = ImmutableMultimap.builder();
		descendentsBuilder.putAll(sectDescendantsMap);
		descendentsBuilder.put(jump.fromSection, jump.toSection);
		
		int toIndex = jump.toCluster.subSects.indexOf(jump.toSection);
		Preconditions.checkState(toIndex >= 0, "toSection not found in toCluster subsection list");
		// build in both direction from toIndex
		for (int i=toIndex; i<jump.toCluster.subSects.size()-1; i++) {
			FaultSection sect1 = jump.toCluster.subSects.get(i);
			FaultSection sect2 = jump.toCluster.subSects.get(i+1);
			descendentsBuilder.put(sect1, sect2);
			predecessorBuilder.put(sect2, sect1);
		}
		for (int i=toIndex; --i>=0;) {
			FaultSection sect1 = jump.toCluster.subSects.get(i+1);
			FaultSection sect2 = jump.toCluster.subSects.get(i);
			descendentsBuilder.put(sect1, sect2);
			predecessorBuilder.put(sect2, sect1);
		}
		
		UniqueRupture newUnique = new UniqueRupture(this.unique, jump.toCluster);
		int expectedCount = this.unique.size() + jump.toCluster.subSects.size();
		Preconditions.checkState(newUnique.size() == expectedCount,
				"Duplicate subsections. Have %s unique, %s total", newUnique.size(), expectedCount);
		
		ImmutableMultimap<FaultSection, FaultSection> newDescendentsMap = descendentsBuilder.build();
		ImmutableMap<FaultSection, FaultSection> newPredecessorMap = predecessorBuilder.build();
		if (internalSects.contains(jump.fromSection)) {
			// it's on the main strand
			FaultSubsectionCluster lastCluster = clusters[clusters.length-1];
			FaultSubsectionCluster[] newClusters;
			ImmutableMap<Jump, ClusterRupture> newSplays;
			ImmutableSet<Jump> newInternalJumps;
			HashSet<FaultSection> newInternalSects;
			if (lastCluster.endSects.contains(jump.fromSection)) {
				// regular jump from the end
//				System.out.println("Taking a regular jump to extend a strand");
				newClusters = Arrays.copyOf(clusters, clusters.length+1);
				newClusters[clusters.length] = jump.toCluster;
				newSplays = splays;
				ImmutableSet.Builder<Jump> internalJumpBuild =
						ImmutableSet.builderWithExpectedSize(internalJumps.size()+1);
				internalJumpBuild.addAll(internalJumps);
				internalJumpBuild.add(jump);
				newInternalJumps = internalJumpBuild.build();
				newInternalSects = new HashSet<>(internalSects);
				newInternalSects.addAll(jump.toCluster.subSects);
			} else {
				// it's a new splay
//				System.out.println("it's a new splay!");
				newClusters = clusters;
				ImmutableMap.Builder<Jump, ClusterRupture> splayBuilder = ImmutableMap.builder();
				splayBuilder.putAll(splays);
				splayBuilder.put(jump, new ClusterRupture(jump.toCluster));
				newSplays = splayBuilder.build();
				newInternalJumps = internalJumps;
				newInternalSects = new HashSet<>(internalSects);
			}
			return new ClusterRupture(newClusters, newInternalJumps, newSplays,
					newDescendentsMap, newPredecessorMap, newInternalSects, newUnique);
		} else {
			// it's on a splay, grow that
			boolean found = false;
			ImmutableMap.Builder<Jump, ClusterRupture> splayBuilder = ImmutableMap.builder();
			for (Jump splayJump : splays.keySet()) {
				ClusterRupture splay = splays.get(splayJump);
				if (splay.contains(jump.fromSection)) {
					Preconditions.checkState(!found);
					found = true;
					ClusterRupture newSplay = splay.take(jump);
//					System.out.println("Extended a splay! newSplay: "+newSplay);
					splayBuilder.put(splayJump, newSplay);
				} else {
					// unmodified
					splayBuilder.put(splayJump, splay);
				}
			}
			Preconditions.checkState(found,
					"From section for jump not found in rupture (including splays): %s", jump);
			return new ClusterRupture(clusters, internalJumps, splayBuilder.build(),
					newDescendentsMap, newPredecessorMap, internalSects, newUnique);
		}
	}
	
	/**
	 * @return list of fault sections included in this rupture (including all splays) in order. Splays
	 * are always taken immediately, before continuing on the current strand 
	 */
	public List<FaultSection> buildOrderedSectionList() {
		Multimap<FaultSection, ClusterRupture> splayBranchPoints = HashMultimap.create();
		for (Jump splayJump : splays.keySet()) {
			ClusterRupture splay = splays.get(splayJump);
			splayBranchPoints.put(splayJump.fromSection, splay);
		}
		List<FaultSection> ret = new ArrayList<>();
		for (FaultSubsectionCluster cluster : clusters) {
			ret.addAll(cluster.subSects);
			for (FaultSection sect : cluster.subSects)
				for (ClusterRupture splay : splayBranchPoints.get(sect))
					ret.addAll(splay.buildOrderedSectionList());
		}
		return ret;
	}
	
	/**
	 * @return a reversed view of this rupture. must be a simple, non-splayed rupture
	 * @throws IllegalStateException if rupture is splayed or contains jumps from anywhere 
	 * but the last section of a cluster
	 */
	public ClusterRupture reversed() {
		Preconditions.checkState(splays.isEmpty(), "Can't reverse a splayed rupture");
		
		ImmutableMultimap.Builder<FaultSection, FaultSection> descendentsBuilder = ImmutableMultimap.builder();
		ImmutableMap.Builder<FaultSection, FaultSection> predecessorsBuilder = ImmutableMap.builder();
		for (FaultSection sect1 : sectDescendantsMap.keys())
			for (FaultSection sect2 : sectDescendantsMap.get(sect1))
				predecessorsBuilder.put(sect1, sect2);
		for (FaultSection sect1 : sectPredecessorsMap.keySet())
			descendentsBuilder.put(sect1, sectPredecessorsMap.get(sect1));
		
		List<FaultSubsectionCluster> clusterList = new ArrayList<>();
		for (int i=clusters.length; --i>=0;)
			clusterList.add(clusters[i].reversed());
		Table<FaultSection, FaultSection, Jump> jumpsTable = HashBasedTable.create();
		for (Jump jump : internalJumps) {
			Preconditions.checkState(
					jump.fromSection == jump.fromCluster.subSects.get(jump.fromCluster.subSects.size()-1),
					"Can't reverse a ClusterRupture which contains a non-splay jump from a subsection "
					+ "which is not the last subsection on that cluster");
			jumpsTable.put(jump.fromSection, jump.toSection, jump);
		}
		ImmutableSet.Builder<Jump> jumpsBuilder = ImmutableSet.builder();
		for (int i=1; i<clusterList.size(); i++) {
			FaultSubsectionCluster fromCluster = clusterList.get(i-1);
			FaultSection fromSection = fromCluster.subSects.get(fromCluster.subSects.size()-1);
			FaultSubsectionCluster toCluster = clusterList.get(i);
			FaultSection toSection = toCluster.startSect;
			Jump jump = jumpsTable.get(toSection, fromSection); // get old, non-reversed jump
			jumpsBuilder.add(new Jump(fromSection, fromCluster, toSection, toCluster, jump.distance));
		}
		
		return new ClusterRupture(clusterList.toArray(new FaultSubsectionCluster[0]), jumpsBuilder.build(),
				ImmutableMap.of(), descendentsBuilder.build(), predecessorsBuilder.build(), internalSects, unique);
	}
	
	/**
	 * Constructs a ClusterRupture from a simple, single-strand section list
	 * @param sects
	 * @param distCalc
	 * @return
	 */
	public static ClusterRupture forOrderedSingleStrandRupture(List<? extends FaultSection> sects,
			SectionDistanceAzimuthCalculator distCalc) {
		List<FaultSubsectionCluster> clusterList = new ArrayList<>();
		List<FaultSection> curSects = null;
		int curParent = -2;
		HashSet<Integer> sectIDs = new HashSet<>();
		for (FaultSection sect : sects) {
			if (sect.getParentSectionId() != curParent) {
				if (curSects != null)
					clusterList.add(new FaultSubsectionCluster(curSects));
				curSects = new ArrayList<>();
				curParent = sect.getParentSectionId();
			}
			curSects.add(sect);
			sectIDs.add(sect.getSectionId());
		}
		clusterList.add(new FaultSubsectionCluster(curSects));
		
		ImmutableMultimap.Builder<FaultSection, FaultSection> descendentsBuilder = ImmutableMultimap.builder();
		ImmutableMap.Builder<FaultSection, FaultSection> predecessorsBuilder = ImmutableMap.builder();
		for (int i=1; i<sects.size(); i++) {
			FaultSection sect1 = sects.get(i-1);
			FaultSection sect2 = sects.get(i);
			descendentsBuilder.put(sect1, sect2);
			predecessorsBuilder.put(sect2, sect1);
		}
		
		FaultSubsectionCluster[] clusters = clusterList.toArray(new FaultSubsectionCluster[0]);
		
		ImmutableSet.Builder<Jump> jumpsBuilder = ImmutableSet.builder();
		for (int i=1; i<clusters.length; i++) {
			FaultSubsectionCluster fromCluster = clusters[i-1];
			FaultSection fromSect = fromCluster.subSects.get(fromCluster.subSects.size()-1);
			FaultSubsectionCluster toCluster = clusters[i];
			FaultSection toSect = toCluster.startSect;
			double distance = distCalc == null ? Double.NaN : distCalc.getDistance(fromSect, toSect);
			Jump jump = new Jump(fromSect, fromCluster, toSect, toCluster, distance);
			jumpsBuilder.add(jump);
			fromCluster.addConnection(jump);
			toCluster.addConnection(jump.reverse());
		}
		
		return new ClusterRupture(clusters, jumpsBuilder.build(), ImmutableMap.of(),
				descendentsBuilder.build(), predecessorsBuilder.build(),
				new HashSet<>(sects), new UniqueRupture(sectIDs));
	}
	
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		for (FaultSubsectionCluster cluster : clusters)
			str.append(cluster.toString());
		for (Jump jump : splays.keySet()) {
			ClusterRupture splay = splays.get(jump);
			str.append("\n\t--splay from [").append(jump.fromCluster.parentSectionID);
			str.append(":").append(jump.fromSection.getSectionId()).append("]: "+splay);
		}
		return str.toString();
	}
	
	/**
	 * 
	 * @return Iterable over all Jumps in this rupture and it's splays
	 */
	public Iterable<Jump> getJumpsIterable() {
		if (splays.isEmpty())
			return internalJumps;
		List<Iterable<Jump>> iterables = new ArrayList<>();
		iterables.add(internalJumps);
		iterables.add(splays.keySet());
		for (ClusterRupture splay : splays.values())
			iterables.add(splay.getJumpsIterable());
		return Iterables.concat(iterables);
	}

}
