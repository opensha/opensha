package org.opensha.commons.data.siteData.gui;

import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;

import org.opensha.commons.gui.DisclaimerDialog;
import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.bugReports.DefaultExceptionHandler;

public class SiteDataCombinedApp extends JFrame {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public final static String APP_NAME = "Site Data Application";
	public final static String APP_SHORT_NAME = "SiteData";
	
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
				version = new ApplicationVersion(-1, -1, -1);
			}
		}
		return version;
	}
	
	private JTabbedPane pane;
	
	private SiteDataApplet dataApplet;
	private SiteDataMapApplet mapApplet;
	
	public SiteDataCombinedApp() {
		
	}
	
	public void init() {
		pane = new JTabbedPane(JTabbedPane.TOP);
		
		dataApplet = new SiteDataApplet();
		mapApplet = new SiteDataMapApplet();
		
		pane.addTab("Site Data Values", dataApplet);
		pane.addTab("Site Data Maps", mapApplet);
		
		setContentPane(pane);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		pack();
		setTitle(APP_NAME+" ("+getAppVersion().getDisplayString()+")");
		setLocationRelativeTo(null);
		setVisible(true);
	}

	/**
	 * @param args
	 * @throws  
	 */
	public static void main(String[] args) {
		new DisclaimerDialog(APP_NAME, APP_SHORT_NAME, getAppVersion());
		DefaultExceptionHandler exp = new DefaultExceptionHandler(
				APP_SHORT_NAME, getAppVersion(), null, null);
		Thread.setDefaultUncaughtExceptionHandler(exp);
		launch(exp);
	}
	
	public static SiteDataCombinedApp launch(DefaultExceptionHandler handler) {
		SiteDataCombinedApp app = new SiteDataCombinedApp();
		if (handler != null) {
			handler.setApp(app);
			handler.setParent(app);
		}
		app.init();
		return app;
	}

}
