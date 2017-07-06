package org.opensha.commons.util.bugReports;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.rmi.ConnectException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkEvent.EventType;
import javax.swing.text.FlowView;
import javax.swing.event.HyperlinkListener;

import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.BrowserUtils;
import org.opensha.commons.util.bugReports.knownBugImpl.ExceptionTypeKnownBugDetector;

public class BugReportDialog extends JDialog implements ActionListener, HyperlinkListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private BugReport bug;
	private static String message = "Oops...something went wrong!";
	
	private static Color topTextColor = new Color(0, 0, 180);
	private static Color topBottomColor = new Color(150, 150, 220);
	private static Color mainColor = Color.WHITE;
	
	private JButton quitButton = new JButton("Exit Application");
	private JButton continueButton = new JButton("Continue Using Application");
	protected static final String submitButtonTextDefault = "<html><center><b><font size=+2>" +
			"Submit Bug Report</font></b><br><font size=-1>(will open in web browser)" +
			"</font></center></html>";
	private static final String submitButtonTextKnownBug = "<html><center><b>I read the above and still want to " +
			"submit a report.</font></b><br><font size=-1>(will open in web browser)</center></html>";
	private JButton submitBugButton = new JButton(submitButtonTextDefault);
	private JButton technicalButton = new JButton("View Techical Details");
	private JTextField emailField = new JTextField("", 100);
	
	private boolean canIgnore = false;
	
	// this is a list of known bugs
	private static ArrayList<KnownBugDetector> knownBugDetectors;
	
	static {
		knownBugDetectors = new ArrayList<KnownBugDetector>();
		
		knownBugDetectors.add(new ExceptionTypeKnownBugDetector(NumberFormatException.class,
				"<b>This is mostly likely due to an incorrectly formatted number.</b> OpenSHA" +
				" currently only supports numbers with a decimal point, and will not work for" +
				" decimal commas. For example, to specify the longitude and latitude" +
				" of downtown Los Angeles, CA, USA, here is the correct number representation:" +
				"<br><br>Latitude: 34.053 Longitude: -118.243" +
				"<br><br>If you still think that this is a bug, you may submit a report by clicking below.", false));
		knownBugDetectors.add(new ExceptionTypeKnownBugDetector(java.rmi.ConnectException.class,
				"<b>This is most likely a firewall issue!</b> Either your computer cannot connect" +
				" to our server, or our server is temporarily down. Make sure that you have an" +
				" internet connection and that your firewall allows connections to ports 40000-40500" +
				" on opensha.usc.edu. If you are still having problems, try back later, as the server" +
				" might just be down.", false));
		knownBugDetectors.add(new ExceptionTypeKnownBugDetector(java.net.ConnectException.class,
				"<b>This is most likely a firewall issue!</b> Either your computer cannot connect" +
				" to our server, or our server is temporarily down. Make sure that you have an " +
				"internet connection and that your firewall allows connections to port 8080 on" +
				" opensha.usc.edu. If you are still having problems, try back later, as the server" +
				" might just be down.", false));
		knownBugDetectors.add(new ExceptionTypeKnownBugDetector(java.lang.IllegalStateException.class,
				"Buffers have not been created",
				"<b>This is a known Java bug and not related to OpenSHA.</b> It applies to versions" +
				" of Java above 6 update 18 on Windows when changing display resolutions or running" +
				" a full screen media player.<br>For more information on this bug, <a href=" +
				"\"http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6933331\">Click Here</a>", false));
		knownBugDetectors.add(new ExceptionTypeKnownBugDetector(java.lang.IllegalArgumentException.class,
				"Value \\(-?\\d+\\.?\\d*\\) is out of range.",
				"<b>This problem might be due to an incorrectly formatted Longitude value!</b>" +
				" Currently OpenSHA only supports longitude values in the range (-180,180). If" +
				" this isn't the problem, please submit a bug report below.", false)
				.setMessageAsRegex());
		knownBugDetectors.add(new ExceptionTypeKnownBugDetector(java.lang.OutOfMemoryError.class,
				"<b>You ran out of memory!</b>" +
				" Java requires that you specify the maximum amount of memory needed before running" +
				" an application. If this limit is too high, however, the application won't start. If"+
				" you are running via web start from our website, you will need to download the jar files"+
				" and run manually. For example, to run with 4GB of memory: java -Xmx4G -jar [jar-file-name]", false));
		knownBugDetectors.add(new ExceptionTypeKnownBugDetector(NullPointerException.class, FlowView.class, "layoutRow",
				null, "<b>This is an inconsequential Java Bug<b>"+
				" No further action is required as this in an internal Java Swing bug that won't affect"+
				" the operation of this application. No bug report is necessary.", true));
		knownBugDetectors.add(new ExceptionTypeKnownBugDetector(null, "javax.swing", null,
				null, "<b>This is an inconsequential Java Bug<b>"+
				" No further action is required as this in an internal Java Swing bug that won't affect"+
				" the operation of this application. No bug report is necessary.", true));
	}
	
	private boolean fatal;

	public BugReportDialog(Component parent, BugReport bug, boolean fatal) {
		if (bug == null) {
			bug = new BugReport();
		}
		
		this.fatal = fatal;
		this.bug = bug;
		init();
		setLocationRelativeTo(parent);
	}

	private class ImagePanel extends JPanel {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		
		BufferedImage image;
		public ImagePanel(BufferedImage image) {
			this.image = image;
			this.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
		}

		@Override
		public void paintComponent(Graphics g) {
			g.drawImage(image, 0, 0, null);
		}
	}

	private void init() {
		this.setTitle(message);

		this.setLayout(new BorderLayout());
		this.setSize(650, 500);
		this.setResizable(false);

		setModal(true);
		if (fatal)
			setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		else
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		this.setBackground(mainColor);
			
		this.add(getTopPanel(), BorderLayout.NORTH);
		
		this.add(getCenterPanel(), BorderLayout.CENTER);
		
		this.add(getBottomPanel(), BorderLayout.SOUTH);
	}
	
	private static JPanel wrapInPanel(Component comp, Color backgroundColor) {
		JPanel panel = new JPanel();
		panel.add(comp);
		if (backgroundColor != null)
			panel.setBackground(backgroundColor);
		return panel;
	}
	
	private JPanel getTopPanel() {
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
		JLabel messageLabel = new JLabel(message + "   ");
		messageLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
		messageLabel.setForeground(topTextColor);
		messageLabel.setBackground(topBottomColor);
		try {
			BufferedImage cautionImage = ImageIO.read(
					this.getClass().getResource("/resources/images/icons/software_bug.png"));
			ImagePanel imagePanel = new ImagePanel(cautionImage);
			imagePanel.setBackground(topBottomColor);
			topPanel.add(imagePanel);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		topPanel.add(messageLabel);
		topPanel.setBackground(topBottomColor);
		topPanel.setBorder(new EmptyBorder(new Insets(3, 3, 3, 3)));
		
		return topPanel;
	}
	
	private KnownBugDetector getApplicableKnownBug() {
		for (KnownBugDetector knownBug : knownBugDetectors) {
			if (knownBug.isKnownBug(bug))
				return knownBug;
		}
		return null;
	}
	
	public boolean canIgnoreKnownBug() {
		KnownBugDetector known = getApplicableKnownBug();
		return known != null && known.canIgnore();
	}
	
	public JPanel getCenterPanel() {
		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
		centerPanel.setBackground(mainColor);
		
		String text = "Sorry for the inconvenience, but an error has occurred.";
		
		if (fatal)
			text += "<br><br>Unfortunately this error is fatal and the application will now exit.";
		else
			text += "<br><br><b>It may be possible to continue to use the application, but errors " +
				"may persist and the application may produce unexpected results.</b>";
		
		KnownBugDetector knownBug = getApplicableKnownBug();
		text += "<br><br>";
		if (knownBug == null) {
			text += "You can help to improve OpenSHA by submitting a bug report to our " +
					"<a href=\""+BugReport.TRAC_URL+"\">Trac Site</a>. Click the button below which " +
					"will launch your web browser, allowing you to submit the bug. Information on " +
					"the bug will automatically be included. To view that information, click " +
					"\"View Technical Details\". Note that this requires an internet connection.";
		} else {
			submitBugButton.setText(submitButtonTextKnownBug);
			text += knownBug.getKnownBugDescription();
		}
		
		JTextPane mainText = new JTextPane();
		mainText.setContentType("text/html");
		mainText.setText(text);
		mainText.setEditable(false);
		mainText.setPreferredSize(new Dimension(this.getWidth()-6, 100));
		mainText.setBackground(mainColor);
		mainText.addHyperlinkListener(this);
		
		centerPanel.add(mainText);
		
		JPanel bottomCenter = new JPanel();
		bottomCenter.setLayout(new BoxLayout(bottomCenter, BoxLayout.Y_AXIS));
		
		bottomCenter.add(wrapInPanel(submitBugButton, mainColor));
		bottomCenter.setBackground(mainColor);
		submitBugButton.addActionListener(this);
		
		JLabel emailLabel = new JLabel("Your E-mail Address (optional): ");
		JPanel emailPanel = new JPanel();
		emailPanel.setLayout(new BoxLayout(emailPanel, BoxLayout.X_AXIS));
		emailPanel.add(emailLabel);
		emailPanel.add(emailField);
		emailPanel.setPreferredSize(new Dimension(400, 20));
		emailPanel.setBackground(mainColor);
		emailPanel.setMaximumSize(new Dimension(400, 20));
		bottomCenter.add(wrapInPanel(emailPanel, mainColor));
		
		centerPanel.add(wrapInPanel(bottomCenter, mainColor));
		
		centerPanel.setBorder(new EmptyBorder(new Insets(3, 3, 3, 3)));
		
		return centerPanel;
	}
	
	public JPanel getBottomPanel() {
		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
		bottomPanel.add(quitButton);
		Dimension spacerSize = new Dimension(3, 3);
		bottomPanel.add(new Box.Filler(spacerSize, spacerSize, spacerSize));
		quitButton.addActionListener(this);
		bottomPanel.add(continueButton);
		bottomPanel.add(new Box.Filler(spacerSize, spacerSize, spacerSize));
		continueButton.setEnabled(!fatal);
		continueButton.addActionListener(this);
		bottomPanel.add(technicalButton);
		technicalButton.setEnabled(bug.getDescription() != null);
		technicalButton.addActionListener(this);
		bottomPanel.setBackground(topBottomColor);
		bottomPanel.setBorder(new EmptyBorder(new Insets(3, 3, 3, 3)));
		
		return wrapInPanel(bottomPanel, topBottomColor);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == quitButton) {
			System.exit(1);
		} else if (e.getSource() == continueButton) {
			this.setVisible(false);
			this.dispose();
		} else if (e.getSource() == submitBugButton) {
			String email = emailField.getText();
			if (email.length() > 2)
				bug.setReporter(email);
			URL url = null;
			try {
				url = bug.buildTracURL();
			} catch (MalformedURLException e2) {
				e2.printStackTrace();
				String text = "We couldn't automatically generate a bug report. Please manually submit " +
						"one at " + BugReport.TRAC_NEW_TICKET_URL;
				JOptionPane.showMessageDialog(this, text, "Could geneate bug report", JOptionPane.ERROR_MESSAGE);
				return;
			}
			try {
				BrowserUtils.launch(url.toURI());
			} catch (Exception e1) {
				String text = "Java couldn't open the bug report URL in your web browser. " +
						"Please copy/paste this entire link manually into your web browser to " +
						"submit the bug. Thanks!\n\n"+url.toString();
				JTextArea ta = new JTextArea(10, 50);
				ta.setText(text);
				ta.setLineWrap(true);
				ta.setEditable(false);
				ta.setWrapStyleWord(false);
				ta.setPreferredSize(new Dimension(300, 200));
				JScrollPane scroll = new JScrollPane(ta);
				scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
				JOptionPane.showMessageDialog(this, scroll,
						"Could not launch browser!", JOptionPane.ERROR_MESSAGE);
			}
			
		} else if (e.getSource() == technicalButton) {
			JTextArea ta = new JTextArea(20, 50);
			ta.setEditable(false);
			ta.setText(bug.getDescription());
			JScrollPane scroll = new JScrollPane(ta);
			JOptionPane.showMessageDialog(this, scroll, "Technical Information", JOptionPane.PLAIN_MESSAGE);
		}
	}

	@Override
	public void hyperlinkUpdate(HyperlinkEvent e) {
		if (e.getEventType() == EventType.ACTIVATED) {
			BrowserUtils.launch(e.getURL());
		}
	}
	
	public static void main(String args[]) throws IOException {
//		Throwable t = new RuntimeException(new IllegalArgumentException("Value (183.0) is out of range."));
//		Throwable t = new java.net.ConnectException("asdf");
		Throwable t = new IllegalStateException("Buffers have not been created");
		
		BugReport bug = new BugReport(t,
				"Metadata is here\nmore stuff\nand done", "BugReportDialog",
				ApplicationVersion.loadBuildVersion(), null);
		
		BugReportDialog dialog = new BugReportDialog(null, bug, true);
		dialog.setVisible(true);
	}

}
