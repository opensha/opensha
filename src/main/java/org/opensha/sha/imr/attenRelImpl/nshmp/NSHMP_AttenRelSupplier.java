package org.opensha.sha.imr.attenRelImpl.nshmp;

import org.opensha.sha.imr.AttenRelSupplier;
import org.opensha.sha.imr.ScalarIMR;

import gov.usgs.earthquake.nshmp.gmm.Gmm;

public class NSHMP_AttenRelSupplier implements AttenRelSupplier {
	
	private Gmm gmm;
	private String shortName;

	public NSHMP_AttenRelSupplier(Gmm gmm) {
		this(gmm, gmm.name());
	}
	
	public NSHMP_AttenRelSupplier(Gmm gmm, String shortName) {
		this.gmm = gmm;
		this.shortName = shortName;
	}

	@Override
	public ScalarIMR get() {
		return new NSHMP_GMM_Wrapper(gmm, shortName, true);
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
