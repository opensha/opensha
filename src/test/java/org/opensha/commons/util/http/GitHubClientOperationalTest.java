package org.opensha.commons.util.http;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Live-API smoke test for {@link GitHubClient}. This hits the real GitHub REST
 * releases endpoint and is therefore network- and rate-limit-dependent. It is
 * named {@code *Operational*} so it only runs via {@code ./gradlew
 * testOperational} and is excluded from the default {@code ./gradlew test}
 * suite-only run. Parsing regressions are covered deterministically and
 * offline by {@link GitHubClientTest}.
 *
 * @author Akash Bhatthal
 */
public class GitHubClientOperationalTest {

	private static GitHubClient client;
	private static GitHubRelease latest;

	@BeforeClass
	public static void setUp() throws IOException {
		client = new GitHubClient();
		latest = client.getLatestRelease();
	}

	@Test(timeout = 30000)
	public final void testLatestReleaseFetch() {
		assertNotNull("latest release should be retrievable (network required)", latest);
		assertNotNull(latest.getTagName());
		assertTrue("latest release should have at least one asset", latest.getAssets().size() > 0);
	}

	@Test(timeout = 30000)
	public final void testLatestReleaseAssetsHaveDigest() throws IOException {
		assertNotNull("latest release should be retrievable (network required)", latest);
		for (GitHubAsset asset : latest.getAssets()) {
			assertNotNull("asset " + asset.getName() + " missing digest", asset.getDigest());
			assertTrue("asset " + asset.getName() + " digest should be sha256",
					asset.getDigest().toLowerCase().startsWith("sha256:"));
		}
	}

	@Test(timeout = 30000)
	public final void testRecentReleasesFetch() throws IOException {
		List<GitHubRelease> recent = client.getRecentReleases(5, 1);
		assertNotNull(recent);
		assertTrue("expected at least 1 recent release", recent.size() > 0);
		for (GitHubRelease r : recent)
			assertNotNull(r.getTagName());
	}
}