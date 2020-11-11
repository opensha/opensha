package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

import static org.junit.Assert.*;

import org.junit.Test;

// tested against sceqsrc (https://github.com/thingbaijam/sceqsrc)

public class TestTMG2017CruMagAreaRel {

    @Test
    public void testGetMedianMag() {
        TMG2017CruMagAreaRel tmg = new TMG2017CruMagAreaRel();

        assertTrue(Double.isNaN(tmg.getMedianMag(1, Double.NaN)));

        // area 1
        assertEquals(4.158246, tmg.getMedianMag(1, 90), 0.01);
        assertEquals(3.157178, tmg.getMedianMag(1, -90), 0.01);
        assertEquals(3.700637, tmg.getMedianMag(1, 0), 0.01);
        // area 500
        assertEquals(6.731144, tmg.getMedianMag(500, 90), 0.01);
        assertEquals(6.497488, tmg.getMedianMag(500, -90), 0.01);
        assertEquals(6.565786, tmg.getMedianMag(500, 0), 0.01);
        // area 10000
        assertEquals(7.971401, tmg.getMedianMag(10000, 90), 0.01);
        assertEquals(8.107673, tmg.getMedianMag(10000, -90), 0.01);
        assertEquals(7.946921, tmg.getMedianMag(10000, 0), 0.01);
        // area 10000
        assertEquals(8.924690, tmg.getMedianMag(100000, 90), 0.01);
        assertEquals(9.345297, tmg.getMedianMag(100000, -90), 0.01);
        assertEquals(9.008493, tmg.getMedianMag(100000, 0), 0.01);
    }

    @Test
    public void testGetMedianArea() {
        TMG2017CruMagAreaRel tmg = new TMG2017CruMagAreaRel();

        assertTrue(Double.isNaN(tmg.getMedianArea(1, Double.NaN)));

        // magnitude 4
        assertEquals(0.682339, tmg.getMedianArea(4, 90), 0.001);
        assertEquals(4.797334, tmg.getMedianArea(4, -90), 0.001);
        assertEquals(1.914256, tmg.getMedianArea(4, 0), 0.001);
        // magnitude 6
        assertEquals(85.506671, tmg.getMedianArea(6, 90), 0.001);
        assertEquals(198.152703, tmg.getMedianArea(6, -90), 0.001);
        assertEquals(146.554784, tmg.getMedianArea(6, 0), 0.001);
        // magnitude 8
        assertEquals(10715.193052, tmg.getMedianArea(8, 90), 0.001);
        assertEquals(8184.647881, tmg.getMedianArea(8, -90), 0.001);
        assertEquals(11220.184543, tmg.getMedianArea(8, 0), 0.001);
        // magnitude 9
        assertEquals(119949.930315, tmg.getMedianArea(9, 90), 0.001);
        assertEquals(52601.726639, tmg.getMedianArea(9, -90), 0.001);
        assertEquals(98174.794302, tmg.getMedianArea(9, 0), 0.001);
    }
}
