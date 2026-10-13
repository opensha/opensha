package org.opensha.sha.earthquake.faultSysSolution.mpj;

import static org.junit.Assert.assertEquals;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.Test;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;

public class InversionConfigTest {

	@Test
	public void testSystemProperties() throws Exception {
		Options options = new Options();
		InversionConfig.addOptions(options);
		CommandLine commandLine = new DefaultParser().parse(options, new String[] {
				"--inversion-system-property", "test.property=first",
				"--inversion-system-property", "test.property=override",
				"--inversion-system-property", "empty.property="
		});
		InversionConfig config = InversionConfig.builder()
				.factoryClass(NSHM23_InvConfigFactory.class)
				.wallTimeMinutes(60)
				.systemProperty("builder.property", "value")
				.forCMD(commandLine)
				.build();
		assertEquals("override", config.systemProperties().get("test.property"));
		assertEquals("", config.systemProperties().get("empty.property"));
		assertEquals("value", config.systemProperties().get("builder.property"));
	}
}
