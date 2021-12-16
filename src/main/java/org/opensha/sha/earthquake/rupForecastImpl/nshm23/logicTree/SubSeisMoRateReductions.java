package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs.SubSeisMoRateReduction;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum SubSeisMoRateReductions implements LogicTreeNode {
	FAULT_SPECIFIC("Fault-Specific", "FaultSpec", 0d, SubSeisMoRateReduction.FAULT_SPECIFIC_IMPLIED_FROM_SUPRA_B),
	SYSTEM_AVG("System-Average", "SysAvg", 0d, SubSeisMoRateReduction.SYSTEM_AVG_IMPLIED_FROM_SUPRA_B),
	SUB_B_1("Sub-Seis b=1", "SubB1", 1d, SubSeisMoRateReduction.SUB_SEIS_B_1);
	
	private String name;
	private String shortName;
	private double weight;
	private SubSeisMoRateReduction choice;

	private SubSeisMoRateReductions(String name, String shortName, double weight, SubSeisMoRateReduction choice) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
		this.choice = choice;
	}
	
	public SubSeisMoRateReduction getChoice() {
		return choice;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return shortName;
	}

}
