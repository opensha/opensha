package org.opensha.commons.util.bugReports;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextPane;

import org.opensha.commons.util.BrowserUtils;

public class SimpleBugMessagePanel extends JPanel implements ActionListener {
	
	private JButton submitButton = new JButton(BugReportDialog.submitButtonTextDefault);
	
	private BugReport bug;
	
	public SimpleBugMessagePanel(BugReport bug, String message) {
		super(new BorderLayout());
		
		this.bug = bug;
		
		JTextPane text = new JTextPane();
		text.setText(message);
		text.setPreferredSize(new Dimension(400, 100));
		text.setEditable(false);
		
		this.add(text, BorderLayout.CENTER);
		this.add(submitButton, BorderLayout.SOUTH);
		
		submitButton.addActionListener(this);
	}
	
	
	public void showAsDialog(Component parentComponent, String title) {
		JOptionPane.showMessageDialog(parentComponent, this, title, JOptionPane.ERROR_MESSAGE);
	}


	@Override
	public void actionPerformed(ActionEvent e) {
		try {
			BrowserUtils.launch(bug.buildTracURL());
		} catch (MalformedURLException e1) {
			e1.printStackTrace();
		}
	}

}
