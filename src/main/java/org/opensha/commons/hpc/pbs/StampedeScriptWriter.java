package org.opensha.commons.hpc.pbs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class StampedeScriptWriter extends BatchScriptWriter {
	
	public static final File JAVA_BIN = new File("/home1/00950/kevinm/java/default/bin/java");
	public static final File FMPJ_HOME = new File("/home1/00950/kevinm/FastMPJ");
	
	private boolean knl;
	
	public StampedeScriptWriter() {
		this(false);
	}
	
	public StampedeScriptWriter(boolean knl) {
		setKNL(knl);
	}
	
	public void setKNL(boolean knl) {
		this.knl = knl;
	}

	@Override
	public List<String> getBatchHeader(int mins, int nodes,
			int ppn, String queue) {
		ArrayList<String> pbs = new ArrayList<String>();
		
		if (queue == null || queue.isEmpty())
			queue = "normal";
		
		int cpus = nodes * ppn;
		
//		#$ -l h_rt=00:05:00
//		#$ -pe 1way 32
//		#$ -q normal
//		#$ -V
		pbs.add("#SBATCH -t 00:"+mins+":00");
		if (knl)
			pbs.add("#SBATCH -N "+nodes);
		pbs.add("#SBATCH -n "+cpus);
		pbs.add("#SBATCH -p "+queue);
		pbs.add("");
		pbs.add("PBS_NODEFILE=\"/tmp/${USER}-hostfile-${SLURM_JOBID}\"");
		pbs.add("echo \"creating PBS_NODEFILE: $PBS_NODEFILE\"");
		pbs.add("scontrol show hostnames $SLURM_NODELIST > $PBS_NODEFILE");
		pbs.add("");
		
		return pbs;
	}

}
