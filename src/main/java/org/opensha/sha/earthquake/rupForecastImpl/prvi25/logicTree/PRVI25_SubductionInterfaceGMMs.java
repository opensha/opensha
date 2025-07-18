package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import org.opensha.commons.logicTree.AffectsNone;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.imr.AttenRelSupplier;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_AttenRelSupplier;
import org.opensha.sha.imr.logicTree.ScalarIMRsLogicTreeNode;
import org.opensha.sha.util.TectonicRegionType;

import gov.usgs.earthquake.nshmp.gmm.Gmm;

@AffectsNone
public enum PRVI25_SubductionInterfaceGMMs implements ScalarIMRsLogicTreeNode.SingleTRT {
	AS_PROVIDED(Gmm.PRVI_2025_INTERFACE, "As Provided", 0.5),
	DATA_ADJUSTED(Gmm.PRVI_2025_INTERFACE_ADJUSTED, "Data Adjusted", 0.5);
	
	private Gmm gmm;
	private String shortName;
	private double weight;

	private PRVI25_SubductionInterfaceGMMs(Gmm gmm, String shortName, double weight) {
		this.gmm = gmm;
		this.shortName = shortName;
		this.weight = weight;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return "INTERFACE_"+name();
	}

	@Override
	public AttenRelSupplier getSupplier() {
		return new NSHMP_AttenRelSupplier(gmm, shortName, false);
	}

	@Override
	public TectonicRegionType getTectonicRegion() {
		return TectonicRegionType.SUBDUCTION_INTERFACE;
	}

}
