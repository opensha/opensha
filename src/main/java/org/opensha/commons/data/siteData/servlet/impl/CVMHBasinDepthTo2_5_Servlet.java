/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.commons.data.siteData.servlet.impl;

import java.io.File;
import java.io.IOException;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.impl.CVMHBasinDepth;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.util.ServerPrefUtils;

public class CVMHBasinDepthTo2_5_Servlet extends
		AbstractSiteDataServlet<Double> {
	
	private static final File DIR = new File(ServerPrefUtils.SERVER_PREFS.getTomcatDir(),
										CVMHBasinDepth.DEFAULT_DATA_DIR);
	
	public CVMHBasinDepthTo2_5_Servlet() throws IOException {
		super(new CVMHBasinDepth(SiteData.TYPE_DEPTH_TO_2_5, DIR, false));
	}
}
