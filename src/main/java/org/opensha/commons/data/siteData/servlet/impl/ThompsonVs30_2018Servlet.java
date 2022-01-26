package org.opensha.commons.data.siteData.servlet.impl;

import java.io.IOException;

import org.opensha.commons.data.siteData.impl.ThompsonVs30_2018;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;

public class ThompsonVs30_2018Servlet extends AbstractSiteDataServlet<Double> {
	
	public ThompsonVs30_2018Servlet() throws IOException {
		super(new ThompsonVs30_2018(ThompsonVs30_2018.SERVER_BIN_FILE));
	}
	
}
