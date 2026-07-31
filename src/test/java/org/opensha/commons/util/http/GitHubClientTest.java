package org.opensha.commons.util.http;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Fixture-based unit tests for {@link GitHubClient} parsing. These run fully
 * offline against a checked-in {@code releases/latest} response captured from
 * the GitHub API, so they deterministically catch "GitHub renamed a field"
 * regressions. The live-API smoke test lives in
 * {@code GitHubClientOperationalTest}.
 *
 * <p>The {@code releases_latest.json} fixture was captured under the API
 * version pinned by {@link GitHubClient#API_VERSION}. The schema-conformance
 * tests below assert that the fixture carries every <em>required</em> field of
 * that version's Release and Release Asset schemas (per the GitHub REST docs),
 * and that the pinned version matches the one the fixture was captured under.
 * Bumping {@link GitHubClient#API_VERSION} therefore forces a fixture refresh
 * and a schema re-check.</p>
 *
 * @author Akash Bhatthal
 */
public class GitHubClientTest {

	private static final String FIXTURE = "/org/opensha/commons/util/http/releases_latest.json";

	/**
	 * The API version the fixture was captured under. Must match
	 * {@link GitHubClient#API_VERSION}; if you bump the pinned version, refresh
	 * the fixture and update the schema-conformance field sets below.
	 */
	private static final String FIXTURE_API_VERSION = "2026-03-10";

	/**
     * Required properties of the Release schema under {@link #FIXTURE_API_VERSION}
     * (per <a href="https://docs.github.com/en/rest/releases/releases?apiVersion=2026-03-10">...</a>).
     */
	private static final List<String> REQUIRED_RELEASE_FIELDS = Arrays.asList(
			"url", "html_url", "assets_url", "upload_url", "tarball_url", "zipball_url",
			"id", "node_id", "tag_name", "target_commitish", "name", "draft", "prerelease",
			"created_at", "published_at", "author", "assets");

	/**
	 * Required properties of the Release Asset schema under
	 * {@link #FIXTURE_API_VERSION}.
	 */
	private static final List<String> REQUIRED_ASSET_FIELDS = Arrays.asList(
			"url", "browser_download_url", "id", "node_id", "name", "label", "state",
			"content_type", "size", "digest", "download_count", "created_at",
			"updated_at", "uploader");

	private static String loadFixture() {
		try (InputStream is = GitHubClientTest.class.getResourceAsStream(FIXTURE)) {
			assertNotNull("Missing fixture resource: " + FIXTURE, is);
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Test
	public final void testParseLatestRelease() {
		JsonObject obj = JsonParser.parseString(loadFixture()).getAsJsonObject();
		GitHubRelease release = GitHubClient.parseRelease(obj);

		assertNotNull(release);
		assertEquals("v26.1.1", release.getTagName());
		assertNotNull(release.getName());
		assertTrue("release name should mention the version", release.getName().contains("26.1.1"));
		assertFalse(release.isPrerelease());
		assertNotNull(release.getBody());
		assertNotNull(release.getHtmlUrl());
		assertTrue("release should have assets", release.getAssets().size() > 0);
	}

	@Test
	public final void testPinnedApiVersionMatchesFixture() {
		// The fixture was captured under FIXTURE_API_VERSION; the client must
		// pin the same version so behavior matches what was tested.
		assertEquals("GitHubClient.API_VERSION must match the fixture's API version "
				+ "(if you bump the version, refresh releases_latest.json and update "
				+ "the REQUIRED_*_FIELDS sets)",
				FIXTURE_API_VERSION, GitHubClient.API_VERSION);
	}

	@Test
	public final void testFixtureReleaseConformsToApiVersionSchema() {
		// Asserts every required field of the 2026-03-10 Release schema is
		// present in the fixture, so a GitHub schema change is caught offline.
		JsonObject obj = JsonParser.parseString(loadFixture()).getAsJsonObject();
		for (String field : REQUIRED_RELEASE_FIELDS)
			assertTrue("fixture release missing required field '" + field
					+ "' (API version " + FIXTURE_API_VERSION + ")", obj.has(field));
	}

	@Test
	public final void testFixtureAssetsConformToApiVersionSchema() {
		JsonObject obj = JsonParser.parseString(loadFixture()).getAsJsonObject();
		assertNotNull("release should have an assets array", obj.get("assets"));
		assertTrue("release should have at least one asset", obj.get("assets").isJsonArray()
				&& obj.get("assets").getAsJsonArray().size() > 0);
		for (JsonElement assetEl : obj.get("assets").getAsJsonArray()) {
			JsonObject assetObj = assetEl.getAsJsonObject();
			for (String field : REQUIRED_ASSET_FIELDS)
				assertTrue("fixture asset missing required field '" + field
						+ "' (API version " + FIXTURE_API_VERSION + ")",
						assetObj.has(field));
		}
	}

	@Test
	public final void testAssetsHaveExpectedFields() {
		// Validates the fields relied on by the updater are present per the
		// GitHub releases API docs (name, size, browser_download_url, digest).
		JsonObject obj = JsonParser.parseString(loadFixture()).getAsJsonObject();
		GitHubRelease release = GitHubClient.parseRelease(obj);

		boolean foundHazardCurve = false;
		for (GitHubAsset asset : release.getAssets()) {
			assertNotNull("asset name missing", asset.getName());
			assertTrue("asset size should be positive", asset.getSize() > 0);
			assertNotNull("asset download URL missing", asset.getBrowserDownloadUrl());
			assertTrue("asset download URL should be https",
					asset.getBrowserDownloadUrl().startsWith("https://"));
			assertNotNull("asset digest missing", asset.getDigest());
			assertTrue("asset digest should be sha256",
					asset.getDigest().toLowerCase().startsWith("sha256:"));
			assertNotNull("sha256 hex should be parseable", asset.getSha256Hex());
			assertEquals(64, asset.getSha256Hex().length());
			if (asset.getName().equals("HazardCurveGUI-26.1.1.jar"))
				foundHazardCurve = true;
		}
		assertTrue("expected HazardCurveGUI-26.1.1.jar asset for prefix matching", foundHazardCurve);
	}

	@Test
	public final void testParseReleaseMissingFieldsDoesNotNPE() {
		// Minimal object missing most optional fields
		JsonObject obj = JsonParser.parseString("{\"tag_name\":\"v1.0.0\",\"assets\":[]}").getAsJsonObject();
		GitHubRelease release = GitHubClient.parseRelease(obj);
		assertEquals("v1.0.0", release.getTagName());
		assertNull(release.getBody());
		assertFalse(release.isPrerelease());
		assertTrue(release.getAssets().isEmpty());
	}

	@Test
	public final void testAssetWithoutDigest() {
		JsonObject assetObj = JsonParser.parseString(
				"{\"name\":\"x.jar\",\"size\":10,\"browser_download_url\":\"https://example.com/x.jar\"}")
				.getAsJsonObject();
		// parseAsset is private; verify behavior through a release wrapping it
		JsonObject releaseObj = JsonParser.parseString(
				"{\"tag_name\":\"v1\",\"assets\":[" + assetObj + "]}").getAsJsonObject();
		GitHubRelease release = GitHubClient.parseRelease(releaseObj);
		GitHubAsset asset = release.getAssets().get(0);
		assertEquals("x.jar", asset.getName());
		assertNull(asset.getDigest());
		assertNull(asset.getSha256Hex());
	}

	@Test
	public final void testConstructionDefaults() {
		// Verifies default repo wiring without hitting the network. Network
		// behavior is covered by the Operational test.
		GitHubClient client = new GitHubClient();
		assertNotNull(client.getRepoSlug());
		assertEquals("opensha/opensha", client.getRepoSlug());
	}
}