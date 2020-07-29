package scratch.UCERF3.inversion.ruptures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

public class ClusterRupture {
	
	public final FaultSubsectionCluster[] primaryStrand;
	public final Set<Jump> jumps;
	public final Set<FaultSection> sectsSet;
	public final List<FaultSubsectionCluster[]> splays;
	
	private final Set<FaultSection> mainSects;
	private final Set<FaultSection> splaySects;
	
	public ClusterRupture(FaultSubsectionCluster[] primaryStrand) {
		this(primaryStrand, new HashSet<>(), null);
		Preconditions.checkState(primaryStrand.length == 1);
	}

	private ClusterRupture(FaultSubsectionCluster[] primaryStrand, Set<Jump> jumps,
			List<FaultSubsectionCluster[]> splays) {
		super();
		this.primaryStrand = primaryStrand;
		if (jumps instanceof ImmutableSet<?>)
			this.jumps = jumps;
		else
			this.jumps = ImmutableSet.copyOf(jumps);
		if (splays == null)
			this.splays = ImmutableList.of();
		else if (splays instanceof ImmutableList<?>)
			this.splays = splays;
		else
			this.splays = ImmutableList.copyOf(splays);
		mainSects = new HashSet<>();
		int sectCount = 0;
		for (FaultSubsectionCluster cluster : primaryStrand) {
			sectCount += cluster.subSects.size();
			mainSects.addAll(cluster.subSects);
		}
		splaySects = new HashSet<>();
		for (FaultSubsectionCluster[] splay : this.splays) {
			for (FaultSubsectionCluster cluster : splay) {
				sectCount += cluster.subSects.size();
				splaySects.addAll(cluster.subSects);
			}
		}
		ImmutableSet.Builder<FaultSection> sectsBuilder = ImmutableSet.builderWithExpectedSize(sectCount);
		sectsSet = sectsBuilder.addAll(mainSects).addAll(splaySects).build();
		Preconditions.checkState(sectCount == sectsSet.size(),
				"Duplicate subsections. Have %s unique, %s total", sectsSet.size(), sectCount);
	}
	
	public ClusterRupture take(Jump jump) {
		Preconditions.checkState(sectsSet.contains(jump.fromSection),
				"Cannot take jump because this rupture doesn't have the fromSection: %s", jump);
		FaultSubsectionCluster lastCluster = primaryStrand[primaryStrand.length-1];
		FaultSubsectionCluster[] newPrimaryStrand;
		List<FaultSubsectionCluster[]> newSplays;
		if (jump.fromSection.equals(lastCluster.lastSect)) {
			// regular jump from the end
			newPrimaryStrand = Arrays.copyOf(primaryStrand, primaryStrand.length+1);
			newPrimaryStrand[primaryStrand.length] = jump.toCluster;
			newSplays = splays;
		} else if (mainSects.contains(jump.fromSection)) {
			// it's a new splay
			newPrimaryStrand = primaryStrand;
			newSplays = new ArrayList<>(splays);
			newSplays.add(new FaultSubsectionCluster[] {jump.toCluster});
		} else {
			// it's a continuation of an existing splay
			newPrimaryStrand = primaryStrand;
			boolean found = false;
			newSplays = new ArrayList<>();
			for (FaultSubsectionCluster[] splay : splays) {
				if (splay[splay.length-1].lastSect.equals(jump.fromSection)) {
					found = true;
					splay = Arrays.copyOf(splay, splay.length+1);
					splay[splay.length-1] = jump.toCluster;
					break;
				}
				newSplays.add(splay);
			}
			Preconditions.checkState(found,
					"Jump would be a splay off of an existing splay, which is not allowed: %s", jump);
		}
		HashSet<Jump> newJumps = new HashSet<>(jumps);
		newJumps.add(jump);
		return new ClusterRupture(newPrimaryStrand, newJumps, newSplays);
	}
	
	public List<FaultSection> buildOrderedSectionList() {
		Multimap<FaultSection, FaultSubsectionCluster[]> splayBranchPoints = HashMultimap.create();
		Map<FaultSection, Jump> toJumps = new HashMap<>();
		for (Jump jump : jumps)
			toJumps.put(jump.toSection, jump);
		for (FaultSubsectionCluster[] splay : splays) {
			FaultSection first = splay[0].firstSect;
			Jump jump = toJumps.get(first);
			splayBranchPoints.put(jump.fromSection, splay);
		}
		List<FaultSection> ret = new ArrayList<>();
		for (FaultSubsectionCluster cluster : primaryStrand) {
			ret.addAll(cluster.subSects);
			for (FaultSection sect : cluster.subSects) {
				for (FaultSubsectionCluster[] splay : splayBranchPoints.get(sect)) {
					for (FaultSubsectionCluster splayCluster : splay)
						ret.addAll(splayCluster.subSects);
				}
			}
		}
		return ret;
	}
	
//	public interface StrandSectionView {
//		
//		public int size();
//		
//		public FaultSection get(int index);
//		
//		public StrandSectionView getParent();
//	}
//	
//	private class PrimaryStrandSectionView implements StrandSectionView {
//		
//	}

}
