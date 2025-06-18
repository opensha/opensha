package org.opensha.sha.earthquake.faultSysSolution.ruptures;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster.JumpStub;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.ComplexRuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureConnectionSearch;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SingleStrandRuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Rupture which is constructed as a set of connected FaultSubsectionCluster's. Jump's occur
 * between each cluster, and multiple splays are possible. Each rupture has a primary
 * strand defined by the clusters listed below in the 'clusters' array. Additional
 * splays off of that primary strand are contained in the splays map. These ruptures are recursive
 * in nature: splays can have splays. The key contract is that sections only exist once in the
 * rupture, and although each section can have multiple descendants (sections directly downstream
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
	 * Jumps internal to the primary strand, between the primary clusters, in order
	 */
	public final ImmutableList<Jump> internalJumps;
	/**
	 * Jumps to each splay sub-rupture
	 */
	public final ImmutableMap<Jump, ClusterRupture> splays;
	
	/**
	 * UniqueRupture instance, useful for determining if this rupture has already been built and included
	 * in a rupture set (as defined by the set of subsection IDs included, regardless of order)
	 */
	public final UniqueRupture unique;
	
	private RuptureTreeNavigator navigator;
	
	/**
	 * UniqueRupture instance for only the primary strand
	 */
	public final UniqueRupture internalUnique;
	
	/**
	 * True if this rupture is a single strand of connected clusters, i.e., each jump occurs between the last section
	 * of the prior cluster and the first section of the next.
	 */
	public final boolean singleStrand;
	
	/**
	 * Initiate a ClusterRupture with the given starting cluster. You can grow it later with the
	 * take(Jump) method
	 * @param cluster
	 */
	public ClusterRupture(FaultSubsectionCluster cluster) {
		this(new FaultSubsectionCluster[] {cluster}, ImmutableList.of(),
				ImmutableMap.of(), cluster.unique, cluster.unique, true);
	}

	protected ClusterRupture(FaultSubsectionCluster[] clusters, ImmutableList<Jump> internalJumps,
			ImmutableMap<Jump, ClusterRupture> splays, UniqueRupture unique, UniqueRupture internalUnique, boolean singleStrand) {
		super();
		this.clusters = clusters;
		this.internalJumps = internalJumps;
		this.splays = splays;
		this.unique = unique;
		this.internalUnique = internalUnique;
		this.singleStrand = singleStrand;
		Preconditions.checkState(internalUnique.size() <= unique.size());
		int expectedJumps = clusters.length-1;
		Preconditions.checkState(internalJumps.size() == expectedJumps,
				"Expected %s internal jumps but have %s. Rupture: %s",
				expectedJumps, internalJumps.size(), this);
	}
	
	/**
	 * @param sect
	 * @return true if the primary strand of this rupture contains the given section
	 */
	public boolean containsInternal(FaultSection sect) {
		return internalUnique.contains(sect.getSectionId());
	}
	
	/**
	 * @param sect
	 * @return true if this rupture or any splays contains the given section
	 */
	public boolean contains(FaultSection sect) {
		if (containsInternal(sect))
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
		return unique.size();
	}
	
	/**
	 * 
	 * @return the number of sections on the primary strand of this rupture
	 */
	public int getNumInternalSects() {
		return internalUnique.size();
	}
	
	/**
	 * 
	 * @return the total number of jumps (including to and within any splays) of this rupture
	 */
	public int getTotalNumJumps() {
		int tot = internalJumps.size() + splays.size();
		for (ClusterRupture splay : splays.values())
			tot += splay.getTotalNumJumps();
		return tot;
	}
	
	/**
	 * 
	 * @return total number of clusters across this and any splays
	 */
	public int getTotalNumClusters() {
		int tot = clusters.length;
		for (ClusterRupture splay : splays.values())
			tot += splay.getTotalNumClusters();
		return tot;
	}
	
	/**
	 * 
	 * @return total number of splays across this and any splays
	 */
	public int getTotalNumSplays() {
		int tot = splays.size();
		for (ClusterRupture splay : splays.values())
			tot += splay.getTotalNumSplays();
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
		Preconditions.checkState(jump.toSection.equals(jump.toCluster.startSect),
				"Jump toSection is not the start section of the toCluster: jump=%s, toCluster=%s", jump, jump.toCluster);
		
		UniqueRupture newUnique = UniqueRupture.add(this.unique, jump.toCluster.unique);
		int expectedCount = this.unique.size() + jump.toCluster.subSects.size();
		Preconditions.checkState(newUnique.size() == expectedCount,
				"Duplicate subsections. Have %s unique, %s total", newUnique.size(), expectedCount);
		
		boolean newSingleStrand = singleStrand && jump.toCluster.subSects.get(0).equals(jump.toSection);
		
		if (containsInternal(jump.fromSection)) {
			// it's on the main strand
			FaultSubsectionCluster lastCluster = clusters[clusters.length-1];
			FaultSubsectionCluster[] newClusters;
			ImmutableMap<Jump, ClusterRupture> newSplays;
			ImmutableList<Jump> newInternalJumps;
			UniqueRupture newInternalUnique;
			if (lastCluster.endSects.contains(jump.fromSection)) {
				// regular jump from the end
//				System.out.println("Taking a regular jump to extend a strand");
				Preconditions.checkState(lastCluster.equals(jump.fromCluster),
						"Cannot take jump %s: it's from a section on the last cluster, but fromCluster=%s doesn't match lastCluster=%s",
						jump, jump.fromCluster, lastCluster);
				newClusters = Arrays.copyOf(clusters, clusters.length+1);
				newClusters[clusters.length] = jump.toCluster;
				newSplays = splays;
				ImmutableList.Builder<Jump> internalJumpBuild =
						ImmutableList.builderWithExpectedSize(internalJumps.size()+1);
				internalJumpBuild.addAll(internalJumps);
				internalJumpBuild.add(jump);
				newInternalJumps = internalJumpBuild.build();
				newInternalUnique = UniqueRupture.add(internalUnique, jump.toCluster.unique);
				newSingleStrand = newSingleStrand && lastCluster.subSects.get(lastCluster.subSects.size()-1).equals(jump.fromSection);
			} else {
				// it's a new splay
//				System.out.println("it's a new splay!");
				boolean found = false;
				for (FaultSubsectionCluster cluster : clusters) {
					if (cluster.equals(jump.fromCluster)) {
						found = true;
						break;
					}
				}
				Preconditions.checkState(found, "Cannot take jump=%s: fromCluster=%s not found in rupture: %s",
						jump, jump.fromCluster, this);
				newClusters = clusters;
				ImmutableMap.Builder<Jump, ClusterRupture> splayBuilder = ImmutableMap.builder();
				splayBuilder.putAll(splays);
				splayBuilder.put(jump, new ClusterRupture(jump.toCluster));
				newSplays = splayBuilder.build();
				newInternalJumps = internalJumps;
				newInternalUnique = internalUnique;
				newSingleStrand = false;
			}
			return new ClusterRupture(newClusters, newInternalJumps, newSplays, newUnique, newInternalUnique, newSingleStrand);
		} else {
			// it's on a splay, grow that
			newSingleStrand = false;
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
					newUnique, internalUnique, newSingleStrand);
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
		Preconditions.checkState(singleStrand, "Can only reverse single strand ruptures");
		
		List<FaultSubsectionCluster> clusterList = new ArrayList<>();
		for (int i=clusters.length; --i>=0;)
			clusterList.add(clusters[i].reversed());
		
		Table<FaultSection, FaultSection, Jump> jumpsTable = HashBasedTable.create();
		for (Jump jump : internalJumps)
			jumpsTable.put(jump.fromSection, jump.toSection, jump);
		
		ImmutableList.Builder<Jump> jumpsBuilder = ImmutableList.builder();
		for (int i=1; i<clusterList.size(); i++) {
			FaultSubsectionCluster fromCluster = clusterList.get(i-1);
			FaultSection fromSection = fromCluster.subSects.get(fromCluster.subSects.size()-1);
			FaultSubsectionCluster toCluster = clusterList.get(i);
			FaultSection toSection = toCluster.startSect;
			Jump jump = jumpsTable.get(toSection, fromSection); // get old, non-reversed jump
			Preconditions.checkNotNull(jump, "No jump found from %s to %s in rupture:\n\t%s\n\tAvailable jumps:%s",
					toSection.getSectionId(), fromSection.getSectionId(), this, jumpsTable);
			jumpsBuilder.add(new Jump(fromSection, fromCluster, toSection, toCluster, jump.distance));
		}
		
		return new ClusterRupture(clusterList.toArray(new FaultSubsectionCluster[0]), jumpsBuilder.build(),
				ImmutableMap.of(), unique, internalUnique, singleStrand);
	}

	/**
	 * Returns a list of the best possible alternate paths through this rupture,
	 * using each cluster as the starting point. This means that, from each cluster,
	 * the shortest possible jump is taken to the next cluster.
	 *
	 * @param connSearch {@link RuptureConnectionSearch} to find the best paths through this rupture
	 * @return
	 */
	public List<ClusterRupture> getPreferredAltRepresentations(RuptureConnectionSearch connSearch) {
		return getPreferredAltRepresentations(connSearch, false);
	}
	
	public List<ClusterRupture> getPreferredAltRepresentations(RuptureConnectionSearch connSearch, boolean debug) {
		List<ClusterRupture> inversions = new ArrayList<>();

		if (singleStrand) {
			// simple
			inversions.add(reversed());
			return inversions;
		}

		// complex
		List<FaultSubsectionCluster> clusters = new ArrayList<>();
		for (FaultSubsectionCluster cluster : getClustersIterable())
			clusters.add(cluster);
		List<Jump> jumps = new ArrayList<>();
		for (Jump jump : getJumpsIterable())
			jumps.add(jump);

		// now build them out
		int numClusters = getTotalNumClusters();
		for (FaultSubsectionCluster newStart : clusters) {
			ClusterRupture inversion = connSearch.buildClusterRupture(
					clusters, jumps, debug, newStart);
//			if (inversion.getTotalNumClusters() != numClusters) {
//				System.err.println("Inversion has a different cluster count ("
//						+inversion.getTotalNumClusters()+" vs "+numClusters+")");
//				System.err.println("Original: "+this);
//				System.err.println("Inversion: "+inversion);
//				System.err.flush();
//				System.exit(0);
//			}
			Preconditions.checkState(inversion.getTotalNumClusters() == numClusters);
			inversions.add(inversion);
		}

		return inversions;
	}
	
	private final static boolean inv_d = false;

	/**
	 * Returns all possible alternate paths through this rupture that would be allowed by the
	 * given {@link ClusterConnectionStrategy}, trying each cluster as the starting cluster.
	 *
	 * This should return the same set of inversions that would be tried by
	 * {@link ClusterRuptureBuilder}. This differs from the alternative method which takes a
	 * {@link RuptureConnectionSearch} and only returns the optimal paths from each starting
	 * cluster (where the shortest jump from each cluster is taken, not all possible jumps).
	 * 
	 * Note: clusters are never broken up or stitched together, so it's possible that rupture
	 * permutations are missed that use different permutations of these sections, e.g., a cluster
	 * that is contiguous in one representation but can also be represented as one partial cluster that
	 * jumps to another fault and then back to the rest of the original cluster.
	 * 
	 * @return
	 */
	public List<ClusterRupture> getAllAltRepresentations(ClusterConnectionStrategy connStrat, int maxNumSplays) {
		List<ClusterRupture> inversions = new ArrayList<>();

		if (getTotalNumClusters() == 1 || singleStrand) {
			inversions.add(reversed());
			return inversions;
		}

		HashSet<FaultSubsectionCluster> availableClusters = new HashSet<>();
		for (FaultSubsectionCluster cluster : getClustersIterable())
			availableClusters.add(cluster);
		
		if (inv_d) System.out.println("Building inversions for "+this);

		for (FaultSubsectionCluster startCluster : availableClusters) {
			HashSet<FaultSubsectionCluster> nextAvailable = new HashSet<>();
			for (FaultSubsectionCluster avail : availableClusters)
				if (avail != startCluster)
					nextAvailable.add(avail);
			buildAltRepsRecursive(inversions, new ClusterRupture(startCluster),
					nextAvailable, connStrat, maxNumSplays);
			if (startCluster.subSects.size() > 1)
				// try it in reverse as well
				buildAltRepsRecursive(inversions, new ClusterRupture(startCluster.reversed()),
						nextAvailable, connStrat, maxNumSplays);
		}

		return inversions;
	}

	private void buildAltRepsRecursive(List<ClusterRupture> inversions,
			ClusterRupture curRupture,
			HashSet<FaultSubsectionCluster> availableClusters,
			ClusterConnectionStrategy connStrat, int maxNumSplays) {
		if (inv_d) System.out.println("\tBuilding inversion from "+curRupture);
		if (availableClusters.isEmpty()) {
			Preconditions.checkState(curRupture.unique.equals(unique));
			inversions.add(curRupture);
			if (inv_d) System.out.println("\t\tCOMPLETE");
			return;
		}
		for (FaultSubsectionCluster nextCluster : availableClusters) {
			int numSects = nextCluster.subSects.size();
			if (inv_d) System.out.println("\t\tSearching to "+nextCluster);
			HashSet<FaultSubsectionCluster> nextAvailable = new HashSet<>();
			for (FaultSubsectionCluster avail : availableClusters)
				if (avail != nextCluster)
					nextAvailable.add(avail);
			for (int i=0; i<numSects; i++) {
				FaultSection toSect = nextCluster.subSects.get(i);
				if (inv_d) System.out.println("\t\t\tLooking for jumps from "+curRupture+"\tto "+toSect.getSectionId());
				for (Jump jump : connStrat.getJumpsFrom(toSect)) {
					if (inv_d) System.out.println("\t\t\ttestJump="+jump+" sect="+toSect.getSectionId());
					Preconditions.checkState(jump.fromSection.equals(toSect));
					if (curRupture.contains(jump.toSection)) {
						// try this jump, but first reverse it (this was a jump from 'toSect')
						jump = jump.reverse();
						if (inv_d) System.out.println("\t\t\tTaking jump: "+jump);

						// need to replace the source/dest clusters in this jump
						FaultSubsectionCluster fromCluster = null;
						for (FaultSubsectionCluster cluster : curRupture.getClustersIterable()) {
							if (cluster.contains(jump.fromSection)) {
								fromCluster = cluster;
								break;
							}
						}
						Preconditions.checkNotNull(fromCluster);

						int numFromEnd = numSects - i - 1;
						if (numFromEnd < i || nextCluster.endSects.contains(jump.toSection)) {
							// jump is closer to the end of this cluster, reverse it
							if (inv_d) System.out.println("\t\t\tReversing with index="+i
									+" and numFromEnd="+numFromEnd);
							jump = new Jump(jump.fromSection, fromCluster,
									jump.toSection, nextCluster.reversed(jump.toSection), jump.distance);
						} else {
							jump = new Jump(jump.fromSection, fromCluster,
									jump.toSection, nextCluster, jump.distance);
						}

						if (jump.toCluster.startSect != jump.toSection) {
							FaultSubsectionCluster modToCluster = new FaultSubsectionCluster(
									jump.toCluster.subSects, jump.toSection, jump.toCluster.endSects);
							jump = new Jump(jump.fromSection, jump.fromCluster, jump.toSection, modToCluster, jump.distance);
						}
						ClusterRupture nextRupture = curRupture.take(jump);
						if (inv_d) System.out.println("\t\tNew curRupture: "+nextRupture);
						if (maxNumSplays >= 0 && nextRupture.getTotalNumSplays() > maxNumSplays) {
							if (inv_d) System.out.println("\t\t\tBailing here, splays="
									+nextRupture.getTotalNumSplays());
							continue;
						}

						buildAltRepsRecursive(inversions, nextRupture,
								nextAvailable, connStrat, maxNumSplays);
					}
				}
			}
		}
	}

	
	/**
	 * @return Rupture tree navigator (lazily initialized)
	 */
	public RuptureTreeNavigator getTreeNavigator() {
		if (navigator == null) {
			if (singleStrand)
				navigator = new SingleStrandRuptureTreeNavigator(this);
			else
				navigator = new ComplexRuptureTreeNavigator(this);
		}
		return navigator;
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
		
		FaultSubsectionCluster[] clusters = clusterList.toArray(new FaultSubsectionCluster[0]);
		
		ImmutableList.Builder<Jump> jumpsBuilder = ImmutableList.builder();
		for (int i=1; i<clusters.length; i++) {
			FaultSubsectionCluster fromCluster = clusters[i-1];
			FaultSection fromSect = fromCluster.subSects.get(fromCluster.subSects.size()-1);
			FaultSubsectionCluster toCluster = clusters[i];
			FaultSection toSect = toCluster.startSect;
			double distance;
			if (distCalc == null) {
				distance = Double.NaN;
			} else {
				distance = Double.POSITIVE_INFINITY;
				for (FaultSection s1 : fromCluster.subSects)
					for (FaultSection s2 : toCluster.subSects)
						distance = Double.min(distance, distCalc.getDistance(s1, s2));
			}
			Jump jump = new Jump(fromSect, fromCluster, toSect, toCluster, distance);
			jumpsBuilder.add(jump);
			fromCluster.addConnection(jump);
			toCluster.addConnection(jump.reverse());
		}
		
		UniqueRupture unique = UniqueRupture.forClusters(clusters);
		
		return new ClusterRupture(clusters, jumpsBuilder.build(), ImmutableMap.of(), unique, unique, true);
	}
	
	@Override
	public String toString() {
		return toString(false);
	}
	
	public String toString(boolean verbose) {
		StringBuilder str = new StringBuilder();
		if (singleStrand) {
			for (FaultSubsectionCluster cluster : clusters)
				str.append(cluster.toString(verbose));
		} else {
			RuptureTreeNavigator nav = getTreeNavigator();
			for (int i=0; i<clusters.length; i++) {
				FaultSubsectionCluster cluster = clusters[i];
				if (i > 0) {
					int toID = nav.getJump(clusters[i-1], cluster).toSection.getSectionId();
					if (toID == cluster.subSects.get(0).getSectionId())
						toID = -1;
					str.append(cluster.toString(verbose, toID));
				} else {
					str.append(cluster.toString(verbose));
				}
			}
		}
		for (Jump jump : splays.keySet()) {
			ClusterRupture splay = splays.get(jump);
			str.append("\n\t--splay from [").append(jump.fromCluster.parentSectionID);
			if (verbose)
				str.append(". ").append(jump.fromCluster.parentSectionName);
			str.append(":").append(jump.fromSection.getSectionId()).append("]: "+splay);
		}
		return str.toString();
	}
	
	/**
	 * 
	 * @return Iterable over all Jumps in this rupture and its splays
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
	
	/**
	 * 
	 * @return Iterable over all clusters in this rupture and its splays
	 */
	public Iterable<FaultSubsectionCluster> getClustersIterable() {
		if (splays.isEmpty())
			return Lists.newArrayList(clusters);
		List<Iterable<FaultSubsectionCluster>> iterables = new ArrayList<>();
		iterables.add(Lists.newArrayList(clusters));
		for (ClusterRupture splay : splays.values())
			iterables.add(splay.getClustersIterable());
		return Iterables.concat(iterables);
	}
	
	/**
	 * 
	 * @return Iterable over all strands in this rupture
	 */
	public Iterable<ClusterRupture> getStrandsIterable() {
		if (splays.isEmpty())
			return Lists.newArrayList(this);
		List<Iterable<ClusterRupture>> iterables = new ArrayList<>();
		iterables.add(Lists.newArrayList(this));
		for (ClusterRupture splay : splays.values())
			iterables.add(splay.getStrandsIterable());
		return Iterables.concat(iterables);
	}
	
	/*
	 * JSON [de]serialization
	 */
	
	public static void writeJSON(File jsonFile, List<ClusterRupture> ruptures,
			List<? extends FaultSection> subSects) throws IOException {
		Gson gson = buildGson(subSects, ruptures.size()<1000);
		FileWriter fw = new FileWriter(jsonFile);
		Type listType = new TypeToken<List<ClusterRupture>>(){}.getType();
		gson.toJson(ruptures, listType, fw);
		fw.write("\n");
		fw.close();
	}
	
	public static List<ClusterRupture> readJSON(File jsonFile, List<? extends FaultSection> subSects)
			throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(jsonFile));
		return readJSON(reader, subSects);
	}
	
	public static List<ClusterRupture> readJSON(String json, List<? extends FaultSection> subSects) {
		return readJSON(new StringReader(json), subSects);
	}
	
	public static List<ClusterRupture> readJSON(Reader json, List<? extends FaultSection> subSects) {
		Gson gson = buildGson(subSects, false);
		Type listType = new TypeToken<List<ClusterRupture>>(){}.getType();
		List<ClusterRupture> ruptures = gson.fromJson(json, listType);
		try {
			json.close();
		} catch (IOException e) {}
		return ruptures;
	}
	
	public static Gson buildGson(List<? extends FaultSection> subSects, boolean pretty) {
		GsonBuilder builder = new GsonBuilder();
		if (pretty)
			builder.setPrettyPrinting(); // extra whitespace makes these large files large
		builder.registerTypeAdapter(ClusterRupture.class, new Adapter(subSects));
		Gson gson = builder.create();
		
		return gson;
	}
	
	public static class Adapter extends TypeAdapter<ClusterRupture> {
		
		private List<? extends FaultSection> subSects;
		private HashMap<FaultSubsectionCluster, FaultSubsectionCluster> prevClustersMap;

		public Adapter(List<? extends FaultSection> subSects) {
			this.subSects = subSects;
			this.prevClustersMap = new HashMap<>();
		}

		@Override
		public void write(JsonWriter out, ClusterRupture rupture) throws IOException {
			out.beginObject(); // {
			
			out.name("clusters").beginArray(); // [
			for (FaultSubsectionCluster cluster : rupture.clusters) {
				// clusters can have more internal jumps than those used here, so we need to recreate
				// with only the relevant ones
				FaultSubsectionCluster newCluster = new FaultSubsectionCluster(
						cluster.subSects, cluster.endSects);
				for (Jump jump : rupture.internalJumps)
					if (jump.fromCluster == cluster)
						newCluster.addConnection(new Jump(jump.fromSection, newCluster,
								jump.toSection, jump.toCluster, jump.distance));
				for (Jump jump : rupture.splays.keySet())
					if (jump.fromCluster == cluster)
						newCluster.addConnection(new Jump(jump.fromSection, newCluster,
								jump.toSection, jump.toCluster, jump.distance));
				newCluster.writeJSON(out);
			}
			out.endArray(); // ]
			
			if (!rupture.splays.isEmpty()) {
				out.name("splays").beginArray(); // [
				
				for (ClusterRupture splay : rupture.splays.values())
					write(out, splay);
				
				out.endArray(); // ]
			}
			
			out.endObject(); // }
		}

		@Override
		public ClusterRupture read(JsonReader in) throws IOException {
			List<FaultSubsectionCluster> internalClusterList = null;
			List<ClusterRupture> splayList = null;
			Map<FaultSubsectionCluster, List<JumpStub>> jumpStubsMap = new HashMap<>();
			
			in.beginObject(); // {
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "clusters":
//					System.out.println("in clsters: "+in.getPath());
					internalClusterList = new ArrayList<>();
					in.beginArray();
					while (in.hasNext())
						internalClusterList.add(
								FaultSubsectionCluster.readJSON(in, subSects, jumpStubsMap, prevClustersMap));
					in.endArray();
					break;
				case "splays":
//					System.out.println("in splays: "+in.getPath());
					splayList = new ArrayList<>();
					in.beginArray();
					while (in.hasNext())
						splayList.add(read(in));
					in.endArray();
					break;

				default:
					break;
				}
			}
			
//			System.out.println(in.getPath());
			in.endObject(); // }
			
			// reconcile all internal jumps
			List<FaultSubsectionCluster> targetClusters;
			if (splayList == null) {
				targetClusters = internalClusterList;
			} else {
				targetClusters = new ArrayList<>(internalClusterList);
				for (ClusterRupture splay : splayList)
					// add first cluster of splays
					targetClusters.add(splay.clusters[0]);
			}
			FaultSubsectionCluster.buildJumpsFromStubs(targetClusters, jumpStubsMap);
			
			ImmutableList.Builder<Jump> internalJumpsBuilder = ImmutableList.builder();
			FaultSubsectionCluster[] clusters = internalClusterList.toArray(new FaultSubsectionCluster[0]);
			boolean singleStrand = true;
			for (int i=0; i<clusters.length-1; i++) { // -1 as no internal jumps from last section
				FaultSubsectionCluster cluster = clusters[i];
				FaultSubsectionCluster nextCluster = clusters[i+1];
				Collection<Jump> connections = cluster.getConnectionsTo(clusters[i+1]);
				Preconditions.checkState(connections.size() == 1, "Expected 1 jump from %s to %s, have %s?",
						cluster, nextCluster, connections.size());
				Jump jump = connections.iterator().next();
				singleStrand = singleStrand && jump.fromSection.equals(cluster.subSects.get(cluster.subSects.size()-1))
						&& jump.toSection.equals(nextCluster.subSects.get(0));
				internalJumpsBuilder.add(jump);
			}
			ImmutableList<Jump> internalJumps = internalJumpsBuilder.build();
			
			ImmutableMap<Jump, ClusterRupture> splays;
			UniqueRupture internalUnique = UniqueRupture.forClusters(clusters);
			UniqueRupture unique;
			if (splayList == null || splayList.isEmpty()) {
				splays = ImmutableMap.of();
				unique = internalUnique;
			} else {
				singleStrand = false;
				ImmutableMap.Builder<Jump, ClusterRupture> splayBuilder = ImmutableMap.builder();
				// reverse as we are going to search for them in reverse order below, so double reverse
				// ensures that they are loaded in order (which shouldn't matter, but is nice)
				Collections.reverse(splayList);
				for (FaultSubsectionCluster cluster : clusters) {
					for (int i=splayList.size(); --i>=0;) {
						ClusterRupture splay = splayList.get(i);
						Collection<Jump> connections = cluster.getConnectionsTo(splay.clusters[0]);
						if (!connections.isEmpty()) {
							Preconditions.checkState(connections.size() == 1);
							// this is the splay connection
							Jump splayJump = connections.iterator().next();
							splayBuilder.put(splayJump, splay);
							splayList.remove(i);
						}
					}
					if (splayList.isEmpty())
						break;
				}
				Preconditions.checkState(splayList.isEmpty(),
						"No jumps found to %s splay(s)?", splayList.size());
				splays = splayBuilder.build();
				unique = internalUnique;
				for (ClusterRupture splay : splays.values())
					unique = UniqueRupture.add(unique, splay.unique);
			}
			
			return new ClusterRupture(clusters, internalJumps, splays, unique, internalUnique, singleStrand);
		}
		
	}

}
