package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import org.opensha.commons.logicTree.AffectsNone;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper;
import org.opensha.sha.imr.logicTree.ScalarIMR_ParamsLogicTreeNode;
import org.opensha.sha.util.TectonicRegionType;

import gov.usgs.earthquake.nshmp.gmm.UsgsPrviBackbone2025;

@AffectsNone
public enum PRVI25_GMM_SlabSigmaModel implements ScalarIMR_ParamsLogicTreeNode {
	SIGMA_NGA(PRVI25_GMM_GenericSigmaModel.SIGMA_NGA),
	SIGMA_PRVI(PRVI25_GMM_GenericSigmaModel.SIGMA_PRVI);
	
	private final PRVI25_GMM_GenericSigmaModel model;

	private PRVI25_GMM_SlabSigmaModel(PRVI25_GMM_GenericSigmaModel model) {
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
	
	public PRVI25_GMM_GenericSigmaModel getModel() {
		return model;
	}

}
