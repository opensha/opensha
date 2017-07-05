package scratch.UCERF3.utils.finiteFaultMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.Shaw_2009_ModifiedMagAreaRel;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.parsers.UCERF3_CatalogParser;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.GriddedSurfaceImpl;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Loads in Jeanne's UCERF3 rupture surfaces and maps to ObsEqkRupture objects. It attempts to load surfaces as an evenly
 * gridded surface but must load some arbitrarily gridded surfaces for strange ruptures.
 * @author kevin
 *
 */
public class JeanneFileLoader {
	
	private static final boolean D = true;
	
	public static List<ObsEqkRupture> loadFiniteRups(File finiteFile, List<? extends ObsEqkRupture> inputRups)
			throws IOException {
		// first load in all rupture surfaces
		Map<String, List<Location>> finiteLocs = Maps.newHashMap();
		BufferedReader read = new BufferedReader(new FileReader(finiteFile));
		String line;
		while ((line = read.readLine()) != null) {
			line = line.trim();
			if (line.startsWith("UCERF3") || line.isEmpty())
				// header
				continue;
			// remove double spaces
			while (line.contains("  "))
				line = line.replaceAll("  ", " ");
			String[] split = line.split(" ");
			Preconditions.checkState(split.length == 4);
			double depth = Double.parseDouble(split[2]);
			if (depth == -0)
				depth = 0;
			depth = Math.round(depth*1000d)/1000d;
			Location loc = new Location(Double.parseDouble(split[0]), Double.parseDouble(split[1]), depth);
			String id = split[3];
			
			List<Location> locs = finiteLocs.get(id);
			if (locs == null) {
				locs = Lists.newArrayList();
				finiteLocs.put(id, locs);
			}
			locs.add(loc);
		}
		read.close();
		
		List<ObsEqkRupture> matches = Lists.newArrayList();
		for (String id : finiteLocs.keySet()) {
			if (D) System.out.println("Processing "+id);
			RuptureSurface surf;
			try {
				surf = buildSurf(finiteLocs.get(id));
			} catch (Exception e) {
				if (D) System.err.println("Failed to create evenly gridded, doing arbitrary: "+e.getMessage());
				LocationList locList = new LocationList();
				locList.addAll(finiteLocs.get(id));
				surf = new ArbitrarilyDiscretizedSurface(locList, Double.NaN);
			}
			ObsEqkRupture rup = getForID(id, inputRups, surf);
			if (rup != null) {
				rup.setRuptureSurface(surf);
				matches.add(rup);
			}
			if (D) System.out.println("Match: "+getRupStr(rup));
			if (D) System.out.println("***********************************");
//			if (id.equals("187203261406"))
//				System.exit(0);
		}
		
		return matches;
	}
	
	private static DateFormat printDateFormat = new SimpleDateFormat("yyyy/MM/dd kk:mm");
	
	public static String getRupStr(ObsEqkRupture rup) {
		if (rup == null)
			return "(None)";
		return printDateFormat.format(new Date(rup.getOriginTime()))+": M"+rup.getMag();
	}
	
	private static DateFormat idDateFormat = new SimpleDateFormat("yyyyMMddkkmm");
	public static final TimeZone utc = TimeZone.getTimeZone("UTC");
	static {
		idDateFormat.setTimeZone(utc);
	}
	private static MagAreaRelationship magArea = new Shaw_2009_ModifiedMagAreaRel();
	
	private static ObsEqkRupture getForID(String id, List<? extends ObsEqkRupture> inputRups, RuptureSurface surf) {
		for (ObsEqkRupture rup : inputRups)
			if (rup.getEventId().equals(id))
				return rup;
		// now try by date
		if (id.length() != 12)
			return null;
		Date date;
		try {
			date = idDateFormat.parse(id);
		} catch (ParseException e) {
			System.err.println("Date parse error: "+e.getMessage());
			return null;
		}
		
		// look for exact match
		long t1 = date.getTime();
		long minDelta = Long.MAX_VALUE;
		List<Long> deltas = Lists.newArrayList();
		List<ObsEqkRupture> rups = Lists.newArrayList();
		rups.addAll(inputRups);
		for (ObsEqkRupture rup : rups) {
			long delta = t1 - rup.getOriginTime();
			if (delta < 0l)
				delta = -delta;
			if (delta == 0l)
				return rup;
			if (delta < minDelta)
				minDelta = delta;
			deltas.add(delta);
		}
		double estMag = magArea.getMedianMag(surf.getArea());
		if (D) System.out.println("No exact match. Min delta: "+minDelta+". Date: "+printDateFormat.format(date)
				+" Est Mag: "+estMag);
		List<ComparablePairing<Long, ObsEqkRupture>> pairings = ComparablePairing.build(deltas, rups);
		Collections.sort(pairings);
		double closestMag = Double.POSITIVE_INFINITY;
		ObsEqkRupture match = null;
		for (ComparablePairing<Long, ObsEqkRupture> pairing : pairings) {
			long delta = pairing.getComparable();
			ObsEqkRupture rup = pairing.getData();
			if (delta > 86400000)
				// more than a day
				break;
			double magDelta = Math.abs(estMag - rup.getMag());
			if (D) System.out.println("\tCandidate: "+getRupStr(rup)+" (Time delta="+delta+", Mag delta: "+magDelta+")");
			if (delta < 100000 && magDelta < 0.5 && !id.equals("187203261406"))
				return rup;
			if (magDelta < closestMag) {
				closestMag = magDelta;
				match = rup;
			}
		}
		
		if (D) System.out.println("\tMatching to rupture by mag: "+getRupStr(match));
		
		return match;
	}
	
	/**
	 * Creates an EvenlyGriddedSurface from a gridded (but not sorted) list of locations. Does not conform
	 * to Aki/Richards convention. Fails if arbitrarily discretized
	 * @param locs
	 * @return
	 */
	private static EvenlyGriddedSurface buildSurf(List<Location> locs) {
		// bin by depth
		Map<Double, List<Location>> depthBinned = Maps.newHashMap();
		for (Location loc : locs) {
			Double depth = loc.getDepth();
			List<Location> depthLocs = depthBinned.get(depth);
			
			if (depthLocs == null) {
				depthLocs = Lists.newArrayList();
				depthBinned.put(depth, depthLocs);
			}
			
			depthLocs.add(loc);
		}
		
		int rows = depthBinned.size();
		if (D) System.out.println("Found "+rows+" unique depths");
		int cols = -1;
		List<Double> depths = Lists.newArrayList(depthBinned.keySet());
		Collections.sort(depths);
		List<List<Location>> locsByRow = Lists.newArrayList();
		for (int row=0; row<rows; row++) {
			Double depth = depths.get(row);
			List<Location> locsAtDepth = depthBinned.get(depth);
			if (cols < 0)
				cols = locsAtDepth.size();
			else
				Preconditions.checkState(cols == locsAtDepth.size(),
					"Gridding columns mismatch. Expected %s got %s, r=%s, d=%s", cols, locsAtDepth.size(), row, depth);
			locsByRow.add(locsAtDepth);
		}
		Preconditions.checkState(locsByRow.size() == rows);
		Preconditions.checkState(cols > 1, "Must have at least 2 columns per row");
		
		// now sort columns
		
		// determine if we should sort by lat or lon
		double latDelta = 0d;
		double lonDelta = 0d;
		List<Location> firstRow = locsByRow.get(0);
		for (int i=0; i<cols; i++) {
			Location l1 = firstRow.get(i);
			for (int j=i+1; j<cols; j++) {
				Location l2 = firstRow.get(j);
				latDelta = Math.max(latDelta, Math.abs(l1.getLatitude() - l2.getLatitude()));
				lonDelta = Math.max(lonDelta, Math.abs(l1.getLongitude() - l2.getLongitude()));
			}
		}
		
		LocComparator comp = new LocComparator(latDelta > lonDelta);
		for (List<Location> rowLocs : locsByRow)
			Collections.sort(rowLocs, comp);
		
		double gridSpacing = LocationUtils.horzDistanceFast(firstRow.get(0), firstRow.get(1));
		if (D) System.out.println("Surface size: "+rows+" x "+cols+". Spacing: "+gridSpacing);
		
		GriddedSurfaceImpl surf = new GriddedSurfaceImpl(rows, cols, gridSpacing);
		for (int row=0; row<rows; row++) {
			List<Location> rowLocs = locsByRow.get(row);
			for (int col=0; col<cols; col++)
				surf.set(row, col, rowLocs.get(col));
		}
		
		return surf;
	}
	
	public static class LocComparator implements Comparator<Location> {
		
		private boolean latSort;
		public LocComparator(boolean latSort) {
			this.latSort = latSort;
		}

		@Override
		public int compare(Location o1, Location o2) {
			Double v1, v2;
			if (latSort) {
				v1 = o1.getLatitude();
				v2 = o2.getLatitude();
			} else {
				v1 = o1.getLongitude();
				v2 = o2.getLongitude();
			}
			return v1.compareTo(v2);
		}
		
	}

	public static void main(String[] args) throws IOException {
		File finiteFile = new File("/home/kevin/OpenSHA/UCERF3/historical_finite_fault_mapping/UCERF3_finite.dat");
		ObsEqkRupList inputRups = UCERF3_CatalogParser.loadCatalog(
				new File("/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/EarthquakeCatalog/ofr2013-1165_EarthquakeCat.txt"));
		
		loadFiniteRups(finiteFile, inputRups);
	}

}
