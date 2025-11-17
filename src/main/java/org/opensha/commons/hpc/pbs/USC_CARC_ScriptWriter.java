package org.opensha.commons.hpc.pbs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class USC_CARC_ScriptWriter extends BatchScriptWriter {

	public static final File FMPJ_HOME = new File("/project2/scec_608/kmilner/mpj/FastMPJ");
	public static final File MPJ_HOME = new File("/project2/scec_608/kmilner/mpj/mpj-current");
	public static final File JAVA_BIN = new File("java");
	
	public static final String SHARED_SCRATCH_DIR = "/scratch1";
	public static final String NODE_TEMP_DIR = "${TMPDIR}";
	
	private String nodesAddition;
//	private int perNodeMemGB = -1;
	private boolean skipRootNode = false;
	
	public USC_CARC_ScriptWriter() {
		this(null);
	}
	
	public USC_CARC_ScriptWriter(String nodesAddition) {
		this.nodesAddition = nodesAddition;
	}

	public String getNodesAddition() {
		return nodesAddition;
	}

	public void setNodesAddition(String nodesAddition) {
		this.nodesAddition = nodesAddition;
	}
	
//	public void setPerNodeMemGB(int perNodeMemGB) {
//		this.perNodeMemGB = perNodeMemGB;
//	}
	
	public void setSkipRootNode(boolean skipRootNode) {
		this.skipRootNode = skipRootNode;
	}

	@Override
	public List<String> getBatchHeader(int mins, int nodes,
			int ppn, String queue) {
		ArrayList<String> pbs = new ArrayList<String>();
		
		pbs.add("#SBATCH -t 00:"+mins+":00");
		pbs.add("#SBATCH -N "+nodes);
//		pbs.add("#SBATCH -n "+cpus);
		pbs.add("#SBATCH --ntasks="+nodes+" --cpus-per-task="+ppn);
		if (queue != null && !queue.isEmpty())
			pbs.add("#SBATCH -p "+queue);
		pbs.add("#SBATCH --mem 0"); // use all available memory
		pbs.add("");
		pbs.add("PBS_NODEFILE=\"/tmp/${USER}-hostfile-${SLURM_JOBID}\"");
		pbs.add("echo \"creating PBS_NODEFILE: $PBS_NODEFILE\"");
		pbs.add("scontrol show hostnames $SLURM_NODELIST > $PBS_NODEFILE");
		if (skipRootNode) {
			pbs.add("	tail -n +2 $PBS_NODEFILE > ${PBS_NODEFILE}.tmp");
			pbs.add("	mv ${PBS_NODEFILE}.tmp ${PBS_NODEFILE}");
		}
		pbs.add("");
		
		return pbs;
	}

}
