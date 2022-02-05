package org.opensha.sha.imr.attenRelImpl.ngaw2;

import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_WrapperFullParam.EpistemicOption;
import org.opensha.sha.imr.logicTree.ScalarIMR_ParamsLogicTreeNode;

import com.google.common.base.Preconditions;

import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;

//this doesn't affect anything inversion/rupture set related
@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@DoesNotAffect(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(AbstractGridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(AbstractGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME)
@DoesNotAffect(AbstractGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME)
@DoesNotAffect(AbstractGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME)
public enum NGAW2_EpistemicLogicTreeNode implements ScalarIMR_ParamsLogicTreeNode {
	UPPER(EpistemicOption.UPPER, 0.185),
	NONE(null, 0.630),
	LOWER(EpistemicOption.LOWER, 0.185);
	
	private EpistemicOption choice;
	private double weight;
	
	private NGAW2_EpistemicLogicTreeNode(EpistemicOption choice, double weight) {
		this.choice = choice;
		this.weight = weight;
	}

	@Override
	public String getShortName() {
		return getName();
	}

	@Override
	public String getName() {
		return choice == null ? "None" : choice.toString();
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
	public boolean isApplicableTo(ScalarIMR imr) {
		return imr instanceof NGAW2_WrapperFullParam;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setParams(ScalarIMR imr) {
		Preconditions.checkNotNull(imr, "Supplied IMR is null");
		Preconditions.checkState(isApplicableTo(imr), "%s is not applicable to %s", getName(), imr.getName());
		
		imr.getParameter(NGAW2_WrapperFullParam.EPISTEMIC_PARAM_NAME).setValue(choice);
	}

}
