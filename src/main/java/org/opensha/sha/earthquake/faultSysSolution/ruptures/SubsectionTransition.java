package org.opensha.sha.earthquake.faultSysSolution.ruptures;

import java.util.Objects;

import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

/** A directed subsection boundary, without inter-cluster jump geometry. */
public class SubsectionTransition implements Transition {

	public final FaultSection fromSection;
	public final FaultSection toSection;

	public SubsectionTransition(FaultSection fromSection, FaultSection toSection) {
		this.fromSection = Preconditions.checkNotNull(fromSection);
		this.toSection = Preconditions.checkNotNull(toSection);
		Preconditions.checkArgument(fromSection.getSectionId() != toSection.getSectionId(),
				"A transition must connect distinct subsections");
	}

	@Override
	public FaultSection getFromSection() { return fromSection; }

	@Override
	public FaultSection getToSection() { return toSection; }

	public SubsectionTransition reverse() {
		return new SubsectionTransition(toSection, fromSection);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || obj.getClass() != getClass()) return false;
		SubsectionTransition other = (SubsectionTransition)obj;
		return fromSection.getSectionId() == other.fromSection.getSectionId()
				&& toSection.getSectionId() == other.toSection.getSectionId();
	}

	@Override
	public int hashCode() {
		return Objects.hash(fromSection.getSectionId(), toSection.getSectionId());
	}

	@Override
	public String toString() {
		return fromSection.getSectionId()+" -> "+toSection.getSectionId();
	}
}
