package org.opensha.commons.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

}
