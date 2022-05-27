package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.text.DecimalFormat;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum DistDependSegShift implements LogicTreeNode {
	
	NONE(0d, 1d),
	ONE_KM(1d, 1d),
	TWO_KM(2d, 1d),
	THREE_KM(3d, 1d);
	
	private double shiftKM;
	private double weight;

	private DistDependSegShift(double shiftKM, double weight) {
		this.shiftKM = shiftKM;
		this.weight = weight;
	}

	@Override
	public String getShortName() {
		return getName().replaceAll(" ", "");
	}

	@Override
	public String getName() {
		if (shiftKM != 0d)
			return "Shift "+oDF.format(shiftKM)+"km";
		return "No Shift";
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return getShortName().replace(".", "p");
	}
	
	public double getShiftKM() {
		return shiftKM;
	}
	
	private static final DecimalFormat oDF = new DecimalFormat("0.##");

}
