package scratch.UCERF3.analysis;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.opensha.commons.hpc.JavaShellScriptWriter;
import org.opensha.commons.hpc.mpj.FastMPJShellScriptWriter;
import org.opensha.commons.hpc.mpj.MPJExpressShellScriptWriter;
import org.opensha.commons.hpc.pbs.BatchScriptWriter;
import org.opensha.commons.hpc.pbs.USC_HPCC_ScriptWriter;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.MagDependentAperiodicityOptions;

import scratch.UCERF3.simulatedAnnealing.hpc.LogicTreePBSWriter;
import scratch.UCERF3.simulatedAnnealing.hpc.LogicTreePBSWriter.RunSites;

import com.google.common.collect.Lists;

public class MPJ_ERF_ProbGainCalcScriptWriter {

	public static void main(String[] args) throws IOException {
		String runName = "ucerf3-prob-gains-open1875-main-30yr";
		if (args.length > 1)
			runName = args[1];
		
		boolean mainFaults = true;
		
		double duration = 30;
		int histBasis = 1875;
		
		// it is assumed that this file is also stored locally in InversionSolutions!
		String compoundFileName = "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL.zip";
		
//		RunSites site = RunSites.HPCC;
//		int nodes = 20;
//		int jobMins = 10*60; // TODO
//		int threads = 1;
		
		RunSites site = RunSites.STAMPEDE;
		int nodes = 60;
		int jobMins = 2*60; // TODO
		int threads = 3;
		
//		String threadsArg = "";
		// trailing space is important
		
		File localMainDir = new File("/home/kevin/OpenSHA/UCERF3/probGains");
		if (!localMainDir.exists())
			localMainDir.mkdir();
		File remoteMainDir = new File(site.getRUN_DIR().getParentFile(), "prob_gains");
		
		runName = LogicTreePBSWriter.df.format(new Date())+"-"+runName;
		
		File remoteDir = new File(remoteMainDir, runName);
		File writeDir = new File(localMainDir, runName);
		if (!writeDir.exists())
			writeDir.mkdir();
		
		File remoteCompoundfile = new File(remoteMainDir, compoundFileName);
		
		List<File> classpath = LogicTreePBSWriter.getClasspath(remoteMainDir, remoteDir);
		
		JavaShellScriptWriter mpjWrite;
		if (site.isFastMPJ())
			mpjWrite = new FastMPJShellScriptWriter(site.getJAVA_BIN(), site.getMaxHeapSizeMB(null),
					classpath, site.getMPJ_HOME());
		else
			mpjWrite = new MPJExpressShellScriptWriter(site.getJAVA_BIN(), site.getMaxHeapSizeMB(null),
					classpath, site.getMPJ_HOME());
		
		mpjWrite.setInitialHeapSizeMB(site.getInitialHeapSizeMB(null));
		mpjWrite.setHeadless(true);
		
		BatchScriptWriter batchWrite = site.forBranch(null);
		if (site == RunSites.HPCC) {
			((USC_HPCC_ScriptWriter)batchWrite).setNodesAddition(null);
		}
		
//		List<BPTAveragingTypeOptions> calcOpsList = Lists.newArrayList(BPTAveragingTypeOptions.AVE_RI_AVE_NORM_TIME_SINCE);
		List<BPTAveragingTypeOptions> calcOpsList = Lists.newArrayList(BPTAveragingTypeOptions.values());
		
		MagDependentAperiodicityOptions[] covs = { MagDependentAperiodicityOptions.LOW_VALUES,
				MagDependentAperiodicityOptions.MID_VALUES, MagDependentAperiodicityOptions.HIGH_VALUES };
		
		String className = MPJ_ERF_ProbGainCalc.class.getName();
		
		for (BPTAveragingTypeOptions calcOps : calcOpsList) {
			String opsStr = getAveDirName(calcOps);
			
			for (MagDependentAperiodicityOptions cov : covs) {
				String pbsName = opsStr+"_"+cov.name()+".pbs";
				
				File remoteOutput = new File(new File(remoteDir, opsStr), cov.name());
				
				String classArgs = "--compound-sol "+remoteCompoundfile.getAbsolutePath()
						+" --output-dir "+remoteOutput.getAbsolutePath()+" --threads "+threads
						+" --duration "+duration+" --aperiodicity "+cov.name()
						+" --ave "+calcOps.name();
				if (mainFaults)
					classArgs += " --main-faults";
				if (histBasis > 0)
					classArgs += " --hist-open-interval-basis "+histBasis;
				
				List<String> script = mpjWrite.buildScript(className, classArgs);
				
				batchWrite.writeScript(new File(writeDir, pbsName), script, jobMins, nodes, site.getPPN(null), null);
			}
		}
	}
	
	public static String getAveDirName(BPTAveragingTypeOptions aveType) {
		String opsStr;
		if (aveType.isAveRI())
			opsStr = "aveRI";
		else
			opsStr = "aveRate";
		if (aveType.isAveNTS())
			opsStr += "_aveNTS";
		else
			opsStr += "_aveTS";
		return opsStr;
	}

}
