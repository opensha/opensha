package org.opensha.commons.util;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link OpenSHAConfig}: lazy defaults, parsing, atomic save
 * round-trip, tolerance of corrupt/empty input, and directory creation. All
 * file I/O is redirected to a per-test temp directory via the
 * {@code setConfigFileForTesting} seam so a developer's real
 * {@code ~/.opensha/config.json} can never affect the results.
 *
 * @author Akash Bhatthal
 */
public class OpenSHAConfigTest {

	private Path tmpDir;
	private Path configFile;

	@Before
	public void setUp() throws IOException {
		tmpDir = Files.createTempDirectory("opensha-config-test");
		configFile = tmpDir.resolve("config.json");
		OpenSHAConfig.setConfigFileForTesting(configFile);
	}

	@After
	public void tearDown() throws IOException {
		OpenSHAConfig.clearConfigFileOverride();
	}

	@Test
	public final void testLoadDefaultsWhenFileMissing() {
		assertFalse(Files.isRegularFile(configFile));
		OpenSHAConfig config = OpenSHAConfig.load();
		assertNotNull(config);
		assertFalse(config.isDisableUpdatePrompts());
		// A missing file must not be created on read.
		assertFalse(Files.isRegularFile(configFile));
	}

	@Test
	public final void testLoadParsesDisableUpdatePromptsTrue() throws IOException {
		Files.writeString(configFile, "{\"disableUpdatePrompts\": true}");
		assertTrue(OpenSHAConfig.load().isDisableUpdatePrompts());
	}

	@Test
	public final void testLoadParsesDisableUpdatePromptsFalse() throws IOException {
		Files.writeString(configFile, "{\"disableUpdatePrompts\": false}");
		assertFalse(OpenSHAConfig.load().isDisableUpdatePrompts());
	}

	@Test
	public final void testSaveThenLoadRoundTripTrue() {
		OpenSHAConfig config = new OpenSHAConfig();
		config.setDisableUpdatePrompts(true);
		config.save();
		assertTrue(Files.isRegularFile(configFile));
		assertTrue(OpenSHAConfig.load().isDisableUpdatePrompts());
	}

	@Test
	public final void testSaveThenLoadRoundTripFalse() {
		OpenSHAConfig config = new OpenSHAConfig();
		config.setDisableUpdatePrompts(false);
		config.save();
		assertTrue(Files.isRegularFile(configFile));
		assertFalse(OpenSHAConfig.load().isDisableUpdatePrompts());
	}

	@Test
	public final void testSaveCreatesParentDirectory() {
		// Point at a config path whose parent does not yet exist.
		Path nestedDir = tmpDir.resolve("a/b/c");
		Path nestedConfig = nestedDir.resolve("config.json");
		OpenSHAConfig.setConfigFileForTesting(nestedConfig);

		assertFalse(Files.isDirectory(nestedDir));
		new OpenSHAConfig().save();
		assertTrue(Files.isDirectory(nestedDir));
		assertTrue(Files.isRegularFile(nestedConfig));
	}

	@Test
	public final void testCorruptFileYieldsDefaults() throws IOException {
		Files.writeString(configFile, "{ this is not : valid json ]");
		assertFalse(OpenSHAConfig.load().isDisableUpdatePrompts());
	}

	@Test
	public final void testEmptyFileYieldsDefaults() throws IOException {
		Files.writeString(configFile, "");
		assertFalse(OpenSHAConfig.load().isDisableUpdatePrompts());
	}

	@Test
	public final void testFileMissingDisableKeyYieldsDefaultFalse() throws IOException {
		// An otherwise-valid config object without the key keeps the field default.
		Files.writeString(configFile, "{\"someOtherKey\": 42}");
		assertFalse(OpenSHAConfig.load().isDisableUpdatePrompts());
	}
}