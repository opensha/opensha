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

package org.opensha.sha.magdist.gui;

import java.io.IOException;

import org.opensha.commons.gui.DisclaimerDialog;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.util.ApplicationVersion;
import org.opensha.sha.gui.util.IconFetcher;

/**
 * <p>Title:MagFreqDistApp </p>
 *
 * <p>Description: Shows the MagFreqDist Editor and plot in a window.</p>
 *
 * <p>Copyright: Copyright (c) 2002</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class MagFreqDistApp {
	
	public static final String APP_NAME = "Magnitude Frequency Distribution Application";
	public static final String APP_SHORT_NAME = "MagFreqDist";
	private static ApplicationVersion version;
	
	/**
	 * Returns the Application version
	 * @return ApplicationVersion
	 */
	public static ApplicationVersion getAppVersion(){
		if (version == null) {
			try {
				version = ApplicationVersion.loadBuildVersion();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return version;
	}

	/**
	 * Main function to run this as an application
	 * @param args String[]
	 */
	public static void main(String[] args) throws IOException {
		new DisclaimerDialog(APP_NAME, APP_SHORT_NAME, getAppVersion());
		launch();
	}
	
	public static MagFreqDistAppWindow launch() {
		MagFreqDistAppWindow magFreqDistApp = new MagFreqDistAppWindow();
		magFreqDistApp.setIconImages(IconFetcher.fetchIcons(APP_SHORT_NAME));
		magFreqDistApp.initMagParamEditor();
		magFreqDistApp.createMagParam();
		magFreqDistApp.makeSumDistVisible(true);
		return magFreqDistApp;
	}
}
