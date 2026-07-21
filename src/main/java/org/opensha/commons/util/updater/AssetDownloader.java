package org.opensha.commons.util.updater;

import java.io.IOException;
import java.nio.file.Path;

import org.opensha.commons.util.http.GitHubAsset;

/**
 * Functional strategy for downloading a release asset to disk while reporting
 * progress. The production implementation ({@code ApplicationUpdater}'s
 * {@code downloadAssetToTemp}) streams the asset to a {@code .part} temp file,
 * verifies its SHA-256 against the GitHub-provided {@code digest}, and atomically
 * renames it to its honest versioned name; tests inject a fake to avoid the
 * network. This is a top-level interface (rather than nested in
 * {@link ApplicationUpdater}) so implementations and their unit tests can live
 * alongside it.
 *
 * @author Akash Bhatthal
 */
@FunctionalInterface
public interface AssetDownloader {

	/**
	 * Download (and verify) the given asset and return the path of the resulting
	 * file on disk.
	 *
	 * @param asset  the release asset to download
	 * @param prompt sink for download-progress updates (may be {@code null})
	 * @return path to the downloaded (and digest-verified) file
	 * @throws IOException if the download ultimately fails
	 */
	Path download(GitHubAsset asset, UpdatePrompt prompt) throws IOException;
}