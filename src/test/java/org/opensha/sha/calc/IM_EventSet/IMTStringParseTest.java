package org.opensha.sha.calc.IM_EventSet;

import static org.junit.Assert.*;

import org.junit.Test;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

/**
 * IM Event Set Calculator v3 test to verify SA periods are set correctly.
 * Tests both HAZ01 IMT (getHAZ01IMTString) and regular IMT (getRegularIMTString) formats.
 * Note that the HAZ01 IMT format only allows precision up to 0.1 seconds.
 * <p>
 * ====== Expected Values ======
 * PGA       (HAZ01: PGA)
 * PGV       (HAZ01: PGV)
 * SA 0.01   (HAZ01: SA00)
 * SA 0.02   (HAZ01: SA00)
 * SA 0.03   (HAZ01: SA00)
 * SA 0.05   (HAZ01: SA01)
 * SA 0.075  (HAZ01: SA01)
 * SA 0.1    (HAZ01: SA01)
 * SA 0.15   (HAZ01: SA02)
 * SA 0.2    (HAZ01: SA02)
 * SA 0.25   (HAZ01: SA03)
 * SA 0.3    (HAZ01: SA03)
 * SA 0.4    (HAZ01: SA04)
 * SA 0.5    (HAZ01: SA05)
 * SA 0.75   (HAZ01: SA08)
 * SA 1.0    (HAZ01: SA10)
 * SA 1.5    (HAZ01: SA15)
 * SA 2.0    (HAZ01: SA20)
 * SA 3.0    (HAZ01: SA30)
 * SA 4.0    (HAZ01: SA40)
 * SA 5.0    (HAZ01: SA50)
 * SA 7.5    (HAZ01: SA75)
 * SA 10.0   (HAZ01: SA100)
 * </p>
 */
public class IMTStringParseTest {
	
	private final CB_2008_AttenRel cb08 = new CB_2008_AttenRel(null);

    /**
     * Verifies if the IMT period is set as expected.
     * @param period Period to verify
     */
	private void checkIsSetCorrectly(double period) {
		Parameter<?> imt = cb08.getIntensityMeasure();
		assertEquals(SA_Param.NAME, imt.getName());
		assertTrue(imt instanceof Parameter);
		Parameter<?> depIMT = (Parameter<?>)imt;
		Parameter<?> periodParam = depIMT.getIndependentParameter(PeriodParam.NAME);
		double imtPer = (Double)periodParam.getValue();
		System.out.println("got: " + imtPer + " sec, expecting: " + period + " sec");
		assertEquals(period, imtPer, 0);
	}

    /**
     * Sets the IMT using a HAZ01 IMT string and verifies if the period is set correctly.
     * @param imtStr HAZ01 format IMT string
     * @param imtPeriod Period to set and verify
     */
	private void doTestHAZ01Period(String imtStr, double imtPeriod) {
		IM_EventSetOutputWriter.setIMTFromString(imtStr, cb08);
		checkIsSetCorrectly(imtPeriod);
		String newStr = IM_EventSetOutputWriter.getHAZ01IMTString(cb08.getIntensityMeasure());
		assertEquals(imtStr, newStr);
	}

    /**
     * Sets the IMT using a regular IMT string and verifies if the period is set correctly.
     * @param imtStr Regular format IMT string
     * @param imtPeriod Period to set and verify
     */
    private void doTestRegularPeriod(String imtStr, double imtPeriod) {
        IM_EventSetOutputWriter.setIMTFromString(imtStr, cb08);
        checkIsSetCorrectly(imtPeriod);
        String newStr = IM_EventSetOutputWriter.getRegularIMTString(cb08.getIntensityMeasure());
        assertEquals(imtStr, newStr);
    }

    // HAZ01 tests commented out are due to constraint exceptions.
    // We can't set that SA period value for the given IMT (CB08).

    // This is not an issue in the IM Event Set Calculator, since we don't
    // set the IMT period from the HAZ01 string, we always use the period directly.
    // Expected HAZ01 string representations are observed in the GUI app.

    // Constraint exceptions can be encountered in the CLT if an unsupported SA period is passed.
    // To provide periods with greater precision than 0.1, the HAZ01 format cannot be used.


	@Test
	public void test0_01Sec() {
//        doTestHAZ01Period("SA00", 0);
		doTestRegularPeriod("SA 0.01", 0.01);
	}
	
	@Test
	public void test0_02Sec() {
//        doTestHAZ01Period("SA00", 0);
		doTestRegularPeriod("SA 0.02", 0.02);
	}
	
	@Test
	public void test0_03Sec() {
//        doTestHAZ01Period("SA00", 0);
		doTestRegularPeriod("SA 0.03", 0.03);
	}
	
	@Test
	public void test0_05Sec() {
        doTestHAZ01Period("SA01", 0.1);
		doTestRegularPeriod("SA 0.05", 0.05);
	}
	
	@Test
	public void test0_075Sec() {
        doTestHAZ01Period("SA01", 0.1);
		doTestRegularPeriod("SA 0.075", 0.075);
	}

	@Test
	public void test0_1Sec() {
        doTestHAZ01Period("SA01", 0.1);
		doTestRegularPeriod("SA 0.1", 0.1);
	}
	
	@Test
	public void test0_15Sec() {
        doTestHAZ01Period("SA02", 0.2);
		doTestRegularPeriod("SA 0.15", 0.15);
	}
	
	@Test
	public void test0_2Sec() {
        doTestHAZ01Period("SA02", 0.2);
		doTestRegularPeriod("SA 0.2", 0.2);
	}
	
	@Test
	public void test0_3Sec() {
        doTestHAZ01Period("SA03", 0.3);
		doTestRegularPeriod("SA 0.3", 0.3);
	}
	
	@Test
	public void test0_4Sec() {
        doTestHAZ01Period("SA04", 0.4);
		doTestRegularPeriod("SA 0.4", 0.4);
	}

	@Test
	public void test0_5Sec() {
        doTestHAZ01Period("SA05", 0.5);
		doTestRegularPeriod("SA 0.5", 0.5);
	}
	
	@Test
	public void test0_75Sec() {
//        doTestHAZ01Period("SA08", 0.8);
		doTestRegularPeriod("SA 0.75", 0.75);
	}
	
	@Test
	public void test1Sec() {
		doTestHAZ01Period("SA10", 1.0);
		doTestRegularPeriod("SA 1.0", 1.0);
	}
	
	@Test
	public void test1_5Sec() {
		doTestHAZ01Period("SA15", 1.5);
		doTestRegularPeriod("SA 1.5", 1.5);
	}
	
	@Test
	public void test2Sec() {
		doTestHAZ01Period("SA20", 2.0);
		doTestRegularPeriod("SA 2.0", 2.0);
	}
	
	@Test
	public void test3Sec() {
		doTestHAZ01Period("SA30", 3.0);
		doTestRegularPeriod("SA 3.0", 3.0);
	}
	
	@Test
	public void test4Sec() {
		doTestHAZ01Period("SA40", 4.0);
		doTestRegularPeriod("SA 4.0", 4.0);
	}

	@Test
	public void test5Sec() {
		doTestHAZ01Period("SA50", 5.0);
		doTestRegularPeriod("SA 5.0", 5.0);
	}
	
	@Test
	public void test7_5Sec() {
		doTestHAZ01Period("SA75", 7.5);
		doTestRegularPeriod("SA 7.5", 7.5);
	}
	
	@Test
	public void test10Sec() {
		doTestHAZ01Period("SA100", 10.0);
		doTestRegularPeriod("SA 10.0", 10.0);
	}

}
