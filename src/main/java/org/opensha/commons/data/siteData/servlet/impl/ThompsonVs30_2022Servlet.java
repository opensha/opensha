package org.opensha.commons.data.siteData.servlet.impl;

import java.io.IOException;

import org.opensha.commons.data.siteData.impl.ThompsonVs30_2022;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;

public class ThompsonVs30_2022Servlet extends AbstractSiteDataServlet<Double> {
	
	public ThompsonVs30_2022Servlet() throws IOException {
		super(new ThompsonVs30_2022(ThompsonVs30_2022.SERVER_BIN_FILE));
	}
	
}
