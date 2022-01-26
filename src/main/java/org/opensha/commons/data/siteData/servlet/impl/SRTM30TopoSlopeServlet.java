package org.opensha.commons.data.siteData.servlet.impl;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.data.siteData.impl.SRTM30TopoSlope;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.util.ServerPrefUtils;

public class SRTM30TopoSlopeServlet extends AbstractSiteDataServlet<Double> {
	
	public static final String FILE_NAME = ServerPrefUtils.SERVER_PREFS.getDataDir().getAbsolutePath()
			+File.separator+"siteData"+File.separator+"wald_allen_vs30"+File.separator+"srtm30_v2.0_grad.bin";
	
	public SRTM30TopoSlopeServlet() throws IOException {
		super(new SRTM30TopoSlope(FILE_NAME));
	}
}
