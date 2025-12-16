package org.opensha.sha.calc.IM_EventSet.gui;

import java.io.File;
import java.util.ArrayList;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.IM_EventSet.AbstractIMEventSetCalc;
import org.opensha.sha.calc.IM_EventSet.IMEventSetCalcAPI;

/**
 * Implementation of the IM Event Set Calculator API for use in the GUI application.
 * This allows us to structure, parse, and pass site data and outputs for the writers.
 */
public class IMEventSetCalcGUIImpl implements IMEventSetCalcAPI {
	
	private ArrayList<Site> sites;
	private ArrayList<ParameterList> userSitesData;
	private ArrayList<ParameterList> sitesData;
	private File outputDir;
	private OrderedSiteDataProviderList providers;
	
	public IMEventSetCalcGUIImpl(ArrayList<Location> locs, ArrayList<ParameterList> userSitesData,
                                 File outputDir, OrderedSiteDataProviderList providers) {
		sites = new ArrayList<Site>();
		for (Location loc : locs) {
			sites.add(new Site(loc));
		}
		this.userSitesData = userSitesData;
		this.outputDir = outputDir;
		this.providers = providers;
	}

	public int getNumSites() {
		return sites.size();
	}

	public File getOutputDir() {
		return outputDir;
	}

	public Location getSiteLocation(int i) {
		return sites.get(i).getLocation();
	}

	public ArrayList<Site> getSites() {
		return sites;
	}

	public ArrayList<ParameterList> getSitesData() {
		if (sitesData == null) {
			sitesData = AbstractIMEventSetCalc.getSitesData(this);
		}
		return sitesData;
	}

	public ParameterList getUserSiteData(int i) {
		return userSitesData.get(i);
	}

}
