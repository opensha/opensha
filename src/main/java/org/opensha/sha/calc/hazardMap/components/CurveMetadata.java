package org.opensha.sha.calc.hazardMap.components;

import org.opensha.commons.data.Site;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.util.TectonicRegionType;

import java.util.HashMap;
import java.util.Map;

/**
 * Metadata for the curves that's necessary for archiving them such as the location and
 * information about the IMRs used to caclulate them.
 * 
 * @author kevin
 *
 */
public class CurveMetadata {
	
	private Site site;
	private Map<TectonicRegionType, ScalarIMR> imrMap;
	private String shortLabel;
	private int index;
	
	public CurveMetadata(Site site, int index,
			Map<TectonicRegionType, ScalarIMR> imrMap,
			String shortLabel) {
		this.site = site;
		this.index = index;
		this.imrMap = imrMap;
		this.shortLabel = shortLabel;
	}

	public Site getSite() {
		return site;
	}

	public void setSite(Site site) {
		this.site = site;
	}

	public Map<TectonicRegionType, ScalarIMR> getImrMap() {
		return imrMap;
	}

	public void setImrMap(
			HashMap<TectonicRegionType, ScalarIMR> imrMap) {
		this.imrMap = imrMap;
	}

	public String getShortLabel() {
		return shortLabel;
	}

	public void setShortLabel(String shortLabel) {
		this.shortLabel = shortLabel;
	}
	
	public int getIndex() {
		return index;
	}

}
