package org.opensha.commons.util.updater;

import org.opensha.commons.util.ApplicationVersion;

/**
 * Abstraction over the user-facing update prompt so that {@link ApplicationUpdater}
 * can be unit-tested without Swing. The real implementation is
 * {@link SwingUpdatePrompt}; tests use a hand-rolled fake or a Mockito mock of
 * this interface (mirroring the {@code ParameterEditor}/{@code MockStringParameterEditor}
 * testability pattern used elsewhere in OpenSHA).
 *
 * <p>The lifecycle is:</p>
 * <ol>
 *   <li>{@link #prompt(String, ApplicationVersion, String)} blocks until the
 *       user chooses, and returns that {@link Choice}.</li>
 *   <li>If the choice is {@link Choice#UPDATE_NOW}, the download worker drives
 *       {@link #setProgress(double)} (fraction in {@code [0,1]}) while
 *       downloading.</li>
 *   <li>{@link Choice#REMIND_LATER} and {@link Choice#SKIP_THIS_VERSION} stop
 *       the flow before any download, so {@link #setProgress(double)} is not
 *       called for those choices.</li>
 *   <li>{@link #showMessage(String)} announces completion/failure.</li>
 *   <li>{@link #close()} releases any window resources.</li>
 * </ol>
 *
 * @author Akash Bhatthal
 */
public interface UpdatePrompt {

	/** User's response to an update-available prompt. */
	enum Choice {
		/** Download, install, and restart now. */
		UPDATE_NOW,
		/** Dismiss and remind the user again later. */
		REMIND_LATER,
		/** Permanently skip this version (do not prompt again until a newer release). */
		SKIP_THIS_VERSION
	}

	/**
	 * Show the "a new version is available" prompt and block until the user
	 * decides.
	 *
	 * @param appName        display name of the application being updated
	 * @param latestVersion  the latest available version
	 * @param releaseNotes   Markdown/plain-text release notes to display
	 *                       (may be {@code null})
	 * @return the user's choice
	 */
	Choice prompt(String appName, ApplicationVersion latestVersion, String releaseNotes);

	/**
	 * Show or update a progress indicator for the download.
	 *
	 * @param fraction completed fraction in {@code [0,1]}
	 */
	void setProgress(double fraction);

	/**
	 * Show a completion or status message (e.g. "Download finished, restarting
	 * &hellip;").
	 *
	 * @param message the message to display
	 */
	void showMessage(String message);

	/**
	 * Close and dispose any window(s) held by this prompt.
	 */
	void close();
}