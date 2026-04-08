package org.opensha.sha.calc.sourceFilters;

import org.opensha.commons.data.Site;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;

import java.util.Collection;

/**
 * Utility class for methods relating to sources, ruptures, sites.
 * Filtering, skipping, or otherwise aggregating sets of data for calculations.
 */
public class SourceFilterUtils {
    public static boolean canSkipSource(Collection<SourceFilter> filters, ProbEqkSource source, Site site) {
        if (filters == null || filters.isEmpty())
            return false;
        // source-site distance
        double distance = source.getMinDistance(site);

        for (SourceFilter filter : filters)
            if (filter.canSkipSource(source, site, distance))
                return true;
        return false;
    }

    public static boolean canSkipRupture(Collection<SourceFilter> filters, EqkRupture rupture, Site site) {
		if (filters == null || filters.isEmpty())
			return false;
        for (SourceFilter filter : filters)
            if (filter.canSkipRupture(rupture, site))
                return true;
		return false;
	}
}
