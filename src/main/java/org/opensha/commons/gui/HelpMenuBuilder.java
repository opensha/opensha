package org.opensha.commons.gui;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.BrowserUtils;
import org.opensha.commons.util.bugReports.BugReport;

// TODO if JDesktop.browse() fails, dialog with clickable/copiable url should
// be presented
public class HelpMenuBuilder implements ActionListener {
	
	public static String OPENSHA_HOMEPAGE_URL = "http://www.opensha.org";
	public static String OPENSHA_DOCUMENTATION_URL = OPENSHA_HOMEPAGE_URL+"/docs";
	public static String OPENSHA_CONTACT_URL = OPENSHA_HOMEPAGE_URL+"/contact";
	
	private String appName;
	private String appShortName;
	private ApplicationVersion appVersion;
	private Component application;
	
	private JMenuItem homepageItem;
	private JMenuItem docsItem;
	private JMenuItem submitBugItem;
	private JMenuItem contactItem;
	private JMenuItem aboutItem;
	
	private AppVersionDialog appVersionDialog;
	
	private JMenuItem guideItem;
	private String guideURL;
	
	private JMenuItem tutorialItem;
	private String tutorialURL;
	
	public HelpMenuBuilder(String appName, String appShortName, ApplicationVersion appVersion, Component application) {
		this.appName = appName;
		this.appShortName = appShortName;
		this.appVersion = appVersion;
		this.application = application;
	}
	
	public void setGuideURL(String guideURL) {
		this.guideURL = guideURL;
	}
	
	public void setTutorialURL(String tutorialURL) {
		this.tutorialURL = tutorialURL;
	}
	
	public JMenu buildMenu() {
		JMenu helpMenu = new JMenu("Help");
		
		if (guideURL != null) {
			guideItem = new JMenuItem("View User Guide");
			guideItem.addActionListener(this);
			helpMenu.add(guideItem);
		}
		if (tutorialURL != null) {
			tutorialItem = new JMenuItem("View Tutorial");
			tutorialItem.addActionListener(this);
			helpMenu.add(tutorialItem);
		}
		
		if (helpMenu.getItemCount() > 0) {
			helpMenu.addSeparator();
		}
		
		homepageItem = new JMenuItem("OpenSHA Home Page");
		homepageItem.addActionListener(this);
		helpMenu.add(homepageItem);
		
		docsItem = new JMenuItem("Online Documentation");
		docsItem.addActionListener(this);
		helpMenu.add(docsItem);
		
		submitBugItem = new JMenuItem("Submit Bug Report");
		submitBugItem.addActionListener(this);
		helpMenu.add(submitBugItem);
		
		contactItem = new JMenuItem("Contact Developers");
		contactItem.addActionListener(this);
		helpMenu.add(contactItem);
		
		helpMenu.addSeparator();
		
		aboutItem = new JMenuItem("About - License");
		aboutItem.addActionListener(this);
		helpMenu.add(aboutItem);
		
		return helpMenu;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
			if (e.getSource() == homepageItem) {
				BrowserUtils.launch(new URL(OPENSHA_HOMEPAGE_URL));
			} else if (e.getSource() == docsItem) {
				BrowserUtils.launch(new URL(OPENSHA_DOCUMENTATION_URL));
			} else if (e.getSource() == contactItem) {
				BrowserUtils.launch(new URL(OPENSHA_CONTACT_URL));
			} else if (e.getSource() == submitBugItem) {
				BugReport bug = new BugReport(null, null, appShortName, appVersion, application);
				BrowserUtils.launch(bug.buildTracURL());
			} else if (guideItem != null && e.getSource() == guideItem) {
				BrowserUtils.launch(new URL(guideURL));
			} else if (tutorialItem != null && e.getSource() == tutorialItem) {
				BrowserUtils.launch(new URL(tutorialURL));
			} else if (e.getSource() == aboutItem) {
				if (appVersionDialog ==  null) {
					appVersionDialog = new AppVersionDialog(appName, appVersion);
				}
//				System.out.println("pp: " + application);
				appVersionDialog.setLocationRelativeTo(application);
				appVersionDialog.setVisible(true);
			}
		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

}
