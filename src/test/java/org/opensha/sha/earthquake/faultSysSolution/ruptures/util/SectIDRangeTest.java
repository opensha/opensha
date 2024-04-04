package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class SectIDRangeTest {

    @Test
    public void testSingleShort() {
        SectIDRange rangeA = SectIDRange.build(1, 1);

        assertEquals(1, rangeA.getStartID());
        assertEquals(1, rangeA.getEndID());
        assertEquals(1, rangeA.size());

        SectIDRange rangeB = SectIDRange.build(1, 1);

        assertSame(rangeA, rangeB);
        assertEquals(rangeA, rangeB);
        assertEquals(rangeA.hashCode(), rangeB.hashCode());

        rangeB = SectIDRange.build(2, 2);

        assertEquals(1, rangeB.size());
        assertNotSame(rangeA, rangeB);
        assertNotEquals(rangeA, rangeB);
    }

    @Test
    public void testSingleInt() {

        SectIDRange rangeA = SectIDRange.build(Short.MAX_VALUE + 5, Short.MAX_VALUE + 5);

        assertEquals(Short.MAX_VALUE + 5, rangeA.getStartID());
        assertEquals(Short.MAX_VALUE + 5, rangeA.getEndID());
        assertEquals(1, rangeA.size());

        SectIDRange rangeB = SectIDRange.build(Short.MAX_VALUE + 5, Short.MAX_VALUE + 5);

        assertSame(rangeA, rangeB);
        assertEquals(rangeA, rangeB);
        assertEquals(rangeA.hashCode(), rangeB.hashCode());

        rangeB = SectIDRange.build(Short.MAX_VALUE + 6, Short.MAX_VALUE + 6);

        assertNotSame(rangeA, rangeB);
        assertNotEquals(rangeA, rangeB);
    }

    @Test
    public void testRangeShort() {
        SectIDRange rangeA = SectIDRange.build(1, 2);

        assertEquals(1, rangeA.getStartID());
        assertEquals(2, rangeA.getEndID());
        assertEquals(2, rangeA.size());

        SectIDRange rangeB = SectIDRange.build(1, 2);

        assertSame(rangeA, rangeB);
        assertEquals(rangeA, rangeB);
        assertEquals(rangeA.hashCode(), rangeB.hashCode());

        rangeB = SectIDRange.build(2, 3);

        assertNotSame(rangeA, rangeB);
        assertNotEquals(rangeA, rangeB);
    }

    @Test
    public void testRangeInt() {

        SectIDRange rangeA = SectIDRange.build(Short.MAX_VALUE + 5, Short.MAX_VALUE + 15);

        assertEquals(Short.MAX_VALUE + 5, rangeA.getStartID());
        assertEquals(Short.MAX_VALUE + 15, rangeA.getEndID());
        assertEquals(11, rangeA.size());

        SectIDRange rangeB = SectIDRange.build(Short.MAX_VALUE + 5, Short.MAX_VALUE + 15);

        assertSame(rangeA, rangeB);
        assertEquals(rangeA, rangeB);
        assertEquals(rangeA.hashCode(), rangeB.hashCode());

        rangeB = SectIDRange.build(Short.MAX_VALUE + 5, Short.MAX_VALUE + 16);

        assertNotSame(rangeA, rangeB);
        assertNotEquals(rangeA, rangeB);
    }
}
