package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.SeismicityRegions;

import com.google.common.base.Preconditions;

public class NSHM23_SeisDepthDistributions {
	
	private static final ConcurrentMap<SeismicityRegions, EvenlyDiscretizedFunc> cache = new ConcurrentHashMap<>();
	
	private static final String NSHM23_SD_PATH_PREFIX = "/data/erf/nshm23/seismicity/seis_depth_dists/";
	
	private static String getResourceName(SeismicityRegions region) {
		return NSHM23_SD_PATH_PREFIX+region.name()+".csv";
	}
	
	public static EvenlyDiscretizedFunc load(SeismicityRegions region) throws IOException {
		EvenlyDiscretizedFunc cached = cache.get(region);
		if (cached != null)
			return cached.deepClone();
		
		// need to load it
		String resourceName = getResourceName(region);
		InputStream is = NSHM23_SeisDepthDistributions.class.getResourceAsStream(resourceName);
		Preconditions.checkNotNull(is, "No depth distribution available for seismicity region %s", region);
		
		CSVFile<String> csv = CSVFile.readStream(is, true);
		double minX = csv.getDouble(0, 0);
		double maxX = csv.getDouble(csv.getNumRows()-1, 0);
		int size = csv.getNumRows();
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(minX, maxX, size);
		for (int row=0; row<func.size(); row++) {
			double x = func.getX(row);
			double xTest = csv.getDouble(row, 0);
			Preconditions.checkState((float)x == (float)xTest,
					"Seismicity depth distribution for %s isn't evenly discretized, expected %s at index %s, got %s",
					region, (float)x, row, (float)xTest);
			func.set(row, csv.getDouble(row, 1));
		}
		double sum = func.calcSumOfY_Vals();
		Preconditions.checkState(sum >= 0.99 && sum <= 1.01,
				"Seismicity depth distribution isn't normalied for %s, sum=%s", region, sum);
		if (sum != 1d)
			// make it exact
			func.scale(1d/sum);
		synchronized (cache) {
			cache.putIfAbsent(region, func);
		}
		return func.deepClone();
	}

	public static void main(String[] args) throws IOException {
		load(SeismicityRegions.CONUS_EAST);
		load(SeismicityRegions.CONUS_IMW);
		load(SeismicityRegions.CONUS_PNW);
		load(SeismicityRegions.CONUS_U3_RELM);
	}

}
