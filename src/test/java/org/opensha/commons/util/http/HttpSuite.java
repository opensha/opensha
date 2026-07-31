package org.opensha.commons.util.http;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Suite aggregating the offline unit tests for the GitHub HTTP/JSON layer.
 * The live-API {@code GitHubClientOperationalTest} is intentionally excluded;
 * it runs only via {@code ./gradlew testOperational}.
 *
 * @author Akash Bhatthal
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
	GitHubClientTest.class
})

public class HttpSuite {
}