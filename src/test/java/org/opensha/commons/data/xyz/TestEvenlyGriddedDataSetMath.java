package org.opensha.commons.data.xyz;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;

public class TestEvenlyGriddedDataSetMath extends TestXYZ_DataSetMath {
	
	EvenlyDiscrXYZ_DataSet data1;
	EvenlyDiscrXYZ_DataSet data2;
	EvenlyDiscrXYZ_DataSet data3;
	
	protected static final int ncols = 10;
	protected static final int nrows = 5;
	protected static final double minX = 0.5;
	protected static final double minY = 1.5;
	protected static final double gridSpacing = 0.15;

	@Before
	public void setUp() throws Exception {
		data1 = new EvenlyDiscrXYZ_DataSet(ncols, nrows, minX, minY, gridSpacing);
		data2 = new EvenlyDiscrXYZ_DataSet(ncols, nrows, minX, minY, gridSpacing);
		data3 = new EvenlyDiscrXYZ_DataSet(ncols+2, nrows+2, minX, minY, gridSpacing);
		
		for (int i=0; i<data1.size(); i++) {
			data1.set(i, (double)i);
			data2.set(i, 2d*i);
		}
		
		for (int i=0; i<data3.size(); i++) {
			data3.set(i, i);
		}
	}
	
	@Override
	protected XYZ_DataSet getData1() {
		return data1;
	}
	
	@Override
	protected XYZ_DataSet getData2() {
		return data2;
	}
	
	@Override
	protected XYZ_DataSet getData3() {
		return data3;
	}
}
