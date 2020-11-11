package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

import static org.junit.Assert.*;
import org.junit.Test;
import org.opensha.commons.exceptions.InvalidRangeException;

// tested against sceqsrc (https://github.com/thingbaijam/sceqsrc)
// for subduction-interface events

public class TestTMG2017SubMagAreaRel {

    @Test
    public void testGetMedianMag() {
        TMG2017SubMagAreaRel tmg = new TMG2017SubMagAreaRel();

        try {
            tmg.getMedianMag(1, Double.NaN);
            assertFalse("the previous line should have thrown an exception", true);
        } catch (InvalidRangeException irx) {
        }

        // area 1
        assertEquals(3.468915, tmg.getMedianMag(1, 90), 0.01);
        // area 500
        assertEquals(6.312929, tmg.getMedianMag(500, 90), 0.01);
        // area 10000
        assertEquals(7.683878, tmg.getMedianMag(10000, 90), 0.01);
        // area 100000
        assertEquals(8.737619, tmg.getMedianMag(100000, 90), 0.01);
    }

    @Test
    public void testGetMedianArea() {
        TMG2017SubMagAreaRel tmg = new TMG2017SubMagAreaRel();

        try {
            tmg.getMedianArea(1, Double.NaN);
            assertFalse("the previous line should have thrown an exception", true);
        } catch (InvalidRangeException irx) {
        }

        // magnitude 4
        assertEquals(3.191538, tmg.getMedianArea(4, 90), 0.001);
        // magnitude 6
        assertEquals(252.348077, tmg.getMedianArea(6, 90), 0.001);
        // magnitude 8
        assertEquals(19952.623150, tmg.getMedianArea(8, 90), 0.001);
        // magnitude 9
        assertEquals(177418.948089, tmg.getMedianArea(9, 90), 0.001);
    }
}
