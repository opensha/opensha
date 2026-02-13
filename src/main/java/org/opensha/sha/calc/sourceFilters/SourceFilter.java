package org.opensha.sha.calc.sourceFilters;

import org.opensha.commons.data.Site;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.EqkSource;

public interface SourceFilter {
	
	/**
	 * @param source source in question
	 * @param site site in question
	 * @param sourceSiteDistance distance between the source and the site
	 * @return true if this entire source can be skipped for this site, false otherwise
	 */
	public boolean canSkipSource(EqkSource source, Site site, double sourceSiteDistance);
	
	/**
	 * @param rup rupture in question
	 * @param site site in question
	 * @return true if this rupture can be skipped for this site, false otherwise
	 */
	public boolean canSkipRupture(EqkRupture rup, Site site);
	
	/**
	 * @return adjustable parameter list for this filter
	 */
	public ParameterList getAdjustableParams();

}
