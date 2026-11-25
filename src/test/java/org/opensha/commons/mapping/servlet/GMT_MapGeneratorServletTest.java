package org.opensha.commons.mapping.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GMT_MapGeneratorServletTest {

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void preservesValidHintAndSkipsExistingName() throws Exception {
		File parent = new File(temp.getRoot(), "gmtData");
		assertEquals(new File(parent, "map-1.0_run"),
				GMT_MapGeneratorServlet.createPlotDirectory(parent, "map-1.0_run"));
		assertEquals(new File(parent, "map-1.0_run_1"),
				GMT_MapGeneratorServlet.createPlotDirectory(parent, "map-1.0_run"));
	}

	@Test
	public void usesGeneratedNamesForMissingOrInvalidHints() throws Exception {
		File parent = temp.newFolder("gmtData");
		for (String hint : new String[] { null, "", "two words", "nested/map", ".hidden", "a".repeat(81) }) {
			File directory = GMT_MapGeneratorServlet.createPlotDirectory(parent, hint);
			assertEquals(parent, directory.getParentFile());
			assertTrue(directory.isDirectory());
			assertTrue(directory.getName(), directory.getName().matches("[0-9]+(_[0-9]+)?"));
		}
	}

	@Test
	public void concurrentJobsGetDistinctDirectories() throws Exception {
		File parent = new File(temp.getRoot(), "gmtData");
		int jobCount = 8;
		ExecutorService workers = Executors.newFixedThreadPool(jobCount);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<File>> jobs = new ArrayList<>();
			for (int i = 0; i < jobCount; i++) {
				jobs.add(workers.submit(() -> {
					start.await();
					return GMT_MapGeneratorServlet.createPlotDirectory(parent, "shared");
				}));
			}
			start.countDown();

			Set<File> directories = new HashSet<>();
			for (Future<File> job : jobs) {
				File directory = job.get(10, TimeUnit.SECONDS);
				assertEquals(parent, directory.getParentFile());
				assertTrue(directory.isDirectory());
				assertTrue("Directory reused: " + directory, directories.add(directory));
			}
		} finally {
			workers.shutdownNow();
		}
	}
}
