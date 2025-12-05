package org.opensha.sha.calc.IM_EventSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;

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

	/**
	 * This should ONLY be accessed through the getter method as it may
	 * be uninitialized
	 */
	private ArrayList<Site> sites = null;

	private ArrayList<ArrayList<SiteDataValue<?>>> sitesData = null;

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
	
	public static ArrayList<ArrayList<SiteDataValue<?>>> getSitesData(IMEventSetCalcAPI calc) {
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
					userTypes.add(dataVal.getDataType());
					logger.log(Level.FINE, "User data value for site "+i+": "+dataVal);
				}
			}
			if (providers != null) {
				boolean hasNewType = false;
				for (SiteData<?> provider : providers) {
                    // The provider specifies a data type that the user didn't
					if (!userTypes.contains(provider.getDataType())) {
						hasNewType = true;
						break;
					}
				}
                // Only fetch site data if it's actually necessary
				if (hasNewType) {
                    // Fetch all data values from providers that provide at least one new data type
					ArrayList<SiteDataValue<?>> provData = providers.getBestAvailableData(site.getLocation());

					if (provData != null) {
                        // Ignore provider data types where user types are already specified
                        provData.removeIf(dataVal ->
                                userTypes.stream()
                                        .anyMatch(userVal -> userVal.equals(dataVal.getDataType())));

						for (SiteDataValue<?> dataVal : provData) {
                            logger.log(Level.FINE, "Provider data value for site " + i + ": " + dataVal);
                        }
                        // Remove all dataVals that have a matching dataType in provData (i.e. Use the provider value)
                        dataVals.removeIf(dataVal ->
                                provData.stream()
                                        .anyMatch(provVal ->provVal.getDataType().equals(dataVal.getDataType()))
                        );

                        // Add all provider values for new data types
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
