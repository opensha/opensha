package org.opensha.sha.imr.attenRelImpl.ngaw2;

import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.logicTree.ScalarIMR_LogicTreeNode;

import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;

// this doesn't affect anything inversion/rupture set related
@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@DoesNotAffect(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(AbstractGridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(AbstractGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME)
@DoesNotAffect(AbstractGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME)
@DoesNotAffect(AbstractGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME)
public enum NGAW2_LogicTreeNode implements ScalarIMR_LogicTreeNode {
	ASK_2014(AttenRelRef.ASK_2014, 0.22),
	BSSA_2014(AttenRelRef.BSSA_2014, 0.22),
	CB_2014(AttenRelRef.CB_2014, 0.22),
	CY_2014(AttenRelRef.CY_2014, 0.22),
	IDRISS_2014(AttenRelRef.IDRISS_2014, 0.12);
	
	private AttenRelRef ref;
	private double weight;
	private NGAW2_LogicTreeNode(AttenRelRef ref, double weight) {
		this.ref = ref;
		this.weight = weight;
	}
	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}
	@Override
	public String getFilePrefix() {
		return name();
	}
	@Override
	public AttenRelRef getRef() {
		return ref;
	}
}
