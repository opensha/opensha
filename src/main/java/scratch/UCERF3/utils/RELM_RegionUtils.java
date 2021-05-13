package scratch.UCERF3.utils;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.region.CaliforniaRegions.RELM_TESTING_GRIDDED;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.Region;

public class RELM_RegionUtils {
	
	
	public static CaliforniaRegions.RELM_TESTING_GRIDDED getGriddedRegionInstance() {
		return new CaliforniaRegions.RELM_TESTING_GRIDDED();
	}
	
	
	public static CaliforniaRegions.RELM_NOCAL_GRIDDED getNoCalGriddedRegionInstance() {
		return new CaliforniaRegions.RELM_NOCAL_GRIDDED();
	}

	
	public static CaliforniaRegions.RELM_SOCAL_GRIDDED getSoCalGriddedRegionInstance() {
		return new CaliforniaRegions.RELM_SOCAL_GRIDDED();
	}
	
	/**
	 * this creates a GriddedGeoDataSet based on a gridded RELM region and initializes 
	 * all the z values to zero
	 * @return
	 */
	public static GriddedGeoDataSet getRELM_RegionGeoDataSetInstance() {
		GriddedGeoDataSet data = new GriddedGeoDataSet(getGriddedRegionInstance(), true);
		for(int i=0;i<data.size();i++) data.set(i,0.0);
		return data;
	}
	
	public static void printNumberOfGridNodes() {
		RELM_TESTING_GRIDDED region = new CaliforniaRegions.RELM_TESTING_GRIDDED();		
		System.out.println("RELM_TESTING_GRIDDED has "+region.getNumLocations()+" locations");
	}
	

}
