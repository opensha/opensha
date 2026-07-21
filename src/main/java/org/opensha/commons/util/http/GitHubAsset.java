package org.opensha.commons.util.http;

/**
 * Immutable representation of a single asset attached to a GitHub release, as
 * returned by the GitHub REST releases API. This is a plain data-transfer
 * object parsed from the API response by {@link GitHubClient} so that callers
 * (and tests) are never coupled to the underlying JSON parser.
 *
 * <p>Notable fields:</p>
 * <ul>
 *   <li>{@code name} &mdash; the asset filename, e.g.
 *       {@code "HazardCurveGUI-26.1.1.jar"}.</li>
 *   <li>{@code size} &mdash; the asset size in bytes.</li>
 *   <li>{@code browserDownloadUrl} &mdash; the direct download URL (redirects
 *       to GitHub's CDN).</li>
 *   <li>{@code digest} &mdash; the per-asset integrity hash in the form
 *       {@code "sha256:<hex>"} exposed by the API. This is the integrity
 *       gate used by the auto-updater; use {@link #getSha256Hex()} to get the
 *       bare hex digest.</li>
 * </ul>
 *
 * @author Akash Bhatthal
 */
public class GitHubAsset {

	private final String name;
	private final long size;
	private final String browserDownloadUrl;
	private final String digest;

	public GitHubAsset(String name, long size, String browserDownloadUrl, String digest) {
		this.name = name;
		this.size = size;
		this.browserDownloadUrl = browserDownloadUrl;
		this.digest = digest;
	}

	public String getName() {
		return name;
	}

	public long getSize() {
		return size;
	}

	public String getBrowserDownloadUrl() {
		return browserDownloadUrl;
	}

	/**
	 * @return the raw {@code digest} field from the API, e.g.
	 *         {@code "sha256:0c70..."}, or {@code null} if the release did not
	 *         include one.
	 */
	public String getDigest() {
		return digest;
	}

	/**
	 * @return the bare SHA-256 hex digest parsed from {@link #getDigest()}, or
	 *         {@code null} if no digest is present or it is not a SHA-256
	 *         digest.
	 */
	public String getSha256Hex() {
		if (digest == null)
			return null;
		String prefix = "sha256:";
		if (digest.toLowerCase().startsWith(prefix))
			return digest.substring(prefix.length());
		return null;
	}

	@Override
	public String toString() {
		return "GitHubAsset[name=" + name + ", size=" + size + ", digest=" + digest + "]";
	}
}