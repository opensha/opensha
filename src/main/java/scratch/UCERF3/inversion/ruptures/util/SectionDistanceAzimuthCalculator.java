package scratch.UCERF3.inversion.ruptures.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class SectionDistanceAzimuthCalculator {

	private LoadingCache<IDPairing, Double> distCache;
	private LoadingCache<IDPairing, Double> azCache;
	private Map<Integer, RuptureSurface> sectSurfs;

	public SectionDistanceAzimuthCalculator(List<? extends FaultSection> subSects) {
		sectSurfs = new HashMap<>();
		for (FaultSection subSect : subSects)
			sectSurfs.put(subSect.getSectionId(), subSect.getFaultSurface(1d, false, false));
		distCache = CacheBuilder.newBuilder().build(new DistLoader());
		azCache = CacheBuilder.newBuilder().build(new AzLoader());
	}
	
	private class DistLoader extends CacheLoader<IDPairing, Double> {

		@Override
		public Double load(IDPairing pair) throws Exception {
			int id1 = pair.getID1();
			int id2 = pair.getID2();
			if (id1 == id2)
				return 0d;
			
			RuptureSurface surf1 = sectSurfs.get(id1);
			Preconditions.checkNotNull(surf1);
			RuptureSurface surf2 = sectSurfs.get(id2);
			Preconditions.checkNotNull(surf2);
			
			// if the quick distance is less than this value, calculate a full distance
			double quickDistThreshold = 5d*Math.max(surf1.getAveLength(), surf2.getAveLength());
			double minDist = Double.POSITIVE_INFINITY;
			
			for (Location loc : surf1.getPerimeter()) {
				minDist = Math.min(minDist, surf2.getQuickDistance(loc));
				if (minDist < quickDistThreshold)
					break;
			}
			
			if (minDist < quickDistThreshold)
				// do the full calculation
				minDist = surf1.getMinDistance(surf2);
			return minDist;
		}
		
	}
	
	private class AzLoader extends CacheLoader<IDPairing, Double> {

		@Override
		public Double load(IDPairing pair) throws Exception {
			int id1 = pair.getID1();
			int id2 = pair.getID2();
			if (id1 == id2)
				return Double.NaN;
			
			RuptureSurface surf1 = sectSurfs.get(id1);
			Preconditions.checkNotNull(surf1);
			RuptureSurface surf2 = sectSurfs.get(id2);
			Preconditions.checkNotNull(surf2);
			
			Location loc1 = GriddedSurfaceUtils.getSurfaceMiddleLoc(surf1);
			Location loc2 = GriddedSurfaceUtils.getSurfaceMiddleLoc(surf2);
			return LocationUtils.azimuth(loc1, loc2);
		}
		
	}
	
	public double getDistance(FaultSection sect1, FaultSection sect2) {
		return getDistance(sect1.getSectionId(), sect2.getSectionId());
	}
	
	public double getDistance(int id1, int id2) {
		try {
			if (id1 < id2)
				return distCache.get(new IDPairing(id1, id2));
			return distCache.get(new IDPairing(id2, id1));
		} catch (ExecutionException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	public double getAzimuth(FaultSection sect1, FaultSection sect2) {
		return getAzimuth(sect1.getSectionId(), sect2.getSectionId());
	}
	
	public double getAzimuth(int id1, int id2) {
		try {
			return azCache.get(new IDPairing(id1, id2));
		} catch (ExecutionException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	public void writeCacheFile(File cacheFile) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("ID1", "ID2", "Distance", "Azimuth");
		Set<IDPairing> distPairings = distCache.asMap().keySet();
		Set<IDPairing> azPairings = azCache.asMap().keySet();
		HashSet<IDPairing> combPairings = new HashSet<>();
		combPairings.addAll(distPairings);
		combPairings.addAll(azPairings);
//		int distPresent = distCache.
//		List<IDPairing> allPairings = new ArrayList<>(distCache.g);
		for (IDPairing pair : combPairings) {
			List<String> line = new ArrayList<>();
			line.add(pair.getID1()+"");
			line.add(pair.getID2()+"");
			Double dist = distCache.getIfPresent(pair);
			if (dist == null)
				line.add("");
			else
				line.add(dist.toString());
			Double az = azCache.getIfPresent(pair);
			if (az == null)
				line.add("");
			else
				line.add(az.toString());
			csv.addLine(line);
		}
		csv.writeToFile(cacheFile);
		System.out.println("Wrote cache file for "+distPairings.size()
			+" distances and "+azPairings.size()+" azimuths");
	}
	
	public void loadCacheFile(File cacheFile) throws IOException {
		CSVFile<String> csv = CSVFile.readFile(cacheFile, true);
		int numAz = 0;
		int numDist = 0;
		for (int row=1; row<csv.getNumRows(); row++) {
			int id1 = csv.getInt(row, 0);
			int id2 = csv.getInt(row, 1);
			
			IDPairing pair = new IDPairing(id1, id2);
			String distStr = csv.get(row, 2);
			if (!distStr.isEmpty()) {
				distCache.put(pair, Double.parseDouble(distStr));
				numDist++;
			}
			String azStr = csv.get(row, 3);
			if (!azStr.isEmpty()) {
				azCache.put(pair, Double.parseDouble(azStr));
				numAz++;
			}
		}
		System.out.println("Loaded cache file for "+numDist+" distances and "+numAz+" azimuths");
	}

}
