package org.opensha.sha.simulators.stiffness;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Random;

import org.junit.Test;

public class DoubleDoubleTest {
    private static final MathContext ORACLE=new MathContext(80);

    private static BigDecimal decimal(DoubleDouble value) {
        return new BigDecimal(value.hi).add(new BigDecimal(value.lo));
    }

    private static void assertAccurate(BigDecimal expected, DoubleDouble actual) {
        assertTrue(Double.isFinite(actual.hi) && Double.isFinite(actual.lo));
        BigDecimal error=decimal(actual).subtract(expected).abs();
        BigDecimal tolerance=expected.abs().multiply(new BigDecimal("1e-28")).add(new BigDecimal("1e-280"));
        assertTrue("expected "+expected+", actual "+decimal(actual)+", error "+error,error.compareTo(tolerance)<=0);
    }

    @Test public void recoversCancellationAndDecimalConstants() {
        DoubleDouble large=DoubleDouble.of(1e16).add(DoubleDouble.ONE);
        assertEquals(1,large.subtract(DoubleDouble.of(1e16)).doubleValue(),0);
        assertEquals(1,large.add(DoubleDouble.of(-1e16)).doubleValue(),0);
        assertAccurate(new BigDecimal("0.1"),DoubleDouble.parse("0.1"));
        assertAccurate(new BigDecimal("3.1415926535897932384626433832795028841971693993751"),
                DoubleDouble.parse("3.1415926535897932384626433832795028841971693993751"));
    }

    @Test public void arithmeticMatchesIndependentDecimalOracle() {
        Random random=new Random(5310937);
        for (int i=0; i<500; i++) {
            double first=Math.scalb(.5+random.nextDouble(),random.nextInt(501)-250);
            double second=Math.scalb(.5+random.nextDouble(),random.nextInt(501)-250);
            if (random.nextBoolean()) first=-first;
            if (random.nextBoolean()) second=-second;
            DoubleDouble a=DoubleDouble.of(first).add(DoubleDouble.of(Math.ulp(first)*.2));
            DoubleDouble b=DoubleDouble.of(second).add(DoubleDouble.of(Math.ulp(second)*.3));
            BigDecimal aa=decimal(a),bb=decimal(b);
            assertAccurate(aa.add(bb),a.add(b));
            assertAccurate(aa.subtract(bb),a.subtract(b));
            assertAccurate(aa.multiply(bb),a.multiply(b));
            assertAccurate(aa.divide(bb,ORACLE),a.divide(b));
            DoubleDouble positive=first<0 ? a.negate() : a;
            assertAccurate(decimal(positive).sqrt(ORACLE),positive.sqrt());
        }
    }
}
