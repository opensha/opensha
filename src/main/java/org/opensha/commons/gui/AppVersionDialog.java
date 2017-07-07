package org.opensha.commons.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import org.opensha.commons.util.ApplicationVersion;
import org.opensha.ui.components.Resources;

/**
 * Class for an OpenSHA application 'About' dialog.
 * 
 * @author Peter Powers
 * @version $Id: AppVersionDialog.java 7478 2011-02-15 04:56:25Z pmpowers $
 */
public class AppVersionDialog extends JDialog {
	
	private static final long serialVersionUID = 1L;
	private static final int margin = 16;

	/**
	 * Constructs a new 'About' dialog for OpenSHA applications with a logo
	 * and license/disclaimer panel.
	 * 
	 * @param appName to use
	 * @param appVersion to use
	 */
	public AppVersionDialog(String appName, ApplicationVersion appVersion) {
		setResizable(false);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setPreferredSize(new Dimension(600, 320));
		setLayout(new BorderLayout());
		setResizable(false);
		
		// app info
		JPanel infoPanel = new JPanel();
		infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.X_AXIS));
		JLabel appIcon = new JLabel(Resources.getLogo64());
		appIcon.setBorder(new EmptyBorder(margin, 0, margin, margin));
		infoPanel.add(appIcon);
		StringBuilder sb = new StringBuilder();
		sb.append("<html><b>").append(appName);
		sb.append("<br/>Version: ").append(appVersion).append("</b></html>");
		JLabel appInfo = new JLabel(sb.toString());
		infoPanel.add(appInfo);

		// layout
		JPanel p = new JPanel(new BorderLayout());
		p.setBorder(new EmptyBorder(0,margin,margin,margin));
		p.add(infoPanel, BorderLayout.PAGE_START);
		p.add(DisclaimerDialog.getLicensePanel(), BorderLayout.CENTER);
		getContentPane().add(p);
		
		pack();
	}
	
	public static void main(String[] args) {
		JDialog d = new AppVersionDialog("Test", new ApplicationVersion(1,2,3));
		d.setVisible(true);
	}

}
