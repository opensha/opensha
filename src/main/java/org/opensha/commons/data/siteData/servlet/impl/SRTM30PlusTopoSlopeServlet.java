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

import org.opensha.commons.data.siteData.impl.SRTM30PlusTopoSlope;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServlet;
import org.opensha.commons.util.ServerPrefUtils;

public class SRTM30PlusTopoSlopeServlet extends AbstractSiteDataServlet<Double> {
	
	public static final String FILE_NAME = ServerPrefUtils.SERVER_PREFS.getDataDir().getAbsolutePath()
			+File.separator+"siteData"+File.separator+"wald_allen_vs30"+File.separator+"srtm30_plus_v5.0_grad.bin";
	
	public SRTM30PlusTopoSlopeServlet() throws IOException {
		super(new SRTM30PlusTopoSlope(FILE_NAME));
	}
}
