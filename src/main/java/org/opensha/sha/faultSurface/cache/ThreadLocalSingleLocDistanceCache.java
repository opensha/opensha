package org.opensha.sha.faultSurface.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

import org.opensha.commons.geo.Location;

import com.google.common.base.Preconditions;

/**
 * Cache that is unique to each thread, useful for avoiding contention. Each individual thread cache is single-valued.
 * When using this option, you should try to use long-running threads that churn through many tasks (e.g., via an
 * {@link ExecutorService}), rather than single task threads.
 */
public class ThreadLocalSingleLocDistanceCache implements SurfaceDistanceCache {
	
	private CacheEnabledSurface surf;
	private ConcurrentMap<Thread, SingleLocDistanceCache> threadCaches;
	private int prefMaxSize;

	public ThreadLocalSingleLocDistanceCache(CacheEnabledSurface surf, int prefMaxSize) {
		this.surf = surf;
		this.prefMaxSize = prefMaxSize;
		threadCaches = new ConcurrentHashMap<>();
	}
	
	private SurfaceDistanceCache getCache() {
		Thread thread = Thread.currentThread();
		SingleLocDistanceCache cache = threadCaches.get(thread);
		if (cache == null) {
			threadCaches.putIfAbsent(thread, new SingleLocDistanceCache(surf));
			cache = threadCaches.get(thread);
			Preconditions.checkNotNull(cache);
			
			if (threadCaches.size() > prefMaxSize) {
				// clear out any dead threads
				synchronized (threadCaches) {
					List<Thread> toRemove = null;
					for (Thread oThread : threadCaches.keySet()) {
						if (!oThread.isAlive()) {
							if (toRemove == null)
								toRemove = new ArrayList<>();
							toRemove.add(thread);
						}
					}
					if (toRemove != null)
						for (Thread oThread : toRemove)
							threadCaches.remove(oThread);
				}
			}
		}
		return cache;
	}

	@Override
	public SurfaceDistances getSurfaceDistances(Location loc) {
		return getCache().getSurfaceDistances(loc);
	}

	@Override
	public double getQuickDistance(Location loc) {
		return getCache().getQuickDistance(loc);
	}

	@Override
	public synchronized void clearCache() {
		threadCaches.clear();
	}

}
