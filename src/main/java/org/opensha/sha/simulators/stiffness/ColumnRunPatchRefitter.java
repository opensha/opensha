package org.opensha.sha.simulators.stiffness;

import java.awt.BasicStroke;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.GenericSubSectPatchBuilder.PatchFactory;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;

/**
 * One pass over coherent bands of neighboring physical columns, before thin-strip
 * repair. Both total covered area and protected-interior covered area are measured
 * with the actual tangent rectangles projected into the common reference plane.
 * The interior test is an area comparison, not a pointwise coverage guarantee.
 */
final class ColumnRunPatchRefitter {
	// Areas are km²: require >10 m² improvement and tolerate at most 1 m²
	// interior loss to avoid accepting/rejecting coordinate-rounding slivers.
	private static final double MIN_COVERAGE_GAIN_KM2 = 1e-5;
	private static final double INTERIOR_AREA_TOLERANCE_KM2 = 1e-6;

	static void refit(SurfaceReferenceFrame frame, double spacing, List<PatchLocation> patches,
			List<int[]> indices, PatchFactory factory) {
		List<Column> columns=proposeColumns(frame, spacing, patches, indices, factory);
		if (columns.isEmpty()) return;

		Path2D.Double outline=frame.outlineCopy();
		Area surface=new Area(outline), interior=new Area(surface);
		interior.subtract(new Area(new BasicStroke((float)(spacing*Math.sqrt(2))).createStrokedShape(outline)));
		List<Area> footprints=new ArrayList<>();
		for (PatchLocation patch : patches) footprints.add(new ProjectedPatchFootprint(frame, patch).area());
		boolean[] replaced=new boolean[patches.size()];
		List<PatchLocation> additions=new ArrayList<>();
		List<int[]> newIndices=new ArrayList<>();
		// Leave an ID gap so diagnostics do not connect retained and refitted rows.
		int nextColumn=indices.stream().mapToInt(i -> i[1]).max().getAsInt()+2;
		for (int start=0; start<columns.size();) {
			int end=start+1, count=columns.get(start).proposedPatches.size();
			while (end<columns.size() && columns.get(end).station <= columns.get(end-1).station+1
					&& columns.get(end).proposedPatches.size() == count) end++;
			// Reflow broad bands with an interior row and adjacent columns.
			// One/two-row ends retain the original fits; strip repair handles thin runs.
			if (end-start >= 2 && count >= 3) {
				List<Integer> originalIndices=new ArrayList<>();
				List<PatchLocation> proposedPatches=new ArrayList<>();
				for (int j=start; j<end; j++) {
					originalIndices.addAll(columns.get(j).originalIndices);
					proposedPatches.addAll(columns.get(j).proposedPatches);
				}
				if (improvesCoverage(frame, originalIndices, proposedPatches, footprints, surface, interior)) {
					// Keep stable indices into original sources until all bands have
					// been considered. Empty footprints remove accepted old sources;
					// appended footprints let later bands see accepted replacements.
					for (int i : originalIndices) {
						replaced[i]=true;
						footprints.set(i, new Area());
					}
					for (int j=start; j<end; j++) {
						int row=0;
						for (PatchLocation patch : columns.get(j).proposedPatches) {
							additions.add(patch);
							newIndices.add(new int[] {row++, nextColumn, 0});
							footprints.add(new ProjectedPatchFootprint(frame, patch).area());
						}
						nextColumn++;
					}
				}
			}
			nextColumn++; // separate diagnostic identities of independently fitted bands
			start=end;
		}
		for (int i=replaced.length-1; i>=0; i--) if (replaced[i]) {
			patches.remove(i);
			indices.remove(i);
		}
		patches.addAll(additions); indices.addAll(newIndices);
	}

	/**
	 * Associate sources with physical columns, then propose equal down-dip spacing.
	 * Reference-grid column IDs alone are unsuitable: row fits can shift their phase.
	 * Stations follow the physical middle trace; the projection itself is unchanged.
	 */
	private static List<Column> proposeColumns(SurfaceReferenceFrame frame, double spacing,
			List<PatchLocation> patches, List<int[]> indices, PatchFactory factory) {
		GriddedSurfaceGeometry geometry=frame.geometry;
		double[] middleDistances=new double[geometry.cols];
		for (int c=1; c<geometry.cols; c++)
			middleDistances[c]=middleDistances[c-1]+geometry.point((geometry.rows-1)*0.5, c).distance(geometry.point((geometry.rows-1)*0.5, c-1));
		double[] stations=PatchIntervalPlacement.centers(middleDistances[geometry.cols-1], spacing, PatchAlignment.FILL_OVERLAP);
		TreeMap<Integer, List<Integer>> groups=new TreeMap<>();
		double[] sourceColumns=new double[patches.size()];
		for (int i=0; i<patches.size(); i++) {
			double[] p=frame.project(new Vector3D(patches.get(i).patch.center).scalarMultiply(0.001));
			if (!frame.contains(p[0], p[1])) return java.util.Collections.emptyList(); // retain exceptional off-surface sources
			double col=frame.indices(p[0], p[1])[1];
			sourceColumns[i]=col;
			int c=Math.min(geometry.cols-2, (int)col);
			double d=middleDistances[c]+(middleDistances[c+1]-middleDistances[c])*(col-c);
			// Strict comparison resolves equidistant ties toward the earlier station.
			int nearest=0;
			for (int j=1; j<stations.length; j++) if (Math.abs(d-stations[j]) < Math.abs(d-stations[nearest])) nearest=j;
			groups.computeIfAbsent(nearest, k -> new ArrayList<>()).add(i);
		}
		List<Column> columns=new ArrayList<>();
		for (var entry : groups.entrySet()) {
			// Row fitting can put two legitimate neighboring columns in the same
			// station bin. Duplication in at least two rows indicates persistent
			// neighbors; a lone duplicate can be a displaced boundary patch.
			TreeMap<Integer, Integer> rowCounts=new TreeMap<>();
			for (int i : entry.getValue()) rowCounts.merge(indices.get(i)[0], 1, Integer::sum);
			TreeMap<Integer, List<Integer>> subgroups=new TreeMap<>();
			boolean split=rowCounts.values().stream().filter(n -> n > 1).count() >= 2;
			for (int i : entry.getValue()) subgroups.computeIfAbsent(split ? indices.get(i)[1] : 0, k -> new ArrayList<>()).add(i);
			for (List<Integer> originalIndices : subgroups.values()) {
				double[] columnCoordinates=originalIndices.stream().mapToDouble(i -> sourceColumns[i]).sorted().toArray();
				// Use the upper median (an existing coordinate), not the mean:
				// averaging can move a boundary column away from its fitted side.
				double col=columnCoordinates[columnCoordinates.length/2];
				List<PatchLocation> proposedPatches=fitColumn(frame, spacing, col, factory);
				columns.add(new Column(entry.getKey(), originalIndices, proposedPatches));
			}
		}

		return columns;
	}

	/**
	 * Integrate width perpendicular to the local strike through every supplied row.
	 * Using raw edge length would mistake along-strike shear for extra down-dip width.
	 * A null factory result rejects the whole proposed column, preserving its count.
	 */
	private static List<PatchLocation> fitColumn(SurfaceReferenceFrame frame, double spacing,
			double col, PatchFactory factory) {
		GriddedSurfaceGeometry geometry=frame.geometry;
		double[] widths=new double[geometry.rows];
		for (int r=1; r<geometry.rows; r++) {
			Vector3D delta=geometry.point(r, col).subtract(geometry.point(r-1, col));
			double strike=Math.toRadians(geometry.interpolate(r-0.5, col).strike);
			double along=delta.dotProduct(new Vector3D(Math.sin(strike), Math.cos(strike), 0));
			// A skewed grid edge includes along-strike motion, which adds no width.
			widths[r]=widths[r-1]+Math.sqrt(Math.max(0, delta.getNormSq()-along*along));
		}
		List<PatchLocation> proposedPatches=new ArrayList<>();
		if (widths[geometry.rows-1] >= spacing) {
			for (double d : PatchIntervalPlacement.centers(widths[geometry.rows-1], spacing, PatchAlignment.FILL_OVERLAP)) {
				int r=0;
				while (r<geometry.rows-2 && widths[r+1] < d) r++;
				double row=r+(d-widths[r])/(widths[r+1]-widths[r]);
				double[] p=frame.project(geometry.point(row, col));
				PatchLocation patch=factory.build(p[0], p[1]);
				if (patch == null) { proposedPatches.clear(); break; }
				proposedPatches.add(patch);
			}
		}
		return proposedPatches;
	}

	/** Neighbor footprints matter: moving a band can uncover area its neighbors already cover. */
	private static boolean improvesCoverage(SurfaceReferenceFrame frame, List<Integer> originalIndices,
			List<PatchLocation> proposedPatches, List<Area> footprints, Area surface, Area interior) {
		Area before=new Area(), after=new Area();
		for (int i : originalIndices) before.add(footprints.get(i));
		for (PatchLocation patch : proposedPatches) after.add(new ProjectedPatchFootprint(frame, patch).area());
		Rectangle2D bounds=before.getBounds2D().createUnion(after.getBounds2D());
		for (int i=0; i<footprints.size(); i++) if (!originalIndices.contains(i) && footprints.get(i).getBounds2D().intersects(bounds)) {
			before.add(footprints.get(i));
			after.add(footprints.get(i));
		}
		Area oldInterior=new Area(before), newInterior=new Area(after);
		oldInterior.intersect(interior); newInterior.intersect(interior);
		before.intersect(surface); after.intersect(surface);
		// Require a net coverage gain and no increase in uncovered interior.
		// Square-meter tolerances suppress coordinate-rounding slivers.
		return area(after)-area(before) > MIN_COVERAGE_GAIN_KM2
				&& area(newInterior) >= area(oldInterior)-INTERIOR_AREA_TOLERANCE_KM2;
	}

	private static final class Column {
		final int station;
		final List<Integer> originalIndices;
		final List<PatchLocation> proposedPatches;
		Column(int station, List<Integer> originalIndices, List<PatchLocation> proposedPatches) {
			this.station=station;
			this.originalIndices=originalIndices;
			this.proposedPatches=proposedPatches;
		}
	}
	/** Shoelace sum of linear contours; opposite winding subtracts holes. */
	private static double area(Area area) {
		PathIterator iterator=area.getPathIterator(null);
		double[] p=new double[6]; double sum=0, x=0, y=0, firstX=0, firstY=0;
		while (!iterator.isDone()) {
			int type=iterator.currentSegment(p);
			if (type == PathIterator.SEG_MOVETO) { x=firstX=p[0]; y=firstY=p[1]; }
			else {
				double xx=type == PathIterator.SEG_CLOSE ? firstX : p[0], yy=type == PathIterator.SEG_CLOSE ? firstY : p[1];
				sum+=x*yy-y*xx; x=xx; y=yy;
			}
			iterator.next();
		}
		return Math.abs(sum)*0.5;
	}
}
