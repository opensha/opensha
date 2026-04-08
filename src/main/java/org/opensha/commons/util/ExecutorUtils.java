package org.opensha.commons.util;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Preconditions;

public class ExecutorUtils {
	
	/**
	 * Creates an {@link ExecutorService} with the given number of threads that will block the current thread until
	 * capacity is available to accept the supplied tasks. This method sets the capacity to the number of threads,
	 * but a larger capacity can be specified via {@link #newBlockingThreadPool(int, int)}.
	 * @param threads
	 * @return
	 */
	public static ExecutorService newBlockingThreadPool(int threads) {
		return newBlockingThreadPool(threads, threads);
	}
	
	/**
	 * Creates an {@link ExecutorService} with the given number of threads that will block the current thread until
	 * capacity is available to accept the supplied tasks. This methodd allows you to set the capacity to be larger
	 * than the number of threads.
	 * @param threads
	 * @param capacity
	 * @return
	 */
	public static ExecutorService newBlockingThreadPool(int threads, int capacity) {
		return newBlockingThreadPool(threads, capacity, null);
	}
	
	public static ExecutorService newBlockingThreadPool(int threads, int capacity, String name) {
		Preconditions.checkState(capacity >= threads, "capacity (%s) must be >= threads (%s)", capacity, threads);
		return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<Runnable>(capacity),
				new NamedThreadFactory(name == null || name.isBlank() ? "blocking" : name),
				new ThreadPoolExecutor.CallerRunsPolicy());
	}
	
	public static ExecutorService newNamedThreadPool(int threads, String name) {
		return Executors.newFixedThreadPool(threads, new NamedThreadFactory(name));
	}
	
	public static class NamedThreadFactory implements ThreadFactory {
		private static final AtomicInteger poolNumber = new AtomicInteger(1);
		private final ThreadGroup group;
		private final AtomicInteger threadNumber = new AtomicInteger(1);
		private final String namePrefix;

		public NamedThreadFactory(String name) {
			group = Thread.currentThread().getThreadGroup();
			Preconditions.checkState(name != null && !name.isBlank(),
					"Name must be specified (use Executors.defaultThreadFactory() if you don't want a name)");
			this.namePrefix = name+"-pool-"+poolNumber.getAndIncrement()+"-thread-";
		}

		public Thread newThread(Runnable r) {
			Thread t = new Thread(group, r,
					namePrefix + threadNumber.getAndIncrement(),
					0);
			t.setDaemon(false);
			if (t.getPriority() != Thread.NORM_PRIORITY)
				t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}
	
	public static ExecutorService newDaemonThreadPool(int threads) {
		return newDaemonThreadPool(threads, null);
	}
	
	public static ExecutorService newDaemonThreadPool(int threads, String nameAdd) {
		return Executors.newFixedThreadPool(threads, new DaemonThreadFactory(nameAdd));
	}
	
	public static class DaemonThreadFactory implements ThreadFactory {
		private static final AtomicInteger poolNumber = new AtomicInteger(1);
		private final ThreadGroup group;
		private final AtomicInteger threadNumber = new AtomicInteger(1);
		private final String namePrefix;

		public DaemonThreadFactory() {
			this(null);
		}

		public DaemonThreadFactory(String nameAdd) {
			group = Thread.currentThread().getThreadGroup();
			if (nameAdd == null || nameAdd.isBlank())
				nameAdd = "";
			else
				nameAdd = nameAdd+"-";
			this.namePrefix = nameAdd+"daemon-pool-"+poolNumber.getAndIncrement()+"-thread-";
		}

		public Thread newThread(Runnable r) {
			Thread t = new Thread(group, r,
					namePrefix + threadNumber.getAndIncrement(),
					0);
			t.setDaemon(true);
			if (t.getPriority() != Thread.NORM_PRIORITY)
				t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}
	
	/**
	 * Creates an {@link ExecutorService} that can only run a single task at once, and silently ignores all submissions
	 * when a task is already running.
	 * @return
	 */
	public static ExecutorService singleTaskRejectingExecutor() {
		return new SingleTaskRejectingExecutor();
	}
	
	private static class SingleTaskRejectingExecutor extends AbstractExecutorService {

		private final ThreadPoolExecutor executor;

		public SingleTaskRejectingExecutor() {
			// Create an executor with a single thread and no queue
			executor = new ThreadPoolExecutor(
					1,                      // Core pool size (single thread)
					1,                      // Max pool size (single thread)
					0L, TimeUnit.MILLISECONDS, // Keep-alive time
					new SynchronousQueue<>(), // No queue: rejects if one task is running
					new ThreadPoolExecutor.AbortPolicy() // Rejects additional tasks
					);
		}

		@Override
		public void execute(Runnable command) {
			try {
				executor.execute(command);
			} catch (RejectedExecutionException e) {
//				System.out.println("Task rejected: " + e.getMessage());
			}
		}

		@Override
		public void shutdown() {
			executor.shutdown();
		}

		@Override
		public List<Runnable> shutdownNow() {
			return executor.shutdownNow();
		}

		@Override
		public boolean isShutdown() {
			return executor.isShutdown();
		}

		@Override
		public boolean isTerminated() {
			return executor.isTerminated();
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
			return executor.awaitTermination(timeout, unit);
		}
	}

}
