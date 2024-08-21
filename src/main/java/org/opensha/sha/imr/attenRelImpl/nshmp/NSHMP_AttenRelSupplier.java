package org.opensha.sha.imr.attenRelImpl.nshmp;

import org.opensha.sha.imr.AttenRelSupplier;
import org.opensha.sha.imr.ScalarIMR;

import gov.usgs.earthquake.nshmp.gmm.Gmm;

public class NSHMP_AttenRelSupplier implements AttenRelSupplier {
	
	private Gmm gmm;
	private String shortName;
	private boolean parameterize;
	private GroundMotionLogicTreeFilter treefilter;

	public NSHMP_AttenRelSupplier(Gmm gmm) {
		this(gmm, true);
	}
	
	public NSHMP_AttenRelSupplier(Gmm gmm, boolean parameterize) {
		this(gmm, gmm.name(), parameterize);
	}
	
	public NSHMP_AttenRelSupplier(Gmm gmm, String shortName, boolean parameterize) {
		this(gmm, shortName, parameterize, null);
	}
	
	public NSHMP_AttenRelSupplier(Gmm gmm, String shortName, boolean parameterize, GroundMotionLogicTreeFilter treefilter) {
		this.gmm = gmm;
		this.shortName = shortName;
		this.parameterize = parameterize;
		this.treefilter = treefilter;
	}

	@Override
	public ScalarIMR get() {
		NSHMP_GMM_Wrapper ret = new NSHMP_GMM_Wrapper(gmm, shortName, parameterize);
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
