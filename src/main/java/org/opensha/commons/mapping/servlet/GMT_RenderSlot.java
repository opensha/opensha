package org.opensha.commons.mapping.servlet;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.util.ServletWorkLimits;

/** A render slot shared by the applications in one Tomcat JVM. */
public final class GMT_RenderSlot implements Closeable {

	private static final long RETRY_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

	private final FileChannel channel;

	private GMT_RenderSlot(FileChannel channel) {
		this.channel = channel;
	}

	public static GMT_RenderSlot acquire() throws IOException {
		Path directory = Path.of(System.getProperty("java.io.tmpdir"), "opensha-render-locks");
		return acquire(directory, ServletWorkLimits.CONCURRENT_RENDERS, ServletWorkLimits.QUEUED_RENDERS,
				Duration.ofSeconds(ServletWorkLimits.RENDER_WAIT_SECONDS));
	}

	static GMT_RenderSlot acquire(Path directory, int renderSlots, int waitingSlots, Duration maxWait)
			throws IOException {
		Files.createDirectories(directory);
		GMT_RenderSlot render = tryAcquire(directory, "slot-", renderSlots);
		if (render != null)
			return render;

		// Waiting requests also hold a servlet thread and their decoded input, so bound them across apps.
		try (GMT_RenderSlot waiting = tryAcquire(directory, "waiting-", waitingSlots)) {
			render = tryAcquire(directory, "slot-", renderSlots);
			if (render == null && waiting == null)
				throw new IOException("Render service is busy and its waiting queue is full. Please retry shortly.");

			long deadline = System.nanoTime() + maxWait.toNanos();
			while (render == null) {
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0)
					throw new IOException("Render service is busy. Timed out waiting for a render slot. Please retry.");
				TimeUnit.NANOSECONDS.sleep(Math.min(remaining, RETRY_NANOS));
				render = tryAcquire(directory, "slot-", renderSlots);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting for a render slot", e);
		} catch (IOException e) {
			// If releasing the waiting slot fails after acquiring a render slot, release both.
			try (GMT_RenderSlot acquired = render) {
				throw e;
			}
		}
		return render;
	}

	private static GMT_RenderSlot tryAcquire(Path directory, String prefix, int slots) throws IOException {
		for (int i = 0; i < slots; i++) {
			Path path = directory.resolve(prefix + i);
			FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
					LinkOption.NOFOLLOW_LINKS);
			boolean acquired = false;
			try {
				acquired = channel.tryLock() != null;
				if (acquired)
					return new GMT_RenderSlot(channel);
			} catch (OverlappingFileLockException e) {
				// File locks also coordinate across the web application class loaders in this JVM.
			} finally {
				if (!acquired)
					channel.close();
			}
		}
		return null;
	}

	@Override
	public void close() throws IOException {
		channel.close();
	}
}
