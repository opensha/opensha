package org.opensha.commons.data.getfile;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import org.apache.commons.io.FileUtils;

import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_Downloader;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.NSHM25_Downloader;
import scratch.UCERF3.utils.UCERF3_Downloader;

/**
 * Tests to verify all GetFile endpoints are operational
 */
public class TestGetFileStatus_Operational {
	private static final String CLIENT_ROOT = "src/test/resources/org/opensha/commons/data/getfile";
	private static final boolean D = true;
	
	/**
	 * Ensure no cache and client root exists prior to each test.
	 */
	@Before
	public void setUp() {
		if (D) System.out.println("TestGetFileStatus_Operational.setUp()");
		File client = new File(CLIENT_ROOT);
		try {
			FileUtils.forceMkdir(client);
			FileUtils.cleanDirectory(client);
		} catch (IOException e) {
			if (D) e.printStackTrace();
		}
	}
	
	/**
	 * Should be able to download UCERF3 metadata.
	 */
	@Test(timeout = 20000)
	public void testUCERF3() {
		if (D) System.out.println("TestGetFileStatus_Operational.testUCERF3()");
	    new UCERF3_Downloader(/*showProgress=*/false);
		File serverMeta = new File(
				System.getProperty("user.home"), ".opensha/ucerf3/ucerf3_client.json");
		assertTrue(serverMeta.exists());
	}

	/**
	 * Should be able to download NSHM23 metadata.
	 */
	@Test(timeout = 20000)
	public void testNSHM23() {
		if (D) System.out.println("TestGetFileStatus_Operational.testNSHM23()");
		new NSHM23_Downloader(/*showProgress=*/false);
		File serverMeta = new File(
				System.getProperty("user.home"), ".opensha/nshm23/nshm23_client.json");
		assertTrue(serverMeta.exists());
	}

    /**
     * Should be able to download NSHM25 metadata.
     */
    @Test(timeout = 20000)
    public void testNSHM25() {
        if (D) System.out.println("TestGetFileStatus_Operational.testNSHM25()");
        new NSHM25_Downloader(/*showProgress=*/false);
        File serverMeta = new File(
                System.getProperty("user.home"), ".opensha/nshm25/nshm25_client.json");
        assertTrue(serverMeta.exists());
    }
}
