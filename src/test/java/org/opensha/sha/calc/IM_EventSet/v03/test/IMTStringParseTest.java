package org.opensha.sha.calc.IM_EventSet.v03.test;

import static org.junit.Assert.*;

import org.junit.Test;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.calc.IM_EventSet.v03.IM_EventSetOutputWriter;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

/**
 * IM Event Set Calculator v3 test to verify SA periods are set correctly.
 * Tests both HAZ01 IMT (getHAZ01IMTString) and regular IMT (getRegularIMTString) formats.
 * Note that the HAZ01 IMT format only allows precision up to 0.001 seconds.
 * <p>
 * </p>
 * <p>
 * ====== Expected Values ======
 * PGA      (HAZ01: PGA)
 * PGV      (HAZ01: PGV)
 * SA 0.01  (HAZ01: SA10)
 * SA 0.02  (HAZ01: SA20)
 * SA 0.03  (HAZ01: SA30)
 * SA 0.05  (HAZ01: SA50)
 * SA 0.075 (HAZ01: SA75)
 * SA 0.1   (HAZ01: SA100)
 * SA 0.15  (HAZ01: SA150)
 * SA 0.2   (HAZ01: SA200)
 * SA 0.25  (HAZ01: SA250)
 * SA 0.3   (HAZ01: SA300)
 * SA 0.4   (HAZ01: SA400)
 * SA 0.5   (HAZ01: SA500)
 * SA 0.75  (HAZ01: SA750)
 * SA 1.0   (HAZ01: SA1000)
 * SA 1.5   (HAZ01: SA1500)
 * SA 2.0   (HAZ01: SA2000)
 * SA 3.0   (HAZ01: SA3000)
 * SA 4.0   (HAZ01: SA4000)
 * SA 5.0   (HAZ01: SA5000)
 * SA 7.5   (HAZ01: SA7500)
 * SA 10.0  (HAZ01: SA10000)
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

	@Test
	public void test0_01Sec() {
        doTestHAZ01Period("SA10", 0.01);
		doTestRegularPeriod("SA 0.01", 0.01);
	}
	
	@Test
	public void test0_02Sec() {
        doTestHAZ01Period("SA20", 0.02);
		doTestRegularPeriod("SA 0.02", 0.02);
	}
	
	@Test
	public void test0_03Sec() {
        doTestHAZ01Period("SA30", 0.03);
		doTestRegularPeriod("SA 0.03", 0.03);
	}
	
	@Test
	public void test0_05Sec() {
        doTestHAZ01Period("SA50", 0.05);
		doTestRegularPeriod("SA 0.05", 0.05);
	}
	
	@Test
	public void test0_075Sec() {
        doTestHAZ01Period("SA75", 0.075);
		doTestRegularPeriod("SA 0.075", 0.075);
	}

	@Test
	public void test0_1Sec() {
        doTestHAZ01Period("SA100", 0.1);
		doTestRegularPeriod("SA 0.1", 0.1);
	}
	
	@Test
	public void test0_15Sec() {
        doTestHAZ01Period("SA150", 0.15);
		doTestRegularPeriod("SA 0.15", 0.15);
	}
	
	@Test
	public void test0_2Sec() {
        doTestHAZ01Period("SA200", 0.2);
		doTestRegularPeriod("SA 0.2", 0.2);
	}
	
	@Test
	public void test0_3Sec() {
        doTestHAZ01Period("SA300", 0.3);
		doTestRegularPeriod("SA 0.3", 0.3);
	}
	
	@Test
	public void test0_4Sec() {
        doTestHAZ01Period("SA400", 0.4);
		doTestRegularPeriod("SA 0.4", 0.4);
	}

	@Test
	public void test0_5Sec() {
        doTestHAZ01Period("SA500", 0.5);
		doTestRegularPeriod("SA 0.5", 0.5);
	}
	
	@Test
	public void test0_75Sec() {
        doTestHAZ01Period("SA750", 0.75);
		doTestRegularPeriod("SA 0.75", 0.75);
	}
	
	@Test
	public void test1Sec() {
		doTestHAZ01Period("SA1000", 1.0);
		doTestRegularPeriod("SA 1.0", 1.0);
	}
	
	@Test
	public void test1_5Sec() {
		doTestHAZ01Period("SA1500", 1.5);
		doTestRegularPeriod("SA 1.5", 1.5);
	}
	
	@Test
	public void test2Sec() {
		doTestHAZ01Period("SA2000", 2.0);
		doTestRegularPeriod("SA 2.0", 2.0);
	}
	
	@Test
	public void test3Sec() {
		doTestHAZ01Period("SA3000", 3.0);
		doTestRegularPeriod("SA 3.0", 3.0);
	}
	
	@Test
	public void test4Sec() {
		doTestHAZ01Period("SA4000", 4.0);
		doTestRegularPeriod("SA 4.0", 4.0);
	}

	@Test
	public void test5Sec() {
		doTestHAZ01Period("SA5000", 5.0);
		doTestRegularPeriod("SA 5.0", 5.0);
	}
	
	@Test
	public void test7_5Sec() {
		doTestHAZ01Period("SA7500", 7.5);
		doTestRegularPeriod("SA 7.5", 7.5);
	}
	
	@Test
	public void test10Sec() {
		doTestHAZ01Period("SA10000", 10.0);
		doTestRegularPeriod("SA 10.0", 10.0);
	}

}
