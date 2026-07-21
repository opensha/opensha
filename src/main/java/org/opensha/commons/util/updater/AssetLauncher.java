package org.opensha.commons.util.updater;

import java.nio.file.Path;

/**
 * Functional strategy for handing off to a freshly downloaded JAR: launch it in
 * a detached process and exit the current JVM. The production implementation
 * ({@code ApplicationUpdater}'s {@code launchJar}) spawns
 * {@code java -jar <path>} and calls {@code System.exit(0)}; tests inject a
 * recording no-op to avoid terminating the test JVM. The file is already on disk
 * under its versioned name (placed by {@link AssetDownloader}); this interface
 * only launches it, it does not move or rename anything.
 *
 * @author Akash Bhatthal
 */
@FunctionalInterface
public interface AssetLauncher {

	/**
	 * Launch the JAR at the given path and exit this JVM.
	 *
	 * @param jar path of the JAR to launch
	 */
	void launch(Path jar);
}