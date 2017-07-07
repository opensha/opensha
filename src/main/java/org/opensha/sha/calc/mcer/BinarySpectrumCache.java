package org.opensha.sha.calc.mcer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.Location;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

public class BinarySpectrumCache {
	
	private Map<Location, DiscretizedFunc> cache;
	private File cacheFile;
	
	private BinarySpectrumCache(Map<Location, DiscretizedFunc> cache, File cacheFile) {
		this.cache = cache;
		this.cacheFile = cacheFile;
	}
	
	public static BinarySpectrumCache createEmpty(File cacheFile) {
		 Map<Location, DiscretizedFunc> cache = Maps.newHashMap();
		 return new BinarySpectrumCache(cache, cacheFile);
	}
	
	public static BinarySpectrumCache load(File cacheFile) throws IOException {
		Map<Location, DiscretizedFunc> cache = Maps.newHashMap();
		
		Preconditions.checkNotNull(cacheFile, "File cannot be null!");
		Preconditions.checkArgument(cacheFile.exists(), "File doesn't exist!");

		long len = cacheFile.length();
		Preconditions.checkState(len > 0, "file is empty!");
		Preconditions.checkState(len % 4 == 0, "file size isn't evenly divisible by 4, " +
		"thus not a sequence of double & integer values.");
		
		InputStream is = new BufferedInputStream(new FileInputStream(cacheFile));
		
		DataInputStream in = new DataInputStream(is);

		int size = in.readInt();

		Preconditions.checkState(size > 0, "Size must be > 0!");

		for (int i=0; i<size; i++) {
			double lat = in.readDouble();
			double lon = in.readDouble();
			int arraySize = in.readInt();

			double[] x = new double[arraySize];
			double[] y = new double[arraySize];
			for (int j=0; j<arraySize; j++) {
				x[j] = in.readDouble();
				y[j] = in.readDouble();
			}
			
			Location loc = new Location(lat, lon);
			cache.put(loc, new LightFixedXFunc(x, y));
		}

		in.close();
		
		return new BinarySpectrumCache(cache, cacheFile);
	}
	
	public DiscretizedFunc get(Location loc) {
		return cache.get(loc);
	}
	
	public synchronized void put(Location loc, DiscretizedFunc func) {
		cache.put(loc, func);
	}
	
	public synchronized int size() {
		return cache.size();
	}
	
	public synchronized void writeCache() throws IOException {
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cacheFile)));
		
		out.writeInt(cache.size());
		
		for (Location loc : cache.keySet()) {
			
		}
	}

}
