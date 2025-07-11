package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;

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
public enum PRVI25_SubductionSlabMMax implements LogicTreeNode {
	MAG_7p5(7.5, 0.5d),
	MAG_8p0(8d, 0.5d);
	
	public static PRVI25_SubductionSlabMMax DEFAULT = MAG_8p0;
	
	private double mMax;
	private double weight;

	private PRVI25_SubductionSlabMMax(double mMax, double weight) {
		this.mMax = mMax;
		this.weight = weight;
	}
	
	@Override
	public String getShortName() {
		return (float)mMax+"";
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
		return "SlabMmax"+getShortName().replace('.', 'p');
	}
	
	public double getMmax() {
		return mMax;
	}
	
	public double getIncrementalMmax() {
		return mMax-0.05;
	}

}
