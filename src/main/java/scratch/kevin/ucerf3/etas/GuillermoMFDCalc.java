package scratch.kevin.ucerf3.etas;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_CatalogIteration;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_CatalogIteration.Callback;

public class GuillermoMFDCalc {

	public static void main(String[] args) throws IOException {
//		File simsDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/");
//		File mainOutputDir = new File("/home/kevin/OpenSHA/UCERF3/etas/guillermo");
		File simsDir = new File("/home/scec-02/kmilner/ucerf3/etas_sim");
		File mainOutputDir = new File("/home/scec-02/kmilner/ucerf3/etas_guillermo");
		
		File cubesCSVFile = new File(mainOutputDir, "California_0p5.csv");
		double spacing = 0.5;
		
		String prefix, contains;
		File outputDir;
		if (args.length == 0) {
			prefix = "2020_02_04-Start";
			contains = "1yr_kCOV1p5";
			outputDir = new File(mainOutputDir, "full_td_kCOV1p5");
		} else {
			Preconditions.checkArgument(args.length == 3, "USAGE: <prefix> <contains> <outfix>");
			prefix = args[0];
			contains = args[1];
			outputDir = new File(mainOutputDir, args[2]);
		}
		
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		int minYear = 1986;
		int maxYear = 2020;
		
		// load cubes
		CSVFile<String> cubesCSV = CSVFile.readFile(cubesCSVFile, true);
		List<Location> centers = new ArrayList<>();
		
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		for (int row=1; row<cubesCSV.getNumRows(); row++) {
			double lat = cubesCSV.getDouble(row, 8);
			double lon = cubesCSV.getDouble(row, 7);
			double depth = cubesCSV.getDouble(row, 9);
			if (depth > 24)
				continue;
			latTrack.addValue(lat);
			lonTrack.addValue(lon);
			centers.add(new Location(lat, lon));
		}
		double minLat = latTrack.getMin();
		double minLon = lonTrack.getMin();
		Location minLoc = new Location(minLat, minLon);
		double maxLat = latTrack.getMax();
		double maxLon = lonTrack.getMax();
		Location maxLoc = new Location(maxLat, maxLon);
		GriddedRegion gridReg = new GriddedRegion(minLoc, maxLoc, spacing, minLoc);
		
		System.out.println("Min loc: "+minLoc);
		System.out.println("Max loc: "+maxLoc);
		System.out.println("Loaded "+centers.size()+" locations");
		System.out.println("Grid reg has "+gridReg.getNodeCount()+" locations");

		Map<Integer, Integer> gridToCubeIDMap = new HashMap<>();
		Map<Integer, Integer> cubeIdToGridMap = new HashMap<>();
		Map<Integer, CSVFile<String>> cubeCSVs = new HashMap<>();
		MinMaxAveTracker distTrack = new MinMaxAveTracker();
		IncrementalMagFreqDist refMFD = new IncrementalMagFreqDist(mfdMinMag, mfdNumMag, mfdDelta);
		List<String> header = new ArrayList<>();
		header.add("Year");
		for (Point2D pt : refMFD)
			header.add((float)pt.getX()+"");
		for (int i=0; i<centers.size(); i++) {
			int id = i+1;
			Location center = centers.get(i);
			int index = gridReg.indexForLocation(center);
			Preconditions.checkState(index >= 0);
			Location gridLoc = gridReg.getLocation(index);
			double dist = LocationUtils.horzDistanceFast(center, gridLoc);
			distTrack.addValue(dist);
			gridToCubeIDMap.put(index, id);
			cubeIdToGridMap.put(id, index);
		}
		System.out.println("Distance stats: "+distTrack);
		
		File[] simDirs = simsDir.listFiles();
		for (int year=minYear; year<=maxYear; year++) {
			String starts = prefix+year;
			File match = null;
			for (File simDir : simDirs) {
				String name = simDir.getName();
				if (name.startsWith(starts) && name.contains(contains)) {
					match = simDir;
					break;
				}
			}
			System.out.println("*** "+year+" ***");
			if (match == null) {
				System.out.println("\tno match");
				System.out.println("************");
				continue;
			}
			System.out.println("\t"+match.getName());
			File resultsFile = new File(match, "results_complete.bin");
			if (!resultsFile.exists()) {
				System.out.println("\tno results");
				System.out.println("************");
				continue;
			}
			System.out.println("\thas results, processsing...");
			Map<Integer, IncrementalMagFreqDist> mfds = new HashMap<>();
			MFDCalcCallback call = new MFDCalcCallback(gridReg, mfds);
			ETAS_CatalogIteration.processCatalogs(resultsFile, call);
			int numNonZero = 0;
			for (Integer id : cubeIdToGridMap.keySet()) {
				CSVFile<String> csv = cubeCSVs.get(id);
				Integer gridIndex = cubeIdToGridMap.get(id);
				IncrementalMagFreqDist mfd = mfds.get(gridIndex);
				if (mfd == null) {
					// nothing in this cube
					if (csv == null)
						continue;
					mfd = refMFD;
				} else {
					mfd.scale(1d/(double)call.catalogCount);
					numNonZero++;
				}
				
				if (csv == null) {
					csv = new CSVFile<>(true);
					csv.addLine(header);
					for (int year2=minYear; year2<year; year++) {
						// fill in any empty prior years with zeros
						List<String> line = new ArrayList<>();
						line.add(year+"");
						for (Point2D pt : refMFD)
							line.add((float)pt.getY()+"");
						csv.addLine(line);
					}
					cubeCSVs.put(id, csv);
				}
				
				List<String> line = new ArrayList<>();
				line.add(year+"");
				for (Point2D pt : mfd)
					line.add((float)pt.getY()+"");
				csv.addLine(line);
			}
			System.out.println("\t"+numNonZero+"/"+cubeIdToGridMap.size()+" have values");
			System.out.println("************");
		}
		
		System.out.println("Writing CSVs");
		for (Integer id : cubeCSVs.keySet()) {
			CSVFile<String> csv = cubeCSVs.get(id);
			csv.writeToFile(new File(outputDir, "cube_"+id+".csv"));
		}
		System.out.println("DONE");
	}
	
	static double mfdMinMag = 2.55;
	static double mfdDelta = 0.1;
	static int mfdNumMag = 66;
	
	private static class MFDCalcCallback implements Callback {
		
		private GriddedRegion gridReg;
		private Map<Integer, IncrementalMagFreqDist> mfdMap;
		private int catalogCount = 0;
		private int skipped = 0;

		public MFDCalcCallback(GriddedRegion gridReg, Map<Integer, IncrementalMagFreqDist> mfdMap) {
			this.gridReg = gridReg;
			this.mfdMap = mfdMap;
			
		}

		@Override
		public void processCatalog(ETAS_Catalog catalog, int index) {
			catalogCount++;
			
			for (ETAS_EqkRupture rup : catalog) {
				Location hypo = rup.getHypocenterLocation();
				int gridIndex = gridReg.indexForLocation(hypo);
				if (gridIndex < 0) {
					skipped++;
					continue;
				}
				IncrementalMagFreqDist mfd = mfdMap.get(gridIndex);
				if (mfd == null) {
					mfd = new IncrementalMagFreqDist(mfdMinMag, mfdNumMag, mfdDelta);
					mfdMap.put(gridIndex, mfd);
				}
				mfd.add(mfd.getClosestXIndex(rup.getMag()), 1d);
			}
		}
		
	}

}
