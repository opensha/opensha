package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
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
import com.google.common.collect.ImmutableList;

public class SectionDistanceAzimuthCalculator {

	private List<? extends FaultSection> subSects;
	
	private double[][] distCache;
	private double[][] azCache;
	private Map<Integer, RuptureSurface> sectSurfs;

	public SectionDistanceAzimuthCalculator(List<? extends FaultSection> subSects) {
		this.subSects = ImmutableList.copyOf(subSects);
		sectSurfs = new HashMap<>();
		for (FaultSection subSect : subSects)
			sectSurfs.put(subSect.getSectionId(), subSect.getFaultSurface(1d, false, false));
		distCache = new double[subSects.size()][];
		azCache = new double[subSects.size()][];
	}
	
	private RuptureSurface getSurface(int id) {
		RuptureSurface surf = sectSurfs.get(id);
		if (surf == null) {
			FaultSection sect = subSects.get(id);
			Preconditions.checkState(id == sect.getSectionId(), "Section IDs are not indexes");
			surf = sect.getFaultSurface(1d, false, false);
			sectSurfs.putIfAbsent(id, surf);
		}
		return surf;
	}
	
	public List<? extends FaultSection> getSubSections() {
		return subSects;
	}
	
	public boolean isDistanceCached(int id1, int id2) {
		if (id1 == id2)
			return false;
		if (id2 < id1) {
			// swap them
			int tmp = id1;
			id1 = id2;
			id2 = tmp;
		}
		return distCache[id1] != null && Double.isFinite(distCache[id1][calcDistIndexOffset(id1, id2)]);
	}
	
	public synchronized void setDistance(int id1, int id2, double dist) {
		if (id1 == id2)
			return;
		if (id2 < id1) {
			// swap them
			int tmp = id1;
			id1 = id2;
			id2 = tmp;
		}
		if (distCache[id1] == null) {
			double[] cache = new double[subSects.size()-id1];
			for (int i=0; i<cache.length; i++)
				cache[i] = Double.NaN;
			distCache[id1] = cache;
		}
		distCache[id1][calcDistIndexOffset(id1, id2)] = dist;
	}
	
	public double getDistance(FaultSection sect1, FaultSection sect2) {
		return getDistance(sect1.getSectionId(), sect2.getSectionId());
	}
	
	private static int calcDistIndexOffset(int id1, int id2) {
		Preconditions.checkState(id1 < id2);
		return id2 - (id1 + 1);
	}
	
	public double getDistance(int id1, int id2) {
		if (id1 == id2)
			return 0d;
		if (id2 < id1) {
			// swap them
			int tmp = id1;
			id1 = id2;
			id2 = tmp;
		}
		int offset = calcDistIndexOffset(id1, id2);
		
		if (distCache[id1] == null) {
			synchronized (distCache) {
				if (distCache[id1] == null) {
					double[] cache = new double[subSects.size()-id1];
					for (int i=0; i<cache.length; i++)
						cache[i] = Double.NaN;
					distCache[id1] = cache;
				}
			}
		} else if (Double.isFinite(distCache[id1][offset])) {
			return distCache[id1][offset];
		}
		RuptureSurface surf1 = getSurface(id1);
		Preconditions.checkNotNull(surf1);
		RuptureSurface surf2 = getSurface(id2);
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
		distCache[id1][offset] = minDist;
		return minDist;
	}
	
	public synchronized void setAzimuth(int id1, int id2, double azimuth) {
		if (id1 == id2)
			return;
		if (azCache[id1] == null) {
			double[] cache = new double[subSects.size()];
			for (int i=0; i<cache.length; i++)
				cache[i] = Double.NaN;
			azCache[id1] = cache;
		}
		azCache[id1][id2] = azimuth;
	}
	
	public double getAzimuth(FaultSection sect1, FaultSection sect2) {
		return getAzimuth(sect1.getSectionId(), sect2.getSectionId());
	}
	
	public boolean isAzimuthCached(int id1, int id2) {
		return azCache[id1] != null && Double.isFinite(azCache[id1][id2]);
	}
	
	public double getAzimuth(int id1, int id2) {
		if (id1 == id2)
			return Double.NaN;
		if (azCache[id1] == null) {
			synchronized (azCache) {
				if (azCache[id1] == null) {
					double[] cache = new double[subSects.size()];
					for (int i=0; i<cache.length; i++)
						cache[i] = Double.NaN;
					azCache[id1] = cache;
					
				}
			}
		} else if (Double.isFinite(azCache[id1][id2])) {
			return azCache[id1][id2];
		}
		
		RuptureSurface surf1 = getSurface(id1);
		Preconditions.checkNotNull(surf1);
		RuptureSurface surf2 = getSurface(id2);
		Preconditions.checkNotNull(surf2);
		
		Location loc1 = GriddedSurfaceUtils.getSurfaceMiddleLoc(surf1);
		Location loc2 = GriddedSurfaceUtils.getSurfaceMiddleLoc(surf2);
		azCache[id1][id2] = LocationUtils.azimuth(loc1, loc2);
		return azCache[id1][id2];
	}
	
	public int getNumCachedDistances() {
		int count = 0;
		for (double[] cache : distCache) {
			if (cache == null)
				continue;
			for (double val : cache)
				if (Double.isFinite(val))
					count++;
		}
		return count;
	}
	
	public int getNumCachedAzimuths() {
		int count = 0;
		for (double[] cache : azCache) {
			if (cache == null)
				continue;
			for (double val : cache)
				if (Double.isFinite(val))
					count++;
		}
		return count;
	}
	
	public void writeCacheFile(File cacheFile) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("ID1", "ID2", "Distance", "Azimuth");
		int numDist = 0;
		int numAz = 0;
		for (int id1=0; id1<subSects.size(); id1++) {
			for (int id2=0; id2<subSects.size(); id2++) {
				boolean distCached = isDistanceCached(id1, id2);
				boolean azCached = isAzimuthCached(id1, id2);
				if (distCached || azCached) {
					List<String> line = new ArrayList<>();
					line.add(id1+"");
					line.add(id2+"");
					if (distCached) {
						line.add(getDistance(id1, id2)+"");
						numDist++;
					} else {
						line.add("");
					}
					if (azCached) {
						line.add(getAzimuth(id1, id2)+"");
						numAz++;
					} else {
						line.add("");
					}
					csv.addLine(line);
				}
			}
		}
		csv.writeToFile(cacheFile);
		System.out.println("Wrote cache file for "+numDist+" distances and "+numAz+" azimuths");
	}
	
	public void loadCacheFile(File cacheFile) throws IOException {
		CSVFile<String> csv = CSVFile.readFile(cacheFile, true);
		int numAz = 0;
		int numDist = 0;
		for (int row=1; row<csv.getNumRows(); row++) {
			int id1 = csv.getInt(row, 0);
			int id2 = csv.getInt(row, 1);
			if (id1 == id2)
				continue;
			
			String distStr = csv.get(row, 2);
			if (!distStr.isEmpty()) {
				setDistance(id1, id2, Double.parseDouble(distStr));
				numDist++;
			}
			String azStr = csv.get(row, 3);
			if (!azStr.isEmpty()) {
				setAzimuth(id1, id2, Double.parseDouble(azStr));
				numAz++;
			}
		}
		System.out.println("Loaded cache file for "+numDist+" distances and "+numAz+" azimuths");
	}

}
