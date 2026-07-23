package org.opensha.commons.util.updater;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;

import org.opensha.commons.util.ApplicationVersion;
import org.opensha.commons.util.http.GitHubAsset;
import org.opensha.commons.util.http.GitHubRelease;
import org.opensha.commons.util.http.GitHubClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates the automatic update workflow for an OpenSHA application:
 *
 * <ol>
 *   <li>Determine the latest <em>stable</em> release from GitHub (via
 *       {@link GitHubClient}).</li>
 *   <li>Compare it to the running build version using
 *       {@link ApplicationVersion#isGreaterThan}.</li>
 *   <li>If newer, prompt the user (via {@link UpdatePrompt}) showing the
 *       release notes.</li>
 *   <li>If the user accepts, download the matching per-app JAR asset, verify
 *       its SHA-256 against the GitHub-provided {@code digest}, and atomically
 *       move it into place beside the running JAR under its honest versioned
 *       name (e.g. {@code HazardCurveGUI-26.1.2.jar}), then launch that new JAR
 *       in a detached process and exit this JVM.</li>
 * </ol>
 *
 * <p>Design notes:</p>
 * <ul>
 *   <li>Version comparison reuses {@link ApplicationVersion}'s
 *       {@link Comparable} implementation rather than a bespoke comparator.</li>
 *   <li>The new JAR is downloaded to its release-asset name (the version it
 *       actually contains), <em>not</em> over the running JAR's path. The
 *       running JAR is left untouched at install time so the old version
 *       remains available as a fallback if the new one cannot start (e.g. the
 *       wrong Java is installed). On the new version's first startup the user
 *       is asked once whether to delete the older JAR(s); see
 *       {@link #cleanupOldVersions}.</li>
 *   <li>The new JAR is a fresh, unlocked file, so {@link #launchJar} launches it
 *       with a detached {@link ProcessBuilder} and then calls
 *       {@code System.exit(0)}. The only file that was ever locked (the running
 *       JAR) is never mutated here; deleting the old JAR is deferred to the new
 *       app's startup, by which
 *       point this JVM has exited and the file is no longer locked.</li>
 *   <li>The asset download and launch steps are injectable via
 *       {@link AssetDownloader} / {@link AssetLauncher} so the full flow is
 *       unit-testable without a network or {@code System.exit}.</li>
 * </ul>
 *
 * @author Akash Bhatthal
 */
public class ApplicationUpdater {

	private static final Logger log = LoggerFactory.getLogger(ApplicationUpdater.class);

	/** Number of whole-download retries on a digest mismatch or IO error. */
	private static final int MAX_DOWNLOAD_ATTEMPTS = 3;

	/** Days to suppress re-prompting after the user chooses "Remind me later". */
	private static final long REMIND_LATER_DELAY_DAYS = 7;

	private static final String PREF_REMIND_AFTER = "remindAfter_";
	private static final String PREF_SKIP_VERSION = "skipVersion_";
	private static final String PREF_CLEANUP_ASKED = "cleanupAsked_";

	private static final Preferences prefs = Preferences.userNodeForPackage(ApplicationUpdater.class);

	private final GitHubClient client;
	private final UpdatePrompt prompt;
	private final ApplicationVersion runningVersion;
	private final AssetDownloader downloader;
	private final AssetLauncher launcher;

	/**
	 * Construct an updater that loads the running version from
	 * {@link ApplicationVersion#loadBuildVersion()} and uses the real network
	 * downloader and real launch/exit behavior.
	 */
	public ApplicationUpdater(GitHubClient client, UpdatePrompt prompt) {
		this.client = client;
		this.prompt = prompt;
		this.runningVersion = loadRunningVersion();
		this.downloader = ApplicationUpdater::downloadAssetToTemp;
		this.launcher = this::launchJar;
	}

	/**
	 * Construct an updater with explicit dependencies (primarily for tests).
	 *
	 * @param client         GitHub client (may be a mock in tests)
	 * @param prompt         user prompt (may be a fake/mock)
	 * @param runningVersion the currently running version
	 * @param downloader     asset-download strategy (inject a fake to avoid
	 *                       network in tests)
	 * @param launcher       launch/exit strategy (inject a no-op to avoid
	 *                       {@code System.exit} in tests)
	 */
	public ApplicationUpdater(GitHubClient client, UpdatePrompt prompt,
			ApplicationVersion runningVersion, AssetDownloader downloader, AssetLauncher launcher) {
		this.client = client;
		this.prompt = prompt;
		this.runningVersion = runningVersion;
		this.downloader = downloader;
		this.launcher = launcher;
	}

	/**
	 * Parse a release tag into a stable {@link ApplicationVersion}, or return
	 * {@code null} if the tag does not denote a stable release.
	 *
	 * <p>Rules: an optional leading {@code v}/{@code V} is stripped; any tag
	 * containing {@code beta} or {@code alpha} is rejected; the numeric portion
	 * before a {@code -} suffix must match {@code M.m} or {@code M.m.b} with no
	 * other characters. The result is parsed via
	 * {@link ApplicationVersion#fromString}.</p>
	 */
	static ApplicationVersion parseStableVersion(String tag) {
		if (tag == null)
			return null;
		String s = tag.strip();
		if (s.toLowerCase().contains("beta") || s.toLowerCase().contains("alpha"))
			return null;
		if (s.startsWith("v") || s.startsWith("V"))
			s = s.substring(1);
		int dash = s.indexOf('-');
		if (dash >= 0)
			s = s.substring(0, dash);
		if (s.isEmpty())
			return null;
		// reject any non-digit, non-period characters
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (!(Character.isDigit(c) || c == '.'))
				return null;
		}
		try {
			return ApplicationVersion.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** @return true if {@code tag} denotes a stable release. */
	static boolean isStableTag(String tag) {
		return parseStableVersion(tag) != null;
	}

	/**
	 * Return the latest stable release. If the latest release is itself stable,
	 * it is returned directly (a single cheap API call). Otherwise the most
	 * recent page of releases is fetched and the first stable one is returned.
	 * Returns {@code null} if no stable release is available on the first page
	 * or if the GitHub API could not be reached (a network failure is logged
	 * and treated as "no update available", so the host application is never
	 * disturbed).
	 *
	 * <p>It is more efficient to retrieve just the latest release, so we only
	 * fall back to retrieving the last page of releases when the latest version
	 * is not a valid stable release (e.g. a beta). GitHub returns up to 100
	 * releases per page (we request 30); we only consider this first page,
	 * since in practice the most recent stable release will always appear
	 * within it.</p>
	 */
	public GitHubRelease getLatestStableRelease() {
		GitHubRelease latest;
		try {
			latest = client.getLatestRelease();
		} catch (IOException e) {
			log.warn("Could not fetch latest release from GitHub", e);
			return null;
		}
		if (latest != null && isStableTag(latest.getTagName()))
			return latest;
		try {
			List<GitHubRelease> recent = client.getRecentReleases();
			for (GitHubRelease r : recent) {
				if (isStableTag(r.getTagName()))
					return r;
			}
		} catch (IOException e) {
			log.warn("Could not fetch recent releases from GitHub", e);
		}
		return null;
	}

	/**
	 * @return the latest stable version, or {@code null} if no stable release
	 *         is available.
	 */
	public ApplicationVersion getLatestStableVersion() {
		GitHubRelease r = getLatestStableRelease();
		return r == null ? null : parseStableVersion(r.getTagName());
	}

	/**
	 * @return release notes for the latest stable release, or {@code null}.
	 */
	public String getLatestStableReleaseNotes() {
		GitHubRelease r = getLatestStableRelease();
		return r == null ? null : r.getBody();
	}

	/**
	 * @return true if a stable remote release newer than the running version
	 *         is available. Returns false if the running version is unknown
	 *         (e.g. could not be loaded) or if no remote stable release exists.
	 */
	public boolean isOutdated() {
		if (runningVersion == null || runningVersion.getMajor() < 0)
			return false;
		ApplicationVersion latest = getLatestStableVersion();
		if (latest == null)
			return false;
		return latest.isGreaterThan(runningVersion);
	}

	/**
	 * Select the asset on the given release whose name starts with
	 * {@code assetPrefix + "-"}. Release assets are named
	 * {@code <prefix>-<version>.jar}, e.g. {@code HazardCurveGUI-26.1.1.jar}.
	 *
	 * @return the matching asset, or {@code null} if none.
	 */
	static GitHubAsset selectAsset(GitHubRelease release, String assetPrefix) {
		if (release == null || assetPrefix == null)
			return null;
		String prefix = assetPrefix + "-";
		for (GitHubAsset a : release.getAssets())
			if (a.getName() != null && a.getName().startsWith(prefix))
				return a;
		return null;
	}

	/**
	 * Mark a version as skipped so the updater will not prompt for it again
	 * until a newer version is released. Passing {@code null} clears any
	 * previously skipped version for this application.
	 */
	public void setSkipVersion(String appShortName, ApplicationVersion version) {
		if (version == null) {
			prefs.remove(PREF_SKIP_VERSION + appShortName);
		} else {
			prefs.put(PREF_SKIP_VERSION + appShortName, version.toString());
		}
		flushPrefs();
	}

	/**
	 * Entry point: run the update check on a background daemon thread so it
	 * never blocks application startup.
	 *
	 * @param appName       display name (shown in the prompt)
	 * @param appShortName  short name used to key per-app preferences
	 * @param assetPrefix   release-asset prefix for this app (e.g.
	 *                      {@code "HazardCurveGUI"})
	 */
	public void checkForUpdates(String appName, String appShortName, String assetPrefix) {
		if (GraphicsEnvironment.isHeadless()) {
			log.debug("Skipping update check: headless environment");
			return;
		}
		Path runningJar = getRunningJarPath();
		if (runningJar == null) {
			log.debug("Skipping update check: not running from a JAR (e.g. IDE/dev build)");
			return;
		}
		Path jarDir = runningJar.getParent();
		Thread t = new Thread(() -> {
			cleanupOldVersions(appShortName, assetPrefix, jarDir,
					names -> confirmDeleteOldVersions(appName, names));
			runUpdateCheck(appName, appShortName, assetPrefix);
		}, "opensha-update-checker");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Convenience entry point that wires up the default {@link GitHubClient}
	 * and a {@link SwingUpdatePrompt} and launches the update check on a
	 * background thread. This is the one-liner an application's {@code main}
	 * method should call after its disclaimer dialog and GUI launch:
	 *
	 * <pre>
	 * ApplicationUpdater.checkForUpdates(APP_NAME, APP_SHORT_NAME, "HazardCurveGUI");
	 * </pre>
	 *
	 * @param appName       display name (shown in the prompt)
	 * @param appShortName  short name used to key per-app preferences
	 * @param assetPrefix   release-asset prefix for this app
	 */
	public static void checkForUpdatesDefault(String appName, String appShortName, String assetPrefix) {
		GitHubClient client = new GitHubClient();
		new ApplicationUpdater(client, new SwingUpdatePrompt())
				.checkForUpdates(appName, appShortName, assetPrefix);
	}

	/**
	 * Synchronous core of the update check. Package-visible so tests can drive
	 * it on the test thread with mocked dependencies.
	 */
	void runUpdateCheck(String appName, String appShortName, String assetPrefix) {
		try {
			if (runningVersion == null || runningVersion.getMajor() < 0) {
				log.debug("Skipping update check: running version unknown");
				return;
			}
			ApplicationVersion latest = getLatestStableVersion();
			if (latest == null) {
				log.debug("No stable remote release available");
				return;
			}
			if (!latest.isGreaterThan(runningVersion)) {
				log.debug("Application is up to date (running " + runningVersion + ", latest " + latest + ")");
				return;
			}
			// If the latest version's JAR is already installed beside the running
			// JAR, do not re-download/overwrite it; the user can launch it directly.
			Path jarDir = getRunningJarPath() == null ? null : getRunningJarPath().getParent();
			if (findInstalledJar(assetPrefix, latest, jarDir) != null) {
				log.info("Latest version " + latest + " is already installed beside the running JAR "
						+ "(" + assetPrefix + "-" + latest + ".jar); not re-downloading. "
						+ "Launch that JAR to use the new version.");
				return;
			}
			if (isSkipped(appShortName, latest)) {
				log.debug("Latest version " + latest + " is skipped by user preference");
				return;
			}
			if (isReminderActive(appShortName)) {
				log.debug("Update reminder deferred by user preference");
				return;
			}

			String notes = getLatestStableReleaseNotes();
			UpdatePrompt.Choice choice = prompt.prompt(appName, latest, notes);
			if (choice == UpdatePrompt.Choice.REMIND_LATER) {
				setReminderActive(appShortName);
				return;
			}
			if (choice == UpdatePrompt.Choice.SKIP_THIS_VERSION) {
				setSkipVersion(appShortName, latest);
				log.info("User skipped version " + latest + "; will not prompt again until a newer release");
				return;
			}

			// UPDATE_NOW
			GitHubRelease release = getLatestStableRelease();
			GitHubAsset asset = selectAsset(release, assetPrefix);
			if (asset == null) {
				log.error("No release asset matched prefix '" + assetPrefix + "' for " + appName);
				prompt.showMessage("No downloadable asset matched this application. "
						+ "Please update manually.");
				prompt.close();
				return;
			}
			Path downloaded;
			try {
				downloaded = downloader.download(asset, prompt);
			} catch (IOException e) {
				log.error("Download failed for " + asset.getName(), e);
				prompt.showMessage("Download failed: " + e.getMessage()
						+ "\nPlease try again later.");
				prompt.close();
				return;
			}
			prompt.showMessage("Download finished. Launching " + appName + " " + latest + "…");
			// Brief pause so the user can read the status before the JVM exits.
			sleepQuietly(2000L);
			launcher.launch(downloaded);
			// launch exits the JVM; if it somehow returns, close the prompt
			prompt.close();
		} catch (Throwable t) {
			// The updater must never crash the host application.
			log.warn("Update check failed", t);
			try {
				prompt.close();
			} catch (Throwable ignore) {}
		}
	}

	private boolean isSkipped(String appShortName, ApplicationVersion latest) {
		String skip = prefs.get(PREF_SKIP_VERSION + appShortName, null);
		return latest != null && latest.toString().equals(skip);
	}

	private boolean isReminderActive(String appShortName) {
		long remindAfter = prefs.getLong(PREF_REMIND_AFTER + appShortName, 0L);
		return remindAfter > 0 && System.currentTimeMillis() < remindAfter;
	}

	private void setReminderActive(String appShortName) {
		prefs.putLong(PREF_REMIND_AFTER + appShortName,
				System.currentTimeMillis() + REMIND_LATER_DELAY_DAYS * 24L * 60L * 60L * 1000L);
		flushPrefs();
	}

	private static void flushPrefs() {
		try {
			prefs.flush();
		} catch (Throwable t) {
			log.warn("Could not flush update preferences", t);
		}
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Launch the downloaded JAR in a detached process and then exit this JVM. The
	 * downloaded JAR has already been moved to its honest versioned name (e.g.
	 * {@code HazardCurveGUI-26.1.2.jar}) beside the running JAR, so it is a fresh,
	 * unlocked file and can be launched directly. The old JAR is intentionally
	 * left in place; it is offered for deletion on the new version's first
	 * startup ({@link #cleanupOldVersions}).
	 *
	 * <p>This method exits the JVM; if it cannot proceed (e.g. not running from a
	 * JAR) it returns without exiting.</p>
	 */
	public void launchJar(Path downloadedJar) {
		try {
			Path runningJar = getRunningJarPath();
			if (runningJar == null) {
				log.error("Cannot launch update: not running from a JAR (code source is not a file). "
						+ "Update is staged at " + downloadedJar);
				prompt.showMessage("Application is not running from a JAR; cannot auto-launch. "
						+ "Please launch the new JAR manually: " + downloadedJar);
				return;
			}
			String java = resolveJavaExecutable();
			ProcessBuilder pb = new ProcessBuilder(java, "-jar", downloadedJar.toString());
			pb.directory(runningJar.getParent().toFile());
			pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			pb.redirectError(ProcessBuilder.Redirect.DISCARD);
			Process p = pb.start();
			p.getInputStream().close();
			p.getErrorStream().close();
			log.info("Launched updated " + downloadedJar + "; exiting this JVM");
			System.exit(0);
		} catch (Throwable t) {
			log.error("Launch failed", t);
			try {
				prompt.showMessage("Launch failed: " + t.getMessage()
						+ "\nThe new JAR is staged beside the old one; launch it manually.");
			} catch (Throwable ignore) {}
		}
	}

	/**
	 * On the new version's first startup, offer to delete older versioned JARs
	 * left beside the running JAR by a previous update. The old JAR is kept as a
	 * fallback until the user confirms deletion, so that if the new version
	 * cannot start (e.g. the wrong Java is installed) the user is not left
	 * without a working application. The prompt is shown at most once per
	 * running version (tracked via a preference), never on subsequent startups.
	 *
	 * <p>Stale {@code .part} download leftovers are deleted silently (they are
	 * always garbage). This method never throws; failures are logged and the
	 * host application is never disturbed.</p>
	 *
	 * @param appShortName  short name used to key the per-app preference
	 * @param assetPrefix   release-asset prefix for this app
	 * @param jarDir        directory holding the running JAR (and old siblings)
	 * @param confirmDelete callback that asks the user whether to delete the
	 *                      named old JAR(s) and returns their choice; the default
	 *                      Swing implementation is
	 *                      {@link #confirmDeleteOldVersions}
	 */
	void cleanupOldVersions(String appShortName, String assetPrefix, Path jarDir,
			Function<List<String>, Boolean> confirmDelete) {
		if (runningVersion == null || runningVersion.getMajor() < 0 || jarDir == null)
			return;
		try {
			deleteStalePartFiles(jarDir, assetPrefix);
			List<Path> oldJars = findOldJars(assetPrefix, runningVersion, jarDir);
			if (oldJars.isEmpty())
				return;
			String gateKey = PREF_CLEANUP_ASKED + appShortName + "_" + runningVersion;
			if (prefs.getBoolean(gateKey, false)) {
				log.debug("Old-version cleanup already prompted for " + runningVersion
						+ "; leaving " + oldJars.size() + " old JAR(s) in place");
				return;
			}
			List<String> names = new ArrayList<>();
			for (Path p : oldJars)
				names.add(p.getFileName().toString());
			boolean delete = false;
			try {
				delete = Boolean.TRUE.equals(confirmDelete.apply(names));
			} catch (Throwable t) {
				log.warn("Old-version cleanup prompt failed", t);
			}
			if (delete)
				deleteOldJars(oldJars);
			prefs.putBoolean(gateKey, true);
			flushPrefs();
		} catch (Throwable t) {
			log.warn("Old-version cleanup failed", t);
		}
	}

	/**
	 * Default Swing implementation of the cleanup confirmation: a YES/NO
	 * dialog listing the old JAR(s). Returns {@code false} in a headless
	 * environment (no dialog can be shown).
	 */
	static boolean confirmDeleteOldVersions(String appName, List<String> oldJarNames) {
		if (GraphicsEnvironment.isHeadless())
			return false;
		StringBuilder msg = new StringBuilder();
		msg.append("An older version of ").append(appName).append(" was found beside the new one:\n");
		for (String n : oldJarNames)
			msg.append("  ").append(n).append('\n');
		msg.append("\nDelete the old version(s)? Keep them if you would like a fallback ");
		msg.append("in case the new version has issues.");
		int choice = JOptionPane.showConfirmDialog(null, msg.toString(),
				appName + " - Clean up old version", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		return choice == JOptionPane.YES_OPTION;
	}

	/**
	 * Scan {@code jarDir} for sibling JARs named {@code <prefix>-<version>.jar}
	 * whose parsed version satisfies {@code versionFilter}. Shared by
	 * {@link #findOldJars} (older than running) and {@link #findInstalledJar}
	 * (equal to a target version). Returns an empty list if {@code assetPrefix}
	 * or {@code jarDir} is null. Package-visible for testing.
	 */
	static List<Path> findVersionedJars(String assetPrefix, Path jarDir,
			Predicate<ApplicationVersion> versionFilter) throws IOException {
		List<Path> matches = new ArrayList<>();
		if (assetPrefix == null || jarDir == null)
			return matches;
		String prefixDash = assetPrefix + "-";
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(jarDir)) {
			for (Path p : ds) {
				String name = p.getFileName().toString();
				if (!name.endsWith(".jar") || !name.startsWith(prefixDash))
					continue;
				String versionPart = name.substring(prefixDash.length(), name.length() - ".jar".length());
				ApplicationVersion v = parseStableVersion(versionPart);
				if (v != null && versionFilter.test(v))
					matches.add(p);
			}
		}
		return matches;
	}

	/**
	 * Find sibling JARs in {@code jarDir} named {@code <prefix>-<version>.jar}
	 * whose version is older than {@code running}. The running JAR (equal
	 * version, or a name that does not match the prefix pattern) is never
	 * included. Package-visible for testing.
	 */
	static List<Path> findOldJars(String assetPrefix, ApplicationVersion running, Path jarDir)
			throws IOException {
		if (running == null)
			return new ArrayList<>();
		return findVersionedJars(assetPrefix, jarDir, v -> v.isLessThan(running));
	}

	/**
	 * Return the sibling JAR in {@code jarDir} named
	 * {@code <prefix>-<version>.jar} whose version equals {@code version}, or
	 * {@code null} if none. Used to detect that a version is already installed
	 * beside the running JAR (e.g. the latest) so the updater need not
	 * re-download it. Package-visible for testing.
	 */
	static Path findInstalledJar(String assetPrefix, ApplicationVersion version, Path jarDir)
			throws IOException {
		if (version == null)
			return null;
		List<Path> matches = findVersionedJars(assetPrefix, jarDir, v -> v.equals(version));
		return matches.isEmpty() ? null : matches.get(0);
	}

	/** Best-effort deletion of the given JARs; failures are logged, not thrown. */
	static void deleteOldJars(List<Path> jars) {
		for (Path p : jars) {
			try {
				Files.deleteIfExists(p);
			} catch (Throwable t) {
				log.warn("Could not delete old JAR " + p, t);
			}
		}
	}

	/** Silently delete stale {@code .<prefix>-*.part} download leftovers. */
	private static void deleteStalePartFiles(Path jarDir, String assetPrefix) {
		if (assetPrefix == null || jarDir == null)
			return;
		String partPrefix = "." + assetPrefix + "-";
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(jarDir)) {
			for (Path p : ds) {
				String name = p.getFileName().toString();
				if (name.startsWith(partPrefix) && name.endsWith(".part")) {
					try {
						Files.deleteIfExists(p);
					} catch (Throwable t) {
						log.debug("Could not delete stale .part file " + p, t);
					}
				}
			}
		} catch (Throwable t) {
			log.debug("Could not scan for stale .part files in " + jarDir, t);
		}
	}

	/**
	 * @return the path of the running JAR, or {@code null} if not running from
	 *         a JAR file (e.g. running from unpacked classes in an IDE).
	 */
	static Path getRunningJarPath() {
		try {
			URL loc = ApplicationUpdater.class.getProtectionDomain().getCodeSource().getLocation();
			if (loc == null)
				return null;
			Path path = Path.of(loc.toURI());
			return Files.isRegularFile(path) ? path : null;
		} catch (Throwable t) {
			log.debug("Could not determine running JAR path", t);
			return null;
		}
	}

	private static String resolveJavaExecutable() {
		return ProcessHandle.current().info().command().orElseGet(() -> {
			String javaHome = System.getProperty("java.home");
			String exe = javaHome + System.getProperty("file.separator") + "bin"
					+ System.getProperty("file.separator") + "java";
			if (System.getProperty("os.name").toLowerCase().contains("win") && !exe.endsWith(".exe"))
				exe = exe + ".exe";
			return exe;
		});
	}

	// ---- real network downloader ----

	private static final HttpClient DOWNLOAD_CLIENT = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(30))
			.build();

	private static final String DOWNLOAD_USER_AGENT = "OpenSHA-Updater/1.0 (https://github.com/opensha/opensha)";

	/**
	 * Default {@link AssetDownloader}: streams the asset to a {@code .part}
	 * temp file beside the running JAR, reports progress, verifies the SHA-256
	 * against the asset's GitHub {@code digest}, and retries the whole
	 * download up to {@link #MAX_DOWNLOAD_ATTEMPTS} times on mismatch or IO
	 * error. On success the verified {@code .part} file is atomically moved to
	 * its honest versioned name (the asset's release name, e.g.
	 * {@code HazardCurveGUI-26.1.2.jar}) beside the running JAR, and that path
	 * is returned &mdash; ready to be launched by {@link #launchJar}.
	 */
	static Path downloadAssetToTemp(GitHubAsset asset, UpdatePrompt prompt) throws IOException {
		Path jarDir = getRunningJarPath() == null ? Path.of(System.getProperty("java.io.tmpdir"))
				: getRunningJarPath().getParent();
		Path temp = jarDir.resolve("." + asset.getName() + ".part");
		Path target = jarDir.resolve(asset.getName());
		String expected = asset.getSha256Hex();
		IOException last = null;
		for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
			Files.deleteIfExists(temp);
			try {
				streamDownload(asset.getBrowserDownloadUrl(), temp, asset.getSize(), prompt);
				String actual = sha256Hex(temp);
				if (expected == null) {
					log.warn("Asset " + asset.getName() + " has no digest; skipping integrity verification");
				} else if (!expected.equalsIgnoreCase(actual)) {
					log.warn("Digest mismatch on attempt " + attempt + " for " + asset.getName()
							+ " (expected " + expected + ", got " + actual + ")");
					last = new IOException("SHA-256 digest mismatch for " + asset.getName());
					continue;
				}
				// Atomically promote the verified .part to its versioned name.
				Files.deleteIfExists(target);
				Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
				return target;
			} catch (IOException e) {
				last = e;
				log.warn("Download attempt " + attempt + " failed for " + asset.getName()
						+ ": " + e.getMessage());
			}
		}
		Files.deleteIfExists(temp);
		throw last != null ? last : new IOException("Download failed for " + asset.getName());
	}

	private static void streamDownload(String url, Path dest, long size, UpdatePrompt prompt)
			throws IOException {
		HttpRequest request;
		try {
			request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.GET()
					.header("User-Agent", DOWNLOAD_USER_AGENT)
					.header("Accept", "application/octet-stream")
					.timeout(Duration.ofMinutes(10))
					.build();
		} catch (IllegalArgumentException e) {
			throw new IOException("Invalid download URL: " + url, e);
		}
		HttpResponse<InputStream> response;
		try {
			response = DOWNLOAD_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Download interrupted: " + url, e);
		}
		int code = response.statusCode();
		if (code < 200 || code >= 300) {
			throw new IOException("Download failed (HTTP " + code + "): " + url);
		}
		try (InputStream in = response.body();
				OutputStream out = Files.newOutputStream(dest, StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			byte[] buf = new byte[64 * 1024];
			long total = 0;
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
				total += n;
				if (size > 0 && prompt != null)
					prompt.setProgress((double) total / (double) size);
			}
			if (prompt != null)
				prompt.setProgress(1.0);
		}
	}

	/** Compute the SHA-256 hex digest of a file. Package-visible for testing. */
	static String sha256Hex(Path file) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] buf = new byte[64 * 1024];
			int n;
			try (InputStream in = Files.newInputStream(file)) {
				while ((n = in.read(buf)) > 0)
					md.update(buf, 0, n);
			}
			return toHex(md.digest());
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException("SHA-256 unavailable", e);
		}
	}

	private static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes)
			sb.append(String.format("%02x", b & 0xff));
		return sb.toString();
	}

	private static ApplicationVersion loadRunningVersion() {
		try {
			return ApplicationVersion.loadBuildVersion();
		} catch (IOException e) {
			log.debug("Could not load build version; disabling update", e);
			return new ApplicationVersion(-1, -1, -1);
		}
	}
}