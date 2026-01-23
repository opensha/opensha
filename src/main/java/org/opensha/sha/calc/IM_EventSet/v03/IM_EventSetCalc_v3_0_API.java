package org.opensha.sha.calc.IM_EventSet.v03;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.params.filters.SourceFilter;

public interface IM_EventSetCalc_v3_0_API {
	
	/**
	 * Returns the number of sites for the calculation
	 * 
	 * @return
	 */
    int getNumSites();

	/**
	 * Returns the Location of the ith site.
	 * 
	 * @param i
	 * @return
	 */
    Location getSiteLocation(int i);

	/**
     * Returns the user specified (in the input file) site data for the site
     * or null to try to use site data providers
	 * 
	 * @param i ith site user created
	 * @return list of parameters holding site data
	 */
    ParameterList getUserSiteData(int i);
	
	/**
	 * Returns the output directory for all results
	 * 
	 * @return
	 */
    File getOutputDir();
	
	/**
	 * This retrieves the site data parameter list for each site.
     * Each entry in the list corresponds to a single site.
	 * 
	 * @return
	 */
    ArrayList<ParameterList> getSitesData();
	
	ArrayList<Site> getSites();

    List<SourceFilter> getSourceFilters();

}
