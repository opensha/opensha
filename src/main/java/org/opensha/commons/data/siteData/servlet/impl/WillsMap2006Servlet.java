package org.opensha.commons.data.siteData.servlet.impl;

import java.io.IOException;

import org.opensha.commons.data.siteData.impl.WillsMap2006;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;

public class WillsMap2006Servlet extends AbstractSiteDataServlet<Double> {
	
	public WillsMap2006Servlet() throws IOException {
		super(new WillsMap2006(WillsMap2006.SERVER_BIN_FILE));
	}
	
}
