package scratch.UCERF3.simulatedAnnealing.hpc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.hpc.JavaShellScriptWriter;

import com.google.common.base.Preconditions;

import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.TimeCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.params.CoolingScheduleType;
import scratch.UCERF3.simulatedAnnealing.params.GenerationFunctionType;
import scratch.UCERF3.simulatedAnnealing.params.NonnegativityConstraintType;

public class ThreadedScriptCreator {
	
	// required -- java
	private JavaShellScriptWriter writer;
	
	// required -- args
	private File aMat;
	private File dMat;
	private File a_ineqMat;
	private File d_ineqMat;
	private File initial;
	private File zipFile;
	private String numThreads;
	private File solFile;
	private CompletionCriteria criteria;
	private CompletionCriteria subCompletion;
	private TimeCompletionCriteria checkPointCriteria;
	
	// optional -- args
	private File progFile;
	private CoolingScheduleType cool;
	private GenerationFunctionType perturb;
	private NonnegativityConstraintType nonNeg;
	private boolean setSubIterationsZero = false;
	private boolean plots = false;
	
	public ThreadedScriptCreator(JavaShellScriptWriter writer,
			String numThreads, File solFile, CompletionCriteria criteria, CompletionCriteria subCompletion) {
		this.writer = writer;
		this.numThreads = numThreads;
		this.solFile = solFile;
		this.criteria = criteria;
		this.subCompletion = subCompletion;
	}
	
	public String getArgs() {
//		Preconditions.checkNotNull(initial, "initial file is required!"); // no longer required
		Preconditions.checkNotNull(subCompletion, "subCompletion cannot be null");
		Preconditions.checkNotNull(numThreads, "numThreads cannot be null");
		Preconditions.checkState(!numThreads.isEmpty(), "numThreads cannot be blank");
		Preconditions.checkNotNull(solFile, "solution file is required!");
		String args;
		if (zipFile == null) {
			Preconditions.checkNotNull(aMat, "A matrix file is required!");
			Preconditions.checkNotNull(dMat, "d matrix file is required!");
			args =	  "--a-matrix-file "+aMat.getAbsolutePath()
					+" --d-matrix-file "+dMat.getAbsolutePath();
			if (a_ineqMat != null)
				args += " --a-ineq-matrix-file "+a_ineqMat.getAbsolutePath()
					   +" --d-ineq-matrix-file "+d_ineqMat.getAbsolutePath();
		} else {
			args = "--zip-file "+zipFile.getAbsolutePath();
		}
		
		if (initial != null)
			args	+=	 " --initial-state-file "+initial.getAbsolutePath();
		args		+=	 " --num-threads "+numThreads
						+" --solution-file "+solFile.getAbsolutePath();
		
		Preconditions.checkNotNull(criteria, "Criteria cannot be null!");
		args += " "+ThreadedSimulatedAnnealing.completionCriteriaToArgument(criteria);
		Preconditions.checkNotNull(subCompletion, "subCompletion cannot be null!");
		args += " "+ThreadedSimulatedAnnealing.subCompletionCriteriaToArgument(subCompletion);
		
		if (progFile != null)
			args +=		 " --progress-file "+progFile.getAbsolutePath();
		if (setSubIterationsZero)
			args +=		 " --start-sub-iters-zero";
		if (cool != null)
			args +=		 " --cool "+cool.name();
		if (perturb != null)
			args +=		 " --perturb "+perturb.name();
		if (nonNeg != null)
			args +=		 " --nonneg "+nonNeg.name();
		if (checkPointCriteria != null)
			args +=		 " --checkpoint "+checkPointCriteria.getTimeStr();
		if (plots)
			args +=		 " --plots";
		
		return args;
	}
	
	// moved to BatchScriptWriter implementations
//	public List<String> buildPBSScript(int mins, int nodes, int ppn, String queue) {
//		ArrayList<String> pbs = new ArrayList<String>();
//		
//		if (queue != null && !queue.isEmpty())
//			pbs.add("#PBS -q "+queue);
//		pbs.add("#PBS -l walltime=00:"+mins+":00,nodes="+nodes+":ppn="+ppn);
//		pbs.add("#PBS -V");
//		pbs.add("");
//		
//		List<String> script = buildScript();
//		
//		script.addAll(2, pbs);
//		
//		return script;
//	}
	
	public String getClassName() {
		return ThreadedSimulatedAnnealing.class.getName();
	}
	
	public List<String> buildScript() {
		return writer.buildScript(getClassName(), getArgs());
	}
	
	public void writeScript(File file, List<String> script) throws IOException {
		JavaShellScriptWriter.writeScript(file, script);
	}

	public void setaMat(File aMat) {
		this.aMat = aMat;
	}

	public void setdMat(File dMat) {
		this.dMat = dMat;
	}

	public void setInitial(File initial) {
		this.initial = initial;
	}

	public void setA_ineqMat(File a_ineqMat) {
		this.a_ineqMat = a_ineqMat;
	}

	public void setD_ineqMat(File d_ineqMat) {
		this.d_ineqMat = d_ineqMat;
	}

	public void setZipFile(File zipFile) {
		this.zipFile = zipFile;
	}

	public void setSubCompletion(CompletionCriteria subCompletion) {
		this.subCompletion = subCompletion;
	}

	public void setNumThreads(String numThreads) {
		this.numThreads = numThreads;
	}

	public void setSolFile(File solFile) {
		this.solFile = solFile;
	}

	public void setProgFile(File progFile) {
		this.progFile = progFile;
	}

	public void setCriteria(CompletionCriteria criteria) {
		this.criteria = criteria;
	}

	public void setCool(CoolingScheduleType cool) {
		this.cool = cool;
	}

	public void setPerturb(GenerationFunctionType perturb) {
		this.perturb = perturb;
	}

	public void setNonNeg(NonnegativityConstraintType nonNeg) {
		this.nonNeg = nonNeg;
	}
	
	public void setSubIterationsZero(boolean setSubIterationsZero) {
		this.setSubIterationsZero = setSubIterationsZero;
	}

	public TimeCompletionCriteria getCheckPointCriteria() {
		return checkPointCriteria;
	}

	public void setCheckPointCriteria(TimeCompletionCriteria checkPointCriteria) {
		this.checkPointCriteria = checkPointCriteria;
	}
	
	public void setPlots(boolean plots) {
		this.plots = plots;
	}
	
	public boolean isPlots() {
		return plots;
	}

}
