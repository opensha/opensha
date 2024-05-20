package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipException;

import org.dom4j.DocumentException;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import scratch.UCERF3.utils.U3FaultSystemIO;

public class SectionDistanceAzimuthCalculator implements OpenSHA_Module {

	private List<? extends FaultSection> subSects;
	
	private double[][] distCache;
	private double[][] azCache;
	private ConcurrentMap<Integer, RuptureSurface> sectSurfs;
	
	public static final double SURF_DISCRETIZATION_DEFAULT = 1d;
	private double surfDiscretization = SURF_DISCRETIZATION_DEFAULT;
	
	private static boolean CREEP_REDUCED = false;

	public SectionDistanceAzimuthCalculator(List<? extends FaultSection> subSects) {
		this.subSects = ImmutableList.copyOf(subSects);
		sectSurfs = new ConcurrentHashMap<>();
		distCache = new double[subSects.size()][];
		azCache = new double[subSects.size()][];
	}
	
	public void setDiscretization(double surfDicretization) {
		sectSurfs.clear();
		this.surfDiscretization = surfDicretization;
	}
	
	private RuptureSurface getSurface(int id) {
		RuptureSurface surf = sectSurfs.get(id);
		if (surf == null) {
			FaultSection sect = subSects.get(id);
			Preconditions.checkState(id == sect.getSectionId(), "Section IDs are not indexes");
			surf = sect.getFaultSurface(surfDiscretization, false, CREEP_REDUCED);
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
	
	CSVFile<String> buildCache() {
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
		System.out.println("Built cache file for "+numDist+" distances and "+numAz+" azimuths");
		return csv;
	}
	
	public void writeCacheFile(File cacheFile) throws IOException {
		CSVFile<String> csv = buildCache();
		csv.writeToFile(cacheFile);
	}
	
	public void loadCacheFile(File cacheFile) throws IOException {
		CSVFile<String> csv = CSVFile.readFile(cacheFile, true);
		loadCache(csv);
	}
	
	public String getDefaultCacheFileName() {
		return "dist_az_cache_"+getUniqueSectCacheFileStr(subSects)+".csv";
	}

	/**
	 * Creates a unique cache file name string for the given subsections. Will capture any changes to total subsection
	 * count, total number of trace locations, and total subsection area.
	 * 
	 * @param subSects
	 * @return
	 */
	public static String getUniqueSectCacheFileStr(Collection<? extends FaultSection> subSects) {
		int numLocs = 0;
		double area = 0;
		for (FaultSection sect : subSects) {
			numLocs += sect.getFaultTrace().size();
			area += sect.getArea(CREEP_REDUCED);
		}
		return subSects.size()+"_sects_"+numLocs+"_trace_locs_"+(long)(area+0.5)+"_area";
	}
	
	void loadCache(CSVFile<String> csv) {
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
	
	synchronized void copyCacheFrom(SectionDistanceAzimuthCalculator o) {
		Preconditions.checkState(o.subSects.size() == subSects.size());
		copyCache(o.distCache, distCache);
		copyCache(o.azCache, azCache);
		sectSurfs.putAll(o.sectSurfs);
	}
	
	private void copyCache(double[][] from, double[][] to) {
		for (int i=0; i<from.length; i++)
			if (from[i] != null)
				to[i] = Arrays.copyOf(from[i], from[i].length);
	}
	
	public static void main(String[] args) throws ZipException, IOException, DocumentException {
		File rupSetFile = new File("/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_ucerf3.zip");
		FaultSystemRupSet rupSet = U3FaultSystemIO.loadRupSet(rupSetFile);
		SectionDistanceAzimuthCalculator calc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
		System.out.println("516=>521: "+calc.getDistance(516, 521));
		System.out.println("516=>522: "+calc.getDistance(516, 522));
	}

	@Override
	public String getName() {
		return "Section Distance-Azimuth Calculator";
	}
	
	/**
	 * @param subSects
	 * @return archivable cache that will be written to/loaded from an archive
	 */
	public static SectionDistanceAzimuthCalculator archivableInstance(List<? extends FaultSection> subSects) {
		return new ArchivableSectionDistAzCalc(subSects);
	}
	
	/**
	 * @param calc
	 * @return archivable copy of this cache that will be written to/loaded from an archive
	 */
	public static SectionDistanceAzimuthCalculator archivableInstance(SectionDistanceAzimuthCalculator calc) {
		ArchivableSectionDistAzCalc archivable = new ArchivableSectionDistAzCalc(calc.subSects);
		archivable.copyCacheFrom(calc);
		return archivable;
	}
	
	private static class ArchivableSectionDistAzCalc extends SectionDistanceAzimuthCalculator implements CSV_BackedModule {

		public ArchivableSectionDistAzCalc(List<? extends FaultSection> subSects) {
			super(subSects);
		}

		@Override
		public String getFileName() {
			return "dist_az_cache.csv";
		}

		@Override
		public CSVFile<?> getCSV() {
			return buildCache();
		}

		@Override
		public void initFromCSV(CSVFile<String> csv) {
			loadCache(csv);
		}
		
	}

}
