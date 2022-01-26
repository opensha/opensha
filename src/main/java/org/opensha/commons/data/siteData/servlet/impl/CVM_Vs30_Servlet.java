package org.opensha.commons.data.siteData.servlet.impl;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.impl.CVM_Vs30;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.util.ServerPrefUtils;

public class CVM_Vs30_Servlet extends
		AbstractSiteDataServlet<Double> {
	
	private static final File DIR = new File(ServerPrefUtils.SERVER_PREFS.getTomcatProjectDir(),
										CVM_Vs30.DEFAULT_RESOURCE_DIR);
	
	public CVM_Vs30_Servlet() throws IOException {
		super(new CVM_Vs30(DIR, CVM_Vs30.CVM_DEFAULT, false));
	}
}
