package scratch.UCERF3.inversion.ruptures;

import java.text.DecimalFormat;
import java.util.List;

import org.opensha.sha.faultSurface.FaultSection;

public class Jump {
	
	public final List<FaultSection> leadingSections;
	public final FaultSection fromSection;
	public final FaultSubsectionCluster fromCluster;
	public final FaultSection toSection;
	public final FaultSubsectionCluster toCluster;
	public final double distance;
	
	public Jump(List<FaultSection> leadingSections, FaultSubsectionCluster fromCluster,
			FaultSection toSection, FaultSubsectionCluster toCluster, double distance) {
		this(leadingSections, leadingSections.get(leadingSections.size()-1), fromCluster,
				toSection, toCluster, distance);
	}
	
	public Jump(FaultSection fromSection, FaultSubsectionCluster fromCluster, FaultSection toSection,
			FaultSubsectionCluster toCluster, double distance) {
		this(null, fromSection, fromCluster, toSection, toCluster, distance);
	}
	
	private Jump(List<FaultSection> leadingSections, FaultSection fromSection,
			FaultSubsectionCluster fromCluster, FaultSection toSection, FaultSubsectionCluster toCluster,
			double distance) {
		if (fromSection == null || fromCluster == null || toSection == null || toCluster == null)
			throw new IllegalArgumentException("Nulls not allowed in Jump constructor");
		this.leadingSections = leadingSections;
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
		if (!fromSection.equals(other.fromSection))
			return false;
		if (toCluster.parentSectionID != other.toCluster.parentSectionID)
			return false;
		if (!toSection.equals(other.toSection))
			return false;
		return true;
	}

}
