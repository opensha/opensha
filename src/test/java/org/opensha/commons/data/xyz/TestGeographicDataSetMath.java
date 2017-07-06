package org.opensha.commons.data.xyz;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;

public class TestGeographicDataSetMath extends TestXYZ_DataSetMath {
	
	ArbDiscrGeoDataSet arbDiscrData;
	ArbDiscrGeoDataSet arbDiscrData2;

	@Before
	public void setUp() throws Exception {
		arbDiscrData = TestArbDiscrGeographicDataSet.createTestData(true, 0);
		
		arbDiscrData2 = new ArbDiscrGeoDataSet(!arbDiscrData.isLatitudeX());
		for (int i=arbDiscrData.size()-1; i>=0; i--) {
			arbDiscrData2.set(arbDiscrData.getLocation(i), arbDiscrData.get(i)*2d);
		}
	}
	
	@Override
	protected XYZ_DataSet getData1() {
		return arbDiscrData;
	}
	
	@Override
	protected XYZ_DataSet getData2() {
		return arbDiscrData2;
	}
	
	@Override
	protected XYZ_DataSet getData3() {
		ArbDiscrGeoDataSet arbDiscrData3 = (ArbDiscrGeoDataSet)arbDiscrData.clone();
		arbDiscrData3.set(new Location(34.234, -118.243), 43);
		arbDiscrData3.set(new Location(34.2354, -118.267), 43);
		arbDiscrData3.set(new Location(34.2243, -118.296), 43);
		return arbDiscrData3;
	}
}
