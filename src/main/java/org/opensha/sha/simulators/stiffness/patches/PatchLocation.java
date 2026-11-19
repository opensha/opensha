package org.opensha.sha.simulators.stiffness.patches;

import org.opensha.commons.geo.Location;

/** A Cartesian patch together with geographic coordinates for maps and viewers. */
public final class PatchLocation {
	public final StiffnessPatch patch;
	public final Location center;
	public final Location[] corners;

	public PatchLocation(StiffnessPatch patch, Location center, Location[] corners) {
		this.patch = patch;
		this.center = center;
		this.corners = corners;
	}
}
