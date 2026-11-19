package org.opensha.sha.simulators.stiffness.patches;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

/** Area-weighted sampling of physical rectangle footprints in the reference plane. */
final class ProjectedPatchCoverage {
	final double surfaceArea, uncoveredArea, interiorUncoveredArea, maximumGapDistance;

	ProjectedPatchCoverage(GenericSubSectPatchBuilder.Layout layout,double spacing) {
		this(layout,spacing,32);
	}

	ProjectedPatchCoverage(GenericSubSectPatchBuilder.Layout layout,double spacing,int samples) {
		List<Path2D.Double> footprints=new ArrayList<>();
		List<double[][]> corners=new ArrayList<>();
		for (var location : layout.patches) {
			// Independent fixed-step edge sampling checks the curved footprint
			// without reusing production's adaptive subdivision or containment.
			int edgeSamples=layout.frame instanceof RibbonSurfaceReferenceFrame ? 32 : 1;
			double[][] xy=new double[4*edgeSamples][];
			Path2D.Double footprint=new Path2D.Double();
			for (int edge=0; edge<4; edge++) for (int j=0; j<edgeSamples; j++) {
				int i=edge*edgeSamples+j;
				double t=(double)j/edgeSamples;
				Vector3D a=new Vector3D(location.patch.corner[edge]),b=new Vector3D(location.patch.corner[(edge+1)%4]);
				xy[i]=layout.frame.project(new Vector3D(1-t,a,t,b).scalarMultiply(0.001));
				if (i == 0) footprint.moveTo(xy[i][0],xy[i][1]); else footprint.lineTo(xy[i][0],xy[i][1]);
			}
			footprint.closePath(); footprints.add(footprint); corners.add(xy);
		}
		GriddedSurfaceGeometry geometry=layout.frame.geometry();
		double total=0,missed=0,interior=0,maxGap=0;
		for (int r=0; r<geometry.rows-1; r++) for (int c=0; c<geometry.cols-1; c++) {
			double weight=geometry.cellArea(r,c)/(samples*samples);
			for (int a=0; a<samples; a++) for (int b=0; b<samples; b++) {
				total+=weight;
				double[] xy=layout.frame.project(geometry.point(r+(a+0.5)/samples,c+(b+0.5)/samples));
				boolean covered=false;
				for (var footprint : footprints) if (footprint.contains(xy[0],xy[1])) { covered=true; break; }
				if (covered) continue;
				double gap=Double.POSITIVE_INFINITY;
				for (double[][] rect : corners) for (int i=0; i<rect.length; i++) {
					double[] p=rect[i],q=rect[(i+1)%rect.length];
					gap=Math.min(gap,Line2D.ptSegDist(p[0],p[1],q[0],q[1],xy[0],xy[1]));
				}
				if (gap < 1e-6) continue; // numerical contacts are not physical gaps
				missed+=weight;
				maxGap=Math.max(maxGap,gap);
				if (layout.frame.distanceToBoundary(xy[0],xy[1]) > spacing/Math.sqrt(2d)) interior+=weight;
			}
		}
		surfaceArea=total; uncoveredArea=missed; interiorUncoveredArea=interior; maximumGapDistance=maxGap;
	}
}
