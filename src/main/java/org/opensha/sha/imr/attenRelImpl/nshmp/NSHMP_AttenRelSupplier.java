package org.opensha.sha.imr.attenRelImpl.nshmp;

import org.opensha.sha.imr.AttenRelSupplier;
import org.opensha.sha.imr.ScalarIMR;

import org.opensha.nshmp.shaded.gmm.NshmpGmm;

public class NSHMP_AttenRelSupplier implements AttenRelSupplier {
	
	private NshmpGmm gmm;
	private String shortName;
	private boolean parameterize;
	private GroundMotionLogicTreeFilter treefilter;

	public NSHMP_AttenRelSupplier(NshmpGmm gmm) {
		this(gmm, true);
	}
	
	public NSHMP_AttenRelSupplier(NshmpGmm gmm, boolean parameterize) {
		this(gmm, gmm.name(), parameterize);
	}
	
	public NSHMP_AttenRelSupplier(NshmpGmm gmm, String shortName, boolean parameterize) {
		this(gmm, shortName, parameterize, null);
	}
	
	public NSHMP_AttenRelSupplier(NshmpGmm gmm, String shortName, boolean parameterize, GroundMotionLogicTreeFilter treefilter) {
		this.gmm = gmm;
		this.shortName = shortName;
		this.parameterize = parameterize;
		this.treefilter = treefilter;
	}

	@Override
	public ScalarIMR get() {
		NSHMP_GMM_Wrapper ret = new NSHMP_GMM_Wrapper.Single(gmm, shortName, parameterize);
		if (treefilter != null)
			ret.setGroundMotionTreeFilter(treefilter);
		return ret;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return gmm.toString();
	}

}
