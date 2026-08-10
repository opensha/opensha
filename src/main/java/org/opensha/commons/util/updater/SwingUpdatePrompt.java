package org.opensha.commons.util.updater;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.net.URI;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;

import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.MarkdownUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	 * dialog when this is {@code true}. It is {@code true} in the shipped prompt,
	 * so the user is offered all three choices: "Update now", "Remind me later",
	 * and "Skip this version". The dialog width is sized to fit all three buttons
	 * in a single row.
	 */
	private static final boolean SKIP_VERSION_BUTTON_ENABLED = true;

	private static final Logger log = LoggerFactory.getLogger(SwingUpdatePrompt.class);

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
			// Wide enough that "Update and restart now", "Remind me later", and
			// "Skip this version" all fit in a single row of the button panel,
			// with room for the opt-out footer above them.
			dialog.setPreferredSize(new Dimension(620, 380));

			JLabel header = new JLabel("<html><b>A new version of " + appName
					+ " is available: " + (latestVersion == null ? "?" : latestVersion.toString())
					+ "</b></html>");
			header.setBorder(new EmptyBorder(12, 12, 8, 12));

			JEditorPane notes = new JEditorPane();
			notes.setContentType("text/html");
			notes.setEditable(false);
			notes.setText(releaseNotesHtml(releaseNotes));
			notes.setCaretPosition(0); // setText scrolls to the end; reset to top
			notes.addHyperlinkListener(this::openLink);
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
			buttonPanel.setBorder(new EmptyBorder(8, 12, 4, 12));

			// Informational footer telling the user how to opt out of update
			// prompts globally (for this and all future OpenSHA versions) by
			// editing the per-user config file. It performs no action.
			JLabel footer = new JLabel("<html><div style='font-size: 10pt; color: #555555'>"
					+ "Update prompts can be disabled for this and all future OpenSHA versions by setting<br>"
					+ "<code>~/.opensha/config.json</code> with "
					+ "<code>\"disableUpdatePrompts\": true</code>."
					+ "</div></html>");
			footer.setBorder(new EmptyBorder(0, 12, 10, 12));

			// BorderLayout stretches the NORTH/SOUTH children to the full dialog
			// width, so the footer box spans the whole popup (rather than only its
			// natural text width) and the button row stays centered within it.
			JPanel south = new JPanel(new BorderLayout());
			south.add(footer, BorderLayout.NORTH);
			south.add(buttonPanel, BorderLayout.SOUTH);

			JPanel content = new JPanel(new BorderLayout(0, 0));
			content.add(header, BorderLayout.PAGE_START);
			content.add(scroll, BorderLayout.CENTER);
			content.add(south, BorderLayout.PAGE_END);
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
	public void showErrorMessage(String message) {
		if (GraphicsEnvironment.isHeadless())
			return;
		// runUpdateCheck runs on a background thread; shows an error dialog
		// on the EDT and block this thread until the user dismisses it (mirroring
		// prompt()). This guarantees the user sees the message.
		final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
		Runnable showDialog = () -> {
			JOptionPane.showMessageDialog(null, message, "Update Error",
					JOptionPane.ERROR_MESSAGE);
			latch.countDown();
		};
		if (SwingUtilities.isEventDispatchThread()) {
			showDialog.run();
		} else {
			SwingUtilities.invokeLater(showDialog);
			try {
				latch.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
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

	/**
	 * Render the release notes as Markdown into a minimal HTML document for the
	 * {@code text/html} {@link JEditorPane}. Returns a placeholder for
	 * {@code null}/blank notes.
	 */
	private static String releaseNotesHtml(String releaseNotes) {
		// If the body has a "Release Notes" section, show only that section and
		// everything below it, dropping the generic preamble above.
		String section = MarkdownUtils.sectionFromHeading(releaseNotes, "Release Notes");
		String body = MarkdownUtils.renderMarkdownToHtml(section);
		if (body == null || body.isBlank())
			body = "<p><i>No release notes.</i></p>";
		return "<html><head><style>"
				+ "body { font-family: sans-serif; font-size: 11pt; }"
				+ "h1, h2, h3 { margin: 0.6em 0 0.2em; }"
				+ "a { color: #1a5fb4; }"
				+ "code, pre { font-family: monospace; }"
				+ "</style></head><body>" + body + "</body></html>";
	}

	/**
	 * Open release-note links in the system browser when activated. Best-effort:
	 * any failure (no desktop support, malformed URL) is logged, never thrown.
	 */
	private void openLink(HyperlinkEvent e) {
		if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED)
			return;
		try {
			if (Desktop.isDesktopSupported()) {
				URI uri = e.getURL() == null ? null : e.getURL().toURI();
				if (uri != null)
					Desktop.getDesktop().browse(uri);
			}
		} catch (Throwable t) {
			log.warn("Could not open release-notes link: " + e.getURL(), t);
		}
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

	/**
	 * Manual GUI test: shows the update-available prompt with a fake
	 * application name, a test latest version, and a Markdown release-notes
	 * string (including a "Release Notes" section heading, headings, lists,
	 * inline code, and a link) so the rendering and the three-button row can be
	 * eyeballed without launching a full OpenSHA application. After the prompt is
	 * dismissed it also shows the terminal error dialog ({@link #showErrorMessage})
	 * used when an update asset is missing from a release, so that too can be
	 * eyeballed. Run with
	 * {@code java -cp ... org.opensha.commons.util.updater.SwingUpdatePrompt}.
	 *
	 * @param args ignored
	 */
	public static void main(String[] args) {
		String appName = "SwingUpdatePrompt";
		ApplicationVersion latestVersion = new ApplicationVersion(26, 1, 2);
		String releaseNotes = """
                # SwingUpdatePrompt 26.1.2
                
                This is a **manual test** of the update-available prompt. The text above \
                this heading is the generic preamble, which the prompt trims by showing \
                only the `Release Notes` section and everything below it.
                
                ## Release Notes
                
                ### Highlights
                
                - Added a global *disable update prompts* setting.
                - Enabled the **Skip this version** button.
                - Rendered Markdown release notes (via `MarkdownUtils`/commonmark).
                
                ### Notes
                
                Links open in the system browser, e.g. [OpenSHA](https://www.opensha.org).
                
                > Blockquote: the dialog is sized so all three buttons fit on one row.
                
                ```
                code block
                ```
                """;
		UpdatePrompt prompt = new SwingUpdatePrompt();
		UpdatePrompt.Choice choice = prompt.prompt(appName, latestVersion, releaseNotes);
		System.out.println("User chose: " + choice);
		// Also eyeball the terminal error dialog shown when the updated
		// application asset is missing from the latest release.
		prompt.showErrorMessage("A new version of OpenSHA is available (" + latestVersion
				+ "), but the updated application could not be found in that release.\n"
				+ "See the release page for more information:\n"
				+ "https://github.com/opensha/opensha/releases/tag/v" + latestVersion);
		System.out.println("Error dialog dismissed");
		System.exit(0);
	}
}