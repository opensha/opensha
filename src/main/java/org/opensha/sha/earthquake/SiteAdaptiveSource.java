package org.opensha.sha.earthquake;

import org.opensha.commons.data.Site;

/**
 * A {@link ProbEqkSource} that can produce site-dependent variants, e.g., a grid source might increase
 * resolution (supersampling) for nearby sites and use a simpler (faster) representation for far field sites.
 */
public interface SiteAdaptiveSource {
	
	/**
	 * Returns a view of this source that is adapted for the given site. This may return itself if no adaptive 
	 * version exists for the given site.
	 * 
	 * @param site
	 * @return
	 */
	public abstract ProbEqkSource getForSite(Site site);

}
