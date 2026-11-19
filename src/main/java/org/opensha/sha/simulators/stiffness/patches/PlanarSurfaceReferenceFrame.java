package org.opensha.sha.simulators.stiffness.patches;

/** Orthogonal projection onto the area-weighted best-fit plane; exact for planar shapes. */
final class PlanarSurfaceReferenceFrame extends AbstractSurfaceReferenceFrame {
	PlanarSurfaceReferenceFrame(GriddedSurfaceGeometry geometry) {
		this(geometry,new BestFitSurfacePlane(geometry));
	}

	private PlanarSurfaceReferenceFrame(GriddedSurfaceGeometry geometry,BestFitSurfacePlane plane) {
		super(geometry,plane,plane::project);
	}
}
