package org.opensha.sha.simulators.stiffness.patches;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.StiffnessCalc;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchGenerationStrategy;

public class TriangularSubSectPatchBuilderTest {
    @Test public void tilesSyntheticSurfacesWithoutProjectionOrSpillover() {
        for (var entry : PathologicalSurfaceFixtures.examples().entrySet()) {
            var surface=entry.getValue();
            var geometry=new GriddedSurfaceGeometry(surface,11,'S');
            var builder=new TriangularSubSectPatchBuilder(11,'S');
            var patches=builder.buildPatches(surface,90);
            assertFalse(entry.getKey(),patches.isEmpty());
            double expected=0;
            for (int r=0; r<geometry.rows-1; r++) for (int c=0; c<geometry.cols-1; c++)
                expected+=geometry.cellArea(r,c)*1e6;
            assertEquals(entry.getKey(),expected,patches.stream().mapToDouble(p->p.patch.area).sum(),1e-5);
            for (var location : patches) {
                assertEquals(3,location.corners.length);
                assertEquals(3,location.patch.corner.length);
                assertTrue(location.patch.area > 0);
                assertEquals(1,org.apache.commons.math3.util.MathArrays.linearCombination(location.patch.nu,location.patch.nu),1e-12);
                assertEquals(0,org.apache.commons.math3.util.MathArrays.linearCombination(location.patch.nu,location.patch.u),1e-12);
            }
        }
    }

    @Test public void handlesAllThreePreviouslyFailingRealSections() throws Exception {
        for (String name : new String[] {"aubrey_148","bare_mountain_175","beaverhead_268"}) {
            try (var reader=new InputStreamReader(getClass().getResourceAsStream(name+".geojson"),StandardCharsets.UTF_8)) {
                var section=GeoJSONFaultReader.readFaultSections(reader).get(0);
                var calculator=new SubSectStiffnessCalculator(List.of(section),2,30000,30000,.5,
                        PatchAlignment.FILL_OVERLAP,0,PatchGenerationStrategy.TRIANGULAR);
                var patches=calculator.getPatches(section);
                assertFalse(patches.isEmpty());
                // Test every pair, including self stiffness and shared-edge neighbors.
                for (var source : patches) for (var receiver : patches) {
                    double[] stress=StiffnessCalc.calcStiffness(30000,30000,source.patch,receiver.patch);
                    assertTrue(name,Double.isFinite(stress[0]) && Double.isFinite(stress[1]));
                }
            }
        }
    }

    @Test public void realSectionPairUsesSharedFrameAndPhysicalSourceSums() throws Exception {
        java.util.ArrayList<FaultSection> sections=new java.util.ArrayList<>();
        for (String name : new String[] {"aubrey_148","beaverhead_268"})
            try (var reader=new InputStreamReader(getClass().getResourceAsStream(name+".geojson"),StandardCharsets.UTF_8)) {
                sections.add(GeoJSONFaultReader.readFaultSections(reader).get(0));
            }
        var calculator=new SubSectStiffnessCalculator(sections,2,30000,30000,.5,
                PatchAlignment.FILL_OVERLAP,0,PatchGenerationStrategy.TRIANGULAR);
        var distribution=calculator.calcStiffnessDistribution(sections.get(0),sections.get(1));
        var type=SubSectStiffnessCalculator.StiffnessType.CFF;
        double expectedSum=0;
        for (int r=0; r<distribution.receiverPatches.size(); r++) {
            double net=java.util.Arrays.stream(distribution.get(type)[r]).sum();
            assertEquals(net,distribution.receiverAggregate(
                    org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod.SUM).get(type)[r][0],1e-16);
            expectedSum+=net*distribution.receiverPatches.get(r).patch.area/calculator.getReferencePatchArea();
        }
        var sum=org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.builder(type,calculator)
                .process(org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod.SUM)
                .process(org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod.SUM).get();
        assertEquals(expectedSum,sum.calc(sections.get(0),sections.get(1)),Math.abs(expectedSum)*1e-12+1e-16);
    }

    @Test public void requestsCoarseSurfaceOnceAndHasNoAlignmentDependence() {
        var surface=PathologicalSurfaceFixtures.twisted();
        FaultSection delegate=GenericSubSectPatchBuilderTest.section(surface);
        AtomicInteger requests=new AtomicInteger();
        FaultSection section=(FaultSection)Proxy.newProxyInstance(FaultSection.class.getClassLoader(),
                new Class<?>[] {FaultSection.class},(proxy,method,args)-> {
                    if (method.getName().equals("getFaultSurface")) {
                        assertEquals(2d,(double)args[0],0);
                        requests.incrementAndGet();
                        return surface;
                    }
                    return method.invoke(delegate,args);
                });
        var first=new SubSectStiffnessCalculator(List.of(section),2,30000,30000,.5,
                PatchAlignment.CENTER,0,PatchGenerationStrategy.TRIANGULAR).buildPatches(section);
        assertEquals(1,requests.get());
        var second=new TriangularSubSectPatchBuilder(11,'S').buildPatches(section,surface);
        assertEquals(first.size(),second.size());
    }

    @Test public void supportsAreaScaledSelfStiffnessCap() {
        var section=GenericSubSectPatchBuilderTest.section(PathologicalSurfaceFixtures.twisted());
        var calc=new SubSectStiffnessCalculator(List.of(section),2,30000,30000,.5,
                PatchAlignment.FILL_OVERLAP,1,PatchGenerationStrategy.TRIANGULAR);
        var patches=calc.getPatches(section);
        var self=calc.calcSelfStiffness(patches);
        var capped=calc.calcStiffnessDistribution(section,section).get(SubSectStiffnessCalculator.StiffnessType.CFF);
        for (int r=0; r<patches.size(); r++) for (int s=0; s<patches.size(); s++) {
            double limit=Math.abs(self[r])*patches.get(s).patch.area/patches.get(r).patch.area;
            assertTrue(Double.isFinite(capped[r][s]));
            assertTrue(Math.abs(capped[r][s])<=limit+1e-12);
        }
    }

    @Test public void collapsedTipSkipsOnlyZeroAreaTriangle() {
        var top=GenericSubSectPatchBuilderTest.location(0,0,5);
        var surface=GenericSubSectPatchBuilderTest.nodes(new org.opensha.commons.geo.Location[][] {
                {top,top},{GenericSubSectPatchBuilderTest.location(5,0,10),GenericSubSectPatchBuilderTest.location(5,4,10)}});
        var patches=new TriangularSubSectPatchBuilder(11,'S').buildPatches(surface,90);
        assertEquals(1,patches.size());
        assertTrue(patches.get(0).patch.area > 0);
    }

    @Test public void curvedSurfaceCanDoubleBackWithoutUnwrapping() {
        var surface=PathologicalSurfaceFixtures.sample(5,33,(u,v)-> {
            double angle=Math.toRadians(250)*(u-.5);
            return new double[] {5*(1-Math.cos(angle)),5*Math.sin(angle),5+8*v};
        });
        var patches=new TriangularSubSectPatchBuilder(11,'S').buildPatches(surface,90);
        assertEquals(2*4*32,patches.size());
    }

    @Test public void reversedWindingPreservesPhysicalMechanismIncludingVertical() {
        for (double[][] vertices : new double[][][] {{{0,0,-1000},{0,2000,-1000},{-1000,1000,-3000}},
                {{0,0,-1000},{0,2000,-1000},{0,1000,-3000}}}) {
            var a=new TriangularPatch(vertices,35,0);
            var b=new TriangularPatch(new double[][] {vertices[0],vertices[2],vertices[1]},35,0);
            assertArrayEquals(a.nu,b.nu,1e-14);
            assertArrayEquals(a.u,b.u,1e-14);
            assertEquals(a.area,b.area,1e-9);
        }
    }
}
