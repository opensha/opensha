package org.opensha.commons.mapping.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GMT_MapGeneratorServletTest {

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void reportsWhenTheWaitExpires() throws IOException {
		Path directory = temp.newFolder("renderLocks").toPath();
		Duration shortWait = Duration.ofMillis(10);
		try (GMT_RenderSlot first = GMT_RenderSlot.acquire(directory, 1, 1, shortWait)) {
			for (int i = 0; i < 2; i++) {
				IOException error = assertThrows(IOException.class,
						() -> GMT_RenderSlot.acquire(directory, 1, 1, shortWait));
				assertTrue(error.getMessage().contains("Timed out"));
			}
		}
	}

	@Test
	public void reportsWhenTheWaitingQueueIsFull() throws IOException {
		Path directory = temp.newFolder("renderLocks").toPath();
		Duration shortWait = Duration.ofMillis(10);
		try (GMT_RenderSlot active = GMT_RenderSlot.acquire(directory, 1, 1, shortWait);
				FileChannel channel = FileChannel.open(directory.resolve("waiting-0"), StandardOpenOption.CREATE,
						StandardOpenOption.WRITE);
				FileLock waiter = channel.lock()) {
			IOException error = assertThrows(IOException.class,
					() -> GMT_RenderSlot.acquire(directory, 1, 1, shortWait));
			assertTrue(error.getMessage().contains("queue is full"));
		}
	}

	@Test
	public void takesAReleasedRenderSlotWithoutClientRetry() throws Exception {
		Path directory = temp.newFolder("renderLocks").toPath();
		Duration wait = Duration.ofSeconds(5);
		ExecutorService worker = Executors.newSingleThreadExecutor();
		CountDownLatch started = new CountDownLatch(1);
		try {
			Future<Boolean> waiting;
			try (GMT_RenderSlot active = GMT_RenderSlot.acquire(directory, 1, 1, wait)) {
				waiting = worker.submit(() -> {
					started.countDown();
					try (GMT_RenderSlot acquired = GMT_RenderSlot.acquire(directory, 1, 1, wait)) {
						return true;
					}
				});
				assertTrue(started.await(5, TimeUnit.SECONDS));
				assertThrows(TimeoutException.class, () -> waiting.get(100, TimeUnit.MILLISECONDS));
			}
			assertTrue(waiting.get(5, TimeUnit.SECONDS));
		} finally {
			worker.shutdownNow();
		}
	}

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
