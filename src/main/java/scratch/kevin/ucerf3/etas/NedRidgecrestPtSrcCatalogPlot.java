package scratch.kevin.ucerf3.etas;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_SimulatedCatalogPlot;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

public class NedRidgecrestPtSrcCatalogPlot {

	public static void main(String[] args) throws IOException {
		File mainDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations");
		File[] catalogDirs = {
				new File(mainDir, "2021_04_13-ComCatM6p4_ci38443183_ShakeMapSurface_kCOV1p5"),
				new File(mainDir, "2021_04_13-ComCatM6p4_ci38443183_ShakeMapSurface_kCOV1p5_MaxPtSrcM6")
		};
		
		double minMag = 7d;
		
		int deltaPercentile = 5;
		List<Double> percentileList = new ArrayList<>();
		for (int p=0; p<=100; p+=deltaPercentile)
			percentileList.add((double)p);
		double[] percentiles = Doubles.toArray(percentileList);
		
		for (File catalogDir : catalogDirs) {
			File configFile = new File(catalogDir, "config.json");
			ETAS_Config config = ETAS_Config.readJSON(configFile);
			ETAS_Launcher launcher = new ETAS_Launcher(config, false);
			FaultSystemSolution fss = launcher.checkOutFSS();
			File binFile = new File(catalogDir, "results_complete.bin");
			int index = 0;
			System.out.println("Processing "+binFile.getAbsolutePath());
			int numMatches = 0;
			String prefix = catalogDir.getName().contains("MaxPtSrc") ? "random_finite" : "point_source";
			ETAS_SimulatedCatalogPlot plot = new ETAS_SimulatedCatalogPlot(config, launcher, prefix, percentiles);
			
			Location center = null;
			double largestInput = 0d;
			for (ETAS_EqkRupture trigger : launcher.getTriggerRuptures()) {
				if (trigger.getMag() >= largestInput) {
					MinMaxAveTracker latTrack = new MinMaxAveTracker();
					MinMaxAveTracker lonTrack = new MinMaxAveTracker();
					for (Location loc : trigger.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface()) {
						latTrack.addValue(loc.getLatitude());
						lonTrack.addValue(loc.getLongitude());
					}
					center = new Location(latTrack.getAverage(), lonTrack.getAverage());
				}
			}
			System.out.println("Center: "+center);
			double radius = 60d;
			double maxLat = LocationUtils.location(center, 0d, radius).getLatitude();
			double minLat = LocationUtils.location(center, Math.PI, radius).getLatitude();
			double maxLon = LocationUtils.location(center, Math.PI/2d, radius).getLongitude();
			double minLon = LocationUtils.location(center, 3d*Math.PI/2d, radius).getLongitude();
			Region region = new Region(new Location(minLat, minLon), new Location(maxLat, maxLon));
			
			plot.setForceRegion(region);
			plot.setHideTitles();
			plot.setHideInputEvents();
			plot.setPlotDurations(new double[] { config.getDuration() });
			plot.setPlotGenerations(true);
			for (ETAS_Catalog cat : ETAS_CatalogIO.getBinaryCatalogsIterable(binFile, 0)) {
				ETAS_EqkRupture matchingRup = null;
				boolean hasFSS = false;
				for (ETAS_EqkRupture rup : cat) {
					if (rup.getFSSIndex() >= 0) {
						hasFSS = true;
						break;
					}
					if (rup.getMag() >= minMag && rup.getFSSIndex() < 0) {
						matchingRup = rup;
					}
				}
				if (matchingRup != null && !hasFSS) {
//					System.out.println("Found a matching rupture in catalog "+index+", an M"+(float)matchingRup.getMag());
					numMatches++;
					plot.processCatalog(cat, fss);
				}
				index++;
			}
			double fractMatching = (double)numMatches/(double)index;
			System.out.println("Found "+numMatches+" candidates ("+(float)(100d*fractMatching)+" %)");
			File plotDir = new File(catalogDir, "off_fault_catalog_plots");
			Preconditions.checkState(plotDir.exists() || plotDir.mkdir());
			plot.finalize(plotDir, fss);
			launcher.checkInFSS(fss);
		}
	}

}
