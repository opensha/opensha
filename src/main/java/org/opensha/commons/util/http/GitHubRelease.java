package org.opensha.commons.util.http;

import java.util.Collections;
import java.util.List;

/**
 * Immutable representation of a GitHub release, as returned by the GitHub REST
 * releases API. Parsed from the API response by {@link GitHubClient} so that
 * callers (and tests) are never coupled to the underlying JSON parser.
 *
 * <p>Notable fields:</p>
 * <ul>
 *   <li>{@code tagName} &mdash; the release tag, e.g. {@code "v26.1.1"}.</li>
 *   <li>{@code name} &mdash; the release title.</li>
 *   <li>{@code body} &mdash; the release notes (Markdown) text.</li>
 *   <li>{@code prerelease} &mdash; whether GitHub marks this release as a
 *       pre-release.</li>
 *   <li>{@code assets} &mdash; the downloadable assets attached to the
 *       release.</li>
 * </ul>
 *
 * @author Akash Bhatthal
 */
public class GitHubRelease {

	private final String tagName;
	private final String name;
	private final String body;
	private final boolean prerelease;
	private final String htmlUrl;
	private final List<GitHubAsset> assets;

	public GitHubRelease(String tagName, String name, String body, boolean prerelease,
			String htmlUrl, List<GitHubAsset> assets) {
		this.tagName = tagName;
		this.name = name;
		this.body = body;
		this.prerelease = prerelease;
		this.htmlUrl = htmlUrl;
		this.assets = assets == null
				? Collections.emptyList()
				: Collections.unmodifiableList(assets);
	}

	public String getTagName() {
		return tagName;
	}

	public String getName() {
		return name;
	}

	/**
	 * @return the release notes body (Markdown), or {@code null} if absent.
	 */
	public String getBody() {
		return body;
	}

	public boolean isPrerelease() {
		return prerelease;
	}

	public String getHtmlUrl() {
		return htmlUrl;
	}

	/**
	 * @return an unmodifiable list of assets attached to this release (empty
	 *         if none).
	 */
	public List<GitHubAsset> getAssets() {
		return assets;
	}

	@Override
	public String toString() {
		return "GitHubRelease[tag=" + tagName + ", prerelease=" + prerelease
				+ ", assets=" + assets.size() + "]";
	}
}