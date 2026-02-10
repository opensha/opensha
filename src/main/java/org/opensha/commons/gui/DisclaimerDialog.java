package org.opensha.commons.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.prefs.Preferences;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkEvent.EventType;
import javax.swing.event.HyperlinkListener;

import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.BrowserUtils;
import org.opensha.ui.components.Resources;

/**
 * The licensedisclaimer dialog that gets displayed when any OpenSHA
 * application is launched. This dialog provides the user the option to not
 * display the dialog on subsequent launches.
 * 
 * @author Kevin Milner, Peter Powers
 * @version $Id: DisclaimerDialog.java 10997 2015-06-05 18:49:56Z kmilner $
 */
public class DisclaimerDialog extends JDialog implements ActionListener {
	
	// TODO this class is specific to OpenSHA; possibly remove from commons
	
	private static final long serialVersionUID = 1L;
	private static boolean D = false;
	
	private static final int margin = 16;
	
	private static final String prefPrefix = "disc_accpted_";
	private static Preferences prefs;

	private String appName;
	private String shortName;
	private ApplicationVersion version;
	
	private JButton acceptButton;
	private JButton exitButton;
	private JCheckBox hideCheck;
	
	private boolean accepted = false;
	
	
	static {
		prefs = Preferences.userNodeForPackage(DisclaimerDialog.class);
	}
	
	
	/**
	 * Construct a new disclaimer dialog.
	 * 
	 * @param appName application name
	 * @param shortName short version of application name
	 * @param version 
	 */
	public DisclaimerDialog(String appName, String shortName, ApplicationVersion version) {
		this.appName = appName;
		this.shortName = shortName;
		this.version = version;
		
		if (skipDisclaimer()) {
			// just return, never show it
			if (D) System.out.println("can skip disclaimer!");
			return;
		}
		if (D) System.out.println("displaying disclaimer!");
		//license = getLicense();
		
		init();
		if (!accepted)
			System.exit(0);
		if (hideCheck.isSelected()) {
			storeAcceptedVersion(shortName, version);
			flushPrefs();
		}
	}
	
	private void init() {
		setModal(true);
		setResizable(false);
		setTitle("License Agreement");
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setPreferredSize(new Dimension(600, 480));

		// app label
		JLabel appLabel = new JLabel(appName + " (" + version + ")");
		appLabel.setFont(appLabel.getFont().deriveFont(Font.BOLD));
		appLabel.setBorder(new EmptyBorder(20,0,10,0));
		
		// button panel
		hideCheck = new JCheckBox("Don't show again", false);
		exitButton = new JButton("Disagree");
		exitButton.addActionListener(this);
		acceptButton = new JButton("Agree");
		acceptButton.addActionListener(this);
		JPanel buttonPanel = new JPanel();
		buttonPanel.setBorder(new EmptyBorder(10,0,10,0));
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
		buttonPanel.add(hideCheck);
		buttonPanel.add(Box.createHorizontalGlue());
		buttonPanel.add(exitButton);
		buttonPanel.add(acceptButton);

		// layout
		JPanel p  = new JPanel(new BorderLayout());
		p.setBorder(new EmptyBorder(0,margin,0,margin));
		p.add(appLabel, BorderLayout.PAGE_START);
		p.add(getLicensePanel(), BorderLayout.CENTER);
		p.add(buttonPanel, BorderLayout.SOUTH);
		getContentPane().add(p);

		pack();
		setLocationRelativeTo(null);
		setVisible(true);

		if (D) System.out.println("Set visible done (accepted = " + accepted + ")");
	}	
	
	/*
	 * Called when regular LICENSE.html doesn't load properly.
	 */
	private static String getLicenseLink() {
		StringBuilder sb = new StringBuilder();
		sb.append("<html><body style='font: sans-serif;'>");
		sb.append("<div style='text-align: center'>");
		sb.append("<br/><br/><br/><br/>Please see:<br/><br/>");
		sb.append("<a href='https://opensha.org/License-Disclaimer'>");
		sb.append("https://opensha.org/License-Disclaimer");
		sb.append("</a><br/><br/>for license and disclaimer information.");
		sb.append("</div></body></html>");
		return sb.toString();
	}
	
	/**
	 * Returns the scroll pane that displays the OpenSHHA license.
	 * @return the license component
	 */
	public static JComponent getLicensePanel() {
		JTextPane textPane = new JTextPane();
		textPane.setEditable(false);
		textPane.setBorder(new EmptyBorder(10, 6, 10, 2));
		try {
			textPane.setPage(Resources.getLicense());
		} catch (IOException ioe) {
			textPane.setContentType("text/html");
			textPane.setText(getLicenseLink());
		}
		textPane.addHyperlinkListener(new HyperlinkListener() {
			@Override
			public void hyperlinkUpdate(HyperlinkEvent he) {
				try {
					if (he.getEventType() == EventType.ACTIVATED) {
						BrowserUtils.launch(he.getURL());
					}
				} catch (Exception e) {}
			}
		});
		JScrollPane scroller = new JScrollPane(textPane);
		scroller.setVerticalScrollBarPolicy(
			JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scroller.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
		scroller.setOpaque(false);
		return scroller;
	}
	
	private static String getPrefKey(String shortName) {
		return prefPrefix + shortName;
	}
	
	private ApplicationVersion getAcceptedVersion() {
		String version = prefs.get(getPrefKey(shortName), null);
		if (D) System.out.println("getAcceptedVersion(): prefVal=" + version);
		if (version != null && !version.isEmpty() && version.contains(".")) {
			return ApplicationVersion.fromString(version);
		}
		return null;
	}
	
	private boolean skipDisclaimer() {
		ApplicationVersion accepted = getAcceptedVersion();
		
		// skip if user has already accepted version >= current for this app
		if (D) System.out.println("Comparing my version ("+version+") to pref version ("+accepted+")");
		if (accepted != null && !accepted.isLessThan(version)) {
			return true;
		}
		return false;
	}
	
	private static void storeAcceptedVersion(String shortName, ApplicationVersion version) {
		if (version == null) {
			clearAcceptedVersion(shortName);
			return;
		}
		String key = getPrefKey(shortName);
		if (D) System.out.println("setting accepted version for '"+key+"' to: " + version);
		prefs.put(key, version.toString());
	}
	
	private static void clearAcceptedVersion(String shortName) {
		String key = getPrefKey(shortName);
		if (D) System.out.println("clearing accepted version for '"+key+"'");
		try {
			prefs.remove(key);
		} catch (NullPointerException e) {
			if (D) System.out.println("key '" + key + "' cannot be cleared as it doesn't exist!");
		}
	}
	
	private static void flushPrefs() {
		try {
			prefs.flush();
		} catch (Throwable t) {
			t.printStackTrace();
			System.err.println("WARNING: Couldn't write to preferences!");
		}
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource().equals(acceptButton)) {
			accepted = true;
			setVisible(false);
		} else if (e.getSource().equals(exitButton)) {
			accepted = false;
			setVisible(false);
		}
	}
	
	public static void main(String[] args) {
		String name = "Test Application";
		String shortName = "TestApp";
		ApplicationVersion version = ApplicationVersion.fromString("0.2.3");
		ApplicationVersion accepted = ApplicationVersion.fromString("0.2.1");
//		storeAcceptedVersion(shortName, accepted);
		try {
			new DisclaimerDialog(name, shortName, version);
		} catch (Throwable t) {
			System.out.println("Caught an exception!");
			t.printStackTrace();
			System.exit(1);
		}
		System.exit(0);
	}

}
