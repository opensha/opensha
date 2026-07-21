package org.opensha.commons.util.updater;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import org.opensha.commons.util.ApplicationVersion;

/**
 * {@link UpdatePrompt} implementation backed by Swing. The update-available
 * prompt is a modal {@link JDialog}; the download progress display is a
 * separate non-modal {@link JDialog} with a {@link JProgressBar} that is
 * created lazily on the first {@link #setProgress(double)} call. All Swing
 * construction and updates are marshalled to the EDT.
 *
 * <p>Every method is safe to call from a non-EDT (worker) thread, and the
 * implementation degrades gracefully in a headless environment: {@link #prompt}
 * returns {@link Choice#REMIND_LATER} and the visual methods are no-ops. This
 * mirrors the {@code GraphicsEnvironment.isHeadless()} guard used in
 * {@code AbstractGitLabDownloader}.</p>
 *
 * @author Akash Bhatthal
 */
public class SwingUpdatePrompt implements UpdatePrompt {

	/**
	 * Gate for the "Skip this version" button on the update-available prompt.
	 * The button is always constructed and wired, but is only added to the
	 * dialog when this is {@code true}. It defaults to {@code false} so the
	 * shipped prompt offers only "Update now" and "Remind me later"; the skip
	 * plumbing ({@link Choice#SKIP_THIS_VERSION}, {@code setSkipVersion}) is
	 * still reachable via {@link UpdatePrompt#prompt} returning that choice.
	 */
	private static final boolean SKIP_VERSION_BUTTON_ENABLED = false;

	private JDialog progressDialog;
	private JProgressBar progressBar;
	private JLabel progressLabel;

	@Override
	public Choice prompt(String appName, ApplicationVersion latestVersion, String releaseNotes) {
		if (GraphicsEnvironment.isHeadless())
			return Choice.REMIND_LATER;

		final Choice[] result = new Choice[1];
		final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

		Runnable buildAndShow = () -> {
			JDialog dialog = new JDialog();
			dialog.setModal(true);
			dialog.setTitle("Update Available");
			dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			dialog.setPreferredSize(new Dimension(480, 360));

			JLabel header = new JLabel("<html><b>A new version of " + appName
					+ " is available: " + (latestVersion == null ? "?" : latestVersion.toString())
					+ "</b></html>");
			header.setBorder(new EmptyBorder(12, 12, 8, 12));

			JTextArea notes = new JTextArea(releaseNotes == null ? "" : releaseNotes);
			notes.setEditable(false);
			notes.setWrapStyleWord(true);
			notes.setLineWrap(true);
			JScrollPane scroll = new JScrollPane(notes);
			scroll.setBorder(BorderFactory.createTitledBorder("Release Notes"));
			scroll.setBorder(new EmptyBorder(0, 12, 0, 12));

			JButton updateBtn = new JButton("Update and restart now");
			JButton laterBtn = new JButton("Remind me later");
			JButton skipBtn = new JButton("Skip this version");
			updateBtn.addActionListener(e -> {
				result[0] = Choice.UPDATE_NOW;
				dialog.dispose();
				latch.countDown();
			});
			laterBtn.addActionListener(e -> {
				result[0] = Choice.REMIND_LATER;
				dialog.dispose();
				latch.countDown();
			});
			skipBtn.addActionListener(e -> {
				result[0] = Choice.SKIP_THIS_VERSION;
				dialog.dispose();
				latch.countDown();
			});
			JPanel buttonPanel = new JPanel();
			buttonPanel.add(updateBtn);
			buttonPanel.add(laterBtn);
			if (SKIP_VERSION_BUTTON_ENABLED)
				buttonPanel.add(skipBtn);
			buttonPanel.setBorder(new EmptyBorder(8, 12, 12, 12));

			JPanel content = new JPanel(new BorderLayout(0, 0));
			content.add(header, BorderLayout.PAGE_START);
			content.add(scroll, BorderLayout.CENTER);
			content.add(buttonPanel, BorderLayout.PAGE_END);
			dialog.setContentPane(content);

			dialog.pack();
			dialog.setLocationRelativeTo(null);
			dialog.setVisible(true); // modal: runs a nested event loop on the EDT
		};

		if (SwingUtilities.isEventDispatchThread()) {
			buildAndShow.run();
		} else {
			SwingUtilities.invokeLater(buildAndShow);
			try {
				latch.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return Choice.REMIND_LATER;
			}
		}
		return result[0] == null ? Choice.REMIND_LATER : result[0];
	}

	@Override
	public void setProgress(double fraction) {
		if (GraphicsEnvironment.isHeadless())
			return;
		int percent = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * 100.0);
		SwingUtilities.invokeLater(() -> {
			ensureProgressUI();
			progressBar.setValue(percent);
			progressBar.setString(percent + "%");
		});
	}

	@Override
	public void showMessage(String message) {
		if (GraphicsEnvironment.isHeadless())
			return;
		SwingUtilities.invokeLater(() -> {
			ensureProgressUI();
			progressLabel.setText(message);
			progressBar.setIndeterminate(false);
		});
	}

	@Override
	public void close() {
		if (GraphicsEnvironment.isHeadless())
			return;
		SwingUtilities.invokeLater(() -> {
			if (progressDialog != null) {
				progressDialog.dispose();
				progressDialog = null;
				progressBar = null;
				progressLabel = null;
			}
		});
	}

	private void ensureProgressUI() {
		if (progressDialog != null)
			return;
		progressDialog = new JDialog();
		progressDialog.setModal(false);
		progressDialog.setTitle("Updating");
		progressDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		progressDialog.setPreferredSize(new Dimension(420, 120));

		progressLabel = new JLabel("Downloading…");
		progressLabel.setBorder(new EmptyBorder(12, 12, 6, 12));

		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);
		progressBar.setString("0%");

		JPanel content = new JPanel(new BorderLayout(0, 0));
		content.add(progressLabel, BorderLayout.PAGE_START);
		content.add(progressBar, BorderLayout.CENTER);
		JPanel pad = new JPanel(new BorderLayout());
		pad.setBorder(new EmptyBorder(0, 12, 12, 12));
		pad.add(content, BorderLayout.CENTER);
		progressDialog.setContentPane(pad);

		progressDialog.pack();
		progressDialog.setLocationRelativeTo(null);
		progressDialog.setVisible(true);
	}
}