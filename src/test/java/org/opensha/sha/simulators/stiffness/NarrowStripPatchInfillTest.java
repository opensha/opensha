package org.opensha.sha.simulators.stiffness;

import static org.junit.Assert.*;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.junit.Test;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;

public class NarrowStripPatchInfillTest {
	private static GenericSubSectPatchBuilder.Layout fill(org.opensha.sha.faultSurface.EvenlyGriddedSurface surface) {
		return new GenericSubSectPatchBuilder(2,11,'S').buildLayout(surface,90,PatchAlignment.FILL_OVERLAP);
	}

	private static boolean covered(GenericSubSectPatchBuilder.Layout layout,double x,double y) {
		for (var location : layout.patches) {
			Path2D.Double polygon=new Path2D.Double();
			double[][] corners=new double[4][];
			for (int i=0; i<4; i++) {
				double[] p=layout.frame.project(new Vector3D(location.patch.corner[i]).scalarMultiply(0.001));
				corners[i]=p;
				if (i == 0) polygon.moveTo(p[0],p[1]); else polygon.lineTo(p[0],p[1]);
			}
			polygon.closePath();
			if (polygon.contains(x,y)) return true;
			for (int i=0; i<4; i++) {
				double[] a=corners[i],b=corners[(i+1)%4];
				if (Line2D.ptSegDist(a[0],a[1],b[0],b[1],x,y) < 1e-6) return true;
			}
		}
		return false;
	}

	@Test public void thinTongueGetsAContinuousLineOfPatches() {
		var surface=PathologicalSurfaceFixtures.thinTongue();
		var layout=fill(surface);
		assertEquals(4,layout.infillCount);
		for (double x=11; x<=17.5; x+=0.25) {
			var intervals=layout.frame.intervals(x,false);
			assertEquals(1,intervals.size());
			double[] interval=intervals.get(0);
			for (double fraction : new double[] {0.1,0.5,0.9})
				assertTrue("Uncovered tongue at x="+x,covered(layout,x,interval[0]+fraction*(interval[1]-interval[0])));
		}
		var centered=new GenericSubSectPatchBuilder(2,11,'S').buildLayout(surface,90,PatchAlignment.CENTER);
		assertEquals(0,centered.infillCount);
	}

	@Test public void bothNarrowNecksAreRepresented() {
		var layout=fill(PathologicalSurfaceFixtures.narrowNecks());
		assertEquals(4,layout.infillCount);
		for (double x : new double[] {5,5.5,6,6.5,7,14.7,15,15.5,16,16.5}) {
			double[] interval=layout.frame.intervals(x,false).get(0);
			for (double fraction : new double[] {0.1,0.5,0.9})
				assertTrue("Uncovered neck at x="+x,covered(layout,x,interval[0]+fraction*(interval[1]-interval[0])));
		}
		for (int i=layout.patches.size()-layout.infillCount; i<layout.patches.size(); i++) {
			double[] xy=layout.referenceCenters.get(i);
			boolean narrow=false;
			for (double[] interval : layout.frame.intervals(xy[0],false)) {
				if (xy[1] >= interval[0] && xy[1] <= interval[1])
					narrow=layout.frame.point(xy[0],interval[0]).distance(layout.frame.point(xy[0],interval[1])) < 2;
			}
			assertTrue("Infill moved into a wide lobe",narrow);
		}

		var coverage=new ProjectedPatchCoverage(layout,2);
		assertTrue(coverage.uncoveredArea/coverage.surfaceArea < 0.06);
	}

	@Test public void isolatedShortSliverDoesNotQualify() {
		var surface=PathologicalSurfaceFixtures.sample(9,15,(u,v) -> {
			double width=u < 0.8 ? 8 : u >= 0.9 ? 0.4 : 8+(0.4-8)*(u-0.8)/0.1;
			double down=4+width*(v-0.5);
			return new double[] {down*Math.cos(Math.PI/4),14*u,5+down*Math.sin(Math.PI/4)};
		});
		var layout=fill(surface);
		assertEquals(0,layout.infillCount);
		// The lone narrow column is not enough to identify a persistent strip.
		double[] interval=layout.frame.intervals(13,false).get(0);
		assertFalse(covered(layout,13,(interval[0]+interval[1])*0.5));
	}

	@Test public void representedNarrowRunNeedsNoExtraPatches() {
		var layout=fill(PathologicalSurfaceFixtures.planar(8,1,45,5));
		assertEquals(0,layout.infillCount);
		assertEquals(4,layout.patches.size());
	}

	@Test public void broadShapesAndTriangleNeedNoInfill() {
		for (var surface : java.util.List.of(PathologicalSurfaceFixtures.planar(12,8,45,5),GenericSubSectPatchBuilderTest.skewedSurface(),
				GenericSubSectPatchBuilderTest.surface(12,new double[] {0,2,4,6,8},new double[] {45,45,45,45},5),
				PathologicalSurfaceFixtures.twisted(),PathologicalSurfaceFixtures.unevenTraces(),
				GenericSubSectPatchBuilderTest.surface(12,new double[] {8,8,8,8,8},new double[] {80,60,40,20},5))) {
			var layout=fill(surface);
			assertEquals(0,layout.infillCount);
		}
	}
}
