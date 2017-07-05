package scratch.UCERF3.erf.mean;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.opensha.commons.hpc.JavaShellScriptWriter;
import org.opensha.commons.hpc.mpj.FastMPJShellScriptWriter;
import org.opensha.commons.hpc.pbs.BatchScriptWriter;
import org.opensha.commons.hpc.pbs.StampedeScriptWriter;
import org.opensha.commons.hpc.pbs.USC_HPCC_ScriptWriter;

import com.google.common.collect.Lists;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.simulatedAnnealing.hpc.LogicTreePBSWriter;
import scratch.UCERF3.simulatedAnnealing.hpc.MPJInversionDistributor;

public class CurveCalcPBSWriter {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		double[] upperDepthTols = { 0, 1d, 5d, Double.POSITIVE_INFINITY };
		boolean[] rakeCombines = { false, true };
		double[] magTols = { 0, 0.05, 0.1, 0.5};
		DeformationModels[] rakeBasisDMs = { null, DeformationModels.GEOLOGIC };
		DeformationModels[] noRakeBasisDMs = { null };
		
		File writeDir = new File("/home/kevin/OpenSHA/UCERF3/MeanUCERF3-curves-test2");
		if (!writeDir.exists())
			writeDir.mkdir();
		
		BatchScriptWriter pbsWrite = new USC_HPCC_ScriptWriter();
		File remoteDir = new File("/auto/scec-02/kmilner/ucerf3/curves/MeanUCERF3-curves");
		File javaBin = USC_HPCC_ScriptWriter.JAVA_BIN;
		File mpjHome = null;
		int maxHeapMB = 9000;
		
//		BatchScriptWriter pbsWrite = new StampedeScriptWriter();
//		File remoteDir = new File("/work/00950/kevinm/ucerf3/curves/MeanUCERF3-curves");
//		File javaBin = StampedeScriptWriter.JAVA_BIN;
//		File mpjHome = StampedeScriptWriter.FMPJ_HOME;
//		int maxHeapMB = 26000;
		
		String meanSolFileName = "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_TRUE_HAZARD_MEAN_SOL.zip";
		File meanSolFile = new File(remoteDir, meanSolFileName);
		
		JavaShellScriptWriter javaWrite = new JavaShellScriptWriter(javaBin, maxHeapMB,
				LogicTreePBSWriter.getClasspath(remoteDir, remoteDir));
		
		int mins = 1000;
		int nodes = 1;
		int ppn = 8;
		String queue = null;
		
		String className = CurveCalcTest.class.getName();
		
		int numJobs = 0;
		
		for (double upperDepthTol : upperDepthTols) {
			for (boolean rakeCombine : rakeCombines) {
				DeformationModels[] myRakeBasisDMs;
				if (rakeCombine)
					myRakeBasisDMs = rakeBasisDMs;
				else
					myRakeBasisDMs = noRakeBasisDMs;
				for (double magTol : magTols) {
					for (DeformationModels rakeBasis : myRakeBasisDMs) {
						String rakeBasisStr;
						if (rakeBasis == null)
							rakeBasisStr = "null";
						else
							rakeBasisStr = rakeBasis.name();
						String jobArgs = meanSolFile.getAbsolutePath()+" "+remoteDir.getAbsolutePath()
								+" "+upperDepthTol+" "+magTol+" "+rakeCombine+" "+rakeBasisStr;
						List<String> script = javaWrite.buildScript(className, jobArgs);
						String subDirName = CurveCalcTest.getSubDirName(upperDepthTol, magTol, rakeCombine, rakeBasis);
						File jobFile = new File(writeDir, subDirName+".pbs");
						
						pbsWrite.writeScript(jobFile, script, mins, nodes, ppn, queue);
						numJobs++;
					}
				}
			}
		}
		
		String jobArgs = meanSolFile.getAbsolutePath()+" "+remoteDir.getAbsolutePath()+" gridded";
		List<String> script = javaWrite.buildScript(className, jobArgs);
		File jobFile = new File(writeDir, "gridded.pbs");
		numJobs++;
		
		pbsWrite.writeScript(jobFile, script, mins, nodes, ppn, queue);
		
		jobArgs = meanSolFile.getAbsolutePath()+" "+remoteDir.getAbsolutePath()+" truemean";
		script = javaWrite.buildScript(className, jobArgs);
		jobFile = new File(writeDir, "truemean.pbs");
		numJobs++;
		
		pbsWrite.writeScript(jobFile, script, mins, nodes, ppn, queue);
		
		if (pbsWrite instanceof StampedeScriptWriter) {
			FastMPJShellScriptWriter mpjWrite = new FastMPJShellScriptWriter(javaBin, javaWrite.getMaxHeapSizeMB(),
					javaWrite.getClasspath(), mpjHome);
			jobArgs = "--exact-dispatch 1 "+meanSolFile.getAbsolutePath()+" "+remoteDir.getAbsolutePath();
			script = mpjWrite.buildScript(CurveCalcSweepMPJ.class.getName(), jobArgs);
			jobFile = new File(writeDir, "mpjsubmit.pbs");
			pbsWrite.writeScript(jobFile, script, mins, CurveCalcSweepMPJ.buildCalcsList().size(), ppn, queue);
		}
	}

}
