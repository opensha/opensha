package org.opensha.sha.calc.IM_EventSet.v03;

import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha.commons.data.Site;
import org.opensha.commons.param.ParameterList;

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
        assert logger != null;
        logger.setLevel(level);
	}
	
	public static final float MIN_SOURCE_DIST = 200;

	/**
	 * This should ONLY be accessed through the getter method as it may
	 * be uninitialized
	 */
	private ArrayList<Site> sites = null;

	private ArrayList<ParameterList> sitesData = null;

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

	public static ArrayList<ParameterList> getSitesData(IM_EventSetCalc_v3_0_API calc) {
		ArrayList<ParameterList> sitesData = new ArrayList<ParameterList>();
		ArrayList<Site> sites = calc.getSites();
		for (int i=0; i<sites.size(); i++) {
			ParameterList userSiteData = calc.getUserSiteData(i);
			if (userSiteData == null) {
				logger.log(Level.FINE, "No user site data for site "+i);
                continue;
			}
			sitesData.add(userSiteData);
		}
		return sitesData;
	}

	public final ArrayList<ParameterList> getSitesData() {
		if (sitesData == null) {
			logger.log(Level.FINE, "Generating site data lists");
			sitesData = getSitesData(this);
		}

		return sitesData;
	}
	
}
