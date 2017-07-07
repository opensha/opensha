package org.opensha.sha.faultSurface.cache;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ExceptionUtils;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;

/**
 * {@link SurfaceDistanceCache} implementation that can store multiple values simultaneously, optionally with
 * an expiration time. Note that expiration time comes with a small but noticeable performance penalty and is
 * disabled by default.
 * @author kevin
 *
 */
public class MultiDistanceCache implements SurfaceDistanceCache {
	
	private CacheEnabledSurface surf;
	private LoadingCache<Location, SurfaceDistances> distCache;
	private LoadingCache<Location, Double> distXCache;
	
	// this code is just used for collecting statistics
	private static final boolean ENABLE_DEBUG = false;
	private static List<MultiDistanceCache> cachesForDebug;
	static {
		if (ENABLE_DEBUG) cachesForDebug = Lists.newArrayList();
	}
	
	/**
	 * Create cache of size (num available processors)+5, no expiration time.
	 * 
	 * @param surf
	 */
	public MultiDistanceCache(CacheEnabledSurface surf) {
		this(surf, Runtime.getRuntime().availableProcessors()+5);
	}
	
	/**
	 * Create cache of given size, no expiration time.
	 * 
	 * @param surf
	 * @param maxSize
	 */
	public MultiDistanceCache(CacheEnabledSurface surf, int maxSize) {
		this(surf, maxSize, 0, TimeUnit.HOURS);
	}
	
	/**
	 * Create cache of given size and with given expiration time (or 0 to disable expiration).
	 * 
	 * @param surf
	 * @param maxSize
	 * @param expirationTime
	 * @param expirationUnit
	 */
	public MultiDistanceCache(CacheEnabledSurface surf, int maxSize, long expirationTime, TimeUnit expirationUnit) {
		this.surf = surf;
		distCache = buildCache(new DistCacheLoader(), maxSize, expirationTime, expirationUnit);
		distXCache = buildCache(new DistXCacheLoader(), maxSize, expirationTime, expirationUnit);
		
		if (ENABLE_DEBUG) cachesForDebug.add(this);
	}
	
	private final class DistCacheLoader extends CacheLoader<Location, SurfaceDistances> {

		@Override
		public SurfaceDistances load(Location loc) throws Exception {
			return surf.calcDistances(loc);
		}
		
	}
	
	private final class DistXCacheLoader extends CacheLoader<Location, Double> {

		@Override
		public Double load(Location loc) throws Exception {
			return surf.calcDistanceX(loc);
		}
		
	}
	
	private static <E> LoadingCache<Location, E> buildCache(CacheLoader<Location, E> loader,
			int maxSize, long expirationTime, TimeUnit expirationUnit) {
		int concurrencyLevel = maxSize;
		if (concurrencyLevel < 4)
			concurrencyLevel = 4;
		CacheBuilder<Object, Object> build = CacheBuilder.newBuilder();
		build.maximumSize(maxSize);
		build.concurrencyLevel(concurrencyLevel);
		if (expirationTime > 0l && expirationUnit != null)
			build.expireAfterAccess(expirationTime, expirationUnit);
		if (ENABLE_DEBUG)
			build.recordStats();
		return build.build(loader);
	}

	@Override
	public SurfaceDistances getSurfaceDistances(Location loc) {
		try {
			return distCache.get(loc);
		} catch (ExecutionException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}

	@Override
	public double getDistanceX(Location loc) {
		try {
			return distXCache.get(loc);
		} catch (ExecutionException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	public static void printDebugStats() {
		if (!ENABLE_DEBUG)
			return;
		for (MultiDistanceCache cache : cachesForDebug) {
			CacheStats stats = cache.distCache.stats();
			if (stats.hitCount() > 0)
				System.out.println(stats);
		}
	}

}
