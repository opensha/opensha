package org.opensha.commons.eq.cat.filters;

import org.opensha.commons.eq.cat.MutableCatalog;

/**
 * This interface guarantees some basic access to all types of filters and
 * provides some common error messages.
 *
 * Created on Feb 28, 2005
 * 
 * @author P. Powers
 * @version $Id: CatalogFilter.java 7478 2011-02-15 04:56:25Z pmpowers $
 */
public interface CatalogFilter {

    /** Generic filter error . */
    public static final int FILTER_ERROR = 0;
    /** Empty result set message */
    public static final int FILTER_ERROR_NO_RESULT = 1;
    /** Loading source file message */
    public static final int FILTER_STATUS_IDLE = 2;
    /** Processing filter message */
    public static final int FILTER_STATUS_WORKING = 3;

    /**
     * Processes a given <code>MutableCatalog</code> against a filter. Returns
     * an array of valid event indices. Returns null if filter produces no results.
     * 
     * @param catalog to process
     * @return array of event indices
     */
    public int[] process(MutableCatalog catalog);
    
}
