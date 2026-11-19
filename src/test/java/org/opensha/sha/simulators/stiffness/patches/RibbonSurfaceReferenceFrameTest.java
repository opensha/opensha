package org.opensha.sha.simulators.stiffness.patches;

import static org.junit.Assert.*;

import java.util.List;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;

import org.junit.Test;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.commons.geo.Location;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.patches.GenericSubSectPatchBuilder.ReferenceFrameType;

public class RibbonSurfaceReferenceFrameTest {
	static EvenlyGriddedSurface curvedRibbon() {
		return PathologicalSurfaceFixtures.sample(9,65,(u,v) -> {
			double angle=Math.toRadians(130)*(u-0.5);
			return new double[] {5*(1-Math.cos(angle)),5*Math.sin(angle),5+8*v};
		});
	}

	@Test public void planarShapesRetainTheirOriginalCoordinatesAndLayouts() {
		for (var surface : List.of(GenericSubSectPatchBuilderTest.skewedSurface(),
				GenericSubSectPatchBuilderTest.surface(12,new double[] {0,2,4,6,8},new double[] {45,45,45,45},5),
				PathologicalSurfaceFixtures.unevenTraces(),PathologicalSurfaceFixtures.narrowNecks())) {
			var geometry=new GriddedSurfaceGeometry(surface,11,'S');
			SurfaceReferenceFrame planar=new PlanarSurfaceReferenceFrame(geometry);
			SurfaceReferenceFrame ribbon=new RibbonSurfaceReferenceFrame(geometry,2);
			for (int r=0; r<geometry.rows; r++) for (int c=0; c<geometry.cols; c++) {
				assertEquals(planar.nodeX(r,c),ribbon.nodeX(r,c),1e-10);
				assertEquals(planar.nodeY(r,c),ribbon.nodeY(r,c),1e-10);
			}
			for (var alignment : PatchAlignment.values()) {
				var a=new GenericSubSectPatchBuilder(2,11,'S',ReferenceFrameType.PLANAR).buildLayout(surface,90,alignment);
				var b=new GenericSubSectPatchBuilder(2,11,'S',ReferenceFrameType.RIBBON).buildLayout(surface,90,alignment);
				assertEquals(a.patches.size(),b.patches.size());
				for (int i=0; i<a.patches.size(); i++)
					assertArrayEquals(a.patches.get(i).patch.center,b.patches.get(i).patch.center,1e-7);
			}
		}
	}

	@Test public void fixedDownDipAxisAndCurvedAlongStrikeDistance() {
		var geometry=new GriddedSurfaceGeometry(curvedRibbon(),11,'S');
		SurfaceReferenceFrame planar=new PlanarSurfaceReferenceFrame(geometry);
		SurfaceReferenceFrame ribbon=new RibbonSurfaceReferenceFrame(geometry,1);
		assertTrue(ribbon.maxX() > planar.maxX()*1.2);
		assertEquals(5*Math.toRadians(130),ribbon.maxX(),0.03);
		assertTrue(ribbon.minimumEdgeRatio() > 0.98);
		assertTrue(ribbon.minimumAreaRatio() > 0.98);
		for (int r=0; r<geometry.rows; r++) for (int c=0; c<geometry.cols; c++)
			assertEquals(planar.nodeY(r,c),ribbon.nodeY(r,c),1e-10);
	}

	@Test public void inverseGridMappingRetainsActualSurfaceAndMechanisms() {
		var geometry=new GriddedSurfaceGeometry(curvedRibbon(),11,'S');
		SurfaceReferenceFrame frame=new RibbonSurfaceReferenceFrame(geometry,2);
		for (int r=1; r<geometry.rows-1; r++) for (int c=1; c<geometry.cols-1; c+=7) {
			double u=0.37,v=0.61;
			double x=(1-v)*((1-u)*frame.nodeX(r,c)+u*frame.nodeX(r,c+1))
					+v*((1-u)*frame.nodeX(r+1,c)+u*frame.nodeX(r+1,c+1));
			double y=(1-v)*((1-u)*frame.nodeY(r,c)+u*frame.nodeY(r,c+1))
					+v*((1-u)*frame.nodeY(r+1,c)+u*frame.nodeY(r+1,c+1));
			assertArrayEquals(new double[] {r+v,c+u},frame.indices(x,y),1e-7);
			assertEquals(0,geometry.point(r+v,c+u).distance(frame.point(x,y)),1e-7);
			assertEquals(geometry.interpolate(r+v,c+u).dip,frame.sample(x,y).dip,1e-7);
		}
	}

	@Test public void curvedFootprintsSampleEdgesAndKeepPhysicalRectangles() {
		for (var alignment : PatchAlignment.values()) {
			var layout=new GenericSubSectPatchBuilder(2,11,'S',ReferenceFrameType.RIBBON)
					.buildLayout(curvedRibbon(),90,alignment);
			assertFalse(layout.patches.isEmpty());
			for (var p : layout.patches) {
				assertEquals(4e6,p.patch.area,0);
				assertEquals(90,Math.toDegrees(p.patch.dip),1e-5);
				assertTrue(layout.frame.projectPatchBoundary(p.patch).length > 4);
				for (double[] point : layout.frame.projectPatchBoundary(p.patch)) {
					assertTrue(point[0] >= -1e-7); assertTrue(point[0] <= layout.frame.maxX()+1e-7);
				}
			}
		}
	}

	@Test public void invalidSmoothingWindowsAreRejected() {
		var geometry=new GriddedSurfaceGeometry(curvedRibbon(),11,'S');
		for (double window : new double[] {0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
			assertThrows(IllegalArgumentException.class,() -> new RibbonSurfaceReferenceFrame(geometry,window));
		}
	}

	@Test public void namedPathologiesProduceFiniteBoundedPhysicalPatches() {
		for (var entry : PathologicalSurfaceFixtures.examples().entrySet()) for (var alignment : PatchAlignment.values()) {
			try {
				var layout=new GenericSubSectPatchBuilder(2,11,'S',ReferenceFrameType.RIBBON)
						.buildLayout(entry.getValue(),90,alignment);
				PathologicalSubSectPatchBuilderTest.checkPhysicalPatches(layout);
			} catch (RuntimeException | AssertionError e) { throw new AssertionError(entry.getKey()+" / "+alignment,e); }
		}
	}

	@Test public void bareMountainProjectsWithoutFoldsAndBuildsBoundedPatches() throws Exception {
		try (var reader=new InputStreamReader(getClass().getResourceAsStream("bare_mountain_175.geojson"),StandardCharsets.UTF_8)) {
			var section=GeoJSONFaultReader.readFaultSections(reader).get(0);
			var surface=(EvenlyGriddedSurface)section.getFaultSurface(0.2,false,false);
			var geometry=new GriddedSurfaceGeometry(surface,11,'S');
			var failure=assertThrows(IllegalArgumentException.class,() -> new PlanarSurfaceReferenceFrame(geometry));
			assertTrue(failure.getMessage().contains("folds cell"));
			for (var alignment : PatchAlignment.values()) {
				var layout=new GenericSubSectPatchBuilder(2,11,'S',ReferenceFrameType.RIBBON)
						.buildLayout(surface,section.getAveRake(),alignment);
				PathologicalSubSectPatchBuilderTest.checkPhysicalPatches(layout);
				if (alignment == PatchAlignment.FILL_OVERLAP) {
					var coverage=new ProjectedPatchCoverage(layout,2,8);
					assertTrue(coverage.uncoveredArea/coverage.surfaceArea < 0.1);
					assertEquals(0,coverage.interiorUncoveredArea,1e-8);
				}
			}
		}
	}

	@Test public void crossSectionThatDoublesBackIsRejectedClearly() {
		var surface=PathologicalSurfaceFixtures.sample(9,65,(u,v) -> {
			double angle=Math.toRadians(250)*(u-0.5);
			return new double[] {5*(1-Math.cos(angle)),5*Math.sin(angle),5+8*v};
		});
		var geometry=new GriddedSurfaceGeometry(surface,11,'S');
		var failure=assertThrows(IllegalArgumentException.class,() -> new RibbonSurfaceReferenceFrame(geometry,2));
		assertTrue(failure.getMessage().contains("doubles back"));
	}

	@Test public void aubreyFallsBackAtOneWidthAndUsesRibbonAtTwoWidths() throws Exception {
		try (var reader=new InputStreamReader(getClass().getResourceAsStream("aubrey_148.geojson"),StandardCharsets.UTF_8)) {
			var section=GeoJSONFaultReader.readFaultSections(reader).get(0);
			var surface=(EvenlyGriddedSurface)section.getFaultSurface(0.2,false,false);
			for (var alignment : PatchAlignment.values()) {
				var fallback=new GenericSubSectPatchBuilder(2,11,'S',ReferenceFrameType.RIBBON,1)
						.buildLayout(surface,section.getAveRake(),alignment);
				assertTrue(fallback.frame instanceof PlanarSurfaceReferenceFrame);
				assertTrue(fallback.planarFallbackReason.contains("folds cell"));
				var planar=new GenericSubSectPatchBuilder(2,11,'S',ReferenceFrameType.PLANAR)
						.buildLayout(surface,section.getAveRake(),alignment);
				assertEquals(planar.patches.size(),fallback.patches.size());
				for (int i=0; i<planar.patches.size(); i++)
					assertArrayEquals(planar.patches.get(i).patch.center,fallback.patches.get(i).patch.center,0);
				var smooth=new GenericSubSectPatchBuilder(2,11,'S',ReferenceFrameType.RIBBON)
						.buildLayout(surface,section.getAveRake(),alignment);
				assertTrue(smooth.frame instanceof RibbonSurfaceReferenceFrame);
				assertNull(smooth.planarFallbackReason);
				PathologicalSubSectPatchBuilderTest.checkPhysicalPatches(smooth);
			}
		}
	}

	@Test public void bothProjectionFailuresRetainTheirDiagnostics() {
		var surface=PathologicalSurfaceFixtures.sample(9,65,(u,v) -> {
			double angle=Math.toRadians(250)*(u-0.5);
			return new double[] {5*(1-Math.cos(angle)),5*Math.sin(angle),5+8*v};
		});
		var failure=assertThrows(ReferenceFrameException.class,() ->
				new GenericSubSectPatchBuilder(2,11,'S',ReferenceFrameType.RIBBON)
						.buildLayout(surface,90,PatchAlignment.FILL_OVERLAP));
		assertTrue(failure.getMessage().contains("Ribbon projection failed"));
		assertTrue(failure.getCause().getMessage().contains("doubles back"));
		assertEquals(1,failure.getSuppressed().length);
		assertTrue(failure.getMessage().contains("planar fallback also failed"));
	}

	@Test public void invalidOriginalGeometryDoesNotTriggerProjectionFallback() {
		Location[][] nodes={
				{GenericSubSectPatchBuilderTest.location(0,0,5),GenericSubSectPatchBuilderTest.location(0,2,5)},
				{GenericSubSectPatchBuilderTest.location(2,2,7),GenericSubSectPatchBuilderTest.location(2,0,7)}};
		var surface=GenericSubSectPatchBuilderTest.nodes(nodes);
		var failure=assertThrows(IllegalArgumentException.class,() ->
				new GenericSubSectPatchBuilder(2,11,'S',ReferenceFrameType.RIBBON)
						.buildLayout(surface,90,PatchAlignment.FILL_OVERLAP));
		assertFalse(failure instanceof ReferenceFrameException);
		assertTrue(failure.getMessage().contains("Folded cell"));
		assertEquals(0,failure.getSuppressed().length);
	}
}
