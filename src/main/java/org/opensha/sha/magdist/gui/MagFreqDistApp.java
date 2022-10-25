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
