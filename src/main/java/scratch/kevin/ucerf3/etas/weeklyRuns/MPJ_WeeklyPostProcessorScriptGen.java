package scratch.kevin.ucerf3.etas.weeklyRuns;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.hpc.JavaShellScriptWriter;
import org.opensha.commons.hpc.mpj.MPJExpressShellScriptWriter;
import org.opensha.commons.hpc.pbs.BatchScriptWriter;
import org.opensha.commons.hpc.pbs.USC_HPCC_ScriptWriter;

import com.google.common.base.Preconditions;

import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;

public class MPJ_WeeklyPostProcessorScriptGen {

	public static void main(String[] args) throws IOException {
		File localSimsDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations");
		
		String batchName = "2020_05_14-weekly-1986-present-full_td-kCOV1.5";
		
		File localBatchDir = new File(localSimsDir, batchName);
		Preconditions.checkState(localBatchDir.exists());
		
		BatchScriptWriter pbsWrite = new USC_HPCC_ScriptWriter();
		File remoteSimsDir = new File("/home/scec-02/kmilner/ucerf3/etas_sim");
		File remoteBatchDir = new File(remoteSimsDir, batchName);
		File javaBin = USC_HPCC_ScriptWriter.JAVA_BIN;
		File mpjHome = USC_HPCC_ScriptWriter.MPJ_HOME;
		int maxHeapMB = 55000;
		List<File> classpath = new ArrayList<>();
		classpath.add(new File(remoteSimsDir, "opensha-dev-all.jar"));
		JavaShellScriptWriter mpjWrite = new MPJExpressShellScriptWriter(
				javaBin, maxHeapMB, classpath, mpjHome);
		
		int mins = 10*60;
		int nodes = 30;
		int ppn = 8;
		String queue = "scec";
		
		String argz = MPJTaskCalculator.argumentBuilder().minDispatch(1).maxDispatch(10).threads(1).build(" ");
		argz += " "+remoteBatchDir.getAbsolutePath();
		
		File jobFile = new File(localBatchDir, "post_process.slurm");
		pbsWrite.writeScript(jobFile, mpjWrite.buildScript(MPJ_WeeklyPostProcessor.class.getName(), argz),
				mins, nodes, ppn, queue);
	}

}
