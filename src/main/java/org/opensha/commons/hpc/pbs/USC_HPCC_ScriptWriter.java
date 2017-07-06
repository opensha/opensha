package org.opensha.commons.hpc.pbs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class USC_HPCC_ScriptWriter extends BatchScriptWriter {
	
//	public static final File MPJ_HOME = new File("/home/rcf-12/kmilner/mpj-v0_38");
	public static final File MPJ_HOME = new File("/home/scec-00/kmilner/mpj-current");
	public static final File FMPJ_HOME = new File("/home/rcf-12/kmilner/FastMPJ");
//	public static final File JAVA_BIN = new File("/usr/usc/jdk/default/jre/bin/java");
	public static final File JAVA_BIN = new File("/usr/usc/jdk/1.8.0_45/bin/java");
	
	private String nodesAddition;
	private int perNodeMemGB = -1;
	
	public USC_HPCC_ScriptWriter() {
		this(null);
	}
	
	public USC_HPCC_ScriptWriter(String nodesAddition) {
		this.nodesAddition = nodesAddition;
	}

	public String getNodesAddition() {
		return nodesAddition;
	}

	public void setNodesAddition(String nodesAddition) {
		this.nodesAddition = nodesAddition;
	}
	
	public void setPerNodeMemGB(int perNodeMemGB) {
		this.perNodeMemGB = perNodeMemGB;
	}

	@Override
	public List<String> getBatchHeader(int mins, int nodes,
			int ppn, String queue) {
		ArrayList<String> pbs = new ArrayList<String>();
		
		if (queue != null && !queue.isEmpty())
			pbs.add("#PBS -q "+queue);
		String dashL = "#PBS -l walltime=00:"+mins+":00,nodes="+nodes;
		
		if (nodesAddition != null && !nodesAddition.isEmpty())
			dashL += ":"+nodesAddition;
		if (perNodeMemGB > 0 && ppn <= 0)
			ppn = 1;
		if (ppn > 0)
			dashL += ":ppn="+ppn;
		if (perNodeMemGB > 0) {
			int memPerProcess = perNodeMemGB/ppn;
			dashL += ",pmem="+memPerProcess+"gb";
		}
		pbs.add(dashL);
		pbs.add("#PBS -V");
		pbs.add("");
		pbs.add("if [[ -e $PBS_NODEFILE ]];then");
		pbs.add("	NEW_NODEFILE=\"/tmp/${USER}-hostfile-${PBS_JOBID}\"");
		pbs.add("	echo \"creating PBS_NODEFILE: $NEW_NODEFILE\"");
		pbs.add("	cat $PBS_NODEFILE | sort | uniq > $NEW_NODEFILE");
		pbs.add("	export PBS_NODEFILE=$NEW_NODEFILE");
		pbs.add("fi");
		pbs.add("");
		
		return pbs;
	}

}
