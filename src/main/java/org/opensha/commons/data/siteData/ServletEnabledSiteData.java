package org.opensha.commons.data.siteData;

import org.opensha.commons.param.ParameterList;

public interface ServletEnabledSiteData<Element> extends SiteData<Element> {

	/**
	 * These parameters will be sent with each get request to the servlet
	 * 
	 * @return
	 */
	public ParameterList getServerSideParams();
}
