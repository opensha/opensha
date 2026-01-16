package org.opensha.sha.calc.IM_EventSet.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.IM_EventSet.AbstractIMEventSetCalc;
import org.opensha.sha.calc.IM_EventSet.IMEventSetCalcAPI;
import org.opensha.sha.calc.params.filters.*;

/**
 * Implementation of the IM Event Set Calculator API for use in the GUI application.
 * This allows us to structure, parse, and pass site data and outputs for the writers.
 */
public class IMEventSetCalcGUIImpl implements IMEventSetCalcAPI {
	
	private ArrayList<Site> sites;
	private ArrayList<ParameterList> userSitesData;
	private ArrayList<ParameterList> sitesData;
	private File outputDir;

    private final SourceFilterManager sourceFilters;

	public IMEventSetCalcGUIImpl(ArrayList<Location> locs,
                           ArrayList<ParameterList> userSitesData,
                           File outputDir,
                           SourceFilterManager sourceFilters) {
		sites = new ArrayList<Site>();
		for (Location loc : locs) {
			sites.add(new Site(loc));
		}
		this.userSitesData = userSitesData;
		this.outputDir = outputDir;
        this.sourceFilters = sourceFilters;
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

    public List<SourceFilter> getSourceFilters() {
        return sourceFilters.getEnabledFilters();
    }
}
