package org.opensha.sha.simulators.stiffness;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.simulators.stiffness.GriddedSurfaceGeometry.SurfacePoint;
import org.opensha.sha.simulators.stiffness.StiffnessCalc.Patch;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;
import com.google.common.base.Preconditions;

/** Shared-grid layout in an area-weighted representative plane. */
final class GenericSubSectPatchBuilder implements SubSectPatchBuilder {
	/** Increment when geometry or layout conventions change; included in persistent cache keys. */
	static final int VERSION=6;
	private final double spacing;
	private final int zone;
	private final char letter;
	private final boolean aseis;

	GenericSubSectPatchBuilder(double spacing, int zone, char letter, boolean aseis) {
		this.spacing=spacing;
		this.zone=zone;
		this.letter=letter;
		this.aseis=aseis;
	}

	@Override
	public List<PatchLocation> buildPatches(FaultSection section, PatchAlignment alignment) {
		RuptureSurface surface=section.getFaultSurface(spacing/10d, false, aseis);
		Preconditions.checkArgument(surface instanceof EvenlyGriddedSurface,
				"GENERIC requires EvenlyGriddedSurface; section %s supplies %s",
				section.getSectionName(),surface.getClass().getName());
		try {
			return buildPatches((EvenlyGriddedSurface)surface, section.getAveRake(), alignment);
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
		List<double[]> referenceCenters=new ArrayList<>();
		List<int[]> gridIndices=new ArrayList<>();
		for (ProjectedGridPatchLayout.Node node : grid.nodes) {
			double x=node.x, y=node.y;
			SurfacePoint sample=frame.sample(x,y);
			Patch patch=patch(sample,rake);
			boolean fitted=false;
			for (int attempt=0; attempt<30; attempt++) {
				double[] extent=alongStrikeExtent(patch,frame);
				if (extent[1]-extent[0] <= frame.maxX-frame.minX+1e-9
						&& (extent[0] < frame.minX-1e-9 || extent[1] > frame.maxX+1e-9)) {
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
						sample=new SurfacePoint(sample.center.add(new Vector3D(0,0,-(highest+1e-6)/1000d)),sample.strike,sample.dip);
						patch=patch(sample,rake);
					}
				} else if (!withinDepthBounds(patch,geometry)) {
					// The reference-plane constraint is approximate for curved faults.
					// Find a feasible physical rectangle, then approach it from the
					// current center while retaining a feasible endpoint in bisection.
					double feasible=Double.NaN, nearest=Double.POSITIVE_INFINITY;
					for (double[] interval : frame.intervals(x,false)) {
						int steps=Math.max(1,(int)Math.ceil((interval[1]-interval[0])/(spacing/20d)));
						for (int step=0; step<=steps; step++) {
							double trial=interval[0]+(interval[1]-interval[0])*step/steps;
							if (Math.abs(trial-y) < nearest && withinDepthBounds(patch(frame.sample(x,trial),rake),geometry)) {
								feasible=trial; nearest=Math.abs(trial-y);
							}
						}
					}
					Preconditions.checkState(Double.isFinite(feasible),
							"Patch at reference (%s,%s) cannot fit within original depth bounds [%s,%s] km",
							x,y,geometry.minDepth,geometry.maxDepth);
					double outside=y;
					for (int i=0; i<40; i++) {
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
			Preconditions.checkState(fitted,"Cannot fit patch at reference (%s,%s) within joint global bounds",x,y);

			Location[] corners=new Location[4];
			int[] indices={3,2,1,0};
			for (int i=0; i<4; i++)
				corners[i]=geometry.location(new Vector3D(patch.corner[indices[i]]).scalarMultiply(0.001));
			patches.add(new PatchLocation(patch,geometry.location(sample.center),corners));
			referenceCenters.add(frame.project(new Vector3D(patch.center).scalarMultiply(0.001)));
			gridIndices.add(new int[] {node.row,node.col,node.segment});
		}
		Preconditions.checkState(!patches.isEmpty(),"Surface produced no patches");
		return new Layout(frame,patches,referenceCenters,gridIndices,grid.maxBoundaryCorrection);
	}

	/** Diagnostics preserve row/column identities as well as 2D/3D correspondence. */
	static final class Layout {
		final SurfaceReferenceFrame frame;
		final List<PatchLocation> patches;
		final List<double[]> referenceCenters;
		final List<int[]> gridIndices;
		final double maxBoundaryCorrection;
		Layout(SurfaceReferenceFrame frame,List<PatchLocation> patches,List<double[]> referenceCenters,
				List<int[]> gridIndices,double correction) {
			this.frame=frame;
			this.patches=java.util.Collections.unmodifiableList(patches);
			this.referenceCenters=java.util.Collections.unmodifiableList(referenceCenters);
			this.gridIndices=java.util.Collections.unmodifiableList(gridIndices);
			this.maxBoundaryCorrection=correction;
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
		return extent[1]-extent[0] > frame.maxX-frame.minX+1e-9
				|| extent[0] >= frame.minX-1e-9 && extent[1] <= frame.maxX+1e-9;
	}

	private static boolean withinEffectiveDepthBounds(Patch patch,GriddedSurfaceGeometry geometry) {
		if (!exceedsOriginalDepthSpan(patch,geometry)) return withinDepthBounds(patch,geometry);
		for (double[] corner : patch.corner) if (corner[2] > 1e-6) return false;
		return true;
	}

	private static boolean exceedsOriginalDepthSpan(Patch patch,GriddedSurfaceGeometry geometry) {
		double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
		for (double[] corner : patch.corner) { low=Math.min(low,corner[2]); high=Math.max(high,corner[2]); }
		return high-low > (geometry.maxDepth-geometry.minDepth)*1000+1e-6;
	}

	private static boolean withinDepthBounds(Patch patch,GriddedSurfaceGeometry geometry) {
		double shallow=Math.max(0,geometry.minDepth)*1000, deep=geometry.maxDepth*1000;
		for (double[] corner : patch.corner)
			if (-corner[2] < shallow-1e-6 || -corner[2] > deep+1e-6) return false;
		return true;
	}

}
