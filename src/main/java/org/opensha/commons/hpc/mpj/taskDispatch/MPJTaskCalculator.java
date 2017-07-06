package org.opensha.commons.hpc.mpj.taskDispatch;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;

import mpi.MPI;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.DeadlockDetectionThread;

public abstract class MPJTaskCalculator {
	
	protected static final int TAG_READY_FOR_BATCH = 1;
	protected static final int TAG_NEW_BATCH_LENGH = 2;
	protected static final int TAG_NEW_BATCH = 3;
	
	public static final int MIN_DISPATCH_DEFAULT = 5;
	public static final int MAX_DISPATCH_DEFAULT = 100;
	
	public static final boolean D = true;
	public static final SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");
	
	protected static final boolean SINGLE_NODE_NO_MPJ = Boolean.parseBoolean(System.getProperty("mpj.disable", "false"));
	
	protected int rank;
	protected int size;
	private int minDispatch;
	private int maxDispatch;
	private int exactDispatch;
	private boolean rootDispatchOnly;
	private int numThreads;
	protected boolean shuffle = true;
	
	private int startIndex;
	private int endIndex;
	
	private DispatcherThread dispatcher;
	
	private static DeadlockDetectionThread deadlock;
	
	private String hostname;
	
	protected PostBatchHook postBatchHook;
	
	public MPJTaskCalculator(CommandLine cmd) {
		int numThreads = Runtime.getRuntime().availableProcessors();
		int minDispatch = MIN_DISPATCH_DEFAULT;
		int maxDispatch = MAX_DISPATCH_DEFAULT;
		int exactDispatch = -1;
		int startIndex = -1;
		int endIndex = -1;
		boolean rootDispatchOnly = false;
		
		try {
			hostname = java.net.InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {}
		
		if (cmd.hasOption("threads"))
			numThreads = Integer.parseInt(cmd.getOptionValue("threads"));
		
		if (cmd.hasOption("min-dispatch"))
			minDispatch = Integer.parseInt(cmd.getOptionValue("min-dispatch"));
		
		if (cmd.hasOption("max-dispatch"))
			maxDispatch = Integer.parseInt(cmd.getOptionValue("max-dispatch"));
		
		if (cmd.hasOption("exact-dispatch"))
			exactDispatch = Integer.parseInt(cmd.getOptionValue("exact-dispatch"));
		
		if (cmd.hasOption("root-dispatch-only"))
			rootDispatchOnly = true;
		
		if (cmd.hasOption("deadlock")) {
			deadlock = new DeadlockDetectionThread(5000);
			deadlock.start();
		}
		
		if (cmd.hasOption("start-index"))
			startIndex = Integer.parseInt(cmd.getOptionValue("start-index"));
		
		if (cmd.hasOption("end-index"))
			endIndex = Integer.parseInt(cmd.getOptionValue("end-index"));
		
		init(numThreads, minDispatch, maxDispatch, exactDispatch, rootDispatchOnly, startIndex, endIndex);
	}
	
	public MPJTaskCalculator(int numThreads, int minDispatch, int maxDispatch, boolean rootDispatchOnly) {
		init(numThreads, minDispatch, maxDispatch, -1, rootDispatchOnly);
	}
	
	private void init(int numThreads, int minDispatch, int maxDispatch, int exactDispatch, boolean rootDispatchOnly) {
		init(numThreads, minDispatch, maxDispatch, exactDispatch, rootDispatchOnly, -1, -1);
	}
	
	private void init(int numThreads, int minDispatch, int maxDispatch, int exactDispatch, boolean rootDispatchOnly,
			int startIndex, int endIndex) {
		if (SINGLE_NODE_NO_MPJ) {
			this.rank = 0;
			this.size = 1;
			rootDispatchOnly = true;
		} else {
			this.rank = MPI.COMM_WORLD.Rank();
			this.size = MPI.COMM_WORLD.Size();
		}
		this.numThreads = numThreads;
		this.minDispatch = minDispatch;
		this.maxDispatch = maxDispatch;
		this.exactDispatch = exactDispatch;
		this.rootDispatchOnly = rootDispatchOnly;
		
		this.startIndex = startIndex;
		this.endIndex = endIndex;
	}
	
	protected int getNumThreads() {
		return numThreads;
	}
	
	protected void debug(String message) {
		debug(rank, hostname, message);
	}
	
	protected String getDebugText(String message) {
		return getDebugText(rank, hostname, message);
	}
	
	protected static String getDebugText(int rank, String hostname, String message) {
		if (hostname == null)
			return "["+df.format(new Date())+" Process "+rank+"]: "+message;
		else
			return "["+df.format(new Date())+" ("+hostname+") Process "+rank+"]: "+message;
	}
	
	protected static void debug(int rank, String hostname, String message) {
		if (!D)
			return;
		System.out.flush();
		System.out.println(getDebugText(rank, hostname, message));
		System.out.flush();
	}
	
	protected abstract int getNumTasks();
	
	public void run() throws IOException, InterruptedException {
		if (rank == 0) {
			// launch the dispatcher
			if (startIndex < 0)
				startIndex = 0;
			if (endIndex < 0)
				endIndex = getNumTasks();
			dispatcher = new DispatcherThread(size, getNumTasks(),
					minDispatch, maxDispatch, exactDispatch, shuffle, startIndex, endIndex, postBatchHook);
			if (rootDispatchOnly) {
				debug("starting dispatcher serially");
				dispatcher.run();
			} else {
				debug("starting dispatcher threaded");
				dispatcher.start();
			}
		}
		
		int[] my_id = { rank };
		
		int[] batch_lengh_buf = new int[1];
		
		while (true) {
			if (rank == 0 && rootDispatchOnly)
				break;
			
			int[] batch;
			if (dispatcher == null) {
				// this is a non-root thread, use MPJ to get the next batch
				
				debug("sending READY message");
				// report to dispatcher as ready
				MPI.COMM_WORLD.Send(my_id, 0, 1, MPI.INT, 0, TAG_READY_FOR_BATCH);
				
				debug("receiving batch lengh");
				// receive a new batch length
				MPI.COMM_WORLD.Recv(batch_lengh_buf, 0, 1, MPI.INT, 0, TAG_NEW_BATCH_LENGH);
				
				if (batch_lengh_buf[0] == 0) {
					debug("DONE!");
					// we're done
					break;
				}
				
				batch = new int[batch_lengh_buf[0]];
				
				debug("receiving batch of length "+batch.length);
				MPI.COMM_WORLD.Recv(batch, 0, batch.length, MPI.INT, 0, TAG_NEW_BATCH);
			} else {
				debug("getting next batch directly");
				batch = dispatcher.getNextBatch(rank);
				
				if (batch == null || batch.length == 0) {
					debug("DONE!");
					// we're done
					break;
				} else {
					debug("receiving batch of length "+batch.length);
				}
			}
			
			// now calculate the batch
			debug("calculating batch");
			try {
				calculateBatch(batch);
			} catch (Exception e) {
				abortAndExit(e);
			}
		}
		
		debug("waiting for other processes with Barrier()");
		
		// wait for everyone
		if (!SINGLE_NODE_NO_MPJ)
			MPI.COMM_WORLD.Barrier();
		try {
			doFinalAssembly();
		} catch (Exception e) {
			abortAndExit(e);
		}
		
		debug("Process "+rank+" DONE!");
	}
	
	protected abstract void calculateBatch(int[] batch) throws Exception;
	
	protected abstract void doFinalAssembly() throws Exception;
	
	protected static Options createOptions() {
		Options ops = new Options();
		
		Option threadsOption = new Option("t", "threads", true,
				"Number of calculation threads on each node. Default is the number" +
				" of available processors (in this case: "+Runtime.getRuntime().availableProcessors()+")");
		threadsOption.setRequired(false);
		ops.addOption(threadsOption);
		
		Option minDispatchOption = new Option("min", "min-dispatch", true, "Minimum number of tasks to dispatch" +
				" to a compute node at a time. Default: "+MIN_DISPATCH_DEFAULT);
		minDispatchOption.setRequired(false);
		ops.addOption(minDispatchOption);
		
		Option maxDispatchOption = new Option("max", "max-dispatch", true, "Maximum number of tasks to dispatch" +
				" to a compute node at a time. Actual tasks per node will never be greater than the number of" +
				" sites divided by the number of nodes. Default: "+MAX_DISPATCH_DEFAULT);
		maxDispatchOption.setRequired(false);
		ops.addOption(maxDispatchOption);
		
		Option exactDispatchOption = new Option("exact", "exact-dispatch", true, "Exact number of tasks to dispatch" +
				" to a compute node at a time. Default is calculated from min/max and number of tasks left.");
		exactDispatchOption.setRequired(false);
		ops.addOption(exactDispatchOption);
		
		Option rootDispatchOnlyOption = new Option("rdo", "root-dispatch-only", false, "Flag for root node only" +
				"dispatching tasks and not calculating itself");
		rootDispatchOnlyOption.setRequired(false);
		ops.addOption(rootDispatchOnlyOption);
		
		Option deadlockOption = new Option("dead", "deadlock", false,
				"If supplied, dedlock detection will be enabled (no recovery, however).");
		deadlockOption.setRequired(false);
		ops.addOption(deadlockOption);
		
		Option startIndexOption = new Option("start", "start-index", true, "If supplied, will calculate tasks starting at the"
				+ " given index, includsive. Default is zero.");
		startIndexOption.setRequired(false);
		ops.addOption(startIndexOption);
		
		Option endIndexOption = new Option("end", "end-index", true, "If supplied, will calculate tasks up until the"
				+ " given index, excludsive. Default is the number of tasks.");
		endIndexOption.setRequired(false);
		ops.addOption(endIndexOption);
		
		return ops;
	}
	
	protected static String[] initMPJ(String[] args) {
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				abortAndExit(e);
			}
		});
		if (SINGLE_NODE_NO_MPJ)
			return args;
		return MPI.Init(args);
	}
	
	protected static CommandLine parse(Options options, String args[], Class<?> clazz) {
		try {
			CommandLineParser parser = new GnuParser();
			
			CommandLine cmd = parser.parse(options, args);
			return cmd;
		} catch (Exception e) {
			System.out.println(e.getMessage());
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(
					ClassUtils.getClassNameWithoutPackage(clazz),
					options, true );
			abortAndExit(2);
			return null; // not accessible
		}
	}
	
	protected static void finalizeMPJ() {
		if (deadlock != null)
			deadlock.kill();
		if (!SINGLE_NODE_NO_MPJ)
			MPI.Finalize();
		System.exit(0);
	}
	
	public static void abortAndExit(int ret) {
		abortAndExit(null, ret);
	}
	
	public static void abortAndExit(Throwable t) {
		abortAndExit(t, 1);
	}
	
	public static void abortAndExit(Throwable t, int ret) {
		if (t != null)
			t.printStackTrace();
		if (deadlock != null)
			deadlock.kill();
		if (!SINGLE_NODE_NO_MPJ)
			MPI.COMM_WORLD.Abort(ret);
		System.exit(ret);
	}

}
