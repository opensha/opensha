package org.opensha.sha.imr.attenRelImpl.nshmp;

import org.opensha.sha.imr.AttenRelSupplier;
import org.opensha.sha.imr.ScalarIMR;

import gov.usgs.earthquake.nshmp.gmm.Gmm;

public class NSHMP_AttenRelSupplier implements AttenRelSupplier {
	
	private Gmm gmm;
	private String shortName;
	private boolean parameterize;

	public NSHMP_AttenRelSupplier(Gmm gmm) {
		this(gmm, true);
	}
	
	public NSHMP_AttenRelSupplier(Gmm gmm, boolean parameterize) {
		this(gmm, gmm.name(), parameterize);
	}
	
	public NSHMP_AttenRelSupplier(Gmm gmm, String shortName, boolean parameterize) {
		this.gmm = gmm;
		this.shortName = shortName;
		this.parameterize = parameterize;
	}

	@Override
	public ScalarIMR get() {
		return new NSHMP_GMM_Wrapper(gmm, shortName, parameterize);
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
