package scratch.UCERF3.utils;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ExceptionUtils;

public class WriteRELM_GriddedRegion {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		CaliforniaRegions.RELM_TESTING_GRIDDED griddedRegion  = new CaliforniaRegions.RELM_TESTING_GRIDDED();
		try {
			FileWriter fileWriter = new FileWriter(
					new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, "RELM_GriddedRegion.txt"));
			fileWriter.write("lat\tlon\n");
			for(int i=0; i<griddedRegion.getNumLocations(); i++) {
				Location loc = griddedRegion.getLocation(i);
				fileWriter.write((float)loc.getLatitude()+"\t"+(float)loc.getLongitude()+"\n");
			}
			fileWriter.close();
		} catch (IOException e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}

	}

}
