package org.opensha.commons.data.xyz;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class TestXYZ_DataSetMath {
	
	// data where val == i
	private XYZ_DataSet data1;
	// data where val == i*2
	private XYZ_DataSet data2;
	// data where val == i*2, but with additional points
	private XYZ_DataSet data3;

	@Before
	public void setUp() throws Exception {
		data1 = new ArbDiscrXYZ_DataSet();
		data2 = new ArbDiscrXYZ_DataSet();
		
		for (int i=1; i<=100; i++) {
			getData1().set(i, -i, i);
		}
		for (int i=data1.size()-1; i>=0; i--) {
			data2.set(data1.getPoint(i), data1.get(i)*2d);
		}
		
		data3 = getData2().copy();
		for (int i=1000; i<=1100; i++) {
			getData3().set(i, -i, i*2);
		}
	}
	
	protected XYZ_DataSet getData1() {
		return data1;
	}
	
	protected XYZ_DataSet getData2() {
		return data2;
	}
	
	protected XYZ_DataSet getData3() {
		return data3;
	}
	
	private double getData2Val(int i, XYZ_DataSet data1, XYZ_DataSet data2) {
		if (data1 instanceof GeoDataSet && data2 instanceof GeoDataSet)
			return ((GeoDataSet)data2).get(((GeoDataSet)data1).getLocation(i));
		return data2.get(data1.getPoint(i));
	}

	protected void doTestSum(XYZ_DataSet sum, XYZ_DataSet data1, XYZ_DataSet data2) {
		for (int i=0; i<getData1().size(); i++) {
			double val1 = data1.get(i);
			double val2 = getData2Val(i, data1, data2);
			assertEquals("sum is incorrect!", val1 + val2, sum.get(i), 0d);
		}
	}
	
	@Test
	public void testAddXYZ_DataSetAPIXYZ_DataSetAPI() {
		XYZ_DataSet sum = XYZ_DataSetMath.add(getData1(), getData2());
		
		assertEquals("sum size is incorrect", getData1().size(), sum.size());
		doTestSum(sum, getData1(), getData2());
		
		sum = XYZ_DataSetMath.add(getData1(), getData3());
		
		assertEquals("sum contians extras!", getData1().size(), sum.size());
		doTestSum(sum, getData1(), getData3());
	}
	
	private void doTestSubtract(XYZ_DataSet diference, XYZ_DataSet data1, XYZ_DataSet data2) {
		for (int i=0; i<getData1().size(); i++) {
			double val1 = data1.get(i);
			double val2 = getData2Val(i, data1, data2);
			assertEquals("difference is incorrect!", val1 - val2, diference.get(i), 0d);
		}
	}

	@Test
	public void testSubtract() {
		XYZ_DataSet diference = XYZ_DataSetMath.subtract(getData1(), getData2());
		
		assertEquals("diference size is incorrect", getData1().size(), diference.size());
		doTestSubtract(diference, getData1(), getData2());
		
		diference = XYZ_DataSetMath.subtract(getData1(), getData3());
		
		assertEquals("diference contians extras!", getData1().size(), diference.size());
		doTestSubtract(diference, getData1(), getData3());
	}
	
	private void doTestMultiply(XYZ_DataSet product, XYZ_DataSet data1, XYZ_DataSet data2) {
		for (int i=0; i<getData1().size(); i++) {
			double val1 = data1.get(i);
			double val2 = getData2Val(i, data1, data2);
			assertEquals("product is incorrect! ("+val1+" * "+val2+")", val1 * val2, product.get(i), 0d);
		}
	}


	@Test
	public void testMultiply() {
		XYZ_DataSet product = XYZ_DataSetMath.multiply(getData1(), getData2());
		
		assertEquals("sum size is incorrect", getData1().size(), product.size());
		doTestMultiply(product, getData1(), getData2());
		
		product = XYZ_DataSetMath.multiply(getData1(), getData3());
		
		assertEquals("product contians extras!", getData1().size(), product.size());
		doTestMultiply(product, getData1(), getData3());
	}
	
	private void doTestDivide(XYZ_DataSet quotient, XYZ_DataSet data1, XYZ_DataSet data2) {
		for (int i=0; i<getData1().size(); i++) {
			double val1 = data1.get(i);
			double val2 = getData2Val(i, data1, data2);
			assertEquals("quotient is incorrect ("+val1+"/"+val2+")!", val1 / val2, quotient.get(i), 0d);
		}
	}

	@Test
	public void testDivide() {
		XYZ_DataSet quotient = XYZ_DataSetMath.divide(getData1(), getData2());
		
		assertEquals("quotient size is incorrect", getData1().size(), quotient.size());
		doTestDivide(quotient, getData1(), getData2());
		
		quotient = XYZ_DataSetMath.divide(getData1(), getData3());
		
		assertEquals("product contians extras!", getData1().size(), quotient.size());
		doTestDivide(quotient, getData1(), getData3());
	}

	@Test
	public void testAbs() {
		XYZ_DataSet dataNeg = getData1().copy();
		for (int i=0; i<dataNeg.size(); i++) {
			double val = dataNeg.get(i);
			if (val > 0)
				dataNeg.set(i, -1 * val);
		}
		
		dataNeg.abs();
		for (int i=0; i<dataNeg.size(); i++) {
			assertEquals(getData1().get(i), dataNeg.get(i), 0d);
		}
	}

	@Test
	public void testLog() {
		XYZ_DataSet logged = getData1().copy();
		logged.log();
		
		for (int i=0; i<getData1().size(); i++) {
			double val = getData1().get(i);
			double logVal = logged.get(i);
			double myLogVal = Math.log(val);
			
			assertEquals(myLogVal, logVal, 0d);
		}
	}

	@Test
	public void testLog10() {
		XYZ_DataSet logged = getData1().copy();
		logged.log10();
		
		for (int i=0; i<getData1().size(); i++) {
			double val = getData1().get(i);
			double logVal = logged.get(i);
			double myLogVal = Math.log10(val);
			
			assertEquals(myLogVal, logVal, 0d);
		}
	}

	@Test
	public void testExp() {
		XYZ_DataSet exp = getData1().copy();
		exp.exp();
		
		for (int i=0; i<getData1().size(); i++) {
			double val = getData1().get(i);
			double expVal = exp.get(i);
			double myExpVal = Math.exp(val);
			
			assertEquals(myExpVal, expVal, 0d);
		}
	}

	@Test
	public void testPow() {
		double[] pows = {0.5, 1, 2, 7.7 };
		
		for (double pow : pows) {
			XYZ_DataSet powData = getData1().copy();
			powData.pow(pow);
			
			for (int i=0; i<getData1().size(); i++) {
				double val = getData1().get(i);
				double calcVal = powData.get(i);
				double myVal = Math.pow(val, pow);
				
				assertEquals(myVal, calcVal, 0d);
			}
		}
	}

	@Test
	public void testScale() {
		double[] scalars = {0, 1, -1, -.5, 99.1 };
		
		for (double scalar : scalars) {
			XYZ_DataSet scaled = getData1().copy();
			scaled.scale(scalar);
			
			for (int i=0; i<getData1().size(); i++) {
				double val = getData1().get(i);
				double scaledVal = scaled.get(i);
				double myScaledVal = val * scalar;
				
				assertEquals(myScaledVal, scaledVal, 0d);
			}
		}
	}

	@Test
	public void testAddXYZ_DataSetAPIDouble() {
		double[] adds = {0, 1, -1, -.5, 99.1 };
		
		for (double add : adds) {
			XYZ_DataSet sum = getData1().copy();
			sum.add(add);
			
			for (int i=0; i<getData1().size(); i++) {
				double val = getData1().get(i);
				double sumVal = sum.get(i);
				double mySumVal = val + add;
				
				assertEquals(mySumVal, sumVal, 0d);
			}
		}
	}

}
