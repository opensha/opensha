package org.opensha.commons.data.xyz;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;

public class TestGriddedGeographicDataSetMath extends TestXYZ_DataSetMath {
	
	GriddedGeoDataSet griddedData;
	
	@Before
	public void setUp() throws Exception {
		GriddedRegion region = new CaliforniaRegions.RELM_TESTING_GRIDDED(0.5);
		
		griddedData = new GriddedGeoDataSet(region, true);
		
		for (int i=0; i<griddedData.size(); i++) {
			griddedData.set(i, (double)i);
		}
	}
	
	@Override
	protected XYZ_DataSet getData1() {
		return griddedData;
	}
	
	@Override
	protected XYZ_DataSet getData2() {
		return (GriddedGeoDataSet)griddedData.clone();
	}
	
	@Override
	protected XYZ_DataSet getData3() {
		GriddedRegion region = new CaliforniaRegions.RELM_TESTING_GRIDDED(0.25);
		
		GriddedGeoDataSet data3 = new GriddedGeoDataSet(region, true);
		
		for (int i=0; i<data3.size(); i++) {
			data3.set(i, (double)i);
		}
		
		return data3;
	}

}
