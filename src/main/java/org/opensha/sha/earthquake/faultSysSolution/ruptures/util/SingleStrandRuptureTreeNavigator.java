package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.Collection;
import java.util.Collections;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public class SingleStrandRuptureTreeNavigator implements RuptureTreeNavigator {
	
	private ClusterRupture rupture;

	public SingleStrandRuptureTreeNavigator(ClusterRupture rupture) {
		this.rupture = rupture;
		Preconditions.checkState(rupture.singleStrand);
	}

	@Override
	public FaultSubsectionCluster getPredecessor(FaultSubsectionCluster cluster) {
		for (int i=0; i<rupture.clusters.length; i++) {
			if (rupture.clusters[i] == cluster) {
				if (i == 0)
					return null;
				return rupture.clusters[i-1];
			}
		}
		throw new IllegalStateException("Cluster not found in rupture: "+cluster);
	}
	
	private static final Collection<FaultSubsectionCluster> emptyClusters = Collections.emptySet();

	@Override
	public Collection<FaultSubsectionCluster> getDescendants(FaultSubsectionCluster cluster) {
		for (int i=0; i<rupture.clusters.length; i++) {
			if (rupture.clusters[i] == cluster) {
				if (i == rupture.clusters.length-1)
					return emptyClusters;
				return Collections.singleton(rupture.clusters[i+1]);
			}
		}
		throw new IllegalStateException("Cluster not found in rupture: "+cluster);
	}

	@Override
	public Jump getJump(FaultSubsectionCluster fromCluster, FaultSubsectionCluster toCluster) {
		for (Jump jump : rupture.getJumpsIterable()) {
			FaultSubsectionCluster myFrom = jump.fromCluster;
			FaultSubsectionCluster myTo = jump.toCluster;
			if (myFrom.equals(fromCluster) && myTo.equals(toCluster))
				return jump;
			if (myTo.equals(fromCluster) && myFrom.equals(toCluster))
				return jump.reverse();
		}
		throw new IllegalStateException("no jump fround from "+fromCluster+" to "+toCluster+" in rupture: "+rupture);
	}

	@Override
	public Jump getJump(FaultSection fromSection, FaultSection toSection) {
		for (Jump jump : rupture.getJumpsIterable()) {
			FaultSection myFrom = jump.fromSection;
			FaultSection myTo = jump.toSection;
			if (myFrom == fromSection && myTo == toSection)
				return jump;
			if (myTo == fromSection && myFrom == toSection)
				return jump.reverse();
		}
		throw new IllegalStateException("no jump fround from "+fromSection.getSectionId()
			+" to "+toSection.getSectionId()+" in rupture: "+rupture);
	}
	
	private static final Collection<FaultSection> emptySects = Collections.emptySet();

	@Override
	public FaultSection getPredecessor(FaultSection sect) {
		for (int i=0; i<rupture.clusters.length; i++) {
			if (rupture.clusters[i].contains(sect)) {
				int j = rupture.clusters[i].subSects.indexOf(sect);
				Preconditions.checkState(j >= 0);
				if (j > 0)
					return rupture.clusters[i].subSects.get(j-1);
				if (i == 0)
					return null;
				return rupture.clusters[i-1].subSects.get(rupture.clusters[i-1].subSects.size()-1);
			}
		}
		throw new IllegalStateException("Section not found in rupture: "+sect);
	}

	@Override
	public Collection<FaultSection> getDescendants(FaultSection sect) {
		for (int i=0; i<rupture.clusters.length; i++) {
			if (rupture.clusters[i].contains(sect)) {
				int j = rupture.clusters[i].subSects.indexOf(sect);
				Preconditions.checkState(j >= 0);
				if (j < rupture.clusters[i].subSects.size()-1)
					return Collections.singleton(rupture.clusters[i].subSects.get(j+1));
				if (i == rupture.clusters.length-1)
					return emptySects;
				return Collections.singleton(rupture.clusters[i+1].subSects.get(0));
			}
		}
		throw new IllegalStateException("Section not found in rupture: "+sect);
	}

	@Override
	public FaultSubsectionCluster locateCluster(FaultSection section) {
		for (int i=0; i<rupture.clusters.length; i++)
			if (rupture.clusters[i].contains(section))
				return rupture.clusters[i];
		throw new IllegalStateException("Section "+section.getSectionId()+" not found in rupture: "+rupture);
	}

	@Override
	public Jump getJumpTo(FaultSubsectionCluster cluster) {
		for (int i=0; i<rupture.clusters.length; i++) {
//			if (rupture.clusters[i] == cluster) {
			if (rupture.clusters[i].equals(cluster)) {
				if (i == 0)
					return null;
				return getJump(rupture.clusters[i-1], cluster);
			}
		}
		throw new IllegalStateException("Cluster "+cluster+" not found in rupture: "+rupture);
	}

}
