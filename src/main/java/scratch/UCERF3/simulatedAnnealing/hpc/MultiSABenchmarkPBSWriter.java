package scratch.UCERF3.simulatedAnnealing.hpc;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.opensha.commons.hpc.JavaShellScriptWriter;
import org.opensha.commons.hpc.mpj.MPJExpressShellScriptWriter;
import org.opensha.commons.hpc.pbs.BatchScriptWriter;
import org.opensha.commons.hpc.pbs.RangerScriptWriter;
import org.opensha.commons.hpc.pbs.USC_HPCC_ScriptWriter;

import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.IterationCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.TimeCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.params.CoolingScheduleType;

public class MultiSABenchmarkPBSWriter {
	
	public static File RUN_DIR = new File("/home/scec-02/kmilner/ucerf3/inversion_bench");
	
	public static ArrayList<File> getClasspath() {
		ArrayList<File> jars = new ArrayList<File>();
		jars.add(new File(RUN_DIR, "OpenSHA_complete.jar"));
		jars.add(new File(RUN_DIR, "parallelcolt-0.9.4.jar"));
		jars.add(new File(RUN_DIR, "commons-cli-1.2.jar"));
		jars.add(new File(RUN_DIR, "csparsej.jar"));
		return jars;
	}
	
	private static DateFormat df = new SimpleDateFormat("yyyy_MM_dd");

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
//		String runName = "2011_08_17-morgan";
//		String runName = "2011_09-02-ncal_50node";
//		String runName = "2011_09-06-ncal_const_100node";
//		String runName = "2011_09_08-ranger-morgan-new";
//		String runName = "ncal_1_sup_1thread_long";
//		String runName = "2011_09_29_new_create_test";
//		String runName = "2011_10_17-morgan-ncal1";
//		String runName = "2011_10_19-threads_test";
//		String runName = "2011_10_20-ncal-bench";
//		String runName = "2011_10_27-ncal-bench-sub-secs-test";
//		String runName = "2011_10_31-allcal-bench-sub-secs-test";
		
		
//		String runName = "agu/";
//		
//		runName += "ncal_constrained";
//		
//		runName += "/"+df.format(new Date());
		String runName = df.format(new Date())+"-model2-bench";
		
		File writeDir = new File("/home/kevin/OpenSHA/UCERF3/test_inversion/bench/"+runName);
		if (!writeDir.exists())
			writeDir.mkdir();
		
//		String queue = "nbns";
		String queue = null;
//		BatchScriptWriter batch = new USC_HPCC_ScriptWriter("pe1950");
		BatchScriptWriter batch = new USC_HPCC_ScriptWriter("quadcore");
		int ppn = 8;
		File mpjHome = USC_HPCC_ScriptWriter.MPJ_HOME;
		File javaBin = USC_HPCC_ScriptWriter.JAVA_BIN;
		
//		String queue = "normal";
//		BatchScriptWriter batch = new RangerScriptWriter();
//		int ppn = 1;
//		File mpjHome = new File("/share/home/00950/kevinm/mpj-v0_38");
//		File javaBin = new File("/share/home/00950/kevinm/java/default/bin/java");
//		File runDir = new File("/work/00950/kevinm/ucerf3/inversion");
		
		File runSubDir = new File(RUN_DIR, runName);
		
		int dsaAnnealMins, tsaAnnealMins, tsaSingleMins;
		CompletionCriteria subCompletion;
		
		if (runName.contains("agu")) {
			if (runName.contains("ncal")) {
				subCompletion = new TimeCompletionCriteria(500);
				dsaAnnealMins = 60*2;
			} else if (runName.contains("allcal")) {
				subCompletion = TimeCompletionCriteria.getInSeconds(1);
				dsaAnnealMins = 60*8;
			} else
				throw new IllegalStateException("how'd we get here???");
			tsaAnnealMins = dsaAnnealMins;
			tsaSingleMins = 60*72;
		} else {
			subCompletion = TimeCompletionCriteria.getInSeconds(1);
			subCompletion = null;
			
			dsaAnnealMins = 60*2;
			tsaAnnealMins = dsaAnnealMins;
			tsaSingleMins = 60*8;
		}

		int dsaWallMins = dsaAnnealMins + 60;
		int tsaWallMins = tsaAnnealMins + 30;
		int tsaSingleWallMins = tsaSingleMins + 30;

		ArrayList<File> jars = getClasspath();

		int heapSizeMB = 8000;
		
		boolean useMxdev = false;

		File zipFile = new File(runSubDir, "inputs.zip");

//		initialMat = new File(runSubDir, "initial.mat");
		
//		initialMat = new File(runDir, "initial.mat");
//		aMat = new File(runDir, "A_ncal_unconstrained.mat");
//		dMat = new File(runDir, "d_ncal_unconstrained.mat");
//		initialMat = null;

		CompletionCriteria dsaCriteria = TimeCompletionCriteria.getInMinutes(dsaAnnealMins);
		System.out.println("DSA criteria: "+dsaCriteria);
		CompletionCriteria tsaCriteria = TimeCompletionCriteria.getInMinutes(tsaAnnealMins);
		System.out.println("TSA criteria: "+tsaCriteria);
		CompletionCriteria tsaSingleCriteria = TimeCompletionCriteria.getInMinutes(tsaSingleMins);
		System.out.println("TSA (single node) criteria: "+tsaSingleCriteria);
		
		MPJExpressShellScriptWriter mpjWriter = new MPJExpressShellScriptWriter(javaBin, heapSizeMB, jars, mpjHome);
		JavaShellScriptWriter javaWriter = new JavaShellScriptWriter(javaBin, heapSizeMB, jars);

		DistributedScriptCreator dsa_create = new DistributedScriptCreator(mpjWriter, null, null, dsaCriteria, subCompletion, mpjHome, false);
		dsa_create.setZipFile(zipFile);
		ThreadedScriptCreator tsa_create = new ThreadedScriptCreator(javaWriter, null, null, tsaCriteria, subCompletion);
		tsa_create.setZipFile(zipFile);
		
		/* OFFICIAL AGU 2011 BENCHMARKS */
//		int[] dsa_threads = { 4 };
//		int[] tsa_threads = { 1,2,4,8 };
//		int[] nodes = { 2, 5, 10, 20 };
//		CompletionCriteria[] dSubComps = { subCompletion };
//		CoolingScheduleType[] cools = { CoolingScheduleType.FAST_SA };
//		int numRuns = 5;
		
		/* OFFICIAL HUGE SUPLEMENT */
//		int[] dsa_threads = { 4 };
//		int[] tsa_threads = new int[0];
//		int[] nodes = { 50, 100, 200 };
//		CompletionCriteria[] dSubComps = { subCompletion };
//		CoolingScheduleType[] cools = { CoolingScheduleType.FAST_SA };
//		int numRuns = 5;
		
		int[] dsa_threads = { 8 };
//		int[] tsa_threads = { 1,2,4,8 };
		int[] tsa_threads = new int[0];
		int[] nodes = { 20, 50 };
		CompletionCriteria[] dSubComps = {
				TimeCompletionCriteria.getInSeconds(1) };
//		CompletionCriteria[] dSubComps = {
//				TimeCompletionCriteria.getInSeconds(1), new TimeCompletionCriteria(2500),
//				TimeCompletionCriteria.getInSeconds(5), TimeCompletionCriteria.getInSeconds(10) };
		
		CoolingScheduleType[] cools = { CoolingScheduleType.FAST_SA };
		int numRuns = 3;

//		int[] dsa_threads = { 4 };
//		int[] dsa_threads = { 8 };

//		int[] tsa_threads = { 1 };
//		int[] tsa_threads = { 1,2,4,8 };
//		int[] tsa_threads = new int[0];

//		int[] nodes = { 20,50,100,200 };
//		int[] nodes = { 500 };
//		int[] nodes = { 10 };
//		int[] nodes = { 100, 200 };
//		int[] nodes = { 2, 5, 10, 20 };
//		int[] nodes = new int[0];

		//		int[] dSubIters = { 200, 600 };
//		CompletionCriteria[] dSubComps = { subCompletion };
//		CompletionCriteria[] dSubComps = {
//				new IterationCompletionCriteria(25), new IterationCompletionCriteria(50),
//				new IterationCompletionCriteria(100), new IterationCompletionCriteria(200),
//				new IterationCompletionCriteria(500), new TimeCompletionCriteria(500),
//				TimeCompletionCriteria.getInSeconds(1), new TimeCompletionCriteria(2500),
//				TimeCompletionCriteria.getInSeconds(5), TimeCompletionCriteria.getInSeconds(10),
//				TimeCompletionCriteria.getInSeconds(15), TimeCompletionCriteria.getInSeconds(20),  };
		
//		CoolingScheduleType[] cools = { CoolingScheduleType.FAST_SA };

//		int numRuns = 1;
//		int numRuns = 5;

		double nodeHours = 0;

		for (CoolingScheduleType cool : cools) {
			for (int numNodes : nodes) {
				for (int dsaThreads : dsa_threads) {
					for (CompletionCriteria dSubComp : dSubComps) {
						for (int r=0; r<numRuns; r++) {
							CompletionCriteria mySubCrit;
							if (subCompletion == null)
								mySubCrit = dSubComp;
							else
								mySubCrit = subCompletion;
							dsa_create.setSubCompletion(mySubCrit);
							String name = "dsa_"+dsaThreads+"threads_"+numNodes+"nodes_"+cool.name();
							name += "_dSub"+ThreadedSimulatedAnnealing.subCompletionArgVal(dSubComp);
							name += "_sub"+ThreadedSimulatedAnnealing.subCompletionArgVal(mySubCrit);
							name += "_run"+r;

							dsa_create.setProgFile(new File(runSubDir, name+".csv"));
							dsa_create.setSolFile(new File(runSubDir, name+".mat"));
							dsa_create.setNumThreads(""+dsaThreads);
							dsa_create.setCool(cool);
							if (dSubComp == mySubCrit)
								dsa_create.setDistSubCompletion(null);
							else
								dsa_create.setDistSubCompletion(dSubComp);
							
							File pbs = new File(writeDir, name+".pbs");
							System.out.println("Writing: "+pbs.getName());
							batch.writeScript(pbs, dsa_create.buildScript(), dsaWallMins, numNodes, ppn, queue);
							
							nodeHours += (double)numNodes * ((double)dsaAnnealMins / 60d);
						}
					}
				}
			}
			for (int tsaThreads : tsa_threads) {
				int wallMins;
				if (tsaThreads == 1) {
					tsa_create.setCriteria(tsaSingleCriteria);
					wallMins = tsaSingleWallMins;
				} else {
					tsa_create.setCriteria(tsaCriteria);
					wallMins = tsaWallMins;
				}
				for (int r=0; r<numRuns; r++) {
					CompletionCriteria mySubCompletion = subCompletion;
					if (mySubCompletion == null)
						mySubCompletion = dSubComps[0];
					
					String name = "tsa_"+tsaThreads+"threads_"+cool.name();
					name += "_sub"+ThreadedSimulatedAnnealing.subCompletionArgVal(mySubCompletion);
					name += "_run"+r;

					tsa_create.setProgFile(new File(runSubDir, name+".csv"));
					tsa_create.setSolFile(new File(runSubDir, name+".mat"));
					tsa_create.setNumThreads(""+tsaThreads);
					tsa_create.setCool(cool);
					tsa_create.setSubCompletion(mySubCompletion);

					File pbs = new File(writeDir, name+".pbs");
					System.out.println("Writing: "+pbs.getName());
					nodeHours += (double)tsaAnnealMins / 60d;
						batch.writeScript(pbs, tsa_create.buildScript(), wallMins, 1, 1, queue);
					}
			}
		}

		System.out.println("Node hours: "+(float)nodeHours + " (/60: "+((float)nodeHours/60f)+")");
		
		// now write a test job
		String name = "test_job";

		dsa_create.setProgFile(new File(runSubDir, name+".csv"));
		dsa_create.setSolFile(new File(runSubDir, name+".mat"));
		dsa_create.setCriteria(TimeCompletionCriteria.getInMinutes(3));
		
		File pbs = new File(writeDir, name+".pbs");
		System.out.println("Writing: "+pbs.getName());
		batch.writeScript(pbs, dsa_create.buildScript(), 10, 3, ppn, queue);
	}

}
