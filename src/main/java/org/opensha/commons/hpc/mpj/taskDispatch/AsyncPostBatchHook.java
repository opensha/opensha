package org.opensha.commons.hpc.mpj.taskDispatch;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.opensha.commons.util.ExceptionUtils;

import com.google.common.base.Preconditions;

public abstract class AsyncPostBatchHook implements PostBatchHook {
	
	private ExecutorService exec;
	private List<Future<?>> futures;
	
	public AsyncPostBatchHook(int threads) {
		Preconditions.checkState(threads > 0);
		
		if (threads == 1)
			exec = Executors.newSingleThreadExecutor();
		else
			exec = Executors.newFixedThreadPool(threads);
	}
	
	public void batchProcessed(int[] batch, int processIndex) {
		ProcessHookRunnable run = new ProcessHookRunnable(batch, processIndex);
		futures.add(exec.submit(run));
	}
	
	private class ProcessHookRunnable implements Runnable {
		private int[] batch;
		private int processIndex;
		
		public ProcessHookRunnable(int[] batch, int processIndex) {
			this.batch = batch;
			this.processIndex = processIndex;
		}

		@Override
		public void run() {
			batchProcessedAsync(batch, processIndex);
		}
	}
	
	public void shutdown() {
		exec.shutdown();
		for (Future<?> f : futures) {
			try {
				f.get();
			} catch (Exception e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
	}
	
	protected abstract void batchProcessedAsync(int[] batch, int processIndex);

}
