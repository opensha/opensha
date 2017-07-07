package org.opensha.sha.earthquake.observedEarthquake.parsers.ngaWest;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.ApproxEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GriddedSurfaceImpl;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;

public class NGAWestParser {

	private static final FilenameFilter polFilter = new FilenameFilter() {

		@Override
		public boolean accept(File dir, String name) {
			return name.endsWith(".POL");
		}
	};

	public static HashMap<Integer, RuptureSurface> loadPolTlls(File dir) throws IOException {
		return loadPolTlls(dir, 0d);
	}

	public static HashMap<Integer, RuptureSurface> loadPolTlls(File dir, double gridSpacing) throws IOException {
		HashMap<Integer, ArrayList<EvenlyGriddedSurface>> surfs = new HashMap<Integer, ArrayList<EvenlyGriddedSurface>>();

		for (File polFile : dir.listFiles(polFilter)) {
			File tllFile = new File(dir, polFile.getName().replace(".POL", ".TLL"));

			if (!tllFile.exists()) {
				System.out.println("No TLL file for "+polFile.getName());
				continue;
			}

			/* -------- load the TLL -------- */

			ArrayList<String> tllLines = FileUtils.loadFile(tllFile.getAbsolutePath());

			String nameLine = tllLines.get(0).trim();
			String originLine = tllLines.get(1);
			String hypoLine = tllLines.get(2);

			// load the name
			int firstSpace = nameLine.indexOf(' ');
			int eqID = Integer.parseInt(nameLine.substring(0, firstSpace));
			nameLine = nameLine.substring(firstSpace).trim();
			String name = "";
			for (int i=0; i<nameLine.length(); i++) {
				char c = nameLine.charAt(i);
				if (c == ' ' && name.endsWith(" "))
					break;
				name += c;
			}
			name = name.trim();

			StringTokenizer origTok = new StringTokenizer(originLine);
			double origLat = Double.parseDouble(origTok.nextToken());
			double origLon = Double.parseDouble(origTok.nextToken());
			Location origin = new Location(origLat, origLon);

			/* -------- load the POL -------- */

			ArrayList<String> polLines = FileUtils.loadFile(polFile.getAbsolutePath());
			polLines.remove(0); // header
			//			int numPts = Integer.parseInt(polLines.remove(0).trim());
			//			Preconditions.checkState(numPts == 5, "numPts should always be 5!");
			polLines.remove(0).trim();

			Location[][] locs = null;

			int size = -1;

			for (int i=0; i<4; i++) {
				String line = polLines.get(i);

				StringTokenizer tok = new StringTokenizer(line.trim());

				if (size < 0) {
					size = tok.countTokens()/3;
					Preconditions.checkState(size>0, "Size is 0 for line: "+line);
					locs = new Location[size][4];
				} else {
					int mySize = tok.countTokens()/3;
					Preconditions.checkState(size == mySize, "inconsistent sizes for "+polFile.getName()
							+" (expected="+size+", actual="+mySize+")\nline: "+line);
				}

				for (int j=0; j<size; j++) {
					double kmEast = Double.parseDouble(tok.nextToken());
					double kmNorth = Double.parseDouble(tok.nextToken());
					double dep = -Double.parseDouble(tok.nextToken());

					// move north
					Location loc = LocationUtils.location(origin, 0, kmNorth);
					// move east
					loc = LocationUtils.location(loc, Math.PI/2d, kmEast);

					locs[j][i] = new Location(loc.getLatitude(), loc.getLongitude(), dep);
				}
			}

			// if all locs are the same, we can just skip this as there is actually no finite rupture
			boolean equal = true;
			Location prev = null;
			for (Location[] locArray : locs) {
				for (Location loc : locArray) {
					if (prev == null) {
						prev = loc;
						continue;
					}
					equal = equal && loc.getLatitude() == prev.getLatitude() && loc.getLongitude() == prev.getLongitude();
					prev = loc;
					if (!equal)
						break;
				}
				if (!equal)
					break;
			}
			if (equal)
				continue;


			GriddedSurfaceImpl surface = new GriddedSurfaceImpl(2, size+1, Double.NaN);

			Preconditions.checkState(surface.size()>=4, "surface's size is <4: "+surface.size()+" (dims: 2x"+(size+1)+")");

			for (int i=0; i<size; i++) {
				if (i == 0) {
					surface.set(0, i, locs[i][0]);
					surface.set(1, i, locs[i][3]);
				}
				surface.set(0, i+1, locs[i][1]);
				surface.set(1, i+1, locs[i][2]);
			}

			if (!surfs.containsKey(eqID))
				surfs.put(eqID, new ArrayList<EvenlyGriddedSurface>());

			if (gridSpacing > 0) {
				FaultTrace upper = new FaultTrace("upper");
				FaultTrace lower = new FaultTrace("lower");

				for (int i=0; i<surface.getNumCols(); i++) {
					upper.add(surface.get(0, i));
					lower.add(surface.get(1, i));
				}
				ApproxEvenlyGriddedSurface gridSurf = new ApproxEvenlyGriddedSurface(upper, lower, gridSpacing);
				surfs.get(eqID).add(gridSurf);
			} else {
				surfs.get(eqID).add(surface);
			}
		}

		HashMap<Integer, RuptureSurface> map = new HashMap<Integer, RuptureSurface>();
		for (Integer id : surfs.keySet()) {
			ArrayList<EvenlyGriddedSurface> surfList = surfs.get(id);
			RuptureSurface surf;
			if (surfList.size() == 1) {
				surf = surfList.get(0);
			} else {
				surf = new CompoundSurface(surfList);
			}

			map.put(id, surf);
		}

		return map;
	}

	public static ObsEqkRupList loadNGAWestFiles(File excelFile, File polTllDir) throws IOException {
		return loadNGAWestFiles(excelFile, polTllDir, 0d);
	}

	public static ObsEqkRupList loadNGAWestFiles(File excelFile, File polTllDir, double gridSpacing) throws IOException {
		Preconditions.checkNotNull(excelFile, "Excel file is null!");
		Preconditions.checkArgument(excelFile.exists(), "Excel file doesn't exist!");
		Preconditions.checkArgument(excelFile.isFile(), "Excel file isn't a regular file!");

		Preconditions.checkNotNull(polTllDir, "Pol/Tll directory is null!");
		Preconditions.checkArgument(polTllDir.exists(), "Excel directory doesn't exist!");
		Preconditions.checkArgument(polTllDir.isDirectory(), "Excel directory isn't a directory!");

		// first load the pol/tll files
		HashMap<Integer, RuptureSurface> surfaces = loadPolTlls(polTllDir, gridSpacing);

		ObsEqkRupList rups = new ObsEqkRupList();

		POIFSFileSystem fs = new POIFSFileSystem(new BufferedInputStream(new FileInputStream(excelFile)));
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		HSSFSheet sheet = wb.getSheetAt(0);

		for (int i=1; i<=sheet.getLastRowNum(); i++) {
			HSSFRow row = sheet.getRow(i);
			// make sure this is a valid row first by checking that it has a year entry
			try {
				double test = row.getCell(2).getNumericCellValue();
				if (test <= 0)
					continue;
			} catch (Exception e) {
				continue;
			}

			NGAWestEqkRupture rup = new NGAWestEqkRupture(row, excelFile.getName());
			int id = rup.getId();
			if (surfaces.containsKey(id)) {
				rup.setRuptureSurface(surfaces.get(id));
			}
			Preconditions.checkState(rup.isFiniteRuptureModel() == (rup.getRuptureSurface() != null),
					"Excel sheet & existance of POL/TLL files doesn't match up! EQ: "+id);
			rups.add(rup);
		}

		return rups;
	}

	public static ObsEqkRupList mergeLists(List<ObsEqkRupture> primaryList, List<ObsEqkRupture> secondaryList) {
		ObsEqkRupList list = new ObsEqkRupList();

		// first add the secondary
		list.addAll(secondaryList);

		// now replace events with primary, or add if needed
		for (ObsEqkRupture primary : primaryList) {
			int ind = getBestMatchIndex(primary, secondaryList);
			if (ind < 0) {
				list.add(primary);
			} else {
				// see if we've already replaced one here
				if (list.get(ind) != secondaryList.get(ind)) {
					System.out.println("Duplicate replace found!!! just adding the second one");
					list.add(primary);
				} else {
					list.set(ind, primary);
				}
			}
		}

		list.sortByOriginTime();

		return list;
	}

	private static SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private static String getRupStr(ObsEqkRupture rup) {
		Location hypo = rup.getHypocenterLocation();
		return "Mag:\t"+rup.getMag()+"\tDate:\t"+df.format(new Date(rup.getOriginTime()))
				+"\tHypo:\t"+hypo.getLatitude()+","+hypo.getLongitude()+","+hypo.getDepth();
	}

	public static int getBestMatchIndex(ObsEqkRupture rup, List<ObsEqkRupture> candidates) {
		int bestIndex = -1;
		double bestScore = Double.MAX_VALUE;

		for (int i=0; i<candidates.size(); i++) {
			double score = getMatchScore(rup, candidates.get(i));

			if (score < bestScore) {
				bestIndex = i;
				bestScore = score;
			}
			
			if (rup.getEventId().equals("1980.11.08, Mw=7.3, W of Eureka, CA.inp")) {
				ObsEqkRupture cand = candidates.get(i);
				GregorianCalendar cal = cand.getOriginTimeCal();
				if (cal.get(GregorianCalendar.YEAR) == 1980 && cal.get(GregorianCalendar.MONTH) == 10)
					System.out.println("CAND: "+score+" - "+getRupStr(cand));
			}
		}

		//		System.out.println("Getting match for: "+rup);
		//		System.out.println("Best score: "+bestScore);

		//		if (bestScore < 10000 && bestScore >= 100)
		//		if (bestScore < 10000)
		System.out.println("Best match score for rupture "+rup.getEventId()+": "+bestScore);
		ObsEqkRupture match = candidates.get(bestIndex);
		System.out.println("Orig: "+getRupStr(rup));
		System.out.println("Best: "+getRupStr(match));
		double[] matchDeltas = getMatchDeltas(rup, match);
		System.out.println("Deltas:\t"+matchDeltas[0]+"\t"+matchDeltas[1]+"\t"+matchDeltas[2]);
		System.out.println("Deltas^2:\t"+matchDeltas[0]*matchDeltas[0]+"\t"
				+matchDeltas[1]*matchDeltas[1]+"\t"+matchDeltas[2]*matchDeltas[2]);

		if (bestScore < 160)
			return bestIndex;
		//		System.err.println("Warning: no match found for rup: "+rup);
		//		System.err.println("best score: "+bestScore);
		return -1;
	}

	private static double[] getMatchDeltas(ObsEqkRupture rup1, ObsEqkRupture rup2) {
		// high is bad, low is good
		double magDelta = Math.abs(rup1.getMag() - rup2.getMag());
		// magnitude is important!!!!!
		magDelta *= 10;
		// convert to days
		double timeDelta = Math.abs((rup1.getOriginTime() - rup2.getOriginTime()) / 86400000d);
		timeDelta *= 12;

		if (rup1.getHypocenterLocation() == null)
			return null;

		// difference in KMs
		double distanceDelta = LocationUtils.horzDistanceFast(rup1.getHypocenterLocation(), rup2.getHypocenterLocation());
		// least important
		distanceDelta /= 2;
		
		double[] matches = {magDelta, timeDelta, distanceDelta};
		return matches;
	}

	private static double getMatchScore(ObsEqkRupture rup1, ObsEqkRupture rup2) {
		double[] matches = getMatchDeltas(rup1, rup2);
		if (matches == null)
			return Double.MAX_VALUE;

		double squaresFit = matches[0]*matches[0] + matches[1]*matches[1] + matches[2]*matches[2];

		return squaresFit;
	}

	public static void main(String[] args) throws IOException {
		Location zoo = new Location(38.92875, -77.04927);
		Location quake = new Location(37.936, -77.933);
		double dist = LocationUtils.linearDistance(zoo, quake);
		System.out.println("Distance from epicenter to zoo (KM): "+dist);
		double pTimeTravel = dist / 8d;
		double sTimeTravel = dist / 3.5d;
		System.out.println("pTimeTravel (assuming 8km/s p wave speed): "+pTimeTravel);
		System.out.println("sTimeTravel (assuming 3.5km/s s wave speed): "+sTimeTravel);
		System.out.println("delta: "+(sTimeTravel-pTimeTravel));
		System.exit(0);
		File polTllDir = new File("src"+File.separator+"resources"+File.separator+"data"+File.separator+"ngaWest");
		File excelFile = new File(polTllDir, "EQ.V8.xls");


	}

}
