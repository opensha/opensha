package org.opensha.commons.hpc.mpj.taskDispatch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import mpi.MPI;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class DispatcherThread extends Thread {
	
	private static final boolean D = true;
	
	private int size;
	private int maxPerDispatch;
	private int minPerDispatch;
	private int exactDispatch;
	
	private Deque<Integer> stack;
	
	private PostBatchHook postBatchHook;
	private Map<Integer, int[]> outstandingBatches;
	
	public DispatcherThread(int size, int numTasks, int minPerDispatch, int maxPerDispatch,
			int exactDispatch, boolean shuffle, int startIndex, int endIndex, PostBatchHook postBatchHook) {
		this.size = size;
		this.minPerDispatch = minPerDispatch;
		this.maxPerDispatch = maxPerDispatch;
		this.exactDispatch = exactDispatch;
		this.postBatchHook = postBatchHook;
		Preconditions.checkArgument(minPerDispatch <= maxPerDispatch, "min per dispatch must be <= max");
		Preconditions.checkArgument(minPerDispatch >= 1, "min per dispatch must be >= 1");
		Preconditions.checkArgument(size >= 1, "size must be >= 1");
		Preconditions.checkArgument(numTasks >= 1, "num sites must be >= 1");
		Preconditions.checkState(startIndex >= 0 && startIndex < numTasks,
				"Start index must be >= 0 and less than the number of tasks.");
		Preconditions.checkState(endIndex > 0 && endIndex <= numTasks && endIndex > startIndex,
				"End index must be > 0, greater than startIndex, and less than or equal to the number of tasks.");
		
		debug("starting with "+size+" processes and "+numTasks+" sites." +
				" minPerDispatch="+minPerDispatch+", maxPerDispatch="+maxPerDispatch);
		if (startIndex > 0 || endIndex < numTasks)
			debug("startIndex="+startIndex+", endIndex="+endIndex);
		
		ArrayList<Integer> list = new ArrayList<Integer>();
		for (int i=startIndex; i<endIndex; i++)
			list.add(i);
		
		if (shuffle) {
			debug("shuffling stack");
			Collections.shuffle(list);
		}
		stack = new ArrayDeque<Integer>(list);
		outstandingBatches = Maps.newHashMap();
	}
	
	public void setPostBatchHook(PostBatchHook postBatchHook) {
		this.postBatchHook = postBatchHook;
	}
	
	protected synchronized int[] getNextBatch(int processIndex) {
		if (outstandingBatches.containsKey(processIndex)) {
			int[] prevBatch = outstandingBatches.get(processIndex);
			if (postBatchHook != null) {
				debug("process "+processIndex+" just finished a batch of length "+prevBatch.length+". running post-batch hook");
				outstandingBatches.remove(processIndex);
				postBatchHook.batchProcessed(prevBatch, processIndex);
				debug("done running post-batch hook for process "+processIndex);
			}
		}
		int numLeft = stack.size();
		debug("getting batch with "+numLeft+" left");
		if (numLeft == 0)
			return new int[0];
		
		int numToDispatch;
		if (exactDispatch > 0) {
			numToDispatch = exactDispatch;
		} else {
			double numLeftPer = (double)numLeft / (double)size;
			
			numToDispatch = (int)Math.ceil(numLeftPer);
			if (numToDispatch > maxPerDispatch)
				numToDispatch = maxPerDispatch;
			if (numToDispatch < minPerDispatch)
				numToDispatch = minPerDispatch;
		}
		
		if (numToDispatch > numLeft)
			numToDispatch = numLeft;
		
		int[] batch = new int[numToDispatch];
		for (int i=0; i<numToDispatch; i++)
			batch[i] = stack.pop();
		
		debug("returning batch of size: "+numToDispatch);
		
		outstandingBatches.put(processIndex, batch);
		
		return batch;
	}
	
	@Override
	public void run() {
		debug("now running.");
		try {
			// this keeps track of if each process has finished once all batches have been sent out;
			boolean[] dones = new boolean[size];
			for (int i=0; i<size; i++)
				dones[i] = false;
			
			int[] single_int_buf = new int[1];
			while (true) {
				debug("waiting for READY message.");
				// this receives a READY_FOR_BATCH message from any process. the process # is sent
				MPI.COMM_WORLD.Recv(single_int_buf, 0, 1, MPI.INT, MPI.ANY_SOURCE, MPJTaskCalculator.TAG_READY_FOR_BATCH);
				int proc_id = single_int_buf[0];
				
				debug("received READY from "+proc_id);
				
				int[] batch = getNextBatch(proc_id);
				
				// now we send the the length of the batch
				single_int_buf[0] = batch.length;
				debug("sending batch length ("+batch.length+") to: "+proc_id);
				MPI.COMM_WORLD.Send(single_int_buf, 0, 1, MPI.INT, proc_id, MPJTaskCalculator.TAG_NEW_BATCH_LENGH);
				
				if (batch.length > 0) {
					// now we send the batch to the process.
					debug("sending batch of length "+batch.length+" to: "+proc_id);
					MPI.COMM_WORLD.Send(batch, 0, batch.length, MPI.INT, proc_id, MPJTaskCalculator.TAG_NEW_BATCH);
					dones[proc_id] = false;
				} else {
					// set the index for the process we just communicated with to "done"
//					Preconditions.checkState(!dones[proc_id],
//							"proc id "+proc_id+" has already been marked done!");
					dones[proc_id] = true;
					
					// this means that we're done dispatching batches, and are waiting for everyone to report back
					debug("checking if we're all done...");
					List<Integer> notDones = Lists.newArrayList();
					boolean allDone = true;
					for (int i=1; i<size; i++) {
						if (!dones[i]) {
							allDone = false;
							notDones.add(i);
						}
					}
					if (allDone) {
						// this means that all tasks have been calculated
						debug("DONE!");
						break;
					}
					debug("not yet. waiting on: "+Joiner.on(",").join(notDones));
				}
			}
		} catch (Throwable t) {
			MPJTaskCalculator.abortAndExit(t);
		}
	}
	
	private static void debug(String message) {
		if (!D)
			return;
		
		System.out.println("["+MPJTaskCalculator.df.format(new Date())+" DispatcherThread]: "+message);
	}

}
