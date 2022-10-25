package org.opensha.commons.data.siteData.servlet.impl;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.data.siteData.impl.CVM2BasinDepth;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.util.ServerPrefUtils;

public class CVM2BasinDepthServlet extends AbstractSiteDataServlet<Double> {
	
	private static final String FILE = ServerPrefUtils.SERVER_PREFS.getTomcatProjectDir().getAbsolutePath()
			+File.separator+CVM2BasinDepth.FILE_NAME;
	
	public CVM2BasinDepthServlet() throws IOException {
		super(new CVM2BasinDepth(FILE));
	}
}
