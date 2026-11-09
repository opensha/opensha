package org.opensha.sha.earthquake.faultSysSolution.ruptures;

import org.opensha.sha.faultSurface.FaultSection;

/**
 * A directed transition between two subsections. A transition can be an inter-cluster
 * {@link Jump} or a traversed boundary within a parent section. Implementations need
 * not carry jump distances or clusters.
 */
public interface Transition {

	FaultSection getFromSection();

	FaultSection getToSection();
}
