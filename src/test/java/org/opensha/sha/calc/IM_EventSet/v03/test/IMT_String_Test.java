package org.opensha.sha.calc.IM_EventSet.v03.test;

import static org.junit.Assert.*;

import org.junit.Test;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.calc.IM_EventSet.v03.IM_EventSetOutputWriter;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

public class IMT_String_Test {
	
	private CB_2008_AttenRel cb08 = new CB_2008_AttenRel(null);

	public IMT_String_Test() {
	}
	
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
	
	private void doTestPeriod(String imtStr, double imtPeriod) {
		IM_EventSetOutputWriter.setIMTFromString(imtStr, cb08);
		checkIsSetCorrectly(imtPeriod);
		String newStr = IM_EventSetOutputWriter.getHAZ01IMTString(cb08.getIntensityMeasure());
		assertEquals(imtStr, newStr);
	}
	
	@Test
	public void test0_1Sec() {
		doTestPeriod("SA01", 0.1);
	}
	
	@Test
	public void test0_25Sec() {
		doTestPeriod("SA025", 0.25);
	}
	
	@Test
	public void test0_5Sec() {
		doTestPeriod("SA05", 0.5);
	}
	
	@Test
	public void test1Sec() {
		doTestPeriod("SA1", 1.0);
	}
	
	@Test
	public void test1_5Sec() {
		doTestPeriod("SA15", 1.5);
	}
	
	@Test
	public void test5Sec() {
		doTestPeriod("SA50", 5.0);
	}
	
	@Test
	public void test10Sec() {
		doTestPeriod("SA100", 10.0);
	}

}
