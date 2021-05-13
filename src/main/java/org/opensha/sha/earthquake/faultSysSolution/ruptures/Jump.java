package org.opensha.sha.earthquake.faultSysSolution.ruptures;

import java.text.DecimalFormat;
import java.util.Comparator;

import org.opensha.sha.faultSurface.FaultSection;

/**
 * Jump from one FaultSubsectionCluster to another. This is used both when defining all possible jumps
 * between clusters, and to enumerate the individual jumps taken within a ClusterRupture.
 * 
 * hashCode() and equals() only take into account the IDs of the jumping sections, the parent section IDs
 * of the clusters involved, and the direction of the jump
 * 
 * @author kevin
 *
 */
public class Jump {
	
	/**
	 * Section on fromCluster which is the jumping point
	 */
	public final FaultSection fromSection;
	/**
	 * The cluster which we are jumping from
	 */
	public final FaultSubsectionCluster fromCluster;
	/**
	 * Section on toCluster which is the jumping point
	 */
	public final FaultSection toSection;
	/**
	 * The cluster which we are jumping to
	 */
	public final FaultSubsectionCluster toCluster;
	/**
	 * Distance in km between fromSection and toSection
	 */
	public final double distance;
	
	/**
	 * Create a new jump
	 * 
	 * @param fromSection Section on fromCluster which is the jumping point
	 * @param fromCluster The cluster which we are jumping from
	 * @param toSection Section on toCluster which is the jumping point
	 * @param toCluster The cluster which we are jumping to
	 * @param distance Distance in km between fromSection and toSection
	 */
	public Jump(FaultSection fromSection, FaultSubsectionCluster fromCluster, FaultSection toSection,
			FaultSubsectionCluster toCluster, double distance) {
		if (fromSection == null || fromCluster == null || toSection == null || toCluster == null)
			throw new IllegalArgumentException("Nulls not allowed in Jump constructor");
		this.fromSection = fromSection;
		this.fromCluster = fromCluster;
		this.toSection = toSection;
		this.toCluster = toCluster;
		this.distance = distance;
	}
	
	private static final DecimalFormat distDF = new DecimalFormat("0.0");
	
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		
		str.append(fromCluster.parentSectionID).append("[").append(fromSection.getSectionId()).append("]=>");
		str.append(toCluster.parentSectionID).append("[").append(toSection.getSectionId()).append("]");
		str.append(", ").append(distDF.format(distance)).append(" km");
		
		return str.toString();
	}
	
	/**
	 * @return reversed view of this jump (note that this doesn't reverse the clusters themselves)
	 */
	public Jump reverse() {
		return new Jump(toSection, toCluster, fromSection, fromCluster, distance);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + fromCluster.parentSectionID;
		result = prime * result + fromSection.getSectionId();
		result = prime * result + toCluster.parentSectionID;
		result = prime * result + toSection.getSectionId();
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
		Jump other = (Jump) obj;
		if (fromCluster.parentSectionID != other.fromCluster.parentSectionID)
			return false;
		if (fromSection.getSectionId() != other.fromSection.getSectionId())
			return false;
		if (toCluster.parentSectionID != other.toCluster.parentSectionID)
			return false;
		if (toSection.getSectionId() != other.toSection.getSectionId())
			return false;
		return true;
	}
	
	public static final Comparator<Jump> id_comparator = new Comparator<Jump>() {

		@Override
		public int compare(Jump o1, Jump o2) {
			int cmp = compareIDs(o1.fromCluster, o1.fromSection, o2.fromCluster, o2.fromSection);
			if (cmp == 0)
				cmp = compareIDs(o1.toCluster, o1.toSection, o2.toCluster, o2.toSection);
			return cmp;
		}
	};
	
	private static int compareIDs(FaultSubsectionCluster cluster1, FaultSection jumpSect1,
			FaultSubsectionCluster cluster2, FaultSection jumpSect2) {
		int cmp = Integer.compare(cluster1.parentSectionID, cluster2.parentSectionID);
		if (cmp != 0)
			return cmp;
		cmp = Integer.compare(jumpSect1.getSectionId(), jumpSect2.getSectionId());
		if (cmp != 0)
			return cmp;
		return cluster1.compareTo(cluster2);
	}
	
	public static final Comparator<Jump> dist_comparator = new Comparator<Jump>() {

		@Override
		public int compare(Jump o1, Jump o2) {
			return Double.compare(o1.distance, o2.distance);
		}
	};

}
