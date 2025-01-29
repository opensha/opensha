package org.opensha.commons.data.siteData;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Test;

import org.apache.commons.io.FileUtils;

import scratch.UCERF3.erf.mean.MeanUCERF3;

/**
 * Tests to verify all GetFile endpoints are operational
 */
public class TestGetFileStatus_Operational {
	private static final String CLIENT_ROOT = "src/test/resources/org/opensha/commons/data/siteData/getfile";
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
	 * Verify the MeanUCERF3.checkDownload GetFile wrapper is operational.
	 * Should be able to download UCERF3 metadata and download a small file
	 * within 20 seconds.
	 */
	@Test(timeout = 20000)
	public void testMeanUCERF3() {
		if (D) System.out.println("TestGetFileStatus_Operational.testMeanUCERF3()");
		File solFile = null;
		try {
			solFile = MeanUCERF3.checkDownload(
					new File(CLIENT_ROOT, "mean_ucerf3_sol.zip"),
					/*showProgress=*/false).get();
		} catch (InterruptedException | ExecutionException e) {
			if (D) e.printStackTrace();
		}
		assertTrue(solFile.exists());
		assertTrue(solFile != null);
		assertTrue(solFile.isFile());
		assertTrue(solFile.length() > 0);
	}
}
