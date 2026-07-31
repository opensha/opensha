package org.opensha.commons.util;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Global, per-user OpenSHA configuration stored as JSON at
 * {@code ~/.opensha/config.json}. This is a single, cross-application file: it
 * is read once at launch by the {@link org.opensha.commons.util.updater.ApplicationUpdater}
 * (via {@link #isDisableUpdatePrompts}) and honoured by every OpenSHA
 * application, so a setting changed here takes effect for all of them.
 *
 * <p>The file is created <em>lazily</em>: a missing file is equivalent to the
 * defaults and is never written on a normal launch. It is created/updated only
 * when the user changes a setting (currently via the update-preferences dialog
 * reachable from an application's Help menu; see
 * {@code org.opensha.commons.gui.UpdatePreferencesDialog}).</p>
 *
 * <p>Parsing is tolerant: a missing, empty, or corrupt file yields a default
 * instance (with a logged warning) rather than throwing, so a bad hand-edit can
 * never break application startup. The directory {@code ~/.opensha/} follows the
 * same convention used by the OpenSHA data downloaders
 * ({@code System.getProperty("user.home")} + {@code ".opensha"}).</p>
 *
 * <p>JSON (de)serialization uses Gson, the codebase's standard JSON library, and
 * treats each non-static field on this class as a config key by name.</p>
 *
 * @author Akash Bhatthal
 */
public class OpenSHAConfig {

	private static final Logger log = LoggerFactory.getLogger(OpenSHAConfig.class);

	/** Directory name under the user home that holds OpenSHA user data. */
	public static final String DIR_NAME = ".opensha";
	/** File name of the global user config within {@link #configDir()}. */
	public static final String FILE_NAME = "config.json";

	/**
	 * When {@code true}, every OpenSHA application skips its launch-time update
	 * check entirely &mdash; no background thread, no network call, and no update
	 * dialog. Defaults to {@code false} (prompts enabled).
	 */
	private boolean disableUpdatePrompts = false;

	/**
	 * Optional path used in place of {@link #configFile()} by tests. Gson ignores
	 * static fields, so this is never serialized.
	 */
	static Path configFileOverride = null;

	/**
	 * The {@code ~/.opensha} directory for the current user. Callers that need the
	 * directory to exist should create it lazily with
	 * {@code Files.createDirectories(configDir())}.
	 *
	 * @return the config directory path (not guaranteed to exist)
	 */
	public static Path configDir() {
		return Paths.get(System.getProperty("user.home"), DIR_NAME);
	}

	/**
	 * The {@code ~/.opensha/config.json} file path, or a test-supplied override.
	 *
	 * @return the config file path (not guaranteed to exist)
	 */
	public static Path configFile() {
		return configFileOverride != null ? configFileOverride : configDir().resolve(FILE_NAME);
	}

	/**
	 * Load the global user config. Returns a default instance when the file is
	 * missing, empty, or not valid JSON, so this method never throws and a bad
	 * hand-edit cannot break application startup.
	 *
	 * @return the loaded config, or defaults if unavailable
	 */
	public static OpenSHAConfig load() {
		Path file = configFile();
		if (!Files.isRegularFile(file)) {
			return new OpenSHAConfig();
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			OpenSHAConfig config = new Gson().fromJson(reader, OpenSHAConfig.class);
			return config != null ? config : new OpenSHAConfig();
		} catch (IOException | JsonSyntaxException e) {
			log.warn("Could not read OpenSHA config from {}; using defaults. {}", file, e.toString());
			return new OpenSHAConfig();
		}
	}

	/**
	 * Whether the user has globally disabled update prompts.
	 *
	 * @return {@code true} if update prompts/checks are disabled for all apps
	 */
	public boolean isDisableUpdatePrompts() {
		return disableUpdatePrompts;
	}

	/**
	 * Set whether update prompts are globally disabled.
	 *
	 * @param disableUpdatePrompts {@code true} to disable update prompts globally
	 */
	public void setDisableUpdatePrompts(boolean disableUpdatePrompts) {
		this.disableUpdatePrompts = disableUpdatePrompts;
	}

	/**
	 * Persist this config to {@link #configFile()}. Creates {@code ~/.opensha/}
	 * lazily if needed and writes atomically via a sibling temp file so a partial
	 * write can never corrupt the config. Best-effort: failures are logged, never
	 * thrown.
	 */
	public void save() {
		Path file = configFile();
		try {
			Files.createDirectories(file.getParent());
		} catch (IOException e) {
			log.warn("Could not create OpenSHA config directory {}; config not saved. {}", file.getParent(), e.toString());
			return;
		}
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			gson.toJson(this, writer);
		} catch (IOException e) {
			log.warn("Could not write OpenSHA config to {}; config not saved. {}", tmp, e.toString());
			return;
		}
		try {
			Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException atomicFailed) {
			// ATOMIC_MOVE is unsupported on some filesystems; fall back to a
			// plain replace.
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				log.warn("Could not move OpenSHA config into place at {}; config not saved. {}", file, e.toString());
				try {
					Files.deleteIfExists(tmp);
				} catch (IOException ignore) {
				}
			}
		}
	}

	/**
	 * Point {@link #configFile()} at an explicit path for tests. Pass a path to a
	 * non-existent file to simulate the default (lazy) state. Production code
	 * should never call this.
	 *
	 * @param configFileOverride the config file path to use, or {@code null} to
	 *                            restore the default {@code ~/.opensha/config.json}
	 */
	public static void setConfigFileForTesting(Path configFileOverride) {
		OpenSHAConfig.configFileOverride = configFileOverride;
	}

	/**
	 * Restore the default {@code ~/.opensha/config.json} after a test. Production
	 * code should never call this.
	 */
	public static void clearConfigFileOverride() {
		OpenSHAConfig.configFileOverride = null;
	}

	/**
	 * Manual utility: build the global config file at {@link #configFile()}
	 * ({@code ~/.opensha/config.json}) with default values by invoking
	 * {@link #save()}, creating {@code ~/.opensha/} lazily if needed. This lets a
	 * user materialize the file ahead of time so they can hand-edit it (e.g. to
	 * set {@code disableUpdatePrompts}) without having to launch an OpenSHA
	 * application first. Prints the path written and exits.
	 *
	 * <p>Run with {@code java -cp ... org.opensha.commons.util.OpenSHAConfig}.</p>
	 *
	 * @param args ignored
	 */
	public static void main(String[] args) {
		OpenSHAConfig config = new OpenSHAConfig();
		config.save();
		Path file = configFile();
		System.out.println("Wrote OpenSHA config: " + file);
		System.out.println("  disableUpdatePrompts = " + config.isDisableUpdatePrompts());
	}
}