package org.opensha.sha.calc.IM_EventSet;

import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.IM_EventSet.IMEventSetCalcAPI;
import org.opensha.sha.util.SiteTranslator;

/**
 * The abstract IM Event Set calculator allows for site data parsing logic to
 * be shared between the GUI and CLI implementations.
 */
public abstract class AbstractIMEventSetCalc implements IMEventSetCalcAPI {
	
	public static Logger logger = Logger.getLogger("IMEventSetLog");
	
	public static void initLogger(Level level) {
		Logger parent = logger;
		while (parent != null) {
			for (Handler handler : parent.getHandlers())
				handler.setLevel(level);
			parent.setLevel(level);
			parent = parent.getParent();
		}
        assert logger != null;
        logger.setLevel(level);
	}
	
	public static final float MIN_SOURCE_DIST = 200;

    private static final SiteTranslator siteTrans = new SiteTranslator();

	/**
	 * This should ONLY be accessed through the getter method as it may
	 * be uninitialized
	 */
	private ArrayList<Site> sites = null;

	private ArrayList<ParameterList> sitesData = null;

	public AbstractIMEventSetCalc() {}

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

	public static ArrayList<ParameterList> getSitesData(IMEventSetCalcAPI calc) {
		ArrayList<ParameterList> sitesData = new ArrayList<ParameterList>();
		ArrayList<Site> sites = calc.getSites();
		OrderedSiteDataProviderList providers = calc.getSiteDataProviders();
		for (int i=0; i<sites.size(); i++) {
			Site site = sites.get(i);
			ParameterList userSiteData = calc.getUserSiteData(i);
			if (userSiteData == null) {
				logger.log(Level.FINE, "No user site data for site "+i);
				userSiteData = new ParameterList();
			}
			if (providers != null) {
                // Check if there is a provider applicable to user provided site data
				boolean hasNewType = false;
				for (SiteData<?> provider : providers) {
                    for (Parameter<?> userVal : userSiteData) {
                        if (SiteTranslator.DATA_TYPE_PARAM_NAME_MAP.isValidMapping(provider.getDataType(), userVal.getName())
                            && userVal.getValue() == null) {
                            hasNewType = true;
                            break;
                        }
                    }
                }
                // Only fetch site data if it's actually necessary
				if (hasNewType) {
                    // Fetch all data values from providers that provide at least one new data type
					ArrayList<SiteDataValue<?>> provData = providers.getBestAvailableData(site.getLocation());

					if (provData != null) {
                        // Set site data params where user did not specify values
                        for (Parameter<?> param : userSiteData) {
                            if (param.getValue() == null) {
                                siteTrans.setParameterValue(param, provData);
                            }
                        }
					}
				}
			}
			sitesData.add(userSiteData);
		}
		return sitesData;
	}

	public final ArrayList<ParameterList> getSitesData() {
		if (sitesData == null) {
			logger.log(Level.FINE, "Generating site data providers lists");
			sitesData = getSitesData(this);
		}

		return sitesData;
	}
	
}
