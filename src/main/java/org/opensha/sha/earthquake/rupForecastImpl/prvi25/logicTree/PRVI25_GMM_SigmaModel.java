package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.util.Arrays;

import org.opensha.commons.logicTree.AffectsNone;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.nshmp.GroundMotionLogicTreeFilter;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_GMM_Wrapper;
import org.opensha.sha.imr.logicTree.ScalarIMR_ParamsLogicTreeNode;

import com.google.common.base.Preconditions;

import gov.usgs.earthquake.nshmp.gmm.UsgsPrviBackbone2025;

@AffectsNone
public enum PRVI25_GMM_SigmaModel implements ScalarIMR_ParamsLogicTreeNode {
	SIGMA_NGA("NGA Sigma Model", "NGA", UsgsPrviBackbone2025.SIGMA_NGA_ID, 0.5d),
	SIGMA_PRVI("PRVI Sigma Model", "PRVI", UsgsPrviBackbone2025.SIGMA_PRVI_ID, 0.5d);
	
	private String name;
	private String shortName;
	private String matchStr;
	private double weight;

	private PRVI25_GMM_SigmaModel(String name, String shortName, String matchStr, double weight) {
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
		NSHMP_GMM_Wrapper gmm = (NSHMP_GMM_Wrapper)imr;
		GroundMotionLogicTreeFilter upstream = gmm.getGroundMotionTreeFilter();
		GroundMotionLogicTreeFilter filter;
		if (upstream == null) {
			filter = new GroundMotionLogicTreeFilter.StringMatching(matchStr);
		} else {
			Preconditions.checkState(upstream instanceof GroundMotionLogicTreeFilter.StringMatching,
					"Can only combine with other string matching filters");
			String[] required = ((GroundMotionLogicTreeFilter.StringMatching)upstream).getRequired();
			required = Arrays.copyOf(required, required.length+1);
			required[required.length-1] = matchStr;
			filter = new GroundMotionLogicTreeFilter.StringMatching(required);
		}
		gmm.setGroundMotionTreeFilter(filter);
	}

}
