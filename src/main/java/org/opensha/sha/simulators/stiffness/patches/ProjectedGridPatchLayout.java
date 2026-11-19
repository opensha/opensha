package org.opensha.sha.simulators.stiffness.patches;

import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;

/**
 * Regular reference rows with persistent logical grid identities. Side endpoints
 * are fitted evenly at row center lines. Global extent constraints spread over whole
 * runs, preserving interior overlap and neutral spillover at sloping sides.
 */
final class ProjectedGridPatchLayout {
	final List<Node> nodes=new ArrayList<>();
	final double maxBoundaryCorrection;
	private final SurfaceReferenceFrame frame;
	private final double spacing;

	ProjectedGridPatchLayout(SurfaceReferenceFrame frame,double spacing,PatchAlignment alignment) {
		this.frame=frame;
		this.spacing=spacing;
		double[] xs=PatchIntervalPlacement.centers(frame.maxX(),spacing,alignment);
		double[] ys=PatchIntervalPlacement.centers(frame.maxY(),spacing,alignment);
		for (int r=0; r<ys.length; r++) {
			if (alignment == PatchAlignment.CENTER) {
				for (int c=0; c<xs.length; c++) if (frame.contains(xs[c],ys[r])) nodes.add(new Node(r,c,xs[c],ys[r]));
			} else {
				// Use center-line intersections for neutral side boundaries. An
				// intersecting sliver alone no longer creates an extra whole source.
				int previousEnd=-1,segment=0;
				for (double[] interval : frame.intervals(ys[r],true)) {
					double[] centers=PatchIntervalPlacement.centers(interval[1]-interval[0],spacing,alignment);
					int first=Math.max(previousEnd+1,Math.min(xs.length-centers.length,(int)Math.round(interval[0]/spacing)));
					for (int i=0; i<centers.length; i++) {
						Node node=new Node(r,first+i,interval[0]+centers[i],ys[r],segment);
						nodes.add(node);
					}
					previousEnd=first+centers.length-1;
					segment++;
				}
			}
		}

		double correction=0;
		Area centers=frame.centerDomain(spacing);
		if (alignment == PatchAlignment.FILL_OVERLAP) {
			// Side fits are anchored to the original row center lines, so
			// constraint movements cannot drag their boundary targets.
			List<List<Node>> rows=runs(true), columns=runs(false);
			fitRows(rows);
			// Keep the shared down-dip row positions unless a hard depth or
			// center-containment constraint requires movement. Refitting every
			// column to its local length would bend rows around side cutouts and
			// create gaps between otherwise touching horizontal patch bands.
			// Spread endpoint extent constraints over whole runs. These are
			// feasibility corrections, not new boundary fits that can cause drift.
			for (int pass=0; pass<30; pass++) {
				double movement=Math.max(constrain(rows,true,centers),constrain(columns,false,centers));
				if (movement < spacing*1e-8) break;
			}
			for (Node node : nodes) {
				double[] inside=frame.inside(node.targetX,node.targetY,centers);
				correction=Math.max(correction,Math.hypot(inside[0]-node.targetX,inside[1]-node.targetY));
				node.x=inside[0]; node.y=inside[1];
			}
		} else {
			for (Node node : nodes) {
				double[] inside=frame.inside(node.x,node.y,centers);
				node.x=inside[0]; node.y=inside[1];
			}
		}
		maxBoundaryCorrection=correction;
	}

	private List<List<Node>> runs(boolean horizontal) {
		TreeMap<Integer,List<Node>> lines=new TreeMap<>();
		for (Node node : nodes) lines.computeIfAbsent(horizontal ? node.row : node.col,k -> new ArrayList<>()).add(node);
		List<List<Node>> runs=new ArrayList<>();
		for (List<Node> line : lines.values()) {
			line.sort(Comparator.comparingInt(n -> horizontal ? n.col : n.row));
			List<Node> run=null;
			int previous=-2,previousSegment=-1;
			for (Node node : line) {
				int index=horizontal ? node.col : node.row;
				if (index != previous+1 || horizontal && node.segment != previousSegment) { run=new ArrayList<>(); runs.add(run); }
				run.add(node);
				previous=index; previousSegment=node.segment;
			}
		}
		return runs;
	}

	private void fitRows(List<List<Node>> runs) {
		for (List<Node> run : runs) {
			Node first=run.get(0), last=run.get(run.size()-1);
			double transverse=first.y;
			double anchor=(first.x+last.x)*0.5;
			double[] interval=nearestInterval(transverse,anchor);
			double low=interval[0],high=interval[1];
			double start=low+spacing*0.5, end=high-spacing*0.5;
			for (int i=0; i<run.size(); i++) {
				Node node=run.get(i);
				double target;
				if (run.size() == 1) target=(low+high)*0.5;
				else if (end-start < spacing*1e-6)
					// Narrow slivers retain their cells and accept heavy overlap;
					// spread centers rather than collapsing all onto one midpoint.
					target=low+(high-low)*(i+0.5)/run.size();
				else target=start+(end-start)*i/(run.size()-1);
				node.targetX=target;
			}
		}
	}

	private double[] nearestInterval(double transverse,double anchor) {
		double best=Double.POSITIVE_INFINITY;
		double[] interval={anchor,anchor};
		for (double[] candidate : frame.intervals(transverse,true)) {
			double distance=Math.max(candidate[0]-anchor,Math.max(0,anchor-candidate[1]));
			if (distance < best) { best=distance; interval=candidate; }
		}
		return interval;
	}

	private double constrain(List<List<Node>> runs,boolean horizontal,Area centers) {
		double movement=0;
		for (List<Node> run : runs) {
			Node first=run.get(0),last=run.get(run.size()-1);
			double[] a=frame.inside(first.targetX,first.targetY,centers);
			double[] b=frame.inside(last.targetX,last.targetY,centers);
			double da=horizontal ? a[0]-first.targetX : a[1]-first.targetY;
			double db=horizontal ? b[0]-last.targetX : b[1]-last.targetY;
			for (int i=0; i<run.size(); i++) {
				double t=run.size() == 1 ? 0.5 : (double)i/(run.size()-1);
				double delta=(1-t)*da+t*db;
				if (horizontal) run.get(i).targetX+=delta; else run.get(i).targetY+=delta;
				movement=Math.max(movement,Math.abs(delta));
			}
		}
		return movement;
	}

	static final class Node {
		final int row,col,segment;
		double x,y,targetX,targetY;
		Node(int row,int col,double x,double y) { this(row,col,x,y,0); }
		Node(int row,int col,double x,double y,int segment) {
			this.row=row; this.col=col; this.segment=segment;
			this.x=x; this.y=y; this.targetX=x; this.targetY=y;
		}
	}
}
