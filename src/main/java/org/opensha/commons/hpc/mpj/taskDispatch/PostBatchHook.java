package org.opensha.commons.hpc.mpj.taskDispatch;

public interface PostBatchHook {
	
	public void batchProcessed(int[] batch, int processIndex);

}
