package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This represents a NucleationClusterEvaluator that represents the spread of a rupture (be it uni/bilaterally). Subclasses
 * supply their own PathNavigator (section-by-section and cluster-by-cluster implementations are defined below) and evaluate
 * each addition as the rupture spreads.
 * 
 * @author kevin
 *
 */
public abstract class PathEvaluator implements NucleationClusterEvaluator {
	
	/**
	 * This interface defines how a rupture spreads. Typically, this is either outward one cluster at a time
	 * or one section at a time.
	 * @author kevin
	 *
	 */
	public static interface PathNavigator {
		
		/**
		 * @return the sections in the current path of this rupture, at the moment that this method is called
		 */
		public List<FaultSection> getCurrentSects();
		
		/**
		 * This locates and returns all of the next additions to this rupture. Those sections will be then consumed
		 * into the current path, so future calls to getCurrentSects() will contain the sections represented by these
		 * additions.
		 * 
		 * @return the next additions to this rupture (be they within clusters or to new clusters)
		 */
		public Set<PathAddition> getNextAdditions();
		
		public default void setVerbose(boolean verbose) {
			// to nothing
		}
	}
	
	/**
	 * This represents an addition to a growing rupture
	 * @author kevin
	 *
	 */
	public static class PathAddition {
		/**
		 * Section that was the jumping point to this addition
		 */
		public final FaultSection  fromSect;
		/**
		 * Cluster that contains fromSect
		 */
		public final FaultSubsectionCluster fromCluster;
		/**
		 * Collection of sections to be added
		 */
		public final Collection<? extends FaultSection> toSects;
		/**
		 * Cluster that contains toSect (may be equal to fromCluster)
		 */
		public final FaultSubsectionCluster toCluster;
		
		public PathAddition(FaultSection fromSect, FaultSubsectionCluster fromCluster,
				Collection<? extends FaultSection> toSects, FaultSubsectionCluster toCluster) {
			this.fromSect = fromSect;
			this.fromCluster = fromCluster;
			this.toSects = toSects;
			this.toCluster = toCluster;
		}
		
		public PathAddition(FaultSection fromSect, FaultSubsectionCluster fromCluster,
				FaultSection toSect, FaultSubsectionCluster toCluster) {
			this.fromSect = fromSect;
			this.fromCluster = fromCluster;
			this.toSects = Collections.singleton(toSect);
			this.toCluster = toCluster;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((toSects == null) ? 0 : toSects.hashCode());
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
			PathAddition other = (PathAddition) obj;
			if (toSects == null) {
				if (other.toSects != null)
					return false;
			} else if (!toSects.equals(other.toSects))
				return false;
			return true;
		}
		
		public String toString() {
			String ret;
			if (fromSect != null)
				ret = fromSect.getSectionId()+"->[";
			else
				ret = "?->[";
			return ret+toSects.stream().map(S -> S.getSectionId()).map(S -> S.toString())
					.collect(Collectors.joining(","))+"]";
		}
	}
	
	/**
	 * Path navigator where rupture grow outward, cluster by cluster
	 * 
	 * @author kevin
	 *
	 */
	public static class ClusterPathNavigator implements PathNavigator {
		
		protected HashSet<FaultSubsectionCluster> currentClusters;
		protected HashSet<FaultSection> currentSects;
		protected RuptureTreeNavigator rupNav;
		
		private List<FaultSubsectionCluster> growthPoints;
		
		protected boolean verbose = true;
		
		public ClusterPathNavigator(FaultSubsectionCluster startCluster, RuptureTreeNavigator nav) {
			currentSects = new HashSet<>(startCluster.subSects);
			currentClusters = new HashSet<>();
			currentClusters.add(startCluster);
			this.rupNav = nav;
			this.growthPoints = new ArrayList<>();
			this.growthPoints.add(startCluster);
		}
		
		public void setVerbose(boolean verbose) {
			this.verbose = verbose;
		}

		@Override
		public List<FaultSection> getCurrentSects() {
			return new ArrayList<>(currentSects);
		}
		
		protected List<FaultSubsectionCluster> getNeighbors(FaultSubsectionCluster cluster) {
			List<FaultSubsectionCluster> neighbors = new ArrayList<>();
			FaultSubsectionCluster predecessor = rupNav.getPredecessor(cluster);
			if (predecessor != null)
				neighbors.add(predecessor);
			neighbors.addAll(rupNav.getDescendants(cluster));
			return neighbors;
		}

		@Override
		public Set<PathAddition> getNextAdditions() {
			HashSet<PathAddition> nextAdds = new HashSet<>();
			if (verbose)
				System.out.println("getNextAdditions with "+growthPoints.size()+" growth points, "+currentSects.size()+" curSects");
			for (FaultSubsectionCluster cluster : growthPoints)
				for (FaultSubsectionCluster neighbor : getNeighbors(cluster))
					if (!currentClusters.contains(neighbor))
						nextAdds.add(new PathAddition(rupNav.getJump(cluster, neighbor).fromSection, cluster, neighbor.subSects, neighbor));
			if (verbose)
				System.out.println("\tFound "+nextAdds.size()+" nextAdds: "+
						nextAdds.stream().map(S -> S.toString()).collect(Collectors.joining(";")));
			List<FaultSubsectionCluster> newGrowthPoints = new ArrayList<>(nextAdds.size());
			for (PathAddition add : nextAdds) {
				currentSects.addAll(add.toSects);
				newGrowthPoints.add(add.toCluster);
				currentClusters.add(add.toCluster);
			}
			growthPoints = newGrowthPoints;
			return nextAdds;
		}
		
	}
	
	/**
	 * Path navigator where rupture grow outward, section by section
	 * 
	 * @author kevin
	 *
	 */
	public static class SectionPathNavigator implements PathNavigator {
		
		protected HashSet<FaultSection> currentSects;
		protected RuptureTreeNavigator rupNav;
		
		private Set<FaultSection> growthPoints;
		
		protected boolean verbose = true;
		
		public SectionPathNavigator(Collection<? extends FaultSection> startSects, RuptureTreeNavigator nav) {
			currentSects = new HashSet<>(startSects);
			Preconditions.checkState(currentSects.size() == startSects.size());
//			System.out.println("Initializing SectionPathNav with "+startSects.size()+" startSects");
			this.rupNav = nav;
			this.growthPoints = currentSects;
		}
		
		public void setVerbose(boolean verbose) {
			this.verbose = verbose;
		}
		
		protected List<FaultSection> getNeighbors(FaultSection sect) {
			List<FaultSection> neighbors = new ArrayList<>();
			FaultSection predecessor = rupNav.getPredecessor(sect);
			if (predecessor != null)
				neighbors.add(predecessor);
			neighbors.addAll(rupNav.getDescendants(sect));
			return neighbors;
		}
		
		@Override
		public List<FaultSection> getCurrentSects() {
			return Lists.newArrayList(currentSects);
		}
		
		@Override
		public Set<PathAddition> getNextAdditions() {
			HashSet<PathAddition> nextAdds = new HashSet<>();
			if (verbose)
				System.out.println("getNextAdditions with "+growthPoints.size()+" growth points, "+currentSects.size()+" curSects");
			for (FaultSection sect : growthPoints) {
				FaultSubsectionCluster fromCluster = rupNav.locateCluster(sect);
				for (FaultSection neighbor : getNeighbors(sect))
					if (!currentSects.contains(neighbor))
						nextAdds.add(new PathAddition(sect, fromCluster, neighbor, rupNav.locateCluster(neighbor)));
			}
			if (verbose)
				System.out.println("\tFound "+nextAdds.size()+" nextAdds: "+
						nextAdds.stream().map(S -> S.toString()).collect(Collectors.joining(";")));
			HashSet<FaultSection> newGrowthPoints = new HashSet<>(nextAdds.size());
			for (PathAddition add : nextAdds) {
				currentSects.addAll(add.toSects);
				newGrowthPoints.addAll(add.toSects);
			}
			growthPoints = newGrowthPoints;
			return nextAdds;
		}
	}

	public abstract PlausibilityResult testAddition(Collection<FaultSection> curSects,
			PathAddition addition, boolean verbose);

	protected abstract PathNavigator getPathNav(ClusterRupture rupture, FaultSubsectionCluster nucleationCluster);
	//		protected SectionPathNavigator getSectPathNav(ClusterRupture rupture, FaultSubsectionCluster nucleationCluster) {
	//			return new SectionPathNavigator(nucleationCluster.subSects, rupture.getTreeNavigator());
	//		}

	@Override
	public PlausibilityResult testNucleationCluster(ClusterRupture rupture,
			FaultSubsectionCluster nucleationCluster, boolean verbose) {
		PathNavigator nav = getPathNav(rupture, nucleationCluster);
		nav.setVerbose(verbose);

		if (verbose)
			System.out.println(getName()+": testing strand(s) with start="+nucleationCluster);

		List<FaultSection> curSects = nav.getCurrentSects();
		Set<PathAddition> nextAdds = nav.getNextAdditions();
		if (verbose)
			System.out.println("Have "+nextAdds.size()+" nextAdds");

		PlausibilityResult result = PlausibilityResult.PASS;
		while (!nextAdds.isEmpty()) {
			for (PathAddition add : nextAdds) {
				PlausibilityResult myResult = testAddition(curSects, add, verbose);
				if (verbose)
					System.out.println("\taddition="+add+" w/ "+curSects.size()+" sources: "+myResult);
				result = result.logicalAnd(myResult);
				if (!verbose && !result.isPass())
					return result;
			}

			curSects = nav.getCurrentSects();
			nextAdds = nav.getNextAdditions();
			if (verbose)
				System.out.println("Have "+nextAdds.size()+" nextAdds");
		}
		Preconditions.checkState(nav.getCurrentSects().size() == rupture.getTotalNumSects(),
				"Processed %s sects but rupture has %s:\n\t%s", nav.getCurrentSects().size(), rupture.getTotalNumSects(), rupture);
		return result;
	}

	public static abstract class Scalar<E extends Number & Comparable<E>> extends PathEvaluator
	implements NucleationClusterEvaluator.Scalar<E> {

		protected final Range<E> acceptableRange;
		protected final PlausibilityResult failureType;

		public Scalar(Range<E> acceptableRange, PlausibilityResult failureType) {
			super();
			this.acceptableRange = acceptableRange;
			Preconditions.checkState(!failureType.isPass());
			this.failureType = failureType;
		}

		public abstract E getAdditionValue(Collection<FaultSection> curSects,
				PathAddition addition, boolean verbose);

		public PlausibilityResult testAddition(Collection<FaultSection> curSects,
				PathAddition addition, boolean verbose) {
			if (acceptableRange.contains(getAdditionValue(curSects, addition, verbose)))
				return PlausibilityResult.PASS;
			return failureType;
		}

		@Override
		public E getNucleationClusterValue(ClusterRupture rupture,
				FaultSubsectionCluster nucleationCluster, boolean verbose) {
			PathNavigator nav = getPathNav(rupture, nucleationCluster);
			nav.setVerbose(verbose);

			if (verbose)
				System.out.println(getName()+": testing strand(s) with start="+nucleationCluster);

			List<FaultSection> curSects = nav.getCurrentSects();
			Set<PathAddition> nextAdds = nav.getNextAdditions();
			if (verbose)
				System.out.println("Have "+nextAdds.size()+" nextAdds");

			E worstVal = null;
			while (!nextAdds.isEmpty()) {
				for (PathAddition add : nextAdds) {
					E val = getAdditionValue(curSects, add, verbose);
					if (worstVal == null || !ScalarValuePlausibiltyFilter.isValueBetter(val, worstVal, acceptableRange))
						worstVal = val;
					if (verbose)
						System.out.println("\taddition="+add+": "+val+" (worst="+worstVal+")");
				}

				curSects = nav.getCurrentSects();
				nextAdds = nav.getNextAdditions();
				if (verbose)
					System.out.println("Have "+nextAdds.size()+" nextAdds");
			}
			Preconditions.checkState(nav.getCurrentSects().size() == rupture.getTotalNumSects(),
					"Processed %s sects but rupture has %s:\n\t%s", nav.getCurrentSects().size(), rupture.getTotalNumSects(), rupture);
			return worstVal;
		}

		@Override
		public Range<E> getAcceptableRange() {
			return acceptableRange;
		}

		@Override
		public PlausibilityResult getFailureType() {
			return failureType;
		}

	}
	
}