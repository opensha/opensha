package org.opensha.commons.util.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Thin transport client for the GitHub REST releases API. Built on
 * {@link java.net.http.HttpClient} and parses responses into the
 * {@link GitHubRelease}/{@link GitHubAsset} DTOs so that callers and tests are
 * never coupled to the JSON parser.
 *
 * <p>This class is intentionally stateless: it issues requests and parses
 * responses only, with no caching &mdash; the auto-updater checks for updates
 * once at launch, so a cache would add no value. To retarget a fork that
 * publishes its own releases, use {@link #GitHubClient(String, String)}.</p>
 *
 * <p>Methods throw {@link IOException} (translated from HTTP/timeout errors);
 * callers decide how to handle a network failure.</p>
 *
 * <p>Required headers (GitHub returns 403 without a {@code User-Agent}):
 * {@code User-Agent}, {@code Accept: application/vnd.github+json}, and
 * {@code X-GitHub-Api-Version}.</p>
 *
 * @author Akash Bhatthal
 */
public class GitHubClient {

	/** Default repository whose releases are queried for updates. */
	public static final String DEFAULT_OWNER = "opensha";
	public static final String DEFAULT_REPO = "opensha";

	/**
	 * GitHub REST API version header value. {@code 2026-03-10} is the latest
	 * published API version (released 2026-03-10); the prior {@code 2022-11-28}
	 * version is scheduled to leave support on 2028-03-10. See
	 * <a href="https://docs.github.com/en/rest/about-the-rest-api/api-versions">API versions</a>.
	 */
	public static final String API_VERSION = "2026-03-10";
	private static final String ACCEPT = "application/vnd.github+json";
	private static final String USER_AGENT = "OpenSHA-Updater/1.0 (https://github.com/opensha/opensha)";

	private static final String API_BASE = "https://api.github.com/repos/";

	private final String owner;
	private final String repo;
	private final HttpClient httpClient;

	/**
	 * Construct a client for the default {@code opensha/opensha} repository.
	 */
	public GitHubClient() {
		this(DEFAULT_OWNER, DEFAULT_REPO);
	}

	/**
	 * Construct a client for a specific repository.
	 *
	 * @param owner repository owner (e.g. {@code "opensha"})
	 * @param repo  repository name (e.g. {@code "opensha"})
	 */
	public GitHubClient(String owner, String repo) {
		this(owner, repo, HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(30))
				.build());
	}

	/**
	 * Construct a client with an explicit {@link HttpClient}. Primarily for
	 * testing or when a shared client is desired.
	 */
	public GitHubClient(String owner, String repo, HttpClient httpClient) {
		this.owner = owner;
		this.repo = repo;
		this.httpClient = httpClient;
	}

	/**
	 * Fetch only the latest published release.
	 *
	 * @return the latest release, or {@code null} if the API returned no body
	 *         (e.g. no published releases exist)
	 * @throws IOException if the request fails or returns a non-2xx status
	 */
	public GitHubRelease getLatestRelease() throws IOException {
		String url = API_BASE + owner + "/" + repo + "/releases/latest";
		String json = getJson(url);
		if (json == null || json.isBlank())
			return null;
		return parseRelease(JsonParser.parseString(json).getAsJsonObject());
	}

	/**
	 * Fetch a page of recent releases, most-recent first.
	 *
	 * @param perPage number of releases per page (1&ndash;100; GitHub's default
	 *                is 30)
	 * @param page    1-based page number
	 * @return the releases on that page (empty list if none)
	 * @throws IOException if the request fails or returns a non-2xx status
	 */
	public List<GitHubRelease> getRecentReleases(int perPage, int page) throws IOException {
		String url = API_BASE + owner + "/" + repo + "/releases?per_page=" + perPage + "&page=" + page;
		String json = getJson(url);
		if (json == null || json.isBlank())
			return new ArrayList<>();
		JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
		List<GitHubRelease> releases = new ArrayList<>();
		for (JsonElement el : arr)
			releases.add(parseRelease(el.getAsJsonObject()));
		return releases;
	}

	/**
	 * Convenience: fetch the first page of recent releases with GitHub's
	 * default page size of 30.
	 */
	public List<GitHubRelease> getRecentReleases() throws IOException {
		return getRecentReleases(30, 1);
	}

	private String getJson(String url) throws IOException {
		HttpRequest request;
		try {
			request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.GET()
					.header("User-Agent", USER_AGENT)
					.header("Accept", ACCEPT)
					.header("X-GitHub-Api-Version", API_VERSION)
					.timeout(Duration.ofSeconds(60))
					.build();
		} catch (IllegalArgumentException e) {
			throw new IOException("Invalid GitHub API URL: " + url, e);
		}
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			int code = response.statusCode();
			if (code < 200 || code >= 300) {
				// 404 typically means no releases exist for the repo
				throw new IOException("GitHub API request failed (HTTP " + code + "): " + url);
			}
			return response.body();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("GitHub API request interrupted: " + url, e);
		} catch (java.net.ConnectException | java.net.SocketTimeoutException e) {
			throw new IOException("Could not reach GitHub API: " + url, e);
		} catch (IOException e) {
			throw e;
		} catch (Throwable t) {
			// HttpRequest.send can wrap other exceptions; surface as IOException
			throw new IOException("GitHub API request failed: " + url, t);
		}
	}

	/**
	 * Parse a single release JSON object into a {@link GitHubRelease}. Package
	 * visible so tests can exercise parsing directly against fixtures.
	 */
	static GitHubRelease parseRelease(JsonObject obj) {
		String tag = getAsString(obj, "tag_name");
		String name = getAsString(obj, "name");
		String body = getAsString(obj, "body");
		boolean prerelease = obj.has("prerelease") && !obj.get("prerelease").isJsonNull()
				&& obj.get("prerelease").getAsBoolean();
		String htmlUrl = getAsString(obj, "html_url");
		List<GitHubAsset> assets = new ArrayList<>();
		if (obj.has("assets") && obj.get("assets").isJsonArray()) {
			for (JsonElement a : obj.get("assets").getAsJsonArray())
				assets.add(parseAsset(a.getAsJsonObject()));
		}
		return new GitHubRelease(tag, name, body, prerelease, htmlUrl, assets);
	}

	private static GitHubAsset parseAsset(JsonObject obj) {
		String name = getAsString(obj, "name");
		long size = obj.has("size") && !obj.get("size").isJsonNull() ? obj.get("size").getAsLong() : 0L;
		String downloadUrl = getAsString(obj, "browser_download_url");
		String digest = getAsString(obj, "digest");
		return new GitHubAsset(name, size, downloadUrl, digest);
	}

	private static String getAsString(JsonObject obj, String key) {
		if (obj.has(key) && !obj.get(key).isJsonNull())
			return obj.get(key).getAsString();
		return null;
	}

	String getRepoSlug() {
		return owner + "/" + repo;
	}
}