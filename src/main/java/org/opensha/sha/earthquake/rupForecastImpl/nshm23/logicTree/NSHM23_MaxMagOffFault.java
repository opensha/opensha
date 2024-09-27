package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.util.MaxMagOffFaultBranchNode;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@DoesNotAffect(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME)
@Affects(MFDGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM23_MaxMagOffFault implements MaxMagOffFaultBranchNode {
	MAG_7p3(7.3, 0.1d),
	MAG_7p6(7.6, 0.8d),
	MAG_7p9(7.9, 0.1d); // TODO: currently using U3 values, weights
	
	private double mMax;
	private double weight;

	private NSHM23_MaxMagOffFault(double mMax, double weight) {
		this.mMax = mMax;
		this.weight = weight;
	}

	@Override
	public String getShortName() {
		return "MMax"+(float)mMax;
	}

	@Override
	public String getName() {
		return "Mmax="+(float)mMax;
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
	public double getMaxMagOffFault() {
		return mMax;
	}

}
