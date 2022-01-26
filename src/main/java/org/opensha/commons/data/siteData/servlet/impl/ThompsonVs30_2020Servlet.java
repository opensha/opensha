package org.opensha.commons.data.siteData.servlet.impl;

import java.io.IOException;

import org.opensha.commons.data.siteData.impl.ThompsonVs30_2020;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;

public class ThompsonVs30_2020Servlet extends AbstractSiteDataServlet<Double> {
	
	public ThompsonVs30_2020Servlet() throws IOException {
		super(new ThompsonVs30_2020(ThompsonVs30_2020.SERVER_BIN_FILE));
	}
	
}
