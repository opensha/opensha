package org.opensha.sha.faultSurface.cache;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.opensha.sha.faultSurface.CompoundSurface;

/**
 * This class determines which cache should be used by @link CacheEnabledSurface}'s. It is configurable via
 * java properties to tune settings for specific environments. See static members for available properties.
 * <br>
 * <br>Each rupture surface should call the {@link #build(CacheEnabledSurface)} to generate a cache specific to
 * that surface and according to each configurable property.
 * @author kevin
 *
 */
public final class SurfaceCachingPolicy {
	
	/**
	 * The <i>dist.cache.force</i> property is a can be used to force surfaces to use the specified cache.
	 * The value, if set, must be one of the {@link CacheTypes} enum constant names.
	 * when size > 1, exept for {@link CompoundSurface}'s. 
	 */
	public static final String FORCE_TYPE = "dist.cache.force";
	public static enum CacheTypes {
		SINGLE,
		MULTI,
		HYBRID,
		DISABLED
	}
	private static CacheTypes force = null;
	
	/**
	 * This property sets the default cache size. It defaults to <i>Runtime.getRuntime().availableProcessors()+5</i>.
	 */
	public static final String SIZE_PROP = "dist.cache.size";
	private static int size = Runtime.getRuntime().availableProcessors()+5;
	
	/**
	 * This property sets the expiration time for multi caches, or 0 for no expiration.
	 */
	public static final String EXP_TIME_PROP = "dist.cache.exp.time";
	private static long expirationTime = 0; // defaults to no expiration, as it slows things down
	
	/**
	 * This property sets the epiration time unit for multi caches, or null for no expiration. Must be an
	 * enum constant name of {@link TimeUnit}, e.g. HOURS (case sensitive).
	 */
	public static final String EXP_UNIT_PROP = "dist.cache.exp.unit";
	private static TimeUnit expirationUnit = TimeUnit.HOURS;
	
	static {
		loadConfigFromProps();
	}
	
	public static void loadConfigFromProps() {
		// configure from properties if set
		Properties props = System.getProperties();
		if (props.containsKey(FORCE_TYPE))
			force = CacheTypes.valueOf(props.getProperty(FORCE_TYPE));
		if (props.containsKey(SIZE_PROP))
			size = Integer.parseInt(props.getProperty(SIZE_PROP));
		if (props.containsKey(EXP_TIME_PROP))
			expirationTime = Integer.parseInt(props.getProperty(EXP_TIME_PROP));
		if (props.containsKey(EXP_UNIT_PROP)) {
			String val = props.getProperty(EXP_UNIT_PROP);
			if (val.equals("null"))
				expirationUnit = null;
			else
				expirationUnit = TimeUnit.valueOf(val);
		}
	}
	
	/**
	 * Build a cache for the given {@link CacheEnabledSurface}. Returns a {@link HybridDistanceCache} if
	 * the force multi property is set, or if size>1 and it is not a {@link CompoundSurface}. Otherwise a
	 * {@link SingleLocDistanceCache} will be returned (unless size=0 for testing, and a {@link DisabledDistanceCache}
	 * is returned).
	 * @param surf
	 * @return
	 */
	public static SurfaceDistanceCache build(CacheEnabledSurface surf) {
		if (force != null) {
			switch (force) {
			case SINGLE:
				return new SingleLocDistanceCache(surf);
			case MULTI:
				return new MultiDistanceCache(surf, size, expirationTime, expirationUnit);
			case HYBRID:
				return new HybridDistanceCache(surf, size, expirationTime, expirationUnit);
			case DISABLED:
				return new DisabledDistanceCache(surf);

			default:
				throw new IllegalStateException("Unkown forced cache type: "+force);
			}
		}
		boolean multi = (size > 1 && !(surf instanceof CompoundSurface));
		if (multi)
//			return new MultiDistanceCache(surf, size, expirationTime, expirationUnit);
			return new HybridDistanceCache(surf, size, expirationTime, expirationUnit);
		if (size == 0)
			return new DisabledDistanceCache(surf);
		return new SingleLocDistanceCache(surf);
	}
	
	/**
	 * Returns a string representation of the current caching policy.
	 * 
	 * @return
	 */
	public static String getPolicyStr() {
		String expUnitStr;
		if (expirationUnit == null)
			expUnitStr = "null";
		else
			expUnitStr = expirationUnit.name();
		String forceStr;
		if (force == null)
			forceStr = "null";
		else
			forceStr = force.name();
		return "force="+forceStr+", size="+size+", expTime="+expirationTime+", expUnit="+expUnitStr;
	}

}
