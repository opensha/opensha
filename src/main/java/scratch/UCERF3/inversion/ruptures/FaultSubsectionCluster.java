package scratch.UCERF3.inversion.ruptures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;

public class FaultSubsectionCluster {

	public final int parentSectionID;
	public final String parentSectionName;
	public final List<FaultSection> subSects;
	public final FaultSection firstSect;
	public final FaultSection lastSect;
	private final HashSet<FaultSection> sectsSet;
	private final List<Jump> possibleJumps;
	private final Multimap<FaultSection, Jump> jumpsForSection;
	private final Multimap<FaultSubsectionCluster, Jump> jumpsToCluster;
	
	public FaultSubsectionCluster(List<FaultSection> subSects) {
		this(subSects, null);
	}
	
	public FaultSubsectionCluster(List<FaultSection> subSects,
			List<Jump> possibleJumps) {
		Preconditions.checkArgument(!subSects.isEmpty(), "Must supply at least 1 subsection");
		this.subSects = ImmutableList.copyOf(subSects);
		this.firstSect = subSects.get(0);
		this.lastSect = subSects.get(subSects.size()-1);
		sectsSet = new HashSet<>(subSects);
		int parentSectionID = -1;
		String parentSectionName = null;
		for (FaultSection subSect : subSects) {
			if (parentSectionID < 0) {
				parentSectionID = subSect.getParentSectionId();
				parentSectionName = subSect.getParentSectionName();
			} else {
				Preconditions.checkState(subSect.getParentSectionId() == parentSectionID);
			}
		}
		this.parentSectionID = parentSectionID;
		this.parentSectionName = parentSectionName;
		jumpsForSection = HashMultimap.create();
		jumpsToCluster = HashMultimap.create();
		this.possibleJumps = new ArrayList<>();
		if (possibleJumps != null)
			for (Jump jump : possibleJumps)
				addConnection(jump);
	}
	
	public void addConnection(Jump jump) {
		Preconditions.checkState(jump.fromCluster == this);
		Preconditions.checkState(sectsSet.contains(jump.fromSection));
		possibleJumps.add(jump);
		jumpsForSection.put(jump.fromSection, jump);
		jumpsToCluster.put(jump.toCluster, jump);
	}
	
	public Set<FaultSection> getExitPoints() {
		return jumpsForSection.keySet();
	}
	
	public Set<FaultSubsectionCluster> getConnectedClusters() {
		return jumpsToCluster.keySet();
	}
	
	public List<Jump> getConnections() {
		return ImmutableList.copyOf(possibleJumps);
	}
	
	public Collection<Jump> getConnections(FaultSection exitPoint) {
		Preconditions.checkState(sectsSet.contains(exitPoint), "Given section is not part of this cluster");
		return jumpsForSection.get(exitPoint);
	}
	@Override
	public String toString() {
		StringBuilder str = null;
		for (FaultSection sect : subSects) {
			if (str == null)
				str = new StringBuilder("[").append(parentSectionID).append(":");
			else
				str.append(",");
			str.append(sect.getSectionId());
		}
		return str.append("]").toString();
	}
	
	public FaultSubsectionCluster reversed() {
		List<FaultSection> sects = new ArrayList<>(subSects);
		Collections.reverse(sects);
		FaultSubsectionCluster reversed = new FaultSubsectionCluster(sects);
		for (Jump jump : possibleJumps)
			reversed.addConnection(new Jump(jump.fromSection, reversed,
					jump.toSection, jump.toCluster, jump.distance));
		return reversed;
	}

}
