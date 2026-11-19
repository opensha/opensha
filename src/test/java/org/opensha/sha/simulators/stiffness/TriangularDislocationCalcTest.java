package org.opensha.sha.simulators.stiffness;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.simulators.stiffness.patches.TriangularPatch;

public class TriangularDislocationCalcTest {
    @Test public void matchesCutdeHalfSpaceReference() throws Exception {
        try (BufferedReader in=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(
                "triangle_cutde_reference.csv"),StandardCharsets.UTF_8))) {
            String line;
            int count=0;
            while ((line=in.readLine()) != null) {
                if (line.startsWith("#")) continue;
                double[] v=Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();
                double[][] vertices=new double[3][3];
                for (int i=0; i<9; i++) vertices[i/3][i%3]=v[1+i];
                double[] actual=TriangularDislocationCalc.strain(vertices,Arrays.copyOfRange(v,10,13),
                        Arrays.copyOfRange(v,13,16),v[16]);
                for (int i=0; i<6; i++) assertEquals("case "+v[0]+", component "+i,
                        v[17+i],actual[i],1e-14+1e-9*Math.abs(v[17+i]));
                count++;
            }
            assertEquals(237,count);
        }
    }

    @Test public void splitTrianglesReproduceOkadaForAllMechanisms() {
        for (double strike : new double[] {0,35,180,279})
            for (double dip : new double[] {0,15,45,89.99,90})
                for (double rake : new double[] {0,90,-90,175}) {
                    var source=new StiffnessCalc.Patch(new double[] {500000,3800000,-5000},2000,3000,
                            new FocalMechanism(strike,dip,rake));
                    var a=new TriangularPatch(new double[][] {source.corner[0],source.corner[1],source.corner[2]},rake,strike);
                    var b=new TriangularPatch(new double[][] {source.corner[0],source.corner[2],source.corner[3]},rake,strike);
                    for (double[] center : new double[][] {{510500,3811100,-3000},{491700,3798700,-12000},{501110,3800770,-1000}}) {
                        var receiver=new StiffnessCalc.Patch(center,2000,2000,new FocalMechanism(127,62,-35));
                        double[] expected=StiffnessCalc.calcStiffness(30000,30000,source,receiver);
                        double[] first=StiffnessCalc.calcStiffness(30000,30000,a,receiver);
                        double[] second=StiffnessCalc.calcStiffness(30000,30000,b,receiver);
                        // The comparison is sensitive to cancellation very close
                        // to vertical; use an absolute stress tolerance there, not a relaxed
                        // tolerance for the cutde reference fixtures above.
                        for (int i=0; i<2; i++) assertEquals("strike/dip/rake="+strike+"/"+dip+"/"+rake,
                                expected[i],first[i]+second[i],(dip == 89.99 ? 1e-8 : 1e-9)+1e-8*Math.abs(expected[i]));
                    }
                }
    }

    @Test public void subdivisionPreservesSkinnyTriangleStress() {
        for (double ratio : new double[] {1,10,100,1000,10000}) {
            double[][] t={{0,0,-5000},{2000,0,-5000},{1800,2000/ratio,-6000}};
            // Make the entire triangle skinny, rather than retaining a large vertical altitude.
            t[2][2]=-5000-1000/ratio;
            double[] p={800,700,-3000}, slip={.7,-.3,0};
            double[] expected=TriangularDislocationCalc.strain(t,p,slip,.25);
            double[][] mid=new double[3][3];
            for (int i=0; i<3; i++) for (int j=0; j<3; j++) mid[i][j]=.5*(t[i][j]+t[(i+1)%3][j]);
            double[] total=new double[6];
            for (double[][] part : new double[][][] {{t[0],mid[0],mid[2]}, {mid[0],t[1],mid[1]},
                    {mid[2],mid[1],t[2]}, {mid[0],mid[1],mid[2]}}) {
                double[] strain=TriangularDislocationCalc.strain(part,p,slip,.25);
                for (int i=0; i<6; i++) total[i]+=strain[i];
            }
            for (int i=0; i<6; i++) assertEquals(expected[i],total[i],1e-12+1e-4*Math.abs(expected[i]));
        }
    }

    @Test public void physicalEdgeSingularityIsNotSilentlyRegularized() {
        double[] result=TriangularDislocationCalc.strain(new double[][] {{0,0,-3000},{2000,0,-3000},{0,2000,-4000}},
                new double[] {1000,0,-3000},new double[] {1,0,0},.25);
        assertTrue(Arrays.stream(result).anyMatch(v->!Double.isFinite(v)));
    }

    @Test public void nearlyVerticalEdgesConvergeSmoothlyToVertical() {
        double[] observation={800,700,-3000}, slip={.7,-.3,0};
        double[][] vertical={{0,0,-5000},{0,2000,-5000},{0,1000,-8000}};
        double[] expected=TriangularDislocationCalc.strain(vertical,observation,slip,.25);
        for (double offset : new double[] {.5,.05,.005,.0005}) {
            double[][] plus={{0,0,-5000},{0,2000,-5000},{offset,1000,-8000}};
            double[][] minus={{0,0,-5000},{0,2000,-5000},{-offset,1000,-8000}};
            double[] a=TriangularDislocationCalc.strain(plus,observation,slip,.25);
            double[] b=TriangularDislocationCalc.strain(minus,observation,slip,.25);
            for (int i=0; i<6; i++) {
                assertTrue(Double.isFinite(a[i]) && Double.isFinite(b[i]));
                assertEquals("near-vertical symmetric limit",expected[i],.5*(a[i]+b[i]),1e-12);
            }
        }
    }

    @Test public void rejectsDegenerateOrAboveSurfaceInputs() {
        for (double[][] tri : new double[][][] {{{0,0,-1000},{1,0,-1000},{2,0,-1000}},
                {{0,0,1},{1000,0,-1000},{0,1000,-2000}}}) {
            assertThrows(IllegalArgumentException.class,()->TriangularDislocationCalc.strain(tri,
                    new double[] {0,0,-5000},new double[] {1,0,0},.25));
        }
    }
}
