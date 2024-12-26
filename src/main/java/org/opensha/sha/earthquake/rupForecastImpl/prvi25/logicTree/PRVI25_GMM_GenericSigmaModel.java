package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import org.opensha.commons.logicTree.AffectsNone;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper;
import org.opensha.sha.imr.logicTree.ScalarIMR_ParamsLogicTreeNode;

import gov.usgs.earthquake.nshmp.gmm.UsgsPrviBackbone2025;

@AffectsNone
public enum PRVI25_GMM_GenericSigmaModel implements ScalarIMR_ParamsLogicTreeNode {
	SIGMA_NGA("NGA Sigma Model", "NGA", UsgsPrviBackbone2025.SIGMA_NGA_ID, 0.5d),
	SIGMA_PRVI("PRVI Sigma Model", "PRVI", UsgsPrviBackbone2025.SIGMA_PRVI_ID, 0.5d);
	
	final String name;
	final String shortName;
	final String matchStr;
	final double weight;

	private PRVI25_GMM_GenericSigmaModel(String name, String shortName, String matchStr, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.matchStr = matchStr;
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
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean isApplicableTo(ScalarIMR imr) {
		return imr instanceof NSHMP_GMM_Wrapper;
	}

	@Override
	public void setParams(ScalarIMR imr) {
		PRVI25_GMM_GenericEpistemicModel.setParams(imr, matchStr, null);
	}

}
