package org.opensha.commons.data.siteData.servlet.impl;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.data.siteData.impl.GarciaRegionPerlWrapper;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.util.ServerPrefs;

public class GarciaRegionPerlWrapperServlet extends
		AbstractSiteDataServlet<String> {
	
	private static File getScriptFile() {
		File tomcatDir = ServerPrefs.PRODUCTION_PREFS.getTomcatDir().getParentFile();
		File feDir = new File(tomcatDir, "flinn_engdahl_regions");
		return new File(feDir, "feregion_ajm.pl");
	}
	
	public GarciaRegionPerlWrapperServlet() throws IOException {
		super(new GarciaRegionPerlWrapper(getScriptFile()));
	}
}
