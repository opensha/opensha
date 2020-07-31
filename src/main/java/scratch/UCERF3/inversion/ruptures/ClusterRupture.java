package scratch.UCERF3.inversion.ruptures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;

public class ClusterRupture {
	
	public final FaultSubsectionCluster[] clusters;
	public final ImmutableSet<Jump> internalJumps;
	public final ImmutableMap<Jump, ClusterRupture> splays;
	
	// these are for navigating the rupture section tree
	public final ImmutableMultimap<FaultSection, FaultSection> sectDescendentsMap;
	public final ImmutableMap<FaultSection, FaultSection> sectPredecessorsMap;
	
	private final Set<FaultSection> internalSects;
	
	public ClusterRupture(FaultSubsectionCluster cluster) {
		this(new FaultSubsectionCluster[] {cluster}, ImmutableSet.of(),
				ImmutableMap.of(), initialDescendentsMap(cluster), initialPredecessorsMap(cluster));
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
			ImmutableMap<FaultSection, FaultSection> sectPredecessorsMap) {
		super();
		this.clusters = clusters;
		this.internalJumps = internalJumps;
		this.splays = splays;
		this.sectDescendentsMap = sectDescendentsMap;
		this.sectPredecessorsMap = sectPredecessorsMap;
		
		internalSects = new HashSet<>();
		int sectCount = 0;
		for (FaultSubsectionCluster cluster : clusters) {
			sectCount += cluster.subSects.size();
			internalSects.addAll(cluster.subSects);
		}
		Preconditions.checkState(sectCount == internalSects.size(),
				"Duplicate subsections. Have %s unique, %s total", internalSects.size(), sectCount);
	}
	
	public boolean contains(FaultSection sect) {
		if (internalSects.contains(sect))
			return true;
		for (ClusterRupture splay : splays.values())
			if (splay.contains(sect))
				return true;
		return false;
	}
	
	public int getTotalNumSects() {
		int tot = internalSects.size();
		for (ClusterRupture splay : splays.values())
			tot += splay.getTotalNumSects();
		return tot;
	}
	
	public int getNumInternalSects() {
		return internalSects.size();
	}
	
	public int getTotalNumJumps() {
		int tot = internalJumps.size();
		for (ClusterRupture splay : splays.values())
			tot += splay.getTotalNumJumps();
		return tot;
	}
	
	public ClusterRupture take(Jump jump) {
		Preconditions.checkState(contains(jump.fromSection),
				"Cannot take jump because this rupture doesn't have the fromSection: %s", jump);
		Preconditions.checkState(!contains(jump.toSection),
				"Cannot take jump because this rupture already has the toSection: %s", jump);
		ImmutableMap.Builder<FaultSection, FaultSection> predecessorBuilder = ImmutableMap.builder();
		predecessorBuilder.putAll(sectPredecessorsMap);
		predecessorBuilder.put(jump.toSection, jump.fromSection);
		ImmutableMultimap.Builder<FaultSection, FaultSection> descendentsBuilder = ImmutableMultimap.builder();
		descendentsBuilder.putAll(sectDescendentsMap);
		descendentsBuilder.put(jump.fromSection, jump.toSection);
		for (int i=0; i<jump.toCluster.subSects.size()-1; i++) {
			FaultSection sect1 = jump.toCluster.subSects.get(i);
			FaultSection sect2 = jump.toCluster.subSects.get(i+1);
			descendentsBuilder.put(sect1, sect2);
			predecessorBuilder.put(sect2, sect1);
		}
		ImmutableMultimap<FaultSection, FaultSection> newDescendentsMap = descendentsBuilder.build();
		ImmutableMap<FaultSection, FaultSection> newPredecessorMap = predecessorBuilder.build();
		if (internalSects.contains(jump.fromSection)) {
			// it's on the main strand
			FaultSubsectionCluster lastCluster = clusters[clusters.length-1];
			FaultSubsectionCluster[] newClusters;
			ImmutableMap<Jump, ClusterRupture> newSplays;
			ImmutableSet<Jump> newInternalJumps;
			if (jump.fromSection.equals(lastCluster.lastSect)) {
				// regular jump from the end
				newClusters = Arrays.copyOf(clusters, clusters.length+1);
				newClusters[clusters.length] = jump.toCluster;
				newSplays = splays;
				ImmutableSet.Builder<Jump> internalJumpBuild =
						ImmutableSet.builderWithExpectedSize(internalJumps.size()+1);
				internalJumpBuild.addAll(internalJumps);
				internalJumpBuild.add(jump);
				newInternalJumps = internalJumpBuild.build();
			} else {
				// it's a new splay
				newClusters = clusters;
				ImmutableMap.Builder<Jump, ClusterRupture> splayBuilder = ImmutableMap.builder();
				splayBuilder.putAll(splays);
				splayBuilder.put(jump, new ClusterRupture(jump.toCluster));
				newSplays = splayBuilder.build();
				newInternalJumps = internalJumps;
			}
			return new ClusterRupture(newClusters, newInternalJumps, newSplays,
					newDescendentsMap, newPredecessorMap);
		} else {
			// it's on a splay, grow that
			boolean found = false;
			ImmutableMap.Builder<Jump, ClusterRupture> splayBuilder = ImmutableMap.builder();
			for (Jump splayJump : splays.keySet()) {
				ClusterRupture splay = splays.get(splayJump);
				if (splay.clusters[splay.clusters.length-1].lastSect.equals(jump.fromSection)) {
					Preconditions.checkState(!found);
					found = true;
					splayBuilder.put(splayJump, splay.take(jump));
				} else {
					// unmodified
					splayBuilder.put(splayJump, splay);
				}
			}
			Preconditions.checkState(found,
					"Jump would be a splay off of an existing splay, which is not allowed: %s", jump);
			return new ClusterRupture(clusters, internalJumps, splayBuilder.build(),
					newDescendentsMap, newPredecessorMap);
		}
	}
	
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
	
	public ClusterRupture reversed() {
		Preconditions.checkState(splays.isEmpty(), "Can't reverse a splayed rupture");
		
		ImmutableMultimap.Builder<FaultSection, FaultSection> descendentsBuilder = ImmutableMultimap.builder();
		ImmutableMap.Builder<FaultSection, FaultSection> predecessorsBuilder = ImmutableMap.builder();
		for (FaultSection sect1 : sectDescendentsMap.keys())
			for (FaultSection sect2 : sectDescendentsMap.get(sect1))
				predecessorsBuilder.put(sect1, sect2);
		for (FaultSection sect1 : sectPredecessorsMap.keySet())
			descendentsBuilder.put(sect1, sectPredecessorsMap.get(sect1));
		
		List<FaultSubsectionCluster> clusterList = new ArrayList<>();
		for (int i=clusters.length; --i>=0;)
			clusterList.add(clusters[i].reversed());
		Table<FaultSection, FaultSection, Jump> jumpsTable = HashBasedTable.create();
		for (Jump jump : internalJumps)
			jumpsTable.put(jump.fromSection, jump.toSection, jump);
		ImmutableSet.Builder<Jump> jumpsBuilder = ImmutableSet.builder();
		for (int i=1; i<clusterList.size(); i++) {
			FaultSubsectionCluster fromCluster = clusterList.get(i-1);
			FaultSection fromSection = fromCluster.lastSect;
			FaultSubsectionCluster toCluster = clusterList.get(i);
			FaultSection toSection = toCluster.firstSect;
			Jump jump = jumpsTable.get(toSection, fromSection); // get old, non-reversed jump
			jumpsBuilder.add(new Jump(fromSection, fromCluster, toSection, toCluster, jump.distance));
		}
		
		return new ClusterRupture(clusterList.toArray(new FaultSubsectionCluster[0]), jumpsBuilder.build(),
				ImmutableMap.of(), descendentsBuilder.build(), predecessorsBuilder.build());
	}
	
	public static ClusterRupture forOrderedSingleStrandRupture(List<? extends FaultSection> sects,
			SectionDistanceAzimuthCalculator distCalc) {
		List<FaultSubsectionCluster> clusterList = new ArrayList<>();
		List<FaultSection> curSects = null;
		int curParent = -2;
		for (FaultSection sect : sects) {
			if (sect.getParentSectionId() != curParent) {
				if (curSects != null)
					clusterList.add(new FaultSubsectionCluster(curSects));
				curSects = new ArrayList<>();
				curParent = sect.getParentSectionId();
			}
			curSects.add(sect);
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
			FaultSection fromSect = fromCluster.lastSect;
			FaultSubsectionCluster toCluster = clusters[i];
			FaultSection toSect = toCluster.firstSect;
			double distance = distCalc == null ? Double.NaN : distCalc.getDistance(fromSect, toSect);
			Jump jump = new Jump(fromSect, fromCluster, toSect, toCluster, distance);
			jumpsBuilder.add(jump);
			fromCluster.addConnection(jump);
			toCluster.addConnection(jump.reverse());
		}
		
		return new ClusterRupture(clusters, jumpsBuilder.build(), ImmutableMap.of(),
				descendentsBuilder.build(), predecessorsBuilder.build());
	}
	
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		for (FaultSubsectionCluster cluster : clusters)
			str.append(cluster.toString());
		for (Jump jump : splays.keySet()) {
			ClusterRupture splay = splays.get(jump);
			str.append("\n\t--splay from [").append(jump.fromCluster.parentSectionID);
			str.append(jump.fromSection.getSectionId()).append("]");
		}
		return str.toString();
	}

}
