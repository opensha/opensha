package org.opensha.sha.imr.attenRelImpl.nshmp.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.json.Feature;
import org.opensha.nshmp.shaded.gmm.NshmpGmm;
import org.opensha.nshmp.shaded.gmm.NshmpGmm.Type;
import org.opensha.nshmp.shaded.gmm.NshmpGroundMotion;
import org.opensha.nshmp.shaded.tree.NshmpLogicTree;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper.RegionalCombination;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;

public class RegionalTests {

	public static void main(String[] args) throws IOException {
		List<Location> testLocs = new ArrayList<>();
		testLocs.add(new Location(0d, 0d));
		
		File inputDir = new File("/home/kevin/OpenSHA/nshm23/nshmp-haz-models/nshm-conus-6.2.0/site-data/gmm-region/");
		
//		Type type = Type.ACTIVE_CRUST;
//		WeightedList<NshmpGmm> fallback = WeightedList.evenlyWeighted(NshmpGmm.TOTAL_TREE_CONUS_ACTIVE_CRUST_2023);
//		List<Feature> overrideFeatures = List.of(
//				Feature.read(new File(inputDir, "bay-area-alt-basin.geojson")),
//				Feature.read(new File(inputDir, "los-angeles-cybershake.geojson")));
//		testLocs.add(new Location(34.031106886556586, -118.22139009902237));
//		testLocs.add(new Location(37.77384915552912, -122.41768206093282));
		
//		Type type = Type.SUBDUCTION_INTERFACE;
//		WeightedList<NshmpGmm> fallback = WeightedList.evenlyWeighted(NshmpGmm.TOTAL_TREE_CONUS_INTERFACE_2023);
		Type type = Type.SUBDUCTION_SLAB;
		WeightedList<NshmpGmm> fallback = WeightedList.evenlyWeighted(NshmpGmm.TOTAL_TREE_CONUS_INTRASLAB_2023);
		List<Feature> overrideFeatures = List.of(
				Feature.read(new File(inputDir, "seattle-m9.geojson")));
		testLocs.add(new Location(47.60292680381297, -122.33014591064673));
		
		RegionalCombination gmm = NSHMP_GMM_Wrapper.loadRegionalOverrides(fallback, overrideFeatures, type, "Test", "Test", false, null);
		gmm.setParamDefaults();
		gmm.setIntensityMeasure(PGA_Param.NAME);
		
		for (Location testLoc : testLocs) {
			System.out.println("Test location:\t"+testLoc);
			PointSurface surf = new PointSurface(testLoc);
			surf.setAveDip(90d);
			surf.setDepths(0d, 10d);
			EqkRupture testRup = new EqkRupture(7d, 0d, surf, null);
			gmm.setEqkRupture(testRup);
			Site site = new Site(testLoc);
			site.addParameterList(gmm.getSiteParams());
			gmm.setSite(site);
			
			System.out.println("Test input:\t"+gmm.getCurrentGmmInput());
			
			NshmpLogicTree<NshmpGroundMotion> gmTree = gmm.getGroundMotionTree();
			System.out.println("GM tree:\n"+gmTree);
			System.out.println();
		}
	}

}
