package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

/**
 * Utility class for making map view plots of rupture sets
 * 
 * @author kevin
 *
 */
public class RupSetMapMaker extends GeographicMapMaker {
	
	public RupSetMapMaker(FaultSystemRupSet rupSet, Region region) {
		this(rupSet.getFaultSectionDataList(), region);
	}

	public RupSetMapMaker(List<? extends FaultSection> sects, Region region) {
		super(region);
		setFaultSections(sects);
	}
	
	public static void main(String[] args) throws IOException {
//		System.out.println(getGeoJSONViewerRelativeLink("My Link", "map.geojson"));
//		System.out.println(getGeoJSONViewerLink("http://opensha.usc.edu/ftp/kmilner/mrkdown/rupture-sets/rsqsim_4983_stitched_m6.5_skip65000_sectArea0.5/comp_fm3_1_ucerf3/resources/conn_rates_m6.5.geojson"));
		
		List<GeoJSONFaultSection> sects = GeoJSONFaultReader.readFaultSections(new File("/tmp/GEOLOGIC_sub_sects.geojson"));
		
		GeographicMapMaker mapMaker = new RupSetMapMaker(sects, buildBufferedRegion(sects));
		
		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-1, 1);
		double[] scalars = new double[sects.size()];
		for (int s=0; s<scalars.length; s++) {
			if (Math.random() < 0.3)
				scalars[s] = Double.NaN;
			else
				scalars[s] = 2d*Math.random()-1;
		}
		mapMaker.setReverseSort(true);
		mapMaker.plotSectScalars(scalars, cpt, "Label");
		mapMaker.plot(new File("/tmp"), "nan_test", " ");
	}

}
