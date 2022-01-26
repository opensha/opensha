package org.opensha.commons.data.siteData.servlet.impl;

import java.io.IOException;

import org.opensha.commons.data.siteData.impl.WillsMap2015;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;

public class WillsMap2015Servlet extends AbstractSiteDataServlet<Double> {
	
	public WillsMap2015Servlet() throws IOException {
		super(new WillsMap2015(WillsMap2015.SERVER_BIN_FILE));
	}
	
}
