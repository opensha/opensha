package org.opensha.sha.earthquake.faultSysSolution.inversion.mpj;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.SolutionProcessor;
import org.opensha.sha.earthquake.faultSysSolution.util.BranchAverageSolutionCreator;

import edu.usc.kmilner.mpj.taskDispatch.AsyncPostBatchHook;

public abstract class AbstractAsyncLogicTreeWriter extends AsyncPostBatchHook {

	private File outputDir;
	private SolutionProcessor processor;
	private BranchWeightProvider weightProv;
	
	private Map<String, BranchAverageSolutionCreator> baCreators;
	private SolutionLogicTree.FileBuilder sltBuilder;
	
	private boolean[] dones;

	public AbstractAsyncLogicTreeWriter(File outputDir, SolutionProcessor processor, BranchWeightProvider weightProv) {
		super(1);
		this.outputDir = outputDir;
		this.processor = processor;
		this.weightProv = weightProv;
		this.baCreators = new HashMap<>();
		
		dones = new boolean[getNumTasks()];
	}
	
	public abstract int getNumTasks();
	
	public abstract void debug(String message);
	
	public abstract LogicTreeBranch<?> getBranch(int calcIndex);
	
	public abstract FaultSystemSolution getSolution(LogicTreeBranch<?> branch, int calcIndex) throws IOException;
	
	public abstract void abortAndExit(Throwable t, int exitCode);

	@Override
	protected void batchProcessedAsync(int[] batch, int processIndex) {
		// TODO Auto-generated method stub
		memoryDebug("AsyncLogicTree: beginning async call with batch size "
				+batch.length+" from "+processIndex+": "+getCountsString());
		
		synchronized (dones) {
			try {
				if (sltBuilder == null) {
					sltBuilder = new SolutionLogicTree.FileBuilder(processor,
							new File(outputDir.getParentFile(), outputDir.getName()+".zip"));
					sltBuilder.setWeightProv(weightProv);
				}
				
				for (int index : batch) {
					dones[index] = true;
					
					LogicTreeBranch<?> branch = getBranch(index);
					
					debug("AsyncLogicTree: calcDone "+index+" = branch "+branch);
					
					FaultSystemSolution sol = getSolution(branch, index);
					if (sol == null)
						// not ready, can happen if there are multiple inversions per branch
						continue;
					
					sltBuilder.solution(sol, branch);
					
					// now add in to branch averaged
					if (baCreators != null) {
						String baPrefix = null;
						boolean allAffect = true;
						for (int i=0; i<branch.size(); i++) {
							if (branch.getLevel(i).affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME, true)) {
								if (baPrefix == null)
									baPrefix = "";
								else
									baPrefix += "_";
								baPrefix += branch.getValue(i).getFilePrefix();
							} else {
								allAffect = false;
							}
						}
						
						if (allAffect) {
							debug("AsyncLogicTree won't branch average, all levels affect "+FaultSystemRupSet.RUP_PROPS_FILE_NAME);
						} else {
							if (!baCreators.containsKey(baPrefix))
								baCreators.put(baPrefix, new BranchAverageSolutionCreator(weightProv));
							BranchAverageSolutionCreator baCreator = baCreators.get(baPrefix);
							try {
								baCreator.addSolution(sol, branch);
							} catch (Exception e) {
								e.printStackTrace();
								System.err.flush();
								debug("AsyncLogicTree: Branch averaging failed for branch "+branch+", disabling averaging");
								baCreators = null;
							}
						}
					}
				}
			} catch (IOException ioe) {
				abortAndExit(ioe, 2);
			}
		}
		
		memoryDebug("AsyncLogicTree: exiting async process, stats: "+getCountsString());
	}

	@Override
	public void shutdown() {
		super.shutdown();
		
		memoryDebug("AsyncLogicTree: finalizing logic tree zip");
		try {
			sltBuilder.build();
		} catch (IOException e) {
			memoryDebug("AsyncLogicTree: failed to build logic tree zip");
			e.printStackTrace();
		}
		
		if (baCreators != null && !baCreators.isEmpty()) {
			for (String baPrefix : baCreators.keySet()) {
				String prefix = outputDir.getName();
				if (!baPrefix.isBlank())
					prefix += "_"+baPrefix;
				
				File baFile = new File(outputDir.getParentFile(), prefix+"_branch_averaged.zip");
				memoryDebug("AsyncLogicTree: building "+baFile.getAbsolutePath());
				try {
					FaultSystemSolution baSol = baCreators.get(baPrefix).build();
					baSol.write(baFile);
				} catch (Exception e) {
					memoryDebug("AsyncLogicTree: failed to build BA for "+baFile.getAbsolutePath());
					e.printStackTrace();
					continue;
				}
			}
		}
	}
	
	private void memoryDebug(String info) {
		if (info == null || info.isBlank())
			info = "";
		else
			info += "; ";
	    
	    System.gc();
		Runtime rt = Runtime.getRuntime();
		long totalMB = rt.totalMemory() / 1024 / 1024;
		long freeMB = rt.freeMemory() / 1024 / 1024;
		long usedMB = totalMB - freeMB;
		debug(info+"mem t/u/f: "+totalMB+"/"+usedMB+"/"+freeMB);
	}

}
