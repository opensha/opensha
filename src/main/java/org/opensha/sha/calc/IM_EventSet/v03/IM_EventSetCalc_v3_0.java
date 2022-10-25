package org.opensha.sha.calc.IM_EventSet.v03;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;

public abstract class IM_EventSetCalc_v3_0 implements IM_EventSetCalc_v3_0_API {
	
	public static Logger logger = Logger.getLogger("IMv3Log");
	
	public static void initLogger(Level level) {
		Logger parent = logger;
		while (parent != null) {
			for (Handler handler : parent.getHandlers())
				handler.setLevel(level);
			parent.setLevel(level);
			parent = parent.getParent();
		}
		logger.setLevel(level);
	}
	
	public static final float MIN_SOURCE_DIST = 200;

	/**
	 * This should ONLY be accessed through the getter method as it may
	 * be uninitialized
	 */
	private ArrayList<Site> sites = null;

	private ArrayList<ArrayList<SiteDataValue<?>>> sitesData = null;

	public IM_EventSetCalc_v3_0() {

	}

	public final ArrayList<Site> getSites() {
		if (sites == null) {
			logger.log(Level.FINE, "Generating site list");
			sites = new ArrayList<Site>();
			for (int i=0; i<getNumSites(); i++) {
				Site site = new Site(getSiteLocation(i));
				sites.add(site);
			}
		}
		return sites;
	}
	
	public static ArrayList<ArrayList<SiteDataValue<?>>> getSitesData(IM_EventSetCalc_v3_0_API calc) {
		ArrayList<ArrayList<SiteDataValue<?>>> sitesData = new ArrayList<ArrayList<SiteDataValue<?>>>();
		ArrayList<Site> sites = calc.getSites();
		OrderedSiteDataProviderList providers = calc.getSiteDataProviders();
		for (int i=0; i<sites.size(); i++) {
			Site site = sites.get(i);
			ArrayList<SiteDataValue<?>> dataVals = calc.getUserSiteDataValues(i);
			HashSet<String> userTypes = new HashSet<>();
			if (dataVals == null) {
				logger.log(Level.FINE, "No user site data for site "+i);
				dataVals = new ArrayList<SiteDataValue<?>>();
			} else {
				for (SiteDataValue<?> dataVal : dataVals) {
					userTypes.add(dataVal.getDataType());;
					logger.log(Level.FINE, "User data value for site "+i+": "+dataVal);
				}
			}
			if (providers != null) {
				boolean hasNewType = false;
				for (SiteData<?> provider : providers) {
					if (!userTypes.contains(provider.getDataType())) {
						hasNewType = true;
						break;
					}
				}
				if (hasNewType) {
					// only fetch site data if it's actually needed
					ArrayList<SiteDataValue<?>> provData = providers.getAllAvailableData(site.getLocation());
					if (provData != null) {
						for (SiteDataValue<?> dataVal : provData)
							logger.log(Level.FINE, "Provider data value for site "+i+": "+dataVal);
						dataVals.addAll(provData);
					}
				}
			}
			sitesData.add(dataVals);
		}
		return sitesData;
	}

	public final ArrayList<ArrayList<SiteDataValue<?>>> getSitesData() {
		if (sitesData == null) {
			logger.log(Level.FINE, "Generating site data providers lists");
			sitesData = getSitesData(this);
		}

		return sitesData;
	}
	
}
