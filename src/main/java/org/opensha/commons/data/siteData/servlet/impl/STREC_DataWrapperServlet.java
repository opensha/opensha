package org.opensha.commons.data.siteData.servlet.impl;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.data.siteData.impl.STREC_DataWrapper;
import org.opensha.commons.data.siteData.impl.TectonicRegime;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;

public class STREC_DataWrapperServlet extends
		AbstractSiteDataServlet<TectonicRegime> {
	
	private static final File SCRIPT_FILE = new File("/export/opensha-00/strec/anaconda2/bin/getstrec_bulk.py");
	
	public STREC_DataWrapperServlet() throws IOException {
		super(new STREC_DataWrapper(SCRIPT_FILE));
	}
}
