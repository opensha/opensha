package org.opensha.sha.calc.IM_EventSet.v03.gui;

import java.io.File;
import java.util.ArrayList;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.geo.Location;
import org.opensha.sha.calc.IM_EventSet.v03.IM_EventSetCalc_v3_0;
import org.opensha.sha.calc.IM_EventSet.v03.IM_EventSetCalc_v3_0_API;

public class GUICalcAPI_Impl implements IM_EventSetCalc_v3_0_API {
	
	private ArrayList<Site> sites;
	private ArrayList<ArrayList<SiteDataValue<?>>> userSitesData;
	private ArrayList<ArrayList<SiteDataValue<?>>> sitesData;
	private File outputDir;
	private OrderedSiteDataProviderList providers;
	
	public GUICalcAPI_Impl(ArrayList<Location> locs, ArrayList<ArrayList<SiteDataValue<?>>> userSitesData,
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

	public OrderedSiteDataProviderList getSiteDataProviders() {
		return providers;
	}

	public Location getSiteLocation(int i) {
		return sites.get(i).getLocation();
	}

	public ArrayList<Site> getSites() {
		return sites;
	}

	public ArrayList<ArrayList<SiteDataValue<?>>> getSitesData() {
		if (sitesData == null) {
			sitesData = IM_EventSetCalc_v3_0.getSitesData(this);
		}
		return sitesData;
	}

	public ArrayList<SiteDataValue<?>> getUserSiteDataValues(int i) {
		return userSitesData.get(i);
	}

}
