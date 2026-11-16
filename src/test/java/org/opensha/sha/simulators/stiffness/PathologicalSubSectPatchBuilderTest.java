package org.opensha.sha.simulators.stiffness;

import static org.junit.Assert.*;

import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;

public class PathologicalSubSectPatchBuilderTest {
	private static final double SPACING=2;

	private static GenericSubSectPatchBuilder.Layout layout(EvenlyGriddedSurface surface,PatchAlignment alignment) {
		return new GenericSubSectPatchBuilder(SPACING,11,'S').buildLayout(surface,90,alignment);
	}

	/** Bounds, physical metadata, unit vectors, and a nonsingular remote interaction. */
	private static void checkPhysicalPatches(GenericSubSectPatchBuilder.Layout layout) {
		assertFalse(layout.patches.isEmpty());
		for (PatchLocation location : layout.patches) {
			var patch=location.patch;
			for (double value : patch.center) assertTrue(Double.isFinite(value));
			assertEquals(SPACING*SPACING*1e6,patch.area,0);
			assertTrue(Double.isFinite(patch.strike)); assertTrue(Double.isFinite(patch.dip));
			assertEquals(1,new Vector3D(patch.nu).getNorm(),1e-12);
			assertEquals(1,new Vector3D(patch.u).getNorm(),1e-12);
			assertEquals(0,new Vector3D(patch.nu).dotProduct(new Vector3D(patch.u)),1e-12);
			assertEquals(location.center.getDepth(),-patch.center[2]/1000,1e-12);
			double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;
			double minDepth=Double.POSITIVE_INFINITY,maxDepth=Double.NEGATIVE_INFINITY;
			for (double[] corner : patch.corner) {
				for (double value : corner) assertTrue(Double.isFinite(value));
				assertTrue(corner[2] <= 1e-6);
				double x=layout.frame.project(new Vector3D(corner).scalarMultiply(0.001))[0];
				minX=Math.min(minX,x); maxX=Math.max(maxX,x);
				minDepth=Math.min(minDepth,-corner[2]/1000); maxDepth=Math.max(maxDepth,-corner[2]/1000);
			}
			if (maxX-minX <= layout.frame.maxX-layout.frame.minX+1e-9) {
				assertTrue(minX >= layout.frame.minX-1e-8); assertTrue(maxX <= layout.frame.maxX+1e-8);
			}
			if (maxDepth-minDepth <= layout.frame.geometry.maxDepth-layout.frame.geometry.minDepth+1e-9) {
				assertTrue(minDepth >= layout.frame.geometry.minDepth-1e-8);
				assertTrue(maxDepth <= layout.frame.geometry.maxDepth+1e-8);
			}
			double[] self=StiffnessCalc.calcStiffness(30000,30000,patch,patch);
			assertNotNull(self);
			for (double value : self) assertTrue(Double.isFinite(value));
			var receiver=new StiffnessCalc.Patch(new Vector3D(patch.center).add(new Vector3D(25000,35000,-1000)).toArray(),
					patch.L,patch.W,new org.opensha.sha.earthquake.FocalMechanism(10,50,90));
			double[] stiffness=StiffnessCalc.calcStiffness(30000,30000,patch,receiver);
			assertNotNull(stiffness);
			for (double value : stiffness) assertTrue(Double.isFinite(value));
			assertTrue(Double.isFinite(StiffnessCalc.calcCoulombStress(stiffness[1],stiffness[0],0.5)));
		}
	}

	@Test public void namedShapesProduceFiniteBoundedPatches() {
		for (var entry : PathologicalSurfaceFixtures.examples().entrySet()) for (PatchAlignment alignment : PatchAlignment.values()) {
			try { checkPhysicalPatches(layout(entry.getValue(),alignment)); }
			catch (RuntimeException | AssertionError e) { throw new AssertionError(entry.getKey()+" / "+alignment,e); }
		}
	}

	@Test public void twistChangesBothStrikeAndDip() {
		var result=layout(PathologicalSurfaceFixtures.twisted(),PatchAlignment.FILL_OVERLAP);
		double minStrike=Double.POSITIVE_INFINITY,maxStrike=Double.NEGATIVE_INFINITY,minDip=minStrike,maxDip=maxStrike;
		for (PatchLocation location : result.patches) {
			double strike=Math.toDegrees(location.patch.strike);
			if (strike > 180) strike-=360;
			minStrike=Math.min(minStrike,strike); maxStrike=Math.max(maxStrike,strike);
			minDip=Math.min(minDip,Math.toDegrees(location.patch.dip)); maxDip=Math.max(maxDip,Math.toDegrees(location.patch.dip));
		}
		assertTrue(maxStrike-minStrike > 15);
		assertTrue(maxDip-minDip > 10);
	}

	@Test public void twistedColumnsReflowTogetherWithoutCrowdedExtras() {
		var result=layout(PathologicalSurfaceFixtures.twisted(),PatchAlignment.FILL_OVERLAP);
		java.util.Map<Integer,Integer> counts=new java.util.TreeMap<>();
		for (int[] index : result.gridIndices) counts.merge(index[1],1,Integer::sum);
		assertEquals(8,counts.size());
		for (int count : counts.values()) assertEquals(5,count);
		assertEquals(0,result.infillCount);
		for (int i=0; i<result.patches.size(); i++) for (int j=0; j<i; j++)
			assertTrue(new Vector3D(result.patches.get(i).patch.center)
					.distance(new Vector3D(result.patches.get(j).patch.center)) > 1000);
		var coverage=new ProjectedPatchCoverage(result,SPACING);
		assertTrue(coverage.uncoveredArea/coverage.surfaceArea < 0.04);
		assertTrue(coverage.interiorUncoveredArea/coverage.surfaceArea < 0.006);
	}

	@Test public void rightNeckLobeUsesThreeShiftedRows() {
		var result=layout(PathologicalSurfaceFixtures.narrowNecks(),PatchAlignment.FILL_OVERLAP);
		int[] counts=new int[3];
		for (double[] center : result.referenceCenters) if (center[0] > 19.2) {
			int row=(int)Math.round((center[1]-2)*0.5);
			assertTrue(row >= 0 && row < 3);
			assertEquals(2+2*row,center[1],1e-4);
			counts[row]++;
		}
		assertArrayEquals(new int[] {2,2,2},counts);
		assertEquals(4,result.infillCount);
		var coverage=new ProjectedPatchCoverage(result,SPACING);
		assertEquals(0,coverage.interiorUncoveredArea,1e-8);
		assertTrue(coverage.uncoveredArea/coverage.surfaceArea < 0.02);
	}

	@Test public void unevenTopAndBottomTracesPreservePlanarityAndRestartRows() {
		var result=layout(PathologicalSurfaceFixtures.unevenTraces(),PatchAlignment.FILL_OVERLAP);
		assertEquals(1,result.frame.minimumEdgeRatio,1e-7);
		assertEquals(0,result.frame.rmsPlaneDistance,1e-5);
		boolean restarted=false;
		for (double y=0.1; y<result.frame.maxY; y+=0.1)
			if (result.frame.intervals(y,true).size() > 1) restarted=true;
		assertTrue(restarted);
		assertTrue(java.util.Arrays.stream(result.frame.nodeY[0]).max().getAsDouble()
				-java.util.Arrays.stream(result.frame.nodeY[0]).min().getAsDouble() > 2);
		assertTrue(java.util.Arrays.stream(result.frame.nodeY[result.frame.geometry.rows-1]).max().getAsDouble()
				-java.util.Arrays.stream(result.frame.nodeY[result.frame.geometry.rows-1]).min().getAsDouble() > 1.5);
	}

	@Test public void patchCountTransitionsAndJointUndersizedLimits() {
		// Explicit expected CENTER/FILL_OVERLAP counts, including the documented
		// 10-ppm count snap and transitions outside that tolerance.
		double[][] lengths={{0.3,1,1},{1.999,1,1},{1.999999,1,1},{2,1,1},{2.000001,1,1},{2.001,1,2},{4,2,2}};
		double[][] widths={{0.4,1,1},{1.999,1,1},{2,1,1},{2.001,1,2},{4.3,2,3}};
		for (double[] along : lengths) for (double[] down : widths) for (PatchAlignment alignment : PatchAlignment.values()) {
			double length=along[0],width=down[0];
			try {
				var surface=PathologicalSurfaceFixtures.planar(length,width,90,0);
				var result=layout(surface,alignment);
				checkPhysicalPatches(result);
				int index=alignment == PatchAlignment.CENTER ? 1 : 2;
				assertEquals((int)(along[index]*down[index]),result.patches.size());
			} catch (RuntimeException | AssertionError e) {
				throw new AssertionError("dimensions "+length+" x "+width+" / "+alignment,e);
			}
		}
	}

	@Test public void horizontalAndVerticalLimitsRemainFinite() {
		for (double dip : new double[] {0,0.000001,0.01,1,89,89.99,89.999999,90})
			for (double depth : new double[] {0,5}) for (PatchAlignment alignment : PatchAlignment.values()) {
				try { checkPhysicalPatches(layout(PathologicalSurfaceFixtures.planar(9.7,6.3,dip,depth),alignment)); }
				catch (RuntimeException | AssertionError e) { throw new AssertionError("dip "+dip+", depth "+depth+" / "+alignment,e); }
			}
	}

	@Test public void verySmallDipsRetainTheirMagnitude() {
		for (double dip : new double[] {0.00000001,0.000001,0.01}) {
			var surface=PathologicalSurfaceFixtures.planar(9.7,6.3,dip,5);
			var geometry=new GriddedSurfaceGeometry(surface,11,'S');
			assertEquals(dip,geometry.interpolate(2.3,3.7).dip,dip*0.001);
		}
	}

	@Test public void smallPerturbationsDoNotRearrangeLayout() {
		for (long seed=1; seed<=8; seed++) {
			var shape=PathologicalSurfaceFixtures.seededShape(seed);
			var base=PathologicalSurfaceFixtures.sample(9,15,shape);
			var perturbed=PathologicalSurfaceFixtures.sample(9,15,(u,v) -> {
				double[] p=shape.point(u,v); p[2]+=0.0001*Math.sin(Math.PI*u)*Math.sin(Math.PI*v); return p;
			});
			for (PatchAlignment alignment : PatchAlignment.values()) {
				try {
					var a=layout(base,alignment); var b=layout(perturbed,alignment);
					checkPhysicalPatches(a); checkPhysicalPatches(b);
					assertEquals(a.patches.size(),b.patches.size());
					for (int i=0; i<a.patches.size(); i++)
						assertTrue(new Vector3D(a.patches.get(i).patch.center).distance(new Vector3D(b.patches.get(i).patch.center)) < 2);
				} catch (RuntimeException | AssertionError e) { throw new AssertionError("seed "+seed+" / "+alignment,e); }
			}
		}
	}

	@Test public void equivalentTwistedGeometrySurvivesResampling() {
		EvenlyGriddedSurface original=PathologicalSurfaceFixtures.twisted();
		GriddedSurfaceGeometry geometry=new GriddedSurfaceGeometry(original,11,'S');
		Location[][] refined=new Location[(geometry.rows-1)*2+1][(geometry.cols-1)*2+1];
		for (int r=0; r<refined.length; r++) for (int c=0; c<refined[0].length; c++)
			refined[r][c]=geometry.location(geometry.point(r*0.5,c*0.5));
		for (PatchAlignment alignment : PatchAlignment.values()) {
			var a=layout(original,alignment); var b=layout(GenericSubSectPatchBuilderTest.nodes(refined),alignment);
			assertEquals(a.patches.size(),b.patches.size());
			for (int i=0; i<a.patches.size(); i++)
				assertTrue(new Vector3D(a.patches.get(i).patch.center).distance(new Vector3D(b.patches.get(i).patch.center)) < 40);
		}
	}
}
