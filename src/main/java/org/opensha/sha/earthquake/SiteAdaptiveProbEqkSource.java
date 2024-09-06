package org.opensha.sha.earthquake;

import org.opensha.commons.data.Site;

/**
 * A {@link ProbEqkSource} that can produce site-dependent variants, likely a grid source that might increase
 * resolution (supersampling) for nearby sites and use a simpler (faster) representation for far field sites.
 */
public abstract class SiteAdaptiveProbEqkSource extends ProbEqkSource {
	
	/**
	 * Returns a view of this source that is adapted for the given site.
	 * 
	 * @param site
	 * @return
	 */
	public abstract ProbEqkSource getForSite(Site site);

}
