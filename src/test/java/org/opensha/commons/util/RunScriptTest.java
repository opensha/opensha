package org.opensha.commons.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RunScriptTest {

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void executesTwoArgumentsWithASpacedScriptPath() throws IOException {
		Path bash = Path.of("/bin/bash");
		assumeTrue(Files.isExecutable(bash));
		Path script = temp.newFile("render map.sh").toPath();
		Files.writeString(script, "printf '%s' rendered > \"$(dirname -- \"$0\")/done.txt\"\n");

		RunScript.runScriptChecked(bash.toString(), script.toString());

		assertEquals("rendered", Files.readString(script.resolveSibling("done.txt")));
	}

	@Test
	public void reportsNonzeroExitStatus() throws IOException {
		Path bash = Path.of("/bin/bash");
		assumeTrue(Files.isExecutable(bash));
		Path script = temp.newFile("render.sh").toPath();
		Files.writeString(script, "exit 7\n");

		IOException error = assertThrows(IOException.class,
				() -> RunScript.runScriptChecked(bash.toString(), script.toString()));
		assertEquals("Script exited with status 7", error.getMessage());
	}

	@Test
	public void reportsLaunchFailure() {
		Path missingExecutable = temp.getRoot().toPath().resolve("missing-executable");
		IOException error = assertThrows(IOException.class,
				() -> RunScript.runScriptChecked(missingExecutable.toString()));
		assertEquals("Could not run the script to completion", error.getMessage());
	}
}
