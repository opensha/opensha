package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import org.opensha.commons.logicTree.AffectsNone;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper;
import org.opensha.sha.imr.logicTree.ScalarIMR_ParamsLogicTreeNode;
import org.opensha.sha.util.TectonicRegionType;

@AffectsNone
public enum PRVI25_GMM_SlabEpistemicModel implements ScalarIMR_ParamsLogicTreeNode {
	EPI_LOW(PRVI25_GMM_GenericEpistemicModel.EPI_LOW),
	EPI_OFF(PRVI25_GMM_GenericEpistemicModel.EPI_OFF),
	EPI_HIGH(PRVI25_GMM_GenericEpistemicModel.EPI_HIGH);
	
	private final PRVI25_GMM_GenericEpistemicModel model;

	private PRVI25_GMM_SlabEpistemicModel(PRVI25_GMM_GenericEpistemicModel model) {
		this.model = model;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return model.weight;
	}

	@Override
	public String getFilePrefix() {
		return "SLAB_"+name();
	}

	@Override
	public String getShortName() {
		return model.shortName;
	}

	@Override
	public String getName() {
		return model.name;
	}

	@Override
	public boolean isApplicableTo(ScalarIMR imr) {
		return imr instanceof NSHMP_GMM_Wrapper;
	}

	@Override
	public void setParams(ScalarIMR imr) {
		PRVI25_GMM_GenericEpistemicModel.setParams(imr, model.matchStr, TectonicRegionType.SUBDUCTION_SLAB);
	}
	
	public PRVI25_GMM_GenericEpistemicModel getModel() {
		return model;
	}

}
