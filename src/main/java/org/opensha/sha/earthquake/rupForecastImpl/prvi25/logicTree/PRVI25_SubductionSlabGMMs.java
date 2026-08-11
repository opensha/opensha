package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import org.opensha.commons.logicTree.AffectsNone;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.imr.AttenRelSupplier;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_AttenRelSupplier;
import org.opensha.sha.imr.logicTree.ScalarIMRsLogicTreeNode;
import org.opensha.sha.util.TectonicRegionType;

import org.opensha.nshmp.shaded.gmm.NshmpGmm;

@AffectsNone
public enum PRVI25_SubductionSlabGMMs implements ScalarIMRsLogicTreeNode.SingleTRT {
	AS_PROVIDED(NshmpGmm.PRVI_2025_INTRASLAB, "As Provided", 0.5),
	DATA_ADJUSTED(NshmpGmm.PRVI_2025_INTRASLAB_ADJUSTED, "Data Adjusted", 0.5);
	
	private NshmpGmm gmm;
	private String shortName;
	private double weight;

	private PRVI25_SubductionSlabGMMs(NshmpGmm gmm, String shortName, double weight) {
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
		return "SLAB_"+name();
	}

	@Override
	public AttenRelSupplier getSupplier() {
		return new NSHMP_AttenRelSupplier(gmm, shortName, false);
	}

	@Override
	public TectonicRegionType getTectonicRegion() {
		return TectonicRegionType.SUBDUCTION_SLAB;
	}
	
	public NshmpGmm getGMM() {
		return gmm;
	}

}
