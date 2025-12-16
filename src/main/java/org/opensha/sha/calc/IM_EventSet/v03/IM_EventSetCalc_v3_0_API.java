package org.opensha.sha.calc.IM_EventSet.v03;

import java.io.File;
import java.util.ArrayList;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.ParameterList;

public interface IM_EventSetCalc_v3_0_API {
	
	/**
	 * Returns the number of sites for the calculation
	 * 
	 * @return
	 */
	public int getNumSites();

	/**
	 * Returns the Location of the ith site.
	 * 
	 * @param i
	 * @return
	 */
	public Location getSiteLocation(int i);

	/**
	 * Returns the ordered site data provider list, or null to not use site data providers
	 * 
	 * @return
	 */
	public OrderedSiteDataProviderList getSiteDataProviders();

	/**
     * Returns the user specified (in the input file) site data for the site
     * or null to try to use site data providers
	 * 
	 * @param i ith site user created
	 * @return list of parameters holding site data
	 */
	public ParameterList getUserSiteData(int i);
	
	/**
	 * Returns the output directory for all results
	 * 
	 * @return
	 */
	public abstract File getOutputDir();
	
	/**
	 * This retrieves the site data parameter list for each site.
     * Each entry in the list corresponds to a single site.
	 * 
	 * @return
	 */
	public ArrayList<ParameterList> getSitesData();
	
	public ArrayList<Site> getSites();

}
