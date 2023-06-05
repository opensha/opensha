package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.BranchAverageSolutionCreator;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

/**
 * This module keeps track of the order that a branch averaged solution was built. This is useful to map individual
 * results to specific branches from other modules built with {@link BranchModuleBuilder}'s. Created automatically by 
 * {@link BranchAverageSolutionCreator}.
 * 
 * @author kevin
 *
 */
public class BranchAveragingOrder implements SubModule<ModuleContainer<?>>, CSV_BackedModule {
	
	private FaultSystemSolution sol;
	
	private String[] branchFileNames;
	private double[] weights;
	
	private Map<String, Integer> nameIndexMap = null;
	
	@SuppressWarnings("unused") // for deserialization
	private BranchAveragingOrder() {};
	
	public static class Builder implements BranchModuleBuilder<FaultSystemSolution, BranchAveragingOrder> {
		
		private List<String> branchFileNames;
		private List<Double> weights;
		
		public Builder() {
			branchFileNames = new ArrayList<>();
			weights = new ArrayList<>();
		}

		public synchronized void process(FaultSystemSolution sol, LogicTreeBranch<?> branch, double weight) {
			weights.add(weight);
			branchFileNames.add(branch.buildFileName());
		}
		
		public BranchAveragingOrder build() {
			BranchAveragingOrder ret = new BranchAveragingOrder();
			
			ret.branchFileNames = branchFileNames.toArray(new String[0]);
			ret.weights = Doubles.toArray(weights);
			
			return ret;
		}
	}

	@Override
	public String getName() {
		return "Branch Averaging Order";
	}

	@Override
	public void setParent(ModuleContainer<?> parent) throws IllegalStateException {
		Preconditions.checkNotNull(parent);
		if (parent instanceof FaultSystemSolution) {
			// it's a solution
			FaultSystemSolution sol = (FaultSystemSolution)parent;
			this.sol = sol;
		} else {
			// likely just an empty archive for standalone storage, do nothing
			this.sol = null;
		}
	}

	@Override
	public FaultSystemSolution getParent() {
		return sol;
	}

	@Override
	public BranchAveragingOrder copy(ModuleContainer<?> newParent) throws IllegalStateException {
		BranchAveragingOrder ret = new BranchAveragingOrder();
		ret.branchFileNames = branchFileNames;
		ret.weights = weights;
		ret.setParent(newParent);
		return ret;
	}
	
	public int getNumBranches() {
		return branchFileNames.length;
	}
	
	public double[] getBranchWeights() {
		return weights;
	}
	
	public double getBranchWeight(int index) {
		return weights[index];
	}
	
	public String[] getBranchFileNames() {
		return getBranchFileNames();
	}
	
	public String getBranchFileName(int index) {
		return branchFileNames[index];
	}
	
	private void checkInitMap() {
		if (nameIndexMap == null) {
			synchronized (this) {
				if (nameIndexMap == null) {
					Map<String, Integer> map = new HashMap<>(branchFileNames.length);
					for (int i=0; i<branchFileNames.length; i++)
						map.put(branchFileNames[i], i);
					this.nameIndexMap = map;
				}
			}
		}
	}
	
	public int getBranchAveragingIndex(LogicTreeBranch<?> branch) {
		checkInitMap();
		String name = branch.buildFileName();
		Integer ret = nameIndexMap.get(name);
		Preconditions.checkState(ret != null, "Branch not found: %s", name);
		return ret;
	}
	
	private static final String FILE_NAME = "branch_averaging_order.csv";

	@Override
	public String getFileName() {
		return FILE_NAME;
	}

	@Override
	public CSVFile<?> getCSV() {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("Branch Index", "Branch Weight", "Branch Name");
		
		for (int i=0; i<branchFileNames.length; i++)
			csv.addLine(i+"", weights[i]+"", branchFileNames[i]);
		
		return csv;
	}

	@Override
	public void initFromCSV(CSVFile<String> csv) {
		branchFileNames = new String[csv.getNumRows()-1];
		weights = new double[branchFileNames.length];
		
		for (int i=0; i<branchFileNames.length; i++) {
			int row = i+1;
			Preconditions.checkState(csv.getInt(row, 0) == i);
			weights[i] = csv.getDouble(row, 1);
			branchFileNames[i] = csv.get(row, 2);
		}
	}

}
