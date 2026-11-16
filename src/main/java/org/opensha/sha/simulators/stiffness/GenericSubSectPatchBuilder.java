package org.opensha.sha.simulators.stiffness;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.GriddedSurfaceGeometry.SurfacePoint;
import org.opensha.sha.simulators.stiffness.StiffnessCalc.Patch;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;
import com.google.common.base.Preconditions;

/**
 * Builds initial reference rows, optionally refits physical columns, then repairs
 * persistent thin strips. Geometry/reference coordinates are km; StiffnessCalc
 * centers, corners and side lengths are meters. Mechanisms enter in degrees.
 */
final class GenericSubSectPatchBuilder implements SubSectPatchBuilder {
	private static final int MAX_BOUND_FIT_ATTEMPTS = 30;
	private static final int DEPTH_SEARCH_SUBDIVISIONS = 20;
	private static final int DEPTH_BISECTION_STEPS = 40;
	private static final double CORNER_TOLERANCE_METERS = 1e-6;
	private static final double EXTENT_TOLERANCE_KM = 1e-9;
	/** Increment when geometry or layout conventions change; included in persistent cache keys. */
	static final int VERSION=10;
	private final double spacing;
	private final int zone;
	private final char letter;

	GenericSubSectPatchBuilder(double spacing, int zone, char letter) {
		this.spacing=spacing;
		this.zone=zone;
		this.letter=letter;
	}

	@Override
	public List<PatchLocation> buildPatches(FaultSection section, EvenlyGriddedSurface surface, PatchAlignment alignment) {
		try {
			return buildPatches(surface, section.getAveRake(), alignment);
		} catch (IllegalArgumentException | IllegalStateException e) {
			throw new IllegalStateException("Cannot build GENERIC patches for section "
					+section.getSectionId()+" ("+section.getSectionName()+"): "+e.getMessage(),e);
		}
	}

	List<PatchLocation> buildPatches(EvenlyGriddedSurface surface, double rake, PatchAlignment alignment) {
		return buildLayout(surface,rake,alignment).patches;
	}

	Layout buildLayout(EvenlyGriddedSurface surface, double rake, PatchAlignment alignment) {
		GriddedSurfaceGeometry geometry=new GriddedSurfaceGeometry(surface,zone,letter);
		SurfaceReferenceFrame frame=new SurfaceReferenceFrame(geometry);
		ProjectedGridPatchLayout grid=new ProjectedGridPatchLayout(frame,spacing,alignment);
		List<PatchLocation> patches=new ArrayList<>();
		List<int[]> gridIndices=new ArrayList<>();
		for (ProjectedGridPatchLayout.Node node : grid.nodes) {
			PatchLocation location=buildPatch(frame,rake,node.x,node.y);
			patches.add(location);
			gridIndices.add(new int[] {node.row,node.col,node.segment});
		}
		int infillCount=0;
		if (alignment == PatchAlignment.FILL_OVERLAP) {
			// Initial sources must be feasible; only optional refinements may fail
			// placement. Do not hide invalid geometry or other unexpected failures.
			PatchFactory optionalPatch=(x,y) -> {
				try { return buildPatch(frame,rake,x,y); }
				catch (UnplaceablePatchException e) { return null; }
			};
			ColumnRunPatchRefitter.refit(frame,spacing,patches,gridIndices,optionalPatch);
			for (PatchLocation location : NarrowStripPatchInfill.build(frame,spacing,patches,optionalPatch)) {
				patches.add(location);
				// Negative, unique rows describe isolated supplements, not fitted
				// rows or neighbor relationships. Refitted columns keep positive IDs.
				gridIndices.add(new int[] {-(++infillCount),-1,0});
			}
		}
		List<double[]> referenceCenters=new ArrayList<>();
		for (PatchLocation location : patches)
			referenceCenters.add(frame.project(new Vector3D(location.patch.center).scalarMultiply(0.001)));

		Preconditions.checkState(!patches.isEmpty(),"Surface produced no patches");
		return new Layout(frame,patches,referenceCenters,gridIndices,grid.maxBoundaryCorrection,infillCount);
	}

	/** Alternate exact physical width/depth checks: either correction can change the local tangent. */
	private PatchLocation buildPatch(SurfaceReferenceFrame frame,double rake,double x,double y) {
		GriddedSurfaceGeometry geometry=frame.geometry;
		double[] initialCenter=frame.inside(x,y,frame.centerDomain(spacing));
		x=initialCenter[0]; y=initialCenter[1];
		SurfacePoint sample=frame.sample(x,y);
		Patch patch=patch(sample,rake);
		boolean fitted=false;
		for (int attempt=0; attempt<MAX_BOUND_FIT_ATTEMPTS; attempt++) {
			double[] extent=alongStrikeExtent(patch,frame);
			if (extent[1]-extent[0] <= frame.maxX-frame.minX+EXTENT_TOLERANCE_KM
					&& (extent[0] < frame.minX-EXTENT_TOLERANCE_KM || extent[1] > frame.maxX+EXTENT_TOLERANCE_KM)) {
				double centerX=frame.project(new Vector3D(patch.center).scalarMultiply(0.001))[0];
				double halfWidth=0.5*(extent[1]-extent[0]);
				double targetX=Math.max(frame.minX+halfWidth,Math.min(frame.maxX-halfWidth,centerX));
				double[] inside=frame.inside(targetX,y,frame.centerDomain(spacing,halfWidth));
				x=inside[0]; y=inside[1];
				sample=frame.sample(x,y);
				patch=patch(sample,rake);
			}
			if (exceedsOriginalDepthSpan(patch,geometry)) {
				// Full-size patches on undersized sections may exceed the original
				// depth range. Keep their surface centers unless the free surface
				// requires a minimal vertical translation (one-micrometer padding).
				double highest=Double.NEGATIVE_INFINITY;
				for (double[] corner : patch.corner) highest=Math.max(highest,corner[2]);
				if (highest > 0) {
					sample=new SurfacePoint(sample.center.add(new Vector3D(0,0,-(highest+CORNER_TOLERANCE_METERS)/1000d)),sample.strike,sample.dip);
					patch=patch(sample,rake);
				}
			} else if (!withinDepthBounds(patch,geometry)) {
				// The reference-plane constraint is approximate for curved faults.
				// Find a feasible physical rectangle, then approach it from the
				// current center while retaining a feasible endpoint in bisection.
				double feasible=Double.NaN, nearest=Double.POSITIVE_INFINITY;
				for (double[] interval : frame.intervals(x,false)) {
					int steps=Math.max(1,(int)Math.ceil((interval[1]-interval[0])/(spacing/DEPTH_SEARCH_SUBDIVISIONS)));
					for (int step=0; step<=steps; step++) {
						double trial=interval[0]+(interval[1]-interval[0])*step/steps;
						if (Math.abs(trial-y) < nearest && withinDepthBounds(patch(frame.sample(x,trial),rake),geometry)) {
							feasible=trial; nearest=Math.abs(trial-y);
						}
					}
				}
				if (!Double.isFinite(feasible))
					throw new UnplaceablePatchException("Patch at reference ("+x+","+y+
							") cannot fit within original depth bounds ["+geometry.minDepth+","+geometry.maxDepth+"] km");
				double outside=y;
				for (int i=0; i<DEPTH_BISECTION_STEPS; i++) {
					double mid=(outside+feasible)*0.5;
					if (withinDepthBounds(patch(frame.sample(x,mid),rake),geometry)) feasible=mid; else outside=mid;
				}
				y=feasible;
				sample=frame.sample(x,y);
				patch=patch(sample,rake);
			}

			if (withinAlongStrikeBounds(patch,frame) && withinEffectiveDepthBounds(patch,geometry)) {
				fitted=true;
				break;
			}
		}
		if (!fitted) throw new UnplaceablePatchException("Cannot fit patch at reference ("+x+","+y+") within joint global bounds");

		Location[] corners=new Location[4];
		// Public plotting order is the reverse of the stiffness kernel corner order.
		int[] indices={3,2,1,0};
		for (int i=0; i<4; i++)
			corners[i]=geometry.location(new Vector3D(patch.corner[indices[i]]).scalarMultiply(0.001));
		return new PatchLocation(patch,geometry.location(new Vector3D(patch.center).scalarMultiply(0.001)),corners);
	}

	/**
	 * Build a bounded physical patch from reference coordinates in km. Optional
	 * passes receive a factory returning null only when placement is infeasible.
	 */
	@FunctionalInterface
	interface PatchFactory {
		PatchLocation build(double x, double y);
	}

	/** Fatal for initial patches; optional refit/infill candidates may be skipped. */
	private static final class UnplaceablePatchException extends IllegalStateException {
		UnplaceablePatchException(String message) { super(message); }
	}

	/** Diagnostics preserve row/column identities as well as 2D/3D correspondence. */
	static final class Layout {
		final SurfaceReferenceFrame frame;
		final List<PatchLocation> patches;
		final List<double[]> referenceCenters;
		// {row, column, row-interval segment}. Initial rows share a grid;
		// refitted row numbers are local to a column. Negative rows mark infill.
		final List<int[]> gridIndices;
		final double maxBoundaryCorrection;
		final int infillCount;
		Layout(SurfaceReferenceFrame frame,List<PatchLocation> patches,List<double[]> referenceCenters,
				List<int[]> gridIndices,double correction,int infillCount) {
			this.frame=frame;
			this.patches=java.util.Collections.unmodifiableList(patches);
			this.referenceCenters=java.util.Collections.unmodifiableList(referenceCenters);
			this.gridIndices=java.util.Collections.unmodifiableList(gridIndices);
			this.maxBoundaryCorrection=correction;
			this.infillCount=infillCount;
		}
	}

	private Patch patch(SurfacePoint sample, double rake) {
		return new Patch(sample.center.scalarMultiply(1000).toArray(),spacing*1000,spacing*1000,
				new FocalMechanism(sample.strike,sample.dip,rake));
	}

	private static double[] alongStrikeExtent(Patch patch,SurfaceReferenceFrame frame) {
		double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
		for (double[] corner : patch.corner) {
			double x=frame.project(new Vector3D(corner).scalarMultiply(0.001))[0];
			low=Math.min(low,x); high=Math.max(high,x);
		}
		return new double[] {low,high};
	}

	private static boolean withinAlongStrikeBounds(Patch patch,SurfaceReferenceFrame frame) {
		double[] extent=alongStrikeExtent(patch,frame);
		return extent[1]-extent[0] > frame.maxX-frame.minX+EXTENT_TOLERANCE_KM
				|| extent[0] >= frame.minX-EXTENT_TOLERANCE_KM && extent[1] <= frame.maxX+EXTENT_TOLERANCE_KM;
	}

	private static boolean withinEffectiveDepthBounds(Patch patch,GriddedSurfaceGeometry geometry) {
		if (!exceedsOriginalDepthSpan(patch,geometry)) return withinDepthBounds(patch,geometry);
		for (double[] corner : patch.corner) if (corner[2] > CORNER_TOLERANCE_METERS) return false;
		return true;
	}

	private static boolean exceedsOriginalDepthSpan(Patch patch,GriddedSurfaceGeometry geometry) {
		double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
		for (double[] corner : patch.corner) { low=Math.min(low,corner[2]); high=Math.max(high,corner[2]); }
		return high-low > (geometry.maxDepth-geometry.minDepth)*1000+CORNER_TOLERANCE_METERS;
	}

	private static boolean withinDepthBounds(Patch patch,GriddedSurfaceGeometry geometry) {
		double shallow=Math.max(0,geometry.minDepth)*1000, deep=geometry.maxDepth*1000;
		for (double[] corner : patch.corner)
			if (-corner[2] < shallow-CORNER_TOLERANCE_METERS || -corner[2] > deep+CORNER_TOLERANCE_METERS) return false;
		return true;
	}

}
