package org.opensha.commons.hpc.pbs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class RangerScriptWriter extends BatchScriptWriter {
	
	public static final File JAVA_BIN = new File("/share/home/00950/kevinm/java/default/bin/java");
	public static final File MPJ_HOME = new File("/share/home/00950/kevinm/mpj-v0_38");
	public static final File FMPJ_HOME = new File("/share/home/00950/kevinm/FastMPJ");

	@Override
	public List<String> getBatchHeader(int mins, int nodes,
			int ppn, String queue) {
		ArrayList<String> pbs = new ArrayList<String>();
		
		if (queue == null || queue.isEmpty())
			queue = "normal";
		
		String pType = ppn+"way";
		int cpus = nodes * 16;
		
//		#$ -l h_rt=00:05:00
//		#$ -pe 1way 32
//		#$ -q normal
//		#$ -V
		pbs.add("#$ -l h_rt=00:"+mins+":00");
		pbs.add("#$ -pe "+pType+" "+cpus);
		pbs.add("#$ -q "+queue);
		pbs.add("#$ -cwd");
		pbs.add("#$ -V");
		pbs.add("");
		pbs.add("PBS_NODEFILE=\"/tmp/${USER}-hostfile-${JOB_ID}\"");
		pbs.add("echo \"creating PBS_NODEFILE: $PBS_NODEFILE\"");
		pbs.add("cat $PE_HOSTFILE | awk '{ print $1 }' > $PBS_NODEFILE");
		pbs.add("");
		
		return pbs;
	}

}
