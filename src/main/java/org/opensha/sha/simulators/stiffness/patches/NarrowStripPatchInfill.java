package org.opensha.sha.simulators.stiffness.patches;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;
import org.opensha.sha.simulators.stiffness.patches.GenericSubSectPatchBuilder.PatchFactory;

/**
 * Conservative FILL_OVERLAP repair for persistent narrow strips only. An interval
 * must be narrower than a patch and overlap a narrow interval in an adjacent
 * sampled column. Add one missing midpoint per column; leave existing patches fixed.
 */
final class NarrowStripPatchInfill {
	private static final double COUNT_SNAP_TOLERANCE = 1e-5;
	private static final double MIN_INTERVAL_OVERLAP_KM = 1e-7;

	static List<PatchLocation> build(SurfaceReferenceFrame frame, double spacing,
			List<PatchLocation> existing, PatchFactory factory) {
		List<Candidate> candidates=new ArrayList<>();
		List<Candidate> previous=java.util.Collections.emptyList();
		for (double x : PatchIntervalPlacement.centers(frame.maxX()-frame.minX(), spacing, PatchAlignment.FILL_OVERLAP)) {
			x+=frame.minX();
			List<Candidate> current=new ArrayList<>();
			for (double[] interval : frame.intervals(x, false)) {
				// Projection cannot lengthen a path, so broad intervals need no
				// physical sampling. The tolerance excludes nominal exact fits.
				if (interval[1]-interval[0] >= spacing*(1-COUNT_SNAP_TOLERANCE)) continue;
				ColumnPath path=new ColumnPath(frame, x, interval, spacing);
				if (path.length() >= spacing*(1-COUNT_SNAP_TOLERANCE)) continue;
				Candidate candidate=new Candidate(x, path.yAt(path.length()*0.5), interval[0], interval[1]);
				for (Candidate neighbor : previous) {
					if (Math.min(candidate.high, neighbor.high)-Math.max(candidate.low, neighbor.low) > MIN_INTERVAL_OVERLAP_KM) {
						candidate.supported=true;
						neighbor.supported=true;
					}
				}
				current.add(candidate);
			}
			candidates.addAll(current);
			previous=current; // a broad/empty column interrupts the narrow run
		}

		Coverage coverage=new Coverage(frame, spacing);
		for (PatchLocation location : existing) coverage.add(location);
		List<PatchLocation> additions=new ArrayList<>();
		for (Candidate candidate : candidates) {
			if (!candidate.supported || coverage.contains(candidate.x, candidate.y)) continue;
			PatchLocation location=factory.build(candidate.x, candidate.y);
			if (location == null || !new ProjectedPatchFootprint(frame, location).contains(candidate.x, candidate.y)) continue;
			additions.add(location);
			coverage.add(location);
		}
		return additions;
	}

	private static final class Candidate {
		final double x, y, low, high;
		boolean supported;
		Candidate(double x, double y, double low, double high) {
			this.x=x; this.y=y; this.low=low; this.high=high;
		}
	}

	/** Arc-length lookup sampled at a tenth of the target patch spacing. */
	private static final class ColumnPath {
		final double[] y, distances;
		ColumnPath(SurfaceReferenceFrame frame, double x, double[] interval, double spacing) {
			int steps=Math.max(1, (int)Math.ceil((interval[1]-interval[0])/(spacing/10)));
			y=new double[steps+1]; distances=new double[steps+1];
			Vector3D previous=null;
			for (int i=0; i<=steps; i++) {
				y[i]=interval[0]+(interval[1]-interval[0])*i/steps;
				Vector3D point=frame.point(x, y[i]);
				if (previous != null) distances[i]=distances[i-1]+point.distance(previous);
				previous=point;
			}
		}
		double length() { return distances[distances.length-1]; }
		double yAt(double distance) {
			int low=0, high=y.length-1;
			while (high-low > 1) {
				int mid=(low+high)/2;
				if (distances[mid] <= distance) low=mid; else high=mid;
			}
			double fraction=(distance-distances[low])/(distances[high]-distances[low]);
			return y[low]+fraction*(y[high]-y[low]);
		}
	}

	/** Reference-plane buckets avoid scanning every patch for every sample. */
	private static final class Coverage {
		final SurfaceReferenceFrame frame;
		final double size;
		final Map<Long, List<ProjectedPatchFootprint>> buckets=new HashMap<>();
		Coverage(SurfaceReferenceFrame frame, double spacing) { this.frame=frame; size=spacing; }
		void add(PatchLocation location) {
			ProjectedPatchFootprint footprint=new ProjectedPatchFootprint(frame, location);
			for (int x=bucket(footprint.minX-1e-6); x<=bucket(footprint.maxX+1e-6); x++)
				for (int y=bucket(footprint.minY-1e-6); y<=bucket(footprint.maxY+1e-6); y++)
					buckets.computeIfAbsent(key(x, y), k -> new ArrayList<>()).add(footprint);
		}
		boolean contains(double x, double y) {
			List<ProjectedPatchFootprint> nearby=buckets.get(key(bucket(x), bucket(y)));
			if (nearby != null) for (ProjectedPatchFootprint footprint : nearby) if (footprint.contains(x, y)) return true;
			return false;
		}
		int bucket(double value) { return (int)Math.floor(value/size); }
		static long key(int x, int y) { return ((long)x << 32) ^ (y & 0xffffffffL); }
	}
}
