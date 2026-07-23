package org.opensha.commons.util.updater;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.http.GitHubAsset;
import org.opensha.commons.util.http.GitHubClient;
import org.opensha.commons.util.http.GitHubRelease;

/**
 * Unit tests for {@link ApplicationUpdater} logic: stable-version parsing and
 * fallback, {@code isOutdated}, asset-prefix selection, SHA-256 verification,
 * and the prompt decision flow (with the download and install steps injected
 * as fakes so no network is used and {@code System.exit} is avoided). The
 * {@link GitHubClient} transport is mocked, so no network is required.
 *
 * @author Akash Bhatthal
 */
public class ApplicationUpdaterTest {

	private GitHubClient client;
	private UpdatePrompt prompt;
	private String shortName;

	private GitHubAsset asset(String name) {
		return new GitHubAsset(name, 1000L, "https://example.com/" + name,
				"sha256:" + "a".repeat(64));
	}

	private GitHubRelease release(String tag, boolean prerelease, String body, String... assetNames) {
		List<GitHubAsset> assets = new ArrayList<>();
		for (String n : assetNames)
			assets.add(asset(n));
		return new GitHubRelease(tag, tag, body, prerelease, "https://example.com/" + tag, assets);
	}

	@Before
	public void setUp() {
		client = mock(GitHubClient.class);
		prompt = mock(UpdatePrompt.class);
		shortName = "UpdaterUnitTest_" + System.nanoTime();
	}

	@After
	public void tearDown() {
		// Clear any prefs written by the remind-later / skip / cleanup flows so
		// tests stay isolated.
		java.util.prefs.Preferences prefs =
				java.util.prefs.Preferences.userNodeForPackage(ApplicationUpdater.class);
		prefs.remove("remindAfter_" + shortName);
		prefs.remove("skipVersion_" + shortName);
		try {
			for (String key : prefs.keys()) {
				if (key.startsWith("cleanupAsked_" + shortName)) {
					prefs.remove(key);
				}
			}
		} catch (Exception ignore) {}
		try {
			prefs.flush();
		} catch (Exception ignore) {}
	}

	// ---- stable version parsing ----

	@Test
	public final void testParseStableVersion() {
		assertEquals(new ApplicationVersion(26, 1, 1), ApplicationUpdater.parseStableVersion("v26.1.1"));
		assertEquals(new ApplicationVersion(26, 1, 1), ApplicationUpdater.parseStableVersion("26.1.1"));
		assertEquals(new ApplicationVersion(26, 1, 0), ApplicationUpdater.parseStableVersion("v26.1"));
		assertEquals(new ApplicationVersion(26, 1, 0), ApplicationUpdater.parseStableVersion("26.1"));
	}

	@Test
	public final void testParseStableVersionRejectsNonStable() {
		assertNull(ApplicationUpdater.parseStableVersion("v27.0.0-beta"));
		assertNull(ApplicationUpdater.parseStableVersion("v27.0.0-alpha"));
		assertNull(ApplicationUpdater.parseStableVersion("v26.1.1.BETA"));
		assertNull(ApplicationUpdater.parseStableVersion("abc"));
		assertNull(ApplicationUpdater.parseStableVersion(""));
		assertNull(ApplicationUpdater.parseStableVersion(null));
		// too many numeric components
		assertNull(ApplicationUpdater.parseStableVersion("v26.1.1.2"));
	}

	// ---- latest stable release selection ----

	@Test
	public final void testGetLatestStableVersionWhenLatestIsStable() throws IOException {
		when(client.getLatestRelease()).thenReturn(release("v26.1.1", false, "notes"));
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 0), (a, p) -> null, p -> {});
		assertEquals(new ApplicationVersion(26, 1, 1), u.getLatestStableVersion());
		verify(client, never()).getRecentReleases();
	}

	@Test
	public final void testGetLatestStableVersionFallsBackAcrossPage() throws IOException {
		when(client.getLatestRelease()).thenReturn(release("v27.0.0-beta", true, "beta notes"));
		when(client.getRecentReleases()).thenReturn(Arrays.asList(
				release("v27.0.0-beta", true, "beta notes"),
				release("v26.1.1", false, "stable notes"),
				release("v26.1.0", false, "older notes")));
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 0), (a, p) -> null, p -> {});
		assertEquals(new ApplicationVersion(26, 1, 1), u.getLatestStableVersion());
		assertEquals("stable notes", u.getLatestStableReleaseNotes());
	}

	@Test
	public final void testGetLatestStableVersionNoneAvailable() throws IOException {
		when(client.getLatestRelease()).thenReturn(release("v27.0.0-beta", true, "beta"));
		when(client.getRecentReleases()).thenReturn(List.of(release("v27.0.0-beta", true, "beta")));
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 0), (a, p) -> null, p -> {});
		assertNull(u.getLatestStableVersion());
		assertNull(u.getLatestStableReleaseNotes());
	}

	@Test
	public final void testGetLatestStableReleaseNullOnIoError() throws IOException {
		// A network failure is treated as "no update available", never propagated.
		when(client.getLatestRelease()).thenThrow(new IOException("network down"));
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 0), (a, p) -> null, p -> {});
		assertNull(u.getLatestStableRelease());
		assertNull(u.getLatestStableVersion());
		assertFalse(u.isOutdated());
	}

	// ---- isOutdated ----

	@Test
	public final void testIsOutdatedTrue() throws IOException {
		when(client.getLatestRelease()).thenReturn(release("v26.1.1", false, "notes"));
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 0), (a, p) -> null, p -> {});
		assertTrue(u.isOutdated());
	}

	@Test
	public final void testIsOutdatedFalseWhenUpToDate() throws IOException {
		when(client.getLatestRelease()).thenReturn(release("v26.1.1", false, "notes"));
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 1), (a, p) -> null, p -> {});
		assertFalse(u.isOutdated());
	}

	@Test
	public final void testIsOutdatedFalseWhenRunningVersionUnknown() throws IOException {
		when(client.getLatestRelease()).thenReturn(release("v26.1.1", false, "notes"));
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(-1, -1, -1), (a, p) -> null, p -> {});
		assertFalse(u.isOutdated());
	}

	@Test
	public final void testIsOutdatedFalseWhenNoRemoteStable() throws IOException {
		when(client.getLatestRelease()).thenReturn(release("v27.0.0-beta", true, "beta"));
		when(client.getRecentReleases()).thenReturn(List.of(release("v27.0.0-beta", true, "beta")));
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 0), (a, p) -> null, p -> {});
		assertFalse(u.isOutdated());
	}

	// ---- asset selection ----

	@Test
	public final void testSelectAssetByPrefix() {
		GitHubRelease rel = release("v26.1.1", false, "notes",
				"HazardCurveGUI-26.1.1.jar", "opensha-all.jar");
		GitHubAsset a = ApplicationUpdater.selectAsset(rel, "HazardCurveGUI");
		assertNotNull(a);
		assertEquals("HazardCurveGUI-26.1.1.jar", a.getName());
	}

	@Test
	public final void testSelectAssetNoMatch() {
		GitHubRelease rel = release("v26.1.1", false, "notes", "OtherGUI-26.1.1.jar");
		assertNull(ApplicationUpdater.selectAsset(rel, "HazardCurveGUI"));
		assertNull(ApplicationUpdater.selectAsset(null, "HazardCurveGUI"));
		assertNull(ApplicationUpdater.selectAsset(rel, null));
	}

	// ---- SHA-256 ----

	@Test
	public final void testSha256Hex() throws IOException {
		// SHA-256 of the ASCII bytes "abc" is a known constant.
		Path tmp = Files.createTempFile("updater-sha-test-", ".bin");
		Files.writeString(tmp, "abc");
		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
				ApplicationUpdater.sha256Hex(tmp));
		Files.deleteIfExists(tmp);
	}

	// ---- decision flow ----

	@Test
	public final void testRunUpdateCheckUpToDateDoesNotPrompt() throws IOException {
		when(client.getLatestRelease()).thenReturn(release("v26.1.0", false, "notes"));
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 0), (a, p) -> null, p -> {});
		u.runUpdateCheck("Test App", shortName, "HazardCurveGUI");
		verify(prompt, never()).prompt(anyString(), any(), anyString());
	}

	@Test
	public final void testRunUpdateCheckRemindLaterDefersNextPrompt() throws IOException {
		when(client.getLatestRelease()).thenReturn(release("v26.1.1", false, "notes"));
		when(prompt.prompt(anyString(), any(), anyString())).thenReturn(UpdatePrompt.Choice.REMIND_LATER);
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 0), (a, p) -> null, p -> {});

		u.runUpdateCheck("Test App", shortName, "HazardCurveGUI");
		// Second check should be suppressed by the remind-later preference.
		u.runUpdateCheck("Test App", shortName, "HazardCurveGUI");

		verify(prompt, times(1)).prompt(anyString(), any(), anyString());
		verify(prompt, never()).setProgress(anyDouble());
	}

	@Test
	public final void testRunUpdateCheckSkipVersionSuppressesNextPrompt() throws IOException {
		when(client.getLatestRelease()).thenReturn(release("v26.1.1", false, "notes"));
		when(prompt.prompt(anyString(), any(), anyString())).thenReturn(UpdatePrompt.Choice.SKIP_THIS_VERSION);
		List<Path> downloaded = new ArrayList<>();
		AssetDownloader recorder = (a, p) -> { downloaded.add(null); return null; };
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 0), recorder, p -> {});

		u.runUpdateCheck("Test App", shortName, "HazardCurveGUI");
		// Second check should be suppressed by the skip-version preference.
		u.runUpdateCheck("Test App", shortName, "HazardCurveGUI");

		verify(prompt, times(1)).prompt(anyString(), any(), anyString());
		verify(prompt, never()).setProgress(anyDouble());
		assertTrue("skip should not download", downloaded.isEmpty());
		java.util.prefs.Preferences prefs =
				java.util.prefs.Preferences.userNodeForPackage(ApplicationUpdater.class);
		assertEquals("26.1.1", prefs.get("skipVersion_" + shortName, null));
	}

	@Test
	public final void testRunUpdateCheckSkipVersionRePromptsAtNewerRelease() throws IOException {
		when(client.getLatestRelease()).thenReturn(release("v26.1.1", false, "notes"));
		when(prompt.prompt(anyString(), any(), anyString())).thenReturn(UpdatePrompt.Choice.SKIP_THIS_VERSION);
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 0), (a, p) -> null, p -> {});

		u.runUpdateCheck("Test App", shortName, "HazardCurveGUI"); // skips 26.1.1
		// A newer latest release no longer matches the skipped version.
		when(client.getLatestRelease()).thenReturn(release("v26.1.2", false, "notes"));
		when(prompt.prompt(anyString(), any(), anyString())).thenReturn(UpdatePrompt.Choice.REMIND_LATER);
		u.runUpdateCheck("Test App", shortName, "HazardCurveGUI");

		verify(prompt, times(2)).prompt(anyString(), any(), anyString());
	}

	@Test
	public final void testRunUpdateCheckUpdateNowDownloadsAndInstalls() throws IOException {
		GitHubRelease rel = release("v26.1.1", false, "release notes",
				"HazardCurveGUI-26.1.1.jar", "opensha-all.jar");
		when(client.getLatestRelease()).thenReturn(rel);
		when(prompt.prompt(anyString(), any(), anyString())).thenReturn(UpdatePrompt.Choice.UPDATE_NOW);

		Path staged = Files.createTempFile("updater-staged-", ".jar");
		Files.writeString(staged, "fake jar bytes");

		List<Path> installed = new ArrayList<>();
		AssetDownloader fakeDownloader = (asset, p) -> {
			p.setProgress(1.0);
			return staged;
		};
		AssetLauncher recordingLauncher = installed::add;

		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 0), fakeDownloader, recordingLauncher);
		u.runUpdateCheck("Test App", shortName, "HazardCurveGUI");

		assertEquals(1, installed.size());
		assertEquals(staged, installed.get(0));
		verify(prompt, times(1)).prompt(anyString(), eq(new ApplicationVersion(26, 1, 1)), eq("release notes"));
		verify(prompt, atLeastOnce()).setProgress(anyDouble());
		verify(prompt, atLeastOnce()).showMessage(contains("Download finished"));
	}

	@Test
	public final void testRunUpdateCheckNoMatchingAssetShowsError() throws IOException {
		GitHubRelease rel = release("v26.1.1", false, "notes", "OtherGUI-26.1.1.jar");
		when(client.getLatestRelease()).thenReturn(rel);
		when(prompt.prompt(anyString(), any(), anyString())).thenReturn(UpdatePrompt.Choice.UPDATE_NOW);
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 0), (a, p) -> null, p -> {});
		u.runUpdateCheck("Test App", shortName, "HazardCurveGUI");
		verify(prompt, atLeastOnce()).showMessage(contains("No downloadable asset"));
	}

	@Test
	public final void testRunUpdateCheckDownloadFailureShowsError() throws IOException {
		GitHubRelease rel = release("v26.1.1", false, "notes", "HazardCurveGUI-26.1.1.jar");
		when(client.getLatestRelease()).thenReturn(rel);
		when(prompt.prompt(anyString(), any(), anyString())).thenReturn(UpdatePrompt.Choice.UPDATE_NOW);
		AssetDownloader failingDownloader = (asset, p) -> {
			throw new IOException("network down");
		};
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 0), failingDownloader, p -> {});
		u.runUpdateCheck("Test App", shortName, "HazardCurveGUI");
		verify(prompt, atLeastOnce()).showMessage(contains("Download failed"));
	}

	// ---- old-version cleanup ----

	@Test
	public final void testFindOldJarsSelectsOnlyOlderVersionedSiblings() throws IOException {
		Path dir = Files.createTempDirectory("updater-oldjars-");
		Files.createFile(dir.resolve("HazardCurveGUI-26.1.0.jar"));
		Files.createFile(dir.resolve("HazardCurveGUI-26.1.1.jar"));
		Files.createFile(dir.resolve("HazardCurveGUI-26.1.2.jar")); // running
		Files.createFile(dir.resolve("OtherGUI-1.0.0.jar"));
		Files.createFile(dir.resolve("readme.txt"));
		List<Path> old = ApplicationUpdater.findOldJars("HazardCurveGUI",
				new ApplicationVersion(26, 1, 2), dir);
		List<String> names = new ArrayList<>();
		for (Path p : old)
			names.add(p.getFileName().toString());
		assertEquals(2, old.size());
		assertTrue(names.contains("HazardCurveGUI-26.1.0.jar"));
		assertTrue(names.contains("HazardCurveGUI-26.1.1.jar"));
		// the running jar (equal version), a different prefix, and a non-jar are excluded
		assertFalse(names.contains("HazardCurveGUI-26.1.2.jar"));
		assertFalse(names.contains("OtherGUI-1.0.0.jar"));
		assertFalse(names.contains("readme.txt"));
	}

	@Test
	public final void testFindInstalledJarFindsMatchingSibling() throws IOException {
		Path dir = Files.createTempDirectory("updater-installed-");
		Files.createFile(dir.resolve("HazardCurveGUI-26.1.2.jar")); // the latest, already installed
		Files.createFile(dir.resolve("HazardCurveGUI-26.1.1.jar")); // older
		Files.createFile(dir.resolve("OtherGUI-26.1.2.jar"));      // different prefix
		Files.createFile(dir.resolve("readme.txt"));
		Path found = ApplicationUpdater.findInstalledJar("HazardCurveGUI",
				new ApplicationVersion(26, 1, 2), dir);
		assertNotNull("should find the already-installed latest jar", found);
		assertEquals("HazardCurveGUI-26.1.2.jar", found.getFileName().toString());
	}

	@Test
	public final void testFindInstalledJarReturnsNullWhenNotPresent() throws IOException {
		Path dir = Files.createTempDirectory("updater-installed-none-");
		Files.createFile(dir.resolve("HazardCurveGUI-26.1.1.jar")); // only older present
		assertNull(ApplicationUpdater.findInstalledJar("HazardCurveGUI",
				new ApplicationVersion(26, 1, 2), dir));
		assertNull(ApplicationUpdater.findInstalledJar("HazardCurveGUI",
				new ApplicationVersion(26, 1, 2), null));
		assertNull(ApplicationUpdater.findInstalledJar("HazardCurveGUI",
				null, dir));
	}

	@Test
	public final void testCleanupOldVersionsDeletesOnConfirm() throws IOException {
		Path dir = Files.createTempDirectory("updater-cleanup-confirm-");
		Path running = dir.resolve("HazardCurveGUI-26.1.2.jar");
		Files.createFile(running);
		Path old0 = dir.resolve("HazardCurveGUI-26.1.0.jar");
		Path old1 = dir.resolve("HazardCurveGUI-26.1.1.jar");
		Files.createFile(old0);
		Files.createFile(old1);
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 2), (a, p) -> null, p -> {});
		u.cleanupOldVersions(shortName, "HazardCurveGUI", dir, names -> true);
		assertFalse("old jar should be deleted on confirm", Files.exists(old0));
		assertFalse("old jar should be deleted on confirm", Files.exists(old1));
		assertTrue("running jar must be kept", Files.exists(running));
		assertTrue("cleanup gate should be set after prompting",
				cleanupAskedPref(shortName, new ApplicationVersion(26, 1, 2)));
	}

	@Test
	public final void testCleanupOldVersionsKeepsOnDecline() throws IOException {
		Path dir = Files.createTempDirectory("updater-cleanup-decline-");
		Path running = dir.resolve("HazardCurveGUI-26.1.2.jar");
		Files.createFile(running);
		Path old = dir.resolve("HazardCurveGUI-26.1.1.jar");
		Files.createFile(old);
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				new ApplicationVersion(26, 1, 2), (a, p) -> null, p -> {});
		u.cleanupOldVersions(shortName, "HazardCurveGUI", dir, names -> false);
		assertTrue("old jar must be kept on decline", Files.exists(old));
		assertTrue("running jar must be kept", Files.exists(running));
		assertTrue("cleanup gate should be set even when declined (no re-prompt)",
				cleanupAskedPref(shortName, new ApplicationVersion(26, 1, 2)));
	}

	@Test
	public final void testCleanupOldVersionsDoesNotPromptAgainAfterAsked() throws IOException {
		Path dir = Files.createTempDirectory("updater-cleanup-gated-");
		Path running = dir.resolve("HazardCurveGUI-26.1.2.jar");
		Files.createFile(running);
		Path old = dir.resolve("HazardCurveGUI-26.1.1.jar");
		Files.createFile(old);
		ApplicationVersion runningVersion = new ApplicationVersion(26, 1, 2);
		// Pre-set the gate so a prior startup already asked.
		java.util.prefs.Preferences.userNodeForPackage(ApplicationUpdater.class)
				.putBoolean("cleanupAsked_" + shortName + "_" + runningVersion, true);
		boolean[] prompted = { false };
		ApplicationUpdater u = new ApplicationUpdater(client, prompt,
				runningVersion, (a, p) -> null, p -> {});
		u.cleanupOldVersions(shortName, "HazardCurveGUI", dir, names -> {
			prompted[0] = true;
			return true;
		});
		assertFalse("must not prompt again once the gate is set", prompted[0]);
		assertTrue("old jar must be left in place when not prompted", Files.exists(old));
	}

	private static boolean cleanupAskedPref(String shortName, ApplicationVersion version) {
		return java.util.prefs.Preferences.userNodeForPackage(ApplicationUpdater.class)
				.getBoolean("cleanupAsked_" + shortName + "_" + version, false);
	}
}